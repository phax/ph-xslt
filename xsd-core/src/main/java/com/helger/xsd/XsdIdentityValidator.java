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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmDateTimeValue;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Evaluation of identity constraints on an instance (the run-time half of
 * go-xslt <code>identity.go</code>: key tables, tuples, keyref checks). The
 * compile-time half (parsing, the restricted XPath grammar, keyref
 * cross-checks) is part of the loader ({@link XsdIdentityCompiler}).
 *
 * @author Philip Helger
 */
@Immutable
final class XsdIdentityValidator
{
  private static final String TUPLE_SEP = "\u0000";

  @PresentForCodeCoverage
  private static final XsdIdentityValidator INSTANCE = new XsdIdentityValidator ();

  private XsdIdentityValidator ()
  {}

  /**
   * The set of key tuples gathered for one key/unique constraint at one scope
   * element (go-xslt <code>keyTable</code>).
   */
  static final class KeyTable
  {
    private final ICommonsSet <String> m_aTuples = new CommonsHashSet <> ();

    boolean contains (@NonNull final String sTuple)
    {
      return m_aTuples.contains (sTuple);
    }

    boolean add (@NonNull final String sTuple)
    {
      return m_aTuples.add (sTuple);
    }
  }

  /**
   * go-xslt <code>evalNodes</code>: the nodes a selector or field selects from
   * a context node.
   *
   * @throws XsdUnsupportedException
   *         if the XPath engine cannot evaluate the expression
   */
  @NonNull
  private static ICommonsList <IXdmNode> _evalNodes (@NonNull final CompiledXPath aXPath,
                                                    @NonNull final IXdmNode aContext)
  {
    final XdmSequence aSeq;
    try
    {
      final DynamicContext aCtx = aXPath.createDynamicContext ().withFocus (aContext, 1, 1);
      aSeq = aXPath.evaluate (aCtx);
    }
    catch (final XPathException ex)
    {
      throw new XsdUnsupportedException ("identity-constraint XPath " +
                                         XsdLexHelper.quote (aXPath.getSource ()) +
                                         " cannot be evaluated: " +
                                         ex.getMessage (),
                                         ex);
    }
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    for (final IXdmItem aItem : aSeq)
      if (aItem instanceof final IXdmNode aNode)
        ret.add (aNode);
    return ret;
  }

  /**
   * go-xslt <code>absentAttrDefault</code>: resolve a field of the simple form
   * "@name" against the target element's declared type: an absent attribute
   * whose use carries a default or fixed value contributes that value to the
   * key sequence.
   *
   * @return The attribute use whose default or fixed value applies, or
   *         <code>null</code> if there is none.
   */
  @Nullable
  private static XsdAttributeUse _absentAttrDefault (@NonNull final XsdValidationRun aRun,
                                                     @NonNull final String sRaw,
                                                     @NonNull final IXdmNode aTarget)
  {
    if (!sRaw.startsWith ("@"))
      return null;
    final String sName = sRaw.substring (1).strip ();
    if (sName.isEmpty ())
      return null;
    for (int i = 0; i < sName.length (); i++)
      if ("/|[]:*".indexOf (sName.charAt (i)) >= 0)
        return null; // only the plain unprefixed @NCName form
    if (!(aRun.elementTypeOf (aTarget) instanceof final XsdComplexType aCT))
      return null;
    for (final XsdAttributeUse aUse : aRun.getSchema ().effectiveAttrs (aCT).uses ())
    {
      if (!aUse.m_aDecl.m_aName.local ().equals (sName) || !aUse.m_aDecl.m_aName.space ().isEmpty ())
        continue;
      if (aUse.m_bHasDefault || aUse.m_bHasFixed)
        return aUse;
    }
    return null;
  }

