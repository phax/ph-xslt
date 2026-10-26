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
package com.helger.xpath.xdm.jaxp;

import java.util.function.UnaryOperator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.EXdmContentType;
import com.helger.xpath.xdm.schema.EXdmSimpleVariety;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xpath.xdm.schema.XdmTypedToken;

/**
 * {@link IXdmTypeDefinition} over a type the JDK validator reports. The JDK
 * gives no schema component model, only the type name and derivation checks
 * ({@link TypeInfo#isDerivedFrom(String, String, int)}), so this definition
 * {@link #hasComponentModel() has no component model}: whether a type is
 * simple, a list, and its built-in atomic type are derived from those checks,
 * and the simple content type of a type is the type itself. The
 * {@link TypeInfo} objects are the validator's own type definitions; that
 * they stay usable after validation is the behaviour of the JDK
 * implementation, not an API guarantee.
 *
 * @author Philip Helger
 */
@Immutable
final class JdkTypeDefinition implements IXdmTypeDefinition
{
  private static final int DERIVATION_RESTRICTION_EXTENSION = TypeInfo.DERIVATION_RESTRICTION |
                                                              TypeInfo.DERIVATION_EXTENSION;

  /**
   * The built-in atomic types, every type before all of its base types, so the
   * first one a type derives from is the most specific one.
   */
  private static final String [] BUILTIN_ATOMIC_TYPES = { "ID",
                                                          "IDREF",
                                                          "ENTITY",
                                                          "NCName",
                                                          "Name",
                                                          "NMTOKEN",
                                                          "language",
                                                          "token",
                                                          "normalizedString",
                                                          "string",
                                                          "byte",
                                                          "short",
                                                          "int",
                                                          "long",
                                                          "unsignedByte",
                                                          "unsignedShort",
                                                          "unsignedInt",
                                                          "unsignedLong",
                                                          "positiveInteger",
                                                          "nonNegativeInteger",
                                                          "negativeInteger",
                                                          "nonPositiveInteger",
                                                          "integer",
                                                          "decimal",
                                                          "double",
                                                          "float",
                                                          "boolean",
                                                          "dateTimeStamp",
                                                          "dateTime",
                                                          "date",
                                                          "time",
                                                          "yearMonthDuration",
                                                          "dayTimeDuration",
                                                          "duration",
                                                          "gYearMonth",
                                                          "gYear",
                                                          "gMonthDay",
                                                          "gDay",
                                                          "gMonth",
                                                          "hexBinary",
                                                          "base64Binary",
                                                          "anyURI",
                                                          "QName",
                                                          "NOTATION" };

  private final TypeInfo m_aTypeInfo;
  // true for the item type of a list type: the same TypeInfo, with the
  // derivation by list counted
  private final boolean m_bListItem;
  private final boolean m_bComplex;
  private final boolean m_bList;
  private final XdmQName m_aBuiltinAtomicType;

  /**
   * @param aTypeInfo
   *        The type the JDK validator reported.
   * @param bListItem
   *        <code>true</code> for the view on the item type of a list type.
   */
  JdkTypeDefinition (@NonNull final TypeInfo aTypeInfo, final boolean bListItem)
  {
    m_aTypeInfo = aTypeInfo;
    m_bListItem = bListItem;
    m_bComplex = !bListItem && !_isDerivedFrom (aTypeInfo, XdmConstants.NS_XS, "anySimpleType", TypeInfo.DERIVATION_RESTRICTION);
    m_bList = !bListItem && _isDerivedFrom (aTypeInfo, XdmConstants.NS_XS, "anySimpleType", TypeInfo.DERIVATION_LIST);
    m_aBuiltinAtomicType = _findAtomicBase (aTypeInfo, bListItem);
  }

  private static boolean _isDerivedFrom (@NonNull final TypeInfo aTypeInfo,
                                         @NonNull final String sNamespace,
                                         @NonNull final String sLocalName,
                                         final int nMethods)
  {
    final String sTypeNamespace = aTypeInfo.getTypeNamespace ();
    if (sNamespace.equals (sTypeNamespace == null ? "" : sTypeNamespace) && sLocalName.equals (aTypeInfo.getTypeName ()))
      return true;
    return aTypeInfo.isDerivedFrom (sNamespace, sLocalName, nMethods);
  }

  @Nullable
  private static XdmQName _findAtomicBase (@NonNull final TypeInfo aTypeInfo, final boolean bListItem)
  {
    // A list type derives from its item type by list; a complex type with
    // simple content from its base by extension
    final int nMethods = bListItem ? DERIVATION_RESTRICTION_EXTENSION | TypeInfo.DERIVATION_LIST
                                   : DERIVATION_RESTRICTION_EXTENSION;
    for (final String sBuiltin : BUILTIN_ATOMIC_TYPES)
      if (_isDerivedFrom (aTypeInfo, XdmConstants.NS_XS, sBuiltin, nMethods))
        return new XdmQName (XdmConstants.NS_XS, sBuiltin, "xs");
    return null;
  }

