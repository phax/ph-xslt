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
package com.helger.xslt.ext.merge;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.fn.FnUri;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.ext.accumulator.AccumulatorExtension;
import com.helger.xslt.instr.CompiledInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.instr.SortKeyDef;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.NodeCopier;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.schema.ValidationRequest;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * xsl:merge (XSLT 3.0 section 15) without streaming: every input sequence is
 * evaluated, checked to be sorted (or sorted with sort-before-merge), and all
 * of them are merged into groups of items with equal merge keys.
 *
 * @author Philip Helger
 */
@Immutable
public final class MergeInstruction
{
  /** A compiled xsl:merge-source. */
  private record Source (@NonNull XslElement element,
                         @Nullable String name,
                         @NonNull XPathExpr select,
                         @Nullable XPathExpr forEachItem,
                         @Nullable XPathExpr forEachSource,
                         boolean sortBeforeMerge,
                         @Nullable ValidationRequest validation,
                         @NonNull ICommonsList <SortKeyDef> keys,
                         @NonNull ICommonsList <XdmQName> accumulators)
  {}

  /** One item of an input sequence with its merge key values. */
  private record Entry (@NonNull IXdmItem item, int source, @NonNull XdmAtomic [] keys)
  {}

  private final ICommonsList <Source> m_aSources;
  private final SequenceConstructor m_aAction;

  private MergeInstruction (@NonNull final ICommonsList <Source> aSources, @NonNull final SequenceConstructor aAction)
  {
    m_aSources = aSources;
    m_aAction = aAction;
  }

  @NonNull
  private static XdmAtomic [] _computeKeys (final SortKeyDef.@NonNull Evaluated aKeys, @NonNull final XsltContext aItemCtx)
  {
    try
    {
      return aKeys.computeKeys (aItemCtx);
    }
    catch (final XPathException ex)
    {
      if ("XTTE1020".equals (ex.getCode ()))
        throw new XPathException ("XTTE2230", "A merge key evaluates to more than one item");
      throw ex;
    }
  }

  private static int _compare (final SortKeyDef.@NonNull Evaluated aKeys, @NonNull final XdmAtomic [] a, @NonNull final XdmAtomic [] b)
  {
    try
    {
      return aKeys.compare (a, b);
    }
    catch (final XPathException ex)
    {
      if ("XTDE1030".equals (ex.getCode ()))
        throw new XPathException ("XTTE2230", "Merge key values are not comparable: " + ex.getDescription ());
      throw ex;
    }
  }

  /**
   * @return The contexts in which the select expression of a merge source is
   *         evaluated, one per input sequence.
   */
  @NonNull
  private static ICommonsList <XsltContext> _getAnchors (@NonNull final Source aSource, @NonNull final XsltContext aCtx)
  {
    final ICommonsList <XsltContext> ret = new CommonsArrayList <> ();
    if (aSource.forEachItem () != null)
    {
      final XdmSequence aItems = aSource.forEachItem ().evaluate (aCtx);
      final int nSize = aItems.size ();
      for (int i = 0; i < nSize; ++i)
        ret.add (aCtx.withFocus (aItems.get (i), i + 1, nSize));
    }
    else
      if (aSource.forEachSource () != null)
      {
        final XdmSequence aUris = aSource.forEachSource ().evaluate (aCtx);
        final int nSize = aUris.size ();
        final String sBase = aSource.element ().getStaticBaseUri ();
        for (int i = 0; i < nSize; ++i)
        {
          final IXdmItem aItem = aUris.get (i);
          final String sRef;
          if (aItem instanceof final XdmNode aNode)
            sRef = aNode.getStringValue ();
          else
            if (aItem instanceof final XdmAtomic aAtomic && aAtomic.isStringLike ())
              sRef = aAtomic.getString ();
            else
              throw new XPathException ("XPTY0004", "The for-each-source attribute of xsl:merge-source must deliver strings");
          final String sTrimmed = FnUri.iriToUri (sRef.trim ());
          String sUri;
          try
          {
            sUri = sBase != null && FnUri.isAbsoluteUri (sBase) ? FnUri.resolveUri (sTrimmed, sBase) : sTrimmed;
          }
          catch (final XPathException ex)
          {
            throw new XPathException ("FODC0005", "Invalid URI '" + sTrimmed + "' in xsl:merge-source");
          }
          XdmNode aDoc = aCtx.getEngine ().getDocument (aCtx, sUri);
          if (aDoc == null)
            throw new XPathException ("FODC0002", "Cannot retrieve the document '" + sUri + "'");
          // Validate a copy, the document itself is shared by all retrievals
          // of its URI
          final ValidationRequest aValidation = aSource.validation ();
          if (aValidation != null && !aValidation.isPreserve ())
          {
            final XdmNode aCopy = NodeCopier.copyDeep (aDoc.getRoot (), true, !aValidation.isStrip ());
            aCopy.setDocumentUri (aDoc.getRoot ().getDocumentUri ());
            aValidation.apply (aCopy);
            aDoc = aCopy;
          }
          // Only the accumulators named in use-accumulators apply to the document
          AccumulatorExtension.restrictTree (aCtx.getEngine (), aDoc.getRoot (), aSource.accumulators (), false);
          ret.add (aCtx.withFocus (aDoc, i + 1, nSize));
        }
      }
      else
        ret.add (aCtx);
    return ret;
  }

