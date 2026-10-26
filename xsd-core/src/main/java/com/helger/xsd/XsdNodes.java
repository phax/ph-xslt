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
package com.helger.xsd;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.xdm.XdmNode;

/**
 * Helpers over the {@link XdmNode} tree of a schema document (the go-xslt
 * <code>xmltree</code> accessors the loader uses).
 *
 * @author Philip Helger
 */
@Immutable
final class XsdNodes
{
  @PresentForCodeCoverage
  private static final XsdNodes INSTANCE = new XsdNodes ();

  private XsdNodes ()
  {}

  /** @return <code>true</code> if the node is an element in the XSD namespace. */
  static boolean isXSElement (@Nullable final XdmNode aNode)
  {
    return aNode != null && aNode.isElement () && XsdConstants.NS_XS.equals (aNode.getName ().namespaceUri ());
  }

  /** @return <code>true</code> if the node is the XSD element with the given local name. */
  static boolean isXS (@Nullable final XdmNode aNode, @NonNull final String sLocal)
  {
    return isXSElement (aNode) && sLocal.equals (aNode.getName ().localName ());
  }

  /** @return The local name of an element or attribute. */
  @NonNull
  static String localName (@NonNull final XdmNode aNode)
  {
    return aNode.getName ().localName ();
  }

  /** @return The namespace URI of an element or attribute, empty for none. */
  @NonNull
  static String namespaceUri (@NonNull final XdmNode aNode)
  {
    return aNode.getName ().namespaceUri ();
  }

  /** @return The children of a node, in order (a copy). */
  @NonNull
  @ReturnsMutableCopy
  static ICommonsList <XdmNode> children (@NonNull final XdmNode aNode)
  {
    return aNode.getAllChildren ();
  }

  /** @return The attributes of an element, in order (a copy). */
  @NonNull
  @ReturnsMutableCopy
  static ICommonsList <XdmNode> attributes (@NonNull final XdmNode aNode)
  {
    return aNode.getAllAttributes ();
  }

  /** @return The first XSD child element with the given local name, or <code>null</code>. */
  @Nullable
  static XdmNode firstXSChild (@NonNull final XdmNode aNode, @NonNull final String sLocal)
  {
    final int nCount = aNode.getChildCount ();
    for (int i = 0; i < nCount; ++i)
    {
      final XdmNode aChild = aNode.getChildAtIndex (i);
      if (isXS (aChild, sLocal))
        return aChild;
    }
    return null;
  }

  /**
   * @return The value of the no-namespace attribute with the given local name,
   *         or <code>null</code> if absent (go-xslt <code>AttrLocal</code>).
   */
  @Nullable
  static String attr (@NonNull final XdmNode aNode, @NonNull final String sLocal)
  {
    final XdmNode aAttr = aNode.getAttribute (null, sLocal);
    return aAttr == null ? null : aAttr.getValue ();
  }

  /** @return The value of the no-namespace attribute, or the empty string if absent. */
  @NonNull
  static String attrOrEmpty (@NonNull final XdmNode aNode, @NonNull final String sLocal)
  {
    final String ret = attr (aNode, sLocal);
    return ret == null ? "" : ret;
  }

  /** @return <code>true</code> if the no-namespace attribute is present. */
  static boolean hasAttr (@NonNull final XdmNode aNode, @NonNull final String sLocal)
  {
    return aNode.getAttribute (null, sLocal) != null;
  }

  /** @return The value of the attribute with the given expanded name, or <code>null</code>. */
  @Nullable
  static String attrNS (@NonNull final XdmNode aNode, @NonNull final String sNamespaceUri, @NonNull final String sLocal)
  {
    final XdmNode aAttr = aNode.getAttribute (sNamespaceUri, sLocal);
    return aAttr == null ? null : aAttr.getValue ();
  }

  /**
   * go-xslt <code>attrIs</code>: compare the raw attribute value; for
   * <code>true</code> also <code>1</code> matches.
   */
  static boolean attrIs (@NonNull final XdmNode aNode, @NonNull final String sName, @NonNull final String sWant)
  {
    final String v = attrOrEmpty (aNode, sName);
    if ("true".equals (sWant))
      return "true".equals (v) || "1".equals (v);
    return sWant.equals (v);
  }

  /**
   * go-xslt <code>boolAttrTrue</code>: an xs:boolean attribute read with
   * whitespace collapse.
   */
  static boolean boolAttrTrue (@NonNull final XdmNode aNode, @NonNull final String sName)
  {
    final String v = attr (aNode, sName);
    if (v == null)
      return false;
    final String s = XsdWhiteSpace.collapse (v);
    return "true".equals (s) || "1".equals (s);
  }

  /**
   * Resolve a prefix against the in-scope namespaces of a node.
   *
   * @return The namespace URI, or <code>null</code> if the prefix is unbound.
   *         The default namespace when undeclared yields <code>null</code>.
   */
  @Nullable
  static String lookupPrefix (@NonNull final XdmNode aNode, @NonNull final String sPrefix)
  {
    return aNode.lookupNamespaceUri (sPrefix);
  }

  /**
   * go-xslt <code>resolveQName</code>: expand a lexical QName against the
   * namespaces in scope at the node; an unbound prefix maps to no namespace.
   */
  @NonNull
  static XsdName resolveQName (@NonNull final XdmNode aNode, @NonNull final String sQName)
  {
    final String q = sQName.strip ();
    final int i = q.indexOf (':');
    if (i >= 0)
    {
      final String sNS = lookupPrefix (aNode, q.substring (0, i));
      return new XsdName (sNS == null ? "" : sNS, q.substring (i + 1));
    }
    final String sNS = lookupPrefix (aNode, "");
    return new XsdName (sNS == null ? "" : sNS, q);
  }

  /**
   * go-xslt <code>declName</code>: a declaration's <code>@name</code> with the
   * whiteSpace facet of xs:NCName (collapse) applied.
   */
  @NonNull
  static String declName (@NonNull final XdmNode aNode)
  {
    return XsdWhiteSpace.collapse (attrOrEmpty (aNode, "name"));
  }

  /** @return The in-scope namespaces of the node, prefix to URI. */
  @NonNull
  @ReturnsMutableCopy
  static ICommonsOrderedMap <String, String> inScopeNamespaces (@NonNull final XdmNode aNode)
  {
    return aNode.getInScopeNamespaces ();
  }

  /** @return The document element of a document node (or the element itself). */
  @Nullable
  static XdmNode rootElement (@NonNull final XdmNode aNode)
  {
    if (aNode.isElement ())
      return aNode;
    final int nCount = aNode.getChildCount ();
    for (int i = 0; i < nCount; ++i)
    {
      final XdmNode aChild = aNode.getChildAtIndex (i);
      if (aChild.isElement ())
        return aChild;
    }
    return null;
  }

  /**
   * @return <code>true</code> if the node is xs:schema, xs:redefine or
   *         xs:override (so a component directly under it is global).
   */
  static boolean isTopLevelParent (@Nullable final XdmNode aParent)
  {
    return isXS (aParent, "schema") || isXS (aParent, "redefine") || isXS (aParent, "override");
  }

  /** @return A short description of a node for messages. */
  @NonNull
  static String describe (@NonNull final XdmNode aNode)
  {
    return aNode.getName () == null ? aNode.getKind ().toString () : aNode.getName ().getLexicalName ();
  }
}
