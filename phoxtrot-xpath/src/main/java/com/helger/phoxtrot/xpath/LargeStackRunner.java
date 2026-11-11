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
package com.helger.phoxtrot.xpath;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.wrapper.Wrapper;
import com.helger.phoxtrot.xpath.XPathException;

/**
 * Runs compilations, transformations and XPath evaluations on a thread with a
 * large stack: template and function recursion follows the input, and the
 * depth limits (10,000 nested templates or functions) must be reachable
 * without overflowing the Java stack. A stack overflow that still happens
 * becomes XPDY0130. Which thread is used is chosen by {@link EThreading}. A
 * plain XPath caller wraps its evaluation, e.g.
 * <code>LargeStackRunner.run (() -&gt; aXPath.evaluate (aCtx), "my-xpath", EThreading.THREAD_POOL)</code>.
 *
 * @author Philip Helger
 */
@Immutable
public final class LargeStackRunner
{
  /** Stack size of the worker thread (reserved address space, committed on use). */
  public static final long STACK_SIZE = 2L * 1024 * 1024 * 1024;

  /** Seconds a pool thread is kept without a call. */
  private static final long POOL_KEEP_ALIVE_SECONDS = 60;

  @PresentForCodeCoverage
  private static final LargeStackRunner INSTANCE = new LargeStackRunner ();

  private LargeStackRunner ()
  {}

  /** The worker threads, so that a nested run (e.g. fn:transform) stays on its thread. */
  private static final class WorkerThread extends Thread
  {
    WorkerThread (@NonNull final Runnable aRunnable, @NonNull final String sName)
    {
      super (null, aRunnable, sName, STACK_SIZE);
    }
  }

  /**
   * The pool of {@link EThreading#THREAD_POOL}, created on first use: daemon
   * worker threads, as many as there are concurrent calls (no queue, so a call
   * never waits for another one), each kept for
   * {@link #POOL_KEEP_ALIVE_SECONDS} after its last call.
   */
  private static final class PoolHolder
  {
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger (0);
    private static final ExecutorService POOL = new ThreadPoolExecutor (0,
                                                                        Integer.MAX_VALUE,
                                                                        POOL_KEEP_ALIVE_SECONDS,
                                                                        TimeUnit.SECONDS,
                                                                        new SynchronousQueue <> (),
                                                                        aRunnable -> {
                                                                          final Thread ret = new WorkerThread (aRunnable,
                                                                                                               "phoxtrot-pool-" +
                                                                                                                          THREAD_COUNTER.incrementAndGet ());
                                                                          ret.setDaemon (true);
                                                                          return ret;
                                                                        });
  }

  /**
   * Run an action on the current thread, a {@link StackOverflowError} turned
   * into XPDY0130.
   */
  private static <T> T _runHere (@NonNull final Supplier <T> aAction)
  {
    try
    {
      return aAction.get ();
    }
    catch (final StackOverflowError ex)
    {
      throw new XPathException ("XPDY0130", "The processing is nested too deeply", ex);
    }
  }

  /**
   * Rethrow what an action ended with on another thread: a runtime exception
   * or error unchanged, anything else wrapped in an
   * {@link IllegalStateException}. Nothing happens for <code>null</code>.
   */
  private static void _rethrow (final Throwable aThrowable)
  {
    if (aThrowable instanceof final RuntimeException aRtEx)
      throw aRtEx;
    if (aThrowable instanceof final Error aErr)
      throw aErr;
    if (aThrowable != null)
      throw new IllegalStateException (aThrowable);
  }

  /**
   * A caller that is already interrupted does not start a run on another
   * thread: waiting for it would only notice the interrupt if the action had
   * not finished yet (<code>Thread.join</code> and <code>Future.get</code>
   * return a finished result without checking the flag), so the outcome
   * would depend on timing. The flag stays set.
   *
   * @throws XPathException
   *         FOER0000 if the calling thread is interrupted
   */
  private static void _checkNotInterrupted ()
  {
    if (Thread.currentThread ().isInterrupted ())
      throw new XPathException ("FOER0000", "The processing was interrupted");
  }

