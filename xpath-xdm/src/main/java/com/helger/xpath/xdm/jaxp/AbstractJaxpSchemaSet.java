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

import java.util.ArrayDeque;
import java.util.Map;
import java.util.function.IntFunction;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.validation.Schema;
import javax.xml.validation.ValidatorHandler;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.xdm.EIDKind;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmTreeBuilder;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xpath.xdm.schema.XdmSaxEmitter;

/**
 * Base class of the schema sets of JAXP schema processors. A node is validated by writing it as SAX
 * events through a {@link ValidatorHandler} into a new tree, which receives the types the validator
 * reports, and then copying the outcome onto the original nodes. Subclasses map the types a
 * validator reports to their type definitions.
 *
 * @author Philip Helger
 */
@Immutable
public abstract class AbstractJaxpSchemaSet implements IXdmSchemaSet
{
  private static final String FEATURE_ID_IDREF_CHECKING = "http://apache.org/xml/features/validation/id-idref-checking";
  private static final String PROPERTY_ROOT_TYPE = "http://apache.org/xml/properties/validation/schema/root-type-definition";

  private static final ErrorHandler THROWING_ERROR_HANDLER = new ErrorHandler ()
  {
    public void warning (final SAXParseException ex)
    {
      // ignore
    }

    public void error (final SAXParseException ex) throws SAXException
    {
      throw ex;
    }

    public void fatalError (final SAXParseException ex) throws SAXException
    {
      throw ex;
    }
  };

  private final Schema m_aSchema;

  /**
   * @param aSchema
   *        The compiled JAXP schema the validators are created from. May not be <code>null</code>.
   */
  protected AbstractJaxpSchemaSet (@NonNull final Schema aSchema)
  {
    ValueEnforcer.notNull (aSchema, "Schema");
    m_aSchema = aSchema;
  }

  /**
   * @param aTypeInfo
   *        A type reported by a validator of this schema set.
   * @return The type definition of it. Never <code>null</code>.
   */
  @NonNull
  protected abstract IXdmTypeDefinition getDefinition (@NonNull TypeInfo aTypeInfo);

  /**
   * @param aValidator
   *        A validator of this schema set.
   * @return A function from the attribute index of the current start tag to the declared type of
   *         the attribute (for a union type the union, not the member that matched), or
   *         <code>null</code> if the validator does not tell. This implementation returns
   *         <code>null</code>.
   */
  @Nullable
  protected IntFunction <TypeInfo> getDeclaredAttributeTypes (@NonNull final ValidatorHandler aValidator)
  {
    return null;
  }

  @NonNull
  private static ICommonsList <IXdmNode> _getElementsInOrder (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if (aCur.isElement ())
        ret.add (aCur);
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return ret;
  }

  /**
   * A default attribute in a namespace needs a prefix bound to it on the element: an existing one,
   * or a new declaration (namespace fixup as in XSD 1.1 section 3.4.5.1).
   */
  @NonNull
  private static XdmQName _fixupAttributeName (@NonNull final IXdmNode aElement, @NonNull final XdmQName aName)
  {
    final String sUri = aName.namespaceUri ();
    if (sUri.isEmpty ())
      return aName;
    final ICommonsOrderedMap <String, String> aInScope = aElement.getInScopeNamespaces ();
    for (final Map.Entry <String, String> aEntry : aInScope.entrySet ())
      if (!aEntry.getKey ().isEmpty () && sUri.equals (aEntry.getValue ()))
        return new XdmQName (sUri, aName.localName (), aEntry.getKey ());
    String sPrefix = aName.prefix ();
    if (sPrefix == null || sPrefix.isEmpty () || aInScope.containsKey (sPrefix))
    {
      int n = 0;
      do
      {
        sPrefix = "ns" + n++;
      } while (aInScope.containsKey (sPrefix));
    }
    aElement.declareNamespace (sPrefix, sUri);
    return new XdmQName (sUri, aName.localName (), sPrefix);
  }

  private static boolean _hasTextOrElementChild (@NonNull final IXdmNode aElement)
  {
    for (final IXdmNode aChild : aElement.getAllChildren ())
      if (aChild.isText () || aChild.isElement ())
        return true;
    return false;
  }

