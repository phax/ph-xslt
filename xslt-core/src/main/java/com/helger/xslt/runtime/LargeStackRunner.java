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
package com.helger.xslt.runtime;

import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.wrapper.Wrapper;
import com.helger.xpath.XPathException;

/**
 * Runs compilations and transformations on a thread with a large stack:
 * template recursion follows the input, and the depth limits (10,000 nested
 * templates or functions) must be reachable without overflowing the Java
 * stack. A stack overflow that still happens becomes XPDY0130.
 *
 * @author Philip Helger
 */
@Immutable
public final class LargeStackRunner
{
  /** Stack size of the worker thread (reserved address space, committed on use). */
  public static final long STACK_SIZE = 2L * 1024 * 1024 * 1024;

  @PresentForCodeCoverage
  private static final LargeStackRunner INSTANCE = new LargeStackRunner ();

  private LargeStackRunner ()
  {}

  /**
   * @param aAction
   *        The action.
   * @param sThreadName
   *        Name of the worker thread.
   * @param <T>
   *        Result type.
   * @return The result of the action.
   */
  public static <T> T run (@NonNull final Supplier <T> aAction, @NonNull final String sThreadName)
  {
    final Wrapper <T> aResult = new Wrapper <> ();
    final Wrapper <Throwable> aError = new Wrapper <> ();
    final Thread aThread = new Thread (null, () -> {
      try
      {
        aResult.set (aAction.get ());
      }
      catch (final StackOverflowError ex)
      {
        aError.set (new XPathException ("XPDY0130", "The processing is nested too deeply", ex));
      }
      catch (final Throwable t)
      {
        aError.set (t);
      }
    }, sThreadName, STACK_SIZE);
    aThread.setDaemon (true);
    aThread.start ();
    try
    {
      aThread.join ();
    }
    catch (final InterruptedException ex)
    {
      Thread.currentThread ().interrupt ();
      throw new XPathException ("FOER0000", "The processing was interrupted");
    }
    final Throwable aThrowable = aError.get ();
    if (aThrowable instanceof final RuntimeException aRtEx)
      throw aRtEx;
    if (aThrowable instanceof final Error aErr)
      throw aErr;
    if (aThrowable != null)
      throw new IllegalStateException (aThrowable);
    return aResult.get ();
  }
}
