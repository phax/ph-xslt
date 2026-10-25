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
package com.helger.xpath.xdm.schema;

import java.util.ArrayDeque;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Writes an element of an XDM tree, with its subtree, as namespace-aware SAX
 * events wrapped in a document, e.g. to validate it. The walk is iterative.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmSaxEmitter
{
  @PresentForCodeCoverage
  private static final XdmSaxEmitter INSTANCE = new XdmSaxEmitter ();

  private XdmSaxEmitter ()
  {}

  @NonNull
  private static String _qname (@NonNull final XdmQName aName)
  {
    final String sPrefix = aName.prefix ();
    return sPrefix == null || sPrefix.isEmpty () ? aName.localName () : sPrefix + ":" + aName.localName ();
  }

  private static void _startElement (@NonNull final XdmNode aElement,
                                     @NonNull final Map <String, String> aNamespaces,
                                     @NonNull final ContentHandler aHandler) throws SAXException
  {
    for (final Map.Entry <String, String> aEntry : aNamespaces.entrySet ())
      if (!"xml".equals (aEntry.getKey ()))
        aHandler.startPrefixMapping (aEntry.getKey (), aEntry.getValue ());
    final AttributesImpl aAttrs = new AttributesImpl ();
    for (final XdmNode aAttr : aElement.getAllAttributes ())
    {
      final XdmQName aName = aAttr.getName ();
      aAttrs.addAttribute (aName.namespaceUri (), aName.localName (), _qname (aName), "CDATA", aAttr.getValue ());
    }
    final XdmQName aName = aElement.getName ();
    aHandler.startElement (aName.namespaceUri (), aName.localName (), _qname (aName), aAttrs);
  }

  private static void _endElement (@NonNull final XdmNode aElement,
                                   @NonNull final Map <String, String> aNamespaces,
                                   @NonNull final ContentHandler aHandler) throws SAXException
  {
    final XdmQName aName = aElement.getName ();
    aHandler.endElement (aName.namespaceUri (), aName.localName (), _qname (aName));
    for (final String sPrefix : aNamespaces.keySet ())
      if (!"xml".equals (sPrefix))
        aHandler.endPrefixMapping (sPrefix);
  }

  /**
   * @param aElement
   *        The element to write. Its in-scope namespaces are declared on it.
   * @param aHandler
   *        The receiver of the content events.
   * @param aLexicalHandler
   *        The receiver of comments, may be <code>null</code>.
   * @throws SAXException
   *         from the handlers
   */
  public static void emitElement (@NonNull final XdmNode aElement,
                                  @NonNull final ContentHandler aHandler,
                                  @Nullable final LexicalHandler aLexicalHandler) throws SAXException
  {
    if (!aElement.isElement ())
      throw new IllegalArgumentException ("Not an element: " + aElement);
    aHandler.startDocument ();
    // Stack entries: a node to open, or (with bClose) an element to close
    record Entry (@NonNull XdmNode node, boolean close, @Nullable Map <String, String> namespaces)
    {}
    final ArrayDeque <Entry> aStack = new ArrayDeque <> ();
    aStack.push (new Entry (aElement, false, null));
    while (!aStack.isEmpty ())
    {
      final Entry aCur = aStack.pop ();
      final XdmNode aNode = aCur.node ();
      if (aCur.close ())
      {
        _endElement (aNode, aCur.namespaces (), aHandler);
        continue;
      }
      switch (aNode.getKind ())
      {
        case ELEMENT:
        {
          final ICommonsOrderedMap <String, String> aNamespaces = aNode == aElement ? aNode.getInScopeNamespaces ()
                                                                                    : aNode.getDeclaredNamespaces ();
          _startElement (aNode, aNamespaces, aHandler);
          aStack.push (new Entry (aNode, true, aNamespaces));
          for (int i = aNode.getChildCount () - 1; i >= 0; --i)
            aStack.push (new Entry (aNode.getChildAtIndex (i), false, null));
          break;
        }
        case TEXT:
        {
          final char [] aChars = aNode.getValue ().toCharArray ();
          aHandler.characters (aChars, 0, aChars.length);
          break;
        }
        case COMMENT:
          if (aLexicalHandler != null)
          {
            final char [] aChars = aNode.getValue ().toCharArray ();
            aLexicalHandler.comment (aChars, 0, aChars.length);
          }
          break;
        case PROCESSING_INSTRUCTION:
          aHandler.processingInstruction (aNode.getName ().localName (), aNode.getValue ());
          break;
        default:
          break;
      }
    }
    aHandler.endDocument ();
  }
}