  /**
   * Copy the outcome of the validation from the validated copy to the original element. The copy
   * has the same elements in the same order; it may lack whitespace text nodes of element-only
   * content and may have default attributes and default element content.
   *
   * @param bDocument
   *        <code>true</code> for a document read by the parser: a default attribute keeps the name
   *        the validator gave it, and an ID kind of the DTD stays unless the schema gives one (with
   *        the attribute value then normalised), as if the document had been validated while it was
   *        parsed. <code>false</code> for a node validated later: the ID kinds are the ones of the
   *        schema.
   * @return <code>true</code> if the structure of the original changed.
   */
  private static boolean _transferAnnotations (@NonNull final IXdmNode aOriginal,
                                               @NonNull final IXdmNode aTyped,
                                               final boolean bDocument)
  {
    final ICommonsList <IXdmNode> aOrigElements = _getElementsInOrder (aOriginal);
    final ICommonsList <IXdmNode> aTypedElements = _getElementsInOrder (aTyped);
    if (aOrigElements.size () != aTypedElements.size ())
      throw new IllegalStateException ("Validated copy has a different structure");
    boolean bChanged = false;
    for (int i = 0; i < aOrigElements.size (); ++i)
    {
      final IXdmNode aOrig = aOrigElements.get (i);
      final IXdmNode aCopy = aTypedElements.get (i);
      aOrig.setSchemaType (aCopy.getSchemaType ());
      aOrig.setTypeAnnotation (aCopy.getSchemaType () == null ? aCopy.getTypeAnnotation () : null);
      aOrig.setNilled (aCopy.isNilled ());
      aOrig.setIDKind (aCopy.getIDKind ());
      for (final IXdmNode aCopyAttr : aCopy.getAllAttributes ())
      {
        final XdmQName aName = aCopyAttr.getName ();
        IXdmNode aOrigAttr = aOrig.getAttribute (aName.namespaceUri (), aName.localName ());
        if (aOrigAttr == null)
        {
          // A default attribute added by the schema
          if (bDocument)
          {
            aOrigAttr = IXdmNode.createAttribute (aName, aCopyAttr.getValue ());
            aOrigAttr.setLocation (aOrig.getLine (), aOrig.getColumn ());
          }
          else
            aOrigAttr = IXdmNode.createAttribute (_fixupAttributeName (aOrig, aName), aCopyAttr.getValue ());
          aOrig.addAttribute (aOrigAttr);
          bChanged = true;
        }
        aOrigAttr.setSchemaType (aCopyAttr.getSchemaType ());
        if (!bDocument)
          aOrigAttr.setIDKind (aCopyAttr.getIDKind ());
        else
          if (aCopyAttr.getIDKind () != EIDKind.NONE)
          {
            aOrigAttr.setIDKind (aCopyAttr.getIDKind ());
            aOrigAttr.setValue (aCopyAttr.getValue ());
          }
      }
      if (aOrig.getChildCount () == 0 && aCopy.getChildCount () == 1 && aCopy.getChildAtIndex (0).isText ())
      {
        // Default element content added by the schema
        aOrig.appendChild (IXdmNode.createText (aCopy.getChildAtIndex (0).getValue ()));
        bChanged = true;
      }
      else
        if (aOrig.getChildCount () != aCopy.getChildCount ())
        {
          // Whitespace text nodes of element-only content are not part of
          // the validated tree
          final ICommonsList <IXdmNode> aRemove = new CommonsArrayList <> ();
          for (final IXdmNode aChild : aOrig.getAllChildren ())
            if (aChild.getKind () == ENodeKind.TEXT && aChild.getValue ().isBlank ())
              aRemove.add (aChild);
          for (final IXdmNode aChild : aRemove)
            aOrig.removeChild (aChild);
          bChanged |= aRemove.isNotEmpty ();
          if (bDocument && !_hasTextOrElementChild (aOrig) && aCopy.getChildCount () > aOrig.getChildCount ())
          {
            // Default element content of an element with only comments or
            // processing instructions: the validator reports it at the end
            final IXdmNode aLast = aCopy.getChildAtIndex (aCopy.getChildCount () - 1);
            if (aLast.isText ())
            {
              aOrig.appendChild (IXdmNode.createText (aLast.getValue ()));
              bChanged = true;
            }
          }
        }
    }
    return bChanged;
  }

  private static void _setFeatureIfSupported (@NonNull final ValidatorHandler aValidator,
                                              @NonNull final String sName,
                                              final boolean bValue)
  {
    try
    {
      aValidator.setFeature (sName, bValue);
    }
    catch (final SAXNotRecognizedException | SAXNotSupportedException ex)
    {
      // Not every validator knows the Xerces features
    }
  }

