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

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;

/**
 * The constraining facets declared at one restriction step of a simple type
 * (go-xslt <code>facetSet</code>). A value object: {@link #getClone()} gives a
 * copy whose lists may be replaced, but not modified, independently.
 *
 * @author Philip Helger
 */
final class XsdFacetSet
{
  /**
   * The compiled patterns, one group per restriction step: a value must match
   * every group, and within a group one of its patterns.
   */
  ICommonsList <ICommonsList <XsdPattern>> m_aPatterns = new CommonsArrayList <> ();
  /** The source text of the patterns, group for group (kept with documentation only). */
  ICommonsList <ICommonsList <String>> m_aPatternSrc = new CommonsArrayList <> ();

  ICommonsList <String> m_aEnumeration = new CommonsArrayList <> ();
  /**
   * The enumeration expanded against the facet element's own namespace
   * bindings, in Clark form, one entry per enumeration entry. Read only for an
   * xs:QName/xs:NOTATION-based type.
   */
  ICommonsList <String> m_aEnumQName = new CommonsArrayList <> ();
  boolean m_bHasEnum;

  int m_nLength;
  int m_nMinLength;
  int m_nMaxLength;
  boolean m_bHasLength;
  boolean m_bHasMinLength;
  boolean m_bHasMaxLength;

  String m_sMinIncl = "";
  String m_sMaxIncl = "";
  String m_sMinExcl = "";
  String m_sMaxExcl = "";
  boolean m_bHasMinIncl;
  boolean m_bHasMaxIncl;
  boolean m_bHasMinExcl;
  boolean m_bHasMaxExcl;

  int m_nTotalDigits;
  int m_nFractionDigits;
  boolean m_bHasTotalDigits;
  boolean m_bHasFractionDigits;

  /** The declared whiteSpace value, empty if none. */
  String m_sWhiteSpace = "";
  /** The declared explicitTimezone value (collapsed), empty if none. */
  String m_sExplicitTZ = "";

  /**
   * The facets declared with <code>fixed="true"</code> at this step (by facet
   * element name); a restriction may not change a fixed facet's value.
   */
  ICommonsSet <String> m_aFixed = new CommonsHashSet <> ();

  /** @return <code>true</code> if any facet is declared at this step. */
  boolean any ()
  {
    return m_bHasEnum ||
           m_bHasLength ||
           m_bHasMinLength ||
           m_bHasMaxLength ||
           m_bHasMinIncl ||
           m_bHasMaxIncl ||
           m_bHasMinExcl ||
           m_bHasMaxExcl ||
           m_bHasTotalDigits ||
           m_bHasFractionDigits ||
           m_aPatterns.isNotEmpty () ||
           !m_sWhiteSpace.isEmpty () ||
           !m_sExplicitTZ.isEmpty ();
  }

  /** @return A shallow copy (the Go value copy). */
  XsdFacetSet getClone ()
  {
    final XsdFacetSet ret = new XsdFacetSet ();
    ret.m_aPatterns = m_aPatterns.getClone ();
    ret.m_aPatternSrc = m_aPatternSrc.getClone ();
    ret.m_aEnumeration = m_aEnumeration.getClone ();
    ret.m_aEnumQName = m_aEnumQName.getClone ();
    ret.m_bHasEnum = m_bHasEnum;
    ret.m_nLength = m_nLength;
    ret.m_nMinLength = m_nMinLength;
    ret.m_nMaxLength = m_nMaxLength;
    ret.m_bHasLength = m_bHasLength;
    ret.m_bHasMinLength = m_bHasMinLength;
    ret.m_bHasMaxLength = m_bHasMaxLength;
    ret.m_sMinIncl = m_sMinIncl;
    ret.m_sMaxIncl = m_sMaxIncl;
    ret.m_sMinExcl = m_sMinExcl;
    ret.m_sMaxExcl = m_sMaxExcl;
    ret.m_bHasMinIncl = m_bHasMinIncl;
    ret.m_bHasMaxIncl = m_bHasMaxIncl;
    ret.m_bHasMinExcl = m_bHasMinExcl;
    ret.m_bHasMaxExcl = m_bHasMaxExcl;
    ret.m_nTotalDigits = m_nTotalDigits;
    ret.m_nFractionDigits = m_nFractionDigits;
    ret.m_bHasTotalDigits = m_bHasTotalDigits;
    ret.m_bHasFractionDigits = m_bHasFractionDigits;
    ret.m_sWhiteSpace = m_sWhiteSpace;
    ret.m_sExplicitTZ = m_sExplicitTZ;
    ret.m_aFixed = m_aFixed.getClone ();
    return ret;
  }
}
