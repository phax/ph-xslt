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
package com.helger.xslt.ext.transform;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.fn.FnTransform;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Test class for class {@link XsltTransformHandler}.
 *
 * @author Philip Helger
 */
public final class XsltTransformHandlerTest
{
  private static final String INNER = XsltTestHelper.SHEET +
                                      "<xsl:param name='p' select='0'/>" +
                                      "<xsl:template name='main'><out><xsl:value-of select='$p + 1'/></out></xsl:template>" +
                                      "<xsl:template match='/'><root><xsl:value-of select='count(//*)'/></root></xsl:template>" +
                                      "<xsl:template name='fn'><xsl:sequence select='upper-case#1'/></xsl:template>" +
                                      "<xsl:template name='split'><xsl:result-document href='sec.xml'><s/></xsl:result-document>" +
                                      "<p/></xsl:template>" +
                                      XsltTestHelper.END;

  @NonNull
  private static String _runXslt (@NonNull final String sBody)
  {
    final ICommonsMap <String, String> aParams = new CommonsHashMap <> ();
    aParams.put ("inner", INNER);
    return XsltTestHelper.run (XsltTestHelper.SHEET +
                               "<xsl:param name='inner'/><xsl:output method='text'/>" +
                               "<xsl:template name='xsl:initial-template'>" +
                               sBody +
                               "</xsl:template>" +
                               XsltTestHelper.END,
                               null,
                               aParams)
                         .principalSerialized ();
  }

  @NonNull
  private static XdmSequence _evalXPath (@NonNull final String sExpr)
  {
    final StaticContext aSC = new StaticContext ();
    final XdmQName aVar = new XdmQName ("inner");
    aSC.declareVariable (aVar);
    final CompiledXPath aXP = XPathCompiler.compile (sExpr, aSC);
    final DynamicContext aCtx = aXP.createDynamicContext ().withVariable (aVar, XdmSequence.of (XdmAtomic.string (INNER)));
    return aXP.evaluate (aCtx);
  }

  @NonNull
  private static String _xpathError (@NonNull final String sExpr)
  {
    try
    {
      _evalXPath (sExpr);
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
    throw new IllegalStateException ("No error");
  }

  @Test
  public void testAvailable ()
  {
    assertNotNull (FnTransform.getHandler ());
    assertNotNull (FunctionLibrary.getDefault ().getFunction (new XdmQName ("http://www.w3.org/2005/xpath-functions", "transform"), 1));
  }

  @Test
  public void testInStylesheet ()
  {
    assertEquals ("6",
                  _runXslt ("<xsl:value-of select=\"transform(map{'stylesheet-text': $inner, 'initial-template': QName('', 'main')," +
                            " 'stylesheet-params': map{QName('', 'p'): 5}})?output/out\"/>"));
    assertEquals ("3",
                  _runXslt ("<xsl:value-of select=\"transform(map{'stylesheet-text': $inner, 'source-node': parse-xml('&lt;a>&lt;b/>&lt;c/>&lt;/a>')})?output\"/>"));
    assertEquals ("ABC",
                  _runXslt ("<xsl:value-of select=\"transform(map{'stylesheet-text': $inner, 'initial-template': QName('', 'fn')," +
                            " 'delivery-format': 'raw'})?output('abc')\"/>"));
    assertEquals ("output sec.xml",
                  _runXslt ("<xsl:value-of select=\"sort(map:keys(transform(map{'stylesheet-text': $inner, 'initial-template': QName('', 'split')})))\"" +
                            " xmlns:map='http://www.w3.org/2005/xpath-functions/map'/>"));
  }

  @Test
  public void testStandalone ()
  {
    final XdmSequence aResult = _evalXPath ("transform(map{'stylesheet-text': $inner, 'initial-template': QName('', 'main')," +
                                            " 'delivery-format': 'serialized', 'serialization-params': map{'omit-xml-declaration': true()}})?output");
    assertEquals ("<out xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">1</out>", ((XdmAtomic) aResult.getFirst ()).getString ());
    final XdmSequence aPost = _evalXPath ("transform(map{'stylesheet-text': $inner, 'initial-template': QName('', 'main')," +
                                          " 'post-process': function ($k, $v) { $k || '=' || $v }})?output");
    assertEquals ("output=1", ((XdmAtomic) aPost.getFirst ()).getString ());
  }

  @Test
  public void testErrors ()
  {
    assertEquals ("FOXT0002", _xpathError ("transform(map{'stylesheet-text': $inner, 'stylesheet-location': 'x.xsl'})"));
    assertEquals ("FOXT0002", _xpathError ("transform(map{'initial-template': QName('', 'main')})"));
    assertEquals ("FOXT0002", _xpathError ("transform(map{'stylesheet-text': $inner, 'delivery-format': 'bogus'})"));
    assertEquals ("FOXT0002", _xpathError ("transform(map{'stylesheet-location': 'file:///does/not/exist.xsl'})"));
    assertEquals ("XPTY0004", _xpathError ("transform(map{'stylesheet-text': $inner, 'xslt-version': '3.0'})"));
    assertEquals ("FOXT0001",
                  _xpathError ("transform(map{'stylesheet-text': $inner, 'initial-template': QName('', 'main')," +
                               " 'requested-properties': map{QName('http://www.w3.org/1999/XSL/Transform', 'supports-namespace-axis'): false()}})"));
    // The stylesheet's own errors are propagated
    assertEquals ("XTDE0040", _xpathError ("transform(map{'stylesheet-text': $inner, 'initial-template': QName('', 'nope')})"));
  }

  @Test
  public void testPackageName ()
  {
    final String sPackage = "<xsl:package name='http://example.com/pkg' package-version='1.2.3' version='3.0'" +
                            " xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
                            "<xsl:template name='main' visibility='public'><v>1.2.3</v></xsl:template>" +
                            "</xsl:package>";
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    aOptions.addPackage (new XsltCompileOptions.PackageSource (sPackage, null, null));
    final String sSheet = XsltTestHelper.SHEET +
                          "<xsl:output method='text'/>" +
                          "<xsl:template name='xsl:initial-template'>" +
                          "<xsl:value-of select=\"transform(map{'package-name': 'http://example.com/pkg', 'package-version': '1.*'," +
                          " 'initial-template': QName('', 'main')})?output\"/>" +
                          "</xsl:template>" +
                          XsltTestHelper.END;
    assertEquals ("1.2.3", XsltCompiler.compile (sSheet, aOptions).transform (new XsltTransformRequest ()).principalSerialized ());
  }

  @Test
  public void testSharedBudget ()
  {
    // A stylesheet that starts itself again and again stops with XPDY0130
    final String sSelf = XsltTestHelper.SHEET +
                         "<xsl:param name='self'/>" +
                         "<xsl:template name='xsl:initial-template'>" +
                         "<xsl:sequence select=\"transform(map{'stylesheet-text': $self, 'stylesheet-params': map{QName('', 'self'): $self}})?output\"/>" +
                         "</xsl:template>" +
                         XsltTestHelper.END;
    final ICommonsMap <String, String> aParams = new CommonsHashMap <> ();
    aParams.put ("self", sSelf);
    try
    {
      XsltTestHelper.run (sSelf, null, aParams);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPDY0130", ex.getCode ());
    }
  }
}
