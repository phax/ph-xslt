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
package com.helger.xpath.context;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The XSD datatype version whose rules apply where XSD 1.0 and XSD 1.1 differ
 * (for example <code>+INF</code> as an <code>xs:double</code>, the year
 * <code>0000</code>, the character class grammar of regular expressions).
 *
 * @author Philip Helger
 */
public enum EXsdVersion
{
  V10 ("1.0"),
  V11 ("1.1");

  private final String m_sID;

  EXsdVersion (@NonNull final String sID)
  {
    m_sID = sID;
  }

  /** @return The version number, "1.0" or "1.1". */
  @NonNull
  public String getID ()
  {
    return m_sID;
  }

  /**
   * @param sID
   *        A version number such as "1.1".
   * @return The matching version or <code>null</code>.
   */
  @Nullable
  public static EXsdVersion getFromIDOrNull (@Nullable final String sID)
  {
    for (final EXsdVersion e : values ())
      if (e.m_sID.equals (sID))
        return e;
    return null;
  }
}
