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

import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.xdm.schema.EXdmSimpleVariety;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * The schema type of a node of a schema-validated tree (see
 * {@link com.helger.xpath.xdm.schema.IXdmSchemaSet}): the type definition of
 * the node together with what XPath needs from it: the type annotation name,
 * whether the type is simple, and the built-in atomic type of the typed value.
 * Instances are usually shared by all nodes of the same type.
 * <p>
 * With a provider that has no schema component model (the JDK validator,
 * {@link IXdmTypeDefinition#hasComponentModel()}), element-only and mixed
 * content cannot be told apart, so the typed value of an element with complex
 * content is always the untyped string value, and the members of a union type
 * are not known.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmSchemaType
{
  private static final XdmQName ANY_SIMPLE_TYPE = new XdmQName (XdmConstants.NS_XS, "anySimpleType");

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

  private final IXdmTypeDefinition m_aType;
  private final IXdmTypeDefinition m_aValueType;
  private final XdmQName m_aName;
  private final boolean m_bSimple;
  private final boolean m_bList;
  private final XdmQName m_aAtomicBase;

  private XdmSchemaType (@NonNull final IXdmTypeDefinition aType, @NonNull final IXdmTypeDefinition aValueType)
  {
    m_aType = aType;
    m_aValueType = aValueType;
    m_aName = aType.getAnnotationName ();
    m_bSimple = !aType.isComplex ();
    // The typed value comes from the simple content of the value type; a
    // list is typed by its item type. As with the DOM TypeInfo derivation
    // checks of the validators: xs:anySimpleType counts as a list, and a
    // complex type with list content has no known built-in item type.
    final IXdmTypeDefinition aContent = aValueType.getSimpleContentType ();
    m_bList = aContent != null && aContent.getVariety () == EXdmSimpleVariety.LIST ||
              aValueType.getAnnotationName ().equals (ANY_SIMPLE_TYPE);
    if (m_bList)
    {
      final IXdmTypeDefinition aItem = aContent == null || aValueType.isComplex () ? null : aContent.getItemType ();
      m_aAtomicBase = aItem == null ? null : aItem.getBuiltinAtomicType ();
    }
    else
      m_aAtomicBase = findBuiltinAtomicType (aValueType::isDerivedFrom);
  }

  /**
   * @param aDerivesFrom
   *        The derivation check of a type against a built-in atomic type
   *        name.
   * @return The most specific built-in atomic type the check accepts, or
   *         <code>null</code>.
   */
  @Nullable
  public static XdmQName findBuiltinAtomicType (@NonNull final Predicate <XdmQName> aDerivesFrom)
  {
    for (final String sBuiltin : BUILTIN_ATOMIC_TYPES)
    {
      final XdmQName aName = new XdmQName (XdmConstants.NS_XS, sBuiltin, "xs");
      if (aDerivesFrom.test (aName))
        return aName;
    }
    return null;
  }

  /**
   * Create the annotation for a type definition.
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
    ValueEnforcer.notNull (aType, "Type");
    ValueEnforcer.notNull (aValueType, "ValueType");
    return new XdmSchemaType (aType, aValueType);
  }

  /**
   * @return The type definition of the node, as the schema provider reports
   *         it. Never <code>null</code>.
   */
  @NonNull
  public IXdmTypeDefinition getTypeDefinition ()
  {
    return m_aType;
  }

  /**
   * @return The type definition the value was validated against (for a union
   *         the matching member type). Never <code>null</code>.
   */
  @NonNull
  public IXdmTypeDefinition getValueTypeDefinition ()
  {
    return m_aValueType;
  }

  /**
   * @return The type definition of the node if the provider has a schema
   *         component model (Apache Xerces-J, see
   *         {@link com.helger.xpath.xdm.schema.XdmSchemaSupport}), otherwise
   *         <code>null</code> (the JDK validator).
   */
  @Nullable
  public IXdmTypeDefinition getDefinition ()
  {
    return m_aType.hasComponentModel () ? m_aType : null;
  }

  /**
   * @return The type definition the value was validated against (for a union
   *         the matching member type), or <code>null</code> like
   *         {@link #getDefinition()}.
   */
  @Nullable
  public IXdmTypeDefinition getValueDefinition ()
  {
    return m_aValueType.hasComponentModel () ? m_aValueType : null;
  }

  /**
   * @return The type name. Anonymous types carry a name whose local part
   *         starts with <code>#</code> (see {@link #isAnonymous()}).
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
    if (sNamespace.equals (m_aName.namespaceUri ()) && sLocalName.equals (m_aName.localName ()))
      return true;
    return m_aType.isDerivedFrom (new XdmQName (sNamespace, sLocalName));
  }

  @Override
  public String toString ()
  {
    return "XdmSchemaType[" + m_aName + (m_bList ? ", list of " : ", ") + m_aAtomicBase + "]";
  }
}
