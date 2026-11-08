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
package com.helger.phoxtrot.xslt.runtime;

import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmStreamReader;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.eval.SequenceTypeMatcher;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;
import com.helger.phoxtrot.xpath.serialize.XdmIncrementalSerializer;
import com.helger.phoxtrot.xpath.serialize.XdmSerializer;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.XsltTransformResult;
import com.helger.phoxtrot.xslt.compile.IOutputCustomizer;
import com.helger.phoxtrot.xslt.compile.XsltExtensionRegistry;
import com.helger.phoxtrot.xslt.ext.accumulator.AccumulatorExtension;
import com.helger.phoxtrot.xslt.model.CompiledStylesheet;
import com.helger.phoxtrot.xslt.model.Mode;
import com.helger.phoxtrot.xslt.model.OutputDefinition;
import com.helger.phoxtrot.xslt.model.Template;
import com.helger.phoxtrot.xslt.model.TemplateRule;
import com.helger.phoxtrot.xslt.model.UserFunction;
import com.helger.phoxtrot.xslt.pkg.PackageComponent;
import com.helger.phoxtrot.xslt.stream.StreamConsumer;
import com.helger.phoxtrot.xslt.stream.StreamPlanner;
import com.helger.phoxtrot.xslt.stream.StreamedRun;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslModule;
import com.helger.phoxtrot.xslt.tree.XsltNames;

/**
 * Runs one transformation (XSLT 3.0 section 2.3): sets up the engine, invokes
 * the initial function, initial template or initial mode, builds the
 * principal result tree and serializes it.
 * <p>
 * The principal result can be written to a writer or output stream instead of
 * being returned as a string. Where the output definition allows it, it is
 * then also written while it is built ({@link ResultDrain}): the stylesheet is
 * not schema-aware, the method is stated by the unnamed xsl:output, no
 * xsl:result-document could redirect the principal output, a tree is built,
 * and the serialization parameters do not need content not seen yet
 * ({@link XdmIncrementalSerializer#isSupported(SerializationParams)}).
 * Otherwise the tree is built in full and then written, with the same
 * characters as the returned string.
 *
 * @author Philip Helger
 */
public final class XsltTransformer
{
  private static final XdmQName INITIAL_TEMPLATE = new XdmQName (XsltNames.NS_XSL, "initial-template");
  private static final String NS_XHTML = "http://www.w3.org/1999/xhtml";

  private final CompiledStylesheet m_aSheet;
  private final XsltTransformRequest m_aRequest;
  // The drain of the current run, if its result is written while it is built
  private ResultDrain m_aDrain;

  /**
   * Prepare one run; nothing happens before {@link #run()} or a
   * <code>runTo</code> method is called.
   *
   * @param aSheet
   *        The compiled stylesheet.
   * @param aRequest
   *        The request with the source, initial component, parameters and
   *        options.
   */
  public XsltTransformer (@NonNull final CompiledStylesheet aSheet, @NonNull final XsltTransformRequest aRequest)
  {
    m_aSheet = aSheet;
    m_aRequest = aRequest;
  }

  /**
   * @param aRequest
   *        The request.
   * @return The template parameters of the request (tunnel and non-tunnel) as
   *         parameter values for the initial template or the initial
   *         apply-templates, in request order.
   */
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

