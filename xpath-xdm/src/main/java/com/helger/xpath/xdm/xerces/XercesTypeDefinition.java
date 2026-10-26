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
package com.helger.xpath.xdm.xerces;

import java.util.function.UnaryOperator;

import org.apache.xerces.impl.dv.InvalidDatatypeValueException;
import org.apache.xerces.impl.dv.ValidatedInfo;
import org.apache.xerces.impl.dv.XSSimpleType;
import org.apache.xerces.impl.validation.ValidationState;
import org.apache.xerces.util.NamespaceSupport;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.apache.xerces.xs.XSTypeDefinition;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.EXdmContentType;
import com.helger.xpath.xdm.schema.EXdmSimpleVariety;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xpath.xdm.schema.XdmTypedToken;

/**
 * {@link IXdmTypeDefinition} over a Xerces-J type definition.
 *
 * @author Philip Helger
 */
@Immutable
final class XercesTypeDefinition implements IXdmTypeDefinition
{
  private static final short DERIVATION_RESTRICTION_EXTENSION = XSConstants.DERIVATION_RESTRICTION |
                                                                XSConstants.DERIVATION_EXTENSION;

  private final XSTypeDefinition m_aType;

  XercesTypeDefinition (@NonNull final XSTypeDefinition aType)
  {
    m_aType = aType;
  }

  @Nullable
  static IXdmTypeDefinition wrap (@Nullable final XSTypeDefinition aType)
  {
    return aType == null ? null : new XercesTypeDefinition (aType);
  }

  @Nullable
  public XdmQName getName ()
  {
    if (m_aType.getAnonymous ())
      return null;
    final String sNamespace = m_aType.getNamespace ();
    return new XdmQName (sNamespace == null ? "" : sNamespace, m_aType.getName ());
  }

  public boolean isComplex ()
  {
    return m_aType.getTypeCategory () == XSTypeDefinition.COMPLEX_TYPE;
  }

  @NonNull
  public EXdmContentType getContentType ()
  {
    if (m_aType instanceof final XSComplexTypeDefinition aComplex)
      return switch (aComplex.getContentType ())
      {
        case XSComplexTypeDefinition.CONTENTTYPE_EMPTY -> EXdmContentType.EMPTY;
        case XSComplexTypeDefinition.CONTENTTYPE_SIMPLE -> EXdmContentType.SIMPLE;
        case XSComplexTypeDefinition.CONTENTTYPE_ELEMENT -> EXdmContentType.ELEMENT_ONLY;
        default -> EXdmContentType.MIXED;
      };
    return EXdmContentType.SIMPLE;
  }

  @Nullable
  private XSSimpleTypeDefinition _getSimple ()
  {
    if (m_aType instanceof final XSSimpleTypeDefinition aSimple)
      return aSimple;
    if (m_aType instanceof final XSComplexTypeDefinition aComplex)
      return aComplex.getSimpleType ();
    return null;
  }

  @Nullable
  public IXdmTypeDefinition getSimpleContentType ()
  {
    if (m_aType instanceof XSSimpleTypeDefinition)
      return this;
    return wrap (_getSimple ());
  }

  @Nullable
  public EXdmSimpleVariety getVariety ()
  {
    if (!(m_aType instanceof final XSSimpleTypeDefinition aSimple))
      return null;
    return switch (aSimple.getVariety ())
    {
      case XSSimpleTypeDefinition.VARIETY_ATOMIC -> EXdmSimpleVariety.ATOMIC;
      case XSSimpleTypeDefinition.VARIETY_LIST -> EXdmSimpleVariety.LIST;
      case XSSimpleTypeDefinition.VARIETY_UNION -> EXdmSimpleVariety.UNION;
      default -> null;
    };
  }

  @Nullable
  public IXdmTypeDefinition getItemType ()
  {
    if (m_aType instanceof final XSSimpleTypeDefinition aSimple &&
        aSimple.getVariety () == XSSimpleTypeDefinition.VARIETY_LIST)
      return wrap (aSimple.getItemType ());
    return null;
  }

  @NonNull
  public ICommonsList <IXdmTypeDefinition> getMemberTypes ()
  {
    final ICommonsList <IXdmTypeDefinition> ret = new CommonsArrayList <> ();
    if (m_aType instanceof final XSSimpleTypeDefinition aSimple &&
        aSimple.getVariety () == XSSimpleTypeDefinition.VARIETY_UNION)
    {
      final XSObjectList aMembers = aSimple.getMemberTypes ();
      for (int i = 0; i < aMembers.getLength (); ++i)
        ret.add (new XercesTypeDefinition ((XSTypeDefinition) aMembers.item (i)));
    }
    return ret;
  }

