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
package com.helger.xpath;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;

/**
 * An XPath/XQuery/F&amp;O error with its W3C error code (for example
 * <code>XPTY0004</code>, <code>FORG0001</code> or <code>XPST0003</code>). The
 * code is the local part of the error QName in the namespace
 * <code>http://www.w3.org/2005/xqt-errors</code>. This is an unchecked
 * exception so it can cross lambdas freely.
 *
 * @author Philip Helger
 */
public class XPathException extends RuntimeException
{
  public static final String ERR_NAMESPACE = "http://www.w3.org/2005/xqt-errors";

  private final String m_sCode;
  private XdmQName m_aCodeName;
  private final String m_sDescription;
  private final XdmSequence m_aErrorObject;

  public XPathException (@NonNull final String sCode, @NonNull final String sMessage)
  {
    super (sCode + ": " + sMessage);
    m_sCode = sCode;
    m_sDescription = sMessage;
    m_aErrorObject = null;
  }

  public XPathException (@NonNull final String sCode, @NonNull final String sMessage, @Nullable final Throwable aCause)
  {
    super (sCode + ": " + sMessage, aCause);
    m_sCode = sCode;
    m_sDescription = sMessage;
    m_aErrorObject = null;
  }

  /**
   * An error raised by <code>fn:error</code>: the code may be in any
   * namespace, and the description and error object are kept.
   *
   * @param aCodeName
   *        The error code QName.
   * @param sDescription
   *        The description, may be <code>null</code>.
   * @param aErrorObject
   *        The error object, may be <code>null</code>.
   */
  public XPathException (@NonNull final XdmQName aCodeName,
                         @Nullable final String sDescription,
                         @Nullable final XdmSequence aErrorObject)
  {
    super (aCodeName.localName () + (sDescription == null || sDescription.isEmpty () ? "" : ": " + sDescription));
    m_sCode = aCodeName.localName ();
    m_aCodeName = aCodeName;
    m_sDescription = sDescription;
    m_aErrorObject = aErrorObject;
  }

  /**
   * @return The error code local part, e.g. <code>XPTY0004</code>.
   */
  @NonNull
  public final String getCode ()
  {
    return m_sCode;
  }

  /**
   * @return The error code as QName in the standard error namespace.
   */
  @NonNull
  public final XdmQName getCodeName ()
  {
    if (m_aCodeName == null)
      m_aCodeName = new XdmQName (ERR_NAMESPACE, m_sCode, "err");
    return m_aCodeName;
  }

  /**
   * @return The description of the error (the message without the code), may
   *         be <code>null</code> for an <code>fn:error</code> call without
   *         description.
   */
  @Nullable
  public final String getDescription ()
  {
    return m_sDescription;
  }

  /**
   * @return The error object passed as third argument of
   *         <code>fn:error</code>, or <code>null</code> if there is none.
   */
  @Nullable
  public final XdmSequence getErrorObject ()
  {
    return m_aErrorObject;
  }

  /** Static syntax error XPST0003. */
  @NonNull
  public static XPathException syntax (@NonNull final String sMessage)
  {
    return new XPathException ("XPST0003", sMessage);
  }

  /** Type error XPTY0004. */
  @NonNull
  public static XPathException type (@NonNull final String sMessage)
  {
    return new XPathException ("XPTY0004", sMessage);
  }

  /** Invalid lexical value FORG0001 (casting, constructors). */
  @NonNull
  public static XPathException invalidValue (@NonNull final String sMessage)
  {
    return new XPathException ("FORG0001", sMessage);
  }

  /** Context item absent XPDY0002. */
  @NonNull
  public static XPathException noContext (@NonNull final String sMessage)
  {
    return new XPathException ("XPDY0002", sMessage);
  }

  /** Any other code. */
  @NonNull
  public static XPathException of (@NonNull final String sCode, @NonNull final String sMessage)
  {
    return new XPathException (sCode, sMessage);
  }
}
