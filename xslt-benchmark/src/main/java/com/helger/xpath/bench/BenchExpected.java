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
package com.helger.xpath.bench;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.bench.BenchData.Line;

/**
 * The expected results of the XPath and XSLT workloads, computed in Java from the generated lines
 * and independently of the engine.
 *
 * @author Philip Helger
 */
final class BenchExpected
{
  private static final Pattern SKU_PATTERN = Pattern.compile ("^[A-Z]{3}-[0-9]{4}$");
  private static final Pattern ITEM_PATTERN = Pattern.compile ("([a-z]+) ([0-9]+)");
  private static final BigDecimal HUNDRED = BigDecimal.valueOf (100);
  private static final BigDecimal FIFTY = BigDecimal.valueOf (50);

  private BenchExpected ()
  {}

  /**
   * @param aValue
   *        A decimal.
   * @return The value as XPath casts an <code>xs:decimal</code> to a string: no exponent, no
   *         trailing zeros, no decimal point for integers.
   */
  @NonNull
  static String decimal (@NonNull final BigDecimal aValue)
  {
    if (aValue.signum () == 0)
      return "0";
    return aValue.stripTrailingZeros ().toPlainString ();
  }

  /**
   * @param sText
   *        A text with spaces only as whitespace.
   * @return <code>fn:normalize-space</code> of it.
   */
  @NonNull
  static String normalizeSpace (@NonNull final String sText)
  {
    return sText.trim ().replaceAll (" +", " ");
  }

  @NonNull
  static BigDecimal amount (@NonNull final Line aLine)
  {
    return aLine.price ().multiply (BigDecimal.valueOf (aLine.qty ()));
  }

  static boolean isSkuValid (@NonNull final Line aLine)
  {
    return SKU_PATTERN.matcher (aLine.sku ()).matches ();
  }

  @NonNull
  private static ICommonsMap <String, Integer> _countBySku (@NonNull final ICommonsList <Line> aLines)
  {
    final ICommonsMap <String, Integer> ret = new CommonsHashMap <> ();
    for (final Line aLine : aLines)
      ret.merge (aLine.sku (), Integer.valueOf (1), (aOld, aAdd) -> Integer.valueOf (aOld.intValue () + aAdd.intValue ()));
    return ret;
  }

  @NonNull
  static String xpathPositional (@NonNull final BenchData aData)
  {
    final int nCount = aData.getLineCount ();
    long nSum = 0;
    for (int i = 1; i <= 200; ++i)
      nSum += Math.max (1, i * nCount / 200);
    return nSum + "/" + nCount / 10 + "/" + nCount;
  }

  @NonNull
  static String xpathPredicate (@NonNull final BenchData aData)
  {
    int nCount = 0;
    for (final Line aLine : aData.getAllLines ())
      if ("A".equals (aLine.cat ()) && aLine.price ().compareTo (FIFTY) > 0)
        nCount++;
    return Integer.toString (nCount);
  }

  @NonNull
  static String xpathExists (@NonNull final BenchData aData)
  {
    boolean bRate0 = false;
    boolean bSku = false;
    boolean bCatC = false;
    for (final Line aLine : aData.getAllLines ())
    {
      bRate0 |= "0".equals (aLine.taxRate ());
      bSku |= "ZZZ-0000".equals (aLine.sku ());
      bCatC |= "C".equals (aLine.cat ());
    }
    return bRate0 + ",true,true," + bSku + "," + bCatC;
  }

  @NonNull
  static String xpathStrings (@NonNull final BenchData aData)
  {
    long nSum = 0;
    for (final Line aLine : aData.getAllLines ())
      nSum += aLine.item ().length () + 1 + 3 + 1 + normalizeSpace (aLine.note ()).length ();
    return Long.toString (nSum);
  }