  private void _execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final int nSources = m_aSources.size ();
    // The attributes of the merge keys are evaluated in the context of
    // xsl:merge; corresponding keys must agree
    final SortKeyDef.Evaluated [] aEvaluated = new SortKeyDef.Evaluated [nSources];
    for (int s = 0; s < nSources; ++s)
    {
      aEvaluated[s] = SortKeyDef.evaluate (m_aSources.get (s).keys (), aCtx);
      for (int k = 0; k < aEvaluated[s].getKeyCount (); ++k)
        if (!aEvaluated[s].hasSameSettings (k, aEvaluated[0]))
          throw XsltException.locate (new XPathException ("XTDE2210",
                                                          "Corresponding xsl:merge-key elements have different effective attributes"),
                                      m_aSources.get (s).keys ().get (k).getElement ());
    }

    final ICommonsList <Entry> aAll = new CommonsArrayList <> ();
    for (int s = 0; s < nSources; ++s)
    {
      final Source aSource = m_aSources.get (s);
      final SortKeyDef.Evaluated aKeys = aEvaluated[s];
      for (final XsltContext aAnchor : _getAnchors (aSource, aCtx))
      {
        final XdmSequence aInput = aSource.select ().evaluate (aAnchor);
        final ICommonsList <Entry> aEntries = new CommonsArrayList <> (aInput.size ());
        for (final IXdmItem aItem : aInput)
        {
          // Merge keys are evaluated with a singleton focus
          aEntries.add (new Entry (aItem, s, _computeKeys (aKeys, aAnchor.withFocus (aItem, 1, 1))));
        }
        if (aSource.sortBeforeMerge ())
          aEntries.sort ( (a, b) -> _compare (aKeys, a.keys (), b.keys ()));
        else
          for (int i = 1; i < aEntries.size (); ++i)
            if (_compare (aKeys, aEntries.get (i - 1).keys (), aEntries.get (i).keys ()) > 0)
              throw XsltException.locate (new XPathException ("XTDE2220",
                                                              "The input sequence of xsl:merge-source is not sorted by its merge keys"),
                                          aSource.element ());
        aAll.addAll (aEntries);
      }
      aCtx.getDynamicContext ().checkInterrupt ();
    }

    // Stable: items with equal keys stay in merge source and input order
    final SortKeyDef.Evaluated aKeys = aEvaluated[0];
    aAll.sort ( (a, b) -> _compare (aKeys, a.keys (), b.keys ()));

    final ICommonsList <ICommonsList <Entry>> aGroups = new CommonsArrayList <> ();
    ICommonsList <Entry> aCur = null;
    for (final Entry aEntry : aAll)
    {
      if (aCur == null || _compare (aKeys, aCur.getFirstOrNull ().keys (), aEntry.keys ()) != 0)
      {
        aCur = new CommonsArrayList <> ();
        aGroups.add (aCur);
      }
      aCur.add (aEntry);
    }

