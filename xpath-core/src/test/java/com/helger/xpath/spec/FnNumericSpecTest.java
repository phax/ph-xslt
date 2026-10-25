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
package com.helger.xpath.spec;

import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

import com.helger.xpath.context.StaticContext;
import com.helger.xpath.fn.FormatDecimalFormat;
import com.helger.xpath.xdm.XdmQName;

/**
 * Port of go-xslt's <code>fn_numeric_spec_test.go</code>. Expected values come
 * from F&amp;O 3.1 §4.4 (abs, ceiling, floor, round, round-half-to-even),
 * §4.6.1 (format-integer), §4.7 (format-number) and §4.8.14 (math:atan2) -
 * mostly the REC's own examples, otherwise derived directly from the numbered
 * rules of those sections. <code>TestNumFormatNumberPic</code> is not ported:
 * it tests go-xslt's internal <code>formatNumberPic</code> entry point.
 *
 * @author Philip Helger
 */
public final class FnNumericSpecTest
{
  private static void _setupNamedFormats (final StaticContext aSC)
  {
    final FormatDecimalFormat aCh = FormatDecimalFormat.builder ().groupingSeparator (0x02B9).decimalSeparator (0x00B7).build ();
    final FormatDecimalFormat aFortran = FormatDecimalFormat.builder ().exponentSeparator ('E').build ();
    final FormatDecimalFormat aAr = FormatDecimalFormat.builder ().zeroDigit (0x0660).decimalSeparator (0x066B).build ();
    final FormatDecimalFormat aSymbols = FormatDecimalFormat.builder ()
                                                            .nan ("nan")
                                                            .infinity ("\u221E")
                                                            .minusSign ('~')
                                                            .patternSeparator ('|')
                                                            .digit ('x')
                                                            .percent ('p')
                                                            .build ();
    // go-xslt: a format whose exponent separator field is unset (Go zero
    // value) means 'e'; here that is simply the builder default
    final FormatDecimalFormat aLegacy = FormatDecimalFormat.builder ().build ();
    aSC.addDecimalFormat (new XdmQName ("ch"), aCh);
    aSC.addDecimalFormat (new XdmQName ("fortran"), aFortran);
    aSC.addDecimalFormat (new XdmQName ("ar"), aAr);
    aSC.addDecimalFormat (new XdmQName ("sym"), aSymbols);
    aSC.addDecimalFormat (new XdmQName ("legacy"), aLegacy);
    aSC.addDecimalFormat (new XdmQName ("http://example.com/d", "f"), aFortran);
    aSC.addDecimalFormat (new XdmQName ("http://www.w3.org/2005/xpath-functions", "fn"), aCh);
    aSC.declareNamespace ("d", "http://example.com/d");
  }

