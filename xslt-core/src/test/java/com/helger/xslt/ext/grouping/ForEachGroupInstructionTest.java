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
package com.helger.xslt.ext.grouping;

import static com.helger.xslt.XsltTestHelper.END;
import static com.helger.xslt.XsltTestHelper.SHEET;
import static com.helger.xslt.XsltTestHelper.errorCode;
import static com.helger.xslt.XsltTestHelper.text;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test class for class {@link ForEachGroupInstruction}.
 *
 * @author Philip Helger
 */
public final class ForEachGroupInstructionTest
{
  private static final String IT = "<xsl:template name='xsl:initial-template'>";
  private static final String TE = "</xsl:template>";

  @Test
  public void testGroupBy ()
  {
    assertEquals ("1:1 3 5|0:2 4|",
                  text (IT +
                        "<xsl:for-each-group select='1 to 5' group-by='. mod 2'>" +
                        "<xsl:value-of select='current-grouping-key()'/>:<xsl:value-of select='current-group()'/>|" +
                        "</xsl:for-each-group>" +
                        TE,
                        null));
  }

  @Test
  public void testGroupByMultipleKeys ()
  {
    assertEquals ("a:1 2|b:1|c:2|",
                  text (IT +
                        "<xsl:variable name='m' select='[(\"a\",\"b\"), (\"a\",\"c\")]'/>" +
                        "<xsl:for-each-group select='1 to 2' group-by='$m(.)'>" +
                        "<xsl:value-of select='current-grouping-key()'/>:<xsl:value-of select='current-group()'/>|" +
                        "</xsl:for-each-group>" +
                        TE,
                        null));
  }

  @Test
  public void testGroupAdjacentAndSort ()
  {
    assertEquals ("b2a2a1",
                  text (IT +
                        "<xsl:for-each-group select='(\"a\",\"a\",\"b\",\"b\",\"a\")' group-adjacent='.'>" +
                        "<xsl:sort select='current-grouping-key()' order='descending'/>" +
                        "<xsl:sort select='count(current-group())' order='descending'/>" +
                        "<xsl:value-of select='current-grouping-key(), count(current-group())' separator=''/>" +
                        "</xsl:for-each-group>" +
                        TE,
                        null));
  }

  @Test
  public void testComposite ()
  {
    assertEquals ("1 a:2|2 b:1|",
                  text (IT +
                        "<xsl:for-each-group select='(\"1a\",\"2b\",\"1a\")' group-by='substring(.,1,1), substring(.,2)' composite='yes'>" +
                        "<xsl:value-of select='current-grouping-key()'/>:<xsl:value-of select='count(current-group())'/>|" +
                        "</xsl:for-each-group>" +
                        TE,
                        null));
  }

  @Test
  public void testStartingAndEndingWith ()
  {
    final String sSource = "<r><h/><p/><p/><h/><p/></r>";
    assertEquals ("3 2 ",
                  text ("<xsl:template match='r'><xsl:for-each-group select='*' group-starting-with='h'>" +
                        "<xsl:value-of select='count(current-group())'/><xsl:text> </xsl:text>" +
                        "</xsl:for-each-group></xsl:template>",
                        sSource));
    assertEquals ("2 1 2 ",
                  text ("<xsl:template match='r'><xsl:variable name='n' select='\"p\"'/>" +
                        "<xsl:for-each-group select='*' group-ending-with='*[name() = $n]'>" +
                        "<xsl:value-of select='count(current-group())'/><xsl:text> </xsl:text>" +
                        "</xsl:for-each-group></xsl:template>",
                        sSource));
  }

  @Test
  public void testErrors ()
  {
    assertEquals ("XTDE1061", errorCode (SHEET + IT + "<xsl:value-of select='current-group()'/>" + TE + END, null));
    assertEquals ("XTDE1071",
                  errorCode (SHEET +
                             IT +
                             "<xsl:for-each-group select='1' group-starting-with='.'><xsl:value-of select='current-grouping-key()'/></xsl:for-each-group>" +
                             TE +
                             END,
                             null));
    assertEquals ("XTTE1100",
                  errorCode (SHEET + IT + "<xsl:for-each-group select='1' group-adjacent='(1,2)'/>" + TE + END, null));
  }
}
