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
package com.helger.xslt.spec;

import static com.helger.xslt.spec.XsltSpecTestHelper.err;
import static com.helger.xslt.spec.XsltSpecTestHelper.ok;
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunAll;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

/**
 * Port of go-xslt's <code>internal/xslt/sort_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class SortSpecTest
{
  @NonNull
  private static String _each (@NonNull final String sSorts)
  {
    return "<xsl:template match=\"/\"><xsl:for-each select=\"r/i\">" +
           sSorts +
           "<xsl:value-of select=\"@n\"/><xsl:text> </xsl:text></xsl:for-each></xsl:template>";
  }

  /**
   * XSLT 3.0 §13 sorting: xsl:sort in xsl:for-each / xsl:apply-templates /
   * xsl:perform-sort, with data-type, order, case-order, collation, lang and
   * typed sort keys.
   */
  @Test
  public void sortSpec ()
  {
    final String sSrc = "<r><i n=\"10\" s=\"b\">x</i><i n=\"9\" s=\"A\">y</i><i n=\"100\" s=\"a\">z</i><i n=\"9\" s=\"B\">w</i></r>";
    specRunAll (ok ("text order by default", _each ("<xsl:sort select=\"@n\"/>"), sSrc, "10 100 9 9 "),
                ok ("number order", _each ("<xsl:sort select=\"@n\" data-type=\"number\"/>"), sSrc, "9 9 10 100 "),
                ok ("descending", _each ("<xsl:sort select=\"@n\" data-type=\"number\" order=\"descending\"/>"), sSrc, "100 10 9 9 "),
                ok ("AVT data-type and order",
                    _each ("<xsl:sort select=\"@n\" data-type=\"{'number'}\" order=\"{'descending'}\"/>"),
                    sSrc,
                    "100 10 9 9 "),
                // Stable: equal keys stay in document order (y before w).
                ok ("stable for equal keys",
                    "<xsl:template match=\"/\"><xsl:for-each select=\"r/i\"><xsl:sort select=\"@n\" data-type=\"number\"/><xsl:sort select=\"0\" data-type=\"number\"/><xsl:value-of select=\".\"/><xsl:text> </xsl:text></xsl:for-each></xsl:template>",
                    sSrc,
                    "y w x z "),
                ok ("second key breaks ties",
                    "<xsl:template match=\"/\"><xsl:for-each select=\"r/i\"><xsl:sort select=\"@n\" data-type=\"number\"/><xsl:sort select=\".\"/><xsl:value-of select=\".\"/><xsl:text> </xsl:text></xsl:for-each></xsl:template>",
                    sSrc,
                    "w y x z "),
                // Codepoint collation: upper-case letters precede lower-case.
                ok ("codepoint collation",
                    "<xsl:template match=\"/\"><xsl:for-each select=\"r/i\"><xsl:sort select=\"@s\" collation=\"http://www.w3.org/2005/xpath-functions/collation/codepoint\"/><xsl:value-of select=\"@s\"/><xsl:text> </xsl:text></xsl:for-each></xsl:template>",
                    sSrc,
                    "A B a b "),
                // HTML ASCII case-insensitive collation treats A and a as equal:
                // document order decides.
                ok ("case-insensitive collation",
                    "<xsl:template match=\"/\"><xsl:for-each select=\"r/i\"><xsl:sort select=\"@s\" collation=\"http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive\"/><xsl:value-of select=\"@s\"/><xsl:text> </xsl:text></xsl:for-each></xsl:template>",
                    sSrc,
                    "A a b B "),
                // XTDE1035: an unknown collation.
                err ("unknown collation", _each ("<xsl:sort select=\"@s\" collation=\"http://example.com/no-such-collation\"/>"), sSrc, "XTDE1035"),
                err ("invalid lang", _each ("<xsl:sort select=\"@s\" lang=\"{'not a language'}\"/>"), sSrc, "XTDE0030"),
                ok ("sort body instead of select",
                    _each ("<xsl:sort data-type=\"number\"><xsl:value-of select=\"@n\"/></xsl:sort>"),
                    sSrc,
                    "9 9 10 100 "),
                ok ("sort body xsl:sequence", _each ("<xsl:sort><xsl:sequence select=\"xs:integer(@n)\"/></xsl:sort>"), sSrc, "9 9 10 100 "),
                ok ("sort without select uses the string value",
                    "<xsl:template match=\"/\"><xsl:for-each select=\"r/i\"><xsl:sort/><xsl:value-of select=\".\"/><xsl:text> </xsl:text></xsl:for-each></xsl:template>",
                    sSrc,
                    "w x y z "),
                // Typed sort keys compare by their type, not as strings.
                ok ("typed numeric keys",
                    "<xsl:template match=\"/\"><xsl:perform-sort select=\"(3, 10, 1.5, 2)\"><xsl:sort select=\".\"/></xsl:perform-sort></xsl:template>",
                    null,
                    "1.5 2 3 10"),
                ok ("perform-sort of dates",
                    "<xsl:template match=\"/\"><xsl:perform-sort select=\"(xs:date('2026-10-24'), xs:date('2001-01-01'), xs:date('2020-05-01'))\"><xsl:sort select=\".\"/></xsl:perform-sort></xsl:template>",
                    null,
                    "2001-01-01 2020-05-01 2026-10-24"),
                ok ("empty sort key sorts first",
                    "<xsl:template match=\"/\"><xsl:for-each select=\"r/i\"><xsl:sort select=\"@k\"/><xsl:value-of select=\"if (@k) then @k else '-'\"/></xsl:for-each></xsl:template>",
                    "<r><i k=\"b\"/><i/><i k=\"a\"/></r>",
                    "-ab"),
                ok ("NaN sorts first with number",
                    "<xsl:template match=\"/\"><xsl:for-each select=\"r/i\"><xsl:sort select=\"@n\" data-type=\"number\"/><xsl:value-of select=\"@n\"/></xsl:for-each></xsl:template>",
                    "<r><i n=\"2\"/><i n=\"x\"/><i n=\"1\"/></r>",
                    "x12"),
                // XTDE1030: sort key values that are not comparable.
                err ("incomparable typed keys",
                     "<xsl:template match=\"/\"><xsl:perform-sort select=\"(1, 'a')\"><xsl:sort select=\".\"/></xsl:perform-sort></xsl:template>",
                     null,
                     "XTDE1030"),
                // XTTE1020: a sort key of more than one item.
                err ("sort key sequence", _each ("<xsl:sort select=\"(@n, @s)\"/>"), sSrc, "XTTE1020"),
                ok ("apply-templates with sort",
                    "<xsl:template match=\"/\"><xsl:apply-templates select=\"r/i\"><xsl:sort select=\"@n\" data-type=\"number\" order=\"descending\"/></xsl:apply-templates></xsl:template>\n" +
                                                 "<xsl:template match=\"i\"><xsl:value-of select=\".\"/></xsl:template>",
                    sSrc,
                    "zxyw"),
                // XTSE1015: a sort key with both select and content.
                err ("select and content", _each ("<xsl:sort select=\"@n\">x</xsl:sort>"), sSrc, "XTSE1015"),
                err ("bad stable", _each ("<xsl:sort select=\"@n\" stable=\"maybe\"/>"), sSrc, "XTSE0020"));
  }
}
