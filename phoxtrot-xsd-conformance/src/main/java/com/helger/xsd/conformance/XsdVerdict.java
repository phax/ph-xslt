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
package com.helger.xsd.conformance;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A verdict of an {@link IXsdTestValidator} with an optional message (the
 * first error, or why the case is unsupported).
 *
 * @param verdict
 *        The verdict.
 * @param message
 *        The message, may be <code>null</code>.
 * @author Philip Helger
 */
public record XsdVerdict (@NonNull EXsdVerdict verdict, @Nullable String message)
{
  private static final XsdVerdict VALID = new XsdVerdict (EXsdVerdict.VALID, null);

  /** @return <code>true</code> for {@link EXsdVerdict#VALID}. */
  public boolean isValid ()
  {
    return verdict == EXsdVerdict.VALID;
  }

  /** @return <code>true</code> for {@link EXsdVerdict#UNSUPPORTED}. */
  public boolean isUnsupported ()
  {
    return verdict == EXsdVerdict.UNSUPPORTED;
  }

  /** @return The valid verdict. */
  @NonNull
  public static XsdVerdict valid ()
  {
    return VALID;
  }

  /**
   * @param sMessage
   *        The first error. May be <code>null</code>.
   * @return An invalid verdict.
   */
  @NonNull
  public static XsdVerdict invalid (@Nullable final String sMessage)
  {
    return new XsdVerdict (EXsdVerdict.INVALID, sMessage);
  }

  /**
   * @param sMessage
   *        Why the case cannot be decided. May be <code>null</code>.
   * @return An unsupported verdict.
   */
  @NonNull
  public static XsdVerdict unsupported (@Nullable final String sMessage)
  {
    return new XsdVerdict (EXsdVerdict.UNSUPPORTED, sMessage);
  }
}
