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
package com.helger.xslt.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.compile.CompiledPattern;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.tree.XslElement;

/**
 * One compiled xsl:key declaration. Several declarations may share a name.
 *
 * @author Philip Helger
 */
@Immutable
public final class KeyDefinition
{
  private final XslElement m_aElement;
  private final XdmQName m_aName;
  private final CompiledPattern m_aMatch;
  private final XPathExpr m_aUse;
  private final SequenceConstructor m_aBody;
  private final boolean m_bComposite;
  private final String m_sCollation;

  public KeyDefinition (@NonNull final XslElement aElement,
                        @NonNull final XdmQName aName,
                        @NonNull final CompiledPattern aMatch,
                        @Nullable final XPathExpr aUse,
                        @Nullable final SequenceConstructor aBody,
                        final boolean bComposite,
                        @NonNull final String sCollation)
  {
    m_aElement = aElement;
    m_aName = aName;
    m_aMatch = aMatch;
    m_aUse = aUse;
    m_aBody = aBody;
    m_bComposite = bComposite;
    m_sCollation = sCollation;
  }

  @NonNull
  public XslElement getElement ()
  {
    return m_aElement;
  }

  @NonNull
  public XdmQName getName ()
  {
    return m_aName;
  }

  @NonNull
  public CompiledPattern getMatch ()
  {
    return m_aMatch;
  }

  @Nullable
  public XPathExpr getUse ()
  {
    return m_aUse;
  }

  @Nullable
  public SequenceConstructor getBody ()
  {
    return m_aBody;
  }

  public boolean isComposite ()
  {
    return m_bComposite;
  }

  /** @return The collation URI (the codepoint collation unless given). */
  @NonNull
  public String getCollation ()
  {
    return m_sCollation;
  }
}
