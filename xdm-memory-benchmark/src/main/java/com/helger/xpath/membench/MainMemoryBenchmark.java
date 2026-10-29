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
package com.helger.xpath.membench;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.Supplier;

import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.openjdk.jol.info.GraphLayout;

import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.serialize.MicroReader;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.tree.tiny.TinyTree;

/**
 * Measures the retained heap of one parsed document in three tree models: ph-xslt
 * {@link XdmNode}, ph-xml MicroDOM and Saxon TinyTree. The primary figure is the deep size of
 * the object graph reachable from the tree (JOL); a heap delta after full GC is printed as a
 * cross-check.
 *
 * @author Philip Helger
 */
public final class MainMemoryBenchmark
{
  private static final String BASE = "../../../git-thirdparty/";
  private static final String [] [] DOCUMENTS = { { "UBL invoice (Peppol)",
                                                    "../../peppol-commons/peppol-testfiles/src/main/resources/external/peppol-ubl/invoice1.xml" },
                                                  { "XMark auction",
                                                    BASE + "qt3tests/app/XMark/XMarkAuction.xml" },
                                                  { "iTunes plist",
                                                    BASE + "qt3tests/app/Demos/iTunesMusicLibrary.xml" },
                                                  { "Bank transactions",
                                                    BASE + "xslt30-test/tests/strm/docs/big-transactions.xml" },
                                                  { "Unicode characters",
                                                    BASE + "xslt30-test/tests/misc/unicode-90/docs/unicode-L.xml" },
                                                  { "Old Testament",
                                                    BASE + "xslt30-test/tests/strm/docs/ot.xml" },
                                                  { "XSLT 3.0 spec (XHTML)",
                                                    BASE + "xslt30-test/specs/xslt-lcwd30.xml" },
                                                  { "XSLT test stylesheets",
                                                    BASE + "xslt30-test/report/all-test-xsl.xml" } };

  private MainMemoryBenchmark ()
  {}

  private static long _usedHeapAfterGC ()
  {
    final MemoryMXBean aBean = ManagementFactory.getMemoryMXBean ();
    for (int i = 0; i < 4; ++i)
      System.gc ();
    return aBean.getHeapMemoryUsage ().getUsed ();
  }

  private static long _heapDelta (@NonNull final Supplier <Object> aBuilder)
  {
    final long nBefore = _usedHeapAfterGC ();
    final Object aTree = aBuilder.get ();
    final long nAfter = _usedHeapAfterGC ();
    // Keep the tree reachable until after the measurement
    if (aTree.hashCode () == 42)
      System.out.print ("");
    return nAfter - nBefore;
  }

