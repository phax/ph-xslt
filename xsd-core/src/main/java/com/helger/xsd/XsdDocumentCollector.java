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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Schema composition (go-xslt <code>imports.go</code>): resolves the
 * include/import/redefine graph starting from a schema element into a flat
 * list of {@link XsdSchemaDocument}s, applying conditional inclusion
 * (<code>vc:*</code>) first. Missing files are skipped (an import without a
 * resolvable location is legal); a visited set breaks cycles. Every document is
 * read through the compile's {@link XsdFileAccess}.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class XsdDocumentCollector
{
  private final XsdCompiler m_aC;

  /** The state of one {@link #stripVersioning} run: the first vc error. */
  private static final class VcState
  {
    private final EXsdVersion m_eVersion;
    private XsdInvalidException m_aError;

    VcState (@NonNull final EXsdVersion eVersion)
    {
      m_eVersion = eVersion;
    }

    private void _setError (@NonNull final XsdInvalidException ex)
    {
      if (m_aError == null)
        m_aError = ex;
    }
  }

  XsdDocumentCollector (@NonNull final XsdCompiler aCompiler)
  {
    m_aC = aCompiler;
  }

  /**
   * A vc version number: <code>vc:minVersion</code> and
   * <code>vc:maxVersion</code> are typed as xs:decimal (XSD 1.1 Part 1,
   * 4.2.2), so only the decimal lexical form (optional sign, digits with an
   * optional fraction) is a number; INF, NaN, exponents and hexadecimal are
   * not (go-xslt also accepts INF and NaN).
   *
   * @return The value or <code>null</code> if it is no xs:decimal.
   */
  @Nullable
  private static Double _parseVcNumber (@NonNull final String s)
  {
    if (s.matches ("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)"))
      return Double.valueOf (Double.parseDouble (s));
    return null;
  }

  /**
   * Read the value of <code>vc:minVersion</code> or <code>vc:maxVersion</code>
   * (XSD 1.1 Part 1, 4.2.2) after stripping leading and trailing whitespace,
   * with {@link #_parseVcNumber}. Under 1.1 a value that is no number records
   * an "invalid vc:..." schema error in the state (only the first error of a
   * run is kept); under 1.0 it is silently ignored.
   *
   * @param aState
   *        The run state receiving the error.
   * @param sValue
   *        The raw attribute value.
   * @param sAttr
   *        The local attribute name, for the message.
   * @return The number, or <code>null</code> if the value is no number. The
   *         caller then ignores the attribute.
   */
  @Nullable
  private static Double _vcDecimal (@NonNull final VcState aState, @NonNull final String sValue, @NonNull final String sAttr)
  {
    final String v = sValue.strip ();
    final Double ret = _parseVcNumber (v);
    if (ret == null && aState.m_eVersion == EXsdVersion.V11)
      aState._setError (XsdCompiler.invalid ("", "invalid vc:" + sAttr + " value " + XsdLexHelper.quote (v)));
    return ret;
  }

  /**
   * Lexical check of a <code>vc:typeAvailable</code>,
   * <code>vc:typeUnavailable</code>, <code>vc:facetAvailable</code> or
   * <code>vc:facetUnavailable</code> value, which XSD 1.1 Part 1, 4.2.2 types
   * as a list of xs:QName. Only under 1.1: the first token that is no lexical
   * QName, or whose prefix is not declared in scope of the element, records a
   * schema error in the state and ends the check. Under 1.0 nothing is
   * checked. The availability decision itself is made by the caller.
   *
   * @param aState
   *        The run state receiving the error.
   * @param aNode
   *        The element carrying the attribute, for the prefix lookup.
   * @param sValue
   *        The raw attribute value (whitespace-separated tokens).
   * @param sAttr
   *        The local attribute name, for the message.
   */
  private static void _vcQNames (@NonNull final VcState aState,
                                 @NonNull final IXdmNode aNode,
                                 @NonNull final String sValue,
                                 @NonNull final String sAttr)
  {
    if (aState.m_eVersion != EXsdVersion.V11)
      return;
    for (final String sToken : XsdLexHelper.xmlFields (sValue))
    {
      if (!XsdLexHelper.validQNameLexical (sToken))
      {
        aState._setError (XsdCompiler.invalid ("", "invalid QName " + XsdLexHelper.quote (sToken) + " in vc:" + sAttr));
        return;
      }
      final int i = sToken.indexOf (':');
      if (i >= 0 && XsdNodes.lookupPrefix (aNode, sToken.substring (0, i)) == null)
      {
        aState._setError (XsdCompiler.invalid ("",
                                               "undeclared prefix " +
                                                   XsdLexHelper.quote (sToken.substring (0, i)) +
                                                   " in vc:" +
                                                   sAttr));
        return;
      }
    }
  }

  /** Whether an element is retained by its own vc attributes. */
  private static boolean _keepElement (@NonNull final VcState aState, @NonNull final IXdmNode aNode)
  {
    final double dOur = aState.m_eVersion == EXsdVersion.V11 ? 1.1 : 1.0;
    String v = XsdNodes.attrNS (aNode, XsdConstants.NS_VC, "minVersion");
    if (v != null)
    {
      final Double aF = _vcDecimal (aState, v, "minVersion");
      if (aF != null && dOur < aF.doubleValue ())
        return false;
    }
    v = XsdNodes.attrNS (aNode, XsdConstants.NS_VC, "maxVersion");
    if (v != null)
    {
      final Double aF = _vcDecimal (aState, v, "maxVersion");
      if (aF != null && dOur >= aF.doubleValue ())
        return false;
    }
    v = XsdNodes.attrNS (aNode, XsdConstants.NS_VC, "facetAvailable");
    if (v != null)
    {
      _vcQNames (aState, aNode, v, "facetAvailable");
      for (final String sFacet : XsdLexHelper.xmlFields (v))
        if (!vcFacetSupported (aNode, sFacet, aState.m_eVersion))
          return false;
    }
    v = XsdNodes.attrNS (aNode, XsdConstants.NS_VC, "facetUnavailable");
    if (v != null)
    {
      _vcQNames (aState, aNode, v, "facetUnavailable");
      // Retained iff at least one named facet is unsupported
      boolean bAll = true;
      for (final String sFacet : XsdLexHelper.xmlFields (v))
        if (!vcFacetSupported (aNode, sFacet, aState.m_eVersion))
        {
          bAll = false;
          break;
        }
      if (bAll)
        return false;
    }
    v = XsdNodes.attrNS (aNode, XsdConstants.NS_VC, "typeAvailable");
    if (v != null)
    {
      _vcQNames (aState, aNode, v, "typeAvailable");
      for (final String sType : XsdLexHelper.xmlFields (v))
        if (!vcTypeAvailable (aNode, sType, aState.m_eVersion))
          return false;
    }
    v = XsdNodes.attrNS (aNode, XsdConstants.NS_VC, "typeUnavailable");
    if (v != null)
    {
      _vcQNames (aState, aNode, v, "typeUnavailable");
      // Retained iff at least one named type is unavailable
      boolean bAll = true;
      for (final String sType : XsdLexHelper.xmlFields (v))
        if (!vcTypeAvailable (aNode, sType, aState.m_eVersion))
        {
          bAll = false;
          break;
        }
      if (bAll)
        return false;
    }
    return true;
  }

  /**
   * go-xslt <code>redefChildNames</code>: the "kind name" identities of the
   * components an xs:redefine element redefines.
   */
  @NonNull
  private static ICommonsSet <String> _redefChildNames (@NonNull final IXdmNode aRedefine)
  {
    final ICommonsSet <String> ret = new CommonsHashSet <> ();
    for (final IXdmNode aChild : XsdNodes.children (aRedefine))
    {
      if (!XsdNodes.isXSElement (aChild))
        continue;
      final String sLocal = XsdNodes.localName (aChild);
      switch (sLocal)
      {
        case "simpleType":
        case "complexType":
        case "group":
        case "attributeGroup":
        {
          final String sName = XsdNodes.attr (aChild, "name");
          if (sName != null)
            ret.add (sLocal + " " + XsdWhiteSpace.collapse (sName));
          break;
        }
        default:
          break;
      }
    }
    return ret;
  }

  /**
   * go-xslt <code>directiveTnsOK</code>: the namespace-consistency rules
   * between an include/redefine/override/import directive and the
   * targetNamespace of the document it resolves to.
   */
  private static void _directiveTnsOK (@NonNull final IXdmNode aDirective,
                                       @NonNull final String sOwnTns,
                                       @NonNull final String sSubTns)
  {
    switch (XsdNodes.localName (aDirective))
    {
      case "include":
      case "redefine":
      case "override":
        // The referenced schema must share this document's target namespace
        // or have none (chameleon)
        if (!sSubTns.isEmpty () && !sSubTns.equals (sOwnTns))
          throw XsdCompiler.invalid ("",
                                     "included schema namespace " +
                                         XsdLexHelper.quote (sSubTns) +
                                         " does not match " +
                                         XsdLexHelper.quote (sOwnTns));
        break;
      case "import":
      {
        // The import's namespace must equal the imported document's target
        // namespace (both absent means no-namespace components)
        final String sImpNs = XsdNodes.attrOrEmpty (aDirective, "namespace");
        if (!sSubTns.equals (sImpNs))
          throw XsdCompiler.invalid ("",
                                     "imported schema namespace " +
                                         XsdLexHelper.quote (sSubTns) +
                                         " does not match import namespace " +
                                         XsdLexHelper.quote (sImpNs));
        break;
      }
      default:
        break;
    }
  }

  /** src-redefine.2 for indirect cycles: two documents redefining each other. */
  private void _checkRedefineCycle (@NonNull final String sSelfPath, @NonNull final String sPath)
  {
    final ICommonsSet <String> aSeen = new CommonsHashSet <> ();
    aSeen.add (sSelfPath);
    final ICommonsList <String> aStack = new CommonsArrayList <> ();
    aStack.add (sPath);
    while (aStack.isNotEmpty ())
    {
      final String sCur = aStack.removeLast ();
      if (sCur.equals (sSelfPath))
        throw XsdCompiler.invalid ("src-redefine.2", "circular xs:redefine between schema documents");
      if (aSeen.contains (sCur) && !sCur.equals (sPath))
        continue;
      final ICommonsList <String> aNext = m_aC.m_aRedefineEdges.get (sCur);
      if (aNext != null)
        for (final String sNext : aNext)
        {
          if (sNext.equals (sSelfPath))
            throw XsdCompiler.invalid ("src-redefine.2", "circular xs:redefine between schema documents");
          if (aSeen.add (sNext))
            aStack.add (sNext);
        }
    }
  }

  /**
   * go-xslt <code>collectOverride</code>: load the target of an xs:override
   * carrying component children, apply the override transformation, and
   * collect the rewritten document. The dedup keys carry the override's
   * signature: the same path under a different override is a fresh
   * collection, while the merge fixpoint in override cycles terminates because
   * a repeated (path, signature) pair is skipped.
   */
  private void _collectOverride (@NonNull final IXdmNode aOverride,
                                 @NonNull final XsdSchemaDocument aSD,
                                 @NonNull final Path aPath,
                                 @NonNull final String sLoc,
                                 @NonNull final ICommonsList <XsdSchemaDocument> aOut,
                                 @NonNull final ICommonsSet <String> aVisited)
  {
    final String sSignature = XsdOverride.signature (aOverride);
    final String sPath = aPath.toString ();
    if (!aVisited.add ("ov:" + sPath + "\u0000" + sSignature))
      return;
    final byte [] aData = m_aC.m_aFileAccess.readFile (aPath);
    if (aData == null)
    {
      // Unresolvable location: components simply unavailable
      return;
    }
    final XsdSchemaSource aSource = XsdSchemaSource.ofBytes (aData, aPath, aPath.getParent ());
    final IXdmNode aSub = XsdNodes.rootElement (XsdCompiler.parseDocument (aSource, "overridden schema " + sLoc));
    if (!XsdCompiler.isSchemaElement (aSub))
      throw XsdCompiler.invalid ("", "overridden " + sLoc + " is not a schema");
    final String sSubTns = XsdNodes.attrOrEmpty (aSub, "targetNamespace");
    m_aC.m_aTnsByPath.putIfAbsent (sPath, sSubTns);
    _directiveTnsOK (aOverride, aSD.m_sTNS, sSubTns);
    final String sContent = new String (aData, StandardCharsets.ISO_8859_1);
    if (!aVisited.add ("ovc:" + sSignature + "\u0000" + sContent))
      return;
    // Mark the original content as consumed by the override: a supplementary
    // document handed to the compiler that is also an override target
    // contributes only its transformed self, never its plain components
    aVisited.add ("overridden:" + sContent);
    final IXdmNode aTransformed = XsdOverride.apply (aSub, aOverride);
    final int nMark = aOut.size ();
    m_aC.enterNesting ();
    try
    {
      _collect (aTransformed, aPath.getParent (), sPath, aOut, aVisited, aSD.m_sTNS);
    }
    finally
    {
      m_aC.leaveNesting ();
    }
    // QName references inside the transplanted override children must also
    // reach the namespaces the overriding document imports (W3C bug 17574)
    if (aOut.size () > nMark)
      aOut.get (nMark).m_aImports.addAll (aSD.m_aImports);
  }

  /**
   * Collect one schema document and, depth first, every document its
   * include/import/redefine/override directives reach (XSD 1.1 Part 1, 4.2.3
   * to 4.2.6).
   * <p>
   * First the conditional-inclusion pass ({@link #stripVersioning}) runs on
   * the element; if the schema element itself is excluded, nothing is
   * collected and no directive is followed. Otherwise a
   * {@link XsdSchemaDocument} is appended to <code>aOut</code> (before the
   * documents it reaches) carrying elementFormDefault, attributeFormDefault,
   * the target namespace (or, for a chameleon include, the adopted
   * <code>sAdoptNs</code>), the imported namespaces, finalDefault,
   * blockDefault and, under 1.1 only, defaultAttributes.
   * <p>
   * Then each directive child is handled in order:
   * <ul>
   * <li>xs:import: no <code>namespace</code> without a target namespace
   * (src-import.1.2), an empty <code>namespace</code> (no clause of its own,
   * reported without a code) and the document's own target namespace
   * (src-import.1.1) are schema errors.</li>
   * <li>A missing <code>schemaLocation</code> is an error except for an
   * import; an import without location, any directive while the base
   * directory is unknown, and a location that is no local file are
   * skipped.</li>
   * <li>Under 1.1 an xs:override is checked for distinct component names
   * and, if it has component children, handed to
   * {@link #_collectOverride}; a childless one is followed like an
   * include. Under 1.0 xs:override is treated like an include.</li>
   * <li>xs:redefine: a redefine of the own document is an error
   * (src-redefine.1 in this code); the redefine edge and the redefined
   * component names are recorded on the compiler and an indirect cycle is
   * rejected by {@link #_checkRedefineCycle}.</li>
   * <li>A path already collected through another directive is not read
   * again: a redefine of it is an error, otherwise only the namespace rules
   * of {@link #_directiveTnsOK} are applied.</li>
   * <li>Otherwise the file is read (unreadable: skipped), parsed, must have
   * xs:schema as root, has its target namespace cached by path and checked by
   * {@link #_directiveTnsOK}. A document whose bytes equal an already
   * collected one is skipped; for a redefine this is a src-redefine.2 error
   * if both redefine arcs name the same component. Else the document is
   * collected recursively (one nesting level deeper), an include, redefine
   * or override passing this document's target namespace for chameleon
   * adoption and an import passing none.</li>
   * </ul>
   *
   * @param aElement
   *        The xs:schema element. Changed in place by the conditional
   *        inclusion pass.
   * @param aBaseDir
   *        Its directory; <code>null</code> if unknown (no location is then
   *        followed).
   * @param sSelfPath
   *        Its path (empty for a document handed to the compiler).
   * @param aOut
   *        Receives every collected document, outermost first.
   * @param aVisited
   *        The visited keys (paths, <code>c:</code>-prefixed contents and the
   *        override keys of {@link #_collectOverride}).
   * @param sAdoptNs
   *        The namespace a chameleon include adopts, empty for none.
   * @throws XsdInvalidException
   *         for a composition error
   */
  private void _collect (@NonNull final IXdmNode aElement,
                         @Nullable final Path aBaseDir,
                         @NonNull final String sSelfPath,
                         @NonNull final ICommonsList <XsdSchemaDocument> aOut,
                         @NonNull final ICommonsSet <String> aVisited,
                         @NonNull final String sAdoptNs)
  {
    if (!stripVersioning (aElement, m_aC.m_eVersion))
    {
      // The document element itself is vc-excluded: the document contributes
      // no components, and its includes/imports are never followed
      return;
    }
    final XsdSchemaDocument aSD = new XsdSchemaDocument (aElement, aBaseDir);
    aSD.m_bElemQualified = XsdNodes.attrIs (aElement, "elementFormDefault", "qualified");
    aSD.m_bAttrQualified = XsdNodes.attrIs (aElement, "attributeFormDefault", "qualified");
    final String sTns = XsdNodes.attr (aElement, "targetNamespace");
    if (sTns != null)
    {
      if (sTns.isEmpty ())
        throw XsdCompiler.invalid ("", "targetNamespace must not be the empty string");
      aSD.m_sTNS = sTns;
    }
    else
      if (!sAdoptNs.isEmpty ())
      {
        // Chameleon: a no-namespace include adopts the including namespace
        aSD.m_sTNS = sAdoptNs;
        aSD.m_bChameleon = true;
      }
    for (final IXdmNode aChild : XsdNodes.children (aElement))
      if (XsdNodes.isXS (aChild, "import"))
        aSD.m_aImports.add (XsdNodes.attrOrEmpty (aChild, "namespace"));
    aSD.m_sFinalDefault = XsdNodes.attrOrEmpty (aElement, "finalDefault");
    aSD.m_sBlockDefault = XsdNodes.attrOrEmpty (aElement, "blockDefault");
    final String sDefaultAttrs = XsdNodes.attr (aElement, "defaultAttributes");
    if (sDefaultAttrs != null && m_aC.isXsd11 ())
    {
      aSD.m_aDefaultAttrs = XsdNodes.resolveQName (aElement, sDefaultAttrs);
      aSD.m_bHasDefaultAttrs = true;
    }
    aOut.add (aSD);

    for (final IXdmNode aChild : XsdNodes.children (aElement))
    {
      if (!XsdNodes.isXSElement (aChild))
        continue;
      final String sKind = XsdNodes.localName (aChild);
      if (!"include".equals (sKind) && !"import".equals (sKind) && !"redefine".equals (sKind) && !"override".equals (sKind))
        continue;
      if ("import".equals (sKind))
      {
        // src-import: an import without namespace needs a target namespace
        final String sNs = XsdNodes.attr (aChild, "namespace");
        if (sNs == null && aSD.m_sTNS.isEmpty ())
          throw XsdCompiler.invalid ("src-import.1.2", "import without a namespace requires a targetNamespace");
        if (sNs != null && sNs.isEmpty ())
          throw XsdCompiler.invalid ("", "xs:import namespace must not be the empty string");
        if (sNs != null && sNs.equals (aSD.m_sTNS))
          throw XsdCompiler.invalid ("src-import.1.1",
                                     "a schema may not import its own target namespace " + XsdLexHelper.quote (sNs));
      }
      final String sLoc = XsdNodes.attr (aChild, "schemaLocation");
      if (sLoc == null && !"import".equals (sKind))
        throw XsdCompiler.invalid ("", "xs:" + sKind + " requires a schemaLocation");
      if (sLoc == null || aBaseDir == null)
      {
        // Import with no location: components simply unavailable
        continue;
      }
      final Path aPath = XsdFileAccess.resolveLocation (aBaseDir, sLoc);
      if (aPath == null)
      {
        // Not a local file: never fetched, like a missing file
        continue;
      }
      final String sPath = aPath.toString ();
      if ("override".equals (sKind) && m_aC.isXsd11 ())
      {
        // src-override: distinct {kind, name} among one override's children
        XsdOverride.checkUniqueness (aChild);
        // An override with component children goes through the 4.2.5
        // transformation; a childless one keeps the include-like path below.
        // 1.0 never sees xs:override semantics
        if (XsdOverride.components (aChild).isNotEmpty ())
        {
          _collectOverride (aChild, aSD, aPath, sLoc, aOut, aVisited);
          continue;
        }
      }
      if ("redefine".equals (sKind))
      {
        if (sPath.equals (sSelfPath))
          throw XsdCompiler.invalid ("src-redefine.1", "xs:redefine targets its own schema document");
        m_aC.m_aRedefineEdges.computeIfAbsent (sSelfPath, k -> new CommonsArrayList <> ()).add (sPath);
        // Remember which components this directive redefines in the target
        m_aC.m_aRedefTargets.computeIfAbsent (sPath, k -> new CommonsHashSet <> ())
                            .addAll (_redefChildNames (aChild));
        _checkRedefineCycle (sSelfPath, sPath);
      }
      final String sCachedTns = m_aC.m_aTnsByPath.get (sPath);
      if (sCachedTns != null)
      {
        // Already collected via another route: the namespace rules still
        // apply, and a redefine of it is circular or conflicting
        if ("redefine".equals (sKind))
          throw XsdCompiler.invalid ("src-redefine.2", "xs:redefine targets an already-collected schema document");
        _directiveTnsOK (aChild, aSD.m_sTNS, sCachedTns);
        continue;
      }
      if (!aVisited.add (sPath))
        continue;
      final byte [] aData = m_aC.m_aFileAccess.readFile (aPath);
      if (aData == null)
      {
        // Unresolvable location: skip
        continue;
      }
      final XsdSchemaSource aSource = XsdSchemaSource.ofBytes (aData, aPath, aPath.getParent ());
      final IXdmNode aSub = XsdNodes.rootElement (XsdCompiler.parseDocument (aSource, "included schema " + sLoc));
      if (!XsdCompiler.isSchemaElement (aSub))
        throw XsdCompiler.invalid ("", "included " + sLoc + " is not a schema");
      final String sSubTns = XsdNodes.attrOrEmpty (aSub, "targetNamespace");
      m_aC.m_aTnsByPath.put (sPath, sSubTns);
      _directiveTnsOK (aChild, aSD.m_sTNS, sSubTns);
      // Dedupe by content too: a cyclic import resolving back to an already
      // collected document must not be collected twice. A redefine resolving
      // back here is an error when both arcs redefine the same component
      final String sContentKey = "c:" + new String (aData, StandardCharsets.ISO_8859_1);
      if (aVisited.contains (sContentKey))
      {
        if ("redefine".equals (sKind))
        {
          final ICommonsSet <String> aOwnTargets = m_aC.m_aRedefTargets.get (sSelfPath);
          if (aOwnTargets != null)
            for (final String sComp : _redefChildNames (aChild))
              if (aOwnTargets.contains (sComp))
                throw XsdCompiler.invalid ("src-redefine.2", "mutual xs:redefine of the same component " + sComp);
        }
        continue;
      }
      aVisited.add (sContentKey);
      // An include/redefine of a no-namespace schema adopts this document's
      // target namespace (chameleon); an import keeps its own
      final String sSubAdopt = "import".equals (sKind) ? "" : aSD.m_sTNS;
      m_aC.enterNesting ();
      try
      {
        _collect (aSub, aPath.getParent (), sPath, aOut, aVisited, sSubAdopt);
      }
      finally
      {
        m_aC.leaveNesting ();
      }
    }
  }

  /**
   * The local name of a QName token of a vc attribute if it is in the XML
   * Schema namespace: the prefix (or, without one, the default namespace) is
   * resolved in scope of the element carrying the attribute.
   *
   * @param aNode
   *        The element carrying the attribute.
   * @param sQName
   *        The QName token.
   * @return The local name, or <code>null</code> if the name is not in the XML
   *         Schema namespace or its prefix is not declared.
   */
  @Nullable
  private static String _xsLocalName (@NonNull final IXdmNode aNode, @NonNull final String sQName)
  {
    final int i = sQName.indexOf (':');
    final String sNS = XsdNodes.lookupPrefix (aNode, i < 0 ? "" : sQName.substring (0, i));
    if (!XsdConstants.NS_XS.equals (sNS))
      return null;
    return sQName.substring (i + 1);
  }

  /**
   * go-xslt <code>vcFacetSupported</code>: whether this processor supports the
   * facet with the expanded name (XSD 1.1 Part 1, 4.2.2: the expanded name of
   * the element applying the facet). The name must be in the XML Schema
   * namespace. The twelve XSD 1.0 facets are supported at both versions,
   * assertion and explicitTimezone only at 1.1, any other name at neither.
   *
   * @param aNode
   *        The element carrying the attribute, for the prefix lookup.
   * @param sQName
   *        The QName token.
   * @param eVersion
   *        The processor version.
   * @return <code>true</code> if the facet is supported.
   */
  static boolean vcFacetSupported (@NonNull final IXdmNode aNode,
                                   @NonNull final String sQName,
                                   @NonNull final EXsdVersion eVersion)
  {
    final String sLocal = _xsLocalName (aNode, sQName);
    if (sLocal == null)
      return false;
    switch (sLocal)
    {
      case "length":
      case "minLength":
      case "maxLength":
      case "pattern":
      case "enumeration":
      case "whiteSpace":
      case "totalDigits":
      case "fractionDigits":
      case "minInclusive":
      case "maxInclusive":
      case "minExclusive":
      case "maxExclusive":
        return true;
      case "assertion":
      case "explicitTimezone":
        return eVersion == EXsdVersion.V11;
      default:
        return false;
    }
  }

  /**
   * go-xslt <code>vcTypeAvailable</code>: whether the type with the expanded
   * name is automatically known at the version (XSD 1.1 Part 1, 4.2.2). Only
   * built-in types in the XML Schema namespace are known. The XSD 1.1-only
   * built-ins and xs:anyAtomicType exist only at 1.1; xs:anyType,
   * xs:anySimpleType, NMTOKENS, IDREFS, ENTITIES and every name known as an
   * atomic type exist at both versions.
   *
   * @param aNode
   *        The element carrying the attribute, for the prefix lookup.
   * @param sQName
   *        The QName token.
   * @param eVersion
   *        The processor version.
   * @return <code>true</code> if the type is available.
   */
  static boolean vcTypeAvailable (@NonNull final IXdmNode aNode,
                                  @NonNull final String sQName,
                                  @NonNull final EXsdVersion eVersion)
  {
    final String sLocal = _xsLocalName (aNode, sQName);
    if (sLocal == null)
      return false;
    if (XsdCompiler.isXsd11OnlyBuiltin (sLocal))
      return eVersion == EXsdVersion.V11;
    switch (sLocal)
    {
      case "anyAtomicType":
        return eVersion == EXsdVersion.V11;
      case "anyType":
      case "anySimpleType":
      case "NMTOKENS":
      case "IDREFS":
      case "ENTITIES":
        return true;
      default:
        return EAtomicType.getFromLocalName (sLocal) != null;
    }
  }

  /**
   * go-xslt <code>stripVersioning</code>: remove, in place, every descendant
   * element excluded by its <code>vc:minVersion</code>/<code>vc:maxVersion</code>
   * (and the facet/type availability attributes) for the processor version.
   * Under 1.1 a lexically invalid vc attribute value is a schema error.
   *
   * @return <code>true</code> if the root element itself is retained.
   * @throws XsdInvalidException
   *         for an invalid vc attribute (1.1)
   */
  static boolean stripVersioning (@NonNull final IXdmNode aRoot, @NonNull final EXsdVersion eVersion)
  {
    final VcState aState = new VcState (eVersion);
    boolean bRootKept = _keepElement (aState, aRoot);
    if (bRootKept)
    {
      // Iterative walk: decide each child element, descend into the kept ones
      final ICommonsList <IXdmNode> aStack = new CommonsArrayList <> ();
      aStack.add (aRoot);
      while (aStack.isNotEmpty ())
      {
        final IXdmNode aCur = aStack.removeLast ();
        final ICommonsList <IXdmNode> aKept = new CommonsArrayList <> ();
        for (final IXdmNode aChild : XsdNodes.children (aCur))
        {
          if (!aChild.isElement ())
            continue;
          if (_keepElement (aState, aChild))
            aKept.add (aChild);
          else
            aCur.removeChild (aChild);
        }
        for (int i = aKept.size () - 1; i >= 0; i--)
          aStack.add (aKept.get (i));
      }
    }
    if (aState.m_aError != null)
      throw aState.m_aError;
    return bRootKept;
  }

  /**
   * go-xslt <code>collect</code>: collect the schema document of the element
   * and, recursively, every document its include/import/redefine/override
   * directives reach, as described at {@link #_collect}.
   *
   * @param aElement
   *        The xs:schema element.
   * @param aBaseDir
   *        Its directory; <code>null</code> if unknown (no location is then
   *        followed).
   * @param sSelfPath
   *        Its path (empty for a document handed to the compiler).
   * @param aOut
   *        Receives every collected document, outermost first.
   * @param aVisited
   *        The visited keys (paths and <code>c:</code>-prefixed contents).
   * @param sAdoptNs
   *        The namespace a chameleon include adopts, empty for none.
   * @throws XsdInvalidException
   *         for a composition error
   */
  void collect (@NonNull final IXdmNode aElement,
                @Nullable final Path aBaseDir,
                @NonNull final String sSelfPath,
                @NonNull final ICommonsList <XsdSchemaDocument> aOut,
                @NonNull final ICommonsSet <String> aVisited,
                @NonNull final String sAdoptNs)
  {
    _collect (aElement, aBaseDir, sSelfPath, aOut, aVisited, sAdoptNs);
  }
}
