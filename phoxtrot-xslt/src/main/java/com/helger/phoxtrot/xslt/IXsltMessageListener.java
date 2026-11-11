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

import org.jspecify.annotations.NonNull;

/**
 * Receives the messages of one transformation while it runs (see
 * {@link XsltTransformRequest#setMessageListener(IXsltMessageListener)}).
 * <p>
 * The methods are called synchronously, in the order the messages are
 * produced, on the thread that runs the transformation: normally the
 * large-stack worker thread of
 * {@link com.helger.phoxtrot.xpath.LargeStackRunner}, not the thread
 * that called <code>transform</code>. A listener is used by one
 * transformation at a time unless the implementation is thread-safe. A
 * runtime exception thrown by a method ends the transformation and reaches
 * the caller of <code>transform</code>.
 *
 * @author Philip Helger
 */
public interface IXsltMessageListener
{
  /**
   * Called for every executed <code>xsl:message</code> and every failed
   * <code>xsl:assert</code>, before a terminating one ends the
   * transformation. A message inside an <code>xsl:try</code> whose error is
   * caught, or inside output that is rolled back, has been reported already.
   *
   * @param aMessage
   *        The message. Never <code>null</code>.
   */
  void onMessage (@NonNull XsltMessage aMessage);

  /**
   * Called for a processor warning (kind {@link EXsltMessageKind#WARNING}):
   * <code>warning-on-no-match="yes"</code> when no template rule matches an
   * item, and <code>warning-on-multiple-match="yes"</code> when several rules
   * of the same import precedence and priority match. Warnings are not part
   * of {@link XsltTransformResult#messages()}. Does nothing by default.
   *
   * @param aWarning
   *        The warning. Never <code>null</code>.
   */
  default void onWarning (@NonNull final XsltMessage aWarning)
  {}
}