  @NonNull
  static String xpathRegex (@NonNull final BenchData aData)
  {
    int nValid = 0;
    long nLength = 0;
    for (final Line aLine : aData.getAllLines ())
    {
      if (isSkuValid (aLine))
        nValid++;
      nLength += ITEM_PATTERN.matcher (aLine.item ()).replaceAll ("$2-$1-$2").length ();
    }
    return nValid + "/" + nLength;
  }

  @NonNull
  static String xpathArithmetic (@NonNull final BenchData aData)
  {
    BigDecimal aSum = BigDecimal.ZERO;
    int nAtLeast100 = 0;
    long nQty = 0;
    int nCheap = 0;
    for (final Line aLine : aData.getAllLines ())
    {
      final BigDecimal aAmount = amount (aLine);
      aSum = aSum.add (aAmount);
      if (aAmount.compareTo (HUNDRED) >= 0)
        nAtLeast100++;
      nQty += aLine.qty ();
      if (aLine.price ().doubleValue () < 20.5)
        nCheap++;
    }
    return decimal (aSum) + "/" + nAtLeast100 + "/" + nQty / 7 + "/" + nCheap;
  }

  @NonNull
  static String xpathFlwor (@NonNull final BenchData aData)
  {
    final Map <String, long []> aByCat = new TreeMap <> ();
    long nDays = 0;
    boolean bExpensive = false;
    for (final Line aLine : aData.getAllLines ())
    {
      final long [] aEntry = aByCat.computeIfAbsent (aLine.cat (), sKey -> new long [2]);
      aEntry[0]++;
      aEntry[1] += aLine.qty ();
      nDays += ChronoUnit.DAYS.between (aLine.start (), aLine.end ());
      bExpensive |= aLine.price ().compareTo (BigDecimal.valueOf (199)) > 0;
    }
    final StringBuilder aSB = new StringBuilder ();
    for (final Map.Entry <String, long []> aEntry : aByCat.entrySet ())
    {
      if (aSB.length () > 0)
        aSB.append (',');
      aSB.append (aEntry.getKey ()).append ('=').append (aEntry.getValue ()[0]).append (':').append (aEntry.getValue ()[1]);
    }
    return aSB.append ('/').append (nDays).append ("/true/").append (bExpensive).toString ();
  }

  @NonNull
  static String xpathMaps (@NonNull final BenchData aData)
  {
    final ICommonsList <Line> aLines = aData.getAllLines ();
    final ICommonsSet <String> aSkus = new CommonsHashSet <> ();
    long nQty = 0;
    BigDecimal aPrices = BigDecimal.ZERO;
    final int [] aCats = new int [3];
    for (final Line aLine : aLines)
    {
      aSkus.add (aLine.sku ());
      nQty += aLine.qty ();
      aPrices = aPrices.add (aLine.price ());
      aCats[aLine.cat ().charAt (0) - 'A']++;
    }
    return aSkus.size () +
           "/" +
           nQty +
           "/" +
           aLines.size () +
           "/" +
           decimal (aPrices) +
           "/" +
           aCats[0] +
           "," +
           aCats[1] +
           "," +
           aCats[2];
  }

  /**
   * @param aData
   *        The data.
   * @return The failures per rule of the rule set of
   *         {@link XPathRulesWorkload}, in the order of the rules, separated by commas.
   */
  @NonNull
  static String xpathRules (@NonNull final BenchData aData)
  {
    final int [] aFails = new int [XPathRulesWorkload.RULES.length];
    for (final Line aLine : aData.getAllLines ())
    {
      // R2: SKU pattern
      if (!isSkuValid (aLine))
        aFails[1]++;
      // R3: quantity at least 1
      if (aLine.qty () < 1)
        aFails[2]++;
      // R4: amount below 2000
      if (amount (aLine).compareTo (BigDecimal.valueOf (2000)) >= 0)
        aFails[3]++;
      // R6: category C only with the full rate
      if ("C".equals (aLine.cat ()) && !"19".equals (aLine.taxRate ()))
        aFails[5]++;
      // R7: period not reversed
      if (aLine.start ().isAfter (aLine.end ()))
        aFails[6]++;
      // R8: rate 0 needs a reason
      if ("0".equals (aLine.taxRate ()) && aLine.reason () == null)
        aFails[7]++;
      // R10: note length
      if (aLine.note ().length () > 40)
        aFails[9]++;
    }
    final StringBuilder aSB = new StringBuilder ();
    for (final int nFails : aFails)
    {
      if (aSB.length () > 0)
        aSB.append (',');
      aSB.append (nFails);
    }
    return aSB.toString ();
  }

