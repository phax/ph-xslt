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
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.value.EAtomicType;

/**
 * A simple type definition of the read-only component model (go-xslt
 * <code>SimpleTypeView</code>).
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdSimpleTypeView implements IXsdTypeDefinitionView
{
  private static final int MAX_CHAIN = 64;
  private static final XsdName ANY_SIMPLE_TYPE = new XsdName (XsdConstants.NS_XS, "anySimpleType");

  private final XsdSchemaView m_aView;
  private final XsdSimpleType m_aType;

  XsdSimpleTypeView (@NonNull final XsdSchemaView aView, @NonNull final XsdSimpleType aType)
  {
    m_aView = aView;
    m_aType = aType;
  }

  public boolean isSimple ()
  {
    return true;
  }

  /** @return The type's xs:documentation (needs documentation kept at compile time). */
  @NonNull
  public ICommonsList <XsdDocumentation> getDocumentation ()
  {
    return m_aType.m_aDocs.getClone ();
  }

  /**
   * @return The constraining facets in force: each as declared by the nearest
   *         restriction step that sets it. Intrinsic bounds of built-ins are not
   *         reported; facets of a list type constrain the list.
   */
  @NonNull
  public XsdFacetsView getFacets ()
  {
    Integer aLength = null;
    Integer aMinLength = null;
    Integer aMaxLength = null;
    String sMinIncl = null;
    String sMaxIncl = null;
    String sMinExcl = null;
    String sMaxExcl = null;
    Integer aTotalDigits = null;
    Integer aFractionDigits = null;
    String sExplicitTZ = "";
    final ICommonsList <ICommonsList <String>> aPatterns = new CommonsArrayList <> ();
    XsdSimpleType s = m_aType;
    for (int n = 0; s != null && n < MAX_CHAIN; n++)
    {
      final XsdFacetSet f = s.m_aFacets;
      if (aLength == null && f.m_bHasLength)
        aLength = Integer.valueOf (f.m_nLength);
      if (aMinLength == null && f.m_bHasMinLength)
        aMinLength = Integer.valueOf (f.m_nMinLength);
      if (aMaxLength == null && f.m_bHasMaxLength)
        aMaxLength = Integer.valueOf (f.m_nMaxLength);
      if (sMinIncl == null && f.m_bHasMinIncl)
        sMinIncl = f.m_sMinIncl;
      if (sMaxIncl == null && f.m_bHasMaxIncl)
        sMaxIncl = f.m_sMaxIncl;
      if (sMinExcl == null && f.m_bHasMinExcl)
        sMinExcl = f.m_sMinExcl;
      if (sMaxExcl == null && f.m_bHasMaxExcl)
        sMaxExcl = f.m_sMaxExcl;
      if (aTotalDigits == null && f.m_bHasTotalDigits)
        aTotalDigits = Integer.valueOf (f.m_nTotalDigits);
      if (aFractionDigits == null && f.m_bHasFractionDigits)
        aFractionDigits = Integer.valueOf (f.m_nFractionDigits);
      if (sExplicitTZ.isEmpty ())
        sExplicitTZ = f.m_sExplicitTZ;
      // Every step's patterns must hold; base-most step last
      for (final ICommonsList <String> aGroup : f.m_aPatternSrc)
        aPatterns.add (aGroup.getClone ());
      s = s.m_aBase;
    }
    return new XsdFacetsView (aLength,
                              aMinLength,
                              aMaxLength,
                              sMinIncl,
                              sMaxIncl,
                              sMinExcl,
                              sMaxExcl,
                              aTotalDigits,
                              aFractionDigits,
                              m_aType.effectiveWhiteSpace (),
                              aPatterns,
                              sExplicitTZ);
  }

  /**
   * @return The expanded name; for a reference to a built-in type that is the
   *         built-in's own; {@link XsdName#NONE} for an anonymous type.
   */
  @NonNull
  public XsdName getName ()
  {
    if (!m_aType.m_aName.isZero ())
      return m_aType.m_aName;
    if (m_aType.m_bBaseBuiltin && !m_aType.m_bDeclared && m_aType.m_eVariety == EXsdVariety.ATOMIC)
      return getBuiltinBase ();
    return XsdName.NONE;
  }

  @NonNull
  public XsdName getBuiltinBase ()
  {
    switch (m_aType.m_eVariety)
    {
      case LIST:
        if (m_aType.m_aItem != null)
          return new XsdSimpleTypeView (m_aView, m_aType.m_aItem).getBuiltinBase ();
        return ANY_SIMPLE_TYPE;
      case UNION:
        return ANY_SIMPLE_TYPE;
      default:
        final EAtomicType ePrim = m_aType.resolvePrim ();
        if (ePrim == EAtomicType.ANY_ATOMIC_TYPE)
          return ANY_SIMPLE_TYPE;
        return new XsdName (XsdConstants.NS_XS, ePrim.getLocalName ());
    }
  }

  /** @return The variety. */
  @NonNull
  public EXsdVariety getVariety ()
  {
    return m_aType.m_eVariety;
  }

  /** @return The type this one restricts; <code>null</code> when it is or derives directly from a built-in. */
  @Nullable
  public XsdSimpleTypeView getBase ()
  {
    return m_aView.simple (m_aType.m_aBase);
  }

  /** @return A list's item type; <code>null</code> otherwise. */
  @Nullable
  public XsdSimpleTypeView getItemType ()
  {
    return m_aView.simple (m_aType.m_aItem);
  }

  /** @return A union's member types; empty otherwise. */
  @NonNull
  public ICommonsList <XsdSimpleTypeView> getMemberTypes ()
  {
    final ICommonsList <XsdSimpleTypeView> ret = new CommonsArrayList <> ();
    for (final XsdSimpleType aMember : m_aType.m_aMembers)
      ret.add (m_aView.simple (aMember));
    return ret;
  }

  /**
   * @return The enumeration values of the nearest restriction step that
   *         declares any, in document order; empty if none.
   */
  @NonNull
  public ICommonsList <String> getEnumeration ()
  {
    XsdSimpleType s = m_aType;
    for (int n = 0; s != null && n < MAX_CHAIN; n++)
    {
      if (s.m_aFacets.m_bHasEnum)
        return s.m_aFacets.m_aEnumeration.getClone ();
      s = s.m_aBase;
    }
    return new CommonsArrayList <> ();
  }

  @Override
  public String toString ()
  {
    return "XsdSimpleTypeView[" + getName () + "]";
  }
}
