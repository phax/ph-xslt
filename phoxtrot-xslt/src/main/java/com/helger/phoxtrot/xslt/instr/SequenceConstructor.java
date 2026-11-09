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
package com.helger.phoxtrot.xslt.instr;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xslt.IXsltTraceListener;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.XsltTraceInstruction;
import com.helger.phoxtrot.xslt.runtime.Engine;
import com.helger.phoxtrot.xslt.runtime.IXsltOutput;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslNode;

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

  /**
   * Describe an instruction for the trace listener.
   *
   * @param aInstr
   *        The instruction.
   * @param aOwner
   *        The location of the sequence constructor that contains it, may be
   *        <code>null</code>.
   * @return The name and location of the element of the instruction, or
   *         <code>null</code> if it is not traced: literal text, and an
   *         instruction located at the owner of its sequence constructor (a
   *         text value template, or the wrapper of a sequence constructor with
   *         xsl:on-empty), which is no element of its own.
   */
  @Nullable
  static XsltTraceInstruction getTraceInstruction (@NonNull final IInstruction aInstr, @Nullable final XslNode aOwner)
  {
    if (aInstr.getLocation () instanceof final XslElement aEl && aEl != aOwner)
      return new XsltTraceInstruction (aEl.getName (), aEl.getModule ().getUri (), aEl.getLine (), aEl.getColumn ());
    return null;
  }

  /**
   * {@link #execute(XsltContext, IXsltOutput)} with a trace listener for
   * instructions: each traced instruction (see
   * {@link #getTraceInstruction(IInstruction, XslNode)}) is enclosed in its
   * enter and leave calls, after its step was counted; the leave call also
   * happens when the instruction raises an error.
   *
   * @param aCtx
   *        The context.
   * @param aOut
   *        Where the result goes.
   * @param aTrace
   *        The trace listener.
   */
  private void _executeTraced (@NonNull final XsltContext aCtx,
                               @NonNull final IXsltOutput aOut,
                               @NonNull final IXsltTraceListener aTrace)
  {
    final Engine aEngine = aCtx.getEngine ();
    XsltContext aCur = aCtx;
    for (final IInstruction aInstr : m_aInstructions)
    {
      aEngine.step ();
      final XsltTraceInstruction aInfo = getTraceInstruction (aInstr, m_aLocation);
      if (aInfo != null)
        aTrace.onInstructionEnter (aInfo);
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
      finally
      {
        if (aInfo != null)
          aTrace.onInstructionLeave (aInfo);
      }
    }
  }

  /**
   * Run the instructions in order. Before each instruction one step is
   * counted against the execution budget of the engine. A binding instruction
   * (local variable) writes nothing; the context it returns is used for all
   * following instructions. A dynamic error raised by an instruction is
   * located at that instruction, or at the sequence constructor if the
   * instruction has no location; an error that already has a location keeps
   * it (see {@link XsltException#locate}). When the engine has a trace
   * listener for instructions, the instructions are traced (see
   * {@link #_executeTraced(XsltContext, IXsltOutput, IXsltTraceListener)}).
   */
  public void execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final Engine aEngine = aCtx.getEngine ();
    final IXsltTraceListener aTrace = aEngine.getInstructionTraceListener ();
    if (aTrace != null)
    {
      _executeTraced (aCtx, aOut, aTrace);
      return;
    }
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
