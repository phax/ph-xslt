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
package com.helger.phoxtrot.xslt.runtime;

import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;
import com.helger.phoxtrot.xpath.serialize.XdmIncrementalSerializer;
import com.helger.phoxtrot.xpath.value.IXdmFunction;
import com.helger.phoxtrot.xpath.value.XdmMap;

/**
 * Writes the principal result tree while it is built and releases every part
 * that can no longer change, so that the memory used does not grow with the
 * size of the result.
 * <p>
 * The engine builds elements bottom-up: an instruction creates the element,
 * fills it through its own {@link ContentOutput} and appends it to the
 * enclosing output when it is complete. The elements under construction form
 * a chain from the document node down to the innermost one (the levels here),
 * each detached from its parent until it is complete. Only the innermost level
 * receives content. A level is committed (its start tag written) when it gets
 * its first child or a child level: XTDE0410 forbids attributes and namespaces
 * after that, so the start tag is final. Of the children of the innermost
 * committed level all but the last are written and detached as they arrive
 * (the last may be a text node that still grows); when a child level is
 * opened, all children before it are. When a committed level is complete, its
 * remaining children and its end tag are written. An element whose
 * instruction changes its content after it was added (inherit-namespaces="no",
 * validation) is no level: it is built in full and written as one node, like
 * everything constructed in a temporary output (variables, xsl:try,
 * xsl:where-populated and others).
 * <p>
 * Serialization errors are raised at the end, as in the serialization of a
 * complete tree, so that a dynamic error later in the run takes precedence;
 * nothing is written after the first one.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class ResultDrain
{
  private static final AtomicLong DRAINED_RUNS = new AtomicLong (0);

  /** An element (or the document node) under construction. */
  private static final class Level
  {
    private final ContentOutput m_aContent;
    private boolean m_bCommitted;

    Level (@NonNull final ContentOutput aContent)
    {
      m_aContent = aContent;
    }
  }

  private final SerializationParams m_aParams;
  private final XdmIncrementalSerializer m_aSerializer;
  private final boolean m_bText;
  private final boolean m_bHtml;
  private final ContentOutput m_aDocument;
  private final ICommonsList <Level> m_aLevels = new CommonsArrayList <> ();
  // The level whose element is being appended to its parent
  private Level m_aClosing;
  private XPathException m_aError;

  /**
   * @param aParams
   *        The serialization parameters, supported by
   *        {@link XdmIncrementalSerializer#isSupported(SerializationParams)}.
   * @param aWriter
   *        The target.
   * @throws XPathException
   *         for invalid serialization parameters
   */
  ResultDrain (@NonNull final SerializationParams aParams, @NonNull final Writer aWriter)
  {
    m_aParams = aParams;
    m_aSerializer = new XdmIncrementalSerializer (aParams, aWriter);
    m_bText = SerializationParams.METHOD_TEXT.equals (aParams.getMethod ());
    m_bHtml = SerializationParams.METHOD_HTML.equals (aParams.getMethod ());
    m_aDocument = new ContentOutput (IXdmNode.createDocument (), this);
    final Level aDocLevel = new Level (m_aDocument);
    aDocLevel.m_bCommitted = true;
    m_aLevels.add (aDocLevel);
    DRAINED_RUNS.incrementAndGet ();
  }

  /**
   * @return The number of transformations so far whose principal result was
   *         written while it was built (in this JVM). For tests that need to
   *         know which path ran: the output is the same either way.
   */
  public static long getDrainedRunCount ()
  {
    return DRAINED_RUNS.get ();
  }

  /**
   * @param aItem
   *        An item of the principal result.
   * @return <code>true</code> if sequence normalisation rejects it (SENR0001):
   *         an attribute or namespace node, a map or a function item.
   */
  static boolean isNormalizationError (@NonNull final IXdmItem aItem)
  {
    return (aItem instanceof final IXdmNode aNode && (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE)) ||
           aItem instanceof XdmMap ||
           aItem instanceof IXdmFunction;
  }

  /** @return The output the principal result is written to. */
  @NonNull
  IXsltOutput getOutput ()
  {
    return m_aDocument;
  }

  @NonNull
  private Level _getInnermost ()
  {
    return m_aLevels.getLastOrNull ();
  }

  /**
   * Serialise one complete node (with its subtree) of the result. Unless the
   * output method is text, the namespace fixup runs on it first; for the html
   * method the content checks of {@link SerializationChecks} run on the whole
   * subtree. A serialisation error is recorded, not thrown (it is raised by
   * {@link #finish()}); after the first error nothing more is written.
   *
   * @param aNode
   *        The node to write.
   */
  private void _write (@NonNull final IXdmNode aNode)
  {
    if (m_aError != null)
      return;
    try
    {
      if (!m_bText)
        NamespaceFixup.fixNamespaces (aNode);
      if (m_bHtml)
        SerializationChecks.checkHtmlContent (m_aParams, aNode, true);
      m_aSerializer.writeNode (aNode);
    }
    catch (final XPathException ex)
    {
      m_aError = ex;
    }
  }

  /**
   * Commit a level: mark it committed and write the start tag of its element,
   * after the namespace fixup of that element alone (not for the text method)
   * and the html checks of the element itself. Does nothing for a level that
   * is already committed; after an earlier error the level is only marked.
   * A serialisation error is recorded as in {@link #_write(IXdmNode)}.
   *
   * @param aLevel
   *        The level to commit.
   */
  private void _commit (@NonNull final Level aLevel)
  {
    if (aLevel.m_bCommitted)
      return;
    aLevel.m_bCommitted = true;
    if (m_aError != null)
      return;
    final IXdmNode aElement = aLevel.m_aContent.getParent ();
    try
    {
      if (!m_bText)
        NamespaceFixup.fixElement (aElement);
      if (m_bHtml)
        SerializationChecks.checkHtmlContent (m_aParams, aElement, false);
      m_aSerializer.startElement (aElement);
    }
    catch (final XPathException ex)
    {
      m_aError = ex;
    }
  }

  /**
   * Write the end tag of the innermost started element, unless an error was
   * recorded (which it records in the same way).
   */
  private void _endElement ()
  {
    if (m_aError != null)
      return;
    try
    {
      m_aSerializer.endElement ();
    }
    catch (final XPathException ex)
    {
      m_aError = ex;
    }
  }

  /**
   * Write and detach the children of a level, all or all but the last.
   */
  private void _writeChildren (@NonNull final Level aLevel, final boolean bAll)
  {
    final IXdmNode aParent = aLevel.m_aContent.getParent ();
    final int nKeep = bAll ? 0 : 1;
    while (aParent.getChildCount () > nKeep)
    {
      final IXdmNode aChild = aParent.getChildAtIndex (0);
      _write (aChild);
      aParent.removeChild (aChild);
    }
    if (bAll)
      aLevel.m_aContent.forgetLastText ();
  }

  /**
   * An instruction starts an element that it appends to the innermost level
   * when complete.
   *
   * @param aParent
   *        The output the element is appended to.
   * @param aElement
   *        The new element.
   * @return The output for its content, or <code>null</code> if the parent is
   *         not the innermost level.
   */
  @Nullable
  ContentOutput openElement (@NonNull final ContentOutput aParent, @NonNull final IXdmNode aElement)
  {
    final Level aInnermost = _getInnermost ();
    if (aInnermost.m_aContent != aParent || m_aClosing != null)
      return null;
    // Nothing else is appended to the parent before the element
    _commit (aInnermost);
    _writeChildren (aInnermost, true);
    final ContentOutput ret = new ContentOutput (aElement, this);
    m_aLevels.add (new Level (ret));
    return ret;
  }

  /**
   * Called before content is appended to a level.
   *
   * @param aContent
   *        The output of the level.
   * @param aItem
   *        The item, <code>null</code> for text.
   * @return <code>false</code> if the item is to be dropped (the result is in
   *         error).
   * @throws IllegalStateException
   *         if the content does not arrive in the order the drain relies on
   */
  boolean beforeAppend (@NonNull final ContentOutput aContent, @Nullable final IXdmItem aItem)
  {
    if (aContent == m_aDocument && aItem != null && isNormalizationError (aItem))
    {
      // Raised at the end, as when the complete result is normalised
      if (m_aError == null)
        m_aError = new XPathException ("SENR0001",
                                       "The principal result contains an attribute or namespace node, a map or a function");
      return false;
    }
    final Level aInnermost = _getInnermost ();
    if (aInnermost.m_aContent == aContent)
      return true;
    final int nLevels = m_aLevels.size ();
    if (nLevels >= 2 && m_aLevels.get (nLevels - 2).m_aContent == aContent && aItem == aInnermost.m_aContent.getParent ())
    {
      // The innermost element is complete
      m_aClosing = m_aLevels.removeLastOrNull ();
      return true;
    }
    throw new IllegalStateException ("The principal result is not built in the order its incremental serialization relies on");
  }

  /**
   * Called after content was appended to the innermost level.
   *
   * @param aContent
   *        The output of the level.
   */
  void afterAppend (@NonNull final ContentOutput aContent)
  {
    final Level aInnermost = _getInnermost ();
    if (aInnermost.m_aContent != aContent)
      throw new IllegalStateException ("Content was appended to an element that is not the innermost one under construction");
    final IXdmNode aParent = aInnermost.m_aContent.getParent ();
    final Level aClosed = m_aClosing;
    if (aClosed != null)
    {
      m_aClosing = null;
      if (aClosed.m_bCommitted)
      {
        // Its start tag is written: the rest follows, and it is released
        final IXdmNode aElement = aClosed.m_aContent.getParent ();
        if (aParent.getChildCount () != 1 || aParent.getChildAtIndex (0) != aElement)
          throw new IllegalStateException ("The completed element is not the only child of its unwritten parent");
        _writeChildren (aClosed, true);
        _endElement ();
        aParent.removeChild (aElement);
        return;
      }
    }
    if (aParent.getChildCount () > 0)
    {
      _commit (aInnermost);
      _writeChildren (aInnermost, false);
    }
  }

  /**
   * Write what is left of the result and flush the writer.
   *
   * @throws XPathException
   *         the first serialization error of the result
   */
  void finish ()
  {
    if (m_aLevels.size () != 1 || m_aClosing != null)
      throw new IllegalStateException ((m_aLevels.size () - 1) + " elements of the principal result are not complete");
    _writeChildren (m_aLevels.getFirstOrNull (), true);
    if (m_aError != null)
    {
      flushQuietly ();
      throw m_aError;
    }
    m_aSerializer.finish ();
  }

  /** Write what was serialized so far, after an error. */
  void flushQuietly ()
  {
    try
    {
      m_aSerializer.flush ();
    }
    catch (final UncheckedIOException ex)
    {
      // The original error is reported
    }
  }
}
