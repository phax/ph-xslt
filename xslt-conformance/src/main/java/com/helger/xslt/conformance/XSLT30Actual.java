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
package com.helger.xslt.conformance;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmSequence;
import com.helger.xslt.XsltTransformResult;

/**
 * What an assertion is checked against: the outcome of one transformation, or
 * a part of it (a secondary result document, a message).
 *
 * @param output
 *        The serialised result the assertion looks at; empty after an error.
 * @param error
 *        The error the transformation raised, <code>null</code> if it
 *        succeeded.
 * @param secondaries
 *        The results of <code>xsl:result-document</code>.
 * @param messages
 *        The <code>xsl:message</code> texts.
 * @param rawValue
 *        The raw result for the assertions that inspect a sequence
 *        (<code>assert-count</code>, <code>assert-type</code>, ...) and for
 *        type-annotation questions; <code>null</code> if there is none.
 * @param resultVar
 *        The variable name the raw result is bound to in
 *        <code>assert</code> expressions; empty to bind none.
 * @param baseUri
 *        Base URI given to a reparsed output, <code>null</code> for none.
 * @author Philip Helger
 */
public record XSLT30Actual (@NonNull String output,
                            @Nullable XPathException error,
                            @NonNull ICommonsList <XsltTransformResult.SecondaryResult> secondaries,
                            @NonNull ICommonsList <String> messages,
                            @Nullable XdmSequence rawValue,
                            @NonNull String resultVar,
                            @Nullable String baseUri)
{
  /** The default name of the result variable. */
  public static final String DEFAULT_RESULT_VAR = "result";

  /**
   * @param aError
   *        The error the transformation raised.
   * @return The actual outcome of a failed transformation.
   */
  @NonNull
  public static XSLT30Actual ofError (@NonNull final XPathException aError)
  {
    ValueEnforcer.notNull (aError, "Error");
    return new XSLT30Actual ("",
                             aError,
                             new CommonsArrayList <> (),
                             new CommonsArrayList <> (),
                             null,
                             DEFAULT_RESULT_VAR,
                             null);
  }

  /**
   * @param aResult
   *        The transformation result.
   * @param bInitialFunction
   *        <code>true</code> if the stylesheet was invoked through an initial
   *        function: its raw result is the returned sequence. Otherwise the
   *        raw result is the principal result tree (one document node), as in
   *        the W3C reference runner; the raw sequence is used when there is
   *        no tree.
   * @param sResultVar
   *        The result variable name.
   * @return The actual outcome.
   */
  @NonNull
  public static XSLT30Actual ofResult (@NonNull final XsltTransformResult aResult,
                                       final boolean bInitialFunction,
                                       @NonNull final String sResultVar)
  {
    ValueEnforcer.notNull (aResult, "Result");
    XdmSequence aRaw = aResult.principalRaw ();
    if (!bInitialFunction && aResult.principalTree () != null)
      aRaw = XdmSequence.of (aResult.principalTree ());
    return new XSLT30Actual (aResult.principalSerialized (),
                             null,
                             aResult.secondaryResults (),
                             aResult.messages (),
                             aRaw,
                             sResultVar,
                             null);
  }
}
