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
package com.helger.phoxtrot.xdm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Test class for the short attribute values the tree builder shares within a
 * document.
 *
 * @author Philip Helger
 */
public final class XdmTreeBuilderSharedValuesTest
{
  @Test
  public void testShared () throws XdmParseException
  {
    final String sLong = "a value that is longer than thirty-two characters";
    final IXdmNode aDoc = new XdmParser ().parse ("<r>" +
                                                  "<a c='EUR' n='" +
                                                  sLong +
                                                  "'>EUR</a>" +
                                                  "<a c='EUR' n='" +
                                                  sLong +
                                                  "'>EUR</a>" +
                                                  "<b>" +
                                                  sLong +
                                                  "</b><b>" +
                                                  sLong +
                                                  "</b>" +
                                                  "<c>x &amp; y</c><c>x &amp; y</c>" +
                                                  "</r>");
    final IXdmNode aR = aDoc.getChildAtIndex (0);
    final IXdmNode aA1 = aR.getChildAtIndex (0);
    final IXdmNode aA2 = aR.getChildAtIndex (1);
    // Short attribute values are shared, text is not
    assertSame (aA1.getAttribute (null, "c").getValue (), aA2.getAttribute (null, "c").getValue ());
    assertNotSame (aA1.getChildAtIndex (0).getValue (), aA2.getChildAtIndex (0).getValue ());
    assertEquals ("EUR", aA2.getChildAtIndex (0).getValue ());
    // Longer values are not
    assertEquals (sLong, aA1.getAttribute (null, "n").getValue ());
    assertNotSame (aA1.getAttribute (null, "n").getValue (), aA2.getAttribute (null, "n").getValue ());
    assertNotSame (aR.getChildAtIndex (2).getChildAtIndex (0).getValue (),
                   aR.getChildAtIndex (3).getChildAtIndex (0).getValue ());
    // Text of several character events is complete
    assertEquals ("x & y", aR.getChildAtIndex (4).getChildAtIndex (0).getValue ());
    assertEquals ("x & y", aR.getChildAtIndex (5).getChildAtIndex (0).getValue ());
  }

  @Test
  public void testManyDistinctValues () throws XdmParseException
  {
    // More distinct values than the table has slots: every value stays right
    final StringBuilder aSB = new StringBuilder ("<r>");
    for (int i = 0; i < 5000; ++i)
      aSB.append ("<v a='").append (i).append ("' b='").append (i % 3000).append ("'/>");
    aSB.append ("</r>");
    final IXdmNode aR = new XdmParser ().parse (aSB.toString ()).getChildAtIndex (0);
    for (int i = 0; i < 5000; ++i)
    {
      final IXdmNode aV = aR.getChildAtIndex (i);
      assertEquals (Integer.toString (i), aV.getAttributeAtIndex (0).getValue ());
      assertEquals (Integer.toString (i % 3000), aV.getAttributeAtIndex (1).getValue ());
    }
  }
}
