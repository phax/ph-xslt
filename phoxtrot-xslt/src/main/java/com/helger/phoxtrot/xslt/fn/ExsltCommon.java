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
package com.helger.phoxtrot.xslt.fn;

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * The core functions of EXSLT - Common (namespace
 * <code>http://exslt.org/common</code>, https://exslt.github.io/exsl/):
 * <code>exsl:node-set</code> and <code>exsl:object-type</code>. EXSLT is not
 * part of any W3C specification; XSLT 3.0 section 24.1 allows extension
 * functions. They are provided so that XSLT 1.0 stylesheets that use them
 * (such as DocBook XSL) run unchanged. The unstable element
 * <code>exsl:document</code> is not provided (<code>xsl:result-document</code>
 * replaces it).
 *
 * @author Philip Helger
 */
@Immutable
public final class ExsltCommon
{
  /** The namespace URI of EXSLT - Common. */
  public static final String NS_EXSLT_COMMON = "http://exslt.org/common";

  @PresentForCodeCoverage
  private static final ExsltCommon INSTANCE = new ExsltCommon ();

  private ExsltCommon ()
  {}

  /**
   * <code>exsl:node-set ($object)</code>: in XSLT 1.0 it turned a result tree
   * fragment into a node-set; since XSLT 2.0 a variable with content already
   * holds a temporary tree, so nodes are returned as they are. A single atomic
   * value becomes a parentless text node with its string value (EXSLT: "turn a
   * string into a text node"); the empty sequence stays empty.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The argument.
   * @return The nodes, or the text node.
   * @throws XPathException
   *         XPTY0004 for a sequence that mixes atomic values with other items
   *         or has more than one atomic value, and for maps, arrays and
   *         functions.
   */
  @NonNull
  private static XdmSequence _nodeSet (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aArg = aArgs.get (0);
    boolean bAllNodes = true;
    for (final IXdmItem aItem : aArg)
      if (!(aItem instanceof IXdmNode))
      {
        bAllNodes = false;
        break;
      }
    if (bAllNodes)
      return aArg;
    if (aArg.size () == 1 && aArg.getFirst () instanceof final XdmAtomic aAtomic)
      return XdmSequence.of (IXdmNode.createText (aAtomic.isStringLike () ? aAtomic.getString () : aAtomic.getLexical ()));
    throw new XPathException ("XPTY0004", "exsl:node-set: the argument must be nodes or a single atomic value");
  }

  /**
   * <code>exsl:object-type ($object)</code>: the XSLT 1.0 type of the value,
   * one of <code>string</code>, <code>number</code>, <code>boolean</code>,
   * <code>node-set</code> or <code>external</code>. Nodes (also an empty
   * sequence and a temporary tree, which is a node since XSLT 2.0) are
   * <code>node-set</code>; <code>RTF</code> is never returned. String-like
   * atomic values are <code>string</code>, numeric ones <code>number</code>,
   * <code>xs:boolean</code> is <code>boolean</code>; any other value
   * (other atomic types, mixed sequences, several atomic values, maps, arrays,
   * functions) is <code>external</code>.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The argument.
   * @return The type name as <code>xs:string</code>.
   */
  @NonNull
  private static XdmSequence _objectType (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aArg = aArgs.get (0);
    String sType = "node-set";
    for (final IXdmItem aItem : aArg)
      if (!(aItem instanceof IXdmNode))
      {
        sType = "external";
        break;
      }
    if (aArg.size () == 1 && aArg.getFirst () instanceof final XdmAtomic aAtomic)
    {
      if (aAtomic.isStringLike ())
        sType = "string";
      else
        if (aAtomic.isNumeric ())
          sType = "number";
        else
          if (aAtomic.getType () == EAtomicType.BOOLEAN)
            sType = "boolean";
    }
    return XdmSequence.of (XdmAtomic.string (sType));
  }

  /**
   * Register <code>exsl:node-set#1</code> and <code>exsl:object-type#1</code>.
   *
   * @param aLib
   *        The function library.
   */
  public static void register (@NonNull final FunctionLibrary aLib)
  {
    aLib.register (NS_EXSLT_COMMON, "node-set", "(item()*) as item()*", ExsltCommon::_nodeSet);
    aLib.register (NS_EXSLT_COMMON, "object-type", "(item()*) as xs:string", ExsltCommon::_objectType);
  }
}
