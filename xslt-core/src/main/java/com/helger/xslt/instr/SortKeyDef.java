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
package com.helger.xslt.instr;

import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.collation.ICollation;
import com.helger.xpath.ops.AtomicCaster;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xslt.compile.Avt;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.StaticContexts;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.runtime.Engine;
import com.helger.xslt.runtime.SequenceOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * A compiled xsl:sort (XSLT 3.0 section 13), plus the sorting of a sequence by
 * a list of sort keys.
 *
 * @author Philip Helger
 */
@Immutable
public final class SortKeyDef
{
  private final XslElement m_aElement;
  private final XPathExpr m_aSelect;
  private final SequenceConstructor m_aBody;
  private final Avt m_aOrder;
  private final Avt m_aDataType;
  private final Avt m_aCaseOrder;
  private final Avt m_aCollation;
  private final Avt m_aLang;
  private final Avt m_aStable;
  private final String m_sDefaultCollation;

  private SortKeyDef (@NonNull final XslElement aElement,
                      @Nullable final XPathExpr aSelect,
                      @Nullable final SequenceConstructor aBody,
                      @Nullable final Avt aOrder,
                      @Nullable final Avt aDataType,
                      @Nullable final Avt aCaseOrder,
                      @Nullable final Avt aCollation,
                      @Nullable final Avt aLang,
                      @Nullable final Avt aStable,
                      @NonNull final String sDefaultCollation)
  {
    m_aElement = aElement;
    m_aSelect = aSelect;
    m_aBody = aBody;
    m_aOrder = aOrder;
    m_aDataType = aDataType;
    m_aCaseOrder = aCaseOrder;
    m_aCollation = aCollation;
    m_aLang = aLang;
    m_aStable = aStable;
    m_sDefaultCollation = sDefaultCollation;
  }

  @NonNull
  public XslElement getElement ()
  {
    return m_aElement;
  }

  /**
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        An xsl:sort element (or another element with the same attributes,
   *        such as xsl:merge-key).
   * @return The compiled sort key.
   */
  @NonNull
  public static SortKeyDef compile (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    SequenceConstructor aBody = aSelect == null ? aCtx.compileSequenceConstructor (aEl, aEl.getChildren ()) : null;
    if (aBody != null && aBody.isEmpty ())
    {
      // Neither select nor content: the sort key is the item itself
      aSelect = aCtx.compileExpression (aEl, ".");
      aBody = null;
    }
    return new SortKeyDef (aEl,
                           aSelect,
                           aBody,
                           aCtx.compileOptionalAvt (aEl, "order"),
                           aCtx.compileOptionalAvt (aEl, "data-type"),
                           aCtx.compileOptionalAvt (aEl, "case-order"),
                           aCtx.compileOptionalAvt (aEl, "collation"),
                           aCtx.compileOptionalAvt (aEl, "lang"),
                           aCtx.compileOptionalAvt (aEl, "stable"),
                           StaticContexts.getDefaultCollation (aEl));
  }

  /** The evaluated attributes of a sort key. */
  private record Settings (boolean descending, @Nullable String dataType, int caseOrder, @Nullable ICollation collation)
  {}

  @NonNull
  private Settings _evaluateSettings (@NonNull final XsltContext aCtx)
  {
    boolean bDescending = false;
    if (m_aOrder != null)
    {
      final String s = m_aOrder.evaluate (aCtx).trim ();
      if ("descending".equals (s))
        bDescending = true;
      else
        if (!"ascending".equals (s))
          throw new XPathException ("XTDE0030", "order='" + s + "' is not ascending or descending");
    }
    String sDataType = null;
    if (m_aDataType != null)
    {
      final String s = m_aDataType.evaluate (aCtx).trim ();
      if (!"text".equals (s) && !"number".equals (s))
      {
        if (s.indexOf (':') < 0)
          throw new XPathException ("XTDE0030", "data-type='" + s + "' is not text or number");
      }
      else
        sDataType = s;
    }
    int nCaseOrder = 0;
    if (m_aCaseOrder != null && m_aCollation == null)
    {
      final String s = m_aCaseOrder.evaluate (aCtx).trim ();
      if ("upper-first".equals (s))
        nCaseOrder = 1;
      else
        if ("lower-first".equals (s))
          nCaseOrder = -1;
        else
          throw new XPathException ("XTDE0030", "case-order='" + s + "' is not upper-first or lower-first");
    }
    if (m_aStable != null)
    {
      final String s = m_aStable.evaluate (aCtx).trim ();
      if (!XsltNames.isXsltBooleanLexical (s))
        throw new XPathException ("XTDE0030", "stable='" + s + "' is not a boolean");
    }
    if (m_aLang != null)
    {
      final String s = m_aLang.evaluate (aCtx).trim ();
      if (!s.isEmpty () && !s.matches ("[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*"))
        throw new XPathException ("XTDE0030", "lang='" + s + "' is not a valid language code");
    }
    String sCollation = m_sDefaultCollation;
    if (m_aCollation != null)
      sCollation = CollationRegistry.resolveUri (m_aCollation.evaluate (aCtx).trim (), m_aElement.getStaticBaseUri ());
    final ICollation aCollation;
    if (CollationRegistry.CODEPOINT_URI.equals (sCollation))
      aCollation = null;
    else
    {
      aCollation = CollationRegistry.getCollation (sCollation);
      if (aCollation == null)
        throw new XPathException ("XTDE1035", "The collation '" + sCollation + "' is not supported");
    }
    return new Settings (bDescending, sDataType, nCaseOrder, aCollation);
  }

