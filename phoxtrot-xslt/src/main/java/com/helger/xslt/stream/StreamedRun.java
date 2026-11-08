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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import com.helger.xslt.runtime.XsltContext;
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
 * reader: each record (a node selected by a record path) is read in full,
 * handed to the unchanged evaluator, and dropped; everything off the record
 * paths is skipped without being built. Peak memory is the ancestor spine plus
 * one record, plus what a handler retains (an open group). Several record
 * paths are read in the same pass (the prongs of <code>xsl:fork</code>).
 * <p>
 * A streamed <code>xsl:merge-source</code> reads its records one at a time
 * instead ({@link #startPull(StreamRecordPath, XsltContext)},
 * {@link #nextRecord()}), as the merge needs them.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class StreamedRun
{
  private static final Object RUN_DATA_KEY = new Object ();
  private static final AtomicLong STREAMED_RUNS = new AtomicLong (0);
  private static final Map <String, AtomicLong> STREAMED_BY_KIND = new ConcurrentHashMap <> ();

  private final XdmStreamReader m_aReader;
  private final XslElement m_aConsumer;
  private IStreamAccumulators m_aAccumulators;
  private boolean m_bDriven;

  // The cursor over the records
  private List <StreamRecordPath> m_aPaths;
  private XsltContext m_aPathContext;
  // How much of each record path the open element chain matches
  private int [] m_aOnPath;
  // Which paths selected the record last returned
  private boolean [] m_aMatched;
  // The records still held (by the driver and retaining handlers)
  private final Map <IXdmNode, int []> m_aHolds = new IdentityHashMap <> ();
  // The ancestors of held records, with the number of held records below
  private final Map <IXdmNode, int []> m_aAncestorHolds = new IdentityHashMap <> ();
  // Completed ancestors of held records, dropped when the last one is released
  private final Map <IXdmNode, Boolean> m_aExitedHeld = new IdentityHashMap <> ();

  /**
   * @param aReader
   *        The open reader. Its document is the context item of the host.
   * @param aConsumer
   *        The instruction that consumes the records (see
   *        {@link StreamPlanner#findConsumer(XslElement, StreamVerdicts)}), or
   *        <code>null</code> for a run read with {@link #nextRecord()}.
   */
  public StreamedRun (@NonNull final XdmStreamReader aReader, @Nullable final XslElement aConsumer)
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

  /**
   * @param aEngine
   *        The engine.
   * @return The stack of the live runs of the engine, innermost last, kept in
   *         the per-run data of the engine and created empty on first use. A
   *         host nested in a streamed host pushes its own run on top.
   */
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

  /**
   * Remove this run from the stack of live runs of the engine, if it is the
   * innermost one. Otherwise (it was not pushed, or was already popped)
   * nothing happens.
   *
   * @param aEngine
   *        The engine.
   */
  public void pop (@NonNull final Engine aEngine)
  {
    final ArrayDeque <StreamedRun> aStack = _getStack (aEngine);
    if (aStack.peekLast () == this)
      aStack.removeLast ();
  }

  /**
   * @return The number of record sequences consumed streamed in this JVM,
   *         for tests that must tell the streamed from the buffered execution.
   */
  public static long getStreamedRunCount ()
  {
    return STREAMED_RUNS.get ();
  }

  /**
   * @param sKind
   *        The local name of the consuming instruction, e.g.
   *        <code>iterate</code>, <code>for-each-group</code>,
   *        <code>fork</code> or <code>merge</code>.
   * @return The number of record sequences that kind of instruction consumed
   *         streamed in this JVM, for tests that must tell the streamed from
   *         the buffered execution of a particular construct.
   */
  public static long getStreamedCount (@NonNull final String sKind)
  {
    final AtomicLong aCount = STREAMED_BY_KIND.get (sKind);
    return aCount == null ? 0 : aCount.get ();
  }

  /**
   * Count one streamed execution, for {@link #getStreamedRunCount()} and
   * {@link #getStreamedCount(String)}. Called by {@link #drive(List, List, XsltContext, String)}
   * and by an instruction that reads streams with {@link #nextRecord()}.
   *
   * @param sKind
   *        The local name of the consuming instruction.
   */
  public static void countStreamedExecution (@NonNull final String sKind)
  {
    STREAMED_RUNS.incrementAndGet ();
    STREAMED_BY_KIND.computeIfAbsent (sKind, k -> new AtomicLong (0)).incrementAndGet ();
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

  @Nullable
  public XslElement getConsumer ()
  {
    return m_aConsumer;
  }

  /** @return <code>true</code> if the consumer has read the document. */
  public boolean isDriven ()
  {
    return m_bDriven;
  }

  /**
   * @param ex
   *        What the reader raised: the document is not well-formed, ends
   *        inside a record, or exceeds a limit of the reader.
   * @return The dynamic error FODC0002 with the message of the reader.
   */
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

  /** Drop a completed node, unless a held record is below it. */
  private void _dropOrKeep (@NonNull final IXdmNode aNode)
  {
    if (m_aAncestorHolds.containsKey (aNode))
      m_aExitedHeld.put (aNode, Boolean.TRUE);
    else
      _drop (aNode);
  }

  /**
   * Set up the cursor over the records of the given paths, with no element
   * on any path yet, and mark the run as driven: the document of a run is
   * read once only, either by {@link #drive(List, List, XsltContext, String)}
   * or with {@link #startPull(StreamRecordPath, XsltContext)}.
   *
   * @throws IllegalStateException
   *         if the run was already driven or pulled
   */
  private void _startCursor (@NonNull final List <StreamRecordPath> aPaths, @NonNull final XsltContext aPathContext)
  {
    if (m_bDriven)
      throw new IllegalStateException ("The streamed document was already consumed");
    m_bDriven = true;
    m_aPaths = aPaths;
    m_aPathContext = aPathContext;
    m_aOnPath = new int [aPaths.size ()];
    m_aMatched = new boolean [aPaths.size ()];
  }


  /**
   * Apply the predicates of the last step to a candidate record.
   *
   * @return <code>true</code> if at least one path still selects it.
   */
  private boolean _filterRecord (@NonNull final IXdmNode aNode)
  {
    boolean ret = false;
    for (int i = 0; i < m_aMatched.length; ++i)
      if (m_aMatched[i])
      {
        final StreamRecordPath aPath = m_aPaths.get (i);
        final int nLast = aPath.getLength () - 1;
        if (aPath.hasPredicates (nLast) && !aPath.acceptsPredicates (nLast, aNode, m_aPathContext))
          m_aMatched[i] = false;
        else
          ret = true;
      }
    return ret;
  }

  /**
   * Read on to the next record of any path. Which paths selected it is in
   * {@link #m_aMatched}.
   *
   * @return The complete record, or <code>null</code> at the end of the
   *         document.
   */
  @Nullable
  private IXdmNode _next () throws XdmParseException
  {
    final int nPaths = m_aPaths.size ();
    while (true)
    {
      final XdmStreamReader.EEvent eEvent = m_aReader.next ();
      final IXdmNode aNode = m_aReader.getNode ();
      if (eEvent == XdmStreamReader.EEvent.END)
      {
        if (m_aAccumulators != null)
          m_aAccumulators.exit (aNode);
        return null;
      }
      final int nDepth = m_aReader.getDepth ();
      switch (eEvent)
      {
        case ENTER:
        {
          if (m_aAccumulators != null)
            m_aAccumulators.enter (aNode);
          boolean bRecord = false;
          boolean bDeeper = false;
          for (int i = 0; i < nPaths; ++i)
          {
            m_aMatched[i] = false;
            final StreamRecordPath aPath = m_aPaths.get (i);
            final int nLength = aPath.getLength ();
            if (nDepth <= nLength && m_aOnPath[i] == nDepth - 1 && aPath.matches (nDepth - 1, aNode))
            {
              if (nDepth < nLength)
              {
                // Motionless: the start tag decides
                if (!aPath.hasPredicates (nDepth - 1) || aPath.acceptsPredicates (nDepth - 1, aNode, m_aPathContext))
                {
                  m_aOnPath[i] = nDepth;
                  bDeeper = true;
                }
              }
              else
              {
                m_aMatched[i] = true;
                bRecord = true;
              }
            }
          }
          if (bRecord && bDeeper)
            throw new IllegalStateException ("A record of one path contains records of another");
          if (bRecord)
          {
            // A record: read it to its end tag
            _captureTo (aNode);
            if (_filterRecord (aNode))
              return aNode;
            _drop (aNode);
          }
          else
            if (!bDeeper && m_aAccumulators == null)
            {
              // Off the record paths nothing can be a record
              m_aReader.skipSubtree ();
            }
          // With accumulators every node must be seen: the subtree is read
          // and each node dropped when it is complete
          break;
        }
        case EXIT:
          if (m_aAccumulators != null)
            m_aAccumulators.exit (aNode);
          for (int i = 0; i < nPaths; ++i)
            if (m_aOnPath[i] == nDepth)
              m_aOnPath[i] = nDepth - 1;
          _dropOrKeep (aNode);
          break;
        case LEAF:
        {
          if (m_aAccumulators != null)
          {
            m_aAccumulators.enter (aNode);
            m_aAccumulators.exit (aNode);
          }
          boolean bRecord = false;
          for (int i = 0; i < nPaths; ++i)
          {
            final StreamRecordPath aPath = m_aPaths.get (i);
            m_aMatched[i] = nDepth == aPath.getLength () &&
                            m_aOnPath[i] == nDepth - 1 &&
                            aPath.matches (nDepth - 1, aNode);
            bRecord |= m_aMatched[i];
          }
          if (bRecord && _filterRecord (aNode))
            return aNode;
          _drop (aNode);
          break;
        }
        default:
          break;
      }
    }
  }

  /**
   * Hold a record: it and its ancestors stay linked, with their accumulator
   * values, until {@link #release(IXdmNode)} was called as often.
   */
  private void _hold (@NonNull final IXdmNode aRecord, final int nHolds)
  {
    m_aHolds.put (aRecord, new int [] { nHolds });
    for (IXdmNode aCur = aRecord.getParent (); aCur != null; aCur = aCur.getParent ())
      m_aAncestorHolds.computeIfAbsent (aCur, k -> new int [1])[0]++;
  }

  /**
   * Release a record returned by {@link #nextRecord()} or retained by a
   * handler. When nothing holds it any more, it is dropped, and so are its
   * completed ancestors that hold no other record.
   *
   * @param aRecord
   *        The record.
   */
  public void release (@NonNull final IXdmNode aRecord)
  {
    final int [] aCount = m_aHolds.get (aRecord);
    if (aCount != null && --aCount[0] > 0)
      return;
    m_aHolds.remove (aRecord);
    if (aCount == null)
    {
      _drop (aRecord);
      return;
    }
    // The ancestors, bottom-up, before the record is detached
    final ArrayDeque <IXdmNode> aFree = new ArrayDeque <> ();
    for (IXdmNode aCur = aRecord.getParent (); aCur != null; aCur = aCur.getParent ())
    {
      final int [] aBelow = m_aAncestorHolds.get (aCur);
      if (aBelow != null && --aBelow[0] == 0)
      {
        m_aAncestorHolds.remove (aCur);
        if (m_aExitedHeld.remove (aCur) != null)
          aFree.addLast (aCur);
      }
    }
    _drop (aRecord);
    for (final IXdmNode aAncestor : aFree)
      _drop (aAncestor);
  }

  /**
   * Read the document and hand every record of each path to the handler of
   * that path, in document order. Called once, by the consumer.
   *
   * @param aPaths
   *        The record paths, none of which may select nodes within the
   *        records of another ({@link StreamRecordPath#isNestedWith(StreamRecordPath)}).
   * @param aHandlers
   *        One handler per path.
   * @param aPathContext
   *        The context the predicates of the paths are evaluated in.
   * @param sKind
   *        The local name of the consuming instruction, for
   *        {@link #getStreamedCount(String)}.
   * @throws XPathException
   *         FODC0002 if the document cannot be read, or what a handler
   *         raises
   */
  public void drive (@NonNull final List <StreamRecordPath> aPaths,
                     @NonNull final List <? extends IStreamRecordHandler> aHandlers,
                     @NonNull final XsltContext aPathContext,
                     @NonNull final String sKind)
  {
    _startCursor (aPaths, aPathContext);
    countStreamedExecution (sKind);
    final int nHandlers = aHandlers.size ();
    final int [] aPositions = new int [nHandlers];
    try
    {
      while (true)
      {
        final IXdmNode aRecord = _next ();
        if (aRecord == null)
          break;
        // The driver holds the record while the handlers see it, so that a
        // handler can release it at once
        int nHolds = 1;
        for (int i = 0; i < nHandlers; ++i)
          if (m_aMatched[i] && !aHandlers.get (i).isDone () && aHandlers.get (i).isRetaining ())
            nHolds++;
        _hold (aRecord, nHolds);
        boolean bAllDone = true;
        for (int i = 0; i < nHandlers; ++i)
        {
          final IStreamRecordHandler aHandler = aHandlers.get (i);
          if (m_aMatched[i] && !aHandler.isDone ())
            aHandler.handle (aRecord, ++aPositions[i]);
          bAllDone &= aHandler.isDone ();
        }
        release (aRecord);
        // Every handler has stopped (xsl:break): the rest is not read
        if (bAllDone)
          return;
      }
      for (final IStreamRecordHandler aHandler : aHandlers)
        if (!aHandler.isDone ())
          aHandler.complete ();
    }
    catch (final XdmParseException ex)
    {
      throw _readError (ex);
    }
  }

  /**
   * Start reading the records of one path one at a time, with
   * {@link #nextRecord()}.
   *
   * @param aPath
   *        The record path.
   * @param aPathContext
   *        The context its predicates are evaluated in.
   */
  public void startPull (@NonNull final StreamRecordPath aPath, @NonNull final XsltContext aPathContext)
  {
    _startCursor (List.of (aPath), aPathContext);
  }

  /**
   * @return The next complete record, or <code>null</code> at the end of the
   *         document. Pass it to {@link #release(IXdmNode)} before the next
   *         call.
   * @throws XPathException
   *         FODC0002 if the document cannot be read
   */
  @Nullable
  public IXdmNode nextRecord ()
  {
    if (m_aPaths == null)
      throw new IllegalStateException ("startPull was not called");
    try
    {
      return _next ();
    }
    catch (final XdmParseException ex)
    {
      throw _readError (ex);
    }
  }
}
