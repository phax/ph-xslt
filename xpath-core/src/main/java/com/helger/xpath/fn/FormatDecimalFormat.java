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
package com.helger.xpath.fn;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;

/**
 * The properties of a decimal format (XSLT 3.0 <code>xsl:decimal-format</code>,
 * F&amp;O 3.1 section 4.7.1) used by <code>fn:format-number</code>. The
 * character properties are Unicode code points. Instances are immutable; use
 * {@link #builder()} to create one, e.g. for a named XSLT decimal format, and
 * register it in the static context.
 *
 * @author Philip Helger
 */
@Immutable
public final class FormatDecimalFormat
{
  /** The default (unnamed) decimal format. */
  public static final FormatDecimalFormat DEFAULT = builder ().build ();

  private final int m_nDecimalSeparator;
  private final int m_nGroupingSeparator;
  private final int m_nExponentSeparator;
  private final int m_nPercent;
  private final int m_nPerMille;
  private final int m_nZeroDigit;
  private final int m_nDigit;
  private final int m_nPatternSeparator;
  private final int m_nMinusSign;
  private final String m_sInfinity;
  private final String m_sNaN;

  /**
   * Mutable builder, starting with the default values.
   */
  @NotThreadSafe
  public static final class Builder
  {
    private int m_nDecimalSeparator = '.';
    private int m_nGroupingSeparator = ',';
    private int m_nExponentSeparator = 'e';
    private int m_nPercent = '%';
    private int m_nPerMille = 0x2030;
    private int m_nZeroDigit = '0';
    private int m_nDigit = '#';
    private int m_nPatternSeparator = ';';
    private int m_nMinusSign = '-';
    private String m_sInfinity = "Infinity";
    private String m_sNaN = "NaN";

    Builder ()
    {}

    Builder (@NonNull final FormatDecimalFormat aSrc)
    {
      m_nDecimalSeparator = aSrc.m_nDecimalSeparator;
      m_nGroupingSeparator = aSrc.m_nGroupingSeparator;
      m_nExponentSeparator = aSrc.m_nExponentSeparator;
      m_nPercent = aSrc.m_nPercent;
      m_nPerMille = aSrc.m_nPerMille;
      m_nZeroDigit = aSrc.m_nZeroDigit;
      m_nDigit = aSrc.m_nDigit;
      m_nPatternSeparator = aSrc.m_nPatternSeparator;
      m_nMinusSign = aSrc.m_nMinusSign;
      m_sInfinity = aSrc.m_sInfinity;
      m_sNaN = aSrc.m_sNaN;
    }

    @NonNull
    public Builder decimalSeparator (final int n)
    {
      m_nDecimalSeparator = n;
      return this;
    }

    @NonNull
    public Builder groupingSeparator (final int n)
    {
      m_nGroupingSeparator = n;
      return this;
    }

    @NonNull
    public Builder exponentSeparator (final int n)
    {
      m_nExponentSeparator = n;
      return this;
    }

    @NonNull
    public Builder percent (final int n)
    {
      m_nPercent = n;
      return this;
    }

    @NonNull
    public Builder perMille (final int n)
    {
      m_nPerMille = n;
      return this;
    }

    /**
     * @param n
     *        A Unicode digit with numeric value zero.
     * @return this for chaining
     */
    @NonNull
    public Builder zeroDigit (final int n)
    {
      m_nZeroDigit = n;
      return this;
    }

    @NonNull
    public Builder digit (final int n)
    {
      m_nDigit = n;
      return this;
    }

    @NonNull
    public Builder patternSeparator (final int n)
    {
      m_nPatternSeparator = n;
      return this;
    }

    @NonNull
    public Builder minusSign (final int n)
    {
      m_nMinusSign = n;
      return this;
    }

    @NonNull
    public Builder infinity (@NonNull final String s)
    {
      m_sInfinity = ValueEnforcer.notNull (s, "Infinity");
      return this;
    }

    @NonNull
    public Builder nan (@NonNull final String s)
    {
      m_sNaN = ValueEnforcer.notNull (s, "NaN");
      return this;
    }

    @NonNull
    public FormatDecimalFormat build ()
    {
      return new FormatDecimalFormat (this);
    }
  }

  private FormatDecimalFormat (@NonNull final Builder aBuilder)
  {
    m_nDecimalSeparator = aBuilder.m_nDecimalSeparator;
    m_nGroupingSeparator = aBuilder.m_nGroupingSeparator;
    m_nExponentSeparator = aBuilder.m_nExponentSeparator;
    m_nPercent = aBuilder.m_nPercent;
    m_nPerMille = aBuilder.m_nPerMille;
    m_nZeroDigit = aBuilder.m_nZeroDigit;
    m_nDigit = aBuilder.m_nDigit;
    m_nPatternSeparator = aBuilder.m_nPatternSeparator;
    m_nMinusSign = aBuilder.m_nMinusSign;
    m_sInfinity = aBuilder.m_sInfinity;
    m_sNaN = aBuilder.m_sNaN;
  }

  /** @return A builder with the default values. */
  @NonNull
  public static Builder builder ()
  {
    return new Builder ();
  }

  /** @return A builder initialised with the values of this format. */
  @NonNull
  public Builder toBuilder ()
  {
    return new Builder (this);
  }

  public int getDecimalSeparator ()
  {
    return m_nDecimalSeparator;
  }

  public int getGroupingSeparator ()
  {
    return m_nGroupingSeparator;
  }

  public int getExponentSeparator ()
  {
    return m_nExponentSeparator;
  }

  public int getPercent ()
  {
    return m_nPercent;
  }

  public int getPerMille ()
  {
    return m_nPerMille;
  }

  public int getZeroDigit ()
  {
    return m_nZeroDigit;
  }

  public int getDigit ()
  {
    return m_nDigit;
  }

  public int getPatternSeparator ()
  {
    return m_nPatternSeparator;
  }

  public int getMinusSign ()
  {
    return m_nMinusSign;
  }

  @NonNull
  public String getInfinity ()
  {
    return m_sInfinity;
  }

  @NonNull
  public String getNaN ()
  {
    return m_sNaN;
  }

  /**
   * @param c
   *        A code point.
   * @return <code>true</code> if it is one of the ten digits of the zero-digit
   *         family.
   */
  public boolean isDecimalDigit (final int c)
  {
    return c >= m_nZeroDigit && c <= m_nZeroDigit + 9;
  }
}
