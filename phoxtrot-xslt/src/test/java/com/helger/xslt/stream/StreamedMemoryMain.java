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

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmStreamReader;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltTestHelper;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.runtime.ResultDrain;

/**
 * The forked process of {@link StreamedMemoryTest}: transforms a generated
 * document, which never exists as a whole (neither on disk nor in memory),
 * with a streamable stylesheet. Arguments: the number of records, the mode
 * (<code>streamed</code> or <code>buffered</code> for a small result, or
 * <code>copy</code> or <code>copy-undrained</code> for a streamed copy of every
 * record, written to a stream that only counts the bytes, while it is built or
 * after it was built in full), and optionally the shape of the stylesheet
 * ({@link #SHAPES}, default <code>for-each</code>, not for the copy modes).
 * Prints the result, the document size, the peak heap use and whether the
 * streamed and drained paths ran; exits with 3 on an OutOfMemoryError.
 *
 * @author Philip Helger
 */
public final class StreamedMemoryMain
{
  static final String DOC_URI = "file:/generated/big.xml";
  /** A second document with the same content, for xsl:merge. */
  static final String DOC2_URI = "file:/generated/big2.xml";

  /** Every this many records is reported by the stylesheet. */
  static final int HIT_EVERY = 250_000;

  /** Writes the document on demand. */
  static final class GeneratorStream extends InputStream
  {
    private final long m_nRecords;
    private long m_nNext = 0;
    private byte [] m_aChunk = "<?xml version='1.0'?>\n<data>\n".getBytes (StandardCharsets.UTF_8);
    private int m_nPos;
    private boolean m_bEnd;
    private long m_nBytes;

    GeneratorStream (final long nRecords)
    {
      m_nRecords = nRecords;
    }

    private boolean _fill ()
    {
      if (m_nPos < m_aChunk.length)
        return true;
      if (m_bEnd)
        return false;
      final StringBuilder aSB = new StringBuilder (64 * 1024 + 256);
      while (aSB.length () < 64 * 1024 && m_nNext < m_nRecords)
      {
        ++m_nNext;
        aSB.append ("  <rec id='r")
           .append (m_nNext)
           .append ("' g='")
           .append (m_nNext / 4)
           .append ("'><v>")
           .append (m_nNext % 7)
           .append ("</v><pad>xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx</pad></rec>\n");
      }
      if (m_nNext >= m_nRecords)
      {
        aSB.append ("</data>\n");
        m_bEnd = true;
      }
      m_aChunk = aSB.toString ().getBytes (StandardCharsets.UTF_8);
      m_nPos = 0;
      return true;
    }

    @Override
    public int read ()
    {
      if (!_fill ())
        return -1;
      m_nBytes++;
      return m_aChunk[m_nPos++] & 0xff;
    }

    @Override
    public int read (final byte [] aBuf, final int nOfs, final int nLen)
    {
      if (nLen == 0)
        return 0;
      if (!_fill ())
        return -1;
      final int n = Math.min (nLen, m_aChunk.length - m_nPos);
      System.arraycopy (m_aChunk, m_nPos, aBuf, nOfs, n);
      m_nPos += n;
      m_nBytes += n;
      return n;
    }

    long getBytes ()
    {
      return m_nBytes;
    }
  }

  /** Serves the generated document, streamed or read in full. */
  static final class GeneratorResolver implements IResourceResolver
  {
    private final long m_nRecords;
    private GeneratorStream m_aLast;

    GeneratorResolver (final long nRecords)
    {
      m_nRecords = nRecords;
    }

    @NonNull
    private InputSource _source ()
    {
      m_aLast = new GeneratorStream (m_nRecords);
      final InputSource ret = new InputSource (m_aLast);
      ret.setSystemId (DOC_URI);
      return ret;
    }

    @Nullable
    public IXdmNode resolveDocument (@NonNull final String sAbsoluteUri)
    {
      if (!DOC_URI.equals (sAbsoluteUri) && !DOC2_URI.equals (sAbsoluteUri))
        return null;
      try
      {
        return new XdmParser ().parse (_source ());
      }
      catch (final XdmParseException ex)
      {
        return null;
      }
    }

