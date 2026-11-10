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
package com.helger.phoxtrot.xdm;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;
import org.xml.sax.helpers.DefaultHandler;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.nonblocking.NonBlockingStringReader;
import com.helger.base.string.StringHex;
import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;

/**
 * Reads XML with the JDK parser into an {@link IXdmNode} tree, also when another JAXP
 * implementation is on the class path. The defaults are safe: secure processing is on and external
 * entities and the external DTD subset are not loaded.
 * <p>
 * With a {@link #setSchemaSet(IXdmSchemaSet) schema set} the document is validated (strict) after
 * the tree is built, and the nodes carry the schema information (see
 * {@link IXdmSchemaSet#validateDocument(IXdmNode)}).
 *
 * @author Philip Helger
 */
@NotThreadSafe
public class XdmParser
{
  public static final boolean DEFAULT_LOAD_EXTERNAL_ENTITIES = false;
  /**
   * The maximum element nesting depth. JDK 25 enforces 100 under secure processing, JDK 21 enforces
   * none, so the limit is always set explicitly.
   */
  public static final int DEFAULT_MAX_ELEMENT_DEPTH = 10_000;
  /**
   * Whether the nodes get the location the parser reports. Off by default: an input document of a
   * transformation or query needs none. Every parse of a stylesheet module, a package, a schema
   * document or a document whose validation errors are reported with a line switches it on
   * explicitly ({@link #setRecordLocations(boolean)}), and a resolver asked for a stylesheet module
   * gets it on as default of the current thread ({@link #getWithRecordLocations(Supplier)}).
   */
  public static final boolean DEFAULT_RECORD_LOCATIONS = false;

  private static final Logger LOGGER = LoggerFactory.getLogger (XdmParser.class);
  // The location default of the parsers and tree builders created on a thread
  // while getWithRecordLocations runs there; not set outside of it
  private static final ThreadLocal <Boolean> RECORD_LOCATIONS_SCOPE = new ThreadLocal <> ();
  // Optimisation: the reusable SAX parser of each thread, without (index 0) and with
  // (index 1) external entities, instead of a new parser per parse; a slot is empty while its
  // parser is in use
  private static final ThreadLocal <SAXParser []> PARSERS = ThreadLocal.withInitial ( () -> new SAXParser [2]);
  /**
   * Whether the JDK parser accepts a code point in an XML 1.0 name, per code point that XML 1.0
   * (5th edition) allows; filled on demand
   */
  private static final Map <Integer, Boolean> JDK_NAME_CHARS = new ConcurrentHashMap <> ();

  /**
   * Entity limits of the JDK parser, set explicitly because the secure processing defaults differ
   * between JDK versions (JDK 25 allows only 2,500 entity expansions, JDK 21 64,000). The values
   * are the JDK 21 defaults. They refuse the usual expansion attacks (billion laughs, quadratic
   * blow-up, parameter entities) on every supported JDK. The attributes per element are limited the
   * same way (JDK 25 allows 200, JDK 21 10,000).
   */
  static final String [] [] ENTITY_LIMITS = { { "entityExpansionLimit", "64000" },
                                              { "totalEntitySizeLimit", "50000000" },
                                              { "maxGeneralEntitySizeLimit", "0" },
                                              { "maxParameterEntitySizeLimit", "1000000" },
                                              { "entityReplacementLimit", "3000000" },
                                              { "elementAttributeLimit", "10000" } };

  /**
   * The entity resolver installed on the SAX parser when external entities are read and the user
   * set a resolver: it asks the user's resolver first and returns <code>null</code> (the parser
   * then opens the system ID itself) when that has no answer. A source the user's resolver returns
   * without a system ID gets the absolute system ID of the entity, so that the base URI of the
   * nodes read from it stays the entity's URI.
   */
  private static final class UserEntityResolver implements EntityResolver2
  {
    private final EntityResolver m_aDelegate;

    UserEntityResolver (@NonNull final EntityResolver aDelegate)
    {
      m_aDelegate = aDelegate;
    }

