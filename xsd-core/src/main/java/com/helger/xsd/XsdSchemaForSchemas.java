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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.ops.XsdLexical;
import com.helger.xpath.xdm.XdmNode;

/**
 * Structural validation of schema documents against the schema for schemas
 * (go-xslt <code>schemavalid.go</code>, and <code>validateAttrValues</code> /
 * <code>validBlockFinal</code> of <code>structure.go</code>): the attributes
 * each XSD element admits and their fixed vocabularies, the permitted child
 * elements and their order, occurrence values, and the syntactic Schema
 * Representation Constraints that need no component. The allowed attribute
 * sets are the union of XSD 1.0 and 1.1; foreign-namespace attributes are
 * always allowed. Both walks are iterative.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdSchemaForSchemas
{
  /** The legal values of the fixed-vocabulary attributes. */
  private static final ICommonsMap <String, ICommonsSet <String>> ENUM_ATTR_VALUES = new CommonsHashMap <> ();
  /** The boolean-valued attributes. */
  private static final ICommonsSet <String> BOOL_ATTRS = new CommonsHashSet <> ("abstract",
                                                                                "nillable",
                                                                                "mixed",
                                                                                "inheritable");
  /** The allowed unqualified attributes per XSD element (id everywhere). */
  private static final ICommonsMap <String, ICommonsSet <String>> ALLOWED_ATTRS = new CommonsHashMap <> ();
  /** XSD 1.1 constructs; their appearance in a 1.0 schema is an error. */
  private static final ICommonsSet <String> XSD11_ONLY_ELEMENTS = new CommonsHashSet <> ("openContent",
                                                                                         "defaultOpenContent",
                                                                                         "assert",
                                                                                         "alternative");
  /**
   * The permitted child elements of XSD elements with an unambiguous content
   * model (annotation added everywhere except where noted); elements absent
   * here are not child-checked.
   */
  private static final ICommonsMap <String, ICommonsSet <String>> ALLOWED_CHILDREN = new CommonsHashMap <> ();
  /** The constraining facets plus annotation and simpleType. */
  private static final ICommonsSet <String> FACET_CHILDREN = _cset ("simpleType",
                                                                    "minExclusive",
                                                                    "minInclusive",
                                                                    "maxExclusive",
                                                                    "maxInclusive",
                                                                    "totalDigits",
                                                                    "fractionDigits",
                                                                    "length",
                                                                    "minLength",
                                                                    "maxLength",
                                                                    "enumeration",
                                                                    "whiteSpace",
                                                                    "pattern",
                                                                    "assertion",
                                                                    "explicitTimezone");
  /** Per parent element, the allowed children of a restriction/extension below it. */
  private static final ICommonsMap <String, ICommonsMap <String, ICommonsSet <String>>> DERIV_CHILDREN_BY_PARENT = new CommonsHashMap <> ();
  /** The XSD elements whose @name must be an NCName. */
  private static final ICommonsSet <String> NCNAME_ELEMS = new CommonsHashSet <> ("element",
                                                                                  "attribute",
                                                                                  "simpleType",
                                                                                  "complexType",
                                                                                  "group",
                                                                                  "attributeGroup",
                                                                                  "notation",
                                                                                  "key",
                                                                                  "keyref",
                                                                                  "unique");

  static
  {
    ENUM_ATTR_VALUES.put ("form", new CommonsHashSet <> ("qualified", "unqualified"));
    ENUM_ATTR_VALUES.put ("elementFormDefault", new CommonsHashSet <> ("qualified", "unqualified"));
    ENUM_ATTR_VALUES.put ("attributeFormDefault", new CommonsHashSet <> ("qualified", "unqualified"));
    ENUM_ATTR_VALUES.put ("use", new CommonsHashSet <> ("optional", "required", "prohibited"));
    ENUM_ATTR_VALUES.put ("processContents", new CommonsHashSet <> ("strict", "lax", "skip"));

    ALLOWED_ATTRS.put ("schema",
                       _set ("targetNamespace",
                             "version",
                             "elementFormDefault",
                             "attributeFormDefault",
                             "blockDefault",
                             "finalDefault",
                             "defaultAttributes",
                             "xpathDefaultNamespace",
                             "lang"));
    ALLOWED_ATTRS.put ("element",
                       _set ("name",
                             "ref",
                             "type",
                             "minOccurs",
                             "maxOccurs",
                             "default",
                             "fixed",
                             "nillable",
                             "abstract",
                             "substitutionGroup",
                             "block",
                             "final",
                             "form",
                             "targetNamespace"));
    ALLOWED_ATTRS.put ("attribute",
                       _set ("name", "ref", "type", "use", "default", "fixed", "form", "targetNamespace", "inheritable"));
    ALLOWED_ATTRS.put ("complexType", _set ("name", "abstract", "mixed", "block", "final", "defaultAttributesApply"));
    ALLOWED_ATTRS.put ("simpleType", _set ("name", "final"));
    ALLOWED_ATTRS.put ("complexContent", _set ("mixed"));
    ALLOWED_ATTRS.put ("simpleContent", _set ());
    ALLOWED_ATTRS.put ("restriction", _set ("base"));
    ALLOWED_ATTRS.put ("extension", _set ("base"));
    ALLOWED_ATTRS.put ("sequence", _set ("minOccurs", "maxOccurs"));
    ALLOWED_ATTRS.put ("choice", _set ("minOccurs", "maxOccurs"));
    ALLOWED_ATTRS.put ("all", _set ("minOccurs", "maxOccurs"));
    ALLOWED_ATTRS.put ("group", _set ("name", "ref", "minOccurs", "maxOccurs"));
    ALLOWED_ATTRS.put ("attributeGroup", _set ("name", "ref"));
    ALLOWED_ATTRS.put ("any",
                       _set ("namespace", "processContents", "minOccurs", "maxOccurs", "notNamespace", "notQName"));
    ALLOWED_ATTRS.put ("anyAttribute", _set ("namespace", "processContents", "notNamespace", "notQName"));
    ALLOWED_ATTRS.put ("key", _set ("name", "ref"));
    ALLOWED_ATTRS.put ("keyref", _set ("name", "ref", "refer"));
    ALLOWED_ATTRS.put ("unique", _set ("name", "ref"));
    ALLOWED_ATTRS.put ("selector", _set ("xpath", "xpathDefaultNamespace"));
    ALLOWED_ATTRS.put ("field", _set ("xpath", "xpathDefaultNamespace"));
    ALLOWED_ATTRS.put ("annotation", _set ());
    ALLOWED_ATTRS.put ("appinfo", _set ("source"));
    ALLOWED_ATTRS.put ("documentation", _set ("source", "lang"));
    ALLOWED_ATTRS.put ("notation", _set ("name", "public", "system"));
    ALLOWED_ATTRS.put ("import", _set ("namespace", "schemaLocation"));
    ALLOWED_ATTRS.put ("include", _set ("schemaLocation"));
    ALLOWED_ATTRS.put ("redefine", _set ("schemaLocation"));
    ALLOWED_ATTRS.put ("override", _set ("schemaLocation"));
    ALLOWED_ATTRS.put ("list", _set ("itemType"));
    ALLOWED_ATTRS.put ("union", _set ("memberTypes"));
    ALLOWED_ATTRS.put ("enumeration", _set ("value"));
    ALLOWED_ATTRS.put ("pattern", _set ("value"));
    for (final String sFacet : new String [] { "whiteSpace",
                                               "length",
                                               "minLength",
                                               "maxLength",
                                               "minInclusive",
                                               "maxInclusive",
                                               "minExclusive",
                                               "maxExclusive",
                                               "totalDigits",
                                               "fractionDigits",
                                               "explicitTimezone" })
      ALLOWED_ATTRS.put (sFacet, _set ("value", "fixed"));
    ALLOWED_ATTRS.put ("assert", _set ("test", "xpathDefaultNamespace"));
    ALLOWED_ATTRS.put ("assertion", _set ("test", "xpathDefaultNamespace"));
    ALLOWED_ATTRS.put ("alternative", _set ("test", "type", "xpathDefaultNamespace"));
    ALLOWED_ATTRS.put ("openContent", _set ("mode"));
    ALLOWED_ATTRS.put ("defaultOpenContent", _set ("mode", "appliesToEmpty"));

    ALLOWED_CHILDREN.put ("redefine", _cset ("simpleType", "complexType", "group", "attributeGroup"));
    ALLOWED_CHILDREN.put ("element", _cset ("simpleType", "complexType", "key", "keyref", "unique", "alternative"));
    ALLOWED_CHILDREN.put ("attribute", _cset ("simpleType"));
    ALLOWED_CHILDREN.put ("simpleType", _cset ("restriction", "list", "union"));
    ALLOWED_CHILDREN.put ("complexType",
                          _cset ("simpleContent",
                                 "complexContent",
                                 "group",
                                 "sequence",
                                 "choice",
                                 "all",
                                 "attribute",
                                 "attributeGroup",
                                 "anyAttribute",
                                 "assert",
                                 "openContent"));
    ALLOWED_CHILDREN.put ("complexContent", _cset ("restriction", "extension"));
    ALLOWED_CHILDREN.put ("simpleContent", _cset ("restriction", "extension"));
    ALLOWED_CHILDREN.put ("sequence", _cset ("element", "group", "sequence", "choice", "any"));
    ALLOWED_CHILDREN.put ("choice", _cset ("element", "group", "sequence", "choice", "any"));
    ALLOWED_CHILDREN.put ("all", _cset ("element", "group", "any"));
    ALLOWED_CHILDREN.put ("group", _cset ("sequence", "choice", "all"));
    ALLOWED_CHILDREN.put ("attributeGroup", _cset ("attribute", "attributeGroup", "anyAttribute"));
    ALLOWED_CHILDREN.put ("key", _cset ("selector", "field"));
    ALLOWED_CHILDREN.put ("keyref", _cset ("selector", "field"));
    ALLOWED_CHILDREN.put ("unique", _cset ("selector", "field"));
    ALLOWED_CHILDREN.put ("list", _cset ("simpleType"));
    ALLOWED_CHILDREN.put ("union", _cset ("simpleType"));
    ALLOWED_CHILDREN.put ("selector", _cset ());
    ALLOWED_CHILDREN.put ("field", _cset ());
    ALLOWED_CHILDREN.put ("any", _cset ());
    ALLOWED_CHILDREN.put ("anyAttribute", _cset ());
    ALLOWED_CHILDREN.put ("openContent", _cset ("any"));
    ALLOWED_CHILDREN.put ("defaultOpenContent", _cset ("any"));
    ALLOWED_CHILDREN.put ("notation", _cset ());
    // xs:annotation holds only appinfo/documentation, never another annotation
    ALLOWED_CHILDREN.put ("annotation", new CommonsHashSet <> ("appinfo", "documentation"));
    ALLOWED_CHILDREN.put ("include", _cset ());
    ALLOWED_CHILDREN.put ("import", _cset ());
    ALLOWED_CHILDREN.put ("schema",
                          _cset ("include",
                                 "import",
                                 "redefine",
                                 "override",
                                 "element",
                                 "attribute",
                                 "notation",
                                 "simpleType",
                                 "complexType",
                                 "group",
                                 "attributeGroup",
                                 "defaultOpenContent"));
    for (final String sFacet : new String [] { "enumeration",
                                               "pattern",
                                               "whiteSpace",
                                               "length",
                                               "minLength",
                                               "maxLength",
                                               "minInclusive",
                                               "maxInclusive",
                                               "minExclusive",
                                               "maxExclusive",
                                               "totalDigits",
                                               "fractionDigits" })
      ALLOWED_CHILDREN.put (sFacet, _cset ());

    final ICommonsMap <String, ICommonsSet <String>> aSimpleType = new CommonsHashMap <> ();
    aSimpleType.put ("restriction", FACET_CHILDREN);
    DERIV_CHILDREN_BY_PARENT.put ("simpleType", aSimpleType);
    final ICommonsMap <String, ICommonsSet <String>> aSimpleContent = new CommonsHashMap <> ();
    final ICommonsSet <String> aSCRestriction = new CommonsHashSet <> (FACET_CHILDREN);
    aSCRestriction.addAll (_cset ("attribute", "attributeGroup", "anyAttribute", "assert"));
    aSimpleContent.put ("restriction", aSCRestriction);
    aSimpleContent.put ("extension", _cset ("attribute", "attributeGroup", "anyAttribute", "assert"));
    DERIV_CHILDREN_BY_PARENT.put ("simpleContent", aSimpleContent);
    final ICommonsMap <String, ICommonsSet <String>> aComplexContent = new CommonsHashMap <> ();
    final ICommonsSet <String> aCC = _cset ("group",
                                            "sequence",
                                            "choice",
                                            "all",
                                            "attribute",
                                            "attributeGroup",
                                            "anyAttribute",
                                            "assert",
                                            "openContent");
    aComplexContent.put ("restriction", aCC);
    aComplexContent.put ("extension", aCC);
    DERIV_CHILDREN_BY_PARENT.put ("complexContent", aComplexContent);
  }

  @PresentForCodeCoverage
  private static final XsdSchemaForSchemas INSTANCE = new XsdSchemaForSchemas ();

  private XsdSchemaForSchemas ()
  {}

  @NonNull
  private static ICommonsSet <String> _set (@NonNull final String... aNames)
  {
    final ICommonsSet <String> ret = new CommonsHashSet <> (aNames);
    ret.add ("id");
    return ret;
  }

  @NonNull
  private static ICommonsSet <String> _cset (@NonNull final String... aNames)
  {
    final ICommonsSet <String> ret = new CommonsHashSet <> (aNames);
    ret.add ("annotation");
    return ret;
  }

  @NonNull
  private static ICommonsSet <String> _blockFinalSet (@NonNull final String... aNames)
  {
    return new CommonsHashSet <> (aNames);
  }

  /**
   * go-xslt <code>validBlockFinal</code>: <code>#all</code>, or a list of the
   * derivation-control keywords allowed for that element and attribute.
   */
  private static boolean _validBlockFinal (@NonNull final String sElem,
                                           @NonNull final String sAttr,
                                           @NonNull final String v,
                                           @NonNull final EXsdVersion eVersion)
  {
    if ("#all".equals (v))
      return true;
    final ICommonsSet <String> aAllowed;
    switch (sElem)
    {
      case "element":
        aAllowed = "block".equals (sAttr) ? _blockFinalSet ("extension", "restriction", "substitution")
                                         : _blockFinalSet ("extension", "restriction");
        break;
      case "complexType":
        aAllowed = _blockFinalSet ("extension", "restriction");
        break;
      case "simpleType":
        // XSD 1.1 widened simpleDerivationSet by extension (bug 2074)
        aAllowed = eVersion == EXsdVersion.V10 ? _blockFinalSet ("restriction", "list", "union")
                                               : _blockFinalSet ("restriction", "list", "union", "extension");
        break;
      case "schema":
        aAllowed = "blockDefault".equals (sAttr) ? _blockFinalSet ("extension", "restriction", "substitution")
                                                 : _blockFinalSet ("extension", "restriction", "list", "union");
        break;
      default:
        aAllowed = _blockFinalSet ("extension", "restriction", "substitution", "list", "union");
        break;
    }
    for (final String sToken : XsdLexHelper.xmlFields (v))
      if (!aAllowed.contains (sToken))
        return false;
    return true;
  }

  private static void _checkAttrValues (@NonNull final XdmNode aElement,
                                        @NonNull final ICommonsSet <String> aSeenIDs,
                                        @NonNull final EXsdVersion eVersion)
  {
    final String sElem = XsdNodes.localName (aElement);
    for (final XdmNode a : XsdNodes.attributes (aElement))
    {
      final String sNS = XsdNodes.namespaceUri (a);
      final String sLocal = XsdNodes.localName (a);
      if (XsdConstants.NS_XS.equals (sNS))
      {
        // An attribute in the XSD namespace is never legal on a schema element
        throw XsdCompiler.invalid ("", "attribute {xsd}" + sLocal + " is not allowed on xs:" + sElem);
      }
      if (!sNS.isEmpty ())
        continue;
      final String v = XsdWhiteSpace.collapse (a.getValue ());
      if ("id".equals (sLocal))
      {
        if (!XsdLexHelper.isNCName (v))
          throw XsdCompiler.invalid ("", "invalid id value " + XsdLexHelper.quote (a.getValue ()));
        if (!aSeenIDs.add (v))
          throw XsdCompiler.invalid ("", "duplicate id " + XsdLexHelper.quote (v));
      }
      else
        if (BOOL_ATTRS.contains (sLocal))
        {
          if (!"true".equals (v) && !"false".equals (v) && !"1".equals (v) && !"0".equals (v))
            throw XsdCompiler.invalid ("", "invalid boolean value " + XsdLexHelper.quote (a.getValue ()) + " for @" + sLocal);
        }
        else
          if ("block".equals (sLocal) ||
              "final".equals (sLocal) ||
              "blockDefault".equals (sLocal) ||
              "finalDefault".equals (sLocal))
          {
            if (!_validBlockFinal (sElem, sLocal, v, eVersion))
              throw XsdCompiler.invalid ("", "invalid value " + XsdLexHelper.quote (a.getValue ()) + " for @" + sLocal);
          }
          else
          {
            final ICommonsSet <String> aAllowed = ENUM_ATTR_VALUES.get (sLocal);
            if (aAllowed != null && !aAllowed.contains (v))
              throw XsdCompiler.invalid ("", "invalid value " + XsdLexHelper.quote (a.getValue ()) + " for @" + sLocal);
          }
    }
  }

  /** go-xslt <code>occursValue</code>: a nonNegativeInteger, large values saturate. */
  private static long _occursValue (@NonNull final String s)
  {
    if (s.isEmpty ())
      return -1;
    int i = s.charAt (0) == '+' ? 1 : 0;
    if (i >= s.length ())
      return -1;
    long n = 0;
    for (; i < s.length (); i++)
    {
      final char c = s.charAt (i);
      if (c < '0' || c > '9')
        return -1;
      if (n < 1L << 60)
        n = n * 10 + (c - '0');
    }
    return Math.min (n, 1L << 60);
  }

  /**
   * go-xslt <code>checkOccurs</code>: minOccurs a nonNegativeInteger,
   * maxOccurs one or <code>unbounded</code>, minOccurs not above maxOccurs.
   */
  private static void _checkOccurs (@NonNull final XdmNode aElement)
  {
    long nMin = 1;
    long nMax = 1;
    final String sMin = XsdNodes.attr (aElement, "minOccurs");
    if (sMin != null)
    {
      nMin = _occursValue (XsdWhiteSpace.collapse (sMin));
      if (nMin < 0)
        throw XsdCompiler.invalid ("", "invalid minOccurs " + XsdLexHelper.quote (sMin));
    }
    final String sMax = XsdNodes.attr (aElement, "maxOccurs");
    if (sMax != null)
    {
      final String sCV = XsdWhiteSpace.collapse (sMax);
      if ("unbounded".equals (sCV))
        nMax = 1L << 62;
      else
      {
        nMax = _occursValue (sCV);
        if (nMax < 0)
          throw XsdCompiler.invalid ("", "invalid maxOccurs " + XsdLexHelper.quote (sMax));
      }
    }
    if (nMin > nMax)
      throw XsdCompiler.invalid ("", "minOccurs (" + nMin + ") exceeds maxOccurs");
  }

  private static boolean _hasXSChild (@NonNull final XdmNode aElement, @NonNull final String sLocal)
  {
    return XsdNodes.firstXSChild (aElement, sLocal) != null;
  }

  /** The S4S consistency of an (default) open content element. */
  private static void _checkOpenContentElement (@NonNull final XdmNode aEl, @NonNull final String sElem)
  {
    String sMode = "interleave";
    final String sModeAttr = XsdNodes.attr (aEl, "mode");
    if (sModeAttr != null)
      sMode = XsdWhiteSpace.collapse (sModeAttr);
    if (!"none".equals (sMode) && !"interleave".equals (sMode) && !"suffix".equals (sMode))
      throw XsdCompiler.invalid ("", "invalid xs:" + sElem + " mode " + XsdLexHelper.quote (sMode));
    if ("defaultOpenContent".equals (sElem) && "none".equals (sMode))
      throw XsdCompiler.invalid ("", "xs:defaultOpenContent mode may not be \"none\"");
    final boolean bHasAny = _hasXSChild (aEl, "any");
    if ("none".equals (sMode) && bHasAny)
      throw XsdCompiler.invalid ("", "xs:openContent with mode=\"none\" must not have an xs:any child");
    if (!"none".equals (sMode) && !bHasAny)
      throw XsdCompiler.invalid ("", "xs:" + sElem + " with mode=" + XsdLexHelper.quote (sMode) + " requires an xs:any child");
    // Inside a complexContent restriction an openContent needs a particle
    // sibling to attach to
    final XdmNode aParent = aEl.getParent ();
    if ("openContent".equals (sElem) && XsdNodes.isXS (aParent, "restriction"))
    {
      boolean bHasParticle = false;
      for (final XdmNode aSib : XsdNodes.children (aParent))
        if (XsdNodes.isXS (aSib, "group") ||
            XsdNodes.isXS (aSib, "sequence") ||
            XsdNodes.isXS (aSib, "choice") ||
            XsdNodes.isXS (aSib, "all"))
        {
          bHasParticle = true;
          break;
        }
      if (!bHasParticle)
        throw XsdCompiler.invalid ("", "xs:openContent in a restriction requires a sibling particle");
    }
    // The open-content xs:any is not a particle: no occurrence attributes
    for (final XdmNode aChild : XsdNodes.children (aEl))
    {
      if (!XsdNodes.isXS (aChild, "any"))
        continue;
      if (XsdNodes.hasAttr (aChild, "minOccurs"))
        throw XsdCompiler.invalid ("", "minOccurs is not allowed on an open-content xs:any");
      if (XsdNodes.hasAttr (aChild, "maxOccurs"))
        throw XsdCompiler.invalid ("", "maxOccurs is not allowed on an open-content xs:any");
    }
  }

  /**
   * The children of xs:schema/redefine/override: a global definition is no
   * reference and has no local-only attributes; xs:defaultOpenContent precedes
   * every component declaration.
   */
  private static void _checkTopLevelChildren (@NonNull final XdmNode aEl)
  {
    boolean bSeenComponent = false;
    for (final XdmNode aChild : XsdNodes.children (aEl))
    {
      if (!XsdNodes.isXSElement (aChild))
        continue;
      final String sLocal = XsdNodes.localName (aChild);
      switch (sLocal)
      {
        case "element":
        case "attribute":
        case "notation":
        case "simpleType":
        case "complexType":
        case "group":
        case "attributeGroup":
          bSeenComponent = true;
          break;
        case "defaultOpenContent":
          if (bSeenComponent)
            throw XsdCompiler.invalid ("", "xs:defaultOpenContent must precede the schema's component declarations");
          break;
        default:
          break;
      }
      switch (sLocal)
      {
        case "element":
        case "attribute":
        case "group":
        case "attributeGroup":
          if (XsdNodes.hasAttr (aChild, "ref"))
            throw XsdCompiler.invalid ("", "a top-level xs:" + sLocal + " must be a definition, not a ref");
          if (XsdNodes.hasAttr (aChild, "minOccurs"))
            throw XsdCompiler.invalid ("", "minOccurs is not allowed on a top-level xs:" + sLocal);
          if (XsdNodes.hasAttr (aChild, "maxOccurs"))
            throw XsdCompiler.invalid ("", "maxOccurs is not allowed on a top-level xs:" + sLocal);
          if (("element".equals (sLocal) || "attribute".equals (sLocal)) && XsdNodes.hasAttr (aChild, "form"))
            throw XsdCompiler.invalid ("", "form is not allowed on a top-level xs:" + sLocal);
          if ("attribute".equals (sLocal) && XsdNodes.hasAttr (aChild, "use"))
            throw XsdCompiler.invalid ("", "use is not allowed on a top-level attribute");
          break;
        default:
          break;
      }
    }
  }

  /**
   * go-xslt <code>checkDeclConstraints</code>: the syntactic Schema
   * Representation Constraints of one XSD element that are independent of the
   * wider schema.
   */
  private static void _checkDeclConstraints (@NonNull final XdmNode aEl, @NonNull final EXsdVersion eVersion)
  {
    final String sElem = XsdNodes.localName (aEl);
    // An identity constraint is annotation? selector field+ (and a refer on
    // keyref); a name is required unless this is the 1.1 @ref form
    if ("key".equals (sElem) || "keyref".equals (sElem) || "unique".equals (sElem))
    {
      int nSel = 0;
      int nField = 0;
      boolean bSawSel = false;
      for (final XdmNode aChild : XsdNodes.children (aEl))
      {
        if (!XsdNodes.isXSElement (aChild))
          continue;
        switch (XsdNodes.localName (aChild))
        {
          case "selector":
            if (bSawSel || nField > 0)
              throw XsdCompiler.invalid ("", "xs:" + sElem + " must have exactly one xs:selector, before its fields");
            bSawSel = true;
            nSel++;
            break;
          case "field":
            if (!bSawSel)
              throw XsdCompiler.invalid ("", "xs:" + sElem + ": xs:field must follow the xs:selector");
            nField++;
            break;
          default:
            break;
        }
      }
      if (!XsdNodes.hasAttr (aEl, "ref"))
      {
        if (nSel != 1 || nField == 0)
          throw XsdCompiler.invalid ("", "xs:" + sElem + " requires one xs:selector followed by at least one xs:field");
        if (!XsdNodes.hasAttr (aEl, "name"))
          throw XsdCompiler.invalid ("", "xs:" + sElem + " requires a name");
        if ("keyref".equals (sElem) && !XsdNodes.hasAttr (aEl, "refer"))
          throw XsdCompiler.invalid ("", "xs:keyref requires a refer");
      }
    }

    // A top-level declaration is named and has no occurrence or use
    final boolean bTopLevel = XsdNodes.isTopLevelParent (aEl.getParent ());
    switch (sElem)
    {
      case "complexType":
      case "group":
      case "attributeGroup":
        if (bTopLevel && !XsdNodes.hasAttr (aEl, "name"))
          throw XsdCompiler.invalid ("", "a top-level xs:" + sElem + " requires a name");
        break;
      case "element":
      case "attribute":
        if (bTopLevel)
        {
          if (!XsdNodes.hasAttr (aEl, "name"))
            throw XsdCompiler.invalid ("", "a top-level xs:" + sElem + " requires a name");
          for (final String sAttr : new String [] { "minOccurs", "maxOccurs", "use", "form", "ref" })
            if (XsdNodes.hasAttr (aEl, sAttr))
              throw XsdCompiler.invalid ("", "a top-level xs:" + sElem + " must not have @" + sAttr);
        }
        break;
      default:
        break;
    }

    // The particle of a named model group definition has no occurrence
    if ("group".equals (sElem) && XsdNodes.hasAttr (aEl, "name"))
      for (final XdmNode aChild : XsdNodes.children (aEl))
      {
        if (!XsdNodes.isXSElement (aChild))
          continue;
        switch (XsdNodes.localName (aChild))
        {
          case "sequence":
          case "choice":
          case "all":
            if (XsdNodes.hasAttr (aChild, "minOccurs"))
              throw XsdCompiler.invalid ("", "the content of a named xs:group must not have minOccurs");
            if (XsdNodes.hasAttr (aChild, "maxOccurs"))
              throw XsdCompiler.invalid ("", "the content of a named xs:group must not have maxOccurs");
            break;
          default:
            break;
        }
      }

    // A reference carries no content but an annotation
    switch (sElem)
    {
      case "element":
      case "attribute":
      case "group":
      case "attributeGroup":
        if (XsdNodes.hasAttr (aEl, "ref"))
          for (final XdmNode aChild : XsdNodes.children (aEl))
            if (XsdNodes.isXSElement (aChild) && !"annotation".equals (XsdNodes.localName (aChild)))
              throw XsdCompiler.invalid ("",
                                         "xs:" + sElem + " with a ref must not have " + XsdNodes.localName (aChild) + " children");
        break;
      default:
        break;
    }

    // At most one inline type definition
    if ("attribute".equals (sElem) || "element".equals (sElem))
    {
      int nTypes = 0;
      for (final XdmNode aChild : XsdNodes.children (aEl))
        if (XsdNodes.isXS (aChild, "simpleType") || XsdNodes.isXS (aChild, "complexType"))
          nTypes++;
      if (nTypes > 1)
        throw XsdCompiler.invalid ("", "xs:" + sElem + " may have at most one inline type definition");
    }
    switch (sElem)
    {
      case "complexType":
      case "restriction":
      case "extension":
      case "group":
      case "attributeGroup":
      {
        int nGroups = 0;
        int nAnyAttrs = 0;
        boolean bSeenAttr = false;
        boolean bSeenAnyAttr = false;
        for (final XdmNode aChild : XsdNodes.children (aEl))
        {
          if (!XsdNodes.isXSElement (aChild))
            continue;
          switch (XsdNodes.localName (aChild))
          {
            case "group":
            case "all":
            case "choice":
            case "sequence":
              nGroups++;
              // The content model precedes the attribute declarations
              if (bSeenAttr)
                throw XsdCompiler.invalid ("", "model group must come before attributes in xs:" + sElem);
              break;
            case "anyAttribute":
              nAnyAttrs++;
              bSeenAttr = true;
              bSeenAnyAttr = true;
              break;
            case "attribute":
            case "attributeGroup":
              bSeenAttr = true;
              // The attribute wildcard is the last of the attribute uses
              if (bSeenAnyAttr)
                throw XsdCompiler.invalid ("", "anyAttribute must be the last attribute in xs:" + sElem);
              break;
            default:
              break;
          }
        }
        if (nGroups > 1)
          throw XsdCompiler.invalid ("", "xs:" + sElem + " has more than one model-group child");
        if (nAnyAttrs > 1)
          throw XsdCompiler.invalid ("", "xs:" + sElem + " has more than one anyAttribute");
        break;
      }
      default:
        break;
    }
    // Every named component's name must be an NCName
    final String sName = XsdNodes.attr (aEl, "name");
    if (sName != null && NCNAME_ELEMS.contains (sElem) && !XsdLexHelper.isNCName (XsdWhiteSpace.collapse (sName)))
      throw XsdCompiler.invalid ("", "invalid name " + XsdLexHelper.quote (sName) + " for xs:" + sElem);
    // An inline type definition must be anonymous
    final XdmNode aParent = aEl.getParent ();
    if (sName != null && XsdNodes.isXSElement (aParent))
    {
      final String sParent = XsdNodes.localName (aParent);
      if ("simpleType".equals (sElem) &&
          ("restriction".equals (sParent) || "list".equals (sParent) || "union".equals (sParent)) ||
          "complexType".equals (sElem) && "element".equals (sParent))
        throw XsdCompiler.invalid ("", "an inline xs:" + sElem + " must not have a name");
    }
    switch (sElem)
    {
      case "all":
      {
        // cos-all-limited: minOccurs 0/1, maxOccurs 1 (1.1 also 0), and in
        // 1.0 each member maxOccurs at most 1
        final String sMax = XsdNodes.attr (aEl, "maxOccurs");
        if (sMax != null)
        {
          final String s = XsdWhiteSpace.collapse (sMax);
          if (!"1".equals (s))
          {
            if (!"0".equals (s) || eVersion == EXsdVersion.V10)
              throw XsdCompiler.invalid ("", "xs:all must have maxOccurs=1");
          }
        }
        final String sMin = XsdNodes.attr (aEl, "minOccurs");
        if (sMin != null)
        {
          final String s = XsdWhiteSpace.collapse (sMin);
          if (!"0".equals (s) && !"1".equals (s))
            throw XsdCompiler.invalid ("", "xs:all minOccurs must be 0 or 1");
        }
        if (eVersion == EXsdVersion.V10)
          for (final XdmNode aChild : XsdNodes.children (aEl))
          {
            if (!XsdNodes.isXS (aChild, "element"))
              continue;
            final String sChildMax = XsdNodes.attr (aChild, "maxOccurs");
            if (sChildMax != null)
            {
              final String s = XsdWhiteSpace.collapse (sChildMax);
              if (!"0".equals (s) && !"1".equals (s))
                throw XsdCompiler.invalid ("", "a member of xs:all must have maxOccurs 0 or 1");
            }
          }
        break;
      }
      case "sequence":
      case "choice":
        // An xs:all may only be the whole content model
        for (final XdmNode aChild : XsdNodes.children (aEl))
          if (XsdNodes.isXS (aChild, "all"))
            throw XsdCompiler.invalid ("", "xs:all may only appear as the top-level content model");
        break;
      case "appinfo":
      case "documentation":
        // @source is xs:anyURI: under 1.0 an RFC 2396 URI reference
        if (eVersion == EXsdVersion.V10)
        {
          final String sSource = XsdNodes.attr (aEl, "source");
          if (sSource != null && !XsdLexHelper.validAnyURI10 (XsdWhiteSpace.collapse (sSource)))
            throw XsdCompiler.invalid ("", "invalid anyURI " + XsdLexHelper.quote (sSource) + " for @source");
        }
        break;
      case "openContent":
      case "defaultOpenContent":
        _checkOpenContentElement (aEl, sElem);
        break;
      case "simpleType":
      case "simpleContent":
        // A simple-type restriction may not derive from the ur-types
        for (final XdmNode aChild : XsdNodes.children (aEl))
        {
          if (!XsdNodes.isXS (aChild, "restriction"))
            continue;
          final String sBase = XsdNodes.attr (aChild, "base");
          if (sBase == null)
            continue;
          final XsdName aBase = XsdNodes.resolveQName (aChild, sBase);
          if (XsdComplexType.ANY_TYPE_NAME.equals (aBase))
          {
            // simpleContent may restrict xs:anyType when the restriction
            // carries its own simpleType
            if ("simpleContent".equals (sElem) && _hasXSChild (aChild, "simpleType"))
              continue;
            throw XsdCompiler.invalid ("", "a simpleType cannot restrict " + sBase);
          }
          if (new XsdName (XsdConstants.NS_XS, "anySimpleType").equals (aBase))
            throw XsdCompiler.invalid ("", "a simpleType cannot restrict " + sBase);
          // 1.1: anyAtomicType may not be the base of a restriction
          if (new XsdName (XsdConstants.NS_XS, "anyAtomicType").equals (aBase) && eVersion != EXsdVersion.V10)
            throw XsdCompiler.invalid ("", "a simpleType cannot restrict " + sBase);
        }
        break;
      case "schema":
      case "redefine":
      case "override":
        _checkTopLevelChildren (aEl);
        break;
      default:
        break;
    }
    switch (sElem)
    {
      case "attribute":
      {
        final boolean bHasRef = XsdNodes.hasAttr (aEl, "ref");
        final boolean bHasType = XsdNodes.hasAttr (aEl, "type");
        final boolean bHasST = _hasXSChild (aEl, "simpleType");
        if (XsdNodes.hasAttr (aEl, "default") && XsdNodes.hasAttr (aEl, "fixed"))
          throw XsdCompiler.invalid ("", "xs:attribute has both default and fixed");
        if (bHasRef && (bHasType || XsdNodes.hasAttr (aEl, "form") || XsdNodes.hasAttr (aEl, "name") || bHasST))
          throw XsdCompiler.invalid ("", "xs:attribute ref is not compatible with name/type/form/simpleType");
        if (bHasType && bHasST)
          throw XsdCompiler.invalid ("", "xs:attribute has both a type attribute and a simpleType child");
        if (XsdNodes.hasAttr (aEl, "default"))
        {
          final String sUse = XsdNodes.attrOrEmpty (aEl, "use");
          if ("required".equals (sUse) || "prohibited".equals (sUse))
            throw XsdCompiler.invalid ("", "xs:attribute default requires use=optional");
        }
        if ("xmlns".equals (XsdNodes.attrOrEmpty (aEl, "name")))
          throw XsdCompiler.invalid ("", "an attribute declaration must not be named xmlns");
        break;
      }
      case "element":
      {
        if (XsdNodes.hasAttr (aEl, "default") && XsdNodes.hasAttr (aEl, "fixed"))
          throw XsdCompiler.invalid ("", "xs:element has both default and fixed");
        if (XsdNodes.hasAttr (aEl, "ref"))
        {
          for (final String sAttr : new String [] { "name",
                                                    "type",
                                                    "form",
                                                    "nillable",
                                                    "block",
                                                    "final",
                                                    "abstract",
                                                    "substitutionGroup",
                                                    "default",
                                                    "fixed" })
            if (XsdNodes.hasAttr (aEl, sAttr))
              throw XsdCompiler.invalid ("", "xs:element ref is not compatible with " + sAttr);
          if (_hasXSChild (aEl, "simpleType") || _hasXSChild (aEl, "complexType"))
            throw XsdCompiler.invalid ("", "xs:element ref must not have an inline type");
        }
        if (XsdNodes.hasAttr (aEl, "type") && (_hasXSChild (aEl, "simpleType") || _hasXSChild (aEl, "complexType")))
          throw XsdCompiler.invalid ("", "xs:element has both a type attribute and an inline type");
        // abstract/final/substitutionGroup only on a global declaration
        if (!XsdNodes.isTopLevelParent (aEl.getParent ()))
          for (final String sAttr : new String [] { "abstract", "final", "substitutionGroup" })
            if (XsdNodes.hasAttr (aEl, sAttr))
              throw XsdCompiler.invalid ("", sAttr + " is not allowed on a local element");
        break;
      }
      default:
        break;
    }
  }

  /** The checks of one XSD element (go-xslt <code>validateSchemaAttrs</code> minus the recursion). */
  private static void _checkElement (@NonNull final XdmNode aEl, @NonNull final EXsdVersion eVersion)
  {
    final String sElem = XsdNodes.localName (aEl);
    final ICommonsSet <String> aAllowed = ALLOWED_ATTRS.get (sElem);
    if (aAllowed != null)
      for (final XdmNode a : XsdNodes.attributes (aEl))
      {
        if (!XsdNodes.namespaceUri (a).isEmpty ())
        {
          // Foreign and xsi attributes are permitted
          continue;
        }
        if (!aAllowed.contains (XsdNodes.localName (a)))
          throw XsdCompiler.invalid ("", "attribute " + XsdLexHelper.quote (XsdNodes.localName (a)) + " is not allowed on xs:" + sElem);
      }
    final String sLang = XsdNodes.attrNS (aEl, XsdConstants.NS_XML, "lang");
    if (sLang != null && !XsdLexical.isLanguage (sLang))
      throw XsdCompiler.invalid ("", "xml:lang " + XsdLexHelper.quote (sLang) + " is not a valid xs:language");
    // Element-only content everywhere except inside appinfo/documentation
    if (!"appinfo".equals (sElem) && !"documentation".equals (sElem))
      for (final XdmNode aChild : XsdNodes.children (aEl))
      {
        if (aChild.isText () && !aChild.getValue ().strip ().isEmpty ())
          throw XsdCompiler.invalid ("", "character data is not allowed inside xs:" + sElem);
        if (aChild.isElement () && !XsdConstants.NS_XS.equals (XsdNodes.namespaceUri (aChild)))
          throw XsdCompiler.invalid ("",
                                     "element {" +
                                         XsdNodes.namespaceUri (aChild) +
                                         "}" +
                                         XsdNodes.localName (aChild) +
                                         " is not allowed inside xs:" +
                                         sElem);
      }
    final ICommonsSet <String> aAllowedChildren = ALLOWED_CHILDREN.get (sElem);
    if (aAllowedChildren != null)
      for (final XdmNode aChild : XsdNodes.children (aEl))
      {
        if (!XsdNodes.isXSElement (aChild))
          continue;
        final String sChild = XsdNodes.localName (aChild);
        if (eVersion == EXsdVersion.V10 && "all".equals (sElem) && "any".equals (sChild))
          throw XsdCompiler.invalid ("", "an XSD 1.0 xs:all group may contain only element declarations");
        if (eVersion == EXsdVersion.V10 && XSD11_ONLY_ELEMENTS.contains (sChild))
          throw XsdCompiler.invalid ("", "xs:" + sChild + " is an XSD 1.1 construct (not valid in a 1.0 schema)");
        if (!aAllowedChildren.contains (sChild))
          throw XsdCompiler.invalid ("", "element xs:" + sChild + " is not allowed inside xs:" + sElem);
      }
    if ("element".equals (sElem))
    {
      // Grammar order: the inline type precedes the identity constraints
      boolean bSeenIC = false;
      for (final XdmNode aChild : XsdNodes.children (aEl))
      {
        if (!XsdNodes.isXSElement (aChild))
          continue;
        switch (XsdNodes.localName (aChild))
        {
          case "unique":
          case "key":
          case "keyref":
            bSeenIC = true;
            break;
          case "simpleType":
          case "complexType":
            if (bSeenIC)
              throw XsdCompiler.invalid ("",
                                         "xs:" + XsdNodes.localName (aChild) + " must precede the identity constraints of xs:element");
            break;
          default:
            break;
        }
      }
    }
    // At most one xs:annotation, as the first child; only schema, redefine and
    // override may carry several, interspersed
    switch (sElem)
    {
      case "schema":
      case "redefine":
      case "override":
      case "appinfo":
      case "documentation":
        break;
      default:
      {
        boolean bSeenOther = false;
        int nAnnots = 0;
        for (final XdmNode aChild : XsdNodes.children (aEl))
        {
          if (!XsdNodes.isXSElement (aChild))
            continue;
          if ("annotation".equals (XsdNodes.localName (aChild)))
          {
            nAnnots++;
            if (nAnnots > 1)
              throw XsdCompiler.invalid ("", "xs:" + sElem + " may have at most one xs:annotation");
            if (bSeenOther)
              throw XsdCompiler.invalid ("", "xs:annotation must be the first child of xs:" + sElem);
          }
          else
            bSeenOther = true;
        }
        break;
      }
    }
    _checkOccurs (aEl);
    _checkDeclConstraints (aEl, eVersion);
    // restriction/extension children depend on the context
    final ICommonsMap <String, ICommonsSet <String>> aAllowedD = DERIV_CHILDREN_BY_PARENT.get (sElem);
    if (aAllowedD != null)
      for (final XdmNode aBody : XsdNodes.children (aEl))
      {
        if (!XsdNodes.isXS (aBody, "restriction") && !XsdNodes.isXS (aBody, "extension"))
          continue;
        final ICommonsSet <String> aAllowedBody = aAllowedD.get (XsdNodes.localName (aBody));
        for (final XdmNode aChild : XsdNodes.children (aBody))
        {
          if (!XsdNodes.isXSElement (aChild))
            continue;
          if (aAllowedBody == null || !aAllowedBody.contains (XsdNodes.localName (aChild)))
            throw XsdCompiler.invalid ("",
                                       "element xs:" +
                                           XsdNodes.localName (aChild) +
                                           " is not allowed inside xs:" +
                                           XsdNodes.localName (aBody));
        }
      }
  }

  /**
   * go-xslt <code>validateAttrValues</code>: every XSD element below (and
   * including) the given one has valid boolean, enumeration, block/final and
   * id attribute values; ids are unique per document.
   *
   * @throws XsdInvalidException
   *         for the first invalid value
   */
  static void validateAttrValues (@NonNull final XdmNode aRoot, @NonNull final EXsdVersion eVersion)
  {
    final ICommonsSet <String> aSeenIDs = new CommonsHashSet <> ();
    final ICommonsList <XdmNode> aStack = new CommonsArrayList <> ();
    aStack.add (aRoot);
    while (aStack.isNotEmpty ())
    {
      final XdmNode aCur = aStack.removeLast ();
      if (XsdNodes.isXSElement (aCur))
        _checkAttrValues (aCur, aSeenIDs, eVersion);
      final ICommonsList <XdmNode> aChildren = XsdNodes.children (aCur);
      for (int i = aChildren.size () - 1; i >= 0; i--)
        if (aChildren.get (i).isElement ())
          aStack.add (aChildren.get (i));
    }
  }

  /**
   * go-xslt <code>validateSchemaAttrs</code>: the schema-for-schemas checks of
   * every XSD element below (and including) the given one, not descending into
   * appinfo/documentation.
   *
   * @throws XsdInvalidException
   *         for the first violation
   */
  static void validateSchemaAttrs (@NonNull final XdmNode aRoot, @NonNull final EXsdVersion eVersion)
  {
    final ICommonsList <XdmNode> aStack = new CommonsArrayList <> ();
    aStack.add (aRoot);
    while (aStack.isNotEmpty ())
    {
      final XdmNode aCur = aStack.removeLast ();
      final boolean bXS = XsdNodes.isXSElement (aCur);
      if (bXS)
      {
        _checkElement (aCur, eVersion);
        final String sLocal = XsdNodes.localName (aCur);
        if ("appinfo".equals (sLocal) || "documentation".equals (sLocal))
        {
          // Arbitrary content: nothing below is schema grammar
          continue;
        }
      }
      final ICommonsList <XdmNode> aChildren = XsdNodes.children (aCur);
      for (int i = aChildren.size () - 1; i >= 0; i--)
        if (aChildren.get (i).isElement ())
          aStack.add (aChildren.get (i));
    }
  }
}