    @Nullable
    public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
    {
      return null;
    }

    @Override
    @Nullable
    public XdmStreamReader openDocumentStream (@NonNull final String sAbsoluteUri,
                                               @Nullable final Predicate <XdmQName> aStripWhitespace) throws XdmParseException
    {
      if (!DOC_URI.equals (sAbsoluteUri) && !DOC2_URI.equals (sAbsoluteUri))
        return null;
      return XdmStreamReader.open (_source (), false, XdmParser.DEFAULT_MAX_ELEMENT_DEPTH, aStripWhitespace);
    }

    long getBytes ()
    {
      return m_aLast == null ? 0 : m_aLast.getBytes ();
    }
  }

  /** Every this many groups of four records is reported by the grouping stylesheet. */
  static final int HIT_EVERY_GROUP = HIT_EVERY / 4;

  /** The stylesheet shapes, each streaming another construct. */
  static final String [] SHAPES = { "for-each", "iterate", "group", "fork", "merge", "generate-id", "snapshot" };

  private static final String HITS = "<xsl:for-each select='data/rec'>" +
                                     "<xsl:if test='position() mod " +
                                     HIT_EVERY +
                                     " = 0'><xsl:value-of select='@id'/>,</xsl:if>" +
                                     "</xsl:for-each>";
  private static final String SUM = "<xsl:iterate select='data/rec'><xsl:param name='s' select='0'/>" +
                                    "<xsl:on-completion><xsl:value-of select='$s'/></xsl:on-completion>" +
                                    "<xsl:next-iteration><xsl:with-param name='s' select='$s + xs:integer(v)'/></xsl:next-iteration>" +
                                    "</xsl:iterate>";

  @NonNull
  private static String _sheet (@NonNull final String sDecls, @NonNull final String sBody)
  {
    return XsltTestHelper.SHEET +
           "<xsl:output method='text'/>" +
           sDecls +
           "<xsl:template name='xsl:initial-template'>" +
           sBody +
           "</xsl:template>" +
           XsltTestHelper.END;
  }

  @NonNull
  private static String _sourceDocument (@NonNull final String sAttrs, @NonNull final String sBody)
  {
    return "<xsl:source-document href='" + DOC_URI + "' streamable='yes'" + sAttrs + ">" + sBody + "</xsl:source-document>";
  }

