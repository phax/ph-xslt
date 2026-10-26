/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.xsd;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.ops.XsdLexical;
import com.helger.xpath.xdm.XdmNode;

/**
 * The compile-time half of identity constraints (go-xslt
 * <code>identity.go</code>: <code>parseIdentityConstraints</code>,
 * <code>checkKeyrefs</code>, the restricted XPath grammar of Part 1 section
 * 3.11.6; <code>xsd11.go</code>: <code>resolveXPathDefaultNamespace</code>).
 * The evaluation on instances is phase V5 ({@link XsdIdentityValidator}).
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class XsdIdentityCompiler
{
  private final XsdCompiler m_aC;

  XsdIdentityCompiler (@NonNull final XsdCompiler aCompiler)
  {
    m_aC = aCompiler;
  }

  private static boolean _isXPathWS (final char c)
  {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
  }

  private static boolean _containsWS (@NonNull final String s)
  {
    for (int i = 0; i < s.length (); i++)
      if (_isXPathWS (s.charAt (i)))
        return true;
    return false;
  }

  /** go-xslt <code>validIdentityNameTest</code>: <code>*</code>, an NCName or a QName with a bound prefix. */
  private static boolean _validNameTest (@NonNull final String s, @NonNull final XdmNode aNode)
  {
    if ("*".equals (s))
      return true;
    if (_containsWS (s))
      return false;
    final int i = s.indexOf (':');
    if (i >= 0)
    {
      final String sPrefix = s.substring (0, i);
      final String sLocal = s.substring (i + 1);
      if (!XsdLexical.isNCName (sPrefix))
        return false;
      if (XsdNodes.lookupPrefix (aNode, sPrefix) == null)
        return false;
      return "*".equals (sLocal) || XsdLexical.isNCName (sLocal);
    }
    return XsdLexical.isNCName (s);
  }

  /**
   * go-xslt <code>validIdentityStep</code>: '.', a name test (optionally with a
   * <code>child::</code> axis) or, as a field's last step only, an attribute
   * step.
   */
  private static boolean _validStep (@NonNull final String s,
                                     final boolean bField,
                                     final boolean bLast,
                                     @NonNull final XdmNode aNode)
  {
    if (s.isEmpty ())
      return false;
    if (s.startsWith ("@"))
      return bField && bLast && _validNameTest (s.substring (1).strip (), aNode);
    final int nAxis = s.indexOf ("::");
    if (nAxis >= 0)
    {
      final String sAxis = s.substring (0, nAxis).strip ();
      final String sRest = s.substring (nAxis + 2).strip ();
      switch (sAxis)
      {
        case "child":
          return _validNameTest (sRest, aNode);
        case "attribute":
          return bField && bLast && _validNameTest (sRest, aNode);
        default:
          return false;
      }
    }
    if (".".equals (s))
      return true;
    return _validNameTest (s, aNode);
  }

  /** go-xslt <code>validIdentityPath</code>. */
  private static boolean _validPath (@NonNull final String sPath, final boolean bField, @NonNull final XdmNode aNode)
  {
    if (sPath.isEmpty ())
      return false;
    final String p = sPath.startsWith (".//") ? sPath.substring (3) : sPath;
    if (p.isEmpty () || p.startsWith ("/") || p.contains ("//"))
      return false;
    final String [] aSegs = p.split ("/", -1);
    for (int i = 0; i < aSegs.length; i++)
      if (!_validStep (aSegs[i].strip (), bField, i == aSegs.length - 1, aNode))
        return false;
    return true;
  }

  /**
   * Compile a selector or field with the namespaces in scope at the schema
   * element. go-xslt keeps the parsed expression unresolved; here names are
   * resolved at compile time, so the namespaces go into the static context.
   *
   * @throws XsdUnsupportedException
   *         if the XPath engine cannot compile the expression
   */
  @NonNull
  private static CompiledXPath _compile (@NonNull final String sExpr,
                                         @NonNull final ICommonsOrderedMap <String, String> aNS,
                                         @NonNull final String sDefaultNS)
  {
    final StaticContext aSC = new StaticContext ();
    for (final var aEntry : aNS.entrySet ())
      if (!aEntry.getKey ().isEmpty ())
        aSC.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    aSC.setDefaultElementNamespace (sDefaultNS);
    try
    {
      return XPathCompiler.compile (sExpr, aSC);
    }
    catch (final XPathException ex)
    {
      throw new XsdUnsupportedException ("identity-constraint XPath " +
                                         XsdLexHelper.quote (sExpr) +
                                         " cannot be compiled: " +
                                         ex.getMessage (),
                                         ex);
    }
  }

  /**
   * go-xslt <code>validateIdentityXPath</code>: a selector or field against
   * the restricted XPath subset of identity constraints.
   */
  static void validateIdentityXPath (@NonNull final String sExpr, final boolean bField, @NonNull final XdmNode aNode)
  {
    // A QName admits no internal whitespace: a single ':' adjacent to
    // whitespace splits a QName across tokens
    final int nLen = sExpr.length ();
    for (int i = 0; i < nLen; i++)
    {
      if (sExpr.charAt (i) != ':')
        continue;
      if (i > 0 && sExpr.charAt (i - 1) == ':' || i + 1 < nLen && sExpr.charAt (i + 1) == ':')
        continue;
      if (i > 0 && _isXPathWS (sExpr.charAt (i - 1)) || i + 1 < nLen && _isXPathWS (sExpr.charAt (i + 1)))
        throw XsdCompiler.invalid ("", "invalid xpath " + XsdLexHelper.quote (sExpr) + ": whitespace inside a QName");
    }
    // No token of this grammar contains whitespace: drop it
    final String sCollapsed = String.join ("", XsdLexHelper.xmlFields (sExpr));
    for (final String sPath : sCollapsed.split ("\\|", -1))
      if (!_validPath (sPath, bField, aNode))
        throw XsdCompiler.invalid ("",
                                   "invalid " + (bField ? "field" : "selector") + " xpath " + XsdLexHelper.quote (sCollapsed));
  }

  /**
   * go-xslt <code>resolveXPathDefaultNS</code>: the effective
   * xpathDefaultNamespace of an XPath-carrying schema element (the nearest
   * attribute on the element or an ancestor up to xs:schema).
   */
  @NonNull
  String resolveXPathDefaultNamespace (@NonNull final XdmNode aNode)
  {
    String sRaw = null;
    XdmNode aCarrier = null;
    for (XdmNode n = aNode; n != null && n.isElement (); n = n.getParent ())
    {
      final String v = XsdNodes.attr (n, "xpathDefaultNamespace");
      if (v != null)
      {
        sRaw = v;
        aCarrier = n;
        break;
      }
      if (XsdNodes.isXS (n, "schema"))
        break;
    }
    if (aCarrier == null)
      return "";
    switch (sRaw.strip ())
    {
      case "##local":
        return "";
      case "##targetNamespace":
        return m_aC.m_sTNS;
      case "##defaultNamespace":
      {
        final String sNS = XsdNodes.lookupPrefix (aCarrier, "");
        return sNS == null ? "" : sNS;
      }
      default:
        return sRaw.strip ();
    }
  }

  /**
   * go-xslt <code>parseIdentityConstraints</code>: the xs:key, xs:keyref and
   * xs:unique children of an element declaration.
   */
  @NonNull
  ICommonsList <XsdIdentityConstraint> parseIdentityConstraints (@NonNull final XdmNode aNode)
  {
    final ICommonsList <XsdIdentityConstraint> ret = new CommonsArrayList <> ();
    for (final XdmNode aChild : XsdNodes.children (aNode))
    {
      if (!XsdNodes.isXSElement (aChild))
        continue;
      final String sKind = XsdNodes.localName (aChild);
      if (!"key".equals (sKind) && !"keyref".equals (sKind) && !"unique".equals (sKind))
        continue;
      final String sRef = XsdNodes.attr (aChild, "ref");
      if (sRef != null)
      {
        // XSD 1.1: an identity constraint may reference another definition
        // of the same kind (resolved in checkKeyrefs)
        if (m_aC.isXsd10 ())
          throw XsdCompiler.invalid ("src-identity-constraint", "identity-constraint @ref is an XSD 1.1 feature");
        if (XsdNodes.hasAttr (aChild, "name"))
          throw XsdCompiler.invalid ("src-identity-constraint", "@name and @ref are mutually exclusive on xs:" + sKind);
        if (XsdNodes.hasAttr (aChild, "refer"))
          throw XsdCompiler.invalid ("src-identity-constraint", "@refer is not allowed together with @ref");
        for (final XdmNode aSub : XsdNodes.children (aChild))
          if (aSub.isElement () && !XsdNodes.isXS (aSub, "annotation"))
            throw XsdCompiler.invalid ("src-identity-constraint", "an xs:" + sKind + " with @ref admits only an annotation child");
        final XsdIdentityConstraint aIC = new XsdIdentityConstraint (sKind);
        aIC.m_aRefName = XsdNodes.resolveQName (aChild, sRef);
        aIC.m_bRefPending = true;
        ret.add (aIC);
        m_aC.m_aICRefs.add (aIC);
        continue;
      }
      final XsdIdentityConstraint aIC = new XsdIdentityConstraint (sKind);
      aIC.m_aNS = XsdNodes.inScopeNamespaces (aChild);
      final String sName = XsdNodes.declName (aChild);
      if (!XsdLexHelper.isNCName (sName))
        throw XsdCompiler.invalid ("", "invalid identity-constraint name " + XsdLexHelper.quote (sName));
      aIC.m_aName = new XsdName (m_aC.m_sTNS, sName);
      if ("keyref".equals (sKind))
      {
        final String sRefer = XsdNodes.attr (aChild, "refer");
        if (sRefer != null)
        {
          aIC.m_aRefer = XsdNodes.resolveQName (aChild, sRefer);
          // src-resolve: @refer may only reach a namespace this document imports
          if (!m_aC.reachableNS (aIC.m_aRefer.space ()))
            throw XsdCompiler.invalid ("src-resolve",
                                       "keyref @refer " + aIC.m_aRefer + " is in a namespace this schema does not import");
        }
      }
      final XdmNode aSel = XsdNodes.firstXSChild (aChild, "selector");
      if (aSel == null)
        throw XsdCompiler.invalid ("", "identity constraint without a selector");
      final String sSelector = XsdNodes.attrOrEmpty (aSel, "xpath");
      validateIdentityXPath (sSelector, false, aSel);
      aIC.m_sSelNS = resolveXPathDefaultNamespace (aSel);
      aIC.m_aSelector = _compile (sSelector, XsdNodes.inScopeNamespaces (aSel), aIC.m_sSelNS);
      aIC.m_sSelectorSrc = sSelector;
      for (final XdmNode aField : XsdNodes.children (aChild))
      {
        if (!XsdNodes.isXS (aField, "field"))
          continue;
        final String sFieldExpr = XsdNodes.attrOrEmpty (aField, "xpath");
        validateIdentityXPath (sFieldExpr, true, aField);
        final String sFieldNS = resolveXPathDefaultNamespace (aField);
        aIC.m_aFields.add (_compile (sFieldExpr, XsdNodes.inScopeNamespaces (aField), sFieldNS));
        aIC.m_aFieldRaw.add (sFieldExpr.strip ());
        aIC.m_aFieldSrc.add (sFieldExpr);
        aIC.m_aFieldNS.add (sFieldNS);
      }
      if (aIC.m_aFields.isEmpty ())
        throw XsdCompiler.invalid ("", "identity constraint without a field");
      ret.add (aIC);
      m_aC.m_aAllICs.add (aIC);
    }
    return ret;
  }

  /**
   * go-xslt <code>checkKeyrefs</code>: identity-constraint names are unique,
   * XSD 1.1 @ref uses resolve, a keyref's @refer names a key or unique with as
   * many fields.
   */
  void checkKeyrefs ()
  {
    final ICommonsMap <XsdName, XsdIdentityConstraint> aByName = new CommonsHashMap <> ();
    for (final XsdIdentityConstraint aIC : m_aC.m_aAllICs)
    {
      final XsdIdentityConstraint aPrev = aByName.get (aIC.m_aName);
      if (aPrev != null && aPrev != aIC)
        throw XsdCompiler.invalid ("c-props-correct.1", "duplicate identity constraint " + aIC.m_aName);
      aByName.put (aIC.m_aName, aIC);
    }
    // Resolve 1.1 @ref uses: the referenced definition is copied in
    for (final XsdIdentityConstraint aIC : m_aC.m_aICRefs)
    {
      if (!aIC.m_bRefPending)
        continue;
      final XsdIdentityConstraint aRef = aByName.get (aIC.m_aRefName);
      if (aRef == null)
        throw XsdCompiler.invalid ("src-resolve", "identity constraint " + aIC.m_aRefName + " does not resolve");
      if (!aRef.m_sKind.equals (aIC.m_sKind))
        throw XsdCompiler.invalid ("c-props-correct",
                                   "xs:" + aIC.m_sKind + " @ref must name a " + aIC.m_sKind + " (got " + aRef.m_sKind + ")");
      aIC.assignFrom (aRef);
    }
    for (final XsdIdentityConstraint aIC : m_aC.m_aAllICs)
    {
      if (!"keyref".equals (aIC.m_sKind) || aIC.m_aRefer.isZero ())
        continue;
      final XsdIdentityConstraint aRef = aByName.get (aIC.m_aRefer);
      if (aRef == null || "keyref".equals (aRef.m_sKind))
        throw XsdCompiler.invalid ("c-props-correct.2",
                                   "keyref " + aIC.m_aName + " refers to " + aIC.m_aRefer + ", which is not a key or unique");
      if (aRef.m_aFields.size () != aIC.m_aFields.size ())
        throw XsdCompiler.invalid ("c-props-correct.2",
                                   "keyref " +
                                                         aIC.m_aName +
                                                         " has " +
                                                         aIC.m_aFields.size () +
                                                         " fields but " +
                                                         aIC.m_aRefer +
                                                         " has " +
                                                         aRef.m_aFields.size ());
    }
  }
}
