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
package com.helger.xslt.compile;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xslt.instr.SimpleContent;
import com.helger.xslt.runtime.XsltContext;

/**
 * A compiled attribute value template or text value template: fixed text and XPath expressions. The
 * value of each expression is atomized and its items joined with a single space (XSLT 3.0 section
 * 5.6.1).
 *
 * @author Philip Helger
 */
@Immutable
public final class Avt
{
  private final Object [] m_aParts;
  private final boolean m_bTextValueTemplate;

  /**
   * @param aParts
   *        The parts: {@link String} for fixed text, {@link XPathExpr} for expressions.
   * @param bTextValueTemplate
   *        <code>true</code> for a text value template. Only recorded: attribute and text value
   *        templates both convert expression values by the rules for simple content (XSLT 3.0
   *        sections 5.6 and 5.7.2: adjacent text nodes are merged before the items are joined with
   *        spaces).
   */
  public Avt (@NonNull final List <Object> aParts, final boolean bTextValueTemplate)
  {
    m_aParts = aParts.toArray ();
    m_bTextValueTemplate = bTextValueTemplate;
  }

  /**
   * Evaluate one expression of the template and turn its value into a
   * string by the rules for simple content: zero-length text nodes dropped,
   * adjacent text nodes merged, the items atomised and joined with single
   * spaces.
   *
   * @param aExpr
   *        The expression part.
   * @param aCtx
   *        Run-time context.
   * @return The string value of the part, "" for an empty sequence.
   */
  @NonNull
  private String _evaluatePart (@NonNull final XPathExpr aExpr, @NonNull final XsltContext aCtx)
  {
    // XSLT 3.0 sections 5.6.1/5.6.2: zero-length text nodes are dropped and
    // adjacent text nodes merged before the items are joined with spaces
    return SimpleContent.join (aExpr.evaluate (aCtx), " ");
  }

  /** @return <code>true</code> if the template contains no expression. */
  public boolean isConstant ()
  {
    for (final Object aPart : m_aParts)
      if (aPart instanceof XPathExpr)
        return false;
    return true;
  }

  /** @return The fixed value if {@link #isConstant()}, else <code>null</code>. */
  @Nullable
  public String getConstantValue ()
  {
    if (!isConstant ())
      return null;

    final StringBuilder aSB = new StringBuilder ();
    for (final Object aPart : m_aParts)
      aSB.append ((String) aPart);
    return aSB.toString ();
  }

  /**
   * Evaluate the template: the fixed text and the string values of the
   * expressions (see {@link #_evaluatePart(XPathExpr, XsltContext)})
   * concatenated in order, without separators between the parts.
   *
   * @param aCtx
   *        Run-time context.
   * @return The value.
   */
  @NonNull
  public String evaluate (@NonNull final XsltContext aCtx)
  {
    if (m_aParts.length == 1)
      return m_aParts[0] instanceof final XPathExpr aExpr ? _evaluatePart (aExpr, aCtx) : (String) m_aParts[0];

    final StringBuilder aSB = new StringBuilder ();
    for (final Object aPart : m_aParts)
      if (aPart instanceof final XPathExpr aExpr)
        aSB.append (_evaluatePart (aExpr, aCtx));
      else
        aSB.append ((String) aPart);
    return aSB.toString ();
  }
}
