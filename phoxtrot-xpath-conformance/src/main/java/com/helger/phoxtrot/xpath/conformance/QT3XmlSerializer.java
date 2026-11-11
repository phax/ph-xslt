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
package com.helger.phoxtrot.xpath.conformance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * A small, iterative XML serializer for harness messages only (fail dumps).
 * It is not the engine's serializer and is never used to decide a verdict.
 *
 * @author Philip Helger
 */
@Immutable
public final class QT3XmlSerializer
{
  private record Frame (@NonNull IXdmNode node, boolean closing)
  {}

  @PresentForCodeCoverage
  private static final QT3XmlSerializer INSTANCE = new QT3XmlSerializer ();

  private QT3XmlSerializer ()
  {}

  /**
   * Append text with <code>&amp;</code>, <code>&lt;</code> and
   * <code>&gt;</code> escaped, and in attribute values also the double quote.
   * Other characters (including CR, tab and line feed in attributes) are
   * appended as they are.
   *
   * @param aSB
   *        The target.
   * @param s
   *        The text; <code>null</code> appends nothing.
   * @param bAttr
   *        <code>true</code> for an attribute value.
   */
  private static void _escape (@NonNull final StringBuilder aSB, @Nullable final String s, final boolean bAttr)
  {
    if (s == null)
      return;
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      switch (c)
      {
        case '&':
          aSB.append ("&amp;");
          break;
        case '<':
          aSB.append ("&lt;");
          break;
        case '>':
          aSB.append ("&gt;");
          break;
        case '"':
          aSB.append (bAttr ? "&quot;" : "\"");
          break;
        default:
          aSB.append (c);
          break;
      }
    }
  }

  @NonNull
  private static String _name (@Nullable final XdmQName aName)
  {
    return aName == null ? "" : aName.getLexicalName ();
  }

  /**
   * Append the start tag of an element with its lexical name, the namespace
   * declarations made on the element itself (not the inherited ones) and its
   * attributes; an element without children is written as an empty-element
   * tag.
   *
   * @param aSB
   *        The target.
   * @param aElement
   *        The element.
   */
  private static void _startTag (@NonNull final StringBuilder aSB, @NonNull final IXdmNode aElement)
  {
    aSB.append ('<').append (_name (aElement.getName ()));
    for (final Map.Entry <String, String> aEntry : aElement.declaredNamespaces ().entrySet ())
    {
      aSB.append (aEntry.getKey ().isEmpty () ? " xmlns" : " xmlns:" + aEntry.getKey ()).append ("=\"");
      _escape (aSB, aEntry.getValue (), true);
      aSB.append ('"');
    }
    for (final IXdmNode aAttr : aElement.getAllAttributes ())
    {
      aSB.append (' ').append (_name (aAttr.getName ())).append ("=\"");
      _escape (aSB, aAttr.getValue (), true);
      aSB.append ('"');
    }
    aSB.append (aElement.getChildCount () == 0 ? "/>" : ">");
  }

  /**
   * @param aNode
   *        The node to serialise.
   * @return Its XML form, built with an explicit stack so deep trees cannot
   *         overflow: a document node contributes only its children, an
   *         attribute or namespace node is written as
   *         <code>name="value"</code>, comments and processing instructions
   *         are not escaped.
   */
  @NonNull
  public static String serialize (@NonNull final IXdmNode aNode)
  {
    final StringBuilder aSB = new StringBuilder ();
    final Deque <Frame> aStack = new ArrayDeque <> ();
    aStack.push (new Frame (aNode, false));
    while (!aStack.isEmpty ())
    {
      final Frame aFrame = aStack.pop ();
      final IXdmNode aCur = aFrame.node ();
      if (aFrame.closing ())
      {
        aSB.append ("</").append (_name (aCur.getName ())).append ('>');
        continue;
      }
      switch (aCur.getKind ())
      {
        case DOCUMENT:
        case ELEMENT:
          if (aCur.isElement ())
          {
            _startTag (aSB, aCur);
            if (aCur.getChildCount () > 0)
              aStack.push (new Frame (aCur, true));
          }
          for (int i = aCur.getChildCount () - 1; i >= 0; --i)
            aStack.push (new Frame (aCur.getChildAtIndex (i), false));
          break;
        case ATTRIBUTE:
          aSB.append (_name (aCur.getName ())).append ("=\"");
          _escape (aSB, aCur.getValue (), true);
          aSB.append ('"');
          break;
        case TEXT:
          _escape (aSB, aCur.getValue (), false);
          break;
        case COMMENT:
          aSB.append ("<!--").append (aCur.getValue ()).append ("-->");
          break;
        case PROCESSING_INSTRUCTION:
          aSB.append ("<?").append (_name (aCur.getName ()));
          if (aCur.getValue () != null && !aCur.getValue ().isEmpty ())
            aSB.append (' ').append (aCur.getValue ());
          aSB.append ("?>");
          break;
        case NAMESPACE:
          aSB.append ("xmlns:").append (_name (aCur.getName ())).append ("=\"");
          _escape (aSB, aCur.getValue (), true);
          aSB.append ('"');
          break;
        default:
          break;
      }
    }
    return aSB.toString ();
  }

  /**
   * @param aItem
   *        Any item.
   * @return A short description for messages: nodes serialised, atomic values
   *         as <code>type(payload)</code>, other items by class name.
   */
  @NonNull
  public static String describe (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final IXdmNode aNode)
      return serialize (aNode);
    if (aItem instanceof final XdmAtomic aAtomic)
      return aAtomic.getType ().getQualifiedName () + "(" + aAtomic.getValue () + ")";
    return aItem.getClass ().getSimpleName ();
  }

  /**
   * @param aSeq
   *        A sequence.
   * @param nMaxLen
   *        Maximum length of the description; longer ones are cut.
   * @return A short description like <code>(a, b)</code>.
   */
  @NonNull
  public static String describe (@NonNull final XdmSequence aSeq, final int nMaxLen)
  {
    final StringBuilder aSB = new StringBuilder ("(");
    for (int i = 0; i < aSeq.size () && aSB.length () < nMaxLen; ++i)
    {
      if (i > 0)
        aSB.append (", ");
      aSB.append (describe (aSeq.get (i)));
    }
    aSB.append (')');
    return aSB.length () > nMaxLen ? aSB.substring (0, nMaxLen) + "..." : aSB.toString ();
  }
}
