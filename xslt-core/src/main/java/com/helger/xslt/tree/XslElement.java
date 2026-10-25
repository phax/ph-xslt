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
package com.helger.xslt.tree;

import java.math.BigDecimal;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.fn.FnAccessor;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * An element of a stylesheet module after static processing: shadow
 * attributes are expanded, elements excluded by use-when are gone. The
 * namespaces and the source location come from the parsed source element.
 *
 * @author Philip Helger
 */
public final class XslElement extends XslNode
{
  /** XSLT 3.0 as a version number. */
  public static final BigDecimal VERSION_30 = new BigDecimal ("3.0");

  private final XdmNode m_aSource;
  private final XdmQName m_aName;
  private final ICommonsOrderedMap <XdmQName, String> m_aAttrs = new CommonsLinkedHashMap <> ();
  private final ICommonsList <XslNode> m_aChildren = new CommonsArrayList <> ();
  private ICommonsOrderedMap <String, String> m_aInScopeNamespaces;
  // Import precedence and the lowest precedence of the modules imported by the
  // module of this declaration; only set on top-level elements
  private int m_nPrecedence;
  private int m_nImportLo;

  public XslElement (@NonNull final XslModule aModule, @NonNull final XdmNode aSource)
  {
    super (aModule);
    m_aSource = aSource;
    m_aName = aSource.getName ();
  }

  /** @return The parsed source element (namespaces, base URI, location). */
  @NonNull
  public XdmNode getSource ()
  {
    return m_aSource;
  }

  @NonNull
  public XdmQName getName ()
  {
    return m_aName;
  }

  @NonNull
  public String getLocalName ()
  {
    return m_aName.localName ();
  }

  /** @return <code>true</code> if this element is in the XSLT namespace. */
  public boolean isXsl ()
  {
    return XsltNames.NS_XSL.equals (m_aName.namespaceUri ());
  }

  /**
   * @param sLocalName
   *        Local name.
   * @return <code>true</code> if this is the XSLT element with that local name.
   */
  public boolean isXsl (@NonNull final String sLocalName)
  {
    return isXsl () && m_aName.localName ().equals (sLocalName);
  }

  @Override
  public int getLine ()
  {
    return m_aSource.getLine ();
  }

  @Override
  public int getColumn ()
  {
    return m_aSource.getColumn ();
  }

  /**
   * @param sNamespaceUri
   *        Namespace URI, empty for none.
   * @param sLocalName
   *        Local name.
   * @return The attribute value or <code>null</code>.
   */
  @Nullable
  public String getAttribute (@NonNull final String sNamespaceUri, @NonNull final String sLocalName)
  {
    return m_aAttrs.get (new XdmQName (sNamespaceUri, sLocalName));
  }

  /**
   * @param sLocalName
   *        Local name of an attribute in no namespace.
   * @return The value or <code>null</code>.
   */
  @Nullable
  public String getAttr (@NonNull final String sLocalName)
  {
    return m_aAttrs.get (new XdmQName ("", sLocalName));
  }

  /**
   * @param sLocalName
   *        Local name of an attribute in no namespace.
   * @return <code>true</code> if present.
   */
  public boolean hasAttr (@NonNull final String sLocalName)
  {
    return m_aAttrs.containsKey (new XdmQName ("", sLocalName));
  }

  /**
   * @param sLocalName
   *        Name of a standard attribute (version, use-when, expand-text,
   *        ...).
   * @return Its value on this element: the attribute in no namespace on an
   *         XSLT element, the one in the XSLT namespace on any other element.
   */
  @Nullable
  public String getStandardAttr (@NonNull final String sLocalName)
  {
    return isXsl () ? getAttr (sLocalName) : getAttribute (XsltNames.NS_XSL, sLocalName);
  }

  /**
   * @param sLocalName
   *        Name of a standard attribute.
   * @return The value on the nearest ancestor-or-self element of the module
   *         that carries it, or <code>null</code>.
   */
  @Nullable
  public String getInheritedStandardAttr (@NonNull final String sLocalName)
  {
    for (XslElement aCur = this; aCur != null; aCur = aCur.getParent ())
    {
      final String s = aCur.getStandardAttr (sLocalName);
      if (s != null)
        return s;
    }
    return null;
  }

  /**
   * @param sLocalName
   *        Name of a standard attribute.
   * @return The nearest ancestor-or-self element carrying it, or
   *         <code>null</code>.
   */
  @Nullable
  public XslElement getStandardAttrOwner (@NonNull final String sLocalName)
  {
    for (XslElement aCur = this; aCur != null; aCur = aCur.getParent ())
      if (aCur.getStandardAttr (sLocalName) != null)
        return aCur;
    return null;
  }

  /** @return All attributes in source order (after shadow expansion). */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedMap <XdmQName, String> getAllAttributes ()
  {
    return m_aAttrs.getClone ();
  }

  /**
   * Set or replace an attribute.
   *
   * @param aName
   *        Name.
   * @param sValue
   *        Value.
   */
  public void setAttribute (@NonNull final XdmQName aName, @NonNull final String sValue)
  {
    // Replace by expanded name, keeping the position
    for (final Map.Entry <XdmQName, String> aEntry : m_aAttrs.entrySet ())
      if (aEntry.getKey ().equals (aName))
      {
        aEntry.setValue (sValue);
        return;
      }
    m_aAttrs.put (aName, sValue);
  }

  /**
   * @param aName
   *        Name of the attribute to remove.
   */
  public void removeAttribute (@NonNull final XdmQName aName)
  {
    m_aAttrs.remove (aName);
  }

