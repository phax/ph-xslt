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

import java.io.StringReader;
import java.util.List;
import java.util.function.IntFunction;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.apache.xerces.impl.xs.SchemaGrammar;
import org.apache.xerces.impl.xs.XSModelImpl;
import org.apache.xerces.jaxp.validation.XMLSchemaFactory;
import org.apache.xerces.jaxp.validation.XSGrammarPoolContainer;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xs.AttributePSVI;
import org.apache.xerces.xs.PSVIProvider;
import org.apache.xerces.xs.XSTypeDefinition;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.TypeInfo;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.schema.IXdmSchemaProvider;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xpath.xdm.schema.XdmSchemaException;
import com.helger.xpath.xdm.schema.XdmSchemaSource;

/**
 * The Apache Xerces-J schema processor. Only loaded by
 * {@link com.helger.xpath.xdm.schema.XdmSchemaSupport} when Xerces-J is on
 * the class path. All Xerces-J classes are instantiated directly, so the JAXP
 * lookup (and with it every other user of JAXP in the application) is not
 * affected.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class XercesSchemaProvider implements IXdmSchemaProvider
{
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

  private final SimpleReadWriteLock m_aRWLock = new SimpleReadWriteLock ();
  private IXdmSchemaSet m_aBuiltin;

  public XercesSchemaProvider ()
  {}

  @NonNull
  private static IXdmSchemaSet _compile (@NonNull final List <XdmSchemaSource> aSources) throws XdmSchemaException
  {
    final XMLSchemaFactory aFactory = new XMLSchemaFactory ();
    aFactory.setErrorHandler (THROWING_ERROR_HANDLER);
    final ICommonsList <Source> aJaxpSources = new CommonsArrayList <> ();
    for (final XdmSchemaSource aSource : aSources)
    {
      final StreamSource aSS = aSource.text () != null ? new StreamSource (new StringReader (aSource.text ()))
                                                       : new StreamSource ();
      aSS.setSystemId (aSource.systemId ());
      aJaxpSources.add (aSS);
    }
    final Schema aSchema;
    try
    {
      aSchema = aJaxpSources.isEmpty () ? aFactory.newSchema (new Source [] { new StreamSource (new StringReader ("<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>")) })
                                        : aFactory.newSchema (aJaxpSources.toArray (new Source [0]));
    }
    catch (final SAXException ex)
    {
      throw new XdmSchemaException ("Invalid schema: " + ex.getMessage (), ex);
    }
    // The schema component model of the very grammars the validator uses
    final Grammar [] aGrammars = ((XSGrammarPoolContainer) aSchema).getGrammarPool ()
                                                                   .retrieveInitialGrammarSet (XMLGrammarDescription.XML_SCHEMA);
    final SchemaGrammar [] aSchemaGrammars = new SchemaGrammar [aGrammars.length];
    for (int i = 0; i < aGrammars.length; ++i)
      aSchemaGrammars[i] = (SchemaGrammar) aGrammars[i];
    return new XercesSchemaSet (aSchema, new XSModelImpl (aSchemaGrammars));
  }

  @NonNull
  public IXdmSchemaSet compile (@NonNull final List <XdmSchemaSource> aSources) throws XdmSchemaException
  {
    if (!aSources.isEmpty ())
      return _compile (aSources);
    IXdmSchemaSet ret = m_aRWLock.readLockedGet ( () -> m_aBuiltin);
    if (ret == null)
    {
      final IXdmSchemaSet aNew = _compile (aSources);
      ret = m_aRWLock.writeLockedGet ( () -> {
        if (m_aBuiltin == null)
          m_aBuiltin = aNew;
        return m_aBuiltin;
      });
    }
    return ret;
  }

  @Nullable
  public IXdmTypeDefinition getDefinition (@NonNull final TypeInfo aTypeInfo)
  {
    return aTypeInfo instanceof final XSTypeDefinition aType ? new XercesTypeDefinition (aType) : null;
  }

  @Nullable
  public IntFunction <TypeInfo> getAttributeTypeDefinitionAccess (@NonNull final Object aValidatorHandler)
  {
    if (!(aValidatorHandler instanceof final PSVIProvider aPSVI))
      return null;
    return nIndex -> {
      final AttributePSVI aAttr = aPSVI.getAttributePSVI (nIndex);
      return aAttr != null && aAttr.getTypeDefinition () instanceof final TypeInfo aTypeInfo ? aTypeInfo : null;
    };
  }
}
