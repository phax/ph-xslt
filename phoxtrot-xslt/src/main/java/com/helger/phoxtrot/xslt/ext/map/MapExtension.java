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
package com.helger.phoxtrot.xslt.ext.map;

import org.jspecify.annotations.NonNull;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmMap;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.compile.IXsltExtension;
import com.helger.phoxtrot.xslt.compile.XPathExpr;
import com.helger.phoxtrot.xslt.compile.XsltExtensionRegistry;
import com.helger.phoxtrot.xslt.instr.CompiledInstruction;
import com.helger.phoxtrot.xslt.instr.IInstruction;
import com.helger.phoxtrot.xslt.instr.SequenceConstructor;
import com.helger.phoxtrot.xslt.runtime.SequenceOutput;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslNode;
import com.helger.phoxtrot.xslt.tree.XslText;

/**
 * The map construction instructions xsl:map and xsl:map-entry (XSLT 3.0
 * section 21.3).
 *
 * @author Philip Helger
 */
public final class MapExtension implements IXsltExtension
{
  /**
   * Check whether an element has content in the sense of the XTSE3280 rule
   * of xsl:map-entry: any element child other than xsl:fallback or any text
   * child that is not whitespace only counts.
   *
   * @param aEl
   *        The element to check.
   * @return <code>true</code> if there is at least one such child.
   */
  private static boolean _hasContent (@NonNull final XslElement aEl)
  {
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof final XslElement aChild)
      {
        if (!aChild.isXsl ("fallback"))
          return true;
      }
      else
        if (!((XslText) aNode).isWhitespaceOnly ())
          return true;
    return false;
  }

  /**
   * Run a sequence constructor into a fresh {@link SequenceOutput} and return
   * the raw sequence it produced, without building a document node or
   * merging adjacent text.
   *
   * @param aBody
   *        The sequence constructor to run.
   * @param aCtx
   *        The run-time context.
   * @return The produced sequence. Never <code>null</code>, may be empty.
   */
  @NonNull
  private static XdmSequence _evaluateContent (@NonNull final SequenceConstructor aBody, @NonNull final XsltContext aCtx)
  {
    final SequenceOutput aOut = new SequenceOutput ();
    aBody.execute (aCtx, aOut);
    return aOut.getSequence ();
  }

  /**
   * Compile xsl:map (XSLT 3.0 section 21.3). At run time the content is
   * evaluated and every item must be a map, otherwise the type error XTTE3375
   * is raised. The entries of all maps are merged in order into one new map,
   * like <code>map:merge($maps, map{"duplicates":"reject"})</code>: a key
   * that is already present by the op:same-key rule raises XTDE3365. An empty
   * content gives the empty map.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The xsl:map element.
   * @return The compiled instruction. Never <code>null</code>.
   */
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

  /**
   * Compile xsl:map-entry (XSLT 3.0 section 21.3). The key attribute is
   * required; a select attribute together with content other than
   * xsl:fallback (see {@link #_hasContent}) is the static error XTSE3280. At
   * run time the key is atomized and must be exactly one atomic value,
   * otherwise XPTY0004 is raised; an <code>xs:untypedAtomic</code> key is cast
   * to <code>xs:string</code>, any other atomic value is used as it is. The
   * value is the result of
   * select, or of the content, without any conversion. The result is a
   * singleton map.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The xsl:map-entry element.
   * @return The compiled instruction. Never <code>null</code>.
   */
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
      XdmAtomic aKeyAtom = (XdmAtomic) aKeys.get (0);
      if (aKeyAtom.getType () == EAtomicType.UNTYPED_ATOMIC)
        aKeyAtom = XdmAtomic.string (aKeyAtom.getString ());
      aRtOut.append (XdmMap.of (aKeyAtom, aValue));
    });
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("map", MapExtension::_compileMap);
    aRegistry.registerInstruction ("map-entry", MapExtension::_compileMapEntry);
  }
}