    /**
     * @return The system ID resolved against the base URI; as written if there is no base URI, if
     *         it is absolute already or if either is no valid URI.
     */
    @Nullable
    private static String _getAbsoluteSystemID (@Nullable final String sBaseURI, @Nullable final String sSystemID)
    {
      if (sSystemID == null || sBaseURI == null)
        return sSystemID;
      try
      {
        final URI aSystemURI = new URI (sSystemID);
        if (aSystemURI.isAbsolute ())
          return sSystemID;
        return new URI (sBaseURI).resolve (aSystemURI).toString ();
      }
      catch (final URISyntaxException | IllegalArgumentException ex)
      {
        return sSystemID;
      }
    }

    /**
     * Never an external subset for a document that declares none: the JDK parser asks for it, but
     * does not apply the declarations of the returned subset, so the user's resolver is not asked.
     */
    @Nullable
    public InputSource getExternalSubset (final String sName, final String sBaseURI)
    {
      return null;
    }

    /**
     * Ask the user's resolver for an external entity or the external DTD subset (name
     * <code>[dtd]</code>): an {@link EntityResolver2} gets the SAX2 call with the system ID as
     * written, any other resolver the SAX1 call with the absolute system ID.
     *
     * @return The user's source (with the absolute system ID filled in if it has none), or
     *         <code>null</code> for the parser's default behaviour.
     */
    @Nullable
    public InputSource resolveEntity (final String sName,
                                      final String sPublicID,
                                      final String sBaseURI,
                                      final String sSystemID) throws SAXException, IOException
    {
      final String sAbsoluteSystemID = _getAbsoluteSystemID (sBaseURI, sSystemID);
      final InputSource ret;
      if (m_aDelegate instanceof final EntityResolver2 aResolver2)
        ret = aResolver2.resolveEntity (sName, sPublicID, sBaseURI, sSystemID);
      else
        ret = m_aDelegate.resolveEntity (sPublicID, sAbsoluteSystemID);
      if (ret != null && ret.getSystemId () == null)
        ret.setSystemId (sAbsoluteSystemID);
      return ret;
    }

    @Nullable
    public InputSource resolveEntity (final String sPublicID, final String sSystemID) throws SAXException, IOException
    {
      return resolveEntity (null, sPublicID, null, sSystemID);
    }
  }

  private boolean m_bLoadExternalEntities = DEFAULT_LOAD_EXTERNAL_ENTITIES;
  private int m_nMaxElementDepth = DEFAULT_MAX_ELEMENT_DEPTH;
  private boolean m_bRecordLocations = isRecordLocationsByDefault ();
  private IXdmSchemaSet m_aSchemaSet;
  private EntityResolver m_aEntityResolver;

  public final boolean isLoadExternalEntities ()
  {
    return m_bLoadExternalEntities;
  }

  /**
   * Allow external entities and the external DTD subset to be read. Needed for default attributes
   * and entities declared outside the document, but opens the document to local file and network
   * reads, so enable it for trusted input only.
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

  @Nullable
  public final EntityResolver getEntityResolver ()
  {
    return m_aEntityResolver;
  }

  /**
   * Set a SAX entity resolver for the external DTD subset and the external entities (general and
   * parameter). It is used only when they are read ({@link #setLoadExternalEntities(boolean)});
   * with the default settings nothing external is read and the resolver is never called. It is
   * asked first for every external resource; when it returns <code>null</code> the parser opens the
   * system ID itself, as without a resolver. A returned source without system ID gets the absolute
   * system ID of the entity, which stays the base URI of the nodes read from it. An
   * {@link EntityResolver2} gets the SAX2 call (the system ID as written plus the base URI; the JDK
   * parser passes no entity name), any other resolver the SAX1 call with the absolute system ID.
   * {@link EntityResolver2#getExternalSubset(String, String)} is never called. An exception thrown
   * by the resolver fails the parse with an {@link XdmParseException}.
   * <p>
   * {@link XdmStreamReader} has no such hook: it never reads an external resource (a document that
   * would need one is refused or read in full by this parser instead).
   *
   * @param aEntityResolver
   *        The resolver; <code>null</code> (the default) for none.
   * @return this for chaining
   */
  @NonNull
  public final XdmParser setEntityResolver (@Nullable final EntityResolver aEntityResolver)
  {
    m_aEntityResolver = aEntityResolver;
    return this;
  }

