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

import java.util.Comparator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsTreeSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.collection.commons.ICommonsSortedSet;
import com.helger.xpath.value.EAtomicType;

/**
 * The read-only schema component model of a compiled {@link XsdSchema} (go-xslt
 * <code>model.go</code>). The view classes wrap the compiler's own components,
 * which stay free to change: only what is exposed here is API. A view is
 * immutable and safe for concurrent use; wrappers are created lazily and
 * cached, so the same component always yields the same view object.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class XsdSchemaView
{
  /** Bound for the walks of {@link #getAllUnresolved()} over nested types. */
  private static final int MAX_DEPTH = 4096;

  private static final Comparator <XsdName> NAME_ORDER = Comparator.comparing (XsdName::space)
                                                                    .thenComparing (XsdName::local);

  private final XsdSchema m_aSchema;
  private final SimpleReadWriteLock m_aRWLock = new SimpleReadWriteLock ();
  private final ICommonsMap <XsdElementDecl, XsdElementView> m_aElements = new CommonsHashMap <> ();
  private final ICommonsMap <XsdSimpleType, XsdSimpleTypeView> m_aSimpleTypes = new CommonsHashMap <> ();
  private final ICommonsMap <XsdComplexType, XsdComplexTypeView> m_aComplexTypes = new CommonsHashMap <> ();
  private final ICommonsMap <XsdAttributeDecl, XsdAttributeDeclView> m_aAttributes = new CommonsHashMap <> ();
  private XsdComplexTypeView m_aAnyType;

  XsdSchemaView (@NonNull final XsdSchema aSchema)
  {
    m_aSchema = aSchema;
  }

  @NonNull
  private static <T> ICommonsList <XsdName> _sortedNames (@NonNull final ICommonsMap <XsdName, T> aMap)
  {
    final ICommonsList <XsdName> ret = new CommonsArrayList <> (aMap.keySet ());
    ret.sort (NAME_ORDER);
    return ret;
  }

  private static void _walkSimple (@Nullable final XsdSimpleType aType,
                                   @NonNull final String sWhere,
                                   @NonNull final ICommonsSet <Object> aSeen,
                                   @NonNull final ICommonsSortedSet <String> aOut,
                                   final int nDepth)
  {
    if (aType == null || nDepth > MAX_DEPTH)
      return;
    if (aType == XsdSimpleType.ABSENT)
    {
      aOut.add (sWhere);
      return;
    }
    if (!aSeen.add (aType))
      return;
    _walkSimple (aType.m_aBase, sWhere, aSeen, aOut, nDepth + 1);
    _walkSimple (aType.m_aItem, sWhere, aSeen, aOut, nDepth + 1);
    for (final XsdSimpleType aMember : aType.m_aMembers)
      _walkSimple (aMember, sWhere, aSeen, aOut, nDepth + 1);
  }

  private static void _walkType (@Nullable final IXsdType aType,
                                 @NonNull final String sWhere,
                                 @NonNull final ICommonsSet <Object> aSeen,
                                 @NonNull final ICommonsSortedSet <String> aOut,
                                 final int nDepth)
  {
    if (nDepth > MAX_DEPTH)
      return;
    if (aType instanceof final XsdSimpleType aST)
      _walkSimple (aST, sWhere, aSeen, aOut, nDepth + 1);
    else
      if (aType instanceof final XsdComplexType aCT)
      {
        if (!aSeen.add (aCT))
          return;
        final String sCTWhere = aCT.m_aName.isZero () ? "an anonymous complex type" : "type " + aCT.m_aName;
        _walkType (aCT.m_aBaseType, sCTWhere, aSeen, aOut, nDepth + 1);
        _walkSimple (aCT.m_aSimpleType, sCTWhere, aSeen, aOut, nDepth + 1);
        _walkParticle (aCT.m_aParticle, sCTWhere, aSeen, aOut, nDepth + 1);
        for (final XsdAttributeUse aUse : aCT.m_aAttrUses)
          _walkSimple (aUse.m_aDecl.m_aType, "attribute " + aUse.m_aDecl.m_aName, aSeen, aOut, nDepth + 1);
      }
  }

  private static void _walkParticle (@Nullable final XsdParticle aParticle,
                                     @NonNull final String sWhere,
                                     @NonNull final ICommonsSet <Object> aSeen,
                                     @NonNull final ICommonsSortedSet <String> aOut,
                                     final int nDepth)
  {
    if (aParticle == null || nDepth > MAX_DEPTH)
      return;
    if (aParticle.m_aTerm instanceof final XsdElementDecl aDecl)
    {
      if (aSeen.add (aDecl))
        _walkType (aDecl.m_aType, "element " + aDecl.m_aName, aSeen, aOut, nDepth + 1);
    }
    else
      if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
        for (final XsdParticle aSub : aMG.m_aParticles)
          _walkParticle (aSub, sWhere, aSeen, aOut, nDepth + 1);
  }

  @NonNull
  XsdSchema getSchema ()
  {
    return m_aSchema;
  }

  @NonNull
  XsdElementView element (@NonNull final XsdElementDecl aDecl)
  {
    final XsdElementView ret = m_aRWLock.readLockedGet ( () -> m_aElements.get (aDecl));
    if (ret != null)
      return ret;
    return m_aRWLock.writeLockedGet ( () -> m_aElements.computeIfAbsent (aDecl, k -> new XsdElementView (this, k)));
  }

  @NonNull
  XsdAttributeDeclView attr (@NonNull final XsdAttributeDecl aDecl)
  {
    final XsdAttributeDeclView ret = m_aRWLock.readLockedGet ( () -> m_aAttributes.get (aDecl));
    if (ret != null)
      return ret;
    return m_aRWLock.writeLockedGet ( () -> m_aAttributes.computeIfAbsent (aDecl,
                                                                           k -> new XsdAttributeDeclView (this, k)));
  }

  @Nullable
  XsdSimpleTypeView simple (@Nullable final XsdSimpleType aType)
  {
    if (aType == null)
      return null;
    final XsdSimpleTypeView ret = m_aRWLock.readLockedGet ( () -> m_aSimpleTypes.get (aType));
    if (ret != null)
      return ret;
    return m_aRWLock.writeLockedGet ( () -> m_aSimpleTypes.computeIfAbsent (aType,
                                                                            k -> new XsdSimpleTypeView (this, k)));
  }

  @NonNull
  XsdComplexTypeView complexType (@NonNull final XsdComplexType aType)
  {
    final XsdComplexTypeView ret = m_aRWLock.readLockedGet ( () -> m_aComplexTypes.get (aType));
    if (ret != null)
      return ret;
    return m_aRWLock.writeLockedGet ( () -> m_aComplexTypes.computeIfAbsent (aType,
                                                                             k -> new XsdComplexTypeView (this, k)));
  }

  /** @return The ur-type, standing in for an element declared without a type. */
  @NonNull
  XsdComplexTypeView anyTypeView ()
  {
    final XsdComplexTypeView ret = m_aRWLock.readLockedGet ( () -> m_aAnyType);
    if (ret != null)
      return ret;
    return m_aRWLock.writeLockedGet ( () -> {
      if (m_aAnyType == null)
      {
        final XsdComplexType aAny = new XsdComplexType (XsdComplexType.ANY_TYPE_NAME);
        aAny.m_eKind = EXsdContentKind.MIXED;
        final XsdModelGroup aSeq = new XsdModelGroup (EXsdCompositor.SEQUENCE);
        aSeq.m_aParticles.add (new XsdParticle (0, XsdConstants.UNBOUNDED, XsdWildcard.anyLax ()));
        aAny.m_aParticle = new XsdParticle (1, 1, aSeq);
        aAny.m_aAttrWildcard = XsdWildcard.anyLax ();
        m_aAnyType = new XsdComplexTypeView (this, aAny);
      }
      return m_aAnyType;
    });
  }

  @Nullable
  IXsdTypeDefinitionView typeDef (@Nullable final IXsdType aType)
  {
    if (aType instanceof final XsdSimpleType aST)
      return simple (aST);
    if (aType instanceof final XsdComplexType aCT)
      return complexType (aCT);
    return null;
  }

  @NonNull
  static XsdWildcardView wildcard (@NonNull final XsdWildcard aWC)
  {
    final String sMode = aWC.m_sNsMode.isEmpty () ? XsdWildcard.NS_ANY : aWC.m_sNsMode;
    return new XsdWildcardView (sMode, aWC.m_aNamespaces.getClone (), aWC.m_sProcess);
  }

  @NonNull
  XsdParticleView particle (@NonNull final XsdParticle aParticle)
  {
    final ICommonsList <XsdDocumentation> aDocs = aParticle.m_aDocs == null ? new CommonsArrayList <> ()
                                                                             : aParticle.m_aDocs.getClone ();
    final IXsdTermView aTerm;
    if (aParticle.m_aTerm instanceof final XsdElementDecl aDecl)
      aTerm = element (aDecl);
    else
      if (aParticle.m_aTerm instanceof final XsdModelGroup aMG)
      {
        final ICommonsList <XsdParticleView> aParticles = new CommonsArrayList <> ();
        for (final XsdParticle aSub : aMG.m_aParticles)
          aParticles.add (particle (aSub));
        aTerm = new XsdModelGroupView (aMG.m_eCompositor, aParticles);
      }
      else
        aTerm = wildcard ((XsdWildcard) aParticle.m_aTerm);
    return new XsdParticleView (aDocs, aParticle.m_nMin, aParticle.m_nMax, aTerm);
  }

  /** @return The global element declarations, ordered by namespace then local name. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XsdElementView> getAllGlobalElements ()
  {
    final ICommonsList <XsdElementView> ret = new CommonsArrayList <> ();
    for (final XsdName aName : _sortedNames (m_aSchema.m_aElements))
      ret.add (element (m_aSchema.m_aElements.get (aName)));
    return ret;
  }

  /**
   * @return The named type definitions (user-defined and built-in ones the
   *         schema declares), ordered by namespace then local name.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IXsdTypeDefinitionView> getAllGlobalTypes ()
  {
    final ICommonsList <IXsdTypeDefinitionView> ret = new CommonsArrayList <> ();
    for (final XsdName aName : _sortedNames (m_aSchema.m_aTypes))
    {
      final IXsdTypeDefinitionView aView = typeDef (m_aSchema.m_aTypes.get (aName));
      if (aView != null)
        ret.add (aView);
    }
    return ret;
  }

  /** @return The global attribute declarations, ordered by name. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XsdAttributeDeclView> getAllGlobalAttributes ()
  {
    final ICommonsList <XsdAttributeDeclView> ret = new CommonsArrayList <> ();
    for (final XsdName aName : _sortedNames (m_aSchema.m_aAttributes))
      ret.add (attr (m_aSchema.m_aAttributes.get (aName)));
    return ret;
  }

  /**
   * @param aName
   *        The element name.
   * @return The global element declaration, or <code>null</code>.
   */
  @Nullable
  public XsdElementView getElement (@NonNull final XsdName aName)
  {
    final XsdElementDecl aDecl = m_aSchema.m_aElements.get (aName);
    return aDecl == null ? null : element (aDecl);
  }

  /**
   * @param aName
   *        The type name.
   * @return The named type definition (a schema type or a built-in one), or
   *         <code>null</code>.
   */
  @Nullable
  public IXsdTypeDefinitionView getType (@NonNull final XsdName aName)
  {
    final IXsdType aType = m_aSchema.m_aTypes.get (aName);
    if (aType != null)
      return typeDef (aType);
    if (XsdConstants.NS_XS.equals (aName.space ()))
    {
      final EAtomicType eAtom = EAtomicType.getFromLocalName (aName.local ());
      if (eAtom != null)
        return simple (XsdSimpleType.builtin (eAtom));
      if ("anyType".equals (aName.local ()))
        return anyTypeView ();
    }
    return null;
  }

  /**
   * @param aHead
   *        The head element name.
   * @return The global elements that may substitute for the head,
   *         transitively, the head excluded, in name order.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XsdElementView> getAllSubstitutionMembers (@NonNull final XsdName aHead)
  {
    final ICommonsSet <XsdElementDecl> aSeen = new CommonsHashSet <> ();
    final ICommonsList <XsdElementDecl> aDecls = new CommonsArrayList <> ();
    final ICommonsList <XsdName> aStack = new CommonsArrayList <> ();
    aStack.add (aHead);
    while (aStack.isNotEmpty ())
    {
      final XsdName aCur = aStack.removeLast ();
      for (final XsdElementDecl aMember : m_aSchema.substMembersOf (aCur))
        if (aSeen.add (aMember))
        {
          aDecls.add (aMember);
          aStack.add (aMember.m_aName);
        }
    }
    aDecls.sort ( (a, b) -> NAME_ORDER.compare (a.m_aName, b.m_aName));
    final ICommonsList <XsdElementView> ret = new CommonsArrayList <> ();
    for (final XsdElementDecl aDecl : aDecls)
      ret.add (element (aDecl));
    return ret;
  }

  /**
   * The declarations whose type (or list item, union member, base) reference
   * names a type the schema does not define. The compiler accepts such a
   * schema (XSD 1.0 missing sub-components); the error surfaces at validation
   * time, but a tool walking the model must be told.
   *
   * @return Sorted descriptions such as <code>element {urn:x}e</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getAllUnresolved ()
  {
    final ICommonsSortedSet <String> aOut = new CommonsTreeSet <> ();
    final ICommonsSet <Object> aSeen = new CommonsHashSet <> ();
    for (final XsdName aName : _sortedNames (m_aSchema.m_aElements))
    {
      final XsdElementDecl aDecl = m_aSchema.m_aElements.get (aName);
      aSeen.add (aDecl);
      _walkType (aDecl.m_aType, "element " + aDecl.m_aName, aSeen, aOut, 0);
    }
    for (final XsdName aName : _sortedNames (m_aSchema.m_aTypes))
      _walkType (m_aSchema.m_aTypes.get (aName), "type " + aName, aSeen, aOut, 0);
    for (final XsdName aName : _sortedNames (m_aSchema.m_aAttributes))
      _walkSimple (m_aSchema.m_aAttributes.get (aName).m_aType, "attribute " + aName, aSeen, aOut, 0);
    return new CommonsArrayList <> (aOut);
  }

  @Override
  public String toString ()
  {
    return "XsdSchemaView[" + m_aSchema + "]";
  }
}
