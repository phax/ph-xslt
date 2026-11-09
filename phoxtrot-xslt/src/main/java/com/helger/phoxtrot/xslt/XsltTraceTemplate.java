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

import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * An executed <code>xsl:template</code> as passed to an
 * {@link IXsltTraceListener}: a template rule invoked by
 * <code>xsl:apply-templates</code>, <code>xsl:next-match</code> or
 * <code>xsl:apply-imports</code>, or a named template invoked by
 * <code>xsl:call-template</code> or as initial template. Two values are equal
 * when all components are equal, so they can serve as keys for per-template
 * statistics.
 *
 * @param matchPattern
 *        For a template rule the source text of the match pattern alternative
 *        that matched: a union pattern without explicit priority gives one
 *        rule per alternative (XSLT 3.0 section 6.4), so this can be a part
 *        of the <code>match</code> attribute. <code>null</code> for a named
 *        template.
 * @param mode
 *        For a template rule the mode it was invoked in (the unnamed mode is
 *        <code>{http://www.w3.org/1999/XSL/Transform}unnamed</code>);
 *        <code>null</code> for a named template.
 * @param name
 *        The name of the template, <code>null</code> if it has none. A
 *        template rule with a name has it here too.
 * @param priority
 *        For a template rule its priority (explicit or default);
 *        {@link Double#NaN} for a named template.
 * @param moduleUri
 *        The URI of the stylesheet module of the <code>xsl:template</code>
 *        element, or <code>null</code> if unknown.
 * @param line
 *        The 1-based line of the <code>xsl:template</code> element, 0 if
 *        unknown.
 * @param column
 *        The 1-based column of the <code>xsl:template</code> element, 0 if
 *        unknown.
 * @author Philip Helger
 */
@Immutable
public record XsltTraceTemplate (@Nullable String matchPattern,
                                 @Nullable XdmQName mode,
                                 @Nullable XdmQName name,
                                 double priority,
                                 @Nullable String moduleUri,
                                 int line,
                                 int column)
{
  /**
   * @return <code>true</code> for a template rule (invoked by matching),
   *         <code>false</code> for a named template invoked by name.
   */
  public boolean isTemplateRule ()
  {
    return matchPattern != null;
  }
}
