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
package com.helger.xslt.ext.fork;

import org.jspecify.annotations.NonNull;

import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.instr.CompiledInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.tree.XslElement;

/**
 * xsl:fork (XSLT 3.0 section 16). Without streaming the result is the
 * concatenation of the results of its xsl:sequence (or single
 * xsl:for-each-group) children, which is what evaluating the content as a
 * sequence constructor gives. The content model is checked by the core.
 *
 * @author Philip Helger
 */
public final class ForkExtension implements IXsltExtension
{
  @NonNull
  private static IInstruction _compileFork (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    return new CompiledInstruction (aEl, aBody::execute);
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("fork", ForkExtension::_compileFork);
  }
}