  @Nonnegative
  public final int getMaxElementDepth ()
  {
    return m_nMaxElementDepth;
  }

  /**
   * @param nMaxElementDepth
   *        The maximum element nesting depth; 0 means unlimited, which is unsafe for untrusted
   *        input.
   * @return this for chaining
   */
  @NonNull
  public final XdmParser setMaxElementDepth (@Nonnegative final int nMaxElementDepth)
  {
    ValueEnforcer.isGE0 (nMaxElementDepth, "MaxElementDepth");
    m_nMaxElementDepth = nMaxElementDepth;
    return this;
  }

  /**
   * @return The location setting a parser or an {@link XdmTreeBuilder} created now on the current
   *         thread starts with: <code>true</code> while {@link #getWithRecordLocations(Supplier)}
   *         runs on this thread, otherwise {@link #DEFAULT_RECORD_LOCATIONS}.
   */
  public static boolean isRecordLocationsByDefault ()
  {
    final Boolean aScoped = RECORD_LOCATIONS_SCOPE.get ();
    return aScoped != null ? aScoped.booleanValue () : DEFAULT_RECORD_LOCATIONS;
  }

  /**
   * Call the supplier with node locations on by default for every parser and every
   * {@link XdmTreeBuilder} created on the current thread meanwhile. A parser whose
   * {@link #setRecordLocations(boolean)} is called keeps that explicit value. Used to resolve a
   * stylesheet module through a resolver that parses with the default setting
   * (<code>IResourceResolver.resolveStylesheetModule</code>). Calls may be nested; the previous
   * default of the thread is restored afterwards, also if the supplier throws.
   *
   * @param <T>
   *        The result type.
   * @param aSupplier
   *        The code to call. May not be <code>null</code>.
   * @return What the supplier returned.
   */
  public static <T> T getWithRecordLocations (@NonNull final Supplier <T> aSupplier)
  {
    ValueEnforcer.notNull (aSupplier, "Supplier");
    final Boolean aPrevious = RECORD_LOCATIONS_SCOPE.get ();
    RECORD_LOCATIONS_SCOPE.set (Boolean.TRUE);
    try
    {
      return aSupplier.get ();
    }
    finally
    {
      if (aPrevious == null)
        RECORD_LOCATIONS_SCOPE.remove ();
      else
        RECORD_LOCATIONS_SCOPE.set (aPrevious);
    }
  }

  public final boolean isRecordLocations ()
  {
    return m_bRecordLocations;
  }

  /**
   * Decide whether the nodes get the line and column the parser reports (see
   * {@link IXdmNode#getLine()}). Only elements, comments and processing instructions have location
   * fields (attributes and text nodes report the location of their parent); without, each of them
   * is 8 bytes smaller and all locations are 0. Off by default ({@link #DEFAULT_RECORD_LOCATIONS},
   * or on while {@link #getWithRecordLocations(Supplier)} runs on the creating thread); switch it on
   * for documents whose node locations are read: the error messages of stylesheets and schemas and
   * <code>xsl:try</code> use them, and so does the line of an instance validation error of the
   * native XSD validator (without, such an error has line 0 and only its path). The one rule of
   * phoxtrot: every reader leaves them out, only the parses of stylesheet modules, packages, schema
   * documents and XSD instance validation switch them on (for a stylesheet module read through a
   * resolver: <code>IResourceResolver.resolveStylesheetModule</code>).
   *
   * @param bRecordLocations
   *        <code>true</code> to record them.
   * @return this for chaining
   */
  @NonNull
  public final XdmParser setRecordLocations (final boolean bRecordLocations)
  {
    m_bRecordLocations = bRecordLocations;
    return this;
  }

