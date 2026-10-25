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
package com.helger.xslt;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xpath.XPathException;
import com.helger.xslt.tree.XslNode;

/**
 * A static or dynamic XSLT error with its W3C error code (e.g. XTSE0010,
 * XTDE0640) and, where known, the stylesheet location of the instruction it
 * was raised in.
 *
 * @author Philip Helger
 */
public class XsltException extends XPathException
{
  private final String m_sModuleUri;
  private final int m_nLine;
  private final int m_nColumn;

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
   * raised in. Error code (as QName), description and error object are kept.
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
   * Attach a stylesheet location to an error unless it already has one.
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
    if (aNode == null || aEx instanceof final XsltException aXEx && aXEx.isLocated ())
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
}
