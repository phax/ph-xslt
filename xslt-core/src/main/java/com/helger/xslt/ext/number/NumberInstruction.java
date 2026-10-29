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
package com.helger.xslt.ext.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.pattern.CompiledXsltPattern;
import com.helger.xpath.pattern.XsltPattern;
import com.helger.xpath.pattern.XsltPatternParser;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.Avt;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.instr.AbstractInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;

/**
 * xsl:number (XSLT 3.0 section 12): either formats the integers of the value
 * attribute, or numbers a node of the source tree by its position according to
 * level (single, multiple, any) and the count and from patterns, and writes the
 * formatted result as a text node.
 *
 * @author Philip Helger
 */
@Immutable
public final class NumberInstruction extends AbstractInstruction
{
  private enum ELevel
  {
    SINGLE,
    MULTIPLE,
    ANY
  }

  /**
   * A count or from pattern. Unlike template patterns these may refer to the
   * local variables in scope of the xsl:number element, so they are compiled
   * with the full static context and matched in the run-time context.
   *
   * @param pattern
   *        The pattern.
   * @param staticContext
   *        Its static context.
   */
  private record NumberPattern (@NonNull XsltPattern pattern, @NonNull StaticContext staticContext)
  {
    boolean matches (@NonNull final IXdmNode aNode, @NonNull final XsltContext aCtx)
    {
      return pattern.matches (aNode, aCtx.getDynamicContext ().withStaticContext (staticContext));
    }
  }

  private static final String [] [] FORBIDDEN_PATTERN_FUNCTIONS = { { "current-group", "XTSE1060" },
                                                                   { "current-grouping-key", "XTSE1070" },
                                                                   { "current-merge-group", "XTSE3470" },
                                                                   { "current-merge-key", "XTSE3500" } };

  private final XPathExpr m_aValue;
  private final XPathExpr m_aSelect;
  private final NumberPattern m_aCount;
  private final NumberPattern m_aFrom;
  private final ELevel m_eLevel;
  private final Avt m_aFormat;
  private final Avt m_aGroupSep;
  private final Avt m_aGroupSize;
  private final Avt m_aOrdinal;
  private final Avt m_aStartAt;
  private final Avt m_aLang;

  private NumberInstruction (@NonNull final XslElement aEl,
                             @Nullable final XPathExpr aValue,
                             @Nullable final XPathExpr aSelect,
                             @Nullable final NumberPattern aCount,
                             @Nullable final NumberPattern aFrom,
                             @NonNull final ELevel eLevel,
                             @NonNull final Avt aFormat,
                             @Nullable final Avt aGroupSep,
                             @Nullable final Avt aGroupSize,
                             @Nullable final Avt aOrdinal,
                             @NonNull final Avt aStartAt,
                             @Nullable final Avt aLang)
  {
    super (aEl);
    m_aValue = aValue;
    m_aSelect = aSelect;
    m_aCount = aCount;
    m_aFrom = aFrom;
    m_eLevel = eLevel;
    m_aFormat = aFormat;
    m_aGroupSep = aGroupSep;
    m_aGroupSize = aGroupSize;
    m_aOrdinal = aOrdinal;
    m_aStartAt = aStartAt;
    m_aLang = aLang;
  }

  /**
   * The default count pattern: same node kind and, for named nodes, the same
   * expanded name.
   */
  private static boolean _isSameKindAndName (@NonNull final IXdmNode aNode, @NonNull final IXdmNode aTarget)
  {
    if (aNode.getKind () != aTarget.getKind ())
      return false;
    switch (aNode.getKind ())
    {
      case ELEMENT:
      case ATTRIBUTE:
      case PROCESSING_INSTRUCTION:
      case NAMESPACE:
        return aNode.getName ().equals (aTarget.getName ());
      default:
        return true;
    }
  }

  private static boolean _hasNoSiblings (@NonNull final IXdmNode aNode)
  {
    return aNode.getParent () == null ||
           aNode.getKind () == ENodeKind.ATTRIBUTE ||
           aNode.getKind () == ENodeKind.NAMESPACE;
  }

  private static int _indexInParent (@NonNull final IXdmNode aParent, @NonNull final IXdmNode aChild)
  {
    final int nCount = aParent.getChildCount ();
    for (int i = 0; i < nCount; ++i)
      if (aParent.getChildAtIndex (i) == aChild)
        return i;
    return -1;
  }

  /** The subtree of a node (without attributes) in document order. */
  @NonNull
  private static ICommonsList <IXdmNode> _subtreeInDocumentOrder (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    final ICommonsList <IXdmNode> aStack = new CommonsArrayList <> ();
    aStack.add (aRoot);
    while (aStack.isNotEmpty ())
    {
      final IXdmNode aNode = aStack.removeLast ();
      ret.add (aNode);
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.add (aNode.getChildAtIndex (i));
    }
    return ret;
  }