  @Nullable
  public final IXdmSchemaSet getSchemaSet ()
  {
    return m_aSchemaSet;
  }

  /**
   * Validate documents against a schema set after they are read. Validation is strict: the document
   * element must be declared, and an invalid document is rejected with an
   * {@link XdmValidationException}. Elements and attributes then carry their {@link XdmSchemaType},
   * <code>xsi:nil</code> sets the nilled property, schema IDs and IDREFs are recognised, and
   * whitespace in element-only content is dropped (see
   * {@link IXdmSchemaSet#validateDocument(IXdmNode)}).
   *
   * @param aSchemaSet
   *        The compiled schema set (thread-safe, may be shared); <code>null</code> to read without
   *        validation.
   * @return this for chaining
   */
  @NonNull
  public final XdmParser setSchemaSet (@Nullable final IXdmSchemaSet aSchemaSet)
  {
    m_aSchemaSet = aSchemaSet;
    return this;
  }

  /**
   * Probe the JDK parser (namespace aware, no other settings) with a small document.
   *
   * @param sXml
   *        The complete document to parse.
   * @return <code>true</code> if it is read without error, <code>false</code> on any parse or
   *         configuration error.
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
   * The JDK parser applies the name rules of XML 1.0 (4th edition) to XML 1.0 documents.
   *
   * @return <code>true</code> if the code point is an XML 1.0 (5th edition) name character in a
   *         position where the JDK parser rejects it.
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
   * Get the source text again to explain a parse error, without consuming a stream: only a
   * <code>file:</code> system ID of a source without byte or character stream is read, as UTF-8.
   *
   * @param aSource
   *        The source that failed to parse.
   * @param sText
   *        The text the source was made from, if known.
   * @return <code>sText</code> if not <code>null</code>, otherwise the file content, or
   *         <code>null</code> if it cannot be read.
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
   * Explain a parse error that comes from a name the JDK parser does not accept although XML 1.0
   * (5th edition) allows it: the markup of the error line up to the tag at the reported column is
   * searched for such characters.
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
      final String sChar = StringHex.appendHexStringLeadingZero (new StringBuilder ("U+"), c, 4, true).toString ();
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
   * Create a JDK SAX parser with the factory settings of a parse: namespace aware, not validating,
   * secure processing on, and external entities and the external DTD subset as given.
   *
   * @param bLoadExternalEntities
   *        Whether external entities and the external DTD subset are loaded.
   * @return The new parser.
   * @throws ParserConfigurationException
   *         if the JDK parser does not support a setting
   * @throws SAXException
   *         if the JDK parser does not support a feature
   */
  @NonNull
  private static SAXParser _createSAXParser (final boolean bLoadExternalEntities) throws ParserConfigurationException,
                                                                                         SAXException
  {
    // Always the JDK parser: the JAXP lookup would pick up another
    // implementation on the class path (e.g. Apache Xerces), which rejects
    // the JDK properties set in _parse
    final SAXParserFactory aFactory = SAXParserFactory.newDefaultInstance ();
    aFactory.setNamespaceAware (true);
    aFactory.setValidating (false);
    aFactory.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
    aFactory.setFeature ("http://xml.org/sax/features/external-general-entities", bLoadExternalEntities);
    aFactory.setFeature ("http://xml.org/sax/features/external-parameter-entities", bLoadExternalEntities);
    aFactory.setFeature ("http://apache.org/xml/features/nonvalidating/load-external-dtd", bLoadExternalEntities);
    return aFactory.newSAXParser ();
  }

