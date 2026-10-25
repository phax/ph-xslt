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
package com.helger.xslt;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.xdm.XdmNode;

import static org.junit.Assert.assertEquals;

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
    public XdmNode resolveDocument (@NonNull final String sAbsoluteUri)
    {
      final String s = m_aDocs.get (sAbsoluteUri);
      if (s == null)
        return null;
      final XdmNode ret = XsltTestHelper.parse (s);
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
}
