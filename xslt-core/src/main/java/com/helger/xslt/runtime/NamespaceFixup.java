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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Namespace fixup of constructed trees (XSLT 3.0 section 5.7.3): every element
 * gets the namespace declarations its own name and the names of its attributes
 * need, unless an ancestor already provides the same binding. It runs once on
 * a finished tree, so the bindings of all ancestors are known. Afterwards the
 * nodes of the tree are numbered in document order.
 *
 * @author Philip Helger
 */
@Immutable
public final class NamespaceFixup
{
  @PresentForCodeCoverage
  private static final NamespaceFixup INSTANCE = new NamespaceFixup ();

  private NamespaceFixup ()
  {}

  private static boolean _isBound (@NonNull final XdmNode aElement,
                                   @NonNull final String sPrefix,
                                   @NonNull final String sUri)
  {
    final String sCur = aElement.lookupNamespaceUri (sPrefix);
    return sCur == null ? sUri.isEmpty () : sCur.equals (sUri);
  }

  private static void _fixElement (@NonNull final XdmNode aElement)
  {
    final XdmQName aName = aElement.getName ();
    final String sPrefix = aName.prefix ();
    final String sUri = aName.namespaceUri ();
    if (!"xml".equals (sPrefix) && !_isBound (aElement, sPrefix, sUri))
    {
      // Only declare if the element does not declare this prefix itself with
      // another URI (that conflict was rejected when the namespace was added)
      final String sOwn = aElement.getDeclaredNamespaces ().get (sPrefix);
      if (sOwn == null || sOwn.isEmpty ())
        aElement.declareNamespace (sPrefix, sUri);
    }
    final int nAttrs = aElement.getAttributeCount ();
    for (int i = 0; i < nAttrs; ++i)
    {
      final XdmQName aAttrName = aElement.getAttributeAtIndex (i).getName ();
      if (aAttrName.hasNamespace () &&
          aAttrName.hasPrefix () &&
          !"xml".equals (aAttrName.prefix ()) &&
          !_isBound (aElement, aAttrName.prefix (), aAttrName.namespaceUri ()) &&
          (aElement.getDeclaredNamespaces ().get (aAttrName.prefix ()) == null ||
           aElement.getDeclaredNamespaces ().get (aAttrName.prefix ()).isEmpty ()))
        aElement.declareNamespace (aAttrName.prefix (), aAttrName.namespaceUri ());
    }
  }

  private static boolean _conflicts (@NonNull final XdmNode aElement,
                                     @NonNull final Map <String, String> aDeclared,
                                     @NonNull final String sPrefix,
                                     @NonNull final String sUri)
  {
    final String sOwn = aDeclared.get (sPrefix);
    if (sOwn != null && !sOwn.equals (sUri))
      return true;
    final XdmQName aElName = aElement.getName ();
    if (aElName.prefix ().equals (sPrefix) && !aElName.namespaceUri ().equals (sUri))
      return true;
    // Another attribute with this prefix and a different URI
    final int nAttrs = aElement.getAttributeCount ();
    for (int i = 0; i < nAttrs; ++i)
    {
      final XdmQName aOther = aElement.getAttributeAtIndex (i).getName ();
      if (aOther.prefix ().equals (sPrefix) && !aOther.namespaceUri ().equals (sUri))
        return true;
    }
    return false;
  }

  /**
   * @param aElement
   *        The element the attribute is added to.
   * @param aName
   *        The name of the attribute.
   * @return The name to use: a namespaced attribute needs a prefix that is not
   *         bound to another URI on the element itself. If the prefix is
   *         changed, the new binding is declared on the element.
   */
  @NonNull
  public static XdmQName getAttributeNameFor (@NonNull final XdmNode aElement, @NonNull final XdmQName aName)
  {
    if (!aName.hasNamespace ())
      return aName.hasPrefix () ? new XdmQName ("", aName.localName (), "") : aName;
    final String sUri = aName.namespaceUri ();
    if (XdmConstants.NS_XML.equals (sUri))
      return "xml".equals (aName.prefix ()) ? aName : new XdmQName (sUri, aName.localName (), "xml");
    final Map <String, String> aDeclared = aElement.getDeclaredNamespaces ();
    final String sPrefix = aName.prefix ();
    if (!sPrefix.isEmpty () && !_conflicts (aElement, aDeclared, sPrefix, sUri))
      return aName;
    // Reuse a prefix declared for the URI on the element, else make one up
    for (final Map.Entry <String, String> aEntry : aDeclared.entrySet ())
      if (!aEntry.getKey ().isEmpty () && aEntry.getValue ().equals (sUri))
        return new XdmQName (sUri, aName.localName (), aEntry.getKey ());
    final String sBase = sPrefix.isEmpty () ? "ns" : sPrefix;
    int n = 0;
    String sNew;
    do
    {
      sNew = sBase + "_" + n++;
    } while (_conflicts (aElement, aDeclared, sNew, sUri));
    aElement.declareNamespace (sNew, sUri);
    return new XdmQName (sUri, aName.localName (), sNew);
  }

  /**
   * inherit-namespaces="no" (XSLT 3.0 section 11.1.3): the element children of
   * a new element do not inherit its namespaces. Every namespace the element
   * declares and a child does not declare itself is undeclared on the child;
   * the fixup declares again what the child's names need.
   *
   * @param aElement
   *        The new element, with its content.
   */
  public static void applyNoInherit (@NonNull final XdmNode aElement)
  {
    final Map <String, String> aDeclared = aElement.getDeclaredNamespaces ();
    // The binding of the element's own name is a namespace node of it too
    if (!aDeclared.containsKey (aElement.getName ().prefix ()) && !"xml".equals (aElement.getName ().prefix ()))
      aDeclared.put (aElement.getName ().prefix (), aElement.getName ().namespaceUri ());
    for (int i = 0; i < aElement.getChildCount (); ++i)
    {
      final XdmNode aChild = aElement.getChildAtIndex (i);
      if (!aChild.isElement ())
        continue;
      final Map <String, String> aOwn = aChild.getDeclaredNamespaces ();
      for (final Map.Entry <String, String> aEntry : aDeclared.entrySet ())
        if (!aEntry.getValue ().isEmpty () && !aOwn.containsKey (aEntry.getKey ()))
          aChild.declareNamespace (aEntry.getKey (), "");
    }
  }

  /**
   * Fix the namespaces of a finished tree and number it in document order.
   *
   * @param aRoot
   *        The root of the tree (document, element or any other node).
   */
  public static void finish (@Nullable final XdmNode aRoot)
  {
    if (aRoot == null)
      return;
    if (aRoot.isElement () || aRoot.isDocument ())
    {
      final ArrayDeque <XdmNode> aStack = new ArrayDeque <> ();
      aStack.push (aRoot);
      while (!aStack.isEmpty ())
      {
        final XdmNode aCur = aStack.pop ();
        if (aCur.isElement ())
          _fixElement (aCur);
        final int nChildren = aCur.getChildCount ();
        for (int i = nChildren - 1; i >= 0; --i)
        {
          final XdmNode aChild = aCur.getChildAtIndex (i);
          if (aChild.isElement ())
            aStack.push (aChild);
        }
      }
    }
    aRoot.assignDocumentOrder ();
  }
}
