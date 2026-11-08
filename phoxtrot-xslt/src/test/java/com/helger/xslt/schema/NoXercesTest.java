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
package com.helger.xslt.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Assume;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.jaxp.JdkSchemaProvider;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.XdmSchemaSource;
import com.helger.xpath.xdm.schema.XdmSchemaSupport;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltTestHelper;

/**
 * The basic processor without Apache Xerces-J on the class path. Runs in a
 * separate surefire execution that removes Xerces-J from the test class path
 * (see the POM); in the normal test run, where Xerces-J is present, it is
 * skipped.
 *
 * @author Philip Helger
 */
public final class NoXercesTest
{
  private static boolean _isXercesPresent ()
  {
    try
    {
      Class.forName ("org.apache.xerces.xs.XSModel");
      return true;
    }
    catch (final ClassNotFoundException ex)
    {
      return false;
    }
  }

  @Test
  public void testBasicProcessor ()
  {
    Assume.assumeFalse ("Xerces-J is on the class path", _isXercesPresent ());
    assertFalse (XdmSchemaSupport.isAvailable ());

    // A transformation as usual
    assertEquals ("no 3 false",
                  XsltTestHelper.text ("<xsl:template name='xsl:initial-template'>" +
                                       "<xsl:value-of select=\"system-property('xsl:is-schema-aware'), 1 + 2, " +
                                       "element-available('xsl:import-schema')\"/></xsl:template>",
                                       null));
    // The schema-awareness constructs are rejected as by any basic processor
    assertEquals ("XTSE1650",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:import-schema namespace='urn:x'/>" +
                                            "<xsl:template name='xsl:initial-template'/>" +
                                            XsltTestHelper.END,
                                            null));
    assertEquals ("XTSE1660",
                  XsltTestHelper.errorCode (XsltTestHelper.SHEET +
                                            "<xsl:template name='xsl:initial-template'><e xsl:validation='strict'/></xsl:template>" +
                                            XsltTestHelper.END,
                                            null));
    // Claiming schema-awareness fails clearly
    try
    {
      XsltCompiler.compile (XsltTestHelper.SHEET + XsltTestHelper.END, new XsltCompileOptions ().setSchemaAware (true));
      fail ();
    }
    catch (final IllegalStateException ex)
    {
      // expected
    }
    catch (final XPathException ex)
    {
      fail (ex.getMessage ());
    }
  }

  @Test
  public void testJdkValidator () throws Exception
  {
    Assume.assumeFalse ("Xerces-J is on the class path", _isXercesPresent ());
    // Schema-validated input documents need no Xerces-J
    final String sXsd = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'><xs:element name='a' type='xs:int'/></xs:schema>";
    final IXdmSchemaSet aSchemaSet = new JdkSchemaProvider ().compile (new CommonsArrayList <> (new XdmSchemaSource (null, sXsd)));
    final XdmParser aParser = new XdmParser ().setSchemaSet (aSchemaSet);
    final IXdmNode aA = aParser.parse ("<a> 5 </a>").getChildAtIndex (0);
    assertEquals ("int", aA.getSchemaType ().getAtomicBase ().localName ());
    assertNull (aA.getSchemaType ().getDefinition ());
    try
    {
      aParser.parse ("<a>x</a>");
      fail ();
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }
  }
}
