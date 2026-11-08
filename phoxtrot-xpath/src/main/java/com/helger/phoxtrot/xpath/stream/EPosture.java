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
 * The posture of a construct (XSLT 3.0 section 19.5): how the nodes it returns
 * relate to the streamed input document.
 *
 * @author Philip Helger
 */
public enum EPosture
{
  /** The value contains no nodes from the streamed input. */
  GROUNDED ("grounded"),
  /**
   * Nodes reached via parent, ancestor[-or-self], attribute or namespace from
   * the current streaming position.
   */
  CLIMBING ("climbing"),
  /**
   * Nodes reached via descendant[-or-self], so potentially nested; no further
   * downward navigation is permitted.
   */
  CRAWLING ("crawling"),
  /** Peer nodes in document order, none an ancestor of another. */
  STRIDING ("striding"),
  /** The nodes could be anywhere; never streamable. */
  ROAMING ("roaming");

  private final String m_sName;

  EPosture (@NonNull final String sName)
  {
    m_sName = sName;
  }

  /** @return The name the specification uses, e.g. <code>striding</code>. */
  @NonNull
  public String getName ()
  {
    return m_sName;
  }
}
