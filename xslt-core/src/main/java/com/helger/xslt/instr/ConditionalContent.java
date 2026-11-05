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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xslt.XsltException;
import com.helger.xslt.runtime.BufferedOutput;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslNode;

/**
 * A sequence constructor containing xsl:on-empty / xsl:on-non-empty (XSLT 3.0
 * section 8.4): the other instructions run first into buffers; if all their
 * items are vacuous (XSLT 3.0 section 8.4.2), only the xsl:on-empty
 * instructions contribute, otherwise everything except the xsl:on-empty
 * instructions.
 *
 * @author Philip Helger
 */
@Immutable
public final class ConditionalContent implements IInstruction
{
  private final XslNode m_aLocation;
  private final IInstruction [] m_aParts;

  /**
   * @param aLocation
   *        The element whose sequence constructor this is, for error
   *        locations. May be <code>null</code>.
   * @param aParts
   *        All instructions of the sequence constructor in order, the
   *        {@link OnEmptyInstruction}s and the binding instructions included.
   */
  public ConditionalContent (@Nullable final XslNode aLocation, @NonNull final List <IInstruction> aParts)
  {
    m_aLocation = aLocation;
    m_aParts = aParts.toArray (new IInstruction [0]);
  }

  /**
   * @param aItem
   *        An item.
   * @return <code>true</code> if the item is vacuous (XSLT 3.0 section
   *         8.4.2): a zero-length text node, a document node without
   *         children, an atomic value whose string value is zero-length, or an
   *         array whose members contain only vacuous items (an empty array
   *         included). Every other item, including an empty element and an
   *         empty map, is not vacuous. This is not the "deemed empty" test of
   *         xsl:where-populated (section 8.4.1).
   */
  public static boolean isDeemedEmpty (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final IXdmNode aNode)
    {
      if (aNode.isText ())
        return aNode.getValue ().isEmpty ();
      if (aNode.isDocument ())
        return aNode.getChildCount () == 0;
      return false;
    }
    if (aItem instanceof final XdmAtomic aAtomic)
      return aAtomic.getLexical ().isEmpty ();
    if (aItem instanceof final XdmArray aArray)
    {
      for (final XdmSequence aMember : aArray.members ())
        for (final IXdmItem aMemberItem : aMember)
          if (!isDeemedEmpty (aMemberItem))
            return false;
      return true;
    }
    return false;
  }

  /**
   * Evaluate the sequence constructor in two passes (XSLT 3.0 sections 8.4.2
   * and 8.4.3). First every ordinary instruction runs, in order, into a
   * buffer of its own; binding instructions update the context as usual, and
   * the context at the position of every instruction is remembered. Every
   * instruction counts one step against the execution budget. Then the result
   * is written in document order: if any buffered item is not vacuous (see
   * {@link #isDeemedEmpty(IXdmItem)}), the buffers are replayed and the
   * xsl:on-non-empty instructions are evaluated in their place; otherwise the
   * buffers are discarded and only the xsl:on-empty instructions are
   * evaluated. A conditional instruction runs with the variables in scope at
   * its position. Dynamic errors are located at the failing instruction, or
   * at the sequence constructor if the instruction has no location.
   */
  public void execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final ICommonsList <BufferedOutput> aBuffers = new CommonsArrayList <> ();
    final ICommonsList <XsltContext> aContexts = new CommonsArrayList <> ();
    boolean bEmpty = true;
    XsltContext aCur = aCtx;
    for (final IInstruction aPart : m_aParts)
    {
      aCtx.getEngine ().step ();
      final BufferedOutput aBuffer = new BufferedOutput ();
      aBuffers.add (aBuffer);
      aContexts.add (aCur);
      try
      {
        if (aPart instanceof final IBindingInstruction aBinding)
          aCur = aBinding.bind (aCur);
        else
          if (!(aPart instanceof OnEmptyInstruction))
          {
            aPart.execute (aCur, aBuffer);
            for (int i = 0; i < aBuffer.size () && bEmpty; ++i)
              if (!isDeemedEmpty (aBuffer.get (i)))
                bEmpty = false;
          }
      }
      catch (final XPathException ex)
      {
        throw XsltException.locate (ex, aPart.getLocation () != null ? aPart.getLocation () : m_aLocation);
      }
    }
    for (int i = 0; i < m_aParts.length; ++i)
    {
      final IInstruction aPart = m_aParts[i];
      if (aPart instanceof final OnEmptyInstruction aMarker)
      {
        // Evaluated only when its condition holds, with the variables in
        // scope at its position
        if (aMarker.isOnNonEmpty () != bEmpty)
          try
          {
            aMarker.execute (aContexts.get (i), aOut);
          }
          catch (final XPathException ex)
          {
            throw XsltException.locate (ex, aMarker.getLocation ());
          }
      }
      else
        if (!bEmpty)
          aBuffers.get (i).replayAll (aOut);
    }
  }

  @Nullable
  public XslNode getLocation ()
  {
    return m_aLocation;
  }
}
