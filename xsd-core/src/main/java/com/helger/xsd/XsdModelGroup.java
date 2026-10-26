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

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * A sequence, choice or all group - go-xslt <code>modelGroup</code>.
 *
 * @author Philip Helger
 */
final class XsdModelGroup implements IXsdTerm
{
  EXsdCompositor m_eCompositor = EXsdCompositor.SEQUENCE;
  ICommonsList <XsdParticle> m_aParticles = new CommonsArrayList <> ();
  /** The group's msg:message entries: own wording for a cardinality error of its members. */
  ICommonsList <XsdMessage> m_aMsgs = new CommonsArrayList <> ();

  XsdModelGroup ()
  {}

  XsdModelGroup (@NonNull final EXsdCompositor eCompositor)
  {
    m_eCompositor = eCompositor;
  }

  /** Copy every field of another group into this one (Go <code>*g = *o</code>). */
  void assignFrom (@NonNull final XsdModelGroup aOther)
  {
    m_eCompositor = aOther.m_eCompositor;
    m_aParticles = aOther.m_aParticles;
    m_aMsgs = aOther.m_aMsgs;
  }

  /** @return A shallow copy. */
  @NonNull
  XsdModelGroup getShallowCopy ()
  {
    final XsdModelGroup ret = new XsdModelGroup ();
    ret.assignFrom (this);
    return ret;
  }

  /**
   * @return The stand-in for a model group reference that resolves to nothing:
   *         it admits any content.
   */
  @NonNull
  static XsdModelGroup absentGroup ()
  {
    final XsdModelGroup ret = new XsdModelGroup (EXsdCompositor.SEQUENCE);
    final XsdWildcard aWC = new XsdWildcard ();
    aWC.m_sNsMode = XsdWildcard.NS_ANY;
    aWC.m_sProcess = XsdWildcard.PROCESS_SKIP;
    ret.m_aParticles.add (new XsdParticle (0, XsdConstants.UNBOUNDED, aWC));
    return ret;
  }

  @Override
  public String toString ()
  {
    return "XsdModelGroup[" + m_eCompositor.getID () + " " + m_aParticles.size () + "]";
  }
}
