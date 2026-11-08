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
package com.helger.phoxtrot.xpath.bench;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Random;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;

/**
 * The generated input of all workloads: an invoice-like document with a given number of lines,
 * created from a fixed seed, so that every run and every commit sees the same document. Each line
 * has ten elements (<code>line</code>, <code>item</code>, <code>sku</code>, <code>qty</code>,
 * <code>price</code>, <code>tax</code>, <code>note</code>, <code>period</code>,
 * <code>start</code>, <code>end</code>), so 1,000 lines are about 10,000 elements. The document
 * is indented, so the whitespace-only text nodes of a real document are part of the tree.
 * <p>
 * With faults, some lines break the business rules the workloads check: every 53rd SKU is lower
 * case, every 41st quantity is 0, every 67th period ends before it starts, and every third line
 * with the tax rate 0 has no <code>reason</code>. Without faults the document is valid against
 * the benchmark schemas.
 * <p>
 * The lines are kept as records, so that the workloads can compute their expected results in Java
 * independently of the engine.
 *
 * @author Philip Helger
 */
public final class BenchData
{
  /** The seed of all generated documents. */
  public static final long SEED = 20261106L;

  private static final String [] ITEM_WORDS = { "widget",
                                                "gadget",
                                                "bolt",
                                                "nut",
                                                "screw",
                                                "gear",
                                                "valve",
                                                "pipe",
                                                "cable",
                                                "panel",
                                                "switch",
                                                "sensor" };
  private static final String [] NOTE_WORDS = { "fast",
                                                "delivery",
                                                "fragile",
                                                "urgent",
                                                "handle",
                                                "with",
                                                "care",
                                                "back",
                                                "order",
                                                "partial" };
  private static final int SKU_POOL = 400;
  private static final LocalDate FIRST_START = LocalDate.of (2026, 1, 1);

  /**
   * One invoice line as it is written to the document.
   *
   * @param id
   *        The 1-based line number, also the <code>id</code> attribute.
   * @param cat
   *        The category <code>A</code>, <code>B</code> or <code>C</code>.
   * @param item
   *        The item text, e.g. <code>widget 17</code>.
   * @param sku
   *        The SKU, e.g. <code>ABC-0017</code>.
   * @param qty
   *        The quantity.
   * @param price
   *        The unit price with two decimals.
   * @param taxRate
   *        The tax rate <code>19</code>, <code>7</code> or <code>0</code>.
   * @param reason
   *        The <code>reason</code> attribute of the tax, <code>null</code> if there is none.
   * @param note
   *        The note text, with irregular spaces.
   * @param start
   *        The start of the period.
   * @param end
   *        The end of the period.
   */
  public record Line (int id,
                      @NonNull String cat,
                      @NonNull String item,
                      @NonNull String sku,
                      int qty,
                      @NonNull BigDecimal price,
                      @NonNull String taxRate,
                      @Nullable String reason,
                      @NonNull String note,
                      @NonNull LocalDate start,
                      @NonNull LocalDate end)
  {}

  private final ICommonsList <Line> m_aLines;
  private final String m_sXml;
  private final int m_nElements;
  private final int m_nAttributes;
  private final int m_nTexts;

  private BenchData (@NonNull final ICommonsList <Line> aLines,
                     @NonNull final String sXml,
                     final int nElements,
                     final int nAttributes,
                     final int nTexts)
  {
    m_aLines = aLines;
    m_sXml = sXml;
    m_nElements = nElements;
    m_nAttributes = nAttributes;
    m_nTexts = nTexts;
  }

  @NonNull
  private static String _createNote (@NonNull final Random aRandom)
  {
    final StringBuilder aSB = new StringBuilder ();
    if (aRandom.nextInt (4) == 0)
      aSB.append (' ');
    final int nWords = 1 + aRandom.nextInt (6);
    for (int i = 0; i < nWords; ++i)
    {
      if (i > 0)
        aSB.append (aRandom.nextInt (3) == 0 ? "  " : " ");
      aSB.append (NOTE_WORDS[aRandom.nextInt (NOTE_WORDS.length)]);
    }
    if (aRandom.nextInt (4) == 0)
      aSB.append ("  ");
    return aSB.toString ();
  }

  @NonNull
  private static String [] _createSkuPool (@NonNull final Random aRandom)
  {
    final String [] ret = new String [SKU_POOL];
    for (int i = 0; i < SKU_POOL; ++i)
    {
      final StringBuilder aSB = new StringBuilder ();
      for (int j = 0; j < 3; ++j)
        aSB.append ((char) ('A' + aRandom.nextInt (26)));
      aSB.append ('-');
      final String sDigits = Integer.toString (aRandom.nextInt (10_000));
      for (int j = sDigits.length (); j < 4; ++j)
        aSB.append ('0');
      aSB.append (sDigits);
      ret[i] = aSB.toString ();
    }
    return ret;
  }

  @NonNull
  private static Line _createLine (@NonNull final Random aRandom,
                                   @NonNull final String [] aSkuPool,
                                   final int nID,
                                   final boolean bFaults)
  {
    final int nCat = aRandom.nextInt (10);
    final String sCat = nCat < 5 ? "A" : nCat < 8 ? "B" : "C";
    final String sItem = ITEM_WORDS[aRandom.nextInt (ITEM_WORDS.length)] + " " + (1 + aRandom.nextInt (99));
    String sSku = aSkuPool[aRandom.nextInt (aSkuPool.length)];
    int nQty = 1 + aRandom.nextInt (20);
    final BigDecimal aPrice = BigDecimal.valueOf (100 + aRandom.nextInt (19_900), 2);
    final int nRate = aRandom.nextInt (10);
    final String sRate = nRate < 6 ? "19" : nRate < 9 ? "7" : "0";
    String sReason = "0".equals (sRate) ? "export" : null;
    final String sNote = _createNote (aRandom);
    LocalDate aStart = FIRST_START.plusDays (aRandom.nextInt (300));
    LocalDate aEnd = aStart.plusDays (1 + aRandom.nextInt (60));
    if (bFaults)
    {
      if (nID % 53 == 0)
        sSku = sSku.toLowerCase (Locale.ROOT);
      if (nID % 41 == 0)
        nQty = 0;
      if (nID % 67 == 0)
      {
        final LocalDate aTmp = aStart;
        aStart = aEnd;
        aEnd = aTmp;
      }
      if (sReason != null && nID % 3 == 0)
        sReason = null;
    }
    return new Line (nID, sCat, sItem, sSku, nQty, aPrice, sRate, sReason, sNote, aStart, aEnd);
  }

