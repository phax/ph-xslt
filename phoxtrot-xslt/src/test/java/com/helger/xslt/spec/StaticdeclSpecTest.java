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

import static com.helger.xslt.spec.UsewhenSpecTest.err;
import static com.helger.xslt.spec.UsewhenSpecTest.ok;
import static com.helger.xslt.spec.UsewhenSpecTest.statics;
import static com.helger.xslt.spec.UsewhenSpecTest.swBRunAll;

import java.net.URISyntaxException;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

/**
 * Port of go-xslt's <code>internal/xslt/staticdecl_test.go</code>: static
 * variables and parameters across stylesheet modules (XSLT 3.0 §9.6):
 * stylesheet tree order (xsl:include/xsl:import replaced in place by the
 * module's declarations), import precedence, and XTSE3450. The modules live in
 * <code>src/test/resources/spec/usewhen</code>.
 *
 * @author Philip Helger
 */
public final class StaticdeclSpecTest
{
  /**
   * @return The location the principal module is compiled "from", so that
   *         relative hrefs resolve into the usewhen test data.
   */
  @NonNull
  private static String _main ()
  {
    try
    {
      return StaticdeclSpecTest.class.getResource ("/spec/usewhen/inc_static.xsl").toURI ().resolve ("main.xsl").toString ();
    }
    catch (final URISyntaxException ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  @Test
  public void staticTreeOrderAcrossModules ()
  {
    swBRunAll (_main (),
               // The included module's static variable precedes, in tree order,
               // whatever follows the xsl:include.
               ok ("included static visible after include", """
                   <xsl:include href="inc_static.xsl"/>
                   <xsl:template match="/"><xsl:text use-when="$inc-v = 'from-inc'">seen:</xsl:text><xsl:value-of select="$inc-v"/></xsl:template>""",
                   "seen:from-inc"),
               // §3.13.1: use-when false on a secondary module's root excludes
               // all its children; its "/" template must not fire, so the
               // built-in rules produce the (empty) text of the document.
               ok ("imported module excluded by root use-when", "<xsl:import href=\"imp_excluded.xsl\"/>", ""),
               // Same name, same kind, identical values: consistent; the
               // principal (higher precedence) value is used.
               ok ("consistent redeclaration", """
                   <xsl:import href="imp_v1.xsl"/>
                   <xsl:variable name="v" static="yes" select="1"/>
                   <xsl:template match="/"><xsl:value-of select="$v"/></xsl:template>""", "1"),
               // Both declarations are static PARAMETERS with an externally
               // supplied value: consistent, and the supplied value is used.
               ok ("externally supplied param in two modules", """
                   <xsl:import href="imp_pv1.xsl"/>
                   <xsl:param name="v" static="yes" select="1"/>
                   <xsl:template match="/"><xsl:value-of select="$v"/></xsl:template>""", statics ("v", "5"), "5"),
               // A shadow use-when on an included module's root, from a static
               // parameter declared before the include: true keeps the module.
               ok ("shadow root use-when true", """
                   <xsl:param name="INC" static="yes" select="'true()'"/>
                   <xsl:include href="inc_shadow_usewhen.xsl"/>
                   <xsl:template match="/" priority="-5">PRINCIPAL</xsl:template>""", "INCLUDED"),
               // ... and false excludes all of its children.
               ok ("shadow root use-when false", """
                   <xsl:param name="INC" static="yes" select="'false()'"/>
                   <xsl:include href="inc_shadow_usewhen.xsl"/>
                   <xsl:template match="/" priority="-5">PRINCIPAL</xsl:template>""", "PRINCIPAL"),
               // The §3.13.2 example: <xsl:include _href="common{$VERSION}.xsl"/>.
               ok ("shadow href picks the module", """
                   <xsl:param name="which" static="yes" select="'a'"/>
                   <xsl:include _href="sel_{$which}.xsl"/>""", "A"),
               ok ("shadow href with supplied param", """
                   <xsl:param name="which" static="yes" select="'a'"/>
                   <xsl:include _href="sel_{$which}.xsl"/>""", statics ("which", "'b'"), "B"),
               // An excluded xsl:include is not processed at all, so a missing
               // target is no error.
               ok ("excluded include of missing module", """
                   <xsl:include href="no-such-module.xsl" use-when="false()"/>
                   <xsl:template match="/">ok</xsl:template>""", "ok"));
  }

  @Test
  public void staticAcrossModulesErrors ()
  {
    swBRunAll (_main (),
               // §9.6 [ERR XTSE3450]: the higher-precedence declaration comes
               // later in tree order and its value differs.
               err ("different value", """
                   <xsl:import href="imp_v1.xsl"/>
                   <xsl:variable name="v" static="yes" select="2"/>
                   <xsl:template match="/">x</xsl:template>""", "XTSE3450"),
               // Two sibling imports: the second has the higher precedence and
               // follows the first in tree order.
               err ("sibling imports differ", """
                   <xsl:import href="imp_v1.xsl"/>
                   <xsl:import href="imp_v2.xsl"/>
                   <xsl:template match="/">x</xsl:template>""", "XTSE3450"),
               // Consistency also requires the same kind: xsl:variable vs
               // xsl:param.
               err ("variable vs param", """
                   <xsl:import href="imp_v1.xsl"/>
                   <xsl:param name="v" static="yes" select="1"/>
                   <xsl:template match="/">x</xsl:template>""", "XTSE3450"),
               // An excluded module contributes no static variables.
               err ("static of excluded module", """
                   <xsl:import href="imp_excluded.xsl"/>
                   <xsl:template match="/" use-when="$excl">x</xsl:template>""", "XPST0008"),
               // [ERR XTSE0165]: the resource named by xsl:include cannot be
               // retrieved.
               err ("missing module", """
                   <xsl:include href="no-such-module.xsl"/>
                   <xsl:template match="/">x</xsl:template>""", "XTSE0165"));
  }

  /**
   * The per-declaration rules of §9.6 in a stylesheet that has secondary
   * modules.
   */
  @Test
  public void staticDeclarationsInMultiModule ()
  {
    swBRunAll (_main (),
               // A declaration's own use-when: false excludes it.
               ok ("use-when on declarations", """
                   <xsl:include href="inc_static.xsl"/>
                   <my:data>user-defined data element, ignored</my:data>
                   <xsl:variable name="w" static="yes" select="1" use-when="false()"/>
                   <xsl:variable name="w" static="yes" select="2" use-when="$inc-v = 'from-inc'"/>
                   <xsl:template match="/"><xsl:value-of select="$w"/></xsl:template>""", "2"),
               // No select, empty content: the zero-length string (§9.3).
               ok ("param without select", """
                   <xsl:include href="inc_static.xsl"/>
                   <xsl:param name="e" static="yes"/>
                   <xsl:template match="/"><xsl:value-of _select="'[{$e}]'"/></xsl:template>""", "[]"),
               ok ("supplied value", """
                   <xsl:include href="inc_static.xsl"/>
                   <xsl:param name="e" static="yes" select="'d'"/>
                   <xsl:template match="/"><xsl:value-of select="$e"/></xsl:template>""", statics ("e", "'h'"), "h"));

    // §9.7: a dynamic error in any static expression is a static error that
    // keeps its code - wherever in the module graph it occurs. (go-xslt's
    // "supplied value" case with the host expression '1 idiv 0' is specific to
    // its expression-valued host API and not ported.)
    swBRunAll (_main (),
               err ("use-when on declaration", """
                   <xsl:include href="inc_static.xsl"/>
                   <xsl:variable name="w" static="yes" select="1" use-when="1 idiv 0"/>""", "FOAR0001"),
               err ("select", """
                   <xsl:include href="inc_static.xsl"/>
                   <xsl:variable name="w" static="yes" select="1 idiv 0"/>""", "FOAR0001"),
               err ("select in imported module", "<xsl:import href=\"imp_badselect.xsl\"/>", "FOAR0001"),
               err ("select two includes deep", "<xsl:include href=\"inc_nested.xsl\"/>", "FOAR0001"),
               err ("root use-when of imported module", "<xsl:import href=\"imp_badroot.xsl\"/>", "FOAR0001"),
               err ("shadow on root of included module", "<xsl:include href=\"inc_badshadow.xsl\"/>", "FOAR0001"),
               err ("shadow on declaration of included module", "<xsl:include href=\"inc_badchild.xsl\"/>", "FOAR0001"),
               err ("shadow on principal declaration", """
                   <xsl:include href="inc_static.xsl"/>
                   <xsl:template match="/" _mode="{1 idiv 0}">x</xsl:template>""", "FOAR0001"));
  }
}
