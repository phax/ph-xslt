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
package com.helger.xpath.conformance;

import java.util.Map;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;

/**
 * Evaluates an assertion expression. The real implementation compiles and runs
 * it with the engine; unit tests plug in a fake.
 *
 * @author Philip Helger
 */
@FunctionalInterface
public interface IQT3Evaluator
{
  /**
   * @param sExpr
   *        The XPath expression.
   * @param aExtraVars
   *        Variables bound in addition to the environment's ones (e.g.
   *        <code>$result</code>).
   * @return The result.
   * @throws XPathException
   *         if the expression raises an error.
   */
  @NonNull
  XdmSequence evaluate (@NonNull String sExpr, @NonNull Map <XdmQName, XdmSequence> aExtraVars);
}
