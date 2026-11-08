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
 * The result of a streamability classification: a posture and a sweep (XSLT
 * 3.0 section 19.7).
 *
 * @param posture
 *        The posture.
 * @param sweep
 *        The sweep.
 * @author Philip Helger
 */
public record PostureSweep (@NonNull EPosture posture, @NonNull ESweep sweep)
{
  /** Grounded and motionless: the result for an absent operand. */
  public static final PostureSweep GROUNDED_MOTIONLESS = new PostureSweep (EPosture.GROUNDED, ESweep.MOTIONLESS);
  /** Roaming and free-ranging: not streamable. */
  public static final PostureSweep ROAMING_FREE_RANGING = new PostureSweep (EPosture.ROAMING, ESweep.FREE_RANGING);

  /**
   * @param ePosture
   *        A posture.
   * @param eSweep
   *        A sweep.
   * @return <code>false</code> if the posture is roaming or the sweep is
   *         free-ranging, in either combination (XSLT 3.0 section 19.7).
   */
  public static boolean isStreamable (@NonNull final EPosture ePosture, @NonNull final ESweep eSweep)
  {
    return ePosture != EPosture.ROAMING && eSweep != ESweep.FREE_RANGING;
  }

  /**
   * @return <code>false</code> if the posture is roaming or the sweep is
   *         free-ranging (XSLT 3.0 section 19.7).
   */
  public boolean isStreamable ()
  {
    return isStreamable (posture, sweep);
  }

  /** @return The pair in the form <code>striding/consuming</code>. */
  @Override
  public String toString ()
  {
    return posture.getName () + "/" + sweep.getName ();
  }
}
