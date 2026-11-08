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
package com.helger.phoxtrot.xpath.regex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.regex.Pattern;

import org.junit.Test;

import com.helger.phoxtrot.xdm.XmlNames;
import com.helger.phoxtrot.xpath.serialize.XPathTestEval;

/**
 * The regular expression escapes <code>\i</code>, <code>\c</code>,
 * <code>\I</code>, <code>\C</code> and the cast to xs:NCName agree with
 * {@link XmlNames}, the one definition of an XML name (go-xslt
 * <code>name_tables_test.go</code>).
 *
 * @author Philip Helger
 */
public final class NameClassesTest
{
  @Test
  public void testEscapesMatchPredicates ()
  {
    final Pattern aI = XPathRegex.compile ("^\\i$", null).getPattern ();
    final Pattern aC = XPathRegex.compile ("^\\c$", null).getPattern ();
    final Pattern aNotI = XPathRegex.compile ("^\\I$", null).getPattern ();
    final Pattern aNotC = XPathRegex.compile ("^\\C$", null).getPattern ();
    for (int c = 0; c <= 0x10FFFF; ++c)
    {
      if (c >= 0xD800 && c <= 0xDFFF)
        continue;
      final String s = new String (Character.toChars (c));
      final boolean bStart = XmlNames.isNameStartChar (c);
      final boolean bChar = XmlNames.isNameChar (c);
      if (aI.matcher (s).matches () != bStart ||
          aC.matcher (s).matches () != bChar ||
          aNotI.matcher (s).matches () == bStart ||
          aNotC.matcher (s).matches () == bChar)
        fail ("Regex class differs from the predicate at U+" + Integer.toHexString (c));
    }
  }

  @Test
  public void testNCNameCastMatchesPredicate ()
  {
    for (final String s : new String [] { "😀",
                                          "৪",
                                          "a😀",
                                          "𐀀x",
                                          "a",
                                          "1a",
                                          "a￾",
                                          "̀a",
                                          "à",
                                          "a:b",
                                          "",
                                          "-a",
                                          "a-",
                                          "×",
                                          "x·" })
      assertEquals (s,
                    Boolean.toString (XmlNames.isNCName (s)),
                    XPathTestEval.evalString ("'" + s + "' castable as xs:NCName"));
  }
}
