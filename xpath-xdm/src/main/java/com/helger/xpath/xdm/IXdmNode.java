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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;

/**
 * A node of the XPath data model. Nodes are mutable while a tree is built and
 * must be treated as read-only afterwards. Document order is only defined
 * after {@link #assignDocumentOrder()} was called on the root, which
 * {@link XdmTreeBuilder} does; {@link #compareDocumentOrder(IXdmNode, IXdmNode)}
 * numbers an unnumbered tree on demand.<br>
 * Every node kind has its own implementation class that only carries the
 * fields of that kind; a property a kind does not have reads as its default
 * value. Namespace nodes are not stored: an element only keeps the namespace
 * declarations it carries itself, see {@link #getInScopeNamespaces()}. The
 * namespace axis is materialised on demand by {@link #getNamespaceNodes()}.
 * <p>
 * All traversals are iterative, so the depth of a tree never threatens the
 * Java stack.
 *
 * @author Philip Helger
 */
public sealed interface IXdmNode extends IXdmItem permits AbstractXdmNode
{
  @NonNull
  static IXdmNode createDocument ()
  {
    return new XdmDocumentNode ();
  }

  @NonNull
  static IXdmNode createElement (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    return new XdmElementNode (aName);
  }

  @NonNull
  static IXdmNode createAttribute (@NonNull final XdmQName aName, @NonNull final String sValue)
  {
    ValueEnforcer.notNull (aName, "Name");
    ValueEnforcer.notNull (sValue, "Value");
    return new XdmAttributeNode (aName, sValue);
  }

  @NonNull
  static IXdmNode createText (@NonNull final String sValue)
  {
    ValueEnforcer.notNull (sValue, "Value");
    return new XdmTextNode (sValue);
  }

  @NonNull
  static IXdmNode createComment (@NonNull final String sValue)
  {
    ValueEnforcer.notNull (sValue, "Value");
    return new XdmCommentNode (sValue);
  }

  /**
   * @param sTarget
   *        The PI target; becomes the local name of the node name. Never empty.
   * @param sData
   *        The PI data. Never <code>null</code>.
   * @return A new processing instruction node.
   */
  @NonNull
  static IXdmNode createProcessingInstruction (@NonNull final String sTarget, @NonNull final String sData)
  {
    ValueEnforcer.notNull (sData, "Data");
    return new XdmProcessingInstructionNode (new XdmQName (sTarget), sData);
  }

  /**
   * Create a parentless namespace node. The namespace nodes of an element are
   * obtained with {@link #getNamespaceNodes()}, which links them to the
   * element.
   *
   * @param sPrefix
   *        The prefix; the empty string for the default namespace. It becomes
   *        the local name of the node name (no namespace); the default
   *        namespace node has no name.
   * @param sNamespaceUri
   *        The namespace URI, the string value of the node. Never
   *        <code>null</code>.
   * @return A new namespace node.
   */
  @NonNull
  static IXdmNode createNamespace (@NonNull final String sPrefix, @NonNull final String sNamespaceUri)
  {
    ValueEnforcer.notNull (sPrefix, "Prefix");
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    return new XdmNamespaceNode (sPrefix, sNamespaceUri, 0);
  }

  /**
   * Compare two nodes in document order. Nodes of different trees are ordered
   * by the creation order of their trees, which is stable but otherwise
   * implementation dependent as XPath allows.
   *
   * @param aLeft
   *        First node. May not be <code>null</code>.
   * @param aRight
   *        Second node. May not be <code>null</code>.
   * @return negative, 0 or positive like {@link Comparable#compareTo(Object)}.
   */
  static int compareDocumentOrder (@NonNull final IXdmNode aLeft, @NonNull final IXdmNode aRight)
  {
    return AbstractXdmNode.compareDocumentOrder ((AbstractXdmNode) aLeft, (AbstractXdmNode) aRight);
  }

  @NonNull
  ENodeKind getKind ();

  default boolean isDocument ()
  {
    return getKind () == ENodeKind.DOCUMENT;
  }

  default boolean isElement ()
  {
    return getKind () == ENodeKind.ELEMENT;
  }

  default boolean isAttribute ()
  {
    return getKind () == ENodeKind.ATTRIBUTE;
  }

  default boolean isText ()
  {
    return getKind () == ENodeKind.TEXT;
  }

