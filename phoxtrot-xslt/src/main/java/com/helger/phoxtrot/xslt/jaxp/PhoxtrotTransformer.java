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
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.bridge.XdmToDom;
import com.helger.phoxtrot.xdm.bridge.XdmToSax;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.IXsltMessageListener;
import com.helger.phoxtrot.xslt.EXsltThreading;
import com.helger.phoxtrot.xslt.IXsltTraceListener;
import com.helger.phoxtrot.xslt.XsltMessage;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.XsltTransformResult;

/**
 * A JAXP transformer of a {@link PhoxtrotTemplates}: one transformation at a
 * time, not thread-safe (as JAXP says), reusable after
 * {@link #transform(Source, Result)} returns.
 * <ul>
 * <li>Sources: {@link javax.xml.transform.stream.StreamSource},
 * {@link javax.xml.transform.dom.DOMSource} (the DOM node is the global
 * context item, in a copy of its whole DOM tree),
 * {@link javax.xml.transform.sax.SAXSource} (with or without XML reader) and
 * {@link XdmSource}; the node becomes the global context item and the
 * initial match selection.</li>
 * <li>Results: {@link StreamResult} (writer, output stream, or a
 * <code>file:</code> system ID or file path; written while the result is
 * built where the output definition allows it, see
 * {@link com.helger.phoxtrot.xslt.XsltExecutable#transform(XsltTransformRequest, OutputStream)}),
 * {@link DOMResult} (into the given node, or a new document) and
 * {@link SAXResult} (the content handler, and the lexical handler if given or
 * implemented by the content handler). The system ID of the result is the
 * base output URI.</li>
 * <li>Messages: <code>xsl:message</code>, failed <code>xsl:assert</code> and
 * mode warnings go to the message listener if one is set
 * ({@link #setMessageListener(IXsltMessageListener)} or the factory
 * attribute {@link PhoxtrotTransformerFactory#ATTR_MESSAGE_LISTENER}),
 * otherwise to {@link ErrorListener#warning(TransformerException)} with the
 * message text and the location of the instruction; they are reported while
 * the transformation runs, on the thread that runs it. An exception the
 * error listener throws ends the transformation and is thrown by
 * {@link #transform(Source, Result)}.</li>
 * <li>Tracing: a trace listener ({@link #setTraceListener(IXsltTraceListener)}
 * or the factory attribute
 * {@link PhoxtrotTransformerFactory#ATTR_TRACE_LISTENER}) follows the
 * templates, functions and optionally instructions of each transformation,
 * on the thread that runs it.</li>
 * <li>Threads: a transformation runs on the thread that
 * {@link #setThreading(EXsltThreading)} or the factory attribute
 * {@link PhoxtrotTransformerFactory#ATTR_THREADING} chooses (by default a new
 * thread with a large stack per call). An interrupt of the calling thread
 * ends the transformation with FOER0000 in every mode, and a transformation
 * on another thread stops at its next check instead of going on in the
 * background.</li>
 * <li>Errors: a dynamic error is reported to
 * {@link ErrorListener#fatalError(TransformerException)} and thrown as a
 * {@link TransformerException} whose message starts with the error code, with
 * the stylesheet location and the phoxtrot error as cause.</li>
 * <li>Secondary results (<code>xsl:result-document</code> with an href) are
 * not written anywhere; they are available from
 * {@link #getLastResult()}.</li>
 * </ul>
 *
 * @author Philip Helger
 */
@NotThreadSafe
public class PhoxtrotTransformer extends Transformer
{
  /** A parameter as set and as converted to XDM. */
  private record Param (@NonNull Object value, @NonNull XdmSequence converted)
  {}

  /** Carries an exception of the error listener out of the transformation. */
  private static final class ListenerAbortException extends RuntimeException
  {
    private final TransformerException m_aCause;

    ListenerAbortException (@NonNull final TransformerException aCause)
    {
      super (aCause);
      m_aCause = aCause;
    }

    @NonNull
    TransformerException getTransformerException ()
    {
      return m_aCause;
    }
  }

  /** Passes the messages of a transformation to the error listener. */
  private static final class ErrorListenerMessages implements IXsltMessageListener
  {
    private final ErrorListener m_aErrorListener;

    ErrorListenerMessages (@NonNull final ErrorListener aErrorListener)
    {
      m_aErrorListener = aErrorListener;
    }

    private void _warn (@NonNull final XsltMessage aMessage)
    {
      try
      {
        m_aErrorListener.warning (new TransformerException (aMessage.text (),
                                                            JaxpErrors.Location.of (aMessage.moduleUri (),
                                                                                    aMessage.line (),
                                                                                    aMessage.column ())));
      }
      catch (final TransformerException ex)
      {
        throw new ListenerAbortException (ex);
      }
    }

