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
package com.helger.xslt.ext.map;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.XPathException;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.instr.CompiledInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.runtime.SequenceOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XslText;

/**
 * The map construction instructions xsl:map and xsl:map-entry (XSLT 3.0
 * section 21.4).
 *
 * @author Philip Helger
 */
public final class MapExtension implements IXsltExtension
{
  private static boolean _hasContent (@NonNull final XslElement aEl)
  {
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof XslElement || !((XslText) aNode).isWhitespaceOnly ())
        return true;
    return false;
  }

  @NonNull
  private static XdmSequence _evaluateContent (@NonNull final SequenceConstructor aBody, @NonNull final XsltContext aCtx)
  {
    final SequenceOutput aOut = new SequenceOutput ();
    aBody.execute (aCtx, aOut);
    return aOut.getSequence ();
  }

  @NonNull
  private static IInstruction _compileMap (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final XdmSequence aItems = _evaluateContent (aBody, aRtCtx);
      final XdmMap.Builder aBuilder = new XdmMap.Builder ();
      for (final IXdmItem aItem : aItems)
      {
        if (!(aItem instanceof final XdmMap aMap))
          throw new XPathException ("XTTE3375", "The content of xsl:map must be a sequence of maps");
        for (final XdmMap.Entry aEntry : aMap.entries ())
          if (!aBuilder.putIfAbsent (aEntry.key (), aEntry.value ()))
            throw new XPathException ("XTDE3365", "xsl:map: duplicate key '" + aEntry.key ().getLexical () + "'");
      }
      aRtOut.append (aBuilder.build ());
    });
  }

  @NonNull
  private static IInstruction _compileMapEntry (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aKey = aCtx.compileRequiredExpression (aEl, "key");
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    if (aSelect != null && _hasContent (aEl))
      throw XsltException.at (aEl, "XTSE3280", "xsl:map-entry has both a select attribute and content");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final XdmSequence aKeys = Atomizer.atomize (aKey.evaluate (aRtCtx));
      if (aKeys.size () != 1)
        throw new XPathException ("XPTY0004", "The key of xsl:map-entry must be a single atomic value, but " + aKeys.size () + " were supplied");
      final XdmSequence aValue = aSelect != null ? aSelect.evaluate (aRtCtx) : _evaluateContent (aBody, aRtCtx);
      aRtOut.append (XdmMap.of ((XdmAtomic) aKeys.get (0), aValue));
    });
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("map", MapExtension::_compileMap);
    aRegistry.registerInstruction ("map-entry", MapExtension::_compileMapEntry);
  }
}