  /**
   * @param sShape
   *        One of {@link #SHAPES}.
   * @return The stylesheet.
   */
  @NonNull
  static String getSheet (@NonNull final String sShape)
  {
    return switch (sShape)
    {
      case "for-each" -> _sheet ("", _sourceDocument ("", HITS));
      case "iterate" -> _sheet ("", _sourceDocument ("", SUM));
      case "group" -> _sheet ("",
                              _sourceDocument ("",
                                               "<xsl:for-each-group select='data/rec' group-adjacent='@g'>" +
                                                   "<xsl:if test='position() mod " +
                                                   HIT_EVERY_GROUP +
                                                   " = 0'><xsl:value-of select='current-grouping-key()'/>,</xsl:if>" +
                                                   "</xsl:for-each-group>"));
      case "fork" -> _sheet ("",
                             _sourceDocument ("",
                                              "<xsl:fork><xsl:sequence>" +
                                                  HITS +
                                                  "</xsl:sequence><xsl:sequence>" +
                                                  SUM +
                                                  "</xsl:sequence></xsl:fork>"));
      case "merge" -> _sheet ("",
                              "<xsl:merge>" +
                                  "<xsl:merge-source for-each-source=\"'" +
                                  DOC_URI +
                                  "', '" +
                                  DOC2_URI +
                                  "'\" streamable='yes' select='data/rec'><xsl:merge-key select='xs:integer(substring(@id, 2))'/></xsl:merge-source>" +
                                  "<xsl:merge-action><xsl:if test='position() mod " +
                                  HIT_EVERY +
                                  " = 0'><xsl:value-of select='current-merge-key()'/>:<xsl:value-of select='count(current-merge-group())'/>,</xsl:if></xsl:merge-action>" +
                                  "</xsl:merge>");
      // Every record gets an identifier; the predicate and the wrapper do not
      // read the document
      case "generate-id" -> _sheet ("<xsl:param name='p' select='1'/>",
                                    _sourceDocument ("",
                                                     "<out a='{$p + 1}'><xsl:for-each select=\"data/rec[@g != '-1']\">" +
                                                         "<xsl:if test='string-length(generate-id()) gt 0 and position() mod " +
                                                         HIT_EVERY +
                                                         " = 0'><xsl:value-of select='@id'/>,</xsl:if>" +
                                                         "</xsl:for-each></out>"));
      // A snapshot of every record keeps its accumulator values after the
      // record is dropped
      case "snapshot" -> _sheet ("<xsl:accumulator name='total' initial-value='0' streamable='yes'>" +
                                 "<xsl:accumulator-rule match='v/text()' select='$value + xs:integer(.)'/>" +
                                 "</xsl:accumulator>",
                                 _sourceDocument (" use-accumulators='total'",
                                                  "<xsl:iterate select='data/rec'><xsl:param name='prev' select='()'/>" +
                                                      "<xsl:if test='position() mod " +
                                                      HIT_EVERY +
                                                      " = 0'><xsl:value-of select=\"$prev/accumulator-after('total')\"/>,</xsl:if>" +
                                                      "<xsl:next-iteration><xsl:with-param name='prev' select='snapshot(.)'/></xsl:next-iteration>" +
                                                      "</xsl:iterate>"));
      default -> throw new IllegalArgumentException ("Unknown shape " + sShape);
    };
  }

  private static long _sum (final long nRecords)
  {
    long ret = 0;
    for (long i = 1; i <= nRecords; ++i)
      ret += i % 7;
    return ret;
  }

  /**
   * @param sShape
   *        One of {@link #SHAPES}.
   * @param nRecords
   *        The number of records.
   * @return The result the stylesheet of the shape must give.
   */
  @NonNull
  static String getExpected (@NonNull final String sShape, final long nRecords)
  {
    final StringBuilder aSB = new StringBuilder ();
    switch (sShape)
    {
      case "for-each", "fork", "generate-id":
        for (long i = HIT_EVERY; i <= nRecords; i += HIT_EVERY)
          aSB.append ('r').append (i).append (',');
        if ("fork".equals (sShape))
          aSB.append (_sum (nRecords));
        break;
      case "iterate":
        aSB.append (_sum (nRecords));
        break;
      case "group":
        // Group p (1-based) has the key p - 1; keys 0 to nRecords / 4
        for (long p = HIT_EVERY_GROUP; p <= nRecords / 4 + 1; p += HIT_EVERY_GROUP)
          aSB.append (p - 1).append (',');
        break;
      case "merge":
        for (long i = HIT_EVERY; i <= nRecords; i += HIT_EVERY)
          aSB.append (i).append (":2,");
        break;
      case "snapshot":
        for (long i = HIT_EVERY; i <= nRecords; i += HIT_EVERY)
          aSB.append (_sum (i - 1)).append (',');
        break;
      default:
        throw new IllegalArgumentException ("Unknown shape " + sShape);
    }
    return aSB.toString ();
  }

  /** The start of the output of {@link #copySheet(boolean)}. */
  static final String COPY_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><out>";

  /** The end of the output of {@link #copySheet(boolean)}. */
  static final String COPY_TAIL = "</out>";

  /** Counts the bytes written and keeps the last few. */
  static final class CountingStream extends OutputStream
  {
    private final byte [] m_aTail = new byte [64];
    private long m_nBytes;

    @Override
    public void write (final int n)
    {
      m_aTail[(int) (m_nBytes % m_aTail.length)] = (byte) n;
      m_nBytes++;
    }

