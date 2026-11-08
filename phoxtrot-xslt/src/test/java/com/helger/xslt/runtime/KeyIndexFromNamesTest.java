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
package com.helger.xslt.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xslt.XsltTestHelper;

/**
 * A key index built from the name index of the document
 * (<code>Engine._getKeyCandidatesFromNames</code>) equals the one of the walk
 * over the whole tree: the same nodes in the same order for every key value.
 * The walk is taken for a temporary tree (not numbered) and for patterns that
 * can match nodes of other names, so the lookups over a document and over a
 * temporary copy of it are compared.
 *
 * @author Philip Helger
 */
public final class KeyIndexFromNamesTest
{
  private static final String SOURCE = "<r xmlns:p='urn:p'>" +
                                       "<s type='A' id='1'/>" +
                                       "<s type=' PerTP ' id='2'>x</s>" +
                                       "<g><s type='A' id='3'/><t type='B'>A</t><p:s type='A' id='9'/></g>" +
                                       "<t type='A'>PerTP</t>" +
                                       "<g><g><s id='4' type='PerTP'/></g></g>" +
                                       "<s id='5' type='B'><t type='A'>B</t></s>" +
                                       "</r>";

  /**
   * The match and use of the keys k1 to k12: eligible ones and, as control,
   * k9 with a wildcard. k10 has two declarations, k11 is composite.
   */
  private static final String [] [] KEYS = { { "s", "@type" },
                                              { "/r/s[normalize-space(@type) = 'PerTP']", "@id" },
                                              { "s | t", "@type" },
                                              { "//t", "string(.)" },
                                              { "g/s", "@id" },
                                              { "@type", "." },
                                              { "s[2]", "@type" },
                                              { "s[last()] | @id", "string(.)" },
                                              { "*", "@type" },
                                              { "t", "@type" },
                                              { "s", "@type, @id" },
                                              { "p:s | g//s[@id > 3]", "@type" } };

  private static final int ELIGIBLE_KEYS = 11;

  /**
   * @param bWalk
   *        <code>true</code> to add an alternative to every match pattern that
   *        matches nothing here but forces the walk of the whole tree.
   * @param sRoots
   *        The trees to look up in.
   * @return The stylesheet listing the paths of the nodes of every key value.
   */
  @NonNull
  private static String _sheet (final boolean bWalk, @NonNull final String sRoots)
  {
    final StringBuilder aKeys = new StringBuilder ();
    for (int i = 0; i < KEYS.length; ++i)
    {
      aKeys.append ("<xsl:key name='k")
           .append (i + 1)
           .append ("' match=\"")
           .append (KEYS[i][0])
           .append (bWalk ? " | processing-instruction(none)" : "")
           .append ("\" use='")
           .append (KEYS[i][1])
           .append (i == 10 ? "' composite='yes'/>" : "'/>");
      if (i == 9)
        aKeys.append ("<xsl:key name='k10' match=\"s").append (bWalk ? " | processing-instruction(none)" : "").append ("\" use='@type'/>");
    }
    return XsltTestHelper.SHEET.replace ("<xsl:stylesheet ", "<xsl:stylesheet xmlns:p='urn:p' ") +
           "<xsl:output method='text'/>" +
           aKeys +
           "<xsl:variable name='t'><xsl:copy-of select='/'/></xsl:variable>" +
           "<xsl:variable name='d' select='parse-xml(serialize(/))'/>" +
           "<xsl:template match='/'>" +
           "<xsl:for-each select='" +
           sRoots +
           "'><xsl:variable name='root' select='.'/>" +
           "<xsl:for-each select=\"1 to 12\"><xsl:variable name='n' select='.'/>" +
           "<xsl:for-each select=\"'A', 'B', 'PerTP', ' PerTP ', '1', '2', '3', '4', '5', '9', 'x', ''\">" +
           "<xsl:variable name='v' select='.'/>" +
           "<xsl:value-of select=\"'k' || $n || '[' || $v || ']=' || string-join((if ($n = 11) then key('k11', ($v, '1'), $root) " +
           "else key('k' || $n, $v, $root)) ! path(.), ',')\"/><xsl:text>&#10;</xsl:text>" +
           "</xsl:for-each></xsl:for-each></xsl:for-each>" +
           "</xsl:template>" +
           XsltTestHelper.END;
  }

  @NonNull
  private static String _run (final boolean bWalk, @NonNull final String sRoots)
  {
    return XsltTestHelper.transform (_sheet (bWalk, sRoots), SOURCE, null);
  }

  /**
   * The keys over the source document, a temporary tree and a second
   * document (<code>key(..., $doc)</code>) give the same nodes from the name
   * index as from the walk.
   */
  @Test
  public void testSameIndexAsWalk ()
  {
    for (final String sRoots : new String [] { "/", "$t", "$d" })
    {
      long nBefore = Engine.getKeyIndexFromNamesCount ();
      final String sIndexed = _run (false, sRoots);
      // Every eligible key came from the name index
      assertEquals (sRoots, ELIGIBLE_KEYS, Engine.getKeyIndexFromNamesCount () - nBefore);
      nBefore = Engine.getKeyIndexFromNamesCount ();
      final String sWalked = _run (true, sRoots);
      assertEquals (sRoots, 0, Engine.getKeyIndexFromNamesCount () - nBefore);
      assertEquals (sRoots, sWalked, sIndexed);
      // Not trivially empty
      assertNotEquals (sIndexed, -1, sIndexed.indexOf ("k1[A]=/Q{}r[1]/Q{}s[1],/Q{}r[1]/Q{}g[1]/Q{}s[1]"));
    }
  }
}