  @Nullable
  public XdmQName getName ()
  {
    if (m_bListItem)
      return null;
    final String sLocal = m_aTypeInfo.getTypeName ();
    if (sLocal == null || sLocal.isEmpty () || sLocal.startsWith ("#"))
      return null;
    final String sNamespace = m_aTypeInfo.getTypeNamespace ();
    return new XdmQName (sNamespace == null ? "" : sNamespace, sLocal);
  }

  @NonNull
  public XdmQName getAnnotationName ()
  {
    final String sNamespace = m_aTypeInfo.getTypeNamespace ();
    final String sLocal = m_aTypeInfo.getTypeName ();
    return new XdmQName (sNamespace == null ? "" : sNamespace, sLocal == null || sLocal.isEmpty () ? "#anonymous"
                                                                                                  : sLocal);
  }

  public boolean hasComponentModel ()
  {
    return false;
  }

  public boolean isComplex ()
  {
    return m_bComplex;
  }

  /**
   * @return {@link EXdmContentType#SIMPLE} for a simple type.
   * @throws UnsupportedOperationException
   *         for a complex type: the JDK does not tell element-only from mixed
   *         content
   */
  @NonNull
  public EXdmContentType getContentType ()
  {
    if (m_bComplex)
      throw new UnsupportedOperationException ("The JDK validator does not tell the content type");
    return EXdmContentType.SIMPLE;
  }

  @NonNull
  public IXdmTypeDefinition getSimpleContentType ()
  {
    return this;
  }

  @Nullable
  public EXdmSimpleVariety getVariety ()
  {
    if (m_bList)
      return EXdmSimpleVariety.LIST;
    return m_aBuiltinAtomicType != null ? EXdmSimpleVariety.ATOMIC : null;
  }

  @Nullable
  public IXdmTypeDefinition getItemType ()
  {
    return m_bList ? new JdkTypeDefinition (m_aTypeInfo, true) : null;
  }

  /**
   * @return Always empty: the JDK does not tell the member types of a union.
   */
  @NonNull
  public ICommonsList <IXdmTypeDefinition> getMemberTypes ()
  {
    return new CommonsArrayList <> ();
  }

  @Nullable
  public XdmQName getBuiltinAtomicType ()
  {
    return m_aBuiltinAtomicType;
  }

  /**
   * @throws UnsupportedOperationException
   *         always: the JDK does not tell the base type
   */
  @Nullable
  public IXdmTypeDefinition getBaseType ()
  {
    throw new UnsupportedOperationException ("The JDK validator does not tell the base type");
  }

  public boolean isDerivedFrom (@NonNull final IXdmTypeDefinition aBase)
  {
    if (aBase instanceof final JdkTypeDefinition aOther && aOther.m_aTypeInfo == m_aTypeInfo)
      return true;
    final XdmQName aBaseName = aBase.getName ();
    return aBaseName != null && isDerivedFrom (aBaseName);
  }

  public boolean isDerivedFrom (@NonNull final XdmQName aBase)
  {
    return _isDerivedFrom (m_aTypeInfo, aBase.namespaceUri (), aBase.localName (), DERIVATION_RESTRICTION_EXTENSION);
  }

  public boolean isNamespaceSensitive ()
  {
    return m_aBuiltinAtomicType != null &&
           ("QName".equals (m_aBuiltinAtomicType.localName ()) ||
            "NOTATION".equals (m_aBuiltinAtomicType.localName ()));
  }

  /**
   * @throws UnsupportedOperationException
   *         always: the JDK does not offer validation of single values
   */
  @NonNull
  public ICommonsList <XdmTypedToken> validate (@NonNull final String sLexical,
                                                @Nullable final UnaryOperator <String> aNamespaceResolver)
  {
    throw new UnsupportedOperationException ("The JDK validator does not validate single values");
  }

  @Override
  public boolean equals (final Object o)
  {
    if (o == this)
      return true;
    if (o == null || !getClass ().equals (o.getClass ()))
      return false;
    final JdkTypeDefinition rhs = (JdkTypeDefinition) o;
    return m_aTypeInfo == rhs.m_aTypeInfo && m_bListItem == rhs.m_bListItem;
  }

  @Override
  public int hashCode ()
  {
    return System.identityHashCode (m_aTypeInfo) * 31 + (m_bListItem ? 1 : 0);
  }

  @Override
  public String toString ()
  {
    return "JdkTypeDefinition[" + getAnnotationName ().getEQName () + (m_bListItem ? ", item" : "") + "]";
  }
}
