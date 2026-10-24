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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Entity expansion must stay bounded: a few hundred bytes of declarations may
 * neither hang nor exhaust memory. The inputs are the attack shapes of
 * go-xslt's <code>TestEntityExpansionIsBounded</code> and
 * <code>TestEntityExpansionAllocationIsBounded</code>; here the JDK parser's
 * secure-processing limits have to refuse them.
 *
 * @author Philip Helger
 */
public final class XdmParserEntityExpansionTest
{
  private static final long MAX_MILLIS = 10_000;

  private static void _assertRefused (final String sName, final String sXml)
  {
    final long nStart = System.currentTimeMillis ();
    try
    {
      final XdmNode aDoc = new XdmParser ().parse (sXml);
      fail (sName + ": expected a parse error, got a string value of length " + aDoc.getStringValue ().length ());
    }
    catch (final XdmParseException ex)
    {
      // expected
    }
    final long nDuration = System.currentTimeMillis () - nStart;
    assertTrue (sName + " took " + nDuration + " ms", nDuration < MAX_MILLIS);
  }

  @Test
  public void testBillionLaughs ()
  {
    final StringBuilder aSB = new StringBuilder ("<!DOCTYPE r [<!ENTITY l0 \"lol\">");
    for (int i = 1; i <= 9; ++i)
      aSB.append ("<!ENTITY l").append (i).append (" \"").append (("&l" + (i - 1) + ";").repeat (10)).append ("\">");
    aSB.append ("]><r>&l9;</r>");
    _assertRefused ("billion laughs", aSB.toString ());
  }

  @Test
  public void testRecursiveDeclarations ()
  {
    _assertRefused ("recursive declarations",
                    "<!DOCTYPE r [<!ENTITY aaa \"&bbb;\"><!ENTITY bbb \"&aaa;>\">]><r>&aaa;</r>");
  }

  @Test
  public void testMarkupEntities ()
  {
    final StringBuilder aSB = new StringBuilder ("<!DOCTYPE r [<!ENTITY m0 \"<b>lol</b>\">");
    for (int i = 1; i <= 8; ++i)
      aSB.append ("<!ENTITY m")
         .append (i)
         .append (" \"<a>")
         .append (("&m" + (i - 1) + ";").repeat (10))
         .append ("</a>\">");
    aSB.append ("]><r>&m8;</r>");
    _assertRefused ("markup entities", aSB.toString ());
  }

  @Test
  public void testManyReferencesToALargeEntity ()
  {
    final String sBig = "x".repeat (1 << 20);
    _assertRefused ("many references",
                    "<!DOCTYPE r [<!ENTITY e \"" + sBig + "\">]><r>" + "&e;".repeat (200) + "</r>");
  }

  @Test
  public void testOneValueWithManyReferencesToALargeEntity ()
  {
    final String sChain = "<!ENTITY a \"" +
                          "a".repeat (100) +
                          "\">" +
                          "<!ENTITY b \"" +
                          "&a;".repeat (10) +
                          "\">" +
                          "<!ENTITY c \"" +
                          "&b;".repeat (10) +
                          "\">" +
                          "<!ENTITY d \"" +
                          "&c;".repeat (10) +
                          "\">" +
                          "<!ENTITY e \"" +
                          "&d;".repeat (10) +
                          "\">" +
                          "<!ENTITY f \"" +
                          "&e;".repeat (10) +
                          "\">";
    _assertRefused ("100 references to a 10 MB entity",
                    "<!DOCTYPE r [" + sChain + "<!ENTITY z \"" + "&f;".repeat (100) + "\">]><r>&z;</r>");
    _assertRefused ("1000 references to a 10 MB entity",
                    "<!DOCTYPE r [" + sChain + "<!ENTITY z \"" + "&f;".repeat (1000) + "\">]><r>&z;</r>");
  }

  @Test
  public void testParameterEntities ()
  {
    final StringBuilder aSB = new StringBuilder ("<!DOCTYPE r [<!ENTITY % p0 \"" + "x".repeat (100) + "\">");
    for (int i = 1; i <= 4; ++i)
      aSB.append ("<!ENTITY % p").append (i).append (" \"").append (("%p" + (i - 1) + ";").repeat (10)).append ("\">");
    aSB.append ("<!ENTITY g \"").append ("%p4;".repeat (2000)).append ("\">]><r>&g;</r>");
    _assertRefused ("parameter entities", aSB.toString ());
  }

  @Test
  public void testUnterminatedDoctype ()
  {
    _assertRefused ("unterminated DOCTYPE", "<!DOCTYPE[<!ENTITY0 '<']");
  }

  @Test
  public void testOrdinaryEntitiesStillWork () throws XdmParseException
  {
    final String sXml = "<!DOCTYPE r [<!ENTITY e \"abc\"><!ENTITY f \"&e;&e;\">]><r>" + "&f;".repeat (1000) + "</r>";
    assertEquals (6000, new XdmParser ().parse (sXml).getStringValue ().length ());
  }
}
