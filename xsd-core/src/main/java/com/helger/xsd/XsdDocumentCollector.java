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
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.XdmNode;

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
   * A vc version number as go-xslt reads it (<code>strconv.ParseFloat</code>,
   * minus exponents, hexadecimal and '+').
   *
   * @return The value or <code>null</code> if it is no number.
   */
  @Nullable
  private static Double _parseVcNumber (@NonNull final String s)
  {
    for (final char c : "eExX+".toCharArray ())
      if (s.indexOf (c) >= 0)
        return null;
    if (s.matches ("-?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)"))
      return Double.valueOf (Double.parseDouble (s));
    switch (s.toLowerCase (Locale.ROOT))
    {
      case "inf":
      case "infinity":
        return Double.valueOf (Double.POSITIVE_INFINITY);
      case "-inf":
      case "-infinity":
        return Double.valueOf (Double.NEGATIVE_INFINITY);
      case "nan":
      case "-nan":
        return Double.valueOf (Double.NaN);
      default:
        return null;
    }
  }

  @Nullable
  private static Double _vcDecimal (@NonNull final VcState aState, @NonNull final String sValue, @NonNull final String sAttr)
  {
    final String v = sValue.strip ();
    final Double ret = _parseVcNumber (v);
    if (ret == null && aState.m_eVersion == EXsdVersion.V11)
      aState._setError (XsdCompiler.invalid ("", "invalid vc:" + sAttr + " value " + XsdLexHelper.quote (v)));
    return ret;
  }

  private static void _vcQNames (@NonNull final VcState aState,
                                 @NonNull final XdmNode aNode,
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
  private static boolean _keepElement (@NonNull final VcState aState, @NonNull final XdmNode aNode)
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
        if (!vcFacetSupported (sFacet, aState.m_eVersion))
          return false;
    }
    v = XsdNodes.attrNS (aNode, XsdConstants.NS_VC, "facetUnavailable");
    if (v != null)
    {
      _vcQNames (aState, aNode, v, "facetUnavailable");
      // Retained iff at least one named facet is unsupported
      boolean bAll = true;
      for (final String sFacet : XsdLexHelper.xmlFields (v))
        if (!vcFacetSupported (sFacet, aState.m_eVersion))
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
        if (!vcTypeAvailable (sType, aState.m_eVersion))
          return false;
    }
    v = XsdNodes.attrNS (aNode, XsdConstants.NS_VC, "typeUnavailable");
    if (v != null)
    {
      _vcQNames (aState, aNode, v, "typeUnavailable");
      // Retained iff at least one named type is unavailable
      boolean bAll = true;
      for (final String sType : XsdLexHelper.xmlFields (v))
        if (!vcTypeAvailable (sType, aState.m_eVersion))
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
  private static ICommonsSet <String> _redefChildNames (@NonNull final XdmNode aRedefine)
  {
    final ICommonsSet <String> ret = new CommonsHashSet <> ();
    for (final XdmNode aChild : XsdNodes.children (aRedefine))
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
  private static void _directiveTnsOK (@NonNull final XdmNode aDirective,
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

  private void _collect (@NonNull final XdmNode aElement,
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
    for (final XdmNode aChild : XsdNodes.children (aElement))
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

    for (final XdmNode aChild : XsdNodes.children (aElement))
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
          throw XsdCompiler.invalid ("", "import without a namespace requires a targetNamespace");
        if (sNs != null && sNs.isEmpty ())
          throw XsdCompiler.invalid ("src-import.1.2", "xs:import namespace must not be the empty string");
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
        throw XsdCompiler.unsupported11 ("xs:override");
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
      final XdmNode aSub = XsdNodes.rootElement (XsdCompiler.parseDocument (aSource, "included schema " + sLoc));
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
   * go-xslt <code>vcFacetSupported</code>: whether this processor supports the
   * named constraining facet at the version.
   */
  static boolean vcFacetSupported (@NonNull final String sQName, @NonNull final EXsdVersion eVersion)
  {
    final String sLocal = sQName.substring (sQName.lastIndexOf (':') + 1);
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
   * go-xslt <code>vcTypeAvailable</code>: whether the named built-in type exists
   * at the version.
   */
  static boolean vcTypeAvailable (@NonNull final String sQName, @NonNull final EXsdVersion eVersion)
  {
    final String sLocal = sQName.substring (sQName.lastIndexOf (':') + 1);
    if (XsdCompiler.isXsd11OnlyBuiltin (sLocal))
      return eVersion == EXsdVersion.V11;
    switch (sLocal)
    {
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
  static boolean stripVersioning (@NonNull final XdmNode aRoot, @NonNull final EXsdVersion eVersion)
  {
    final VcState aState = new VcState (eVersion);
    boolean bRootKept = _keepElement (aState, aRoot);
    if (bRootKept)
    {
      // Iterative walk: decide each child element, descend into the kept ones
      final ICommonsList <XdmNode> aStack = new CommonsArrayList <> ();
      aStack.add (aRoot);
      while (aStack.isNotEmpty ())
      {
        final XdmNode aCur = aStack.removeLast ();
        final ICommonsList <XdmNode> aKept = new CommonsArrayList <> ();
        for (final XdmNode aChild : XsdNodes.children (aCur))
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
   * go-xslt <code>collect</code>.
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
  void collect (@NonNull final XdmNode aElement,
                @Nullable final Path aBaseDir,
                @NonNull final String sSelfPath,
                @NonNull final ICommonsList <XsdSchemaDocument> aOut,
                @NonNull final ICommonsSet <String> aVisited,
                @NonNull final String sAdoptNs)
  {
    _collect (aElement, aBaseDir, sSelfPath, aOut, aVisited, sAdoptNs);
  }
}
