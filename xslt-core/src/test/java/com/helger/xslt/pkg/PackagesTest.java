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
package com.helger.xslt.pkg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.helger.xpath.XPathException;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;

/**
 * End-to-end tests for xsl:use-package, xsl:override and xsl:accept.
 *
 * @author Philip Helger
 */
public final class PackagesTest
{
  private static final String LIB = "<xsl:package name='urn:lib' package-version='1.2' version='3.0'" +
                                    " xmlns:xsl='http://www.w3.org/1999/XSL/Transform' xmlns:f='urn:f'>" +
                                    "<xsl:function name='f:pub' visibility='public'><xsl:param name='a'/>" +
                                    "<xsl:sequence select=\"'lib:' || f:priv($a)\"/></xsl:function>" +
                                    "<xsl:function name='f:priv'><xsl:param name='a'/><xsl:sequence select='$a'/></xsl:function>" +
                                    "<xsl:variable name='v' select=\"'lib-v'\" visibility='public'/>" +
                                    "<xsl:template name='t' visibility='public'><xsl:value-of select='$v'/></xsl:template>" +
                                    "</xsl:package>";

  private static String _principal (final String sUse, final String sBody)
  {
    return "<xsl:package version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform' xmlns:f='urn:f'>" +
           "<xsl:output method='text'/>" +
           sUse +
           "<xsl:template name='xsl:initial-template' visibility='public'>" +
           sBody +
           "</xsl:template></xsl:package>";
  }

  private static XsltTransformResult _run (final String sPrincipal)
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ().addPackage (new XsltCompileOptions.PackageSource (LIB,
                                                                                                                  null,
                                                                                                                  null));
    final XsltExecutable aExec = XsltCompiler.compile (sPrincipal, aOptions);
    return aExec.transform (new XsltTransformRequest ());
  }

  private static String _errorCode (final String sPrincipal)
  {
    try
    {
      _run (sPrincipal);
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
    fail ("No error");
    return null;
  }

  @Test
  public void testUsePackage ()
  {
    assertEquals ("lib:x lib-v",
                  _run (_principal ("<xsl:use-package name='urn:lib' package-version='1.0 to 2.0'/>",
                                    "<xsl:value-of select=\"f:pub('x')\"/><xsl:text> </xsl:text><xsl:call-template name='t'/>")).principalSerialized ());
  }

  @Test
  public void testOverride ()
  {
    assertEquals ("[lib:x] mine",
                  _run (_principal ("<xsl:use-package name='urn:lib'><xsl:override>" +
                                    "<xsl:function name='f:pub' visibility='public'><xsl:param name='a'/>" +
                                    "<xsl:sequence select=\"'[' || xsl:original($a) || ']'\"/></xsl:function>" +
                                    "<xsl:variable name='v' select=\"'mine'\" visibility='public'/>" +
                                    "</xsl:override></xsl:use-package>",
                                    "<xsl:value-of select=\"f:pub('x')\"/><xsl:text> </xsl:text><xsl:call-template name='t'/>")).principalSerialized ());
  }

  @Test
  public void testErrors ()
  {
    // A private function of the used package is not visible
    assertEquals ("XPST0017", _errorCode (_principal ("<xsl:use-package name='urn:lib'/>", "<xsl:value-of select=\"f:priv('x')\"/>")));
    // Hidden by xsl:accept
    assertEquals ("XTSE0650",
                  _errorCode (_principal ("<xsl:use-package name='urn:lib'><xsl:accept component='template' names='t' visibility='hidden'/></xsl:use-package>",
                                          "<xsl:call-template name='t'/>")));
    // No matching version
    assertEquals ("XTSE3000", _errorCode (_principal ("<xsl:use-package name='urn:lib' package-version='2.0+'/>", "")));
    // Overriding something that does not exist
    assertEquals ("XTSE3058",
                  _errorCode (_principal ("<xsl:use-package name='urn:lib'><xsl:override><xsl:variable name='w' visibility='public'/></xsl:override></xsl:use-package>",
                                          "")));
  }
}
