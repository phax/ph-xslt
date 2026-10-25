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

import org.junit.Test;

import com.helger.xpath.context.EXmlVersion;

/**
 * Port of go-xslt's <code>fn_string_spec_test.go</code>: string functions of
 * F&amp;O 3.1 §5.
 *
 * @author Philip Helger
 */
public final class FnStringSpecTest
{
  private static final String HTML_CI = "http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive";

  /** fn:contains-token (F&amp;O 3.1 §5.3.9 examples). */
  @Test
  public void testStrContainsToken ()
  {
    FnCoreTestHelper.coreRun (env (),
                              ok ("contains-token(\"red green blue \", \"red\")", "true"),
                              ok ("contains-token((\"red\", \"green\", \"blue\"), \" red \")", "true"),
                              ok ("contains-token(\"red, green, blue\", \"red\")", "false"),
                              ok ("contains-token(\"red green blue\", \"RED\", \"" + HTML_CI + "\")", "true"),
                              ok ("contains-token(\"red green blue\", \"RED\")", "false"),
                              // go-xslt expects "false"; $collation is declared
                              // xs:string (not xs:string?), so () is a type
                              // error under the function conversion rules.
                              err ("contains-token(\"red green blue\", \"RED\", ())", "XPTY0004"),
                              // Only XML whitespace separates tokens; an
                              // all-blank token never matches.
                              ok ("contains-token(\"a b\", \"a\")", "false"),
                              ok ("contains-token(\"a b\", \"  \")", "false"),
                              ok ("contains-token((), \"a\")", "false"),
                              err ("contains-token(\"a\", \"a\", \"http://example.com/unknown\")", "FOCH0002"));
  }

  /**
   * fn:codepoints-to-string (§5.2.1), the XML Char production check
   * (FOCH0001) and fn:codepoint-equal (§5.3.7).
   */
  @Test
  public void testStrCodepoints ()
  {
    FnCoreTestHelper.coreRun (env (),
                              ok ("codepoints-to-string((66, 65, 67, 72))", "BACH"),
                              ok ("codepoints-to-string((2309, 2358, 2378, 2325))", "अशॊक"),
                              ok ("codepoints-to-string(())", ""),
                              ok ("codepoints-to-string(97)", "a"),
                              ok ("codepoints-to-string(xs:untypedAtomic('98'))", "b"),
                              err ("codepoints-to-string(0)", "FOCH0001"),
                              err ("codepoints-to-string(55296)", "FOCH0001"),
                              err ("codepoints-to-string(1114112)", "FOCH0001"),
                              err ("codepoints-to-string(65534)", "FOCH0001"),
                              ok ("codepoint-equal(\"abcd\", \"abcd\")", "true"),
                              ok ("codepoint-equal(\"abcd\", \"abcd \")", "false"),
                              ok ("codepoint-equal(\"\", \"\")", "true"),
                              ok ("codepoint-equal(\"\", ())", "()"),
                              ok ("codepoint-equal((), ())", "()"),
                              err ("codepoint-equal(1, \"1\")", "XPTY0004"),
                              err ("codepoint-equal(\"1\", 1)", "XPTY0004"));
    // #x8 is a Char in XML 1.1 but not in XML 1.0.
    FnCoreTestHelper.coreRun (env ().staticSetup (x -> x.setXmlVersion (EXmlVersion.V10)),
                              err ("codepoints-to-string(8)", "FOCH0001"),
                              ok ("string-to-codepoints(codepoints-to-string((9, 10, 13, 32)))", "9|10|13|32"));
    FnCoreTestHelper.coreRun (env ().staticSetup (x -> x.setXmlVersion (EXmlVersion.V11)),
                              ok ("string-to-codepoints(codepoints-to-string(8))", "8"));
  }

  /**
   * fn:compare (§5.3.6) under the codepoint collation, an explicit collation
   * and the in-scope default collation.
   */
  @Test
  public void testStrCompare ()
  {
    FnCoreTestHelper.coreRun (env (),
                              ok ("compare('abc', 'abc')", "0"),
                              ok ("compare('abc', 'abd')", "-1"),
                              ok ("compare('b', 'a')", "1"),
                              // Codepoint order: 's' (U+0073) precedes 'ß'
                              // (U+00DF).
                              ok ("compare('Strasse', 'Straße')", "-1"),
                              ok ("compare('a', 'b', 'http://www.w3.org/2005/xpath-functions/collation/codepoint')", "-1"),
                              ok ("compare('ABC', 'abc', '" + HTML_CI + "')", "0"),
                              ok ("compare('Strasse', 'Straße', 'http://www.w3.org/2013/collation/UCA?lang=de;strength=primary')",
                                  "0"),
                              ok ("compare((), 'a')", "()"),
                              ok ("compare('a', ())", "()"),
                              err ("compare(1, 'a')", "XPTY0004"),
                              err ("compare('a', 1)", "XPTY0004"),
                              err ("compare('a', 'b', 'http://example.com/unknown')", "FOCH0002"));
    FnCoreTestHelper.coreRun (env ().staticSetup (x -> x.setDefaultCollation (HTML_CI)),
                              ok ("compare('ABC', 'abc')", "0"));
  }

