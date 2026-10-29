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
package com.helger.xpath.serialize;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.fn.FnArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Serializes XDM sequences (Serialization 3.1) with the output methods xml,
 * xhtml, html, text, json and adaptive. The result is a Java string; the
 * encoding parameter only decides which characters are written as character
 * references or escapes.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmSerializer
{
  @PresentForCodeCoverage
  private static final XdmSerializer INSTANCE = new XdmSerializer ();

  private XdmSerializer ()
  {}

  private static void _checkParams (@NonNull final SerializationParams aParams)
  {
    final String sMethod = aParams.getMethod ();
    final boolean bXmlLike = SerializationParams.METHOD_XML.equals (sMethod) ||
                             SerializationParams.METHOD_XHTML.equals (sMethod);
    final String sVersion = aParams.getVersion ();
    if (bXmlLike && sVersion != null && !"1.0".equals (sVersion) && !"1.1".equals (sVersion))
      throw XPathException.of ("SESU0013", "Unsupported XML version '" + sVersion + "'");
    if (bXmlLike && aParams.isOmitXmlDeclaration ())
    {
      if (!SerializationParams.STANDALONE_OMIT.equals (aParams.getStandalone ()))
        throw XPathException.of ("SEPM0009", "standalone requires an XML declaration");
      if (sVersion != null && !"1.0".equals (sVersion) && aParams.getDoctypeSystem () != null)
        throw XPathException.of ("SEPM0009", "XML version " + sVersion + " with doctype-system requires an XML declaration");
    }
    if (bXmlLike && aParams.isUndeclarePrefixes () && !aParams.isXml11 ())
      throw XPathException.of ("SEPM0010", "undeclare-prefixes requires XML version 1.1");
  }

  private static void _flushText (@NonNull final StringBuilder aText, @NonNull final ICommonsList <Object> aOut)
  {
    if (aText.length () > 0)
      aOut.add (aText.toString ());
    aText.setLength (0);
  }

  /**
   * Sequence normalisation (Serialization 3.1 section 2) for the xml, xhtml,
   * html and text methods.
   *
   * @param aSeq
   *        The input sequence.
   * @param sItemSeparator
   *        The item-separator or <code>null</code>.
   * @return The children of the normalised document: nodes (element, text,
   *         comment, processing instruction) and strings for merged text.
   */
  @NonNull
  static ICommonsList <Object> normalize (@NonNull final XdmSequence aSeq, @Nullable final String sItemSeparator)
  {
    final XdmSequence aFlat = aSeq.isEmpty () ? XdmSequence.of (XdmAtomic.string ("")) : FnArray.flatten (aSeq);
    final ICommonsList <Object> ret = new CommonsArrayList <> ();
    final StringBuilder aText = new StringBuilder ();
    boolean bPrevString = false;
    for (int i = 0; i < aFlat.size (); ++i)
    {
      final IXdmItem aItem = aFlat.get (i);
      if (i > 0)
      {
        if (sItemSeparator != null)
          aText.append (sItemSeparator);
        else
          if (bPrevString && aItem instanceof XdmAtomic)
            aText.append (' ');
      }
      bPrevString = false;
      if (aItem instanceof final XdmAtomic aAtomic)
      {
        aText.append (aAtomic.getLexical ());
        bPrevString = true;
      }
      else
        if (aItem instanceof final IXdmNode aNode)
        {
          switch (aNode.getKind ())
          {
            case ATTRIBUTE:
            case NAMESPACE:
              throw XPathException.of ("SENR0001",
                                       "An attribute or namespace node cannot be serialized on its own");
            case TEXT:
              if (aNode.isDisableOutputEscaping ())
              {
                // Kept as a node, so that the writer does not escape it
                _flushText (aText, ret);
                ret.add (aNode);
              }
              else
                aText.append (aNode.getValue ());
              break;
            case DOCUMENT:
              for (final IXdmNode aChild : aNode.getAllChildren ())
              {
                if (aChild.getKind () == ENodeKind.TEXT && !aChild.isDisableOutputEscaping ())
                  aText.append (aChild.getValue ());
                else
                {
                  _flushText (aText, ret);
                  ret.add (aChild);
                }
              }
              break;
            default:
              _flushText (aText, ret);
              ret.add (aNode);
              break;
          }
        }
        else
          throw XPathException.of ("SENR0001", "A function item cannot be serialized with this output method");
    }
    _flushText (aText, ret);
    return ret;
  }

  @NonNull
  private static String _serializeText (@NonNull final ICommonsList <Object> aItems,
                                        @NonNull final OutputCharset aCharset)
  {
    final StringBuilder aRaw = new StringBuilder ();
    for (final Object aItem : aItems)
      if (aItem instanceof final String sText)
        aRaw.append (sText);
      else
      {
        final IXdmNode aNode = (IXdmNode) aItem;
        if (aNode.getKind () == ENodeKind.ELEMENT || aNode.getKind () == ENodeKind.TEXT)
          aRaw.append (aNode.getStringValue ());
      }
    final StringBuilder ret = new StringBuilder (aRaw.length ());
    aCharset.mapAndEscape (ret, aRaw.toString (), (aTarget, sRun) -> {
      for (int i = 0; i < sRun.length ();)
      {
        final int cp = sRun.codePointAt (i);
        i += Character.charCount (cp);
        if (!aCharset.canEncode (cp))
          throw XPathException.of ("SERE0008",
                                   "The character U+" +
                                                Integer.toHexString (cp) +
                                                " cannot be represented in the output encoding");
        aTarget.appendCodePoint (cp);
      }
    });
    return ret.toString ();
  }

  /**
   * Append a JSON string literal with the escaping of the JSON output method
   * (also used by fn:xml-to-json).
   *
   * @param aSB
   *        Target.
   * @param s
   *        The string.
   */
  public static void appendJsonString (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    JsonWriter.appendString (aSB, s, null, false);
  }

  /**
   * Serialize a sequence.
   *
   * @param aSeq
   *        The sequence to serialize.
   * @param aParams
   *        The serialization parameters.
   * @return The serialized form.
   * @throws XPathException
   *         with a serialization error code (SENR, SEPM, SERE, SESU).
   */
  @NonNull
  public static String serialize (@NonNull final XdmSequence aSeq, @NonNull final SerializationParams aParams)
  {
    ValueEnforcer.notNull (aSeq, "Seq");
    ValueEnforcer.notNull (aParams, "Params");
    _checkParams (aParams);
    final OutputCharset aCharset = new OutputCharset (aParams);
    final StringBuilder aSB = new StringBuilder ();
    if (aParams.isByteOrderMark ())
      aSB.append ('﻿');
    final String sMethod = aParams.getMethod ();
    switch (sMethod)
    {
      case SerializationParams.METHOD_JSON:
        new JsonWriter (aParams, aCharset, aSB).write (aSeq);
        break;
      case SerializationParams.METHOD_ADAPTIVE:
        new AdaptiveWriter (aParams, aCharset, aSB).write (aSeq);
        break;
      case SerializationParams.METHOD_TEXT:
        aSB.append (_serializeText (normalize (aSeq, aParams.getItemSeparator ()), aCharset));
        break;
      case SerializationParams.METHOD_XML:
      case SerializationParams.METHOD_XHTML:
      case SerializationParams.METHOD_HTML:
        new MarkupWriter (aParams, aCharset, aSB, sMethod).write (normalize (aSeq, aParams.getItemSeparator ()));
        break;
      default:
        throw XPathException.of ("SEPM0016", "Unsupported serialization method '" + sMethod + "'");
    }
    return aSB.toString ();
  }
}