    public void onMessage (@NonNull final XsltMessage aMessage)
    {
      _warn (aMessage);
    }

    @Override
    public void onWarning (@NonNull final XsltMessage aWarning)
    {
      _warn (aWarning);
    }
  }

  private final PhoxtrotTemplates m_aTemplates;
  private final ICommonsOrderedMap <String, Param> m_aParams = new CommonsLinkedHashMap <> ();
  private final Properties m_aOutputProperties = new Properties ();
  private URIResolver m_aURIResolver;
  private ErrorListener m_aErrorListener;
  private IXsltMessageListener m_aMessageListener;
  private IXsltTraceListener m_aTraceListener;
  private EXsltThreading m_eThreading;
  private XsltTransformResult m_aLastResult;

  /**
   * @param aTemplates
   *        The compiled stylesheet with the factory settings.
   */
  PhoxtrotTransformer (@NonNull final PhoxtrotTemplates aTemplates)
  {
    ValueEnforcer.notNull (aTemplates, "Templates");
    m_aTemplates = aTemplates;
    reset ();
  }

  /** @return The templates this transformer belongs to. Never <code>null</code>. */
  @NonNull
  public final PhoxtrotTemplates getTemplates ()
  {
    return m_aTemplates;
  }

  /**
   * @return The phoxtrot result of the last successful transformation: its
   *         secondary results (<code>xsl:result-document</code>) and message
   *         texts; for a {@link DOMResult} or {@link SAXResult} also the
   *         principal result. <code>null</code> before the first one and
   *         after {@link #reset()}.
   */
  @Nullable
  public final XsltTransformResult getLastResult ()
  {
    return m_aLastResult;
  }

  /**
   * @return The message listener, <code>null</code> if messages go to the
   *         error listener.
   */
  @Nullable
  public final IXsltMessageListener getMessageListener ()
  {
    return m_aMessageListener;
  }

  /**
   * Set where <code>xsl:message</code>, failed <code>xsl:assert</code> and
   * mode warnings go during the transformation.
   *
   * @param aMessageListener
   *        The listener, called on the thread that runs the transformation;
   *        <code>null</code> to report them as warnings to the error
   *        listener.
   */
  public final void setMessageListener (@Nullable final IXsltMessageListener aMessageListener)
  {
    m_aMessageListener = aMessageListener;
  }

  /**
   * @return The trace listener, <code>null</code> for none.
   */
  @Nullable
  public final IXsltTraceListener getTraceListener ()
  {
    return m_aTraceListener;
  }

  /**
   * Set the listener that follows the templates, functions and optionally
   * instructions of each transformation (see
   * {@link XsltTransformRequest#setTraceListener(IXsltTraceListener)}).
   *
   * @param aTraceListener
   *        The listener, called on the thread that runs the transformation;
   *        <code>null</code> for none.
   */
  public final void setTraceListener (@Nullable final IXsltTraceListener aTraceListener)
  {
    m_aTraceListener = aTraceListener;
  }

  /**
   * @return The thread the transformations run on. Never <code>null</code>.
   */
  @NonNull
  public final EXsltThreading getThreading ()
  {
    return m_eThreading;
  }

  /**
   * Choose the thread the transformations of this transformer run on (see
   * {@link EXsltThreading} and
   * {@link XsltTransformRequest#setThreading(EXsltThreading)}).
   *
   * @param eThreading
   *        The threading mode. May not be <code>null</code>.
   */
  public final void setThreading (@NonNull final EXsltThreading eThreading)
  {
    ValueEnforcer.notNull (eThreading, "Threading");
    m_eThreading = eThreading;
  }

  /**
   * Build the request for one transformation: the source as global context
   * item, the parameters, the resolver, the message and trace listeners, the
   * interrupt hook, the step limit, the threading mode and the base output
   * URI.
   */
  @NonNull
  private XsltTransformRequest _createRequest (@NonNull final IXdmNode aSource,
                                               @Nullable final String sBaseOutputUri,
                                               @NonNull final BooleanSupplier aInterrupt)
  {
    final JaxpSettings aSettings = m_aTemplates.getSettings ();
    final XsltTransformRequest ret = new XsltTransformRequest ();
    ret.setGlobalContextItem (aSource);
    for (final var aEntry : m_aParams.entrySet ())
      ret.params ().put (JaxpValueConverter.parseName (aEntry.getKey ()), aEntry.getValue ().converted ());
    // Documents read while transforming need no node locations
    ret.setResolver (new JaxpResourceResolver (m_aURIResolver,
                                               aSettings.accessExternalStylesheet (),
                                               aSettings.loadExternalEntities ()));
    ret.setMessageListener (m_aMessageListener != null ? m_aMessageListener : new ErrorListenerMessages (m_aErrorListener));
    ret.setTraceListener (m_aTraceListener);
    ret.setInterrupt (aInterrupt);
    ret.setThreading (m_eThreading);
    if (aSettings.maxSteps () != 0)
      ret.setMaxSteps (aSettings.maxSteps ());
    if (sBaseOutputUri != null)
      ret.setBaseOutputUri (sBaseOutputUri);
    return ret;
  }

