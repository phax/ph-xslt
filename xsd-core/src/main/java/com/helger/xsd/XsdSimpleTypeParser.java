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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Compiles <code>xs:simpleType</code> definitions (go-xslt
 * <code>loader.go</code>: <code>parseSimpleTypeInto</code>,
 * <code>parseRestriction</code>, <code>parseList</code>,
 * <code>parseUnion</code>, <code>parseFacets</code>,
 * <code>checkFacetConsistency</code>, <code>checkNotationUsable</code>;
 * <code>structure.go</code>: <code>checkSimpleTypeStructure</code>). The
 * facet-value checks and the pattern compilation are phase V3
 * ({@link XsdFacetValidator}, {@link XsdPattern}).
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class XsdSimpleTypeParser
{
  private static final int MAX_CHAIN = 64;

  private final XsdCompiler m_aC;
  /**
   * Every simple type whose definition is being parsed, with its derivation
   * kind (restriction, list or union). A reference to one from inside its own
   * definition is a circular derivation, tolerated only as a union member in
   * XSD 1.0 (see {@link #_closeUnion}).
   */
  private final Map <XsdSimpleType, String> m_aOpen = new IdentityHashMap <> ();

  XsdSimpleTypeParser (@NonNull final XsdCompiler aCompiler)
  {
    m_aC = aCompiler;
  }

  /**
   * go-xslt <code>checkSimpleTypeStructure</code>: at most one leading
   * annotation, exactly one of restriction/list/union, a name exactly on
   * top-level definitions, and a base by reference or inline, never both.
   */
  private static void _checkSimpleTypeStructure (@NonNull final IXdmNode aNode)
  {
    int nAnnot = 0;
    int nDeriv = 0;
    boolean bSawDeriv = false;
    for (final IXdmNode aChild : XsdNodes.children (aNode))
    {
      if (!XsdNodes.isXSElement (aChild))
        continue;
      switch (XsdNodes.localName (aChild))
      {
        case "annotation":
          nAnnot++;
          if (bSawDeriv)
            throw XsdCompiler.invalid ("", "xs:annotation must be the first child of xs:simpleType");
          break;
        case "restriction":
        case "list":
        case "union":
          nDeriv++;
          bSawDeriv = true;
          break;
        default:
          bSawDeriv = true;
          break;
      }
    }
    if (nAnnot > 1)
      throw XsdCompiler.invalid ("", "xs:simpleType has more than one annotation");
    if (nDeriv != 1)
      throw XsdCompiler.invalid ("", "xs:simpleType must have exactly one of restriction/list/union");
    final boolean bNamed = XsdNodes.hasAttr (aNode, "name");
    final boolean bTopLevel = XsdNodes.isTopLevelParent (aNode.getParent ());
    if (bNamed && !bTopLevel)
      throw XsdCompiler.invalid ("st-props-correct", "a local xs:simpleType must not have a name");
    if (!bNamed && bTopLevel)
      throw XsdCompiler.invalid ("st-props-correct", "a top-level xs:simpleType requires a name");
    for (final IXdmNode aChild : XsdNodes.children (aNode))
    {
      if (!XsdNodes.isXSElement (aChild))
        continue;
      final String sRefAttr;
      switch (XsdNodes.localName (aChild))
      {
        case "restriction":
          sRefAttr = "base";
          break;
        case "list":
          sRefAttr = "itemType";
          break;
        default:
          continue;
      }
      int nInline = 0;
      for (final IXdmNode aSub : XsdNodes.children (aChild))
        if (XsdNodes.isXS (aSub, "simpleType"))
          nInline++;
      final boolean bHasRef = XsdNodes.hasAttr (aChild, sRefAttr);
      if (nInline > 1)
        throw XsdCompiler.invalid ("", "xs:" + XsdNodes.localName (aChild) + " may hold at most one inline xs:simpleType");
      if (bHasRef && nInline > 0)
        throw XsdCompiler.invalid ("",
                                   "xs:" +
                                       XsdNodes.localName (aChild) +
                                       " has both @" +
                                       sRefAttr +
                                       " and an inline xs:simpleType");
    }
  }

  /**
   * go-xslt <code>scalarFacet</code>: a facet's declared value by element name,
   * for the fixed-facet comparison.
   *
   * @return The value, or <code>null</code> if the facet is not declared.
   */
  @Nullable
  private static String _scalarFacet (@NonNull final XsdFacetSet aFS, @NonNull final String sName)
  {
    switch (sName)
    {
      case "whiteSpace":
        return aFS.m_sWhiteSpace.isEmpty () ? null : aFS.m_sWhiteSpace;
      case "explicitTimezone":
        return aFS.m_sExplicitTZ.isEmpty () ? null : aFS.m_sExplicitTZ;
      case "length":
        return aFS.m_bHasLength ? Integer.toString (aFS.m_nLength) : null;
      case "minLength":
        return aFS.m_bHasMinLength ? Integer.toString (aFS.m_nMinLength) : null;
      case "maxLength":
        return aFS.m_bHasMaxLength ? Integer.toString (aFS.m_nMaxLength) : null;
      case "totalDigits":
        return aFS.m_bHasTotalDigits ? Integer.toString (aFS.m_nTotalDigits) : null;
      case "fractionDigits":
        return aFS.m_bHasFractionDigits ? Integer.toString (aFS.m_nFractionDigits) : null;
      case "minInclusive":
        return aFS.m_bHasMinIncl ? aFS.m_sMinIncl : null;
      case "maxInclusive":
        return aFS.m_bHasMaxIncl ? aFS.m_sMaxIncl : null;
      case "minExclusive":
        return aFS.m_bHasMinExcl ? aFS.m_sMinExcl : null;
      case "maxExclusive":
        return aFS.m_bHasMaxExcl ? aFS.m_sMaxExcl : null;
      default:
        return null;
    }
  }

  /**
   * go-xslt <code>builtinIntBounds</code>: the implicit inclusive bounds of a
   * bounded built-in integer type (<code>null</code> for an unbounded side).
   *
   * @return Two elements: lower and upper bound.
   */
  @NonNull
  private static String [] _builtinIntBounds (@NonNull final EAtomicType eType)
  {
    switch (eType)
    {
      case POSITIVE_INTEGER:
        return new String [] { "1", null };
      case NON_NEGATIVE_INTEGER:
        return new String [] { "0", null };
      case NEGATIVE_INTEGER:
        return new String [] { null, "-1" };
      case NON_POSITIVE_INTEGER:
        return new String [] { null, "0" };
      case LONG:
        return new String [] { "-9223372036854775808", "9223372036854775807" };
      case INT:
        return new String [] { "-2147483648", "2147483647" };
      case SHORT:
        return new String [] { "-32768", "32767" };
      case BYTE:
        return new String [] { "-128", "127" };
      case UNSIGNED_LONG:
        return new String [] { "0", "18446744073709551615" };
      case UNSIGNED_INT:
        return new String [] { "0", "4294967295" };
      case UNSIGNED_SHORT:
        return new String [] { "0", "65535" };
      case UNSIGNED_BYTE:
        return new String [] { "0", "255" };
      default:
        return new String [] { null, null };
    }
  }

  /**
   * Whether the enumeration value is valid against the base type
   * (go-xslt <code>st.base.validate(e) != nil</code>).
   */
  private static boolean _isValidAgainst (@NonNull final XsdSimpleType aType, @NonNull final String sValue)
  {
    try
    {
      XsdSimpleTypeValidator.validate (aType, sValue);
      return true;
    }
    catch (final XsdInvalidException ex)
    {
      return false;
    }
  }

  /**
   * go-xslt <code>checkFacetConsistency</code>: a restriction step's facets
   * narrow, never loosen, the corresponding facets of its base.
   */
  private static void _checkFacetConsistency (@NonNull final XsdSimpleType aType)
  {
    if (aType.m_aBase == null && !aType.m_bBaseBuiltin)
      return;
    final XsdFacetSet f = aType.m_aFacets;
    int nBaseMaxLen = -1;
    int nBaseMinLen = -1;
    int nBaseTotal = -1;
    int nBaseFrac = -1;
    XsdSimpleType b = aType.m_aBase;
    for (int n = 0; b != null && n < MAX_CHAIN; n++)
    {
      final XsdFacetSet bf = b.m_aFacets;
      if (bf.m_bHasMaxLength && (nBaseMaxLen < 0 || bf.m_nMaxLength < nBaseMaxLen))
        nBaseMaxLen = bf.m_nMaxLength;
      if (bf.m_bHasMinLength && bf.m_nMinLength > nBaseMinLen)
        nBaseMinLen = bf.m_nMinLength;
      if (bf.m_bHasTotalDigits && (nBaseTotal < 0 || bf.m_nTotalDigits < nBaseTotal))
        nBaseTotal = bf.m_nTotalDigits;
      if (bf.m_bHasFractionDigits && (nBaseFrac < 0 || bf.m_nFractionDigits < nBaseFrac))
        nBaseFrac = bf.m_nFractionDigits;
      if (b.m_bBaseBuiltin)
        break;
      b = b.m_aBase;
    }
    if (f.m_bHasMaxLength && nBaseMaxLen >= 0 && f.m_nMaxLength > nBaseMaxLen)
      throw XsdCompiler.invalid ("", "maxLength exceeds the base maxLength");
    if (f.m_bHasMinLength && nBaseMinLen >= 0 && f.m_nMinLength < nBaseMinLen)
      throw XsdCompiler.invalid ("", "minLength is below the base minLength");
    // A derived maxLength below an inherited minLength empties the type
    if (f.m_bHasMaxLength && nBaseMinLen >= 0 && f.m_nMaxLength < nBaseMinLen)
      throw XsdCompiler.invalid ("", "maxLength is below the base minLength");
    // length may co-occur with minLength/maxLength only when that facet is
    // inherited (WG bug 6446)
    if (f.m_bHasLength && f.m_bHasMinLength && nBaseMinLen < 0)
      throw XsdCompiler.invalid ("length-minLength-maxLength",
                                 "length cannot appear with minLength unless minLength is inherited");
    if (f.m_bHasLength && f.m_bHasMaxLength && nBaseMaxLen < 0)
      throw XsdCompiler.invalid ("length-minLength-maxLength",
                                 "length cannot appear with maxLength unless maxLength is inherited");
    // Enumeration literals of a list restriction must be valid against the base
    if (aType.m_eVariety == EXsdVariety.LIST && f.m_bHasEnum && aType.m_aBase != null)
      for (final String sEnum : f.m_aEnumeration)
        if (!_isValidAgainst (aType.m_aBase, sEnum))
          throw XsdCompiler.invalid ("enumeration-valid-restriction",
                                     "enumeration value " + XsdLexHelper.quote (sEnum) + " is not valid against the base type");
    if (f.m_bHasTotalDigits && nBaseTotal >= 0 && f.m_nTotalDigits > nBaseTotal)
      throw XsdCompiler.invalid ("", "totalDigits exceeds the base totalDigits");
    if (f.m_bHasFractionDigits && nBaseFrac >= 0 && f.m_nFractionDigits > nBaseFrac)
      throw XsdCompiler.invalid ("", "fractionDigits exceeds the base fractionDigits");
    if (!f.m_sWhiteSpace.isEmpty ())
    {
      // The base's effective whiteSpace: from the base chain, or the builtin's
      // own default for a direct builtin restriction
      String sBaseWS = XsdWhiteSpace.primitiveWhiteSpace (aType.resolvePrim (), aType.m_eVariety);
      if (aType.m_aBase != null)
        sBaseWS = aType.m_aBase.effectiveWhiteSpace ();
      if (XsdWhiteSpace.rank (f.m_sWhiteSpace) < XsdWhiteSpace.rank (sBaseWS))
        throw XsdCompiler.invalid ("",
                                   "whiteSpace " +
                                       XsdLexHelper.quote (f.m_sWhiteSpace) +
                                       " weakens the base whiteSpace " +
                                       XsdLexHelper.quote (sBaseWS));
    }
    // Bounds narrowing across facet kinds; the nearest base bound of either
    // kind is the effective one
    String sBHi = "";
    String sBLo = "";
    boolean bBHiExcl = false;
    boolean bBLoExcl = false;
    b = aType.m_aBase;
    for (int n = 0; b != null && n < MAX_CHAIN; n++)
    {
      final XsdFacetSet bf = b.m_aFacets;
      if (sBHi.isEmpty ())
      {
        if (bf.m_bHasMaxIncl)
        {
          sBHi = bf.m_sMaxIncl;
          bBHiExcl = false;
        }
        else
          if (bf.m_bHasMaxExcl)
          {
            sBHi = bf.m_sMaxExcl;
            bBHiExcl = true;
          }
      }
      if (sBLo.isEmpty ())
      {
        if (bf.m_bHasMinIncl)
        {
          sBLo = bf.m_sMinIncl;
          bBLoExcl = false;
        }
        else
          if (bf.m_bHasMinExcl)
          {
            sBLo = bf.m_sMinExcl;
            bBLoExcl = true;
          }
      }
      if (b.m_bBaseBuiltin)
        break;
      b = b.m_aBase;
    }
    final EAtomicType ePrim = aType.resolvePrim ();
    String sDHi = f.m_sMaxIncl;
    boolean bDHiExcl = false;
    boolean bDHasHi = f.m_bHasMaxIncl;
    if (f.m_bHasMaxExcl)
    {
      sDHi = f.m_sMaxExcl;
      bDHiExcl = true;
      bDHasHi = true;
    }
    String sDLo = f.m_sMinIncl;
    boolean bDLoExcl = false;
    boolean bDHasLo = f.m_bHasMinIncl;
    if (f.m_bHasMinExcl)
    {
      sDLo = f.m_sMinExcl;
      bDLoExcl = true;
      bDHasLo = true;
    }
    if (bDHasHi && !sBHi.isEmpty ())
    {
      final Integer aCmp = XsdValueCompare.compareLexical (sDHi, sBHi, ePrim);
      if (aCmp != null)
      {
        final int c = aCmp.intValue ();
        if (c > 0 || c == 0 && bBHiExcl && !bDHiExcl)
          throw XsdCompiler.invalid ("",
                                     "upper bound " +
                                         XsdLexHelper.quote (sDHi) +
                                         " loosens the base upper bound " +
                                         XsdLexHelper.quote (sBHi));
      }
    }
    if (bDHasLo && !sBLo.isEmpty ())
    {
      final Integer aCmp = XsdValueCompare.compareLexical (sDLo, sBLo, ePrim);
      if (aCmp != null)
      {
        final int c = aCmp.intValue ();
        if (c < 0 || c == 0 && bBLoExcl && !bDLoExcl)
          throw XsdCompiler.invalid ("",
                                     "lower bound " +
                                         XsdLexHelper.quote (sDLo) +
                                         " loosens the base lower bound " +
                                         XsdLexHelper.quote (sBLo));
      }
    }
    // Cross-direction: a derived upper bound below the base's lower bound (or
    // vice versa) yields an empty value space
    if (bDHasHi && !sBLo.isEmpty ())
    {
      final Integer aCmp = XsdValueCompare.compareLexical (sDHi, sBLo, ePrim);
      if (aCmp != null)
      {
        final int c = aCmp.intValue ();
        if (c < 0 || c == 0 && (bDHiExcl || bBLoExcl))
          throw XsdCompiler.invalid ("",
                                     "upper bound " +
                                         XsdLexHelper.quote (sDHi) +
                                         " is below the base lower bound " +
                                         XsdLexHelper.quote (sBLo));
      }
    }
    if (bDHasLo && !sBHi.isEmpty ())
    {
      final Integer aCmp = XsdValueCompare.compareLexical (sDLo, sBHi, ePrim);
      if (aCmp != null)
      {
        final int c = aCmp.intValue ();
        if (c > 0 || c == 0 && (bDLoExcl || bBHiExcl))
          throw XsdCompiler.invalid ("",
                                     "lower bound " +
                                         XsdLexHelper.quote (sDLo) +
                                         " is above the base upper bound " +
                                         XsdLexHelper.quote (sBHi));
      }
    }
    // Built-in implicit bounds of the bounded integer types, compared as
    // xs:integer
    if (ePrim.isIntegerFamily ())
    {
      final String [] aBounds = _builtinIntBounds (ePrim);
      if (bDHasHi && aBounds[0] != null)
      {
        final Integer aCmp = XsdValueCompare.compareLexical (sDHi, aBounds[0], EAtomicType.INTEGER);
        if (aCmp != null && (aCmp.intValue () < 0 || aCmp.intValue () == 0 && bDHiExcl))
          throw XsdCompiler.invalid ("",
                                     "upper bound " +
                                         XsdLexHelper.quote (sDHi) +
                                         " empties the " +
                                         ePrim.getQualifiedName () +
                                         " value space");
      }
      if (bDHasLo && aBounds[1] != null)
      {
        final Integer aCmp = XsdValueCompare.compareLexical (sDLo, aBounds[1], EAtomicType.INTEGER);
        if (aCmp != null && (aCmp.intValue () > 0 || aCmp.intValue () == 0 && bDLoExcl))
          throw XsdCompiler.invalid ("",
                                     "lower bound " +
                                         XsdLexHelper.quote (sDLo) +
                                         " empties the " +
                                         ePrim.getQualifiedName () +
                                         " value space");
      }
    }
    // explicitTimezone Valid Restriction: a base required/prohibited pins the
    // value; the nearest base declaration wins
    if (!f.m_sExplicitTZ.isEmpty ())
    {
      b = aType.m_aBase;
      for (int n = 0; b != null && n < MAX_CHAIN; n++)
      {
        final String sBZ = b.m_aFacets.m_sExplicitTZ;
        if (!sBZ.isEmpty ())
        {
          if (("required".equals (sBZ) || "prohibited".equals (sBZ)) && !f.m_sExplicitTZ.equals (sBZ))
            throw XsdCompiler.invalid ("",
                                       "explicitTimezone " +
                                           XsdLexHelper.quote (f.m_sExplicitTZ) +
                                           " cannot loosen the base's " +
                                           XsdLexHelper.quote (sBZ));
          break;
        }
        b = b.m_aBase;
      }
    }
    // Fixed-facet preservation: a facet declared fixed anywhere in the base
    // chain may not take a different value
    b = aType.m_aBase;
    for (int n = 0; b != null && n < MAX_CHAIN; n++)
    {
      for (final String sName : b.m_aFacets.m_aFixed)
      {
        final String sBV = _scalarFacet (b.m_aFacets, sName);
        final String sDV = _scalarFacet (f, sName);
        if (sBV != null && sDV != null && !sDV.equals (sBV))
          throw XsdCompiler.invalid ("", "facet " + sName + " is fixed to " + XsdLexHelper.quote (sBV) + " in the base type");
      }
      b = b.m_aBase;
    }
  }

  private void _checkNotationUsable (@Nullable final XsdSimpleType aType,
                                     final int nDepth,
                                     @NonNull final Set <XsdSimpleType> aVisited)
  {
    if (aType == null || nDepth > MAX_CHAIN)
      return;
    // A union reachable along several paths (or from itself) is walked once
    if (aType.m_eVariety == EXsdVariety.UNION && !aVisited.add (aType))
      return;
    // A union used as a declaration's type may not carry a bare xs:NOTATION
    // as a direct member; one nested a union deeper escapes
    if (aType.m_eVariety == EXsdVariety.UNION)
    {
      for (final XsdSimpleType aMember : aType.m_aMembers)
        _checkNotationUsable (aMember, nDepth + 1, aVisited);
      return;
    }
    if (aType.m_eVariety != EXsdVariety.ATOMIC || aType.resolvePrim () != EAtomicType.NOTATION)
      return;
    XsdSimpleType s = aType;
    for (int n = 0; s != null && n < MAX_CHAIN; n++)
    {
      if (s.m_aFacets.m_bHasEnum)
      {
        // Each enumeration value must name a declared notation (by local name)
        for (final String sValue : s.m_aFacets.m_aEnumeration)
        {
          final String sLocal = sValue.substring (sValue.lastIndexOf (':') + 1).strip ();
          if (!m_aC.m_aNotationLocals.contains (sLocal))
            throw XsdCompiler.invalid ("enumeration-valid-notation",
                                       "enumeration value " + XsdLexHelper.quote (sValue) + " does not name a declared notation");
        }
        return;
      }
      s = s.m_aBase;
    }
    throw XsdCompiler.invalid ("enumeration-required-notation",
                               "xs:NOTATION may only be used through a restriction with an enumeration facet");
  }

  /**
   * go-xslt <code>unionReaches</code>: whether aTarget is reachable from the
   * given members along union membership (a restriction of a union carries its
   * base's members, so that edge is covered too). Iterative, each type entered
   * once.
   */
  private static boolean _unionReaches (@NonNull final Iterable <XsdSimpleType> aMembers,
                                        @NonNull final XsdSimpleType aTarget,
                                        @NonNull final XsdSimpleType aStart)
  {
    final Set <XsdSimpleType> aSeen = Collections.newSetFromMap (new IdentityHashMap <> ());
    aSeen.add (aStart);
    final Deque <XsdSimpleType> aTodo = new ArrayDeque <> ();
    for (final XsdSimpleType aMember : aMembers)
      aTodo.push (aMember);
    while (!aTodo.isEmpty ())
    {
      final XsdSimpleType aMember = aTodo.pop ();
      if (aMember == aTarget)
        return true;
      if (aMember == null || !aSeen.add (aMember))
        continue;
      for (final XsdSimpleType aSub : aMember.m_aMembers)
        aTodo.push (aSub);
    }
    return false;
  }

  /**
   * go-xslt <code>closeUnion</code>: end the construction of a union's member
   * list. XSD 1.0 tolerates a cycle of unions (stE110); left in the component
   * graph it would send every later walk over members into unbounded
   * recursion. A union's value space is the union of its members' value
   * spaces, so the least fixed point of a cycle is the members from which the
   * union is not reachable, collected in member order through the cyclic ones.
   * XSD 1.1 forbids the cycle (cos-no-circular-unions). Afterwards no cycle
   * runs through a finished union.
   */
  private void _closeUnion (@NonNull final XsdSimpleType aType)
  {
    if (!_unionReaches (aType.m_aMembers, aType, aType))
      return;
    if (m_aC.isXsd11 ())
      throw _circular ();
    final ICommonsList <XsdSimpleType> aMembers = new CommonsArrayList <> ();
    final Set <XsdSimpleType> aSeen = Collections.newSetFromMap (new IdentityHashMap <> ());
    aSeen.add (aType);
    // Depth-first in member order without recursion
    final Deque <Iterator <XsdSimpleType>> aStack = new ArrayDeque <> ();
    aStack.push (aType.m_aMembers.iterator ());
    while (!aStack.isEmpty ())
    {
      final Iterator <XsdSimpleType> aIt = aStack.peek ();
      if (!aIt.hasNext ())
      {
        aStack.pop ();
        continue;
      }
      final XsdSimpleType aMember = aIt.next ();
      if (aMember == null || !aSeen.add (aMember))
        continue;
      if (_unionReaches (aMember.m_aMembers, aType, aMember))
        aStack.push (aMember.m_aMembers.iterator ());
      else
        aMembers.add (aMember);
    }
    // A new list: a restriction shares its base's list
    aType.m_aMembers = aMembers;
  }

  @NonNull
  private static XsdInvalidException _circular ()
  {
    return XsdCompiler.invalid ("", "circular simpleType derivation");
  }

  private void _parseRestriction (@NonNull final XsdSimpleType aType, @NonNull final IXdmNode aRestriction)
  {
    aType.m_eVariety = EXsdVariety.ATOMIC;
    final String sBaseRef = XsdNodes.attr (aRestriction, "base");
    if (sBaseRef != null)
    {
      final XsdSimpleType aBase = m_aC.resolveSimpleType (aRestriction, sBaseRef);
      if (m_aOpen.containsKey (aBase))
        throw _circular ();
      checkSimpleFinal (aBase, XsdDerivation.RESTRICTION);
      linkBase (aType, aBase);
    }
    else
    {
      final IXdmNode aInline = XsdNodes.firstXSChild (aRestriction, "simpleType");
      if (aInline == null)
        throw XsdCompiler.invalid ("", "restriction without a base");
      final XsdSimpleType aBase = new XsdSimpleType ();
      parseSimpleTypeInto (aBase, aInline);
      linkBase (aType, aBase);
    }
    if (!aType.m_bBaseBuiltin && aType.m_aBase != null)
      switch (aType.m_aBase.m_eVariety)
      {
        case LIST:
          aType.m_eVariety = EXsdVariety.LIST;
          aType.m_aItem = aType.m_aBase.m_aItem;
          break;
        case UNION:
          aType.m_eVariety = EXsdVariety.UNION;
          aType.m_aMembers = aType.m_aBase.m_aMembers;
          // The inline base union(aType, ...) of a type still being parsed
          // just handed aType itself back as a member
          _closeUnion (aType);
          break;
        default:
          break;
      }
    parseFacets (aType, aRestriction);
    _checkFacetConsistency (aType);
    checkNotationUsable (aType);
    XsdFacetValidator.validateFacetValues (aType, m_aC.m_eVersion);
  }

  private void _parseList (@NonNull final XsdSimpleType aType, @NonNull final IXdmNode aList)
  {
    aType.m_eVariety = EXsdVariety.LIST;
    final String sItemRef = XsdNodes.attr (aList, "itemType");
    if (sItemRef != null)
    {
      final IXsdType aResolved = m_aC.resolveTypeTolerant (aList, sItemRef);
      if (!(aResolved instanceof final XsdSimpleType aItem))
        throw XsdCompiler.invalid ("", "expected a simple type, got complex " + sItemRef);
      if (aItem != XsdSimpleType.ABSENT)
      {
        if (m_aOpen.containsKey (aItem))
          throw _circular ();
        checkNotationUsable (aItem);
        checkSimpleFinal (aItem, XsdDerivation.LIST);
        if (aItem.m_eVariety == EXsdVariety.LIST)
          throw XsdCompiler.invalid ("cos-list-of-atomic", "a list's item type must be atomic or a union (not a list)");
        // Nor is an ur-type a legal item type (1.1)
        if (m_aC.isXsd11 () && aItem.isURType ())
          throw XsdCompiler.invalid ("cos-list-of-atomic", "an ur-type is not a valid list item type");
      }
      aType.m_aItem = aItem;
      return;
    }
    final IXdmNode aInline = XsdNodes.firstXSChild (aList, "simpleType");
    if (aInline == null)
      throw XsdCompiler.invalid ("", "list without an item type");
    final XsdSimpleType aItem = new XsdSimpleType ();
    parseSimpleTypeInto (aItem, aInline);
    if (aItem.m_eVariety == EXsdVariety.LIST)
      throw XsdCompiler.invalid ("cos-list-of-atomic", "a list's item type must be atomic or a union (not a list)");
    if (m_aC.isXsd11 () && aItem.isURType ())
      throw XsdCompiler.invalid ("cos-list-of-atomic", "an ur-type is not a valid list item type");
    aType.m_aItem = aItem;
  }

  private void _parseUnion (@NonNull final XsdSimpleType aType, @NonNull final IXdmNode aUnion)
  {
    aType.m_eVariety = EXsdVariety.UNION;
    final String sMemberTypes = XsdNodes.attr (aUnion, "memberTypes");
    if (sMemberTypes != null)
      for (final String sRef : XsdLexHelper.xmlFields (sMemberTypes))
      {
        // src-resolve: a QName with an undeclared prefix does not resolve
        final int i = sRef.indexOf (':');
        if (i > 0 && XsdNodes.lookupPrefix (aUnion, sRef.substring (0, i)) == null)
          throw XsdCompiler.invalid ("src-resolve", "undefined namespace prefix in " + XsdLexHelper.quote (sRef));
        final XsdSimpleType aMember = m_aC.resolveSimpleType (aUnion, sRef);
        // A member still being parsed is an enclosing type: through a
        // restriction or list that is a circular derivation; a pure-union
        // cycle is cos-no-circular-unions in 1.1 and _closeUnion's in 1.0
        final String sOpenKind = m_aOpen.get (aMember);
        if (sOpenKind != null && (!"union".equals (sOpenKind) || m_aC.isXsd11 ()))
          throw _circular ();
        // XSD 1.1: the ur-types may not be union members
        if (m_aC.isXsd11 () && aMember.isURType ())
          throw XsdCompiler.invalid ("", sRef + " is not a valid union member type");
        // A bare xs:NOTATION member is legal; the enumeration rule applies
        // where a type is used
        checkSimpleFinal (aMember, XsdDerivation.UNION);
        aType.m_aMembers.add (aMember);
      }
    for (final IXdmNode aChild : XsdNodes.children (aUnion))
      if (XsdNodes.isXS (aChild, "simpleType"))
      {
        final XsdSimpleType aMember = new XsdSimpleType ();
        parseSimpleTypeInto (aMember, aChild);
        aType.m_aMembers.add (aMember);
      }
    if (aType.m_aMembers.isEmpty ())
      throw XsdCompiler.invalid ("", "union without members");
    _closeUnion (aType);
  }

  /**
   * go-xslt <code>checkSimpleFinal</code>: a derivation from a simple type
   * whose {final} forbids that method is invalid.
   */
  static void checkSimpleFinal (@Nullable final XsdSimpleType aBase, @NonNull final String sMethod)
  {
    if (aBase != null && aBase.m_aFinal.contains (sMethod))
      throw XsdCompiler.invalid ("st-props-correct.3",
                                 "derivation by " + sMethod + " from " + aBase.m_aName + " is forbidden by its final");
  }

  /**
   * go-xslt <code>linkBase</code>: a bare built-in base collapses into
   * {@link XsdSimpleType#m_ePrim}; anything else becomes the base link.
   */
  static void linkBase (@NonNull final XsdSimpleType aType, @NonNull final XsdSimpleType aBase)
  {
    if (aBase.m_bBaseBuiltin &&
        aBase.m_aBase == null &&
        aBase.m_aName.isZero () &&
        !aBase.m_aFacets.any () &&
        aBase.m_aAssertions.isEmpty () &&
        aBase.m_eVariety == EXsdVariety.ATOMIC)
    {
      aType.m_bBaseBuiltin = true;
      aType.m_ePrim = aBase.m_ePrim;
      return;
    }
    aType.m_aBase = aBase;
  }

  /**
   * go-xslt <code>checkNotationUsable</code>: xs:NOTATION may only be used
   * through a restriction that pins it down with an enumeration facet; applies
   * where a type is used as an element/attribute type or list item type.
   */
  void checkNotationUsable (@Nullable final XsdSimpleType aType)
  {
    _checkNotationUsable (aType, 0, Collections.newSetFromMap (new IdentityHashMap <> ()));
  }

  /** go-xslt <code>parseFacets</code>. */
  void parseFacets (@NonNull final XsdSimpleType aType, @NonNull final IXdmNode aRestriction)
  {
    final ICommonsList <XsdPattern> aPatternGroup = new CommonsArrayList <> ();
    final ICommonsList <String> aPatternSrc = new CommonsArrayList <> ();
    final ICommonsSet <String> aSeen = new CommonsHashSet <> ();
    final XsdFacetSet aFacets = aType.m_aFacets;
    for (final IXdmNode f : XsdNodes.children (aRestriction))
    {
      if (!XsdNodes.isXSElement (f))
        continue;
      final String sLocal = XsdNodes.localName (f);
      // Attribute uses and asserts of a simpleContent restriction are parsed
      // separately
      switch (sLocal)
      {
        case "attribute":
        case "attributeGroup":
        case "anyAttribute":
        case "assert":
          continue;
        default:
          break;
      }
      // A single-valued facet may appear at most once
      switch (sLocal)
      {
        case "annotation":
        case "simpleType":
        case "pattern":
        case "enumeration":
        case "assertion":
          break;
        default:
          if (!aSeen.add (sLocal))
            throw XsdCompiler.invalid ("", "facet xs:" + sLocal + " specified more than once");
          break;
      }
      final String sVal = XsdNodes.attrOrEmpty (f, "value");
      switch (sLocal)
      {
        case "annotation":
        case "simpleType":
          break;
        case "pattern":
        {
          // An XSD-invalid pattern makes the schema invalid
          XsdPattern.validateSyntax (sVal, m_aC.m_eVersion);
          aPatternGroup.add (XsdPattern.compile (sVal, m_aC.m_eVersion));
          if (m_aC.m_bKeepDocs)
            aPatternSrc.add (sVal);
          break;
        }
        case "enumeration":
          aFacets.m_aEnumeration.add (sVal);
          // Expanded where the facet element's namespace bindings are in hand
          aFacets.m_aEnumQName.add (XsdNodes.resolveQName (f, sVal).toString ());
          aFacets.m_bHasEnum = true;
          break;
        case "minInclusive":
          aFacets.m_sMinIncl = sVal;
          aFacets.m_bHasMinIncl = true;
          break;
        case "maxInclusive":
          aFacets.m_sMaxIncl = sVal;
          aFacets.m_bHasMaxIncl = true;
          break;
        case "minExclusive":
          aFacets.m_sMinExcl = sVal;
          aFacets.m_bHasMinExcl = true;
          break;
        case "maxExclusive":
          aFacets.m_sMaxExcl = sVal;
          aFacets.m_bHasMaxExcl = true;
          break;
        case "length":
        case "minLength":
        case "maxLength":
        case "fractionDigits":
        {
          final int n = XsdLexHelper.nonNegInt (sVal);
          if (n < 0)
            throw XsdCompiler.invalid ("", "facet xs:" + sLocal + " has an invalid value " + XsdLexHelper.quote (sVal));
          switch (sLocal)
          {
            case "length":
              aFacets.m_nLength = n;
              aFacets.m_bHasLength = true;
              break;
            case "minLength":
              aFacets.m_nMinLength = n;
              aFacets.m_bHasMinLength = true;
              break;
            case "maxLength":
              aFacets.m_nMaxLength = n;
              aFacets.m_bHasMaxLength = true;
              break;
            default:
              aFacets.m_nFractionDigits = n;
              aFacets.m_bHasFractionDigits = true;
              break;
          }
          break;
        }
        case "totalDigits":
        {
          // totalDigits must be a positiveInteger
          final int n = XsdLexHelper.nonNegInt (sVal);
          if (n < 1)
            throw XsdCompiler.invalid ("", "facet xs:totalDigits has an invalid value " + XsdLexHelper.quote (sVal));
          aFacets.m_nTotalDigits = n;
          aFacets.m_bHasTotalDigits = true;
          break;
        }
        case "whiteSpace":
          aFacets.m_sWhiteSpace = sVal;
          break;
        case "explicitTimezone":
          aFacets.m_sExplicitTZ = XsdWhiteSpace.collapse (sVal);
          break;
        case "assertion":
          // The XSD 1.1 assertion facet; 1.0 has no such facet
          if (!m_aC.isXsd11 ())
            throw XsdCompiler.invalid ("", "unknown facet xs:assertion");
          aType.m_aAssertions.add (m_aC.m_aComponents.parseAssert (f));
          break;
        default:
          throw XsdCompiler.invalid ("", "unknown facet xs:" + sLocal);
      }
      // A facet declared fixed="true" pins its value for every further step
      final String sFixed = XsdNodes.attr (f, "fixed");
      if (sFixed != null)
      {
        final String sFix = XsdWhiteSpace.collapse (sFixed);
        if ("true".equals (sFix) || "1".equals (sFix))
          aFacets.m_aFixed.add (sLocal);
      }
    }
    if (aPatternGroup.isNotEmpty ())
    {
      aFacets.m_aPatterns = new CommonsArrayList <> ();
      aFacets.m_aPatterns.add (aPatternGroup);
      if (m_aC.m_bKeepDocs)
      {
        aFacets.m_aPatternSrc = new CommonsArrayList <> ();
        aFacets.m_aPatternSrc.add (aPatternSrc);
      }
    }
  }

  /**
   * go-xslt <code>parseSimpleTypeInto</code>: fill a simple type from an
   * xs:simpleType element.
   *
   * @param aType
   *        The (pre-registered or new anonymous) type to fill.
   * @param aNode
   *        The xs:simpleType element.
   */
  void parseSimpleTypeInto (@NonNull final XsdSimpleType aType, @NonNull final IXdmNode aNode)
  {
    m_aC.enterNesting ();
    try
    {
      _checkSimpleTypeStructure (aNode);
      aType.m_bDeclared = true;
      aType.m_aDocs = m_aC.docsOf (aNode);
      final String sFinal = XsdNodes.attr (aNode, "final");
      if (sFinal != null)
        aType.m_aFinal = XsdDerivation.parseBlockSet (sFinal);
      else
        if (!m_aC.m_sFinalDefault.isEmpty ())
          aType.m_aFinal = XsdDerivation.parseBlockSet (m_aC.m_sFinalDefault);
      final IXdmNode aRestriction = XsdNodes.firstXSChild (aNode, "restriction");
      if (aRestriction != null)
      {
        m_aOpen.put (aType, "restriction");
        _parseRestriction (aType, aRestriction);
        return;
      }
      final IXdmNode aList = XsdNodes.firstXSChild (aNode, "list");
      if (aList != null)
      {
        m_aOpen.put (aType, "list");
        _parseList (aType, aList);
        return;
      }
      final IXdmNode aUnion = XsdNodes.firstXSChild (aNode, "union");
      if (aUnion != null)
      {
        m_aOpen.put (aType, "union");
        _parseUnion (aType, aUnion);
        return;
      }
      throw XsdCompiler.invalid ("", "simpleType without restriction/list/union");
    }
    finally
    {
      m_aOpen.remove (aType);
      m_aC.leaveNesting ();
    }
  }
}