  private boolean _matchesCount (@NonNull final XsltContext aCtx,
                                 @NonNull final IXdmNode aNode,
                                 @NonNull final IXdmNode aTarget)
  {
    if (m_aCount == null)
      return _isSameKindAndName (aNode, aTarget);
    return m_aCount.matches (aNode, aCtx);
  }

  private boolean _matchesFrom (@NonNull final XsltContext aCtx, @NonNull final IXdmNode aNode)
  {
    return m_aFrom != null && m_aFrom.matches (aNode, aCtx);
  }

  /** 1 + the number of preceding siblings matching count. */
  private long _siblingIndex (@NonNull final XsltContext aCtx, @NonNull final IXdmNode aNode, @NonNull final IXdmNode aTarget)
  {
    if (_hasNoSiblings (aNode))
      return 1;
    final IXdmNode aParent = aNode.getParent ();
    long ret = 1;
    final int nCount = aParent.getChildCount ();
    for (int i = 0; i < nCount; ++i)
    {
      final IXdmNode aSibling = aParent.getChildAtIndex (i);
      if (aSibling == aNode)
        break;
      if (_matchesCount (aCtx, aSibling, aTarget))
        ret++;
    }
    return ret;
  }

  private long _countSingle (@NonNull final XsltContext aCtx, @NonNull final IXdmNode aTarget)
  {
    // count is tested before from: a node matching both is still numbered
    IXdmNode aCur = aTarget;
    while (aCur != null)
    {
      if (_matchesCount (aCtx, aCur, aTarget))
        return _siblingIndex (aCtx, aCur, aTarget);
      if (_matchesFrom (aCtx, aCur))
        return 0;
      aCur = aCur.getParent ();
    }
    return 0;
  }

  @NonNull
  private ICommonsList <BigInteger> _countMultiple (@NonNull final XsltContext aCtx, @NonNull final IXdmNode aTarget)
  {
    final ICommonsList <IXdmNode> aChain = new CommonsArrayList <> ();
    for (IXdmNode aCur = aTarget; aCur != null; aCur = aCur.getParent ())
    {
      if (_matchesCount (aCtx, aCur, aTarget))
        aChain.add (aCur);
      if (_matchesFrom (aCtx, aCur))
        break;
    }
    final ICommonsList <BigInteger> ret = new CommonsArrayList <> ();
    for (int i = aChain.size () - 1; i >= 0; --i)
      ret.add (BigInteger.valueOf (_siblingIndex (aCtx, aChain.get (i), aTarget)));
    return ret;
  }

  /**
   * level="any": the nodes on the preceding and ancestor-or-self axes that
   * match count, not before the last of them matching from. Walks in reverse
   * document order and stops at the from node.
   */
  private long _countAny (@NonNull final XsltContext aCtx, @NonNull final IXdmNode aTarget)
  {
    long nCount = 0;
    if (_matchesCount (aCtx, aTarget, aTarget))
      nCount++;
    if (_matchesFrom (aCtx, aTarget))
      return nCount;
    IXdmNode aCur = aTarget;
    if (aTarget.getKind () == ENodeKind.ATTRIBUTE || aTarget.getKind () == ENodeKind.NAMESPACE)
    {
      // Other attributes of the parent are on neither axis
      aCur = aTarget.getParent ();
      if (aCur == null)
        return nCount;
      if (_matchesCount (aCtx, aCur, aTarget))
        nCount++;
      if (_matchesFrom (aCtx, aCur))
        return nCount;
    }
    while (true)
    {
      final IXdmNode aParent = aCur.getParent ();
      if (aParent == null)
        break;
      final int nIndex = _indexInParent (aParent, aCur);
      for (int i = nIndex - 1; i >= 0; --i)
      {
        final ICommonsList <IXdmNode> aSubtree = _subtreeInDocumentOrder (aParent.getChildAtIndex (i));
        for (int j = aSubtree.size () - 1; j >= 0; --j)
        {
          final IXdmNode aNode = aSubtree.get (j);
          aCtx.getDynamicContext ().checkInterrupt ();
          if (_matchesCount (aCtx, aNode, aTarget))
            nCount++;
          if (_matchesFrom (aCtx, aNode))
            return nCount;
        }
      }
      if (_matchesCount (aCtx, aParent, aTarget))
        nCount++;
      if (_matchesFrom (aCtx, aParent))
        return nCount;
      aCur = aParent;
    }
    return nCount;
  }

  /** Round a non-negative number half up to an integer. */
  @NonNull
  private static BigInteger _round (@NonNull final BigDecimal aValue)
  {
    return aValue.setScale (0, RoundingMode.HALF_UP).toBigInteger ();
  }

