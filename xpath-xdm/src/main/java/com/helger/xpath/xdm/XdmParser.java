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

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.ValidatorHandler;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;

/**
 * Reads XML with the JDK parser into an {@link XdmNode} tree. The defaults are
 * safe: secure processing is on and external entities and the external DTD
 * subset are not loaded.
 * <p>
 * With a {@link #setSchema(Schema) schema} the document is validated (strict)
 * while the tree is built and the nodes carry the schema information (see
 * {@link XdmTreeBuilder}).
 *
 * @author Philip Helger
 */
@NotThreadSafe
public class XdmParser
{
  public static final boolean DEFAULT_LOAD_EXTERNAL_ENTITIES = false;
  /**
   * The maximum element nesting depth. JDK 25 enforces 100 under secure
   * processing, JDK 21 enforces none, so the limit is always set explicitly.
   */
  public static final int DEFAULT_MAX_ELEMENT_DEPTH = 10_000;

  /**
   * Entity limits of the JDK parser, set explicitly because the secure
   * processing defaults differ between JDK versions (JDK 25 allows only 2,500
   * entity expansions, JDK 21 64,000). The values are the JDK 21 defaults.
   * They refuse the usual expansion attacks (billion laughs, quadratic
   * blow-up, parameter entities) on every supported JDK.
   */
  private static final String [] [] ENTITY_LIMITS = { { "entityExpansionLimit", "64000" },
                                                      { "totalEntitySizeLimit", "50000000" },
                                                      { "maxGeneralEntitySizeLimit", "0" },
                                                      { "maxParameterEntitySizeLimit", "1000000" },
                                                      { "entityReplacementLimit", "3000000" } };

  /**
   * Marks an error reported by the validator, as opposed to a parse error.
   */
  private static final class ValidationError extends SAXException
  {
    ValidationError (@NonNull final SAXParseException aCause)
    {
      super (aCause.getMessage (), aCause);
    }
  }

  private static final ErrorHandler VALIDATION_ERROR_HANDLER = new ErrorHandler ()
  {
    public void warning (final SAXParseException ex)
    {
      // ignore
    }

    public void error (final SAXParseException ex) throws SAXException
    {
      throw new ValidationError (ex);
    }

    public void fatalError (final SAXParseException ex) throws SAXException
    {
      throw new ValidationError (ex);
    }
  };

  private boolean m_bLoadExternalEntities = DEFAULT_LOAD_EXTERNAL_ENTITIES;
  private int m_nMaxElementDepth = DEFAULT_MAX_ELEMENT_DEPTH;
  private Schema m_aSchema;

  public final boolean isLoadExternalEntities ()
  {
    return m_bLoadExternalEntities;
  }

  /**
   * Allow external entities and the external DTD subset to be read. Needed for
   * default attributes and entities declared outside the document, but opens
   * the document to local file and network reads, so enable it for trusted
   * input only.
   *
   * @param bLoadExternalEntities
   *        <code>true</code> to load them.
   * @return this for chaining
   */
  @NonNull
  public final XdmParser setLoadExternalEntities (final boolean bLoadExternalEntities)
  {
    m_bLoadExternalEntities = bLoadExternalEntities;
    return this;
  }

  @Nonnegative
  public final int getMaxElementDepth ()
  {
    return m_nMaxElementDepth;
  }

  /**
   * @param nMaxElementDepth
   *        The maximum element nesting depth; 0 means unlimited, which is
   *        unsafe for untrusted input.
   * @return this for chaining
   */
  @NonNull
  public final XdmParser setMaxElementDepth (@Nonnegative final int nMaxElementDepth)
  {
    ValueEnforcer.isGE0 (nMaxElementDepth, "MaxElementDepth");
    m_nMaxElementDepth = nMaxElementDepth;
    return this;
  }

  @Nullable
  public final Schema getSchema ()
  {
    return m_aSchema;
  }

