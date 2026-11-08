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
package com.helger.phoxtrot.xpath.stream;

import org.jspecify.annotations.NonNull;

/**
 * The sweep of a construct (XSLT 3.0 section 19.7): how far the position in
 * the input stream moves while it is evaluated. The constants are declared in
 * ascending order: motionless &lt; consuming &lt; free-ranging.
 *
 * @author Philip Helger
 */
public enum ESweep
{
  /** Evaluable without moving the stream position. */
  MOTIONLESS ("motionless"),
  /** Moves the stream position over the subtree of the current node. */
  CONSUMING ("consuming"),
  /** Needs data outside the current subtree; never streamable. */
  FREE_RANGING ("free-ranging");

  private final String m_sName;

  ESweep (@NonNull final String sName)
  {
    m_sName = sName;
  }

  /** @return The name the specification uses, e.g. <code>consuming</code>. */
  @NonNull
  public String getName ()
  {
    return m_sName;
  }

  /**
   * @param eOther
   *        The other sweep.
   * @return The wider of this sweep and the other one.
   */
  @NonNull
  public ESweep getWider (@NonNull final ESweep eOther)
  {
    return eOther.ordinal () > ordinal () ? eOther : this;
  }
}