  /**
   * @return The system ID if it is an absolute URI (to be the base output
   *         URI), otherwise <code>null</code>.
   */
  @Nullable
  private static String _getAbsoluteUri (@Nullable final String sSystemId)
  {
    if (sSystemId == null)
      return null;
    try
    {
      return new URI (sSystemId).isAbsolute () ? sSystemId : null;
    }
    catch (final URISyntaxException ex)
    {
      return null;
    }
  }

  /**
   * @return The file a stream result names by its system ID: a
   *         <code>file:</code> URI or a path without scheme.
   * @throws TransformerException
   *         for any other system ID
   */
  @NonNull
  private static Path _getResultPath (@NonNull final String sSystemId) throws TransformerException
  {
    try
    {
      if (sSystemId.startsWith ("file:"))
        return Path.of (new URI (sSystemId));
      if (_getAbsoluteUri (sSystemId) == null)
        return Path.of (sSystemId);
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      throw new TransformerException ("The StreamResult system ID '" + sSystemId + "' is no valid file URI", ex);
    }
    throw new TransformerException ("A StreamResult can only be written to a file: system ID, not to '" + sSystemId + "'");
  }

  /**
   * Write the serialized principal result to a stream result: its writer,
   * else its output stream, else the file its system ID names (created or
   * replaced, and closed afterwards). The output properties set on this
   * transformer are supplied as serialization parameters.
   */
  @NonNull
  private XsltTransformResult _transformToStream (@NonNull final XsltTransformRequest aRequest,
                                                  @NonNull final StreamResult aResult) throws TransformerException
  {
    aRequest.outputOverrides ().putAll (JaxpOutputProperties.toOverrides (m_aOutputProperties));
    final Writer aWriter = aResult.getWriter ();
    if (aWriter != null)
      return m_aTemplates.getExecutable ().transform (aRequest, aWriter);
    final OutputStream aOS = aResult.getOutputStream ();
    if (aOS != null)
      return m_aTemplates.getExecutable ().transform (aRequest, aOS);
    if (aResult.getSystemId () == null)
      throw new TransformerException ("The StreamResult has neither a writer, an output stream nor a system ID");
    final Path aPath = _getResultPath (aResult.getSystemId ());
    try (final OutputStream aFileOS = Files.newOutputStream (aPath))
    {
      return m_aTemplates.getExecutable ().transform (aRequest, aFileOS);
    }
    catch (final IOException ex)
    {
      throw new TransformerException ("Failed to write the result to '" + aPath + "': " + ex.getMessage (), ex);
    }
  }

  /**
   * @return The principal result tree.
   * @throws TransformerException
   *         if the principal result is no tree (build-tree="no")
   */
  @NonNull
  private static IXdmNode _getTree (@NonNull final XsltTransformResult aResult) throws TransformerException
  {
    if (aResult.principalTree () == null)
      throw new TransformerException ("The principal result is no tree (build-tree is \"no\" or the method is json or adaptive)");
    return aResult.principalTree ();
  }

  /**
   * Write the principal result tree into a DOM result: into its node (before
   * its next sibling), or into a new document that becomes its node.
   */
  @NonNull
  private XsltTransformResult _transformToDom (@NonNull final XsltTransformRequest aRequest, @NonNull final DOMResult aResult) throws TransformerException
  {
    final XsltTransformResult ret = m_aTemplates.getExecutable ().transform (aRequest);
    final IXdmNode aTree = _getTree (ret);
    try
    {
      final Node aNode = aResult.getNode ();
      if (aNode == null)
      {
        final Document aDoc = XdmToDom.toDocument (aTree);
        if (aResult.getSystemId () != null && aDoc.getDocumentURI () == null)
          aDoc.setDocumentURI (aResult.getSystemId ());
        aResult.setNode (aDoc);
      }
      else
        XdmToDom.appendTo (aTree, aNode, aResult.getNextSibling ());
    }
    catch (final DOMException | IllegalArgumentException ex)
    {
      throw new TransformerException ("The result cannot be written to the DOMResult: " + ex.getMessage (), ex);
    }
    return ret;
  }

