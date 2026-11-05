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

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonnegative;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Processes one record of a streamed document (see
 * {@link StreamedRun#drive(StreamRecordPath, IStreamRecordHandler)}).
 *
 * @author Philip Helger
 */
@FunctionalInterface
public interface IStreamRecordHandler
{
  /**
   * @param aRecord
   *        The record, complete with its subtree and linked to its ancestors.
   *        It is dropped after this call.
   * @param nPosition
   *        Its 1-based position among the records.
   */
  void handle (@NonNull IXdmNode aRecord, @Nonnegative int nPosition);
}
