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
import org.w3c.dom.Attr;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Entity;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.w3c.dom.TypeInfo;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.wrapper.Wrapper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmTreeBuilder;

/**
 * Builds an XDM tree from a W3C DOM tree without serialising it: the DOM is
 * walked (iteratively) and fed into an {@link XdmTreeBuilder} as SAX events,
 * so that the tree is numbered in document order while it is built and
 * shares its names like a parsed document.
 * <ul>
 * <li>Namespaces: the <code>xmlns</code> attributes of the DOM become the
 * namespace declarations; a binding that the names of an element or its
 * attributes need but no attribute declares (a DOM built with
 * <code>createElementNS</code>) is declared on the element, and a namespaced
 * attribute without a usable prefix gets a new one (<code>ns0</code>, ...).
 * DOM level 1 nodes (no local name) are resolved through the
 * <code>xmlns</code> attributes in scope.</li>
 * <li>Text and CDATA sections become text (adjacent ones merge); whitespace
 * that the DOM marks as element content whitespace is dropped, as the parser
 * drops ignorable whitespace.</li>
 * <li>Entity references are expanded (their children are taken); the content
 * of an external entity gets the entity's base URI.</li>
 * <li>Attributes the DOM knows as DTD ID, IDREF or IDREFS (through
 * {@link Attr#isId()} or their DTD type info) keep that ID kind.</li>
 * <li>Unparsed entities of the document type become unparsed entities of the
 * document; the names of the parsed entities are recorded.</li>
 * <li>The document URI is the system ID passed, or else the
 * <code>documentURI</code> of the DOM document.</li>
 * </ul>
 *
 * @author Philip Helger
 */
@Immutable
public final class DomToXdm
{
  /** The namespace of the DTD types in DOM type info. */
  private static final String DTD_TYPE_NAMESPACE = "http://www.w3.org/TR/REC-xml";

  @PresentForCodeCoverage
  private static final DomToXdm INSTANCE = new DomToXdm ();

  private DomToXdm ()
  {}

  /** One step of the walk: a node to open, or an element or entity reference to close. */
  private record Step (@NonNull Node node, boolean close, @Nullable String restoreSystemId)
  {}

  /** The resolved name of a DOM element or attribute. */
  private record Name (@NonNull String uri, @NonNull String local, @NonNull String prefix)
  {
    @NonNull
    String getQName ()
    {
      return prefix.isEmpty () ? local : prefix + ":" + local;
    }
  }

  /**
   * @param s
   *        A DOM string that may be <code>null</code>.
   * @return The string, or the empty string for <code>null</code>.
   */
  @NonNull
  private static String _nonNull (@Nullable final String s)
  {
    return s == null ? "" : s;
  }

  /**
   * @param aNode
   *        A DOM element or attribute.
   * @return <code>true</code> if it is an <code>xmlns</code> or
   *         <code>xmlns:*</code> declaration attribute.
   */
  private static boolean _isNamespaceDecl (@NonNull final Node aNode)
  {
    final String sName = aNode.getNodeName ();
    return "xmlns".equals (sName) || sName.startsWith ("xmlns:");
  }

  /**
   * Resolve the name of an element or attribute. A namespace-aware node
   * (one with a local name) keeps its namespace URI and prefix. A DOM level 1
   * node is split at the colon and its prefix resolved through the
   * declarations of the element and the scope; an unprefixed attribute is in
   * no namespace.
   *
   * @throws IllegalArgumentException
   *         for a level 1 name with an unbound prefix
   */
  @NonNull
  private static Name _getName (@NonNull final Node aNode,
                                final boolean bAttribute,
                                @NonNull final Map <String, String> aOwnDecls,
                                @NonNull final NamespaceScope aScope)
  {
    final String sLocal = aNode.getLocalName ();
    if (sLocal != null)
      return new Name (_nonNull (aNode.getNamespaceURI ()), sLocal, _nonNull (aNode.getPrefix ()));
    final String sQName = aNode.getNodeName ();
    final int nColon = sQName.indexOf (':');
    final String sPrefix = nColon < 0 ? "" : sQName.substring (0, nColon);
    final String sLocalPart = nColon < 0 ? sQName : sQName.substring (nColon + 1);
    if (bAttribute && sPrefix.isEmpty ())
      return new Name ("", sLocalPart, "");
    String sUri = aOwnDecls.get (sPrefix);
    if (sUri == null)
      sUri = aScope.lookup (sPrefix);
    if (sUri == null || (sUri.isEmpty () && !sPrefix.isEmpty ()))
    {
      if (!sPrefix.isEmpty ())
        throw new IllegalArgumentException ("The prefix of the DOM node name '" + sQName + "' is not bound");
      sUri = "";
    }
    return new Name (sUri, sLocalPart, sPrefix);
  }

