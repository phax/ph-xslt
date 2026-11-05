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

import java.util.ArrayDeque;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmTextNode;

/**
 * Builds the children (and for elements the attributes and namespaces) of an
 * element or document node by the rules for constructing complex content (XSLT
 * 3.0 section 5.7.1): atomic values become text, adjacent atomic values are
 * separated by a space, arrays are flattened, document nodes are replaced by
 * their children, adjacent text is merged and zero-length text dropped,
 * existing nodes are copied, new nodes adopted. Attributes and namespaces
 * after children are XTDE0410, in a document node XTDE0420, function items
 * and maps XTDE0450.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class ContentOutput implements IXsltOutput
{
  private IXdmNode m_aParent;
  private final boolean m_bDocument;
  private boolean m_bHasChildren;
  private boolean m_bLastAtomic;
  private IXdmNode m_aLastText;
  // Set for the nodes of a principal result that is written while it is built
  private final ResultDrain m_aDrain;

  /**
   * @param aParent
   *        The element or document node to fill. It may already have content.
   */
  public ContentOutput (@NonNull final IXdmNode aParent)
  {
    this (aParent, null);
  }

  /**
   * Start filling a node. Existing children count as content, so attributes
   * and namespaces can no longer be added (XTDE0410) and a trailing text child
   * is extended by further text.
   *
   * @param aParent
   *        The element or document node to fill. It may already have content.
   * @param aDrain
   *        The drain writing the content while it is built, or
   *        <code>null</code>.
   */
  ContentOutput (@NonNull final IXdmNode aParent, @Nullable final ResultDrain aDrain)
  {
    ValueEnforcer.isTrue (aParent.isElement () || aParent.isDocument (), "Parent must be an element or document");
    m_aParent = aParent;
    m_bDocument = aParent.isDocument ();
    final int nChildren = aParent.getChildCount ();
    m_bHasChildren = nChildren > 0;
    if (nChildren > 0 && aParent.getChildAtIndex (nChildren - 1).isText ())
      m_aLastText = aParent.getChildAtIndex (nChildren - 1);
    m_aDrain = aDrain;
  }

  /**
   * @return The node being filled. Use this (not the node passed to the
   *         constructor) when the content is complete: namespace fixup may
   *         have replaced an element with one that has another prefix.
   */
  @NonNull
  public IXdmNode getParent ()
  {
    return m_aParent;
  }

  /**
   * Add text content: an empty string adds nothing, otherwise it is appended
   * to the last text child built here (merging adjacent text) or becomes a new
   * text child.
   *
   * @param s
   *        The text.
   */
  private void _addText (@NonNull final String s)
  {
    if (s.isEmpty ())
      return;
    if (m_aLastText != null)
    {
      // Joined when the value is read, not on every piece
      ((XdmTextNode) m_aLastText).appendValue (s);
    }
    else
    {
      final IXdmNode aText = IXdmNode.createText (s);
      m_aParent.appendChild (aText);
      m_aLastText = aText;
    }
    m_bHasChildren = true;
  }

  /**
   * @param sWhat
   *        "attribute" or "namespace", for the message.
   * @throws XPathException
   *         XTDE0420 if the node being filled is a document node, XTDE0410 if
   *         the element already has children
   */
  private void _checkAttributeOrNamespace (@NonNull final String sWhat)
  {
    if (m_bDocument)
      throw new XPathException ("XTDE0420", "A " + sWhat + " node cannot be added to a document node");
    if (m_bHasChildren)
      throw new XPathException ("XTDE0410",
                                "A " + sWhat + " node cannot be added to an element after its children");
  }

  /**
   * Attach an attribute to the element (after the checks of
   * {@link #_checkAttributeOrNamespace(String)}). An existing attribute with
   * the same expanded name is overwritten in place: it takes the new value and
   * annotations and keeps its own name and position. Otherwise the name may
   * get another prefix by {@link NamespaceFixup#getAttributeNameFor} when its
   * prefix is bound differently on the element. A new, parentless attribute
   * whose name needs no change is adopted as it is, except for
   * <code>xml:id</code>; in all other cases a copy is attached (an
   * <code>xml:id</code> copy is whitespace normalised and an ID) that keeps the
   * type annotation of the original.
   *
   * @param aAttr
   *        The attribute.
   * @param bNew
   *        <code>true</code> if it was just constructed and may be adopted.
   */
  private void _addAttribute (@NonNull final IXdmNode aAttr, final boolean bNew)
  {
    _checkAttributeOrNamespace ("attribute");
    final XdmQName aName = aAttr.getName ();
    final IXdmNode aExisting = m_aParent.getAttribute (aName.namespaceUri (), aName.localName ());
    if (aExisting != null)
    {
      // A later attribute of the same name replaces the earlier one
      aExisting.setValue (aAttr.getValue ());
      NodeCopier.copyAnnotation (aAttr, aExisting);
      return;
    }
    final XdmQName aFixed = NamespaceFixup.getAttributeNameFor (m_aParent, aName);
    if (bNew && aFixed == aName && aAttr.getParent () == null && !aName.isSameName (XdmConstants.NS_XML, "id"))
      m_aParent.addAttribute (aAttr);
    else
    {
      // A copy keeps the type annotation; the validation of the constructing
      // instruction decides about it (schema-awareness)
      final IXdmNode aCopy = NodeCopier.createAttribute (aFixed, aAttr.getValue ());
      NodeCopier.copyAnnotation (aAttr, aCopy);
      m_aParent.addAttribute (aCopy);
    }
  }

  /**
   * Namespace fixup: a namespace node claims the prefix of the element name, so
   * the element gets another prefix. It has no children yet (namespaces come
   * before them), so it is rebuilt with its attributes and namespaces. The
   * new prefix is the old one (or "ns" for the default namespace) followed by
   * "_" and the first number whose prefix is not declared on the element; the
   * old prefix's declaration is dropped and the new one declared. Attribute
   * copies lose their type annotations; the base URI is carried over.
   */
  private void _renameElementPrefix ()
  {
    final XdmQName aOld = m_aParent.getName ();
    final Map <String, String> aDeclared = m_aParent.getDeclaredNamespaces ();
    final String sBase = aOld.prefix ().isEmpty () ? "ns" : aOld.prefix ();
    int n = 0;
    String sNew;
    do
    {
      sNew = sBase + "_" + n++;
    } while (aDeclared.containsKey (sNew));
    final IXdmNode aNew = IXdmNode.createElement (new XdmQName (aOld.namespaceUri (), aOld.localName (), sNew));
    for (final Map.Entry <String, String> aEntry : aDeclared.entrySet ())
      if (!aEntry.getKey ().equals (aOld.prefix ()))
        aNew.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    aNew.declareNamespace (sNew, aOld.namespaceUri ());
    for (int i = 0; i < m_aParent.getAttributeCount (); ++i)
    {
      final IXdmNode aAttr = m_aParent.getAttributeAtIndex (i);
      aNew.addAttribute (NodeCopier.createAttribute (aAttr.getName (), aAttr.getValue ()));
    }
    aNew.setBaseUri (m_aParent.getBaseUri ());
    m_aParent = aNew;
  }

  /**
   * Add a namespace node to the element as a namespace declaration (after the
   * checks of {@link #_checkAttributeOrNamespace(String)}). The
   * <code>xml</code> binding to its own URI is ignored. If the prefix of the
   * element name is bound to another URI by the new node, the element is
   * renamed by {@link #_renameElementPrefix()}.
   *
   * @param aNS
   *        The namespace node; no name means the default namespace.
   * @throws XPathException
   *         XTDE0440 for a non-empty default namespace on an element in no
   *         namespace; XTDE0430 if the element already declares the prefix
   *         with another non-empty URI
   */
  private void _addNamespace (@NonNull final IXdmNode aNS)
  {
    _checkAttributeOrNamespace ("namespace");
    final String sPrefix = aNS.getName () == null ? "" : aNS.getName ().localName ();
    final String sUri = aNS.getValue ();
    if ("xml".equals (sPrefix) && XdmConstants.NS_XML.equals (sUri))
      return;
    final XdmQName aElName = m_aParent.getName ();
    if (sPrefix.isEmpty () && !aElName.hasNamespace () && !sUri.isEmpty ())
      throw new XPathException ("XTDE0440",
                                "A default namespace node cannot be added to the element " +
                                            aElName.getLexicalName () +
                                            ", which is in no namespace");
    final String sOwn = m_aParent.getDeclaredNamespaces ().get (sPrefix);
    if (sOwn != null && !sOwn.isEmpty () && !sOwn.equals (sUri))
      throw new XPathException ("XTDE0430",
                                "Two namespace nodes for prefix '" + sPrefix + "' with different URIs");
    if (aElName.prefix ().equals (sPrefix) && !aElName.namespaceUri ().equals (sUri))
      _renameElementPrefix ();
    m_aParent.declareNamespace (sPrefix, sUri);
  }

  /**
   * Add a node as content: a document node is replaced by its children (each
   * copied) and passes its unparsed entities on; attributes and namespaces are
   * attached; text is merged with adjacent text, except that text with
   * disable-output-escaping stays a node of its own (zero-length text is
   * dropped either way). Any other node is adopted if it is new and
   * parentless, otherwise deep copied with namespaces and annotations; an
   * element loses its explicit base URI so that it follows from the new
   * position.
   *
   * @param aNode
   *        The node.
   * @param bNew
   *        <code>true</code> if it was just constructed and may be adopted.
   */
  private void _appendNode (@NonNull final IXdmNode aNode, final boolean bNew)
  {
    switch (aNode.getKind ())
    {
      case DOCUMENT:
      {
        NodeCopier.copyUnparsedEntities (aNode, m_aParent);
        final int nChildren = aNode.getChildCount ();
        for (int i = 0; i < nChildren; ++i)
          _appendNode (aNode.getChildAtIndex (i), false);
        break;
      }
      case ATTRIBUTE:
        _addAttribute (aNode, bNew);
        break;
      case NAMESPACE:
        _addNamespace (aNode);
        break;
      case TEXT:
        if (aNode.isDisableOutputEscaping ())
        {
          // Kept as a node of its own, so that the flag is not merged away
          if (!aNode.getValue ().isEmpty ())
          {
            final IXdmNode aText = IXdmNode.createText (aNode.getValue ());
            aText.setDisableOutputEscaping (true);
            m_aParent.appendChild (aText);
            m_aLastText = null;
            m_bHasChildren = true;
          }
        }
        else
          _addText (aNode.getValue ());
        break;
      default:
      {
        final IXdmNode aChild = bNew && aNode.getParent () == null ? aNode : NodeCopier.copyDeep (aNode, true, true);
        // As content its base URI follows from its new position
        if (aChild.isElement ())
          aChild.setBaseUri (null);
        m_aParent.appendChild (aChild);
        m_aLastText = null;
        m_bHasChildren = true;
        break;
      }
    }
  }

  /**
   * Add one item that is not an array. An atomic value becomes its lexical
   * form as text, preceded by a single space if the previous item was atomic
   * as well; a node goes to {@link #_appendNode(IXdmNode, boolean)} as an
   * existing node (copied).
   *
   * @param aItem
   *        The item.
   * @throws XPathException
   *         XTDE0450 for a function item or map
   */
  private void _appendFlat (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final XdmAtomic aAtomic)
    {
      if (m_bLastAtomic)
        _addText (" ");
      _addText (aAtomic.getLexical ());
      m_bLastAtomic = true;
      return;
    }
    if (aItem instanceof final IXdmNode aNode)
    {
      m_bLastAtomic = false;
      _appendNode (aNode, false);
      return;
    }
    throw new XPathException ("XTDE0450", "A function item or map cannot be added to the content of a node");
  }

  /**
   * Push the items of all members of an array onto a stack so that they are
   * popped in array order.
   *
   * @param aArray
   *        The array.
   * @param aStack
   *        The stack.
   */
  private static void _pushMembers (@NonNull final XdmArray aArray, @NonNull final ArrayDeque <IXdmItem> aStack)
  {
    for (int i = aArray.size () - 1; i >= 0; --i)
    {
      final XdmSequence aMember = aArray.get (i);
      for (int j = aMember.size () - 1; j >= 0; --j)
        aStack.push (aMember.get (j));
    }
  }

  /**
   * The drain wrote the last child, which was text: text appended later
   * starts a new node.
   */
  void forgetLastText ()
  {
    m_aLastText = null;
  }

  /**
   * Add an existing item; an array is flattened (nested arrays as well) with
   * an explicit stack and its items added one by one.
   *
   * @param aItem
   *        The item.
   */
  private void _append (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final XdmArray aArray)
    {
      // Arrays are flattened, iteratively for nested arrays
      final ArrayDeque <IXdmItem> aStack = new ArrayDeque <> ();
      _pushMembers (aArray, aStack);
      while (!aStack.isEmpty ())
      {
        final IXdmItem aCur = aStack.pop ();
        if (aCur instanceof final XdmArray aNested)
          _pushMembers (aNested, aStack);
        else
          _appendFlat (aCur);
      }
      return;
    }
    _appendFlat (aItem);
  }

  /**
   * {@inheritDoc}
   * <p>
   * In a drained principal result the item is first offered to
   * {@link ResultDrain#beforeAppend(ContentOutput, IXdmItem)}, which may drop
   * it after a SENR0001 error, and the drain writes completed content
   * afterwards.
   */
  public void append (@NonNull final IXdmItem aItem)
  {
    if (m_aDrain == null)
      _append (aItem);
    else
      if (m_aDrain.beforeAppend (this, aItem))
      {
        _append (aItem);
        m_aDrain.afterAppend (this);
      }
  }

  /**
   * {@inheritDoc}
   * <p>
   * The node ends a run of atomic values (no separating space). The drain is
   * involved as in {@link #append(IXdmItem)}.
   */
  public void appendNew (@NonNull final IXdmNode aNode)
  {
    if (m_aDrain != null && !m_aDrain.beforeAppend (this, aNode))
      return;
    m_bLastAtomic = false;
    _appendNode (aNode, true);
    if (m_aDrain != null)
      m_aDrain.afterAppend (this);
  }

  /**
   * {@inheritDoc}
   * <p>
   * The text is merged with adjacent text (an empty string adds nothing) and
   * ends a run of atomic values. The drain is involved as in
   * {@link #append(IXdmItem)}.
   */
  @Override
  public void appendText (@NonNull final String sText)
  {
    if (m_aDrain != null && !m_aDrain.beforeAppend (this, null))
      return;
    m_bLastAtomic = false;
    _addText (sText);
    if (m_aDrain != null)
      m_aDrain.afterAppend (this);
  }

  /**
   * The content of a document node written to the enclosing content instead:
   * attributes and namespaces in it are errors, and it separates adjacent
   * atomic values.
   */
  private final class DocumentContent implements IXsltOutput
  {
    private boolean m_bAtomic;

    @NonNull
    ContentOutput getOuter ()
    {
      return ContentOutput.this;
    }

    /**
     * Append to the enclosing content, with the atomic-value spacing of this
     * document node rather than of the enclosing content: an atomic value is
     * separated by a space only from a directly preceding atomic value of the
     * same document content, and after it the enclosing content does not
     * count as following an atomic value.
     *
     * @param aItem
     *        The item.
     * @throws XPathException
     *         XTDE0420 for an attribute or namespace node
     */
    public void append (@NonNull final IXdmItem aItem)
    {
      if (aItem instanceof final IXdmNode aNode && (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE))
        throw new XPathException ("XTDE0420", "An attribute or namespace node cannot be added to a document node");
      m_bLastAtomic = m_bAtomic;
      ContentOutput.this.append (aItem);
      m_bAtomic = m_bLastAtomic;
      m_bLastAtomic = false;
    }

    public void appendNew (@NonNull final IXdmNode aNode)
    {
      if (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE)
        throw new XPathException ("XTDE0420", "An attribute or namespace node cannot be added to a document node");
      m_bAtomic = false;
      ContentOutput.this.appendNew (aNode);
    }

    @Override
    public void appendText (@NonNull final String sText)
    {
      m_bAtomic = false;
      ContentOutput.this.appendText (sText);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Always possible here: the returned output writes into this content, and a
   * following atomic value is not separated from one before the document
   * node.
   */
  @Override
  @Nullable
  public IXsltOutput getDocumentContentOutput ()
  {
    // The content of the document node replaces it
    m_bLastAtomic = false;
    return new DocumentContent ();
  }

  /**
   * Create the output for the content of a new element that an instruction
   * appends to a target output with {@link IXsltOutput#appendNew(IXdmNode)}
   * once it is complete, with nothing else appended to that target in
   * between. If the target is part of a principal result that is written
   * while it is built ({@link ResultDrain}), the element is written while it
   * is built as well, provided <code>bDrainable</code> says that the
   * instruction does not change the content after it was added (as
   * inherit-namespaces="no" and validation do).
   *
   * @param aElement
   *        The new element, without children.
   * @param aTarget
   *        The output the element is appended to.
   * @param bDrainable
   *        <code>true</code> if the content of the element is not changed
   *        after it was added.
   * @return A new content output for the element.
   */
  @NonNull
  public static ContentOutput createFor (@NonNull final IXdmNode aElement,
                                         @NonNull final IXsltOutput aTarget,
                                         final boolean bDrainable)
  {
    if (bDrainable)
    {
      ContentOutput aParent = null;
      if (aTarget instanceof final ContentOutput aContent)
        aParent = aContent;
      else
        if (aTarget instanceof final DocumentContent aDocContent)
          aParent = aDocContent.getOuter ();
      if (aParent != null && aParent.m_aDrain != null)
      {
        final ContentOutput ret = aParent.m_aDrain.openElement (aParent, aElement);
        if (ret != null)
          return ret;
      }
    }
    return new ContentOutput (aElement);
  }

  /**
   * Finish the tree rooted at the parent: namespace fixup and document order
   * (via {@link NamespaceFixup#finish(IXdmNode)}). Call this only for a tree root (document nodes, parentless elements kept
   * as items).
   */
  public void finishTree ()
  {
    NamespaceFixup.finish (m_aParent);
  }
}
