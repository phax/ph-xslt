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
package com.helger.xpath.conformance;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsTreeMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSortedMap;

/**
 * The counts of one test set.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class QT3SetResult
{
  private final String m_sName;
  private int m_nPass;
  private int m_nFail;
  private int m_nSkip;
  private final ICommonsSortedMap <String, Integer> m_aSkipReasons = new CommonsTreeMap <> ();
  private final ICommonsSortedMap <String, Integer> m_aFailReasons = new CommonsTreeMap <> ();
  private final ICommonsList <String> m_aFailNames = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aFailLines = new CommonsArrayList <> ();
  private String m_sLoadError;

  public QT3SetResult (@NonNull final String sName)
  {
    m_sName = sName;
  }

  private static void _inc (@NonNull final ICommonsSortedMap <String, Integer> aMap, @NonNull final String sKey)
  {
    final Integer aOld = aMap.get (sKey);
    aMap.put (sKey, Integer.valueOf (aOld == null ? 1 : aOld.intValue () + 1));
  }

  @NonNull
  public String getName ()
  {
    return m_sName;
  }

  public void addPass ()
  {
    m_nPass++;
  }

  public void addSkip (@NonNull final String sReason)
  {
    m_nSkip++;
    _inc (m_aSkipReasons, sReason);
  }

  /**
   * @param sCaseName
   *        Failed case.
   * @param sReason
   *        Short reason.
   * @param sLine
   *        Fail dump line, may be <code>null</code> if not collected.
   */
  public void addFail (@NonNull final String sCaseName, @NonNull final String sReason, final String sLine)
  {
    m_nFail++;
    _inc (m_aFailReasons, sReason);
    m_aFailNames.add (sCaseName);
    if (sLine != null)
      m_aFailLines.add (sLine);
  }

  public void setLoadError (@NonNull final String sLoadError)
  {
    m_sLoadError = sLoadError;
  }

  /** @return The error that prevented reading the test set, or <code>null</code>. */
  public String getLoadError ()
  {
    return m_sLoadError;
  }

  public int getPass ()
  {
    return m_nPass;
  }

  public int getFail ()
  {
    return m_nFail;
  }

  public int getSkip ()
  {
    return m_nSkip;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsSortedMap <String, Integer> getSkipReasons ()
  {
    return m_aSkipReasons.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsSortedMap <String, Integer> getFailReasons ()
  {
    return m_aFailReasons.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getFailNames ()
  {
    return m_aFailNames.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getFailLines ()
  {
    return m_aFailLines.getClone ();
  }
}
