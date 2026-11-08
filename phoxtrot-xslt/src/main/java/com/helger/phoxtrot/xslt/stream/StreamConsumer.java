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

import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.compile.XPathExpr;
import com.helger.phoxtrot.xslt.model.CompiledStylesheet;
import com.helger.phoxtrot.xslt.runtime.BufferedOutput;
import com.helger.phoxtrot.xslt.runtime.IXsltOutput;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslNode;
import com.helger.phoxtrot.xslt.tree.XslText;

/**
 * The streamed branch of an instruction that can consume the records of a
 * streamed document (<code>xsl:for-each</code>,
 * <code>xsl:apply-templates</code>, <code>xsl:copy-of</code>,
 * <code>xsl:iterate</code>, <code>xsl:for-each-group</code>,
 * <code>xsl:fork</code>). The instruction creates it at compile time and asks
 * it first at run time: if a {@link StreamedRun} names the instruction as its
 * consumer, the records are read from the stream and handed to the per-item
 * code of the instruction ({@link IStreamHandlerFactory}); otherwise the
 * instruction runs buffered as always.
 * <p>
 * An <code>xsl:fork</code> consumes when each of its prongs is an
 * <code>xsl:sequence</code> made of one consuming instruction (or the fork is
 * one <code>xsl:for-each-group</code>) and no record path selects nodes
 * within the records of another: every prong gets the records of its path in
 * the same pass, writes its result into its own buffer, and the buffers are
 * concatenated in prong order at the end (XSLT 3.0 section 16). Memory grows
 * with the results of the prongs, not with the input. A port of go-xslt's
 * <code>stream_fork.go</code>, which requires one record path for all prongs.
 *
 * @author Philip Helger
 */
@Immutable
public final class StreamConsumer
{
  // Key of the compile-time map of the instructions that have a streamed
  // branch
  private static final Object EXTENSION_KEY = StreamConsumer.class;

  private final XslElement m_aElement;
  // One path and handler factory per population; several for xsl:fork
  private final ICommonsList <StreamRecordPath> m_aPaths;
  private final ICommonsList <IStreamHandlerFactory> m_aFactories;
  private final boolean m_bFork;

  private StreamConsumer (@NonNull final XslElement aElement,
                          @NonNull final ICommonsList <StreamRecordPath> aPaths,
                          @NonNull final ICommonsList <IStreamHandlerFactory> aFactories,
                          final boolean bFork)
  {
    m_aElement = aElement;
    m_aPaths = aPaths;
    m_aFactories = aFactories;
    m_bFork = bFork;
  }

  /**
   * Run the instruction over the records of the live stream, if it is the
   * consumer of the innermost live run.
   *
   * @param aCtx
   *        Context of the instruction.
   * @param aOut
   *        Where the instruction writes its result.
   * @return <code>true</code> if the records were consumed,
   *         <code>false</code> if the instruction is to run buffered.
   */
  public boolean drive (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final StreamedRun aRun = StreamedRun.getActive (aCtx.getEngine ());
    if (aRun == null || aRun.getConsumer () != m_aElement || aRun.isDriven ())
      return false;
    if (!m_bFork)
    {
      aRun.drive (m_aPaths,
                  new CommonsArrayList <> (m_aFactories.getFirstOrNull ().create (aCtx, aOut, aRun)),
                  aCtx,
                  m_aElement.getLocalName ());
      return true;
    }
    // Each prong writes into its own buffer
    final ICommonsList <BufferedOutput> aBuffers = new CommonsArrayList <> ();
    final ICommonsList <IStreamRecordHandler> aHandlers = new CommonsArrayList <> ();
    for (final IStreamHandlerFactory aFactory : m_aFactories)
    {
      final BufferedOutput aBuffer = new BufferedOutput ();
      aBuffers.add (aBuffer);
      aHandlers.add (aFactory.create (aCtx, aBuffer, aRun));
    }
    aRun.drive (m_aPaths, aHandlers, aCtx, m_aElement.getLocalName ());
    for (final BufferedOutput aBuffer : aBuffers)
      aBuffer.replayAll (aOut);
    return true;
  }

