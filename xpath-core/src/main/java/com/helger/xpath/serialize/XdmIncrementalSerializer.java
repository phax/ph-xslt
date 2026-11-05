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
package com.helger.xpath.serialize;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Serializes a result tree piece by piece while it is built: complete nodes
 * are written with {@link #writeNode(IXdmNode)}, an element whose content is
 * still to come is opened with {@link #startElement(IXdmNode)} and closed with
 * {@link #endElement()}. Fed with the children of a document node in order,
 * it writes the same characters as
 * {@link XdmSerializer#serialize(com.helger.xpath.value.XdmSequence, SerializationParams)}
 * for that document node.
 * <p>
 * Only parameter sets whose output does not depend on content not seen yet
 * are supported ({@link #isSupported(SerializationParams)}). The caller must
 * fix the namespaces of the nodes (namespace fixup) before they are written,
 * and does the whole-tree checks of the output method itself.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmIncrementalSerializer
{
  private final SerializationParams m_aParams;
  private final OutputCharset m_aCharset;
  private final Writer m_aWriter;
  private final StringBuilder m_aSB = new StringBuilder ();
  // null for the text method, which writes no markup
  private final MarkupWriter m_aMarkup;
  private boolean m_bBegun;
  private int m_nOpen;

  /**
   * @param aParams
   *        The serialization parameters. Must be supported.
   * @param aWriter
   *        The target. It is flushed by {@link #finish()}, never closed.
   * @throws XPathException
   *         with the parameter errors of the serializer (SESU0007, SESU0011,
   *         SESU0013, SEPM0009, SEPM0010)
   * @throws IllegalArgumentException
   *         if the parameters are not supported
   */
  public XdmIncrementalSerializer (@NonNull final SerializationParams aParams, @NonNull final Writer aWriter)
  {
    ValueEnforcer.notNull (aParams, "Params");
    ValueEnforcer.notNull (aWriter, "Writer");
    ValueEnforcer.isTrue (isSupported (aParams), "The serialization parameters need the complete result");
    checkParams (aParams);
    m_aParams = aParams;
    m_aCharset = new OutputCharset (aParams);
    m_aWriter = aWriter;
    final String sMethod = aParams.getMethod ();
    m_aMarkup = SerializationParams.METHOD_TEXT.equals (sMethod) ? null
                                                                  : new MarkupWriter (aParams, m_aCharset, m_aSB, sMethod, aWriter);
  }

  /**
   * Decide whether a parameter set can be served incrementally. Rejected is
   * everything that needs the complete result before the first character can
   * be written: indentation and an item separator (they depend on what comes
   * next), a doctype-system or standalone declaration with the xml and xhtml
   * methods (SEPM0004 counts the top-level elements), the Content-Type
   * <code>meta</code> element of the html and xhtml methods (it depends on
   * the children of <code>head</code>), and, for the text method, a character
   * map or Unicode normalisation (applied to the result as a whole there).
   * The json and adaptive methods serialize values, not a tree.
   *
   * @param aParams
   *        The serialization parameters.
   * @return <code>true</code> if the result can be written while it is built.
   */
  public static boolean isSupported (@NonNull final SerializationParams aParams)
  {
    ValueEnforcer.notNull (aParams, "Params");
    if (aParams.isIndent () || aParams.getItemSeparator () != null)
      return false;
    switch (aParams.getMethod ())
    {
      case SerializationParams.METHOD_XML:
        return SerializationParams.STANDALONE_OMIT.equals (aParams.getStandalone ()) && aParams.getDoctypeSystem () == null;
      case SerializationParams.METHOD_XHTML:
        return SerializationParams.STANDALONE_OMIT.equals (aParams.getStandalone ()) &&
               aParams.getDoctypeSystem () == null &&
               !aParams.isIncludeContentType ();
      case SerializationParams.METHOD_HTML:
        return !aParams.isIncludeContentType ();
      case SerializationParams.METHOD_TEXT:
        return !aParams.hasCharacterMap () && "none".equals (aParams.getNormalizationForm ());
      default:
        return false;
    }
  }

  /**
   * Check the parameters as the constructor does, without writing anything.
   *
   * @param aParams
   *        The serialization parameters.
   * @throws XPathException
   *         with the parameter errors of the serializer (SESU0007, SESU0011,
   *         SESU0013, SEPM0009, SEPM0010)
   */
  public static void checkParams (@NonNull final SerializationParams aParams)
  {
    ValueEnforcer.notNull (aParams, "Params");
    XdmSerializer.checkParams (aParams);
    new OutputCharset (aParams);
  }

  private void _flushIfFull ()
  {
    if (m_aSB.length () >= MarkupWriter.FLUSH_SIZE)
      flush ();
  }

  private void _begin ()
  {
    if (m_bBegun)
      return;
    m_bBegun = true;
    if (m_aParams.isByteOrderMark ())
      m_aSB.append ('﻿');
    if (m_aMarkup != null)
      m_aMarkup.writeDeclaration ();
  }

  /**
   * Write a complete node as the next child of the innermost open element,
   * or as a top-level node if none is open.
   *
   * @param aNode
   *        An element, text, comment or processing instruction node. Its
   *        namespaces must be fixed.
   * @throws XPathException
   *         for a serialization error (e.g. SERE0006, SERE0008)
   */
  public void writeNode (@NonNull final IXdmNode aNode)
  {
    ValueEnforcer.notNull (aNode, "Node");
    _begin ();
    if (m_aMarkup != null)
      m_aMarkup.writeNode (aNode);
    else
      if (aNode.getKind () == ENodeKind.ELEMENT || aNode.getKind () == ENodeKind.TEXT)
        XdmSerializer.appendTextMethod (m_aSB, aNode.getStringValue (), m_aCharset);
    _flushIfFull ();
  }

  /**
   * Write the start tag of an element whose children follow. Its name,
   * attributes and namespaces must be final, its namespaces fixed.
   *
   * @param aElement
   *        The element.
   * @throws XPathException
   *         for a serialization error
   */
  public void startElement (@NonNull final IXdmNode aElement)
  {
    ValueEnforcer.isTrue (aElement.getKind () == ENodeKind.ELEMENT, "Not an element");
    _begin ();
    if (m_aMarkup != null)
      m_aMarkup.openElement (aElement);
    m_nOpen++;
    _flushIfFull ();
  }

  /** Write the end tag of the innermost element opened by startElement. */
  public void endElement ()
  {
    if (m_nOpen == 0)
      throw new IllegalStateException ("No element is open");
    m_nOpen--;
    if (m_aMarkup != null)
      m_aMarkup.closeElement ();
    _flushIfFull ();
  }

  /**
   * Write what is buffered to the writer and flush it.
   *
   * @throws UncheckedIOException
   *         on a write error
   */
  public void flush ()
  {
    try
    {
      if (m_aSB.length () > 0)
      {
        m_aWriter.append (m_aSB);
        m_aSB.setLength (0);
      }
      m_aWriter.flush ();
    }
    catch (final IOException ex)
    {
      throw new UncheckedIOException (ex);
    }
  }

  /**
   * Finish the output: write the prolog if nothing was written yet and flush
   * the writer.
   *
   * @throws IllegalStateException
   *         if an element is still open
   */
  public void finish ()
  {
    if (m_nOpen > 0)
      throw new IllegalStateException (m_nOpen + " elements are still open");
    _begin ();
    flush ();
  }
}
