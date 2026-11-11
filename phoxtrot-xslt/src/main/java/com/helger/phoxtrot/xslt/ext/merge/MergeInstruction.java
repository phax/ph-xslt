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
package com.helger.phoxtrot.xslt.ext.merge;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmStreamReader;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.collation.CollationRegistry;
import com.helger.phoxtrot.xpath.context.ResourceUri;
import com.helger.phoxtrot.xpath.fn.FnUri;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.compile.XPathExpr;
import com.helger.phoxtrot.xslt.ext.accumulator.AccumulatorExtension;
import com.helger.phoxtrot.xslt.instr.CompiledInstruction;
import com.helger.phoxtrot.xslt.instr.IInstruction;
import com.helger.phoxtrot.xslt.instr.SequenceConstructor;
import com.helger.phoxtrot.xslt.instr.SortKeyDef;
import com.helger.phoxtrot.xslt.runtime.Engine;
import com.helger.phoxtrot.xslt.runtime.IXsltOutput;
import com.helger.phoxtrot.xslt.runtime.NodeCopier;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.schema.ValidationRequest;
import com.helger.phoxtrot.xslt.stream.StreamRecordPath;
import com.helger.phoxtrot.xslt.stream.StreamedRun;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XsltNames;

/**
 * xsl:merge (XSLT 3.0 section 15): every input sequence is evaluated, checked to be sorted (or
 * sorted with sort-before-merge), and all of them are merged into groups of items with equal merge
 * keys. With the streaming claim, the documents of a streamable merge source are read as streams
 * instead, one record at a time as the merge needs it (section 15.4).
 *
 * @author Philip Helger
 */
@Immutable
public final class MergeInstruction
{
  private static final XdmQName SNAPSHOT = new XdmQName (XdmConstants.NS_FN, "snapshot");

  /** A compiled xsl:merge-source. */
  private record Source (@NonNull XslElement element,
                         @Nullable String name,
                         @NonNull XPathExpr select,
                         @Nullable XPathExpr forEachItem,
                         @Nullable XPathExpr forEachSource,
                         boolean sortBeforeMerge,
                         @Nullable ValidationRequest validation,
                         @NonNull ICommonsList <SortKeyDef> keys,
                         @NonNull ICommonsList <XdmQName> accumulators,
                         boolean snapshot,
                         @Nullable StreamRecordPath recordPath)
  {}

  /** One item of an input sequence with its merge key values. */
  private record Entry (@NonNull IXdmItem item, int source, @NonNull XdmAtomic [] keys)
  {}

  /**
   * One input sequence of the merge: the entries of a source evaluated in
   * full, or the records of a document read as a stream, one at a time.
   */
  private static final class Input
  {
    private final Source m_aSource;
    private final int m_nSource;
    private final SortKeyDef.Evaluated m_aKeys;
    private final XsltContext m_aAnchor;
    private final ICommonsList <Entry> m_aEntries;
    private final StreamedRun m_aRun;
    private int m_nNext;
    private Entry m_aHead;

    private Input (@NonNull final Source aSource,
                   final int nSource,
                   final SortKeyDef.@NonNull Evaluated aKeys,
                   @NonNull final XsltContext aAnchor,
                   @Nullable final ICommonsList <Entry> aEntries,
                   @Nullable final StreamedRun aRun)
    {
      m_aSource = aSource;
      m_nSource = nSource;
      m_aKeys = aKeys;
      m_aAnchor = aAnchor;
      m_aEntries = aEntries;
      m_aRun = aRun;
    }
  }

  private final XslElement m_aElement;
  private final ICommonsList <Source> m_aSources;
  private final SequenceConstructor m_aAction;

  private MergeInstruction (@NonNull final XslElement aElement,
                            @NonNull final ICommonsList <Source> aSources,
                            @NonNull final SequenceConstructor aAction)
  {
    m_aElement = aElement;
    m_aSources = aSources;
    m_aAction = aAction;
  }

