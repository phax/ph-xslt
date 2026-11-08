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
package com.helger.phoxtrot.xslt;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsHashMap;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xpath.context.IResourceResolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests of xsl:include, xsl:import and import precedence.
 *
 * @author Philip Helger
 */
public final class XsltModulesTest
{
  private static final class MapResolver implements IResourceResolver
  {
    private final Map <String, String> m_aDocs;

    MapResolver (@NonNull final Map <String, String> aDocs)
    {
      m_aDocs = aDocs;
    }

    @Nullable
    public IXdmNode resolveDocument (@NonNull final String sAbsoluteUri)
    {
      final String s = m_aDocs.get (sAbsoluteUri);
      if (s == null)
        return null;
      final IXdmNode ret = XsltTestHelper.parse (s);
      ret.setDocumentUri (sAbsoluteUri);
      return ret;
    }

    @Nullable
    public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
    {
      return m_aDocs.get (sAbsoluteUri);
    }
  }

  private static String _run (final String sMain, final Map <String, String> aModules, final String sSource)
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ().setBaseUri ("http://example.org/main.xsl")
                                                                 .setResolver (new MapResolver (aModules));
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    if (sSource != null)
      aRequest.setGlobalContextItem (XsltTestHelper.parse (sSource));
    return XsltCompiler.compile (sMain, aOptions).transform (aRequest).principalSerialized ();
  }

  @Test
  public void testImportPrecedenceAndApplyImports ()
  {
    final String sImported = XsltTestHelper.SHEET +
                             "<xsl:output method='text'/>" +
                             "<xsl:template match='a'>imported</xsl:template>" +
                             "<xsl:variable name='v' select='\"low\"'/>" +
                             XsltTestHelper.END;
    final String sMain = XsltTestHelper.SHEET +
                         "<xsl:import href='lib/imported.xsl'/>" +
                         "<xsl:template match='a'>main(<xsl:apply-imports/>)<xsl:value-of select='$v'/></xsl:template>" +
                         "<xsl:variable name='v' select='\"high\"'/>" +
                         XsltTestHelper.END;
    assertEquals ("main(imported)high", _run (sMain, Map.of ("http://example.org/lib/imported.xsl", sImported), "<a/>"));
  }

  @Test
  public void testIncludeAndCycle ()
  {
    final String sIncluded = XsltTestHelper.SHEET + "<xsl:template name='t'>inc</xsl:template>" + XsltTestHelper.END;
    final String sMain = XsltTestHelper.SHEET +
                         "<xsl:output method='text'/><xsl:include href='inc.xsl'/>" +
                         "<xsl:template name='xsl:initial-template'><xsl:call-template name='t'/></xsl:template>" +
                         XsltTestHelper.END;
    assertEquals ("inc", _run (sMain, Map.of ("http://example.org/inc.xsl", sIncluded), null));

    final String sSelf = XsltTestHelper.SHEET + "<xsl:include href='self.xsl'/>" + XsltTestHelper.END;
    try
    {
      _run (sSelf, Map.of ("http://example.org/self.xsl", sSelf), null);
      throw new IllegalStateException ("no error");
    }
    catch (final XsltException ex)
    {
      assertEquals ("XTSE0180", ex.getCode ());
    }
  }

  @Nullable
  private static String _compileError (final String sMain, final Map <String, String> aModules)
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ().setBaseUri ("http://example.org/main.xsl")
                                                                 .setResolver (new MapResolver (aModules));
    try
    {
      XsltCompiler.compile (sMain, aOptions);
      return null;
    }
    catch (final XsltException ex)
    {
      return ex.getCode ();
    }
  }

  @Test
  public void testDuplicatesOverriddenByHigherPrecedence ()
  {
    // Two declarations with the same name at the same import precedence are no
    // error if a declaration with a higher import precedence exists
    final String sImported = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                             " xmlns:f='urn:f' xmlns:a='urn:a' xmlns:b='urn:b' xmlns:c='urn:c'>" +
                             "<xsl:variable name='v' select='1'/><xsl:variable name='v' select='2'/>" +
                             "<xsl:function name='f:a'><xsl:sequence select='1'/></xsl:function>" +
                             "<xsl:function name='f:a'><xsl:sequence select='2'/></xsl:function>" +
                             "<xsl:template name='t'>1</xsl:template><xsl:template name='t'>2</xsl:template>" +
                             "<xsl:namespace-alias stylesheet-prefix='a' result-prefix='b'/>" +
                             "<xsl:namespace-alias stylesheet-prefix='a' result-prefix='c'/>" +
                             XsltTestHelper.END;
    final Map <String, String> aModules = Map.of ("http://example.org/imp.xsl", sImported);
    final String sMain = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                         " xmlns:f='urn:f' xmlns:a='urn:a' xmlns:c='urn:c' exclude-result-prefixes='f'>" +
                         "<xsl:import href='imp.xsl'/><xsl:output method='xml' omit-xml-declaration='yes'/>" +
                         "<xsl:variable name='v' select='3'/>" +
                         "<xsl:function name='f:a'><xsl:sequence select='3'/></xsl:function>" +
                         "<xsl:template name='t'>3</xsl:template>" +
                         "<xsl:namespace-alias stylesheet-prefix='a' result-prefix='c'/>" +
                         "<xsl:template name='xsl:initial-template'><a:x v='{$v}{f:a()}'><xsl:call-template name='t'/></a:x></xsl:template>" +
                         XsltTestHelper.END;
    assertEquals ("<c:x xmlns:c=\"urn:c\" v=\"33\">3</c:x>", _run (sMain, aModules, null));

    // Without the overriding declaration each pair is an error
    final String sHead = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                         " xmlns:f='urn:f' xmlns:a='urn:a' xmlns:b='urn:b' xmlns:c='urn:c'><xsl:import href='imp.xsl'/>";
    final Map <String, String> aEmpty = Map.of ("http://example.org/imp.xsl", XsltTestHelper.SHEET + XsltTestHelper.END);
    final String sStart = "<xsl:template name='xsl:initial-template'/>";
    assertEquals ("XTSE0630",
                  _compileError (sHead + "<xsl:variable name='v' select='1'/><xsl:variable name='v' select='2'/>" + sStart + XsltTestHelper.END,
                                 aEmpty));
    assertEquals ("XTSE0770",
                  _compileError (sHead +
                                 "<xsl:function name='f:a'><xsl:sequence select='1'/></xsl:function>" +
                                 "<xsl:function name='f:a'><xsl:sequence select='2'/></xsl:function>" +
                                 sStart +
                                 XsltTestHelper.END,
                                 aEmpty));
    assertEquals ("XTSE0660",
                  _compileError (sHead + "<xsl:template name='t'>1</xsl:template><xsl:template name='t'>2</xsl:template>" + sStart + XsltTestHelper.END,
                                 aEmpty));
    assertEquals ("XTSE0810",
                  _compileError (sHead +
                                 "<xsl:namespace-alias stylesheet-prefix='a' result-prefix='b'/>" +
                                 "<xsl:namespace-alias stylesheet-prefix='a' result-prefix='c'/>" +
                                 sStart +
                                 XsltTestHelper.END,
                                 aEmpty));
    // The duplicates of the imported module alone, with nothing overriding them
    assertNotNull (_compileError (sHead + sStart + XsltTestHelper.END, aModules));
  }

  @Test
  public void testOutputConflictOverriddenByHigherPrecedence ()
  {
    final String sImported = XsltTestHelper.SHEET +
                             "<xsl:output indent='yes'/><xsl:output indent='no'/>" +
                             XsltTestHelper.END;
    final String sHead = XsltTestHelper.SHEET + "<xsl:import href='imp.xsl'/>";
    final String sStart = "<xsl:template name='xsl:initial-template'><a><b/></a></xsl:template>" + XsltTestHelper.END;
    final Map <String, String> aModules = Map.of ("http://example.org/imp.xsl", sImported);
    // The higher precedence declaration resolves the conflict
    assertTrue (_run (sHead + "<xsl:output omit-xml-declaration='yes' indent='yes'/>" + sStart, aModules, null).contains ("\n  <b/>"));
    // Without it, it remains
    assertEquals ("XTSE1560", _compileError (sHead + "<xsl:output omit-xml-declaration='yes'/>" + sStart, aModules));
  }

  @Test
  public void testIncludeNestingLimit ()
  {
    // A chain of includes deeper than StylesheetLoader.MAX_DEPTH
    final Map <String, String> aModules = new CommonsHashMap <> ();
    for (int i = 0; i < 1100; ++i)
      aModules.put ("http://example.org/m" + i + ".xsl", XsltTestHelper.SHEET + "<xsl:include href='m" + (i + 1) + ".xsl'/>" + XsltTestHelper.END);
    aModules.put ("http://example.org/m1100.xsl", XsltTestHelper.SHEET + XsltTestHelper.END);
    assertEquals ("XPDY0130", _compileError (XsltTestHelper.SHEET + "<xsl:include href='m0.xsl'/>" + XsltTestHelper.END, aModules));
  }
}
