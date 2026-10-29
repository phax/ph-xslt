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

import java.util.function.UnaryOperator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.EXdmContentType;
import com.helger.xpath.xdm.schema.EXdmSimpleVariety;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xpath.xdm.schema.XdmTypedToken;

/**
 * {@link IXdmTypeDefinition} over a compiled {@link IXsdType}. A reference to a
 * built-in type is a fresh, nameless sentinel in the compiled model, so the
 * name is carried here (go-xslt <code>builtinRefName</code>). Two definitions
 * are equal if they describe the same component: the same instance, or the
 * same type name.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdTypeDefinition implements IXdmTypeDefinition
{
  /** How far a base chain, a union or a list is followed. */
  private static final int MAX_DEPTH = 64;

  private final XsdBridge m_aBridge;
  private final IXsdType m_aType;
  /** The name of the type, {@link XsdName#NONE} for an anonymous one. */
  private final XsdName m_aName;

  XsdTypeDefinition (@NonNull final XsdBridge aBridge, @NonNull final IXsdType aType, @NonNull final XsdName aName)
  {
    m_aBridge = aBridge;
    m_aType = aType;
    m_aName = aName;
  }

  /**
   * go-xslt <code>builtinRefName</code>: the name of a pristine reference to a
   * built-in atomic type, which the compiled model materialises as a nameless
   * sentinel. Not named: an anonymous restriction of a built-in (facets of its
   * own), and the ur-type sentinel, which is xs:anySimpleType here.
   */
  @NonNull
  private static XsdName _builtinRefName (@NonNull final XsdSimpleType aType)
  {
    if (aType.m_eVariety != EXsdVariety.ATOMIC ||
        !aType.m_bBaseBuiltin ||
        aType.m_aBase != null ||
        aType.m_aAssertions.isNotEmpty ())
      return XsdName.NONE;
    final EAtomicType ePrim = aType.resolvePrim ();
    if (ePrim == EAtomicType.ANY_ATOMIC_TYPE)
      return aType.m_aFacets.any () ? XsdName.NONE : new XsdName (XsdConstants.NS_XS, "anySimpleType");
    if (aType.m_aFacets.any () && ePrim != EAtomicType.DATE_TIME_STAMP)
      return XsdName.NONE;
    return new XsdName (XsdConstants.NS_XS, ePrim.getLocalName ());
  }

  /**
   * @param aType
   *        A compiled type.
   * @return Its name; {@link XsdName#NONE} if it is anonymous.
   */
  @NonNull
  static XsdName nameOf (@NonNull final IXsdType aType)
  {
    if (aType instanceof final XsdComplexType aCT)
      return aCT.m_aName;
    final XsdSimpleType aST = (XsdSimpleType) aType;
    if (aST == XsdSimpleType.ABSENT)
      return XsdName.NONE;
    return aST.m_aName.isZero () ? _builtinRefName (aST) : aST.m_aName;
  }

  @NonNull
  IXsdType getType ()
  {
    return m_aType;
  }

  @Nullable
  public XdmQName getName ()
  {
    return m_aName.isZero () ? null : new XdmQName (m_aName.space (), m_aName.local ());
  }

  @NonNull
  public XdmQName getAnnotationName ()
  {
    return m_aName.isZero () ? m_aBridge.getAnonymousName (m_aType) : new XdmQName (m_aName.space (), m_aName.local ());
  }

  public boolean isComplex ()
  {
    return m_aType instanceof XsdComplexType;
  }

  @NonNull
  public EXdmContentType getContentType ()
  {
    if (m_aType instanceof final XsdComplexType aCT)
      return switch (aCT.m_eKind)
      {
        case EMPTY -> EXdmContentType.EMPTY;
        case SIMPLE -> EXdmContentType.SIMPLE;
        case ELEMENT_ONLY -> EXdmContentType.ELEMENT_ONLY;
        case MIXED -> EXdmContentType.MIXED;
      };
    return EXdmContentType.SIMPLE;
  }

  @Nullable
  public IXdmTypeDefinition getSimpleContentType ()
  {
    if (m_aType instanceof XsdSimpleType)
      return this;
    final XsdComplexType aCT = (XsdComplexType) m_aType;
    if (aCT.m_eKind != EXsdContentKind.SIMPLE || aCT.m_aSimpleType == null)
      return null;
    return m_aBridge.definitionOf (aCT.m_aSimpleType);
  }

  @Nullable
  private XsdSimpleType _getSimple ()
  {
    return XsdComponentParser.simpleContentType (m_aType);
  }

  /** @return <code>true</code> for xs:anySimpleType (the ur-type sentinel). */
  private boolean _isAnySimpleType ()
  {
    return m_aType instanceof final XsdSimpleType aST && aST != XsdSimpleType.ABSENT && XsdSimpleType.isURType (aST);
  }

  @Nullable
  public EXdmSimpleVariety getVariety ()
  {
    if (!(m_aType instanceof final XsdSimpleType aST) || aST == XsdSimpleType.ABSENT || _isAnySimpleType ())
      return null;
    return switch (aST.m_eVariety)
    {
      case ATOMIC -> EXdmSimpleVariety.ATOMIC;
      case LIST -> EXdmSimpleVariety.LIST;
      case UNION -> EXdmSimpleVariety.UNION;
    };
  }

  /**
   * go-xslt <code>listItemSimpleType</code>: the item type of a list, which a
   * restriction of the list keeps on its base.
   */
  @Nullable
  static XsdSimpleType listItemOf (@NonNull final XsdSimpleType aType)
  {
    XsdSimpleType aCur = aType;
    for (int i = 0; aCur != null && i < MAX_DEPTH; i++)
    {
      if (aCur.m_aItem != null)
        return aCur.m_aItem;
      aCur = aCur.m_aBase;
    }
    return null;
  }

  /**
   * @return The member types of a union, which a restriction of the union
   *         keeps on its base.
   */
  @NonNull
  static ICommonsList <XsdSimpleType> membersOf (@NonNull final XsdSimpleType aType)
  {
    XsdSimpleType aCur = aType;
    for (int i = 0; aCur != null && i < MAX_DEPTH; i++)
    {
      if (aCur.m_aMembers.isNotEmpty ())
        return aCur.m_aMembers;
      aCur = aCur.m_aBase;
    }
    return new CommonsArrayList <> ();
  }

  @Nullable
  public IXdmTypeDefinition getItemType ()
  {
    if (m_aType instanceof final XsdSimpleType aST && aST.m_eVariety == EXsdVariety.LIST)
    {
      final XsdSimpleType aItem = listItemOf (aST);
      if (aItem != null)
        return m_aBridge.definitionOf (aItem);
    }
    return null;
  }

  @NonNull
  public ICommonsList <IXdmTypeDefinition> getMemberTypes ()
  {
    final ICommonsList <IXdmTypeDefinition> ret = new CommonsArrayList <> ();
    if (m_aType instanceof final XsdSimpleType aST && aST.m_eVariety == EXsdVariety.UNION)
      for (final XsdSimpleType aMember : membersOf (aST))
        ret.add (m_aBridge.definitionOf (aMember));
    return ret;
  }

  @Nullable
  public XdmQName getBuiltinAtomicType ()
  {
    if (!(m_aType instanceof final XsdSimpleType aST) ||
        aST == XsdSimpleType.ABSENT ||
        aST.m_eVariety != EXsdVariety.ATOMIC)
      return null;
    final EAtomicType ePrim = aST.resolvePrim ();
    if (ePrim == EAtomicType.ANY_ATOMIC_TYPE)
      return null;
    return new XdmQName (XdmConstants.NS_XS, ePrim.getLocalName (), "xs");
  }

  @Nullable
  public IXdmTypeDefinition getBaseType ()
  {
    if (m_aType instanceof final XsdComplexType aCT)
    {
      if (aCT.isAnyType ())
        return null;
      return m_aBridge.definitionOf (aCT.m_aBaseType != null ? aCT.m_aBaseType : XsdComplexType.anyType ());
    }
    final XsdSimpleType aST = (XsdSimpleType) m_aType;
    if (aST == XsdSimpleType.ABSENT)
      return null;
    if (_isAnySimpleType ())
      return m_aBridge.definitionOf (XsdComplexType.anyType ());
    if (aST.m_aBase != null)
      return m_aBridge.definitionOf (aST.m_aBase);
    if (aST.m_eVariety != EXsdVariety.ATOMIC)
      return m_aBridge.definitionOf (XsdSimpleType.anySimpleType ());
    if (!aST.m_aName.isZero ())
    {
      // A user-defined restriction of a built-in
      return m_aBridge.definitionOf (XsdSimpleType.builtin (aST.resolvePrim ()));
    }
    final EAtomicType eParent = aST.resolvePrim ().getParent ();
    return m_aBridge.definitionOf (eParent == null || eParent == EAtomicType.ANY_ATOMIC_TYPE ? XsdSimpleType.anySimpleType ()
                                                                                              : XsdSimpleType.builtin (eParent));
  }

  /**
   * Is this type the base, or derived from it by restriction or extension?
   * go-xslt <code>derivesFromMode</code> without union substitution, with the
   * two corrections the SPI needs: a complex type does not derive from
   * xs:anySimpleType unless its content is simple, and the simple content of a
   * complex type takes part in the built-in hierarchy.
   */
  private boolean _derivesFrom (@NonNull final IXsdType aBase)
  {
    if (aBase instanceof final XsdSimpleType aSBase && m_aType instanceof final XsdComplexType aCT)
    {
      if (aCT.m_eKind != EXsdContentKind.SIMPLE || aCT.m_aSimpleType == null)
        return false;
      return XsdDerivation.derivesFromMode (aCT, aSBase, false) ||
             XsdDerivation.simpleDerivesFromMode (aCT.m_aSimpleType, aSBase, false);
    }
    return XsdDerivation.derivesFromMode (m_aType, aBase, false);
  }

  public boolean isDerivedFrom (@NonNull final IXdmTypeDefinition aBase)
  {
    if (!(aBase instanceof final XsdTypeDefinition aOther))
      return false;
    if (aOther.m_aType == m_aType)
      return true;
    // By name for named types, so that types of different schema sets that
    // describe the same component compare equal
    if (!m_aName.isZero () && m_aName.equals (aOther.m_aName))
      return true;
    return _derivesFrom (aOther.m_aType);
  }

  public boolean isDerivedFrom (@NonNull final XdmQName aBase)
  {
    final XsdName aName = XsdName.of (aBase);
    if (!m_aName.isZero () && m_aName.equals (aName))
      return true;
    final IXsdType aType = m_aBridge.typeByName (aName);
    return aType != null && _derivesFrom (aType);
  }

  private static boolean _isNamespaceSensitive (@Nullable final XsdSimpleType aType, final int nDepth)
  {
    if (aType == null || nDepth > MAX_DEPTH || aType == XsdSimpleType.ABSENT)
      return false;
    switch (aType.m_eVariety)
    {
      case ATOMIC:
      {
        final EAtomicType ePrim = aType.resolvePrim ();
        return ePrim == EAtomicType.QNAME || ePrim == EAtomicType.NOTATION;
      }
      case LIST:
        return _isNamespaceSensitive (listItemOf (aType), nDepth + 1);
      default:
        for (final XsdSimpleType aMember : membersOf (aType))
          if (_isNamespaceSensitive (aMember, nDepth + 1))
            return true;
        return false;
    }
  }

  public boolean isNamespaceSensitive ()
  {
    return _isNamespaceSensitive (_getSimple (), 0);
  }

  /**
   * The context for the prefixes of a value: a detached element declaring the
   * prefixes the value uses, as the resolver binds them.
   */
  @Nullable
  private static IXdmNode _namespaceContext (@NonNull final String sLexical,
                                            @Nullable final UnaryOperator <String> aNamespaceResolver)
  {
    if (aNamespaceResolver == null)
      return null;
    final IXdmNode ret = IXdmNode.createElement (new XdmQName ("x"));
    final String sDefault = aNamespaceResolver.apply ("");
    if (sDefault != null && !sDefault.isEmpty ())
      ret.declareNamespace ("", sDefault);
    for (final String sToken : XsdLexHelper.xmlFields (sLexical))
    {
      final int nColon = sToken.indexOf (':');
      if (nColon <= 0)
        continue;
      final String sPrefix = sToken.substring (0, nColon);
      final String sUri = aNamespaceResolver.apply (sPrefix);
      if (sUri != null && !sUri.isEmpty ())
        ret.declareNamespace (sPrefix, sUri);
    }
    return ret;
  }

  /**
   * The first member of a union that accepts the value, followed to an atomic
   * or list member.
   *
   * @return <code>null</code> if no member accepts the value.
   */
  @Nullable
  static XsdSimpleType acceptingMember (@NonNull final XsdSimpleType aUnion,
                                        @NonNull final String sRaw,
                                        @Nullable final IXdmNode aAt)
  {
    XsdSimpleType aCur = aUnion;
    for (int i = 0; i < MAX_DEPTH; i++)
    {
      XsdSimpleType aFound = null;
      for (final XsdSimpleType aMember : membersOf (aCur))
        try
        {
          XsdSimpleTypeValidator.validateIn (aMember, sRaw, aAt);
          aFound = aMember;
          break;
        }
        catch (final XsdInvalidException ex)
        {
          // Next member
        }
      if (aFound == null)
        return null;
      if (aFound.m_eVariety != EXsdVariety.UNION)
        return aFound;
      aCur = aFound;
    }
    return null;
  }

  @NonNull
  private XdmQName _builtinOf (@NonNull final XsdSimpleType aAtomic)
  {
    final EAtomicType ePrim = aAtomic.resolvePrim ();
    return new XdmQName (XdmConstants.NS_XS,
                         ePrim == EAtomicType.ANY_ATOMIC_TYPE ? "untypedAtomic" : ePrim.getLocalName (),
                         "xs");
  }

  private void _collectTokensRecursive (@NonNull final XsdSimpleType aType,
                                        @NonNull final String sRaw,
                                        @Nullable final IXdmNode aAt,
                                        @NonNull final ICommonsList <XdmTypedToken> aTokens,
                                        final int nDepth)
  {
    if (nDepth > MAX_DEPTH)
      throw new XsdUnsupportedException ("simple types nested deeper than " + MAX_DEPTH + " levels");
    switch (aType.m_eVariety)
    {
      case LIST:
      {
        final XsdSimpleType aItem = listItemOf (aType);
        final String sNormalised = XsdWhiteSpace.apply (aType.effectiveWhiteSpace (), sRaw);
        for (final String sToken : XsdLexHelper.xmlFields (sNormalised))
          if (aItem != null)
            _collectTokensRecursive (aItem, sToken, aAt, aTokens, nDepth + 1);
        break;
      }
      case UNION:
      {
        final String sNormalised = XsdWhiteSpace.apply (aType.effectiveWhiteSpace (), sRaw);
        final XsdSimpleType aMember = acceptingMember (aType, sNormalised, aAt);
        if (aMember != null)
          _collectTokensRecursive (aMember, sNormalised, aAt, aTokens, nDepth + 1);
        break;
      }
      default:
        aTokens.add (new XdmTypedToken (XsdWhiteSpace.apply (aType.effectiveWhiteSpace (), sRaw),
                                        _builtinOf (aType),
                                        m_aBridge.definitionOf (aType)));
        break;
    }
  }

  @NonNull
  public ICommonsList <XdmTypedToken> validate (@NonNull final String sLexical,
                                                @Nullable final UnaryOperator <String> aNamespaceResolver) throws XdmValidationException
  {
    final XsdSimpleType aSimple = _getSimple ();
    if (aSimple == null)
      throw new XdmValidationException ("Type " + _describe () + " has no simple content",
                                        new IllegalStateException ("no simple content"));
    final ICommonsList <XdmTypedToken> ret = new CommonsArrayList <> ();
    if (aSimple == XsdSimpleType.ABSENT)
    {
      ret.add (new XdmTypedToken (sLexical, new XdmQName (XdmConstants.NS_XS, "untypedAtomic", "xs"), this));
      return ret;
    }
    final IXdmNode aAt = _namespaceContext (sLexical, aNamespaceResolver);
    try
    {
      XsdSimpleTypeValidator.validateIn (aSimple, sLexical, aAt);
      _collectTokensRecursive (aSimple, sLexical, aAt, ret, 0);
    }
    catch (final XsdInvalidException ex)
    {
      throw new XdmValidationException ("'" +
                                        sLexical +
                                        "' is not a valid value of type " +
                                        _describe () +
                                        ": " +
                                        ex.getError ().message (),
                                        ex);
    }
    return ret;
  }

  @NonNull
  private String _describe ()
  {
    return m_aName.isZero () ? "(anonymous)" : getName ().getEQName ();
  }

  @Override
  public boolean equals (final Object o)
  {
    if (o == this)
      return true;
    if (o == null || !getClass ().equals (o.getClass ()))
      return false;
    final XsdTypeDefinition rhs = (XsdTypeDefinition) o;
    if (m_aType == rhs.m_aType)
      return true;
    return !m_aName.isZero () && m_aName.equals (rhs.m_aName);
  }

  @Override
  public int hashCode ()
  {
    return m_aName.isZero () ? System.identityHashCode (m_aType) : m_aName.hashCode ();
  }

  @Override
  public String toString ()
  {
    return "XsdTypeDefinition[" + _describe () + "]";
  }
}
