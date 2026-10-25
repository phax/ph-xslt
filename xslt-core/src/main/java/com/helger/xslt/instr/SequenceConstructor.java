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
import com.helger.xpath.XPathException;
import com.helger.xslt.XsltException;
import com.helger.xslt.runtime.Engine;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslNode;

/**
 * A compiled sequence constructor: instructions executed in order. A binding
 * instruction (local variable) changes the context of the following
 * instructions. Every instruction counts against the execution budget, and a
 * dynamic error gets the location of the innermost instruction it passed.
 *
 * @author Philip Helger
 */
@Immutable
public final class SequenceConstructor implements IInstruction
{
  /** The empty sequence constructor. */
  public static final SequenceConstructor EMPTY = new SequenceConstructor (null, new IInstruction [0]);

  private final XslNode m_aLocation;
  private final IInstruction [] m_aInstructions;

  public SequenceConstructor (@Nullable final XslNode aLocation, @NonNull final List <IInstruction> aInstructions)
  {
    this (aLocation, aInstructions.toArray (new IInstruction [0]));
  }

  private SequenceConstructor (@Nullable final XslNode aLocation, @NonNull final IInstruction [] aInstructions)
  {
    m_aLocation = aLocation;
    m_aInstructions = aInstructions;
  }

  @Nullable
  public XslNode getLocation ()
  {
    return m_aLocation;
  }

  /** @return <code>true</code> if there are no instructions. */
  public boolean isEmpty ()
  {
    return m_aInstructions.length == 0;
  }

  /** @return The number of instructions. */
  public int size ()
  {
    return m_aInstructions.length;
  }

  /**
   * @param nIndex
   *        Index.
   * @return The instruction.
   */
  @NonNull
  public IInstruction get (final int nIndex)
  {
    return m_aInstructions[nIndex];
  }

  public void execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final Engine aEngine = aCtx.getEngine ();
    XsltContext aCur = aCtx;
    for (final IInstruction aInstr : m_aInstructions)
    {
      aEngine.step ();
      try
      {
        if (aInstr instanceof final IBindingInstruction aBinding)
          aCur = aBinding.bind (aCur);
        else
          aInstr.execute (aCur, aOut);
      }
      catch (final XPathException ex)
      {
        throw XsltException.locate (ex, aInstr.getLocation () != null ? aInstr.getLocation () : m_aLocation);
      }
    }
  }
}