  /**
   * @return The node name. Only elements, attributes and processing
   *         instructions have one (for the latter the local name is the
   *         target); <code>null</code> for all other kinds.
   */
  @Nullable
  XdmQName getName ();

  /**
   * @return The raw value: the content of attributes, text, comments and
   *         processing instructions. <code>null</code> for documents and
   *         elements, use {@link #getStringValue()} for those.
   */
  @Nullable
  String getValue ();

  void setValue (@NonNull String sValue);

  @Nullable
  IXdmNode getParent ();

  @Nonnegative
  int getLine ();

  @Nonnegative
  int getColumn ();

  void setLocation (@Nonnegative int nLine, @Nonnegative int nColumn);

  /**
   * @return The ID kind of an attribute (from its DTD or schema type) or of
   *         an element of a schema-validated tree (from its schema type);
   *         {@link EIDKind#NONE} for all other nodes.
   */
  @NonNull
  EIDKind getIDKind ();

  /**
   * @param eIDKind
   *        The ID kind. Only elements and attributes accept a value other
   *        than {@link EIDKind#NONE}.
   */
  void setIDKind (@NonNull EIDKind eIDKind);

  /**
   * @return The name of the type annotation of an element or attribute node,
   *         or <code>null</code> for the default of an unvalidated node
   *         (<code>xs:untyped</code> for elements,
   *         <code>xs:untypedAtomic</code> for attributes). For a node of a
   *         schema-validated tree it is the name of its
   *         {@link #getSchemaType() schema type} (anonymous types have a
   *         local name starting with <code>#</code>); otherwise only built-in
   *         schema types are used, e.g. by the result tree of
   *         fn:analyze-string.
   */
  @Nullable
  XdmQName getTypeAnnotation ();

  /**
   * Annotate the node with a built-in schema type, without schema
   * information. Ignored while a {@link #getSchemaType() schema type} is set.
   *
   * @param aTypeAnnotation
   *        The type name, <code>null</code> for the default.
   */
  void setTypeAnnotation (@Nullable XdmQName aTypeAnnotation);

  /**
   * @return The schema type an element or attribute node was validated
   *         against, or <code>null</code> if the node was not validated.
   */
  @Nullable
  XdmSchemaType getSchemaType ();

  /**
   * @param aSchemaType
   *        The schema type. Only elements and attributes accept a value other
   *        than <code>null</code>.
   */
  void setSchemaType (@Nullable XdmSchemaType aSchemaType);

  /**
   * @return The XDM <code>nilled</code> property: <code>true</code> for a
   *         validated element with <code>xsi:nil="true"</code>. Always
   *         <code>false</code> for other nodes.
   */
  boolean isNilled ();

  void setNilled (boolean bNilled);

  /**
   * @return The URI the document was read from. Only set on document nodes.
   */
  @Nullable
  String getDocumentUri ();

  void setDocumentUri (@Nullable String sDocumentUri);

  /**
   * @return The explicit base URI of this node, or <code>null</code>. It is set
   *         where the base URI does not follow from the parent: on nodes read
   *         from an external entity (the entity's URI) and on document nodes
   *         whose base URI differs from their document URI. An
   *         <code>xml:base</code> attribute of the node itself is resolved
   *         against it.
   */
  @Nullable
  String getBaseUri ();

  void setBaseUri (@Nullable String sBaseUri);

  /**
   * @return <code>true</code> if this text node is written without escaping
   *         by the XML and HTML output methods (XSLT disable-output-escaping).
   */
  boolean isDisableOutputEscaping ();

  void setDisableOutputEscaping (boolean bDisableOutputEscaping);

  /**
   * Record an unparsed entity declared in the DTD of a document.
   *
   * @param sName
   *        Entity name.
   * @param sSystemId
   *        The (absolute) system identifier.
   * @param sPublicId
   *        The public identifier or <code>null</code>.
   */
  void addUnparsedEntity (@NonNull String sName, @NonNull String sSystemId, @Nullable String sPublicId);

  /**
   * @param sName
   *        Entity name.
   * @return The system identifier (index 0) and public identifier (index 1,
   *         possibly <code>null</code>) of an unparsed entity of this document
   *         node, or <code>null</code> if there is none.
   */
  @Nullable
  String [] getUnparsedEntity (@NonNull String sName);