  /**
   * fn:normalize-unicode (§5.4.6): the effective form is
   * upper-case(normalize-space($form)), "" means no normalisation.
   */
  @Test
  public void testStrNormalizeUnicode ()
  {
    FnCoreTestHelper.coreRun (env (),
                              ok ("string-to-codepoints(normalize-unicode(codepoints-to-string((101, 769))))", "233"),
                              ok ("string-to-codepoints(normalize-unicode(codepoints-to-string((101, 769)), 'NFC'))", "233"),
                              ok ("string-to-codepoints(normalize-unicode(codepoints-to-string(233), 'NFD'))", "101|769"),
                              ok ("normalize-unicode(codepoints-to-string(64257), 'NFKC')", "fi"),
                              ok ("normalize-unicode(codepoints-to-string(64257), ' nfkd ')", "fi"),
                              ok ("string-to-codepoints(normalize-unicode(codepoints-to-string((101, 769)), ''))", "101|769"),
                              // FULLY-NORMALIZED: a leading composing character
                              // gets a space prefix.
                              ok ("string-to-codepoints(normalize-unicode(codepoints-to-string(769), 'FULLY-NORMALIZED'))",
                                  "32|769"),
                              ok ("normalize-unicode('abc', 'fully-normalized')", "abc"),
                              ok ("normalize-unicode((), 'NFC')", ""),
                              err ("normalize-unicode('a', 'NFX')", "FOCH0003"),
                              err ("normalize-unicode(12)", "XPTY0004"),
                              err ("normalize-unicode('a', ())", "XPTY0004"));
  }

  /**
   * fn:collation-key (§5.3.4): two keys are equal exactly when the strings
   * are equal under the collation.
   */
  @Test
  public void testStrCollationKey ()
  {
    FnCoreTestHelper.coreRun (env (),
                              ok ("collation-key(\"abc\") eq collation-key(\"abc\")", "true"),
                              ok ("collation-key(\"abc\") eq collation-key(\"abd\")", "false"),
                              ok ("collation-key(\"abc\") instance of xs:base64Binary", "true"),
                              ok ("collation-key(\"ABC\", \"" +
                                  HTML_CI +
                                  "\") eq collation-key(\"abc\", \"" +
                                  HTML_CI +
                                  "\")",
                                  "true"),
                              ok ("collation-key(\"ABC\", \"" +
                                  HTML_CI +
                                  "\") eq collation-key(\"abd\", \"" +
                                  HTML_CI +
                                  "\")",
                                  "false"),
                              ok ("collation-key(xs:untypedAtomic(\"a\")) eq collation-key(\"a\")", "true"),
                              err ("collation-key(())", "XPTY0004"),
                              err ("collation-key((\"a\", \"b\"))", "XPTY0004"),
                              err ("collation-key(1)", "XPTY0004"),
                              err ("collation-key(\"a\", \"http://example.com/unknown\")", "FOCH0002"));
  }

  /**
   * fn:generate-id (§14.5.4): a stable identifier per node, distinct across
   * nodes, consisting of ASCII alphanumerics and starting with a letter; ()
   * gives "".
   */
  @Test
  public void testStrGenerateID ()
  {
    FnCoreTestHelper.coreRun (env ("<r xmlns:p=\"urn:p\"><a/><a/></r>"),
                              ok ("generate-id(/r) eq generate-id(/r)", "true"),
                              ok ("generate-id(/r/a[1]) ne generate-id(/r/a[2])", "true"),
                              ok ("generate-id(/r) ne generate-id(/)", "true"),
                              ok ("/r/a[1]/generate-id() eq generate-id(/r/a[1])", "true"),
                              ok ("matches(generate-id(/r/a[2]), '^[A-Za-z][A-Za-z0-9]*$')", "true"),
                              ok ("count(distinct-values(/r/a/namespace::*!generate-id(.)))", "4"),
                              ok ("generate-id(())", ""),
                              err ("generate-id(1)", "XPTY0004"),
                              err ("generate-id(/r/a)", "XPTY0004"));
    FnCoreTestHelper.coreRun (env ().noContext (), err ("generate-id()", "XPDY0002"));
  }
}
