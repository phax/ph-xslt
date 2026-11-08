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
package com.helger.xpath.fn;

import static com.helger.xpath.fn.FnTestHelper.assertLex;
import static com.helger.xpath.fn.FnTestHelper.assertXPathError;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link FnError}.
 *
 * @author Philip Helger
 */
public final class FnErrorTest
{
  @Test
  public void testError ()
  {
    XPathException ex = assertXPathError ("FOER0000", "error()");
    assertSame (FnError.FOER0000, ex.getCodeName ());
    assertNull (ex.getErrorObject ());

    ex = assertXPathError ("FOER0000", "error(())");
    assertEquals (XPathException.ERR_NAMESPACE, ex.getCodeName ().namespaceUri ());

    ex = assertXPathError ("FOER0000", "error((), 'desc')");
    assertEquals ("desc", ex.getDescription ());

    ex = assertXPathError ("FORG0001", "error(xs:QName('err:FORG0001'))");
    assertEquals (XPathException.ERR_NAMESPACE, ex.getCodeName ().namespaceUri ());

    ex = assertXPathError ("abc", "error(xs:QName('fn:abc'), 'my desc', (1, 'two'))");
    assertEquals (new XdmQName (XdmConstants.NS_FN, "abc"), ex.getCodeName ());
    assertEquals ("my desc", ex.getDescription ());
    assertEquals (2, ex.getErrorObject ().size ());
  }

  @Test
  public void testErrorInExpressions ()
  {
    assertLex ("1", "if (true()) then 1 else error()");
    assertXPathError ("FOER0000", "(1, 2)[error()]");
  }

  @Test
  public void testTrace ()
  {
    final ICommonsList <String> aLog = new CommonsArrayList <> ();
    final FnError.ITraceListener aOld = FnError.getTraceListener ();
    FnError.setTraceListener ( (aValue, sLabel) -> aLog.add (sLabel + "=" + FnError.getTraceString (aValue)));
    try
    {
      assertLex ("1 2 3", "trace((1, 2, 3), 'lbl')");
      assertLex ("4", "trace(4)");
      assertLex ("0", "count(trace((), 'empty'))");
    }
    finally
    {
      FnError.setTraceListener (aOld);
    }
    assertEquals ("[lbl=1 2 3, null=4, empty=]", aLog.toString ());
    assertLex ("x", "trace('x', 'default listener')");
  }
}
