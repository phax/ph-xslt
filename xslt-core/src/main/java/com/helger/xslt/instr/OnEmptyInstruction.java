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
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;

/**
 * xsl:on-empty or xsl:on-non-empty (XSLT 3.0 section 8.4). The enclosing
 * sequence constructor is wrapped in a {@link ConditionalContent} that decides
 * whether these are evaluated.
 *
 * @author Philip Helger
 */
@Immutable
public final class OnEmptyInstruction extends AbstractInstruction
{
  private final boolean m_bOnNonEmpty;
  private final XPathExpr m_aSelect;
  private final SequenceConstructor m_aBody;

  public OnEmptyInstruction (@NonNull final XslElement aElement,
                             final boolean bOnNonEmpty,
                             @Nullable final XPathExpr aSelect,
                             @Nullable final SequenceConstructor aBody)
  {
    super (aElement);
    m_bOnNonEmpty = bOnNonEmpty;
    m_aSelect = aSelect;
    m_aBody = aBody;
  }

  /** @return <code>true</code> for xsl:on-non-empty. */
  public boolean isOnNonEmpty ()
  {
    return m_bOnNonEmpty;
  }

  public void execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    if (m_aSelect != null)
      aOut.appendAll (m_aSelect.evaluate (aCtx));
    else
      m_aBody.execute (aCtx, aOut);
  }
}
