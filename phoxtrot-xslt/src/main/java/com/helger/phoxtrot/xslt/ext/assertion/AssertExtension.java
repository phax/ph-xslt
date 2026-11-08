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
package com.helger.phoxtrot.xslt.ext.assertion;

import org.jspecify.annotations.NonNull;

import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;
import com.helger.phoxtrot.xpath.serialize.XdmSerializer;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.compile.Avt;
import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.compile.IXsltExtension;
import com.helger.phoxtrot.xslt.compile.XPathExpr;
import com.helger.phoxtrot.xslt.compile.XsltExtensionRegistry;
import com.helger.phoxtrot.xslt.instr.CompiledInstruction;
import com.helger.phoxtrot.xslt.instr.IInstruction;
import com.helger.phoxtrot.xslt.instr.SequenceConstructor;
import com.helger.phoxtrot.xslt.runtime.BufferedOutput;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XsltNames;

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

  /**
   * Build the text of the assertion message from its value: adjacent atomic
   * values are joined with a single space (their lexical forms); attribute
   * and namespace nodes contribute their string value; other nodes are
   * serialised as XML without an XML declaration; any other item (maps,
   * arrays, functions) is serialised with the adaptive method.
   *
   * @param aValue
   *        The value of select followed by the result of the content.
   * @return The message text. Never <code>null</code>, may be empty.
   */
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
      if (aItem instanceof final IXdmNode aNode)
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

  /**
   * Compile xsl:assert (XSLT 3.0 section 23.2). At run time the test is
   * evaluated for its effective boolean value; if true, nothing happens and
   * the result is empty. Otherwise the value of select (if present) and the
   * result of the content are collected in that order, the message text
   * (see {@link #_messageText}) is recorded with the engine like an
   * xsl:message, and a dynamic error is raised whose error object is the
   * collected value and whose description is the message text (or
   * "xsl:assert failed" when it is empty). The error code is XTMM9001 unless
   * error-code is given: its value is whitespace-trimmed and resolved as an
   * EQName against the in-scope namespaces of the instruction; a value that
   * is not a valid EQName or uses an undeclared prefix falls back to
   * XTMM9001, like xsl:message falls back to its default code (XSLT 3.0
   * section 23.1). A dynamic error while evaluating the test makes the
   * assertion fail exactly like a false test (section 23.2).
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The xsl:assert element.
   * @return The compiled instruction. Never <code>null</code>.
   */
  @NonNull
  private static IInstruction _compileAssert (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aTest = aCtx.compileRequiredExpression (aEl, "test");
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    final Avt aErrorCode = aCtx.compileOptionalAvt (aEl, "error-code");
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      boolean bPassed;
      try
      {
        bPassed = aTest.evaluateBoolean (aRtCtx);
      }
      catch (final XPathException ex)
      {
        // A dynamic error in the test is an assertion failure
        bPassed = false;
      }
      if (bPassed)
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
        if (aResolved != null)
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
