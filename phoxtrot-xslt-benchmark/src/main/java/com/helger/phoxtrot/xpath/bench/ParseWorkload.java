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
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParser;

/**
 * Builds a tree with {@link com.helger.phoxtrot.xdm.XdmParser} from the UTF-8 bytes of the generated
 * document (JDK SAX parser, tree builder, document order numbering while building). Measured: the
 * parse from bytes in memory. The allocation per parse is the <code>gc.alloc.rate.norm</code> of
 * the JMH GC profiler (<code>-prof gc</code>). The case <code>unlocated</code> parses without node
 * locations (<code>XdmParser.setRecordLocations (false)</code>), called by reflection so that the
 * benchmark also runs against an engine that predates the option: it then parses like
 * <code>parse</code>.
 *
 * @author Philip Helger
 */
public final class ParseWorkload implements IWorkload
{
  private final BenchData m_aData;
  private final byte [] m_aBytes;
  // XdmParser.setRecordLocations of the unlocated case; null for the default
  // parse or an engine without the option
  private final Method m_aRecordLocations;

  /**
   * @param nLines
   *        The number of invoice lines of the document.
   * @param bLocations
   *        <code>false</code> to parse without node locations, where the
   *        engine supports it.
   */
  public ParseWorkload (final int nLines, final boolean bLocations)
  {
    m_aData = BenchData.create (nLines, true);
    m_aBytes = m_aData.getBytes ();
    m_aRecordLocations = bLocations ? null : _findRecordLocations ();
  }

  /**
   * @return <code>XdmParser.setRecordLocations (boolean)</code>, or
   *         <code>null</code> if the engine on the class path has no such
   *         method.
   */
  @Nullable
  private static Method _findRecordLocations ()
  {
    try
    {
      return XdmParser.class.getMethod ("setRecordLocations", boolean.class);
    }
    catch (final NoSuchMethodException ex)
    {
      return null;
    }
  }

  @NonNull
  public Object run () throws Exception
  {
    if (m_aRecordLocations == null)
      return BenchData.parse (m_aBytes, "urn:bench:invoice.xml");
    final XdmParser aParser = new XdmParser ();
    m_aRecordLocations.invoke (aParser, Boolean.FALSE);
    final InputSource aSource = new InputSource (new ByteArrayInputStream (m_aBytes));
    aSource.setSystemId ("urn:bench:invoice.xml");
    return aParser.parse (aSource);
  }

  public void verify (@NonNull final Object aResult)
  {
    int nElements = 0;
    int nAttributes = 0;
    int nTexts = 0;
    final Deque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push ((IXdmNode) aResult);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aNode = aStack.pop ();
      if (aNode.isElement ())
      {
        nElements++;
        nAttributes += aNode.getAttributeCount ();
      }
      else
        if (aNode.isText ())
          nTexts++;
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.push (aNode.getChildAtIndex (i));
    }
    BenchAssert.assertEquals ("elements", Integer.valueOf (m_aData.getElementCount ()), Integer.valueOf (nElements));
    BenchAssert.assertEquals ("attributes", Integer.valueOf (m_aData.getAttributeCount ()), Integer.valueOf (nAttributes));
    BenchAssert.assertEquals ("text nodes", Integer.valueOf (m_aData.getTextCount ()), Integer.valueOf (nTexts));
  }
}