  /**
   * Put a parser back into the cache of the current thread after a parse, reset to its factory
   * settings and without the handlers and resolver of the parse, so that the cache keeps no
   * reference to the tree or to user objects. A parser that cannot be reset is dropped.
   *
   * @param aParsers
   *        The parser cache of the current thread.
   * @param nSlot
   *        The slot of the parser.
   * @param aParser
   *        The parser used for the parse.
   */
  private static void _releaseSAXParser (@NonNull final SAXParser [] aParsers,
                                         final int nSlot,
                                         @NonNull final SAXParser aParser)
  {
    try
    {
      aParser.reset ();
      final XMLReader aReader = aParser.getXMLReader ();
      aReader.setContentHandler (null);
      aReader.setErrorHandler (null);
      aReader.setDTDHandler (null);
      aReader.setEntityResolver (null);
      aReader.setProperty ("http://xml.org/sax/properties/lexical-handler", null);
      aReader.setProperty ("http://xml.org/sax/properties/declaration-handler", null);
      aParsers[nSlot] = aParser;
    }
    catch (final SAXException | RuntimeException ex)
    {
      // Not reused
      LOGGER.warn ("Failed to reset the SAX parser for reuse: " + ex.getMessage ());
    }
  }

  /**
   * Read a source with the JDK SAX parser into a tree built by {@link XdmTreeBuilder}: namespace
   * aware, not DTD validating, secure processing on, external entities and the external DTD subset
   * only with {@link #setLoadExternalEntities(boolean)} (then through the
   * {@link #setEntityResolver(EntityResolver) entity resolver} if one is set), the
   * {@link #ENTITY_LIMITS} and the {@link #getMaxElementDepth() maximum element depth} set
   * explicitly. With a schema set the finished document is validated afterwards. A parse error that
   * comes from a name the JDK parser rejects against the 5th edition rules gets an explanation
   * appended to the message, which is also logged.
   * <p>
   * The SAX parser is reused: each thread keeps one per setting of external entities. It is taken
   * out of the cache for the parse, so that a parse started during it on the same thread (e.g. from
   * an entity resolver) creates its own.
   *
   * @param aSource
   *        The source to read.
   * @param sText
   *        The source text, if the source was made from a string; used only to explain a parse
   *        error.
   * @return The document node.
   * @throws XdmValidationException
   *         if a schema set is set and the document is not valid against it
   * @throws XdmParseException
   *         on any other parser, configuration or I/O error
   */
  @NonNull
  private IXdmNode _parse (@NonNull final InputSource aSource, @Nullable final String sText) throws XdmParseException
  {
    final SAXParser [] aParsers = PARSERS.get ();
    final int nSlot = m_bLoadExternalEntities ? 1 : 0;
    SAXParser aParser = aParsers[nSlot];
    aParsers[nSlot] = null;
    try
    {
      if (aParser == null)
        aParser = _createSAXParser (m_bLoadExternalEntities);
      final XMLReader aReader = aParser.getXMLReader ();
      // Secure processing denies every external access by default, which
      // would make the features above ineffective
      if (m_bLoadExternalEntities)
        aReader.setProperty (XMLConstants.ACCESS_EXTERNAL_DTD, "all");
      final XdmTreeBuilder aBuilder = new XdmTreeBuilder (m_bRecordLocations);
      aReader.setContentHandler (aBuilder);
      aReader.setErrorHandler (aBuilder);
      aReader.setDTDHandler (aBuilder);
      // The user's resolver only where the parser reads external resources
      if (m_bLoadExternalEntities && m_aEntityResolver != null)
        aReader.setEntityResolver (new UserEntityResolver (m_aEntityResolver));
      else
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
    finally
    {
      if (aParser != null)
        _releaseSAXParser (aParsers, nSlot, aParser);
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
   *        The XML text to read. May not be <code>null</code>. The document has no system ID, so
   *        relative references cannot be resolved.
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
    // StringReader reads from the string itself, where NonBlockingStringReader
    // would copy it into a char array first
    return _parse (new InputSource (new StringReader (sXml)), sXml);
  }

  /**
   * @param aPath
   *        The file to read. May not be <code>null</code>. Its URI becomes the system ID of the
   *        source.
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
