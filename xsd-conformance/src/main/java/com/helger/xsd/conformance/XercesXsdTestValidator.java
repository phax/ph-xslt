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
package com.helger.xsd.conformance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;

import org.apache.xerces.impl.Version;
import org.apache.xerces.jaxp.validation.XMLSchemaFactory;
import org.jspecify.annotations.NonNull;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;

import com.helger.annotation.concurrent.NotThreadSafe;

/**
 * The Apache Xerces-J 2.12.2 baseline: an {@link IXsdTestValidator} over
 * Xerces-J's own JAXP schema factory, instantiated directly (never through a
 * JAXP lookup, so the JDK's built-in copy of Xerces is never used), with the
 * factory defaults (full schema checking on, <code>honour-all-schemaLocations</code>
 * off) plus secure processing. Secure processing installs Xerces-J's own
 * security manager, whose limit of 3,000 content-model nodes turns the
 * expansion of very large <code>maxOccurs</code> values into an error;
 * without it a handful of suite instances (<code>particlesZ036_b1.i</code>,
 * <code>particlesZ036_c.v</code>, ...) make Xerces-J expand the content model
 * until the heap is exhausted, which would make the whole run depend on the
 * heap size. Xerces-J 2.12.2 implements XSD 1.0 only; every XSD 1.1 case is
 * reported as unsupported.
 * <p>
 * Every reference is resolved by an {@link XsdLocalOnlyResourceResolver}, so
 * the run stays offline. Warnings (for example an <code>xs:import</code> whose
 * location cannot be read) are ignored; the first error decides.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XercesXsdTestValidator implements IXsdTestValidator
{
  private static final ErrorHandler THROWING_ERROR_HANDLER = new ErrorHandler ()
  {
    public void warning (final SAXParseException ex)
    {
      // Warnings never change the verdict
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

  private final XsdLocalOnlyResourceResolver m_aResolver = new XsdLocalOnlyResourceResolver ();
  private final XMLSchemaFactory m_aFactory;

  /** A schema compiled by Xerces-J. */
  private final class XercesSchema implements IXsdTestSchema
  {
    private final Schema m_aSchema;
    private final boolean m_bHintsOnly;

    XercesSchema (@NonNull final Schema aSchema, final boolean bHintsOnly)
    {
      m_aSchema = aSchema;
      m_bHintsOnly = bHintsOnly;
    }

    @NonNull
    public XsdVerdict validate (@NonNull final Path aInstance)
    {
      try
      {
        // The schema of the empty set caches the grammars its hints load: take a fresh one per instance
        final Schema aSchema = m_bHintsOnly ? m_aFactory.newSchema () : m_aSchema;
        final Validator aValidator = aSchema.newValidator ();
        aValidator.setResourceResolver (m_aResolver);
        aValidator.setErrorHandler (THROWING_ERROR_HANDLER);
        aValidator.validate (new StreamSource (aInstance.toFile ()));
        return XsdVerdict.valid ();
      }
      catch (final SAXException | IOException ex)
      {
        return XsdVerdict.invalid (ex.getMessage ());
      }
    }
  }

  /** Constructor. */
  public XercesXsdTestValidator ()
  {
    m_aFactory = new XMLSchemaFactory ();
    try
    {
      m_aFactory.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
    }
    catch (final SAXNotRecognizedException | SAXNotSupportedException ex)
    {
      throw new IllegalStateException ("Xerces-J does not support secure processing", ex);
    }
    m_aFactory.setResourceResolver (m_aResolver);
    m_aFactory.setErrorHandler (THROWING_ERROR_HANDLER);
  }

  @NonNull
  public String getDisplayName ()
  {
    return Version.getVersion ();
  }

  public boolean isVersionSupported (@NonNull final EXsdVersion eVersion)
  {
    return eVersion == EXsdVersion.XSD_10;
  }

  @NonNull
  public XsdCompileResult compile (@NonNull final List <Path> aSchemaDocuments, @NonNull final EXsdVersion eVersion)
  {
    if (!isVersionSupported (eVersion))
      return XsdCompileResult.failed (XsdVerdict.unsupported ("XSD " +
                                                              eVersion.getID () +
                                                              " is not implemented by " +
                                                              getDisplayName ()));
    try
    {
      if (aSchemaDocuments.isEmpty ())
        return XsdCompileResult.valid (new XercesSchema (m_aFactory.newSchema (), true));

      final Source [] aSources = new Source [aSchemaDocuments.size ()];
      for (int i = 0; i < aSources.length; ++i)
        aSources[i] = new StreamSource (aSchemaDocuments.get (i).toFile ());
      return XsdCompileResult.valid (new XercesSchema (m_aFactory.newSchema (aSources), false));
    }
    catch (final SAXException ex)
    {
      return XsdCompileResult.failed (XsdVerdict.invalid (ex.getMessage ()));
    }
  }

  /** @return The resource resolver, to see which references were refused. */
  @NonNull
  public XsdLocalOnlyResourceResolver getResourceResolver ()
  {
    return m_aResolver;
  }
}
