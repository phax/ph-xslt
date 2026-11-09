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
package com.helger.phoxtrot.xslt.fn;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.eval.NodeSorter;
import com.helger.phoxtrot.xpath.fn.FnAccessor;
import com.helger.phoxtrot.xpath.fn.FnUri;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.compile.XsltElementSpecs;
import com.helger.phoxtrot.xslt.compile.XsltExtensionRegistry;
import com.helger.phoxtrot.xslt.runtime.Engine;
import com.helger.phoxtrot.xslt.runtime.NamespaceFixup;
import com.helger.phoxtrot.xslt.runtime.NodeCopier;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.schema.ValidationRequest;
import com.helger.phoxtrot.xslt.tree.XsltNames;

/**
 * The functions XSLT 3.0 adds to XPath (section 20, a port of go-xslt's
 * functions.go): system-property, function-available, element-available,
 * type-available, available-system-properties (also in static expressions),
 * and at run time current, key, document, regex-group, current-output-uri,
 * unparsed-entity-uri/-public-id, copy-of, snapshot and stream-available.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsltFunctions
{
  /** The system properties in the XSLT namespace. */
  public static final String [] SYSTEM_PROPERTIES = { "version",
                                                      "vendor",
                                                      "vendor-url",
                                                      "product-name",
                                                      "product-version",
                                                      "is-schema-aware",
                                                      "supports-serialization",
                                                      "supports-backwards-compatibility",
                                                      "supports-namespace-axis",
                                                      "supports-streaming",
                                                      "supports-dynamic-evaluation",
                                                      "supports-higher-order-functions",
                                                      "xpath-version",
                                                      "xsd-version" };

  private static final String NS = XdmConstants.NS_FN;

  @PresentForCodeCoverage
  private static final XsltFunctions INSTANCE = new XsltFunctions ();

  private XsltFunctions ()
  {}

  /**
   * Resolve a lexical QName or EQName argument against the static context.
   *
   * @return <code>null</code> if invalid or the prefix is unbound.
   */
  @Nullable
  private static XdmQName _resolveName (@NonNull final StaticContext aSC,
                                        @NonNull final String sName,
                                        @NonNull final String sDefaultNamespace)
  {
    final String s = XsltNames.trimXmlWhitespace (sName);
    if (s.startsWith ("Q{"))
    {
      if (!XsltNames.isEQName (s))
        return null;
      final int n = s.indexOf ('}');
      return new XdmQName (s.substring (2, n), s.substring (n + 1));
    }
    if (!XsltNames.isLexicalQName (s))
      return null;
    final int n = s.indexOf (':');
    if (n < 0)
      return new XdmQName (sDefaultNamespace, s);
    final String sPrefix = s.substring (0, n);
    final String sUri = "xml".equals (sPrefix) ? XdmConstants.NS_XML : aSC.getNamespaceUri (sPrefix);
    if (sUri == null)
      return null;
    return new XdmQName (sUri, s.substring (n + 1), sPrefix);
  }

  /**
   * @param aSeq
   *        An <code>xs:string?</code> argument.
   * @return The string value of its first item, or the empty string for an
   *         empty sequence.
   */
  @NonNull
  private static String _getString (@NonNull final XdmSequence aSeq)
  {
    return aSeq.isEmpty () ? "" : ((XdmAtomic) aSeq.get (0)).getString ();
  }

  /**
   * @param sLocalName
   *        Local name of a property in the XSLT namespace.
   * @return The value of the system property, empty if unknown.
   */
  @NonNull
  public static String getSystemProperty (@NonNull final String sLocalName)
  {
    return switch (sLocalName)
    {
      case "version" -> "3.0";
      case "vendor" -> "Philip Helger";
      case "product-name" -> "phoxtrot";
      case "vendor-url" -> "https://github.com/phax/phoxtrot";
      case "product-version" -> "0.1";
      case "is-schema-aware", "supports-backwards-compatibility", "supports-streaming", "supports-dynamic-evaluation" -> XsltExtensionRegistry.getInstance ()
                                                                                                                                              .isSystemPropertyYes (sLocalName) ? "yes"
                                                                                                                                                                                : "no";
      case "supports-serialization", "supports-namespace-axis", "supports-higher-order-functions" -> "yes";
      case "xpath-version" -> "3.1";
      case "xsd-version" -> StaticContext.DEFAULT_XSD_VERSION.getID ();
      default -> "";
    };
  }

  /**
   * fn:system-property (XSLT 3.0 section 20.4.4). The argument is resolved as
   * a QName or EQName (an unprefixed name is in no namespace). A property in
   * the XSLT namespace gets its value from {@link #getSystemProperty}, with
   * three overrides that depend on the stylesheet: is-schema-aware is "yes"
   * exactly when the static context has a schema set, xsd-version is the XSD
   * version of the built-in datatypes of the static context (the native schema
   * provider still compiles imported schemas as XSD 1.1),
   * supports-streaming is "yes" when the stylesheet was compiled with the
   * streaming claim, and supports-backwards-compatibility is "yes" when it
   * was compiled with the backwards compatibility claim (section 20.4.4: the
   * processor offers the XSLT 1.0 compatibility feature). Any other name gives
   * the empty string.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        The property name.
   * @param bStreamingClaim
   *        <code>true</code> for a stylesheet compiled with the streaming
   *        claim.
   * @param bBackwardsCompatibleClaim
   *        <code>true</code> for a stylesheet compiled with the backwards
   *        compatibility claim.
   * @return The property value as a single <code>xs:string</code>.
   * @throws XPathException
   *         XTDE1390 if the name is not a valid QName or its prefix is
   *         unbound.
   */
  @NonNull
  private static XdmSequence _systemProperty (@NonNull final DynamicContext aCtx,
                                              @NonNull final List <XdmSequence> aArgs,
                                              final boolean bStreamingClaim,
                                              final boolean bBackwardsCompatibleClaim)
  {
    final String sName = _getString (aArgs.get (0));
    final XdmQName aName = _resolveName (aCtx.getStaticContext (), sName, "");
    if (aName == null)
      throw new XPathException ("XTDE1390", "'" + sName + "' is not a valid QName with a bound prefix");
    String sValue = XsltNames.NS_XSL.equals (aName.namespaceUri ()) ? getSystemProperty (aName.localName ()) : "";
    // Schema-awareness is a property of the stylesheet: its static contexts
    // carry the in-scope schema definitions
    if (XsltNames.NS_XSL.equals (aName.namespaceUri ()) && "is-schema-aware".equals (aName.localName ()))
      sValue = aCtx.getStaticContext ().getSchemaSet () != null ? "yes" : "no";
    // The XSD version of the built-in datatypes is the one of the static
    // context (schemas compiled by the schema provider may use another one)
    if (XsltNames.NS_XSL.equals (aName.namespaceUri ()) && "xsd-version".equals (aName.localName ()))
      sValue = aCtx.getStaticContext ().getXsdVersion ().getID ();
    // So is the streaming claim (XsltCompileOptions.setStreamingClaim)
    if (bStreamingClaim && XsltNames.NS_XSL.equals (aName.namespaceUri ()) && "supports-streaming".equals (aName.localName ()))
      sValue = "yes";
    // And the backwards compatibility claim
    // (XsltCompileOptions.setBackwardsCompatibleClaim)
    if (bBackwardsCompatibleClaim &&
        XsltNames.NS_XSL.equals (aName.namespaceUri ()) &&
        "supports-backwards-compatibility".equals (aName.localName ()))
      sValue = "yes";
    return XdmSequence.of (XdmAtomic.string (sValue));
  }

  /**
   * fn:function-available (XSLT 3.0 section 24.1.1). An unprefixed name is in
   * the default function namespace. With an arity argument the function is
   * looked up by name and arity in the function library of the static
   * context (a negative arity or one of 10000 or more is never available);
   * without it, any arity counts.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        The function name and the optional arity.
   * @return <code>true</code> if such a function is in the library.
   * @throws XPathException
   *         XTDE1400 if the name is not a valid EQName or its prefix is
   *         unbound.
   */
  @NonNull
  private static XdmSequence _functionAvailable (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final String sName = _getString (aArgs.get (0));
    final StaticContext aSC = aCtx.getStaticContext ();
    final XdmQName aName = _resolveName (aSC, sName, aSC.getDefaultFunctionNamespace ());
    if (aName == null)
      throw new XPathException ("XTDE1400", "'" + sName + "' is not a valid QName with a bound prefix");
    final FunctionLibrary aLib = aSC.getFunctionLibrary ();
    final boolean bRet;
    if (aArgs.size () > 1)
    {
      final long nArity = ((XdmAtomic) aArgs.get (1).get (0)).getBigInteger ().longValue ();
      bRet = nArity >= 0 && nArity < 10_000 && aLib.getFunction (aName, (int) nArity) != null;
    }
    else
      bRet = aLib.containsName (aName);
    return XdmSequence.of (XdmAtomic.bool (bRet));
  }

  /**
   * fn:element-available (XSLT 3.0 section 24.2.2). An unprefixed name is in
   * the default namespace in scope (xmlns). Only names in the XSLT namespace
   * can be available: every element known to {@link XsltElementSpecs}
   * (declarations and other non-instructions included) except
   * xsl:import-schema when the stylesheet is not schema-aware, and every
   * instruction or declaration registered by an extension.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        The element name.
   * @return <code>true</code> if the element is available.
   * @throws XPathException
   *         XTDE1440 if the name is not a valid EQName or its prefix is
   *         unbound.
   */
  @NonNull
  private static XdmSequence _elementAvailable (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final String sName = _getString (aArgs.get (0));
    final StaticContext aSC = aCtx.getStaticContext ();
    // XSLT 3.0 section 24.2.2: an unprefixed name is in the default namespace
    // in scope (xmlns), not in the default element namespace
    final XdmQName aName = _resolveName (aSC, sName, aSC.getInScopeDefaultNamespace ());
    if (aName == null)
      throw new XPathException ("XTDE1440", "'" + sName + "' is not a valid QName with a bound prefix");
    // Every XSLT element is available (catalog-006 checks all names used in
    // the suite, xsl:transform and xsl:when included), except
    // xsl:import-schema, as this processor is not schema-aware
    final String sLocal = aName.localName ();
    final boolean bSchemaAware = aSC.getSchemaSet () != null;
    final boolean bRet = XsltNames.NS_XSL.equals (aName.namespaceUri ()) &&
                         ((XsltElementSpecs.isKnown (sLocal) && (bSchemaAware || !"import-schema".equals (sLocal))) ||
                          XsltExtensionRegistry.getInstance ().isImplemented (sLocal));
    return XdmSequence.of (XdmAtomic.bool (bRet));
  }

  /**
   * fn:type-available (XSLT 3.0 section 24.1.4). An unprefixed name is in the
   * default element/type namespace. In the XML Schema namespace, the built-in
   * atomic types (not the internal numeric and error pseudo types) and
   * xs:anyType, xs:anySimpleType, xs:untyped, xs:NMTOKENS, xs:ENTITIES and
   * xs:IDREFS are available. A name in any other namespace is available if
   * the stylesheet is schema-aware and its imported schemas define a type of
   * that name.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        The type name.
   * @return <code>true</code> if the type is available.
   * @throws XPathException
   *         XTDE1428 if the name is not a valid EQName or its prefix is
   *         unbound.
   */
  @NonNull
  private static XdmSequence _typeAvailable (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final String sName = _getString (aArgs.get (0));
    final StaticContext aSC = aCtx.getStaticContext ();
    final XdmQName aName = _resolveName (aSC, sName, aSC.getDefaultElementNamespace ());
    if (aName == null)
      throw new XPathException ("XTDE1428", "'" + sName + "' is not a valid QName with a bound prefix");
    boolean bRet = false;
    if (XdmConstants.NS_XS.equals (aName.namespaceUri ()))
      switch (aName.localName ())
      {
        case "anyType", "anySimpleType", "untyped", "NMTOKENS", "ENTITIES", "IDREFS":
          bRet = true;
          break;
        default:
          final EAtomicType eType = EAtomicType.getFromLocalName (aName.localName ());
          bRet = eType != null && eType != EAtomicType.NUMERIC && eType != EAtomicType.ERROR;
          break;
      }
    else
      if (aSC.getSchemaSet () != null)
      {
        // A type of the imported schemas
        bRet = aSC.getSchemaSet ().getTypeDefinition (aName) != null;
      }
    return XdmSequence.of (XdmAtomic.bool (bRet));
  }

  /**
   * fn:available-system-properties (XSLT 3.0 section 20.4.5).
   *
   * @param aCtx
   *        Dynamic context of the call (unused).
   * @param aArgs
   *        No arguments.
   * @return The names of {@link #SYSTEM_PROPERTIES} as
   *         <code>xs:QName</code> values in the XSLT namespace with the prefix
   *         xsl.
   */
  @NonNull
  private static XdmSequence _availableSystemProperties (@NonNull final DynamicContext aCtx,
                                                         @NonNull final List <XdmSequence> aArgs)
  {
    final ICommonsList <IXdmItem> ret = new CommonsArrayList <> ();
    for (final String s : SYSTEM_PROPERTIES)
      ret.add (XdmAtomic.qname (new XdmQName (XsltNames.NS_XSL, s, "xsl")));
    return XdmSequence.of (ret);
  }

  /**
   * Register the functions available in static expressions (use-when, static
   * variables, shadow attributes).
   *
   * @param aLib
   *        Library.
   */
  public static void registerStatic (@NonNull final FunctionLibrary aLib)
  {
    aLib.register (NS,
                   "system-property",
                   "(xs:string) as xs:string",
                   (aRtCtx, aFnArgs) -> _systemProperty (aRtCtx, aFnArgs, false, false));
    aLib.register (NS, "function-available", "(xs:string) as xs:boolean", XsltFunctions::_functionAvailable);
    aLib.register (NS, "function-available", "(xs:string, xs:integer) as xs:boolean", XsltFunctions::_functionAvailable);
    aLib.register (NS, "element-available", "(xs:string) as xs:boolean", XsltFunctions::_elementAvailable);
    aLib.register (NS, "type-available", "(xs:string) as xs:boolean", XsltFunctions::_typeAvailable);
    aLib.register (NS, "available-system-properties", "() as xs:QName*", XsltFunctions::_availableSystemProperties);
  }

  /**
   * Get the XSLT context behind a dynamic context, for the functions that
   * need the running transformation.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param sFunction
   *        Local name of the function, for the error message.
   * @return The XSLT context. Never <code>null</code>.
   * @throws XPathException
   *         XPST0017 if the call is not evaluated during a transformation
   *         (e.g. in a static expression).
   */
  @NonNull
  private static XsltContext _xslt (@NonNull final DynamicContext aCtx, @NonNull final String sFunction)
  {
    final XsltContext ret = XsltContext.of (aCtx);
    if (ret == null)
      throw new XPathException ("XPST0017", "The function " + sFunction + "() is only available during a transformation");
    return ret;
  }

  /**
   * fn:current (XSLT 3.0 section 20.4.1): the item that was the context item
   * when the outermost XPath expression of the instruction or pattern
   * started.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        No arguments.
   * @return The current item.
   * @throws XPathException
   *         XTDE1360 if the current item is absent.
   */
  @NonNull
  private static XdmSequence _current (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final IXdmItem aItem = aCtx.getCurrentItem ();
    if (aItem == null)
      throw new XPathException ("XTDE1360", "current() is called while the current item is absent");
    return XdmSequence.of (aItem);
  }

  /**
   * fn:key (XSLT 3.0 section 20.2.2). The key name is resolved as a QName or
   * EQName (an unprefixed name is in no namespace). The tree searched is
   * that of the third argument, of which only the descendants-or-self are
   * returned, or with two arguments the whole tree of the context node. The
   * lookup itself, including the error for an undeclared key, is done by
   * {@link Engine#findKeyNodes}.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        Key name, key values and the optional top node.
   * @return The matching nodes in document order.
   * @throws XPathException
   *         XTDE1260 if the name is not a valid QName or its prefix is
   *         unbound; XTDE1270 if the tree is not rooted at a document node or
   *         (two arguments) the context item is absent; XPTY0004 if the
   *         context item is not a node.
   */
  @NonNull
  private static XdmSequence _key (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XsltContext aXCtx = _xslt (aCtx, "key");
    final String sName = _getString (aArgs.get (0));
    final XdmQName aName = _resolveName (aCtx.getStaticContext (), sName, "");
    if (aName == null)
      throw new XPathException ("XTDE1260", "'" + sName + "' is not a valid key name");
    final IXdmNode aTop;
    if (aArgs.size () > 2)
    {
      aTop = (IXdmNode) aArgs.get (2).get (0);
      if (!aTop.getRoot ().isDocument ())
        throw new XPathException ("XTDE1270", "The third argument of key() is not in a tree rooted at a document node");
    }
    else
    {
      if (!(aCtx.getContextItem () instanceof final IXdmNode aNode))
        throw new XPathException (aCtx.getContextItem () == null ? "XTDE1270" : "XPTY0004",
                                  "key() with two arguments requires a node as context item");
      aTop = aNode.getRoot ();
      if (!aTop.isDocument ())
        throw new XPathException ("XTDE1270", "key() with two arguments requires the context node to be in a tree rooted at a document node");
    }
    return aXCtx.getEngine ().findKeyNodes (aXCtx, aName, aArgs.get (1), aTop);
  }

  /**
   * fn:document (XSLT 3.0 section 20.1). Each item of the first argument
   * gives URI references: a node is atomized and each atomic value is one
   * reference, resolved against the base URI of that node; any other item
   * is one reference, resolved against the static base URI. A second
   * argument replaces these bases with its base URI. A reference is trimmed
   * and converted from an IRI; an absolute reference is used as it is, a
   * relative one is resolved when the base is absolute (and used as it is
   * with a relative base). Each URI is loaded through the
   * document cache of the engine (so repeated calls return the same node);
   * a fragment identifier that selects nothing contributes no node. The
   * result is in document order without duplicates.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        The URI references and the optional base node.
   * @return The documents (or fragment nodes), sorted and deduplicated.
   * @throws XPathException
   *         XTDE1162 if a relative reference has no base URI to be resolved
   *         against (XSLT 3.0 section 20.1); FODC0005 if a reference cannot
   *         be resolved; FODC0002 from the engine if a document cannot be
   *         retrieved.
   */
  @NonNull
  private static XdmSequence _document (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XsltContext aXCtx = _xslt (aCtx, "document");
    final Engine aEngine = aXCtx.getEngine ();
    final boolean bFixedBase = aArgs.size () > 1;
    final String sFixedBase = bFixedBase ? FnAccessor.getBaseUri ((IXdmNode) aArgs.get (1).get (0)) : null;
    final XdmSequence.Builder aResult = new XdmSequence.Builder ();
    for (final IXdmItem aItem : aArgs.get (0))
    {
      final String sBase;
      final ICommonsList <String> aRefs = new CommonsArrayList <> ();
      if (aItem instanceof final IXdmNode aNode)
      {
        sBase = bFixedBase ? sFixedBase : FnAccessor.getBaseUri (aNode);
        for (final IXdmItem aAtom : Atomizer.atomize (XdmSequence.of (aNode)))
          aRefs.add (Atomizer.getStringValue (aAtom));
      }
      else
      {
        sBase = bFixedBase ? sFixedBase : aCtx.getStaticContext ().getBaseUri ();
        aRefs.add (Atomizer.getStringValue (aItem));
      }
      for (final String sRef : aRefs)
      {
        final String sTrimmed = FnUri.iriToUri (sRef.trim ());
        if (sBase == null && FnUri.isValidUriReference (sTrimmed) && !FnUri.isAbsoluteUri (sTrimmed))
          throw new XPathException ("XTDE1162", "The relative URI reference '" + sTrimmed + "' in document() has no base URI");
        String sUri;
        try
        {
          sUri = sBase != null && FnUri.isAbsoluteUri (sBase) ? FnUri.resolveUri (sTrimmed, sBase) : sTrimmed;
        }
        catch (final XPathException ex)
        {
          throw new XPathException ("FODC0005", "Invalid URI '" + sTrimmed + "' in document()");
        }
        final IXdmNode aDoc = aEngine.getDocument (aXCtx, sUri);
        if (aDoc != null)
          aResult.add (aDoc);
      }
    }
    return NodeSorter.sortAndDeduplicate (aResult.build ());
  }

  /**
   * fn:regex-group (XSLT 3.0 section 17.2): the captured substring of group
   * n of the current xsl:analyze-string match; group 0 is the whole match.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        The group number.
   * @return The captured substring, or the empty string outside a
   *         transformation, outside xsl:matching-substring, for a group
   *         number out of range or for a group that did not participate.
   */
  @NonNull
  private static XdmSequence _regexGroup (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XsltContext aXCtx = XsltContext.of (aCtx);
    final String [] aGroups = aXCtx == null ? null : aXCtx.getState ().getRegexGroups ();
    final long n = ((XdmAtomic) aArgs.get (0).get (0)).getBigInteger ().longValue ();
    if (aGroups == null || n < 0 || n >= aGroups.length || aGroups[(int) n] == null)
      return XdmSequence.of (XdmAtomic.string (""));
    return XdmSequence.of (XdmAtomic.string (aGroups[(int) n]));
  }

  /**
   * fn:current-output-uri (XSLT 3.0 section 25.3.1): the absolute URI of the
   * result being written, as set by xsl:result-document or the transformation
   * start.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        No arguments.
   * @return The URI as <code>xs:anyURI</code>, or the empty sequence outside a
   *         transformation or when the current output URI is absent or empty
   *         (e.g. in temporary output state).
   */
  @NonNull
  private static XdmSequence _currentOutputUri (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XsltContext aXCtx = XsltContext.of (aCtx);
    if (aXCtx == null)
      return XdmSequence.EMPTY;
    final String sUri = aXCtx.get (Engine.CURRENT_OUTPUT_URI);
    return sUri == null || sUri.isEmpty () ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.anyUri (sUri));
  }

  /**
   * fn:unparsed-entity-uri and fn:unparsed-entity-public-id (XSLT 3.0
   * sections 20.4.2 and 20.4.3): look up an unparsed entity declared in the
   * DTD of the document that contains the given node (or the context node).
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        The entity name and the optional node.
   * @param bPublicId
   *        <code>true</code> for unparsed-entity-public-id,
   *        <code>false</code> for unparsed-entity-uri.
   * @return The system identifier as <code>xs:anyURI</code>, or the public
   *         identifier as <code>xs:string</code>; a zero-length value if
   *         there is no such entity or (public ID) it has no public
   *         identifier.
   * @throws XPathException
   *         XTDE1370 (uri) or XTDE1380 (public ID) if the node is not in a
   *         tree rooted at a document node, and also, with one argument, if
   *         the context item is absent (the W3C test cases error-1370a and
   *         error-1380a expect this code; section 20.4.2 allows XPDY0002);
   *         XPTY0004 with one argument if the context item is not a node.
   */
  @NonNull
  private static XdmSequence _unparsedEntity (@NonNull final DynamicContext aCtx,
                                              @NonNull final List <XdmSequence> aArgs,
                                              final boolean bPublicId)
  {
    final String sCode = bPublicId ? "XTDE1380" : "XTDE1370";
    final IXdmNode aNode;
    if (aArgs.size () > 1)
      aNode = (IXdmNode) aArgs.get (1).get (0);
    else
    {
      final IXdmItem aCtxItem = aCtx.getContextItem ();
      if (aCtxItem == null)
        throw new XPathException (sCode, "The function requires a context node, but the context item is absent");
      if (!(aCtxItem instanceof final IXdmNode aCtxNode))
        throw new XPathException ("XPTY0004", "The function requires a node as context item");
      aNode = aCtxNode;
    }
    final IXdmNode aRoot = aNode.getRoot ();
    if (!aRoot.isDocument ())
      throw new XPathException (sCode, "The node is not in a tree rooted at a document node");
    final String [] aEntity = aRoot.getUnparsedEntity (_getString (aArgs.get (0)));
    if (aEntity == null)
      return XdmSequence.of (bPublicId ? XdmAtomic.string ("") : XdmAtomic.anyUri (""));
    if (bPublicId)
      return XdmSequence.of (XdmAtomic.string (aEntity[1] == null ? "" : aEntity[1]));
    return XdmSequence.of (XdmAtomic.anyUri (aEntity[0]));
  }

  /**
   * fn:copy-of (XSLT 3.0 section 18.3): every node of the input (or of the
   * context item without an argument) is replaced by a deep copy that keeps
   * namespaces and type annotations and becomes the root of a new tree with
   * fixed-up namespaces and fresh document order; other items are returned
   * unchanged. Copying an attribute checks XTTE0950 first.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        The optional input sequence.
   * @return The copied sequence, in input order.
   * @throws XPathException
   *         XPDY0002 if there is no argument and the context item is absent.
   */
  @NonNull
  private static XdmSequence _copyOf (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aInput;
    if (aArgs.isEmpty ())
    {
      if (aCtx.getContextItem () == null)
        throw new XPathException ("XPDY0002", "The context item is absent");
      aInput = XdmSequence.of (aCtx.getContextItem ());
    }
    else
      aInput = aArgs.get (0);
    final XdmSequence.Builder ret = new XdmSequence.Builder ();
    for (final IXdmItem aItem : aInput)
      if (aItem instanceof final IXdmNode aNode)
      {
        if (aNode.isAttribute ())
          ValidationRequest.checkPreserveCopy (aNode, true);
        final IXdmNode aCopy = NodeCopier.copyDeep (aNode, true, true);
        NamespaceFixup.finish (aCopy);
        ret.add (aCopy);
      }
      else
        ret.add (aItem);
    return ret.build ();
  }

  /**
   * Snapshot one node (XSLT 3.0 section 18.4): the node is copied deeply and
   * its ancestors shallowly (each with its attributes and namespaces, the
   * document node with its unparsed entities), so that the copy keeps its
   * ancestry. For an attribute or namespace node, its parent element is
   * copied without children but with all its attributes and namespaces and
   * its base URI, and the matching attribute (by name) or namespace node (by
   * name and value) of that copy is returned. A parentless node is simply
   * copied deeply. The new tree gets fixed-up namespaces and fresh document
   * order.
   *
   * @param aNode
   *        The node to snapshot.
   * @return The copy of the node inside the new tree. Never
   *         <code>null</code> (falls back to the parent copy if the namespace
   *         node is not found).
   */
  @NonNull
  private static IXdmNode _snapshotNode (@NonNull final IXdmNode aNode)
  {
    // Copy the ancestors shallowly (with their attributes and namespaces) and
    // the node deeply; return the copy of the node
    final ICommonsList <IXdmNode> aAncestors = new CommonsArrayList <> ();
    final IXdmNode aStart = aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE ? aNode.getParent ()
                                                                                                             : aNode;
    if (aStart == null)
    {
      final IXdmNode ret = NodeCopier.copyDeep (aNode, true, true);
      NamespaceFixup.finish (ret);
      return ret;
    }
    for (IXdmNode aCur = aStart.getParent (); aCur != null; aCur = aCur.getParent ())
      aAncestors.add (0, aCur);
    IXdmNode aParentCopy = null;
    IXdmNode aRoot = null;
    for (final IXdmNode aAnc : aAncestors)
    {
      final IXdmNode aCopy = NodeCopier.copyShallow (aAnc);
      // The copied document node keeps the unparsed entities, as with a deep
      // copy
      NodeCopier.copyUnparsedEntities (aAnc, aCopy);
      if (aAnc.isElement ())
      {
        NodeCopier.copyNamespaces (aAnc, aCopy, aParentCopy == null);
        for (int i = 0; i < aAnc.getAttributeCount (); ++i)
          aCopy.addAttribute (NodeCopier.copyShallow (aAnc.getAttributeAtIndex (i)));
      }
      if (aParentCopy != null)
        aParentCopy.appendChild (aCopy);
      else
        aRoot = aCopy;
      aParentCopy = aCopy;
    }
    final IXdmNode aStartCopy;
    if (aStart == aNode)
      aStartCopy = NodeCopier.copyDeep (aStart, true, true);
    else
    {
      // An attribute or namespace node is grafted to a copy of its parent
      // that has the parent's attributes and namespaces, but no children
      aStartCopy = NodeCopier.copyShallow (aStart);
      NodeCopier.copyNamespaces (aStart, aStartCopy, aParentCopy == null);
      for (int i = 0; i < aStart.getAttributeCount (); ++i)
        aStartCopy.addAttribute (NodeCopier.copyShallow (aStart.getAttributeAtIndex (i)));
      final String sBase = FnAccessor.getBaseUri (aStart);
      if (sBase != null)
        aStartCopy.setBaseUri (sBase);
    }
    if (aParentCopy != null)
      aParentCopy.appendChild (aStartCopy);
    else
      aRoot = aStartCopy;
    NamespaceFixup.finish (aRoot);
    if (aStart == aNode)
      return aStartCopy;
    if (aNode.isAttribute ())
      return aStartCopy.getAttribute (aNode.getName ().namespaceUri (), aNode.getName ().localName ());
    for (final IXdmNode aNS : aStartCopy.getNamespaceNodes ())
      if (aNS.getStringValue ().equals (aNode.getStringValue ()) && Objects.equals (aNS.getName (), aNode.getName ()))
        return aNS;
    return aStartCopy;
  }

  /**
   * fn:snapshot (XSLT 3.0 section 18.4): every node of the input (or of the
   * context item without an argument) is replaced by its snapshot (see
   * {@link #_snapshotNode}); other items are returned unchanged.
   *
   * @param aCtx
   *        Dynamic context of the call.
   * @param aArgs
   *        The optional input sequence.
   * @return The snapshot sequence, in input order.
   * @throws XPathException
   *         XPDY0002 if there is no argument and the context item is absent.
   */
  @NonNull
  private static XdmSequence _snapshot (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aInput;
    if (aArgs.isEmpty ())
    {
      if (aCtx.getContextItem () == null)
        throw new XPathException ("XPDY0002", "The context item is absent");
      aInput = XdmSequence.of (aCtx.getContextItem ());
    }
    else
      aInput = aArgs.get (0);
    final XdmSequence.Builder ret = new XdmSequence.Builder ();
    for (final IXdmItem aItem : aInput)
      ret.add (aItem instanceof final IXdmNode aNode ? _snapshotNode (aNode) : aItem);
    return ret.build ();
  }

  /**
   * @return the index just past the DOCTYPE declaration beginning at nStart,
   *         including any bracketed internal subset, or -1 if it is
   *         unterminated.
   */
  private static int _skipDoctype (@NonNull final String s, final int nStart)
  {
    int nDepth = 0;
    for (int i = nStart; i < s.length (); ++i)
      switch (s.charAt (i))
      {
        case '[':
          nDepth++;
          break;
        case ']':
          nDepth--;
          break;
        case '>':
          if (nDepth <= 0)
            return i + 1;
          break;
        default:
          break;
      }
    return -1;
  }

  /**
   * Whether the text, after its XML prolog (BOM, whitespace, XML declaration,
   * processing instructions, comments and a DOCTYPE declaration with any
   * internal subset), begins with an element start tag. Only the prolog is
   * inspected: whether the rest of the document is well-formed is exactly what
   * a streaming processor cannot know up front.
   */
  private static boolean _startsWithXmlElement (@NonNull final String sText)
  {
    final String s = sText.startsWith ("﻿") ? sText.substring (1) : sText;
    int i = 0;
    while (true)
    {
      while (i < s.length () && (s.charAt (i) == ' ' || s.charAt (i) == '\t' || s.charAt (i) == '\n' || s.charAt (i) == '\r'))
        i++;
      if (i >= s.length () || s.charAt (i) != '<')
        return false;
      if (s.startsWith ("<?", i))
      {
        final int j = s.indexOf ("?>", i);
        if (j < 0)
          return false;
        i = j + 2;
      }
      else
        if (s.startsWith ("<!--", i))
        {
          final int j = s.indexOf ("-->", i + 4);
          if (j < 0)
            return false;
          i = j + 3;
        }
        else
          if (s.startsWith ("<!DOCTYPE", i))
          {
            i = _skipDoctype (s, i);
            if (i < 0)
              return false;
          }
          else
          {
            if (i + 1 >= s.length ())
              return false;
            final char c = s.charAt (i + 1);
            return c == '_' || c == ':' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c >= 0x80;
          }
    }
  }

  /**
   * fn:stream-available (XSLT 3.0 section 18.1.3): true when the resource can
   * be retrieved and begins as well-formed XML. A truncated document or one
   * with two top-level elements still starts streamably; a missing file, a
   * non-XML file or one that is nothing but a DTD does not.
   */
  @NonNull
  private static XdmSequence _streamAvailable (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.of (XdmAtomic.bool (false));
    final String sHref = Atomizer.getStringValue (aArgs.get (0).get (0));
    if (sHref.isEmpty () || !FnUri.isValidUriReference (sHref))
      return XdmSequence.of (XdmAtomic.bool (false));
    final String sUri;
    if (FnUri.isAbsoluteUri (sHref))
      sUri = sHref;
    else
    {
      final String sBase = aCtx.getStaticContext ().getBaseUri ();
      if (sBase == null || !FnUri.isAbsoluteUri (sBase))
        return XdmSequence.of (XdmAtomic.bool (false));
      sUri = FnUri.resolveUri (sHref, sBase);
    }
    String sText;
    try
    {
      sText = aCtx.getResourceResolver ().resolveText (sUri, null);
    }
    catch (final XPathException ex)
    {
      sText = null;
    }
    return XdmSequence.of (XdmAtomic.bool (sText != null && _startsWithXmlElement (sText)));
  }

  /**
   * Replace <code>system-property</code> in a library that already has the
   * static functions by one for a stylesheet compiled with the streaming
   * claim or the backwards compatibility claim:
   * <code>system-property('xsl:supports-streaming')</code> resp.
   * <code>system-property('xsl:supports-backwards-compatibility')</code> then
   * reports "yes". Without any claim the library is not changed.
   *
   * @param aLib
   *        Library of a stylesheet.
   * @param bStreamingClaim
   *        <code>true</code> if the stylesheet is compiled with the streaming
   *        claim.
   * @param bBackwardsCompatibleClaim
   *        <code>true</code> if the stylesheet is compiled with the backwards
   *        compatibility claim.
   */
  public static void registerClaims (@NonNull final FunctionLibrary aLib,
                                     final boolean bStreamingClaim,
                                     final boolean bBackwardsCompatibleClaim)
  {
    if (bStreamingClaim || bBackwardsCompatibleClaim)
      aLib.register (NS,
                     "system-property",
                     "(xs:string) as xs:string",
                     (aRtCtx, aFnArgs) -> _systemProperty (aRtCtx, aFnArgs, bStreamingClaim, bBackwardsCompatibleClaim));
  }

  /**
   * Register the XSLT functions available during a transformation (including
   * the static ones).
   *
   * @param aLib
   *        Library of a stylesheet.
   */
  public static void registerRuntime (@NonNull final FunctionLibrary aLib)
  {
    registerStatic (aLib);
    aLib.register (NS, "current", "() as item()", XsltFunctions::_current, true);
    aLib.register (NS, "key", "(xs:string, xs:anyAtomicType*) as node()*", XsltFunctions::_key, true);
    aLib.register (NS, "key", "(xs:string, xs:anyAtomicType*, node()) as node()*", XsltFunctions::_key);
    aLib.register (NS, "document", "(item()*) as node()*", XsltFunctions::_document);
    aLib.register (NS, "document", "(item()*, node()) as node()*", XsltFunctions::_document);
    // A dynamic call (regex-group#1) has no captured substrings (XSLT 3.0
    // section 5.3.4), whatever xsl:analyze-string encloses the call
    aLib.register (NS,
                   "regex-group",
                   "(xs:integer) as xs:string",
                   XsltFunctions::_regexGroup,
                   false,
                   (aRtCtx, aFnArgs) -> XdmSequence.of (XdmAtomic.string ("")));
    aLib.register (NS, "current-output-uri", "() as xs:anyURI?", XsltFunctions::_currentOutputUri, true);
    aLib.register (NS, "unparsed-entity-uri", "(xs:string) as xs:anyURI", (aRtCtx, aFnArgs) -> _unparsedEntity (aRtCtx, aFnArgs, false), true);
    aLib.register (NS, "unparsed-entity-uri", "(xs:string, node()) as xs:anyURI", (aRtCtx, aFnArgs) -> _unparsedEntity (aRtCtx, aFnArgs, false));
    aLib.register (NS, "unparsed-entity-public-id", "(xs:string) as xs:string", (aRtCtx, aFnArgs) -> _unparsedEntity (aRtCtx, aFnArgs, true), true);
    aLib.register (NS, "unparsed-entity-public-id", "(xs:string, node()) as xs:string", (aRtCtx, aFnArgs) -> _unparsedEntity (aRtCtx, aFnArgs, true));
    aLib.register (NS, "copy-of", "() as item()*", XsltFunctions::_copyOf, true);
    aLib.register (NS, "copy-of", "(item()*) as item()*", XsltFunctions::_copyOf);
    aLib.register (NS, "snapshot", "() as item()*", XsltFunctions::_snapshot, true);
    aLib.register (NS, "snapshot", "(item()*) as item()*", XsltFunctions::_snapshot);
    aLib.register (NS, "stream-available", "(xs:string?) as xs:boolean", XsltFunctions::_streamAvailable);
  }
}
