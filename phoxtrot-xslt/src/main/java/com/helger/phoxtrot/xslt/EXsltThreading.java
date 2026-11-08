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
package com.helger.phoxtrot.xslt;

/**
 * The thread a compilation or transformation runs on. Template recursion
 * follows the input, and the depth limits (10,000 nested templates or
 * functions) must be reachable without overflowing the Java stack, so by
 * default each call gets a new thread with a stack of
 * {@link com.helger.phoxtrot.xslt.runtime.LargeStackRunner#STACK_SIZE}. In
 * every mode a stack overflow becomes XPDY0130, and a call made on one of the
 * large-stack threads (a nested <code>fn:transform</code>) runs in place.
 *
 * @author Philip Helger
 */
public enum EXsltThreading
{
  /**
   * A new daemon thread with a large stack per call, which the calling thread
   * waits for. If the calling thread is interrupted while waiting, the call
   * fails with FOER0000 and the run goes on in the background until it ends.
   * The default.
   */
  NEW_THREAD,
  /**
   * A thread with a large stack from a shared pool, which the calling thread
   * waits for. The pool has no size limit (one thread per concurrent call, as
   * with {@link #NEW_THREAD}) and keeps a thread for 60 seconds after its last
   * call, which saves the thread start of each call. Waiting and interruption
   * as with {@link #NEW_THREAD}.
   */
  THREAD_POOL,
  /**
   * The calling thread itself, without any thread change: the cheapest mode,
   * but the caller provides the stack. With a default thread stack (often 512
   * KB to 2 MB, <code>-Xss</code>), deep recursion in the stylesheet or the
   * input fails with XPDY0130 long before the depth limits are reached; use a
   * thread with a larger stack for such stylesheets. An interrupt of the
   * calling thread does not stop the run, use
   * {@link XsltTransformRequest#setInterrupt(java.util.function.BooleanSupplier)}.
   */
  CALLING_THREAD;

  /** The default for compilations and transformations. */
  public static final EXsltThreading DEFAULT = NEW_THREAD;
}