  /**
   * fn:abs, fn:ceiling, fn:floor and fn:round (§4.4.1-§4.4.4).
   */
  @Test
  public void testNumRoundingFamilySpec ()
  {
    run (// §4.4.1 fn:abs examples + type preservation.
        ok ("abs(10.5)", "10.5"),
        ok ("abs(-10.5)", "10.5"),
        ok ("abs(-3)", "3"),
        ok ("abs(-3) instance of xs:integer", "true"),
        ok ("abs(-10.5) instance of xs:decimal", "true"),
        ok ("abs(xs:float('-1.5'))", "1.5"),
        ok ("abs(xs:float('-1.5')) instance of xs:float", "true"),
        ok ("abs(-2.5e0)", "2.5"),
        ok ("abs(-2.5e0) instance of xs:double", "true"),
        ok ("empty(abs(()))", "true"),
        // §4.4.2 fn:ceiling examples; -1 < x < 0 gives negative zero.
        ok ("ceiling(10.5)", "11"),
        ok ("ceiling(-10.5)", "-10"),
        ok ("ceiling(7)", "7"),
        ok ("ceiling(xs:float('1.2'))", "2"),
        ok ("ceiling(xs:float('1.2')) instance of xs:float", "true"),
        ok ("ceiling(1.2e0)", "2"),
        ok ("ceiling(-0.5e0)", "-0"),
        ok ("empty(ceiling(()))", "true"),
        // §4.4.3 fn:floor examples.
        ok ("floor(10.5)", "10"),
        ok ("floor(-10.5)", "-11"),
        ok ("floor(7)", "7"),
        ok ("floor(xs:float('-1.2'))", "-2"),
        ok ("floor(xs:float('-1.2')) instance of xs:float", "true"),
        ok ("floor(-1.2e0)", "-2"),
        ok ("floor(-0e0)", "-0"),
        ok ("empty(floor(()))", "true"),
        // §4.4.4 fn:round examples.
        ok ("round(2.5)", "3"),
        ok ("round(2.4999)", "2"),
        ok ("round(-2.5)", "-2"),
        ok ("round(1.125, 2)", "1.13"),
        ok ("round(8452, -2)", "8500"),
        ok ("round(8452, -2) instance of xs:integer", "true"),
        ok ("round(8452, 2)", "8452"),
        ok ("round(3.1415e0, 2)", "3.14"),
        ok ("round(3.1415e0, 2) instance of xs:double", "true"),
        ok ("round(xs:float('2.5'))", "3"),
        ok ("round(xs:float('2.5')) instance of xs:float", "true"),
        // A zero result keeps the sign of the argument (§4.4.4 rules).
        ok ("round(-0.4e0)", "-0"),
        ok ("round(xs:double('NaN'))", "NaN"),
        ok ("round(xs:double('-INF'))", "-INF"),
        // Huge precisions: more places than the value has leaves it unchanged,
        // a power of ten above any value gives zero.
        ok ("round(1.5, 5000)", "1.5"),
        ok ("round(12345, -5000)", "0"),
        ok ("round(1.5e0, 400)", "1.5"),
        ok ("round(1.5e300, -400)", "0"),
        ok ("empty(round(()))", "true"),
        // xs:untypedAtomic is cast to xs:double (§4.4 general rules).
        ok ("round(xs:untypedAtomic('2.5'))", "3"),
        ok ("round(xs:untypedAtomic('2.5')) instance of xs:double", "true"),
        err ("round('2.5')", "XPTY0004"),
        err ("abs('x')", "XPTY0004"),
        err ("floor(true())", "XPTY0004"),
        err ("ceiling(xs:untypedAtomic('abc'))", "FORG0001"));
  }

  /**
   * An untyped node value is cast to xs:double (§4.4).
   */
  @Test
  public void testNumRoundingUntypedNode ()
  {
    run (env ("<r>2.5</r>"), ok ("round(/r)", "3"));
    run (env ("<r>-4</r>"), ok ("abs(/r) instance of xs:double", "true"));
  }

  /**
   * fn:round-half-to-even (§4.4.5).
   */
  @Test
  public void testNumRoundHalfToEvenSpec ()
  {
    run (// §4.4.5 examples.
        ok ("round-half-to-even(0.5)", "0"),
        ok ("round-half-to-even(1.5)", "2"),
        ok ("round-half-to-even(2.5)", "2"),
        ok ("round-half-to-even(3.567812e+3, 2)", "3567.81"),
        ok ("round-half-to-even(4.7564e-3, 2)", "0"),
        ok ("round-half-to-even(35612.25, -2)", "35600"),
        // Ties to even on both signs, integers at negative precision.
        ok ("round-half-to-even(-2.5)", "-2"),
        ok ("round-half-to-even(-3.5)", "-4"),
        ok ("round-half-to-even(-3.6)", "-4"),
        ok ("round-half-to-even(1350, -2)", "1400"),
        ok ("round-half-to-even(-1250, -2)", "-1200"),
        ok ("round-half-to-even(1251, -2)", "1300"),
        ok ("round-half-to-even(1250, -2) instance of xs:integer", "true"),
        // xs:float keeps its type; 1.25 is exact in binary, so it is a tie.
        ok ("round-half-to-even(xs:float('1.25'), 1)", "1.2"),
        ok ("round-half-to-even(xs:float('1.25'), 1) instance of xs:float", "true"),
        ok ("round-half-to-even(xs:float('NaN'))", "NaN"),
        ok ("round-half-to-even(xs:float('INF'))", "INF"),
        ok ("round-half-to-even(xs:double('INF'))", "INF"),
        ok ("round-half-to-even(xs:double('NaN'))", "NaN"),
        // The double nearest 2.675 is below it: no tie (cast to exact decimal).
        ok ("round-half-to-even(2.675e0, 2)", "2.67"),
        ok ("round-half-to-even(-0.5e0)", "-0"),
        // Huge precisions.
        ok ("round-half-to-even(1.5, 5000)", "1.5"),
        ok ("round-half-to-even(12345, -5000)", "0"),
        ok ("round-half-to-even(1.5e0, 400)", "1.5"),
        ok ("round-half-to-even(1.5e0, -400)", "0"),
        ok ("empty(round-half-to-even(()))", "true"),
        ok ("round-half-to-even(xs:untypedAtomic('2.5'))", "2"),
        err ("round-half-to-even('1')", "XPTY0004"),
        err ("round-half-to-even(1.5, 'two')", "XPTY0004"));
  }

