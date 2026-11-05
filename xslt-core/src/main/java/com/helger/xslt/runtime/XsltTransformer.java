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
package com.helger.xslt.runtime;

import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.XPathException;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.serialize.XdmSerializer;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmStreamReader;
import com.helger.xslt.XsltException;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;
import com.helger.xslt.compile.IOutputCustomizer;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.ext.accumulator.AccumulatorExtension;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.Mode;
import com.helger.xslt.model.OutputDefinition;
import com.helger.xslt.model.Template;
import com.helger.xslt.model.TemplateRule;
import com.helger.xslt.model.UserFunction;
import com.helger.xslt.pkg.PackageComponent;
import com.helger.xslt.stream.StreamConsumer;
import com.helger.xslt.stream.StreamPlanner;
import com.helger.xslt.stream.StreamedRun;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * Runs one transformation (XSLT 3.0 section 2.3): sets up the engine, invokes
 * the initial function, initial template or initial mode, builds the
 * principal result tree and serializes it.
 *
 * @author Philip Helger
 */
public final class XsltTransformer
{
  private static final XdmQName INITIAL_TEMPLATE = new XdmQName (XsltNames.NS_XSL, "initial-template");
  private static final String NS_XHTML = "http://www.w3.org/1999/xhtml";

  private final CompiledStylesheet m_aSheet;
  private final XsltTransformRequest m_aRequest;

  public XsltTransformer (@NonNull final CompiledStylesheet aSheet, @NonNull final XsltTransformRequest aRequest)
  {
    m_aSheet = aSheet;
    m_aRequest = aRequest;
  }

  @NonNull
  private static ICommonsList <ParamValue> _getTemplateParams (@NonNull final XsltTransformRequest aRequest)
  {
    final ICommonsList <ParamValue> ret = new CommonsArrayList <> ();
    for (final XsltTransformRequest.TemplateParam aParam : aRequest.templateParams ())
      ret.add (new ParamValue (aParam.name (), aParam.value (), aParam.tunnel ()));
    return ret;
  }

  /**
   * In an xsl:package only public or final named templates can be the initial
   * template.
   */
  private boolean _isPublic (@NonNull final Template aTemplate)
  {
    if (!m_aSheet.isPackage ())
      return true;
    // A template of a used package is an entry point only if the principal
    // package accepted it explicitly
    if (m_aSheet.isMultiPackage () &&
        !m_aSheet.getPackages ().isInPrincipal (aTemplate.getElement ()) &&
        !m_aSheet.getPackages ().isAcceptedExplicitly (PackageComponent.getSymbolicName ("template", aTemplate.getName (), 0)))
      return false;
    // An explicit name in xsl:expose takes precedence over the declared
    // visibility (XSLT 3.0 section 3.5.3.1)
    if (m_aSheet.privateTemplates ().contains (aTemplate.getName ()))
      return false;
    final String sVis = aTemplate.getElement ().getAttr ("visibility");
    if (sVis != null)
      return "public".equals (sVis.trim ()) || "final".equals (sVis.trim ());
    return m_aSheet.exposedTemplates ().contains (null) || m_aSheet.exposedTemplates ().contains (aTemplate.getName ());
  }

  @NonNull
  private static String _detectMethod (@Nullable final IXdmNode aTree)
  {
    if (aTree != null)
      for (int i = 0; i < aTree.getChildCount (); ++i)
      {
        final IXdmNode aChild = aTree.getChildAtIndex (i);
        if (aChild.isElement ())
        {
          final XdmQName aName = aChild.getName ();
          if ("html".equals (aName.localName ().toLowerCase (Locale.ROOT)))
          {
            if (!aName.hasNamespace ())
              return SerializationParams.METHOD_HTML;
            if (NS_XHTML.equals (aName.namespaceUri ()))
              return SerializationParams.METHOD_XHTML;
          }
          return SerializationParams.METHOD_XML;
        }
        if (aChild.isText () && !XsltNames.isWhitespaceOnly (aChild.getValue ()))
          return SerializationParams.METHOD_XML;
      }
    return SerializationParams.METHOD_XML;
  }

