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

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.string.XmlWhitespace;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.EIDKind;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * Writes an XDM tree into a W3C DOM. The walk is iterative.
 * <ul>
 * <li>Elements and attributes keep their prefixes. Every element gets an <code>xmlns</code>
 * attribute for each namespace it declares (or undeclares) in XDM that is not bound the same way in
 * scope already, the outermost element written also for every other namespace in scope that the DOM
 * parent does not bind the same way, and any element or attribute whose prefix is not bound to its
 * namespace at that point gets the missing declaration (namespace fixup), so the DOM is namespace
 * well-formed and a serialiser needs no fixup.</li>
 * <li>Attributes of ID kind ID are marked as ID attributes
 * ({@link Element#setIdAttributeNS(String, String, boolean)}).</li>
 * <li>Text, comments and processing instructions are copied. Under a DOM document, which cannot
 * hold text, whitespace-only text between the top-level nodes is dropped.</li>
 * <li>A document node writes its children. A new DOM document gets the document URI of the XDM
 * document, or its base URI if it has no document URI, as <code>documentURI</code>. Other explicit
 * base URIs, unparsed entities, type annotations and disable-output-escaping have no place in the
 * DOM and are not written.</li>
 * </ul>
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmToDom
{
  @PresentForCodeCoverage
  private static final XdmToDom INSTANCE = new XdmToDom ();

  private XdmToDom ()
  {}

  /**
   * One step of the walk: a node to write under a DOM parent, or (with close) an element to close.
   */
  private record Step (@NonNull IXdmNode node, @Nullable Node parent, boolean close)
  {}

  @Nullable
  private static String _nullIfEmpty (@NonNull final String s)
  {
    return s.isEmpty () ? null : s;
  }

  /**
   * @return The prefix bound in the DOM at the node, through
   *         {@link Node#lookupNamespaceURI(String)}: an empty result is the empty string for the
   *         default prefix and unbound otherwise.
   */
  @Nullable
  private static String _lookupInDom (@Nullable final Node aParent, @NonNull final String sPrefix)
  {
    if (aParent == null || aParent.getNodeType () == Node.DOCUMENT_NODE)
      return null;
    final String ret = aParent.lookupNamespaceURI (sPrefix.isEmpty () ? null : sPrefix);
    return ret == null || ret.isEmpty () ? null : ret;
  }

  /**
   * Declare a namespace on a DOM element as an <code>xmlns</code> attribute and bind it in the
   * scope.
   */
  private static void _declare (@NonNull final Element aElement,
                                @NonNull final String sPrefix,
                                @NonNull final String sUri,
                                @NonNull final NamespaceScope aScope)
  {
    aElement.setAttributeNS (XdmConstants.NS_XMLNS, sPrefix.isEmpty () ? "xmlns" : "xmlns:" + sPrefix, sUri);
    aScope.bind (sPrefix, sUri);
  }

  /**
   * Create a DOM element for an XDM element with its namespace declarations and attributes, and
   * open a scope frame for it.
   *
   * @param bOutermost
   *        <code>true</code> for the outermost element written, which also declares the namespaces
   *        it inherits in XDM.
   */
  @NonNull
  private static Element _createElement (@NonNull final IXdmNode aNode,
                                         @NonNull final Document aDoc,
                                         @NonNull final NamespaceScope aScope,
                                         final boolean bOutermost)
  {
    final XdmQName aName = aNode.getName ();
    final Element ret = aDoc.createElementNS (_nullIfEmpty (aName.namespaceUri ()), aName.getLexicalName ());
    aScope.pushFrame ();
    final ICommonsOrderedMap <String, String> aDecls = new CommonsLinkedHashMap <> ();
    if (bOutermost)
    {
      for (final Map.Entry <String, String> aEntry : aNode.getInScopeNamespaces ().entrySet ())
        if (!"xml".equals (aEntry.getKey ()) && !aScope.isBoundTo (aEntry.getKey (), aEntry.getValue ()))
          aDecls.put (aEntry.getKey (), aEntry.getValue ());
    }
    else
      if (aNode.hasDeclaredNamespaces ())
      {
        // Declarations that repeat the binding in scope are not written
        for (final Map.Entry <String, String> aEntry : aNode.getDeclaredNamespaces ().entrySet ())
          if (!aScope.isBoundTo (aEntry.getKey (), aEntry.getValue ()))
            aDecls.put (aEntry.getKey (), aEntry.getValue ());
      }
    aDecls.remove ("xml");
    for (final Map.Entry <String, String> aEntry : aDecls.entrySet ())
      _declare (ret, aEntry.getKey (), aEntry.getValue (), aScope);
    if (!"xml".equals (aName.prefix ()) && !aScope.isBoundTo (aName.prefix (), aName.namespaceUri ()))
      _declare (ret, aName.prefix (), aName.namespaceUri (), aScope);
    for (int i = 0; i < aNode.getAttributeCount (); ++i)
    {
      final IXdmNode aAttr = aNode.getAttributeAtIndex (i);
      final XdmQName aAttrName = aAttr.getName ();
      String sQName = aAttrName.getLexicalName ();
      if (aAttrName.hasNamespace () && !"xml".equals (aAttrName.prefix ()))
      {
        if (!aAttrName.hasPrefix () || !aScope.isBoundTo (aAttrName.prefix (), aAttrName.namespaceUri ()))
        {
          String sPrefix = aAttrName.prefix ();
          if (sPrefix.isEmpty () || aScope.lookup (sPrefix) != null)
            sPrefix = aScope.createPrefix (aDecls);
          _declare (ret, sPrefix, aAttrName.namespaceUri (), aScope);
          aDecls.put (sPrefix, aAttrName.namespaceUri ());
          sQName = sPrefix + ":" + aAttrName.localName ();
        }
      }
      ret.setAttributeNS (_nullIfEmpty (aAttrName.namespaceUri ()), sQName, aAttr.getValue ());
      if (aAttr.getIDKind () == EIDKind.ID)
        ret.setIdAttributeNS (_nullIfEmpty (aAttrName.namespaceUri ()), aAttrName.localName (), true);
    }
    return ret;
  }

  /**
   * @return <code>true</code> if the string consists of XML whitespace only.
   */
  private static boolean _isWhitespace (@NonNull final String s)
  {
    // Unlike XmlWhitespace.isAllXmlWhitespace, the empty string counts as whitespace only
    return s.isEmpty () || XmlWhitespace.isAllXmlWhitespace (s);
  }

  /**
   * Add a new DOM node to its parent, before the next sibling at the top level.
   */
  private static void _add (@NonNull final Node aParent, @NonNull final Node aChild, @Nullable final Node aNextSibling)
  {
    if (aNextSibling != null)
      aParent.insertBefore (aChild, aNextSibling);
    else
      aParent.appendChild (aChild);
  }

  /**
   * Write the XDM node (a document node: its children) under the DOM parent.
   *
   * @param aNode
   *        The XDM node.
   * @param aParent
   *        The DOM parent: a document, document fragment or element.
   * @param aNextSibling
   *        The child of the parent to insert the top-level nodes before, or <code>null</code> to
   *        append them.
   */
  private static void _write (@NonNull final IXdmNode aNode,
                              @NonNull final Node aParent,
                              @Nullable final Node aNextSibling)
  {
    final Document aDoc = aParent instanceof final Document aDocNode ? aDocNode : aParent.getOwnerDocument ();
    final NamespaceScope aScope = new NamespaceScope (s -> _lookupInDom (aParent, s));
    final ArrayDeque <Step> aStack = new ArrayDeque <> ();
    if (aNode.isDocument ())
    {
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.push (new Step (aNode.getChildAtIndex (i), aParent, false));
    }
    else
      aStack.push (new Step (aNode, aParent, false));
    while (!aStack.isEmpty ())
    {
      final Step aStep = aStack.pop ();
      if (aStep.close ())
      {
        aScope.popFrame ();
        continue;
      }
      final IXdmNode aCur = aStep.node ();
      final Node aDomParent = aStep.parent ();
      final boolean bTop = aDomParent == aParent;
      final Node aNextAtLevel = bTop ? aNextSibling : null;
      switch (aCur.getKind ())
      {
        case ELEMENT:
        {
          final Element aElement = _createElement (aCur, aDoc, aScope, bTop);
          _add (aDomParent, aElement, aNextAtLevel);
          aStack.push (new Step (aCur, null, true));
          for (int i = aCur.getChildCount () - 1; i >= 0; --i)
            aStack.push (new Step (aCur.getChildAtIndex (i), aElement, false));
          break;
        }
        case TEXT:
          if (aDomParent.getNodeType () != Node.DOCUMENT_NODE || !_isWhitespace (aCur.getValue ()))
            _add (aDomParent, aDoc.createTextNode (aCur.getValue ()), aNextAtLevel);
          break;
        case COMMENT:
          _add (aDomParent, aDoc.createComment (aCur.getValue ()), aNextAtLevel);
          break;
        case PROCESSING_INSTRUCTION:
          _add (aDomParent,
                aDoc.createProcessingInstruction (aCur.getName ().localName (), aCur.getValue ()),
                aNextAtLevel);
          break;
        case ATTRIBUTE:
        {
          if (!(aDomParent instanceof final Element aElement))
            throw new IllegalArgumentException ("An attribute can only be written to a DOM element");
          final XdmQName aName = aCur.getName ();
          aElement.setAttributeNS (_nullIfEmpty (aName.namespaceUri ()), aName.getLexicalName (), aCur.getValue ());
          break;
        }
        case NAMESPACE:
        {
          if (!(aDomParent instanceof final Element aElement))
            throw new IllegalArgumentException ("A namespace node can only be written to a DOM element");
          final String sPrefix = aCur.getName ().localName ();
          aElement.setAttributeNS (XdmConstants.NS_XMLNS,
                                   sPrefix.isEmpty () ? "xmlns" : "xmlns:" + sPrefix,
                                   aCur.getValue ());
          break;
        }
        case DOCUMENT:
        default:
          throw new IllegalArgumentException ("Unexpected node kind " + aCur.getKind () + " below the top level");
      }
    }
  }

  /**
   * Create a new, empty DOM document with the JDK DOM implementation (namespace aware), never
   * through a JAXP lookup.
   *
   * @return The new document. Never <code>null</code>.
   * @throws IllegalStateException
   *         if the JDK DOM implementation cannot be configured
   */
  @NonNull
  public static Document createEmptyDocument ()
  {
    try
    {
      final DocumentBuilderFactory aFactory = DocumentBuilderFactory.newDefaultInstance ();
      aFactory.setNamespaceAware (true);
      return aFactory.newDocumentBuilder ().newDocument ();
    }
    catch (final ParserConfigurationException ex)
    {
      throw new IllegalStateException ("Failed to create a DOM document", ex);
    }
  }

  /**
   * Write an XDM node into a new DOM document: a document node with its children, any other
   * element, text, comment or processing instruction as the content of the document. The document
   * URI of the XDM document (or its base URI if it has none) becomes the <code>documentURI</code>.
   *
   * @param aNode
   *        The XDM node. May not be <code>null</code>.
   * @return The new DOM document. Never <code>null</code>.
   * @throws org.w3c.dom.DOMException
   *         if the content is not allowed in a DOM document (several elements, non-whitespace text)
   */
  @NonNull
  public static Document toDocument (@NonNull final IXdmNode aNode)
  {
    ValueEnforcer.notNull (aNode, "Node");
    final Document ret = createEmptyDocument ();
    if (aNode.isDocument ())
    {
      final String sUri = aNode.getDocumentUri () != null ? aNode.getDocumentUri () : aNode.getBaseUri ();
      if (sUri != null)
        ret.setDocumentURI (sUri);
    }
    _write (aNode, ret, null);
    return ret;
  }

  /**
   * Write an XDM node into a new DOM document fragment of the given document, which may hold any
   * content (several elements, text): a document node gives its children, any other node itself.
   * Use it for a parentless XDM subtree or a result that is not a well-formed document.
   *
   * @param aNode
   *        The XDM node. May not be <code>null</code>.
   * @param aOwner
   *        The document that owns the fragment. May not be <code>null</code>.
   * @return The new fragment. Never <code>null</code>.
   */
  @NonNull
  public static DocumentFragment toDocumentFragment (@NonNull final IXdmNode aNode, @NonNull final Document aOwner)
  {
    ValueEnforcer.notNull (aNode, "Node");
    ValueEnforcer.notNull (aOwner, "Owner");
    final DocumentFragment ret = aOwner.createDocumentFragment ();
    _write (aNode, ret, null);
    return ret;
  }

  /**
   * Write an XDM node into an existing DOM node: a document node gives its children, an attribute
   * or namespace node is set on the DOM element, any other node is added as a child. The outermost
   * elements written declare the namespaces they have in scope in XDM that the DOM parent does not
   * already bind the same way.
   *
   * @param aNode
   *        The XDM node. May not be <code>null</code>.
   * @param aParent
   *        The DOM document, document fragment or element to write to. May not be
   *        <code>null</code>.
   * @param aNextSibling
   *        A child of the parent to insert the new nodes before, or <code>null</code> to append
   *        them.
   * @throws IllegalArgumentException
   *         if the parent is of another kind, or an attribute or namespace node is to be written to
   *         a non-element
   * @throws org.w3c.dom.DOMException
   *         if the content is not allowed in the parent
   */
  public static void appendTo (@NonNull final IXdmNode aNode,
                               @NonNull final Node aParent,
                               @Nullable final Node aNextSibling)
  {
    ValueEnforcer.notNull (aNode, "Node");
    ValueEnforcer.notNull (aParent, "Parent");
    final short nType = aParent.getNodeType ();
    if (nType != Node.DOCUMENT_NODE && nType != Node.DOCUMENT_FRAGMENT_NODE && nType != Node.ELEMENT_NODE)
      throw new IllegalArgumentException ("XDM nodes can only be written to a DOM document, document fragment or element, not to node type " +
                                          nType);
    if (aNextSibling != null && aNextSibling.getParentNode () != aParent)
      throw new IllegalArgumentException ("The next sibling is not a child of the parent");
    if (aNode.getKind () == ENodeKind.DOCUMENT && nType == Node.DOCUMENT_NODE && aNode.getDocumentUri () != null)
    {
      final Document aDoc = (Document) aParent;
      if (aDoc.getDocumentURI () == null)
        aDoc.setDocumentURI (aNode.getDocumentUri ());
    }
    _write (aNode, aParent, aNextSibling);
  }
}
