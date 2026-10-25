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
