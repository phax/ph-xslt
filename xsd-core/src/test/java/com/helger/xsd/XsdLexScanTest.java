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

import static org.junit.Assert.assertEquals;

import java.util.Base64;
import java.util.Random;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.ops.XsdLexical;

/**
 * The hand-written scanners (go-xslt <code>lexscan_test.go</code> and
 * <code>lexical_large_test.go</code>) against the regular expressions they
 * replace.
 *
 * @author Philip Helger
 */
public final class XsdLexScanTest
{
  private static final Pattern RE_INTEGER = Pattern.compile ("[+-]?[0-9]+");
  private static final Pattern RE_DECIMAL = Pattern.compile ("[+-]?([0-9]+\\.?[0-9]*|\\.[0-9]+)");
  private static final Pattern RE_HEX = Pattern.compile ("([0-9A-Fa-f]{2})*");
  private static final Pattern RE_BASE64 = Pattern.compile ("((([A-Za-z0-9+/] ?){4})*(([A-Za-z0-9+/] ?){3}[A-Za-z0-9+/]|" +
                                                            "([A-Za-z0-9+/] ?){2}[AEIMQUYcgkosw048] ?=|" +
                                                            "[A-Za-z0-9+/] ?[AQgw] ?= ?=))?");
  private static final Pattern RE_WS_RUN = Pattern.compile ("[ \\t\\r\\n]+");

  private static void _check (@NonNull final String sValue)
  {
    assertEquals ("isIntegerLex '" + sValue + "'", RE_INTEGER.matcher (sValue).matches (), XsdLexScan.isIntegerLex (sValue));
    assertEquals ("isDecimalLex '" + sValue + "'", RE_DECIMAL.matcher (sValue).matches (), XsdLexScan.isDecimalLex (sValue));
    assertEquals ("isNCNameLex '" + sValue + "'", XsdLexical.isNCName (sValue), XsdLexScan.isNCNameLex (sValue));
  }

  private static void _generate (@NonNull final String sPrefix,
                                 final int nLeft,
                                 @NonNull final String [] aAlphabet,
                                 @NonNull final Consumer <String> aCheck)
  {
    aCheck.accept (sPrefix);
    if (nLeft == 0)
      return;
    for (final String sPart : aAlphabet)
      _generate (sPrefix + sPart, nLeft - 1, aAlphabet, aCheck);
  }

  @Test
  public void testIntegerDecimalNCName ()
  {
    final String [] aAlphabet = { "+",
                                  "-",
                                  "0",
                                  "7",
                                  "9",
                                  ".",
                                  "a",
                                  "Z",
                                  "_",
                                  ":",
                                  " ",
                                  "\n",
                                  "e",
                                  "\u00b7",
                                  "\u00e9",
                                  "\u0300",
                                  "\ud835\udc00",
                                  "x1" };
    _generate ("", 4, aAlphabet, XsdLexScanTest::_check);
    final Random aRandom = new Random (1);
    for (int i = 0; i < 200_000; i++)
    {
      final int n = aRandom.nextInt (12);
      final StringBuilder aSB = new StringBuilder ();
      for (int j = 0; j < n; j++)
        aSB.append (aAlphabet[aRandom.nextInt (aAlphabet.length)]);
      _check (aSB.toString ());
    }
  }

  private static String _wrap (final String sValue, final int nWidth)
  {
    final StringBuilder aSB = new StringBuilder ();
    String s = sValue;
    while (s.length () > nWidth)
    {
      aSB.append (s, 0, nWidth).append ("\r\n");
      s = s.substring (nWidth);
    }
    return aSB.append (s).toString ();
  }

  @Test
  public void testHexAndBase64 ()
  {
    final String [] aExamples = { "",
                                  " ",
                                  "  ",
                                  "=",
                                  "==",
                                  "A",
                                  "AA",
                                  "AAA",
                                  "AAAA",
                                  "AAAA ",
                                  " AAAA",
                                  "AAAA  AAAA",
                                  "A A A A",
                                  "A A A A ",
                                  "AQ==",
                                  "AQ= =",
                                  "AQ = =",
                                  "A Q = =",
                                  "AQ== ",
                                  "AB==",
                                  "AAE=",
                                  "AAB=",
                                  "AAE= ",
                                  "AAE =",
                                  "AA=A",
                                  "A===",
                                  "====",
                                  "AAAAAQ==",
                                  "AQ==AAAA",
                                  "AAA=AAAA",
                                  "AAAA\tAAAA",
                                  "AAAA\nAAAA",
                                  "AAA!",
                                  "AAA\u00e9",
                                  "+/+/",
                                  "-_-_",
                                  "AAAAA",
                                  "AAAAAA==",
                                  "AAAAAAA=",
                                  "AQ=",
                                  "00",
                                  "0",
                                  "0g",
                                  "aF",
                                  "a F",
                                  "0123456789abcdefABCDEF",
                                  "abc",
                                  " 00",
                                  "00 " };
    for (final String sValue : aExamples)
    {
      assertEquals ("base64Binary '" + sValue + "'", RE_BASE64.matcher (sValue).matches (), XsdLexScan.isBase64BinaryLex (sValue));
      assertEquals ("hexBinary '" + sValue + "'", RE_HEX.matcher (sValue).matches (), XsdLexScan.isHexBinaryLex (sValue));
    }
    _generate ("", 8, "AEB= \t!".split (""), s -> assertEquals ("base64Binary '" + s + "'",
                                                               RE_BASE64.matcher (s).matches (),
                                                               XsdLexScan.isBase64BinaryLex (s)));
    _generate ("", 7, "0aFg ".split (""), s -> assertEquals ("hexBinary '" + s + "'",
                                                          RE_HEX.matcher (s).matches (),
                                                          XsdLexScan.isHexBinaryLex (s)));
    // Real encodings of every length, plain and wrapped
    final Random aRandom = new Random (2);
    for (int n = 0; n < 200; n++)
    {
      final byte [] aRaw = new byte [n];
      aRandom.nextBytes (aRaw);
      final String sEncoded = Base64.getEncoder ().encodeToString (aRaw);
      for (final String sValue : new String [] { sEncoded, _wrap (sEncoded, 5) })
        for (final String sCandidate : new String [] { sValue, XsdWhiteSpace.collapse (sValue) })
          assertEquals ("base64Binary '" + sCandidate + "'",
                        RE_BASE64.matcher (sCandidate).matches (),
                        XsdLexScan.isBase64BinaryLex (sCandidate));
    }
  }

  @Test
  public void testCollapse ()
  {
    final Consumer <String> aCheck = s -> assertEquals ("collapse '" + s + "'",
                                                        RE_WS_RUN.matcher (s).replaceAll (" ").replaceAll ("^ +| +$", ""),
                                                        XsdWhiteSpace.collapse (s));
    for (final String s : new String [] { "",
                                          " ",
                                          "\t",
                                          "a",
                                          " a",
                                          "a ",
                                          " a ",
                                          "a b",
                                          "a  b",
                                          "a\tb",
                                          "a\r\nb",
                                          "\n\na b\t\t",
                                          "a \t b",
                                          "\u00e9  \u00fc",
                                          "a\u000bb" })
      aCheck.accept (s);
    _generate ("", 7, "ab \t\r\n".split (""), aCheck);
    // XSD whitespace is #x20 #x9 #xD #xA only: other Unicode spaces survive,
    // also at the ends
    assertEquals ("\u00a0x\u00a0", XsdWhiteSpace.apply ("collapse", "\u00a0x\u00a0"));
  }
}
