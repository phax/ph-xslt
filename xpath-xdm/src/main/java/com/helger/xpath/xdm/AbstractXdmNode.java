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
package com.helger.xpath.xdm;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;

/**
 * Base class of all node kinds. It holds what every node has (parent,
 * location, document order) and the default for every property a kind does
 * not have. The subclasses only add the fields of their kind.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public abstract sealed class AbstractXdmNode implements IXdmNode permits AbstractXdmParentNode, AbstractXdmValueNode
{
  private static final AtomicLong TREE_COUNTER = new AtomicLong (0);

  /**
   * The properties only few nodes have. They live in a separate object that is
   * created on the first non-default value, which keeps every node small. All
   * of them are set while a tree is built.
   */
  private static final class Details
  {
    // Namespace declarations of an element
    private ICommonsOrderedMap <String, String> m_aNamespaces;
    // Built-in type annotation of a node without schema type
    private XdmQName m_aTypeAnnotation;
    private boolean m_bNilled;
    private String m_sDocumentUri;
    private String m_sBaseUri;
    // XSLT disable-output-escaping on a text node
    private boolean m_bDisableOutputEscaping;
  }

  private AbstractXdmNode m_aParent;
  private int m_nOrder;
  private int m_nLine;
  private int m_nColumn;
  // 0 = not numbered yet
  private long m_nTree;
  // Created on demand
  private Details m_aDetails;

  AbstractXdmNode ()
  {}

  @NonNull
  private Details _details ()
  {
    if (m_aDetails == null)
      m_aDetails = new Details ();
    return m_aDetails;
  }

  @Nullable
  private ICommonsOrderedMap <String, String> _getNamespaces ()
  {
    return m_aDetails == null ? null : m_aDetails.m_aNamespaces;
  }

  final void internalSetParent (@Nullable final AbstractXdmNode aParent)
  {
    m_aParent = aParent;
  }

  final void internalDeclareNamespace (@NonNull final String sPrefix, @NonNull final String sNamespaceUri)
  {
    final Details aDetails = _details ();
    if (aDetails.m_aNamespaces == null)
      aDetails.m_aNamespaces = new CommonsLinkedHashMap <> ();
    aDetails.m_aNamespaces.put (sPrefix, sNamespaceUri);
  }

  @Nullable
  public XdmQName getName ()
  {
    return null;
  }

  @Nullable
  public String getValue ()
  {
    return null;
  }

  public void setValue (@NonNull final String sValue)
  {
    ValueEnforcer.notNull (sValue, "Value");
    throw new IllegalStateException ("A " + getKind () + " node has no raw value");
  }

  @Nullable
  public final AbstractXdmNode getParent ()
  {
    return m_aParent;
  }

  @Nonnegative
  public final int getLine ()
  {
    return m_nLine;
  }

  @Nonnegative
  public final int getColumn ()
  {
    return m_nColumn;
  }

  public final void setLocation (@Nonnegative final int nLine, @Nonnegative final int nColumn)
  {
    m_nLine = nLine;
    m_nColumn = nColumn;
  }

  @NonNull
  public EIDKind getIDKind ()
  {
    return EIDKind.NONE;
  }

  public void setIDKind (@NonNull final EIDKind eIDKind)
  {
    ValueEnforcer.notNull (eIDKind, "IDKind");
    if (eIDKind != EIDKind.NONE)
      throw new IllegalStateException ("A " + getKind () + " node has no ID kind");
  }

  @Nullable
  public final XdmQName getTypeAnnotation ()
  {
    final XdmSchemaType aSchemaType = getSchemaType ();
    if (aSchemaType != null)
      return aSchemaType.getName ();
    return m_aDetails == null ? null : m_aDetails.m_aTypeAnnotation;
  }

  public final void setTypeAnnotation (@Nullable final XdmQName aTypeAnnotation)
  {
    if (aTypeAnnotation != null || m_aDetails != null)
      _details ().m_aTypeAnnotation = aTypeAnnotation;
  }

  @Nullable
  public XdmSchemaType getSchemaType ()
  {
    return null;
  }

  public void setSchemaType (@Nullable final XdmSchemaType aSchemaType)
  {
    if (aSchemaType != null)
      throw new IllegalStateException ("A " + getKind () + " node has no schema type");
  }

  public final boolean isNilled ()
  {
    return m_aDetails != null && m_aDetails.m_bNilled;
  }

  public final void setNilled (final boolean bNilled)
  {
    if (bNilled || m_aDetails != null)
      _details ().m_bNilled = bNilled;
  }

  @Nullable
  public final String getDocumentUri ()
  {
    return m_aDetails == null ? null : m_aDetails.m_sDocumentUri;
  }

  public final void setDocumentUri (@Nullable final String sDocumentUri)
  {
    if (sDocumentUri != null || m_aDetails != null)
      _details ().m_sDocumentUri = sDocumentUri;
  }

  @Nullable
  public final String getBaseUri ()
  {
    return m_aDetails == null ? null : m_aDetails.m_sBaseUri;
  }

  public final void setBaseUri (@Nullable final String sBaseUri)
  {
    if (sBaseUri != null || m_aDetails != null)
      _details ().m_sBaseUri = sBaseUri;
  }

  public final boolean isDisableOutputEscaping ()
  {
    return m_aDetails != null && m_aDetails.m_bDisableOutputEscaping;
  }

  public final void setDisableOutputEscaping (final boolean bDisableOutputEscaping)
  {
    if (bDisableOutputEscaping || m_aDetails != null)
      _details ().m_bDisableOutputEscaping = bDisableOutputEscaping;
  }

  public void addUnparsedEntity (@NonNull final String sName,
                                 @NonNull final String sSystemId,
                                 @Nullable final String sPublicId)
  {
    ValueEnforcer.notNull (sName, "Name");
    ValueEnforcer.notNull (sSystemId, "SystemId");
    throw new IllegalStateException ("Only a document node has unparsed entities");
  }

  @Nullable
  public String [] getUnparsedEntity (@NonNull final String sName)
  {
    return null;
  }

  public void addParsedEntityName (@NonNull final String sName)
  {
    ValueEnforcer.notNull (sName, "Name");
    throw new IllegalStateException ("Only a document node has entities");
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getAllParsedEntityNames ()
  {
    return new CommonsArrayList <> ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getAllUnparsedEntityNames ()
  {
    return new CommonsArrayList <> ();
  }

  public void appendChild (@NonNull final IXdmNode aChild)
  {
    ValueEnforcer.notNull (aChild, "Child");
    throw new IllegalStateException ("A " + getKind () + " node cannot have children");
  }

  public void removeChild (@NonNull final IXdmNode aChild)
  {
    ValueEnforcer.notNull (aChild, "Child");
    throw new IllegalArgumentException ("Not a child of this node");
  }

  public void addAttribute (@NonNull final IXdmNode aAttribute)
  {
    ValueEnforcer.notNull (aAttribute, "Attribute");
    throw new IllegalStateException ("Only an element can have attributes");
  }

  public void declareNamespace (@NonNull final String sPrefix, @NonNull final String sNamespaceUri)
  {
    ValueEnforcer.notNull (sPrefix, "Prefix");
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    throw new IllegalStateException ("Only an element can declare namespaces");
  }

  @Nonnegative
  public int getChildCount ()
  {
    return 0;
  }

  @NonNull
  public AbstractXdmNode getChildAtIndex (@Nonnegative final int nIndex)
  {
    throw new IndexOutOfBoundsException ("No children");
  }

  @Nonnegative
  public int getAttributeCount ()
  {
    return 0;
  }

  @NonNull
  public AbstractXdmNode getAttributeAtIndex (@Nonnegative final int nIndex)
  {
    throw new IndexOutOfBoundsException ("No attributes");
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IXdmNode> getAllChildren ()
  {
    return new CommonsArrayList <> ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IXdmNode> getAllAttributes ()
  {
    return new CommonsArrayList <> ();
  }

  @Nullable
  public IXdmNode getAttribute (@Nullable final String sNamespaceUri, @NonNull final String sLocalName)
  {
    return null;
  }

  @NonNull
  @ReturnsMutableCopy
  public final ICommonsOrderedMap <String, String> getDeclaredNamespaces ()
  {
    final ICommonsOrderedMap <String, String> aNamespaces = _getNamespaces ();
    return aNamespaces == null ? new CommonsLinkedHashMap <> () : aNamespaces.getClone ();
  }

  @Nullable
  public final String lookupNamespaceUri (@NonNull final String sPrefix)
  {
    ValueEnforcer.notNull (sPrefix, "Prefix");
    if ("xml".equals (sPrefix))
      return XdmConstants.NS_XML;
    for (AbstractXdmNode aCur = this; aCur != null; aCur = aCur.m_aParent)
    {
      final ICommonsOrderedMap <String, String> aNamespaces = aCur._getNamespaces ();
      if (aNamespaces != null)
      {
        final String sUri = aNamespaces.get (sPrefix);
        if (sUri != null)
          return sUri.isEmpty () ? null : sUri;
      }
    }
    return null;
  }

  @NonNull
  @ReturnsMutableCopy
  public final ICommonsOrderedMap <String, String> getInScopeNamespaces ()
  {
    // Collect from the root downwards so that closer declarations overwrite
    final ArrayDeque <AbstractXdmNode> aPath = new ArrayDeque <> ();
    for (AbstractXdmNode aCur = this; aCur != null; aCur = aCur.m_aParent)
      aPath.push (aCur);
    final ICommonsOrderedMap <String, String> ret = new CommonsLinkedHashMap <> ();
    ret.put ("xml", XdmConstants.NS_XML);
    for (final AbstractXdmNode aNode : aPath)
    {
      final ICommonsOrderedMap <String, String> aNamespaces = aNode._getNamespaces ();
      if (aNamespaces != null)
        for (final var aEntry : aNamespaces.entrySet ())
          if (aEntry.getValue ().isEmpty ())
            ret.remove (aEntry.getKey ());
          else
            ret.put (aEntry.getKey (), aEntry.getValue ());
    }
    return ret;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IXdmNode> getNamespaceNodes ()
  {
    return new CommonsArrayList <> ();
  }

  @NonNull
  public final AbstractXdmNode getRoot ()
  {
    AbstractXdmNode ret = this;
    while (ret.m_aParent != null)
      ret = ret.m_aParent;
    return ret;
  }

  @Nullable
  public final AbstractXdmNode getOwnerDocument ()
  {
    final AbstractXdmNode aRoot = getRoot ();
    return aRoot.isDocument () ? aRoot : null;
  }

  public final void assignDocumentOrder ()
  {
    final AbstractXdmNode aRoot = getRoot ();
    final long nTree = TREE_COUNTER.incrementAndGet ();
    int nOrder = 0;
    final ArrayDeque <AbstractXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final AbstractXdmNode aCur = aStack.pop ();
      aCur.m_nTree = nTree;
      aCur.m_nOrder = nOrder++;
      for (int i = 0; i < aCur.getAttributeCount (); ++i)
      {
        final AbstractXdmNode aAttr = aCur.getAttributeAtIndex (i);
        aAttr.m_nTree = nTree;
        aAttr.m_nOrder = nOrder++;
      }
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
  }

  static int compareDocumentOrder (@NonNull final AbstractXdmNode aLeft, @NonNull final AbstractXdmNode aRight)
  {
    if (aLeft == aRight)
      return 0;
    // A namespace node with a parent is not numbered itself: it sorts directly
    // after its parent element, before the attributes of that element
    final AbstractXdmNode aLeftAnchor = aLeft instanceof XdmNamespaceNode && aLeft.m_aParent != null ? aLeft.m_aParent
                                                                                                      : aLeft;
    final AbstractXdmNode aRightAnchor = aRight instanceof XdmNamespaceNode && aRight.m_aParent != null ? aRight.m_aParent
                                                                                                        : aRight;
    if (aLeftAnchor.m_nTree == 0)
      aLeftAnchor.assignDocumentOrder ();
    if (aRightAnchor.m_nTree == 0)
      aRightAnchor.assignDocumentOrder ();
    if (aLeftAnchor.m_nTree != aRightAnchor.m_nTree)
      return Long.compare (aLeftAnchor.m_nTree, aRightAnchor.m_nTree);
    if (aLeftAnchor.m_nOrder != aRightAnchor.m_nOrder)
      return Integer.compare (aLeftAnchor.m_nOrder, aRightAnchor.m_nOrder);
    final int nLeftSub = aLeftAnchor != aLeft && aLeft instanceof final XdmNamespaceNode aLeftNS ? 1 +
                                                                                                    aLeftNS.getNamespaceIndex ()
                                                                                                  : 0;
    final int nRightSub = aRightAnchor != aRight && aRight instanceof final XdmNamespaceNode aRightNS ? 1 +
                                                                                                        aRightNS.getNamespaceIndex ()
                                                                                                      : 0;
    return Integer.compare (nLeftSub, nRightSub);
  }

  @Override
  public String toString ()
  {
    final XdmQName aName = getName ();
    return getKind () + (aName != null ? " " + aName : "");
  }
}
