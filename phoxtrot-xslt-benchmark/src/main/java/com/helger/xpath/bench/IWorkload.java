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
package com.helger.xpath.bench;

import org.jspecify.annotations.NonNull;

/**
 * One measured operation of the benchmark, prepared for a given input size. The preparation
 * (generating and parsing the input, compiling the stylesheet or expressions) happens before and is
 * not measured.
 *
 * @author Philip Helger
 */
public interface IWorkload
{
  /**
   * Run the measured operation once.
   *
   * @return The result, which the caller consumes so that the work cannot be optimised away. Never
   *         <code>null</code>.
   * @throws Exception
   *         if the engine fails
   */
  @NonNull
  Object run () throws Exception;

  /**
   * Check a result of {@link #run()} against the result computed independently of the engine, so
   * that a later change cannot silently make the workload do less.
   *
   * @param aResult
   *        The result of {@link #run()}.
   * @throws IllegalStateException
   *         if the result is not the expected one
   */
  void verify (@NonNull Object aResult);

  /**
   * Run the operation once and check its result. Called once in the preparation of every
   * benchmark and of {@link MainProfile}.
   *
   * @throws Exception
   *         if the engine fails
   * @throws IllegalStateException
   *         if the result is not the expected one
   */
  default void runAndVerify () throws Exception
  {
    verify (run ());
  }
}
