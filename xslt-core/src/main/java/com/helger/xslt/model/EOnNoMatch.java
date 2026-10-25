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
package com.helger.xslt.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The on-no-match values of xsl:mode (XSLT 3.0 section 6.7).
 *
 * @author Philip Helger
 */
public enum EOnNoMatch
{
  DEEP_COPY ("deep-copy"),
  SHALLOW_COPY ("shallow-copy"),
  DEEP_SKIP ("deep-skip"),
  SHALLOW_SKIP ("shallow-skip"),
  TEXT_ONLY_COPY ("text-only-copy"),
  FAIL ("fail");

  private final String m_sID;

  EOnNoMatch (@NonNull final String sID)
  {
    m_sID = sID;
  }

  @NonNull
  public String getID ()
  {
    return m_sID;
  }

  @Nullable
  public static EOnNoMatch getFromIDOrNull (@Nullable final String sID)
  {
    for (final EOnNoMatch e : values ())
      if (e.m_sID.equals (sID))
        return e;
    return null;
  }
}
