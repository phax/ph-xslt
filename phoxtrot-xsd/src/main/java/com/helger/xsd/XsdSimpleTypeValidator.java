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
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.ops.XsdLexical;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Validation of values against simple types (go-xslt <code>simpletype.go</code>:
 * <code>validate</code>, <code>validateIn</code>, <code>check</code>,
 * <code>checkIn</code>, <code>fixedEqual</code>, <code>atomicEqual</code>,
 * <code>firstMemberRaw</code>, the primitive lexical checks).
 * <p>
 * Union member lists may be cyclic (XSD 1.0 tolerates a pure-union cycle), so
 * every walk over members and item types keeps the unions it is inside of and
 * a depth counter: re-entering a union that is being checked is a member that
 * does not accept the value, and a nesting beyond {@link #MAX_DEPTH} is no
 * verdict. The base chain is walked iteratively.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdSimpleTypeValidator
{
  /** The deepest nesting of union members and list item types followed. */
  private static final int MAX_DEPTH = 256;
  /** The longest base chain followed. */
  private static final int MAX_CHAIN = 10_000;

  @PresentForCodeCoverage
  private static final XsdSimpleTypeValidator INSTANCE = new XsdSimpleTypeValidator ();

  private XsdSimpleTypeValidator ()
  {}

  /**
   * The range check of the built-in integer types for a value that fits a
   * <code>long</code>: xs:byte, xs:short, xs:int and the unsigned types up to
   * xs:unsignedInt by their bounds, xs:unsignedLong and
   * xs:nonNegativeInteger from 0, xs:positiveInteger from 1,
   * xs:nonPositiveInteger up to 0 and xs:negativeInteger up to -1. Every other
   * type (xs:integer, xs:long) accepts any <code>long</code>.
   *
   * @param n
   *        The value.
   * @param ePrim
   *        The built-in integer type.
   * @return <code>true</code> if the value is in the type's range.
   */
  private static boolean _integerInRange (final long n, @NonNull final EAtomicType ePrim)
  {
    switch (ePrim)
    {
      case BYTE:
        return n >= -128 && n <= 127;
      case SHORT:
        return n >= -32768 && n <= 32767;
      case INT:
        return n >= -2147483648L && n <= 2147483647L;
      case UNSIGNED_BYTE:
        return n >= 0 && n <= 255;
      case UNSIGNED_SHORT:
        return n >= 0 && n <= 65535;
      case UNSIGNED_INT:
        return n >= 0 && n <= 4294967295L;
      case UNSIGNED_LONG:
      case NON_NEGATIVE_INTEGER:
        return n >= 0;
      case POSITIVE_INTEGER:
        return n >= 1;
      case NON_POSITIVE_INTEGER:
        return n <= 0;
      case NEGATIVE_INTEGER:
        return n <= -1;
      default:
        // xs:integer, xs:long: unbounded within long
        return true;
    }
  }

  /**
   * @param sValue
   *        The value.
   * @param ePrim
   *        The built-in type it was checked against.
   * @return A new cvc-datatype-valid error for a value outside the lexical
   *         space or range of the type.
   */
  @NonNull
  private static XsdInvalidException _notValid (@NonNull final String sValue, @NonNull final EAtomicType ePrim)
  {
    return XsdCompiler.invalid ("cvc-datatype-valid",
                                XsdLexHelper.quote (sValue) + " is not a valid " + ePrim.getQualifiedName ());
  }

  /**
   * go-xslt <code>checkPrimitiveLexical</code>: validate against the built-in
   * primitive's lexical space. The string-family types are handled directly;
   * everything else defers to the cast of phoxtrot-xpath, with the integer and
   * decimal lexical spaces guarded explicitly.
   *
   * @param ePrim
   *        The built-in primitive.
   * @param sValue
   *        The normalised value.
   * @throws XsdInvalidException
   *         if the value is not in the primitive's lexical space
   */
  static void checkPrimitiveLexical (@NonNull final EAtomicType ePrim, @NonNull final String sValue)
  {
    switch (ePrim)
    {
      case STRING:
      case NORMALIZED_STRING:
      case TOKEN:
      case ANY_ATOMIC_TYPE:
      case UNTYPED_ATOMIC:
        // String family: every value is lexically valid
        return;
      case QNAME:
      case NOTATION:
        // Structural lexical check only, no namespace resolution, so an unbound
        // prefix is not an error here
        if (!XsdLexHelper.validQNameLexical (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case LANGUAGE:
        if (!XsdLexical.isLanguage (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case NAME:
        if (!XsdLexical.isName (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case NCNAME:
      case ID:
      case IDREF:
      case ENTITY:
        if (!XsdLexScan.isNCNameLex (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case NMTOKEN:
        if (!XsdLexical.isNmtoken (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case HEX_BINARY:
        if (!XsdLexScan.isHexBinaryLex (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case BASE64_BINARY:
        if (!XsdLexScan.isBase64BinaryLex (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case DOUBLE:
      case FLOAT:
        // Overflow maps to the infinities and is a valid double/float, so the
        // lexical shape is validated directly rather than through the cast
        if (!XsdLexical.isDoubleLexical (sValue))
          throw _notValid (sValue, ePrim);
        return;
      default:
        break;
    }
    // The integer/decimal lexical spaces need an explicit guard
    if (ePrim.isIntegerFamily ())
    {
      if (!XsdLexScan.isIntegerLex (sValue))
        throw _notValid (sValue, ePrim);
      // A value that fits a long is range-checked here without building the
      // cast's atomic; one that overflows takes the cast
      try
      {
        final long n = Long.parseLong (sValue);
        if (!_integerInRange (n, ePrim))
          throw _notValid (sValue, ePrim);
        return;
      }
      catch (final NumberFormatException ex)
      {
        // Falls through to the cast
      }
    }
    if (ePrim == EAtomicType.DECIMAL)
    {
      if (!XsdLexScan.isDecimalLex (sValue))
        throw _notValid (sValue, ePrim);
      return;
    }
    if (XsdValueCompare.castOrNull (sValue, ePrim) == null)
      throw _notValid (sValue, ePrim);
  }

  /**
   * Compare two atomic lexicals in the value space of a type's primitive:
   * both are cast to it and compared with
   * {@link XsdValueCompare#compare(XdmAtomic, XdmAtomic)}.
   *
   * @param aType
   *        The atomic type.
   * @param sA
   *        The first lexical.
   * @param sB
   *        The second lexical.
   * @return <code>true</code> if both cast and compare as equal;
   *         <code>false</code> if either does not cast or the values are not
   *         comparable.
   */
  private static boolean _atomicEqual (@NonNull final XsdSimpleType aType,
                                       @NonNull final String sA,
                                       @NonNull final String sB)
  {
    final EAtomicType ePrim = aType.resolvePrim ();
    final XdmAtomic aA = XsdValueCompare.castOrNull (sA, ePrim);
    final XdmAtomic aB = XsdValueCompare.castOrNull (sB, ePrim);
    if (aA == null || aB == null)
      return false;
    final Integer aC = XsdValueCompare.compare (aA, aB);
    return aC != null && aC.intValue () == 0;
  }

  /**
   * @return <code>true</code> if the list value items equal one of the
   *         enumerated lists item by item in the value space of the atomic item
   *         type (any other item type keeps the lexical comparison of the
   *         caller).
   */
  private static boolean _listEnumMatch (@NonNull final XsdFacetSet aFacets,
                                         @NonNull final ICommonsList <String> aItems,
                                         @Nullable final XsdSimpleType aItem)
  {
    if (aItem == null || aItem.m_eVariety != EXsdVariety.ATOMIC)
      return false;
    for (final String sEnum : aFacets.m_aEnumeration)
    {
      final ICommonsList <String> aEnumItems = XsdLexHelper.xmlFields (sEnum);
      if (aEnumItems.size () != aItems.size ())
        continue;
      boolean bEqual = true;
      for (int i = 0; i < aEnumItems.size (); i++)
      {
        final String sE = aEnumItems.get (i);
        final String sI = aItems.get (i);
        if (!sE.equals (sI) && !_atomicEqual (aItem, sE, sI))
        {
          bEqual = false;
          break;
        }
      }
      if (bEqual)
        return true;
    }
    return false;
  }

  /**
   * The XSD Part 2 3.2.18 value of an xs:QName (and of an xs:NOTATION) is an
   * expanded name, so a prefixed lexical is valid only where that prefix is
   * bound; an unprefixed name is always fine.
   */
  private static void _checkQNamePrefixBound (@NonNull final String sValue,
                                              @NonNull final EAtomicType ePrim,
                                              @NonNull final IXdmNode aAt)
  {
    final int i = sValue.indexOf (':');
    if (i <= 0)
      return;
    final String sPrefix = sValue.substring (0, i);
    if (XsdNodes.lookupPrefix (aAt, sPrefix) == null)
      throw XsdCompiler.invalid ("cvc-datatype-valid",
                                 "the prefix " +
                                                       XsdLexHelper.quote (sPrefix) +
                                                       " of " +
                                                       ePrim.getQualifiedName () +
                                                       " value " +
                                                       XsdLexHelper.quote (sValue) +
                                                       " is not bound to a namespace");
  }

  /**
   * @return A new error for a nesting of union members and list item types
   *         beyond {@link #MAX_DEPTH}, or a base chain beyond
   *         {@link #MAX_CHAIN}: no verdict.
   */
  @NonNull
  private static XsdUnsupportedException _tooDeep ()
  {
    return new XsdUnsupportedException ("Simple type nesting (union members, list item types) deeper than " +
                                        MAX_DEPTH +
                                        " levels, possibly a cyclic union");
  }

  /**
   * Check a normalised value against a list type. The value is split at XML
   * whitespace. For every step of the base chain the length facets apply to
   * the item count, and pattern and enumeration to the whole lexical; an
   * enumeration miss is forgiven when the items equal an enumerated list item
   * by item in the value space of an atomic item type. Then every item is
   * checked against the item type. Finally, if any step carries assertions,
   * they are all evaluated with <code>$value</code> bound to the sequence of
   * typed items.
   *
   * @param aType
   *        The list type.
   * @param sValue
   *        The whitespace-normalised value.
   * @param aAt
   *        The instance node for QName/NOTATION prefixes; may be
   *        <code>null</code>.
   * @param aActive
   *        The unions being checked on the current path.
   * @param nDepth
   *        The current nesting depth.
   * @throws XsdInvalidException
   *         if the value is not valid
   * @throws XsdUnsupportedException
   *         if the nesting or base chain is too deep
   */
  private static void _checkListRecursive (@NonNull final XsdSimpleType aType,
                                           @NonNull final String sValue,
                                           @Nullable final IXdmNode aAt,
                                           @NonNull final ICommonsSet <XsdSimpleType> aActive,
                                           final int nDepth)
  {
    final ICommonsList <String> aItems = XsdLexHelper.xmlFields (sValue);
    // Every restriction step's facets apply (a derived list must still honour
    // the base's, e.g. the built-in list types' minLength 1); item checks run
    // once against the resolved item type
    XsdSimpleType s = aType;
    boolean bHasAsserts = false;
    for (int n = 0; s != null; n++)
    {
      if (n > MAX_CHAIN)
        throw _tooDeep ();
      XsdFacetValidator.checkListLength (s.m_aFacets, aItems.size ());
      try
      {
        XsdFacetValidator.checkPatternEnumIn (s.m_aFacets, sValue, EAtomicType.STRING, aAt);
      }
      catch (final XsdInvalidException ex)
      {
        // The patterns passed (they are checked first); the enumeration
        // compares values, so "01 2" is the enumerated "1 2" of an xs:int list
        if (!"cvc-enumeration-valid".equals (ex.getError ().code ()) ||
            !_listEnumMatch (s.m_aFacets, aItems, aType.m_aItem))
          throw ex;
      }
      if (s.m_aAssertions.isNotEmpty ())
        bHasAsserts = true;
      s = s.m_aBase;
    }
    if (aType.m_aItem != null)
      for (final String sItem : aItems)
        _checkInRecursive (aType.m_aItem, sItem, aAt, aActive, nDepth + 1);
    if (bHasAsserts)
    {
      // $value is the sequence of typed items
      final XdmSequence aValue = XsdAssertions.typedListValue (aItems,
                                                               aType.m_aItem == null ? null
                                                                                     : aType.m_aItem.resolvePrim ());
      int n = 0;
      for (XsdSimpleType aStep = aType; aStep != null && n <= MAX_CHAIN; aStep = aStep.m_aBase, n++)
        XsdAssertions.evalSimpleAssertions (aStep.m_aAssertions, aValue);
    }
  }

  /**
   * Check a value against a union type. The members are tried in order, each
   * with its own whiteSpace processing applied to the value; the first one
   * that accepts it is the accepting member. Then, for the union and every
   * union in its base chain (a restriction of a union restricts all facets of
   * its base), the pattern and enumeration facets must hold for the value as
   * given or, failing that, for the value as normalised by the accepting
   * member, and the assertions are evaluated with <code>$value</code> typed by
   * the accepting member.
   *
   * @param aType
   *        The union type. It is in <code>aActive</code> while the members are
   *        tried.
   * @param sValue
   *        The value.
   * @param aAt
   *        The instance node for QName/NOTATION prefixes; may be
   *        <code>null</code>.
   * @param aActive
   *        The unions being checked on the current path. Modified and
   *        restored.
   * @param nDepth
   *        The current nesting depth.
   * @throws XsdInvalidException
   *         if no member accepts the value, or a facet or assertion of the
   *         union or of a union in its base chain fails
   * @throws XsdUnsupportedException
   *         if the nesting or base chain is too deep
   */
  private static void _checkUnionRecursive (@NonNull final XsdSimpleType aType,
                                            @NonNull final String sValue,
                                            @Nullable final IXdmNode aAt,
                                            @NonNull final ICommonsSet <XsdSimpleType> aActive,
                                            final int nDepth)
  {
    XsdSimpleType aAccepting = null;
    aActive.add (aType);
    try
    {
      for (final XsdSimpleType aMember : aType.m_aMembers)
      {
        // Each member applies its own whiteSpace processing (a union has none
        // of its own), so validate, not check, the value against the member
        try
        {
          _validateInRecursive (aMember, sValue, aAt, aActive, nDepth + 1);
          aAccepting = aMember;
          break;
        }
        catch (final XsdInvalidException ex)
        {
          // Next member
        }
      }
    }
    finally
    {
      aActive.remove (aType);
    }
    if (aAccepting == null)
      throw XsdCompiler.invalid ("cvc-datatype-valid", "value " + XsdLexHelper.quote (sValue) + " is not valid for any union member");
    // Facets on the union restriction (pattern/enumeration) constrain the whole
    // lexical in addition to member validity. They also hold if they match the
    // value as normalised by the accepting member's whiteSpace. Every union
    // step of the base chain contributes its facets and assertions.
    final String sMemberValue = XsdWhiteSpace.apply (aAccepting.effectiveWhiteSpace (), sValue);
    XsdSimpleType aStep = aType;
    for (int n = 0; aStep != null && aStep.m_eVariety == EXsdVariety.UNION; n++)
    {
      if (n > MAX_CHAIN)
        throw _tooDeep ();
      try
      {
        XsdFacetValidator.checkPatternEnumIn (aStep.m_aFacets, sValue, EAtomicType.STRING, aAt);
      }
      catch (final XsdInvalidException ex)
      {
        if (sMemberValue.equals (sValue))
          throw ex;
        try
        {
          XsdFacetValidator.checkPatternEnumIn (aStep.m_aFacets, sMemberValue, EAtomicType.STRING, aAt);
        }
        catch (final XsdInvalidException ex2)
        {
          throw ex;
        }
      }
      if (aStep.m_aAssertions.isNotEmpty ())
      {
        // $value carries the accepting member's type
        XsdAssertions.evalSimpleAssertions (aStep.m_aAssertions,
                                            XsdAssertions.typedAtomicValue (sMemberValue, aAccepting.resolvePrim ()));
      }
      aStep = aStep.m_aBase;
    }
  }

  /**
   * Check a normalised value against an atomic type and its base chain. The
   * chain is collected up to the first base that is not a plain atomic type
   * (a list, a union, the absent type or xs:error); such a base is checked
   * first as a whole. Then, from the deepest base to the type itself, each
   * step applies its facets and its assertions (with <code>$value</code> typed
   * by the step's primitive). The lexical space of the built-in primitive -
   * and, for xs:QName and xs:NOTATION with a node available, that the prefix
   * is bound - is checked at the deepest step only when the chain ends at a
   * built-in.
   *
   * @param aType
   *        The atomic type.
   * @param sValue
   *        The whitespace-normalised value.
   * @param aAt
   *        The instance node for QName/NOTATION prefixes; may be
   *        <code>null</code>.
   * @param aActive
   *        The unions being checked on the current path.
   * @param nDepth
   *        The current nesting depth.
   * @throws XsdInvalidException
   *         if the value is not valid
   * @throws XsdUnsupportedException
   *         if the nesting or base chain is too deep
   */
  private static void _checkAtomicRecursive (@NonNull final XsdSimpleType aType,
                                             @NonNull final String sValue,
                                             @Nullable final IXdmNode aAt,
                                             @NonNull final ICommonsSet <XsdSimpleType> aActive,
                                             final int nDepth)
  {
    // The base chain, walked iteratively: the deepest base is checked first
    final ICommonsList <XsdSimpleType> aChain = new CommonsArrayList <> ();
    XsdSimpleType aDelegate = null;
    XsdSimpleType s = aType;
    while (true)
    {
      if (aChain.size () > MAX_CHAIN)
        throw _tooDeep ();
      aChain.add (s);
      final XsdSimpleType aBase = s.m_aBase;
      if (aBase == null)
        break;
      if (aBase == XsdSimpleType.ABSENT || aBase == XsdSimpleType.ERROR || aBase.m_eVariety != EXsdVariety.ATOMIC)
      {
        aDelegate = aBase;
        break;
      }
      s = aBase;
    }
    if (aDelegate != null)
      _checkInRecursive (aDelegate, sValue, aAt, aActive, nDepth + 1);
    for (int i = aChain.size () - 1; i >= 0; i--)
    {
      final XsdSimpleType aLevel = aChain.get (i);
      final EAtomicType ePrim = aLevel.resolvePrim ();
      if (i == aChain.size () - 1 && aDelegate == null)
      {
        checkPrimitiveLexical (ePrim, sValue);
        // Skipped when no node is available (a value checked outside any
        // instance, e.g. a schema's own default): only the shape is checked
        if (aAt != null && (ePrim == EAtomicType.QNAME || ePrim == EAtomicType.NOTATION))
          _checkQNamePrefixBound (sValue, ePrim, aAt);
      }
      XsdFacetValidator.checkIn (aLevel.m_aFacets, sValue, ePrim, aAt);
      if (aLevel.m_aAssertions.isNotEmpty ())
        XsdAssertions.evalSimpleAssertions (aLevel.m_aAssertions, XsdAssertions.typedAtomicValue (sValue, ePrim));
    }
  }

  /**
   * Check an already normalised value against a simple type, dispatching on
   * its variety. The absent type (a type reference that never resolved) and
   * xs:error refuse every value with cvc-type. A union that is already being
   * checked on the current path refuses the value, which bounds a cyclic
   * member list.
   *
   * @param aType
   *        The simple type.
   * @param sValue
   *        The whitespace-normalised value.
   * @param aAt
   *        The instance node for QName/NOTATION prefixes; may be
   *        <code>null</code>.
   * @param aActive
   *        The unions being checked on the current path.
   * @param nDepth
   *        The current nesting depth.
   * @throws XsdInvalidException
   *         if the value is not valid
   * @throws XsdUnsupportedException
   *         beyond {@link #MAX_DEPTH}
   */
  private static void _checkInRecursive (@NonNull final XsdSimpleType aType,
                                         @NonNull final String sValue,
                                         @Nullable final IXdmNode aAt,
                                         @NonNull final ICommonsSet <XsdSimpleType> aActive,
                                         final int nDepth)
  {
    if (nDepth > MAX_DEPTH)
      throw _tooDeep ();
    if (aType == XsdSimpleType.ABSENT)
    {
      // Using a declaration whose type reference never resolved is the error
      // the Missing family defers to validation time
      throw XsdCompiler.invalid ("cvc-type",
                                 "the declaration's type " + aType.m_aName.local ().substring (1) + " is absent from the schema");
    }
    if (aType == XsdSimpleType.ERROR)
      throw XsdCompiler.invalid ("cvc-type", "xs:error has an empty value space");
    switch (aType.m_eVariety)
    {
      case LIST:
        _checkListRecursive (aType, sValue, aAt, aActive, nDepth);
        break;
      case UNION:
        // A union that is being checked already is a member that accepts no
        // value, which is what bounds a cyclic member list
        if (aActive.contains (aType))
          throw XsdCompiler.invalid ("cvc-datatype-valid", "value " + XsdLexHelper.quote (sValue) + " reaches a cyclic union");
        _checkUnionRecursive (aType, sValue, aAt, aActive, nDepth);
        break;
      default:
        _checkAtomicRecursive (aType, sValue, aAt, aActive, nDepth);
        break;
    }
  }

  /**
   * Apply the type's effective whiteSpace facet to a raw value and check the
   * result with {@link #_checkInRecursive}.
   *
   * @param aType
   *        The simple type.
   * @param sRaw
   *        The raw value.
   * @param aAt
   *        The instance node; may be <code>null</code>.
   * @param aActive
   *        The unions being checked on the current path.
   * @param nDepth
   *        The current nesting depth.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  private static void _validateInRecursive (@NonNull final XsdSimpleType aType,
                                            @NonNull final String sRaw,
                                            @Nullable final IXdmNode aAt,
                                            @NonNull final ICommonsSet <XsdSimpleType> aActive,
                                            final int nDepth)
  {
    _checkInRecursive (aType, XsdWhiteSpace.apply (aType.effectiveWhiteSpace (), sRaw), aAt, aActive, nDepth);
  }

  /**
   * The first member of a union that accepts a raw value with its own
   * whiteSpace processing, checked without a namespace context (so a prefixed
   * QName is only checked for its shape). The union itself counts as active,
   * so a cyclic member refuses the value.
   *
   * @param aType
   *        The union type.
   * @param sRaw
   *        The raw value.
   * @return The accepting member, or <code>null</code> if none accepts it.
   */
  @Nullable
  private static XsdSimpleType _firstMember (@NonNull final XsdSimpleType aType, @NonNull final String sRaw)
  {
    final ICommonsSet <XsdSimpleType> aActive = new CommonsHashSet <> ();
    aActive.add (aType);
    for (final XsdSimpleType aMember : aType.m_aMembers)
      try
      {
        _validateInRecursive (aMember, sRaw, null, aActive, 1);
        return aMember;
      }
      catch (final XsdInvalidException ex)
      {
        // Next member
      }
    return null;
  }

  /**
   * Recursive worker of {@link #fixedEqual}. For a union both sides must be
   * accepted first by the same member and be equal in it. For a list both
   * sides must have the same number of items, each pair equal in the item
   * type (or lexically equal without an item type). For an atomic type both
   * sides are normalised with the type's whiteSpace and are equal if the
   * strings are, else if they cast to equal values.
   *
   * @param aType
   *        The simple type.
   * @param sContent
   *        The instance value.
   * @param sFixed
   *        The fixed value.
   * @param nDepth
   *        The current nesting depth.
   * @return <code>true</code> if equal.
   * @throws XsdUnsupportedException
   *         beyond {@link #MAX_DEPTH}
   */
  private static boolean _fixedEqualRecursive (@NonNull final XsdSimpleType aType,
                                               @NonNull final String sContent,
                                               @NonNull final String sFixed,
                                               final int nDepth)
  {
    if (nDepth > MAX_DEPTH)
      throw _tooDeep ();
    switch (aType.m_eVariety)
    {
      case UNION:
      {
        // Each side takes the first member that accepts it; equal only when
        // both land in the same member and are equal there
        final XsdSimpleType aMA = _firstMember (aType, sContent);
        final XsdSimpleType aMB = _firstMember (aType, sFixed);
        return aMA != null && aMA == aMB && _fixedEqualRecursive (aMA, sContent, sFixed, nDepth + 1);
      }
      case LIST:
      {
        final ICommonsList <String> aFA = XsdLexHelper.xmlFields (sContent);
        final ICommonsList <String> aFB = XsdLexHelper.xmlFields (sFixed);
        if (aFA.size () != aFB.size ())
          return false;
        for (int i = 0; i < aFA.size (); i++)
        {
          if (aType.m_aItem == null)
          {
            if (!aFA.get (i).equals (aFB.get (i)))
              return false;
            continue;
          }
          if (!_fixedEqualRecursive (aType.m_aItem, aFA.get (i), aFB.get (i), nDepth + 1))
            return false;
        }
        return true;
      }
      default:
      {
        final String sWS = aType.effectiveWhiteSpace ();
        final String sA = XsdWhiteSpace.apply (sWS, sContent);
        final String sB = XsdWhiteSpace.apply (sWS, sFixed);
        if (sA.equals (sB))
          return true;
        return _atomicEqual (aType, sA, sB);
      }
    }
  }

  /**
   * go-xslt <code>SimpleType.validate</code>: apply the type's whiteSpace and
   * check the value (lexical space, facets, list items, union members). A value
   * of a QName/NOTATION-based type is checked without namespace context.
   *
   * @param aType
   *        The simple type. {@link XsdSimpleType#ABSENT} refuses every value.
   * @param sRaw
   *        The raw (not yet normalised) value.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  static void validate (@NonNull final XsdSimpleType aType, @NonNull final String sRaw)
  {
    validateIn (aType, sRaw, null);
  }

  /**
   * go-xslt <code>SimpleType.validateIn</code>: {@link #validate} with the
   * namespace context of an instance node (for QName/NOTATION prefixes).
   *
   * @param aType
   *        The simple type.
   * @param sRaw
   *        The raw value.
   * @param aAt
   *        The element or attribute node the value sits on; <code>null</code>
   *        for none.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  static void validateIn (@NonNull final XsdSimpleType aType, @NonNull final String sRaw, @Nullable final IXdmNode aAt)
  {
    _validateInRecursive (aType, sRaw, aAt, new CommonsHashSet <> (), 0);
  }

  /**
   * go-xslt <code>SimpleType.check</code>: check an already whitespace
   * normalised value.
   *
   * @param aType
   *        The simple type.
   * @param sValue
   *        The normalised value.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  static void check (@NonNull final XsdSimpleType aType, @NonNull final String sValue)
  {
    _checkInRecursive (aType, sValue, null, new CommonsHashSet <> (), 0);
  }

  /**
   * go-xslt <code>SimpleType.checkIn</code>: {@link #check} with the instance
   * node of a QName/NOTATION value.
   *
   * @param aType
   *        The simple type.
   * @param sValue
   *        The normalised value.
   * @param aAt
   *        The instance node; may be <code>null</code>.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  static void checkIn (@NonNull final XsdSimpleType aType, @NonNull final String sValue, @Nullable final IXdmNode aAt)
  {
    _checkInRecursive (aType, sValue, aAt, new CommonsHashSet <> (), 0);
  }

  /**
   * go-xslt <code>SimpleType.fixedEqual</code>: whether instance content equals
   * a fixed value constraint in the type's value space. Both lexicals are
   * normalised with the governing type's whiteSpace (for a union, the
   * whiteSpace of the first member accepting each side), then compared as
   * values.
   *
   * @param aType
   *        The simple type.
   * @param sContent
   *        The instance value.
   * @param sFixed
   *        The fixed value.
   * @return <code>true</code> if equal.
   */
  static boolean fixedEqual (@NonNull final XsdSimpleType aType,
                             @NonNull final String sContent,
                             @NonNull final String sFixed)
  {
    return _fixedEqualRecursive (aType, sContent, sFixed, 0);
  }

  /**
   * go-xslt <code>SimpleType.firstMemberRaw</code>: the first union member whose
   * whiteSpace-normalised reading of the raw value validates (XSD 1.1
   * assertions and CTA need it).
   *
   * @param aType
   *        The union type.
   * @param sRaw
   *        The raw value.
   * @return The member, or <code>null</code>.
   */
  @Nullable
  static XsdSimpleType firstMemberRaw (@NonNull final XsdSimpleType aType, @NonNull final String sRaw)
  {
    return _firstMember (aType, sRaw);
  }
}
