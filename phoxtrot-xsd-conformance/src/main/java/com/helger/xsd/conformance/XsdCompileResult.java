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
package com.helger.xsd.conformance;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The result of {@link IXsdTestValidator#compile}.
 *
 * @param verdict
 *        {@link EXsdVerdict#VALID} if the schema set compiled,
 *        {@link EXsdVerdict#INVALID} if it is in error,
 *        {@link EXsdVerdict#UNSUPPORTED} if the validator cannot decide.
 * @param schema
 *        The compiled schema; not <code>null</code> exactly when the verdict
 *        is valid.
 * @author Philip Helger
 */
public record XsdCompileResult (@NonNull XsdVerdict verdict, @Nullable IXsdTestSchema schema)
{
  /**
   * @param aSchema
   *        The compiled schema.
   * @return A valid result.
   */
  @NonNull
  public static XsdCompileResult valid (@NonNull final IXsdTestSchema aSchema)
  {
    return new XsdCompileResult (XsdVerdict.valid (), aSchema);
  }

  /**
   * @param aVerdict
   *        An invalid or unsupported verdict.
   * @return A result without schema.
   */
  @NonNull
  public static XsdCompileResult failed (@NonNull final XsdVerdict aVerdict)
  {
    return new XsdCompileResult (aVerdict, null);
  }
}