  /**
   * Compute the merge key values of one item. A key that evaluates to more
   * than one item is reported as XTTE2230 instead of the XTTE1020 the sort key
   * evaluation raises; every other error passes unchanged.
   *
   * @param aKeys
   *        The evaluated merge keys of the item's merge source.
   * @param aItemCtx
   *        The context with the item as singleton focus.
   * @return One value per merge key; an entry is <code>null</code> for an
   *         empty key value.
   */
  @NonNull
  private static XdmAtomic [] _computeKeys (final SortKeyDef.@NonNull Evaluated aKeys,
                                            @NonNull final XsltContext aItemCtx)
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

  /**
   * Compare two merge key tuples with the settings of the given keys. Values
   * that are not comparable (XTDE1030 of the sort key comparison) are reported
   * as XTTE2230.
   *
   * @param aKeys
   *        The evaluated merge keys whose order, collation and data type
   *        apply.
   * @param a
   *        First key tuple.
   * @param b
   *        Second key tuple.
   * @return negative, 0 or positive, honouring the order of each key.
   */
  private static int _compare (final SortKeyDef.@NonNull Evaluated aKeys,
                               @NonNull final XdmAtomic [] a,
                               @NonNull final XdmAtomic [] b)
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

  /** @return The absolute URIs of the documents for-each-source selects. */
  @NonNull
  private static ICommonsList <ResourceUri> _getSourceUris (@NonNull final Source aSource, @NonNull final XsltContext aCtx)
  {
    final ICommonsList <ResourceUri> ret = new CommonsArrayList <> ();
    final String sBase = aSource.element ().getStaticBaseUri ();
    for (final IXdmItem aItem : aSource.forEachSource ().evaluate (aCtx))
    {
      final String sRef;
      if (aItem instanceof final IXdmNode aNode)
        sRef = aNode.getStringValue ();
      else
        if (aItem instanceof final XdmAtomic aAtomic && aAtomic.isStringLike ())
          sRef = aAtomic.getString ();
        else
          throw new XPathException ("XPTY0004", "The for-each-source attribute of xsl:merge-source must deliver strings");
      final String sTrimmed = FnUri.iriToUri (sRef.trim ());
      try
      {
        ret.add (ResourceUri.of (sBase != null && FnUri.isAbsoluteUri (sBase) ? FnUri.resolveUri (sTrimmed, sBase) : sTrimmed,
                                 sTrimmed,
                                 sBase));
      }
      catch (final XPathException ex)
      {
        throw new XPathException ("FODC0005", "Invalid URI '" + sTrimmed + "' in xsl:merge-source");
      }
    }
    return ret;
  }

  /**
   * @return The contexts in which the select expression of a merge source is evaluated, one per
   *         input sequence.
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
        final ICommonsList <ResourceUri> aUris = _getSourceUris (aSource, aCtx);
        final int nSize = aUris.size ();
        for (int i = 0; i < nSize; ++i)
        {
          final String sUri = aUris.get (i).absoluteUri ();
          IXdmNode aDoc = aCtx.getEngine ().getDocument (aCtx, aUris.get (i));
          if (aDoc == null)
            throw new XPathException ("FODC0002", "Cannot retrieve the document '" + sUri + "'");
          // Validate a copy, the document itself is shared by all retrievals
          // of its URI
          final ValidationRequest aValidation = aSource.validation ();
          if (aValidation != null && !aValidation.isPreserve ())
          {
            final IXdmNode aCopy = NodeCopier.copyDeep (aDoc.getRoot (), true, !aValidation.isStrip ());
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

  /**
   * The attributes of the merge keys are evaluated in the context of xsl:merge; corresponding keys
   * must agree.
   */
  private SortKeyDef.@NonNull Evaluated [] _evaluateKeys (@NonNull final XsltContext aCtx)
  {
    final int nSources = m_aSources.size ();
    final SortKeyDef.Evaluated [] ret = new SortKeyDef.Evaluated [nSources];
    for (int s = 0; s < nSources; ++s)
    {
      ret[s] = SortKeyDef.evaluate (m_aSources.get (s).keys (), aCtx);
      for (int k = 0; k < ret[s].getKeyCount (); ++k)
        if (!ret[s].hasSameSettings (k, ret[0]))
          throw XsltException.locate (new XPathException ("XTDE2210",
                                                          "Corresponding xsl:merge-key elements have different effective attributes"),
                                      m_aSources.get (s).keys ().get (k).getElement ());
    }
    return ret;
  }

