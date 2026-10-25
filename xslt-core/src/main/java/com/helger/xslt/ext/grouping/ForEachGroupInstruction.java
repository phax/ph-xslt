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
package com.helger.xslt.ext.grouping;

import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.XPathException;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.collation.ICollation;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.pattern.CompiledXsltPattern;
import com.helger.xpath.pattern.XsltPattern;
import com.helger.xpath.pattern.XsltPatternParser;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.Avt;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.StaticContexts;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.instr.CompiledInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.instr.SortKeyDef;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * xsl:for-each-group (XSLT 3.0 section 14): group-by, group-adjacent,
 * group-starting-with and group-ending-with, composite keys, collations and
 * sorting of the groups.
 *
 * @author Philip Helger
 */
@Immutable
public final class ForEachGroupInstruction
{
  private enum EMode
  {
    BY,
    ADJACENT,
    STARTING_WITH,
    ENDING_WITH
  }

  /** One group being formed. */
  private static final class Group
  {
    private final ICommonsList <IXdmItem> m_aItems = new CommonsArrayList <> ();
    /** The key components, <code>null</code> for pattern based grouping. */
    private final XdmAtomic [] m_aKey;

    Group (@Nullable final XdmAtomic [] aKey)
    {
      m_aKey = aKey;
    }
  }

  private final XslElement m_aElement;
  private final XPathExpr m_aSelect;
  private final EMode m_eMode;
  private final XPathExpr m_aKeyExpr;
  private final XsltPattern m_aPattern;
  private final StaticContext m_aPatternSC;
  private final boolean m_bComposite;
  private final Avt m_aCollation;
  private final String m_sDefaultCollation;
  private final ICommonsList <SortKeyDef> m_aSorts;
  private final SequenceConstructor m_aBody;

  private ForEachGroupInstruction (@NonNull final XslElement aElement,
                                   @NonNull final XPathExpr aSelect,
                                   @NonNull final EMode eMode,
                                   @Nullable final XPathExpr aKeyExpr,
                                   @Nullable final XsltPattern aPattern,
                                   @Nullable final StaticContext aPatternSC,
                                   final boolean bComposite,
                                   @Nullable final Avt aCollation,
                                   @NonNull final String sDefaultCollation,
                                   @NonNull final ICommonsList <SortKeyDef> aSorts,
                                   @NonNull final SequenceConstructor aBody)
  {
    m_aElement = aElement;
    m_aSelect = aSelect;
    m_eMode = eMode;
    m_aKeyExpr = aKeyExpr;
    m_aPattern = aPattern;
    m_aPatternSC = aPatternSC;
    m_bComposite = bComposite;
    m_aCollation = aCollation;
    m_sDefaultCollation = sDefaultCollation;
    m_aSorts = aSorts;
    m_aBody = aBody;
  }

  private static void _checkPatternFunctions (@NonNull final XslElement aEl, @NonNull final XsltPattern aPattern)
  {
    if (!(aPattern instanceof final CompiledXsltPattern aCP))
      return;
    final String [] [] aForbidden = { { "current-group", "XTSE1060" },
                                      { "current-grouping-key", "XTSE1070" },
                                      { "current-merge-group", "XTSE3470" },
                                      { "current-merge-key", "XTSE3500" } };
    for (final String [] aEntry : aForbidden)
    {
      final Predicate <XdmQName> aFilter = n -> XdmConstants.NS_FN.equals (n.namespaceUri ()) && aEntry[0].equals (n.localName ());
      if (aCP.containsFunction (aFilter))
        throw XsltException.at (aEl, aEntry[1], aEntry[0] + "() may not be used in a pattern");
    }
  }

  @Nullable
  private ICollation _resolveCollation (@NonNull final XsltContext aCtx)
  {
    String sUri = m_sDefaultCollation;
    if (m_aCollation != null)
      sUri = CollationRegistry.resolveUri (m_aCollation.evaluate (aCtx).trim (), m_aElement.getStaticBaseUri ());
    if (CollationRegistry.CODEPOINT_URI.equals (sUri))
      return null;
    final ICollation ret = CollationRegistry.getCollation (sUri);
    if (ret == null)
      throw new XPathException ("XTDE1110", "The collation '" + sUri + "' is not supported");
    return ret;
  }

