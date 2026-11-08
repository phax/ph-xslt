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
package com.helger.xpath.conformance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.xpath.xdm.IXdmNode;

/**
 * Test class for classes {@link QT3XmlCompare} and {@link QT3XmlSerializer}.
 *
 * @author Philip Helger
 */
public final class QT3XmlCompareTest
{
  @Test
  public void testEqual ()
  {
    assertTrue (QT3XmlCompare.isXmlEqual ("<a/>", "<a></a>", false));
    assertTrue (QT3XmlCompare.isXmlEqual ("<?xml version=\"1.0\"?>\n<a/>", "<a/>", false));
    // Attributes are a set
    assertTrue (QT3XmlCompare.isXmlEqual ("<a x='1' y='2'/>", "<a y=\"2\" x=\"1\"/>", false));
    // Fragments and top-level text
    assertTrue (QT3XmlCompare.isXmlEqual ("<a/><b/>text", "<a/><b/>text", false));
    assertTrue (QT3XmlCompare.isXmlEqual ("plain", "plain", false));
    assertTrue (QT3XmlCompare.isXmlEqual ("<a><!--c--><?pi data?></a>", "<a><!--c--><?pi data?></a>", false));
    // Namespace declarations are not compared, only the names
    assertTrue (QT3XmlCompare.isXmlEqual ("<a xmlns:p='urn:p'/>", "<a/>", false));
  }

  @Test
  public void testNotEqual ()
  {
    assertFalse (QT3XmlCompare.isXmlEqual ("<a/>", "<b/>", false));
    assertFalse (QT3XmlCompare.isXmlEqual ("<a x='1'/>", "<a x='2'/>", false));
    assertFalse (QT3XmlCompare.isXmlEqual ("<a x='1'/>", "<a x='1' y='1'/>", false));
    // Whitespace-only text is significant
    assertFalse (QT3XmlCompare.isXmlEqual ("<a> <b/></a>", "<a><b/></a>", false));
    assertFalse (QT3XmlCompare.isXmlEqual ("<a><!--c--></a>", "<a><!--d--></a>", false));
    assertFalse (QT3XmlCompare.isXmlEqual ("<a><?p x?></a>", "<a><?q x?></a>", false));
    assertFalse (QT3XmlCompare.isXmlEqual ("<a>", "<a/>", false));
    // Namespace URI matters
    assertFalse (QT3XmlCompare.isXmlEqual ("<a xmlns='urn:x'/>", "<a/>", true));
  }

  @Test
  public void testPrefixes ()
  {
    final String sLeft = "<p:a xmlns:p='urn:x' p:at='1'/>";
    final String sRight = "<q:a xmlns:q='urn:x' q:at='1'/>";
    assertFalse (QT3XmlCompare.isXmlEqual (sLeft, sRight, false));
    assertTrue (QT3XmlCompare.isXmlEqual (sLeft, sRight, true));
  }

  @Test
  public void testDeepTreeIsIterative ()
  {
    final int nDepth = 5_000;
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < nDepth; ++i)
      aSB.append ("<e>");
    for (int i = 0; i < nDepth; ++i)
      aSB.append ("</e>");
    final String s = aSB.toString ();
    assertTrue (QT3XmlCompare.isXmlEqual (s, s, false));
    final IXdmNode aDoc = QT3XmlCompare.parseFragment (s);
    assertNotNull (aDoc);
    assertEquals ("<_x_>" + s.replace ("<e></e>", "<e/>") + "</_x_>", QT3XmlSerializer.serialize (aDoc));
  }

  @Test
  public void testParseFragment ()
  {
    assertNull (QT3XmlCompare.parseFragment ("<a"));
    final IXdmNode aDoc = QT3XmlCompare.parseFragment ("<a x='&lt;'>t&amp;</a>");
    assertNotNull (aDoc);
    assertEquals ("<_x_><a x=\"&lt;\">t&amp;</a></_x_>", QT3XmlSerializer.serialize (aDoc));
  }
}
