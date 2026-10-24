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
package com.helger.xpath.ast;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The XPath axes.
 *
 * @author Philip Helger
 */
public enum EAxis
{
  CHILD ("child"),
  DESCENDANT ("descendant"),
  ATTRIBUTE ("attribute"),
  SELF ("self"),
  DESCENDANT_OR_SELF ("descendant-or-self"),
  FOLLOWING_SIBLING ("following-sibling"),
  FOLLOWING ("following"),
  NAMESPACE ("namespace"),
  PARENT ("parent"),
  ANCESTOR ("ancestor"),
  PRECEDING_SIBLING ("preceding-sibling"),
  PRECEDING ("preceding"),
  ANCESTOR_OR_SELF ("ancestor-or-self");

  private final String m_sName;

  EAxis (@NonNull final String sName)
  {
    m_sName = sName;
  }

  @NonNull
  public String getName ()
  {
    return m_sName;
  }

  /** @return <code>true</code> for axes that deliver nodes in reverse document order. */
  public boolean isReverse ()
  {
    return this == PARENT ||
           this == ANCESTOR ||
           this == PRECEDING_SIBLING ||
           this == PRECEDING ||
           this == ANCESTOR_OR_SELF;
  }

  @Nullable
  public static EAxis getFromName (@Nullable final String sName)
  {
    for (final EAxis e : values ())
      if (e.m_sName.equals (sName))
        return e;
    return null;
  }
}