  @NonNull
  private XdmAtomic [] _computeKey (@NonNull final XsltContext aItemCtx)
  {
    final XdmSequence aAtoms = Atomizer.atomize (m_aKeyExpr.evaluate (aItemCtx));
    final XdmAtomic [] ret = new XdmAtomic [aAtoms.size ()];
    for (int i = 0; i < ret.length; ++i)
      ret[i] = (XdmAtomic) aAtoms.get (i);
    return ret;
  }

  private static boolean _sameKey (@NonNull final XdmAtomic [] a,
                                   @NonNull final XdmAtomic [] b,
                                   @Nullable final ICollation aCollation,
                                   @NonNull final DynamicContext aDyn)
  {
    if (a.length != b.length)
      return false;
    for (int i = 0; i < a.length; ++i)
      if (!AtomicComparator.isEqualForDistinct (a[i], b[i], aCollation, aDyn))
        return false;
    return true;
  }

  /**
   * @return A hash bucket for a single key value, consistent with
   *         {@link AtomicComparator#isEqualForDistinct}: only string-like
   *         values under the codepoint collation get their own bucket,
   *         everything else shares one per kind.
   */
  @NonNull
  private static Object _bucket (@NonNull final XdmAtomic aKey, @Nullable final ICollation aCollation)
  {
    if (aKey.isStringLike ())
      return aCollation == null ? aKey.getString () : "";
    if (aKey.getType ().isNumeric ())
      return Boolean.TRUE;
    return Boolean.FALSE;
  }

  @NonNull
  private ICommonsList <Group> _formByGroups (@NonNull final XdmSequence aPop,
                                              @NonNull final XsltContext aCtx,
                                              @Nullable final ICollation aCollation)
  {
    final DynamicContext aDyn = aCtx.getDynamicContext ();
    final ICommonsList <Group> ret = new CommonsArrayList <> ();
    final ICommonsMap <Object, ICommonsList <Group>> aBuckets = new CommonsHashMap <> ();
    final int nSize = aPop.size ();
    for (int i = 0; i < nSize; ++i)
    {
      final IXdmItem aItem = aPop.get (i);
      final XdmAtomic [] aKey = _computeKey (aCtx.withFocus (aItem, i + 1, nSize));
      if (m_bComposite)
      {
        // One composite key per item: the item joins exactly one group
        Group aTarget = null;
        for (final Group aGroup : ret)
          if (_sameKey (aKey, aGroup.m_aKey, aCollation, aDyn))
          {
            aTarget = aGroup;
            break;
          }
        if (aTarget == null)
        {
          aTarget = new Group (aKey);
          ret.add (aTarget);
        }
        aTarget.m_aItems.add (aItem);
        continue;
      }
      // Each distinct key value: the item joins one group per value
      final ICommonsList <Group> aJoined = new CommonsArrayList <> ();
      for (final XdmAtomic aValue : aKey)
      {
        final ICommonsList <Group> aBucket = aBuckets.computeIfAbsent (_bucket (aValue, aCollation), k -> new CommonsArrayList <> ());
        Group aTarget = null;
        for (final Group aGroup : aBucket)
          if (AtomicComparator.isEqualForDistinct (aValue, aGroup.m_aKey[0], aCollation, aDyn))
          {
            aTarget = aGroup;
            break;
          }
        if (aTarget == null)
        {
          aTarget = new Group (new XdmAtomic [] { aValue });
          aBucket.add (aTarget);
          ret.add (aTarget);
        }
        final Group aFinalTarget = aTarget;
        if (!aJoined.containsAny (x -> x == aFinalTarget))
        {
          aJoined.add (aFinalTarget);
          aFinalTarget.m_aItems.add (aItem);
        }
      }
      aCtx.getDynamicContext ().checkInterrupt ();
    }
    return ret;
  }

