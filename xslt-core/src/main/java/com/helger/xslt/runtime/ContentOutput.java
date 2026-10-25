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
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

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
  private final XdmNode m_aParent;
  private final boolean m_bDocument;
  private boolean m_bHasChildren;
  private boolean m_bLastAtomic;
  private XdmNode m_aLastText;

  /**
   * @param aParent
   *        The element or document node to fill. It may already have content.
   */
  public ContentOutput (@NonNull final XdmNode aParent)
  {
    ValueEnforcer.isTrue (aParent.isElement () || aParent.isDocument (), "Parent must be an element or document");
    m_aParent = aParent;
    m_bDocument = aParent.isDocument ();
    final int nChildren = aParent.getChildCount ();
    m_bHasChildren = nChildren > 0;
    if (nChildren > 0 && aParent.getChildAtIndex (nChildren - 1).isText ())
      m_aLastText = aParent.getChildAtIndex (nChildren - 1);
  }

  /** @return The node being filled. */
  @NonNull
  public XdmNode getParent ()
  {
    return m_aParent;
  }

  private void _addText (@NonNull final String s)
  {
    if (s.isEmpty ())
      return;
    if (m_aLastText != null)
      m_aLastText.setValue (m_aLastText.getValue () + s);
    else
    {
      final XdmNode aText = XdmNode.createText (s);
      m_aParent.appendChild (aText);
      m_aLastText = aText;
    }
    m_bHasChildren = true;
  }

  private void _checkAttributeOrNamespace (@NonNull final String sWhat)
  {
    if (m_bDocument)
      throw new XPathException ("XTDE0420", "A " + sWhat + " node cannot be added to a document node");
    if (m_bHasChildren)
      throw new XPathException ("XTDE0410",
                                "A " + sWhat + " node cannot be added to an element after its children");
  }

  private void _addAttribute (@NonNull final XdmNode aAttr, final boolean bNew)
  {
    _checkAttributeOrNamespace ("attribute");
    final XdmQName aName = aAttr.getName ();
    final XdmNode aExisting = m_aParent.getAttribute (aName.namespaceUri (), aName.localName ());
    if (aExisting != null)
    {
      // A later attribute of the same name replaces the earlier one
      aExisting.setValue (aAttr.getValue ());
      return;
    }
    final XdmQName aFixed = NamespaceFixup.getAttributeNameFor (m_aParent, aName);
    if (bNew && aFixed == aName && aAttr.getParent () == null)
      m_aParent.addAttribute (aAttr);
    else
      m_aParent.addAttribute (XdmNode.createAttribute (aFixed, aAttr.getValue ()));
  }

  private void _addNamespace (@NonNull final XdmNode aNS)
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
    if (aElName.prefix ().equals (sPrefix) && !aElName.namespaceUri ().equals (sUri))
      throw new XPathException ("XTDE0430",
                                "The namespace node for prefix '" +
                                            sPrefix +
                                            "' conflicts with the name of the element " +
                                            aElName.getLexicalName ());
    final String sOwn = m_aParent.getDeclaredNamespaces ().get (sPrefix);
    if (sOwn != null && !sOwn.equals (sUri))
      throw new XPathException ("XTDE0430",
                                "Two namespace nodes for prefix '" + sPrefix + "' with different URIs");
    m_aParent.declareNamespace (sPrefix, sUri);
  }

  private void _appendNode (@NonNull final XdmNode aNode, final boolean bNew)
  {
    switch (aNode.getKind ())
    {
      case DOCUMENT:
      {
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
        _addText (aNode.getValue ());
        break;
      default:
      {
        final XdmNode aChild = bNew && aNode.getParent () == null ? aNode : NodeCopier.copyDeep (aNode, true);
        m_aParent.appendChild (aChild);
        m_aLastText = null;
        m_bHasChildren = true;
        break;
      }
    }
  }

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
    if (aItem instanceof final XdmNode aNode)
    {
      m_bLastAtomic = false;
      _appendNode (aNode, false);
      return;
    }
    throw new XPathException ("XTDE0450", "A function item or map cannot be added to the content of a node");
  }

  private static void _pushMembers (@NonNull final XdmArray aArray, @NonNull final ArrayDeque <IXdmItem> aStack)
  {
    for (int i = aArray.size () - 1; i >= 0; --i)
    {
      final XdmSequence aMember = aArray.get (i);
      for (int j = aMember.size () - 1; j >= 0; --j)
        aStack.push (aMember.get (j));
    }
  }

  public void append (@NonNull final IXdmItem aItem)
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

  public void appendNew (@NonNull final XdmNode aNode)
  {
    m_bLastAtomic = false;
    _appendNode (aNode, true);
  }

  @Override
  public void appendText (@NonNull final String sText)
  {
    m_bLastAtomic = false;
    _addText (sText);
  }

  @Override
  @Nullable
  public IXsltOutput getDocumentContentOutput ()
  {
    final ContentOutput aOuter = this;
    // The content of the document node replaces it: attributes and namespaces
    // in it are errors, and it separates adjacent atomic values
    m_bLastAtomic = false;
    return new IXsltOutput ()
    {
      private boolean m_bAtomic;

      public void append (@NonNull final IXdmItem aItem)
      {
        if (aItem instanceof final XdmNode aNode && (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE))
          throw new XPathException ("XTDE0420", "An attribute or namespace node cannot be added to a document node");
        aOuter.m_bLastAtomic = m_bAtomic;
        aOuter.append (aItem);
        m_bAtomic = aOuter.m_bLastAtomic;
        aOuter.m_bLastAtomic = false;
      }

      public void appendNew (@NonNull final XdmNode aNode)
      {
        if (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE)
          throw new XPathException ("XTDE0420", "An attribute or namespace node cannot be added to a document node");
        m_bAtomic = false;
        aOuter.appendNew (aNode);
      }

      @Override
      public void appendText (@NonNull final String sText)
      {
        m_bAtomic = false;
        aOuter.appendText (sText);
      }
    };
  }

  /**
   * Finish the tree rooted at the parent: namespace fixup and document order.
   * Call this only for a tree root (document nodes, parentless elements kept
   * as items).
   */
  public void finishTree ()
  {
    NamespaceFixup.finish (m_aParent);
  }
}
