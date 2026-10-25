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
package com.helger.xslt.tree;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.xdm.XdmConstants;

/**
 * A text node of a stylesheet module (adjacent text, also across removed
 * comments and processing instructions, is merged).
 *
 * @author Philip Helger
 */
public final class XslText extends XslNode
{
  private final String m_sValue;
  private final int m_nLine;
  private final int m_nColumn;

  public XslText (@NonNull final XslModule aModule, @NonNull final String sValue, final int nLine, final int nColumn)
  {
    super (aModule);
    m_sValue = sValue;
    m_nLine = nLine;
    m_nColumn = nColumn;
  }

  @NonNull
  public String getValue ()
  {
    return m_sValue;
  }

  @Override
  public int getLine ()
  {
    return m_nLine;
  }

  @Override
  public int getColumn ()
  {
    return m_nColumn;
  }

  /** @return <code>true</code> if the text consists of XML whitespace only. */
  public boolean isWhitespaceOnly ()
  {
    for (int i = 0; i < m_sValue.length (); ++i)
    {
      final char c = m_sValue.charAt (i);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r')
        return false;
    }
    return true;
  }

  /**
   * @return <code>true</code> if the nearest ancestor with an
   *         <code>xml:space</code> attribute says <code>preserve</code>.
   */
  public boolean isXmlSpacePreserve ()
  {
    for (XslElement aCur = getParent (); aCur != null; aCur = aCur.getParent ())
    {
      final String s = aCur.getAttribute (XdmConstants.NS_XML, "space");
      if (s != null)
        return "preserve".equals (s.trim ());
    }
    return false;
  }

  @Override
  public String toString ()
  {
    return "text '" + m_sValue + "'";
  }
}
