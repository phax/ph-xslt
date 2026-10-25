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
package com.helger.xpath.xdm;

import org.jspecify.annotations.NonNull;

/**
 * Thrown if a well-formed source document is not valid against the schema
 * given to {@link XdmParser#setSchema(javax.xml.validation.Schema)}.
 *
 * @author Philip Helger
 */
public class XdmValidationException extends XdmParseException
{
  private final boolean m_bDocumentConstraint;

  public XdmValidationException (@NonNull final String sMessage, @NonNull final Throwable aCause)
  {
    this (sMessage, aCause, false);
  }

  /**
   * @param sMessage
   *        Message.
   * @param aCause
   *        Cause.
   * @param bDocumentConstraint
   *        <code>true</code> if a document-level constraint (ID uniqueness,
   *        IDREF target) is violated.
   */
  public XdmValidationException (@NonNull final String sMessage,
                                 @NonNull final Throwable aCause,
                                 final boolean bDocumentConstraint)
  {
    super (sMessage, aCause);
    m_bDocumentConstraint = bDocumentConstraint;
  }

  /**
   * @return <code>true</code> if a document-level constraint (ID uniqueness,
   *         IDREF target) is violated, as opposed to the validity of a node.
   */
  public boolean isDocumentConstraint ()
  {
    return m_bDocumentConstraint;
  }
}
