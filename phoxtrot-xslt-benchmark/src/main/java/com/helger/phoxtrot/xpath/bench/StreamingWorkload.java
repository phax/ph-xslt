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
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmStreamReader;
import com.helger.phoxtrot.xpath.bench.BenchData.Line;
import com.helger.phoxtrot.xpath.context.IResourceResolver;
import com.helger.phoxtrot.xpath.context.ResourceUri;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.stream.StreamedRun;

/**
 * Reads the generated document through <code>xsl:source-document</code> and sums the amounts of all
 * lines with <code>xsl:iterate</code>. Streamed, the stylesheet is compiled with the streaming claim
 * and enforced streamability, and the document is read with the incremental reader one line at a
 * time; buffered, the same stylesheet runs without the claim and the document is parsed in full
 * first. Measured: reading the document (from bytes in memory) and the transformation; this is the
 * time, the memory is tested by <code>StreamedMemoryTest</code> of <code>phoxtrot-xslt</code>.
 *
 * @author Philip Helger
 */
public final class StreamingWorkload implements IWorkload
{
  /** The URI the stylesheet reads the document from. */
  public static final String DOC_URI = "file:/generated/invoice.xml";

  /** Serves the document bytes, as a stream or parsed in full. */
  private static final class BytesResolver implements IResourceResolver
  {
    private final byte [] m_aBytes;

    BytesResolver (final byte @NonNull [] aBytes)
    {
      m_aBytes = aBytes;
    }

    @NonNull
    private InputSource _createSource ()
    {
      final InputSource ret = new InputSource (new ByteArrayInputStream (m_aBytes));
      ret.setSystemId (DOC_URI);
      return ret;
    }

    @Nullable
    public IXdmNode resolveDocument (@NonNull final ResourceUri aUri)
    {
      if (!DOC_URI.equals (aUri.absoluteUri ()))
        return null;
      try
      {
        return new XdmParser ().parse (_createSource ());
      }
      catch (final XdmParseException ex)
      {
        throw new IllegalStateException (ex);
      }
    }

    @Nullable
    public String resolveText (@NonNull final ResourceUri aUri, @Nullable final String sEncoding)
    {
      return null;
    }

    @Override
    @Nullable
    public XdmStreamReader openDocumentStream (@NonNull final ResourceUri aUri,
                                               @Nullable final Predicate <XdmQName> aStripWhitespace) throws XdmParseException
    {
      if (!DOC_URI.equals (aUri.absoluteUri ()))
        return null;
      return XdmStreamReader.open (_createSource (), false, XdmParser.DEFAULT_MAX_ELEMENT_DEPTH, aStripWhitespace);
    }
  }

  private final boolean m_bStreamed;
  private final BenchData m_aData;
  private final BytesResolver m_aResolver;
  private final XsltExecutable m_aExecutable;

  /**
   * @param bStreamed
   *        <code>true</code> to stream the document, <code>false</code> to parse it in full.
   * @param nLines
   *        The number of invoice lines of the document.
   */
  public StreamingWorkload (final boolean bStreamed, final int nLines)
  {
    m_bStreamed = bStreamed;
    m_aData = BenchData.create (nLines, true);
    m_aResolver = new BytesResolver (m_aData.getBytes ());
    final XsltCompileOptions aOptions = new XsltCompileOptions ().setStreamingClaim (bStreamed)
                                                                 .setEnforceStreamability (bStreamed);
    m_aExecutable = XsltCompiler.compile (BenchResources.getText ("xsl/streaming.xsl"), aOptions);
  }

  @NonNull
  public Object run ()
  {
    final XsltTransformRequest aRequest = new XsltTransformRequest ().setResolver (m_aResolver)
                                                                     .setMaxSteps (Long.MAX_VALUE);
    aRequest.params ().put (new XdmQName ("href"), XdmSequence.of (XdmAtomic.string (DOC_URI)));
    return m_aExecutable.transform (aRequest).principalSerialized ();
  }

  public void verify (@NonNull final Object aResult)
  {
    BigDecimal aTotal = BigDecimal.ZERO;
    for (final Line aLine : m_aData.getAllLines ())
      aTotal = aTotal.add (BenchExpected.amount (aLine));
    BenchAssert.assertEquals ("streaming output", m_aData.getLineCount () + "/" + BenchExpected.decimal (aTotal), aResult);
  }

  /**
   * Run once, check the result and that the streamed path was taken if and only if streaming is
   * wanted.
   */
  @Override
  public void runAndVerify ()
  {
    final long nBefore = StreamedRun.getStreamedRunCount ();
    verify (run ());
    final long nStreamedRuns = StreamedRun.getStreamedRunCount () - nBefore;
    BenchAssert.assertEquals ("streamed runs", Long.valueOf (m_bStreamed ? 1 : 0), Long.valueOf (nStreamedRuns));
  }
}
