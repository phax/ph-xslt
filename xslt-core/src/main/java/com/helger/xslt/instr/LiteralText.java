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
package com.helger.xslt.instr;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslNode;

/**
 * Fixed text of a sequence constructor (literal text or xsl:text): produces a
 * text node.
 *
 * @author Philip Helger
 */
@Immutable
public final class LiteralText implements IInstruction
{
  private final XslNode m_aLocation;
  private final String m_sText;
  private final boolean m_bDisableOutputEscaping;

  public LiteralText (@Nullable final XslNode aLocation, @NonNull final String sText)
  {
    this (aLocation, sText, false);
  }

  public LiteralText (@Nullable final XslNode aLocation, @NonNull final String sText, final boolean bDisableOutputEscaping)
  {
    m_aLocation = aLocation;
    m_sText = sText;
    m_bDisableOutputEscaping = bDisableOutputEscaping;
  }

  @NonNull
  public String getText ()
  {
    return m_sText;
  }

  public void execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final IXdmNode aText = IXdmNode.createText (m_sText);
    if (m_bDisableOutputEscaping)
      aText.setDisableOutputEscaping (true);
    aOut.appendNew (aText);
  }

  @Nullable
  public XslNode getLocation ()
  {
    return m_aLocation;
  }
}
