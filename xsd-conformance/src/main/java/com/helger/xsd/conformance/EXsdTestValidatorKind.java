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

import java.util.Locale;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;

/**
 * The validators the harness can run, chosen with the system property
 * <code>xsd.validator</code> (see {@link XsdSettings}).
 *
 * @author Philip Helger
 */
public enum EXsdTestValidatorKind
{
  /** Apache Xerces-J 2.12.2, the baseline. */
  XERCES ("xerces", "xsd", "docs/accepted-failures/xsd.md", true, XercesXsdTestValidator::new),
  /**
   * The native validator of <code>xsd-core</code>. Its accepted-failures check
   * is the gate: go-xslt's five documented XSD 1.0 failures are accepted.
   */
  NATIVE ("native", "xsd-native", "docs/accepted-failures/xsd-native.md", true, NativeXsdTestValidator::new);

  private final String m_sID;
  private final String m_sFilePrefix;
  private final String m_sAcceptedFile;
  private final boolean m_bCheckAcceptedByDefault;
  private final Supplier <? extends IXsdTestValidator> m_aFactory;

  EXsdTestValidatorKind (@NonNull @Nonempty final String sID,
                         @NonNull @Nonempty final String sFilePrefix,
                         @NonNull @Nonempty final String sAcceptedFile,
                         final boolean bCheckAcceptedByDefault,
                         @NonNull final Supplier <? extends IXsdTestValidator> aFactory)
  {
    m_sID = sID;
    m_sFilePrefix = sFilePrefix;
    m_sAcceptedFile = sAcceptedFile;
    m_bCheckAcceptedByDefault = bCheckAcceptedByDefault;
    m_aFactory = aFactory;
  }

  /** @return The value of <code>xsd.validator</code>. */
  @NonNull
  @Nonempty
  public String getID ()
  {
    return m_sID;
  }

  /**
   * @return The prefix of the output files: <code>xsd</code> for Xerces-J
   *         (<code>xsd-fails.txt</code>), <code>xsd-native</code> for the
   *         native validator (<code>xsd-native-fails.txt</code>).
   */
  @NonNull
  @Nonempty
  public String getFilePrefix ()
  {
    return m_sFilePrefix;
  }

  /** @return The default accepted-failures file of this validator. */
  @NonNull
  @Nonempty
  public String getAcceptedFile ()
  {
    return m_sAcceptedFile;
  }

  /** @return Whether the accepted-failures check is on unless configured. */
  public boolean isCheckAcceptedByDefault ()
  {
    return m_bCheckAcceptedByDefault;
  }

  /** @return The factory of validator instances (one per test set). */
  @NonNull
  public Supplier <? extends IXsdTestValidator> getFactory ()
  {
    return m_aFactory;
  }

  /**
   * @param sID
   *        The ID, case-insensitive. May be <code>null</code>.
   * @return The matching kind or <code>null</code>.
   */
  @Nullable
  public static EXsdTestValidatorKind getFromIDOrNull (@Nullable final String sID)
  {
    if (sID != null)
      for (final EXsdTestValidatorKind e : values ())
        if (e.m_sID.equals (sID.strip ().toLowerCase (Locale.ROOT)))
          return e;
    return null;
  }
}
