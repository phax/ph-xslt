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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.fn.IXPathFunction;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.runtime.SequenceOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;

/**
 * A compiled xsl:function. It is registered in the function library of the
 * stylesheet with its parsed signature; the evaluator converts the arguments,
 * this class runs the body with an absent focus and converts the result to the
 * declared type (XTTE0780).
 *
 * @author Philip Helger
 */
public final class UserFunction implements IXPathFunction
{
  private final XslElement m_aElement;
  private final XdmQName m_aName;
  private final ICommonsList <VariableDef> m_aParams;
  private final SeqType m_aAs;
  private final String m_sVisibility;
  private SequenceConstructor m_aBody = SequenceConstructor.EMPTY;

  public UserFunction (@NonNull final XslElement aElement,
                       @NonNull final XdmQName aName,
                       @NonNull final List <VariableDef> aParams,
                       @Nullable final SeqType aAs,
                       @Nullable final String sVisibility)
  {
    m_aElement = aElement;
    m_aName = aName;
    m_aParams = new CommonsArrayList <> (aParams);
    m_aAs = aAs;
    m_sVisibility = sVisibility;
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

  public int getArity ()
  {
    return m_aParams.size ();
  }

  @NonNull
  public ICommonsList <VariableDef> getParams ()
  {
    return m_aParams.getClone ();
  }

  @Nullable
  public SeqType getAs ()
  {
    return m_aAs;
  }

  /** @return The declared visibility (public, private, final, abstract) or <code>null</code>. */
  @Nullable
  public String getVisibility ()
  {
    return m_sVisibility;
  }

  public void setBody (@NonNull final SequenceConstructor aBody)
  {
    m_aBody = aBody;
  }

  /**
   * Call the function with arguments that were already converted to the
   * parameter types.
   *
   * @param aCaller
   *        The context of the caller.
   * @param aArgs
   *        The arguments.
   * @return The converted result.
   */
  @NonNull
  public XdmSequence invoke (@NonNull final XsltContext aCaller, @NonNull final List <XdmSequence> aArgs)
  {
    XsltContext aCtx = aCaller.withoutFocusAndVariables ().withState (aCaller.getState ().forFunctionCall ());
    for (int i = 0; i < m_aParams.size (); ++i)
      aCtx = aCtx.withVariable (m_aParams.get (i).getName (), aArgs.get (i));
    final SequenceOutput aOut = new SequenceOutput ();
    aCtx.getEngine ().enterDepth ();
    try
    {
      m_aBody.execute (aCtx, aOut);
    }
    finally
    {
      aCtx.getEngine ().leaveDepth ();
    }
    final XdmSequence aResult = aOut.getSequence ();
    if (m_aAs == null)
      return aResult;
    try
    {
      return SequenceTypeMatcher.convert (aResult, m_aAs, aCtx.getDynamicContext (), "XTTE0780");
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, m_aElement);
    }
  }

  @NonNull
  public XdmSequence call (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XsltContext aXCtx = XsltContext.of (aCtx);
    if (aXCtx == null)
      throw new XPathException ("XPST0017",
                                "The stylesheet function " + m_aName.getEQName () + " is not available here");
    return invoke (aXCtx, aArgs);
  }

  @Override
  public String toString ()
  {
    return "function " + m_aName.getEQName () + "#" + m_aParams.size ();
  }
}
