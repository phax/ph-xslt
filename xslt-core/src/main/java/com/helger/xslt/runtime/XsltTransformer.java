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
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;
import com.helger.xslt.compile.IOutputCustomizer;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.Mode;
import com.helger.xslt.model.OutputDefinition;
import com.helger.xslt.model.Template;
import com.helger.xslt.model.UserFunction;
import com.helger.xslt.pkg.PackageComponent;
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
  private static String _detectMethod (@Nullable final XdmNode aTree)
  {
    if (aTree != null)
      for (int i = 0; i < aTree.getChildCount (); ++i)
      {
        final XdmNode aChild = aTree.getChildAtIndex (i);
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
  private static XdmNode _mapToStripped (@NonNull final XdmNode aRoot,
                                         @NonNull final XdmNode aStrippedRoot,
                                         @NonNull final XdmNode aNode)
  {
    final ICommonsList <XdmNode> aPath = new CommonsArrayList <> ();
    for (XdmNode aCur = aNode; aCur != null && aCur != aRoot; aCur = aCur.getParent ())
      aPath.add (aCur);
    XdmNode aOrig = aRoot;
    XdmNode aCopy = aStrippedRoot;
    for (int nLevel = aPath.size () - 1; nLevel >= 0; --nLevel)
    {
      final XdmNode aWanted = aPath.get (nLevel);
      XdmNode aFound = null;
      if (aWanted.isAttribute ())
        aFound = aCopy.getAttribute (aWanted.getName ().namespaceUri (), aWanted.getName ().localName ());
      else
        if (aWanted.getKind () == ENodeKind.NAMESPACE)
        {
          for (final XdmNode aNS : aCopy.getNamespaceNodes ())
            if (Objects.equals (aNS.getName (), aWanted.getName ()))
              aFound = aNS;
        }
        else
        {
          int nCopyIdx = 0;
          for (int i = 0; i < aOrig.getChildCount (); ++i)
          {
            final XdmNode aChild = aOrig.getChildAtIndex (i);
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
  private static XdmNode _toStripped (@NonNull final Engine aEngine,
                                      @NonNull final ICommonsMap <XdmNode, XdmNode> aStrippedRoots,
                                      @NonNull final XdmNode aNode)
  {
    final XdmNode aRoot = aNode.getRoot ();
    if (!aRoot.isDocument ())
      return aNode;
    final XdmNode aStripped = aStrippedRoots.computeIfAbsent (aRoot, aEngine::stripWhitespace);
    if (aStripped == aRoot)
      return aNode;
    return _mapToStripped (aRoot, aStripped, aNode);
  }

  @NonNull
  private XsltTransformResult _run ()
  {
    final Engine aEngine = new Engine (m_aSheet, m_aRequest);
    // Whitespace stripping applies to the trees of the global context item
    // and of the initial match selection; a node that is stripped itself is
    // gone (strip-space-023, mode-1802)
    final ICommonsMap <XdmNode, XdmNode> aStrippedRoots = new CommonsHashMap <> ();
    IXdmItem aGlobalItem = m_aRequest.getGlobalContextItem ();
    if (aGlobalItem instanceof final XdmNode aNode)
      aGlobalItem = _toStripped (aEngine, aStrippedRoots, aNode);
    XdmSequence aMatchSelection = m_aRequest.getInitialMatchSelection ();
    if (aMatchSelection != null)
    {
      final XdmSequence.Builder aNew = new XdmSequence.Builder ();
      for (final IXdmItem aItem : aMatchSelection)
      {
        final IXdmItem aMapped = aItem instanceof final XdmNode aNode ? _toStripped (aEngine, aStrippedRoots, aNode) : aItem;
        if (aMapped != null)
          aNew.add (aMapped);
      }
      aMatchSelection = aNew.build ();
    }
    final CompiledStylesheet.GlobalContextItem aGCI = m_aSheet.getGlobalContextItem ();
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
    aEngine.checkGlobalParams ();

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
          XdmQName aModeName = m_aRequest.getInitialMode ();
          boolean bExplicitMode = aModeName != null;
          if (aModeName != null && !aModeName.hasNamespace () && "#unnamed".equals (aModeName.localName ()))
            aModeName = Mode.UNNAMED;
          else
            if (aModeName == null || !aModeName.hasNamespace () && "#default".equals (aModeName.localName ()))
            {
              aModeName = m_aSheet.getDefaultModeName ();
              bExplicitMode = false;
            }
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
    XdmNode aTree = null;
    if (bBuildTree)
    {
      final ICommonsList <IXdmItem> aItems = new CommonsArrayList <> ();
      for (int i = 0; i < aBuffer.size (); ++i)
      {
        final IXdmItem aItem = aBuffer.get (i);
        // Result tree construction is sequence normalization (XSLT 3.0
        // section 2.3.6.1), which rejects attribute and namespace nodes
        if (aItem instanceof final XdmNode aNode && (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE) ||
            aItem instanceof XdmMap ||
            aItem instanceof IXdmFunction)
          throw new XPathException ("SENR0001", "The principal result contains an attribute or namespace node, a map or a function");
        aItems.add (aItem);
      }
      aTree = XdmNode.createDocument ();
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
