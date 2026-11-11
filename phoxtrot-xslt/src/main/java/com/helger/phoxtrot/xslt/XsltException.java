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
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xslt.tree.XslNode;

/**
 * A static or dynamic XSLT error with its W3C error code (e.g. XTSE0010,
 * XTDE0640) and, where known, the stylesheet location of the instruction it
 * was raised in. An error that ends a transformation also carries the
 * messages the transformation produced until then
 * ({@link #getCollectedMessages()}), and an error raised in the body of an
 * <code>xsl:function</code> names that function ({@link #getFunctionFrame()}).
 *
 * @author Philip Helger
 */
public class XsltException extends XPathException
{
  /**
   * The <code>xsl:function</code> an error was raised in: its name, arity and
   * the location of its declaration.
   *
   * @param name
   *        The function name.
   * @param arity
   *        The number of parameters.
   * @param moduleUri
   *        The URI of the module that declares it, may be <code>null</code>.
   * @param line
   *        1-based line of the <code>xsl:function</code> element, 0 if
   *        unknown.
   * @param column
   *        1-based column of the <code>xsl:function</code> element, 0 if
   *        unknown.
   */
  public record FunctionFrame (@NonNull XdmQName name, int arity, @Nullable String moduleUri, int line, int column)
  {}

  private final String m_sModuleUri;
  private final int m_nLine;
  private final int m_nColumn;
  private final ICommonsList <XsltMessage> m_aCollectedMessages = new CommonsArrayList <> ();
  // The innermost xsl:function the error was raised in, set once
  private FunctionFrame m_aFunctionFrame;

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
   * and so are the collected messages and the function frame of an
   * {@link XsltException}.
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
    {
      m_aCollectedMessages.addAll (aXCause.m_aCollectedMessages);
      m_aFunctionFrame = aXCause.m_aFunctionFrame;
    }
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
   * @return The innermost <code>xsl:function</code> whose body raised this
   *         error (also when the error is reported at the location of the
   *         calling instruction), or <code>null</code> if it was not raised
   *         in a stylesheet function.
   */
  @Nullable
  public final FunctionFrame getFunctionFrame ()
  {
    return m_aFunctionFrame;
  }

  /**
   * Attach the <code>xsl:function</code> whose body raised an error, unless
   * an inner function is already attached. A plain {@link XPathException} is
   * wrapped in an unlocated {@link XsltException} (code, description, error
   * object and cause kept), so that the calling instruction still adds its
   * location. Called by the engine; not meant to be called by users.
   *
   * @param aEx
   *        The error.
   * @param aFrame
   *        The function.
   * @return The error with the frame (possibly the same object).
   */
  @NonNull
  public static XsltException withFunctionFrame (@NonNull final XPathException aEx, @NonNull final FunctionFrame aFrame)
  {
    final XsltException ret = aEx instanceof final XsltException aXEx ? aXEx : new XsltException (aEx, null, 0, 0);
    if (ret.m_aFunctionFrame == null)
      ret.m_aFunctionFrame = aFrame;
    return ret;
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