  @NonNull
  private static long [] _countXdmNodes (@NonNull final XdmNode aRoot)
  {
    // elements, attributes, text, other
    final long [] ret = new long [4];
    final Deque <XdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final XdmNode aNode = aStack.pop ();
      final ENodeKind eKind = aNode.getKind ();
      if (eKind == ENodeKind.ELEMENT)
        ret[0]++;
      else
        if (eKind == ENodeKind.TEXT)
          ret[2]++;
        else
          ret[3]++;
      ret[1] += aNode.getAttributeCount ();
      for (int i = 0; i < aNode.getChildCount (); ++i)
        aStack.push (aNode.getChildAtIndex (i));
    }
    return ret;
  }

  @NonNull
  private static XdmNode _parseXdm (@NonNull final File aFile)
  {
    try
    {
      return new XdmParser ().parse (aFile.toPath ());
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  @NonNull
  private static IMicroDocument _parseMicro (@NonNull final File aFile)
  {
    return MicroReader.readMicroXML (aFile);
  }

  @NonNull
  private static TreeInfo _parseSaxon (@NonNull final Configuration aConfig, @NonNull final File aFile)
  {
    try
    {
      return aConfig.buildDocumentTree (new StreamSource (aFile));
    }
    catch (final Exception ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  @NonNull
  private static String _mb (final long n)
  {
    return String.format (Locale.ROOT, "%.2f", Double.valueOf (n / 1024.0 / 1024.0));
  }

  public static void main (final String [] args) throws Exception
  {
    System.out.println ("Java " + System.getProperty ("java.version") + ", " + System.getProperty ("java.vm.name"));

    // Warm-up so that one-off class and static initialisation is not attributed to the first document
    final File aWarmUp = new File (DOCUMENTS[0][1]);
    _parseXdm (aWarmUp);
    _parseMicro (aWarmUp);
    _parseSaxon (new Configuration (), aWarmUp);

    System.out.println ("| Document | File size | Elements | Attributes | Text nodes | ph-xdm | MicroDOM | Saxon TinyTree | Saxon NamePool growth | ph-xdm / file | MicroDOM / file | TinyTree / file |");
    System.out.println ("|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|");
    final StringBuilder aHeap = new StringBuilder ();
    aHeap.append ("| Document | ph-xdm | MicroDOM | Saxon TinyTree |\n|---|--:|--:|--:|\n");
    for (final String [] aDoc : DOCUMENTS)
    {
      final File aFile = new File (aDoc[1]);
      final long nFileSize = Files.size (Path.of (aFile.getPath ()));

      final XdmNode aXdm = _parseXdm (aFile);
      final long [] aCounts = _countXdmNodes (aXdm);
      final long nXdm = GraphLayout.parseInstance (aXdm).totalSize ();

      final IMicroDocument aMicro = _parseMicro (aFile);
      final long nMicro = GraphLayout.parseInstance (aMicro).totalSize ();

      // Fresh configuration per document; the configuration (with its NamePool) is shared state and
      // excluded from the tree size, the NamePool growth is reported separately
      final Configuration aConfig = new Configuration ();
      final NamePool aNamePool = aConfig.getNamePool ();
      final long nPoolBefore = GraphLayout.parseInstance (aNamePool).totalSize ();
      final TreeInfo aSaxon = _parseSaxon (aConfig, aFile);
      if (!(aSaxon instanceof TinyTree))
        throw new IllegalStateException ("Expected a TinyTree but got " + aSaxon.getClass ().getName ());
      final long nPoolAfter = GraphLayout.parseInstance (aNamePool).totalSize ();
      final long nSaxon = GraphLayout.parseInstance (aSaxon)
                                    .subtract (GraphLayout.parseInstance (aConfig))
                                    .totalSize ();

      System.out.println ("| " +
                          aDoc[0] +
                          " | " +
                          _mb (nFileSize) +
                          " MB | " +
                          aCounts[0] +
                          " | " +
                          aCounts[1] +
                          " | " +
                          aCounts[2] +
                          " | " +
                          _mb (nXdm) +
                          " MB | " +
                          _mb (nMicro) +
                          " MB | " +
                          _mb (nSaxon) +
                          " MB | " +
                          _mb (nPoolAfter - nPoolBefore) +
                          " MB | " +
                          String.format (Locale.ROOT, "%.1f", Double.valueOf ((double) nXdm / nFileSize)) +
                          " | " +
                          String.format (Locale.ROOT, "%.1f", Double.valueOf ((double) nMicro / nFileSize)) +
                          " | " +
                          String.format (Locale.ROOT, "%.1f", Double.valueOf ((double) nSaxon / nFileSize)) +
                          " |");

      aHeap.append ("| ")
           .append (aDoc[0])
           .append (" | ")
           .append (_mb (_heapDelta ( () -> _parseXdm (aFile))))
           .append (" MB | ")
           .append (_mb (_heapDelta ( () -> _parseMicro (aFile))))
           .append (" MB | ")
           .append (_mb (_heapDelta ( () -> _parseSaxon (new Configuration (), aFile))))
           .append (" MB |\n");
    }
    System.out.println ();
    System.out.println ("Cross-check: heap delta after full GC (Saxon including its Configuration)");
    System.out.println (aHeap);
  }
}
