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
 * with a streamable stylesheet. Arguments: the number of records and
 * <code>streamed</code> or <code>buffered</code> (a small result), or
 * <code>copy</code> or <code>copy-undrained</code> (a streamed copy of every
 * record, written to a stream that only counts the bytes, while it is built
 * or after it was built in full). Prints the result, the document size, the
 * peak heap use and whether the streamed and drained paths ran; exits with 3
 * on an OutOfMemoryError.
 *
 * @author Philip Helger
 */
public final class StreamedMemoryMain
{
  static final String DOC_URI = "file:/generated/big.xml";

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
      if (!DOC_URI.equals (sAbsoluteUri))
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
      if (!DOC_URI.equals (sAbsoluteUri))
        return null;
      return XdmStreamReader.open (_source (), false, XdmParser.DEFAULT_MAX_ELEMENT_DEPTH, aStripWhitespace);
    }

    long getBytes ()
    {
      return m_aLast == null ? 0 : m_aLast.getBytes ();
    }
  }

  static final String SHEET = XsltTestHelper.SHEET +
                              "<xsl:output method='text'/>" +
                              "<xsl:template name='xsl:initial-template'>" +
                              "<xsl:source-document href='" +
                              DOC_URI +
                              "' streamable='yes'>" +
                              "<xsl:for-each select='data/rec'>" +
                              "<xsl:if test='position() mod " +
                              HIT_EVERY +
                              " = 0'><xsl:value-of select='@id'/>,</xsl:if>" +
                              "</xsl:for-each>" +
                              "</xsl:source-document>" +
                              "</xsl:template>" +
                              XsltTestHelper.END;

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
    final GeneratorResolver aResolver = new GeneratorResolver (nRecords);
    final long nStart = System.nanoTime ();
    try
    {
      final long nRunsBefore = StreamedRun.getStreamedRunCount ();
      final long nDrainedBefore = ResultDrain.getDrainedRunCount ();
      final XsltCompileOptions aOptions = new XsltCompileOptions ().setStreamingClaim (bStreamed);
      final XsltTransformRequest aRequest = new XsltTransformRequest ().setResolver (aResolver);
      if (bCopy)
      {
        final CountingStream aOS = new CountingStream ();
        XsltCompiler.compile (copySheet ("copy".equals (sMode)), aOptions).transform (aRequest, aOS);
        System.out.println ("out-bytes=" + aOS.getBytes ());
        System.out.println ("out-tail=" + aOS.getTail ());
      }
      else
        System.out.println ("result=" + XsltCompiler.compile (SHEET, aOptions).transform (aRequest).principalSerialized ());
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
