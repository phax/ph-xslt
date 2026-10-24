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
package com.helger.xpath.value;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The built-in atomic types of XML Schema that XPath 3.1 knows, with their
 * derivation hierarchy. {@link #NUMERIC} and {@link #ERROR} are pseudo types:
 * the union <code>xs:numeric</code> and the empty type <code>xs:error</code>.
 *
 * @author Philip Helger
 */
public enum EAtomicType
{
  ANY_ATOMIC_TYPE ("anyAtomicType", null),
  UNTYPED_ATOMIC ("untypedAtomic", ANY_ATOMIC_TYPE),
  STRING ("string", ANY_ATOMIC_TYPE),
  NORMALIZED_STRING ("normalizedString", STRING),
  TOKEN ("token", NORMALIZED_STRING),
  LANGUAGE ("language", TOKEN),
  NMTOKEN ("NMTOKEN", TOKEN),
  NAME ("Name", TOKEN),
  NCNAME ("NCName", NAME),
  ID ("ID", NCNAME),
  IDREF ("IDREF", NCNAME),
  ENTITY ("ENTITY", NCNAME),
  BOOLEAN ("boolean", ANY_ATOMIC_TYPE),
  DECIMAL ("decimal", ANY_ATOMIC_TYPE),
  INTEGER ("integer", DECIMAL),
  NON_POSITIVE_INTEGER ("nonPositiveInteger", INTEGER),
  NEGATIVE_INTEGER ("negativeInteger", NON_POSITIVE_INTEGER),
  LONG ("long", INTEGER),
  INT ("int", LONG),
  SHORT ("short", INT),
  BYTE ("byte", SHORT),
  NON_NEGATIVE_INTEGER ("nonNegativeInteger", INTEGER),
  UNSIGNED_LONG ("unsignedLong", NON_NEGATIVE_INTEGER),
  UNSIGNED_INT ("unsignedInt", UNSIGNED_LONG),
  UNSIGNED_SHORT ("unsignedShort", UNSIGNED_INT),
  UNSIGNED_BYTE ("unsignedByte", UNSIGNED_SHORT),
  POSITIVE_INTEGER ("positiveInteger", NON_NEGATIVE_INTEGER),
  DOUBLE ("double", ANY_ATOMIC_TYPE),
  FLOAT ("float", ANY_ATOMIC_TYPE),
  DATE_TIME ("dateTime", ANY_ATOMIC_TYPE),
  DATE_TIME_STAMP ("dateTimeStamp", DATE_TIME),
  DATE ("date", ANY_ATOMIC_TYPE),
  TIME ("time", ANY_ATOMIC_TYPE),
  DURATION ("duration", ANY_ATOMIC_TYPE),
  YEAR_MONTH_DURATION ("yearMonthDuration", DURATION),
  DAY_TIME_DURATION ("dayTimeDuration", DURATION),
  G_YEAR_MONTH ("gYearMonth", ANY_ATOMIC_TYPE),
  G_YEAR ("gYear", ANY_ATOMIC_TYPE),
  G_MONTH_DAY ("gMonthDay", ANY_ATOMIC_TYPE),
  G_DAY ("gDay", ANY_ATOMIC_TYPE),
  G_MONTH ("gMonth", ANY_ATOMIC_TYPE),
  HEX_BINARY ("hexBinary", ANY_ATOMIC_TYPE),
  BASE64_BINARY ("base64Binary", ANY_ATOMIC_TYPE),
  ANY_URI ("anyURI", ANY_ATOMIC_TYPE),
  QNAME ("QName", ANY_ATOMIC_TYPE),
  NOTATION ("NOTATION", ANY_ATOMIC_TYPE),
  /** The union of xs:double, xs:float and xs:decimal. Never the type of a value. */
  NUMERIC ("numeric", ANY_ATOMIC_TYPE),
  /** The empty type; no value has it. */
  ERROR ("error", ANY_ATOMIC_TYPE);

  private static final Map <String, EAtomicType> BY_LOCAL_NAME = new HashMap <> ();
  static
  {
    for (final EAtomicType e : values ())
      BY_LOCAL_NAME.put (e.m_sLocalName, e);
  }

  private final String m_sLocalName;
  private final EAtomicType m_eParent;

  EAtomicType (@NonNull final String sLocalName, @Nullable final EAtomicType eParent)
  {
    m_sLocalName = sLocalName;
    m_eParent = eParent;
  }

  /** @return The local name, e.g. <code>integer</code>. */
  @NonNull
  public String getLocalName ()
  {
    return m_sLocalName;
  }

  /** @return The name with the conventional prefix, e.g. <code>xs:integer</code>. */
  @NonNull
  public String getQualifiedName ()
  {
    return "xs:" + m_sLocalName;
  }

  /** @return The base type, <code>null</code> for xs:anyAtomicType. */
  @Nullable
  public EAtomicType getParent ()
  {
    return m_eParent;
  }

  /**
   * @param eOther
   *        Candidate ancestor.
   * @return <code>true</code> if this is the same as or derived from the
   *         other type. {@link #NUMERIC} is treated as the union: DOUBLE,
   *         FLOAT, DECIMAL and everything derived from them are derived from
   *         it.
   */
  public boolean isDerivedFrom (@NonNull final EAtomicType eOther)
  {
    if (eOther == NUMERIC)
      return isNumeric ();
    for (EAtomicType e = this; e != null; e = e.m_eParent)
      if (e == eOther)
        return true;
    return false;
  }

  public boolean isNumeric ()
  {
    return this == DOUBLE || this == FLOAT || isDerivedFrom (DECIMAL) && this != NUMERIC;
  }

  /** @return <code>true</code> for xs:integer and all its derived types. */
  public boolean isIntegerFamily ()
  {
    return isDerivedFrom (INTEGER);
  }

  /** @return <code>true</code> for xs:string and all its derived types. */
  public boolean isStringFamily ()
  {
    return isDerivedFrom (STRING);
  }

  /** @return <code>true</code> for xs:duration and its two subtypes. */
  public boolean isDurationFamily ()
  {
    return isDerivedFrom (DURATION);
  }

  /** @return <code>true</code> for dateTime, dateTimeStamp, date, time and the g* types. */
  public boolean isDateTimeFamily ()
  {
    return isDerivedFrom (DATE_TIME) ||
           this == DATE ||
           this == TIME ||
           this == G_YEAR_MONTH ||
           this == G_YEAR ||
           this == G_MONTH_DAY ||
           this == G_DAY ||
           this == G_MONTH;
  }

  /** @return <code>true</code> for hexBinary and base64Binary. */
  public boolean isBinary ()
  {
    return this == HEX_BINARY || this == BASE64_BINARY;
  }

  /**
   * @param sLocalName
   *        Local name without prefix, e.g. <code>integer</code>.
   * @return <code>null</code> if there is no such built-in type.
   */
  @Nullable
  public static EAtomicType getFromLocalName (@Nullable final String sLocalName)
  {
    return sLocalName == null ? null : BY_LOCAL_NAME.get (sLocalName);
  }
}
