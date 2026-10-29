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

import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;

/**
 * The schema loader (go-xslt <code>loader.go</code> and the
 * <code>compiler</code> struct): parses schema documents, resolves the
 * include/import/redefine graph, registers and compiles the components in
 * dependency order, and runs the whole-schema checks. One instance compiles
 * one schema set.
 * <p>
 * The work is split over helper classes that share this compiler's state:
 * {@link XsdDocumentCollector} (<code>imports.go</code>),
 * {@link XsdSimpleTypeParser} (simple types and facets),
 * {@link XsdComponentParser} (complex types, particles, attributes -
 * <code>structure.go</code>), {@link XsdComponentChecks} (the whole-schema
 * checks of <code>structure.go</code>), {@link XsdIdentityCompiler} (the
 * compile-time half of <code>identity.go</code>) and
 * {@link XsdSchemaForSchemas} (<code>schemavalid.go</code>).
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class XsdCompiler
{
  /** The schema document an empty schema set compiles from. */
  static final String EMPTY_SCHEMA_DOC = "<xs:schema xmlns:xs=\"" + XsdConstants.NS_XS + "\"/>";

  /**
   * The maximum nesting of schema components (particles, local declarations,
   * inline types) and of dependency walks. go-xslt relies on its growable
   * stack; the bound keeps a crafted schema from overflowing the Java stack.
   */
  static final int MAX_NESTING = 1000;

  /** XSD 1.1 built-in datatypes; naming one in a 1.0 schema is a reference error. */
  private static final ICommonsSet <String> XSD11_ONLY_BUILTIN = new CommonsHashSet <> ("dateTimeStamp",
                                                                                        "dayTimeDuration",
                                                                                        "yearMonthDuration",
                                                                                        "precisionDecimal",
                                                                                        "error");

  /** A pending top-level component: its declaration plus the document it came from. */
  record Work (@NonNull XsdSchemaDocument sd, @NonNull IXdmNode node)
  {}

  /**
   * A simple type derivation dependency. A hard edge (restriction base, list
   * item type) closing a cycle is a circular derivation; a union member edge
   * is soft.
   */
  private record StDep (@NonNull XsdName name, boolean hard)
  {}

  final EXsdVersion m_eVersion;
  final boolean m_bKeepDocs;
  final XsdFileAccess m_aFileAccess;
  XsdSchema m_aSchema;

  String m_sTNS = "";
  /** The adopted namespace when the current document is a chameleon include, empty otherwise. */
  String m_sChameleonNS = "";
  boolean m_bElementQualified;
  boolean m_bAttributeQualified;
  String m_sFinalDefault = "";
  String m_sBlockDefault = "";
  ICommonsSet <String> m_aImports = new CommonsHashSet <> ();
  /** The document currently being compiled. */
  XsdSchemaDocument m_aCurDoc;
  /** Every identity constraint, for the cross-checks. */
  final ICommonsList <XsdIdentityConstraint> m_aAllICs = new CommonsArrayList <> ();
  final ICommonsOrderedMap <XsdName, XsdModelGroup> m_aGroups = new CommonsLinkedHashMap <> ();
  final ICommonsOrderedMap <XsdName, XsdAttributeGroup> m_aAttrGroups = new CommonsLinkedHashMap <> ();
  /**
   * The pre-redefine components: a reference to a redefined name from inside
   * the xs:redefine element denotes the original.
   */
  final ICommonsMap <XsdName, IXsdType> m_aRedefineType = new CommonsHashMap <> ();
  final ICommonsMap <XsdName, XsdModelGroup> m_aRedefineGroup = new CommonsHashMap <> ();
  final ICommonsMap <XsdName, XsdAttributeGroup> m_aRedefineAttrGroup = new CommonsHashMap <> ();
  /** The local names of every declared xs:notation. */
  final ICommonsSet <String> m_aNotationLocals = new CommonsHashSet <> ();
  /** Each schema document's targetNamespace by resolved path. */
  final ICommonsMap <String, String> m_aTnsByPath = new CommonsHashMap <> ();
  /** Per redefined document path, the "kind name" identities redefined into it. */
  final ICommonsMap <String, ICommonsSet <String>> m_aRedefTargets = new CommonsHashMap <> ();
  /** XSD 1.1 identity-constraint @ref placeholders awaiting resolution. */
  final ICommonsList <XsdIdentityConstraint> m_aICRefs = new CommonsArrayList <> ();
  /** Which document redefines which (by path), for src-redefine.2. */
  final ICommonsMap <String, ICommonsList <String>> m_aRedefineEdges = new CommonsHashMap <> ();
  /** Every complex type as it is parsed, named and anonymous. */
  final ICommonsList <XsdComplexType> m_aAllCTs = new CommonsArrayList <> ();

  final XsdDocumentCollector m_aCollector;
  final XsdSimpleTypeParser m_aSimpleTypes;
  final XsdComponentParser m_aComponents;
  final XsdComponentChecks m_aChecks;
  final XsdIdentityCompiler m_aIdentity;

  private int m_nNesting;

  XsdCompiler (@NonNull final EXsdVersion eVersion, @NonNull final XsdCompileOptions aOptions)
  {
    m_eVersion = eVersion;
    m_bKeepDocs = aOptions.isKeepDocumentation ();
    m_aFileAccess = aOptions.getFileAccess ();
    m_aCollector = new XsdDocumentCollector (this);
    m_aSimpleTypes = new XsdSimpleTypeParser (this);
    m_aComponents = new XsdComponentParser (this);
    m_aChecks = new XsdComponentChecks (this);
    m_aIdentity = new XsdIdentityCompiler (this);
  }

  /**
   * go-xslt <code>seedXSIAttributes</code>: the four attributes of the XSI
   * namespace every schema implicitly contains.
   */
  private static void _seedXSIAttributes (@NonNull final XsdSchema aSchema)
  {
    final XsdSimpleType aUriList = new XsdSimpleType ();
    aUriList.m_eVariety = EXsdVariety.LIST;
    aUriList.m_aItem = XsdSimpleType.builtin (EAtomicType.ANY_URI);
    final ICommonsOrderedMap <String, XsdSimpleType> aAttrs = new CommonsLinkedHashMap <> ();
    aAttrs.put ("type", XsdSimpleType.builtin (EAtomicType.QNAME));
    aAttrs.put ("nil", XsdSimpleType.builtin (EAtomicType.BOOLEAN));
    aAttrs.put ("schemaLocation", aUriList);
    aAttrs.put ("noNamespaceSchemaLocation", XsdSimpleType.builtin (EAtomicType.ANY_URI));
    for (final var aEntry : aAttrs.entrySet ())
    {
      final XsdName n = new XsdName (XsdConstants.NS_XSI, aEntry.getKey ());
      aSchema.m_aAttributes.put (n, new XsdAttributeDecl (n, aEntry.getValue ()));
    }
  }

  private void _parseGlobalElement (@NonNull final IXdmNode aNode)
  {
    final String sName = XsdNodes.declName (aNode);
    if (sName.isEmpty ())
      throw new XsdUnsupportedException ("global element without a name");
    final XsdName aName = new XsdName (m_sTNS, sName);
    XsdElementDecl aDecl = m_aSchema.m_aElements.get (aName);
    if (aDecl == null)
    {
      aDecl = new XsdElementDecl (aName);
      m_aSchema.m_aElements.put (aName, aDecl);
    }
    fillElementCommon (aDecl, aNode);
    checkValueConstraint (aDecl.m_aType, aNode);
    final String sSG = XsdNodes.attr (aNode, "substitutionGroup");
    if (sSG != null)
    {
      aDecl.m_aSubstGroups = new CommonsArrayList <> ();
      if (isXsd10 ())
      {
        // 1.0: the attribute is a single QName
        aDecl.m_aSubstGroups.add (XsdNodes.resolveQName (aNode, sSG));
      }
      else
      {
        // 1.1: xs:QNameList - the element joins every named group
        for (final String sToken : XsdLexHelper.xmlFields (sSG))
          aDecl.m_aSubstGroups.add (XsdNodes.resolveQName (aNode, sToken));
      }
    }
    aDecl.m_bAbstract = XsdNodes.attrIs (aNode, "abstract", "true");
    aDecl.m_aConstraints = m_aIdentity.parseIdentityConstraints (aNode);
    aDecl.m_aAlternatives = m_aComponents.parseAlternatives (aNode);
  }

  private void _parseGlobalAttribute (@NonNull final IXdmNode aNode)
  {
    final String sName = XsdNodes.declName (aNode);
    if (sName.isEmpty ())
      throw new XsdUnsupportedException ("global attribute without a name");
    final XsdName aName = new XsdName (m_sTNS, sName);
    XsdAttributeDecl aDecl = m_aSchema.m_aAttributes.get (aName);
    if (aDecl == null)
    {
      aDecl = new XsdAttributeDecl (aName);
      m_aSchema.m_aAttributes.put (aName, aDecl);
    }
    final XsdSimpleType aST = attributeType (aNode);
    aDecl.m_aType = aST;
    aDecl.m_aDocs = docsOf (aNode);
    aDecl.m_aMsgs = XsdMessages.messagesOf (aNode);
    aDecl.m_bInheritable = XsdNodes.boolAttrTrue (aNode, "inheritable");
    final String sDefault = XsdNodes.attr (aNode, "default");
    if (sDefault != null)
    {
      aDecl.m_sDefault = sDefault;
      aDecl.m_bHasDefault = true;
    }
    final String sFixed = XsdNodes.attr (aNode, "fixed");
    if (sFixed != null)
    {
      aDecl.m_sFixed = sFixed;
      aDecl.m_bHasFixed = true;
    }
    checkValueConstraint (aST, aNode);
  }

  /**
   * The same-kind references to a name below a node, in document order
   * (iterative walk).
   */
  @NonNull
  private static ICommonsList <IXdmNode> _collectSelfRefs (@NonNull final IXdmNode aNode,
                                                          @NonNull final String sKind,
                                                          @NonNull final XsdName aSelf,
                                                          final boolean bFirstOnly)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    final ICommonsList <IXdmNode> aStack = new CommonsArrayList <> ();
    final ICommonsList <IXdmNode> aChildren = XsdNodes.children (aNode);
    for (int i = aChildren.size () - 1; i >= 0; i--)
      aStack.add (aChildren.get (i));
    while (aStack.isNotEmpty ())
    {
      final IXdmNode aCur = aStack.removeLast ();
      if (!aCur.isElement ())
        continue;
      if (XsdNodes.isXS (aCur, sKind))
      {
        final String sRef = XsdNodes.attr (aCur, "ref");
        if (sRef != null && XsdNodes.resolveQName (aCur, sRef).equals (aSelf))
        {
          ret.add (aCur);
          if (bFirstOnly)
            return ret;
        }
      }
      final ICommonsList <IXdmNode> aKids = XsdNodes.children (aCur);
      for (int i = aKids.size () - 1; i >= 0; i--)
        aStack.add (aKids.get (i));
    }
    return ret;
  }

  /**
   * go-xslt <code>redefineHasSelfRef</code>: whether the redefinition contains a
   * same-kind reference to the name being redefined.
   */
  private static boolean _redefineHasSelfRef (@NonNull final IXdmNode aNode,
                                              @NonNull final String sKind,
                                              @NonNull final XsdName aSelf)
  {
    return _collectSelfRefs (aNode, sKind, aSelf, true).isNotEmpty ();
  }

  /**
   * go-xslt <code>checkRedefineSyntax</code>: no name may be redefined twice
   * in the same xs:redefine, and a redefined group/attributeGroup may refer to
   * itself at most once, with minOccurs = maxOccurs = 1.
   */
  private void _checkRedefineSyntax (@NonNull final ICommonsList <Work> aRedefs)
  {
    final ICommonsMap <IXdmNode, ICommonsSet <String>> aSeen = new CommonsHashMap <> ();
    for (final Work w : aRedefs)
    {
      final String sName = XsdNodes.declName (w.node ());
      final String sKind = XsdNodes.localName (w.node ());
      final ICommonsSet <String> aPerParent = aSeen.computeIfAbsent (w.node ().getParent (),
                                                                    k -> new CommonsHashSet <> ());
      if (!aPerParent.add (sKind + " " + sName))
        throw invalid ("src-redefine.6.1.1",
                       sKind + " " + XsdLexHelper.quote (sName) + " is redefined twice in one xs:redefine");
      if (!"group".equals (sKind) && !"attributeGroup".equals (sKind))
        continue;
      final XsdName aSelf = new XsdName (w.sd ().m_sTNS, sName);
      final ICommonsList <IXdmNode> aRefs = _collectSelfRefs (w.node (), sKind, aSelf, false);
      if (aRefs.size () > 1)
        throw invalid ("src-redefine.6.1.1",
                       "redefined " + sKind + " " + XsdLexHelper.quote (sName) + " refers to itself more than once");
      for (final IXdmNode aRef : aRefs)
      {
        final String sMin = XsdNodes.attr (aRef, "minOccurs");
        final String sMax = XsdNodes.attr (aRef, "maxOccurs");
        if (sMin != null && !"1".equals (sMin) || sMax != null && !"1".equals (sMax))
          throw invalid ("src-redefine.6.1.2",
                         "the self-reference in redefined " +
                                               sKind +
                                               " " +
                                               XsdLexHelper.quote (sName) +
                                               " must have minOccurs = maxOccurs = 1");
      }
    }
  }

  /**
   * go-xslt <code>attrGroupRestrictsOK</code>: the restriction reading of
   * src-redefine.7.2.2 - the redefined attributeGroup may drop nothing
   * required, add nothing new, and must preserve fixed values.
   */
  private static void _attrGroupRestrictsOK (@NonNull final XsdAttributeGroup aNew,
                                             @NonNull final XsdAttributeGroup aOrig,
                                             @NonNull final XsdName aName)
  {
    final ICommonsMap <XsdName, XsdAttributeUse> aByName = new CommonsHashMap <> ();
    for (final XsdAttributeUse aUse : aOrig.m_aUses)
      aByName.put (aUse.m_aDecl.m_aName, aUse);
    final ICommonsSet <XsdName> aNewNames = new CommonsHashSet <> ();
    for (final XsdAttributeUse aUse : aNew.m_aUses)
      aNewNames.add (aUse.m_aDecl.m_aName);
    for (final XsdAttributeUse aOU : aOrig.m_aUses)
      if (aOU.m_bRequired && !aNewNames.contains (aOU.m_aDecl.m_aName))
        throw invalid ("src-redefine.7.2.2",
                       "redefined attributeGroup " + aName + " drops required attribute " + aOU.m_aDecl.m_aName);
    for (final XsdAttributeUse aUse : aNew.m_aUses)
    {
      final XsdAttributeUse aOU = aByName.get (aUse.m_aDecl.m_aName);
      if (aOU == null)
      {
        if (aOrig.m_aWildcard == null)
          throw invalid ("src-redefine.7.2.2",
                         "redefined attributeGroup " + aName + " adds attribute " + aUse.m_aDecl.m_aName);
        continue;
      }
      if (aOU.m_bRequired && !aUse.m_bRequired)
        throw invalid ("src-redefine.7.2.2",
                       "redefined attributeGroup " + aName + " weakens required attribute " + aUse.m_aDecl.m_aName);
      if (aOU.m_aDecl.m_aType != null &&
          aUse.m_aDecl.m_aType != null &&
          !XsdComponentChecks.typeRestrictsFrom (aUse.m_aDecl.m_aType, aOU.m_aDecl.m_aType))
        throw invalid ("src-redefine.7.2.2",
                       "redefined attributeGroup " +
                                             aName +
                                             " changes the type of " +
                                             aUse.m_aDecl.m_aName +
                                             " to one not derived from the original");
      if (aOU.m_bHasFixed)
        if (aUse.m_bHasDefault || !aUse.m_bHasFixed || !XsdLexHelper.lexEqualCollapsed (aUse.m_sFixed, aOU.m_sFixed))
          throw invalid ("src-redefine.7.2.2",
                         "redefined attributeGroup " +
                                               aName +
                                               " does not preserve the fixed value of " +
                                               aUse.m_aDecl.m_aName);
    }
  }

  /**
   * go-xslt <code>redefineSelfDerives</code>: a redefined simpleType must be a
   * restriction of the type it redefines, a redefined complexType must derive
   * from itself.
   */
  private boolean _redefineSelfDerives (@NonNull final IXdmNode aNode, @NonNull final XsdName aName)
  {
    IXdmNode aDeriv = null;
    if ("simpleType".equals (XsdNodes.localName (aNode)))
      aDeriv = XsdNodes.firstXSChild (aNode, "restriction");
    else
      for (final String sModel : new String [] { "complexContent", "simpleContent" })
      {
        final IXdmNode aModel = XsdNodes.firstXSChild (aNode, sModel);
        if (aModel != null)
        {
          aDeriv = XsdNodes.firstXSChild (aModel, "restriction");
          if (aDeriv == null)
            aDeriv = XsdNodes.firstXSChild (aModel, "extension");
          break;
        }
      }
    if (aDeriv == null)
      return false;
    final String sBase = XsdNodes.attr (aDeriv, "base");
    if (sBase == null)
      return false;
    final XsdName aBaseName = XsdNodes.resolveQName (aDeriv, sBase);
    if (aBaseName.equals (aName))
      return true;
    final XsdName aAlt = chamAlt (aBaseName);
    return aAlt != null && aAlt.equals (aName);
  }

  /**
   * go-xslt <code>applyRedefinitions</code>: install xs:redefine
   * redefinitions over the compiled originals, innermost first; each original
   * is snapshotted at application time, then the redefinition is compiled into
   * the original component in place, so existing references see it.
   */
  private void _applyRedefinitions (@NonNull final ICommonsList <Work> aRedefs)
  {
    _checkRedefineSyntax (aRedefs);
    final ICommonsList <Work> aApplied = new CommonsArrayList <> (aRedefs);
    aApplied.reverse ();
    for (final Work w : aApplied)
    {
      final String sName = XsdNodes.declName (w.node ());
      final XsdName k = new XsdName (w.sd ().m_sTNS, sName);
      final String sKind = XsdNodes.localName (w.node ());
      boolean bFound = false;
      switch (sKind)
      {
        case "simpleType":
        case "complexType":
        {
          final IXsdType aOrig = m_aSchema.m_aTypes.get (k);
          if (aOrig instanceof final XsdComplexType aCT)
          {
            m_aRedefineType.put (k, aCT.getShallowCopy ());
            bFound = true;
          }
          else
            if (aOrig instanceof final XsdSimpleType aST)
            {
              m_aRedefineType.put (k, aST.getShallowCopy ());
              bFound = true;
            }
          break;
        }
        case "group":
        {
          final XsdModelGroup aOrig = m_aGroups.get (k);
          if (aOrig != null)
          {
            m_aRedefineGroup.put (k, aOrig.getShallowCopy ());
            bFound = true;
          }
          break;
        }
        case "attributeGroup":
        {
          final XsdAttributeGroup aOrig = m_aAttrGroups.get (k);
          if (aOrig != null)
          {
            m_aRedefineAttrGroup.put (k, aOrig.getShallowCopy ());
            bFound = true;
          }
          break;
        }
        default:
          break;
      }
      if (!bFound)
        throw invalid ("src-redefine", "redefined " + sKind + " " + k + " is not present in the redefined schema");
      setDoc (w.sd ());
      switch (sKind)
      {
        case "simpleType":
        {
          if (!_redefineSelfDerives (w.node (), k))
            throw invalid ("src-redefine.5", "redefined simpleType " + k + " must restrict itself");
          if (!(m_aSchema.m_aTypes.get (k) instanceof final XsdSimpleType aST))
            throw invalid ("src-redefine.5", "redefined simpleType " + k + " redefines a complex type");
          aST.reset (k);
          m_aSimpleTypes.parseSimpleTypeInto (aST, w.node ());
          break;
        }
        case "complexType":
        {
          if (!_redefineSelfDerives (w.node (), k))
            throw invalid ("src-redefine.6", "redefined complexType " + k + " must derive from itself");
          if (!(m_aSchema.m_aTypes.get (k) instanceof final XsdComplexType aCT))
            throw invalid ("src-redefine.6", "redefined complexType " + k + " redefines a simple type");
          aCT.reset (k);
          m_aComponents.parseComplexTypeInto (aCT, w.node ());
          break;
        }
        case "group":
        {
          final XsdModelGroup aGroup = m_aComponents.parseGroupBody (w.node ());
          // src-redefine.6.2.2: with no self-reference the redefinition must be
          // a valid restriction of the original
          if (!_redefineHasSelfRef (w.node (), "group", k))
          {
            final XsdRestrictionChecker aRC = new XsdRestrictionChecker (m_aSchema);
            final XsdParticle aRP = new XsdParticle (1, 1, aGroup);
            final XsdParticle aBP = new XsdParticle (1, 1, m_aRedefineGroup.get (k));
            if (!aRC.particleRestrictsOK (aRP, aBP))
              throw invalid ("src-redefine.6.2.2",
                             "redefined group " + k + " is not a valid restriction of the original");
          }
          m_aGroups.get (k).assignFrom (aGroup);
          break;
        }
        case "attributeGroup":
        {
          final XsdAttributeGroup aAG = m_aComponents.parseAttrGroupBody (w.node ());
          // src-redefine.7.2.2: with no self-reference the uses must be a valid
          // restriction of the original's
          if (!_redefineHasSelfRef (w.node (), "attributeGroup", k))
            _attrGroupRestrictsOK (aAG, m_aRedefineAttrGroup.get (k), k);
          m_aAttrGroups.get (k).assignFrom (aAG);
          break;
        }
        default:
          break;
      }
    }
  }

  /** go-xslt <code>checkSubstGroupCycles</code>. */
  private void _checkSubstGroupCycles ()
  {
    for (final XsdElementDecl aStart : m_aSchema.m_aElements.values ())
    {
      // Iterative DFS over the affiliation graph; a name on the current path
      // reached again is a cycle
      final ICommonsSet <XsdName> aOnPath = new CommonsHashSet <> ();
      final ICommonsList <XsdElementDecl> aPathDecls = new CommonsArrayList <> ();
      final ICommonsList <Integer> aPathIndex = new CommonsArrayList <> ();
      aOnPath.add (aStart.m_aName);
      aPathDecls.add (aStart);
      aPathIndex.add (Integer.valueOf (0));
      while (aPathDecls.isNotEmpty ())
      {
        final int nTop = aPathDecls.size () - 1;
        final XsdElementDecl aDecl = aPathDecls.get (nTop);
        final int nIndex = aPathIndex.get (nTop).intValue ();
        if (nIndex >= aDecl.m_aSubstGroups.size ())
        {
          aOnPath.remove (aDecl.m_aName);
          aPathDecls.removeLast ();
          aPathIndex.removeLast ();
          continue;
        }
        aPathIndex.set (nTop, Integer.valueOf (nIndex + 1));
        final XsdElementDecl aHead = m_aSchema.m_aElements.get (aDecl.m_aSubstGroups.get (nIndex));
        if (aHead == null)
          continue;
        if (aOnPath.contains (aHead.m_aName))
          throw invalid ("", "cyclic substitution group");
        aOnPath.add (aHead.m_aName);
        aPathDecls.add (aHead);
        aPathIndex.add (Integer.valueOf (0));
      }
    }
  }

  /** go-xslt <code>buildSubstitutionGroups</code>: index each head to its direct members. */
  private void _buildSubstitutionGroups ()
  {
    for (final XsdElementDecl aDecl : m_aSchema.m_aElements.values ())
      for (final XsdName aHead : aDecl.m_aSubstGroups)
        m_aSchema.m_aSubstMembers.computeIfAbsent (aHead, k -> new CommonsArrayList <> ()).add (aDecl);
  }

  /**
   * go-xslt <code>inheritSubstGroupTypes</code>: an element declared with
   * neither type nor inline type takes the type of its (first)
   * substitution-group head.
   */
  private void _inheritSubstGroupTypes ()
  {
    for (final XsdElementDecl aDecl : m_aSchema.m_aElements.values ())
    {
      if (!aDecl.m_bTypeDefaulted || aDecl.m_aSubstGroups.isEmpty ())
        continue;
      final ICommonsSet <XsdName> aSeen = new CommonsHashSet <> ();
      XsdElementDecl aCur = aDecl;
      while (aCur.m_aSubstGroups.isNotEmpty () && !aSeen.contains (aCur.m_aName))
      {
        aSeen.add (aCur.m_aName);
        final XsdElementDecl aHead = m_aSchema.m_aElements.get (aCur.m_aSubstGroups.getFirstOrNull ());
        if (aHead == null)
          break;
        if (!aHead.m_bTypeDefaulted || aHead.m_aSubstGroups.isEmpty ())
        {
          if (aHead.m_aType != null)
            aDecl.m_aType = aHead.m_aType;
          break;
        }
        aCur = aHead;
      }
    }
  }

  /**
   * go-xslt <code>checkTypeCycles</code>: a type may not take part in its own
   * derivation chain.
   */
  private void _checkTypeCycles ()
  {
    for (final var aEntry : m_aSchema.m_aTypes.entrySet ())
    {
      final ICommonsSet <IXsdType> aSeen = new CommonsHashSet <> ();
      IXsdType aCur = aEntry.getValue ();
      for (int n = 0; aCur != null && n < 256; n++)
      {
        if (!aSeen.add (aCur))
          throw invalid ("ct-props-correct.3", "circular type derivation involving " + aEntry.getKey ());
        if (aCur instanceof final XsdComplexType aCT)
          aCur = aCT.m_aBaseType;
        else
          if (aCur instanceof final XsdSimpleType aST)
            aCur = aST.m_aBase;
          else
            aCur = null;
      }
    }
  }

  /**
   * go-xslt <code>checkIDAttributeUses</code>: ct-props-correct.5 (XSD 1.0
   * only) - no two attribute uses whose types are or derive from xs:ID.
   */
  private void _checkIDAttributeUses ()
  {
    if (!isXsd10 ())
      return;
    for (final XsdComplexType aCT : m_aAllCTs)
    {
      int n = 0;
      for (final XsdAttributeUse aUse : m_aSchema.effectiveAttrs (aCT).uses ())
      {
        if (aUse.m_bProhibited || aUse.m_aDecl.m_aType == null)
          continue;
        if (aUse.m_aDecl.m_aType.resolvePrim () == EAtomicType.ID)
        {
          n++;
          if (n > 1)
            throw invalid ("ct-props-correct.5", "complex type " + aCT.m_aName + " has more than one ID-typed attribute");
        }
      }
    }
  }

  /**
   * go-xslt <code>simpleTypeDeps</code>: the named simple types a definition
   * derives from (restriction base, list item type, union member types).
   */
  @NonNull
  private static ICommonsList <StDep> _simpleTypeDeps (@NonNull final IXdmNode aNode)
  {
    final ICommonsList <StDep> ret = new CommonsArrayList <> ();
    for (final IXdmNode aChild : XsdNodes.children (aNode))
    {
      String sRef = null;
      boolean bHard = true;
      if (XsdNodes.isXS (aChild, "restriction"))
        sRef = XsdNodes.attr (aChild, "base");
      else
        if (XsdNodes.isXS (aChild, "list"))
          sRef = XsdNodes.attr (aChild, "itemType");
        else
          if (XsdNodes.isXS (aChild, "union"))
          {
            sRef = XsdNodes.attr (aChild, "memberTypes");
            bHard = false;
          }
      if (sRef != null)
        for (final String sQ : XsdLexHelper.xmlFields (sRef))
          ret.add (new StDep (XsdNodes.resolveQName (aChild, sQ), bHard));
    }
    return ret;
  }

  private void _visitSimpleType (@NonNull final XsdName aName,
                                 @NonNull final ICommonsMap <XsdName, Work> aByName,
                                 @NonNull final ICommonsMap <XsdName, Integer> aVisited,
                                 @NonNull final ICommonsList <XsdName> aStack,
                                 @NonNull final ICommonsList <Boolean> aEdgeHard,
                                 @NonNull final ICommonsList <Work> aOrder,
                                 final boolean [] aHardCycle)
  {
    final Integer aState = aVisited.get (aName);
    if (aState != null && aState.intValue () == 1)
    {
      // Back edge to a type on the current path: a circular derivation. In
      // 1.1 every cycle is invalid; in 1.0 only if some edge around the loop
      // is hard (restriction/list)
      if (isXsd11 ())
      {
        aHardCycle[0] = true;
        return;
      }
      boolean bHardInLoop = aEdgeHard.getLastOrNull ().booleanValue ();
      for (int i = aStack.size () - 1; i >= 0 && !bHardInLoop; i--)
      {
        if (aStack.get (i).equals (aName))
          break;
        bHardInLoop = aEdgeHard.get (i - 1).booleanValue ();
      }
      if (bHardInLoop)
        aHardCycle[0] = true;
      return;
    }
    if (aState != null && aState.intValue () == 2)
      return;
    enterNesting ();
    try
    {
      aVisited.put (aName, Integer.valueOf (1));
      aStack.add (aName);
      final Work w = aByName.get (aName);
      if (w != null)
      {
        for (final StDep aDep : _simpleTypeDeps (w.node ()))
        {
          aEdgeHard.add (Boolean.valueOf (aDep.hard ()));
          _visitSimpleType (aDep.name (), aByName, aVisited, aStack, aEdgeHard, aOrder, aHardCycle);
          aEdgeHard.removeLast ();
        }
        aOrder.add (w);
      }
      aStack.removeLast ();
      aVisited.put (aName, Integer.valueOf (2));
    }
    finally
    {
      leaveNesting ();
    }
  }

  private void _visitAttrGroup (@NonNull final XsdName aName,
                                @NonNull final ICommonsMap <XsdName, Work> aByName,
                                @NonNull final ICommonsSet <XsdName> aVisited,
                                @NonNull final ICommonsList <Work> aOrder)
  {
    if (!aVisited.add (aName))
      return;
    enterNesting ();
    try
    {
      final Work w = aByName.get (aName);
      if (w != null)
      {
        for (final IXdmNode aChild : XsdNodes.children (w.node ()))
          if (XsdNodes.isXS (aChild, "attributeGroup"))
          {
            final String sRef = XsdNodes.attr (aChild, "ref");
            if (sRef != null)
              _visitAttrGroup (XsdNodes.resolveQName (aChild, sRef), aByName, aVisited, aOrder);
          }
        aOrder.add (w);
      }
    }
    finally
    {
      leaveNesting ();
    }
  }

  @Nullable
  private static XsdName _complexTypeBase (@NonNull final IXdmNode aNode)
  {
    for (final String sKind : new String [] { "simpleContent", "complexContent" })
    {
      final IXdmNode aContent = XsdNodes.firstXSChild (aNode, sKind);
      if (aContent != null)
        for (final String sDeriv : new String [] { "restriction", "extension" })
        {
          final IXdmNode aDeriv = XsdNodes.firstXSChild (aContent, sDeriv);
          if (aDeriv != null)
          {
            final String sBase = XsdNodes.attr (aDeriv, "base");
            if (sBase != null)
              return XsdNodes.resolveQName (aDeriv, sBase);
          }
        }
    }
    return null;
  }

  private void _emitComplexType (final int nIndex,
                                 @NonNull final ICommonsList <Work> aComplexW,
                                 @NonNull final ICommonsMap <XsdName, Integer> aIndex,
                                 final boolean [] aEmitted,
                                 @NonNull final ICommonsList <Work> aOrder)
  {
    if (aEmitted[nIndex])
      return;
    aEmitted[nIndex] = true;
    enterNesting ();
    try
    {
      final XsdName aBase = _complexTypeBase (aComplexW.get (nIndex).node ());
      if (aBase != null)
      {
        final Integer aJ = aIndex.get (aBase);
        if (aJ != null && aJ.intValue () != nIndex)
          _emitComplexType (aJ.intValue (), aComplexW, aIndex, aEmitted, aOrder);
      }
      aOrder.add (aComplexW.get (nIndex));
    }
    finally
    {
      leaveNesting ();
    }
  }

  @NonNull
  private XsdName _named (@NonNull final XsdSchemaDocument aSD, @NonNull final IXdmNode aChild)
  {
    final String sName = XsdNodes.declName (aChild);
    if (!XsdLexHelper.isNCName (sName))
      throw invalid ("", "invalid xs:" + XsdNodes.localName (aChild) + " name " + XsdLexHelper.quote (sName));
    return new XsdName (aSD.m_sTNS, sName);
  }

  boolean isXsd10 ()
  {
    return m_eVersion == EXsdVersion.V10;
  }

  boolean isXsd11 ()
  {
    return m_eVersion == EXsdVersion.V11;
  }

  /**
   * @param sWhat
   *        The XSD 1.1 feature.
   * @return The exception to throw where an XSD 1.1 branch is not ported yet
   *         (phase V8).
   */
  @NonNull
  static XsdUnsupportedException unsupported11 (@NonNull final String sWhat)
  {
    return new XsdUnsupportedException ("XSD 1.1 " + sWhat + " is not implemented yet (phase V8)");
  }

  /** @return A new invalid-verdict exception. */
  @NonNull
  static XsdInvalidException invalid (@NonNull final String sCode, @NonNull final String sMessage)
  {
    return XsdInvalidException.invalid (sCode, sMessage);
  }

  /**
   * Enter one level of component nesting.
   *
   * @throws XsdUnsupportedException
   *         beyond {@link #MAX_NESTING}
   */
  void enterNesting ()
  {
    if (++m_nNesting > MAX_NESTING)
    {
      m_nNesting--;
      throw new XsdUnsupportedException ("schema components are nested deeper than " + MAX_NESTING + " levels");
    }
  }

  void leaveNesting ()
  {
    m_nNesting--;
  }

  /**
   * Parse a schema document.
   *
   * @return The document node.
   * @throws XsdInvalidException
   *         if it is not well-formed
   */
  @NonNull
  static IXdmNode parseDocument (@NonNull final XsdSchemaSource aSource, @NonNull final String sWhat)
  {
    try
    {
      return new XdmParser ().parse (aSource.createInputSource ());
    }
    catch (final XdmParseException ex)
    {
      throw invalid ("", sWhat + " not well-formed: " + ex.getMessage ());
    }
  }

  /** @return <code>true</code> if the element is xs:schema. */
  static boolean isSchemaElement (@Nullable final IXdmNode aElement)
  {
    return XsdNodes.isXS (aElement, "schema");
  }

  /**
   * go-xslt <code>docsOf</code>: the xs:annotation/xs:documentation children of
   * a component's element; empty unless documentation is kept.
   */
  @NonNull
  ICommonsList <XsdDocumentation> docsOf (@Nullable final IXdmNode aNode)
  {
    final ICommonsList <XsdDocumentation> ret = new CommonsArrayList <> ();
    if (!m_bKeepDocs || aNode == null)
      return ret;
    for (final IXdmNode aAnn : XsdNodes.children (aNode))
    {
      if (!XsdNodes.isXS (aAnn, "annotation"))
        continue;
      for (final IXdmNode aDoc : XsdNodes.children (aAnn))
      {
        if (!XsdNodes.isXS (aDoc, "documentation"))
          continue;
        final String sLang = XsdNodes.attrNS (aDoc, XsdConstants.NS_XML, "lang");
        ret.add (new XsdDocumentation (sLang == null ? "" : sLang.strip (),
                                       XsdNodes.attrOrEmpty (aDoc, "source"),
                                       aDoc.getStringValue ().strip ()));
      }
    }
    return ret;
  }

  /** go-xslt <code>setDoc</code>: switch the namespace context to a document. */
  void setDoc (@NonNull final XsdSchemaDocument aSD)
  {
    m_aCurDoc = aSD;
    m_sTNS = aSD.m_sTNS;
    m_sChameleonNS = aSD.m_bChameleon ? aSD.m_sTNS : "";
    m_bElementQualified = aSD.m_bElemQualified;
    m_bAttributeQualified = aSD.m_bAttrQualified;
    m_sFinalDefault = aSD.m_sFinalDefault;
    m_sBlockDefault = aSD.m_sBlockDefault;
    m_aImports = aSD.m_aImports;
  }

  /**
   * go-xslt <code>reachableNS</code>: whether a reference in the current
   * document may resolve into a namespace (its own, an imported one, or the
   * XSD/XML/XSI namespaces).
   */
  boolean reachableNS (@NonNull final String sNS)
  {
    if (sNS.equals (m_sTNS) ||
        XsdConstants.NS_XS.equals (sNS) ||
        XsdConstants.NS_XML.equals (sNS) ||
        XsdConstants.NS_XSI.equals (sNS))
      return true;
    if (!m_sChameleonNS.isEmpty () && sNS.equals (m_sChameleonNS))
      return true;
    return m_aImports.contains (sNS);
  }

  /**
   * go-xslt <code>chamAlt</code>: the adopted-namespace variant of an
   * unqualified name inside a chameleon document, or <code>null</code>.
   */
  @Nullable
  XsdName chamAlt (@NonNull final XsdName aName)
  {
    if (aName.space ().isEmpty () && !m_sChameleonNS.isEmpty ())
      return new XsdName (m_sChameleonNS, aName.local ());
    return null;
  }

  /**
   * go-xslt <code>withinRedefine</code>: whether a node lies inside an
   * xs:redefine element.
   */
  static boolean withinRedefine (@NonNull final IXdmNode aNode)
  {
    for (IXdmNode p = aNode.getParent (); p != null; p = p.getParent ())
    {
      if (XsdNodes.isXS (p, "redefine"))
        return true;
      if (XsdNodes.isXS (p, "schema"))
        return false;
    }
    return false;
  }

  /**
   * go-xslt <code>builtinList</code>: the built-in list types xs:NMTOKENS,
   * xs:IDREFS and xs:ENTITIES (a list of the item type with minLength 1).
   */
  @Nullable
  static XsdSimpleType builtinList (@NonNull final String sLocal)
  {
    final EAtomicType eItem;
    switch (sLocal)
    {
      case "NMTOKENS":
        eItem = EAtomicType.NMTOKEN;
        break;
      case "IDREFS":
        eItem = EAtomicType.IDREF;
        break;
      case "ENTITIES":
        eItem = EAtomicType.ENTITY;
        break;
      default:
        return null;
    }
    final XsdSimpleType ret = new XsdSimpleType (new XsdName (XsdConstants.NS_XS, sLocal));
    ret.m_eVariety = EXsdVariety.LIST;
    ret.m_aItem = XsdSimpleType.builtin (eItem);
    ret.m_aFacets.m_nMinLength = 1;
    ret.m_aFacets.m_bHasMinLength = true;
    return ret;
  }

  /** go-xslt <code>builtinAtom</code>: the built-in atomic type of a local name. */
  @Nullable
  static EAtomicType builtinAtom (@NonNull final String sLocal)
  {
    switch (sLocal)
    {
      case "anySimpleType":
      case "anyAtomicType":
        return EAtomicType.ANY_ATOMIC_TYPE;
      case "anyType":
        return null;
      default:
        return EAtomicType.getFromLocalName (sLocal);
    }
  }

  /** @return <code>true</code> if the local name is an XSD 1.1-only built-in. */
  static boolean isXsd11OnlyBuiltin (@NonNull final String sLocal)
  {
    return XSD11_ONLY_BUILTIN.contains (sLocal);
  }

  /**
   * go-xslt <code>resolveType</code>.
   *
   * @return The type a QName reference denotes. Never <code>null</code>.
   * @throws XsdInvalidException
   *         if it does not resolve
   */
  @NonNull
  IXsdType resolveType (@NonNull final IXdmNode aNode, @NonNull final String sQName)
  {
    final XsdName n = XsdNodes.resolveQName (aNode, sQName);
    if (XsdConstants.NS_XS.equals (n.space ()))
    {
      if (isXsd10 () && isXsd11OnlyBuiltin (n.local ()))
        throw invalid ("src-resolve", "xs:" + n.local () + " is an XSD 1.1 type");
      if ("error".equals (n.local ()))
        return XsdSimpleType.ERROR;
      final XsdSimpleType aList = builtinList (n.local ());
      if (aList != null)
        return aList;
      final EAtomicType eAtom = builtinAtom (n.local ());
      if (eAtom != null)
      {
        final XsdSimpleType aST = XsdSimpleType.builtin (eAtom);
        if (eAtom == EAtomicType.DATE_TIME_STAMP)
        {
          // dateTimeStamp carries {explicitTimezone: required, fixed}
          aST.m_aFacets.m_sExplicitTZ = "required";
          aST.m_aFacets.m_aFixed.add ("explicitTimezone");
        }
        return aST;
      }
      if ("anyType".equals (n.local ()))
        return XsdComplexType.anyType ();
      // The XSD namespace is not closed to user declarations: the schema for
      // schemas declares a handful of global simple types in it
      final IXsdType aDeclared = m_aSchema.m_aTypes.get (n);
      if (aDeclared != null)
        return aDeclared;
      throw invalid ("src-resolve", "unknown built-in type xs:" + n.local ());
    }
    if (withinRedefine (aNode))
    {
      final IXsdType aOrig = m_aRedefineType.get (n);
      if (aOrig != null)
        return aOrig;
    }
    final IXsdType aType = m_aSchema.m_aTypes.get (n);
    if (aType != null)
    {
      if (!reachableNS (n.space ()))
        throw invalid ("src-resolve", "type " + n + " is in a namespace this schema does not import");
      return aType;
    }
    final XsdName aAlt = chamAlt (n);
    if (aAlt != null)
    {
      final IXsdType aAltType = m_aSchema.m_aTypes.get (aAlt);
      if (aAltType != null)
        return aAltType;
    }
    throw invalid ("", "unresolved type " + n);
  }

  /**
   * go-xslt <code>resolveTypeTolerant</code>: {@link #resolveType} that maps a
   * clean "no such component" miss to {@link XsdSimpleType#ABSENT} (XSD 1.0
   * missing sub-components). Never for the XSD namespace or an unreachable
   * namespace.
   */
  @NonNull
  IXsdType resolveTypeTolerant (@NonNull final IXdmNode aNode, @NonNull final String sQName)
  {
    try
    {
      return resolveType (aNode, sQName);
    }
    catch (final XsdInvalidException ex)
    {
      if (!isXsd10 ())
        throw ex;
      final XsdName n = XsdNodes.resolveQName (aNode, sQName);
      if (!m_aSchema.m_aTypes.containsKey (n) &&
          !XsdConstants.NS_XS.equals (n.space ()) &&
          reachableNS (n.space ()) &&
          XsdLexHelper.validQNameLexical (sQName.strip ()))
        return XsdSimpleType.ABSENT;
      throw ex;
    }
  }

  /**
   * go-xslt <code>resolveSimpleType</code>.
   *
   * @throws XsdInvalidException
   *         if it does not resolve or is complex
   */
  @NonNull
  XsdSimpleType resolveSimpleType (@NonNull final IXdmNode aNode, @NonNull final String sQName)
  {
    final IXsdType aType = resolveType (aNode, sQName);
    if (aType instanceof final XsdSimpleType aST)
      return aST;
    throw invalid ("", "expected a simple type, got complex " + sQName);
  }

  /**
   * go-xslt <code>checkValueConstraint</code>: a fixed/default value on the
   * node must be a valid value of the type.
   */
  void checkValueConstraint (@Nullable final IXsdType aType, @NonNull final IXdmNode aNode)
  {
    String v = XsdNodes.attr (aNode, "fixed");
    if (v == null)
      v = XsdNodes.attr (aNode, "default");
    if (v == null)
      return;
    XsdSimpleType aST = null;
    if (aType instanceof final XsdSimpleType aSimple)
      aST = aSimple;
    else
      if (aType instanceof final XsdComplexType aCT)
      {
        if (aCT.m_eKind == EXsdContentKind.SIMPLE)
          aST = aCT.m_aSimpleType;
        else
          if (aCT.m_eKind == EXsdContentKind.ELEMENT_ONLY)
          {
            // A value constraint supplies character content, which element-only
            // content has nowhere to put
            throw invalid ("cos-valid-default.2.1", "a value constraint requires simple or mixed content");
          }
      }
    if (aST == null || aST == XsdSimpleType.ABSENT)
      return;
    // An ID is unique per document, so an ID-typed declaration cannot have a
    // value constraint (XSD 1.1 dropped the rule)
    if (isXsd10 () && aST.resolvePrim () == EAtomicType.ID)
      throw invalid ("a-props-correct.3", "an ID-typed declaration must not have a value constraint");
    try
    {
      XsdSimpleTypeValidator.validate (aST, v);
    }
    catch (final XsdInvalidException ex)
    {
      throw invalid ("",
                     "value constraint " +
                         XsdLexHelper.quote (v) +
                         " is not valid for the type: " +
                         ex.getError ().message ());
    }
  }

  /**
   * go-xslt <code>fillElementCommon</code>: resolve an element's type
   * (type= | inline | default anyType) and nillable/default/fixed. Used for
   * global and local elements.
   */
  void fillElementCommon (@NonNull final XsdElementDecl aDecl, @NonNull final IXdmNode aNode)
  {
    aDecl.m_aDocs = docsOf (aNode);
    aDecl.m_aMsgs = XsdMessages.messagesOf (aNode);
    aDecl.m_bNillable = XsdNodes.attrIs (aNode, "nillable", "true");
    final String sBlock = XsdNodes.attr (aNode, "block");
    if (sBlock != null)
      aDecl.m_aBlocked = XsdDerivation.parseBlockSet (sBlock);
    else
      if (!m_sBlockDefault.isEmpty ())
        aDecl.m_aBlocked = XsdDerivation.parseBlockSet (m_sBlockDefault);
    final String sFinal = XsdNodes.attr (aNode, "final");
    if (sFinal != null)
      aDecl.m_aFinal = XsdDerivation.parseBlockSet (sFinal);
    else
      if (!m_sFinalDefault.isEmpty ())
        aDecl.m_aFinal = XsdDerivation.parseBlockSet (m_sFinalDefault);
    final String sDefault = XsdNodes.attr (aNode, "default");
    if (sDefault != null)
    {
      aDecl.m_sDefault = sDefault;
      aDecl.m_bHasDefault = true;
    }
    final String sFixed = XsdNodes.attr (aNode, "fixed");
    if (sFixed != null)
    {
      aDecl.m_sFixed = sFixed;
      aDecl.m_bHasFixed = true;
    }
    final String sTypeRef = XsdNodes.attr (aNode, "type");
    if (sTypeRef != null)
    {
      final IXsdType aType = resolveTypeTolerant (aNode, sTypeRef);
      if (aType instanceof final XsdSimpleType aST && aST != XsdSimpleType.ABSENT)
        m_aSimpleTypes.checkNotationUsable (aST);
      aDecl.m_aType = aType;
      return;
    }
    final IXdmNode aInlineST = XsdNodes.firstXSChild (aNode, "simpleType");
    if (aInlineST != null)
    {
      final XsdSimpleType aST = new XsdSimpleType ();
      m_aSimpleTypes.parseSimpleTypeInto (aST, aInlineST);
      aDecl.m_aType = aST;
      return;
    }
    final IXdmNode aInlineCT = XsdNodes.firstXSChild (aNode, "complexType");
    if (aInlineCT != null)
    {
      final XsdComplexType aCT = new XsdComplexType ();
      m_aComponents.parseComplexTypeInto (aCT, aInlineCT);
      aDecl.m_aType = aCT;
      return;
    }
    // No type: xs:anyType, unless a substitution-group affiliation supplies
    // the head's type later
    aDecl.m_aType = XsdComplexType.anyType ();
    aDecl.m_bTypeDefaulted = true;
  }

  /**
   * go-xslt <code>attributeType</code>: the simple type of an attribute
   * declaration (type= | inline | xs:anySimpleType).
   */
  @NonNull
  XsdSimpleType attributeType (@NonNull final IXdmNode aNode)
  {
    final String sTypeRef = XsdNodes.attr (aNode, "type");
    if (sTypeRef != null)
    {
      final IXsdType aType = resolveTypeTolerant (aNode, sTypeRef);
      if (!(aType instanceof final XsdSimpleType aST))
        throw invalid ("", "attribute type must be simple");
      if (aST != XsdSimpleType.ABSENT)
        m_aSimpleTypes.checkNotationUsable (aST);
      return aST;
    }
    final IXdmNode aInline = XsdNodes.firstXSChild (aNode, "simpleType");
    if (aInline != null)
    {
      final XsdSimpleType aST = new XsdSimpleType ();
      m_aSimpleTypes.parseSimpleTypeInto (aST, aInline);
      return aST;
    }
    return XsdSimpleType.anySimpleType ();
  }

  /**
   * Compile a schema set (go-xslt <code>compile</code>).
   *
   * @param aSources
   *        The schema documents; empty for the empty schema set.
   * @return The compiled schema.
   * @throws XsdInvalidException
   *         if the schema set is in error
   */
  @NonNull
  XsdSchema compile (@NonNull final List <XsdSchemaSource> aSources)
  {
    if (aSources.isEmpty ())
    {
      // No schema documents: only the built-in components, and whatever the
      // instance's hints or xsi:type name
      final XsdSchema ret = compile (new CommonsArrayList <> (XsdSchemaSource.ofText (EMPTY_SCHEMA_DOC, null)));
      ret.m_aSourceDocs.clear ();
      return ret;
    }
    final XsdSchemaSource aPrimarySource = aSources.get (0);
    final IXdmNode aPrimary = XsdNodes.rootElement (parseDocument (aPrimarySource, "schema"));
    if (!isSchemaElement (aPrimary))
      throw invalid ("", "document element is not xs:schema");

    final Path aBaseDir = aPrimarySource.getBaseDir ();
    m_aSchema = new XsdSchema (m_eVersion, aBaseDir, m_aFileAccess);
    m_aSchema.m_aSourceDocs.addAll (aSources);
    _seedXSIAttributes (m_aSchema);

    // Resolve the include/import graph into a flat list of documents. Seed
    // the visited set with the primary's content
    final ICommonsList <XsdSchemaDocument> aCollected = new CommonsArrayList <> ();
    final ICommonsSet <String> aVisited = new CommonsHashSet <> ();
    aVisited.add ("c:" + aPrimarySource.getContentKey ());
    m_aCollector.collect (aPrimary, aBaseDir, "", aCollected, aVisited, "");
    // Every supplied document contributes components, not just the entry one;
    // documents already reached through the graph are skipped
    for (int i = 1; i < aSources.size (); ++i)
    {
      final XsdSchemaSource aSource = aSources.get (i);
      final String sKey = aSource.getContentKey ();
      if (aVisited.contains ("c:" + sKey) || aVisited.contains ("overridden:" + sKey))
        continue;
      aVisited.add ("c:" + sKey);
      final IXdmNode aExtra;
      try
      {
        aExtra = new XdmParser ().parse (aSource.createInputSource ());
      }
      catch (final XdmParseException ex)
      {
        // A supplementary document we cannot read simply contributes no
        // components
        continue;
      }
      final IXdmNode aEl = XsdNodes.rootElement (aExtra);
      if (!isSchemaElement (aEl))
        continue;
      m_aCollector.collect (aEl, aSource.getBaseDir (), "", aCollected, aVisited, "");
    }
    for (final XsdSchemaDocument aSD : aCollected)
    {
      XsdSchemaForSchemas.validateAttrValues (aSD.m_aElement, m_eVersion);
      XsdSchemaForSchemas.validateSchemaAttrs (aSD.m_aElement, m_eVersion);
    }

    // Pass 1: pre-register named top-level components across all documents
    final ICommonsList <Work> aSimpleW = new CommonsArrayList <> ();
    final ICommonsList <Work> aComplexW = new CommonsArrayList <> ();
    final ICommonsList <Work> aElemW = new CommonsArrayList <> ();
    final ICommonsList <Work> aAttrW = new CommonsArrayList <> ();
    final ICommonsList <Work> aGroupW = new CommonsArrayList <> ();
    final ICommonsList <Work> aAGW = new CommonsArrayList <> ();
    final ICommonsSet <XsdName> aNotationNames = new CommonsHashSet <> ();
    for (final XsdSchemaDocument aSD : aCollected)
      for (final IXdmNode aChild : XsdNodes.children (aSD.m_aElement))
      {
        if (!aChild.isElement ())
          continue;
        if (!XsdConstants.NS_XS.equals (XsdNodes.namespaceUri (aChild)))
          throw new XsdUnsupportedException ("top-level element " + XsdNodes.describe (aChild) + " in a foreign namespace");
        final String sLocal = XsdNodes.localName (aChild);
        switch (sLocal)
        {
          case "annotation":
          case "include":
          case "import":
          case "redefine":
          case "override":
            // resolved during collect / not modelled
            break;
          case "notation":
          {
            final String sName = XsdNodes.declName (aChild);
            if (!XsdLexHelper.isNCName (sName))
              throw invalid ("", "xs:notation requires a valid name");
            if (!XsdNodes.hasAttr (aChild, "public") && !XsdNodes.hasAttr (aChild, "system"))
              throw invalid ("", "xs:notation " + XsdLexHelper.quote (sName) + " requires public or system");
            final XsdName k = new XsdName (aSD.m_sTNS, sName);
            if (!aNotationNames.add (k))
              throw invalid ("", "duplicate notation " + k);
            m_aNotationLocals.add (k.local ());
            break;
          }
          case "simpleType":
          case "complexType":
          {
            final XsdName k = _named (aSD, aChild);
            if (m_aSchema.m_aTypes.containsKey (k))
              throw invalid ("", "duplicate type definition " + k);
            if ("simpleType".equals (sLocal))
            {
              m_aSchema.m_aTypes.put (k, new XsdSimpleType (k));
              aSimpleW.add (new Work (aSD, aChild));
            }
            else
            {
              m_aSchema.m_aTypes.put (k, new XsdComplexType (k));
              aComplexW.add (new Work (aSD, aChild));
            }
            break;
          }
          case "element":
          {
            final XsdName k = _named (aSD, aChild);
            if (m_aSchema.m_aElements.containsKey (k))
              throw invalid ("", "duplicate element declaration " + k);
            m_aSchema.m_aElements.put (k, new XsdElementDecl (k));
            aElemW.add (new Work (aSD, aChild));
            break;
          }
          case "attribute":
          {
            final XsdName k = _named (aSD, aChild);
            if (XsdConstants.NS_XSI.equals (k.space ()))
              throw invalid ("no-xsi", "an attribute declaration cannot target the XSI namespace");
            if (m_aSchema.m_aAttributes.containsKey (k))
              throw invalid ("", "duplicate attribute declaration " + k);
            m_aSchema.m_aAttributes.put (k, new XsdAttributeDecl (k));
            aAttrW.add (new Work (aSD, aChild));
            break;
          }
          case "group":
          {
            final XsdName k = _named (aSD, aChild);
            if (m_aGroups.containsKey (k))
              throw invalid ("sch-props-correct.2", "duplicate group definition " + k);
            m_aGroups.put (k, new XsdModelGroup ());
            aGroupW.add (new Work (aSD, aChild));
            break;
          }
          case "attributeGroup":
          {
            final XsdName k = _named (aSD, aChild);
            if (m_aAttrGroups.containsKey (k))
              throw invalid ("sch-props-correct.2", "duplicate attributeGroup definition " + k);
            m_aAttrGroups.put (k, new XsdAttributeGroup ());
            aAGW.add (new Work (aSD, aChild));
            break;
          }
          case "defaultOpenContent":
            // Per-document default open content (1.1)
            aSD.m_aDefaultOpenNode = aChild;
            break;
          default:
            throw new XsdUnsupportedException ("top-level xs:" + sLocal);
        }
      }

    // Collect the xs:redefine redefinitions. Group/attributeGroup ones are
    // applied before complex-type bodies (which flatten references by value),
    // type ones after the originals are built
    final ICommonsList <Work> aGroupRedefs = new CommonsArrayList <> ();
    final ICommonsList <Work> aTypeRedefs = new CommonsArrayList <> ();
    for (final XsdSchemaDocument aSD : aCollected)
      for (final IXdmNode aRD : XsdNodes.children (aSD.m_aElement))
      {
        if (!XsdNodes.isXS (aRD, "redefine"))
          continue;
        for (final IXdmNode aChild : XsdNodes.children (aRD))
          if (XsdNodes.isXSElement (aChild))
            switch (XsdNodes.localName (aChild))
            {
              case "group":
              case "attributeGroup":
                aGroupRedefs.add (new Work (aSD, aChild));
                break;
              case "simpleType":
              case "complexType":
                aTypeRedefs.add (new Work (aSD, aChild));
                break;
              default:
                break;
            }
      }

    // Pass 2+: simple types in dependency order (a base is built before the
    // type deriving from it)
    final ICommonsMap <XsdName, Work> aSimpleByName = new CommonsHashMap <> ();
    for (final Work w : aSimpleW)
      aSimpleByName.put (new XsdName (w.sd ().m_sTNS, XsdNodes.declName (w.node ())), w);
    final ICommonsList <Work> aSimpleOrder = new CommonsArrayList <> ();
    final ICommonsMap <XsdName, Integer> aSimpleVisited = new CommonsHashMap <> ();
    final boolean [] aHardCycle = { false };
    for (final Work w : aSimpleW)
      _visitSimpleType (new XsdName (w.sd ().m_sTNS, XsdNodes.declName (w.node ())),
                        aSimpleByName,
                        aSimpleVisited,
                        new CommonsArrayList <> (),
                        new CommonsArrayList <> (),
                        aSimpleOrder,
                        aHardCycle);
    if (aHardCycle[0])
      throw invalid ("", "circular simpleType derivation");
    for (final Work w : aSimpleOrder)
    {
      setDoc (w.sd ());
      final String sName = XsdNodes.declName (w.node ());
      if (!(m_aSchema.m_aTypes.get (new XsdName (w.sd ().m_sTNS, sName)) instanceof final XsdSimpleType aST))
        throw invalid ("", "conflicting definitions for type " + sName);
      m_aSimpleTypes.parseSimpleTypeInto (aST, w.node ());
    }
    for (final Work w : aAttrW)
    {
      setDoc (w.sd ());
      _parseGlobalAttribute (w.node ());
    }
    for (final Work w : aGroupW)
    {
      setDoc (w.sd ());
      final XsdModelGroup aGroup = m_aComponents.parseGroupBody (w.node ());
      m_aGroups.get (new XsdName (w.sd ().m_sTNS, XsdNodes.declName (w.node ()))).assignFrom (aGroup);
    }
    m_aChecks.checkGroupCycles ();
    XsdComponentChecks.checkAttrGroupCycles (aCollected, m_eVersion);
    // Attribute groups in dependency order: a group flattens the uses of every
    // attributeGroup it references
    final ICommonsMap <XsdName, Work> aAGByName = new CommonsHashMap <> ();
    for (final Work w : aAGW)
      aAGByName.put (new XsdName (w.sd ().m_sTNS, XsdNodes.declName (w.node ())), w);
    final ICommonsList <Work> aAGOrder = new CommonsArrayList <> ();
    final ICommonsSet <XsdName> aAGVisited = new CommonsHashSet <> ();
    for (final Work w : aAGW)
      _visitAttrGroup (new XsdName (w.sd ().m_sTNS, XsdNodes.declName (w.node ())), aAGByName, aAGVisited, aAGOrder);
    for (final Work w : aAGOrder)
    {
      setDoc (w.sd ());
      final XsdAttributeGroup aAG = m_aComponents.parseAttrGroupBody (w.node ());
      m_aAttrGroups.get (new XsdName (w.sd ().m_sTNS, XsdNodes.declName (w.node ()))).assignFrom (aAG);
    }
    // src-resolve for xs:schema/@defaultAttributes (1.1)
    for (final XsdSchemaDocument aSD : aCollected)
    {
      if (!aSD.m_bHasDefaultAttrs)
        continue;
      if (!m_aAttrGroups.containsKey (aSD.m_aDefaultAttrs))
        throw invalid ("src-resolve", "defaultAttributes group " + aSD.m_aDefaultAttrs + " does not resolve");
      final String sNS = aSD.m_aDefaultAttrs.space ();
      if (!sNS.equals (aSD.m_sTNS) && !sNS.isEmpty () && !aSD.m_aImports.contains (sNS))
        throw invalid ("src-resolve",
                       "defaultAttributes group " +
                                      aSD.m_aDefaultAttrs +
                                      " is in a namespace this document does not import");
    }
    // Group/attributeGroup redefinitions now, so complex-type bodies pick up
    // the redefined content
    _applyRedefinitions (aGroupRedefs);
    // Complex types in dependency order: a simpleContent/complexContent body
    // reads its base's compiled content type while parsing
    final ICommonsMap <XsdName, Integer> aCTIndex = new CommonsHashMap <> ();
    for (int i = 0; i < aComplexW.size (); ++i)
    {
      final Work w = aComplexW.get (i);
      aCTIndex.put (new XsdName (w.sd ().m_sTNS, XsdNodes.declName (w.node ())), Integer.valueOf (i));
    }
    final ICommonsList <Work> aCTOrder = new CommonsArrayList <> ();
    final boolean [] aEmitted = new boolean [aComplexW.size ()];
    for (int i = 0; i < aComplexW.size (); ++i)
      _emitComplexType (i, aComplexW, aCTIndex, aEmitted, aCTOrder);
    for (final Work w : aCTOrder)
    {
      setDoc (w.sd ());
      final String sName = XsdNodes.declName (w.node ());
      if (!(m_aSchema.m_aTypes.get (new XsdName (w.sd ().m_sTNS, sName)) instanceof final XsdComplexType aCT))
        throw invalid ("", "conflicting definitions for type " + sName);
      m_aComponents.parseComplexTypeInto (aCT, w.node ());
    }
    for (final Work w : aElemW)
    {
      setDoc (w.sd ());
      _parseGlobalElement (w.node ());
    }
    _applyRedefinitions (aTypeRedefs);
    _buildSubstitutionGroups ();
    _checkSubstGroupCycles ();
    _inheritSubstGroupTypes ();
    m_aChecks.fillAllWildcardSiblings ();
    _checkTypeCycles ();
    _checkIDAttributeUses ();
    m_aChecks.checkDuplicateConstraints ();
    m_aChecks.checkAllRestrictions ();
    m_aIdentity.checkKeyrefs ();
    m_aChecks.checkSubstitutionGroups ();
    m_aChecks.checkFinalDerivation ();
    m_aChecks.checkAttrDerivation ();
    // Unique Particle Attribution over every complex type, named and anonymous
    for (final XsdComplexType aCT : m_aAllCTs)
      XsdUpa.checkUPA (m_aSchema, m_aSchema.effectiveParticle (aCT));
    // e-props-correct.5 (1.1): every type alternative's type must be validly
    // derived from the declaration's own
    if (isXsd11 ())
      for (final XsdElementDecl aDecl : m_aSchema.m_aElements.values ())
        for (final XsdTypeAlternative aAlt : aDecl.m_aAlternatives)
        {
          // xs:error is validly substitutable for every type
          if (aAlt.m_aType == XsdSimpleType.ERROR)
            continue;
          if (aAlt.m_aType != null &&
              aDecl.m_aType != null &&
              !XsdDerivation.derivesFrom (aAlt.m_aType, aDecl.m_aType))
            throw invalid ("e-props-correct.5",
                           "a type alternative of " + aDecl.m_aName + " is not derived from its declared type");
        }
    return m_aSchema;
  }
}