  /** The integers of the value attribute. */
  @NonNull
  private ICommonsList <BigInteger> _computeValue (@NonNull final XsltContext aCtx)
  {
    final XdmSequence aAtomized = Atomizer.atomize (m_aValue.evaluate (aCtx));
    final ICommonsList <BigInteger> ret = new CommonsArrayList <> ();
    for (final IXdmItem aItem : aAtomized)
    {
      final XdmAtomic aAtomic = (XdmAtomic) aItem;
      final EAtomicType eType = aAtomic.getType ();
      if (eType.isIntegerFamily ())
      {
        final BigInteger aInt = aAtomic.getBigInteger ();
        if (aInt.signum () < 0)
          throw new XPathException ("XTDE0980", "The value " + aInt + " of xsl:number is negative");
        ret.add (aInt);
        continue;
      }
      if (eType.isDerivedFrom (EAtomicType.DECIMAL))
      {
        final BigDecimal aDec = aAtomic.getBigDecimal ();
        if (aDec.signum () < 0)
          throw new XPathException ("XTDE0980", "The value " + aDec.toPlainString () + " of xsl:number is negative");
        ret.add (_round (aDec));
        continue;
      }
      final double d = AtomicComparator.toNumber (aAtomic);
      if (Double.isNaN (d) || Double.isInfinite (d))
        throw new XPathException ("XTDE0980",
                                  "The value '" + aAtomic.getLexical () + "' of xsl:number is not convertible to an integer");
      if (d < 0)
        throw new XPathException ("XTDE0980", "The value " + aAtomic.getLexical () + " of xsl:number is negative");
      ret.add (_round (new BigDecimal (d)));
    }
    return ret;
  }

  /** The numbers to format: from the value attribute or by counting nodes. */
  @NonNull
  private ICommonsList <BigInteger> _compute (@NonNull final XsltContext aCtx)
  {
    if (m_aValue != null)
      return _computeValue (aCtx);

    final IXdmNode aTarget;
    if (m_aSelect != null)
    {
      final XdmSequence aSel = m_aSelect.evaluate (aCtx);
      if (aSel.size () != 1)
        throw new XPathException ("XTTE1000", "The select attribute of xsl:number must select exactly one node, not " + aSel.size () + " items");
      if (!(aSel.get (0) instanceof final IXdmNode aNode))
        throw new XPathException ("XTTE1000", "The select attribute of xsl:number did not select a node");
      aTarget = aNode;
    }
    else
    {
      if (!(aCtx.getContextItem () instanceof final IXdmNode aNode))
        throw new XPathException ("XTTE0990", "The context item of xsl:number is absent or not a node");
      aTarget = aNode;
    }

    final ICommonsList <BigInteger> ret = new CommonsArrayList <> ();
    switch (m_eLevel)
    {
      case MULTIPLE:
        return _countMultiple (aCtx, aTarget);
      case ANY:
      {
        final long n = _countAny (aCtx, aTarget);
        if (n > 0)
          ret.add (BigInteger.valueOf (n));
        return ret;
      }
      default:
      {
        final long n = _countSingle (aCtx, aTarget);
        if (n > 0)
          ret.add (BigInteger.valueOf (n));
        return ret;
      }
    }
  }

  /**
   * A coarse BCP 47 shape check: subtags of 1 to 8 ASCII letters or digits,
   * the first starting with a letter.
   */
  private static boolean _isLanguageTag (@NonNull final String s)
  {
    final String [] aParts = s.split ("-", -1);
    for (int i = 0; i < aParts.length; ++i)
    {
      final String sPart = aParts[i];
      if (sPart.isEmpty () || sPart.length () > 8)
        return false;
      for (int j = 0; j < sPart.length (); ++j)
      {
        final char c = sPart.charAt (j);
        final boolean bLetter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
        final boolean bDigit = c >= '0' && c <= '9';
        if (!bLetter && !(bDigit && (i > 0 || j > 0)))
          return false;
      }
    }
    return true;
  }

  @NonNull
  private static ICommonsList <BigInteger> _parseStartAt (@NonNull final String s)
  {
    final ICommonsList <BigInteger> ret = new CommonsArrayList <> ();
    final String sTrimmed = s.strip ();
    if (!sTrimmed.isEmpty ())
      for (final String sPart : sTrimmed.split ("\\s+"))
      {
        try
        {
          ret.add (new BigInteger (sPart.startsWith ("+") ? sPart.substring (1) : sPart));
        }
        catch (final NumberFormatException ex)
        {
          throw new XPathException ("XTDE0030", "The start-at value '" + s + "' of xsl:number is not a list of integers");
        }
      }
    if (ret.isEmpty ())
      ret.add (BigInteger.ONE);
    return ret;
  }

