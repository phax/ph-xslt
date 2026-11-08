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
package com.helger.phoxtrot.xpath.conformance;

/**
 * The outcome class of one QT3 test case or assertion.
 *
 * @author Philip Helger
 */
public enum EQT3Status
{
  /** The case ran and its assertion held; counted in the pass rate. */
  PASS,
  /**
   * The case ran and its assertion did not hold, or the run crashed; the
   * case name goes to the fail list and must be named in the
   * accepted-failures file.
   */
  FAIL,
  /**
   * The case was not run (inapplicable dependency, missing environment or
   * test file, no assertion, unknown assertion kind); not part of the pass
   * rate.
   */
  SKIP
}
