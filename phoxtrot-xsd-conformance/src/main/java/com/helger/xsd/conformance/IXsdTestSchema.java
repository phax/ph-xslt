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

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;

/**
 * A schema set compiled by an {@link IXsdTestValidator}.
 *
 * @author Philip Helger
 */
public interface IXsdTestSchema
{
  /**
   * Assess the schema-validity of an instance document, starting strictly at
   * its document element. Well-formedness errors make it invalid.
   *
   * @param aInstance
   *        The local instance file.
   * @return {@link EXsdVerdict#VALID}, {@link EXsdVerdict#INVALID} (with the
   *         first error) or {@link EXsdVerdict#UNSUPPORTED}. Never
   *         <code>null</code>.
   */
  @NonNull
  XsdVerdict validate (@NonNull Path aInstance);
}