  /**
   * @return The SAX attribute type for the ID kind of a DOM attribute:
   *         <code>ID</code> if the DOM says it is an ID, <code>IDREF</code>
   *         or <code>IDREFS</code> for these DTD types, <code>CDATA</code>
   *         otherwise.
   */
  @NonNull
  private static String _getAttrType (@NonNull final Attr aAttr)
  {
    if (aAttr.isId ())
      return "ID";
    final TypeInfo aType = aAttr.getSchemaTypeInfo ();
    if (aType != null && DTD_TYPE_NAMESPACE.equals (aType.getTypeNamespace ()))
    {
      final String sType = aType.getTypeName ();
      if ("ID".equals (sType) || "IDREF".equals (sType) || "IDREFS".equals (sType))
        return sType;
    }
    return "CDATA";
  }

  /**
   * Is the binding a name needs in scope? If not, declare it in the own
   * declarations of the element, which take precedence over the scope.
   */
  private static boolean _isBound (@NonNull final Name aName,
                                   @NonNull final Map <String, String> aOwnDecls,
                                   @NonNull final NamespaceScope aScope)
  {
    final String sOwn = aOwnDecls.get (aName.prefix ());
    if (sOwn != null)
      return sOwn.equals (aName.uri ());
    return aScope.isBoundTo (aName.prefix (), aName.uri ());
  }

  /**
   * Open an element: collect its namespace declarations from the
   * <code>xmlns</code> attributes, add the bindings its names need
   * (namespace fixup; a namespaced attribute without a usable prefix gets a
   * new one), report the declarations and the start tag to the builder and
   * open a scope frame with the declarations.
   */
  private static void _startElement (@NonNull final Element aElement,
                                     @NonNull final NamespaceScope aScope,
                                     @NonNull final XdmTreeBuilder aBuilder)
  {
    final NamedNodeMap aAttrs = aElement.getAttributes ();
    final int nAttrs = aAttrs.getLength ();
    final ICommonsOrderedMap <String, String> aOwnDecls = new CommonsLinkedHashMap <> ();
    for (int i = 0; i < nAttrs; ++i)
    {
      final Node aAttr = aAttrs.item (i);
      if (_isNamespaceDecl (aAttr))
      {
        final String sName = aAttr.getNodeName ();
        aOwnDecls.put (sName.length () == 5 ? "" : sName.substring (6), _nonNull (aAttr.getNodeValue ()));
      }
    }
    final Name aName = _getName (aElement, false, aOwnDecls, aScope);
    if (!"xml".equals (aName.prefix ()) && !_isBound (aName, aOwnDecls, aScope))
      aOwnDecls.put (aName.prefix (), aName.uri ());

    final AttributesImpl aSaxAttrs = new AttributesImpl ();
    for (int i = 0; i < nAttrs; ++i)
    {
      final Attr aAttr = (Attr) aAttrs.item (i);
      if (_isNamespaceDecl (aAttr))
        continue;
      Name aAttrName = _getName (aAttr, true, aOwnDecls, aScope);
      if (!aAttrName.uri ().isEmpty () && !"xml".equals (aAttrName.prefix ()))
      {
        if (aAttrName.prefix ().isEmpty () || !_isBound (aAttrName, aOwnDecls, aScope))
        {
          // The own prefix if it is still free, otherwise a new one
          String sPrefix = aAttrName.prefix ();
          if (sPrefix.isEmpty () || aOwnDecls.containsKey (sPrefix) || aScope.lookup (sPrefix) != null)
            sPrefix = aScope.createPrefix (aOwnDecls);
          aOwnDecls.put (sPrefix, aAttrName.uri ());
          aAttrName = new Name (aAttrName.uri (), aAttrName.local (), sPrefix);
        }
      }
      aSaxAttrs.addAttribute (aAttrName.uri (),
                              aAttrName.local (),
                              aAttrName.getQName (),
                              _getAttrType (aAttr),
                              _nonNull (aAttr.getValue ()));
    }

    aScope.pushFrame ();
    for (final Map.Entry <String, String> aEntry : aOwnDecls.entrySet ())
    {
      aScope.bind (aEntry.getKey (), aEntry.getValue ());
      aBuilder.startPrefixMapping (aEntry.getKey (), aEntry.getValue ());
    }
    aBuilder.startElement (aName.uri (), aName.local (), aName.getQName (), aSaxAttrs);
  }

