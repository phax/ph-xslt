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

import org.jspecify.annotations.NonNull;

/**
 * The variety of a simple type.
 *
 * @author Philip Helger
 */
public enum EXsdVariety
{
  ATOMIC ("atomic"),
  LIST ("list"),
  UNION ("union");

  private final String m_sID;

  EXsdVariety (@NonNull final String sID)
  {
    m_sID = sID;
  }

  /** @return <code>atomic</code>, <code>list</code> or <code>union</code>. */
  @NonNull
  public String getID ()
  {
    return m_sID;
  }
}
