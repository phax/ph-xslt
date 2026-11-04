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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xpath.context.StaticContext;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;

/**
 * A compiled stylesheet. Immutable and usable from several threads at once.
 *
 * @author Philip Helger
 */
public interface XsltExecutable
{
  /**
   * @param aRequest
   *        How to invoke the stylesheet.
   * @return The results.
   * @throws XsltException
   *         for dynamic errors (and XPath errors wrapped with their code).
   */
  @NonNull
  XsltTransformResult transform (@NonNull XsltTransformRequest aRequest);

  /**
   * Create the static context for standalone XPath expressions and patterns of
   * a host (e.g. a Schematron executor) that use the declarations of this
   * stylesheet: they see what an expression written on the principal
   * stylesheet element sees, i.e. its namespaces, base URI,
   * xpath-default-namespace and default collation, the stylesheet functions
   * (xsl:function) together with the XSLT functions such as
   * <code>current()</code> and <code>key()</code>, the global variables and
   * parameters, and the decimal formats. The host may declare further
   * namespaces and variables on the returned object.
   *
   * @return A new static context. Never <code>null</code>.
   * @throws UnsupportedOperationException
   *         if this implementation does not support host expressions
   */
  @NonNull
  default StaticContext createStaticContext ()
  {
    throw new UnsupportedOperationException ("Host expressions are not supported by " + getClass ().getName ());
  }

  /**
   * Prepare the evaluation of expressions compiled against
   * {@link #createStaticContext()} for one source document, set up like a
   * transformation with the same request: global context item (with
   * whitespace stripping), global variables and parameters evaluated on first
   * use, keys, and the resolvers and limits of the request.
   *
   * @param aRequest
   *        The request. Only the global context item, the stylesheet
   *        parameters, the resolvers and the limits are used.
   * @return A new evaluation, for one thread at a time. Never
   *         <code>null</code>.
   * @throws XsltException
   *         if the global context item does not fit the stylesheet or a
   *         required parameter is missing
   * @throws UnsupportedOperationException
   *         if this implementation does not support host expressions
   */
  @NonNull
  default XsltEvaluation createEvaluation (@NonNull final XsltTransformRequest aRequest)
  {
    throw new UnsupportedOperationException ("Host expressions are not supported by " + getClass ().getName ());
  }

  /**
   * @return The in-scope schema definitions of a schema-aware stylesheet (to
   *         validate source documents against the same components the
   *         stylesheet refers to), <code>null</code> for a basic processor.
   */
  @Nullable
  default IXdmSchemaSet getSchemaSet ()
  {
    return null;
  }

  /**
   * @return <code>true</code> if the stylesheet says
   *         <code>input-type-annotations="strip"</code>: type annotations of
   *         source documents are to be removed.
   */
  default boolean isStripInputTypeAnnotations ()
  {
    return false;
  }
}
