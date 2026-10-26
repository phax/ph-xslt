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

import java.util.function.Function;
import java.util.function.IntFunction;

import javax.xml.validation.TypeInfoProvider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.xdm.EIDKind;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmSchemaType;
import com.helger.xpath.xdm.XdmTreeBuilder;
import com.helger.xpath.xdm.schema.EXdmContentType;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * Sits between a JAXP {@link javax.xml.validation.ValidatorHandler} and an
 * {@link XdmTreeBuilder}: it passes all content events on to the builder and
 * annotates the elements and attributes the builder creates with their
 * {@link XdmSchemaType}, sets the <code>nilled</code> property and the schema
 * ID kinds, and drops whitespace in element-only content, as XDM construction
 * from a PSVI does.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class JaxpTypeAnnotator implements ContentHandler
{
  private static final XdmQName ANY_TYPE = new XdmQName (XdmConstants.NS_XS, "anyType", "xs");

  // The annotation and the value type of a node, compared by identity
  private record TypeKey (@NonNull TypeInfo typeInfo, @NonNull TypeInfo valueTypeInfo)
  {
    @Override
    public boolean equals (final Object o)
    {
      return o instanceof final TypeKey aOther && aOther.typeInfo == typeInfo && aOther.valueTypeInfo == valueTypeInfo;
    }

    @Override
    public int hashCode ()
    {
      return System.identityHashCode (typeInfo) * 31 + System.identityHashCode (valueTypeInfo);
    }
  }

  private final XdmTreeBuilder m_aBuilder;
  private final TypeInfoProvider m_aTypeInfoProvider;
  private final IntFunction <TypeInfo> m_aDeclaredAttributeTypes;
  private final Function <TypeInfo, IXdmTypeDefinition> m_aDefinitions;
  private final ICommonsMap <TypeKey, XdmSchemaType> m_aSchemaTypes = new CommonsHashMap <> ();
  // The type reported at the start of each open element; may contain null
  private final ICommonsList <TypeInfo> m_aStartTypes = new CommonsArrayList <> ();

  /**
   * @param aBuilder
   *        The builder that receives the content events.
   * @param aTypeInfoProvider
   *        The type info provider of the validator handler.
   * @param aDeclaredAttributeTypes
   *        From the attribute index of the current start tag to the declared
   *        type of the attribute (for a union type the union, not the member
   *        that matched). May be <code>null</code> if the validator does not
   *        tell; then the member type is the annotation.
   * @param aDefinitions
   *        From a type the validator reports to its type definition.
   */
  JaxpTypeAnnotator (@NonNull final XdmTreeBuilder aBuilder,
                     @NonNull final TypeInfoProvider aTypeInfoProvider,
                     @Nullable final IntFunction <TypeInfo> aDeclaredAttributeTypes,
                     @NonNull final Function <TypeInfo, IXdmTypeDefinition> aDefinitions)
  {
    m_aBuilder = aBuilder;
    m_aTypeInfoProvider = aTypeInfoProvider;
    m_aDeclaredAttributeTypes = aDeclaredAttributeTypes;
    m_aDefinitions = aDefinitions;
  }

  @NonNull
  private XdmSchemaType _getSchemaType (@NonNull final TypeInfo aTypeInfo, @NonNull final TypeInfo aValueTypeInfo)
  {
    return m_aSchemaTypes.computeIfAbsent (new TypeKey (aTypeInfo, aValueTypeInfo),
                                           k -> XdmSchemaType.of (m_aDefinitions.apply (k.typeInfo ()),
                                                                  m_aDefinitions.apply (k.valueTypeInfo ())));
  }

  private static boolean _isSingleToken (@NonNull final String sValue)
  {
    final String s = sValue.trim ();
    if (s.isEmpty ())
      return false;
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r')
        return false;
    }
    return true;
  }

  /**
   * XDM 3.1 sections 6.2.3 and 6.3.3: <code>is-id</code> if the typed value is
   * a single xs:ID (also as the only item of a list),
   * <code>is-idrefs</code> if an item of the typed value is an xs:IDREF.
   */
  @NonNull
  private static EIDKind _getSchemaIDKind (@NonNull final XdmSchemaType aType, @NonNull final String sValue)
  {
    final XdmQName aBase = aType.getAtomicBase ();
    if (aBase == null)
      return EIDKind.NONE;
    switch (aBase.localName ())
    {
      case "ID":
        return !aType.isList () || _isSingleToken (sValue) ? EIDKind.ID : EIDKind.NONE;
      case "IDREF":
        return aType.isList () ? EIDKind.IDREFS : EIDKind.IDREF;
      default:
        return EIDKind.NONE;
    }
  }

  private static boolean _isXsiNilTrue (@NonNull final String sValue)
  {
    final String s = sValue.trim ();
    return "true".equals (s) || "1".equals (s);
  }

  /**
   * The JDK validator reports whitespace in element-only content as ignorable
   * whitespace, Apache Xerces-J does not. With Xerces-J the content type is
   * known from the schema component model, so such whitespace text nodes are
   * dropped here (XDM 3.1 section 6.7.3).
   */
  private static void _dropElementOnlyWhitespace (@NonNull final XdmNode aElement, @NonNull final XdmSchemaType aType)
  {
    if (aElement.getChildCount () == 0)
      return;
    final IXdmTypeDefinition aDef = aType.getDefinition ();
    if (aDef == null)
      return;
    final EXdmContentType eContent = aDef.getContentType ();
    if (eContent != EXdmContentType.ELEMENT_ONLY && eContent != EXdmContentType.EMPTY)
      return;
    for (int i = aElement.getChildCount () - 1; i >= 0; --i)
    {
      final XdmNode aChild = aElement.getChildAtIndex (i);
      if (aChild.isText () && aChild.getValue ().isBlank ())
        aElement.removeChild (aChild);
    }
  }

  public void setDocumentLocator (final Locator aLocator)
  {
    m_aBuilder.setDocumentLocator (aLocator);
  }

  public void startDocument () throws SAXException
  {
    m_aBuilder.startDocument ();
  }

  public void endDocument () throws SAXException
  {
    m_aBuilder.endDocument ();
  }

  public void startPrefixMapping (final String sPrefix, final String sUri) throws SAXException
  {
    m_aBuilder.startPrefixMapping (sPrefix, sUri);
  }

  public void endPrefixMapping (final String sPrefix) throws SAXException
  {
    m_aBuilder.endPrefixMapping (sPrefix);
  }

  public void startElement (final String sUri,
                            final String sLocalName,
                            final String sQName,
                            final Attributes aAttrs) throws SAXException
  {
    m_aStartTypes.add (m_aTypeInfoProvider.getElementTypeInfo ());
    m_aBuilder.startElement (sUri, sLocalName, sQName, aAttrs);
    final XdmNode aElement = m_aBuilder.getCurrentNode ();
    for (int i = 0; i < aAttrs.getLength (); ++i)
    {
      // Namespace declarations are not attributes in XDM
      final XdmNode aAttr = aElement.getAttribute (aAttrs.getURI (i), aAttrs.getLocalName (i));
      if (aAttr == null)
        continue;
      final String sValue = aAttrs.getValue (i);
      final TypeInfo aTypeInfo = m_aTypeInfoProvider.getAttributeTypeInfo (i);
      if (aTypeInfo != null)
      {
        // The validator reports the member type of a union; the annotation is
        // the declared type
        final TypeInfo aDeclared = m_aDeclaredAttributeTypes != null ? m_aDeclaredAttributeTypes.apply (i) : null;
        final XdmSchemaType aAttrType = _getSchemaType (aDeclared != null ? aDeclared : aTypeInfo, aTypeInfo);
        aAttr.setSchemaType (aAttrType);
        final EIDKind eSchemaIDKind = m_aTypeInfoProvider.isIdAttribute (i) ? EIDKind.ID
                                                                            : _getSchemaIDKind (aAttrType, sValue);
        if (eSchemaIDKind != EIDKind.NONE)
        {
          aAttr.setIDKind (eSchemaIDKind);
          aAttr.setValue (XdmTreeBuilder.normaliseTokenised (sValue));
        }
      }
      // The document is valid, so xsi:nil="true" means nilled
      if (aAttr.getName ().isSameName (XdmConstants.NS_XSI, "nil") && _isXsiNilTrue (sValue))
        aElement.setNilled (true);
    }
  }

  public void endElement (final String sUri, final String sLocalName, final String sQName) throws SAXException
  {
    // At the end tag the validator reports the member type of a union
    final TypeInfo aStartType = m_aStartTypes.removeLast ();
    final TypeInfo aEndType = m_aTypeInfoProvider.getElementTypeInfo ();
    m_aBuilder.endElement (sUri, sLocalName, sQName);
    // The builder added pending text and returned to the parent, whose last
    // child is the element
    final XdmNode aParent = m_aBuilder.getCurrentNode ();
    final XdmNode aElement = aParent.getChildAtIndex (aParent.getChildCount () - 1);
    if (aStartType == null && aEndType == null)
    {
      // Not assessed, e.g. below a skip wildcard
      aElement.setTypeAnnotation (ANY_TYPE);
    }
    else
    {
      final XdmSchemaType aType = _getSchemaType (aStartType != null ? aStartType : aEndType,
                                                  aEndType != null ? aEndType : aStartType);
      aElement.setSchemaType (aType);
      _dropElementOnlyWhitespace (aElement, aType);
      if (aType.getAtomicBase () != null && !aElement.isNilled ())
        aElement.setIDKind (_getSchemaIDKind (aType, aElement.getStringValue ()));
    }
  }

  public void characters (final char [] aChars, final int nStart, final int nLength) throws SAXException
  {
    m_aBuilder.characters (aChars, nStart, nLength);
  }

  public void ignorableWhitespace (final char [] aChars, final int nStart, final int nLength) throws SAXException
  {
    m_aBuilder.ignorableWhitespace (aChars, nStart, nLength);
  }

  public void processingInstruction (final String sTarget, final String sData) throws SAXException
  {
    m_aBuilder.processingInstruction (sTarget, sData);
  }

  public void skippedEntity (final String sName) throws SAXException
  {
    m_aBuilder.skippedEntity (sName);
  }
}
