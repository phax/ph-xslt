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
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.XdmNode;

/**
 * Compiles complex types, particles, local declarations, wildcards and
 * attribute uses (the parsing half of go-xslt <code>structure.go</code>, plus
 * the parse entry points of <code>xsd11.go</code>, whose XSD 1.1 features are
 * stubs until phase V8).
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class XsdComponentParser
{
  private final XsdCompiler m_aC;

  XsdComponentParser (@NonNull final XsdCompiler aCompiler)
  {
    m_aC = aCompiler;
  }

  /**
   * go-xslt <code>checkComplexTypeStructure</code>: at most one leading
   * annotation, and exactly one content model (simpleContent XOR
   * complexContent XOR a shorthand model group).
   */
  private static void _checkComplexTypeStructure (@NonNull final XdmNode aNode)
  {
    int nAnnot = 0;
    int nSimple = 0;
    int nComplex = 0;
    int nGroup = 0;
    int nAttr = 0;
    boolean bSawContent = false;
    for (final XdmNode aChild : XsdNodes.children (aNode))
    {
      if (!XsdNodes.isXSElement (aChild))
        continue;
      final String sLocal = XsdNodes.localName (aChild);
      switch (sLocal)
      {
        case "annotation":
          nAnnot++;
          if (bSawContent)
            throw XsdCompiler.invalid ("", "xs:annotation must be the first child of xs:complexType");
          break;
        case "simpleContent":
          nSimple++;
          bSawContent = true;
          break;
        case "complexContent":
          nComplex++;
          bSawContent = true;
          break;
        case "sequence":
        case "choice":
        case "all":
        case "group":
          nGroup++;
          bSawContent = true;
          break;
        case "attribute":
        case "attributeGroup":
        case "anyAttribute":
          nAttr++;
          bSawContent = true;
          break;
        case "element":
        case "any":
          throw XsdCompiler.invalid ("", "particle xs:" + sLocal + " cannot appear directly in xs:complexType");
        default:
          bSawContent = true;
          break;
      }
    }
    if (nSimple + nComplex > 0 && nAttr > 0)
      throw XsdCompiler.invalid ("",
                                 "attributes must be inside the extension/restriction when simple/complexContent is used");
    if (nAnnot > 1)
      throw XsdCompiler.invalid ("", "xs:complexType has more than one annotation");
    if (nSimple + nComplex > 1)
      throw XsdCompiler.invalid ("", "xs:complexType has more than one content model");
    if (nSimple + nComplex > 0 && nGroup > 0)
      throw XsdCompiler.invalid ("", "xs:complexType mixes simple/complexContent with a model group");
    if (nGroup > 1)
      throw XsdCompiler.invalid ("", "xs:complexType has more than one model group");
  }

  /**
   * go-xslt <code>checkFacetsBeforeAttrs</code>: in a simpleContent
   * restriction every constraining facet (and the inline simple type) must
   * precede the attribute uses.
   */
  private static void _checkFacetsBeforeAttrs (@NonNull final XdmNode aBody)
  {
    boolean bSeenAttr = false;
    for (final XdmNode aChild : XsdNodes.children (aBody))
    {
      if (!XsdNodes.isXSElement (aChild))
        continue;
      final String sLocal = XsdNodes.localName (aChild);
      switch (sLocal)
      {
        case "attribute":
        case "attributeGroup":
        case "anyAttribute":
          bSeenAttr = true;
          break;
        case "annotation":
        case "assert":
          // order-neutral here
          break;
        case "simpleType":
          if (bSeenAttr)
            throw XsdCompiler.invalid ("", "xs:simpleType must precede attribute declarations");
          break;
        default:
          if (bSeenAttr)
            throw XsdCompiler.invalid ("", "facet xs:" + sLocal + " must precede attribute declarations");
          break;
      }
    }
  }

  /** go-xslt <code>occursOf</code>: minOccurs and maxOccurs (default 1). */
  @NonNull
  private static int [] _occursOf (@NonNull final XdmNode aNode)
  {
    int nMin = 1;
    final String sMin = XsdNodes.attr (aNode, "minOccurs");
    if (sMin != null)
      nMin = XsdLexHelper.atoiOr (sMin);
    int nMax = 1;
    final String sMax = XsdNodes.attr (aNode, "maxOccurs");
    if (sMax != null)
      nMax = "unbounded".equals (sMax) ? XsdConstants.UNBOUNDED : XsdLexHelper.atoiOr (sMax);
    return new int [] { nMin, nMax };
  }

  /** go-xslt <code>hasFacetChildren</code>. */
  private static boolean _hasFacetChildren (@NonNull final XdmNode aNode)
  {
    for (final XdmNode aChild : XsdNodes.children (aNode))
    {
      if (!XsdNodes.isXSElement (aChild))
        continue;
      switch (XsdNodes.localName (aChild))
      {
        case "annotation":
        case "attribute":
        case "attributeGroup":
        case "anyAttribute":
        case "simpleType":
          break;
        default:
          return true;
      }
    }
    return false;
  }

  /**
   * go-xslt <code>excludeNS</code>: the list without the dropped namespace and
   * without the absent namespace.
   */
  @NonNull
  private static ICommonsList <String> _excludeNS (@NonNull final ICommonsList <String> aList,
                                                   @NonNull final String sDrop)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final String sNS : aList)
      if (!sNS.equals (sDrop) && !sNS.isEmpty ())
        ret.add (sNS);
    return ret;
  }

  /**
   * go-xslt <code>builtinXMLAttr</code> / <code>builtinXLinkAttr</code>: the
   * attributes of the XML and XLink namespaces any schema may reference
   * without an import.
   */
  @Nullable
  private static XsdAttributeDecl _builtinAttrDecl (@NonNull final XsdName aName)
  {
    EAtomicType eType = null;
    if (XsdConstants.NS_XML.equals (aName.space ()))
      switch (aName.local ())
      {
        case "base":
          eType = EAtomicType.ANY_URI;
          break;
        case "id":
          eType = EAtomicType.ID;
          break;
        case "lang":
        case "space":
          // modelled as string
          eType = EAtomicType.STRING;
          break;
        default:
          break;
      }
    else
      if (XsdConstants.NS_XLINK.equals (aName.space ()))
        switch (aName.local ())
        {
          case "href":
          case "role":
          case "arcrole":
            eType = EAtomicType.ANY_URI;
            break;
          case "title":
            eType = EAtomicType.STRING;
            break;
          case "type":
          case "show":
          case "actuate":
            eType = EAtomicType.TOKEN;
            break;
          case "label":
          case "from":
          case "to":
            eType = EAtomicType.NCNAME;
            break;
          default:
            break;
        }
    return eType == null ? null : new XsdAttributeDecl (aName, XsdSimpleType.builtin (eType));
  }

  /**
   * go-xslt <code>localTargetNS</code>: the XSD 1.1 @targetNamespace of a local
   * element or attribute declaration.
   *
   * @return The namespace, or <code>null</code> if the attribute is absent.
   */
  @Nullable
  private String _localTargetNS (@NonNull final XdmNode aNode)
  {
    final String sValue = XsdNodes.attr (aNode, "targetNamespace");
    if (sValue == null)
      return null;
    if (m_aC.isXsd10 ())
      throw XsdCompiler.invalid ("src-element", "@targetNamespace on a local declaration is an XSD 1.1 feature");
    final String v = XsdWhiteSpace.collapse (sValue);
    if (v.equals (m_aC.m_sTNS))
      return v;
    if (XsdNodes.hasAttr (aNode, "form"))
      throw XsdCompiler.invalid ("src-element", "@targetNamespace and @form are mutually exclusive");
    for (XdmNode n = aNode.getParent (); n != null && n.isElement (); n = n.getParent ())
    {
      if (!XsdNodes.isXSElement (n))
        continue;
      switch (XsdNodes.localName (n))
      {
        case "restriction":
        {
          // A restriction of xs:anyType is no restriction context at all
          final String sBase = XsdNodes.attr (n, "base");
          if (sBase != null && XsdComplexType.ANY_TYPE_NAME.equals (XsdNodes.resolveQName (n, sBase)))
            throw XsdCompiler.invalid ("src-element",
                                       "a targetNamespace-divergent local declaration requires a restriction of a real base type");
          return v;
        }
        case "extension":
        case "complexType":
        case "schema":
          throw XsdCompiler.invalid ("src-element",
                                     "a local declaration may take a different targetNamespace only inside a restriction");
        default:
          break;
      }
    }
    throw XsdCompiler.invalid ("src-element",
                               "a local declaration may take a different targetNamespace only inside a restriction");
  }

  /**
   * go-xslt <code>applyOpenContent</code>: set a complex type's {open content}
   * (XSD 1.1, phase V8). Under 1.0 nothing happens.
   */
  private void _applyOpenContent (@NonNull final XsdComplexType aCT, @NonNull final XdmNode... aBodies)
  {
    if (!m_aC.isXsd11 ())
      return;
    for (final XdmNode aBody : aBodies)
      if (aBody != null && XsdNodes.firstXSChild (aBody, "openContent") != null)
        throw XsdCompiler.unsupported11 ("xs:openContent");
    final XsdSchemaDocument aSD = m_aC.m_aCurDoc;
    if (aSD != null && aSD.m_aDefaultOpenNode != null && aCT.m_eKind != EXsdContentKind.SIMPLE)
      throw XsdCompiler.unsupported11 ("xs:defaultOpenContent");
  }

  /**
   * go-xslt <code>applyDefaultAttrs</code>: merge the document-wide
   * xs:schema/@defaultAttributes group into a complex type unless it opts out
   * (XSD 1.1).
   */
  private void _applyDefaultAttrs (@NonNull final XsdComplexType aCT, @NonNull final XdmNode aNode)
  {
    final XsdSchemaDocument aSD = m_aC.m_aCurDoc;
    if (!m_aC.isXsd11 () ||
        aSD == null ||
        !aSD.m_bHasDefaultAttrs ||
        XsdNodes.attrIs (aNode, "defaultAttributesApply", "false"))
      return;
    final XsdAttributeGroup aAG = m_aC.m_aAttrGroups.get (aSD.m_aDefaultAttrs);
    if (aAG == null)
      throw XsdCompiler.invalid ("src-resolve", "defaultAttributes group " + aSD.m_aDefaultAttrs + " does not resolve");
    final ICommonsSet <XsdName> aSeen = new CommonsHashSet <> ();
    for (final XsdAttributeUse aUse : aCT.m_aAttrUses)
      aSeen.add (aUse.m_aDecl.m_aName);
    final ICommonsList <XsdAttributeUse> aUses = new CommonsArrayList <> (aCT.m_aAttrUses);
    for (final XsdAttributeUse aUse : aAG.m_aUses)
    {
      if (aSeen.contains (aUse.m_aDecl.m_aName))
        throw XsdCompiler.invalid ("ct-props-correct.4",
                                   "attribute " +
                                                         aUse.m_aDecl.m_aName +
                                                         " appears both on the type and in the defaultAttributes group");
      aUses.add (aUse);
    }
    aCT.m_aAttrUses = aUses;
    if (aAG.m_aWildcard != null)
      aCT.m_aAttrWildcard = intersectWildcards (aCT.m_aAttrWildcard, aAG.m_aWildcard);
  }

  /** go-xslt <code>parseAttrUse</code>. */
  @NonNull
  private XsdAttributeUse _parseAttrUse (@NonNull final XdmNode aNode)
  {
    final XsdAttributeUse aUse = new XsdAttributeUse ();
    aUse.m_aDocs = m_aC.docsOf (aNode);
    aUse.m_aMsgs = XsdMessages.messagesOf (aNode);
    // Use-level {inheritable}: an explicit attribute on the use wins
    final String sUseInheritable = XsdNodes.attr (aNode, "inheritable");
    final String sRef = XsdNodes.attr (aNode, "ref");
    if (sRef != null)
    {
      if (XsdNodes.hasAttr (aNode, "name"))
        throw XsdCompiler.invalid ("", "xs:attribute has both ref and name");
      if (XsdNodes.hasAttr (aNode, "type"))
        throw XsdCompiler.invalid ("", "xs:attribute ref cannot also have a type");
      final XsdName aRefName = XsdNodes.resolveQName (aNode, sRef);
      XsdAttributeDecl aDecl = m_aC.m_aSchema.m_aAttributes.get (aRefName);
      if (aDecl != null && !m_aC.reachableNS (aRefName.space ()))
        throw XsdCompiler.invalid ("src-resolve",
                                   "attribute " + aRefName + " is in a namespace this schema does not import");
      if (aDecl == null)
      {
        // Chameleon repair
        final XsdName aAlt = m_aC.chamAlt (aRefName);
        if (aAlt != null)
          aDecl = m_aC.m_aSchema.m_aAttributes.get (aAlt);
      }
      if (aDecl == null)
      {
        // The XML and XLink namespaces provide built-in attributes
        aDecl = _builtinAttrDecl (aRefName);
        if (aDecl == null)
          throw XsdCompiler.invalid ("", "unresolved attribute ref " + sRef);
      }
      aUse.m_aDecl = aDecl;
    }
    else
    {
      final String sName = XsdNodes.declName (aNode);
      if (sName.isEmpty () || !XsdLexHelper.isNCName (sName))
        throw XsdCompiler.invalid ("src-attribute",
                                   "local xs:attribute requires a valid NCName name or a ref (got " +
                                                    XsdLexHelper.quote (sName) +
                                                    ")");
      String sNS = "";
      final String sForm = XsdNodes.attr (aNode, "form");
      if (sForm != null)
      {
        if ("qualified".equals (sForm))
          sNS = m_aC.m_sTNS;
      }
      else
        if (m_aC.m_bAttributeQualified)
          sNS = m_aC.m_sTNS;
      final String sLocalTNS = _localTargetNS (aNode);
      if (sLocalTNS != null)
        sNS = sLocalTNS;
      // no-xsi: an attribute declaration may not target the XSI namespace
      if (XsdConstants.NS_XSI.equals (sNS))
        throw XsdCompiler.invalid ("no-xsi", "an attribute declaration cannot target the XSI namespace");
      final XsdSimpleType aType = m_aC.attributeType (aNode);
      final XsdAttributeDecl aDecl = new XsdAttributeDecl (new XsdName (sNS, sName), aType);
      aDecl.m_bInheritable = XsdNodes.boolAttrTrue (aNode, "inheritable");
      aDecl.m_aDocs = aUse.m_aDocs;
      aDecl.m_aMsgs = aUse.m_aMsgs;
      aUse.m_aDecl = aDecl;
    }
    if (sUseInheritable != null)
    {
      final String s = XsdWhiteSpace.collapse (sUseInheritable);
      aUse.m_bInheritable = "true".equals (s) || "1".equals (s);
    }
    else
      aUse.m_bInheritable = aUse.m_aDecl.m_bInheritable;
    final String sUse = XsdNodes.attrOrEmpty (aNode, "use");
    if ("required".equals (sUse))
      aUse.m_bRequired = true;
    else
      if ("prohibited".equals (sUse))
        aUse.m_bProhibited = true;
    final String sDefault = XsdNodes.attr (aNode, "default");
    if (sDefault != null)
    {
      aUse.m_sDefault = sDefault;
      aUse.m_bHasDefault = true;
    }
    final String sFixed = XsdNodes.attr (aNode, "fixed");
    if (sFixed != null)
    {
      aUse.m_sFixed = sFixed;
      aUse.m_bHasFixed = true;
    }
    if (aUse.m_bProhibited && aUse.m_bHasFixed)
    {
      // XSD 1.1 makes prohibited + fixed a schema error; in 1.0 the fixed use
      // survives and the prohibition is dropped (attP031)
      if (m_aC.isXsd11 ())
        throw XsdCompiler.invalid ("src-attribute",
                                   "attribute " + aUse.m_aDecl.m_aName + " cannot be both prohibited and fixed");
      aUse.m_bProhibited = false;
    }
    // A reference with no value constraint of its own inherits the
    // declaration's
    if (!aUse.m_bHasDefault && !aUse.m_bHasFixed)
    {
      if (aUse.m_aDecl.m_bHasFixed)
      {
        aUse.m_sFixed = aUse.m_aDecl.m_sFixed;
        aUse.m_bHasFixed = true;
      }
      else
        if (aUse.m_aDecl.m_bHasDefault)
        {
          aUse.m_sDefault = aUse.m_aDecl.m_sDefault;
          aUse.m_bHasDefault = true;
        }
    }
    // au-props-correct.2: a reference may repeat the declaration's fixed value
    // but not change it, nor turn it into a default
    if (aUse.m_aDecl.m_bHasFixed)
    {
      if (aUse.m_bHasDefault)
        throw XsdCompiler.invalid ("au-props-correct.2",
                                   "attribute " +
                                                         aUse.m_aDecl.m_aName +
                                                         " is fixed in its declaration and cannot take a default");
      if (aUse.m_bHasFixed && !XsdLexHelper.lexEqualCollapsed (aUse.m_sFixed, aUse.m_aDecl.m_sFixed))
        throw XsdCompiler.invalid ("au-props-correct.2",
                                   "attribute " + aUse.m_aDecl.m_aName + " must keep the fixed value of its declaration");
    }
    m_aC.checkValueConstraint (aUse.m_aDecl.m_aType, aNode);
    return aUse;
  }

  /**
   * go-xslt <code>parseAttributes</code>: the attribute uses and the attribute
   * wildcard that are direct children of a complexType, extension, restriction
   * or attributeGroup.
   */
  @NonNull
  private XsdEffectiveAttributes _parseAttributes (@NonNull final XdmNode aNode)
  {
    final ICommonsList <XsdAttributeUse> aUses = new CommonsArrayList <> ();
    XsdWildcard aWC = null;
    final ICommonsSet <XsdName> aSeen = new CommonsHashSet <> ();
    for (final XdmNode aChild : XsdNodes.children (aNode))
    {
      if (XsdNodes.isXS (aChild, "attribute"))
      {
        final XsdAttributeUse aUse = _parseAttrUse (aChild);
        if (!aSeen.add (aUse.m_aDecl.m_aName))
          throw XsdCompiler.invalid ("", "duplicate attribute " + aUse.m_aDecl.m_aName);
        aUses.add (aUse);
      }
      else
        if (XsdNodes.isXS (aChild, "attributeGroup"))
        {
          final String sRef = XsdNodes.attr (aChild, "ref");
          if (sRef == null)
            throw XsdCompiler.invalid ("", "attributeGroup without a ref");
          final XsdName aAGName = XsdNodes.resolveQName (aChild, sRef);
          XsdAttributeGroup aAG = m_aC.m_aAttrGroups.get (aAGName);
          if (aAG != null && !m_aC.reachableNS (aAGName.space ()))
            throw XsdCompiler.invalid ("src-resolve",
                                       "attributeGroup " + aAGName + " is in a namespace this schema does not import");
          final XsdAttributeGroup aOrig = m_aC.m_aRedefineAttrGroup.get (aAGName);
          if (aOrig != null && XsdCompiler.withinRedefine (aChild))
          {
            // Redefined attributeGroup self-reference
            aAG = aOrig;
          }
          if (aAG == null)
          {
            // Chameleon repair
            final XsdName aAlt = m_aC.chamAlt (aAGName);
            if (aAlt != null)
              aAG = m_aC.m_aAttrGroups.get (aAlt);
          }
          if (aAG == null)
            throw XsdCompiler.invalid ("", "unresolved attributeGroup ref " + sRef);
          // ct-props-correct.4: two attribute uses of the same name, however
          // they are reached, are an error
          for (final XsdAttributeUse aGroupUse : aAG.m_aUses)
            if (!aSeen.add (aGroupUse.m_aDecl.m_aName))
              throw XsdCompiler.invalid ("ct-props-correct.4", "duplicate attribute " + aGroupUse.m_aDecl.m_aName);
          aUses.addAll (aAG.m_aUses);
          aWC = intersectWildcards (aWC, aAG.m_aWildcard);
        }
        else
          if (XsdNodes.isXS (aChild, "anyAttribute"))
            aWC = intersectWildcards (aWC, parseWildcard (aChild));
    }
    return new XsdEffectiveAttributes (aUses, aWC);
  }

  /** go-xslt <code>parseLocalElement</code>: a local declaration or a reference. */
  @NonNull
  private XsdElementDecl _parseLocalElement (@NonNull final XdmNode aNode)
  {
    final String sRef = XsdNodes.attr (aNode, "ref");
    if (sRef != null)
    {
      if (XsdNodes.hasAttr (aNode, "name"))
        throw XsdCompiler.invalid ("", "xs:element has both ref and name");
      if (XsdNodes.hasAttr (aNode, "targetNamespace"))
        throw XsdCompiler.invalid ("src-element", "xs:element ref cannot carry targetNamespace");
      if (XsdNodes.hasAttr (aNode, "type"))
        throw XsdCompiler.invalid ("", "xs:element ref cannot also have a type");
      final XsdName aRefName = XsdNodes.resolveQName (aNode, sRef);
      final XsdElementDecl aDecl = m_aC.m_aSchema.m_aElements.get (aRefName);
      if (aDecl != null)
      {
        if (!m_aC.reachableNS (aRefName.space ()))
          throw XsdCompiler.invalid ("src-resolve",
                                     "element " + aRefName + " is in a namespace this schema does not import");
        return aDecl;
      }
      // Chameleon repair
      final XsdName aAlt = m_aC.chamAlt (aRefName);
      if (aAlt != null)
      {
        final XsdElementDecl aAltDecl = m_aC.m_aSchema.m_aElements.get (aAlt);
        if (aAltDecl != null)
          return aAltDecl;
      }
      throw XsdCompiler.invalid ("", "unresolved element ref " + aRefName);
    }
    final String sName = XsdNodes.declName (aNode);
    if (sName.isEmpty () || !XsdLexHelper.isNCName (sName))
      throw XsdCompiler.invalid ("src-element",
                                 "local xs:element requires a valid NCName name or a ref (got " +
                                                XsdLexHelper.quote (sName) +
                                                ")");
    String sNS = "";
    final String sForm = XsdNodes.attr (aNode, "form");
    if (sForm != null)
    {
      if ("qualified".equals (sForm))
        sNS = m_aC.m_sTNS;
    }
    else
      if (m_aC.m_bElementQualified)
        sNS = m_aC.m_sTNS;
    final String sLocalTNS = _localTargetNS (aNode);
    if (sLocalTNS != null)
      sNS = sLocalTNS;
    final XsdElementDecl aDecl = new XsdElementDecl (new XsdName (sNS, sName));
    m_aC.fillElementCommon (aDecl, aNode);
    // A local element's fixed/default must fit its type
    m_aC.checkValueConstraint (aDecl.m_aType, aNode);
    aDecl.m_bAbstract = XsdNodes.attrIs (aNode, "abstract", "true");
    aDecl.m_aConstraints = m_aC.m_aIdentity.parseIdentityConstraints (aNode);
    aDecl.m_aAlternatives = parseAlternatives (aNode);
    return aDecl;
  }

  /** go-xslt <code>parseModelGroup</code>: a sequence, choice or all. */
  @NonNull
  private XsdModelGroup _parseModelGroup (@NonNull final XdmNode aNode)
  {
    final XsdModelGroup aMG = new XsdModelGroup ();
    aMG.m_aMsgs = XsdMessages.messagesOf (aNode);
    switch (XsdNodes.localName (aNode))
    {
      case "choice":
        aMG.m_eCompositor = EXsdCompositor.CHOICE;
        break;
      case "all":
        aMG.m_eCompositor = EXsdCompositor.ALL;
        break;
      default:
        aMG.m_eCompositor = EXsdCompositor.SEQUENCE;
        break;
    }
    for (final XdmNode aChild : XsdNodes.children (aNode))
    {
      if (!XsdNodes.isXSElement (aChild))
        continue;
      switch (XsdNodes.localName (aChild))
      {
        case "element":
        case "group":
        case "sequence":
        case "choice":
        case "any":
          aMG.m_aParticles.add (parseParticle (aChild));
          break;
        default:
          break;
      }
    }
    return aMG;
  }

  /**
   * go-xslt <code>parseContentModel</code>: the particle of the model-group or
   * group-reference child, or <code>null</code>.
   */
  @Nullable
  private XsdParticle _parseContentModel (@NonNull final XdmNode aNode)
  {
    for (final XdmNode aChild : XsdNodes.children (aNode))
    {
      if (!XsdNodes.isXSElement (aChild))
        continue;
      switch (XsdNodes.localName (aChild))
      {
        case "sequence":
        case "choice":
        case "all":
        case "group":
          return parseParticle (aChild);
        default:
          break;
      }
    }
    return null;
  }

  /** go-xslt <code>parseSimpleContent</code>. */
  private void _parseSimpleContent (@NonNull final XsdComplexType aCT, @NonNull final XdmNode aSC)
  {
    aCT.m_eKind = EXsdContentKind.SIMPLE;
    final XdmNode aExt = XsdNodes.firstXSChild (aSC, "extension");
    final XdmNode aRes = XsdNodes.firstXSChild (aSC, "restriction");
    XdmNode aBody = aExt;
    aCT.m_sDerivation = XsdDerivation.EXTENSION;
    if (aBody == null)
    {
      aBody = aRes;
      aCT.m_sDerivation = XsdDerivation.RESTRICTION;
    }
    if (aBody == null)
      throw XsdCompiler.invalid ("", "simpleContent without extension/restriction");
    if (aRes != null)
    {
      // The simpleContent restriction orders facets strictly before attributes
      _checkFacetsBeforeAttrs (aBody);
    }
    final IXsdType aBase = m_aC.resolveType (aBody, XsdNodes.attrOrEmpty (aBody, "base"));
    // A simpleContent restriction starting from ur-type content has no value
    // space to restrict (1.1 always, 1.0 when it constrains with facets); an
    // inline xs:simpleType legitimises it
    if (aRes != null && XsdNodes.firstXSChild (aBody, "simpleType") == null)
    {
      final XsdSimpleType aBST = simpleContentType (aBase);
      if (XsdSimpleType.isURType (aBST) && (m_aC.isXsd11 () || _hasFacetChildren (aBody)))
        throw XsdCompiler.invalid ("src-ct.2", "a simpleContent restriction cannot start from xs:anySimpleType content");
    }
    // cos-ct-extends 1.5: a simple base whose {final} contains extension bars
    // a simpleContent extension
    if (aBase instanceof final XsdSimpleType aBST && XsdDerivation.EXTENSION.equals (aCT.m_sDerivation))
      XsdSimpleTypeParser.checkSimpleFinal (aBST, XsdDerivation.EXTENSION);
    aCT.m_aBaseType = aBase;
    final XdmNode aInline = XsdNodes.firstXSChild (aBody, "simpleType");
    if (aInline != null)
    {
      final XsdSimpleType aST = new XsdSimpleType ();
      m_aC.m_aSimpleTypes.parseSimpleTypeInto (aST, aInline);
      // derivation-ok-restriction 5.1.1: provably impossible - a list or union
      // standing where an atomic content type is required
      final XsdSimpleType aBST = simpleContentType (aBase);
      if (aBST != null &&
          XsdDerivation.RESTRICTION.equals (aCT.m_sDerivation) &&
          (aST.m_eVariety == EXsdVariety.LIST || aST.m_eVariety == EXsdVariety.UNION) &&
          aBST.m_eVariety == EXsdVariety.ATOMIC &&
          aBST.resolvePrim () != EAtomicType.ANY_ATOMIC_TYPE)
        throw XsdCompiler.invalid ("derivation-ok-restriction.5.1.1",
                                   "the inline simple type is not derived from the base's content type");
      aCT.m_aSimpleType = aST;
    }
    else
    {
      final XsdSimpleType aBST = simpleContentType (aBase);
      if (aBST != null)
      {
        // A restriction may narrow the base's simple type with facets
        if (aRes != null && _hasFacetChildren (aBody))
        {
          final XsdSimpleType aNST = new XsdSimpleType ();
          aNST.m_aBase = aBST;
          m_aC.m_aSimpleTypes.parseFacets (aNST, aBody);
          aCT.m_aSimpleType = aNST;
        }
        else
          aCT.m_aSimpleType = aBST;
      }
      else
        aCT.m_aSimpleType = XsdSimpleType.anySimpleType ();
    }
    final XsdEffectiveAttributes aAttrs = _parseAttributes (aBody);
    aCT.m_aAttrUses = aAttrs.uses ();
    aCT.m_aAttrWildcard = aAttrs.wildcard ();
    aCT.m_aAsserts = parseAsserts (aBody);
  }

  /** go-xslt <code>parseComplexContent</code>. */
  private void _parseComplexContent (@NonNull final XsdComplexType aCT,
                                     @NonNull final XdmNode aCC,
                                     final boolean bMixedIn)
  {
    boolean bMixed = bMixedIn;
    // src-ct.1 (1.1): @mixed on complexType and complexContent must agree
    if (m_aC.isXsd11 ())
    {
      final String sCCM = XsdNodes.attr (aCC, "mixed");
      final String sCTM = aCC.getParent () == null ? null : XsdNodes.attr (aCC.getParent (), "mixed");
      if (sCCM != null && sCTM != null)
      {
        final String sA = XsdWhiteSpace.collapse (sCCM);
        final String sB = XsdWhiteSpace.collapse (sCTM);
        final boolean bA = "true".equals (sA) || "1".equals (sA);
        final boolean bB = "true".equals (sB) || "1".equals (sB);
        if (bA != bB)
          throw XsdCompiler.invalid ("src-ct.1", "conflicting @mixed on complexType and complexContent");
      }
    }
    if (XsdNodes.boolAttrTrue (aCC, "mixed"))
      bMixed = true;
    final XdmNode aExt = XsdNodes.firstXSChild (aCC, "extension");
    final XdmNode aRes = XsdNodes.firstXSChild (aCC, "restriction");
    XdmNode aBody = aExt;
    aCT.m_sDerivation = XsdDerivation.EXTENSION;
    if (aBody == null)
    {
      aBody = aRes;
      aCT.m_sDerivation = XsdDerivation.RESTRICTION;
    }
    if (aBody == null)
      throw XsdCompiler.invalid ("", "complexContent without extension/restriction");
    final IXsdType aBase = m_aC.resolveType (aBody, XsdNodes.attrOrEmpty (aBody, "base"));
    aCT.m_aBaseType = aBase;
    aCT.m_aParticle = _parseContentModel (aBody);
    XsdComponentChecks.checkElementConsistency (aCT.m_aParticle);
    final XsdEffectiveAttributes aAttrs = _parseAttributes (aBody);
    aCT.m_aAttrUses = aAttrs.uses ();
    aCT.m_aAttrWildcard = aAttrs.wildcard ();
    aCT.m_aAsserts = parseAsserts (aBody);
    aCT.m_eKind = bMixed ? EXsdContentKind.MIXED : EXsdContentKind.ELEMENT_ONLY;
    // An extension that adds only attributes inherits a mixed base's content
    // type verbatim (XSD 3.4.2 clause 2.3.1)
    if (XsdDerivation.EXTENSION.equals (aCT.m_sDerivation) &&
        aCT.m_aParticle == null &&
        !bMixed &&
        aBase instanceof final XsdComplexType aBC &&
        aBC.m_eKind == EXsdContentKind.MIXED)
      aCT.m_eKind = EXsdContentKind.MIXED;
    _applyOpenContent (aCT, aBody);
  }

  /** go-xslt <code>simpleContentType</code>: the simple content type of a type. */
  @Nullable
  static XsdSimpleType simpleContentType (@Nullable final IXsdType aType)
  {
    if (aType instanceof final XsdSimpleType aST)
      return aST;
    if (aType instanceof final XsdComplexType aCT)
      return aCT.m_aSimpleType;
    return null;
  }

  /**
   * go-xslt <code>intersectWildcards</code>: the complete attribute wildcard is
   * the intersection of the own one and those of the referenced groups.
   */
  @Nullable
  static XsdWildcard intersectWildcards (@Nullable final XsdWildcard aA, @Nullable final XsdWildcard aB)
  {
    if (aA == null)
      return aB;
    if (aB == null)
      return aA;
    if (aA.m_aParts.isNotEmpty () || aB.m_aParts.isNotEmpty ())
    {
      // A union operand: leave the already computed result alone
      return aA;
    }
    final XsdWildcard ret = new XsdWildcard ();
    // The stronger requirement wins
    ret.m_sProcess = aA.m_sProcess.equals (aB.m_sProcess) ? aA.m_sProcess : XsdWildcard.PROCESS_STRICT;
    ret.m_sTargetNS = aA.m_sTargetNS;
    ret.m_aNotNS.addAll (aA.m_aNotNS);
    ret.m_aNotNS.addAll (aB.m_aNotNS);
    ret.m_aNotNames.addAll (aA.m_aNotNames);
    ret.m_aNotNames.addAll (aB.m_aNotNames);
    // ##defined survives an intersection when either operand carries it
    ret.m_bNotDefined = aA.m_bNotDefined || aB.m_bNotDefined;
    ret.m_aNotDefNames.addAll (aA.m_aNotDefNames);
    ret.m_aNotDefNames.addAll (aB.m_aNotDefNames);
    if (XsdWildcard.NS_ANY.equals (aA.m_sNsMode))
    {
      ret.m_sNsMode = aB.m_sNsMode;
      ret.m_aNamespaces = aB.m_aNamespaces;
      ret.m_sTargetNS = aB.m_sTargetNS;
    }
    else
      if (XsdWildcard.NS_ANY.equals (aB.m_sNsMode))
      {
        ret.m_sNsMode = aA.m_sNsMode;
        ret.m_aNamespaces = aA.m_aNamespaces;
      }
      else
        if (XsdWildcard.NS_OTHER.equals (aA.m_sNsMode) && XsdWildcard.NS_OTHER.equals (aB.m_sNsMode))
        {
          ret.m_sNsMode = XsdWildcard.NS_OTHER;
          if (!aA.m_sTargetNS.equals (aB.m_sTargetNS))
          {
            // Two different ##other constraints: fall back to the empty set
            ret.m_sNsMode = XsdWildcard.NS_LIST;
            ret.m_aNamespaces = new CommonsArrayList <> ();
          }
        }
        else
          if (XsdWildcard.NS_OTHER.equals (aA.m_sNsMode))
          {
            ret.m_sNsMode = XsdWildcard.NS_LIST;
            ret.m_aNamespaces = _excludeNS (aB.m_aNamespaces, aA.m_sTargetNS);
          }
          else
            if (XsdWildcard.NS_OTHER.equals (aB.m_sNsMode))
            {
              ret.m_sNsMode = XsdWildcard.NS_LIST;
              ret.m_aNamespaces = _excludeNS (aA.m_aNamespaces, aB.m_sTargetNS);
            }
            else
            {
              ret.m_sNsMode = XsdWildcard.NS_LIST;
              ret.m_aNamespaces = new CommonsArrayList <> ();
              for (final String sNS : aA.m_aNamespaces)
                if (aB.m_aNamespaces.contains (sNS))
                  ret.m_aNamespaces.add (sNS);
            }
    return ret;
  }

  /**
   * The parse entry of an XSD 1.1 xs:assert / xs:assertion (go-xslt
   * <code>parseAssert</code>). Phase V8.
   */
  @NonNull
  XsdAssertion parseAssert (@NonNull final XdmNode aNode)
  {
    throw XsdCompiler.unsupported11 ("xs:assert / xs:assertion");
  }

  /**
   * go-xslt <code>parseAsserts</code>: the xs:assert children of a complex type
   * body. Any assertion is an XSD 1.1 feature not implemented yet.
   */
  @NonNull
  ICommonsList <XsdAssertion> parseAsserts (@NonNull final XdmNode aNode)
  {
    final ICommonsList <XsdAssertion> ret = new CommonsArrayList <> ();
    for (final XdmNode aChild : XsdNodes.children (aNode))
      if (XsdNodes.isXS (aChild, "assert"))
        ret.add (parseAssert (aChild));
    if (ret.isNotEmpty ())
      m_aC.m_aSchema.m_bHasAsserts = true;
    return ret;
  }

  /**
   * go-xslt <code>parseAlternatives</code>: the xs:alternative children of an
   * element declaration (XSD 1.1 conditional type assignment, phase V8).
   */
  @NonNull
  ICommonsList <XsdTypeAlternative> parseAlternatives (@NonNull final XdmNode aNode)
  {
    for (final XdmNode aChild : XsdNodes.children (aNode))
      if (XsdNodes.isXS (aChild, "alternative"))
        throw XsdCompiler.unsupported11 ("xs:alternative (conditional type assignment)");
    return new CommonsArrayList <> ();
  }

  /** go-xslt <code>parseWildcard</code>: an xs:any or xs:anyAttribute. */
  @NonNull
  XsdWildcard parseWildcard (@NonNull final XdmNode aNode)
  {
    final XsdWildcard aWC = new XsdWildcard ();
    aWC.m_sTargetNS = m_aC.m_sTNS;
    aWC.m_sProcess = XsdWildcard.PROCESS_STRICT;
    final String sProcess = XsdNodes.attr (aNode, "processContents");
    if (sProcess != null)
      aWC.m_sProcess = sProcess;
    final String sNSC = XsdNodes.attr (aNode, "namespace");
    final String sNotNS = XsdNodes.attr (aNode, "notNamespace");
    final String sLocal = XsdNodes.localName (aNode);
    if (sNSC != null && sNotNS != null)
      throw XsdCompiler.invalid ("", "xs:" + sLocal + " must not carry both namespace and notNamespace");
    if (sNSC != null && sNSC.isEmpty ())
    {
      // An explicit empty list admits nothing
      aWC.m_sNsMode = XsdWildcard.NS_LIST;
    }
    else
      if (sNSC == null || "##any".equals (sNSC))
        aWC.m_sNsMode = XsdWildcard.NS_ANY;
      else
        if ("##other".equals (sNSC))
          aWC.m_sNsMode = XsdWildcard.NS_OTHER;
        else
        {
          aWC.m_sNsMode = XsdWildcard.NS_LIST;
          for (final String sToken : XsdLexHelper.xmlFields (sNSC))
          {
            if ("##targetNamespace".equals (sToken))
              aWC.m_aNamespaces.add (m_aC.m_sTNS);
            else
              if ("##local".equals (sToken))
                aWC.m_aNamespaces.add ("");
              else
                if (sToken.startsWith ("##"))
                  throw XsdCompiler.invalid ("", "invalid wildcard namespace token " + XsdLexHelper.quote (sToken));
                else
                  aWC.m_aNamespaces.add (sToken);
          }
        }
    if (sNotNS != null)
    {
      // XSD 1.1 @notNamespace: everything except these namespaces
      final ICommonsList <String> aTokens = XsdLexHelper.xmlFields (sNotNS);
      if (aTokens.isEmpty ())
        throw XsdCompiler.invalid ("", "notNamespace must not be empty");
      for (final String sToken : aTokens)
      {
        if ("##targetNamespace".equals (sToken))
          aWC.m_aNotNS.add (m_aC.m_sTNS);
        else
          if ("##local".equals (sToken))
            aWC.m_aNotNS.add ("");
          else
            if (sToken.startsWith ("##"))
              throw XsdCompiler.invalid ("", "invalid notNamespace token " + XsdLexHelper.quote (sToken));
            else
              aWC.m_aNotNS.add (sToken);
      }
    }
    final String sNotQName = XsdNodes.attr (aNode, "notQName");
    if (sNotQName != null)
    {
      final boolean bAttrWildcard = "anyAttribute".equals (sLocal);
      for (final String sToken : XsdLexHelper.xmlFields (sNotQName))
      {
        if ("##defined".equals (sToken))
        {
          // Every global declaration of the schema; pass 1 registered them all
          aWC.m_bNotDefined = true;
          if (bAttrWildcard)
            aWC.m_aNotDefNames.addAll (m_aC.m_aSchema.m_aAttributes.keySet ());
          else
            aWC.m_aNotDefNames.addAll (m_aC.m_aSchema.m_aElements.keySet ());
        }
        else
          if ("##definedSibling".equals (sToken))
          {
            if (bAttrWildcard)
              throw XsdCompiler.invalid ("", "##definedSibling is not allowed on xs:anyAttribute");
            aWC.m_bNotSibling = true;
          }
          else
            if (sToken.startsWith ("##"))
              throw XsdCompiler.invalid ("", "invalid notQName token " + XsdLexHelper.quote (sToken));
            else
            {
              if (!XsdLexHelper.validQNameLexical (sToken))
                throw XsdCompiler.invalid ("", "invalid QName in notQName " + XsdLexHelper.quote (sToken));
              final int i = sToken.indexOf (':');
              if (i >= 0 && XsdNodes.lookupPrefix (aNode, sToken.substring (0, i)) == null)
                throw XsdCompiler.invalid ("", "undeclared prefix in notQName " + XsdLexHelper.quote (sToken));
              final XsdName n = XsdNodes.resolveQName (aNode, sToken);
              // A name the wildcard would not admit anyway cannot be excluded
              if (!aWC.admitsNamespace (n.space ()))
                throw XsdCompiler.invalid ("",
                                           "notQName " +
                                               XsdLexHelper.quote (sToken) +
                                               " is not in a namespace the wildcard allows");
              aWC.m_aNotNames.add (n);
            }
      }
    }
    return aWC;
  }

  /**
   * go-xslt <code>parseParticle</code>: one particle (model group, group
   * reference, element or wildcard) with its occurrence bounds.
   */
  @NonNull
  XsdParticle parseParticle (@NonNull final XdmNode aNode)
  {
    m_aC.enterNesting ();
    try
    {
      final int [] aOccurs = _occursOf (aNode);
      final int nMin = aOccurs[0];
      final int nMax = aOccurs[1];
      final String sLocal = XsdNodes.localName (aNode);
      switch (sLocal)
      {
        case "sequence":
        case "choice":
        case "all":
          return new XsdParticle (nMin, nMax, _parseModelGroup (aNode));
        case "group":
        {
          final String sRef = XsdNodes.attr (aNode, "ref");
          if (sRef == null)
            throw XsdCompiler.invalid ("", "group without a ref");
          final XsdName aGroupName = XsdNodes.resolveQName (aNode, sRef);
          XsdModelGroup aGroup = m_aC.m_aGroups.get (aGroupName);
          if (aGroup != null && !m_aC.reachableNS (aGroupName.space ()))
            throw XsdCompiler.invalid ("src-resolve",
                                       "group " + aGroupName + " is in a namespace this schema does not import");
          final XsdModelGroup aOrig = m_aC.m_aRedefineGroup.get (aGroupName);
          if (aOrig != null && XsdCompiler.withinRedefine (aNode))
          {
            // Reference to a redefined group from inside the redefine
            aGroup = aOrig;
          }
          if (aGroup == null)
          {
            // Chameleon repair
            final XsdName aAlt = m_aC.chamAlt (aGroupName);
            if (aAlt != null)
              aGroup = m_aC.m_aGroups.get (aAlt);
          }
          if (aGroup == null)
            throw XsdCompiler.invalid ("", "unresolved group ref " + sRef);
          return new XsdParticle (nMin, nMax, aGroup);
        }
        case "element":
        {
          final XsdElementDecl aDecl = _parseLocalElement (aNode);
          final XsdParticle ret = new XsdParticle (nMin, nMax, aDecl);
          final ICommonsList <XsdDocumentation> aDocs = m_aC.docsOf (aNode);
          if (aDocs.isNotEmpty ())
            ret.m_aDocs = aDocs;
          return ret;
        }
        case "any":
          return new XsdParticle (nMin, nMax, parseWildcard (aNode));
        default:
          throw XsdCompiler.invalid ("", "unexpected particle xs:" + sLocal);
      }
    }
    finally
    {
      m_aC.leaveNesting ();
    }
  }

  /** go-xslt <code>parseComplexTypeInto</code>: fill a complex type from an xs:complexType element. */
  void parseComplexTypeInto (@NonNull final XsdComplexType aCT, @NonNull final XdmNode aNode)
  {
    m_aC.enterNesting ();
    try
    {
      _checkComplexTypeStructure (aNode);
      // Named and anonymous alike, for the whole-schema checks
      m_aC.m_aAllCTs.add (aCT);
      aCT.m_aDocs = m_aC.docsOf (aNode);
      aCT.m_bAbstract = XsdNodes.attrIs (aNode, "abstract", "true");
      final String sFinal = XsdNodes.attr (aNode, "final");
      if (sFinal != null)
        aCT.m_aFinal = XsdDerivation.parseBlockSet (sFinal);
      else
        if (!m_aC.m_sFinalDefault.isEmpty ())
          aCT.m_aFinal = XsdDerivation.parseBlockSet (m_aC.m_sFinalDefault);
      final String sBlock = XsdNodes.attr (aNode, "block");
      if (sBlock != null)
        aCT.m_aBlock = XsdDerivation.parseBlockSet (sBlock);
      else
        if (!m_aC.m_sBlockDefault.isEmpty ())
          aCT.m_aBlock = XsdDerivation.parseBlockSet (m_aC.m_sBlockDefault);
      final boolean bMixed = XsdNodes.boolAttrTrue (aNode, "mixed");
      final XdmNode aSC = XsdNodes.firstXSChild (aNode, "simpleContent");
      if (aSC != null)
      {
        _parseSimpleContent (aCT, aSC);
        _applyDefaultAttrs (aCT, aNode);
        return;
      }
      final XdmNode aCC = XsdNodes.firstXSChild (aNode, "complexContent");
      if (aCC != null)
      {
        _parseComplexContent (aCT, aCC, bMixed);
        _applyDefaultAttrs (aCT, aNode);
        return;
      }
      // Shorthand: a direct model group plus attributes, an implicit
      // restriction of xs:anyType
      final XsdParticle aParticle = _parseContentModel (aNode);
      aCT.m_aParticle = aParticle;
      XsdComponentChecks.checkElementConsistency (aParticle);
      final XsdEffectiveAttributes aAttrs = _parseAttributes (aNode);
      aCT.m_aAttrUses = aAttrs.uses ();
      aCT.m_aAttrWildcard = aAttrs.wildcard ();
      aCT.m_aAsserts = parseAsserts (aNode);
      if (bMixed)
        aCT.m_eKind = EXsdContentKind.MIXED;
      else
        aCT.m_eKind = aParticle != null ? EXsdContentKind.ELEMENT_ONLY : EXsdContentKind.EMPTY;
      _applyOpenContent (aCT, aNode);
      _applyDefaultAttrs (aCT, aNode);
    }
    finally
    {
      m_aC.leaveNesting ();
    }
  }

  /** go-xslt <code>parseGroupBody</code>: the model group of a named group definition. */
  @NonNull
  XsdModelGroup parseGroupBody (@NonNull final XdmNode aNode)
  {
    for (final XdmNode aChild : XsdNodes.children (aNode))
      if (XsdNodes.isXS (aChild, "sequence") || XsdNodes.isXS (aChild, "choice") || XsdNodes.isXS (aChild, "all"))
        return _parseModelGroup (aChild);
    throw XsdCompiler.invalid ("", "group without a model group");
  }

  /**
   * go-xslt <code>parseAttrGroupBody</code>: the uses and wildcard of an
   * attribute group. Prohibited uses contribute nothing through a group
   * reference (attZ015).
   */
  @NonNull
  XsdAttributeGroup parseAttrGroupBody (@NonNull final XdmNode aNode)
  {
    final XsdEffectiveAttributes aAttrs = _parseAttributes (aNode);
    final ICommonsList <XsdAttributeUse> aKept = new CommonsArrayList <> ();
    for (final XsdAttributeUse aUse : aAttrs.uses ())
      if (!aUse.m_bProhibited)
        aKept.add (aUse);
    return new XsdAttributeGroup (aKept, aAttrs.wildcard ());
  }
}