  /**
   * Report the entity declarations of a document type: unparsed entities
   * with their system and public ID, the names of external and internal
   * parsed entities.
   */
  private static void _declareEntities (@NonNull final DocumentType aDocType, @NonNull final XdmTreeBuilder aBuilder)
  {
    final NamedNodeMap aEntities = aDocType.getEntities ();
    if (aEntities == null)
      return;
    for (int i = 0; i < aEntities.getLength (); ++i)
    {
      final Entity aEntity = (Entity) aEntities.item (i);
      final String sName = aEntity.getNodeName ();
      if (aEntity.getNotationName () != null)
        aBuilder.unparsedEntityDecl (sName, aEntity.getPublicId (), aEntity.getSystemId (), aEntity.getNotationName ());
      else
        if (aEntity.getSystemId () != null)
          aBuilder.externalEntityDecl (sName, aEntity.getPublicId (), aEntity.getSystemId ());
        else
          aBuilder.internalEntityDecl (sName, "");
    }
  }

  /**
   * @return The base URI of the content of an entity reference if it refers
   *         to an external entity, <code>null</code> otherwise.
   */
  @Nullable
  private static String _getExternalEntityBase (@NonNull final Node aRef)
  {
    final Document aDoc = aRef.getOwnerDocument ();
    final DocumentType aDocType = aDoc == null ? null : aDoc.getDoctype ();
    if (aDocType == null || aDocType.getEntities () == null)
      return null;
    final Node aEntity = aDocType.getEntities ().getNamedItem (aRef.getNodeName ());
    if (!(aEntity instanceof final Entity aEnt) || aEnt.getSystemId () == null)
      return null;
    return aRef.getBaseURI ();
  }

  /**
   * @return The last child of the node, which the builder has just appended.
   */
  @NonNull
  private static IXdmNode _getLastChild (@NonNull final IXdmNode aParent)
  {
    return aParent.getChildAtIndex (aParent.getChildCount () - 1);
  }

