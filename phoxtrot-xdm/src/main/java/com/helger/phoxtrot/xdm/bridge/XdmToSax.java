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
package com.helger.phoxtrot.xdm.bridge;

import java.util.ArrayDeque;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * Writes an XDM tree as namespace-aware SAX events, wrapped in
 * <code>startDocument</code>/<code>endDocument</code>: a document node with all its children
 * (comments and processing instructions outside the document element included), or an element,
 * text, comment or processing instruction as the only content. The walk is iterative.
 * <ul>
 * <li>Each element reports a <code>startPrefixMapping</code> for every namespace it declares that
 * is not bound the same way in scope already (the outermost elements: every namespace in scope
 * except <code>xml</code>) and for every binding its names need that is not in scope (namespace
 * fixup, a namespaced attribute without usable prefix gets a new one);
 * <code>endPrefixMapping</code> follows its end tag. No <code>xmlns</code> attributes are
 * reported.</li>
 * <li>Attributes have the type <code>ID</code>, <code>IDREF</code> or <code>IDREFS</code> according
 * to their ID kind, <code>CDATA</code> otherwise.</li>
 * <li>Comments go to the lexical handler, if one is given.</li>
 * <li>A locator with the document URI (or base URI) of the tree as system ID is passed to
 * <code>setDocumentLocator</code>; it has no line numbers.</li>
 * </ul>
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmToSax
{
  @PresentForCodeCoverage
  private static final XdmToSax INSTANCE = new XdmToSax ();

  private XdmToSax ()
  {}

  /** One step of the walk: a node to open, or (with close) an element to close. */
  private record Step (@NonNull IXdmNode node, boolean close, @Nullable ICommonsList <String> prefixes)
  {}

  @NonNull
  private static String _getSaxType (@NonNull final IXdmNode aAttr)
  {
    return switch (aAttr.getIDKind ())
    {
      case ID -> "ID";
      case IDREF -> "IDREF";
      case IDREFS -> "IDREFS";
      default -> "CDATA";
    };
  }

  /**
   * Report the start of an element: the prefix mappings it declares and needs, then the start tag,
   * and open a scope frame.
   *
   * @return The prefixes mapped, for the end of the element.
   */
  @NonNull
  private static ICommonsList <String> _startElement (@NonNull final IXdmNode aElement,
                                                      final boolean bOutermost,
                                                      @NonNull final NamespaceScope aScope,
                                                      @NonNull final ContentHandler aHandler) throws SAXException
  {
    final ICommonsOrderedMap <String, String> aDecls = new CommonsLinkedHashMap <> ();
    aScope.pushFrame ();
    if (bOutermost)
      aDecls.putAll (aElement.getInScopeNamespaces ());
    else
      if (aElement.hasDeclaredNamespaces ())
      {
        // Declarations that repeat the binding in scope are not reported
        for (final Map.Entry <String, String> aEntry : aElement.declaredNamespaces ().entrySet ())
          if (!aScope.isBoundTo (aEntry.getKey (), aEntry.getValue ()))
            aDecls.put (aEntry.getKey (), aEntry.getValue ());
      }
    aDecls.remove ("xml");
    for (final Map.Entry <String, String> aEntry : aDecls.entrySet ())
      aScope.bind (aEntry.getKey (), aEntry.getValue ());
    final XdmQName aName = aElement.getName ();
    if (!"xml".equals (aName.prefix ()) && !aScope.isBoundTo (aName.prefix (), aName.namespaceUri ()))
    {
      aDecls.put (aName.prefix (), aName.namespaceUri ());
      aScope.bind (aName.prefix (), aName.namespaceUri ());
    }
    final AttributesImpl aAttrs = new AttributesImpl ();
    for (int i = 0; i < aElement.getAttributeCount (); ++i)
    {
      final IXdmNode aAttr = aElement.getAttributeAtIndex (i);
      final XdmQName aAttrName = aAttr.getName ();
      String sQName = aAttrName.getLexicalName ();
      if (aAttrName.hasNamespace () &&
          !"xml".equals (aAttrName.prefix ()) &&
          (!aAttrName.hasPrefix () || !aScope.isBoundTo (aAttrName.prefix (), aAttrName.namespaceUri ())))
      {
        String sPrefix = aAttrName.prefix ();
        if (sPrefix.isEmpty () || aScope.lookup (sPrefix) != null)
          sPrefix = aScope.createPrefix (aDecls);
        aDecls.put (sPrefix, aAttrName.namespaceUri ());
        aScope.bind (sPrefix, aAttrName.namespaceUri ());
        sQName = sPrefix + ":" + aAttrName.localName ();
      }
      aAttrs.addAttribute (aAttrName.namespaceUri (),
                           aAttrName.localName (),
                           sQName,
                           _getSaxType (aAttr),
                           aAttr.getValue ());
    }
    for (final Map.Entry <String, String> aEntry : aDecls.entrySet ())
      aHandler.startPrefixMapping (aEntry.getKey (), aEntry.getValue ());
    aHandler.startElement (aName.namespaceUri (), aName.localName (), aName.getLexicalName (), aAttrs);
    return new CommonsArrayList <> (aDecls.keySet ());
  }

  /**
   * Write an XDM node as a SAX document.
   *
   * @param aNode
   *        A document, element, text, comment or processing instruction node. May not be
   *        <code>null</code>.
   * @param aHandler
   *        The receiver of the content events. May not be <code>null</code>.
   * @param aLexicalHandler
   *        The receiver of comments, may be <code>null</code> to drop them.
   * @throws SAXException
   *         from the handlers
   * @throws IllegalArgumentException
   *         for an attribute or namespace node
   */
  public static void emit (@NonNull final IXdmNode aNode,
                           @NonNull final ContentHandler aHandler,
                           @Nullable final LexicalHandler aLexicalHandler) throws SAXException
  {
    ValueEnforcer.notNull (aNode, "Node");
    ValueEnforcer.notNull (aHandler, "Handler");
    if (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE)
      throw new IllegalArgumentException ("An attribute or namespace node cannot be written as a SAX document");
    final IXdmNode aRoot = aNode.getRoot ();
    final LocatorImpl aLocator = new LocatorImpl ();
    aLocator.setSystemId (aRoot.getDocumentUri () != null ? aRoot.getDocumentUri () : aRoot.getBaseUri ());
    aHandler.setDocumentLocator (aLocator);
    aHandler.startDocument ();
    final NamespaceScope aScope = new NamespaceScope (null);
    final ArrayDeque <Step> aStack = new ArrayDeque <> ();
    if (aNode.isDocument ())
    {
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.push (new Step (aNode.getChildAtIndex (i), false, null));
    }
    else
      aStack.push (new Step (aNode, false, null));
    int nOpen = 0;
    while (!aStack.isEmpty ())
    {
      final Step aStep = aStack.pop ();
      final IXdmNode aCur = aStep.node ();
      if (aStep.close ())
      {
        final XdmQName aName = aCur.getName ();
        aHandler.endElement (aName.namespaceUri (), aName.localName (), aName.getLexicalName ());
        for (final String sPrefix : aStep.prefixes ())
          aHandler.endPrefixMapping (sPrefix);
        aScope.popFrame ();
        nOpen--;
        continue;
      }
      switch (aCur.getKind ())
      {
        case ELEMENT:
        {
          final ICommonsList <String> aPrefixes = _startElement (aCur, nOpen == 0, aScope, aHandler);
          nOpen++;
          aStack.push (new Step (aCur, true, aPrefixes));
          for (int i = aCur.getChildCount () - 1; i >= 0; --i)
            aStack.push (new Step (aCur.getChildAtIndex (i), false, null));
          break;
        }
        case TEXT:
        {
          final char [] aChars = aCur.getValue ().toCharArray ();
          aHandler.characters (aChars, 0, aChars.length);
          break;
        }
        case COMMENT:
          if (aLexicalHandler != null)
          {
            final char [] aChars = aCur.getValue ().toCharArray ();
            aLexicalHandler.comment (aChars, 0, aChars.length);
          }
          break;
        case PROCESSING_INSTRUCTION:
          aHandler.processingInstruction (aCur.getName ().localName (), aCur.getValue ());
          break;
        default:
          break;
      }
    }
    aHandler.endDocument ();
  }
}