  /**
   * Emit the principal result tree as SAX events to a SAX result.
   */
  @NonNull
  private XsltTransformResult _transformToSax (@NonNull final XsltTransformRequest aRequest, @NonNull final SAXResult aResult) throws TransformerException
  {
    if (aResult.getHandler () == null)
      throw new TransformerException ("The SAXResult has no content handler");
    final XsltTransformResult ret = m_aTemplates.getExecutable ().transform (aRequest);
    final IXdmNode aTree = _getTree (ret);
    LexicalHandler aLexical = aResult.getLexicalHandler ();
    if (aLexical == null && aResult.getHandler () instanceof final LexicalHandler aHandlerLexical)
      aLexical = aHandlerLexical;
    try
    {
      XdmToSax.emit (aTree, aResult.getHandler (), aLexical);
    }
    catch (final SAXException ex)
    {
      throw new TransformerException ("The SAXResult handler failed: " + ex.getMessage (), ex);
    }
    return ret;
  }

  /**
   * Transform the source into the result. See the class description for the
   * kinds of source and result. The transformation polls the interrupted
   * state of the calling thread; once the call ends with that thread
   * interrupted (for example with FOER0000 while it waited for a worker
   * thread), the hook stays <code>true</code>, so that a run left on a worker
   * thread stops even if the caller clears the flag afterwards.
   *
   * @throws TransformerException
   *         if the source cannot be read, the result is of an unsupported
   *         kind, a dynamic error occurs (reported to the error listener
   *         first) or the error listener throws
   */
  @Override
  public void transform (@NonNull final Source aSource, @NonNull final Result aResult) throws TransformerException
  {
    ValueEnforcer.notNull (aSource, "Source");
    ValueEnforcer.notNull (aResult, "Result");
    m_aLastResult = null;
    final Thread aCaller = Thread.currentThread ();
    final AtomicBoolean aStopped = new AtomicBoolean (false);
    try
    {
      final IXdmNode aNode = JaxpSources.toXdm (aSource, m_aTemplates.getSettings ().loadExternalEntities (), false);
      final XsltTransformRequest aRequest = _createRequest (aNode,
                                                            _getAbsoluteUri (aResult.getSystemId ()),
                                                            () -> aStopped.get () || aCaller.isInterrupted ());
      if (aResult instanceof final StreamResult aStreamResult)
        m_aLastResult = _transformToStream (aRequest, aStreamResult);
      else
        if (aResult instanceof final DOMResult aDOMResult)
          m_aLastResult = _transformToDom (aRequest, aDOMResult);
        else
          if (aResult instanceof final SAXResult aSAXResult)
            m_aLastResult = _transformToSax (aRequest, aSAXResult);
          else
            throw new TransformerException ("Unsupported Result " + aResult.getClass ().getName ());
    }
    catch (final ListenerAbortException ex)
    {
      throw ex.getTransformerException ();
    }
    catch (final XPathException ex)
    {
      throw JaxpErrors.reportFatal (m_aErrorListener, JaxpErrors.toTransformerException (ex));
    }
    catch (final TransformerException ex)
    {
      throw JaxpErrors.reportFatal (m_aErrorListener, ex);
    }
    finally
    {
      // A run left behind on a worker thread stops at its next check
      if (aCaller.isInterrupted ())
        aStopped.set (true);
    }
  }

  /**
   * @return The key of a parameter in the map: <code>{uri}local</code>, or
   *         the local name in no namespace, so that all spellings of a name
   *         meet.
   */
  @NonNull
  private static String _getKey (@NonNull final XdmQName aName)
  {
    return aName.hasNamespace () ? "{" + aName.namespaceUri () + "}" + aName.localName () : aName.localName ();
  }

  /**
   * Set a stylesheet parameter. The value is converted to XDM at once
   * ({@link JaxpValueConverter#toXdm(Object)}); a W3C DOM node is copied, so
   * later changes of the DOM are not seen.
   *
   * @param sName
   *        The parameter name: <code>{uri}local</code>,
   *        <code>Q{uri}local</code> or a local name.
   * @param aValue
   *        The value.
   * @throws NullPointerException
   *         if the name is <code>null</code>
   * @throws IllegalArgumentException
   *         if the value is <code>null</code>, the name is invalid or the
   *         value cannot be converted
   */
  @Override
  public void setParameter (@NonNull final String sName, @NonNull final Object aValue)
  {
    if (sName == null)
      throw new NullPointerException ("Parameter name");
    if (aValue == null)
      throw new IllegalArgumentException ("The value of parameter '" + sName + "' is null");
    m_aParams.put (_getKey (JaxpValueConverter.parseName (sName)), new Param (aValue, JaxpValueConverter.toXdm (aValue)));
  }