  /**
   * @param aSheet
   *        The stylesheet being compiled.
   * @return The identity map from each instruction that has a streamed branch
   *         to that branch, kept in the extension data of the stylesheet and
   *         created empty on first use. Filled by
   *         {@link #create(CompileContext, XslElement, XPathExpr, IStreamHandlerFactory)}
   *         and {@link #createFork(CompileContext, XslElement)}; the latter
   *         also reads the branches of its prongs from it.
   */
  @SuppressWarnings ("unchecked")
  @NonNull
  private static Map <XslElement, StreamConsumer> _getMap (@NonNull final CompiledStylesheet aSheet)
  {
    Map <XslElement, StreamConsumer> ret = (Map <XslElement, StreamConsumer>) aSheet.getExtensionData (EXTENSION_KEY);
    if (ret == null)
    {
      ret = new IdentityHashMap <> ();
      aSheet.putExtensionData (EXTENSION_KEY, ret);
    }
    return ret;
  }

  /**
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The instruction.
   * @param aSelect
   *        Its select expression (the record path).
   * @param aFactory
   *        Creates the per-item code of the instruction for one run.
   * @return The streamed branch, or <code>null</code> if streamed execution
   *         is off or the expression is no record path.
   */
  @Nullable
  public static StreamConsumer create (@NonNull final CompileContext aCtx,
                                       @NonNull final XslElement aEl,
                                       @Nullable final XPathExpr aSelect,
                                       @NonNull final IStreamHandlerFactory aFactory)
  {
    if (!aCtx.getCompiler ().getOptions ().isStreamingClaim ())
      return null;
    final StreamRecordPath aPath = StreamRecordPath.of (aSelect);
    if (aPath == null)
      return null;
    final StreamConsumer ret = new StreamConsumer (aEl,
                                                   new CommonsArrayList <> (aPath),
                                                   new CommonsArrayList <> (aFactory),
                                                   false);
    _getMap (aCtx.getStylesheet ()).put (aEl, ret);
    return ret;
  }

  /**
   * @return The only significant child element of an xsl:sequence prong, or
   *         <code>null</code>.
   */
  @Nullable
  private static XslElement _getProngConsumer (@NonNull final XslElement aProng)
  {
    if (aProng.hasAttr ("select"))
      return null;
    XslElement ret = null;
    for (final XslNode aNode : aProng.getChildren ())
    {
      if (aNode instanceof final XslText aText)
      {
        if (aText.isWhitespaceOnly () && !aText.isXmlSpacePreserve ())
          continue;
        return null;
      }
      if (aNode instanceof final XslElement aChild)
      {
        if (ret != null)
          return null;
        ret = aChild;
      }
    }
    return ret;
  }

  /**
   * Create the streamed branch of an xsl:fork, after its content was
   * compiled.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The xsl:fork element.
   * @return The streamed branch, or <code>null</code> if streamed execution
   *         is off or a prong does not have the shape.
   */
  @Nullable
  public static StreamConsumer createFork (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    if (!aCtx.getCompiler ().getOptions ().isStreamingClaim ())
      return null;
    final Map <XslElement, StreamConsumer> aMap = _getMap (aCtx.getStylesheet ());
    final ICommonsList <StreamRecordPath> aPaths = new CommonsArrayList <> ();
    final ICommonsList <IStreamHandlerFactory> aFactories = new CommonsArrayList <> ();
    for (final XslNode aNode : aEl.getChildren ())
    {
      if (aNode instanceof final XslText aText)
      {
        if (aText.isWhitespaceOnly () && !aText.isXmlSpacePreserve ())
          continue;
        return null;
      }
      final XslElement aChild = (XslElement) aNode;
      if (aChild.isXsl ("fallback"))
        continue;
      final XslElement aConsumer;
      if (aChild.isXsl ("for-each-group"))
        aConsumer = aChild;
      else
        if (aChild.isXsl ("sequence"))
          aConsumer = _getProngConsumer (aChild);
        else
          return null;
      final StreamConsumer aProng = aConsumer == null ? null : aMap.get (aConsumer);
      if (aProng == null || aProng.m_bFork)
        return null;
      aPaths.addAll (aProng.m_aPaths);
      aFactories.addAll (aProng.m_aFactories);
    }
    if (aPaths.isEmpty ())
      return null;
    for (int i = 0; i < aPaths.size (); ++i)
      for (int j = i + 1; j < aPaths.size (); ++j)
        if (aPaths.get (i).isNestedWith (aPaths.get (j)))
          return null;
    final StreamConsumer ret = new StreamConsumer (aEl, aPaths, aFactories, true);
    aMap.put (aEl, ret);
    return ret;
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
    return aSheet.getExtensionData (EXTENSION_KEY) instanceof final Map <?, ?> aConsumers && aConsumers.containsKey (aEl);
  }
}