  /**
   * A streamable merge source delivers snapshots of the selected nodes (XSLT 3.0 section 15.4);
   * fn:snapshot keeps their accumulator values.
   */
  @NonNull
  private static XdmSequence _snapshot (@NonNull final XdmSequence aInput, @NonNull final XsltContext aAnchor)
  {
    final ICommonsList <XdmSequence> aArgs = new CommonsArrayList <> ();
    aArgs.add (aInput);
    return aAnchor.getEngine ()
                  .getStylesheet ()
                  .getFunctionLibrary ()
                  .getFunction (SNAPSHOT, 1)
                  .getImpl ()
                  .call (aAnchor.getDynamicContext (), aArgs);
  }

  /**
   * Create the dynamic error XTDE2220 for an input sequence that is not in the
   * order of its merge keys, located at the xsl:merge-source.
   *
   * @param aSource
   *        The merge source whose input is out of order.
   * @return The exception to throw.
   */
  @NonNull
  private static XPathException _notSorted (@NonNull final Source aSource)
  {
    return XsltException.locate (new XPathException ("XTDE2220",
                                                     "The input sequence of xsl:merge-source is not sorted by its merge keys"),
                                 aSource.element ());
  }

  /** @return The entries of one input sequence, sorted or checked to be sorted. */
  @NonNull
  private static ICommonsList <Entry> _collectEntries (@NonNull final Source aSource,
                                                       final int nSource,
                                                       final SortKeyDef.@NonNull Evaluated aKeys,
                                                       @NonNull final XsltContext aAnchor)
  {
    XdmSequence aInput = aSource.select ().evaluate (aAnchor);
    if (aSource.snapshot ())
      aInput = _snapshot (aInput, aAnchor);
    final ICommonsList <Entry> ret = new CommonsArrayList <> (aInput.size ());
    for (final IXdmItem aItem : aInput)
    {
      // Merge keys are evaluated with a singleton focus
      ret.add (new Entry (aItem, nSource, _computeKeys (aKeys, aAnchor.withFocus (aItem, 1, 1))));
    }
    if (aSource.sortBeforeMerge ())
      ret.sort ( (a, b) -> _compare (aKeys, a.keys (), b.keys ()));
    else
      for (int i = 1; i < ret.size (); ++i)
        if (_compare (aKeys, ret.get (i - 1).keys (), ret.get (i).keys ()) > 0)
          throw _notSorted (aSource);
    return ret;
  }

  /** Run the merge action for one merge group. */
  private void _executeGroup (@NonNull final XsltContext aBase,
                              @NonNull final ICommonsList <Entry> aGroup,
                              final int nPosition,
                              final int nSize,
                              @NonNull final IXsltOutput aOut)
  {
    final int nSources = m_aSources.size ();
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
                                       .withFocus (aGroup.getFirstOrNull ().item (), nPosition, nSize);
    m_aAction.execute (aGroupCtx, aOut);
  }