  @NonNull
  private ICommonsList <Group> _formAdjacentGroups (@NonNull final XdmSequence aPop,
                                                    @NonNull final XsltContext aCtx,
                                                    @Nullable final ICollation aCollation)
  {
    final DynamicContext aDyn = aCtx.getDynamicContext ();
    final ICommonsList <Group> ret = new CommonsArrayList <> ();
    Group aCur = null;
    final int nSize = aPop.size ();
    for (int i = 0; i < nSize; ++i)
    {
      final IXdmItem aItem = aPop.get (i);
      final XdmAtomic [] aKey = _computeKey (aCtx.withFocus (aItem, i + 1, nSize));
      if (!m_bComposite && aKey.length != 1)
        throw new XPathException ("XTTE1100",
                                  "The group-adjacent key of an item must be a single atomic value, but has " + aKey.length + " items");
      if (aCur == null || !_sameKey (aKey, aCur.m_aKey, aCollation, aDyn))
      {
        aCur = new Group (aKey);
        ret.add (aCur);
      }
      aCur.m_aItems.add (aItem);
    }
    return ret;
  }

  private boolean _matches (@NonNull final IXdmItem aItem, @NonNull final DynamicContext aPatternDyn)
  {
    return m_aPattern.matches (aItem, aPatternDyn.withCurrentItem (aItem));
  }

  @NonNull
  private ICommonsList <Group> _formPatternGroups (@NonNull final XdmSequence aPop, @NonNull final XsltContext aCtx)
  {
    // Patterns see the local variables, but no current group, captured
    // substrings or other XSLT state
    final DynamicContext aPatternDyn = aCtx.getDynamicContext ()
                                           .withStaticContext (m_aPatternSC)
                                           .withHostState (aCtx.getState ().forFunctionCall ());
    final ICommonsList <Group> ret = new CommonsArrayList <> ();
    Group aCur = null;
    for (final IXdmItem aItem : aPop)
      if (m_eMode == EMode.STARTING_WITH)
      {
        if (aCur == null || _matches (aItem, aPatternDyn))
        {
          aCur = new Group (null);
          ret.add (aCur);
        }
        aCur.m_aItems.add (aItem);
      }
      else
      {
        if (aCur == null)
        {
          aCur = new Group (null);
          ret.add (aCur);
        }
        aCur.m_aItems.add (aItem);
        if (_matches (aItem, aPatternDyn))
          aCur = null;
      }
    return ret;
  }

  @Nullable
  private XdmSequence _keyValue (@NonNull final Group aGroup)
  {
    return aGroup.m_aKey == null ? null : XdmSequence.of (aGroup.m_aKey);
  }

  @NonNull
  private XsltContext _groupContext (@NonNull final XsltContext aBase,
                                     @NonNull final Group aGroup,
                                     final int nPosition,
                                     final int nSize)
  {
    return aBase.with (GroupingExtension.CURRENT_GROUP, XdmSequence.of (aGroup.m_aItems))
                .with (GroupingExtension.CURRENT_GROUPING_KEY, _keyValue (aGroup))
                .withFocus (aGroup.m_aItems.getFirstOrNull (), nPosition, nSize);
  }

  @NonNull
  private ICommonsList <Group> _sortGroups (@NonNull final ICommonsList <Group> aGroups,
                                            @NonNull final XsltContext aCtx,
                                            @NonNull final XsltContext aBase)
  {
    final SortKeyDef.Evaluated aKeys = SortKeyDef.evaluate (m_aSorts, aCtx);
    final int nSize = aGroups.size ();
    final XdmAtomic [] [] aValues = new XdmAtomic [nSize] [];
    for (int i = 0; i < nSize; ++i)
      aValues[i] = aKeys.computeKeys (_groupContext (aBase, aGroups.get (i), i + 1, nSize));
    final ICommonsList <Integer> aOrder = new CommonsArrayList <> (nSize);
    for (int i = 0; i < nSize; ++i)
      aOrder.add (Integer.valueOf (i));
    aOrder.sort ( (x, y) -> aKeys.compare (aValues[x.intValue ()], aValues[y.intValue ()]));
    final ICommonsList <Group> ret = new CommonsArrayList <> (nSize);
    for (final Integer aIdx : aOrder)
      ret.add (aGroups.get (aIdx.intValue ()));
    return ret;
  }

