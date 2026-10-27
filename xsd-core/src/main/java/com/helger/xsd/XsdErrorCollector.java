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
package com.helger.xsd;

import java.util.Comparator;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * The errors of a collecting instance validation (go-xslt <code>collector</code>
 * in <code>collect.go</code>). It lives in the {@link XsdValidationRun}; the
 * in-place path of the bridge has none and stops at the first error, so the
 * verdict is identical either way - only more of the document is looked at
 * once it is known to be invalid.
 *
 * @author Philip Helger
 */
final class XsdErrorCollector
{
  /** An error with the msg:message entries its wording is chosen from. */
  record Entry (@NonNull XsdError error, @NonNull ICommonsList <XsdMessage> messages)
  {}

  /**
   * Unwinds validation once MaxErrors errors are collected (go-xslt
   * <code>errStopped</code>). Neither an invalid nor an unsupported verdict, so
   * no handler of those sees it.
   */
  static final class Stopped extends RuntimeException
  {
    Stopped ()
    {
      super ("xsd: error limit reached", null, false, false);
    }
  }

  /**
   * Thrown by the content-model check after it has recorded the content-model
   * errors itself; the caller may still assess the declared children and the
   * element's assertions (go-xslt <code>errModelMismatch</code>).
   */
  static final class ModelMismatch extends RuntimeException
  {
    ModelMismatch ()
    {
      super ("xsd: content model mismatch recorded", null, false, false);
    }
  }

  private final ICommonsList <Entry> m_aEntries = new CommonsArrayList <> ();
  private final int m_nMax;

  /**
   * @param nMax
   *        The maximum number of errors; 0 for no limit.
   */
  XsdErrorCollector (final int nMax)
  {
    m_nMax = nMax;
  }

  /**
   * Record an error.
   *
   * @param aEntry
   *        The error.
   * @throws Stopped
   *         if that was the last error to collect
   */
  void add (@NonNull final Entry aEntry)
  {
    m_aEntries.add (aEntry);
    if (m_nMax > 0 && m_aEntries.size () >= m_nMax)
      throw new Stopped ();
  }

  /** @return The number of errors recorded so far. */
  int size ()
  {
    return m_aEntries.size ();
  }

  /**
   * Withdraw the errors recorded after a {@link #size()} reading.
   *
   * @param nSize
   *        The size to cut back to.
   */
  void truncate (final int nSize)
  {
    while (m_aEntries.size () > nSize)
      m_aEntries.removeLast ();
  }

  /**
   * @param sLanguage
   *        The preferred language of the messages; may be empty.
   * @return The errors by document position (stable, so the errors of one
   *         element keep the order they were found in), the wording chosen by
   *         language.
   */
  @NonNull
  ICommonsList <XsdError> getSorted (@NonNull final String sLanguage)
  {
    final ICommonsList <XsdError> ret = new CommonsArrayList <> (m_aEntries.size ());
    for (final Entry aEntry : m_aEntries)
      ret.add (XsdMessages.pickMessage (aEntry.error (), aEntry.messages (), sLanguage));
    ret.sort (Comparator.comparingInt (XsdError::line).thenComparingInt (XsdError::column));
    return ret;
  }
}