  @Nullable
  private XdmAtomic _computeKey (@NonNull final XsltContext aCtx, @NonNull final Settings aSettings)
  {
    final XdmSequence aValue;
    if (m_aSelect != null)
      aValue = m_aSelect.evaluate (aCtx);
    else
    {
      final SequenceOutput aOut = new SequenceOutput ();
      m_aBody.execute (Engine.withTemporaryOutputState (aCtx), aOut);
      aValue = aOut.getSequence ();
    }
    final XdmSequence aAtoms = Atomizer.atomize (aValue);
    if (aAtoms.isEmpty ())
      return null;
    if (aAtoms.size () > 1)
      throw new XPathException ("XTTE1020", "A sort key evaluates to more than one item");
    final XdmAtomic aAtom = (XdmAtomic) aAtoms.get (0);
    if ("text".equals (aSettings.dataType ()))
      return XdmAtomic.string (Atomizer.getStringValue (aAtom));
    if ("number".equals (aSettings.dataType ()))
    {
      if (aAtom.getType ().isNumeric ())
        return XdmAtomic.dbl (aAtom.getAsDouble ());
      try
      {
        return AtomicCaster.castFromString (Atomizer.getStringValue (aAtom).trim (),
                                            EAtomicType.DOUBLE,
                                            aCtx.getDynamicContext ().getStaticContext ());
      }
      catch (final XPathException ex)
      {
        return XdmAtomic.dbl (Double.NaN);
      }
    }
    if (aAtom.getType () == EAtomicType.UNTYPED_ATOMIC)
      return XdmAtomic.string (aAtom.getString ());
    return aAtom;
  }

  private static int _compareCase (@NonNull final String a, @NonNull final String b, final int nCaseOrder)
  {
    final int nLower = a.toLowerCase (Locale.ROOT).compareTo (b.toLowerCase (Locale.ROOT));
    if (nLower != 0)
      return nLower;
    for (int i = 0; i < Math.min (a.length (), b.length ()); ++i)
    {
      final char ca = a.charAt (i);
      final char cb = b.charAt (i);
      if (ca != cb)
      {
        final boolean bUpperA = Character.isUpperCase (ca);
        return bUpperA ? -nCaseOrder : nCaseOrder;
      }
    }
    return Integer.compare (a.length (), b.length ());
  }

  private static boolean _isNaN (@NonNull final XdmAtomic a)
  {
    return a.getType ().isNumeric () && Double.isNaN (a.getAsDouble ());
  }

  private static int _compare (@Nullable final XdmAtomic a,
                               @Nullable final XdmAtomic b,
                               @NonNull final Settings aSettings,
                               @NonNull final XsltContext aCtx)
  {
    if (a == null || b == null)
      return a == null ? b == null ? 0 : -1 : 1;
    final boolean bNaNA = _isNaN (a);
    final boolean bNaNB = _isNaN (b);
    if (bNaNA || bNaNB)
      return bNaNA ? bNaNB ? 0 : -1 : 1;
    if (aSettings.caseOrder () != 0 && aSettings.collation () == null && a.isStringLike () && b.isStringLike ())
      return _compareCase (a.getString (), b.getString (), aSettings.caseOrder ());
    try
    {
      return AtomicComparator.compareForOrder (a, b, aSettings.collation (), aCtx.getDynamicContext ());
    }
    catch (final XPathException ex)
    {
      throw new XPathException ("XTDE1030", "Sort key values are not comparable: " + ex.getDescription ());
    }
  }

  /**
   * A list of sort keys whose attributes (order, data-type, collation, ...)
   * have been evaluated once, for instructions that evaluate the key values
   * themselves: xsl:for-each-group sorts groups, xsl:merge compares merge
   * keys. Valid for one execution of the instruction only.
   */
  public static final class Evaluated
  {
    private final ICommonsList <SortKeyDef> m_aKeys;
    private final Settings [] m_aSettings;
    private final XsltContext m_aCtx;

    private Evaluated (@NonNull final ICommonsList <SortKeyDef> aKeys,
                       @NonNull final Settings [] aSettings,
                       @NonNull final XsltContext aCtx)
    {
      m_aKeys = aKeys;
      m_aSettings = aSettings;
      m_aCtx = aCtx;
    }

    /** @return The number of keys. */
    public int getKeyCount ()
    {
      return m_aSettings.length;
    }

