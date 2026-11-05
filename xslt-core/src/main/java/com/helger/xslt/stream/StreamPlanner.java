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
package com.helger.xslt.stream;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XslText;
import com.helger.xslt.tree.XsltNames;

/**
 * Finds the shape of a sequence constructor that streamed execution can run:
 * a chain of literal result elements around exactly one consuming instruction
 * (<code>xsl:for-each</code> without sort keys,
 * <code>xsl:apply-templates</code> without sort keys and parameters,
 * <code>xsl:copy-of</code> without <code>copy-accumulators</code>,
 * <code>xsl:iterate</code>, <code>xsl:for-each-group</code> without sort
 * keys, <code>xsl:fork</code>). A port of go-xslt's
 * <code>strbFindConsumer</code>.
 * <p>
 * The wrappers run with the live document node as context item before any
 * record is read, so they must not read it: a wrapper with
 * <code>xsl:use-attribute-sets</code> is not accepted, and one with attribute
 * value templates only if the streamability analysis proved every expression
 * in them motionless with the document node as context item
 * ({@link StreamVerdicts#isMotionlessWrapper(XslElement)}); no other sibling
 * of the consumer than whitespace is accepted. Whether the consumer's select
 * expression is a record path is decided by the consumer itself
 * ({@link StreamConsumer}). Anything else runs buffered.
 *
 * @author Philip Helger
 */
@Immutable
public final class StreamPlanner
{
  /** The deepest wrapper chain followed. */
  public static final int MAX_WRAPPERS = 1000;

  @PresentForCodeCoverage
  private static final StreamPlanner INSTANCE = new StreamPlanner ();

  private StreamPlanner ()
  {}

  /**
   * @param aEl
   *        A literal result element.
   * @return <code>true</code> if it has attribute value templates (and needs
   *         a verdict to be a wrapper).
   */
  public static boolean hasAvt (@NonNull final XslElement aEl)
  {
    for (final Map.Entry <XdmQName, String> aEntry : aEl.getAllAttributes ().entrySet ())
      if (!XsltNames.NS_XSL.equals (aEntry.getKey ().namespaceUri ()) && aEntry.getValue ().indexOf ('{') >= 0)
        return true;
    return false;
  }

  private static boolean _isSafeWrapper (@NonNull final XslElement aEl, @Nullable final StreamVerdicts aVerdicts)
  {
    for (final Map.Entry <XdmQName, String> aEntry : aEl.getAllAttributes ().entrySet ())
      if (aEntry.getKey ().isSameName (XsltNames.NS_XSL, "use-attribute-sets"))
        return false;
    return !hasAvt (aEl) || aVerdicts != null && aVerdicts.isMotionlessWrapper (aEl);
  }

  private static boolean _isConsumer (@NonNull final XslElement aEl)
  {
    if (!aEl.isXsl ())
      return false;
    switch (aEl.getLocalName ())
    {
      case "for-each":
        return aEl.getXslChildren ("sort").isEmpty ();
      case "apply-templates":
        return aEl.getXslChildren ("sort").isEmpty () && aEl.getXslChildren ("with-param").isEmpty ();
      case "copy-of":
        return !XsltNames.isXsltTrue (aEl.getAttr ("copy-accumulators")) &&
               !aEl.hasAttr ("validation") &&
               !aEl.hasAttr ("type");
      case "iterate", "fork":
        return true;
      case "for-each-group":
        return aEl.getXslChildren ("sort").isEmpty ();
      default:
        return false;
    }
  }

  /**
   * @param aParent
   *        The element whose content is examined.
   * @param bTemplate
   *        <code>true</code> if it is an <code>xsl:template</code>: its
   *        <code>xsl:context-item</code> is no content, an
   *        <code>xsl:param</code> (whose default could read the document)
   *        rejects the shape.
   * @return The only significant child element, or <code>null</code>.
   */
  @Nullable
  private static XslElement _getOnlyChild (@NonNull final XslElement aParent, final boolean bTemplate)
  {
    XslElement ret = null;
    for (final XslNode aNode : aParent.getChildren ())
    {
      if (aNode instanceof final XslText aText)
      {
        if (aText.isWhitespaceOnly () && !aText.isXmlSpacePreserve ())
          continue;
        return null;
      }
      if (aNode instanceof final XslElement aChild)
      {
        if (bTemplate && aChild.isXsl ("context-item"))
          continue;
        if (ret != null)
          return null;
        ret = aChild;
      }
    }
    return ret;
  }

  /**
   * @param aHost
   *        The <code>xsl:source-document</code> or <code>xsl:template</code>
   *        whose sequence constructor runs over the streamed document.
   * @return The literal result elements around the only significant
   *         instruction, outermost first, as long as each has one significant
   *         child. Whether they are acceptable wrappers is not checked.
   */
  @NonNull
  public static ICommonsList <XslElement> getWrapperCandidates (@NonNull final XslElement aHost)
  {
    final ICommonsList <XslElement> ret = new CommonsArrayList <> ();
    XslElement aCur = _getOnlyChild (aHost, aHost.isXsl ("template"));
    for (int i = 0; aCur != null && !aCur.isXsl () && i < MAX_WRAPPERS; ++i)
    {
      ret.add (aCur);
      aCur = _getOnlyChild (aCur, false);
    }
    return ret;
  }

  /**
   * @param aHost
   *        The <code>xsl:source-document</code> or <code>xsl:template</code>
   *        whose sequence constructor runs over the streamed document.
   * @param aVerdicts
   *        What the streamability analysis proved, or <code>null</code> if it
   *        did not run (then no wrapper with an attribute value template is
   *        accepted).
   * @return The consuming instruction, or <code>null</code> if the sequence
   *         constructor does not have the shape.
   */
  @Nullable
  public static XslElement findConsumer (@NonNull final XslElement aHost, @Nullable final StreamVerdicts aVerdicts)
  {
    final boolean bTemplate = aHost.isXsl ("template");
    XslElement aCur = _getOnlyChild (aHost, bTemplate);
    for (int i = 0; aCur != null && i < MAX_WRAPPERS; ++i)
    {
      if (aCur.isXsl ())
        return _isConsumer (aCur) ? aCur : null;
      if (!_isSafeWrapper (aCur, aVerdicts))
        return null;
      aCur = _getOnlyChild (aCur, false);
    }
    return null;
  }
}
