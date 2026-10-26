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

/**
 * A single schema-compile or instance-validation error.
 *
 * @param line
 *        1-based line, 0 if unknown.
 * @param column
 *        1-based column, 0 if unknown.
 * @param code
 *        The spec clause or component constraint, e.g.
 *        <code>src-resolve</code>; empty if not known.
 * @param message
 *        The message.
 * @param rule
 *        The <code>msg:message/@rule</code> of a failed assertion, empty
 *        otherwise.
 * @param path
 *        The location of the failing element, e.g. <code>/Doc[1]/Item[2]</code>,
 *        empty for errors without instance location.
 * @param kind
 *        For a content-model error: <code>missing</code>,
 *        <code>too-many</code>, <code>unexpected</code> or <code>order</code>;
 *        empty otherwise.
 * @param particle
 *        For a content-model error: the expanded name of the element
 *        declaration concerned; empty otherwise.
 * @author Philip Helger
 */
public record XsdError (int line,
                        int column,
                        @NonNull String code,
                        @NonNull String message,
                        @NonNull String rule,
                        @NonNull String path,
                        @NonNull String kind,
                        @NonNull String particle)
{
  /**
   * @param sCode
   *        The constraint code, may be empty.
   * @param sMessage
   *        The message.
   * @return An error without location.
   */
  @NonNull
  public static XsdError of (@NonNull final String sCode, @NonNull final String sMessage)
  {
    return new XsdError (0, 0, sCode, sMessage, "", "", "", "");
  }

  /**
   * @param nLine
   *        1-based line.
   * @param nColumn
   *        1-based column.
   * @return A copy with that location.
   */
  @NonNull
  public XsdError withLocation (final int nLine, final int nColumn)
  {
    return new XsdError (nLine, nColumn, code, message, rule, path, kind, particle);
  }

  @Override
  public String toString ()
  {
    return code.isEmpty () ? message : code + ": " + message;
  }
}
