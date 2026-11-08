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
package com.helger.phoxtrot.xslt;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xslt.tree.XslNode;

/**
 * A static or dynamic XSLT error with its W3C error code (e.g. XTSE0010,
 * XTDE0640) and, where known, the stylesheet location of the instruction it
 * was raised in. An error that ends a transformation also carries the
 * messages the transformation produced until then
 * ({@link #getCollectedMessages()}).
 *
 * @author Philip Helger
 */
public class XsltException extends XPathException
{
  private final String m_sModuleUri;
  private final int m_nLine;
  private final int m_nColumn;
  private final ICommonsList <XsltMessage> m_aCollectedMessages = new CommonsArrayList <> ();

  /**
   * Create an error with an explicit location.
   *
   * @param sCode
   *        Error code, e.g. XTSE0010.
   * @param sMessage
   *        Message.
   * @param sModuleUri
   *        Module URI or <code>null</code>.
   * @param nLine
   *        1-based line, 0 if unknown.
   * @param nColumn
   *        1-based column, 0 if unknown.
   */
  public XsltException (@NonNull final String sCode,
                        @NonNull final String sMessage,
                        @Nullable final String sModuleUri,
                        final int nLine,
                        final int nColumn)
  {
    super (sCode, sMessage);
    m_sModuleUri = sModuleUri;
    m_nLine = nLine;
    m_nColumn = nColumn;
  }

  /**
   * Wrap an XPath error with the stylesheet location of the instruction it was
   * raised in. Error code (as QName), description and error object are kept,
   * and so are the collected messages of an {@link XsltException}.
   *
   * @param aCause
   *        The original error.
   * @param sModuleUri
   *        Module URI or <code>null</code>.
   * @param nLine
   *        1-based line, 0 if unknown.
   * @param nColumn
   *        1-based column, 0 if unknown.
   */
  public XsltException (@NonNull final XPathException aCause,
                        @Nullable final String sModuleUri,
                        final int nLine,
                        final int nColumn)
  {
    super (aCause.getCodeName (), aCause.getDescription (), aCause.getErrorObject ());
    initCause (aCause);
    m_sModuleUri = sModuleUri;
    m_nLine = nLine;
    m_nColumn = nColumn;
    if (aCause instanceof final XsltException aXCause)
      m_aCollectedMessages.addAll (aXCause.m_aCollectedMessages);
  }

  /**
   * Create an error located at a stylesheet node.
   *
   * @param aNode
   *        The stylesheet node. May be <code>null</code>.
   * @param sCode
   *        Error code, e.g. XTSE0010.
   * @param sMessage
   *        Message.
   * @return The new exception.
   */
  @NonNull
  public static XsltException at (@Nullable final XslNode aNode,
                                  @NonNull final String sCode,
                                  @NonNull final String sMessage)
  {
    if (aNode == null)
      return new XsltException (sCode, sMessage, null, 0, 0);
    return new XsltException (sCode, sMessage, aNode.getModule ().getUri (), aNode.getLine (), aNode.getColumn ());
  }

  /**
   * Attach a stylesheet location to an error unless it already has one. An
   * error without location (an XPath error, or an XsltException that is not
   * located) is wrapped in a new XsltException that keeps its code,
   * description and error object and has it as cause.
   *
   * @param aEx
   *        The error.
   * @param aNode
   *        The stylesheet node. May be <code>null</code>.
   * @return The located error (possibly the same object).
   */
  @NonNull
  public static XPathException locate (@NonNull final XPathException aEx, @Nullable final XslNode aNode)
  {
    if (aNode == null || (aEx instanceof final XsltException aXEx && aXEx.isLocated ()))
      return aEx;
    return new XsltException (aEx, aNode.getModule ().getUri (), aNode.getLine (), aNode.getColumn ());
  }

  /** @return <code>true</code> if a stylesheet location is known. */
  public final boolean isLocated ()
  {
    return m_nLine > 0 || m_sModuleUri != null;
  }

  /** @return The stylesheet module URI or <code>null</code>. */
  @Nullable
  public final String getModuleUri ()
  {
    return m_sModuleUri;
  }

  /** @return The 1-based line, 0 if unknown. */
  public final int getLine ()
  {
    return m_nLine;
  }

  /** @return The 1-based column, 0 if unknown. */
  public final int getColumn ()
  {
    return m_nColumn;
  }

  /**
   * @return A copy of the messages (<code>xsl:message</code> and failed
   *         <code>xsl:assert</code>, see {@link XsltMessage}) the
   *         transformation produced before it failed with this error, in the
   *         order they were produced; a terminating message is the last one.
   *         When the error comes from a transformation started by
   *         <code>fn:transform</code>, the messages of the calling
   *         transformation come first, followed by those of the nested one.
   *         Empty for compile errors and for errors not thrown by
   *         <code>XsltExecutable.transform</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public final ICommonsList <XsltMessage> getCollectedMessages ()
  {
    return m_aCollectedMessages.getClone ();
  }

  /**
   * Add the messages of the transformation this error ends, in front of those
   * already attached (by a nested transformation the error comes from).
   * Called by the engine when the error leaves a transformation; not meant
   * to be called by users.
   *
   * @param aMessages
   *        The messages of the transformation, in the order they were
   *        produced.
   */
  public final void internalAddOuterMessages (@NonNull final List <XsltMessage> aMessages)
  {
    m_aCollectedMessages.addAll (0, aMessages);
  }
}
