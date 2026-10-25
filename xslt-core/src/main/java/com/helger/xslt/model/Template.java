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

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.compile.CompiledPattern;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.tree.XslElement;

/**
 * A compiled xsl:template: a template rule (match pattern) and/or a named
 * template. Filled by the compiler and immutable afterwards.
 *
 * @author Philip Helger
 */
public final class Template
{
  /** The xsl:context-item declaration of a template. */
  public record ContextItemDecl (@Nullable SeqType as, @NonNull String use)
  {}

  private final XslElement m_aElement;
  private final CompiledPattern m_aPattern;
  private final XdmQName m_aName;
  private final ICommonsList <XdmQName> m_aModes;
  private final boolean m_bAllModes;
  private final Double m_aPriority;
  private final int m_nPrecedence;
  private final int m_nImportLo;
  private final ICommonsList <VariableDef> m_aParams = new CommonsArrayList <> ();
  private final SeqType m_aAs;
  private ContextItemDecl m_aContextItem;
  private SequenceConstructor m_aBody = SequenceConstructor.EMPTY;

  public Template (@NonNull final XslElement aElement,
                   @Nullable final CompiledPattern aPattern,
                   @Nullable final XdmQName aName,
                   @NonNull final ICommonsList <XdmQName> aModes,
                   final boolean bAllModes,
                   @Nullable final Double aPriority,
                   @Nullable final SeqType aAs)
  {
    m_aElement = aElement;
    m_aPattern = aPattern;
    m_aName = aName;
    m_aModes = aModes;
    m_bAllModes = bAllModes;
    m_aPriority = aPriority;
    m_nPrecedence = aElement.getPrecedence ();
    m_nImportLo = aElement.getImportLo ();
    m_aAs = aAs;
  }

  @NonNull
  public XslElement getElement ()
  {
    return m_aElement;
  }

  @Nullable
  public CompiledPattern getPattern ()
  {
    return m_aPattern;
  }

  @Nullable
  public XdmQName getName ()
  {
    return m_aName;
  }

  /** @return The modes of a template rule ({@link Mode#UNNAMED} for the unnamed one). */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XdmQName> getModes ()
  {
    return m_aModes.getClone ();
  }

  /** @return <code>true</code> for mode="#all". */
  public boolean isAllModes ()
  {
    return m_bAllModes;
  }

  /** @return The explicit priority or <code>null</code>. */
  @Nullable
  public Double getPriority ()
  {
    return m_aPriority;
  }

  public int getPrecedence ()
  {
    return m_nPrecedence;
  }

  public int getImportLo ()
  {
    return m_nImportLo;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <VariableDef> getParams ()
  {
    return m_aParams.getClone ();
  }

  /** @return The parameters without copying (do not modify). */
  @NonNull
  public ICommonsList <VariableDef> params ()
  {
    return m_aParams;
  }

  public void addParam (@NonNull final VariableDef aParam)
  {
    m_aParams.add (aParam);
  }

  @Nullable
  public SeqType getAs ()
  {
    return m_aAs;
  }

  @Nullable
  public ContextItemDecl getContextItem ()
  {
    return m_aContextItem;
  }

  public void setContextItem (@Nullable final ContextItemDecl aContextItem)
  {
    m_aContextItem = aContextItem;
  }

  @NonNull
  public SequenceConstructor getBody ()
  {
    return m_aBody;
  }

  public void setBody (@NonNull final SequenceConstructor aBody)
  {
    m_aBody = aBody;
  }

  @Override
  public String toString ()
  {
    return "template" +
           (m_aName != null ? " name=" + m_aName.getEQName () : "") +
           (m_aPattern != null ? " match=" + m_aPattern.pattern ().getSource () : "");
  }
}