  private void _execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final XdmSequence aPop = m_aSelect.evaluate (aCtx);
    ICommonsList <Group> aGroups;
    switch (m_eMode)
    {
      case BY:
        aGroups = _formByGroups (aPop, aCtx, _resolveCollation (aCtx));
        break;
      case ADJACENT:
        aGroups = _formAdjacentGroups (aPop, aCtx, _resolveCollation (aCtx));
        break;
      default:
        aGroups = _formPatternGroups (aPop, aCtx);
        break;
    }
    final XsltContext aBase = aCtx.withState (aCtx.getState ().withCurrentRule (null, aCtx.getState ().getCurrentMode ()));
    if (m_aSorts.isNotEmpty () && aGroups.size () > 0)
      aGroups = _sortGroups (aGroups, aCtx, aBase);
    final int nSize = aGroups.size ();
    for (int i = 0; i < nSize; ++i)
      m_aBody.execute (_groupContext (aBase, aGroups.get (i), i + 1, nSize), aOut);
  }

  /**
   * Compile an xsl:for-each-group element.
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
    final XPathExpr aSelect = aCtx.compileRequiredExpression (aEl, "select");
    EMode eMode = null;
    XPathExpr aKeyExpr = null;
    XsltPattern aPattern = null;
    StaticContext aPatternSC = null;
    if (aEl.hasAttr ("group-by"))
    {
      eMode = EMode.BY;
      aKeyExpr = aCtx.compileRequiredExpression (aEl, "group-by");
    }
    else
      if (aEl.hasAttr ("group-adjacent"))
      {
        eMode = EMode.ADJACENT;
        aKeyExpr = aCtx.compileRequiredExpression (aEl, "group-adjacent");
      }
      else
      {
        final String sAttr = aEl.hasAttr ("group-starting-with") ? "group-starting-with" : "group-ending-with";
        final String sPattern = aEl.getAttr (sAttr);
        if (sPattern == null)
          throw XsltException.at (aEl, "XTSE1080", "xsl:for-each-group requires exactly one grouping attribute");
        eMode = aEl.hasAttr ("group-starting-with") ? EMode.STARTING_WITH : EMode.ENDING_WITH;
        aPatternSC = aCtx.createStaticContext (aEl);
        try
        {
          aPattern = XsltPatternParser.parse (sPattern, aPatternSC);
        }
        catch (final XPathException ex)
        {
          throw XsltException.locate (ex, aEl);
        }
        _checkPatternFunctions (aEl, aPattern);
      }
    final String sComposite = aEl.getAttr ("composite");
    final boolean bComposite = sComposite != null && XsltNames.isXsltTrue (sComposite);
    final Avt aCollation = aCtx.compileOptionalAvt (aEl, "collation");
    if (aCollation != null && eMode != EMode.BY && eMode != EMode.ADJACENT)
      throw XsltException.at (aEl, "XTSE1090", "The collation attribute requires group-by or group-adjacent");
    if (bComposite && eMode != EMode.BY && eMode != EMode.ADJACENT)
      throw XsltException.at (aEl, "XTSE1090", "The composite attribute requires group-by or group-adjacent");
    final ICommonsList <SortKeyDef> aSorts = aCtx.compileSortKeys (aEl);
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    final ForEachGroupInstruction aInstr = new ForEachGroupInstruction (aEl,
                                                                        aSelect,
                                                                        eMode,
                                                                        aKeyExpr,
                                                                        aPattern,
                                                                        aPatternSC,
                                                                        bComposite,
                                                                        aCollation,
                                                                        StaticContexts.getDefaultCollation (aEl),
                                                                        aSorts,
                                                                        aBody);
    return new CompiledInstruction (aEl, aInstr::_execute);
  }
}
