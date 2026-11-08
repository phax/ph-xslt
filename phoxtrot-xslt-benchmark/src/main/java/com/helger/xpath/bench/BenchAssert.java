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

import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The checks of {@link IWorkload#verify(Object)}: they throw instead of using the Java
 * <code>assert</code> statement, which is off by default.
 *
 * @author Philip Helger
 */
public final class BenchAssert
{
  private static final int MAX_SHOWN = 200;

  private BenchAssert ()
  {}

  @NonNull
  private static String _shorten (@Nullable final Object aValue)
  {
    final String sText = String.valueOf (aValue);
    return sText.length () <= MAX_SHOWN ? sText
                                        : sText.substring (0, MAX_SHOWN) + "... (" + sText.length () + " characters)";
  }

  /**
   * @param sWhat
   *        What is checked, for the message.
   * @param aExpected
   *        The expected value.
   * @param aActual
   *        The actual value.
   * @throws IllegalStateException
   *         if the values differ
   */
  public static void assertEquals (@NonNull final String sWhat,
                                   @Nullable final Object aExpected,
                                   @Nullable final Object aActual)
  {
    if (!Objects.equals (aExpected, aActual))
    {
      // Show where two long strings start to differ
      String sWhere = "";
      if (aExpected instanceof final String sExpected && aActual instanceof final String sActual)
      {
        int nIndex = 0;
        final int nMax = Math.min (sExpected.length (), sActual.length ());
        while (nIndex < nMax && sExpected.charAt (nIndex) == sActual.charAt (nIndex))
          nIndex++;
        final int nFrom = Math.max (0, nIndex - 40);
        sWhere = "; first difference at index " +
                 nIndex +
                 ": expected '" +
                 sExpected.substring (nFrom, Math.min (sExpected.length (), nIndex + 40)) +
                 "', actual '" +
                 sActual.substring (nFrom, Math.min (sActual.length (), nIndex + 40)) +
                 "'";
      }
      throw new IllegalStateException (sWhat +
                                       ": expected <" +
                                       _shorten (aExpected) +
                                       "> but was <" +
                                       _shorten (aActual) +
                                       ">" +
                                       sWhere);
    }
  }

  /**
   * @param sWhat
   *        What is checked, for the message.
   * @param bCondition
   *        The condition that must hold.
   * @throws IllegalStateException
   *         if the condition is <code>false</code>
   */
  public static void assertTrue (@NonNull final String sWhat, final boolean bCondition)
  {
    if (!bCondition)
      throw new IllegalStateException (sWhat);
  }
}