  private static void _appendElement (@NonNull final StringBuilder aSB,
                                      @NonNull final String sIndent,
                                      @NonNull final String sName,
                                      @NonNull final String sText)
  {
    aSB.append (sIndent).append ('<').append (sName).append ('>').append (sText).append ("</").append (sName).append ('>');
  }

  /**
   * Generate a document.
   *
   * @param nLines
   *        The number of invoice lines. Must be at least 1.
   * @param bFaults
   *        <code>true</code> to break some business rules (see the class description),
   *        <code>false</code> for a document valid against the benchmark schemas.
   * @return The generated document. Never <code>null</code>.
   */
  @NonNull
  public static BenchData create (@Nonnegative final int nLines, final boolean bFaults)
  {
    if (nLines < 1)
      throw new IllegalArgumentException ("At least one line is needed: " + nLines);
    final Random aRandom = new Random (SEED);
    final String [] aSkuPool = _createSkuPool (aRandom);
    final ICommonsList <Line> aLines = new CommonsArrayList <> (nLines);
    for (int i = 1; i <= nLines; ++i)
      aLines.add (_createLine (aRandom, aSkuPool, i, bFaults));

    final StringBuilder aSB = new StringBuilder (nLines * 340 + 300);
    aSB.append ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    aSB.append ("<invoice id=\"INV-2026-1\" currency=\"EUR\" date=\"2026-11-06\">");
    aSB.append ("\n  <supplier>");
    _appendElement (aSB, "\n    ", "name", "Bench Supplies Ltd");
    _appendElement (aSB, "\n    ", "country", "AT");
    aSB.append ("\n  </supplier>");
    aSB.append ("\n  <lines>");
    int nAttributes = 3;
    for (final Line aLine : aLines)
    {
      aSB.append ("\n    <line id=\"").append (aLine.id ()).append ("\" cat=\"").append (aLine.cat ()).append ("\">");
      _appendElement (aSB, "\n      ", "item", aLine.item ());
      _appendElement (aSB, "\n      ", "sku", aLine.sku ());
      _appendElement (aSB, "\n      ", "qty", Integer.toString (aLine.qty ()));
      _appendElement (aSB, "\n      ", "price", aLine.price ().toPlainString ());
      aSB.append ("\n      <tax rate=\"").append (aLine.taxRate ()).append ('"');
      nAttributes += 3;
      if (aLine.reason () != null)
      {
        aSB.append (" reason=\"").append (aLine.reason ()).append ('"');
        nAttributes++;
      }
      aSB.append ("/>");
      _appendElement (aSB, "\n      ", "note", aLine.note ());
      aSB.append ("\n      <period>");
      _appendElement (aSB, "\n        ", "start", aLine.start ().toString ());
      _appendElement (aSB, "\n        ", "end", aLine.end ().toString ());
      aSB.append ("\n      </period>");
      aSB.append ("\n    </line>");
    }
    aSB.append ("\n  </lines>");
    aSB.append ("\n</invoice>");
    // Per line 10 elements and 18 text nodes (7 with content, 11 whitespace-only); the lines
    // element has n + 1 whitespace-only text nodes, the rest of the document 8 text nodes
    return new BenchData (aLines, aSB.toString (), 5 + 10 * nLines, nAttributes, 19 * nLines + 9);
  }

  /** @return The lines in document order. Never <code>null</code>. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Line> getAllLines ()
  {
    return m_aLines.getClone ();
  }

  /** @return The number of lines. */
  @Nonnegative
  public int getLineCount ()
  {
    return m_aLines.size ();
  }

  /** @return The document text, with XML declaration. Never <code>null</code>. */
  @NonNull
  public String getXml ()
  {
    return m_sXml;
  }

  /** @return The document encoded as UTF-8. Never <code>null</code>. */
  public byte @NonNull [] getBytes ()
  {
    return m_sXml.getBytes (StandardCharsets.UTF_8);
  }

  /** @return The number of elements of the document. */
  public int getElementCount ()
  {
    return m_nElements;
  }

  /** @return The number of attributes of the document. */
  public int getAttributeCount ()
  {
    return m_nAttributes;
  }

  /** @return The number of text nodes of the document, whitespace-only ones included. */
  public int getTextCount ()
  {
    return m_nTexts;
  }

  /**
   * Parse a document with the default parser settings.
   *
   * @param aBytes
   *        The UTF-8 document.
   * @param sSystemID
   *        The system ID (document URI) of the document.
   * @return The document node. Never <code>null</code>.
   * @throws XdmParseException
   *         if the document is not well-formed
   */
  @NonNull
  public static IXdmNode parse (final byte @NonNull [] aBytes, @NonNull final String sSystemID) throws XdmParseException
  {
    final InputSource aSource = new InputSource (new ByteArrayInputStream (aBytes));
    aSource.setSystemId (sSystemID);
    return new XdmParser ().parse (aSource);
  }
}