  /**
   * The output method when no xsl:output states one, decided from the result
   * tree: the first element child of the document node decides if no text
   * node with non-whitespace content comes before it (XSLT 3.0 section 26.1).
   * The local name "html" in any case gives the html method in no namespace,
   * the lower-case "html" in the XHTML namespace gives the xhtml method;
   * everything else (another element, a
   * non-whitespace text before it, no element at all, no tree) gives xml.
   *
   * @param aTree
   *        The result document node, or <code>null</code> if no tree was
   *        built.
   * @return The method name, never <code>null</code>.
   */
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
          if (!aName.hasNamespace () && "html".equals (aName.localName ().toLowerCase (Locale.ROOT)))
            return SerializationParams.METHOD_HTML;
          if (NS_XHTML.equals (aName.namespaceUri ()) && "html".equals (aName.localName ()))
            return SerializationParams.METHOD_XHTML;
          return SerializationParams.METHOD_XML;
        }
        if (aChild.isText () && !XsltNames.isWhitespaceOnly (aChild.getValue ()))
          return SerializationParams.METHOD_XML;
      }
    return SerializationParams.METHOD_XML;
  }

  /**
   * Walks the ancestor path of the node from the root down: an attribute is
   * found by its name, a namespace node by its prefix, a child by counting
   * the kept children before it.
   *
   * @param aRoot
   *        The root of the original tree.
   * @param aStrippedRoot
   *        The root of its stripped copy.
   * @param aNode
   *        A node of the original tree.
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
                                  (nCopyIdx < aCopy.getChildCount () && aCopy.getChildAtIndex (nCopyIdx).isText ());
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

  /**
   * Map a node to its counterpart in the whitespace-stripped copy of its tree
   * (xsl:strip-space). Only trees rooted at a document node are stripped; the
   * stripped copy of each root is made once by
   * {@link Engine#stripWhitespace(IXdmNode)} and cached in the map. If
   * stripping leaves the tree unchanged (the same root is returned), the node
   * itself is used.
   *
   * @param aEngine
   *        The engine.
   * @param aStrippedRoots
   *        Original root to stripped root, filled here.
   * @param aNode
   *        The node.
   * @return The node in the stripped tree, or <code>null</code> if it was
   *         itself stripped.
   */
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
   * @param aSuppliedItem
   *        The global context item supplied to the run, or <code>null</code>.
   *        Under use="absent" of xsl:global-context-item it is dropped.
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

  /**
   * Set up the global context item of a new engine from the global context
   * item of its request, as {@link #_initGlobalContextItem(Engine, ICommonsMap, IXdmItem)}
   * does for a transformation: stripping, the xsl:global-context-item checks,
   * registration as source document and the check of the global parameters.
   * This is the start-up shared with hosts that evaluate their own XPath
   * against a stylesheet (<code>XsltExecutable.createEvaluation</code>).
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
  public static IXdmItem initGlobalContextItem (@NonNull final Engine aEngine,
                                                @NonNull final ICommonsMap <IXdmNode, IXdmNode> aStrippedRoots)
  {
    return _initGlobalContextItem (aEngine, aStrippedRoots, aEngine.getRequest ().getGlobalContextItem ());
  }

  /**
   * @return The name of the initial mode of the request: <code>#unnamed</code>
   *         gives the unnamed mode, no mode or <code>#default</code> the
   *         default mode of the stylesheet, any other name itself.
   */
  @NonNull
  private XdmQName _getInitialModeName ()
  {
    final XdmQName aModeName = m_aRequest.getInitialMode ();
    if (aModeName != null && !aModeName.hasNamespace () && "#unnamed".equals (aModeName.localName ()))
      return Mode.UNNAMED;
    if (aModeName == null || (!aModeName.hasNamespace () && "#default".equals (aModeName.localName ())))
      return m_aSheet.getDefaultModeName ();
    return aModeName;
  }

  /**
   * Resolve the initial mode of the request (XSLT 3.0 section 2.3.3). A mode
   * requested explicitly (any name but <code>#default</code>) that is neither
   * the unnamed mode nor the default mode must be declared, by xsl:mode or by
   * a template rule naming it (mode="#all" does not count), and must be
   * public: not made private by xsl:expose, not declared private or abstract,
   * and in a package a declared mode without visibility must be exposed.
   *
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
         (sVis == null &&
                         m_aSheet.isPackage () &&
                         aMode.getDeclaration () != null &&
                         !m_aSheet.exposedModes ().contains (aModeName))))
      throw new XsltException ("XTDE0045", "The initial mode " + aModeName.getEQName () + " is not public", null, 0, 0);
    return aMode;
  }

  /**
   * @param aEngine
   *        The engine.
   * @param sUri
   *        The URI of the principal source.
   * @return The document read in full; stripped, as for fn:doc.
   * @throws XPathException
   *         FODC0002 if the URI identifies no node
   */
  @NonNull
  private static IXdmNode _readSource (@NonNull final Engine aEngine, @NonNull final String sUri)
  {
    final IXdmNode ret = aEngine.getDocument (aEngine.createRootContext (), sUri);
    if (ret == null)
      throw new XPathException ("FODC0002", "The source '" + sUri + "' identifies no node");
    return ret;
  }

  /**
   * Read the rest of a streamed source into a complete tree and close the
   * reader in any case.
   *
   * @param aReader
   *        The open reader.
   * @return The document node.
   * @throws XPathException
   *         FODC0002 if the source cannot be parsed
   */
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

  /**
   * Close a reader; a parse error while closing is ignored, since everything
   * the run needed was read.
   *
   * @param aReader
   *        The reader.
   */
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
   * Plan a streamed run of the principal source: find the template rule of
   * the mode for the (still empty) document node and, in its body, the one
   * consuming instruction ({@link StreamPlanner#findConsumer}) that
   * {@link StreamConsumer} can execute.
   *
   * @param aEngine
   *        The engine.
   * @param aMode
   *        The streamable initial mode.
   * @param aReader
   *        The open reader of the principal source.
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
    final XslElement aConsumer = StreamPlanner.findConsumer (aRule.getTemplate ().getElement (), m_aSheet.getStreamVerdicts ());
    if (aConsumer == null || !StreamConsumer.isConsumer (m_aSheet, aConsumer))
      return null;
    return new StreamedRun (aReader, aConsumer);
  }

  /**
   * Apply the templates of the initial mode to the streamed document node:
   * the run is made active on the engine, the streamed accumulators are
   * started, and the template rule executes, during which the consumer reads
   * the stream. Afterwards the run is deactivated and the reader closed, also
   * on error.
   *
   * @param aEngine
   *        The engine.
   * @param aRun
   *        The planned run.
   * @param aCtx
   *        The root context.
   * @param aMode
   *        The initial mode.
   * @param aOut
   *        The principal output.
   * @throws IllegalStateException
   *         if the planned consumer was never reached (the planner and the
   *         execution disagree)
   */
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

  /**
   * @param aOutputDef
   *        The output definition.
   * @param sMethod
   *        The effective method.
   * @return Serialisation parameters from the output definition, after all
   *         output customizers of the extension registry (character maps,
   *         ...) have adjusted them.
   */
  @NonNull
  private SerializationParams _createSerializationParams (@NonNull final OutputDefinition aOutputDef,
                                                          @NonNull final String sMethod)
  {
    final SerializationParams ret = aOutputDef.createSerializationParams (sMethod);
    for (final IOutputCustomizer aCustomizer : XsltExtensionRegistry.getInstance ().getAllOutputCustomizers ())
      aCustomizer.customize (m_aSheet, aOutputDef, ret);
    return ret;
  }

  /**
   * @param aOutputDef
   *        The output definition.
   * @return <code>true</code> if it sets an item-separator other than
   *         <code>#absent</code>.
   */
  private static boolean _hasItemSeparator (@NonNull final OutputDefinition aOutputDef)
  {
    return aOutputDef.getValue ("item-separator") != null && !"#absent".equals (aOutputDef.getValue ("item-separator"));
  }

  /**
   * @return <code>true</code> if any module has an xsl:result-document, which
   *         may write the principal result with another output definition.
   */
  private boolean _hasResultDocument ()
  {
    for (final XslModule aModule : m_aSheet.modules ())
    {
      final XslElement aRoot = aModule.getRoot ();
      if (aRoot == null)
        continue;
      final ArrayDeque <XslElement> aStack = new ArrayDeque <> ();
      aStack.push (aRoot);
      while (!aStack.isEmpty ())
      {
        final XslElement aEl = aStack.pop ();
        if (aEl.isXsl ("result-document"))
          return true;
        for (final XslElement aChild : aEl.getElementChildren ())
          aStack.push (aChild);
      }
    }
    return false;
  }

  /**
   * Create the drain for a target when the principal result can be written
   * while it is built: not schema-aware, a method stated by the unnamed
   * output definition that builds a tree, no item-separator, no
   * xsl:result-document anywhere, and parameters the incremental serialiser
   * supports. Invalid serialisation parameters also mean no drain, so that
   * the error is raised by the serialisation after the run as usual. On
   * success the target is opened with the parameters.
   *
   * @param aTarget
   *        The target of the principal result.
   * @return The drain writing the principal result while it is built, or
   *         <code>null</code> if the output definition does not allow it.
   */
  @Nullable
  private ResultDrain _createDrain (@NonNull final ResultTarget aTarget)
  {
    // Schema-aware: validation may change or check a tree after it was built
    if (m_aSheet.isSchemaAware ())
      return null;
    final OutputDefinition aOutputDef = m_aSheet.getOutput ();
    final String sMethod = aOutputDef.getMethod ();
    // The method must be known before the first character is written
    if (sMethod == null || !aOutputDef.isBuildTree (sMethod) || _hasItemSeparator (aOutputDef) || _hasResultDocument ())
      return null;
    final SerializationParams aParams = _createSerializationParams (aOutputDef, sMethod);
    if (!XdmIncrementalSerializer.isSupported (aParams))
      return null;
    try
    {
      SerializationChecks.check (aParams, null);
      XdmIncrementalSerializer.checkParams (aParams);
    }
    catch (final XPathException ex)
    {
      // Raised by the serialization after the run, as without a drain
      return null;
    }
    return new ResultDrain (aParams, aTarget.open (aParams));
  }

  /**
   * One complete run on the current thread (XSLT 3.0 section 2.3).
   * <ol>
   * <li>For a principal source given as a stream URI and an initial mode
   * whose streamability and accumulators allow it, the source is read as a
   * stream; otherwise it is read in full and also serves as global context
   * item unless one is supplied. A source declared streamed for a streamable
   * initial mode makes global variables run without focus.</li>
   * <li>The global context item is set up, the initial match selection is
   * mapped to the stripped trees (stripped nodes are dropped) and defaults to
   * the principal source.</li>
   * <li>The principal output is a {@link ResultDrain} when the target allows
   * it, otherwise a buffer.</li>
   * <li>Invocation: an initial function (public or final, the arguments
   * converted to the declared types, XTDE0041 if there is none), else an
   * initial named template (it must exist and be public, XTDE0040), else
   * apply-templates of the initial mode to the initial match selection
   * (falling back to the global context item); without any selection
   * xsl:initial-template is called (XTDE0040 if it is absent or not public;
   * XTDE0044 if an initial mode was requested).</li>
   * <li>Post-processing: with a drain only the rest is written. Otherwise
   * XTDE1490 if content followed an xsl:result-document that wrote the
   * principal result; the output definition of that xsl:result-document or
   * the unnamed one applies; unless the request asks for the raw result only
   * (and there is no target), a result tree is built if build-tree allows
   * (SENR0001 for attributes, namespaces, maps and functions at the top
   * level); the method comes from the definition or
   * {@link #_detectMethod(IXdmNode)}; and the tree (or the raw sequence, with
   * an item-separator or without a tree) is serialised to the target or a
   * string.</li>
   * </ol>
   *
   * @param aTarget
   *        Where the serialised principal result goes, or <code>null</code>
   *        to return it in the result.
   * @return The result; with a target the principal result and its
   *         serialised form are empty.
   */
  @NonNull
  private XsltTransformResult _run (@Nullable final ResultTarget aTarget)
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
    m_aDrain = aTarget != null ? _createDrain (aTarget) : null;
    final BufferedOutput aBuffer = m_aDrain == null ? new BufferedOutput () : null;
    final IXsltOutput aPrincipal = m_aDrain != null ? m_aDrain.getOutput () : aBuffer;
    aEngine.setPrincipalOutput (aPrincipal);
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
      aPrincipal.appendAll (aFunc.invoke (aEngine.createRootContext (), aArgs));
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
        aEngine.callTemplate (aCtx, aTemplate, _getTemplateParams (m_aRequest), aPrincipal);
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
          // XSLT 3.0 section 2.3.3: an initial mode needs a match selection
          if (m_aRequest.getInitialMode () != null)
            throw new XsltException ("XTDE0044", "An initial mode is requested, but there is no initial match selection", null, 0, 0);
          if (aInitial == null)
            throw new XsltException ("XTDE0040", "There is no initial match selection and no xsl:initial-template", null, 0, 0);
          aEngine.callTemplate (aCtx, aInitial, _getTemplateParams (m_aRequest), aPrincipal);
        }
        else
        {
          final Mode aMode = _resolveInitialMode ();
          if (aStreamedRun != null)
            _applyStreamed (aEngine, aStreamedRun, aCtx, aMode, aPrincipal);
          else
            aEngine.applyTemplates (aCtx.withState (aCtx.getState ().withCurrentRule (null, aMode)),
                                    aMatchSelection,
                                    aMode,
                                    _getTemplateParams (m_aRequest),
                                    aPrincipal);
        }
      }

    if (m_aDrain != null)
    {
      m_aDrain.finish ();
      return new XsltTransformResult (XdmSequence.EMPTY,
                                      null,
                                      "",
                                      aEngine.getSecondaryResults ().getClone (),
                                      aEngine.getMessages ().getClone (),
                                      m_aSheet.getOutput ().getMethod (),
                                      false);
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
    // Raw delivery does not apply when the result is written to a target
    final boolean bRawOnly = aTarget == null && m_aRequest.isRawDelivery ();
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
        if (ResultDrain.isNormalizationError (aItem))
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
    final SerializationParams aParams = _createSerializationParams (aOutputDef, sMethod);
    SerializationChecks.check (aParams, aTree);
    // With an item separator the raw sequence is serialized, so that the
    // separator comes between its items
    final boolean bSerializeRaw = aTree == null || _hasItemSeparator (aOutputDef);
    final XdmSequence aToSerialize = bSerializeRaw ? aRaw : XdmSequence.of (aTree);
    if (aTarget != null)
    {
      XdmSerializer.serialize (aToSerialize, aParams, aTarget.open (aParams));
      return new XsltTransformResult (XdmSequence.EMPTY,
                                      null,
                                      "",
                                      aEngine.getSecondaryResults ().getClone (),
                                      aEngine.getMessages ().getClone (),
                                      sMethod,
                                      aParams.isIndent ());
    }
    final String sSerialized = XdmSerializer.serialize (aToSerialize, aParams);
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
    return LargeStackRunner.run ( () -> _run (null), "ph-xslt-transform");
  }

  /**
   * {@link #_run(ResultTarget)} on a large-stack worker thread; on an error
   * what was written so far is flushed to the target before the error is
   * rethrown.
   *
   * @param aTarget
   *        The target.
   * @return The result.
   */
  @NonNull
  private XsltTransformResult _runTo (@NonNull final ResultTarget aTarget)
  {
    return LargeStackRunner.run ( () -> {
      try
      {
        return _run (aTarget);
      }
      catch (final RuntimeException ex)
      {
        // What was written so far is the caller's to discard
        if (m_aDrain != null)
          m_aDrain.flushQuietly ();
        aTarget.flushQuietly ();
        throw ex;
      }
    }, "ph-xslt-transform");
  }

  /**
   * Run the transformation on a worker thread with a large stack and write
   * the serialized principal result to an output stream, encoded with the
   * encoding of its output definition. The stream is flushed, not closed.
   *
   * @param aOS
   *        The target stream.
   * @return The result, with an empty principal result and serialized form
   *         (they went to the stream); the secondary results and messages as
   *         with {@link #run()}.
   * @throws XPathException
   *         for dynamic and serialization errors; part of the output may have
   *         been written to the stream then
   */
  @NonNull
  public XsltTransformResult runTo (@NonNull final OutputStream aOS)
  {
    return _runTo (ResultTarget.of (aOS));
  }

  /**
   * Run the transformation on a worker thread with a large stack and write
   * the serialized principal result to a writer, with the characters
   * {@link #run()} would return. The writer is flushed, not closed.
   *
   * @param aWriter
   *        The target writer.
   * @return The result, with an empty principal result and serialized form
   *         (they went to the writer); the secondary results and messages as
   *         with {@link #run()}.
   * @throws XPathException
   *         for dynamic and serialization errors; part of the output may have
   *         been written to the writer then
   */
  @NonNull
  public XsltTransformResult runTo (@NonNull final Writer aWriter)
  {
    return _runTo (ResultTarget.of (aWriter));
  }
}
