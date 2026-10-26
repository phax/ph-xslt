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

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsTreeMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSortedMap;

/**
 * The counts of one test set in one version run.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XsdSetResult
{
  private final String m_sName;
  private int m_nPass;
  private int m_nFail;
  private int m_nSkip;
  private int m_nUnsupported;
  private int m_nQueried;
  private int m_nSchemaPass;
  private int m_nSchemaFail;
  private int m_nSchemaUnsupported;
  private final ICommonsSortedMap <String, Integer> m_aSkipReasons = new CommonsTreeMap <> ();
  private final ICommonsList <String> m_aFailNames = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aLines = new CommonsArrayList <> ();
  private String m_sLoadError;

  /**
   * @param sName
   *        The test-set name.
   */
  public XsdSetResult (@NonNull final String sName)
  {
    m_sName = sName;
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

  /**
   * @param sCaseName
   *        The failing schema or instance test.
   * @param sLine
   *        Fail dump line, may be <code>null</code> if not collected.
   */
  public void addFail (@NonNull final String sCaseName, @Nullable final String sLine)
  {
    m_nFail++;
    m_aFailNames.add (sCaseName);
    if (sLine != null)
      m_aLines.add (sLine);
  }

  /**
   * @param sLine
   *        Dump line, may be <code>null</code> if not collected.
   */
  public void addUnsupported (@Nullable final String sLine)
  {
    m_nUnsupported++;
    if (sLine != null)
      m_aLines.add (sLine);
  }

  public void addQueried ()
  {
    m_nQueried++;
  }

  /**
   * Book the outcome of a schema test (in addition to the totals, which count
   * schema and instance tests alike).
   *
   * @param nPass
   *        Passes to add.
   * @param nFail
   *        Fails to add.
   * @param nUnsupported
   *        Unsupported cases to add.
   */
  public void addSchemaTestOutcome (final int nPass, final int nFail, final int nUnsupported)
  {
    m_nSchemaPass += nPass;
    m_nSchemaFail += nFail;
    m_nSchemaUnsupported += nUnsupported;
  }

  /**
   * @param sReason
   *        Why the case was skipped.
   */
  public void addSkip (@NonNull final String sReason)
  {
    m_nSkip++;
    final Integer aOld = m_aSkipReasons.get (sReason);
    m_aSkipReasons.put (sReason, Integer.valueOf (aOld == null ? 1 : aOld.intValue () + 1));
  }

  /**
   * @param sLoadError
   *        The error that prevented reading the test set.
   */
  public void setLoadError (@NonNull final String sLoadError)
  {
    m_sLoadError = sLoadError;
  }

  /** @return The error that prevented reading the test set, or <code>null</code>. */
  @Nullable
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

  public int getUnsupported ()
  {
    return m_nUnsupported;
  }

  public int getQueried ()
  {
    return m_nQueried;
  }

  /** @return The passing schema tests (part of {@link #getPass()}). */
  public int getSchemaPass ()
  {
    return m_nSchemaPass;
  }

  /** @return The failing schema tests (part of {@link #getFail()}). */
  public int getSchemaFail ()
  {
    return m_nSchemaFail;
  }

  /** @return The unsupported schema tests (part of {@link #getUnsupported()}). */
  public int getSchemaUnsupported ()
  {
    return m_nSchemaUnsupported;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsSortedMap <String, Integer> getSkipReasons ()
  {
    return m_aSkipReasons.getClone ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getFailNames ()
  {
    return m_aFailNames.getClone ();
  }

  /** @return The collected fail (and unsupported) lines. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getLines ()
  {
    return m_aLines.getClone ();
  }
}
