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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.EntityResolver;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.nonblocking.NonBlockingStringReader;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.fn.FnUri;
import com.helger.phoxtrot.xslt.EXsltThreading;
import com.helger.phoxtrot.xslt.IXsltMessageListener;
import com.helger.phoxtrot.xslt.IXsltTraceListener;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;

/**
 * The phoxtrot implementation of the JAXP {@link TransformerFactory}
 * (XSLT 3.0). It is deliberately <b>not</b> registered as a service
 * (<code>META-INF/services/javax.xml.transform.TransformerFactory</code>),
 * which would replace the JDK transformer for every application on the class
 * path. Create it directly with <code>new PhoxtrotTransformerFactory ()</code>
 * or by name with
 * <code>TransformerFactory.newInstance ("com.helger.phoxtrot.xslt.jaxp.PhoxtrotTransformerFactory", aClassLoader)</code>.
 * Like every JAXP factory it is not thread-safe; the {@link Templates} it
 * creates are.
 * <ul>
 * <li>Stylesheets and sources: {@link StreamSource}, {@link DOMSource},
 * {@link SAXSource} and {@link XdmSource}. A stylesheet is parsed with
 * phoxtrot's own parser (secure processing, no external DTD or entities
 * unless {@link #ATTR_LOAD_EXTERNAL_ENTITIES} is set; then the
 * {@link EntityResolver} of {@link #ATTR_ENTITY_RESOLVER} is asked first, and
 * the parser opens them itself only for the schemes of
 * {@link XMLConstants#ACCESS_EXTERNAL_DTD}). The same applies to the sources
 * and the documents of the run, with the entity resolver of the
 * transformer.</li>
 * <li><code>xsl:include</code>, <code>xsl:import</code> and the documents of
 * the run (<code>document()</code>, <code>fn:doc</code>,
 * <code>fn:unparsed-text</code>) go to the {@link URIResolver} first (the
 * factory's for compilation, the transformer's at run time, which starts as
 * the factory's), with the absolute URI as href and base. What it does not
 * resolve is read directly if the scheme is allowed by
 * {@link XMLConstants#ACCESS_EXTERNAL_STYLESHEET} (default
 * <code>all</code>).</li>
 * <li>Compile errors are reported to
 * {@link ErrorListener#fatalError(TransformerException)} and thrown as
 * {@link TransformerConfigurationException} with the error code at the start
 * of the message, the stylesheet location and the phoxtrot error as cause.
 * The compiler produces no warnings.</li>
 * <li>{@link #newTransformer()} gives the identity transformer (method xml
 * unless an output property says otherwise).</li>
 * <li>Features: {@link XMLConstants#FEATURE_SECURE_PROCESSING} (default
 * <code>false</code>; setting it to <code>true</code> sets
 * {@link XMLConstants#ACCESS_EXTERNAL_STYLESHEET} and
 * {@link XMLConstants#ACCESS_EXTERNAL_DTD} to the empty string, as JAXP
 * specifies, so that only the URI resolver, and for external DTDs and
 * entities the entity resolver, gives access to other resources),
 * and the source and result features of the supported kinds (fixed to
 * <code>true</code>). Not supported: {@link javax.xml.transform.sax.SAXTransformerFactory},
 * StAX sources and results.</li>
 * <li>Attributes: {@link XMLConstants#ACCESS_EXTERNAL_STYLESHEET} (a
 * <code>jar:</code> URI needs its nested URI to be allowed as well),
 * {@link XMLConstants#ACCESS_EXTERNAL_DTD} (in effect only with
 * {@link #ATTR_LOAD_EXTERNAL_ENTITIES}: phoxtrot reads no external DTD or
 * entity otherwise), and the phoxtrot attributes
 * {@link #ATTR_LOAD_EXTERNAL_ENTITIES}, {@link #ATTR_ENTITY_RESOLVER},
 * {@link #ATTR_SCHEMA_AWARE}, {@link #ATTR_BACKWARDS_COMPATIBLE},
 * {@link #ATTR_MESSAGE_LISTENER}, {@link #ATTR_TRACE_LISTENER},
 * {@link #ATTR_MAX_STEPS} and {@link #ATTR_THREADING}. Other names are
 * rejected.</li>
 * </ul>
 *
 * @author Philip Helger
 */