  /**
   * Record a parsed general entity declared in the DTD of a document.
   *
   * @param sName
   *        Entity name.
   */
  void addParsedEntityName (@NonNull String sName);

  /** @return The names of the parsed general entities of this document node. */
  @NonNull
  @ReturnsMutableCopy
  ICommonsList <String> getAllParsedEntityNames ();

  /** @return The names of the unparsed entities of this document node. */
  @NonNull
  @ReturnsMutableCopy
  ICommonsList <String> getAllUnparsedEntityNames ();

  void appendChild (@NonNull IXdmNode aChild);

  /**
   * Remove a child node. The document order must be assigned again afterwards
   * if the tree was numbered.
   *
   * @param aChild
   *        The child to remove. May not be <code>null</code>.
   */
  void removeChild (@NonNull IXdmNode aChild);

  void addAttribute (@NonNull IXdmNode aAttribute);

  /**
   * Declare a namespace on this element.
   *
   * @param sPrefix
   *        The prefix; the empty string for the default namespace.
   * @param sNamespaceUri
   *        The namespace URI. The empty string undeclares the default
   *        namespace.
   */
  void declareNamespace (@NonNull String sPrefix, @NonNull String sNamespaceUri);

  @Nonnegative
  int getChildCount ();

  @NonNull
  IXdmNode getChildAtIndex (@Nonnegative int nIndex);

  @Nonnegative
  int getAttributeCount ();

  @NonNull
  IXdmNode getAttributeAtIndex (@Nonnegative int nIndex);

  @NonNull
  @ReturnsMutableCopy
  ICommonsList <IXdmNode> getAllChildren ();

  @NonNull
  @ReturnsMutableCopy
  ICommonsList <IXdmNode> getAllAttributes ();

  /**
   * Find an attribute by expanded name.
   *
   * @param sNamespaceUri
   *        The namespace URI. <code>null</code> or empty for no namespace.
   * @param sLocalName
   *        The local name to search.
   * @return <code>null</code> if no such attribute exists.
   */
  @Nullable
  IXdmNode getAttribute (@Nullable String sNamespaceUri, @NonNull String sLocalName);

  /**
   * @return The namespace declarations of this element only, prefix to URI,
   *         in declaration order.
   */
  @NonNull
  @ReturnsMutableCopy
  ICommonsOrderedMap <String, String> getDeclaredNamespaces ();

  /**
   * Resolve a prefix against the declarations of this node and its ancestors.
   *
   * @param sPrefix
   *        The prefix, empty for the default namespace.
   * @return The namespace URI, or <code>null</code> if the prefix is not
   *         bound. The <code>xml</code> prefix is always bound. A default
   *         namespace that was undeclared yields <code>null</code>.
   */
  @Nullable
  String lookupNamespaceUri (@NonNull String sPrefix);

  /**
   * @return All namespaces in scope for this node (the <code>xml</code> prefix
   *         included), prefix to URI. Closer declarations win and undeclared
   *         default namespaces are left out.
   */
  @NonNull
  @ReturnsMutableCopy
  ICommonsOrderedMap <String, String> getInScopeNamespaces ();

  /**
   * The namespace axis of an element: one namespace node per in-scope
   * namespace (see {@link #getInScopeNamespaces()}), each with this element as
   * parent. The nodes are created on first access and then kept, so repeated
   * calls return identical nodes (node identity matters for <code>is</code>
   * and duplicate elimination). In document order they come after the element
   * and before its attributes. Call only once the tree is complete.
   *
   * @return The namespace nodes in a stable order; empty for nodes other than
   *         elements.
   */
  @NonNull
  @ReturnsMutableCopy
  ICommonsList <IXdmNode> getNamespaceNodes ();

  @NonNull
  IXdmNode getRoot ();

  /**
   * @return The document this node belongs to, or <code>null</code> if the tree
   *         is rooted in a parentless node of another kind.
   */
  @Nullable
  IXdmNode getOwnerDocument ();

  /**
   * @return The XDM string value: for documents and elements the concatenation
   *         of all descendant text nodes in document order, otherwise the raw
   *         value.
   */
  @NonNull
  String getStringValue ();

  /**
   * Number all nodes of the tree this node belongs to in document order: an
   * element comes first, then its attributes, then its children. Numbering
   * starts at the root, so calling this on any node is fine. It must be called
   * again after the structure was changed.
   */
  void assignDocumentOrder ();
}
