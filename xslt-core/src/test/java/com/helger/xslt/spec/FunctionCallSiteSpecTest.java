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
package com.helger.xslt.spec;

import static com.helger.xslt.spec.UsePackageSpecTest.pkAMain;
import static com.helger.xslt.spec.UsePackageSpecTest.pkAPkg;
import static com.helger.xslt.spec.UsePackageSpecTest.pkAWant;
import static com.helger.xslt.spec.UsePackageSpecTest.pkgs;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Static function calls that are evaluated many times resolve through the
 * cache of their call site (<code>com.helger.xpath.fn.FunctionCallSite</code>);
 * with packages, overrides and xsl:original the results must stay those of a
 * fresh lookup in the function scope of each package (XSLT 3.0 sections 3.5.3.3
 * and 3.5.3.4).
 *
 * @author Philip Helger
 */
public final class FunctionCallSiteSpecTest
{
  private static final String LIB = pkAPkg ("urn:pkA:lib", "", """
      <xsl:function name="l:f" visibility="public" as="xs:string">
        <xsl:param name="x" as="xs:string"/>
        <xsl:sequence select="concat('lib:', $x)"/>
      </xsl:function>
      <xsl:function name="l:many" visibility="public" as="xs:string">
        <xsl:sequence select="string-join(for $i in 1 to 3 return l:f(string($i)), ',')"/>
      </xsl:function>
      <xsl:function name="l:lookup" visibility="public" as="xs:string">
        <xsl:sequence select="string-join(for $i in 1 to 2 return function-lookup(xs:QName('l:f'), 1)(string($i)), ',')"/>
      </xsl:function>""");

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  /** Without override every call reaches the function of the package. */
  @Test
  public void testRepeatedCallsWithoutOverride ()
  {
    pkAWant (m_aTempDir, "no override", pkAMain ("""
        <xsl:use-package name="urn:pkA:lib"/>
        <xsl:template match="/">
          <xsl:value-of select="(for $j in 1 to 2 return l:many()), l:f('m'), l:lookup()" separator="|"/>
        </xsl:template>"""), pkgs (LIB), "lib:1,lib:2,lib:3|lib:1,lib:2,lib:3|lib:m|lib:1,lib:2");
  }

  /**
   * With an override, the repeated calls inside the used package reach the
   * override, which reaches the package's function through xsl:original;
   * fn:function-lookup in the used package still finds the package's own
   * function. The same call written in the override and in the principal
   * package resolves per scope.
   */
  @Test
  public void testRepeatedCallsWithOverride ()
  {
    pkAWant (m_aTempDir, "override", pkAMain ("""
        <xsl:use-package name="urn:pkA:lib">
          <xsl:override>
            <xsl:function name="l:f" visibility="public" as="xs:string">
              <xsl:param name="x" as="xs:string"/>
              <xsl:sequence select="concat('ovr(', xsl:original($x), ')')"/>
            </xsl:function>
          </xsl:override>
        </xsl:use-package>
        <xsl:template match="/">
          <xsl:value-of select="(for $j in 1 to 2 return l:many()), (for $j in 1 to 2 return l:f('m')), l:lookup()" separator="|"/>
        </xsl:template>"""),
             pkgs (LIB),
             "ovr(lib:1),ovr(lib:2),ovr(lib:3)|ovr(lib:1),ovr(lib:2),ovr(lib:3)|ovr(lib:m)|ovr(lib:m)|lib:1,lib:2");
  }
}
