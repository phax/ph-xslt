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
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.runtime.ContentOutput;
import com.helger.xslt.runtime.Engine;
import com.helger.xslt.runtime.SequenceOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;

/**
 * A compiled xsl:variable, xsl:param or xsl:with-param (XSLT 3.0 section 9):
 * name, value from select or content, required type.
 *
 * @author Philip Helger
 */
@Immutable
public final class VariableDef
{
  /** Where the definition appears. */
  public enum EKind
  {
    GLOBAL_VARIABLE ("XTTE0570"),
    GLOBAL_PARAM ("XTTE0590"),
    LOCAL_VARIABLE ("XTTE0570"),
    TEMPLATE_PARAM ("XTTE0590"),
    FUNCTION_PARAM ("XTTE0790"),
    WITH_PARAM ("XTTE0590"),
    /** Parameters of other constructs (xsl:iterate, ...) */
    OTHER_PARAM ("XTTE0590");

    private final String m_sTypeErrorCode;

    EKind (@NonNull final String sTypeErrorCode)
    {
      m_sTypeErrorCode = sTypeErrorCode;
    }

    /** @return The error code for a value that does not match the type. */
    @NonNull
    public String getTypeErrorCode ()
    {
      return m_sTypeErrorCode;
    }

    public boolean isParam ()
    {
      return this != GLOBAL_VARIABLE && this != LOCAL_VARIABLE && this != WITH_PARAM;
    }
  }

  private final XslElement m_aElement;
  private final EKind m_eKind;
  private final XdmQName m_aName;
  private final XPathExpr m_aSelect;
  private final SequenceConstructor m_aBody;
  private final SeqType m_aAs;
  private final boolean m_bTunnel;
  private final boolean m_bRequired;

  public VariableDef (@NonNull final XslElement aElement,
                      @NonNull final EKind eKind,
                      @NonNull final XdmQName aName,
                      @Nullable final XPathExpr aSelect,
                      @Nullable final SequenceConstructor aBody,
                      @Nullable final SeqType aAs,
                      final boolean bTunnel,
                      final boolean bRequired)
  {
    m_aElement = aElement;
    m_eKind = eKind;
    m_aName = aName;
    m_aSelect = aSelect;
    m_aBody = aBody;
    m_aAs = aAs;
    m_bTunnel = bTunnel;
    m_bRequired = bRequired;
  }

  @NonNull
  public XslElement getElement ()
  {
    return m_aElement;
  }

  @NonNull
  public EKind getKind ()
  {
    return m_eKind;
  }

  @NonNull
  public XdmQName getName ()
  {
    return m_aName;
  }

  @Nullable
  public XPathExpr getSelect ()
  {
    return m_aSelect;
  }

  @Nullable
  public SequenceConstructor getBody ()
  {
    return m_aBody;
  }

  /** @return The declared type or <code>null</code> if there is none. */
  @Nullable
  public SeqType getAs ()
  {
    return m_aAs;
  }

  public boolean isTunnel ()
  {
    return m_bTunnel;
  }

  /**
   * @return <code>true</code> if the parameter must be supplied: required="yes",
   *         or an <code>as</code> type that does not allow the empty sequence
   *         with no default value.
   */
  public boolean isRequired ()
  {
    return m_bRequired;
  }

  /**
   * Convert a value to the declared type (function conversion rules).
   *
   * @param aValue
   *        Value.
   * @param aCtx
   *        Context.
   * @return The converted value.
   * @throws com.helger.xpath.XPathException
   *         with the type error code of the kind.
   */
  @NonNull
  public XdmSequence convert (@NonNull final XdmSequence aValue, @NonNull final XsltContext aCtx)
  {
    if (m_aAs == null)
      return aValue;
    return SequenceTypeMatcher.convert (aValue, m_aAs, aCtx.getDynamicContext (), m_eKind.getTypeErrorCode ());
  }

  /**
   * Evaluate the value from select or content (for a parameter: its default
   * value) and convert it to the declared type.
   *
   * @param aCtx
   *        The context of the definition.
   * @return The value.
   */
  @NonNull
  public XdmSequence evaluate (@NonNull final XsltContext aCtx)
  {
    final XdmSequence aValue;
    if (m_aSelect != null)
      aValue = m_aSelect.evaluate (aCtx);
    else
      if (m_aBody != null && !m_aBody.isEmpty ())
      {
        // A sequence constructor body is evaluated in temporary output state
        final XsltContext aBodyCtx = Engine.withTemporaryOutputState (aCtx);
        if (m_aAs != null)
        {
          final SequenceOutput aOut = new SequenceOutput ();
          m_aBody.execute (aBodyCtx, aOut);
          aValue = aOut.getSequence ();
        }
        else
        {
          // A temporary tree (XSLT 3.0 section 9.4)
          final IXdmNode aDoc = IXdmNode.createDocument ();
          final String sBase = m_aElement.getStaticBaseUri ();
          if (sBase != null)
            aDoc.setBaseUri (sBase);
          final ContentOutput aOut = new ContentOutput (aDoc);
          m_aBody.execute (aBodyCtx, aOut);
          aOut.finishTree ();
          aValue = XdmSequence.of (aDoc);
        }
      }
      else
        aValue = m_aAs != null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.string (""));
    return convert (aValue, aCtx);
  }

  @Override
  public String toString ()
  {
    return m_eKind + " $" + m_aName.getEQName ();
  }
}