  /**
   * @return The node of the stripped tree that corresponds to a node of the
   *         original tree, or <code>null</code> if the node itself was
   *         stripped. Stripping only removes whitespace-only text nodes, whose
   *         siblings are never text nodes: a kept one faces a text node in the
   *         copy, a removed one does not.
   */
  @Nullable
  private static IXdmNode _mapToStripped (@NonNull final IXdmNode aRoot,
                                         @NonNull final IXdmNode aStrippedRoot,
                                         @NonNull final IXdmNode aNode)
  {
    final ICommonsList <IXdmNode> aPath = new CommonsArrayList <> ();
    for (IXdmNode aCur = aNode; aCur != null && aCur != aRoot; aCur = aCur.getParent ())
      aPath.add (aCur);
    IXdmNode aOrig = aRoot;
    IXdmNode aCopy = aStrippedRoot;
    for (int nLevel = aPath.size () - 1; nLevel >= 0; --nLevel)
    {
      final IXdmNode aWanted = aPath.get (nLevel);
      IXdmNode aFound = null;
      if (aWanted.isAttribute ())
        aFound = aCopy.getAttribute (aWanted.getName ().namespaceUri (), aWanted.getName ().localName ());
      else
        if (aWanted.getKind () == ENodeKind.NAMESPACE)
        {
          for (final IXdmNode aNS : aCopy.getNamespaceNodes ())
            if (Objects.equals (aNS.getName (), aWanted.getName ()))
              aFound = aNS;
        }
        else
        {
          int nCopyIdx = 0;
          for (int i = 0; i < aOrig.getChildCount (); ++i)
          {
            final IXdmNode aChild = aOrig.getChildAtIndex (i);
            final boolean bKept = !aChild.isText () ||
                                  nCopyIdx < aCopy.getChildCount () && aCopy.getChildAtIndex (nCopyIdx).isText ();
            if (aChild == aWanted)
            {
              if (bKept && nCopyIdx < aCopy.getChildCount ())
                aFound = aCopy.getChildAtIndex (nCopyIdx);
              break;
            }
            if (bKept)
              nCopyIdx++;
          }
        }
      if (aFound == null)
        return null;
      aOrig = aWanted;
      aCopy = aFound;
    }
    return aCopy;
  }

  @Nullable
  private static IXdmNode _toStripped (@NonNull final Engine aEngine,
                                      @NonNull final ICommonsMap <IXdmNode, IXdmNode> aStrippedRoots,
                                      @NonNull final IXdmNode aNode)
  {
    final IXdmNode aRoot = aNode.getRoot ();
    if (!aRoot.isDocument ())
      return aNode;
    final IXdmNode aStripped = aStrippedRoots.computeIfAbsent (aRoot, aEngine::stripWhitespace);
    if (aStripped == aRoot)
      return aNode;
    return _mapToStripped (aRoot, aStripped, aNode);
  }

  /**
   * Set up the global context item of a new engine as a transformation does:
   * whitespace stripping of its tree, the checks of xsl:global-context-item,
   * the registration as source document and the check of the global
   * parameters.
   *
   * @param aEngine
   *        The new engine.
   * @param aStrippedRoots
   *        Original root to stripped root, filled here and reused for the
   *        initial match selection.
   * @return The global context item to use (after stripping), or
   *         <code>null</code> if there is none.
   * @throws XsltException
   *         XTDE3086, XTTE0590, or an error of a global parameter
   */
  @Nullable
  private static IXdmItem _initGlobalContextItem (@NonNull final Engine aEngine,
                                                  @NonNull final ICommonsMap <IXdmNode, IXdmNode> aStrippedRoots,
                                                  @Nullable final IXdmItem aSuppliedItem)
  {
    // Whitespace stripping applies to the trees of the global context item
    // and of the initial match selection; a node that is stripped itself is
    // gone (strip-space-023, mode-1802)
    IXdmItem aGlobalItem = aSuppliedItem;
    if (aGlobalItem instanceof final IXdmNode aNode)
      aGlobalItem = _toStripped (aEngine, aStrippedRoots, aNode);
    final CompiledStylesheet.GlobalContextItem aGCI = aEngine.getStylesheet ().getGlobalContextItem ();
    if (aGCI != null)
    {
      if ("required".equals (aGCI.use ()) && aGlobalItem == null)
        throw new XsltException ("XTDE3086", "The stylesheet requires a global context item", null, 0, 0);
      if ("absent".equals (aGCI.use ()))
        aGlobalItem = null;
      if (aGlobalItem != null && aGCI.as () != null && !SequenceTypeMatcher.matches (XdmSequence.of (aGlobalItem), aGCI.as ()))
        throw new XsltException ("XTTE0590", "The global context item does not match the declared type", null, 0, 0);
    }
    aEngine.setGlobalContextItem (aGlobalItem);
    if (aGlobalItem instanceof final IXdmNode aNode)
      aEngine.registerSourceDocument (aNode);
    aEngine.checkGlobalParams ();
    return aGlobalItem;
  }

