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
package com.helger.phoxtrot.xslt.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xslt.compile.CompiledPattern;
import com.helger.phoxtrot.xslt.compile.XPathExpr;
import com.helger.phoxtrot.xslt.instr.SequenceConstructor;
import com.helger.phoxtrot.xslt.tree.XslElement;

/**
 * One compiled xsl:key declaration (XSLT 3.0 section 20.2.1): the match
 * pattern and the key value given either by the use expression or by the
 * sequence constructor. Several declarations may share a name; the compiler
 * requires the declarations of one name within a package to agree in collation
 * (XTSE1220) and composite (XTSE1222).
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

  /** @return The use expression, <code>null</code> if the body gives the value. */
  @Nullable
  public XPathExpr getUse ()
  {
    return m_aUse;
  }

  /** @return The sequence constructor, <code>null</code> if use is given. */
  @Nullable
  public SequenceConstructor getBody ()
  {
    return m_aBody;
  }

  /**
   * @return <code>true</code> for composite="yes": the key value is the whole
   *         sequence of atomic values, not each value on its own.
   */
  public boolean isComposite ()
  {
    return m_bComposite;
  }

  /**
   * @return The absolute collation URI: the collation attribute resolved
   *         against the static base URI, or else the default collation of
   *         the declaration.
   */
  @NonNull
  public String getCollation ()
  {
    return m_sCollation;
  }
}
