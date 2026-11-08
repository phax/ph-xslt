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

import com.helger.annotation.concurrent.Immutable;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;

/**
 * An instruction whose behaviour is given as a function, typically a lambda
 * capturing the compiled parts (expressions, templates, sequence
 * constructors). Most instructions need nothing else.
 *
 * @author Philip Helger
 */
@Immutable
public final class CompiledInstruction extends AbstractInstruction
{
  private final IInstructionBody m_aBody;

  public CompiledInstruction (@NonNull final XslElement aElement, @NonNull final IInstructionBody aBody)
  {
    super (aElement);
    m_aBody = aBody;
  }

  public void execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    m_aBody.execute (aCtx, aOut);
  }

  @Override
  public String toString ()
  {
    return "instruction " + getElement ();
  }
}
