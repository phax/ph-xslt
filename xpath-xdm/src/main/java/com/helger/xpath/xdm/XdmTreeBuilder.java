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

import java.util.ArrayDeque;

import javax.xml.validation.TypeInfoProvider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.xdm.schema.EXdmContentType;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * SAX2 handler that builds an {@link XdmNode} tree. Adjacent character events
 * are merged into one text node, comments inside the DTD are dropped and the
 * location of an element is the position the parser reports at the end of its
 * start tag. DTD attribute types ID, IDREF and IDREFS are recorded, and the
 * value of such an attribute as well as of <code>xml:id</code> is normalised
 * like a tokenised attribute (XDM 3.1 section 6.3.3).
 * <p>
 * Behind a {@link javax.xml.validation.ValidatorHandler} (see
 * {@link #setTypeInfoProvider(TypeInfoProvider)}) the builder annotates
 * elements and attributes with their {@link XdmSchemaType}, sets the
 * <code>nilled</code> property and the schema ID kinds, and drops whitespace in
 * element-only content, as XDM construction from a PSVI does.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmTreeBuilder extends DefaultHandler2
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

  private TypeInfoProvider m_aTypeInfoProvider;
  private final ICommonsMap <TypeKey, XdmSchemaType> m_aSchemaTypes = new CommonsHashMap <> ();
  // The type reported at the start of each open element; may contain null
  private final ICommonsList <TypeInfo> m_aStartTypes = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aPendingPrefixes = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aPendingUris = new CommonsArrayList <> ();
  private final StringBuilder m_aText = new StringBuilder ();
  private Locator m_aLocator;
  private XdmNode m_aDocument;
  private XdmNode m_aCurrent;
  private boolean m_bInDTD = false;
  // The system ID in effect for each open element
  private final ArrayDeque <String> m_aSystemIds = new ArrayDeque <> ();

  private void _flushText ()
  {
    if (!m_aText.isEmpty ())
    {
      m_aCurrent.appendChild (XdmNode.createText (m_aText.toString ()));
      m_aText.setLength (0);
    }
  }

  /**
   * A node read from an external parsed entity gets the entity's URI as base
   * URI, but only the outermost node of the entity: its descendants inherit it
   * (and any <code>xml:base</code> in between) through the parent chain.
   *
   * @return The system ID in effect for the node.
   */
  @Nullable
  private String _applyEntityBase (@NonNull final XdmNode aNode)
  {
    final String sParentSystemId = m_aSystemIds.isEmpty () ? m_aDocument.getDocumentUri () : m_aSystemIds.peek ();
    if (m_aLocator == null)
      return sParentSystemId;
    final String sSystemId = m_aLocator.getSystemId ();
    if (sSystemId != null && !sSystemId.equals (sParentSystemId))
      aNode.setBaseUri (sSystemId);
    return sSystemId;
  }

  @NonNull
  private static String _getPrefix (@NonNull final String sQName)
  {
    final int nIndex = sQName.indexOf (':');
    return nIndex < 0 ? "" : sQName.substring (0, nIndex);
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

  @NonNull
  private static String _normaliseTokenised (@NonNull final String sValue)
  {
    // Trim and collapse runs of spaces; XML attribute normalisation already
    // turned all other whitespace characters into spaces
    if (sValue.indexOf (' ') < 0)
      return sValue;
    final StringBuilder aSB = new StringBuilder (sValue.length ());
    boolean bPendingSpace = false;
    for (int i = 0; i < sValue.length (); ++i)
    {
      final char c = sValue.charAt (i);
      if (c == ' ')
        bPendingSpace = !aSB.isEmpty ();
      else
      {
        if (bPendingSpace)
          aSB.append (' ');
        bPendingSpace = false;
        aSB.append (c);
      }
    }
    return aSB.toString ();
  }

  @NonNull
  private static EIDKind _getIDKind (@Nullable final String sSaxType)
  {
    if (sSaxType != null)
      switch (sSaxType)
      {
        case "ID":
          return EIDKind.ID;
        case "IDREF":
          return EIDKind.IDREF;
        case "IDREFS":
          return EIDKind.IDREFS;
        default:
          break;
      }
    return EIDKind.NONE;
  }

  @NonNull
  private XdmSchemaType _getSchemaType (@NonNull final TypeInfo aTypeInfo, @NonNull final TypeInfo aValueTypeInfo)
  {
    return m_aSchemaTypes.computeIfAbsent (new TypeKey (aTypeInfo, aValueTypeInfo),
                                           k -> new XdmSchemaType (k.typeInfo (), k.valueTypeInfo ()));
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
   * Annotate the tree with the schema information of a
   * {@link javax.xml.validation.ValidatorHandler} that feeds this builder.
   * Must be set before parsing starts.
   *
   * @param aTypeInfoProvider
   *        The type info provider of the validator handler;
   *        <code>null</code> for an unvalidated tree.
   * @return this for chaining
   */
  @NonNull
  public XdmTreeBuilder setTypeInfoProvider (@Nullable final TypeInfoProvider aTypeInfoProvider)
  {
    m_aTypeInfoProvider = aTypeInfoProvider;
    return this;
  }

  /**
   * @return The finished document. Only valid after the parse run completed.
   */
  @NonNull
  public XdmNode getDocument ()
  {
    if (m_aDocument == null)
      throw new IllegalStateException ("No document was built yet");
    return m_aDocument;
  }

  @Override
  public void setDocumentLocator (final Locator aLocator)
  {
    m_aLocator = aLocator;
  }

  @Override
  public void startDocument ()
  {
    m_aDocument = XdmNode.createDocument ();
    m_aCurrent = m_aDocument;
    if (m_aLocator != null)
      m_aDocument.setDocumentUri (m_aLocator.getSystemId ());
  }

  @Override
  public void endDocument ()
  {
    _flushText ();
    m_aDocument.assignDocumentOrder ();
  }

  @Override
  public void startPrefixMapping (final String sPrefix, final String sUri)
  {
    m_aPendingPrefixes.add (sPrefix);
    m_aPendingUris.add (sUri);
  }

  @Override
  public void startElement (final String sUri,
                            final String sLocalName,
                            final String sQName,
                            final Attributes aAttrs)
  {
    _flushText ();
    final XdmNode aElement = XdmNode.createElement (new XdmQName (sUri, sLocalName, _getPrefix (sQName)));
    if (m_aLocator != null)
      aElement.setLocation (m_aLocator.getLineNumber (), m_aLocator.getColumnNumber ());
    for (int i = 0; i < m_aPendingPrefixes.size (); ++i)
      aElement.declareNamespace (m_aPendingPrefixes.get (i), m_aPendingUris.get (i));
    m_aPendingPrefixes.clear ();
    m_aPendingUris.clear ();
    final String sSystemId = _applyEntityBase (aElement);
    if (m_aTypeInfoProvider != null)
      m_aStartTypes.add (m_aTypeInfoProvider.getElementTypeInfo ());

    for (int i = 0; i < aAttrs.getLength (); ++i)
    {
      final String sAttrQName = aAttrs.getQName (i);
      // Namespace declarations are not attributes in XDM
      if (sAttrQName.equals ("xmlns") || sAttrQName.startsWith ("xmlns:"))
        continue;
      final XdmQName aAttrName = new XdmQName (aAttrs.getURI (i), aAttrs.getLocalName (i), _getPrefix (sAttrQName));
      final String sValue = aAttrs.getValue (i);
      EIDKind eIDKind = _getIDKind (aAttrs.getType (i));
      XdmSchemaType aAttrType = null;
      if (m_aTypeInfoProvider != null)
      {
        final TypeInfo aTypeInfo = m_aTypeInfoProvider.getAttributeTypeInfo (i);
        if (aTypeInfo != null)
        {
          aAttrType = _getSchemaType (aTypeInfo, aTypeInfo);
          final EIDKind eSchemaIDKind = m_aTypeInfoProvider.isIdAttribute (i) ? EIDKind.ID
                                                                              : _getSchemaIDKind (aAttrType, sValue);
          if (eSchemaIDKind != EIDKind.NONE)
            eIDKind = eSchemaIDKind;
        }
        // The document is valid, so xsi:nil="true" means nilled
        if (aAttrName.isSameName (XdmConstants.NS_XSI, "nil") && _isXsiNilTrue (sValue))
          aElement.setNilled (true);
      }
      if (eIDKind == EIDKind.NONE && aAttrName.isSameName (XdmConstants.NS_XML, "id"))
        eIDKind = EIDKind.ID;
      final XdmNode aAttr = XdmNode.createAttribute (aAttrName,
                                                     eIDKind == EIDKind.NONE ? sValue : _normaliseTokenised (sValue));
      aAttr.setIDKind (eIDKind);
      aAttr.setSchemaType (aAttrType);
      if (m_aLocator != null)
        aAttr.setLocation (m_aLocator.getLineNumber (), m_aLocator.getColumnNumber ());
      aElement.addAttribute (aAttr);
    }
    m_aCurrent.appendChild (aElement);
    m_aCurrent = aElement;
    m_aSystemIds.push (sSystemId == null ? "" : sSystemId);
  }

  @Override
  public void endElement (final String sUri, final String sLocalName, final String sQName)
  {
    _flushText ();
    if (m_aTypeInfoProvider != null)
    {
      // At the end tag the validator reports the member type of a union
      final TypeInfo aStartType = m_aStartTypes.removeLast ();
      final TypeInfo aEndType = m_aTypeInfoProvider.getElementTypeInfo ();
      if (aStartType == null && aEndType == null)
      {
        // Not assessed, e.g. below a skip wildcard
        m_aCurrent.setTypeAnnotation (ANY_TYPE);
      }
      else
      {
        final XdmSchemaType aType = _getSchemaType (aStartType != null ? aStartType : aEndType,
                                                    aEndType != null ? aEndType : aStartType);
        m_aCurrent.setSchemaType (aType);
        _dropElementOnlyWhitespace (m_aCurrent, aType);
        if (aType.getAtomicBase () != null && !m_aCurrent.isNilled ())
          m_aCurrent.setIDKind (_getSchemaIDKind (aType, m_aCurrent.getStringValue ()));
      }
    }
    m_aCurrent = m_aCurrent.getParent ();
    m_aSystemIds.pop ();
  }

  @Override
  public void characters (final char [] aChars, final int nStart, final int nLength)
  {
    m_aText.append (aChars, nStart, nLength);
  }

  @Override
  public void ignorableWhitespace (final char [] aChars, final int nStart, final int nLength)
  {
    // A schema validator, and the parser for an element declared with element
    // content in the DTD, report whitespace in element-only content this way.
    // XDM 3.1 section 6.7.3 drops it: such a text node has empty content and
    // is discarded
  }

  @Override
  public void unparsedEntityDecl (final String sName, final String sPublicId, final String sSystemId, final String sNotationName)
  {
    if (m_aDocument != null && sName != null && sSystemId != null)
      m_aDocument.addUnparsedEntity (sName, sSystemId, sPublicId);
  }

  @Override
  public void processingInstruction (final String sTarget, final String sData)
  {
    _flushText ();
    final XdmNode aPI = XdmNode.createProcessingInstruction (sTarget, sData == null ? "" : sData);
    if (m_aLocator != null)
      aPI.setLocation (m_aLocator.getLineNumber (), m_aLocator.getColumnNumber ());
    _applyEntityBase (aPI);
    m_aCurrent.appendChild (aPI);
  }

  @Override
  public void startDTD (final String sName, final String sPublicId, final String sSystemId) throws SAXException
  {
    m_bInDTD = true;
  }

  @Override
  public void endDTD ()
  {
    m_bInDTD = false;
  }

  @Override
  public void comment (final char [] aChars, final int nStart, final int nLength)
  {
    if (m_bInDTD)
      return;
    _flushText ();
    final XdmNode aComment = XdmNode.createComment (new String (aChars, nStart, nLength));
    if (m_aLocator != null)
      aComment.setLocation (m_aLocator.getLineNumber (), m_aLocator.getColumnNumber ());
    m_aCurrent.appendChild (aComment);
  }
}