  /**
   * math:atan2 (§4.8.14 examples).
   */
  @Test
  public void testNumAtan2Spec ()
  {
    run (ok ("math:atan2(+0.0e0, 0.0e0)", "0"),
        ok ("math:atan2(-0.0e0, 0.0e0)", "-0"),
        ok ("math:atan2(+0.0e0, -0.0e0)", "3.141592653589793"),
        ok ("math:atan2(-0.0e0, -0.0e0)", "-3.141592653589793"),
        ok ("math:atan2(-1, 0.0e0)", "-1.5707963267948966"),
        ok ("math:atan2(+1, 0.0e0)", "1.5707963267948966"),
        ok ("math:atan2(-0.0e0, -1)", "-3.141592653589793"),
        ok ("math:atan2(+0.0e0, -1)", "3.141592653589793"),
        ok ("math:atan2(-0.0e0, +1)", "-0"),
        ok ("math:atan2(+0.0e0, +1)", "0"),
        ok ("math:atan2(xs:double('NaN'), 1)", "NaN"),
        // go-xslt expects true; the REC declares $y as xs:double (not xs:double?)
         err ("empty(math:atan2((), 1))", "XPTY0004"));
  }

  /**
   * fn:format-integer (§4.6.1).
   */
  @Test
  public void testNumFormatIntegerSpec ()
  {
    run (// §4.6.1 examples.
        ok ("format-integer(123, '0000')", "0123"),
        ok ("format-integer(123, 'w')", "one hundred and twenty-three"),
        ok ("format-integer(21, '1;o', 'en')", "21st"),
        ok ("format-integer(7, 'a')", "g"),
        ok ("format-integer(57, 'I')", "LVII"),
        ok ("format-integer(1234, '#;##0;')", "1;234"),
        // Empty value → zero-length string; negatives get a leading minus.
        ok ("format-integer((), '1')", ""),
        ok ("format-integer(-57, 'I')", "-LVII"),
        ok ("format-integer(-5, '000')", "-005"),
        // Notes of §4.6.1: regular grouping extrapolates leftward.
        ok ("format-integer(1000000, '0''000')", "1'000'000"),
        ok ("format-integer(15, '0''000')", "0'015"),
        ok ("format-integer(1000000, '#''##0')", "1'000'000"),
        ok ("format-integer(15, '#''##0')", "15"),
        ok ("format-integer(300, '01')", "300"),
        ok ("format-integer(3, '99')", "03"),
        // Not regular (positions 3 and 5): only the explicit positions.
        ok ("format-integer(1234567, '#,##,##0')", "12,34,567"),
        // Not regular (multiple 6 of G=2 missing): positional.
        ok ("format-integer(1234567, '###,##,00')", "123,45,67"),
        // Not regular (different separator characters).
        ok ("format-integer(1234567, '0.000,000')", "1.234,567"),
        // Positions 2 and 6 are multiples of 2, but 4 is missing: positional.
        ok ("format-integer(12345678, '00,0000,00')", "12,3456,78"),
        // Regular with G=2 repeating.
        ok ("format-integer(1234567, '00,00,00')", "1,23,45,67"),
        ok ("format-integer(12345678901234567890, '#,##0')", "12,345,678,901,234,567,890"),
        // Other digit families (§4.6.1: Arabic-Indic one; Thai).
        ok ("format-integer(3, '١')", "٣"),
        ok ("format-integer(123, '๐๐๐๐')", "๐๑๒๓"),
        // Ordinals with digits (English: 1st 2nd 3rd 4th …).
        ok ("format-integer(2, '1;o')", "2nd"),
        ok ("format-integer(3, '1;o')", "3rd"),
        ok ("format-integer(4, '1;o')", "4th"),
        ok ("format-integer(12, '1;o')", "12th"),
        ok ("format-integer(113, '1;o')", "113th"),
        ok ("format-integer(22, '1;o(-e)')", "22nd"),
        ok ("format-integer(5, '1;c')", "5"),
        ok ("format-integer(5, '1;c(x)a')", "5"),
        ok ("format-integer(5, '1;t')", "5"),
        // A/a and I/i sequences.
        ok ("format-integer(26, 'A')", "Z"),
        ok ("format-integer(27, 'A')", "AA"),
        ok ("format-integer(28, 'a')", "ab"),
        ok ("format-integer(1999, 'i')", "mcmxcix"),
        ok ("format-integer(4, 'I')", "IV"),
        // 0 has no Roman/alphabetic form: fallback to token 1.
        ok ("format-integer(0, 'I')", "0"),
        ok ("format-integer(0, 'a')", "0"),
        // Words (English).
        ok ("format-integer(7, 'W', 'en')", "SEVEN"),
        ok ("format-integer(7, 'Ww', 'en')", "Seven"),
        ok ("format-integer(2, 'Ww;o', 'en')", "Second"),
        ok ("format-integer(0, 'w', 'en')", "zero"),
        ok ("format-integer(-3, 'w', 'en')", "-three"),
        ok ("format-integer(1, 'w;o', 'en')", "first"),
        ok ("format-integer(21, 'w;o', 'en')", "twenty-first"),
        ok ("format-integer(20, 'w;o', 'en')", "twentieth"),
        ok ("format-integer(100, 'w;o', 'en')", "one hundredth"),
        ok ("format-integer(1000000, 'w', 'en')", "one million"),
        ok ("format-integer(2000000000, 'w', 'en')", "two billion"),
        ok ("format-integer(1001, 'w', 'en')", "one thousand and one"),
        ok ("format-integer(1001, 'w;o', 'en')", "one thousand and first"),
        ok ("format-integer(1200, 'w', 'en')", "one thousand two hundred"),
        ok ("format-integer(1200, 'w;o', 'en')", "one thousand two hundredth"),
        // Greek alphabetic sequence and Kanji numerals.
        ok ("format-integer(1, 'α')", "α"),
        ok ("format-integer(24, 'Α')", "Ω"),
        ok ("format-integer(25, 'α')", "αα"),
        ok ("format-integer(151, '一')", "百五十一"),
        ok ("format-integer(2025, '一')", "二千二十五"),
        ok ("format-integer(10, '一')", "十"),
        // Circled digits.
        ok ("format-integer(3, '①')", "③"),
        ok ("format-integer(3, '⑴')", "⑶"),
        ok ("format-integer(3, '⒈')", "⒊"),
        // Unsupported tokens fall back to the token '1'.
        ok ("format-integer(1500000, '#a')", "1500000"),
        ok ("format-integer(5, '#')", "5"),
        ok ("format-integer(5, 'x;o')", "5th"),
        err ("format-integer(1, '')", "FODF1310"),
        err ("format-integer(1, ';o')", "FODF1310"),
        err ("format-integer(1, '0#')", "FODF1310"),
        err ("format-integer(1, ',00')", "FODF1310"),
        err ("format-integer(1, '00,')", "FODF1310"),
        err ("format-integer(1, '0,,0')", "FODF1310"),
        err ("format-integer(1, '0١')", "FODF1310"),
        err ("format-integer(1, '0a0')", "FODF1310"),
        err ("format-integer(1, '1;o(')", "FODF1310"),
        err ("format-integer(1, '1;oz')", "FODF1310"),
        err ("format-integer(1, '1;at')", "FODF1310"),
        err ("format-integer(1, '1;o(-er)z')", "FODF1310"));
  }