  public void appendChild (@NonNull final XslNode aChild)
  {
    aChild.setParent (this);
    m_aChildren.add (aChild);
  }

  /** Remove the last child (an element excluded by use-when). */
  public void removeLastChild ()
  {
    final XslNode aChild = m_aChildren.removeLast ();
    aChild.setParent (null);
  }

  /** @return The children (elements and merged text). */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XslNode> getChildren ()
  {
    return m_aChildren.getClone ();
  }

  public int getChildCount ()
  {
    return m_aChildren.size ();
  }

  /** @return The element children only. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XslElement> getElementChildren ()
  {
    final ICommonsList <XslElement> ret = new CommonsArrayList <> ();
    for (final XslNode aChild : m_aChildren)
      if (aChild instanceof final XslElement aEl)
        ret.add (aEl);
    return ret;
  }

  /**
   * @param sLocalName
   *        Local name of XSLT elements.
   * @return All XSLT element children of that name.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XslElement> getXslChildren (@NonNull final String sLocalName)
  {
    final ICommonsList <XslElement> ret = new CommonsArrayList <> ();
    for (final XslNode aChild : m_aChildren)
      if (aChild instanceof final XslElement aEl && aEl.isXsl (sLocalName))
        ret.add (aEl);
    return ret;
  }

  /**
   * @param sPrefix
   *        The prefix, empty for the default namespace.
   * @return The namespace URI bound to it on this element, or
   *         <code>null</code> if unbound.
   */
  @Nullable
  public String lookupNamespaceUri (@NonNull final String sPrefix)
  {
    return m_aSource.lookupNamespaceUri (sPrefix);
  }

  /**
   * @return The in-scope namespaces of this element (prefix to URI, the
   *         default namespace under the empty prefix, <code>xml</code>
   *         included).
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedMap <String, String> getInScopeNamespaces ()
  {
    ICommonsOrderedMap <String, String> ret = m_aInScopeNamespaces;
    if (ret == null)
      m_aInScopeNamespaces = ret = m_aSource.getInScopeNamespaces ();
    return ret.getClone ();
  }

  /**
   * @return The static base URI of this element (module URI and
   *         <code>xml:base</code>), or <code>null</code>.
   */
  @Nullable
  public String getStaticBaseUri ()
  {
    return FnAccessor.getBaseUri (m_aSource);
  }

  /**
   * @return The effective [xsl:]version of this element (inherited), or
   *         {@link #VERSION_30} if none is given or it is not a number.
   */
  @NonNull
  public BigDecimal getEffectiveVersion ()
  {
    final String s = getInheritedStandardAttr ("version");
    if (s != null)
    {
      final String sTrimmed = s.trim ();
      if (XsltNames.isDecimalLexical (sTrimmed))
        return new BigDecimal (sTrimmed);
    }
    return VERSION_30;
  }

  /**
   * @return <code>true</code> if forwards-compatible processing is enabled for
   *         this element (effective version greater than 3.0).
   */
  public boolean isForwardsCompatible ()
  {
    return getEffectiveVersion ().compareTo (VERSION_30) > 0;
  }

  /**
   * Resolve a lexical QName or EQName written in an attribute of this element.
   *
   * @param sLexical
   *        The name, surrounding whitespace allowed.
   * @param bUseDefaultNamespace
   *        <code>true</code> if an unprefixed name is in the default namespace
   *        (element names), <code>false</code> for no namespace.
   * @return The expanded name or <code>null</code> if the name is invalid or
   *         its prefix is unbound.
   */
  @Nullable
  public XdmQName resolveQName (@NonNull final String sLexical, final boolean bUseDefaultNamespace)
  {
    final String s = sLexical.trim ();
    if (s.startsWith ("Q{"))
    {
      if (!XsltNames.isEQName (s))
        return null;
      final int n = s.indexOf ('}');
      return new XdmQName (s.substring (2, n).trim (), s.substring (n + 1));
    }
    if (!XsltNames.isLexicalQName (s))
      return null;
    final int n = s.indexOf (':');
    if (n < 0)
    {
      String sUri = "";
      if (bUseDefaultNamespace)
      {
        final String sDefault = lookupNamespaceUri ("");
        if (sDefault != null)
          sUri = sDefault;
      }
      return new XdmQName (sUri, s, "");
    }
    final String sPrefix = s.substring (0, n);
    final String sUri = lookupNamespaceUri (sPrefix);
    if (sUri == null)
      return null;
    return new XdmQName (sUri, s.substring (n + 1), sPrefix);
  }

  /**
   * @return Import precedence of a top-level declaration (higher wins).
   */
  public int getPrecedence ()
  {
    return m_nPrecedence;
  }

  /**
   * @return The lowest import precedence of the modules imported (directly or
   *         indirectly) by the module of this top-level declaration.
   */
  public int getImportLo ()
  {
    return m_nImportLo;
  }

  public void setPrecedence (final int nPrecedence, final int nImportLo)
  {
    m_nPrecedence = nPrecedence;
    m_nImportLo = nImportLo;
  }

  /**
   * @return The top-level element (child of the module root) this element is
   *         in, or the module root itself.
   */
  @NonNull
  public XslElement getTopLevelElement ()
  {
    XslElement ret = this;
    while (ret.getParent () != null && ret.getParent ().getParent () != null)
      ret = ret.getParent ();
    return ret;
  }

  @Override
  public String toString ()
  {
    return "<" + m_aName.getLexicalName () + ">";
  }
}