  /**
   * Move an input to its next entry: for a stream, the snapshot of the next record, whose
   * original is released at once.
   */
  private static void _advance (@NonNull final Input aInput)
  {
    if (aInput.m_aRun == null)
    {
      aInput.m_aHead = aInput.m_nNext < aInput.m_aEntries.size () ? aInput.m_aEntries.get (aInput.m_nNext++) : null;
      return;
    }
    final IXdmNode aRecord = aInput.m_aRun.nextRecord ();
    if (aRecord == null)
    {
      aInput.m_aHead = null;
      return;
    }
    final IXdmItem aSnapshot;
    try
    {
      aSnapshot = _snapshot (XdmSequence.of (aRecord), aInput.m_aAnchor).get (0);
    }
    finally
    {
      aInput.m_aRun.release (aRecord);
    }
    final Entry aEntry = new Entry (aSnapshot,
                                    aInput.m_nSource,
                                    _computeKeys (aInput.m_aKeys, aInput.m_aAnchor.withFocus (aSnapshot, 1, 1)));
    if (aInput.m_aHead != null && _compare (aInput.m_aKeys, aInput.m_aHead.keys (), aEntry.keys ()) > 0)
      throw _notSorted (aInput.m_aSource);
    aInput.m_aHead = aEntry;
  }

  /**
   * @return <code>true</code> if the source is read as a stream: it has a record path (declared
   *         streamable under the streaming claim, with for-each-source and without
   *         sort-before-merge, see {@link #_compileSource(CompileContext, XslElement)}), is not
   *         validated and only streamable accumulators apply to it.
   */
  private static boolean _isStreamed (@NonNull final Source aSource, @NonNull final XsltContext aCtx)
  {
    return aSource.recordPath () != null &&
           (aSource.validation () == null || !aSource.validation ().isValidating ()) &&
           AccumulatorExtension.isStreamable (aCtx.getEngine ().getStylesheet (), aSource.accumulators ());
  }

  /** Read all inputs together, one entry at a time, and run the action per group. */
  private void _mergeInputs (@NonNull final ICommonsList <Input> aInputs,
                             final SortKeyDef.@NonNull Evaluated aKeys,
                             @NonNull final XsltContext aCtx,
                             @NonNull final IXsltOutput aOut)
  {
    for (final Input aInput : aInputs)
      _advance (aInput);
    final XsltContext aBase = aCtx.withState (aCtx.getState ().withCurrentRule (null, aCtx.getState ().getCurrentMode ()));
    int nGroups = 0;
    while (true)
    {
      Entry aMin = null;
      for (final Input aInput : aInputs)
        if (aInput.m_aHead != null && (aMin == null || _compare (aKeys, aInput.m_aHead.keys (), aMin.keys ()) < 0))
          aMin = aInput.m_aHead;
      if (aMin == null)
        return;
      // The order of the stable sort: merge source, input sequence, input
      // order
      final XdmAtomic [] aGroupKeys = aMin.keys ();
      final ICommonsList <Entry> aGroup = new CommonsArrayList <> ();
      for (final Input aInput : aInputs)
        while (aInput.m_aHead != null && _compare (aKeys, aInput.m_aHead.keys (), aGroupKeys) == 0)
        {
          aGroup.add (aInput.m_aHead);
          _advance (aInput);
        }
      ++nGroups;
      // The number of groups is not known while the inputs are read; the
      // merge action does not ask for it (StreamVerdicts.isStreamedMerge)
      _executeGroup (aBase, aGroup, nGroups, nGroups, aOut);
      aCtx.getDynamicContext ().checkInterrupt ();
    }
  }

