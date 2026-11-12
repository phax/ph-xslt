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
package com.helger.phoxtrot.xpath;

import org.jspecify.annotations.NonNull;

import com.helger.phoxtrot.xpath.stream.ESweep;
import com.helger.phoxtrot.xpath.stream.PostureSweep;

/**
 * The simplified streamability verdict of {@link XPathStreamabilityResult}, from the posture and sweep of XSLT 3.0
 * section 19.
 *
 * @author Philip Helger
 */
public enum EStreamabilityVerdict
{
  /**
   * Streamable and motionless: needs nothing beyond the context node, its attributes and ancestors (one pass,
   * constant memory).
   */
  MOTIONLESS,
  /**
   * Streamable and consuming: reads the subtree below the context node once, downwards (one pass; buffering the
   * subtree also works).
   */
  CONSUMING,
  /**
   * Not streamable in one pass, but needs only the subtree of the context node, buffered, and its ancestors and their
   * attributes. Only given in {@link EStreamContextMode#BURST}.
   */
  BURST,
  /**
   * Not streamable (roaming or free-ranging): needs more than one pass or the whole tree.
   */
  FREE_RANGING;

  /**
   * @param aPostureSweep
   *        The posture and sweep. May not be <code>null</code>.
   * @return The verdict for them, never {@link #BURST}. Never <code>null</code>.
   */
  @NonNull
  public static EStreamabilityVerdict getFromPostureSweep (@NonNull final PostureSweep aPostureSweep)
  {
    if (!aPostureSweep.isStreamable ())
      return FREE_RANGING;
    return aPostureSweep.sweep () == ESweep.MOTIONLESS ? MOTIONLESS : CONSUMING;
  }
}