  /**
   * Run an action on a new daemon worker thread and wait for it.
   */
  private static <T> T _runOnNewThread (@NonNull final Supplier <T> aAction, @NonNull final String sThreadName)
  {
    final Wrapper <T> aResult = new Wrapper <> ();
    final Wrapper <Throwable> aError = new Wrapper <> ();
    final Thread aThread = new WorkerThread ( () -> {
      try
      {
        aResult.set (_runHere (aAction));
      }
      catch (final Throwable t)
      {
        aError.set (t);
      }
    }, sThreadName);
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
    _rethrow (aError.get ());
    return aResult.get ();
  }

  /**
   * Run an action on a thread of the shared pool and wait for it.
   */
  private static <T> T _runOnPool (@NonNull final Supplier <T> aAction)
  {
    final Future <T> aFuture = PoolHolder.POOL.submit ( () -> _runHere (aAction));
    try
    {
      return aFuture.get ();
    }
    catch (final InterruptedException ex)
    {
      // The run goes on, as on a thread of its own
      Thread.currentThread ().interrupt ();
      throw new XPathException ("FOER0000", "The processing was interrupted");
    }
    catch (final ExecutionException ex)
    {
      _rethrow (ex.getCause ());
      throw new IllegalStateException (ex);
    }
  }

  /**
   * Run an action on a new daemon worker thread with a stack of
   * {@link #STACK_SIZE} and wait for it ({@link EThreading#NEW_THREAD}).
   *
   * @param aAction
   *        The action.
   * @param sThreadName
   *        Name of the worker thread.
   * @param <T>
   *        Result type.
   * @return The result of the action.
   * @throws XPathException
   *         XPDY0130 when the action overflows the stack, FOER0000 when the
   *         calling thread is interrupted while waiting (its interrupt flag
   *         is set again)
   * @see #run(Supplier, String, EThreading)
   */
  public static <T> T run (@NonNull final Supplier <T> aAction, @NonNull final String sThreadName)
  {
    return run (aAction, sThreadName, EThreading.NEW_THREAD);
  }

  /**
   * Run an action on the thread the mode selects and return its result. On a
   * worker thread already (a nested run started by <code>fn:transform</code>)
   * the action runs in place, whatever the mode. A {@link StackOverflowError}
   * of the action becomes XPDY0130; any other runtime exception or error is
   * rethrown unchanged on the calling thread.
   *
   * @param aAction
   *        The action.
   * @param sThreadName
   *        Name of a new worker thread ({@link EThreading#NEW_THREAD}).
   * @param eThreading
   *        The thread to run on.
   * @param <T>
   *        Result type.
   * @return The result of the action.
   * @throws XPathException
   *         XPDY0130 when the action overflows the stack, FOER0000 when the
   *         calling thread is interrupted before a run on another thread
   *         starts (the action is not started) or while waiting for it (the
   *         action goes on); its interrupt flag is set in both cases
   */
  public static <T> T run (@NonNull final Supplier <T> aAction,
                           @NonNull final String sThreadName,
                           @NonNull final EThreading eThreading)
  {
    ValueEnforcer.notNull (eThreading, "Threading");
    // Already on a worker thread (a transformation started by fn:transform):
    // run in place; the shared depth budget bounds the nesting
    if (Thread.currentThread () instanceof WorkerThread)
      return aAction.get ();
    return switch (eThreading)
    {
      case NEW_THREAD ->
      {
        _checkNotInterrupted ();
        yield _runOnNewThread (aAction, sThreadName);
      }
      case THREAD_POOL ->
      {
        _checkNotInterrupted ();
        yield _runOnPool (aAction);
      }
      case CALLING_THREAD -> _runHere (aAction);
    };
  }
}
