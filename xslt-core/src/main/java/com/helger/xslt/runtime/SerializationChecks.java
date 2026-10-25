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
package com.helger.xslt.runtime;

import java.math.BigDecimal;
import java.util.ArrayDeque;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.XdmNode;

/**
 * Serialization errors that depend on the result tree and that the serializer
 * does not detect itself (a port of go-xslt's checkSerializationErrors):
 * SEPM0004, SESU0013 for the html method, SERE0014 and SERE0015.
 *
 * @author Philip Helger
 */
@Immutable
public final class SerializationChecks
{
  @PresentForCodeCoverage
  private static final SerializationChecks INSTANCE = new SerializationChecks ();

  private SerializationChecks ()
  {}

  private static boolean _isHtml5 (@NonNull final SerializationParams aParams)
  {
    BigDecimal aVersion = aParams.getHtmlVersion ();
    if (aVersion == null && aParams.getVersion () != null)
      try
      {
        aVersion = new BigDecimal (aParams.getVersion ().trim ());
      }
      catch (final NumberFormatException ex)
      {
        return false;
      }
    return aVersion == null || aVersion.compareTo (BigDecimal.valueOf (5)) >= 0;
  }

  /**
   * @param aParams
   *        The effective parameters.
   * @param aTree
   *        The result tree or <code>null</code>.
   * @throws XPathException
   *         with the serialization error code
   */
  public static void check (@NonNull final SerializationParams aParams, @Nullable final XdmNode aTree)
  {
    final String sMethod = aParams.getMethod ();
    final boolean bXml = SerializationParams.METHOD_XML.equals (sMethod) || SerializationParams.METHOD_XHTML.equals (sMethod);
    final boolean bHtml = SerializationParams.METHOD_HTML.equals (sMethod);
    if (bHtml && aParams.getVersion () != null)
    {
      boolean bOk;
      try
      {
        bOk = new BigDecimal (aParams.getVersion ().trim ()).compareTo (BigDecimal.ONE) >= 0;
      }
      catch (final NumberFormatException ex)
      {
        bOk = false;
      }
      if (!bOk)
        throw new XPathException ("SESU0013", "Unsupported HTML version '" + aParams.getVersion () + "'");
    }
    if (aTree == null)
      return;
    if (bXml && (!SerializationParams.STANDALONE_OMIT.equals (aParams.getStandalone ()) || aParams.getDoctypeSystem () != null))
    {
      int nElements = 0;
      for (int i = 0; i < aTree.getChildCount (); ++i)
        if (aTree.getChildAtIndex (i).isElement ())
          nElements++;
      if (nElements > 1)
        throw new XPathException ("SEPM0004",
                                  "A result with " + nElements + " top-level elements cannot be serialized with standalone or doctype-system");
    }
    if (bHtml)
    {
      final boolean bCheckC1 = !_isHtml5 (aParams);
      final ArrayDeque <XdmNode> aStack = new ArrayDeque <> ();
      aStack.push (aTree);
      while (!aStack.isEmpty ())
      {
        final XdmNode aCur = aStack.pop ();
        if (aCur.getKind () == ENodeKind.PROCESSING_INSTRUCTION && aCur.getValue ().indexOf ('>') >= 0)
          throw new XPathException ("SERE0015", "The html output method cannot write '>' in a processing instruction");
        if (bCheckC1)
        {
          if (aCur.isText () && _hasC1 (aCur.getValue ()))
            throw new XPathException ("SERE0014", "The html output method cannot write a character in #x7F-#x9F");
          for (int i = 0; i < aCur.getAttributeCount (); ++i)
            if (_hasC1 (aCur.getAttributeAtIndex (i).getValue ()))
              throw new XPathException ("SERE0014", "The html output method cannot write a character in #x7F-#x9F");
        }
        for (int i = 0; i < aCur.getChildCount (); ++i)
          aStack.push (aCur.getChildAtIndex (i));
      }
    }
  }

  private static boolean _hasC1 (@NonNull final String s)
  {
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      if (c >= 0x7F && c <= 0x9F)
        return true;
    }
    return false;
  }
}
