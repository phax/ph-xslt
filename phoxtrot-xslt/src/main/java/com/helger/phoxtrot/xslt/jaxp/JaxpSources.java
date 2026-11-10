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
package com.helger.phoxtrot.xslt.jaxp;

import java.io.IOException;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmTreeBuilder;
import com.helger.phoxtrot.xdm.bridge.DomToXdm;

/**
 * Turns JAXP {@link Source} objects into XDM nodes.
 *
 * @author Philip Helger
 */
@Immutable
final class JaxpSources
{
  private static final String FEATURE_NAMESPACES = "http://xml.org/sax/features/namespaces";
  private static final String FEATURE_NAMESPACE_PREFIXES = "http://xml.org/sax/features/namespace-prefixes";
  private static final String PROPERTY_LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler";
  private static final String PROPERTY_DECLARATION_HANDLER = "http://xml.org/sax/properties/declaration-handler";

  @PresentForCodeCoverage
  private static final JaxpSources INSTANCE = new JaxpSources ();

  private JaxpSources ()
  {}

  /**
   * @return The SAX input source of a stream source: its byte stream, else
   *         its character stream, with system and public ID; just the system
   *         ID if it has no stream.
   * @throws TransformerException
   *         if it has neither a stream nor a system ID
   */
  @NonNull
  private static InputSource _toInputSource (@NonNull final StreamSource aSource) throws TransformerException
  {
    final InputSource ret = new InputSource ();
    if (aSource.getInputStream () != null)
      ret.setByteStream (aSource.getInputStream ());
    else
      if (aSource.getReader () != null)
        ret.setCharacterStream (aSource.getReader ());
      else
        if (aSource.getSystemId () == null)
          throw new TransformerException ("The StreamSource has neither a stream, a reader nor a system ID");
    ret.setSystemId (aSource.getSystemId ());
    ret.setPublicId (aSource.getPublicId ());
    return ret;
  }

  /**
   * Run a SAX parser the caller configured into an XDM tree builder. The
   * builder becomes its content, DTD and error handler, and its lexical and
   * declaration handler where the parser supports them; namespaces are
   * switched on (namespace-prefixes off) where the parser allows it.
   */
  @NonNull
  private static IXdmNode _parseWithReader (@NonNull final XMLReader aReader,
                                            @NonNull final InputSource aInput,
                                            final boolean bRecordLocations) throws TransformerException
  {
    final XdmTreeBuilder aBuilder = new XdmTreeBuilder (bRecordLocations);
    try
    {
      aReader.setFeature (FEATURE_NAMESPACES, true);
      aReader.setFeature (FEATURE_NAMESPACE_PREFIXES, false);
    }
    catch (final SAXException ex)
    {
      // A parser that is namespace aware anyway does not need it
    }
    aReader.setContentHandler (aBuilder);
    aReader.setDTDHandler (aBuilder);
    aReader.setErrorHandler (aBuilder);
    try
    {
      aReader.setProperty (PROPERTY_LEXICAL_HANDLER, aBuilder);
    }
    catch (final SAXException ex)
    {
      // Without lexical handler there are no comments
    }
    try
    {
      aReader.setProperty (PROPERTY_DECLARATION_HANDLER, aBuilder);
    }
    catch (final SAXException ex)
    {
      // Without declaration handler the parsed entity names are unknown
    }
    try
    {
      aReader.parse (aInput);
    }
    catch (final SAXException | IOException ex)
    {
      throw new TransformerException ("Failed to parse the SAXSource: " + ex.getMessage (), ex);
    }
    final IXdmNode ret = aBuilder.getDocument ();
    if (ret.getDocumentUri () == null && aInput.getSystemId () != null)
      ret.setDocumentUri (aInput.getSystemId ());
    return ret;
  }

  /**
   * @return The document parsed with {@link XdmParser} (the JDK parser with
   *         secure processing) and the parser settings of the adapter.
   */
  @NonNull
  private static IXdmNode _parse (@NonNull final InputSource aInput,
                                  @NonNull final JaxpParserSettings aParserSettings,
                                  final boolean bRecordLocations) throws TransformerException
  {
    try
    {
      return aParserSettings.createParser (bRecordLocations).parse (aInput);
    }
    catch (final XdmParseException ex)
    {
      throw new TransformerException (ex.getMessage (), ex);
    }
  }