  /**
   * go-xslt <code>fieldTypeOK</code>: enforce cvc-identity-constraint.3: a
   * field must select a node with a simple type - a simple-typed element, an
   * element whose complex type has simple content, or a declared attribute. An
   * untyped (anyType) element, an element-only/mixed complex type, or an
   * attribute known only through a skip wildcard has no key value.
   */
  private static void _fieldTypeOK (@NonNull final XsdValidationRun aRun, @NonNull final IXdmNode aNode)
  {
    final IXdmNode aOwner = aNode.isAttribute () ? aNode.getParent () : aNode;
    if (aOwner == null || !aOwner.isElement ())
      return;
    final IXsdType aType = aRun.elementTypeOf (aOwner);
    if (aNode.isAttribute ())
    {
      if (!(aType instanceof final XsdComplexType aCT))
        return; // conservative: owner's type unknown or simple
      final XsdName aName = XsdInstanceValidator.nameOf (aNode);
      for (final XsdAttributeUse aUse : aRun.getSchema ().effectiveAttrs (aCT).uses ())
        if (aUse.m_aDecl.m_aName.equals (aName))
          return; // declared attribute - always simple-typed
      if (aRun.getSchema ().m_aAttributes.containsKey (aName))
        return; // matched via a wildcard, assessed against its global decl
      if (XsdConstants.NS_XSI.equals (aName.space ()) || XsdConstants.NS_XML.equals (aName.space ()))
        return;
      throw XsdCompiler.invalid ("cvc-identity-constraint.3",
                                 "field selects attribute " + aName + ", which has no declaration");
    }
    if (aType instanceof final XsdComplexType aCT && XsdComponentParser.simpleContentType (aCT) == null)
      throw XsdCompiler.invalid ("cvc-identity-constraint.3",
                                 "field selects element " +
                                                              XsdInstanceValidator.nameOf (aOwner) +
                                                              ", whose type has no simple content");
  }

  /**
   * go-xslt <code>declaredTypeOf</code>: the simple type an identity-constraint
   * field's value is validated against. Key comparison happens in the type's
   * value space, so the type has to be known even when the instance carries no
   * xsi:type: a field on two xs:decimal elements must see "1.0" and "1.00" as
   * the same key. The type is found by walking from the document element down
   * to the node (identity constraints are evaluated at a scope element before
   * its subtree has been walked, so nothing is recorded yet).
   */
  @Nullable
  private static XsdSimpleType _declaredTypeOf (@NonNull final XsdValidationRun aRun, @NonNull final IXdmNode aNode)
  {
    final IXdmNode aOwner = aNode.isAttribute () ? aNode.getParent () : aNode;
    if (aOwner == null || !aOwner.isElement ())
      return null;
    final IXsdType aType = aRun.elementTypeOf (aOwner);
    if (aType == null)
      return null;
    if (aNode.isAttribute ())
    {
      if (!(aType instanceof final XsdComplexType aCT))
        return null;
      final XsdName aName = XsdInstanceValidator.nameOf (aNode);
      for (final XsdAttributeUse aUse : aRun.getSchema ().effectiveAttrs (aCT).uses ())
        if (aUse.m_aDecl.m_aName.equals (aName))
          return aUse.m_aDecl.m_aType;
      return null;
    }
    return XsdComponentParser.simpleContentType (aType);
  }

  private static boolean _isDateTimePrim (@NonNull final EAtomicType ePrim)
  {
    switch (ePrim)
    {
      case DATE_TIME:
      case DATE_TIME_STAMP:
      case DATE:
      case TIME:
      case G_YEAR:
      case G_YEAR_MONTH:
      case G_MONTH:
      case G_MONTH_DAY:
      case G_DAY:
        return true;
      default:
        return false;
    }
  }

  /**
   * go-xslt <code>canonAtomic</code>: render one atomic value in its type's
   * value space, prefixed with the type so values of different types cannot
   * collide.
   *
   * @return The key form, or <code>null</code> if the value does not cast.
   */
  @Nullable
  private static String _canonAtomic (@NonNull final XsdSimpleType aType,
                                      @NonNull final IXdmNode aNode,
                                      @NonNull final String sRaw)
  {
    final EAtomicType ePrim = XsdComponentChecks.xsdPrimitiveOf (aType.resolvePrim ());
    final String sPrefix = ePrim.name () + ":";
    if (ePrim == EAtomicType.QNAME || ePrim == EAtomicType.NOTATION)
    {
      // QName values are equal when their namespace and local part match; the
      // prefix carries no identity of its own
      final IXdmNode aScope = aNode.isAttribute () && aNode.getParent () != null ? aNode.getParent () : aNode;
      return sPrefix + XsdNodes.resolveQName (aScope, sRaw);
    }
    String sValue = sRaw;
    if (ePrim == EAtomicType.TIME)
    {
      // Section 3.3.8: the time value space maps 24:00:00 to 00:00:00 of the
      // same (arbitrary) reference date - normalise before casting so the
      // dummy date does not roll over
      final String sTrimmed = sValue.strip ();
      if (sTrimmed.startsWith ("24:"))
        sValue = "00:" + sTrimmed.substring (3);
    }
    final XdmAtomic aAtomic = XsdValueCompare.castOrNull (sValue, ePrim);
    if (aAtomic == null)
      return null;
    if (_isDateTimePrim (ePrim))
    {
      // Key identity is on the timeline for timezoned values (02:00:00-05:00
      // equals 07:00:00Z); values without a timezone live in their own
      // partition and never collide with timezoned ones
      if (aAtomic.getValue () instanceof final XdmDateTimeValue aDT && aDT.hasTimezone ())
        return sPrefix + "Z:" + aDT.getEpochSeconds (0).stripTrailingZeros ().toPlainString ();
      return sPrefix + "-:" + aAtomic.getLexical ();
    }
    return sPrefix + aAtomic.getLexical ();
  }