  /**
   * @return The most specific built-in type of an atomic simple type: the
   *         first type in the base type chain that is named in the XML Schema
   *         namespace.
   */
  @Nullable
  static XdmQName getBuiltinAtomicType (@Nullable final XSSimpleTypeDefinition aSimple)
  {
    if (aSimple == null || aSimple.getVariety () != XSSimpleTypeDefinition.VARIETY_ATOMIC)
      return null;
    XSTypeDefinition aCur = aSimple;
    while (aCur != null)
    {
      if (!aCur.getAnonymous () && XdmConstants.NS_XS.equals (aCur.getNamespace ()))
        return new XdmQName (XdmConstants.NS_XS, aCur.getName (), "xs");
      final XSTypeDefinition aBase = aCur.getBaseType ();
      if (aBase == aCur)
        break;
      aCur = aBase;
    }
    return null;
  }

  @Nullable
  public XdmQName getBuiltinAtomicType ()
  {
    return m_aType instanceof final XSSimpleTypeDefinition aSimple ? getBuiltinAtomicType (aSimple) : null;
  }

  @Nullable
  public IXdmTypeDefinition getBaseType ()
  {
    final XSTypeDefinition aBase = m_aType.getBaseType ();
    return aBase == null || aBase == m_aType ? null : new XercesTypeDefinition (aBase);
  }

  public boolean isDerivedFrom (@NonNull final IXdmTypeDefinition aBase)
  {
    if (!(aBase instanceof final XercesTypeDefinition aOther))
      return false;
    if (aOther.m_aType == m_aType)
      return true;
    // By name for named types, so that types of different schema sets that
    // describe the same component compare equal
    if (!aOther.m_aType.getAnonymous ())
      return m_aType.derivedFrom (aOther.m_aType.getNamespace (),
                                  aOther.m_aType.getName (),
                                  DERIVATION_RESTRICTION_EXTENSION) ||
             _isSameNamed (m_aType, aOther.m_aType);
    return m_aType.derivedFromType (aOther.m_aType, DERIVATION_RESTRICTION_EXTENSION);
  }

  public boolean isDerivedFrom (@NonNull final XdmQName aBase)
  {
    final String sNamespace = aBase.namespaceUri ();
    if (!m_aType.getAnonymous () &&
        sNamespace.equals (m_aType.getNamespace () == null ? "" : m_aType.getNamespace ()) &&
        aBase.localName ().equals (m_aType.getName ()))
      return true;
    // Xerces-J represents "no namespace" as null
    return m_aType.derivedFrom (sNamespace.isEmpty () ? null : sNamespace,
                                aBase.localName (),
                                DERIVATION_RESTRICTION_EXTENSION);
  }

  private static boolean _isSameNamed (@NonNull final XSTypeDefinition aLeft, @NonNull final XSTypeDefinition aRight)
  {
    if (aLeft.getAnonymous () || aRight.getAnonymous ())
      return false;
    final String sLeftNs = aLeft.getNamespace () == null ? "" : aLeft.getNamespace ();
    final String sRightNs = aRight.getNamespace () == null ? "" : aRight.getNamespace ();
    return sLeftNs.equals (sRightNs) && aLeft.getName ().equals (aRight.getName ());
  }

  private static boolean _isNamespaceSensitive (@Nullable final XSSimpleTypeDefinition aSimple)
  {
    if (aSimple == null)
      return false;
    switch (aSimple.getVariety ())
    {
      case XSSimpleTypeDefinition.VARIETY_ATOMIC:
      {
        final short nBuiltin = aSimple.getBuiltInKind ();
        return nBuiltin == XSConstants.QNAME_DT || nBuiltin == XSConstants.NOTATION_DT;
      }
      case XSSimpleTypeDefinition.VARIETY_LIST:
        return _isNamespaceSensitive (aSimple.getItemType ());
      case XSSimpleTypeDefinition.VARIETY_UNION:
      {
        final XSObjectList aMembers = aSimple.getMemberTypes ();
        for (int i = 0; i < aMembers.getLength (); ++i)
          if (_isNamespaceSensitive ((XSSimpleTypeDefinition) aMembers.item (i)))
            return true;
        return false;
      }
      default:
        return false;
    }
  }

  public boolean isNamespaceSensitive ()
  {
    return _isNamespaceSensitive (_getSimple ());
  }

  @NonNull
  private static ValidationState _createValidationState (@Nullable final UnaryOperator <String> aNamespaceResolver,
                                                         @NonNull final String sLexical)
  {
    final ValidationState aState = new ValidationState ();
    aState.setExtraChecking (false);
    aState.setFacetChecking (true);
    aState.setNormalizationRequired (true);
    final NamespaceSupport aNS = new NamespaceSupport ();
    if (aNamespaceResolver != null)
    {
      // Declare the prefixes the value uses; Xerces compares interned strings
      for (final String sToken : sLexical.trim ().split ("\\s+"))
      {
        final int nColon = sToken.indexOf (':');
        final String sPrefix = nColon > 0 ? sToken.substring (0, nColon) : "";
        final String sUri = aNamespaceResolver.apply (sPrefix);
        if (sUri != null)
          aNS.declarePrefix (sPrefix.intern (), sUri.isEmpty () ? null : sUri.intern ());
      }
    }
    aState.setNamespaceSupport (aNS);
    return aState;
  }

