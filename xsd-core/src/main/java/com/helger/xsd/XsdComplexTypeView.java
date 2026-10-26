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
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * A complex type definition of the read-only component model (go-xslt
 * <code>ComplexTypeView</code>).
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdComplexTypeView implements IXsdTypeDefinitionView
{
  private static final int MAX_CHAIN = 64;

  private final XsdSchemaView m_aView;
  private final XsdComplexType m_aType;

  XsdComplexTypeView (@NonNull final XsdSchemaView aView, @NonNull final XsdComplexType aType)
  {
    m_aView = aView;
    m_aType = aType;
  }

  public boolean isSimple ()
  {
    return false;
  }

  @NonNull
  public XsdName getName ()
  {
    return m_aType.m_aName;
  }

  /**
   * @return The built-in simple type of a type with simple content (through
   *         extension and restriction), xs:anyType otherwise.
   */
  @NonNull
  public XsdName getBuiltinBase ()
  {
    final XsdSimpleTypeView aST = getSimpleContent ();
    if (aST != null)
      return aST.getBuiltinBase ();
    return XsdComplexType.ANY_TYPE_NAME;
  }

  public boolean isAbstract ()
  {
    return m_aType.m_bAbstract;
  }

  /** @return The type's xs:documentation (needs documentation kept at compile time). */
  @NonNull
  public ICommonsList <XsdDocumentation> getDocumentation ()
  {
    return m_aType.m_aDocs.getClone ();
  }

  /** @return The type this one derives from; <code>null</code> for xs:anyType and types without explicit base. */
  @Nullable
  public IXsdTypeDefinitionView getBase ()
  {
    return m_aView.typeDef (m_aType.m_aBaseType);
  }

  /** @return <code>extension</code>, <code>restriction</code> or empty (no derivation). */
  @NonNull
  public String getDerivation ()
  {
    return m_aType.m_sDerivation;
  }

  /** @return The content kind. */
  @NonNull
  public EXsdContentKind getContentKind ()
  {
    return m_aType.m_eKind;
  }

  /** @return <code>true</code> for mixed content. */
  public boolean isMixed ()
  {
    return m_aType.m_eKind == EXsdContentKind.MIXED;
  }

  /**
   * @return The simple type of the character content, <code>null</code> unless
   *         the type has simple content (including what it inherits).
   */
  @Nullable
  public XsdSimpleTypeView getSimpleContent ()
  {
    XsdComplexType c = m_aType;
    for (int n = 0; c != null && n < MAX_CHAIN; n++)
    {
      if (c.m_eKind == EXsdContentKind.SIMPLE && c.m_aSimpleType != null)
        return m_aView.simple (c.m_aSimpleType);
      if (!(c.m_aBaseType instanceof final XsdComplexType aBase))
        return null;
      c = aBase;
    }
    return null;
  }

  /** @return The effective (after extension) content model, <code>null</code> without element content. */
  @Nullable
  public XsdParticleView getContentModel ()
  {
    final XsdParticle p = m_aView.getSchema ().effectiveParticle (m_aType);
    return p == null ? null : m_aView.particle (p);
  }

  /**
   * @return The particles of the content model's outermost group - or, if the
   *         model is a single element or wildcard, that one particle.
   */
  @NonNull
  public ICommonsList <XsdParticleView> getContentParticles ()
  {
    final XsdParticleView aRoot = getContentModel ();
    if (aRoot == null)
      return new CommonsArrayList <> ();
    if (aRoot.term () instanceof final XsdModelGroupView aGroup && aRoot.minOccurs () == 1 && aRoot.maxOccurs () == 1)
      return aGroup.particles ().getClone ();
    return new CommonsArrayList <> (aRoot);
  }

  /** @return The effective attribute uses (after extension and restriction). */
  @NonNull
  public ICommonsList <XsdAttributeUseView> getAttributes ()
  {
    final ICommonsList <XsdAttributeUseView> ret = new CommonsArrayList <> ();
    for (final XsdAttributeUse aUse : m_aView.getSchema ().effectiveAttrs (m_aType).uses ())
    {
      final EXsdUse eUse = aUse.m_bProhibited ? EXsdUse.PROHIBITED : aUse.m_bRequired ? EXsdUse.REQUIRED : EXsdUse.OPTIONAL;
      String sDefault = aUse.m_bHasDefault ? aUse.m_sDefault : null;
      String sFixed = aUse.m_bHasFixed ? aUse.m_sFixed : null;
      if (sDefault == null && sFixed == null)
      {
        sDefault = aUse.m_aDecl.m_bHasDefault ? aUse.m_aDecl.m_sDefault : null;
        sFixed = aUse.m_aDecl.m_bHasFixed ? aUse.m_aDecl.m_sFixed : null;
      }
      ret.add (new XsdAttributeUseView (aUse.m_aDocs.getClone (),
                                        m_aView.attr (aUse.m_aDecl),
                                        aUse.m_aDecl.m_aName,
                                        m_aView.simple (aUse.m_aDecl.m_aType),
                                        eUse,
                                        sDefault,
                                        sFixed));
    }
    return ret;
  }

  /** @return The effective xs:anyAttribute, <code>null</code> when there is none. */
  @Nullable
  public XsdWildcardView getAttributeWildcard ()
  {
    final XsdWildcard aWC = m_aView.getSchema ().effectiveAttrs (m_aType).wildcard ();
    return aWC == null ? null : XsdSchemaView.wildcard (aWC);
  }

  @Override
  public String toString ()
  {
    return "XsdComplexTypeView[" + m_aType.m_aName + "]";
  }
}
