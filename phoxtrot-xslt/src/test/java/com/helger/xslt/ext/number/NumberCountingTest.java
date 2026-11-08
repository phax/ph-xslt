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
package com.helger.xslt.ext.number;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xslt.XsltTestHelper;

/**
 * The counting of xsl:number (XSLT 3.0 section 12.3) from the name index of
 * the document gives the same numbers as testing every preceding node: each
 * case numbers the same nodes in the source document (name index where the
 * patterns allow it) and in a parentless copy of its element (no document,
 * so always the walk), and for level="any" also against the expressions of
 * the specification.
 *
 * @author Philip Helger
 */
public final class NumberCountingTest
{
  /**
   * Sections with paragraphs, notes, attributes, text and comments, nested
   * subsections, and a list with more siblings than are scanned one by one.
   */
  @NonNull
  private static String _createDoc ()
  {
    final StringBuilder aSB = new StringBuilder ("<doc>");
    for (int i = 1; i <= 12; ++i)
    {
      aSB.append ("<sec id='").append (i).append ("'>text<h/>");
      for (int j = 0; j < i % 4; ++j)
        aSB.append (j % 2 == 0 ? "<p a='x'/>" : "<p/>");
      if (i % 3 == 0)
        aSB.append ("<note/><!--c--><sub><p/><note><p a='y'/></note></sub>");
      aSB.append ("</sec>");
    }
    aSB.append ("<list>");
    for (int i = 0; i < 80; ++i)
      aSB.append (i % 5 == 0 ? "<p a='z'/>" : i % 7 == 0 ? "<note/>" : "<item/>");
    aSB.append ("</list><sec id='13'><item/><p/></sec></doc>");
    return aSB.toString ();
  }

  private static final String DOC = _createDoc ();

  /**
   * Number every node of a selection in the source document and in a
   * parentless copy of its element.
   *
   * @param sSelect
   *        The relative selection from the doc element, e.g.
   *        <code>.//p</code>.
   * @param sNumber
   *        The attributes of xsl:number.
   * @return The numbers in the document, then "#", then the numbers in the
   *         copy.
   */
  @NonNull
  private static String _number (@NonNull final String sSelect, @NonNull final String sNumber)
  {
    final String sEach = "<xsl:number " + sNumber + "/><xsl:text>,</xsl:text>";
    return XsltTestHelper.text ("<xsl:template match='/'>" +
                                "<xsl:variable name='e' as='element()'><xsl:copy-of select='doc'/></xsl:variable>" +
                                "<xsl:for-each select='doc/(" +
                                sSelect +
                                ")'>" +
                                sEach +
                                "</xsl:for-each><xsl:text>#</xsl:text><xsl:for-each select='$e/(" +
                                sSelect +
                                ")'>" +
                                sEach +
                                "</xsl:for-each></xsl:template>",
                                DOC);
  }

  /**
   * Check that a case numbers the same in the document and in the copy, and
   * whether the name index was used.
   *
   * @return The numbers in the document.
   */
  @NonNull
  private static String _check (@NonNull final String sSelect, @NonNull final String sNumber, final boolean bByName)
  {
    final long nBefore = NumberInstruction.getNameIndexCountCount ();
    final String s = _number (sSelect, sNumber);
    final boolean bUsed = NumberInstruction.getNameIndexCountCount () > nBefore;
    final int nHash = s.indexOf ('#');
    final String sDoc = s.substring (0, nHash);
    assertEquals (sSelect + " " + sNumber, sDoc, s.substring (nHash + 1));
    assertFalse (sSelect + " " + sNumber, sDoc.isEmpty ());
    assertEquals (sSelect + " " + sNumber + ": name index used", Boolean.valueOf (bByName), Boolean.valueOf (bUsed));
    return sDoc;
  }

