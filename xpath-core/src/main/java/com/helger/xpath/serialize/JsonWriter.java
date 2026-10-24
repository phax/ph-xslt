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

import java.util.ArrayDeque;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;

/**
 * The JSON output method (Serialization 3.1 section 9). Nested maps and arrays
 * are processed with an explicit work stack.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class JsonWriter
{
  private record Task (XdmSequence value, int depth)
  {}

  private final SerializationParams m_aParams;
  private final OutputCharset m_aCharset;
  private final StringBuilder m_aSB;

  JsonWriter (@NonNull final SerializationParams aParams,
              @NonNull final OutputCharset aCharset,
              @NonNull final StringBuilder aSB)
  {
    m_aParams = aParams;
    m_aCharset = aCharset;
    m_aSB = aSB;
  }

  /**
   * Append a JSON string literal.
   *
   * @param aSB
   *        Target.
   * @param s
   *        The string.
   * @param aCharset
   *        Output repertoire, character map and normalisation; may be
   *        <code>null</code> for plain UTF-8 without a character map.
   * @param bApplyCharMap
   *        <code>false</code> for text that was already character-mapped.
   */
  static void appendString (@NonNull final StringBuilder aSB,
                            @NonNull final String s,
                            @Nullable final OutputCharset aCharset,
                            final boolean bApplyCharMap)
  {
    aSB.append ('"');
    final OutputCharset.IEscaper aEscaper = (aTarget, sRun) -> {
      for (int i = 0; i < sRun.length ();)
      {
        final int cp = sRun.codePointAt (i);
        i += Character.charCount (cp);
        switch (cp)
        {
          case '"':
            aTarget.append ("\\\"");
            break;
          case '\\':
            aTarget.append ("\\\\");
            break;
          case '/':
            aTarget.append ("\\/");
            break;
          case '\b':
            aTarget.append ("\\b");
            break;
          case '\f':
            aTarget.append ("\\f");
            break;
          case '\n':
            aTarget.append ("\\n");
            break;
          case '\r':
            aTarget.append ("\\r");
            break;
          case '\t':
            aTarget.append ("\\t");
            break;
          default:
            if (cp < 0x20 || cp >= 0x7F && cp <= 0x9F || aCharset != null && !aCharset.canEncode (cp))
            {
              for (final char c : Character.toChars (cp))
                aTarget.append ("\\u").append (String.format (Locale.ROOT, "%04x", Integer.valueOf (c)));
            }
            else
              aTarget.appendCodePoint (cp);
        }
      }
    };
    if (aCharset == null)
      aEscaper.escape (aSB, s);
    else
      if (bApplyCharMap)
        aCharset.mapAndEscape (aSB, s, aEscaper);
      else
        aEscaper.escape (aSB, aCharset.normalize (s));
    aSB.append ('"');
  }

  @NonNull
  private String _getNewline (final int nDepth)
  {
    if (!m_aParams.isIndent ())
      return "";
    final StringBuilder aSB = new StringBuilder (1 + 2 * nDepth).append ('\n');
    for (int i = 0; i < nDepth; ++i)
      aSB.append ("  ");
    return aSB.toString ();
  }

  @NonNull
  private String _serializeNode (@NonNull final XdmNode aNode)
  {
    if (aNode.getKind () == ENodeKind.ATTRIBUTE || aNode.getKind () == ENodeKind.NAMESPACE)
      throw XPathException.of ("SENR0001", "An attribute or namespace node cannot be serialized as JSON");
    final SerializationParams aNodeParams = new SerializationParams (m_aParams);
    aNodeParams.setMethod (m_aParams.getJsonNodeOutputMethod ());
    aNodeParams.setOmitXmlDeclaration (true);
    aNodeParams.setIndent (false);
    aNodeParams.setItemSeparator (null);
    aNodeParams.setByteOrderMark (false);
    return XdmSerializer.serialize (XdmSequence.of (aNode), aNodeParams);
  }

  private void _writeAtomic (@NonNull final XdmAtomic aAtomic)
  {
    final EAtomicType eType = aAtomic.getType ();
    if (eType.isNumeric ())
    {
      final double d = aAtomic.getAsDouble ();
      if ((eType == EAtomicType.DOUBLE || eType == EAtomicType.FLOAT) && (Double.isNaN (d) || Double.isInfinite (d)))
        throw XPathException.of ("SERE0020", "The number " + aAtomic.getLexical () + " cannot be serialized as JSON");
      m_aSB.append (aAtomic.getLexical ());
    }
    else
      if (eType == EAtomicType.BOOLEAN)
        m_aSB.append (aAtomic.getBoolean () ? "true" : "false");
      else
        appendString (m_aSB, aAtomic.getLexical (), m_aCharset, true);
  }

  /**
   * Write a value.
   *
   * @param aValue
   *        The sequence to serialise: empty (null) or a single item.
   */
  void write (@NonNull final XdmSequence aValue)
  {
    final ArrayDeque <Object> aStack = new ArrayDeque <> ();
    aStack.push (new Task (aValue, 0));
    while (!aStack.isEmpty ())
    {
      final Object aTop = aStack.pop ();
      if (aTop instanceof final String sLiteral)
      {
        m_aSB.append (sLiteral);
        continue;
      }
      final Task aTask = (Task) aTop;
      final XdmSequence aSeq = aTask.value ();
      if (aSeq.isEmpty ())
      {
        m_aSB.append ("null");
        continue;
      }
      if (aSeq.size () > 1)
        throw XPathException.of ("SERE0023", "A sequence of " + aSeq.size () + " items cannot be serialized as JSON");
      final IXdmItem aItem = aSeq.get (0);
      final int nDepth = aTask.depth ();
      if (aItem instanceof final XdmMap aMap)
      {
        if (aMap.isEmpty ())
        {
          m_aSB.append ("{}");
          continue;
        }
        m_aSB.append ('{');
        // Push in reverse order
        final ICommonsSet <String> aNames = new CommonsHashSet <> ();
        final var aEntries = aMap.getAllEntries ();
        for (final XdmMap.Entry aEntry : aEntries)
          if (!aNames.add (aEntry.key ().getLexical ()) && !m_aParams.isAllowDuplicateNames ())
            throw XPathException.of ("SERE0022",
                                     "The map has two keys with the string value '" + aEntry.key ().getLexical () + "'");
        aStack.push (_getNewline (nDepth) + "}");
        for (int i = aEntries.size () - 1; i >= 0; --i)
        {
          final XdmMap.Entry aEntry = aEntries.get (i);
          aStack.push (new Task (aEntry.value (), nDepth + 1));
          final StringBuilder aKey = new StringBuilder ();
          if (i > 0)
            aKey.append (',');
          aKey.append (_getNewline (nDepth + 1));
          appendString (aKey, aEntry.key ().getLexical (), m_aCharset, true);
          aKey.append (m_aParams.isIndent () ? ": " : ":");
          aStack.push (aKey.toString ());
        }
      }
      else
        if (aItem instanceof final XdmArray aArray)
        {
          if (aArray.isEmpty ())
          {
            m_aSB.append ("[]");
            continue;
          }
          m_aSB.append ('[');
          aStack.push (_getNewline (nDepth) + "]");
          for (int i = aArray.size () - 1; i >= 0; --i)
          {
            aStack.push (new Task (aArray.get (i), nDepth + 1));
            aStack.push ((i > 0 ? "," : "") + _getNewline (nDepth + 1));
          }
        }
        else
          if (aItem instanceof final XdmNode aNode)
            appendString (m_aSB, _serializeNode (aNode), m_aCharset, false);
          else
            if (aItem instanceof final XdmAtomic aAtomic)
              _writeAtomic (aAtomic);
            else
              throw XPathException.of ("SERE0021", "A function item cannot be serialized as JSON");
    }
  }
}