  public void execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
  {
    final ICommonsList <BigInteger> aNums = _compute (aCtx);
    if (aNums.isNotEmpty ())
    {
      // Each number is shifted by its start-at value minus one; the last
      // start-at value is repeated for surplus numbers
      final ICommonsList <BigInteger> aStarts = _parseStartAt (m_aStartAt.evaluate (aCtx));
      for (int i = 0; i < aNums.size (); ++i)
      {
        final BigInteger aDelta = aStarts.get (Math.min (i, aStarts.size () - 1)).subtract (BigInteger.ONE);
        if (aDelta.signum () != 0)
          aNums.set (i, aNums.get (i).add (aDelta));
      }
    }
    final String sFormat = m_aFormat.evaluate (aCtx);
    final String sGroupSep = m_aGroupSep != null ? m_aGroupSep.evaluate (aCtx) : "";
    int nGroupSize = 0;
    if (m_aGroupSize != null)
    {
      try
      {
        nGroupSize = Integer.parseInt (m_aGroupSize.evaluate (aCtx).strip ());
      }
      catch (final NumberFormatException ex)
      {
        nGroupSize = 0;
      }
    }
    String sLang = null;
    if (m_aLang != null)
    {
      sLang = m_aLang.evaluate (aCtx);
      if (!sLang.isEmpty () && !_isLanguageTag (sLang))
        throw new XPathException ("XTDE0030", "The lang value '" + sLang + "' of xsl:number is not a valid language code");
    }
    final String sOrdinal = m_aOrdinal != null ? m_aOrdinal.evaluate (aCtx) : "";
    final String sText = NumberFormatter.format (aNums, sFormat, sGroupSep, nGroupSize, sLang, sOrdinal);
    if (!sText.isEmpty ())
      aOut.appendNew (IXdmNode.createText (sText));
  }

  @Nullable
  private static NumberPattern _compilePattern (@NonNull final CompileContext aCtx,
                                                @NonNull final XslElement aEl,
                                                @NonNull final String sAttr)
  {
    final String sPattern = aEl.getAttr (sAttr);
    if (sPattern == null)
      return null;
    final StaticContext aSC = aCtx.createStaticContext (aEl);
    final XsltPattern aPattern;
    try
    {
      aPattern = XsltPatternParser.parse (sPattern, aSC);
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aEl);
    }
    if (aPattern instanceof final CompiledXsltPattern aCP)
      for (final String [] aEntry : FORBIDDEN_PATTERN_FUNCTIONS)
      {
        final Predicate <XdmQName> aFilter = n -> XdmConstants.NS_FN.equals (n.namespaceUri ()) &&
                                                   aEntry[0].equals (n.localName ());
        if (aCP.containsFunction (aFilter))
          throw XsltException.at (aEl, aEntry[1], aEntry[0] + "() may not be used in a pattern");
      }
    return new NumberPattern (aPattern, aSC);
  }

  /**
   * Compile an xsl:number element.
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
    final XPathExpr aValue = aCtx.compileOptionalExpression (aEl, "value");
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final NumberPattern aCount = _compilePattern (aCtx, aEl, "count");
    final NumberPattern aFrom = _compilePattern (aCtx, aEl, "from");
    ELevel eLevel = ELevel.SINGLE;
    final String sLevel = aEl.getAttr ("level");
    if (sLevel != null)
      switch (sLevel.strip ())
      {
        case "single" -> eLevel = ELevel.SINGLE;
        case "multiple" -> eLevel = ELevel.MULTIPLE;
        case "any" -> eLevel = ELevel.ANY;
        default -> throw XsltException.at (aEl,
                                           "XTSE0020",
                                           "The level attribute of xsl:number must be single, multiple or any, not '" +
                                                       sLevel +
                                                       "'");
      }
    final String sFormat = aEl.getAttr ("format");
    final Avt aFormat = aCtx.compileAvt (aEl, sFormat == null ? "1" : sFormat);
    final Avt aGroupSep = aCtx.compileOptionalAvt (aEl, "grouping-separator");
    final Avt aGroupSize = aCtx.compileOptionalAvt (aEl, "grouping-size");
    final Avt aOrdinal = aCtx.compileOptionalAvt (aEl, "ordinal");
    final String sStartAt = aEl.getAttr ("start-at");
    final Avt aStartAt = aCtx.compileAvt (aEl, sStartAt == null ? "1" : sStartAt);
    final Avt aLang = aCtx.compileOptionalAvt (aEl, "lang");
    // letter-value is accepted; traditional numbering falls back to alphabetic
    aCtx.compileOptionalAvt (aEl, "letter-value");
    return new NumberInstruction (aEl,
                                  aValue,
                                  aSelect,
                                  aCount,
                                  aFrom,
                                  eLevel,
                                  aFormat,
                                  aGroupSep,
                                  aGroupSize,
                                  aOrdinal,
                                  aStartAt,
                                  aLang);
  }
}
