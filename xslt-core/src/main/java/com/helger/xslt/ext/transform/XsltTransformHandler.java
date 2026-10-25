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
package com.helger.xslt.ext.transform;

import java.io.StringReader;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.fn.FnAccessor;
import com.helger.xpath.fn.FnUri;
import com.helger.xpath.fn.ITransformHandler;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.serialize.SerializationParamsReader;
import com.helger.xpath.serialize.XdmSerializer;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;
import com.helger.xslt.compile.FileResourceResolver;
import com.helger.xslt.compile.IOutputCustomizer;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.OutputDefinition;
import com.helger.xslt.runtime.Engine;
import com.helger.xslt.runtime.ExecutionBudget;
import com.helger.xslt.runtime.NodeCopier;
import com.helger.xslt.tree.XsltNames;

/**
 * fn:transform (F&amp;O 3.1 section 16.3.2) backed by this XSLT engine. Found by
 * xpath-core through <code>META-INF/services</code>, so the function is
 * available wherever xslt-core is on the class path: in plain XPath
 * expressions and inside stylesheets (including static expressions).
 * <p>
 * Inside a transformation the nested one compiles with the caller's compile
 * options (resolver, library packages), reads documents through the caller's
 * resolver and spends from the caller's step and depth budget. Outside one,
 * the resolver of the calling dynamic context is used for both.
 * <p>
 * Supported options: stylesheet-location, stylesheet-node, stylesheet-text,
 * stylesheet-base-uri, package-name with package-version (searched in the
 * library packages of the calling stylesheet), package-location,
 * package-node, package-text, static-params, stylesheet-params, source-node,
 * initial-match-selection, global-context-item, initial-template,
 * initial-mode, initial-function, function-params, template-params,
 * tunnel-params, base-output-uri, delivery-format, serialization-params,
 * post-process and requested-properties. xslt-version, vendor-options, cache,
 * enable-messages, enable-trace and enable-assertions are type checked and
 * otherwise ignored, as the specification allows.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class XsltTransformHandler implements ITransformHandler
{
  /** Maximum number of nested fn:transform calls on one thread. */
  public static final int MAX_NESTING = 100;

  private static final String KEY_OUTPUT = "output";
  private static final String [] STYLESHEET_OPTIONS = { "stylesheet-location",
                                                        "stylesheet-node",
                                                        "stylesheet-text",
                                                        "package-name",
                                                        "package-location",
                                                        "package-node",
                                                        "package-text" };
  private static final ICommonsMap <String, SeqType> OPTION_TYPES = new CommonsHashMap <> ();
  private static final ThreadLocal <Integer> NESTING = ThreadLocal.withInitial ( () -> Integer.valueOf (0));

  static
  {
    final StaticContext aSC = new StaticContext ();
    final String [] aTypes = { "base-output-uri",
                               "xs:string",
                               "cache",
                               "xs:boolean",
                               "delivery-format",
                               "xs:string",
                               "enable-assertions",
                               "xs:boolean",
                               "enable-messages",
                               "xs:boolean",
                               "enable-trace",
                               "xs:boolean",
                               "function-params",
                               "array(*)",
                               "global-context-item",
                               "item()",
                               "initial-function",
                               "xs:QName",
                               "initial-match-selection",
                               "item()*",
                               "initial-mode",
                               "xs:QName",
                               "initial-template",
                               "xs:QName",
                               "package-location",
                               "xs:string",
                               "package-name",
                               "xs:string",
                               "package-node",
                               "node()",
                               "package-text",
                               "xs:string",
                               "package-version",
                               "xs:string",
                               "post-process",
                               "function(xs:string, item()*) as item()*",
                               "requested-properties",
                               "map(xs:QName, xs:anyAtomicType)",
                               "serialization-params",
                               "map(xs:anyAtomicType, item()*)",
                               "source-node",
                               "node()",
                               "static-params",
                               "map(xs:QName, item()*)",
                               "stylesheet-base-uri",
                               "xs:string",
                               "stylesheet-location",
                               "xs:string",
                               "stylesheet-node",
                               "node()",
                               "stylesheet-params",
                               "map(xs:QName, item()*)",
                               "stylesheet-text",
                               "xs:string",
                               "template-params",
                               "map(xs:QName, item()*)",
                               "tunnel-params",
                               "map(xs:QName, item()*)",
                               "vendor-options",
                               "map(xs:QName, item()*)",
                               "xslt-version",
                               "xs:decimal" };
    for (int i = 0; i < aTypes.length; i += 2)
      OPTION_TYPES.put (aTypes[i], XPathParser.parseSequenceType (aTypes[i + 1], aSC));
  }

  /** The options after the option parameter conventions, by name. */
  private static final class Options
  {
    private final ICommonsMap <String, XdmSequence> m_aValues = new CommonsHashMap <> ();

    Options (@NonNull final XdmMap aMap, @NonNull final DynamicContext aCtx)
    {
      for (final XdmMap.Entry aEntry : aMap.entries ())
      {
        final XdmAtomic aKey = aEntry.key ();
        if (!aKey.isStringLike ())
          continue;
        final String sName = aKey.getString ();
        final SeqType aType = OPTION_TYPES.get (sName);
        // Unknown options are ignored (option parameter conventions)
        if (aType != null)
          m_aValues.put (sName,
                         SequenceTypeMatcher.convert (aEntry.value (), aType, aCtx, "XPTY0004"));
      }
    }

    boolean has (@NonNull final String sName)
    {
      return m_aValues.containsKey (sName);
    }

    @Nullable
    XdmSequence get (@NonNull final String sName)
    {
      return m_aValues.get (sName);
    }

    @Nullable
    IXdmItem getItem (@NonNull final String sName)
    {
      final XdmSequence aSeq = m_aValues.get (sName);
      return aSeq == null || aSeq.isEmpty () ? null : aSeq.getFirst ();
    }

    @Nullable
    String getString (@NonNull final String sName)
    {
      return getItem (sName) instanceof final XdmAtomic aAtomic ? aAtomic.getString () : null;
    }

    @Nullable
    XdmQName getQName (@NonNull final String sName)
    {
      return getItem (sName) instanceof final XdmAtomic aAtomic &&
             aAtomic.getValue () instanceof final XdmQName aQName ? aQName : null;
    }

    @Nullable
    XdmMap getMap (@NonNull final String sName)
    {
      return getItem (sName) instanceof final XdmMap aMap ? aMap : null;
    }
  }

  /** What fn:transform needs from its caller. */
  private record Host (@Nullable String staticBaseUri,
                       @NonNull IResourceResolver compileResolver,
                       @Nullable IResourceResolver runResolver,
                       @Nullable XsltCompileOptions callerOptions,
                       @Nullable Engine engine)
  {}

  /**
   * Needed for the service loader.
   */
  public XsltTransformHandler ()
  {}

  @NonNull
  private static XPathException _invalid (@NonNull final String sMessage)
  {
    return new XPathException ("FOXT0002", "fn:transform: " + sMessage);
  }

  @NonNull
  private static Host _getHost (@NonNull final DynamicContext aCtx)
  {
    final String sBaseUri = aCtx.getStaticContext ().getBaseUri ();
    if (aCtx.getHost () instanceof final Engine aEngine)
    {
      final XsltCompileOptions aOptions = TransformExtension.getCompileOptions (aEngine.getStylesheet ());
      IResourceResolver aCompileResolver = aOptions == null ? null : aOptions.getResolver ();
      if (aCompileResolver == null)
        aCompileResolver = new FileResourceResolver (aOptions != null &&
                                                                             aOptions.isLoadExternalEntities ());
      return new Host (sBaseUri, aCompileResolver, aEngine.getRequest ().getResolver (), aOptions, aEngine);
    }
    // A plain XPath expression, or a static expression of a stylesheet being
    // compiled: its resolver is the one of the compilation
    final IResourceResolver aResolver = aCtx.getResourceResolver ();
    return new Host (sBaseUri, aResolver, aResolver, null, null);
  }

  @Nullable
  private static String _resolve (@NonNull final String sUri, @Nullable final String sBase)
  {
    if (FnUri.isAbsoluteUri (sUri))
      return sUri;
    if (sBase == null || sBase.isEmpty ())
      return null;
    try
    {
      return FnUri.resolveUri (sUri, sBase);
    }
    catch (final XPathException ex)
    {
      return null;
    }
  }

  /**
   * Check the requested properties this processor has a fixed answer for.
   * Every other property is left alone (the specification allows ignoring a
   * request).
   */
  private static void _checkRequestedProperties (@NonNull final Options aOpts)
  {
    final XdmMap aMap = aOpts.getMap ("requested-properties");
    if (aMap == null)
      return;
    for (final XdmMap.Entry aEntry : aMap.entries ())
    {
      if (!(aEntry.key ().getValue () instanceof final XdmQName aName) || !XsltNames.NS_XSL.equals (aName.namespaceUri ()))
        continue;
      final boolean bHave;
      switch (aName.localName ())
      {
        case "supports-namespace-axis":
          bHave = true;
          break;
        case "supports-streaming":
        case "supports-dynamic-evaluation":
          // Requests are answered for what fn:transform guarantees
          bHave = "supports-dynamic-evaluation".equals (aName.localName ());
          break;
        default:
          continue;
      }
      final IXdmItem aValue = aEntry.value ().isEmpty () ? null : aEntry.value ().getFirst ();
      if (!(aValue instanceof final XdmAtomic aAtomic))
        continue;
      final boolean bRequested;
      if (aAtomic.getType () == EAtomicType.BOOLEAN)
        bRequested = aAtomic.getBoolean ();
      else
      {
        final String s = aAtomic.getString ().trim ();
        if (!XsltNames.isXsltTrue (s) && !"no".equals (s) && !"false".equals (s) && !"0".equals (s))
          continue;
        bRequested = XsltNames.isXsltTrue (s);
      }
      if (bRequested != bHave)
        throw new XPathException ("FOXT0001",
                                  "fn:transform: the requested property xsl:" +
                                              aName.localName () +
                                              "=" +
                                              bRequested +
                                              " is not available");
    }
  }

  @NonNull
  private static XdmNode _parse (@NonNull final String sText, @Nullable final String sBaseUri, final boolean bExternal)
  {
    try
    {
      final InputSource aSource = new InputSource (new StringReader (sText));
      if (sBaseUri != null)
        aSource.setSystemId (sBaseUri);
      final XdmNode ret = new XdmParser ().setLoadExternalEntities (bExternal).parse (aSource);
      if (sBaseUri != null && ret.getDocumentUri () == null)
        ret.setDocumentUri (sBaseUri);
      return ret;
    }
    catch (final XdmParseException ex)
    {
      throw _invalid ("the stylesheet is not well-formed: " + ex.getMessage ());
    }
  }

  /**
   * Retrieve a stylesheet or package module by URI.
   */
  @NonNull
  private static XdmNode _fetch (@NonNull final String sLocation, @NonNull final Host aHost)
  {
    final String sUri = _resolve (sLocation, aHost.staticBaseUri ());
    if (sUri == null)
      throw _invalid ("the location '" + sLocation + "' cannot be resolved");
    XdmNode aDoc = aHost.compileResolver ().resolveDocument (sUri);
    if (aDoc == null)
    {
      final String sText = aHost.compileResolver ().resolveText (sUri, null);
      if (sText == null)
        throw _invalid ("the stylesheet '" + sLocation + "' cannot be retrieved");
      aDoc = _parse (sText, sUri, false);
    }
    return aDoc;
  }

  /** The package named by package-name and package-version. */
  @NonNull
  private static XdmNode _findPackage (@NonNull final String sName,
                                       @NonNull final String sVersionRanges,
                                       @NonNull final Host aHost)
  {
    XdmNode aBest = null;
    PackageVersions.Version aBestVersion = null;
    if (aHost.callerOptions () != null)
      for (final XsltCompileOptions.PackageSource aSrc : aHost.callerOptions ().packages ())
      {
        XdmNode aRoot = aSrc.node ();
        if (aRoot == null && aSrc.text () != null)
          aRoot = _parse (aSrc.text (), aSrc.baseUri (), false);
        if (aRoot == null)
          continue;
        XdmNode aElement = aRoot;
        if (aRoot.isDocument ())
        {
          aElement = null;
          for (final XdmNode aChild : aRoot.getAllChildren ())
            if (aChild.isElement ())
              aElement = aChild;
        }
        if (aElement == null || !aElement.getName ().isSameName (XsltNames.NS_XSL, "package"))
          continue;
        final XdmNode aNameAttr = aElement.getAttribute (null, "name");
        if (aNameAttr == null || !sName.equals (aNameAttr.getValue ().trim ()))
          continue;
        final XdmNode aVersionAttr = aElement.getAttribute (null, "package-version");
        final PackageVersions.Version aVersion = PackageVersions.parse (aVersionAttr == null ? "1"
                                                                                             : aVersionAttr.getValue ());
        if (aVersion == null || !PackageVersions.matches (sVersionRanges, aVersion))
          continue;
        if (aBestVersion == null || PackageVersions.isHigher (aVersion, aBestVersion))
        {
          aBest = aRoot;
          aBestVersion = aVersion;
        }
      }
    if (aBest == null)
      throw _invalid ("there is no package '" + sName + "' with version " + sVersionRanges);
    return aBest;
  }

  @NonNull
  private static XsltCompileOptions _createCompileOptions (@NonNull final Options aOpts,
                                                           @NonNull final Host aHost,
                                                           @Nullable final String sBaseUri)
  {
    final XsltCompileOptions ret = new XsltCompileOptions ();
    ret.setBaseUri (sBaseUri);
    ret.setResolver (aHost.compileResolver ());
    if (aHost.callerOptions () != null)
    {
      ret.setLoadExternalEntities (aHost.callerOptions ().isLoadExternalEntities ());
      ret.packages ().addAll (aHost.callerOptions ().packages ());
    }
    final XdmMap aStatics = aOpts.getMap ("static-params");
    if (aStatics != null)
      for (final XdmMap.Entry aEntry : aStatics.entries ())
        ret.setStaticParam ((XdmQName) aEntry.key ().getValue (), aEntry.value ());
    return ret;
  }

  /** Locate and compile the stylesheet or package. */
  @NonNull
  private static XsltExecutable _compile (@NonNull final Options aOpts, @NonNull final Host aHost)
  {
    final String sExplicitBase = aOpts.has ("stylesheet-base-uri") ? _resolve (aOpts.getString ("stylesheet-base-uri"),
                                                                               aHost.staticBaseUri ())
                                                                   : null;
    if (aOpts.has ("stylesheet-location") || aOpts.has ("package-location"))
    {
      final String sLocation = aOpts.getString (aOpts.has ("stylesheet-location") ? "stylesheet-location"
                                                                                  : "package-location");
      XdmNode aDoc = _fetch (sLocation, aHost);
      final String sUri = _resolve (sLocation, aHost.staticBaseUri ());
      if (sUri != null && !sUri.equals (aDoc.getDocumentUri ()))
      {
        // The location is the static base URI of the stylesheet, also when
        // the resolver found the document under another URI
        aDoc = NodeCopier.copyDeep (aDoc, true);
        aDoc.setDocumentUri (sUri);
        aDoc.setBaseUri (sUri);
      }
      return XsltCompiler.compile (aDoc, _createCompileOptions (aOpts, aHost, sUri));
    }
    if (aOpts.has ("stylesheet-text") || aOpts.has ("package-text"))
    {
      final String sText = aOpts.getString (aOpts.has ("stylesheet-text") ? "stylesheet-text" : "package-text");
      return XsltCompiler.compile (sText, _createCompileOptions (aOpts, aHost, sExplicitBase));
    }
    if (aOpts.has ("stylesheet-node") || aOpts.has ("package-node"))
    {
      final XdmNode aNode = (XdmNode) aOpts.getItem (aOpts.has ("stylesheet-node") ? "stylesheet-node" : "package-node");
      // The node's own base URI is the default; stylesheet-base-uri is used
      // when it has none (whether it has an effect otherwise is
      // implementation-defined)
      String sBase = FnAccessor.getBaseUri (aNode);
      if (sBase == null)
        sBase = sExplicitBase;
      return XsltCompiler.compile (aNode, _createCompileOptions (aOpts, aHost, sBase));
    }
    if (aOpts.has ("package-name"))
    {
      final String sVersion = aOpts.has ("package-version") ? aOpts.getString ("package-version") : "*";
      final XdmNode aPackage = _findPackage (aOpts.getString ("package-name").trim (), sVersion, aHost);
      return XsltCompiler.compile (aPackage, _createCompileOptions (aOpts, aHost, FnAccessor.getBaseUri (aPackage)));
    }
    throw _invalid ("no stylesheet is supplied");
  }

  @NonNull
  private static XsltTransformRequest _createRequest (@NonNull final Options aOpts,
                                                      @NonNull final Host aHost,
                                                      @NonNull final DynamicContext aCtx)
  {
    final XsltTransformRequest ret = new XsltTransformRequest ();
    ret.setResolver (aHost.runResolver ());
    ret.setCurrentDateTime (aCtx.getCurrentDateTime ());
    if (aHost.engine () != null)
    {
      ret.setSharedBudget (aHost.engine ().getBudget ());
      ret.setInterrupt (aHost.engine ().getRequest ().getInterrupt ());
    }
    if (aOpts.has ("initial-template"))
      ret.setInitialTemplate (aOpts.getQName ("initial-template"));
    if (aOpts.has ("initial-mode"))
      ret.setInitialMode (aOpts.getQName ("initial-mode"));
    if (aOpts.has ("initial-function"))
    {
      ret.setInitialFunction (aOpts.getQName ("initial-function"));
      if (aOpts.getItem ("function-params") instanceof final XdmArray aArgs)
        ret.functionArgs ().addAll (aArgs.members ());
    }
    final XdmMap aParams = aOpts.getMap ("stylesheet-params");
    if (aParams != null)
      for (final XdmMap.Entry aEntry : aParams.entries ())
        ret.params ().put ((XdmQName) aEntry.key ().getValue (), aEntry.value ());
    for (final String sKey : new String [] { "template-params", "tunnel-params" })
    {
      final XdmMap aMap = aOpts.getMap (sKey);
      if (aMap != null)
        for (final XdmMap.Entry aEntry : aMap.entries ())
          ret.templateParams ()
             .add (new XsltTransformRequest.TemplateParam ((XdmQName) aEntry.key ().getValue (),
                                                           aEntry.value (),
                                                           "tunnel-params".equals (sKey)));
    }
    // source-node: the initial match selection, and the root of its tree is
    // the global context item
    IXdmItem aGlobalItem = null;
    if (aOpts.has ("source-node"))
    {
      final XdmNode aNode = (XdmNode) aOpts.getItem ("source-node");
      ret.setInitialMatchSelection (XdmSequence.of (aNode));
      aGlobalItem = aNode.getRoot ();
    }
    else
      if (aOpts.has ("initial-match-selection"))
        ret.setInitialMatchSelection (aOpts.get ("initial-match-selection"));
    if (aOpts.has ("global-context-item"))
      aGlobalItem = aOpts.getItem ("global-context-item");
    ret.setGlobalContextItem (aGlobalItem);
    if (aOpts.has ("base-output-uri"))
    {
      final String sBaseOutput = aOpts.getString ("base-output-uri");
      final String sResolved = _resolve (sBaseOutput, aHost.staticBaseUri ());
      ret.setBaseOutputUri (sResolved != null ? sResolved : sBaseOutput);
    }
    return ret;
  }

  /** Serialize again with serialization-params overriding the xsl:output. */
  @NonNull
  private static String _reserialize (@NonNull final XsltExecutable aExec,
                                      @NonNull final XdmSequence aValue,
                                      @Nullable final String sMethod,
                                      @NonNull final XdmMap aOverrides,
                                      @NonNull final DynamicContext aCtx)
  {
    final SerializationParams aGiven = SerializationParamsReader.fromMap (aOverrides, aCtx);
    String sEffectiveMethod = sMethod != null ? sMethod : SerializationParams.METHOD_XML;
    final XdmAtomic aMethodKey = XdmAtomic.string ("method");
    if (aOverrides.containsKey (aMethodKey))
      sEffectiveMethod = aGiven.getMethod ();
    final SerializationParams aParams;
    if (aExec instanceof final CompiledStylesheet aSheet)
    {
      final OutputDefinition aDef = aSheet.getOutput ();
      aParams = aDef.createSerializationParams (sEffectiveMethod);
      for (final IOutputCustomizer aCustomizer : XsltExtensionRegistry.getInstance ().getAllOutputCustomizers ())
        aCustomizer.customize (aSheet, aDef, aParams);
    }
    else
      aParams = new SerializationParams ().setMethod (sEffectiveMethod);
    for (final XdmAtomic aKey : aOverrides.getAllKeys ())
    {
      if (!aKey.isStringLike ())
        continue;
      switch (aKey.getString ())
      {
        case "allow-duplicate-names" -> aParams.setAllowDuplicateNames (aGiven.isAllowDuplicateNames ());
        case "byte-order-mark" -> aParams.setByteOrderMark (aGiven.isByteOrderMark ());
        case "cdata-section-elements" -> aGiven.getAllCdataSectionElements ().forEach (aParams::addCdataSectionElement);
        case "doctype-public" -> aParams.setDoctypePublic (aGiven.getDoctypePublic ());
        case "doctype-system" -> aParams.setDoctypeSystem (aGiven.getDoctypeSystem ());
        case "encoding" -> aParams.setEncoding (aGiven.getEncoding ());
        case "escape-uri-attributes" -> aParams.setEscapeUriAttributes (aGiven.isEscapeUriAttributes ());
        case "html-version" -> aParams.setHtmlVersion (aGiven.getHtmlVersion ());
        case "include-content-type" -> aParams.setIncludeContentType (aGiven.isIncludeContentType ());
        case "indent" -> aParams.setIndent (aGiven.isIndent ());
        case "item-separator" -> aParams.setItemSeparator (aGiven.getItemSeparator ());
        case "json-node-output-method" -> aParams.setJsonNodeOutputMethod (aGiven.getJsonNodeOutputMethod ());
        case "media-type" -> aParams.setMediaType (aGiven.getMediaType ());
        case "normalization-form" -> aParams.setNormalizationForm (aGiven.getNormalizationForm ());
        case "omit-xml-declaration" -> aParams.setOmitXmlDeclaration (aGiven.isOmitXmlDeclaration ());
        case "standalone" -> aParams.setStandalone (aGiven.getStandalone ());
        case "suppress-indentation" -> aGiven.getAllSuppressIndentation ().forEach (aParams::addSuppressIndentation);
        case "undeclare-prefixes" -> aParams.setUndeclarePrefixes (aGiven.isUndeclarePrefixes ());
        case "use-character-maps" -> aGiven.getCharacterMap ()
                                           .forEach ( (aCP, sRepl) -> aParams.addCharacterMapping (aCP.intValue (),
                                                                                                   sRepl));
        case "version" -> aParams.setVersion (aGiven.getVersion ());
        default ->
        {
          // method is handled above; unknown parameters are ignored
        }
      }
    }
    return XdmSerializer.serialize (aValue, aParams);
  }

  /** Shape one result document by delivery-format. */
  @NonNull
  private static XdmSequence _deliver (@NonNull final String sFormat,
                                       @NonNull final XdmSequence aRaw,
                                       @Nullable final XdmNode aTree,
                                       @NonNull final String sSerialized,
                                       @Nullable final String sMethod,
                                       @NonNull final XsltExecutable aExec,
                                       @Nullable final XdmMap aSerParams,
                                       @NonNull final DynamicContext aCtx)
  {
    switch (sFormat)
    {
      case "serialized":
        if (aSerParams != null)
          return XdmSequence.of (XdmAtomic.string (_reserialize (aExec,
                                                                 aTree != null ? XdmSequence.of (aTree) : aRaw,
                                                                 sMethod,
                                                                 aSerParams,
                                                                 aCtx)));
        return XdmSequence.of (XdmAtomic.string (sSerialized));
      case "raw":
        return aRaw;
      default:
        return aTree != null ? XdmSequence.of (aTree) : aRaw;
    }
  }

  @NonNull
  private static XdmSequence _postProcess (@Nullable final IXdmFunction aFunc,
                                           @NonNull final String sKey,
                                           @NonNull final XdmSequence aValue,
                                           @NonNull final DynamicContext aCtx)
  {
    if (aFunc == null)
      return aValue;
    final ICommonsList <XdmSequence> aArgs = new CommonsArrayList <> ();
    aArgs.add (XdmSequence.of (XdmAtomic.string (sKey)));
    aArgs.add (aValue);
    return aFunc.call (aCtx, aArgs);
  }

  @NonNull
  private static XdmMap _run (@NonNull final DynamicContext aCtx, @NonNull final XdmMap aOptions)
  {
    final Options aOpts = new Options (aOptions, aCtx);

    int nStylesheets = 0;
    for (final String sName : STYLESHEET_OPTIONS)
      if (aOpts.has (sName))
        nStylesheets++;
    if (nStylesheets > 1)
      throw _invalid ("only one of the options stylesheet-location, stylesheet-node, stylesheet-text, package-name, package-location, package-node and package-text may be given");
    if (aOpts.has ("initial-template") && aOpts.has ("initial-mode"))
      throw _invalid ("initial-template and initial-mode are mutually exclusive");
    if (aOpts.has ("source-node") && aOpts.has ("initial-match-selection"))
      throw _invalid ("source-node and initial-match-selection are mutually exclusive");
    final String sFormat = aOpts.has ("delivery-format") ? aOpts.getString ("delivery-format").trim () : "document";
    if (!"document".equals (sFormat) && !"serialized".equals (sFormat) && !"raw".equals (sFormat))
      throw _invalid ("invalid delivery-format '" + sFormat + "'");
    _checkRequestedProperties (aOpts);

    final Host aHost = _getHost (aCtx);
    final XsltExecutable aExec = _compile (aOpts, aHost);
    final XsltTransformRequest aRequest = _createRequest (aOpts, aHost, aCtx);
    aRequest.setRawDelivery ("raw".equals (sFormat));
    final XsltTransformResult aResult;
    if (aHost.engine () != null)
    {
      // The nested transformation counts as one level of the caller's depth
      final ExecutionBudget aBudget = aHost.engine ().getBudget ();
      aBudget.enterDepth ();
      try
      {
        aResult = aExec.transform (aRequest);
      }
      finally
      {
        aBudget.leaveDepth ();
      }
    }
    else
      aResult = aExec.transform (aRequest);

    final XdmMap aSerParams = aOpts.getMap ("serialization-params");
    final IXdmFunction aPostProcess = aOpts.getItem ("post-process") instanceof final IXdmFunction aFunc ? aFunc : null;
    final String sBaseOutputUri = aRequest.getBaseOutputUri ();
    final XdmMap.Builder aOut = new XdmMap.Builder ();
    // No principal result at all (everything went to xsl:result-document):
    // the map has no entry for it
    if (!aResult.principalRaw ().isEmpty ())
    {
      final String sKey = sBaseOutputUri != null ? sBaseOutputUri : KEY_OUTPUT;
      final XdmSequence aValue = _deliver (sFormat,
                                           aResult.principalRaw (),
                                           aResult.principalTree (),
                                           aResult.principalSerialized (),
                                           aResult.principalMethod (),
                                           aExec,
                                           aSerParams,
                                           aCtx);
      aOut.put (XdmAtomic.string (sKey), _postProcess (aPostProcess, sKey, aValue, aCtx));
    }
    for (final XsltTransformResult.SecondaryResult aSecondary : aResult.secondaryResults ())
    {
      // The key is the absolute URI of the document
      String sKey = _resolve (aSecondary.href (), sBaseOutputUri);
      if (sKey == null)
        sKey = aSecondary.href ();
      final XdmSequence aValue = _deliver (sFormat,
                                           aSecondary.raw (),
                                           aSecondary.tree (),
                                           aSecondary.serialized (),
                                           null,
                                           aExec,
                                           aSerParams,
                                           aCtx);
      aOut.put (XdmAtomic.string (sKey), _postProcess (aPostProcess, sKey, aValue, aCtx));
    }
    return aOut.build ();
  }

  @NonNull
  public XdmMap transform (@NonNull final DynamicContext aCtx, @NonNull final XdmMap aOptions)
  {
    final int nNesting = NESTING.get ().intValue ();
    if (nNesting >= MAX_NESTING)
      throw new XPathException ("XPDY0130", "fn:transform calls are nested more than " + MAX_NESTING + " levels deep");
    NESTING.set (Integer.valueOf (nNesting + 1));
    try
    {
      return _run (aCtx, aOptions);
    }
    finally
    {
      NESTING.set (Integer.valueOf (nNesting));
    }
  }
}