  /**
   * Walk the DOM nodes and feed them into the builder, inside a document
   * that is started and ended here. The walk is iterative.
   *
   * @param aTopNodes
   *        The nodes that become the children of the document node.
   * @param sSystemId
   *        The document URI, may be <code>null</code>.
   * @param aTarget
   *        The DOM node whose XDM counterpart is wanted, may be
   *        <code>null</code>.
   * @param aTargetOut
   *        Receives the counterpart of the target, if it is an element,
   *        attribute, comment or processing instruction.
   * @return The document node.
   */
  @NonNull
  private static IXdmNode _build (@NonNull final ICommonsList <Node> aTopNodes,
                                  @Nullable final String sSystemId,
                                  @Nullable final Node aTarget,
                                  @NonNull final Wrapper <IXdmNode> aTargetOut)
  {
    // A DOM has no locations to record
    final XdmTreeBuilder aBuilder = new XdmTreeBuilder (false);
    final LocatorImpl aLocator = new LocatorImpl ();
    aLocator.setSystemId (sSystemId);
    aBuilder.setDocumentLocator (aLocator);
    aBuilder.startDocument ();
    final NamespaceScope aScope = new NamespaceScope (null);
    final ArrayDeque <Step> aStack = new ArrayDeque <> ();
    for (int i = aTopNodes.size () - 1; i >= 0; --i)
      aStack.push (new Step (aTopNodes.get (i), false, null));
    while (!aStack.isEmpty ())
    {
      final Step aStep = aStack.pop ();
      final Node aNode = aStep.node ();
      if (aStep.close ())
      {
        if (aNode.getNodeType () == Node.ELEMENT_NODE)
        {
          aBuilder.endElement (_nonNull (aNode.getNamespaceURI ()), _nonNull (aNode.getLocalName ()), aNode.getNodeName ());
          aScope.popFrame ();
        }
        else
          aLocator.setSystemId (aStep.restoreSystemId ());
        continue;
      }
      switch (aNode.getNodeType ())
      {
        case Node.ELEMENT_NODE:
        {
          _startElement ((Element) aNode, aScope, aBuilder);
          if (aNode == aTarget)
            aTargetOut.set (aBuilder.getCurrentNode ());
          else
            if (aTarget instanceof final Attr aTargetAttr && aTargetAttr.getOwnerElement () == aNode)
            {
              final IXdmNode aCurrent = aBuilder.getCurrentNode ();
              for (int i = 0; i < aCurrent.getAttributeCount (); ++i)
              {
                final IXdmNode aAttr = aCurrent.getAttributeAtIndex (i);
                if (aAttr.getName ().getLexicalName ().equals (aTargetAttr.getNodeName ()) ||
                    (aTargetAttr.getLocalName () != null &&
                     aAttr.getName ().isSameName (aTargetAttr.getNamespaceURI (), aTargetAttr.getLocalName ())))
                  aTargetOut.set (aAttr);
              }
            }
          aStack.push (new Step (aNode, true, null));
          for (Node aChild = aNode.getLastChild (); aChild != null; aChild = aChild.getPreviousSibling ())
            aStack.push (new Step (aChild, false, null));
          break;
        }
        case Node.TEXT_NODE:
        case Node.CDATA_SECTION_NODE:
        {
          final char [] aChars = _nonNull (((CharacterData) aNode).getData ()).toCharArray ();
          if (aNode instanceof final Text aText && aText.isElementContentWhitespace ())
            aBuilder.ignorableWhitespace (aChars, 0, aChars.length);
          else
            aBuilder.characters (aChars, 0, aChars.length);
          break;
        }
        case Node.COMMENT_NODE:
        {
          final char [] aChars = _nonNull (aNode.getNodeValue ()).toCharArray ();
          aBuilder.comment (aChars, 0, aChars.length);
          if (aNode == aTarget)
            aTargetOut.set (_getLastChild (aBuilder.getCurrentNode ()));
          break;
        }
        case Node.PROCESSING_INSTRUCTION_NODE:
        {
          final ProcessingInstruction aPI = (ProcessingInstruction) aNode;
          aBuilder.processingInstruction (aPI.getTarget (), _nonNull (aPI.getData ()));
          if (aNode == aTarget)
            aTargetOut.set (_getLastChild (aBuilder.getCurrentNode ()));
          break;
        }
        case Node.ENTITY_REFERENCE_NODE:
        {
          final String sEntityBase = _getExternalEntityBase (aNode);
          aStack.push (new Step (aNode, true, aLocator.getSystemId ()));
          if (sEntityBase != null)
            aLocator.setSystemId (sEntityBase);
          for (Node aChild = aNode.getLastChild (); aChild != null; aChild = aChild.getPreviousSibling ())
            aStack.push (new Step (aChild, false, null));
          break;
        }
        case Node.DOCUMENT_TYPE_NODE:
          _declareEntities ((DocumentType) aNode, aBuilder);
          break;
        default:
          // Notations, entities and anything else carry no XDM content
          break;
      }
    }
    aBuilder.endDocument ();
    return aBuilder.getDocument ();
  }

