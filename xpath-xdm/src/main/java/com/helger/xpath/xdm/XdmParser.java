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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;

/**
 * Reads XML with the JDK parser into an {@link XdmNode} tree, also when another
 * JAXP implementation is on the class path. The defaults are
 * safe: secure processing is on and external entities and the external DTD
 * subset are not loaded.
 * <p>
 * With a {@link #setSchemaSet(IXdmSchemaSet) schema set} the document is
 * validated (strict) after the tree is built, and the nodes carry the schema
 * information (see {@link IXdmSchemaSet#validateDocument(XdmNode)}).
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

  private boolean m_bLoadExternalEntities = DEFAULT_LOAD_EXTERNAL_ENTITIES;
  private int m_nMaxElementDepth = DEFAULT_MAX_ELEMENT_DEPTH;
  private IXdmSchemaSet m_aSchemaSet;

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
  public final IXdmSchemaSet getSchemaSet ()
  {
    return m_aSchemaSet;
  }

  /**
   * Validate documents against a schema set after they are read. Validation
   * is strict: the document element must be declared, and an invalid document
   * is rejected with an {@link XdmValidationException}. Elements and
   * attributes then carry their {@link XdmSchemaType}, <code>xsi:nil</code>
   * sets the nilled property, schema IDs and IDREFs are recognised, and
   * whitespace in element-only content is dropped (see
   * {@link IXdmSchemaSet#validateDocument(XdmNode)}).
   *
   * @param aSchemaSet
   *        The compiled schema set (thread-safe, may be shared);
   *        <code>null</code> to read without validation.
   * @return this for chaining
   */
  @NonNull
  public final XdmParser setSchemaSet (@Nullable final IXdmSchemaSet aSchemaSet)
  {
    m_aSchemaSet = aSchemaSet;
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
      // Always the JDK parser: the JAXP lookup would pick up another
      // implementation on the class path (e.g. Apache Xerces), which rejects
      // the JDK properties set below
      final SAXParserFactory aFactory = SAXParserFactory.newDefaultInstance ();
      aFactory.setNamespaceAware (true);
      aFactory.setValidating (false);
      aFactory.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
      aFactory.setFeature ("http://xml.org/sax/features/external-general-entities", m_bLoadExternalEntities);
      aFactory.setFeature ("http://xml.org/sax/features/external-parameter-entities", m_bLoadExternalEntities);
      aFactory.setFeature ("http://apache.org/xml/features/nonvalidating/load-external-dtd",
                           m_bLoadExternalEntities);
      final XMLReader aReader = aFactory.newSAXParser ().getXMLReader ();
      // Secure processing denies every external access by default, which
      // would make the features above ineffective
      if (m_bLoadExternalEntities)
        aReader.setProperty (XMLConstants.ACCESS_EXTERNAL_DTD, "all");
      final XdmTreeBuilder aBuilder = new XdmTreeBuilder ();
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
      final XdmNode aDocument = aBuilder.getDocument ();
      if (m_aSchemaSet != null)
        m_aSchemaSet.validateDocument (aDocument);
      return aDocument;
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