  /**
   * The merge with its streamable merge sources read as streams (XSLT 3.0 section 15.4): every
   * input is read one record at a time, as the merge needs the next one, so that memory holds one
   * merge group, not the inputs.
   *
   * @return <code>false</code> if a document cannot be read as a stream, before anything was
   *         executed.
   */
  private boolean _executeStreamed (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final Engine aEngine = aCtx.getEngine ();
    final SortKeyDef.Evaluated [] aEvaluated = _evaluateKeys (aCtx);
    final ICommonsList <XdmStreamReader> aReaders = new CommonsArrayList <> ();
    try
    {
      // Open every stream first: one that cannot be streamed makes the whole
      // merge run buffered
      final ICommonsList <ICommonsList <ResourceUri>> aUris = new CommonsArrayList <> ();
      for (final Source aSource : m_aSources)
      {
        if (!_isStreamed (aSource, aCtx))
        {
          aUris.add (null);
          continue;
        }
        final ICommonsList <ResourceUri> aSourceUris = _getSourceUris (aSource, aCtx);
        for (final ResourceUri aUri : aSourceUris)
        {
          final XdmStreamReader aReader = aEngine.openStreamedDocument (aUri);
          if (aReader == null)
            return false;
          aReaders.add (aReader);
        }
        aUris.add (aSourceUris);
      }
      final ICommonsList <Input> aInputs = new CommonsArrayList <> ();
      int nReader = 0;
      for (int s = 0; s < m_aSources.size (); ++s)
      {
        final Source aSource = m_aSources.get (s);
        if (aUris.get (s) == null)
        {
          for (final XsltContext aAnchor : _getAnchors (aSource, aCtx))
            aInputs.add (new Input (aSource, s, aEvaluated[s], aAnchor, _collectEntries (aSource, s, aEvaluated[s], aAnchor), null));
          continue;
        }
        final int nDocs = aUris.get (s).size ();
        for (int i = 0; i < nDocs; ++i)
        {
          final XdmStreamReader aReader = aReaders.get (nReader++);
          final IXdmNode aRoot = aReader.getDocument ();
          final XsltContext aAnchor = aCtx.withFocus (aRoot, i + 1, nDocs);
          final StreamedRun aRun = new StreamedRun (aReader, null);
          // Only the accumulators named in use-accumulators apply to the
          // document
          AccumulatorExtension.restrictTree (aEngine, aRoot, aSource.accumulators (), true);
          aRun.setAccumulators (AccumulatorExtension.startStreamed (aCtx, aRoot, aSource.accumulators ()));
          aRun.startPull (aSource.recordPath (), aAnchor);
          aInputs.add (new Input (aSource, s, aEvaluated[s], aAnchor, null, aRun));
        }
      }
      StreamedRun.countStreamedExecution ("merge");
      _mergeInputs (aInputs, aEvaluated[0], aCtx, aOut);
      return true;
    }
    finally
    {
      for (final XdmStreamReader aReader : aReaders)
        try
        {
          aReader.close ();
        }
        catch (final XdmParseException ex)
        {
          // Everything needed was read
        }
    }
  }

  /**
   * Run xsl:merge (XSLT 3.0 section 15). If the stream analysis classified this
   * xsl:merge as a streamed merge and a source has a record path, the streamed
   * run is tried first; it falls back to the buffered run only when a document
   * cannot be opened as a stream. The buffered run evaluates the merge keys
   * (XTDE2210 if corresponding keys differ), collects the entries of every
   * input sequence of every merge source (sorted with sort-before-merge,
   * otherwise checked: XTDE2220), sorts all of them stably by the keys of the
   * first source (so equal keys keep merge source, input sequence and input
   * order), splits them into groups of equal keys and runs the merge action
   * once per group, with the group's first item as context item, the group
   * number as position and the number of groups as size. The current template
   * rule is cleared for the action.
   *
   * @param aCtx
   *        The context of the xsl:merge instruction.
   * @param aOut
   *        The output that receives the result of the merge actions.
   */
  private void _execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    // With the streaming claim: the streamable merge sources as streams
    if (m_aSources.containsAny (x -> x.recordPath () != null) &&
        aCtx.getEngine ().getStylesheet ().getStreamVerdicts () != null &&
        aCtx.getEngine ().getStylesheet ().getStreamVerdicts ().isStreamedMerge (m_aElement) &&
        _executeStreamed (aCtx, aOut))
      return;

    final int nSources = m_aSources.size ();
    final SortKeyDef.Evaluated [] aEvaluated = _evaluateKeys (aCtx);
    final ICommonsList <Entry> aAll = new CommonsArrayList <> ();
    for (int s = 0; s < nSources; ++s)
    {
      final Source aSource = m_aSources.get (s);
      for (final XsltContext aAnchor : _getAnchors (aSource, aCtx))
        aAll.addAll (_collectEntries (aSource, s, aEvaluated[s], aAnchor));
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
      _executeGroup (aBase, aGroups.get (g), g + 1, nGroups, aOut);
  }

