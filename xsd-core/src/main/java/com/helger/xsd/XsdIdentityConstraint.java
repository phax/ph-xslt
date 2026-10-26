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
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.CompiledXPath;

/**
 * A compiled <code>xs:key</code>, <code>xs:keyref</code> or
 * <code>xs:unique</code> - go-xslt <code>identityConstraint</code>. The selector
 * and field expressions are compiled with the in-scope namespaces of their
 * schema elements (names are resolved at compile time).
 *
 * @author Philip Helger
 */
final class XsdIdentityConstraint
{
  /** <code>key</code>, <code>keyref</code> or <code>unique</code>. */
  String m_sKind;
  XsdName m_aName = XsdName.NONE;
  /** The referenced key or unique of a keyref. */
  XsdName m_aRefer = XsdName.NONE;
  /** The restricted XPath of the selector, relative to the scope element. */
  CompiledXPath m_aSelector;
  /** The selector's source text. */
  String m_sSelectorSrc = "";
  /** One compiled expression per xs:field. */
  ICommonsList <CompiledXPath> m_aFields = new CommonsArrayList <> ();
  /** The fields' trimmed XPath (for defaults of absent attributes). */
  ICommonsList <String> m_aFieldRaw = new CommonsArrayList <> ();
  /** Each field's source XPath, for value-constraint lookup. */
  ICommonsList <String> m_aFieldSrc = new CommonsArrayList <> ();
  /** The in-scope namespaces of the constraint element. */
  ICommonsOrderedMap <String, String> m_aNS = new CommonsLinkedHashMap <> ();
  /** The resolved xpathDefaultNamespace of the selector (1.1). */
  String m_sSelNS = "";
  /** The resolved xpathDefaultNamespace per field (1.1). */
  ICommonsList <String> m_aFieldNS = new CommonsArrayList <> ();
  /** The referenced definition of an XSD 1.1 <code>@ref</code> use. */
  XsdName m_aRefName = XsdName.NONE;
  /** An XSD 1.1 <code>@ref</code> use not yet resolved; it is not a re-declaration. */
  boolean m_bRefPending;

  XsdIdentityConstraint (@NonNull final String sKind)
  {
    m_sKind = sKind;
  }

  /** Copy every field of another constraint into this one (Go <code>*ic = *ref</code>). */
  void assignFrom (@NonNull final XsdIdentityConstraint aOther)
  {
    m_sKind = aOther.m_sKind;
    m_aName = aOther.m_aName;
    m_aRefer = aOther.m_aRefer;
    m_aSelector = aOther.m_aSelector;
    m_sSelectorSrc = aOther.m_sSelectorSrc;
    m_aFields = aOther.m_aFields;
    m_aFieldRaw = aOther.m_aFieldRaw;
    m_aFieldSrc = aOther.m_aFieldSrc;
    m_aNS = aOther.m_aNS;
    m_sSelNS = aOther.m_sSelNS;
    m_aFieldNS = aOther.m_aFieldNS;
    m_aRefName = aOther.m_aRefName;
    m_bRefPending = aOther.m_bRefPending;
  }

  @Override
  public String toString ()
  {
    return "XsdIdentityConstraint[" + m_sKind + " " + m_aName + "]";
  }
}
