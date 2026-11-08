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
package com.helger.phoxtrot.xslt.ext.fork;

import org.jspecify.annotations.NonNull;

import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.compile.IXsltExtension;
import com.helger.phoxtrot.xslt.compile.XsltExtensionRegistry;
import com.helger.phoxtrot.xslt.instr.CompiledInstruction;
import com.helger.phoxtrot.xslt.instr.IInstruction;
import com.helger.phoxtrot.xslt.instr.SequenceConstructor;
import com.helger.phoxtrot.xslt.stream.StreamConsumer;
import com.helger.phoxtrot.xslt.tree.XslElement;

/**
 * xsl:fork (XSLT 3.0 section 16). Without streaming the result is the
 * concatenation of the results of its xsl:sequence (or single
 * xsl:for-each-group) children, which is what evaluating the content as a
 * sequence constructor gives. Over the records of a streamed document every
 * prong reads the same pass ({@link StreamConsumer#createFork}). The content
 * model is checked by the core.
 *
 * @author Philip Helger
 */
public final class ForkExtension implements IXsltExtension
{
  /**
   * Compile xsl:fork. The content is compiled as an ordinary sequence
   * constructor, so outside streaming the prongs run one after the other and
   * their results are concatenated in order. If the streaming analysis made
   * this xsl:fork the consumer of a streamed document
   * ({@link StreamConsumer#createFork} returns non-<code>null</code>), the
   * instruction first tries to drive all prongs over one pass of the live
   * stream and falls back to the buffered sequence constructor only when the
   * records are not consumed that way at run time.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The xsl:fork element.
   * @return The compiled instruction. Never <code>null</code>.
   */
  @NonNull
  private static IInstruction _compileFork (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    // Over the records of a streamed document, if it is the consumer
    final StreamConsumer aStreamed = StreamConsumer.createFork (aCtx, aEl);
    if (aStreamed == null)
      return new CompiledInstruction (aEl, aBody::execute);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      if (!aStreamed.drive (aRtCtx, aRtOut))
        aBody.execute (aRtCtx, aRtOut);
    });
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("fork", ForkExtension::_compileFork);
  }
}
