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

import com.helger.annotation.concurrent.Immutable;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;

/**
 * The streamed branch of an instruction that can consume the records of a
 * streamed document (<code>xsl:for-each</code>,
 * <code>xsl:apply-templates</code>, <code>xsl:copy-of</code>). The
 * instruction creates it at compile time and asks it first at run time: if a
 * {@link StreamedRun} names the instruction as its consumer, the records are
 * read from the stream and handed to the per-item code of the instruction;
 * otherwise the instruction runs buffered as always.
 *
 * @author Philip Helger
 */
@Immutable
public final class StreamConsumer
{
  // Key of the compile-time set of the instructions that have a streamed branch
  private static final Object EXTENSION_KEY = StreamConsumer.class;

  private final XslElement m_aElement;
  private final StreamRecordPath m_aPath;

  private StreamConsumer (@NonNull final XslElement aElement, @NonNull final StreamRecordPath aPath)
  {
    m_aElement = aElement;
    m_aPath = aPath;
  }

  /**
   * Run the instruction over the records of the live stream, if it is the
   * consumer of the innermost live run.
   *
   * @param aCtx
   *        Context of the instruction.
   * @param aHandler
   *        The per-item code of the instruction.
   * @return <code>true</code> if the records were consumed,
   *         <code>false</code> if the instruction is to run buffered.
   */
  public boolean drive (@NonNull final XsltContext aCtx, @NonNull final IStreamRecordHandler aHandler)
  {
    final StreamedRun aRun = StreamedRun.getActive (aCtx.getEngine ());
    if (aRun == null || aRun.getConsumer () != m_aElement || aRun.isDriven ())
      return false;
    aRun.drive (m_aPath, aHandler);
    return true;
  }

  /**
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The instruction.
   * @param aSelect
   *        Its select expression (the record path).
   * @return The streamed branch, or <code>null</code> if streamed execution
   *         is off or the expression is no record path.
   */
  @Nullable
  public static StreamConsumer create (@NonNull final CompileContext aCtx,
                                       @NonNull final XslElement aEl,
                                       @Nullable final XPathExpr aSelect)
  {
    if (!aCtx.getCompiler ().getOptions ().isStreamingClaim () || aSelect == null)
      return null;
    final StreamRecordPath aPath = StreamRecordPath.of (aSelect.getExpression ());
    if (aPath == null)
      return null;
    final CompiledStylesheet aSheet = aCtx.getStylesheet ();
    @SuppressWarnings ("unchecked")
    Set <XslElement> aConsumers = (Set <XslElement>) aSheet.getExtensionData (EXTENSION_KEY);
    if (aConsumers == null)
    {
      aConsumers = Collections.newSetFromMap (new IdentityHashMap <> ());
      aSheet.putExtensionData (EXTENSION_KEY, aConsumers);
    }
    aConsumers.add (aEl);
    return new StreamConsumer (aEl, aPath);
  }

  /**
   * @param aSheet
   *        The stylesheet.
   * @param aEl
   *        An instruction.
   * @return <code>true</code> if the instruction was compiled with a streamed
   *         branch, so that it can consume the records of a stream.
   */
  public static boolean isConsumer (@NonNull final CompiledStylesheet aSheet, @NonNull final XslElement aEl)
  {
    return aSheet.getExtensionData (EXTENSION_KEY) instanceof final Set <?> aConsumers && aConsumers.contains (aEl);
  }
}