  /**
   * go-xslt <code>nodeStringValue</code>.
   */
  @NonNull
  private static String _nodeStringValue (@NonNull final IXdmNode aNode)
  {
    return aNode.isAttribute () ? aNode.getValue () : aNode.getStringValue ();
  }

  /**
   * go-xslt <code>fieldKeyValue</code>: the comparison key for one
   * identity-constraint field value. Values are compared in their type's value
   * space, so the value is canonicalised and prefixed with the type: boolean
   * "1" and decimal "1" are distinct keys, while decimal "1.0" and "1.00" are
   * the same one. Without a determinable type it falls back to the collapsed
   * lexical string.
   */
  @NonNull
  private static String _fieldKeyValue (@NonNull final XsdValidationRun aRun, @NonNull final IXdmNode aNode)
  {
    final XsdSimpleType aType = _declaredTypeOf (aRun, aNode);
    if (aType == null)
      return XsdWhiteSpace.collapse (_nodeStringValue (aNode));
    // The comparison value is the field's value after the type's own
    // whiteSpace processing - xs:string preserves, so "  test  " and " test "
    // are distinct keys
    final String sRaw = XsdWhiteSpace.apply (aType.effectiveWhiteSpace (), _nodeStringValue (aNode));
    switch (aType.m_eVariety)
    {
      case ATOMIC:
      {
        final String sCanon = _canonAtomic (aType, aNode, sRaw);
        if (sCanon != null)
          return sCanon;
        break;
      }
      case LIST:
      {
        // A list's key is its items' canonical forms joined - which makes a
        // singleton list equal to the same value held atomically, as XSD
        // requires
        if (aType.m_aItem == null || aType.m_aItem.m_eVariety != EXsdVariety.ATOMIC)
          break;
        final String sPrim = XsdComponentChecks.xsdPrimitiveOf (aType.m_aItem.resolvePrim ()).name () + ":";
        final ICommonsList <String> aItems = XsdLexHelper.xmlFields (sRaw);
        final StringBuilder aSB = new StringBuilder ();
        for (int i = 0; i < aItems.size (); i++)
        {
          final String sCanon = _canonAtomic (aType.m_aItem, aNode, aItems.get (i));
          if (sCanon == null)
            return sRaw;
          if (i > 0)
            aSB.append (' ');
          aSB.append (sCanon.startsWith (sPrim) ? sCanon.substring (sPrim.length ()) : sCanon);
        }
        return sPrim + aSB;
      }
      default:
        break;
    }
    return sRaw;
  }

