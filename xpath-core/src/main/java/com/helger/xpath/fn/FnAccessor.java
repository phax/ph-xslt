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
package com.helger.xpath.fn;

import java.util.IdentityHashMap;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Function family: fn:data, fn:string, fn:node-name, fn:nilled, fn:base-uri,
 * fn:document-uri, fn:name, fn:local-name, fn:namespace-uri, fn:root,
 * fn:has-children, fn:path, fn:generate-id.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnAccessor
{
  private static final String RUN_CACHE_GENERATE_ID = "fn:generate-id";
  private static final String ROOT_CALL = "Q{" + XdmConstants.NS_FN + "}root()";

  @PresentForCodeCoverage
  private static final FnAccessor INSTANCE = new FnAccessor ();

  private FnAccessor ()
  {}

  @NonNull
  private static IXdmItem _getContextItem (@NonNull final DynamicContext aCtx)
  {
    final IXdmItem ret = aCtx.getContextItem ();
    if (ret == null)
      throw XPathException.noContext ("The context item is absent");
    return ret;
  }

  /**
   * The node argument of a <code>node()?</code> function, or the context node
   * for the zero-argument form.
   *
   * @return <code>null</code> for an empty argument.
   */
  @Nullable
  private static XdmNode _getNode (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.isEmpty ())
    {
      if (_getContextItem (aCtx) instanceof final XdmNode aNode)
        return aNode;
      throw XPathException.type ("The context item is not a node");
    }
    final XdmSequence aArg = aArgs.get (0);
    return aArg.isEmpty () ? null : (XdmNode) aArg.get (0);
  }

  /**
   * @param aItem
   *        An item.
   * @return Its string value (<code>fn:string</code>).
   * @throws XPathException
   *         FOTY0014 for function items, maps and arrays.
   */
  @NonNull
  public static String getStringValue (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final XdmNode aNode)
      return aNode.getStringValue ();
    if (aItem instanceof final XdmAtomic aAtomic)
      return aAtomic.isStringLike () ? aAtomic.getString () : aAtomic.getLexical ();
    throw XPathException.of ("FOTY0014", "A function item, map or array has no string value");
  }

  /**
   * Compute the base URI of a node (XDM 3.1 section 5.2, XML Base): the
   * <code>xml:base</code> attributes of the ancestor-or-self elements,
   * innermost resolved against outermost, all resolved against the explicit
   * base URI of the nearest node that has one, or the document URI.
   *
   * @param aNode
   *        The node.
   * @return The base URI or <code>null</code> if there is none.
   */
  @Nullable
  public static String getBaseUri (@NonNull final XdmNode aNode)
  {
    switch (aNode.getKind ())
    {
      case NAMESPACE:
        return null;
      case ATTRIBUTE:
      case TEXT:
      case COMMENT:
      case PROCESSING_INSTRUCTION:
        // Their base URI is that of their parent; with no parent there is none
        if (aNode.getParent () == null && aNode.getBaseUri () == null)
          return null;
        break;
      default:
        break;
    }
    final ICommonsList <String> aChain = new CommonsArrayList <> ();
    String sBase = null;
    for (XdmNode aCur = aNode; aCur != null; aCur = aCur.getParent ())
    {
      if (aCur.isElement ())
      {
        final XdmNode aXmlBase = aCur.getAttribute (XdmConstants.NS_XML, "base");
        if (aXmlBase != null)
          aChain.add (aXmlBase.getValue ());
      }
      if (aCur.getBaseUri () != null)
      {
        sBase = aCur.getBaseUri ();
        break;
      }
      if (aCur.isDocument ())
      {
        sBase = aCur.getDocumentUri ();
        break;
      }
    }
    for (int i = aChain.size () - 1; i >= 0; --i)
    {
      final String sRef = aChain.get (i);
      if (sBase == null || !FnUri.isAbsoluteUri (sBase))
        sBase = sRef;
      else
        try
        {
          sBase = FnUri.resolveUri (sRef, sBase);
        }
        catch (final XPathException ex)
        {
          // Keep the base of the parent
        }
    }
    return sBase;
  }

  /**
   * @param aNode
   *        A node.
   * @return The node name as an XDM QName or <code>null</code> for nodes
   *         without name (documents, text, comments, the default namespace
   *         node).
   */
  @Nullable
  public static XdmQName getNodeName (@NonNull final XdmNode aNode)
  {
    return switch (aNode.getKind ())
    {
      case ELEMENT, ATTRIBUTE, PROCESSING_INSTRUCTION, NAMESPACE -> aNode.getName ();
      default -> null;
    };
  }

  @NonNull
  private static String _getName (@Nullable final XdmNode aNode)
  {
    if (aNode == null)
      return "";
    final XdmQName aName = getNodeName (aNode);
    return aName == null ? "" : aName.getLexicalName ();
  }

  @NonNull
  private static String _getLocalName (@Nullable final XdmNode aNode)
  {
    if (aNode == null)
      return "";
    final XdmQName aName = getNodeName (aNode);
    return aName == null ? "" : aName.localName ();
  }

  @NonNull
  private static String _getNamespaceUri (@Nullable final XdmNode aNode)
  {
    if (aNode == null)
      return "";
    return switch (aNode.getKind ())
    {
      case ELEMENT, ATTRIBUTE -> aNode.getName ().namespaceUri ();
      default -> "";
    };
  }

  @NonNull
  private static String _generateId (@NonNull final DynamicContext aCtx, @Nullable final XdmNode aNode)
  {
    if (aNode == null)
      return "";
    @SuppressWarnings ("unchecked")
    final IdentityHashMap <XdmNode, String> aIDs = (IdentityHashMap <XdmNode, String>) aCtx.getRunCache ()
                                                                                         .computeIfAbsent (RUN_CACHE_GENERATE_ID,
                                                                                                           k -> new IdentityHashMap <> ());
    return aIDs.computeIfAbsent (aNode, k -> "id" + Integer.toString (aIDs.size () + 1));
  }

  private static boolean _isSameStep (@NonNull final XdmNode aLeft, @NonNull final XdmNode aRight)
  {
    if (aLeft.getKind () != aRight.getKind ())
      return false;
    return switch (aLeft.getKind ())
    {
      case ELEMENT, PROCESSING_INSTRUCTION -> aLeft.getName ().equals (aRight.getName ());
      default -> true;
    };
  }

  private static int _getSiblingPosition (@NonNull final XdmNode aNode)
  {
    final XdmNode aParent = aNode.getParent ();
    if (aParent == null)
      return 1;
    int nPos = 0;
    for (int i = 0; i < aParent.getChildCount (); ++i)
    {
      final XdmNode aSibling = aParent.getChildAtIndex (i);
      if (_isSameStep (aSibling, aNode))
        nPos++;
      if (aSibling == aNode)
        break;
    }
    return nPos;
  }

  @NonNull
  private static String _getStepLabel (@NonNull final XdmNode aNode)
  {
    final XdmQName aName = aNode.getName ();
    return switch (aNode.getKind ())
    {
      case ELEMENT -> "Q{" + aName.namespaceUri () + "}" + aName.localName () + "[" + _getSiblingPosition (aNode) + "]";
      case ATTRIBUTE -> aName.hasNamespace () ? "@Q{" + aName.namespaceUri () + "}" + aName.localName ()
                                              : "@" + aName.localName ();
      case TEXT -> "text()[" + _getSiblingPosition (aNode) + "]";
      case COMMENT -> "comment()[" + _getSiblingPosition (aNode) + "]";
      case PROCESSING_INSTRUCTION -> "processing-instruction(" +
                                     aName.localName () +
                                     ")[" +
                                     _getSiblingPosition (aNode) +
                                     "]";
      case NAMESPACE -> aName == null ? "namespace::*[Q{" + XdmConstants.NS_FN + "}local-name()=\"\"]"
                                      : "namespace::" + aName.localName ();
      default -> "";
    };
  }

  /**
   * @param aNode
   *        A node.
   * @return The result of <code>fn:path</code> for that node.
   */
  @NonNull
  public static String getPath (@NonNull final XdmNode aNode)
  {
    if (aNode.isDocument ())
      return "/";
    final XdmNode aRoot = aNode.getRoot ();
    final boolean bRooted = aRoot.isDocument ();
    final ICommonsList <String> aSteps = new CommonsArrayList <> ();
    for (XdmNode aCur = aNode; aCur != null && !aCur.isDocument (); aCur = aCur.getParent ())
    {
      if (!bRooted && aCur == aRoot)
        break;
      aSteps.add (_getStepLabel (aCur));
    }
    aSteps.reverse ();
    final String sSteps = String.join ("/", aSteps);
    if (bRooted)
      return "/" + sSteps;
    return aSteps.isEmpty () ? ROOT_CALL : ROOT_CALL + "/" + sSteps;
  }

  @NonNull
  private static XdmSequence _string (@NonNull final String s)
  {
    return XdmSequence.of (XdmAtomic.string (s));
  }

  @NonNull
  private static XdmSequence _anyUriOrEmpty (@Nullable final String s)
  {
    return s == null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.anyUri (s));
  }

  private static void _register01 (@NonNull final FunctionLibrary aLib,
                                   @NonNull final String sLocalName,
                                   @NonNull final String sParamType,
                                   @NonNull final String sReturnType,
                                   @NonNull final IXPathFunction aImpl)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, sLocalName, "() as " + sReturnType, aImpl, true);
    aLib.register (sNS, sLocalName, "(" + sParamType + ") as " + sReturnType, aImpl);
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    _register01 (aLib, "data", "item()*", "xs:anyAtomicType*", (aCtx, aArgs) -> {
      final XdmSequence aArg = aArgs.isEmpty () ? XdmSequence.of (_getContextItem (aCtx)) : aArgs.get (0);
      return Atomizer.atomize (aArg);
    });
    _register01 (aLib, "string", "item()?", "xs:string", (aCtx, aArgs) -> {
      if (aArgs.isEmpty ())
        return _string (getStringValue (_getContextItem (aCtx)));
      final XdmSequence aArg = aArgs.get (0);
      return _string (aArg.isEmpty () ? "" : getStringValue (aArg.get (0)));
    });
    _register01 (aLib, "node-name", "node()?", "xs:QName?", (aCtx, aArgs) -> {
      final XdmNode aNode = _getNode (aCtx, aArgs);
      final XdmQName aName = aNode == null ? null : getNodeName (aNode);
      return aName == null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.qname (aName));
    });
    _register01 (aLib, "nilled", "node()?", "xs:boolean?", (aCtx, aArgs) -> {
      final XdmNode aNode = _getNode (aCtx, aArgs);
      // Only elements of a schema-validated tree can be nilled
      if (aNode == null || !aNode.isElement ())
        return XdmSequence.EMPTY;
      return XdmSequence.of (aNode.isNilled () ? XdmAtomic.TRUE : XdmAtomic.FALSE);
    });
    _register01 (aLib, "base-uri", "node()?", "xs:anyURI?", (aCtx, aArgs) -> {
      final XdmNode aNode = _getNode (aCtx, aArgs);
      return aNode == null ? XdmSequence.EMPTY : _anyUriOrEmpty (getBaseUri (aNode));
    });
    _register01 (aLib, "document-uri", "node()?", "xs:anyURI?", (aCtx, aArgs) -> {
      final XdmNode aNode = _getNode (aCtx, aArgs);
      return aNode == null || !aNode.isDocument () ? XdmSequence.EMPTY : _anyUriOrEmpty (aNode.getDocumentUri ());
    });
    _register01 (aLib, "name", "node()?", "xs:string", (aCtx, aArgs) -> _string (_getName (_getNode (aCtx, aArgs))));
    _register01 (aLib,
                 "local-name",
                 "node()?",
                 "xs:string",
                 (aCtx, aArgs) -> _string (_getLocalName (_getNode (aCtx, aArgs))));
    _register01 (aLib,
                 "namespace-uri",
                 "node()?",
                 "xs:anyURI",
                 (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.anyUri (_getNamespaceUri (_getNode (aCtx, aArgs)))));
    final IXPathFunction aRoot = (aCtx, aArgs) -> {
      final XdmNode aNode = _getNode (aCtx, aArgs);
      return aNode == null ? XdmSequence.EMPTY : XdmSequence.of (aNode.getRoot ());
    };
    // The zero-argument form never returns the empty sequence
    aLib.register (XdmConstants.NS_FN, "root", "() as node()", aRoot, true);
    aLib.register (XdmConstants.NS_FN, "root", "(node()?) as node()?", aRoot);
    _register01 (aLib, "has-children", "node()?", "xs:boolean", (aCtx, aArgs) -> {
      final XdmNode aNode = _getNode (aCtx, aArgs);
      return XdmSequence.of (XdmAtomic.bool (aNode != null && aNode.getChildCount () > 0));
    });
    _register01 (aLib, "path", "node()?", "xs:string?", (aCtx, aArgs) -> {
      final XdmNode aNode = _getNode (aCtx, aArgs);
      return aNode == null ? XdmSequence.EMPTY : _string (getPath (aNode));
    });
    _register01 (aLib,
                 "generate-id",
                 "node()?",
                 "xs:string",
                 (aCtx, aArgs) -> _string (_generateId (aCtx, _getNode (aCtx, aArgs))));
  }
}
