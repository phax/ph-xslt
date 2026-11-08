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
package com.helger.xslt.conformance;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.xslt.XsltExecutable;

/**
 * Compile cache shared by all test sets of a run: many cases use the same
 * stylesheet file. The key must contain everything that changes the
 * compilation (file, embedded or not, static parameters). A compile that
 * throws a {@link RuntimeException} is cached as that exception, so every case
 * using the stylesheet sees the same error; an {@link Error} is not cached.
 * Two threads may compile the same key at once; the first result stored wins.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class XSLT30StylesheetCache
{
  private final ConcurrentHashMap <String, Object> m_aMap = new ConcurrentHashMap <> ();

  /**
   * @param sKey
   *        The cache key.
   * @param aCompiler
   *        Compiles on a cache miss.
   * @return The compiled stylesheet.
   * @throws RuntimeException
   *         the exception the compilation threw, also on a cache hit
   */
  @NonNull
  public XsltExecutable getOrCompile (@NonNull final String sKey, @NonNull final Supplier <XsltExecutable> aCompiler)
  {
    Object aEntry = m_aMap.get (sKey);
    if (aEntry == null)
    {
      try
      {
        aEntry = aCompiler.get ();
      }
      catch (final RuntimeException ex)
      {
        aEntry = ex;
      }
      final Object aOld = m_aMap.putIfAbsent (sKey, aEntry);
      if (aOld != null)
        aEntry = aOld;
    }
    if (aEntry instanceof final RuntimeException ex)
      throw ex;
    return (XsltExecutable) aEntry;
  }
}