  @Nullable
  public static IXdmItem initGlobalContextItem (@NonNull final Engine aEngine,
                                                @NonNull final ICommonsMap <IXdmNode, IXdmNode> aStrippedRoots)
  {
    return _initGlobalContextItem (aEngine, aStrippedRoots, aEngine.getRequest ().getGlobalContextItem ());
  }

  /** @return The name of the initial mode of the request. */
  @NonNull
  private XdmQName _getInitialModeName ()
  {
    final XdmQName aModeName = m_aRequest.getInitialMode ();
    if (aModeName != null && !aModeName.hasNamespace () && "#unnamed".equals (aModeName.localName ()))
      return Mode.UNNAMED;
    if (aModeName == null || !aModeName.hasNamespace () && "#default".equals (aModeName.localName ()))
      return m_aSheet.getDefaultModeName ();
    return aModeName;
  }

  /**
   * @return The initial mode of the request.
   * @throws XsltException
   *         XTDE0045 if it does not exist or is not eligible
   */
  @NonNull
  private Mode _resolveInitialMode ()
  {
    final XdmQName aModeName = _getInitialModeName ();
    final XdmQName aRequested = m_aRequest.getInitialMode ();
    final boolean bExplicitMode = aRequested != null &&
                                  !(!aRequested.hasNamespace () && "#default".equals (aRequested.localName ()));
    final Mode aMode = m_aSheet.getMode (aModeName);
    if (aMode == null)
      throw new XsltException ("XTDE0045", "The initial mode " + aModeName.getEQName () + " does not exist", null, 0, 0);
    // A named initial mode must be declared, by xsl:mode or by the mode
    // attribute of a template rule (not mode="#all", WG bug 3690); a
    // reference from xsl:apply-templates only does not count
    if (bExplicitMode &&
        !aMode.isUnnamed () &&
        !aModeName.equals (m_aSheet.getDefaultModeName ()) &&
        aMode.getDeclaration () == null &&
        aMode.rules ().containsNone (x -> !x.getTemplate ().isAllModes ()))
      throw new XsltException ("XTDE0045", "The initial mode " + aModeName.getEQName () + " is not declared", null, 0, 0);
    final String sVis = aMode.getVisibility ();
    if (bExplicitMode &&
        !aMode.isUnnamed () &&
        !aModeName.equals (m_aSheet.getDefaultModeName ()) &&
        (m_aSheet.privateModes ().contains (aModeName) ||
         "private".equals (sVis) ||
         "abstract".equals (sVis) ||
         sVis == null &&
                         m_aSheet.isPackage () &&
                         aMode.getDeclaration () != null &&
                         !m_aSheet.exposedModes ().contains (aModeName)))
      throw new XsltException ("XTDE0045", "The initial mode " + aModeName.getEQName () + " is not public", null, 0, 0);
    return aMode;
  }

  /** @return The document read in full; stripped, as for fn:doc. */
  @NonNull
  private static IXdmNode _readSource (@NonNull final Engine aEngine, @NonNull final String sUri)
  {
    final IXdmNode ret = aEngine.getDocument (aEngine.createRootContext (), sUri);
    if (ret == null)
      throw new XPathException ("FODC0002", "The source '" + sUri + "' identifies no node");
    return ret;
  }

  @NonNull
  private static IXdmNode _readAll (@NonNull final XdmStreamReader aReader)
  {
    try
    {
      return aReader.readAll ();
    }
    catch (final XdmParseException ex)
    {
      throw new XPathException ("FODC0002", "The source cannot be read: " + ex.getMessage ());
    }
    finally
    {
      _close (aReader);
    }
  }

