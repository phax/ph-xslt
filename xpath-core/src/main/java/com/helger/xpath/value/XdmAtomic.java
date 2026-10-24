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

import java.math.BigDecimal;
import java.math.BigInteger;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmQName;

/**
 * An immutable typed atomic value. The Java class of the payload is fixed per
 * type family:
 * <ul>
 * <li>xs:string and derived, xs:untypedAtomic, xs:anyURI: {@link String}</li>
 * <li>xs:boolean: {@link Boolean}</li>
 * <li>xs:integer and derived: {@link BigInteger}</li>
 * <li>xs:decimal: {@link BigDecimal}</li>
 * <li>xs:double: {@link Double}; xs:float: {@link Float}</li>
 * <li>date/time types including the g* types: {@link XdmDateTimeValue}</li>
 * <li>duration types: {@link XdmDurationValue}</li>
 * <li>xs:hexBinary, xs:base64Binary: <code>byte[]</code> (never modified)</li>
 * <li>xs:QName, xs:NOTATION: {@link XdmQName}</li>
 * </ul>
 * The type is always the most specific type of the value, e.g.
 * {@link EAtomicType#BYTE} for a byte.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmAtomic implements IXdmItem
{
  public static final XdmAtomic TRUE = new XdmAtomic (EAtomicType.BOOLEAN, Boolean.TRUE);
  public static final XdmAtomic FALSE = new XdmAtomic (EAtomicType.BOOLEAN, Boolean.FALSE);

  private final EAtomicType m_eType;
  private final Object m_aValue;

  private XdmAtomic (@NonNull final EAtomicType eType, @NonNull final Object aValue)
  {
    m_eType = eType;
    m_aValue = aValue;
  }

  /**
   * Generic factory. The payload class must match the family of the type, see
   * the class comment. No validation of the value space (range of derived
   * integers, string facets) is done here; that is the job of the casting
   * code.
   *
   * @param eType
   *        The most specific type.
   * @param aValue
   *        The payload.
   * @return The new value.
   */
  @NonNull
  public static XdmAtomic of (@NonNull final EAtomicType eType, @NonNull final Object aValue)
  {
    ValueEnforcer.notNull (eType, "Type");
    ValueEnforcer.notNull (aValue, "Value");
    return new XdmAtomic (eType, aValue);
  }

  @NonNull
  public static XdmAtomic string (@NonNull final String sValue)
  {
    return of (EAtomicType.STRING, sValue);
  }

  @NonNull
  public static XdmAtomic untyped (@NonNull final String sValue)
  {
    return of (EAtomicType.UNTYPED_ATOMIC, sValue);
  }

  @NonNull
  public static XdmAtomic anyUri (@NonNull final String sValue)
  {
    return of (EAtomicType.ANY_URI, sValue);
  }

  @NonNull
  public static XdmAtomic bool (final boolean bValue)
  {
    return bValue ? TRUE : FALSE;
  }

  @NonNull
  public static XdmAtomic integer (final long nValue)
  {
    return of (EAtomicType.INTEGER, BigInteger.valueOf (nValue));
  }

  @NonNull
  public static XdmAtomic integer (@NonNull final BigInteger aValue)
  {
    return of (EAtomicType.INTEGER, aValue);
  }

  @NonNull
  public static XdmAtomic decimal (@NonNull final BigDecimal aValue)
  {
    return of (EAtomicType.DECIMAL, aValue);
  }

  @NonNull
  public static XdmAtomic dbl (final double dValue)
  {
    return of (EAtomicType.DOUBLE, Double.valueOf (dValue));
  }

  @NonNull
  public static XdmAtomic flt (final float fValue)
  {
    return of (EAtomicType.FLOAT, Float.valueOf (fValue));
  }

  @NonNull
  public static XdmAtomic qname (@NonNull final XdmQName aValue)
  {
    return of (EAtomicType.QNAME, aValue);
  }

  @NonNull
  public EAtomicType getType ()
  {
    return m_eType;
  }

  /**
   * @return The payload, see the class comment for its Java class.
   */
  @NonNull
  public Object getValue ()
  {
    return m_aValue;
  }

  public boolean isNumeric ()
  {
    return m_eType.isNumeric ();
  }

  public boolean isStringLike ()
  {
    return m_eType.isStringFamily () || m_eType == EAtomicType.ANY_URI || m_eType == EAtomicType.UNTYPED_ATOMIC;
  }

  /** @return The payload of a string-like value. */
  @NonNull
  public String getString ()
  {
    return (String) m_aValue;
  }

  public boolean getBoolean ()
  {
    return ((Boolean) m_aValue).booleanValue ();
  }

  @NonNull
  public BigInteger getBigInteger ()
  {
    return (BigInteger) m_aValue;
  }

  @NonNull
  public BigDecimal getBigDecimal ()
  {
    return (BigDecimal) m_aValue;
  }

  /**
   * @return The numeric value as double. For integer, decimal and float values
   *         this is the nearest double. Only valid for numeric types.
   */
  public double getAsDouble ()
  {
    if (m_aValue instanceof final Double aDouble)
      return aDouble.doubleValue ();
    if (m_aValue instanceof final Float aFloat)
      return aFloat.doubleValue ();
    if (m_aValue instanceof final BigInteger aBigInt)
      return aBigInt.doubleValue ();
    if (m_aValue instanceof final BigDecimal aBigDec)
      return aBigDec.doubleValue ();
    throw new IllegalStateException ("Not a numeric value: " + m_eType);
  }

  /**
   * @return The canonical lexical representation, as xs:string cast would
   *         produce it.
   */
  @NonNull
  public String getLexical ()
  {
    return AtomicLexical.toLexical (this);
  }

  @Override
  public String toString ()
  {
    return m_eType.getQualifiedName () + "(" + getLexical () + ")";
  }
}
