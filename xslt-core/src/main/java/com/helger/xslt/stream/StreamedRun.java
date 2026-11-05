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

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmDocumentNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmStreamReader;
import com.helger.xslt.runtime.Engine;
import com.helger.xslt.tree.XslElement;

/**
 * The live state of one streamed document: the reader, the instruction that
 * consumes the records, and the accumulators computed while reading. A port of
 * go-xslt's <code>strbRun</code> (<code>internal/xslt/stream_exec.go</code>).
 * <p>
 * The host (a streamable <code>xsl:source-document</code>, or the initial
 * template rule of a streamable mode) pushes the run and executes its
 * sequence constructor unchanged, with the live document node as context item.
 * The consumer named by the run ({@link StreamConsumer}) then drives the
 * reader: each record (a node selected by the record path) is read in full,
 * handed to the unchanged evaluator, and dropped; everything off the record
 * path is skipped without being built. Peak memory is the ancestor spine plus
 * one record.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class StreamedRun
{
  private static final Object RUN_DATA_KEY = new Object ();
  private static final AtomicLong STREAMED_RUNS = new AtomicLong (0);

  private final XdmStreamReader m_aReader;
  private final XslElement m_aConsumer;
  private IStreamAccumulators m_aAccumulators;
  private boolean m_bDriven;

  /**
   * @param aReader
   *        The open reader. Its document is the context item of the host.
   * @param aConsumer
   *        The instruction that consumes the records (see
   *        {@link StreamPlanner#findConsumer(XslElement)}).
   */
  public StreamedRun (@NonNull final XdmStreamReader aReader, @NonNull final XslElement aConsumer)
  {
    m_aReader = aReader;
    m_aConsumer = aConsumer;
  }

  /**
   * @param aAccumulators
   *        The accumulators computed while the document is read, or
   *        <code>null</code> for none. Set before the run is driven.
   */
  public void setAccumulators (@Nullable final IStreamAccumulators aAccumulators)
  {
    m_aAccumulators = aAccumulators;
  }

  @NonNull
  private static ArrayDeque <StreamedRun> _getStack (@NonNull final Engine aEngine)
  {
    return aEngine.getRunData (RUN_DATA_KEY, ArrayDeque::new);
  }

  /**
   * @param aEngine
   *        The engine.
   * @return The innermost live run, or <code>null</code>.
   */
  @Nullable
  public static StreamedRun getActive (@NonNull final Engine aEngine)
  {
    return _getStack (aEngine).peekLast ();
  }

  /**
   * Make this run the innermost live run of the engine. Pair with
   * {@link #pop(Engine)} in a finally block.
   *
   * @param aEngine
   *        The engine.
   */
  public void push (@NonNull final Engine aEngine)
  {
    _getStack (aEngine).addLast (this);
  }

  public void pop (@NonNull final Engine aEngine)
  {
    final ArrayDeque <StreamedRun> aStack = _getStack (aEngine);
    if (aStack.peekLast () == this)
      aStack.removeLast ();
  }

  /**
   * @return The number of records sequences consumed streamed in this JVM,
   *         for tests that must tell the streamed from the buffered execution.
   */
  public static long getStreamedRunCount ()
  {
    return STREAMED_RUNS.get ();
  }

  /**
   * @param aNode
   *        A node.
   * @return <code>true</code> if it belongs to a document that is read by a
   *         stream reader: a question about the whole document cannot be
   *         answered from its tree.
   */
  public static boolean isStreamedNode (@Nullable final IXdmNode aNode)
  {
    return aNode != null && aNode.getRoot () instanceof final XdmDocumentNode aDoc && aDoc.isStreamed ();
  }

  @NonNull
  public XdmStreamReader getReader ()
  {
    return m_aReader;
  }

  @NonNull
  public XslElement getConsumer ()
  {
    return m_aConsumer;
  }

  /** @return <code>true</code> if the consumer has read the document. */
  public boolean isDriven ()
  {
    return m_bDriven;
  }

  @NonNull
  private static XPathException _readError (@NonNull final XdmParseException ex)
  {
    return new XPathException ("FODC0002", "The streamed document cannot be read: " + ex.getMessage ());
  }

  /**
   * Read on until the end tag of a record, keeping everything inside it, and
   * compute the accumulators of the nodes read.
   */
  private void _captureTo (@NonNull final IXdmNode aRecord) throws XdmParseException
  {
    while (true)
    {
      final XdmStreamReader.EEvent eEvent = m_aReader.next ();
      if (eEvent == XdmStreamReader.EEvent.END)
        throw new XdmParseException ("Unexpected end of document inside a record");
      final IXdmNode aNode = m_aReader.getNode ();
      if (m_aAccumulators != null)
      {
        if (eEvent != XdmStreamReader.EEvent.EXIT)
          m_aAccumulators.enter (aNode);
        if (eEvent != XdmStreamReader.EEvent.ENTER)
          m_aAccumulators.exit (aNode);
      }
      if (eEvent == XdmStreamReader.EEvent.EXIT && aNode == aRecord)
        return;
    }
  }

  /** Drop a completed node, with its accumulator values. */
  private void _drop (@NonNull final IXdmNode aNode)
  {
    if (m_aAccumulators != null)
      m_aAccumulators.forget (aNode);
    m_aReader.drop (aNode);
  }

  /**
   * Read the document and hand every record to the handler, in document order.
   * Called once, by the consumer.
   *
   * @param aPath
   *        The record path.
   * @param aHandler
   *        Processes one record.
   * @throws XPathException
   *         FODC0002 if the document cannot be read, or what the handler
   *         raises
   */
  public void drive (@NonNull final StreamRecordPath aPath, @NonNull final IStreamRecordHandler aHandler)
  {
    if (m_bDriven)
      throw new IllegalStateException ("The streamed document was already consumed");
    m_bDriven = true;
    STREAMED_RUNS.incrementAndGet ();
    final int nLength = aPath.getLength ();
    // How much of the record path the open element chain matches
    int nOnPath = 0;
    int nPosition = 0;
    try
    {
      while (true)
      {
        final XdmStreamReader.EEvent eEvent = m_aReader.next ();
        final IXdmNode aNode = m_aReader.getNode ();
        if (eEvent == XdmStreamReader.EEvent.END)
        {
          if (m_aAccumulators != null)
            m_aAccumulators.exit (aNode);
          return;
        }
        final int nDepth = m_aReader.getDepth ();
        switch (eEvent)
        {
          case ENTER:
            if (m_aAccumulators != null)
              m_aAccumulators.enter (aNode);
            if (nDepth <= nLength && nOnPath == nDepth - 1 && aPath.matches (nDepth - 1, aNode))
            {
              if (nDepth < nLength)
              {
                nOnPath = nDepth;
                continue;
              }
              // A record: read it to its end tag, process it, drop it
              _captureTo (aNode);
              aHandler.handle (aNode, ++nPosition);
              _drop (aNode);
            }
            else
              if (m_aAccumulators == null)
              {
                // Off the record path nothing can be a record
                m_aReader.skipSubtree ();
              }
            // With accumulators every node must be seen: the subtree is read
            // and each node dropped when it is complete
            break;
          case EXIT:
            if (m_aAccumulators != null)
              m_aAccumulators.exit (aNode);
            if (nOnPath == nDepth)
              nOnPath = nDepth - 1;
            _drop (aNode);
            break;
          case LEAF:
            if (m_aAccumulators != null)
            {
              m_aAccumulators.enter (aNode);
              m_aAccumulators.exit (aNode);
            }
            if (nDepth == nLength && nOnPath == nDepth - 1 && aPath.matches (nDepth - 1, aNode))
              aHandler.handle (aNode, ++nPosition);
            _drop (aNode);
            break;
          default:
            break;
        }
      }
    }
    catch (final XdmParseException ex)
    {
      throw _readError (ex);
    }
  }
}