  private static void _close (@NonNull final XdmStreamReader aReader)
  {
    try
    {
      aReader.close ();
    }
    catch (final XdmParseException ex)
    {
      // Everything needed was read
    }
  }

  /**
   * @return The run if the template rule for the document node has the shape
   *         the dispatcher runs, else <code>null</code> (the reader is still
   *         open and nothing was read).
   */
  @Nullable
  private StreamedRun _planStreamedRun (@NonNull final Engine aEngine,
                                        @NonNull final Mode aMode,
                                        @NonNull final XdmStreamReader aReader)
  {
    final XsltContext aCtx = aEngine.createRootContext ();
    final TemplateRule aRule = aEngine.findRule (aMode,
                                                 aReader.getDocument (),
                                                 aCtx.withState (aCtx.getState ().withCurrentRule (null, aMode)));
    if (aRule == null)
      return null;
    final XslElement aConsumer = StreamPlanner.findConsumer (aRule.getTemplate ().getElement ());
    if (aConsumer == null || !StreamConsumer.isConsumer (m_aSheet, aConsumer))
      return null;
    return new StreamedRun (aReader, aConsumer);
  }

  private void _applyStreamed (@NonNull final Engine aEngine,
                               @NonNull final StreamedRun aRun,
                               @NonNull final XsltContext aCtx,
                               @NonNull final Mode aMode,
                               @NonNull final IXsltOutput aOut)
  {
    aRun.push (aEngine);
    try
    {
      aRun.setAccumulators (AccumulatorExtension.startStreamed (aCtx, aRun.getReader ().getDocument (), aMode));
      aEngine.applyTemplates (aCtx.withState (aCtx.getState ().withCurrentRule (null, aMode)),
                              XdmSequence.of (aRun.getReader ().getDocument ()),
                              aMode,
                              _getTemplateParams (m_aRequest),
                              aOut);
      if (!aRun.isDriven ())
        throw new IllegalStateException ("The consumer of the streamed source was not executed");
    }
    finally
    {
      aRun.pop (aEngine);
      _close (aRun.getReader ());
    }
  }

