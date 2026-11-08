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
package com.helger.xslt.stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.pattern.XsltPattern;
import com.helger.xslt.tree.XslElement;

/**
 * The parsed expressions, patterns and sequence types of a stylesheet, keyed
 * by the element they are written on and their text. The compiled instructions
 * do not expose their operands, so the compiler records them here while it
 * compiles, and {@link XsltStreamability} reads them back while it walks the
 * stylesheet tree. The text of a text value template part is recorded on the
 * element containing the text node, exactly as the compiler compiles it.
 * <p>
 * The compiler fills an index only when streamability declarations are binding
 * ({@link com.helger.xslt.XsltCompileOptions#isEnforceStreamability()}); a
 * lookup that finds nothing makes the analysis fail closed.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class StreamabilityIndex
{
  // XslElement has identity semantics, so these maps are identity maps
  private final ICommonsMap <XslElement, ICommonsMap <String, IExpr>> m_aExpressions = new CommonsHashMap <> ();
  private final ICommonsMap <XslElement, ICommonsMap <String, XsltPattern>> m_aPatterns = new CommonsHashMap <> ();
  private final ICommonsMap <XslElement, ICommonsMap <String, SeqType>> m_aTypes = new CommonsHashMap <> ();

  /**
   * @param aElement
   *        The element the expression is written on.
   * @param sText
   *        The expression text as compiled.
   * @param aExpr
   *        The parsed expression.
   */
  public void addExpression (@NonNull final XslElement aElement,
                             @NonNull final String sText,
                             @NonNull final IExpr aExpr)
  {
    m_aExpressions.computeIfAbsent (aElement, k -> new CommonsHashMap <> ()).put (sText, aExpr);
  }

  /**
   * @param aElement
   *        The element the expression is written on.
   * @param sText
   *        The expression text.
   * @return The parsed expression or <code>null</code> if none was recorded.
   */
  @Nullable
  public IExpr getExpression (@NonNull final XslElement aElement, @NonNull final String sText)
  {
    final ICommonsMap <String, IExpr> aMap = m_aExpressions.get (aElement);
    return aMap == null ? null : aMap.get (sText);
  }

  /**
   * @param aElement
   *        An element.
   * @return All expressions recorded on that element (attributes, attribute
   *         value templates and the text value templates of its text
   *         children). Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IExpr> getAllExpressions (@NonNull final XslElement aElement)
  {
    final ICommonsMap <String, IExpr> aMap = m_aExpressions.get (aElement);
    return aMap == null ? new CommonsArrayList <> () : new CommonsArrayList <> (aMap.values ());
  }

  /**
   * @param aElement
   *        The element the pattern is written on.
   * @param sText
   *        The pattern text as compiled.
   * @param aPattern
   *        The parsed pattern.
   */
  public void addPattern (@NonNull final XslElement aElement,
                          @NonNull final String sText,
                          @NonNull final XsltPattern aPattern)
  {
    m_aPatterns.computeIfAbsent (aElement, k -> new CommonsHashMap <> ()).put (sText, aPattern);
  }

  /**
   * @param aElement
   *        The element the pattern is written on.
   * @param sText
   *        The pattern text.
   * @return The parsed pattern or <code>null</code> if none was recorded.
   */
  @Nullable
  public XsltPattern getPattern (@NonNull final XslElement aElement, @NonNull final String sText)
  {
    final ICommonsMap <String, XsltPattern> aMap = m_aPatterns.get (aElement);
    return aMap == null ? null : aMap.get (sText);
  }

  /**
   * @param aElement
   *        The element the sequence type is written on.
   * @param sText
   *        The sequence type text as compiled.
   * @param aType
   *        The parsed sequence type.
   */
  public void addSequenceType (@NonNull final XslElement aElement,
                               @NonNull final String sText,
                               @NonNull final SeqType aType)
  {
    m_aTypes.computeIfAbsent (aElement, k -> new CommonsHashMap <> ()).put (sText, aType);
  }

  /**
   * @param aElement
   *        The element the sequence type is written on.
   * @param sText
   *        The sequence type text.
   * @return The parsed sequence type or <code>null</code> if none was
   *         recorded.
   */
  @Nullable
  public SeqType getSequenceType (@NonNull final XslElement aElement, @NonNull final String sText)
  {
    final ICommonsMap <String, SeqType> aMap = m_aTypes.get (aElement);
    return aMap == null ? null : aMap.get (sText);
  }
}