  /**
   * go-xslt <code>tupleFor</code>: evaluate the fields against a target node.
   *
   * @return The serialised key tuple, or <code>null</code> if not every field
   *         selected exactly one node (incomplete).
   * @throws XsdInvalidException
   *         if a field selects more than one node or a node without a simple
   *         type
   */
  @Nullable
  private static String _tupleFor (@NonNull final XsdValidationRun aRun,
                                   @NonNull final XsdIdentityConstraint aConstraint,
                                   @NonNull final IXdmNode aTarget)
  {
    final ICommonsList <String> aVals = new CommonsArrayList <> ();
    for (int nField = 0; nField < aConstraint.m_aFields.size (); nField++)
    {
      final ICommonsList <IXdmNode> aNodes = _evalNodes (aConstraint.m_aFields.get (nField), aTarget);
      if (aNodes.isEmpty ())
      {
        // An absent attribute with a default/fixed value still contributes to
        // a key/unique key-sequence - but a keyref must not resolve through a
        // supplied default
        if (!"keyref".equals (aConstraint.m_sKind) && nField < aConstraint.m_aFieldRaw.size ())
        {
          final XsdAttributeUse aUse = _absentAttrDefault (aRun, aConstraint.m_aFieldRaw.get (nField), aTarget);
          if (aUse != null)
          {
            final String sValue = aUse.m_bHasDefault ? aUse.m_sDefault : aUse.m_sFixed;
            final XsdSimpleType aType = aUse.m_aDecl.m_aType;
            // The supplied value must take the same canonical key form as an
            // explicit one (fieldKeyValue), or "test" and the defaulted
            // "test" would never collide
            String sKey = sValue;
            if (aType != null && aType.m_eVariety == EXsdVariety.ATOMIC)
            {
              final String sCanon = _canonAtomic (aType,
                                                  aTarget,
                                                  XsdWhiteSpace.apply (aType.effectiveWhiteSpace (), sValue));
              if (sCanon != null)
                sKey = sCanon;
            }
            aVals.add (sKey);
            continue;
          }
        }
        return null; // incomplete
      }
      if (aNodes.size () > 1)
        throw XsdCompiler.invalid ("cvc-identity-constraint.3", "field selects more than one node");
      _fieldTypeOK (aRun, aNodes.get (0));
      aVals.add (_fieldKeyValue (aRun, aNodes.get (0)));
    }
    return String.join (TUPLE_SEP, aVals);
  }

  /**
   * go-xslt <code>buildKeyTable</code>: evaluate a key/unique constraint at a
   * scope element, checking uniqueness (and, for a key, that every selected
   * target has all fields).
   *
   * @param aRun
   *        The validation run.
   * @param aConstraint
   *        The key or unique constraint.
   * @param aScope
   *        The instance element the declaration governs.
   * @return The key table of this scope.
   * @throws XsdInvalidException
   *         if the constraint is violated
   */
  @NonNull
  static KeyTable buildKeyTable (@NonNull final XsdValidationRun aRun,
                                 @NonNull final XsdIdentityConstraint aConstraint,
                                 @NonNull final IXdmNode aScope)
  {
    final ICommonsList <IXdmNode> aTargets = aRun.icFilter (_evalNodes (aConstraint.m_aSelector, aScope));
    final KeyTable aTable = new KeyTable ();
    for (final IXdmNode aTarget : aTargets)
    {
      final String sTuple = _tupleFor (aRun, aConstraint, aTarget);
      if (sTuple == null)
      {
        if ("key".equals (aConstraint.m_sKind))
          throw XsdCompiler.invalid ("cvc-identity-constraint.4.2.1", "key " + aConstraint.m_aName + ": a field is missing");
        continue; // unique: incomplete tuples are not compared
      }
      if (!aTable.add (sTuple))
        throw XsdCompiler.invalid ("cvc-identity-constraint.4.1",
                                   "duplicate key/unique value for " + aConstraint.m_aName);
    }
    return aTable;
  }

  /**
   * go-xslt <code>checkKeyref</code>: verify that each complete keyref tuple
   * exists in the referenced key/unique table.
   *
   * @param aRun
   *        The validation run.
   * @param aConstraint
   *        The keyref constraint.
   * @param aScope
   *        The instance element the declaration governs.
   * @param aScopes
   *        The tables of the key/unique constraints whose scope is this
   *        element.
   * @throws XsdInvalidException
   *         if a tuple has no matching key
   */
  static void checkKeyref (@NonNull final XsdValidationRun aRun,
                           @NonNull final XsdIdentityConstraint aConstraint,
                           @NonNull final IXdmNode aScope,
                           @NonNull final ICommonsMap <XsdName, KeyTable> aScopes)
  {
    final KeyTable aRef = aScopes.get (aConstraint.m_aRefer);
    final ICommonsList <IXdmNode> aTargets = aRun.icFilter (_evalNodes (aConstraint.m_aSelector, aScope));
    for (final IXdmNode aTarget : aTargets)
    {
      final String sTuple = _tupleFor (aRun, aConstraint, aTarget);
      if (sTuple == null)
        continue;
      if (aRef == null || !aRef.contains (sTuple))
        throw XsdCompiler.invalid ("cvc-identity-constraint.4.3", "keyref " + aConstraint.m_aName + " has no matching key");
    }
  }
}
