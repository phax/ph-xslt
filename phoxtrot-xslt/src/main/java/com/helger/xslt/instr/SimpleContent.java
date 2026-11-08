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
package com.helger.xslt.instr;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Constructing simple content (XSLT 3.0 section 5.7.2), as for the value of
 * xsl:attribute, xsl:value-of, xsl:comment, ...: zero-length text nodes are
 * dropped, adjacent text nodes merged, every other item atomized, and the
 * string values joined with the separator.
 *
 * @author Philip Helger
 */
@Immutable
public final class SimpleContent
{
  @PresentForCodeCoverage
  private static final SimpleContent INSTANCE = new SimpleContent ();

  private SimpleContent ()
  {}

  /**
   * @param aSeq
   *        The result of the select expression or sequence constructor.
   * @param sSeparator
   *        The separator.
   * @return The string value.
   */
  @NonNull
  public static String join (@NonNull final XdmSequence aSeq, @NonNull final String sSeparator)
  {
    final StringBuilder aSB = new StringBuilder ();
    boolean bFirst = true;
    boolean bLastText = false;
    for (final IXdmItem aItem : aSeq)
    {
      if (aItem instanceof final IXdmNode aNode && aNode.isText ())
      {
        final String s = aNode.getValue ();
        if (s.isEmpty ())
          continue;
        if (!bLastText)
        {
          if (!bFirst)
            aSB.append (sSeparator);
          bFirst = false;
        }
        aSB.append (s);
        bLastText = true;
        continue;
      }
      bLastText = false;
      for (final IXdmItem aAtom : Atomizer.atomize (XdmSequence.of (aItem)))
      {
        if (!bFirst)
          aSB.append (sSeparator);
        aSB.append (Atomizer.getStringValue (aAtom));
        bFirst = false;
      }
    }
    return aSB.toString ();
  }
}
