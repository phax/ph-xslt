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
package com.helger.phoxtrot.xslt.stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.ext.grouping.GroupingExtension;
import com.helger.phoxtrot.xslt.model.CompiledStylesheet;
import com.helger.phoxtrot.xslt.model.Mode;
import com.helger.phoxtrot.xslt.model.Template;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XsltNames;

/**
 * XSLT 3.0 sections 14.2.1 and 14.2.2: an invocation construct
 * (<code>xsl:apply-templates</code>, <code>xsl:call-template</code>,
 * <code>xsl:apply-imports</code>, <code>xsl:next-match</code>) within a
 * declared-streamable construct sets the current group and the current
 * grouping key to absent in the called template, so that the streamability of
 * <code>xsl:for-each-group</code> can be assessed statically. Applied only by a
 * processor claiming streaming; whether the instruction is inside such a
 * construct is decided once, on first use (the modes are known only at the end
 * of compilation).
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class StreamedGroupScope
{
  private final XslElement m_aElement;
  // Computed on first use; races compute equal values
  private volatile Boolean m_aInStreamable;

  private StreamedGroupScope (@NonNull final XslElement aElement)
  {
    m_aElement = aElement;
  }

  /**
   * @param aSheet
   *        The stylesheet.
   * @param aTemplateEl
   *        An <code>xsl:template</code> element with a match pattern.
   * @return <code>true</code> if a template rule compiled from that element
   *         applies in a mode declared streamable (XSLT 3.0 section 6.6.4):
   *         for <code>mode="#all"</code> any streamable mode of the
   *         stylesheet, otherwise one of its listed modes.
   */
  private static boolean _hasStreamableMode (@NonNull final CompiledStylesheet aSheet, @NonNull final XslElement aTemplateEl)
  {
    for (final Template aTemplate : aSheet.templates ())
      if (aTemplate.getElement () == aTemplateEl && aTemplate.getPattern () != null)
      {
        if (aTemplate.isAllModes ())
        {
          for (final Mode aMode : aSheet.modes ().values ())
            if (aMode.isStreamable ())
              return true;
        }
        else
          for (final XdmQName aName : aTemplate.getModes ())
          {
            final Mode aMode = aSheet.getMode (aName);
            if (aMode != null && aMode.isStreamable ())
              return true;
          }
      }
    return false;
  }

  /**
   * @param aSheet
   *        The stylesheet.
   * @param aEl
   *        An instruction.
   * @return <code>true</code> if it is contained in a declared-streamable
   *         construct: a streamable xsl:source-document, xsl:accumulator,
   *         xsl:attribute-set or xsl:merge-source, a stylesheet function with
   *         a streamability category, or a template rule of a streamable mode.
   */
  public static boolean isInDeclaredStreamable (@NonNull final CompiledStylesheet aSheet, @NonNull final XslElement aEl)
  {
    for (XslElement aCur = aEl.getParent (); aCur != null; aCur = aCur.getParent ())
    {
      if (!aCur.isXsl ())
        continue;
      switch (aCur.getLocalName ())
      {
        case "source-document", "accumulator", "attribute-set", "merge-source":
          if (XsltNames.isXsltTrue (aCur.getAttr ("streamable")))
            return true;
          break;
        case "function":
        {
          final String sCategory = aCur.getAttr ("streamability");
          if (sCategory != null && !"unclassified".equals (sCategory.trim ()))
            return true;
          break;
        }
        case "template":
          if (aCur.hasAttr ("match") && _hasStreamableMode (aSheet, aCur))
            return true;
          break;
        default:
          break;
      }
    }
    return false;
  }

  /**
   * @param aCtx
   *        The context of the invocation instruction.
   * @return The context to invoke the template with: without current group and
   *         grouping key if the instruction is in a declared-streamable
   *         construct.
   */
  @NonNull
  public XsltContext apply (@NonNull final XsltContext aCtx)
  {
    Boolean aIn = m_aInStreamable;
    if (aIn == null)
    {
      aIn = Boolean.valueOf (isInDeclaredStreamable (aCtx.getEngine ().getStylesheet (), m_aElement));
      m_aInStreamable = aIn;
    }
    if (!aIn.booleanValue ())
      return aCtx;
    return aCtx.withState (aCtx.getState ()
                               .without (GroupingExtension.CURRENT_GROUP)
                               .without (GroupingExtension.CURRENT_GROUPING_KEY));
  }

  /**
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The invocation instruction.
   * @return The scope, or <code>null</code> if streaming is not claimed.
   */
  @Nullable
  public static StreamedGroupScope create (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    return aCtx.getCompiler ().getOptions ().isStreamingClaim () ? new StreamedGroupScope (aEl) : null;
  }
}