  /**
   * The two-argument form depends on the default language (§4.6.1 properties).
   */
  @Test
  public void testNumFormatIntegerDefaultLanguage ()
  {
    run (env ().dynamicSetup (x -> {
      x.setDefaultLanguage ("en");
      return x;
    }),
        ok ("format-integer(3, 'w')", "three"));
  }

  /**
   * fn:format-number (§4.7) with the default decimal format.
   */
  @Test
  public void testNumFormatNumberSpec ()
  {
    run (// §4.7.2 examples (default decimal format).
        ok ("format-number(12345.6, '#,###.00')", "12,345.60"),
        ok ("format-number(12345678.9, '9,999.99')", "12,345,678.90"),
        ok ("format-number(123.9, '9999')", "0124"),
        ok ("format-number(0.14, '01%')", "14%"),
        ok ("format-number(-6, '000')", "-006"),
        // The four exponent examples, with the default exponent separator 'e'.
        ok ("format-number(1234.5678, '00.000e0')", "12.346e2"),
        ok ("format-number(0.234, '0.0e0')", "2.3e-1"),
        ok ("format-number(0.234, '#.00e0')", "0.23e0"),
        ok ("format-number(0.234, '.00e0')", ".23e0"),
        // §4.7.4 notes: '#.e9', '.9e9', '#.9e9', '#'.
        ok ("format-number(0.123, '#.e9')", "0.1e0"),
        ok ("format-number(0.1, '.9e9')", ".1e0"),
        ok ("format-number(0.1, '#.9e9')", "0.1e0"),
        ok ("format-number(0.23, '#')", "0"),
        // Minimum exponent size pads the exponent.
        ok ("format-number(0.234, '0.0e00')", "2.3e-01"),
        ok ("format-number(5, '0e00')", "5e00"),
        // Per-mille and percent (§4.7.5 adjusted number).
        ok ("format-number(0.5, '#‰')", "500‰"),
        ok ("format-number(0.4857, '###.###%')", "48.57%"),
        ok ("format-number(0.1e0, '0%')", "10%"),
        // Overflow on scaling gives infinity, no error.
        ok ("format-number(1.7e308, '0%')", "Infinity%"),
        // Sub-pictures: negative uses the second, a sole one gets minus + prefix.
        ok ("format-number(-6, '0;(0)')", "(6)"),
        ok ("format-number(0, '0;(0)')", "0"),
        ok ("format-number(-0e0, '0;(0)')", "(0)"),
        ok ("format-number(-5, '$0')", "-$5"),
        // NaN / infinity / empty.
        ok ("format-number(xs:double('NaN'), '0;(0)')", "NaN"),
        ok ("format-number((), '0')", "NaN"),
        ok ("format-number(xs:double('INF'), '0')", "Infinity"),
        ok ("format-number(xs:double('-INF'), '0')", "-Infinity"),
        ok ("format-number(xs:double('-INF'), '0;(0)')", "(Infinity)"),
        // Shortest decimal representation of doubles / floats.
        ok ("format-number(1e25, '#')", "10000000000000000000000000"),
        ok ("format-number(xs:float('0.1'), '0.000')", "0.100"),
        ok ("format-number(12345678901234567890, '#,###')", "12,345,678,901,234,567,890"),
        // Grouping: regular, irregular, non-regular, fractional.
        ok ("format-number(1234567, '#,###')", "1,234,567"),
        ok ("format-number(1234567, '#,##,##0')", "12,34,567"),
        ok ("format-number(1234567, '####,##')", "12345,67"),
        ok ("format-number(1234567, ',##0')", "1,234,567"),
        ok ("format-number(12345.6789, '#.#,##,#')", "12345.6,78,9"),
        // Decimal separator removed when no fractional digit follows; zero.
        ok ("format-number(5, '0.#')", "5"),
        ok ("format-number(0, '#.#')", ".0"),
        ok ("format-number(0.2, '#.#')", ".2"),
        // Rounding is round-half-to-even on the exact decimal.
        ok ("format-number(0.125, '0.00')", "0.12"),
        ok ("format-number(0.135, '0.00')", "0.14"),
        // Fewer digits than one group; leading zeros inside the fraction.
        ok ("format-number(12, '#,###')", "12"),
        ok ("format-number(1.05, '0.00')", "1.05"),
        // An empty or absent $decimal-format-name means the unnamed format.
        ok ("format-number(1.5, '0.0', '')", "1.5"),
        ok ("format-number(1.5, '0.0', ())", "1.5"),
        // Passive prefix and suffix.
        ok ("format-number(42, 'n=0 units')", "n=42 units"));
    run (env ("<r>2.25</r>"), ok ("format-number(/r, '0.0')", "2.2"));
    run (// §4.7.3 FODF1310 rules, one per rule.
        err ("format-number(1, '0;0;0')", "FODF1310"),
        err ("format-number(1, '#.#.#')", "FODF1310"),
        err ("format-number(1, 'abc')", "FODF1310"),
        err ("format-number(1, '#%‰')", "FODF1310"),
        err ("format-number(1, '#%%')", "FODF1310"),
        err ("format-number(1, '#‰‰')", "FODF1310"),
        err ("format-number(1, '0$0')", "FODF1310"),
        err ("format-number(1, '#,.0')", "FODF1310"),
        err ("format-number(1, '#.,0')", "FODF1310"),
        err ("format-number(1, '#,')", "FODF1310"),
        err ("format-number(1, '#,,#')", "FODF1310"),
        err ("format-number(1, '0#')", "FODF1310"),
        err ("format-number(1, '#.#0')", "FODF1310"),
        err ("format-number(1, '0e0e0')", "FODF1310"),
        err ("format-number(1, '0e0%')", "FODF1310"),
        err ("format-number(1, '0e#')", "FODF1310"),
        err ("format-number(1, '0e0#')", "FODF1310"),
        err ("format-number(1, '.e0')", "FODF1310"),
        // 'E' is not the default exponent separator: a passive char inside.
        err ("format-number(1, '0E0')", "FODF1310"),
        // $picture is xs:string.
        err ("format-number(1, 2)", "XPTY0004"),
        // No decimal format of that name in the static context.
        err ("format-number(1, '0', 'nope')", "FODF1280"));
  }