  @NonNull
  private XsltTransformResult _run ()
  {
    final Engine aEngine = new Engine (m_aSheet, m_aRequest);
    final ICommonsMap <IXdmNode, IXdmNode> aStrippedRoots = new CommonsHashMap <> ();
    // A principal source to read as a stream: the initial match selection
    final String sStreamedUri = m_aRequest.getStreamedSourceUri ();
    final boolean bInitialMode = m_aRequest.getInitialFunction () == null && m_aRequest.getInitialTemplate () == null;
    Mode aStreamableMode = null;
    if (sStreamedUri != null && bInitialMode && m_aSheet.getStreamVerdicts () != null)
    {
      final Mode aMode = _resolveInitialMode ();
      if (m_aSheet.getStreamVerdicts ().isModeStreamable (aMode.getName ()) &&
          AccumulatorExtension.isStreamable (m_aSheet, aMode))
        aStreamableMode = aMode;
    }
    // A principal source declared streamed for a streamable initial mode gives
    // global variables no context item
    if (bInitialMode && m_aSheet.getStreamVerdicts () != null && (m_aRequest.isSourceStreamed () || sStreamedUri != null))
    {
      final Mode aMode = m_aSheet.getMode (_getInitialModeName ());
      if (aMode != null && aMode.isStreamable ())
        aEngine.setGlobalVariablesWithoutFocus (true);
    }
    IXdmNode aStreamedSource = null;
    final IXdmItem aGlobalItem;
    if (sStreamedUri != null && aStreamableMode == null)
    {
      // Read in full, and the global context item unless one is supplied
      aStreamedSource = _readSource (aEngine, sStreamedUri);
      final IXdmItem aSupplied = m_aRequest.getGlobalContextItem ();
      aGlobalItem = _initGlobalContextItem (aEngine, aStrippedRoots, aSupplied != null ? aSupplied : aStreamedSource);
    }
    else
      aGlobalItem = initGlobalContextItem (aEngine, aStrippedRoots);
    StreamedRun aStreamedRun = null;
    if (aStreamableMode != null)
    {
      final XdmStreamReader aReader = aEngine.openStreamedDocument (sStreamedUri);
      if (aReader == null)
        aStreamedSource = _readSource (aEngine, sStreamedUri);
      else
      {
        try
        {
          aStreamedRun = _planStreamedRun (aEngine, aStreamableMode, aReader);
        }
        catch (final RuntimeException ex)
        {
          _close (aReader);
          throw ex;
        }
        aStreamedSource = aStreamedRun != null ? aReader.getDocument () : _readAll (aReader);
      }
    }
    XdmSequence aMatchSelection = m_aRequest.getInitialMatchSelection ();
    if (aMatchSelection != null)
    {
      final XdmSequence.Builder aNew = new XdmSequence.Builder ();
      for (final IXdmItem aItem : aMatchSelection)
      {
        final IXdmItem aMapped = aItem instanceof final IXdmNode aNode ? _toStripped (aEngine, aStrippedRoots, aNode) : aItem;
        if (aMapped != null)
          aNew.add (aMapped);
      }
      aMatchSelection = aNew.build ();
    }
    if (aMatchSelection == null && aStreamedSource != null)
      aMatchSelection = XdmSequence.of (aStreamedSource);

    XsltContext aCtx = aEngine.createRootContext ();
    if (aGlobalItem != null)
      aCtx = aCtx.withFocus (aGlobalItem, 1, 1);
    final BufferedOutput aBuffer = new BufferedOutput ();
    aEngine.setPrincipalOutput (aBuffer);
    if (m_aRequest.getInitialFunction () != null)
    {
      final XdmQName aName = m_aRequest.getInitialFunction ();
      final int nArity = m_aRequest.functionArgs ().size ();
      UserFunction aFunc = null;
      for (final UserFunction aCand : m_aSheet.getFunctionsByName (aName))
        if (aCand.getArity () == nArity)
        {
          final String sVis = aCand.getVisibility ();
          // The default visibility of a function is private
          if ("public".equals (sVis) || "final".equals (sVis))
            aFunc = aCand;
        }
      if (aFunc == null)
        throw new XsltException ("XTDE0041", "There is no public function " + aName.getEQName () + "#" + nArity, null, 0, 0);
      final ICommonsList <XdmSequence> aArgs = new CommonsArrayList <> ();
      final var aDef = m_aSheet.getFunctionLibrary ().getFunction (aName, nArity);
      for (int i = 0; i < nArity; ++i)
        aArgs.add (SequenceTypeMatcher.convert (m_aRequest.functionArgs ().get (i),
                                                aDef.getParamTypes ().get (i),
                                                aCtx.getDynamicContext (),
                                                "XPTY0004"));
      aBuffer.appendAll (aFunc.invoke (aEngine.createRootContext (), aArgs));
    }
    else
      if (m_aRequest.getInitialTemplate () != null)
      {
        final Template aTemplate = m_aSheet.getNamedTemplate (m_aRequest.getInitialTemplate ());
        if (aTemplate == null || !_isPublic (aTemplate))
          throw new XsltException ("XTDE0040",
                                   "There is no template named " + m_aRequest.getInitialTemplate ().getEQName (),
                                   null,
                                   0,
                                   0);
        aEngine.callTemplate (aCtx, aTemplate, _getTemplateParams (m_aRequest), aBuffer);
      }
      else
      {
        if (aMatchSelection == null)
          aMatchSelection = aGlobalItem != null ? XdmSequence.of (aGlobalItem) : null;
        if (aMatchSelection == null)
        {
          final Template aInitial = m_aSheet.getNamedTemplate (INITIAL_TEMPLATE);
          if (aInitial != null && !_isPublic (aInitial))
            throw new XsltException ("XTDE0040", "The template xsl:initial-template is not public", null, 0, 0);
          if (aInitial == null)
            throw new XsltException ("XTDE0044", "There is no initial match selection and no xsl:initial-template", null, 0, 0);
          aEngine.callTemplate (aCtx, aInitial, _getTemplateParams (m_aRequest), aBuffer);
        }
        else
        {
          final Mode aMode = _resolveInitialMode ();
          if (aStreamedRun != null)
            _applyStreamed (aEngine, aStreamedRun, aCtx, aMode, aBuffer);
          else
            aEngine.applyTemplates (aCtx.withState (aCtx.getState ().withCurrentRule (null, aMode)),
                                    aMatchSelection,
                                    aMode,
                                    _getTemplateParams (m_aRequest),
                                    aBuffer);
        }
      }

    // The principal result; an xsl:result-document without href may have
    // written it with its own output definition
    if (aEngine.getPrincipalSealedSize () >= 0 && aBuffer.size () > aEngine.getPrincipalSealedSize ())
      throw new XsltException ("XTDE1490",
                               "Content was written to the principal result after an xsl:result-document had produced it",
                               null,
                               0,
                               0);
    final OutputDefinition aOutputDef = aEngine.getPrincipalOutputDefinition () != null ? aEngine.getPrincipalOutputDefinition ()
                                                                                         : m_aSheet.getOutput ();
    final String sDeclaredMethod = aOutputDef.getMethod ();
    final boolean bRawOnly = m_aRequest.isRawDelivery ();
    final boolean bBuildTree = !bRawOnly &&
                               aOutputDef.isBuildTree (sDeclaredMethod != null ? sDeclaredMethod : SerializationParams.METHOD_XML);
    final XdmSequence aRaw;
    IXdmNode aTree = null;
    if (bBuildTree)
    {
      final ICommonsList <IXdmItem> aItems = new CommonsArrayList <> ();
      for (int i = 0; i < aBuffer.size (); ++i)
      {
        final IXdmItem aItem = aBuffer.get (i);
        // Result tree construction is sequence normalization (XSLT 3.0
        // section 2.3.6.1), which rejects attribute and namespace nodes
        if (aItem instanceof final IXdmNode aNode && (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE) ||
            aItem instanceof XdmMap ||
            aItem instanceof IXdmFunction)
          throw new XPathException ("SENR0001", "The principal result contains an attribute or namespace node, a map or a function");
        aItems.add (aItem);
      }
      aTree = IXdmNode.createDocument ();
      if (m_aRequest.getBaseOutputUri () != null)
        aTree.setBaseUri (m_aRequest.getBaseOutputUri ());
      final ContentOutput aContent = new ContentOutput (aTree);
      aBuffer.replayAll (aContent);
      aContent.finishTree ();
      aRaw = XdmSequence.of (aItems);
    }
    else
      aRaw = aBuffer.getSequence ();
    final String sMethod = sDeclaredMethod != null ? sDeclaredMethod : _detectMethod (aTree);
    if (bRawOnly)
      return new XsltTransformResult (aRaw,
                                      null,
                                      "",
                                      aEngine.getSecondaryResults ().getClone (),
                                      aEngine.getMessages ().getClone (),
                                      sMethod,
                                      false);
    final SerializationParams aParams = aOutputDef.createSerializationParams (sMethod);
    for (final IOutputCustomizer aCustomizer : XsltExtensionRegistry.getInstance ().getAllOutputCustomizers ())
      aCustomizer.customize (m_aSheet, aOutputDef, aParams);
    SerializationChecks.check (aParams, aTree);
    // With an item separator the raw sequence is serialized, so that the
    // separator comes between its items
    final boolean bSerializeRaw = aTree == null || aOutputDef.getValue ("item-separator") != null &&
                                                   !"#absent".equals (aOutputDef.getValue ("item-separator"));
    final String sSerialized = XdmSerializer.serialize (bSerializeRaw ? aRaw : XdmSequence.of (aTree), aParams);
    return new XsltTransformResult (aRaw,
                                    aTree,
                                    sSerialized,
                                    aEngine.getSecondaryResults ().getClone (),
                                    aEngine.getMessages ().getClone (),
                                    sMethod,
                                    aParams.isIndent ());
  }

  /**
   * Run the transformation on a worker thread with a large stack.
   *
   * @return The result.
   * @throws XPathException
   *         for dynamic errors
   */
  @NonNull
  public XsltTransformResult run ()
  {
    return LargeStackRunner.run (this::_run, "ph-xslt-transform");
  }
}
