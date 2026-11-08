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
package com.helger.phoxtrot.xslt.stream;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonnegative;
import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * Processes the records of a streamed document, in document order (see
 * {@link StreamedRun#drive(java.util.List, java.util.List, com.helger.phoxtrot.xslt.runtime.XsltContext, String)}).
 * The records of one handler are its population: a consuming instruction, or
 * one prong of an <code>xsl:fork</code>.
 *
 * @author Philip Helger
 */
public interface IStreamRecordHandler
{
  /**
   * @param aRecord
   *        The record, complete with its subtree and linked to its ancestors.
   *        It is dropped after this call, unless the handler
   *        {@link #isRetaining() retains} it.
   * @param nPosition
   *        Its 1-based position among the records of this handler.
   */
  void handle (@NonNull IXdmNode aRecord, @Nonnegative int nPosition);

  /**
   * @return <code>true</code> if the handler wants no more records (after
   *         <code>xsl:break</code>). Asked after every record.
   */
  default boolean isDone ()
  {
    return false;
  }

  /**
   * @return <code>true</code> if the handler keeps the records it is given
   *         beyond {@link #handle(IXdmNode, int)} (a group that is still open)
   *         and releases each of them itself with
   *         {@link StreamedRun#release(IXdmNode)}. A record and its ancestors
   *         stay linked, with their accumulator values, until then.
   */
  default boolean isRetaining ()
  {
    return false;
  }

  /**
   * Called once at the end of the document, unless the handler is
   * {@link #isDone() done}: what the instruction owes after its last item
   * (<code>xsl:on-completion</code>, the last group).
   */
  default void complete ()
  {}
}
