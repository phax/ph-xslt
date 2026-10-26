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

import com.helger.annotation.Nonempty;

/**
 * The XSD versions the suite distinguishes. The ID is the version token used
 * in the suite's <code>version</code> attributes.
 *
 * @author Philip Helger
 */
public enum EXsdVersion
{
  XSD_10 ("1.0", ""),
  XSD_11 ("1.1", "11");

  private final String m_sID;
  private final String m_sFileSuffix;

  EXsdVersion (@NonNull @Nonempty final String sID, @NonNull final String sFileSuffix)
  {
    m_sID = sID;
    m_sFileSuffix = sFileSuffix;
  }

  /** @return The version token, e.g. <code>1.0</code>. */
  @NonNull
  @Nonempty
  public String getID ()
  {
    return m_sID;
  }

  /**
   * @return The suffix after <code>xsd</code> in the names of the output
   *         files: empty for XSD 1.0 (<code>xsd-fails.txt</code>),
   *         <code>11</code> for XSD 1.1 (<code>xsd11-fails.txt</code>).
   */
  @NonNull
  public String getFileSuffix ()
  {
    return m_sFileSuffix;
  }

  /**
   * @param sID
   *        The version token. May be <code>null</code>.
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
