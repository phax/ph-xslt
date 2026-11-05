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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.nonblocking.NonBlockingStringReader;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;

/**
 * Reads XML with the JDK parser into an {@link IXdmNode} tree, also when another
 * JAXP implementation is on the class path. The defaults are
 * safe: secure processing is on and external entities and the external DTD
 * subset are not loaded.
 * <p>
 * With a {@link #setSchemaSet(IXdmSchemaSet) schema set} the document is
 * validated (strict) after the tree is built, and the nodes carry the schema
 * information (see {@link IXdmSchemaSet#validateDocument(IXdmNode)}).
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

  private static final Logger LOGGER = LoggerFactory.getLogger (XdmParser.class);
  /**
   * Whether the JDK parser accepts a code point in an XML 1.0 name, per code
   * point that XML 1.0 (5th edition) allows; filled on demand
   */
  private static final Map <Integer, Boolean> JDK_NAME_CHARS = new ConcurrentHashMap <> ();

  /**
   * Entity limits of the JDK parser, set explicitly because the secure
   * processing defaults differ between JDK versions (JDK 25 allows only 2,500
   * entity expansions, JDK 21 64,000). The values are the JDK 21 defaults.
   * They refuse the usual expansion attacks (billion laughs, quadratic
   * blow-up, parameter entities) on every supported JDK. The attributes per
   * element are limited the same way (JDK 25 allows 200, JDK 21 10,000).
   */
  static final String [] [] ENTITY_LIMITS = { { "entityExpansionLimit", "64000" },
                                                      { "totalEntitySizeLimit", "50000000" },
                                                      { "maxGeneralEntitySizeLimit", "0" },
                                                      { "maxParameterEntitySizeLimit", "1000000" },
                                                      { "entityReplacementLimit", "3000000" },
                                                      { "elementAttributeLimit", "10000" } };

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
   * {@link IXdmSchemaSet#validateDocument(IXdmNode)}).
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
   * Probe the JDK parser (namespace aware, no other settings) with a small
   * document.
   *
   * @param sXml
   *        The complete document to parse.
   * @return <code>true</code> if it is read without error, <code>false</code>
   *         on any parse or configuration error.
   */
  private static boolean _parsesWithJdk (@NonNull final String sXml)
  {
    try
    {
      final SAXParserFactory aFactory = SAXParserFactory.newDefaultInstance ();
      aFactory.setNamespaceAware (true);
      aFactory.newSAXParser ().parse (new InputSource (new NonBlockingStringReader (sXml)), new DefaultHandler ());
      return true;
    }
    catch (final ParserConfigurationException | SAXException | IOException ex)
    {
      return false;
    }
  }

  /**
   * The JDK parser applies the name rules of XML 1.0 (4th edition) to XML 1.0
   * documents.
   *
   * @return <code>true</code> if the code point is an XML 1.0 (5th edition)
   *         name character in a position where the JDK parser rejects it.
   */
  private static boolean _isRejectedNameChar (final int c)
  {
    if (c < 0x80 || !XmlNames.isNameChar (c))
      return false;
    final Boolean aAccepted = JDK_NAME_CHARS.computeIfAbsent (Integer.valueOf (c), k -> {
      final String sChar = new String (Character.toChars (c));
      boolean bAccepted = _parsesWithJdk ("<a" + sChar + "/>");
      if (bAccepted && XmlNames.isNameStartChar (c))
        bAccepted = _parsesWithJdk ("<" + sChar + "/>");
      return Boolean.valueOf (bAccepted);
    });
    return !aAccepted.booleanValue ();
  }

  /**
   * Get the source text again to explain a parse error, without
   * consuming a stream: only a <code>file:</code> system ID of a source
   * without byte or character stream is read, as UTF-8.
   *
   * @param aSource
   *        The source that failed to parse.
   * @param sText
   *        The text the source was made from, if known.
   * @return <code>sText</code> if not <code>null</code>, otherwise the file
   *         content, or <code>null</code> if it cannot be read.
   */
  @Nullable
  private static String _readSourceText (@NonNull final InputSource aSource, @Nullable final String sText)
  {
    if (sText != null)
      return sText;
    final String sSystemId = aSource.getSystemId ();
    if (aSource.getByteStream () != null || aSource.getCharacterStream () != null || sSystemId == null)
      return null;
    try
    {
      final URI aURI = new URI (sSystemId);
      if (!"file".equalsIgnoreCase (aURI.getScheme ()))
        return null;
      return Files.readString (Path.of (aURI), StandardCharsets.UTF_8);
    }
    catch (final URISyntaxException | IOException | IllegalArgumentException ex)
    {
      // Not readable as UTF-8 text: no explanation, the parser's error stands
      return null;
    }
  }

  /**
   * Explain a parse error that comes from a name the JDK parser does not
   * accept although XML 1.0 (5th edition) allows it: the markup of the error
   * line up to the tag at the reported column is searched for such characters.
   *
   * @return The explanation, or <code>null</code> if there is none.
   */
  @Nullable
  private static String _explainNameError (@NonNull final SAXParseException ex, @Nullable final String sText)
  {
    if (sText == null || ex.getLineNumber () < 1)
      return null;
    final String [] aLines = sText.split ("\r\n|\r|\n", -1);
    if (ex.getLineNumber () > aLines.length)
      return null;
    String sLine = aLines[ex.getLineNumber () - 1];
    // Up to the end of the tag at the reported column: the column may lie
    // before or after the character the parser stopped at
    if (ex.getColumnNumber () > 0 && ex.getColumnNumber () < sLine.length ())
    {
      final int nTagEnd = sLine.indexOf ('>', ex.getColumnNumber ());
      if (nTagEnd >= 0)
        sLine = sLine.substring (0, nTagEnd + 1);
    }
    // Names only occur in markup: inside a tag outside quoted values, and in
    // entity references. A line that starts inside a tag is not recognised as
    // such, which only means no explanation.
    final StringBuilder aChars = new StringBuilder ();
    boolean bInTag = false;
    boolean bInRef = false;
    int cQuote = 0;
    int i = 0;
    while (i < sLine.length ())
    {
      final int c = sLine.codePointAt (i);
      i += Character.charCount (c);
      if (cQuote != 0)
      {
        if (c == cQuote)
          cQuote = 0;
        continue;
      }
      if (bInTag)
      {
        if (c == '>')
          bInTag = false;
        else
          if (c == '"' || c == '\'')
            cQuote = c;
      }
      else
        if (bInRef)
        {
          if (c == ';')
            bInRef = false;
        }
        else
        {
          if (c == '<')
            bInTag = true;
          else
            if (c == '&')
              bInRef = true;
          continue;
        }
      final String sChar = String.format ("U+%04X", Integer.valueOf (c));
      if (_isRejectedNameChar (c) && aChars.indexOf (sChar) < 0)
      {
        if (!aChars.isEmpty ())
          aChars.append (", ");
        aChars.append (sChar);
      }
    }
    if (aChars.isEmpty ())
      return null;
    return "line " +
           ex.getLineNumber () +
           " contains " +
           aChars +
           ", which XML 1.0 (5th edition) allows in names, but the JDK XML parser applies the name rules of the 4th edition to XML 1.0 documents";
  }

  /**
   * Read a source with the JDK SAX parser into a tree built by
   * {@link XdmTreeBuilder}: namespace aware, not DTD validating, secure
   * processing on, external entities and the external DTD subset only with
   * {@link #setLoadExternalEntities(boolean)}, the {@link #ENTITY_LIMITS} and
   * the {@link #getMaxElementDepth() maximum element depth} set explicitly.
   * With a schema set the finished document is validated afterwards. A parse
   * error that comes from a name the JDK parser rejects against the 5th
   * edition rules gets an explanation appended to the message, which is also
   * logged.
   *
   * @param aSource
   *        The source to read.
   * @param sText
   *        The source text, if the source was made from a string; used only
   *        to explain a parse error.
   * @return The document node.
   * @throws XdmValidationException
   *         if a schema set is set and the document is not valid against it
   * @throws XdmParseException
   *         on any other parser, configuration or I/O error
   */
  @NonNull
  private IXdmNode _parse (@NonNull final InputSource aSource, @Nullable final String sText) throws XdmParseException
  {
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
      final IXdmNode aDocument = aBuilder.getDocument ();
      if (m_aSchemaSet != null)
        m_aSchemaSet.validateDocument (aDocument);
      return aDocument;
    }
    catch (final SAXParseException ex)
    {
      final String sExplanation = _explainNameError (ex, _readSourceText (aSource, sText));
      if (sExplanation == null)
        throw new XdmParseException ("Failed to parse XML: " + ex.getMessage (), ex);
      final String sMessage = "Failed to parse XML: " + ex.getMessage () + " - " + sExplanation;
      LOGGER.error (sMessage + (aSource.getSystemId () != null ? " [" + aSource.getSystemId () + "]" : ""));
      throw new XdmParseException (sMessage, ex);
    }
    catch (final ParserConfigurationException | SAXException | IOException ex)
    {
      throw new XdmParseException ("Failed to parse XML: " + ex.getMessage (), ex);
    }
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
  public IXdmNode parse (@NonNull final InputSource aSource) throws XdmParseException
  {
    ValueEnforcer.notNull (aSource, "Source");
    return _parse (aSource, null);
  }

  /**
   * @param sXml
   *        The XML text to read. May not be <code>null</code>. The document
   *        has no system ID, so relative references cannot be resolved.
   * @return The document node.
   * @throws XdmValidationException
   *         if a schema is set and the document is not valid against it
   * @throws XdmParseException
   *         if the document cannot be read
   */
  @NonNull
  public IXdmNode parse (@NonNull final String sXml) throws XdmParseException
  {
    ValueEnforcer.notNull (sXml, "Xml");
    return _parse (new InputSource (new NonBlockingStringReader (sXml)), sXml);
  }

  /**
   * @param aPath
   *        The file to read. May not be <code>null</code>. Its URI becomes
   *        the system ID of the source.
   * @return The document node.
   * @throws XdmValidationException
   *         if a schema is set and the document is not valid against it
   * @throws XdmParseException
   *         if the document cannot be read
   */
  @NonNull
  public IXdmNode parse (@NonNull final Path aPath) throws XdmParseException
  {
    ValueEnforcer.notNull (aPath, "Path");
    return parse (new InputSource (aPath.toUri ().toString ()));
  }
}
