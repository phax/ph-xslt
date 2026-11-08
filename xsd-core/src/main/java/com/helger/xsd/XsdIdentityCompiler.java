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
import com.helger.xpath.xdm.IXdmNode;

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

  /**
   * @param aCompiler
   *        The compiler whose target namespace, version, imports and
   *        identity-constraint lists are used and filled.
   */
  XsdIdentityCompiler (@NonNull final XsdCompiler aCompiler)
  {
    m_aC = aCompiler;
  }

  private static boolean _isXPathWS (final char c)
  {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
  }

  /**
   * @param c
   *        A character.
   * @return <code>true</code> if it can end or start a name test or the
   *         <code>.</code> step (a name character or <code>*</code>).
   */
  private static boolean _isNameTestChar (final char c)
  {
    return c == '*' || c == '_' || c == '-' || c == '.' || Character.isLetterOrDigit (c) || c > 0x7f;
  }

  /**
   * @param s
   *        The string to check.
   * @return <code>true</code> if it contains a space, tab, CR or LF.
   */
  private static boolean _containsWS (@NonNull final String s)
  {
    for (int i = 0; i < s.length (); i++)
      if (_isXPathWS (s.charAt (i)))
        return true;
    return false;
  }

  /** go-xslt <code>validIdentityNameTest</code>: <code>*</code>, an NCName or a QName with a bound prefix. */
  private static boolean _validNameTest (@NonNull final String s, @NonNull final IXdmNode aNode)
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
                                     @NonNull final IXdmNode aNode)
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
  private static boolean _validPath (@NonNull final String sPath, final boolean bField, @NonNull final IXdmNode aNode)
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
   * the restricted XPath subset of identity constraints (XSD 1.1 Structures
   * 3.11.6.2 Selector Value OK and 3.11.6.3 Fields Value OK).
   * <p>
   * First a single <code>:</code> with whitespace on either side is rejected
   * (a QName cannot be split; <code>::</code> is exempt), and so is whitespace
   * between two name tests or steps (<code>a b</code>). Then all whitespace
   * is removed and the expression is split at <code>|</code>; every
   * alternative must be a path of the form <code>(.//)? Step ('/' Step)*</code>
   * where a step is <code>.</code>, a name test (<code>*</code>,
   * <code>p:*</code>, an NCName or a QName whose prefix is bound at
   * <code>aNode</code>), optionally with <code>child::</code>, and, for a
   * field's last step only, <code>@</code> or <code>attribute::</code> with a
   * name test. An empty alternative, a leading <code>/</code> and any other
   * <code>//</code> are rejected.
   *
   * @param sExpr
   *        The <code>xpath</code> attribute value.
   * @param bField
   *        <code>true</code> for a field, <code>false</code> for a selector.
   * @param aNode
   *        The xs:selector or xs:field element, for prefix resolution.
   * @throws XsdInvalidException
   *         (without an error code) if the expression is outside the subset
   */
  static void validateIdentityXPath (@NonNull final String sExpr, final boolean bField, @NonNull final IXdmNode aNode)
  {
    // A QName admits no internal whitespace: a single ':' adjacent to
    // whitespace splits a QName across tokens
    final int nLen = sExpr.length ();
    for (int i = 0; i < nLen; i++)
    {
      if (sExpr.charAt (i) != ':')
        continue;
      if ((i > 0 && sExpr.charAt (i - 1) == ':') || (i + 1 < nLen && sExpr.charAt (i + 1) == ':'))
        continue;
      if ((i > 0 && _isXPathWS (sExpr.charAt (i - 1))) || (i + 1 < nLen && _isXPathWS (sExpr.charAt (i + 1))))
        throw XsdCompiler.invalid ("", "invalid xpath " + XsdLexHelper.quote (sExpr) + ": whitespace inside a QName");
    }
    // Whitespace between two name tests (or steps) separates tokens that no
    // production of the subset puts side by side: "a b" is no path
    int nPrev = -1;
    for (int i = 0; i < nLen; i++)
    {
      final char c = sExpr.charAt (i);
      if (_isXPathWS (c))
        continue;
      if (nPrev >= 0 && nPrev < i - 1 && _isNameTestChar (sExpr.charAt (nPrev)) && _isNameTestChar (c))
        throw XsdCompiler.invalid ("", "invalid xpath " + XsdLexHelper.quote (sExpr) + ": two adjacent name tests");
      nPrev = i;
    }
    // No token of this grammar contains whitespace: drop it
    final String sCollapsed = String.join ("", XsdLexHelper.xmlFields (sExpr));
    for (final String sPath : sCollapsed.split ("\\|", -1))
      if (!_validPath (sPath, bField, aNode))
        throw XsdCompiler.invalid ("",
                                   "invalid " + (bField ? "field" : "selector") + " xpath " + XsdLexHelper.quote (sCollapsed));
  }

  /**
   * go-xslt <code>resolveXPathDefaultNS</code>: the {default namespace} of the
   * XPath expression of a host element (XSD 1.1 Structures 3.13.2): the
   * <code>xpathDefaultNamespace</code> attribute of the host element, or else
   * that of its xs:schema ancestor.
   * <p>
   * <code>##local</code> and no attribute at all give the empty string (no
   * namespace), <code>##targetNamespace</code> the target namespace of the
   * schema document being compiled, <code>##defaultNamespace</code> the
   * default namespace in scope at the host element (empty if none), any
   * other value its whitespace-stripped self. go-xslt takes the default
   * namespace in scope at the element carrying the attribute instead.
   *
   * @param aNode
   *        The host element (xs:selector, xs:field, xs:assert, ...).
   * @return The namespace URI used for unprefixed element names, empty for
   *         none. Never <code>null</code>.
   */
  @NonNull
  String resolveXPathDefaultNamespace (@NonNull final IXdmNode aNode)
  {
    String sRaw = XsdNodes.attr (aNode, "xpathDefaultNamespace");
    if (sRaw == null)
      for (IXdmNode n = aNode.getParent (); n != null && n.isElement (); n = n.getParent ())
        if (XsdNodes.isXS (n, "schema"))
        {
          sRaw = XsdNodes.attr (n, "xpathDefaultNamespace");
          break;
        }
    if (sRaw == null)
      return "";
    switch (sRaw.strip ())
    {
      case "##local":
        return "";
      case "##targetNamespace":
        return m_aC.m_sTNS;
      case "##defaultNamespace":
      {
        final String sNS = XsdNodes.lookupPrefix (aNode, "");
        return sNS == null ? "" : sNS;
      }
      default:
        return sRaw.strip ();
    }
  }

  /**
   * go-xslt <code>parseIdentityConstraints</code>: the xs:key, xs:keyref and
   * xs:unique children of an element declaration.
   * <p>
   * A child with <code>@ref</code> (XSD 1.1 only, else
   * src-identity-constraint) may have neither <code>@name</code> nor
   * <code>@refer</code> nor any child other than xs:annotation (all
   * src-identity-constraint); it becomes a pending reference registered
   * with the compiler and resolved by {@link #checkKeyrefs}. Any other child
   * needs an NCName <code>@name</code> (named in the target namespace), an
   * xs:selector and at least one xs:field; the selector and each field are
   * checked with {@link #validateIdentityXPath}, get their effective
   * xpathDefaultNamespace and are compiled with the namespaces in scope at
   * their own element. A keyref's <code>@refer</code> must be in a
   * namespace this document can reach (src-resolve). Each non-reference
   * constraint is also added to the compiler's list of all constraints.
   *
   * @param aNode
   *        The xs:element.
   * @return The constraints in document order, references included.
   * @throws XsdInvalidException
   *         as listed above
   * @throws XsdUnsupportedException
   *         if the XPath engine cannot compile a selector or field
   */
  @NonNull
  ICommonsList <XsdIdentityConstraint> parseIdentityConstraints (@NonNull final IXdmNode aNode)
  {
    final ICommonsList <XsdIdentityConstraint> ret = new CommonsArrayList <> ();
    for (final IXdmNode aChild : XsdNodes.children (aNode))
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
        for (final IXdmNode aSub : XsdNodes.children (aChild))
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
      final IXdmNode aSel = XsdNodes.firstXSChild (aChild, "selector");
      if (aSel == null)
        throw XsdCompiler.invalid ("", "identity constraint without a selector");
      final String sSelector = XsdNodes.attrOrEmpty (aSel, "xpath");
      validateIdentityXPath (sSelector, false, aSel);
      aIC.m_sSelNS = resolveXPathDefaultNamespace (aSel);
      aIC.m_aSelector = _compile (sSelector, XsdNodes.inScopeNamespaces (aSel), aIC.m_sSelNS);
      aIC.m_aSelectorPath = XsdIdentityPath.parse (sSelector, aSel, aIC.m_sSelNS);
      aIC.m_sSelectorSrc = sSelector;
      for (final IXdmNode aField : XsdNodes.children (aChild))
      {
        if (!XsdNodes.isXS (aField, "field"))
          continue;
        final String sFieldExpr = XsdNodes.attrOrEmpty (aField, "xpath");
        validateIdentityXPath (sFieldExpr, true, aField);
        final String sFieldNS = resolveXPathDefaultNamespace (aField);
        aIC.m_aFields.add (_compile (sFieldExpr, XsdNodes.inScopeNamespaces (aField), sFieldNS));
        aIC.m_aFieldPaths.add (XsdIdentityPath.parse (sFieldExpr, aField, sFieldNS));
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
   * <p>
   * Runs once after all schema documents are parsed, over every identity
   * constraint of the schema (the only check of their name uniqueness). A
   * second constraint of the same name is sch-props-correct.2. A pending
   * <code>@ref</code> that names no constraint is src-resolve, one naming a
   * constraint of another kind is c-props-correct; otherwise the referenced
   * definition is copied into it. A keyref whose <code>@refer</code> names no
   * constraint is src-resolve, one naming a keyref c-props-correct.1 (the
   * {referenced key} is a key or unique), and one naming a key/unique with a
   * different number of fields c-props-correct.2.
   *
   * @throws XsdInvalidException
   *         as listed above
   */
  void checkKeyrefs ()
  {
    final ICommonsMap <XsdName, XsdIdentityConstraint> aByName = new CommonsHashMap <> ();
    for (final XsdIdentityConstraint aIC : m_aC.m_aAllICs)
    {
      final XsdIdentityConstraint aPrev = aByName.get (aIC.m_aName);
      if (aPrev != null && aPrev != aIC)
        throw XsdCompiler.invalid ("sch-props-correct.2", "duplicate identity constraint " + aIC.m_aName);
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
      if (aRef == null)
        throw XsdCompiler.invalid ("src-resolve", "keyref " + aIC.m_aName + " refers to " + aIC.m_aRefer + ", which does not resolve");
      if ("keyref".equals (aRef.m_sKind))
        throw XsdCompiler.invalid ("c-props-correct.1",
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
