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