@NotThreadSafe
public class PhoxtrotTransformerFactory extends TransformerFactory
{
  /** The common prefix of the phoxtrot attribute names. */
  public static final String ATTR_PREFIX = "http://helger.com/phoxtrot/jaxp/";
  /**
   * Attribute ({@link Boolean} or "true"/"false"): load external DTDs and
   * entities when parsing stylesheets, sources and documents (default
   * <code>false</code>).
   */
  public static final String ATTR_LOAD_EXTERNAL_ENTITIES = ATTR_PREFIX + "load-external-entities";
  /**
   * Attribute ({@link EntityResolver} or <code>null</code>): the SAX entity
   * resolver asked first for external DTDs and entities when they are loaded
   * ({@link #ATTR_LOAD_EXTERNAL_ENTITIES}), for stylesheets, included and
   * imported modules, and as initial resolver of the transformers for their
   * sources and the documents of the run (default <code>null</code>, see
   * {@link XdmParser#setEntityResolver(EntityResolver)}). What it returns is
   * not checked against {@link XMLConstants#ACCESS_EXTERNAL_DTD}.
   */
  public static final String ATTR_ENTITY_RESOLVER = ATTR_PREFIX + "entity-resolver";
  /**
   * Attribute ({@link Boolean} or "true"/"false"): compile stylesheets as a
   * schema-aware processor (default <code>false</code>, see
   * {@link XsltCompileOptions#setSchemaAware(boolean)}).
   */
  public static final String ATTR_SCHEMA_AWARE = ATTR_PREFIX + "schema-aware";
  /**
   * Attribute ({@link Boolean} or "true"/"false"): compile stylesheets with
   * the XSLT 1.0 compatibility feature, so that elements with an effective
   * version below 2.0 are processed with XSLT 1.0 behaviour instead of raising
   * XTDE0160 (default <code>true</code>, see
   * {@link XsltCompileOptions#setBackwardsCompatibleClaim(boolean)}).
   */
  public static final String ATTR_BACKWARDS_COMPATIBLE = ATTR_PREFIX + "backwards-compatible";
  /**
   * Attribute ({@link IXsltMessageListener} or <code>null</code>): the
   * initial message listener of the transformers, which then receives
   * <code>xsl:message</code>, failed <code>xsl:assert</code> and mode
   * warnings instead of the error listener (default <code>null</code>).
   */
  public static final String ATTR_MESSAGE_LISTENER = ATTR_PREFIX + "message-listener";
  /**
   * Attribute ({@link IXsltTraceListener} or <code>null</code>): the initial
   * trace listener of the transformers, which follows the templates,
   * functions and optionally instructions of each transformation (default
   * <code>null</code>, see
   * {@link com.helger.phoxtrot.xslt.XsltTransformRequest#setTraceListener(IXsltTraceListener)}).
   */
  public static final String ATTR_TRACE_LISTENER = ATTR_PREFIX + "trace-listener";
  /**
   * Attribute ({@link Number} or a decimal string): the step limit of a
   * transformation, see {@link com.helger.phoxtrot.xslt.XsltTransformRequest#setMaxSteps(long)}
   * (0, the default, for the engine default;
   * {@link com.helger.phoxtrot.xslt.XsltTransformRequest#UNLIMITED_STEPS}
   * for no limit).
   */
  public static final String ATTR_MAX_STEPS = ATTR_PREFIX + "max-steps";
  /**
   * Attribute ({@link EXsltThreading} or the name of one of its constants):
   * the thread that compilations ({@link #newTemplates(Source)}) and the
   * transformations of the transformers run on (default
   * {@link EXsltThreading#DEFAULT}, see
   * {@link XsltCompileOptions#setThreading(EXsltThreading)} and
   * {@link com.helger.phoxtrot.xslt.XsltTransformRequest#setThreading(EXsltThreading)}).
   * A transformer can choose its own with
   * {@link PhoxtrotTransformer#setThreading(EXsltThreading)}.
   */
  public static final String ATTR_THREADING = ATTR_PREFIX + "threading";
  /** The default of the external access attributes: all schemes. */
  public static final String DEFAULT_ACCESS_EXTERNAL = "all";

