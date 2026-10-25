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
package com.helger.xslt.fn;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.NodeSorter;
import com.helger.xpath.fn.FnAccessor;
import com.helger.xpath.fn.FnUri;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.runtime.Engine;
import com.helger.xslt.runtime.NamespaceFixup;
import com.helger.xslt.runtime.NodeCopier;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XsltNames;

/**
 * The functions XSLT 3.0 adds to XPath (section 20, a port of go-xslt's
 * functions.go): system-property, function-available, element-available,
 * type-available, available-system-properties (also in static expressions),
 * and at run time current, key, document, regex-group, current-output-uri,
 * unparsed-entity-uri/-public-id, copy-of and snapshot.
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
      case "vendor", "product-name" -> "ph-xslt";
      case "vendor-url" -> "https://github.com/phax/ph-xslt";
      case "product-version" -> "0.1";
      case "is-schema-aware", "supports-backwards-compatibility", "supports-streaming", "supports-dynamic-evaluation" -> XsltExtensionRegistry.getInstance ()
                                                                                                                                              .isSystemPropertyYes (sLocalName) ? "yes"
                                                                                                                                                                                : "no";
      case "supports-serialization", "supports-namespace-axis", "supports-higher-order-functions" -> "yes";
      case "xpath-version" -> "3.1";
      case "xsd-version" -> "1.0";
      default -> "";
    };
  }

  @NonNull
  private static XdmSequence _systemProperty (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final String sName = _getString (aArgs.get (0));
    final XdmQName aName = _resolveName (aCtx.getStaticContext (), sName, "");
    if (aName == null)
      throw new XPathException ("XTDE1390", "'" + sName + "' is not a valid QName with a bound prefix");
    final String sValue = XsltNames.NS_XSL.equals (aName.namespaceUri ()) ? getSystemProperty (aName.localName ()) : "";
    return XdmSequence.of (XdmAtomic.string (sValue));
  }

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

  @NonNull
  private static XdmSequence _elementAvailable (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final String sName = _getString (aArgs.get (0));
    final StaticContext aSC = aCtx.getStaticContext ();
    final XdmQName aName = _resolveName (aSC, sName, aSC.getDefaultElementNamespace ());
    if (aName == null)
      throw new XPathException ("XTDE1440", "'" + sName + "' is not a valid QName with a bound prefix");
    final boolean bRet = XsltNames.NS_XSL.equals (aName.namespaceUri ()) &&
                         XsltExtensionRegistry.getInstance ().isImplemented (aName.localName ());
    return XdmSequence.of (XdmAtomic.bool (bRet));
  }

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
    return XdmSequence.of (XdmAtomic.bool (bRet));
  }

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
    aLib.register (NS, "system-property", "(xs:string) as xs:string", XsltFunctions::_systemProperty);
    aLib.register (NS, "function-available", "(xs:string) as xs:boolean", XsltFunctions::_functionAvailable);
    aLib.register (NS, "function-available", "(xs:string, xs:integer) as xs:boolean", XsltFunctions::_functionAvailable);
    aLib.register (NS, "element-available", "(xs:string) as xs:boolean", XsltFunctions::_elementAvailable);
    aLib.register (NS, "type-available", "(xs:string) as xs:boolean", XsltFunctions::_typeAvailable);
    aLib.register (NS, "available-system-properties", "() as xs:QName*", XsltFunctions::_availableSystemProperties);
  }

  @NonNull
  private static XsltContext _xslt (@NonNull final DynamicContext aCtx, @NonNull final String sFunction)
  {
    final XsltContext ret = XsltContext.of (aCtx);
    if (ret == null)
      throw new XPathException ("XPST0017", "The function " + sFunction + "() is only available during a transformation");
    return ret;
  }

  @NonNull
  private static XdmSequence _current (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final IXdmItem aItem = aCtx.getCurrentItem ();
    if (aItem == null)
      throw new XPathException ("XTDE1360", "current() is called while the current item is absent");
    return XdmSequence.of (aItem);
  }

  @NonNull
  private static XdmSequence _key (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XsltContext aXCtx = _xslt (aCtx, "key");
    final String sName = _getString (aArgs.get (0));
    final XdmQName aName = _resolveName (aCtx.getStaticContext (), sName, "");
    if (aName == null)
      throw new XPathException ("XTDE1260", "'" + sName + "' is not a valid key name");
    final XdmNode aTop;
    if (aArgs.size () > 2)
    {
      aTop = (XdmNode) aArgs.get (2).get (0);
      if (!aTop.getRoot ().isDocument ())
        throw new XPathException ("XTDE1270", "The third argument of key() is not in a tree rooted at a document node");
    }
    else
    {
      if (!(aCtx.getContextItem () instanceof final XdmNode aNode))
        throw new XPathException (aCtx.getContextItem () == null ? "XPDY0002" : "XPTY0004",
                                  "key() with two arguments requires a node as context item");
      aTop = aNode.getRoot ();
      if (!aTop.isDocument ())
        throw new XPathException ("XTDE1270", "key() with two arguments requires the context node to be in a tree rooted at a document node");
    }
    return aXCtx.getEngine ().findKeyNodes (aXCtx, aName, aArgs.get (1), aTop);
  }

  @NonNull
  private static XdmSequence _document (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XsltContext aXCtx = _xslt (aCtx, "document");
    final Engine aEngine = aXCtx.getEngine ();
    String sFixedBase = null;
    if (aArgs.size () > 1)
    {
      sFixedBase = FnAccessor.getBaseUri ((XdmNode) aArgs.get (1).get (0));
      if (sFixedBase == null)
        throw new XPathException ("XTDE1162", "The base node of document() has no base URI");
    }
    final XdmSequence.Builder aResult = new XdmSequence.Builder ();
    for (final IXdmItem aItem : aArgs.get (0))
    {
      final String sBase;
      final ICommonsList <String> aRefs = new CommonsArrayList <> ();
      if (aItem instanceof final XdmNode aNode)
      {
        sBase = sFixedBase != null ? sFixedBase : FnAccessor.getBaseUri (aNode);
        for (final IXdmItem aAtom : Atomizer.atomize (XdmSequence.of (aNode)))
          aRefs.add (Atomizer.getStringValue (aAtom));
      }
      else
      {
        sBase = sFixedBase != null ? sFixedBase : aCtx.getStaticContext ().getBaseUri ();
        aRefs.add (Atomizer.getStringValue (aItem));
      }
      for (final String sRef : aRefs)
      {
        final String sTrimmed = sRef.trim ();
        String sUri;
        try
        {
          sUri = sBase != null && FnUri.isAbsoluteUri (sBase) ? FnUri.resolveUri (sTrimmed, sBase) : sTrimmed;
        }
        catch (final XPathException ex)
        {
          throw new XPathException ("FODC0005", "Invalid URI '" + sTrimmed + "' in document()");
        }
        final XdmNode aDoc = aEngine.getDocument (aXCtx, sUri);
        if (aDoc != null)
          aResult.add (aDoc);
      }
    }
    return NodeSorter.sortAndDeduplicate (aResult.build ());
  }

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

  @NonNull
  private static XdmSequence _currentOutputUri (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XsltContext aXCtx = XsltContext.of (aCtx);
    if (aXCtx == null)
      return XdmSequence.EMPTY;
    final String sUri = aXCtx.get (Engine.CURRENT_OUTPUT_URI);
    return sUri == null || sUri.isEmpty () ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.anyUri (sUri));
  }

  @NonNull
  private static XdmSequence _unparsedEntity (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    // The tree model does not keep unparsed entities
    final XdmNode aNode;
    if (aArgs.size () > 1)
      aNode = (XdmNode) aArgs.get (1).get (0);
    else
    {
      if (!(aCtx.getContextItem () instanceof final XdmNode aCtxNode))
        throw new XPathException ("XTDE1370", "unparsed-entity-uri() requires a node as context item");
      aNode = aCtxNode;
    }
    if (!aNode.getRoot ().isDocument ())
      throw new XPathException ("XTDE1370", "The node is not in a tree rooted at a document node");
    return XdmSequence.of (XdmAtomic.string (""));
  }

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
      if (aItem instanceof final XdmNode aNode)
      {
        final XdmNode aCopy = NodeCopier.copyDeep (aNode, true);
        NamespaceFixup.finish (aCopy);
        ret.add (aCopy);
      }
      else
        ret.add (aItem);
    return ret.build ();
  }

  @NonNull
  private static XdmNode _snapshotNode (@NonNull final XdmNode aNode)
  {
    // Copy the ancestors shallowly (with their attributes and namespaces) and
    // the node deeply; return the copy of the node
    final ICommonsList <XdmNode> aAncestors = new CommonsArrayList <> ();
    final XdmNode aStart = aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE ? aNode.getParent ()
                                                                                                             : aNode;
    if (aStart == null)
    {
      final XdmNode ret = NodeCopier.copyDeep (aNode, true);
      NamespaceFixup.finish (ret);
      return ret;
    }
    for (XdmNode aCur = aStart.getParent (); aCur != null; aCur = aCur.getParent ())
      aAncestors.add (0, aCur);
    XdmNode aParentCopy = null;
    XdmNode aRoot = null;
    for (final XdmNode aAnc : aAncestors)
    {
      final XdmNode aCopy = NodeCopier.copyShallow (aAnc);
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
    final XdmNode aStartCopy = NodeCopier.copyDeep (aStart, true);
    if (aParentCopy != null)
      aParentCopy.appendChild (aStartCopy);
    else
      aRoot = aStartCopy;
    NamespaceFixup.finish (aRoot);
    if (aStart == aNode)
      return aStartCopy;
    if (aNode.isAttribute ())
      return aStartCopy.getAttribute (aNode.getName ().namespaceUri (), aNode.getName ().localName ());
    for (final XdmNode aNS : aStartCopy.getNamespaceNodes ())
      if (aNS.getStringValue ().equals (aNode.getStringValue ()) && Objects.equals (aNS.getName (), aNode.getName ()))
        return aNS;
    return aStartCopy;
  }

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
      ret.add (aItem instanceof final XdmNode aNode ? _snapshotNode (aNode) : aItem);
    return ret.build ();
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
    aLib.register (NS, "regex-group", "(xs:integer) as xs:string", XsltFunctions::_regexGroup);
    aLib.register (NS, "current-output-uri", "() as xs:anyURI?", XsltFunctions::_currentOutputUri, true);
    aLib.register (NS, "unparsed-entity-uri", "(xs:string) as xs:anyURI", XsltFunctions::_unparsedEntity, true);
    aLib.register (NS, "unparsed-entity-uri", "(xs:string, node()) as xs:anyURI", XsltFunctions::_unparsedEntity);
    aLib.register (NS, "unparsed-entity-public-id", "(xs:string) as xs:string", XsltFunctions::_unparsedEntity, true);
    aLib.register (NS, "unparsed-entity-public-id", "(xs:string, node()) as xs:string", XsltFunctions::_unparsedEntity);
    aLib.register (NS, "copy-of", "() as item()*", XsltFunctions::_copyOf, true);
    aLib.register (NS, "copy-of", "(item()*) as item()*", XsltFunctions::_copyOf);
    aLib.register (NS, "snapshot", "() as item()*", XsltFunctions::_snapshot, true);
    aLib.register (NS, "snapshot", "(item()*) as item()*", XsltFunctions::_snapshot);
  }
}
