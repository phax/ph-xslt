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
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.schema.XdmSchemaSupport;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;

/**
 * Test class for schema-awareness (xsl:import-schema, validation of
 * constructed nodes, schema type tests). Needs Apache Xerces-J on the test
 * class path.
 *
 * @author Philip Helger
 */
public final class SchemaAwareTest
{
  private static final String SHEET = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                                      " xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:my='urn:my'>" +
                                      "<xsl:output method='text'/>";
  private static final String SCHEMA = "<xsl:import-schema><xs:schema targetNamespace='urn:my' elementFormDefault='qualified'>" +
                                       "<xs:element name='order' type='my:orderType'/>" +
                                       "<xs:element name='special' substitutionGroup='my:order'/>" +
                                       "<xs:complexType name='orderType'><xs:sequence>" +
                                       "<xs:element name='qty' type='my:size' maxOccurs='5'/>" +
                                       "</xs:sequence><xs:attribute name='sizes' type='my:sizes'/></xs:complexType>" +
                                       "<xs:simpleType name='size'><xs:restriction base='xs:positiveInteger'>" +
                                       "<xs:maxInclusive value='20'/></xs:restriction></xs:simpleType>" +
                                       "<xs:simpleType name='sizes'><xs:list itemType='my:size'/></xs:simpleType>" +
                                       "</xs:schema></xsl:import-schema>";

  @NonNull
  private static String _run (@NonNull final String sBody, final boolean bSchemaAware)
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ().setSchemaAware (bSchemaAware);
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    return XsltCompiler.compile (SHEET + sBody + XsltTestHelper.END, aOptions).transform (aRequest).principalSerialized ();
  }

  @NonNull
  private static String _template (@NonNull final String sContent)
  {
    return "<xsl:template name='xsl:initial-template'>" + sContent + "</xsl:template>";
  }

  @NonNull
  private static String _errorCode (@NonNull final String sBody)
  {
    try
    {
      _run (sBody, true);
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
    throw new IllegalStateException ("No error");
  }

  @Test
  public void testClaim ()
  {
    assertTrue (XdmSchemaSupport.isAvailable ());
    final String sBody = _template ("<xsl:value-of select=\"system-property('xsl:is-schema-aware'), element-available('xsl:import-schema')\"/>");
    assertEquals ("yes true", _run (sBody, true));
    assertEquals ("no false", _run (sBody, false));
    // A basic processor rejects xsl:import-schema
    try
    {
      _run (SCHEMA + _template ("x"), false);
    }
    catch (final XPathException ex)
    {
      assertEquals ("XTSE1650", ex.getCode ());
    }
  }

  @Test
  public void testConstructedNodes ()
  {
    final String sOrder = "<xsl:variable name='o' as='element()'><my:order xsl:validation='strict' sizes='3 4'>" +
                          "<my:qty>7</my:qty></my:order></xsl:variable>";
    assertEquals ("true true true 2 true",
                  _run (SCHEMA +
                        _template (sOrder +
                                   "<xsl:value-of select=\"$o instance of element(*, my:orderType), " +
                                   "$o instance of schema-element(my:order), " +
                                   "data($o/my:qty) instance of my:size, " +
                                   "count(data($o/@sizes)), " +
                                   "type-available('my:size')\"/>"),
                        true));
    // Strict validation of an undeclared element, and of an invalid one
    assertEquals ("XTTE1512", _errorCode (SCHEMA + _template ("<foo xsl:validation='strict'/>")));
    assertEquals ("XTTE1510", _errorCode (SCHEMA + _template ("<my:order xsl:validation='strict'><my:qty>21</my:qty></my:order>")));
    // A type on xsl:attribute
    assertEquals ("XTTE1540", _errorCode (SCHEMA + _template ("<e><xsl:attribute name='a' type='xs:integer' select=\"'abc'\"/></e>")));
    // An unknown type
    assertEquals ("XTSE1520", _errorCode (SCHEMA + _template ("<e xsl:type='my:unknown'/>")));
    // The default strips annotations of copies
    assertEquals ("false true",
                  _run (SCHEMA +
                        _template (sOrder +
                                   "<xsl:variable name='c' as='element()'><w><xsl:copy-of select='$o'/></w></xsl:variable>" +
                                   "<xsl:variable name='p' as='element()'><w xsl:validation='preserve'><xsl:copy-of select='$o' validation='preserve'/></w></xsl:variable>" +
                                   "<xsl:value-of select=\"$c/my:order instance of element(*, my:orderType), " +
                                   "$p/my:order instance of element(*, my:orderType)\"/>"),
                        true));
  }

  @Test
  public void testCasts ()
  {
    assertEquals ("true false 2",
                  _run (SCHEMA +
                        _template ("<xsl:value-of select=\"my:size('7') instance of my:size, " +
                                   "'21' castable as my:size, " +
                                   "count(my:sizes('1 2'))\"/>"),
                        true));
  }
}
