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
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xpath.stream.EPosture;
import com.helger.phoxtrot.xpath.stream.ESweep;
import com.helger.phoxtrot.xpath.stream.PostureSweep;

/**
 * The result of {@link XPathStreamabilityAnalyzer}: posture and sweep (XSLT 3.0 section 19), the simplified verdict
 * and, if the verdict is not motionless, the reason. In {@link EStreamContextMode#BURST} the posture and sweep are
 * those with a streamed context item, and the verdict may be {@link EStreamabilityVerdict#BURST}.
 *
 * @author Philip Helger
 */
@Immutable
public final class XPathStreamabilityResult
{
  private final PostureSweep m_aPostureSweep;
  private final EStreamabilityVerdict m_eVerdict;
  private final XPathStreamabilityReason m_aReason;

  XPathStreamabilityResult (@NonNull final PostureSweep aPostureSweep, @Nullable final XPathStreamabilityReason aReason)
  {
    this (aPostureSweep, EStreamabilityVerdict.getFromPostureSweep (aPostureSweep), aReason);
  }

  XPathStreamabilityResult (@NonNull final PostureSweep aPostureSweep,
                            @NonNull final EStreamabilityVerdict eVerdict,
                            @Nullable final XPathStreamabilityReason aReason)
  {
    m_aPostureSweep = aPostureSweep;
    m_eVerdict = eVerdict;
    m_aReason = aReason;
  }

  /**
   * @return The posture and sweep. Never <code>null</code>.
   */
  @NonNull
  public PostureSweep getPostureSweep ()
  {
    return m_aPostureSweep;
  }

  /**
   * @return The posture. Never <code>null</code>.
   */
  @NonNull
  public EPosture getPosture ()
  {
    return m_aPostureSweep.posture ();
  }

  /**
   * @return The sweep. Never <code>null</code>.
   */
  @NonNull
  public ESweep getSweep ()
  {
    return m_aPostureSweep.sweep ();
  }

  /**
   * @return The simplified verdict. Never <code>null</code>.
   */
  @NonNull
  public EStreamabilityVerdict getVerdict ()
  {
    return m_eVerdict;
  }

  /**
   * @return <code>true</code> if the verdict is motionless or consuming (one pass without buffering).
   */
  public boolean isStreamable ()
  {
    return m_eVerdict == EStreamabilityVerdict.MOTIONLESS || m_eVerdict == EStreamabilityVerdict.CONSUMING;
  }

  /**
   * @return <code>true</code> if the verdict is not free-ranging: motionless, consuming or burst (one pass with the
   *         subtree of the context node buffered).
   */
  public boolean isBurstStreamable ()
  {
    return m_eVerdict != EStreamabilityVerdict.FREE_RANGING;
  }

  /**
   * @return <code>true</code> if the verdict is motionless.
   */
  public boolean isMotionless ()
  {
    return m_eVerdict == EStreamabilityVerdict.MOTIONLESS;
  }

  /**
   * @return The reason for a verdict other than motionless, <code>null</code> for a motionless one. It may also be
   *         <code>null</code> if no construct could be singled out.
   */
  @Nullable
  public XPathStreamabilityReason getReason ()
  {
    return m_aReason;
  }

  @Override
  public String toString ()
  {
    return m_eVerdict + " (" + m_aPostureSweep + ")" + (m_aReason == null ? "" : ": " + m_aReason);
  }
}
