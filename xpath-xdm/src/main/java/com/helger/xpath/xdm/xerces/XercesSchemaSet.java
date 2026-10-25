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

import java.util.ArrayDeque;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.validation.Schema;
import javax.xml.validation.ValidatorHandler;

import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSTypeDefinition;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmTreeBuilder;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.IXdmAttributeDeclaration;
import com.helger.xpath.xdm.schema.IXdmElementDeclaration;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xpath.xdm.schema.XdmSaxEmitter;

/**
 * {@link IXdmSchemaSet} over a schema compiled by Xerces-J: the JAXP schema
 * for validation and the schema component model of the same grammars, so that
 * the types a validator reports are the components of the model.
 *
 * @author Philip Helger
 */
@Immutable
final class XercesSchemaSet implements IXdmSchemaSet
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
  private final XSModel m_aModel;

  XercesSchemaSet (@NonNull final Schema aSchema, @NonNull final XSModel aModel)
  {
    m_aSchema = aSchema;
    m_aModel = aModel;
  }

  @Nullable
  private static String _ns (@NonNull final XdmQName aName)
  {
    return aName.namespaceUri ().isEmpty () ? null : aName.namespaceUri ();
  }

  @Nullable
  public IXdmTypeDefinition getTypeDefinition (@NonNull final XdmQName aName)
  {
    return XercesTypeDefinition.wrap (m_aModel.getTypeDefinition (aName.localName (), _ns (aName)));
  }

  @Nullable
  public IXdmElementDeclaration getElementDeclaration (@NonNull final XdmQName aName)
  {
    final XSElementDeclaration aDecl = m_aModel.getElementDeclaration (aName.localName (), _ns (aName));
    return aDecl == null ? null : new XercesElementDeclaration (m_aModel, aDecl);
  }

  @Nullable
  public IXdmAttributeDeclaration getAttributeDeclaration (@NonNull final XdmQName aName)
  {
    final XSAttributeDeclaration aDecl = m_aModel.getAttributeDeclaration (aName.localName (), _ns (aName));
    return aDecl == null ? null : new XercesAttributeDeclaration (aDecl);
  }

  @NonNull
  public Schema getJaxpSchema ()
  {
    return m_aSchema;
  }

  @NonNull
  private static ICommonsList <XdmNode> _getElementsInOrder (@NonNull final XdmNode aRoot)
  {
    final ICommonsList <XdmNode> ret = new CommonsArrayList <> ();
    final ArrayDeque <XdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final XdmNode aCur = aStack.pop ();
      if (aCur.isElement ())
        ret.add (aCur);
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return ret;
  }

  /**
   * A default attribute in a namespace needs a prefix bound to it on the
   * element: an existing one, or a new declaration (namespace fixup as in XSD
   * 1.1 section 3.4.5.1).
   */
  @NonNull
  private static XdmQName _fixupAttributeName (@NonNull final XdmNode aElement, @NonNull final XdmQName aName)
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

  /**
   * Copy the outcome of the validation from the validated copy to the
   * original element. The copy has the same elements in the same order; it
   * may lack whitespace text nodes of element-only content and may have
   * default attributes and default element content.
   *
   * @return <code>true</code> if the structure of the original changed.
   */
  private static boolean _transferAnnotations (@NonNull final XdmNode aOriginal, @NonNull final XdmNode aTyped)
  {
    final ICommonsList <XdmNode> aOrigElements = _getElementsInOrder (aOriginal);
    final ICommonsList <XdmNode> aTypedElements = _getElementsInOrder (aTyped);
    if (aOrigElements.size () != aTypedElements.size ())
      throw new IllegalStateException ("Validated copy has a different structure");
    boolean bChanged = false;
    for (int i = 0; i < aOrigElements.size (); ++i)
    {
      final XdmNode aOrig = aOrigElements.get (i);
      final XdmNode aCopy = aTypedElements.get (i);
      aOrig.setSchemaType (aCopy.getSchemaType ());
      aOrig.setTypeAnnotation (aCopy.getSchemaType () == null ? aCopy.getTypeAnnotation () : null);
      aOrig.setNilled (aCopy.isNilled ());
      aOrig.setIDKind (aCopy.getIDKind ());
      for (final XdmNode aCopyAttr : aCopy.getAllAttributes ())
      {
        final XdmQName aName = aCopyAttr.getName ();
        XdmNode aOrigAttr = aOrig.getAttribute (aName.namespaceUri (), aName.localName ());
        if (aOrigAttr == null)
        {
          // A default attribute added by the schema
          aOrigAttr = XdmNode.createAttribute (_fixupAttributeName (aOrig, aName), aCopyAttr.getValue ());
          aOrig.addAttribute (aOrigAttr);
          bChanged = true;
        }
        aOrigAttr.setSchemaType (aCopyAttr.getSchemaType ());
        aOrigAttr.setIDKind (aCopyAttr.getIDKind ());
      }
      if (aOrig.getChildCount () == 0 && aCopy.getChildCount () == 1 && aCopy.getChildAtIndex (0).isText ())
      {
        // Default element content added by the schema
        aOrig.appendChild (XdmNode.createText (aCopy.getChildAtIndex (0).getValue ()));
        bChanged = true;
      }
      else
        if (aOrig.getChildCount () != aCopy.getChildCount ())
        {
          // Whitespace text nodes of element-only content are not part of
          // the validated tree
          final ICommonsList <XdmNode> aRemove = new CommonsArrayList <> ();
          for (final XdmNode aChild : aOrig.getAllChildren ())
            if (aChild.getKind () == ENodeKind.TEXT && aChild.getValue ().isBlank ())
              aRemove.add (aChild);
          for (final XdmNode aChild : aRemove)
            aOrig.removeChild (aChild);
          bChanged |= aRemove.isNotEmpty ();
        }
    }
    return bChanged;
  }

  public void validateElement (@NonNull final XdmNode aElement,
                               @Nullable final IXdmTypeDefinition aType,
                               final boolean bDocumentLevel) throws XdmValidationException
  {
    try
    {
      final ValidatorHandler aValidator = m_aSchema.newValidatorHandler ();
      aValidator.setErrorHandler (THROWING_ERROR_HANDLER);
      aValidator.setFeature (FEATURE_ID_IDREF_CHECKING, bDocumentLevel);
      if (aType != null)
      {
        final XSTypeDefinition aXSType = ((XercesTypeDefinition) aType).getXSType ();
        aValidator.setProperty (PROPERTY_ROOT_TYPE,
                                new QName (aXSType.getNamespace () == null ? "" : aXSType.getNamespace (),
                                           aXSType.getName ()));
      }
      final XdmTreeBuilder aBuilder = new XdmTreeBuilder ();
      aBuilder.setTypeInfoProvider (aValidator.getTypeInfoProvider ());
      aBuilder.setValidator (aValidator);
      aValidator.setContentHandler (aBuilder);
      XdmSaxEmitter.emitElement (aElement, aValidator, aBuilder);
      XdmNode aTyped = null;
      for (final XdmNode aChild : aBuilder.getDocument ().getAllChildren ())
        if (aChild.isElement ())
          aTyped = aChild;
      if (aTyped == null)
        throw new IllegalStateException ("Validation produced no element");
      if (_transferAnnotations (aElement, aTyped))
        aElement.assignDocumentOrder ();
    }
    catch (final SAXException ex)
    {
      final String sMsg = ex.getMessage () == null ? ex.toString () : ex.getMessage ();
      throw new XdmValidationException ("Element " + aElement.getName ().getEQName () + " is not valid: " + sMsg,
                                        ex,
                                        sMsg.startsWith ("cvc-id."));
    }
  }
}
