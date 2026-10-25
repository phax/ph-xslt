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
package com.helger.xslt.ext.charactermap;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.helger.xslt.XsltTestHelper;

/**
 * Test class for class {@link CharacterMapExtension}.
 *
 * @author Philip Helger
 */
public final class CharacterMapExtensionTest
{
  private static final String INITIAL = "<xsl:template name='xsl:initial-template'><xsl:value-of select=\"'abc'\"/></xsl:template>";

  @Test
  public void testSimpleMap ()
  {
    assertEquals ("[A]bc",
                  XsltTestHelper.text ("<xsl:character-map name='m'><xsl:output-character character='a' string='[A]'/></xsl:character-map>" +
                                       "<xsl:output method='text' use-character-maps='m'/>" +
                                       INITIAL,
                                       null));
  }

  @Test
  public void testNestedMapsOwnMappingWins ()
  {
    assertEquals ("[a2][B]c",
                  XsltTestHelper.text ("<xsl:character-map name='inner'><xsl:output-character character='a' string='[a1]'/>" +
                                       "<xsl:output-character character='b' string='[B]'/></xsl:character-map>" +
                                       "<xsl:character-map name='outer' use-character-maps='inner'>" +
                                       "<xsl:output-character character='a' string='[a2]'/></xsl:character-map>" +
                                       "<xsl:output method='text' use-character-maps='outer'/>" +
                                       INITIAL,
                                       null));
  }

  @Test
  public void testErrors ()
  {
    assertEquals ("XTSE1590",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET + "<xsl:output use-character-maps='nope'/>" + INITIAL + XsltTestHelper.END,
                                            null));
    assertEquals ("XTSE1590",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:character-map name='a' use-character-maps='nope'/>" +
                                            INITIAL +
                                            XsltTestHelper.END,
                                            null));
    assertEquals ("XTSE1600",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:character-map name='a' use-character-maps='b'/>" +
                                            "<xsl:character-map name='b' use-character-maps='a'/>" +
                                            INITIAL +
                                            XsltTestHelper.END,
                                            null));
    assertEquals ("XTSE1580",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:character-map name='a'/><xsl:character-map name='a'/>" +
                                            INITIAL +
                                            XsltTestHelper.END,
                                            null));
  }
}
