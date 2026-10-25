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
package com.helger.xpath.xdm;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xpath.xdm.schema.XdmSchemaSupport;

/**
 * The schema type of a node that was validated against an XML schema while
 * the tree was built (see {@link XdmParser#setSchema(javax.xml.validation.Schema)}).
 * It wraps the type definition the JDK validator reports
 * ({@link org.w3c.dom.TypeInfo}) together with what XPath needs from it: the
 * type name, whether the type is simple, and the built-in atomic type of the
 * typed value. Instances are shared by all nodes of the same type.
 * <p>
 * Limits of the JDK validator API, on purpose not hidden: it does not tell
 * element-only from mixed content, so the typed value of an element with
 * complex content is always the untyped string value; and it gives no access to
 * element declarations or the members of a union type. The
 * {@link org.w3c.dom.TypeInfo} objects are the validator's own type
 * definitions; that they stay usable after parsing is the behaviour of the JDK
 * implementation, not an API guarantee.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmSchemaType
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
  private final TypeInfo m_aValueTypeInfo;
  private final XdmQName m_aName;
  private final boolean m_bSimple;
  private final boolean m_bList;
  private final XdmQName m_aAtomicBase;

  /**
   * @param aTypeInfo
   *        The type definition of the node, its type annotation.
   * @param aValueTypeInfo
   *        The type the value was validated against. It differs from aTypeInfo
   *        only for a union type, where it is the member type that matched.
   */
  XdmSchemaType (@NonNull final TypeInfo aTypeInfo, @NonNull final TypeInfo aValueTypeInfo)
  {
    ValueEnforcer.notNull (aTypeInfo, "TypeInfo");
    ValueEnforcer.notNull (aValueTypeInfo, "ValueTypeInfo");
    m_aTypeInfo = aTypeInfo;
    m_aValueTypeInfo = aValueTypeInfo;
    final String sNamespace = aTypeInfo.getTypeNamespace ();
    final String sLocal = aTypeInfo.getTypeName ();
    m_aName = new XdmQName (sNamespace == null ? "" : sNamespace, sLocal == null || sLocal.isEmpty () ? "#anonymous"
                                                                                                    : sLocal);
    m_bSimple = _isDerivedFrom (aTypeInfo, XdmConstants.NS_XS, "anySimpleType", TypeInfo.DERIVATION_RESTRICTION);
    m_bList = _isDerivedFrom (aValueTypeInfo, XdmConstants.NS_XS, "anySimpleType", TypeInfo.DERIVATION_LIST);
    m_aAtomicBase = _findAtomicBase (aValueTypeInfo, m_bList);
  }

  private static boolean _isDerivedFrom (@NonNull final TypeInfo aTypeInfo,
                                         @NonNull final String sNamespace,
                                         @NonNull final String sLocalName,
                                         final int nMethods)
  {
    final String sTypeNamespace = aTypeInfo.getTypeNamespace ();
    if (sNamespace.equals (sTypeNamespace == null ? "" : sTypeNamespace) && sLocalName.equals (aTypeInfo.getTypeName ()))
      return true;
    if (nMethods == DERIVATION_RESTRICTION_EXTENSION)
    {
      // With the schema component model (Apache Xerces-J); its DOM TypeInfo
      // view is not reliable for types in no namespace
      final IXdmTypeDefinition aDefinition = XdmSchemaSupport.getDefinition (aTypeInfo);
      if (aDefinition != null)
        return aDefinition.isDerivedFrom (new XdmQName (sNamespace, sLocalName));
    }
    return aTypeInfo.isDerivedFrom (sNamespace, sLocalName, nMethods);
  }

  @Nullable
  private static XdmQName _findAtomicBase (@NonNull final TypeInfo aValueTypeInfo, final boolean bList)
  {
    // A list type derives from its item type by list; a complex type with
    // simple content from its base by extension
    final int nMethods = bList ? DERIVATION_RESTRICTION_EXTENSION | TypeInfo.DERIVATION_LIST
                               : DERIVATION_RESTRICTION_EXTENSION;
    for (final String sBuiltin : BUILTIN_ATOMIC_TYPES)
      if (_isDerivedFrom (aValueTypeInfo, XdmConstants.NS_XS, sBuiltin, nMethods))
        return new XdmQName (XdmConstants.NS_XS, sBuiltin, "xs");
    return null;
  }

  /**
   * Create the annotation for a type of the schema component model.
   *
   * @param aType
   *        The type definition of the node. May not be <code>null</code>.
   * @param aValueType
   *        The type the value was validated against: for a union type the
   *        member type that matched, otherwise the same as aType. May not be
   *        <code>null</code>.
   * @return A new schema type.
   */
  @NonNull
  public static XdmSchemaType of (@NonNull final IXdmTypeDefinition aType, @NonNull final IXdmTypeDefinition aValueType)
  {
    return new XdmSchemaType (aType.getTypeInfo (), aValueType.getTypeInfo ());
  }

  /**
   * @return The schema component model of the type, if the validator provides
   *         one (Apache Xerces-J, see {@link XdmSchemaSupport}), otherwise
   *         <code>null</code> (the JDK validator).
   */
  @Nullable
  public IXdmTypeDefinition getDefinition ()
  {
    return XdmSchemaSupport.getDefinition (m_aTypeInfo);
  }

  /**
   * @return The schema component model of the type the value was validated
   *         against (for a union the matching member type), or
   *         <code>null</code> like {@link #getDefinition()}.
   */
  @Nullable
  public IXdmTypeDefinition getValueDefinition ()
  {
    return XdmSchemaSupport.getDefinition (m_aValueTypeInfo);
  }

  /**
   * @return The type definition as reported by the JDK validator. Never
   *         <code>null</code>.
   */
  @NonNull
  public TypeInfo getTypeInfo ()
  {
    return m_aTypeInfo;
  }

  /**
   * @return The type name. Anonymous types carry the name the JDK gives them,
   *         which starts with <code>#</code> (see {@link #isAnonymous()}).
   */
  @NonNull
  public XdmQName getName ()
  {
    return m_aName;
  }

  /**
   * @return <code>true</code> if the type is anonymous.
   */
  public boolean isAnonymous ()
  {
    return m_aName.localName ().startsWith ("#");
  }

  /**
   * @return <code>true</code> for a simple type (atomic, list or union),
   *         <code>false</code> for a complex type.
   */
  public boolean isSimple ()
  {
    return m_bSimple;
  }

  /**
   * @return <code>true</code> if the typed value is a list: a list type or a
   *         complex type whose simple content is a list type.
   */
  public boolean isList ()
  {
    return m_bList;
  }

  /**
   * @return The built-in atomic type (in the <code>xs</code> namespace) of the
   *         typed value, for a list type the type of its items.
   *         <code>null</code> for complex content and for types whose built-in
   *         base cannot be determined (a union that was not resolved to a
   *         member, a list of a union); their typed value is the untyped
   *         string value.
   */
  @Nullable
  public XdmQName getAtomicBase ()
  {
    return m_aAtomicBase;
  }

  /**
   * @param sAtomicLocalName
   *        Local name of a built-in atomic type.
   * @return <code>true</code> if the typed value is a single value and
   *         {@link #getAtomicBase()} is exactly the given built-in atomic type
   *         (the most specific built-in type the value type derives from).
   */
  public boolean hasAtomicBase (@NonNull final String sAtomicLocalName)
  {
    return !m_bList && m_aAtomicBase != null && m_aAtomicBase.localName ().equals (sAtomicLocalName);
  }

  /**
   * The schema derivation check: is this type the given type, or derived from
   * it by restriction or extension? Union membership and list item types are
   * not derivation steps here.
   *
   * @param sNamespace
   *        Namespace URI of the base type. May not be <code>null</code>.
   * @param sLocalName
   *        Local name of the base type. May not be <code>null</code>.
   * @return <code>true</code> if derived.
   */
  public boolean isDerivedFrom (@NonNull final String sNamespace, @NonNull final String sLocalName)
  {
    return _isDerivedFrom (m_aTypeInfo, sNamespace, sLocalName, DERIVATION_RESTRICTION_EXTENSION);
  }

  @Override
  public String toString ()
  {
    return "XdmSchemaType[" + m_aName + (m_bList ? ", list of " : ", ") + m_aAtomicBase + "]";
  }
}