  /**
   * @return The value as it was set, <code>null</code> if the parameter is
   *         not set.
   */
  @Override
  @Nullable
  public Object getParameter (@NonNull final String sName)
  {
    if (sName == null)
      throw new NullPointerException ("Parameter name");
    final XdmQName aName;
    try
    {
      aName = JaxpValueConverter.parseName (sName);
    }
    catch (final IllegalArgumentException ex)
    {
      return null;
    }
    final Param aParam = m_aParams.get (_getKey (aName));
    return aParam == null ? null : aParam.value ();
  }

  @Override
  public void clearParameters ()
  {
    m_aParams.clear ();
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
   * Replace the output properties set on this transformer.
   *
   * @param aProperties
   *        The properties (defaults included), or <code>null</code> to remove
   *        all that were set.
   * @throws IllegalArgumentException
   *         for an unsupported property name (see
   *         {@link #setOutputProperty(String, String)})
   */
  @Override
  public void setOutputProperties (@Nullable final Properties aProperties)
  {
    if (aProperties == null)
    {
      m_aOutputProperties.clear ();
      return;
    }
    for (final String sName : aProperties.stringPropertyNames ())
      JaxpOutputProperties.checkName (sName);
    m_aOutputProperties.clear ();
    for (final String sName : aProperties.stringPropertyNames ())
      m_aOutputProperties.setProperty (sName, aProperties.getProperty (sName));
  }

  /**
   * @return A copy of the output properties: those set on this transformer,
   *         with the output properties of the stylesheet (and their defaults)
   *         as defaults.
   */
  @Override
  @NonNull
  public Properties getOutputProperties ()
  {
    final Properties ret = new Properties (m_aTemplates.getOutputProperties ());
    ret.putAll (m_aOutputProperties);
    return ret;
  }

  /**
   * Set an output property for a {@link StreamResult}; a {@link DOMResult}
   * and a {@link SAXResult} get the tree and ignore them. The value overrides
   * the stylesheet's <code>xsl:output</code>.
   *
   * @param sName
   *        A name of {@link javax.xml.transform.OutputKeys}, another
   *        serialization attribute of XSLT 3.0 <code>xsl:output</code>
   *        except <code>use-character-maps</code>, or a name in a namespace
   *        (<code>{uri}local</code>, kept without effect).
   * @param sValue
   *        The value, as for the attribute of <code>xsl:output</code>; name
   *        lists in the form <code>{uri}local</code>.
   * @throws IllegalArgumentException
   *         for an unsupported name or a <code>null</code> value
   */
  @Override
  public void setOutputProperty (@NonNull final String sName, @NonNull final String sValue)
  {
    JaxpOutputProperties.checkName (sName);
    if (sValue == null)
      throw new IllegalArgumentException ("The value of output property '" + sName + "' is null");
    m_aOutputProperties.setProperty (sName, sValue);
  }

  /**
   * @return The value set on this transformer, else the stylesheet's, else
   *         the engine default; <code>null</code> if none of them has one.
   * @throws IllegalArgumentException
   *         for an unsupported name
   */
  @Override
  @Nullable
  public String getOutputProperty (@NonNull final String sName)
  {
    JaxpOutputProperties.checkName (sName);
    final String ret = m_aOutputProperties.getProperty (sName);
    return ret != null ? ret : m_aTemplates.getOutputProperties ().getProperty (sName);
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

  /**
   * Return to the state after {@link PhoxtrotTemplates#newTransformer()}: no
   * parameters and output properties, the URI resolver, message listener,
   * trace listener and threading mode of the factory, the default error listener (which logs warnings and
   * throws fatal errors), no last result.
   */
  @Override
  public void reset ()
  {
    m_aParams.clear ();
    m_aOutputProperties.clear ();
    m_aURIResolver = m_aTemplates.getSettings ().uriResolver ();
    m_aErrorListener = new JaxpErrors.LoggingErrorListener ();
    m_aMessageListener = m_aTemplates.getSettings ().messageListener ();
    m_aTraceListener = m_aTemplates.getSettings ().traceListener ();
    m_eThreading = m_aTemplates.getSettings ().threading ();
    m_aLastResult = null;
  }
}