  @NonNull
  static String xsltDispatch (@NonNull final BenchData aData)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final Line aLine : aData.getAllLines ())
    {
      final boolean bA = "A".equals (aLine.cat ());
      final char cClass;
      if (bA && aLine.price ().compareTo (HUNDRED) > 0)
        cClass = 'X';
      else
        if (bA)
          cClass = 'A';
        else
          if ("B".equals (aLine.cat ()))
            cClass = 'B';
          else
            cClass = "0".equals (aLine.taxRate ()) ? 'Z' : 'O';
      aSB.append (cClass)
         .append ('i')
         .append (aLine.sku ().startsWith ("A") ? 'a' : 's')
         .append (aLine.qty () == 1 ? '1' : 'n')
         .append ('n')
         .append ("19".equals (aLine.taxRate ()) ? 'T' : 't')
         .append ("o<>");
    }
    return aSB.toString ();
  }

  @NonNull
  static String xsltKeys (@NonNull final BenchData aData)
  {
    final ICommonsList <Line> aLines = aData.getAllLines ();
    final int [] aCats = new int [3];
    int nRate0 = 0;
    for (final Line aLine : aLines)
    {
      aCats[aLine.cat ().charAt (0) - 'A']++;
      if ("0".equals (aLine.taxRate ()))
        nRate0++;
    }
    final ICommonsMap <String, Integer> aBySku = _countBySku (aLines);
    long nSkuSum = 0;
    for (final Line aLine : aLines)
      nSkuSum += aBySku.get (aLine.sku ()).intValue ();
    return "A=" + aCats[0] + ",B=" + aCats[1] + ",C=" + aCats[2] + ";" + nSkuSum + ";" + nRate0;
  }

  @NonNull
  static String xsltGrouping (@NonNull final BenchData aData)
  {
    final Map <String, long []> aByCat = new TreeMap <> ();
    final ICommonsSet <Character> aFirstLetters = new CommonsHashSet <> ();
    int nAdjacent = 0;
    int nMaxAdjacent = 0;
    int nRun = 0;
    String sLastRate = null;
    for (final Line aLine : aData.getAllLines ())
    {
      final long [] aEntry = aByCat.computeIfAbsent (aLine.cat (), sKey -> new long [2]);
      aEntry[0]++;
      aEntry[1] += aLine.qty ();
      aFirstLetters.add (Character.valueOf (aLine.sku ().charAt (0)));
      if (aLine.taxRate ().equals (sLastRate))
        nRun++;
      else
      {
        nAdjacent++;
        nRun = 1;
        sLastRate = aLine.taxRate ();
      }
      nMaxAdjacent = Math.max (nMaxAdjacent, nRun);
    }
    final StringBuilder aSB = new StringBuilder ();
    for (final Map.Entry <String, long []> aEntry : aByCat.entrySet ())
      aSB.append (aEntry.getKey ()).append ('=').append (aEntry.getValue ()[0]).append (':').append (aEntry.getValue ()[1]).append (',');
    return aSB.append (';')
              .append (nAdjacent)
              .append (':')
              .append (nMaxAdjacent)
              .append (';')
              .append (aFirstLetters.size ())
              .toString ();
  }

  @NonNull
  static String xsltSort (@NonNull final BenchData aData)
  {
    // List.sort is stable, as xsl:sort is
    final ICommonsList <Line> aLines = aData.getAllLines ();
    aLines.sort (Comparator.comparing (Line::price).reversed ().thenComparing (Line::sku));
    final StringBuilder aSB = new StringBuilder ();
    for (final Line aLine : aLines)
      aSB.append (aLine.id ()).append (',');
    return aSB.toString ();
  }

  @NonNull
  static String xsltNumber (@NonNull final BenchData aData)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 1; i <= aData.getLineCount (); ++i)
      aSB.append (i).append ('|').append (i).append (".1.1|").append (2 * i).append (';');
    return aSB.toString ();
  }

  @NonNull
  static String xsltIterate (@NonNull final BenchData aData)
  {
    final ICommonsList <Line> aLines = aData.getAllLines ();
    BigDecimal aTotal = BigDecimal.ZERO;
    for (final Line aLine : aLines)
      aTotal = aTotal.add (amount (aLine));
    return aLines.size () + "/" + decimal (aTotal) + "/" + decimal (aLines.getLastOrNull ().price ());
  }

  @NonNull
  static String xsltRecursion (@NonNull final BenchData aData)
  {
    BigDecimal aSum = BigDecimal.ZERO;
    final StringBuilder aReversed = new StringBuilder ();
    for (final Line aLine : aData.getAllLines ())
    {
      aSum = aSum.add (aLine.price ());
      aReversed.append (new StringBuilder (aLine.item ()).reverse ()).append (',');
    }
    return decimal (aSum) + ";" + aReversed;
  }

  /**
   * @param aData
   *        The data.
   * @return The rows the XML serialisation workload writes, without the document element.
   */
  @NonNull
  static String xsltSerializeXmlRows (@NonNull final BenchData aData)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final Line aLine : aData.getAllLines ())
      aSB.append ("<row id=\"")
         .append (aLine.id ())
         .append ("\" cat=\"")
         .append (aLine.cat ())
         .append ("\" amount=\"")
         .append (decimal (amount (aLine)))
         .append ("\">")
         .append (aLine.item ())
         .append (" (")
         .append (aLine.sku ())
         .append (")<n>")
         .append (normalizeSpace (aLine.note ()))
         .append ("</n></row>");
    return aSB.toString ();
  }

  /**
   * @param aData
   *        The data.
   * @return The table rows the HTML serialisation workload writes.
   */
  @NonNull
  static String xsltSerializeHtmlRows (@NonNull final BenchData aData)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final Line aLine : aData.getAllLines ())
      aSB.append ("<tr class=\"cat-")
         .append (aLine.cat ())
         .append ("\"><td>")
         .append (aLine.id ())
         .append ("</td><td>")
         .append (aLine.item ())
         .append ("</td><td>")
         .append (aLine.sku ())
         .append ("</td><td>")
         .append (decimal (amount (aLine)))
         .append ("</td></tr>");
    return aSB.toString ();
  }

  /**
   * @param aData
   *        The data.
   * @return The length of the JSON the JSON serialisation workload writes. The order of map
   *         entries is implementation-dependent, so the length is compared instead of the text.
   */
  static int xsltSerializeJsonLength (@NonNull final BenchData aData)
  {
    // [{"id":1,"sku":"ABC-0017","amount":37.5,"tags":["A","19"]},...]
    final ICommonsList <Line> aLines = aData.getAllLines ();
    int ret = 2 + Math.max (0, aLines.size () - 1);
    for (final Line aLine : aLines)
      ret += "{\"id\":,\"sku\":\"\",\"amount\":,\"tags\":[\"\",\"\"]}".length () +
             Integer.toString (aLine.id ()).length () +
             aLine.sku ().length () +
             decimal (amount (aLine)).length () +
             aLine.cat ().length () +
             aLine.taxRate ().length ();
    return ret;
  }
}
