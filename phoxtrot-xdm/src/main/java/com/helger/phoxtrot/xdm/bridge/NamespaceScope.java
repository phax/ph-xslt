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
import java.util.Arrays;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phoxtrot.xdm.XdmConstants;

/**
 * The namespace bindings in scope during a walk over a tree: one frame per
 * open element, each binding a prefix (the empty string for the default
 * namespace) to a URI (the empty string undeclares the default namespace).
 * A lookup costs the same at any depth: every prefix has its own stack of
 * URIs. Prefixes bound by no frame are looked up in an optional outer scope
 * (e.g. the DOM node a converted tree is appended to).
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class NamespaceScope
{
  private final ICommonsMap <String, ArrayDeque <String>> m_aBindings = new CommonsHashMap <> ();
  // The prefixes bound by the open frames, in binding order
  private final ICommonsList <String> m_aBound = new CommonsArrayList <> ();
  // The size of m_aBound when each open frame started
  private int [] m_aFrameStarts = new int [32];
  private int m_nFrames;
  private final UnaryOperator <String> m_aOuter;

  /**
   * @param aOuter
   *        Gives the URI of a prefix that no frame binds (<code>null</code>
   *        if unbound), or <code>null</code> for no outer scope.
   */
  NamespaceScope (@Nullable final UnaryOperator <String> aOuter)
  {
    m_aOuter = aOuter;
  }

  /** Open a frame for an element; bindings made now belong to it. */
  void pushFrame ()
  {
    if (m_nFrames == m_aFrameStarts.length)
      m_aFrameStarts = Arrays.copyOf (m_aFrameStarts, m_nFrames * 2);
    m_aFrameStarts[m_nFrames++] = m_aBound.size ();
  }

  /** Close the innermost frame and drop the bindings it made. */
  void popFrame ()
  {
    final int nStart = m_aFrameStarts[--m_nFrames];
    for (int i = m_aBound.size () - 1; i >= nStart; --i)
      m_aBindings.get (m_aBound.remove (i)).pop ();
  }

  /**
   * Bind a prefix in the innermost frame.
   *
   * @param sPrefix
   *        The prefix, empty for the default namespace.
   * @param sUri
   *        The URI, empty to undeclare the default namespace.
   */
  void bind (@NonNull final String sPrefix, @NonNull final String sUri)
  {
    m_aBindings.computeIfAbsent (sPrefix, k -> new ArrayDeque <> ()).push (sUri);
    m_aBound.add (sPrefix);
  }

  /**
   * @param sPrefix
   *        The prefix, empty for the default namespace.
   * @return The URI the innermost binding of the prefix gives, from the outer
   *         scope if no frame binds it; the XML namespace for
   *         <code>xml</code>; <code>null</code> if the prefix is unbound. An
   *         undeclared default namespace gives the empty string.
   */
  @Nullable
  String lookup (@NonNull final String sPrefix)
  {
    if ("xml".equals (sPrefix))
      return XdmConstants.NS_XML;
    final ArrayDeque <String> aStack = m_aBindings.get (sPrefix);
    if (aStack != null && !aStack.isEmpty ())
      return aStack.peek ();
    return m_aOuter == null ? null : m_aOuter.apply (sPrefix);
  }

  /**
   * @param sPrefix
   *        The prefix, empty for the default namespace.
   * @param sUri
   *        The URI, empty for no namespace.
   * @return <code>true</code> if the prefix is bound to exactly this URI; for
   *         the empty prefix and the empty URI also if no default namespace
   *         is in scope.
   */
  boolean isBoundTo (@NonNull final String sPrefix, @NonNull final String sUri)
  {
    final String sBound = lookup (sPrefix);
    if (sBound == null)
      return sPrefix.isEmpty () && sUri.isEmpty ();
    return sBound.equals (sUri);
  }

  /**
   * Find a prefix for an attribute in a namespace that has none (or one
   * that is bound to another URI): <code>ns0</code>, <code>ns1</code>, ...,
   * the first that is neither bound in scope nor used by the element.
   *
   * @param aTaken
   *        The prefixes the element binds or uses itself.
   * @return The new prefix. Never <code>null</code>.
   */
  @NonNull
  String createPrefix (@NonNull final ICommonsMap <String, String> aTaken)
  {
    int n = 0;
    while (true)
    {
      final String ret = "ns" + n;
      if (lookup (ret) == null && !aTaken.containsKey (ret))
        return ret;
      ++n;
    }
  }
}