  private static final Pattern PSEUDO_ATTR = Pattern.compile ("([A-Za-z_][\\w.-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

  /** The identity stylesheet, compiled once. */
  private static final class IdentityHolder
  {
    static final XsltExecutable IDENTITY = XsltCompiler.compile ("<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
                                                                 "<xsl:output method='xml'/>" +
                                                                 "<xsl:template match='.'><xsl:copy-of select='.'/></xsl:template>" +
                                                                 "</xsl:stylesheet>",
                                                                 new XsltCompileOptions ());
  }

  private URIResolver m_aURIResolver;
  private ErrorListener m_aErrorListener = new JaxpErrors.LoggingErrorListener ();
  private boolean m_bSecureProcessing = false;
  private String m_sAccessExternalStylesheet = DEFAULT_ACCESS_EXTERNAL;
  private String m_sAccessExternalDtd = DEFAULT_ACCESS_EXTERNAL;
  private boolean m_bLoadExternalEntities = XdmParser.DEFAULT_LOAD_EXTERNAL_ENTITIES;
  private EntityResolver m_aEntityResolver;
  private boolean m_bSchemaAware = XsltCompileOptions.DEFAULT_SCHEMA_AWARE;
  private boolean m_bBackwardsCompatible = XsltCompileOptions.DEFAULT_BACKWARDS_COMPATIBLE_CLAIM;
  private IXsltMessageListener m_aMessageListener;
  private IXsltTraceListener m_aTraceListener;
  private long m_nMaxSteps = 0;
  private EXsltThreading m_eThreading = EXsltThreading.DEFAULT;

  /** Create a factory with the default settings. */
  public PhoxtrotTransformerFactory ()
  {}

  @NonNull
  private JaxpParserSettings _createParserSettings ()
  {
    return new JaxpParserSettings (m_bLoadExternalEntities, m_sAccessExternalDtd, m_aEntityResolver);
  }

  @NonNull
  private JaxpSettings _createSettings ()
  {
    return new JaxpSettings (m_aURIResolver,
                             m_sAccessExternalStylesheet,
                             _createParserSettings (),
                             m_aMessageListener,
                             m_aTraceListener,
                             m_nMaxSteps,
                             m_eThreading);
  }

  /**
   * Compile a stylesheet. The source is turned into XDM (see the class
   * description), with its system ID (for a DOM source without one, the
   * <code>documentURI</code> of its document) as base URI; includes, imports
   * and static documents are resolved with the factory's URI resolver and
   * access settings.
   *
   * @return The compiled stylesheet. Never <code>null</code>.
   * @throws TransformerConfigurationException
   *         if the source cannot be read (XTSE0010) or for a static error,
   *         after reporting it to the error listener (or what the listener
   *         throws)
   */
  @Override
  @NonNull
  public PhoxtrotTemplates newTemplates (@NonNull final Source aSource) throws TransformerConfigurationException
  {
    ValueEnforcer.notNull (aSource, "Source");
    final String sBaseUri = JaxpSources.getSystemId (aSource);
    final JaxpParserSettings aParserSettings = _createParserSettings ();
    final IXdmNode aSheet;
    try
    {
      aSheet = JaxpSources.toXdm (aSource, aParserSettings, true);
    }
    catch (final TransformerException ex)
    {
      throw JaxpErrors.reportFatalConfiguration (m_aErrorListener,
                                                 new TransformerConfigurationException ("XTSE0010: The stylesheet cannot be read: " +
                                                                                        ex.getMessage (),
                                                                                        ex.getLocator (),
                                                                                        ex));
    }
    // A parsed stylesheet without document URI gets the base URI, as
    // XsltCompiler.compile (String, ...) does; a tree of the caller is not
    // changed
    if (!(aSource instanceof XdmSource) && sBaseUri != null && aSheet.isDocument () && aSheet.getDocumentUri () == null)
      aSheet.setDocumentUri (sBaseUri);
    final XsltCompileOptions aOptions = new XsltCompileOptions ().setBaseUri (sBaseUri)
                                                                 .setResolver (new JaxpResourceResolver (m_aURIResolver,
                                                                                                         m_sAccessExternalStylesheet,
                                                                                                         aParserSettings))
                                                                 .setLoadExternalEntities (m_bLoadExternalEntities)
                                                                 .setSchemaAware (m_bSchemaAware)
                                                                 .setBackwardsCompatibleClaim (m_bBackwardsCompatible)
                                                                 .setThreading (m_eThreading);
    final XsltExecutable aExecutable;
    try
    {
      aExecutable = XsltCompiler.compile (aSheet, aOptions);
    }
    catch (final XPathException ex)
    {
      throw JaxpErrors.reportFatalConfiguration (m_aErrorListener, JaxpErrors.toConfigurationException (ex));
    }
    return new PhoxtrotTemplates (aExecutable, _createSettings ());
  }

  /**
   * Compile a stylesheet ({@link #newTemplates(Source)}) and create a
   * transformer for it.
   */
  @Override
  @NonNull
  public PhoxtrotTransformer newTransformer (@NonNull final Source aSource) throws TransformerConfigurationException
  {
    return newTemplates (aSource).newTransformer ();
  }

  /**
   * @return A new identity transformer: it copies the source to the result
   *         (method xml by default; output properties can change it).
   */
  @Override
  @NonNull
  public PhoxtrotTransformer newTransformer ()
  {
    return new PhoxtrotTemplates (IdentityHolder.IDENTITY, _createSettings ()).newTransformer ();
  }

  /**
   * @return The value of a pseudo attribute of an <code>xml-stylesheet</code>
   *         processing instruction, <code>null</code> if it is absent.
   */
  @Nullable
  private static String _getPseudoAttribute (@NonNull final String sData, @NonNull final String sName)
  {
    final Matcher aMatcher = PSEUDO_ATTR.matcher (sData);
    while (aMatcher.find ())
      if (aMatcher.group (1).equals (sName))
        return aMatcher.group (2) != null ? aMatcher.group (2) : aMatcher.group (3);
    return null;
  }

  /**
   * @return <code>true</code> for the media types of XSLT stylesheets in an
   *         <code>xml-stylesheet</code> processing instruction.
   */
  private static boolean _isXsltType (@Nullable final String sType)
  {
    return "text/xsl".equals (sType) ||
           "text/xml".equals (sType) ||
           "application/xml".equals (sType) ||
           "application/xslt+xml".equals (sType);
  }

  /**
   * Find the stylesheets of the <code>xml-stylesheet</code> processing
   * instructions before the document element that have an XSLT type and match
   * the criteria given (media, title; a non-alternate one if no title is
   * given). One match is returned as a stream source of its href resolved
   * against the system ID of the document (through the URI resolver first);
   * several are combined into a stylesheet that imports them in order.
   *
   * @return The stylesheet source, or <code>null</code> if none matches.
   * @throws TransformerConfigurationException
   *         if the source cannot be read
   */
  @Override
  @Nullable
  public Source getAssociatedStylesheet (@NonNull final Source aSource,
                                         @Nullable final String sMedia,
                                         @Nullable final String sTitle,
                                         @Nullable final String sCharset) throws TransformerConfigurationException
  {
    final IXdmNode aDoc;
    try
    {
      // Only the processing instructions are read
      aDoc = JaxpSources.toXdm (aSource, _createParserSettings (), false).getRoot ();
    }
    catch (final TransformerException ex)
    {
      throw new TransformerConfigurationException ("The source cannot be read: " + ex.getMessage (), ex);
    }
    final String sBase = aDoc.getDocumentUri () != null ? aDoc.getDocumentUri () : JaxpSources.getSystemId (aSource);
    final ICommonsList <String> aHrefs = new CommonsArrayList <> ();
    for (int i = 0; i < aDoc.getChildCount (); ++i)
    {
      final IXdmNode aChild = aDoc.getChildAtIndex (i);
      if (aChild.isElement ())
        break;
      if (aChild.getKind () != ENodeKind.PROCESSING_INSTRUCTION || !"xml-stylesheet".equals (aChild.getName ().localName ()))
        continue;
      final String sData = aChild.getValue ();
      final String sHref = _getPseudoAttribute (sData, "href");
      if (sHref == null || !_isXsltType (_getPseudoAttribute (sData, "type")))
        continue;
      if (sMedia != null && !sMedia.equals (_getPseudoAttribute (sData, "media")))
        continue;
      if (sTitle != null ? !sTitle.equals (_getPseudoAttribute (sData, "title"))
                         : "yes".equals (_getPseudoAttribute (sData, "alternate")))
        continue;
      try
      {
        aHrefs.add (sBase != null && FnUri.isAbsoluteUri (sBase) ? FnUri.resolveUri (sHref, sBase) : sHref);
      }
      catch (final XPathException ex)
      {
        throw new TransformerConfigurationException ("Invalid xml-stylesheet href '" + sHref + "': " + ex.getMessage (), ex);
      }
    }
    if (aHrefs.isEmpty ())
      return null;
    if (aHrefs.size () == 1)
    {
      final String sHref = aHrefs.getFirstOrNull ();
      if (m_aURIResolver != null)
      {
        try
        {
          final Source ret = m_aURIResolver.resolve (sHref, sBase);
          if (ret != null)
            return ret;
        }
        catch (final TransformerException ex)
        {
          throw new TransformerConfigurationException (ex.getMessage (), ex);
        }
      }
      return new StreamSource (sHref);
    }
    final StringBuilder aSB = new StringBuilder ("<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>");
    for (final String sHref : aHrefs)
      aSB.append ("<xsl:import href='").append (sHref.replace ("&", "&amp;").replace ("'", "&apos;").replace ("<", "&lt;")).append ("'/>");
    aSB.append ("</xsl:stylesheet>");
    final StreamSource ret = new StreamSource (new NonBlockingStringReader (aSB.toString ()));
    ret.setSystemId (sBase);
    return ret;
  }

  @Override
  public void setURIResolver (@Nullable final URIResolver aResolver)
  {
    m_aURIResolver = aResolver;
  }

  @Override
  @Nullable
  public URIResolver getURIResolver ()
  {
    return m_aURIResolver;
  }

  /**
   * @return <code>true</code> for a source or result feature this factory
   *         supports.
   */
  private static boolean _isFixedFeature (@NonNull final String sName)
  {
    return StreamSource.FEATURE.equals (sName) ||
           StreamResult.FEATURE.equals (sName) ||
           DOMSource.FEATURE.equals (sName) ||
           DOMResult.FEATURE.equals (sName) ||
           SAXSource.FEATURE.equals (sName) ||
           SAXResult.FEATURE.equals (sName) ||
           XdmSource.FEATURE.equals (sName);
  }

  /**
   * Set a feature: {@link XMLConstants#FEATURE_SECURE_PROCESSING} (see the
   * class description); the supported source and result features may only be
   * set to <code>true</code>.
   *
   * @throws TransformerConfigurationException
   *         for any other feature, or a source or result feature set to
   *         <code>false</code>
   */
  @Override
  public void setFeature (@NonNull final String sName, final boolean bValue) throws TransformerConfigurationException
  {
    if (sName == null)
      throw new NullPointerException ("Feature name");
    if (XMLConstants.FEATURE_SECURE_PROCESSING.equals (sName))
    {
      m_bSecureProcessing = bValue;
      if (bValue)
      {
        m_sAccessExternalStylesheet = "";
        m_sAccessExternalDtd = "";
      }
      return;
    }
    if (_isFixedFeature (sName) && bValue)
      return;
    throw new TransformerConfigurationException ("The feature '" + sName + "' cannot be set to " + bValue);
  }

  /**
   * @return The secure processing setting, <code>true</code> for the
   *         supported source and result features, <code>false</code> for
   *         every other feature.
   */
  @Override
  public boolean getFeature (@NonNull final String sName)
  {
    if (sName == null)
      throw new NullPointerException ("Feature name");
    if (XMLConstants.FEATURE_SECURE_PROCESSING.equals (sName))
      return m_bSecureProcessing;
    return _isFixedFeature (sName);
  }

  /**
   * @return The value as a boolean: a {@link Boolean}, or the string "true"
   *         or "false".
   * @throws IllegalArgumentException
   *         for anything else
   */
  private static boolean _toBoolean (@NonNull final String sName, @Nullable final Object aValue)
  {
    if (aValue instanceof final Boolean aBool)
      return aBool.booleanValue ();
    if ("true".equals (aValue) || "false".equals (aValue))
      return "true".equals (aValue);
    throw new IllegalArgumentException ("The attribute '" + sName + "' needs a boolean value, not " + aValue);
  }

  /**
   * @return The value as a threading mode: an {@link EXsltThreading}, or the
   *         exact name of one of its constants (e.g. "CALLING_THREAD").
   * @throws IllegalArgumentException
   *         for anything else, <code>null</code> included
   */
  @NonNull
  private static EXsltThreading _toThreading (@NonNull final String sName, @Nullable final Object aValue)
  {
    if (aValue instanceof final EXsltThreading eThreading)
      return eThreading;
    if (aValue instanceof final String sValue)
      for (final EXsltThreading eThreading : EXsltThreading.values ())
        if (eThreading.name ().equals (sValue))
          return eThreading;
    throw new IllegalArgumentException ("The attribute '" + sName + "' needs an EXsltThreading value, not " + aValue);
  }

  /**
   * Set an attribute (see the class description for the names).
   *
   * @throws IllegalArgumentException
   *         for an unknown name or a value of the wrong type
   */
  @Override
  public void setAttribute (@NonNull final String sName, @Nullable final Object aValue)
  {
    switch (sName)
    {
      case XMLConstants.ACCESS_EXTERNAL_STYLESHEET:
      case XMLConstants.ACCESS_EXTERNAL_DTD:
      {
        if (!(aValue instanceof final String sValue))
          throw new IllegalArgumentException ("The attribute '" + sName + "' needs a string value");
        if (XMLConstants.ACCESS_EXTERNAL_STYLESHEET.equals (sName))
          m_sAccessExternalStylesheet = sValue;
        else
          m_sAccessExternalDtd = sValue;
        break;
      }
      case ATTR_LOAD_EXTERNAL_ENTITIES:
        m_bLoadExternalEntities = _toBoolean (sName, aValue);
        break;
      case ATTR_ENTITY_RESOLVER:
      {
        if (aValue != null && !(aValue instanceof EntityResolver))
          throw new IllegalArgumentException ("The attribute '" + sName + "' needs an EntityResolver");
        m_aEntityResolver = (EntityResolver) aValue;
        break;
      }
      case ATTR_SCHEMA_AWARE:
        m_bSchemaAware = _toBoolean (sName, aValue);
        break;
      case ATTR_BACKWARDS_COMPATIBLE:
        m_bBackwardsCompatible = _toBoolean (sName, aValue);
        break;
      case ATTR_MESSAGE_LISTENER:
      {
        if (aValue != null && !(aValue instanceof IXsltMessageListener))
          throw new IllegalArgumentException ("The attribute '" + sName + "' needs an IXsltMessageListener");
        m_aMessageListener = (IXsltMessageListener) aValue;
        break;
      }
      case ATTR_TRACE_LISTENER:
      {
        if (aValue != null && !(aValue instanceof IXsltTraceListener))
          throw new IllegalArgumentException ("The attribute '" + sName + "' needs an IXsltTraceListener");
        m_aTraceListener = (IXsltTraceListener) aValue;
        break;
      }
      case ATTR_MAX_STEPS:
      {
        if (aValue instanceof final Number aNumber)
          m_nMaxSteps = aNumber.longValue ();
        else
          if (aValue instanceof final String sValue)
          {
            try
            {
              m_nMaxSteps = Long.parseLong (sValue.trim ());
            }
            catch (final NumberFormatException ex)
            {
              throw new IllegalArgumentException ("The attribute '" + sName + "' needs a number, not '" + sValue + "'", ex);
            }
          }
          else
            throw new IllegalArgumentException ("The attribute '" + sName + "' needs a number");
        if (m_nMaxSteps < 0)
          throw new IllegalArgumentException ("The attribute '" + sName + "' may not be negative");
        break;
      }
      case ATTR_THREADING:
        m_eThreading = _toThreading (sName, aValue);
        break;
      default:
        throw new IllegalArgumentException ("The attribute '" + sName + "' is not supported");
    }
  }

  /**
   * @return The value of an attribute (see the class description for the
   *         names).
   * @throws IllegalArgumentException
   *         for an unknown name
   */
  @Override
  @Nullable
  public Object getAttribute (@NonNull final String sName)
  {
    return switch (sName)
    {
      case XMLConstants.ACCESS_EXTERNAL_STYLESHEET -> m_sAccessExternalStylesheet;
      case XMLConstants.ACCESS_EXTERNAL_DTD -> m_sAccessExternalDtd;
      case ATTR_LOAD_EXTERNAL_ENTITIES -> Boolean.valueOf (m_bLoadExternalEntities);
      case ATTR_ENTITY_RESOLVER -> m_aEntityResolver;
      case ATTR_SCHEMA_AWARE -> Boolean.valueOf (m_bSchemaAware);
      case ATTR_BACKWARDS_COMPATIBLE -> Boolean.valueOf (m_bBackwardsCompatible);
      case ATTR_MESSAGE_LISTENER -> m_aMessageListener;
      case ATTR_TRACE_LISTENER -> m_aTraceListener;
      case ATTR_MAX_STEPS -> Long.valueOf (m_nMaxSteps);
      case ATTR_THREADING -> m_eThreading;
      default -> throw new IllegalArgumentException ("The attribute '" + sName + "' is not supported");
    };
  }

  /**
   * @throws IllegalArgumentException
   *         if the listener is <code>null</code>
   */
  @Override
  public void setErrorListener (@NonNull final ErrorListener aListener)
  {
    if (aListener == null)
      throw new IllegalArgumentException ("The error listener may not be null");
    m_aErrorListener = aListener;
  }

  @Override
  @NonNull
  public ErrorListener getErrorListener ()
  {
    return m_aErrorListener;
  }
}
