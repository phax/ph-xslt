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

import java.util.ArrayDeque;
import java.util.Deque;

import org.jspecify.annotations.NonNull;

import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * Builds a tree with {@link com.helger.phoxtrot.xdm.XdmParser} from the UTF-8 bytes of the generated
 * document (JDK SAX parser, tree builder, document order numbering while building). Measured: the
 * parse from bytes in memory. The allocation per parse is the <code>gc.alloc.rate.norm</code> of
 * the JMH GC profiler (<code>-prof gc</code>).
 *
 * @author Philip Helger
 */
public final class ParseWorkload implements IWorkload
{
  private final BenchData m_aData;
  private final byte [] m_aBytes;

  /**
   * @param nLines
   *        The number of invoice lines of the document.
   */
  public ParseWorkload (final int nLines)
  {
    m_aData = BenchData.create (nLines, true);
    m_aBytes = m_aData.getBytes ();
  }

  @NonNull
  public Object run () throws Exception
  {
    return BenchData.parse (m_aBytes, "urn:bench:invoice.xml");
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