    @Override
    public void write (final byte [] aBuf, final int nOfs, final int nLen)
    {
      for (int i = 0; i < nLen; ++i)
        write (aBuf[nOfs + i]);
    }

    long getBytes ()
    {
      return m_nBytes;
    }

    @NonNull
    String getTail ()
    {
      final int nLen = (int) Math.min (m_nBytes, m_aTail.length);
      final byte [] aTail = new byte [nLen];
      for (int i = 0; i < nLen; ++i)
        aTail[i] = m_aTail[(int) ((m_nBytes - nLen + i) % m_aTail.length)];
      return new String (aTail, StandardCharsets.UTF_8);
    }
  }

  /**
   * @param bExplicitMethod
   *        <code>true</code> to state the output method (the result is
   *        written while it is built), <code>false</code> to leave it to the
   *        result (it is built in full first).
   * @return A stylesheet copying every record of the streamed document.
   */
  @NonNull
  static String copySheet (final boolean bExplicitMethod)
  {
    return XsltTestHelper.SHEET +
           (bExplicitMethod ? "<xsl:output method='xml'/>" : "") +
           "<xsl:template name='xsl:initial-template'>" +
           "<xsl:source-document href='" +
           DOC_URI +
           "' streamable='yes'>" +
           "<out xsl:exclude-result-prefixes='#all'><xsl:copy-of select='data/rec'/></out>" +
           "</xsl:source-document>" +
           "</xsl:template>" +
           XsltTestHelper.END;
  }

  private StreamedMemoryMain ()
  {}

  private static long _getPeakHeap ()
  {
    long ret = 0;
    for (final MemoryPoolMXBean aPool : ManagementFactory.getMemoryPoolMXBeans ())
      if (aPool.getType () == MemoryType.HEAP && aPool.getPeakUsage () != null)
        ret += aPool.getPeakUsage ().getUsed ();
    return ret;
  }

  public static void main (final String [] aArgs)
  {
    final long nRecords = Long.parseLong (aArgs[0]);
    final String sMode = aArgs[1];
    final boolean bCopy = sMode.startsWith ("copy");
    final boolean bStreamed = "streamed".equals (sMode) || bCopy;
    final String sShape = aArgs.length > 2 ? aArgs[2] : "for-each";
    final GeneratorResolver aResolver = new GeneratorResolver (nRecords);
    final long nStart = System.nanoTime ();
    try
    {
      final long nRunsBefore = StreamedRun.getStreamedRunCount ();
      final long nDrainedBefore = ResultDrain.getDrainedRunCount ();
      final XsltCompileOptions aOptions = new XsltCompileOptions ().setStreamingClaim (bStreamed);
      final XsltTransformRequest aRequest = new XsltTransformRequest ().setResolver (aResolver)
                                                                       // An accumulator rule per node
                                                                       .setMaxSteps (Long.MAX_VALUE);
      if (bCopy)
      {
        final CountingStream aOS = new CountingStream ();
        XsltCompiler.compile (copySheet ("copy".equals (sMode)), aOptions).transform (aRequest, aOS);
        System.out.println ("out-bytes=" + aOS.getBytes ());
        System.out.println ("out-tail=" + aOS.getTail ());
      }
      else
        System.out.println ("result=" + XsltCompiler.compile (getSheet (sShape), aOptions).transform (aRequest).principalSerialized ());
      System.out.println ("streamed-runs=" + (StreamedRun.getStreamedRunCount () - nRunsBefore));
      System.out.println ("drained-runs=" + (ResultDrain.getDrainedRunCount () - nDrainedBefore));
    }
    catch (final OutOfMemoryError ex)
    {
      System.out.println ("out-of-memory");
      System.out.println ("bytes=" + aResolver.getBytes ());
      System.exit (3);
    }
    System.out.println ("bytes=" + aResolver.getBytes ());
    System.out.println ("peak-heap=" + _getPeakHeap ());
    System.out.println ("max-heap=" + Runtime.getRuntime ().maxMemory ());
    System.out.println ("millis=" + (System.nanoTime () - nStart) / 1_000_000);
  }
}
