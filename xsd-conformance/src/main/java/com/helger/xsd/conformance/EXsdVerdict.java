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

/**
 * The outcome of compiling a schema set or validating an instance.
 *
 * @author Philip Helger
 */
public enum EXsdVerdict
{
  /** The schema set compiled, resp. the instance is schema-valid. */
  VALID,
  /** The schema set is in error, resp. the instance is not schema-valid. */
  INVALID,
  /**
   * The validator cannot decide (feature or version not implemented). Counted
   * on its own, neither pass nor fail, so the baseline stays honest.
   */
  UNSUPPORTED
}
