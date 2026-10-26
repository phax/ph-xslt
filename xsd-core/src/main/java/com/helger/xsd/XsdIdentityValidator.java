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
package com.helger.xsd;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.xdm.XdmNode;

/**
 * Evaluation of identity constraints on an instance (the run-time half of
 * go-xslt <code>identity.go</code>: key tables, tuples, keyref checks). The
 * compile-time half (parsing, the restricted XPath grammar, keyref
 * cross-checks) is part of the loader ({@link XsdIdentityCompiler}).
 * <p>
 * <b>Phase V5 owns this class.</b> Until then every method is a stub.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdIdentityValidator
{
  @PresentForCodeCoverage
  private static final XsdIdentityValidator INSTANCE = new XsdIdentityValidator ();

  private XsdIdentityValidator ()
  {}

  /**
   * Check the key/unique constraints of a declaration at its scope element
   * (go-xslt <code>buildKeyTable</code> and friends).
   *
   * @param aRun
   *        The validation run.
   * @param aConstraint
   *        The constraint.
   * @param aScope
   *        The instance element the declaration governs.
   * @throws XsdInvalidException
   *         if the constraint is violated
   */
  static void checkConstraint (@NonNull final XsdValidationRun aRun,
                               @NonNull final XsdIdentityConstraint aConstraint,
                               @NonNull final XdmNode aScope)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
