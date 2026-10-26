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

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * An <code>xs:any</code> or <code>xs:anyAttribute</code> - go-xslt
 * <code>wildcard</code>. The string-valued properties keep go-xslt's values
 * (see the constants).
 *
 * @author Philip Helger
 */
final class XsdWildcard implements IXsdTerm
{
  static final String NS_ANY = "any";
  static final String NS_OTHER = "other";
  static final String NS_LIST = "list";

  static final String PROCESS_STRICT = "strict";
  static final String PROCESS_LAX = "lax";
  static final String PROCESS_SKIP = "skip";

  /** {@link #NS_ANY}, {@link #NS_OTHER} or {@link #NS_LIST}. */
  String m_sNsMode = "";
  /** For {@link #NS_LIST}: the namespaces, <code>##local</code> as the empty string. */
  ICommonsList <String> m_aNamespaces = new CommonsArrayList <> ();
  /** The schema target namespace (for <code>##other</code>). */
  String m_sTargetNS = "";
  /** strict, lax or skip (the raw attribute value). */
  String m_sProcess = "";
  /** XSD 1.1 <code>@notNamespace</code> (<code>##local</code> as the empty string). */
  ICommonsList <String> m_aNotNS = new CommonsArrayList <> ();
  /** XSD 1.1 <code>@notQName</code> names, <code>##definedSibling</code> filled in later. */
  ICommonsList <XsdName> m_aNotNames = new CommonsArrayList <> ();
  boolean m_bNotSibling;
  /** <code>@notQName</code> listed <code>##defined</code>; its expansion is {@link #m_aNotDefNames}. */
  boolean m_bNotDefined;
  ICommonsList <XsdName> m_aNotDefNames = new CommonsArrayList <> ();
  /**
   * When non-empty this is a union wildcard: a name matches iff it matches any
   * part.
   */
  ICommonsList <XsdWildcard> m_aParts = new CommonsArrayList <> ();

  /** @return A new <code>##any</code> lax wildcard. */
  @NonNull
  static XsdWildcard anyLax ()
  {
    final XsdWildcard ret = new XsdWildcard ();
    ret.m_sNsMode = NS_ANY;
    ret.m_sProcess = PROCESS_LAX;
    return ret;
  }

  /**
   * @param sProcess
   *        processContents value.
   * @return Strictness rank: skip 0, lax 1, strict (and anything else) 2.
   */
  static int processRank (@NonNull final String sProcess)
  {
    switch (sProcess)
    {
      case PROCESS_SKIP:
        return 0;
      case PROCESS_LAX:
        return 1;
      default:
        return 2;
    }
  }

  /**
   * @param sNS
   *        Namespace URI, empty for none.
   * @return <code>true</code> if the namespace constraint (positive part plus
   *         <code>@notNamespace</code>, ignoring <code>@notQName</code>)
   *         admits the namespace.
   */
  boolean admitsNamespace (@NonNull final String sNS)
  {
    if (m_aNotNS.contains (sNS))
      return false;
    switch (m_sNsMode)
    {
      case NS_OTHER:
        return !sNS.equals (m_sTargetNS) && !sNS.isEmpty ();
      case NS_LIST:
        return m_aNamespaces.contains (sNS);
      default:
        return true;
    }
  }

  /**
   * @param aName
   *        The expanded name.
   * @param bUseDefined
   *        Whether the <code>##defined</code> exclusion applies.
   * @return <code>true</code> if this (non-union) wildcard admits the name.
   */
  boolean admits (@NonNull final XsdName aName, final boolean bUseDefined)
  {
    if (m_aParts.isNotEmpty ())
      return matches (aName);
    final String sNS = aName.space ();
    if (m_aNotNS.contains (sNS))
      return false;
    if (m_aNotNames.contains (aName))
      return false;
    if (bUseDefined && m_aNotDefNames.contains (aName))
      return false;
    switch (m_sNsMode)
    {
      case NS_ANY:
        return true;
      case NS_OTHER:
        return !sNS.equals (m_sTargetNS) && !sNS.isEmpty ();
      case NS_LIST:
        return m_aNamespaces.contains (sNS);
      default:
        return false;
    }
  }

  /**
   * go-xslt <code>wildcardMatches</code>.
   *
   * @param aName
   *        The expanded name.
   * @return <code>true</code> if the wildcard admits the name. A union admits
   *         a name unless every operand excludes it; <code>##defined</code>
   *         survives the union only when every operand carries it.
   */
  boolean matches (@NonNull final XsdName aName)
  {
    if (m_aParts.isNotEmpty ())
    {
      boolean bAllDefined = true;
      for (final XsdWildcard aPart : m_aParts)
        if (!aPart.m_bNotDefined)
        {
          bAllDefined = false;
          break;
        }
      for (final XsdWildcard aPart : m_aParts)
        if (aPart.admits (aName, bAllDefined))
          return true;
      return false;
    }
    return admits (aName, true);
  }

  @Override
  public String toString ()
  {
    return "XsdWildcard[" + m_sNsMode + " " + m_aNamespaces + " " + m_sProcess + "]";
  }
}
