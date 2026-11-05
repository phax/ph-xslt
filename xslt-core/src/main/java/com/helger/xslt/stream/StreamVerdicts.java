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
package com.helger.xslt.stream;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.tree.XslElement;

/**
 * What the streamability analysis ({@link XsltStreamability}) proved
 * guaranteed streamable: the input of streamed execution. Filled once at the
 * end of compilation and only read afterwards.
 * <p>
 * Fail closed: a declaration the analysis rejected (a template rule of a
 * streamable mode, a declared-streamable function or accumulator, a streamable
 * <code>xsl:merge-source</code>) makes the verdicts of every construct
 * unusable, because the analysis of one construct trusts the declarations of
 * the others. That can only happen when streamability is not enforced
 * (with enforcement the compilation fails instead).
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class StreamVerdicts
{
  private final ICommonsSet <XdmQName> m_aStreamableModes = new CommonsHashSet <> ();
  private final ICommonsSet <XdmQName> m_aStreamableAccumulators = new CommonsHashSet <> ();
  private final Set <XslElement> m_aSourceDocuments = Collections.newSetFromMap (new IdentityHashMap <> ());
  private final Set <XslElement> m_aMotionlessWrappers = Collections.newSetFromMap (new IdentityHashMap <> ());
  private final Set <XslElement> m_aStreamedMerges = Collections.newSetFromMap (new IdentityHashMap <> ());
  private boolean m_bDeclarationsFailed;

  void addStreamableMode (@NonNull final XdmQName aMode)
  {
    m_aStreamableModes.add (aMode);
  }

  void addStreamableAccumulator (@NonNull final XdmQName aName)
  {
    m_aStreamableAccumulators.add (aName);
  }

  void addSourceDocument (@NonNull final XslElement aEl)
  {
    m_aSourceDocuments.add (aEl);
  }

  void addMotionlessWrapper (@NonNull final XslElement aEl)
  {
    m_aMotionlessWrappers.add (aEl);
  }

  void addStreamedMerge (@NonNull final XslElement aEl)
  {
    m_aStreamedMerges.add (aEl);
  }

  /**
   * Record that the analysis rejected a declaration (see the class
   * description): from now on every query of these verdicts answers
   * <code>false</code>, whatever was added before or after.
   */
  void setDeclarationsFailed ()
  {
    m_bDeclarationsFailed = true;
  }

  /**
   * @param aMode
   *        Mode name.
   * @return <code>true</code> if the mode is declared streamable and every
   *         template rule of it is guaranteed streamable.
   */
  public boolean isModeStreamable (@Nullable final XdmQName aMode)
  {
    return !m_bDeclarationsFailed && aMode != null && m_aStreamableModes.contains (aMode);
  }

  /**
   * @param aName
   *        Accumulator name.
   * @return <code>true</code> if the accumulator is declared streamable and
   *         guaranteed streamable (section 18.2.9).
   */
  public boolean isAccumulatorStreamable (@Nullable final XdmQName aName)
  {
    return !m_bDeclarationsFailed && aName != null && m_aStreamableAccumulators.contains (aName);
  }

  /**
   * @param aEl
   *        An <code>xsl:source-document</code> element.
   * @return <code>true</code> if it is declared streamable and its body is
   *         guaranteed streamable and grounded.
   */
  public boolean isSourceDocumentStreamable (@Nullable final XslElement aEl)
  {
    return !m_bDeclarationsFailed && aEl != null && m_aSourceDocuments.contains (aEl);
  }

  /**
   * @param aEl
   *        A literal result element.
   * @return <code>true</code> if it is a wrapper around the consumer of a
   *         streamed body and every expression in its attribute value
   *         templates is motionless with the document node as context item:
   *         evaluated before any record is read, it gives the answer of the
   *         complete document.
   */
  public boolean isMotionlessWrapper (@Nullable final XslElement aEl)
  {
    return !m_bDeclarationsFailed && aEl != null && m_aMotionlessWrappers.contains (aEl);
  }

  /**
   * @param aEl
   *        An <code>xsl:merge</code> element.
   * @return <code>true</code> if its streamable merge sources (proven
   *         guaranteed streamable, section 15.4) may be read as streams: the
   *         merge action does not call <code>fn:last</code>, since the number
   *         of merge groups is not known while the inputs are read.
   */
  public boolean isStreamedMerge (@Nullable final XslElement aEl)
  {
    return !m_bDeclarationsFailed && aEl != null && m_aStreamedMerges.contains (aEl);
  }
}