  /**
   * The value of the specification's expressions for level="any" for every
   * selected node, with the count and from patterns as predicates.
   */
  @NonNull
  private static String _spec (@NonNull final String sSelect,
                               @NonNull final String sCountPredicate,
                               @NonNull final String sFromPredicate)
  {
    final String sAxis = "(preceding::node() | ancestor-or-self::node())";
    return XsltTestHelper.text ("<xsl:template match='/'><xsl:for-each select='doc/(" +
                                sSelect +
                                ")'><xsl:variable name='F' select='(" +
                                sAxis +
                                "[" +
                                sFromPredicate +
                                "] | root())[last()]'/><xsl:variable name='n' select='count(" +
                                sAxis +
                                "[" +
                                sCountPredicate +
                                "][. is $F or . &gt;&gt; $F])'/>" +
                                "<xsl:if test='$n gt 0'><xsl:value-of select='$n'/></xsl:if><xsl:text>,</xsl:text>" +
                                "</xsl:for-each></xsl:template>",
                                DOC);
  }

  @Test
  public void testAnyExactNames ()
  {
    assertEquals (_spec (".//p", "self::p", "false()"), _check (".//p", "level='any'", true));
    assertEquals (_spec (".//(p|note)", "self::p or self::note", "false()"),
                  _check (".//(p|note)", "level='any' count='p | note'", true));
    assertEquals (_spec (".//(p|note)", "self::p or self::note", "self::sec"),
                  _check (".//(p|note)", "level='any' count='p | note' from='sec'", true));
    assertEquals (_spec (".//p", "self::p", "self::sub or self::sec"),
                  _check (".//p", "level='any' count='p' from='sub | sec'", true));
  }

  @Test
  public void testAnyTestedNames ()
  {
    assertEquals (_spec (".//(p|note)", "self::p[@a] or self::note", "false()"),
                  _check (".//(p|note)", "level='any' count='p[@a] | note'", true));
    assertEquals (_spec (".//(p|note)", "self::p or self::note", "self::sec[@id mod 2 = 0]"),
                  _check (".//(p|note)", "level='any' count='p | note' from='sec[@id mod 2 = 0]'", true));
    assertEquals (_spec (".//(p|note)", "self::p[parent::sec] or self::note", "self::sec[@id mod 2 = 0] or self::sub[note]"),
                  _check (".//(p|note)", "level='any' count='sec/p | note' from='sec[@id mod 2 = 0] | sub[note]'", true));
  }

  @Test
  public void testAnyAttributes ()
  {
    // The attribute itself is counted, the other attributes are on neither
    // axis
    assertEquals (_spec (".//@a", "self::p or self::attribute(a)", "false()"),
                  _check (".//@a", "level='any' count='p | @a'", true));
    // The default pattern for an attribute can only use the walk
    assertEquals (_spec (".//@a", "self::attribute(a)", "self::sec"), _check (".//@a", "level='any' from='sec'", false));
  }

  @Test
  public void testAnyWalk ()
  {
    // Patterns that can match other nodes than named elements
    assertEquals (_spec (".//p", "self::p or self::comment()", "self::sub"),
                  _check (".//p", "level='any' count='p | comment()' from='node()[self::sub]'", false));
    assertEquals (_spec (".//p", "self::p", "self::sub"), _check (".//p", "level='any' count='p' from='*[self::sub]'", false));
    assertEquals (_spec (".//text()", "self::text()", "false()"), _check (".//text()", "level='any'", false));
  }

  @Test
  public void testSiblings ()
  {
    // The list has more children than are scanned
    _check ("list/*", "", true);
    _check ("list/*", "count='item | p'", true);
    _check ("list/*", "count='p[@a] | item'", true);
    _check ("list/*", "count='*'", false);
    _check (".//p", "level='multiple' count='sec | sub | list | p | item | note'", true);
    _check (".//p", "level='multiple' count='sec | note | p' from='sub'", true);
    // The sections have fewer children: always scanned
    _check ("sec/p", "count='p'", false);
    assertTrue (_check ("list/*", "", true).startsWith ("1,1,2,3,4,2,5,1,"));
  }
}
