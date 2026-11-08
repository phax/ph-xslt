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
package com.helger.phoxtrot.xpath.membench;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.Supplier;

import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.serialize.MicroReader;
import com.sun.management.HotSpotDiagnosticMXBean;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.tree.tiny.TinyTree;

/**
 * Measures the retained heap of one parsed document in three tree models: phoxtrot
 * {@link IXdmNode}, ph-xml MicroDOM and Saxon TinyTree. The primary figure is the deep size of
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

  /**
   * Request a garbage collection four times in a row and read the used heap
   * afterwards. <code>System.gc ()</code> is only a request, so the figure is
   * approximate.
   *
   * @return The used heap in bytes.
   */
  private static long _usedHeapAfterGC ()
  {
    final MemoryMXBean aBean = ManagementFactory.getMemoryMXBean ();
    for (int i = 0; i < 4; ++i)
      System.gc ();
    return aBean.getHeapMemoryUsage ().getUsed ();
  }

  /**
   * Measure the heap a tree retains as the difference of the used heap after
   * GC before and after building it. The tree is touched after the second
   * measurement so that it cannot be collected before it.
   *
   * @param aBuilder
   *        Parses the document and returns the tree.
   * @return The heap delta in bytes; can be inaccurate or even negative,
   *         since GC is only requested.
   */
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

  /**
   * Count the nodes of an XDM tree with an explicit stack (no recursion): the
   * element nodes, the attributes of all nodes (by attribute count; attribute
   * nodes are not visited), the text nodes and all other child nodes
   * including the root itself (document, comment, processing instruction).
   *
   * @param aRoot
   *        The root of the tree, usually the document node.
   * @return The four counts in the order elements, attributes, text, other.
   *         The last one is not printed.
   */
  @NonNull
  private static long [] _countXdmNodes (@NonNull final IXdmNode aRoot)
  {
    // elements, attributes, text, other
    final long [] ret = new long [4];
    final Deque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aNode = aStack.pop ();
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

  /**
   * Parse a file into the phoxtrot XDM tree.
   *
   * @param aFile
   *        The XML file.
   * @return The document node.
   * @throws IllegalStateException
   *         wrapping the {@link XdmParseException} of a file that cannot be
   *         parsed
   */
  @NonNull
  private static IXdmNode _parseXdm (@NonNull final File aFile)
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

  /**
   * Parse a file into the phoxtrot XDM tree without node locations, if the
   * engine on the class path has the option
   * (<code>XdmParser.setRecordLocations</code>, called by reflection so that
   * the benchmark also runs against older engine jars).
   *
   * @param aFile
   *        The XML file.
   * @return The document node, or <code>null</code> if the engine cannot
   *         parse without locations.
   * @throws IllegalStateException
   *         wrapping the exception of a file that cannot be parsed
   */
  @Nullable
  private static IXdmNode _parseXdmWithoutLocations (@NonNull final File aFile)
  {
    final Method aSetter;
    try
    {
      aSetter = XdmParser.class.getMethod ("setRecordLocations", boolean.class);
    }
    catch (final NoSuchMethodException ex)
    {
      return null;
    }
    try
    {
      final XdmParser aParser = new XdmParser ();
      aSetter.invoke (aParser, Boolean.FALSE);
      return aParser.parse (aFile.toPath ());
    }
    catch (final ReflectiveOperationException | XdmParseException ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  /**
   * Parse a file into a ph-xml MicroDOM document.
   *
   * @param aFile
   *        The XML file.
   * @return The document, or <code>null</code> if MicroReader cannot read it.
   */
  @NonNull
  private static IMicroDocument _parseMicro (@NonNull final File aFile)
  {
    return MicroReader.readMicroXML (aFile);
  }

  /**
   * Parse a file into a Saxon tree with the default tree model of the
   * configuration (TinyTree, checked by the caller).
   *
   * @param aConfig
   *        The Saxon configuration; its NamePool receives the names of the
   *        document.
   * @param aFile
   *        The XML file.
   * @return The tree.
   * @throws IllegalStateException
   *         wrapping any exception Saxon throws
   */
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

  /**
   * @param n
   *        A size in bytes.
   * @return The size in MiB (1024 * 1024 bytes) with two decimals.
   */
  @NonNull
  private static String _mb (final long n)
  {
    return String.format (Locale.ROOT, "%.2f", Double.valueOf (n / 1024.0 / 1024.0));
  }

  /**
   * @return <code>true</code> if the JVM runs with compact object headers
   *         (<code>-XX:+UseCompactObjectHeaders</code>, JDK 25 and later),
   *         <code>false</code> if they are off or the JVM does not know the
   *         option.
   */
  private static boolean _isCompactObjectHeaders ()
  {
    try
    {
      return Boolean.parseBoolean (ManagementFactory.getPlatformMXBean (HotSpotDiagnosticMXBean.class)
                                                    .getVMOption ("UseCompactObjectHeaders")
                                                    .getValue ());
    }
    catch (final IllegalArgumentException ex)
    {
      // Unknown option before JDK 24
      return false;
    }
  }

  /**
   * The ph-xdm mode: only the phoxtrot tree is measured, as tab separated
   * lines for <code>run.sh</code>, which joins the lines of several JVM runs
   * into one table. First a <code>VM</code> line with the Java version and
   * whether compact object headers are on, then a <code>CLASS</code> line
   * with the JOL instance size of each node class the engine has (and of the
   * <code>Details</code> holder), then one <code>DOC</code> line per
   * document with its name, element, attribute and text node counts, the
   * JOL deep size of the parsed tree in bytes and the deep size of the tree
   * parsed without node locations (-1 if the engine has no such option).
   *
   * @throws Exception
   *         if a document is missing or cannot be parsed
   */
  private static void _runXdmOnly () throws Exception
  {
    System.out.println ("VM\t" +
                        System.getProperty ("java.version") +
                        "\t" +
                        (_isCompactObjectHeaders () ? "compact" : "normal"));
    final StringBuilder aClasses = new StringBuilder ("CLASS");
    for (final String sClass : new String [] { "XdmDocumentNode",
                                               "XdmElementNode",
                                               "XdmElementNode$Located",
                                               "XdmAttributeNode",
                                               "XdmAttributeNode$Located",
                                               "XdmTextNode",
                                               "XdmTextNode$Located",
                                               "AbstractXdmNode$Details" })
      try
      {
        final Class <?> aClass = Class.forName ("com.helger.phoxtrot.xdm." + sClass);
        aClasses.append ('\t').append (sClass).append ('=').append (ClassLayout.parseClass (aClass).instanceSize ());
      }
      catch (final ClassNotFoundException ex)
      {
        // Not in this version of the engine
      }
    System.out.println (aClasses);
    _parseXdm (new File (DOCUMENTS[0][1]));
    for (final String [] aDoc : DOCUMENTS)
    {
      final IXdmNode aXdm = _parseXdm (new File (aDoc[1]));
      final long [] aCounts = _countXdmNodes (aXdm);
      final long nXdm = GraphLayout.parseInstance (aXdm).totalSize ();
      final IXdmNode aUnlocated = _parseXdmWithoutLocations (new File (aDoc[1]));
      final long nUnlocated = aUnlocated == null ? -1 : GraphLayout.parseInstance (aUnlocated).totalSize ();
      System.out.println ("DOC\t" +
                          aDoc[0] +
                          "\t" +
                          aCounts[0] +
                          "\t" +
                          aCounts[1] +
                          "\t" +
                          aCounts[2] +
                          "\t" +
                          nXdm +
                          "\t" +
                          nUnlocated);
    }
  }

  /**
   * Run the benchmark over the fixed document list. The paths are relative to
   * the working directory, which must be the module directory (as with
   * <code>mvn exec:exec</code>, which also passes <code>-Xmx4g</code> and the
   * flags JOL needs); the documents come from the sibling checkouts of
   * <code>peppol-commons</code>, <code>qt3tests</code> and
   * <code>xslt30-test</code>.
   * <p>
   * First the first document is parsed once in each model so that class and
   * static initialisation is not attributed to it. Then, for every document,
   * a Markdown table row is printed with the file size, the XDM element,
   * attribute and text node counts, the JOL deep size of the XDM tree, of the
   * MicroDOM document and of the Saxon TinyTree (its object graph minus the
   * graph of its fresh {@link Configuration}), the growth of the Saxon
   * NamePool while parsing, and the three sizes divided by the file size.
   * Finally a second table is printed with the heap delta after GC of a fresh
   * parse in each model, where the Saxon figure includes a new Configuration.
   * <p>
   * With the argument <code>xdm</code>, only the phoxtrot tree is measured
   * (see {@link #_runXdmOnly()}).
   *
   * @param args
   *        Nothing for all three models, <code>xdm</code> for the phoxtrot
   *        tree only.
   * @throws Exception
   *         if a document is missing or cannot be parsed
   */
  public static void main (final String [] args) throws Exception
  {
    if (args.length > 0 && "xdm".equals (args[0]))
    {
      _runXdmOnly ();
      return;
    }
    System.out.println ("Java " +
                        System.getProperty ("java.version") +
                        ", " +
                        System.getProperty ("java.vm.name") +
                        ", compact object headers " +
                        (_isCompactObjectHeaders () ? "on" : "off"));

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

      final IXdmNode aXdm = _parseXdm (aFile);
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