  private static void _setPropertyIfSupported (@NonNull final ValidatorHandler aValidator,
                                               @NonNull final String sName,
                                               @NonNull final Object aValue)
  {
    try
    {
      aValidator.setProperty (sName, aValue);
    }
    catch (final SAXNotRecognizedException | SAXNotSupportedException ex)
    {
      // A validator other than the JDK's (e.g. Apache Xerces) does not know
      // the JAXP 1.5 properties; whether it follows schema location hints is
      // then up to its schema (Xerces ignores them for a schema compiled from
      // sources)
    }
  }

  /**
   * Validate an element by writing it through a validator into a new tree.
   *
   * @return The validated copy of the element, with the type annotations.
   */
  @NonNull
  private IXdmNode _validateCopy (@NonNull final IXdmNode aElement,
                                 @Nullable final IXdmTypeDefinition aType,
                                 final boolean bDocumentLevel,
                                 final boolean bParsedEntitiesAsUnparsed) throws SAXException
  {
    final ValidatorHandler aValidator = m_aSchema.newValidatorHandler ();
    aValidator.setErrorHandler (THROWING_ERROR_HANDLER);
    _setFeatureIfSupported (aValidator, FEATURE_ID_IDREF_CHECKING, bDocumentLevel);
    // Schema location hints in the instance are never followed
    _setPropertyIfSupported (aValidator, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    _setPropertyIfSupported (aValidator, XMLConstants.ACCESS_EXTERNAL_DTD, "");
    if (aType != null)
    {
      final XdmQName aTypeName = aType.getName ();
      if (aTypeName == null)
        throw new IllegalArgumentException ("Cannot validate against an anonymous type");
      aValidator.setProperty (PROPERTY_ROOT_TYPE, new QName (aTypeName.namespaceUri (), aTypeName.localName ()));
    }
    final XdmTreeBuilder aBuilder = new XdmTreeBuilder ();
    aValidator.setContentHandler (new JaxpTypeAnnotator (aBuilder,
                                                         aValidator.getTypeInfoProvider (),
                                                         getDeclaredAttributeTypes (aValidator),
                                                         this::getDefinition));
    XdmSaxEmitter.emitElement (aElement, aValidator, aBuilder, bParsedEntitiesAsUnparsed);
    IXdmNode aTyped = null;
    for (final IXdmNode aChild : aBuilder.getDocument ().getAllChildren ())
      if (aChild.isElement ())
        aTyped = aChild;
    if (aTyped == null)
      throw new IllegalStateException ("Validation produced no element");
    return aTyped;
  }

  @NonNull
  private static String _getMessage (@NonNull final SAXException ex)
  {
    return ex.getMessage () == null ? ex.toString () : ex.getMessage ();
  }

  public void validateElement (@NonNull final IXdmNode aElement,
                               @Nullable final IXdmTypeDefinition aType,
                               final boolean bDocumentLevel) throws XdmValidationException
  {
    try
    {
      // Leniently, an xs:ENTITY value may also name a parsed entity of the
      // DTD (as go-xslt does)
      final IXdmNode aTyped = _validateCopy (aElement, aType, bDocumentLevel, true);
      if (_transferAnnotations (aElement, aTyped, false))
        aElement.assignDocumentOrder ();
    }
    catch (final SAXException ex)
    {
      final String sMsg = _getMessage (ex);
      throw new XdmValidationException ("Element " + aElement.getName ().getEQName () + " is not valid: " + sMsg,
                                        ex,
                                        sMsg.startsWith ("cvc-id."));
    }
  }

  public void validateDocument (@NonNull final IXdmNode aDocument) throws XdmValidationException
  {
    ValueEnforcer.isTrue (aDocument.isDocument (), "Not a document node");
    IXdmNode aRoot = null;
    for (final IXdmNode aChild : aDocument.getAllChildren ())
      if (aChild.isElement ())
        aRoot = aChild;
    if (aRoot == null)
      throw new IllegalArgumentException ("The document has no document element");
    try
    {
      // Only unparsed entities, as when validating while parsing
      final IXdmNode aTyped = _validateCopy (aRoot, null, true, false);
      if (_transferAnnotations (aRoot, aTyped, true))
        aDocument.assignDocumentOrder ();
    }
    catch (final SAXException ex)
    {
      final String sMsg = _getMessage (ex);
      throw new XdmValidationException ("Document is not valid: " + sMsg, ex, sMsg.startsWith ("cvc-id."));
    }
  }
}
