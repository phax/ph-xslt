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
package com.helger.xpath.xdm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test class for class {@link XdmQName}.
 *
 * @author Philip Helger
 */
public final class XdmQNameTest
{
  @Test
  public void testPrefixIsIgnoredForEquality ()
  {
    final XdmQName a = new XdmQName ("urn:x", "a", "p");
    final XdmQName b = new XdmQName ("urn:x", "a", "q");
    assertEquals (a, b);
    assertEquals (a.hashCode (), b.hashCode ());
    assertNotEquals (a, new XdmQName ("urn:y", "a"));
    assertNotEquals (a, new XdmQName ("urn:x", "b"));
  }

  @Test
  public void testLexicalForms ()
  {
    assertEquals ("p:a", new XdmQName ("urn:x", "a", "p").getLexicalName ());
    assertEquals ("a", new XdmQName ("a").getLexicalName ());
    assertEquals ("Q{urn:x}a", new XdmQName ("urn:x", "a", "p").getEQName ());
    assertEquals ("a", new XdmQName ("a").getEQName ());
    assertTrue (new XdmQName ("urn:x", "a").hasNamespace ());
    assertFalse (new XdmQName ("a").hasNamespace ());
  }

  @Test
  public void testIsSameName ()
  {
    final XdmQName a = new XdmQName ("a");
    assertTrue (a.isSameName (null, "a"));
    assertTrue (a.isSameName ("", "a"));
    assertFalse (a.isSameName ("urn:x", "a"));
  }

  @Test
  public void testGetInterned ()
  {
    final String sNs = "urn:x:interned";
    final XdmQName a = new XdmQName (new String (sNs), new String ("a"), new String ("p"));
    assertNotSame (sNs, a.namespaceUri ());
    final XdmQName b = a.getInterned ();
    assertEquals (a, b);
    assertEquals ("p", b.prefix ());
    assertSame (sNs, b.namespaceUri ());
    assertSame ("a", b.localName ());
    assertSame ("p", b.prefix ());
    // Canonical already: the same instance
    assertSame (b, b.getInterned ());
  }

  @Test
  public void testParsedNamesAreInterned () throws XdmParseException
  {
    // The names of parsed nodes share their strings with interned node test
    // names, so that a match is decided by identity
    final String sXml = "<p:r xmlns:p='urn:x:parsed' a='1'><p:c p:b='2'/></p:r>";
    final IXdmNode aRoot = new XdmParser ().parse (sXml).getChildAtIndex (0);
    assertSame ("urn:x:parsed", aRoot.getName ().namespaceUri ());
    assertSame ("r", aRoot.getName ().localName ());
    assertSame ("a", aRoot.getAttributeAtIndex (0).getName ().localName ());
    final IXdmNode aChild = aRoot.getChildAtIndex (0);
    assertSame ("c", aChild.getName ().localName ());
    assertSame ("b", aChild.getAttributeAtIndex (0).getName ().localName ());
    assertSame ("urn:x:parsed", aChild.getAttributeAtIndex (0).getName ().namespaceUri ());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyLocalName ()
  {
    new XdmQName ("");
  }
}