    /**
     * @param aItemCtx
     *        The context in which the key values are evaluated (focus on the
     *        item to sort).
     * @return One value per key; <code>null</code> for an empty key.
     * @throws XPathException
     *         XTTE1020 if a key value has more than one item
     */
    @NonNull
    public XdmAtomic [] computeKeys (@NonNull final XsltContext aItemCtx)
    {
      final XdmAtomic [] ret = new XdmAtomic [m_aSettings.length];
      for (int k = 0; k < m_aSettings.length; ++k)
        ret[k] = m_aKeys.get (k)._computeKey (aItemCtx, m_aSettings[k]);
      return ret;
    }

    /**
     * Compare two key tuples computed with {@link #computeKeys(XsltContext)}.
     *
     * @param a
     *        First tuple.
     * @param b
     *        Second tuple.
     * @return negative, 0 or positive, honouring the order of each key.
     * @throws XPathException
     *         XTDE1030 if two values are not comparable
     */
    public int compare (@NonNull final XdmAtomic [] a, @NonNull final XdmAtomic [] b)
    {
      for (int k = 0; k < m_aSettings.length; ++k)
      {
        int c = _compare (a[k], b[k], m_aSettings[k], m_aCtx);
        if (m_aSettings[k].descending ())
          c = -c;
        if (c != 0)
          return c;
      }
      return 0;
    }

    /**
     * @param nKey
     *        Key index.
     * @param aOther
     *        Other evaluated keys.
     * @return <code>true</code> if key <code>nKey</code> has the same
     *         effective order, data type, case order and collation in both.
     */
    public boolean hasSameSettings (final int nKey, @NonNull final Evaluated aOther)
    {
      final Settings a = m_aSettings[nKey];
      final Settings b = aOther.m_aSettings[nKey];
      if (a.descending () != b.descending () || a.caseOrder () != b.caseOrder ())
        return false;
      if (a.dataType () == null ? b.dataType () != null : !a.dataType ().equals (b.dataType ()))
        return false;
      final String sCollA = a.collation () == null ? CollationRegistry.CODEPOINT_URI : a.collation ().getUri ();
      final String sCollB = b.collation () == null ? CollationRegistry.CODEPOINT_URI : b.collation ().getUri ();
      return sCollA.equals (sCollB);
    }
  }

  /**
   * Evaluate the attributes of sort keys.
   *
   * @param aKeys
   *        The sort keys, most significant first.
   * @param aCtx
   *        The context of the instruction (for the attribute value
   *        templates).
   * @return The evaluated keys.
   */
  @NonNull
  public static Evaluated evaluate (@NonNull final ICommonsList <SortKeyDef> aKeys, @NonNull final XsltContext aCtx)
  {
    final Settings [] aSettings = new Settings [aKeys.size ()];
    for (int k = 0; k < aSettings.length; ++k)
      aSettings[k] = aKeys.get (k)._evaluateSettings (aCtx);
    return new Evaluated (aKeys, aSettings, aCtx);
  }

  /**
   * Sort a sequence.
   *
   * @param aKeys
   *        The sort keys, most significant first.
   * @param aSeq
   *        The sequence.
   * @param aCtx
   *        The context of the sorting instruction (for the attribute value
   *        templates of the keys); each key is evaluated with the item as
   *        focus.
   * @return The sorted sequence (stable).
   */
  @NonNull
  public static XdmSequence sort (@NonNull final ICommonsList <SortKeyDef> aKeys,
                                  @NonNull final XdmSequence aSeq,
                                  @NonNull final XsltContext aCtx)
  {
    final int nItems = aSeq.size ();
    if (aKeys.isEmpty ())
      return aSeq;
    final int nKeys = aKeys.size ();
    final Settings [] aSettings = new Settings [nKeys];
    for (int k = 0; k < nKeys; ++k)
      aSettings[k] = aKeys.get (k)._evaluateSettings (aCtx);
    if (nItems < 2)
      return aSeq;
    final XdmAtomic [] [] aValues = new XdmAtomic [nItems] [nKeys];
    final XsltContext aBase = aCtx.withState (aCtx.getState ().withCurrentRule (null, aCtx.getState ().getCurrentMode ()));
    for (int i = 0; i < nItems; ++i)
    {
      final XsltContext aItemCtx = aBase.withFocus (aSeq.get (i), i + 1, nItems);
      for (int k = 0; k < nKeys; ++k)
        aValues[i][k] = aKeys.get (k)._computeKey (aItemCtx, aSettings[k]);
    }
    final ICommonsList <Integer> aOrder = new CommonsArrayList <> (nItems);
    for (int i = 0; i < nItems; ++i)
      aOrder.add (Integer.valueOf (i));
    aOrder.sort ( (x, y) -> {
      final int nX = x.intValue ();
      final int nY = y.intValue ();
      for (int k = 0; k < nKeys; ++k)
      {
        int c = _compare (aValues[nX][k], aValues[nY][k], aSettings[k], aCtx);
        if (aSettings[k].descending ())
          c = -c;
        if (c != 0)
          return c;
      }
      return 0;
    });
    final XdmSequence.Builder ret = new XdmSequence.Builder ();
    for (final Integer aIdx : aOrder)
      ret.add (aSeq.get (aIdx.intValue ()));
    return ret.build ();
  }
}