  @NonNull
  private static XdmTypedToken _token (@NonNull final String sLexical,
                                       @NonNull final XSSimpleTypeDefinition aAtomicType)
  {
    XdmQName aBuiltin = getBuiltinAtomicType (aAtomicType);
    if (aBuiltin == null)
      aBuiltin = new XdmQName (XdmConstants.NS_XS, "string", "xs");
    return new XdmTypedToken (sLexical, aBuiltin, new XercesTypeDefinition (aAtomicType));
  }

  /**
   * Validate an atomic value against an atomic or union type.
   */
  @NonNull
  private static XdmTypedToken _validateAtomic (@NonNull final XSSimpleType aType,
                                                @NonNull final String sLexical,
                                                @Nullable final UnaryOperator <String> aNamespaceResolver) throws InvalidDatatypeValueException
  {
    final ValidatedInfo aInfo = new ValidatedInfo ();
    aType.validate (sLexical, _createValidationState (aNamespaceResolver, sLexical), aInfo);
    XSSimpleTypeDefinition aActual = aType;
    if (aType.getVariety () == XSSimpleTypeDefinition.VARIETY_UNION && aInfo.memberType != null)
      aActual = aInfo.memberType;
    final String sNormalised = aInfo.normalizedValue != null ? aInfo.normalizedValue : sLexical;
    return _token (sNormalised, aActual);
  }

  @NonNull
  public ICommonsList <XdmTypedToken> validate (@NonNull final String sLexical,
                                                @Nullable final UnaryOperator <String> aNamespaceResolver) throws XdmValidationException
  {
    final XSSimpleTypeDefinition aSimple = _getSimple ();
    if (!(aSimple instanceof final XSSimpleType aType))
      throw new XdmValidationException ("Type " + getName () + " has no simple content",
                                        new IllegalStateException ("no simple content"));
    try
    {
      final ICommonsList <XdmTypedToken> ret = new CommonsArrayList <> ();
      if (aType.getVariety () == XSSimpleTypeDefinition.VARIETY_LIST)
      {
        // Validate the whole value (length facets), then each item
        final ValidatedInfo aInfo = new ValidatedInfo ();
        aType.validate (sLexical, _createValidationState (aNamespaceResolver, sLexical), aInfo);
        final XSSimpleTypeDefinition aItemType = aType.getItemType ();
        final String sNormalised = aInfo.normalizedValue != null ? aInfo.normalizedValue : sLexical.trim ();
        for (final String sToken : sNormalised.trim ().split ("\\s+"))
          if (!sToken.isEmpty ())
          {
            if (aItemType instanceof final XSSimpleType aItem)
              ret.add (_validateAtomic (aItem, sToken, aNamespaceResolver));
            else
              ret.add (_token (sToken, aItemType));
          }
      }
      else
        if (aType.getVariety () == XSSimpleTypeDefinition.VARIETY_ABSENT)
          ret.add (new XdmTypedToken (sLexical,
                                      new XdmQName (XdmConstants.NS_XS, "untypedAtomic", "xs"),
                                      new XercesTypeDefinition (aType)));
        else
          ret.add (_validateAtomic (aType, sLexical, aNamespaceResolver));
      return ret;
    }
    catch (final InvalidDatatypeValueException ex)
    {
      throw new XdmValidationException ("'" + sLexical + "' is not a valid value of type " + _describe () + ": " + ex.getMessage (),
                                        ex);
    }
  }

  @NonNull
  private String _describe ()
  {
    final XdmQName aName = getName ();
    return aName != null ? aName.getEQName () : "(anonymous)";
  }

  @NonNull
  public XdmQName getAnnotationName ()
  {
    // The name the validator reports, also for anonymous types
    final TypeInfo aTypeInfo = (TypeInfo) m_aType;
    final String sNamespace = aTypeInfo.getTypeNamespace ();
    final String sLocal = aTypeInfo.getTypeName ();
    return new XdmQName (sNamespace == null ? "" : sNamespace, sLocal == null || sLocal.isEmpty () ? "#anonymous"
                                                                                                  : sLocal);
  }

  @Override
  public boolean equals (final Object o)
  {
    if (o == this)
      return true;
    if (o == null || !getClass ().equals (o.getClass ()))
      return false;
    final XercesTypeDefinition rhs = (XercesTypeDefinition) o;
    return m_aType == rhs.m_aType;
  }

  @Override
  public int hashCode ()
  {
    return System.identityHashCode (m_aType);
  }

  @Override
  public String toString ()
  {
    return "XercesTypeDefinition[" + _describe () + "]";
  }
}