    final XsltContext aBase = aCtx.withState (aCtx.getState ().withCurrentRule (null, aCtx.getState ().getCurrentMode ()));
    final int nGroups = aGroups.size ();
    for (int g = 0; g < nGroups; ++g)
    {
      final ICommonsList <Entry> aGroup = aGroups.get (g);
      final XdmSequence.Builder aItems = new XdmSequence.Builder ();
      final ICommonsList <XdmSequence.Builder> aPerSource = new CommonsArrayList <> (nSources);
      for (int s = 0; s < nSources; ++s)
        aPerSource.add (new XdmSequence.Builder ());
      for (final Entry aEntry : aGroup)
      {
        aItems.add (aEntry.item ());
        aPerSource.get (aEntry.source ()).add (aEntry.item ());
      }
      final ICommonsOrderedMap <String, XdmSequence> aBySource = new CommonsLinkedHashMap <> ();
      for (int s = 0; s < nSources; ++s)
        if (m_aSources.get (s).name () != null)
          aBySource.put (m_aSources.get (s).name (), aPerSource.get (s).build ());
      final XdmSequence.Builder aKeyValues = new XdmSequence.Builder ();
      for (final XdmAtomic aKey : aGroup.getFirstOrNull ().keys ())
        if (aKey != null)
          aKeyValues.add (aKey);
      final XsltContext aGroupCtx = aBase.with (MergeExtension.CURRENT_MERGE_GROUP, new MergeGroup (aItems.build (), aBySource))
                                         .with (MergeExtension.CURRENT_MERGE_KEY, aKeyValues.build ())
                                         .withFocus (aGroup.getFirstOrNull ().item (), g + 1, nGroups);
      m_aAction.execute (aGroupCtx, aOut);
    }
  }

  @NonNull
  private static Source _compileSource (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    if (aEl.hasAttr ("for-each-item") && aEl.hasAttr ("for-each-source"))
      throw XsltException.at (aEl, "XTSE3195", "xsl:merge-source must not have both for-each-item and for-each-source");
    final String sName = aEl.getAttr ("name");
    if (sName != null && !XsltNames.isNCName (sName.trim ()))
      throw XsltException.at (aEl, "XTSE0020", "The name '" + sName + "' of xsl:merge-source is not an NCName");
    for (final String sAttr : new String [] { "sort-before-merge", "streamable" })
    {
      final String s = aEl.getAttr (sAttr);
      if (s != null && !XsltNames.isXsltBooleanLexical (s))
        throw XsltException.at (aEl, "XTSE0020", sAttr + "='" + s + "' is not a valid boolean");
    }
    final String sValidation = aEl.getAttr ("validation");
    final boolean bSchemaAware = aCtx.getStylesheet ().isSchemaAware ();
    if (sValidation != null)
    {
      final String s = sValidation.trim ();
      if (("strict".equals (s) || "lax".equals (s)) && !bSchemaAware)
        throw XsltException.at (aEl, "XTSE1660", "validation='" + s + "' requires a schema-aware processor");
      if (!"preserve".equals (s) && !"strip".equals (s) && !"strict".equals (s) && !"lax".equals (s))
        throw XsltException.at (aEl, "XTSE0020", "validation='" + sValidation + "' is not a valid value");
    }
    if (aEl.hasAttr ("type") && !bSchemaAware)
      throw XsltException.at (aEl, "XTSE1660", "The type attribute requires a schema-aware processor");
    final ValidationRequest aValidation = ValidationRequest.compile (aCtx, aEl, ValidationRequest.EKind.SOURCE_DOCUMENT);
    final ICommonsList <SortKeyDef> aKeys = new CommonsArrayList <> ();
    for (final XslElement aChild : aEl.getElementChildren ())
    {
      if (!aChild.isXsl ("merge-key"))
        throw XsltException.at (aChild, "XTSE0010", "xsl:merge-source may only contain xsl:merge-key elements");
      aKeys.add (SortKeyDef.compile (aCtx, aChild));
    }
    if (aKeys.isEmpty ())
      throw XsltException.at (aEl, "XTSE0010", "xsl:merge-source requires at least one xsl:merge-key");
    final String sSortBefore = aEl.getAttr ("sort-before-merge");
    return new Source (aEl,
                       sName == null ? null : sName.trim (),
                       aCtx.compileRequiredExpression (aEl, "select"),
                       aCtx.compileOptionalExpression (aEl, "for-each-item"),
                       aCtx.compileOptionalExpression (aEl, "for-each-source"),
                       sSortBefore != null && XsltNames.isXsltTrue (sSortBefore),
                       aValidation,
                       aKeys,
                       AccumulatorExtension.registerUseAccumulators (aCtx, aEl));
  }

  /**
   * The static part of XTDE2210: corresponding merge keys must agree in the
   * presence of lang, order, collation, case-order and data-type, and in the
   * values of those written without attribute value templates.
   */
  private static void _checkCorrespondingKeys (@NonNull final XslElement aFirst, @NonNull final XslElement aOther)
  {
    for (final String sAttr : new String [] { "lang", "order", "collation", "case-order", "data-type" })
    {
      String s1 = aFirst.getAttr (sAttr);
      String s2 = aOther.getAttr (sAttr);
      if (s1 == null && s2 == null)
        continue;
      if (s1 == null || s2 == null)
        throw XsltException.at (aOther, "XTDE2210", "Corresponding xsl:merge-key elements differ in the presence of " + sAttr);
      if (s1.indexOf ('{') >= 0 || s2.indexOf ('{') >= 0)
        continue;
      s1 = s1.trim ();
      s2 = s2.trim ();
      if ("collation".equals (sAttr))
      {
        s1 = CollationRegistry.resolveUri (s1, aFirst.getStaticBaseUri ());
        s2 = CollationRegistry.resolveUri (s2, aOther.getStaticBaseUri ());
      }
      if (!s1.equals (s2))
        throw XsltException.at (aOther, "XTDE2210", "Corresponding xsl:merge-key elements have different values for " + sAttr);
    }
  }

  /**
   * Compile an xsl:merge element.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element.
   * @return The instruction.
   */
  @NonNull
  public static IInstruction compile (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final ICommonsList <Source> aSources = new CommonsArrayList <> ();
    SequenceConstructor aAction = null;
    final ICommonsSet <String> aNames = new CommonsHashSet <> ();
    for (final XslElement aChild : aEl.getElementChildren ())
    {
      if (aChild.isXsl ("merge-source"))
      {
        if (aAction != null)
          throw XsltException.at (aChild, "XTSE0010", "xsl:merge-source must precede xsl:merge-action");
        final Source aSource = _compileSource (aCtx, aChild);
        if (aSource.name () != null && !aNames.add (aSource.name ()))
          throw XsltException.at (aChild, "XTSE3190", "Duplicate xsl:merge-source name '" + aSource.name () + "'");
        if (aSources.isNotEmpty () && aSources.getFirstOrNull ().keys ().size () != aSource.keys ().size ())
          throw XsltException.at (aChild, "XTSE2200", "All xsl:merge-source elements must have the same number of xsl:merge-key children");
        aSources.add (aSource);
      }
      else
        if (aChild.isXsl ("merge-action"))
        {
          if (aAction != null)
            throw XsltException.at (aChild, "XTSE0010", "xsl:merge allows only one xsl:merge-action");
          aAction = aCtx.compileContent (aChild);
        }
        else
          if (aChild.isXsl ("fallback"))
          {
            if (aAction == null)
              throw XsltException.at (aChild, "XTSE0010", "xsl:fallback may only follow xsl:merge-action");
          }
          else
            throw XsltException.at (aChild, "XTSE0010", "xsl:" + aChild.getLocalName () + " is not allowed in xsl:merge");
    }
    if (aSources.isEmpty ())
      throw XsltException.at (aEl, "XTSE0010", "xsl:merge requires at least one xsl:merge-source");
    if (aAction == null)
      throw XsltException.at (aEl, "XTSE0010", "xsl:merge requires an xsl:merge-action");
    for (int s = 1; s < aSources.size (); ++s)
      for (int k = 0; k < aSources.get (s).keys ().size (); ++k)
        _checkCorrespondingKeys (aSources.getFirstOrNull ().keys ().get (k).getElement (), aSources.get (s).keys ().get (k).getElement ());
    final MergeInstruction aInstr = new MergeInstruction (aSources, aAction);
    return new CompiledInstruction (aEl, aInstr::_execute);
  }
}