  /**
   * Compile one xsl:merge-source (XSLT 3.0 section 15.3) with its
   * xsl:merge-key children (section 15.5). The streamable flag is only taken
   * when the streaming claim is on; a record path for reading as a stream is
   * derived from the select expression only for a streamable source with
   * for-each-source and without sort-before-merge.
   *
   * @param aCtx
   *        The compile context.
   * @param aEl
   *        The xsl:merge-source element.
   * @return The compiled merge source.
   * @throws XsltException
   *         XTSE3195 for both for-each-item and for-each-source, XTSE0020 for a
   *         name that is no NCName, an invalid boolean in sort-before-merge or
   *         streamable or an invalid validation value, XTSE1660 for
   *         validation strict/lax or a type attribute without a schema-aware
   *         processor, XTSE0010 for a child other than xsl:merge-key or for no
   *         xsl:merge-key at all.
   */
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
    final ValidationRequest aValidation = ValidationRequest.compile (aCtx,
                                                                     aEl,
                                                                     ValidationRequest.EKind.SOURCE_DOCUMENT);
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
    final boolean bSortBefore = sSortBefore != null && XsltNames.isXsltTrue (sSortBefore);
    final XPathExpr aSelect = aCtx.compileRequiredExpression (aEl, "select");
    final XPathExpr aForEachSource = aCtx.compileOptionalExpression (aEl, "for-each-source");
    final boolean bStreamable = XsltNames.isXsltTrue (aEl.getAttr ("streamable")) &&
                                aCtx.getCompiler ().getOptions ().isStreamingClaim ();
    // Read as a stream: the records of the documents of for-each-source
    final StreamRecordPath aRecordPath = bStreamable && aForEachSource != null && !bSortBefore ? StreamRecordPath.of (aSelect)
                                                                                               : null;
    return new Source (aEl,
                       sName == null ? null : sName.trim (),
                       aSelect,
                       aCtx.compileOptionalExpression (aEl, "for-each-item"),
                       aForEachSource,
                       bSortBefore,
                       aValidation,
                       aKeys,
                       AccumulatorExtension.registerUseAccumulators (aCtx, aEl),
                       bStreamable,
                       aRecordPath);
  }

  /**
   * The static part of XTDE2210: corresponding merge keys must agree in the presence of lang,
   * order, collation, case-order and data-type, and in the values of those written without
   * attribute value templates.
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
        throw XsltException.at (aOther,
                                "XTDE2210",
                                "Corresponding xsl:merge-key elements differ in the presence of " + sAttr);
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
        throw XsltException.at (aOther,
                                "XTDE2210",
                                "Corresponding xsl:merge-key elements have different values for " + sAttr);
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
          throw XsltException.at (aChild,
                                  "XTSE2200",
                                  "All xsl:merge-source elements must have the same number of xsl:merge-key children");
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
            throw XsltException.at (aChild,
                                    "XTSE0010",
                                    "xsl:" + aChild.getLocalName () + " is not allowed in xsl:merge");
    }
    if (aSources.isEmpty ())
      throw XsltException.at (aEl, "XTSE0010", "xsl:merge requires at least one xsl:merge-source");
    if (aAction == null)
      throw XsltException.at (aEl, "XTSE0010", "xsl:merge requires an xsl:merge-action");
    for (int s = 1; s < aSources.size (); ++s)
      for (int k = 0; k < aSources.get (s).keys ().size (); ++k)
        _checkCorrespondingKeys (aSources.getFirstOrNull ().keys ().get (k).getElement (),
                                 aSources.get (s).keys ().get (k).getElement ());
    final MergeInstruction aInstr = new MergeInstruction (aEl, aSources, aAction);
    return new CompiledInstruction (aEl, aInstr::_execute);
  }
}