  /**
   * fn:format-number with named decimal formats (§4.7.2 examples, the EQName
   * rules for $decimal-format-name).
   */
  @Test
  public void testNumFormatNumberNamedFormats ()
  {
    run (env ().staticSetup (FnNumericSpecTest::_setupNamedFormats),
        // §4.7.2 examples with named formats.
        ok ("format-number(1234.5678, '#ʹ##0·00', 'ch')", "1ʹ234·57"),
        ok ("format-number(1234.5678, '00.000E0', 'fortran')", "12.346E2"),
        ok ("format-number(0.234, '0.0E0', 'fortran')", "2.3E-1"),
        ok ("format-number(0.234, '#.00E0', 'fortran')", "0.23E0"),
        ok ("format-number(0.234, '.00E0', 'fortran')", ".23E0"),
        // The name is an EQName: whitespace stripped, prefixes from the
        // static context, Q{uri}local.
        ok ("format-number(1234.5678, '#ʹ##0·00', ' ch ')", "1ʹ234·57"),
        ok ("format-number(0.234, '0.0E0', 'd:f')", "2.3E-1"),
        ok ("format-number(0.234, '0.0E0', 'Q{http://example.com/d}f')", "2.3E-1"),
        ok ("format-number(1234.5678, '#ʹ##0·00', 'Q{}ch')", "1ʹ234·57"),
        ok ("format-number(1234.5678, '#ʹ##0·00', 'fn:fn')", "1ʹ234·57"),
        // Digits of the zero-digit family (Arabic-Indic), also in exponents.
        ok ("format-number(1234.5, '٠٫٠', 'ar')", "١٢٣٤٫٥"),
        ok ("format-number(0.234, '٠٫٠e٠', 'ar')", "٢٫٣e-١"),
        // Other symbols: NaN, infinity, minus-sign, pattern-separator, digit,
        // percent.
        ok ("format-number(number('x'), '0', 'sym')", "nan"),
        ok ("format-number(xs:double('-INF'), '0', 'sym')", "~∞"),
        ok ("format-number(-5, 'x0', 'sym')", "~5"),
        ok ("format-number(-5, '0|[0]', 'sym')", "[5]"),
        ok ("format-number(0.25, '0p', 'sym')", "25p"),
        // An unset exponent separator still means 'e'.
        ok ("format-number(0.234, '0.0e0', 'legacy')", "2.3e-1"),
        // With no name the unnamed format is used.
        ok ("format-number(1.5, '0.0')", "1.5"));
    run (env ().staticSetup (FnNumericSpecTest::_setupNamedFormats),
        err ("format-number(1, '0', 'unknown')", "FODF1280"),
        err ("format-number(1, '0;0;0', 'ch')", "FODF1310"),
        err ("format-number(1, '0|0|0', 'sym')", "FODF1310"));
  }
}
