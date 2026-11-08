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
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;

/**
 * A compiled element declaration, global or local - go-xslt
 * <code>ElementDecl</code>.
 *
 * @author Philip Helger
 */
final class XsdElementDecl implements IXsdTerm
{
  XsdName m_aName;
  ICommonsList <XsdDocumentation> m_aDocs = new CommonsArrayList <> ();
  /** The msg:message entries of the declaration's appinfo. */
  ICommonsList <XsdMessage> m_aMsgs = new CommonsArrayList <> ();
  IXsdType m_aType;
  boolean m_bNillable;
  boolean m_bAbstract;
  /** Substitution-group affiliations (global elements; a 1.1 list may name several heads). */
  ICommonsList <XsdName> m_aSubstGroups = new CommonsArrayList <> ();
  /**
   * A declaration with neither <code>@type</code> nor an inline type: its
   * anyType default may be replaced by a substitution-group head's type.
   */
  boolean m_bTypeDefaulted;
  String m_sDefault = "";
  String m_sFixed = "";
  boolean m_bHasDefault;
  boolean m_bHasFixed;
  /** Derivation methods blocked for xsi:type substitution. */
  ICommonsSet <String> m_aBlocked = new CommonsHashSet <> ();
  /** Derivation methods this element forbids for substitution-group members. */
  ICommonsSet <String> m_aFinal = new CommonsHashSet <> ();
  ICommonsList <XsdIdentityConstraint> m_aConstraints = new CommonsArrayList <> ();
  /** XSD 1.1 conditional type assignment. */
  ICommonsList <XsdTypeAlternative> m_aAlternatives = new CommonsArrayList <> ();

  XsdElementDecl (@NonNull final XsdName aName)
  {
    m_aName = aName;
  }

  @Override
  public String toString ()
  {
    return "XsdElementDecl[" + m_aName + "]";
  }
}
