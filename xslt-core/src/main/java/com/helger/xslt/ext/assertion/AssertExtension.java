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
package com.helger.xslt.ext.assertion;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.XPathException;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.serialize.XdmSerializer;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.compile.Avt;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.instr.CompiledInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.runtime.BufferedOutput;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * xsl:assert (XSLT 3.0 section 23.2). Assertions are always enabled: a false
 * test raises the dynamic error XTMM9001 (or the code named by error-code)
 * whose error object is the constructed message.
 *
 * @author Philip Helger
 */
public final class AssertExtension implements IXsltExtension
{
  private static final XdmQName XTMM9001 = new XdmQName (XPathException.ERR_NAMESPACE, "XTMM9001", "err");

  @NonNull
  private static String _messageText (@NonNull final XdmSequence aValue)
  {
    final SerializationParams aParams = new SerializationParams ();
    aParams.setOmitXmlDeclaration (true);
    final StringBuilder aSB = new StringBuilder ();
    boolean bLastAtomic = false;
    for (final IXdmItem aItem : aValue)
    {
      if (aItem instanceof final XdmAtomic aAtomic)
      {
        if (bLastAtomic)
          aSB.append (' ');
        aSB.append (aAtomic.getLexical ());
        bLastAtomic = true;
        continue;
      }
      bLastAtomic = false;
      if (aItem instanceof final XdmNode aNode)
      {
        if (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE)
          aSB.append (aNode.getValue ());
        else
          aSB.append (XdmSerializer.serialize (XdmSequence.of (aNode), aParams));
      }
      else
      {
        final SerializationParams aAdaptive = new SerializationParams ();
        aAdaptive.setMethod (SerializationParams.METHOD_ADAPTIVE);
        aSB.append (XdmSerializer.serialize (XdmSequence.of (aItem), aAdaptive));
      }
    }
    return aSB.toString ();
  }

  @NonNull
  private static IInstruction _compileAssert (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aTest = aCtx.compileRequiredExpression (aEl, "test");
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    final Avt aErrorCode = aCtx.compileOptionalAvt (aEl, "error-code");
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      if (aTest.evaluateBoolean (aRtCtx))
        return;
      final BufferedOutput aContent = new BufferedOutput ();
      if (aSelect != null)
        aContent.appendAll (aSelect.evaluate (aRtCtx));
      aBody.execute (aRtCtx, aContent);
      final XdmSequence aValue = aContent.getSequence ();
      final String sMessage = _messageText (aValue);
      aRtCtx.getEngine ().addMessage (sMessage);
      XdmQName aCode = XTMM9001;
      if (aErrorCode != null)
      {
        final String s = XsltNames.trimXmlWhitespace (aErrorCode.evaluate (aRtCtx));
        final XdmQName aResolved = XsltNames.isEQName (s) ? aEl.resolveQName (s, false) : null;
        if (aResolved == null)
          throw new XPathException ("XTDE0030", "error-code='" + s + "' is not a valid EQName");
        aCode = aResolved;
      }
      throw new XPathException (aCode, sMessage.isEmpty () ? "xsl:assert failed" : sMessage, aValue);
    });
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("assert", AssertExtension::_compileAssert);
  }
}
