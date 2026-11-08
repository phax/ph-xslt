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
 * A compiled complex type - go-xslt <code>ComplexType</code>. Content model and
 * attributes are the definition at this derivation step; the effective
 * (post-derivation) values are computed on demand by
 * {@link XsdSchema#effectiveParticle(XsdComplexType)} and
 * {@link XsdSchema#effectiveAttrs(XsdComplexType)}, which walk the base chain.
 *
 * @author Philip Helger
 */
final class XsdComplexType implements IXsdType
{
  static final XsdName ANY_TYPE_NAME = new XsdName (XsdConstants.NS_XS, "anyType");

  /** The empty name for anonymous types. */
  XsdName m_aName = XsdName.NONE;

  EXsdContentKind m_eKind = EXsdContentKind.EMPTY;
  ICommonsList <XsdDocumentation> m_aDocs = new CommonsArrayList <> ();
  /** The content type of simple content. */
  XsdSimpleType m_aSimpleType;
  /** This step's element or mixed content; <code>null</code> means empty. */
  XsdParticle m_aParticle;
  ICommonsList <XsdAttributeUse> m_aAttrUses = new CommonsArrayList <> ();
  XsdWildcard m_aAttrWildcard;
  boolean m_bAbstract;

  /** The derivation base; <code>null</code> means implicitly xs:anyType. */
  IXsdType m_aBaseType;
  /** <code>extension</code>, <code>restriction</code> or empty. */
  String m_sDerivation = "";
  /** The derivation methods this type forbids as a base. */
  ICommonsSet <String> m_aFinal = new CommonsHashSet <> ();
  /** The derivation methods this type forbids for xsi:type substitution. */
  ICommonsSet <String> m_aBlock = new CommonsHashSet <> ();
  /** XSD 1.1 xs:assert of this step. */
  ICommonsList <XsdAssertion> m_aAsserts = new CommonsArrayList <> ();
  /** XSD 1.1 {open content} declared at this step. */
  XsdOpenContent m_aOpenContent;

  XsdComplexType ()
  {}

  XsdComplexType (@NonNull final XsdName aName)
  {
    m_aName = aName;
  }

  /**
   * @return A new xs:anyType: mixed content, any number of lax wildcards, lax
   *         attribute wildcard.
   */
  @NonNull
  static XsdComplexType anyType ()
  {
    final XsdComplexType ret = new XsdComplexType (ANY_TYPE_NAME);
    ret.m_eKind = EXsdContentKind.MIXED;
    ret.m_aParticle = new XsdParticle (0, XsdConstants.UNBOUNDED, XsdWildcard.anyLax ());
    ret.m_aAttrWildcard = XsdWildcard.anyLax ();
    return ret;
  }

  /** Copy every field of another type into this one (Go <code>*ct = *o</code>). */
  void assignFrom (@NonNull final XsdComplexType aOther)
  {
    m_aName = aOther.m_aName;
    m_eKind = aOther.m_eKind;
    m_aDocs = aOther.m_aDocs;
    m_aSimpleType = aOther.m_aSimpleType;
    m_aParticle = aOther.m_aParticle;
    m_aAttrUses = aOther.m_aAttrUses;
    m_aAttrWildcard = aOther.m_aAttrWildcard;
    m_bAbstract = aOther.m_bAbstract;
    m_aBaseType = aOther.m_aBaseType;
    m_sDerivation = aOther.m_sDerivation;
    m_aFinal = aOther.m_aFinal;
    m_aBlock = aOther.m_aBlock;
    m_aAsserts = aOther.m_aAsserts;
    m_aOpenContent = aOther.m_aOpenContent;
  }

  /** @return A shallow copy (Go <code>cp := *o</code>). */
  @NonNull
  XsdComplexType getShallowCopy ()
  {
    final XsdComplexType ret = new XsdComplexType ();
    ret.assignFrom (this);
    return ret;
  }

  /** Reset to a fresh type with the given name. */
  void reset (@NonNull final XsdName aName)
  {
    assignFrom (new XsdComplexType (aName));
  }

  /** @return <code>true</code> if this is (a copy of) xs:anyType. */
  boolean isAnyType ()
  {
    return ANY_TYPE_NAME.equals (m_aName);
  }

  @Override
  public String toString ()
  {
    return "XsdComplexType[" + (m_aName.isZero () ? "anonymous" : m_aName.toString ()) + "]";
  }
}