  /**
   * @param aNode
   *        Any DOM node.
   * @return The nodes that become the children of the XDM document node: the
   *         children of the document (or document fragment) the node belongs
   *         to, or the outermost ancestor of a node that is in neither.
   */
  @NonNull
  private static ICommonsList <Node> _getTopNodes (@NonNull final Node aNode)
  {
    Node aTop = aNode instanceof final Attr aAttr && aAttr.getOwnerElement () != null ? aAttr.getOwnerElement () : aNode;
    while (aTop.getParentNode () != null)
      aTop = aTop.getParentNode ();
    final ICommonsList <Node> ret = new CommonsArrayList <> ();
    if (aTop.getNodeType () == Node.DOCUMENT_NODE || aTop.getNodeType () == Node.DOCUMENT_FRAGMENT_NODE)
    {
      for (Node aChild = aTop.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
        ret.add (aChild);
    }
    else
      ret.add (aTop);
    return ret;
  }

  /**
   * @return The system ID given, or else the document URI of the DOM
   *         document the node belongs to.
   */
  @Nullable
  private static String _getSystemId (@NonNull final Node aNode, @Nullable final String sSystemId)
  {
    if (sSystemId != null)
      return sSystemId;
    final Document aDoc = aNode instanceof final Document aDocNode ? aDocNode : aNode.getOwnerDocument ();
    return aDoc == null ? null : aDoc.getDocumentURI ();
  }

  /**
   * Convert the DOM tree a node belongs to into an XDM document. The whole
   * tree is converted: for a node inside a document, its document; for a
   * document fragment, the fragment's children; for a node in a subtree
   * without document (a parentless element), that subtree, which becomes the
   * only child of the new document node.
   *
   * @param aNode
   *        Any node of the DOM tree. May not be <code>null</code>.
   * @param sSystemId
   *        The document URI of the result, or <code>null</code> to take the
   *        <code>documentURI</code> of the DOM document.
   * @return The XDM document node. Never <code>null</code>.
   * @throws IllegalArgumentException
   *         if a DOM level 1 name has an unbound prefix
   */
  @NonNull
  public static IXdmNode convertDocument (@NonNull final Node aNode, @Nullable final String sSystemId)
  {
    ValueEnforcer.notNull (aNode, "Node");
    return _build (_getTopNodes (aNode), _getSystemId (aNode, sSystemId), null, new Wrapper <> ());
  }

  /**
   * Convert the DOM tree a node belongs to, as
   * {@link #convertDocument(Node, String)} does, and return the XDM node
   * that corresponds to the given one: the document node for a document or
   * document fragment, the element, attribute, comment or processing
   * instruction for one of these. A parentless DOM attribute becomes a
   * parentless XDM attribute.
   *
   * @param aNode
   *        A document, document fragment, element, attribute, comment or
   *        processing instruction. May not be <code>null</code>.
   * @param sSystemId
   *        The document URI of the result, or <code>null</code> to take the
   *        <code>documentURI</code> of the DOM document.
   * @return The XDM counterpart. Never <code>null</code>.
   * @throws IllegalArgumentException
   *         for a text node, an entity reference or another node kind with
   *         no single XDM counterpart (adjacent text merges), or if a DOM
   *         level 1 name has an unbound prefix
   */
  @NonNull
  public static IXdmNode convert (@NonNull final Node aNode, @Nullable final String sSystemId)
  {
    ValueEnforcer.notNull (aNode, "Node");
    final short nType = aNode.getNodeType ();
    if (nType == Node.ATTRIBUTE_NODE && ((Attr) aNode).getOwnerElement () == null)
    {
      final Attr aAttr = (Attr) aNode;
      final String sLocal = aAttr.getLocalName () != null ? aAttr.getLocalName () : aAttr.getNodeName ();
      return IXdmNode.createAttribute (new XdmQName (_nonNull (aAttr.getNamespaceURI ()),
                                                       sLocal,
                                                       _nonNull (aAttr.getPrefix ())),
                                       _nonNull (aAttr.getValue ()));
    }
    if (nType != Node.DOCUMENT_NODE &&
        nType != Node.DOCUMENT_FRAGMENT_NODE &&
        nType != Node.ELEMENT_NODE &&
        nType != Node.ATTRIBUTE_NODE &&
        nType != Node.COMMENT_NODE &&
        nType != Node.PROCESSING_INSTRUCTION_NODE)
      throw new IllegalArgumentException ("No single XDM node corresponds to the DOM node of type " + nType);
    final Wrapper <IXdmNode> aTargetOut = new Wrapper <> ();
    final IXdmNode aDoc = _build (_getTopNodes (aNode), _getSystemId (aNode, sSystemId), aNode, aTargetOut);
    if (nType == Node.DOCUMENT_NODE || nType == Node.DOCUMENT_FRAGMENT_NODE)
      return aDoc;
    if (aTargetOut.get () == null)
      throw new IllegalStateException ("The XDM counterpart of the DOM node was not found");
    return aTargetOut.get ();
  }
}