  /**
   * @return The system ID of a source, for a DOM source without one the
   *         <code>documentURI</code> of its DOM document.
   */
  @Nullable
  static String getSystemId (@NonNull final Source aSource)
  {
    if (aSource.getSystemId () != null)
      return aSource.getSystemId ();
    if (aSource instanceof final DOMSource aDOMSource && aDOMSource.getNode () != null)
    {
      final Node aNode = aDOMSource.getNode ();
      final Document aDoc = aNode instanceof final Document aDocNode ? aDocNode : aNode.getOwnerDocument ();
      return aDoc == null ? null : aDoc.getDocumentURI ();
    }
    return null;
  }

  /**
   * Turn a source into an XDM node:
   * <ul>
   * <li>{@link XdmSource}: its node;</li>
   * <li>{@link DOMSource}: the XDM counterpart of its node in a copy of the
   * node's DOM tree ({@link DomToXdm#convert(Node, String)}) with the system
   * ID of the source as document URI; an empty document if it has no
   * node;</li>
   * <li>{@link StreamSource}: parsed with {@link XdmParser} from its byte
   * stream, character stream or system ID;</li>
   * <li>{@link SAXSource}: with its XML reader if it has one (see
   * {@link #_parseWithReader(XMLReader, InputSource, boolean)}; the parser
   * settings do not apply to it, its own configuration does), otherwise parsed
   * with {@link XdmParser} from its input source;</li>
   * <li>any other source with a system ID: parsed from the system ID.</li>
   * </ul>
   *
   * @param aSource
   *        The source.
   * @param aParserSettings
   *        The settings of every {@link XdmParser} created here: external
   *        DTDs and entities, the schemes allowed for them and the entity
   *        resolver.
   * @param bRecordLocations
   *        Whether a parsed source gets node locations
   *        ({@link XdmParser#setRecordLocations(boolean)}): <code>true</code>
   *        for a stylesheet (error locations, <code>xsl:try</code>),
   *        <code>false</code> for a source document. A DOM source has no
   *        locations either way.
   * @return The node: a document node, or for a DOM source the node that
   *         corresponds to the DOM node.
   * @throws TransformerException
   *         if the source cannot be read or is of an unsupported kind
   */
  @NonNull
  static IXdmNode toXdm (@NonNull final Source aSource,
                         @NonNull final JaxpParserSettings aParserSettings,
                         final boolean bRecordLocations) throws TransformerException
  {
    if (aSource instanceof final XdmSource aXdmSource)
      return aXdmSource.getNode ();
    if (aSource instanceof final DOMSource aDOMSource)
    {
      final Node aNode = aDOMSource.getNode ();
      if (aNode == null)
      {
        final IXdmNode ret = IXdmNode.createDocument ();
        if (aDOMSource.getSystemId () != null)
          ret.setDocumentUri (aDOMSource.getSystemId ());
        ret.assignDocumentOrder ();
        return ret;
      }
      try
      {
        return DomToXdm.convert (aNode, aDOMSource.getSystemId ());
      }
      catch (final IllegalArgumentException | IllegalStateException ex)
      {
        throw new TransformerException ("The DOMSource cannot be used: " + ex.getMessage (), ex);
      }
    }
    if (aSource instanceof final StreamSource aStreamSource)
      return _parse (_toInputSource (aStreamSource), aParserSettings, bRecordLocations);
    if (aSource instanceof final SAXSource aSAXSource)
    {
      InputSource aInput = aSAXSource.getInputSource ();
      if (aInput == null)
      {
        if (aSAXSource.getSystemId () == null)
          throw new TransformerException ("The SAXSource has neither an input source nor a system ID");
        aInput = new InputSource (aSAXSource.getSystemId ());
      }
      if (aSAXSource.getXMLReader () != null)
        return _parseWithReader (aSAXSource.getXMLReader (), aInput, bRecordLocations);
      return _parse (aInput, aParserSettings, bRecordLocations);
    }
    if (aSource.getSystemId () != null)
      return _parse (new InputSource (aSource.getSystemId ()), aParserSettings, bRecordLocations);
    throw new TransformerException ("Unsupported Source " + aSource.getClass ().getName ());
  }
}