  /**
   * Validate documents against an XML schema while they are read. Validation
   * is strict: the document element must be declared, and an invalid document
   * is rejected with an {@link XdmValidationException}. The tree is built from
   * the validation result: elements and attributes carry their
   * {@link XdmSchemaType}, <code>xsi:nil</code> sets the nilled property,
   * schema IDs and IDREFs are recognised, and whitespace in element-only
   * content is dropped. Schema location hints in the document are only
   * followed if {@link #setLoadExternalEntities(boolean) external entities}
   * are allowed.
   *
   * @param aSchema
   *        The compiled schema (thread-safe, may be shared);
   *        <code>null</code> to read without validation.
   * @return this for chaining
   */
  @NonNull
  public final XdmParser setSchema (@Nullable final Schema aSchema)
  {
    m_aSchema = aSchema;
    return this;
  }

  /**
   * @param aSource
   *        The source to read. May not be <code>null</code>.
   * @return The document node.
   * @throws XdmValidationException
   *         if a schema is set and the document is not valid against it
   * @throws XdmParseException
   *         if the document cannot be read
   */
  @NonNull
  public XdmNode parse (@NonNull final InputSource aSource) throws XdmParseException
  {
    ValueEnforcer.notNull (aSource, "Source");
    try
    {
      final SAXParserFactory aFactory = SAXParserFactory.newInstance ();
      aFactory.setNamespaceAware (true);
      aFactory.setValidating (false);
      aFactory.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
      aFactory.setFeature ("http://xml.org/sax/features/external-general-entities", m_bLoadExternalEntities);
      aFactory.setFeature ("http://xml.org/sax/features/external-parameter-entities", m_bLoadExternalEntities);
      aFactory.setFeature ("http://apache.org/xml/features/nonvalidating/load-external-dtd",
                           m_bLoadExternalEntities);
      final XMLReader aReader = aFactory.newSAXParser ().getXMLReader ();
      final XdmTreeBuilder aBuilder = new XdmTreeBuilder ();
      if (m_aSchema != null)
      {
        // XMLReader -> ValidatorHandler -> builder; lexical and DTD events go
        // to the builder directly
        final ValidatorHandler aValidator = m_aSchema.newValidatorHandler ();
        aValidator.setErrorHandler (VALIDATION_ERROR_HANDLER);
        if (!m_bLoadExternalEntities)
        {
          aValidator.setProperty (XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
          aValidator.setProperty (XMLConstants.ACCESS_EXTERNAL_DTD, "");
        }
        aValidator.setContentHandler (aBuilder);
        aBuilder.setTypeInfoProvider (aValidator.getTypeInfoProvider ());
        aReader.setContentHandler (aValidator);
      }
      else
        aReader.setContentHandler (aBuilder);
      aReader.setErrorHandler (aBuilder);
      aReader.setDTDHandler (aBuilder);
      aReader.setEntityResolver (aBuilder);
      aReader.setProperty ("http://xml.org/sax/properties/lexical-handler", aBuilder);
      aReader.setProperty ("http://xml.org/sax/properties/declaration-handler", aBuilder);
      for (final String [] aLimit : ENTITY_LIMITS)
        aReader.setProperty ("http://www.oracle.com/xml/jaxp/properties/" + aLimit[0], aLimit[1]);
      aReader.setProperty ("http://www.oracle.com/xml/jaxp/properties/maxElementDepth",
                           Integer.toString (m_nMaxElementDepth));
      aReader.parse (aSource);
      return aBuilder.getDocument ();
    }
    catch (final ValidationError ex)
    {
      throw new XdmValidationException ("Document is not valid: " + ex.getMessage (), ex.getCause ());
    }
    catch (final ParserConfigurationException | SAXException | IOException ex)
    {
      throw new XdmParseException ("Failed to parse XML: " + ex.getMessage (), ex);
    }
  }

  @NonNull
  public XdmNode parse (@NonNull final String sXml) throws XdmParseException
  {
    ValueEnforcer.notNull (sXml, "Xml");
    return parse (new InputSource (new StringReader (sXml)));
  }

  @NonNull
  public XdmNode parse (@NonNull final Path aPath) throws XdmParseException
  {
    ValueEnforcer.notNull (aPath, "Path");
    return parse (new InputSource (aPath.toUri ().toString ()));
  }
}
