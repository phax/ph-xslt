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
package com.helger.phoxtrot.xpath.fn;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

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

  /**
   * @param aCtx
   *        The dynamic context.
   * @return The context item, never <code>null</code>.
   * @throws XPathException
   *         XPDY0002 if the context item is absent.
   */
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
  private static IXdmNode _getNode (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.isEmpty ())
    {
      if (_getContextItem (aCtx) instanceof final IXdmNode aNode)
        return aNode;
      throw XPathException.type ("The context item is not a node");
    }
    final XdmSequence aArg = aArgs.get (0);
    return aArg.isEmpty () ? null : (IXdmNode) aArg.get (0);
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
    if (aItem instanceof final IXdmNode aNode)
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
  public static String getBaseUri (@NonNull final IXdmNode aNode)
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
    for (IXdmNode aCur = aNode; aCur != null; aCur = aCur.getParent ())
    {
      if (aCur.isElement ())
      {
        final IXdmNode aXmlBase = aCur.getAttribute (XdmConstants.NS_XML, "base");
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
  public static XdmQName getNodeName (@NonNull final IXdmNode aNode)
  {
    return switch (aNode.getKind ())
    {
      case ELEMENT, ATTRIBUTE, PROCESSING_INSTRUCTION, NAMESPACE -> aNode.getName ();
      default -> null;
    };
  }

  /**
   * @param aNode
   *        A node, <code>null</code> for an empty argument.
   * @return The lexical name (with prefix) for <code>fn:name</code> (F&amp;O
   *         3.1 section 13.1); the empty string for an empty argument or a
   *         node without name.
   */
  @NonNull
  private static String _getName (@Nullable final IXdmNode aNode)
  {
    if (aNode == null)
      return "";
    final XdmQName aName = getNodeName (aNode);
    return aName == null ? "" : aName.getLexicalName ();
  }

  /**
   * @param aNode
   *        A node, <code>null</code> for an empty argument.
   * @return The local part of the name for <code>fn:local-name</code> (F&amp;O
   *         3.1 section 13.2); the empty string for an empty argument or a
   *         node without name.
   */
  @NonNull
  private static String _getLocalName (@Nullable final IXdmNode aNode)
  {
    if (aNode == null)
      return "";
    final XdmQName aName = getNodeName (aNode);
    return aName == null ? "" : aName.localName ();
  }

  /**
   * @param aNode
   *        A node, <code>null</code> for an empty argument.
   * @return The namespace URI of an element or attribute name for
   *         <code>fn:namespace-uri</code> (F&amp;O 3.1 section 13.3); the
   *         empty string for any other node, an empty argument or a name in no
   *         namespace.
   */
  @NonNull
  private static String _getNamespaceUri (@Nullable final IXdmNode aNode)
  {
    if (aNode == null)
      return "";
    return switch (aNode.getKind ())
    {
      case ELEMENT, ATTRIBUTE -> aNode.getName ().namespaceUri ();
      default -> "";
    };
  }

  /** The document number of one tree and the last node number given out in it. */
  private static final class GeneratedTreeIDs
  {
    private final int m_nDocument;
    private int m_nLastNode;

    private GeneratedTreeIDs (final int nDocument)
    {
      m_nDocument = nDocument;
    }
  }

  /**
   * The identifiers fn:generate-id gave out in one run. Nodes and the roots of
   * their trees are compared by identity, and a node or root no longer
   * referenced anywhere is forgotten, so that a streamed document does not
   * make the run keep every node it saw. The counters never go back: an
   * identifier is not given out twice, even after its node was forgotten.
   */
  private static final class GeneratedIDs
  {
    // IXdmNode does not override equals and hashCode: identity semantics
    private final Map <IXdmNode, String> m_aIDs = new WeakHashMap <> ();
    private final Map <IXdmNode, GeneratedTreeIDs> m_aTrees = new WeakHashMap <> ();
    private int m_nLastDocument;
  }

  /**
   * <code>fn:generate-id</code> (F&amp;O 3.1 section 14.5.4): give every node
   * a stable identifier of the form <code>d</code>, the number of its tree
   * (root) in the run, <code>n</code> and the number of the node in that tree
   * (e.g. <code>d1n5</code>), both counted in the order of the first request
   * and kept in the run cache in a {@link GeneratedIDs}; the same node gets
   * the same identifier for the whole run. The form keeps the values apart
   * from IDs like <code>id1</code> that authors write themselves.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aNode
   *        The node, <code>null</code> for an empty argument.
   * @return The identifier, the empty string for <code>null</code>.
   */
  @NonNull
  private static String _generateId (@NonNull final DynamicContext aCtx, @Nullable final IXdmNode aNode)
  {
    if (aNode == null)
      return "";
    final GeneratedIDs aIDs = (GeneratedIDs) aCtx.getRunCache ().computeIfAbsent (RUN_CACHE_GENERATE_ID, k -> new GeneratedIDs ());
    return aIDs.m_aIDs.computeIfAbsent (aNode, k -> {
      final GeneratedTreeIDs aTree = aIDs.m_aTrees.computeIfAbsent (aNode.getRoot (),
                                                                    r -> new GeneratedTreeIDs (++aIDs.m_nLastDocument));
      return "d" + Integer.toString (aTree.m_nDocument) + "n" + Integer.toString (++aTree.m_nLastNode);
    });
  }

  /**
   * @param aLeft
   *        A node.
   * @param aRight
   *        Another node.
   * @return <code>true</code> if both have the same kind and, for elements and
   *         processing instructions, the same name, i.e. they count for the
   *         same positional predicate in an <code>fn:path</code> step.
   */
  private static boolean _isSameStep (@NonNull final IXdmNode aLeft, @NonNull final IXdmNode aRight)
  {
    if (aLeft.getKind () != aRight.getKind ())
      return false;
    return switch (aLeft.getKind ())
    {
      case ELEMENT, PROCESSING_INSTRUCTION -> aLeft.getName ().equals (aRight.getName ());
      default -> true;
    };
  }

  /**
   * @param aNode
   *        A child node.
   * @return The 1-based position of the node among the children of its parent
   *         that match the same step (see
   *         {@link #_isSameStep(IXdmNode, IXdmNode)}); 1 for a node without
   *         parent.
   */
  private static int _getSiblingPosition (@NonNull final IXdmNode aNode)
  {
    final IXdmNode aParent = aNode.getParent ();
    if (aParent == null)
      return 1;
    int nPos = 0;
    for (int i = 0; i < aParent.getChildCount (); ++i)
    {
      final IXdmNode aSibling = aParent.getChildAtIndex (i);
      if (_isSameStep (aSibling, aNode))
        nPos++;
      if (aSibling == aNode)
        break;
    }
    return nPos;
  }

  /**
   * The step of <code>fn:path</code> (F&amp;O 3.1 section 13.6) for one node:
   * <code>Q{uri}local[n]</code> for an element, <code>@local</code> or
   * <code>@Q{uri}local</code> for an attribute, <code>text()[n]</code>,
   * <code>comment()[n]</code>, <code>processing-instruction(name)[n]</code>,
   * <code>namespace::prefix</code>, or for the default namespace
   * <code>namespace::*[Q{http://www.w3.org/2005/xpath-functions}local-name()=""]</code>.
   *
   * @param aNode
   *        A node that is not a document node.
   * @return The step, the empty string for any other kind.
   */
  @NonNull
  private static String _getStepLabel (@NonNull final IXdmNode aNode)
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
  public static String getPath (@NonNull final IXdmNode aNode)
  {
    if (aNode.isDocument ())
      return "/";
    final IXdmNode aRoot = aNode.getRoot ();
    final boolean bRooted = aRoot.isDocument ();
    final ICommonsList <String> aSteps = new CommonsArrayList <> ();
    for (IXdmNode aCur = aNode; aCur != null && !aCur.isDocument (); aCur = aCur.getParent ())
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

  /**
   * @param s
   *        A URI or <code>null</code>.
   * @return The URI as xs:anyURI, or the empty sequence for <code>null</code>.
   */
  @NonNull
  private static XdmSequence _anyUriOrEmpty (@Nullable final String s)
  {
    return s == null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.anyUri (s));
  }

  /**
   * Register a function with an optional node or item argument twice: with
   * arity 0 as focus-dependent (the implementation then reads the context
   * item) and with arity 1.
   *
   * @param aLib
   *        The library to register into.
   * @param sLocalName
   *        Local name in the fn namespace.
   * @param sParamType
   *        The type of the parameter of the 1-argument form.
   * @param sReturnType
   *        The return type of both forms.
   * @param aImpl
   *        The implementation, called with 0 or 1 arguments.
   */
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

  /**
   * Register the accessor and node functions <code>fn:data</code>,
   * <code>fn:string</code>, <code>fn:node-name</code>, <code>fn:nilled</code>
   * (empty for anything but an element), <code>fn:base-uri</code>,
   * <code>fn:document-uri</code> (empty for anything but a document node),
   * <code>fn:name</code>, <code>fn:local-name</code>,
   * <code>fn:namespace-uri</code>, <code>fn:root</code>,
   * <code>fn:has-children</code>, <code>fn:path</code> and
   * <code>fn:generate-id</code> (F&amp;O 3.1 sections 2, 13 and 14.5.4), each
   * with arity 0 (the context item, XPDY0002 if absent and XPTY0004 if a node
   * is needed and it is none) and 1.
   *
   * @param aLib
   *        The library to register into.
   */
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
      final IXdmNode aNode = _getNode (aCtx, aArgs);
      final XdmQName aName = aNode == null ? null : getNodeName (aNode);
      return aName == null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.qname (aName));
    });
    _register01 (aLib, "nilled", "node()?", "xs:boolean?", (aCtx, aArgs) -> {
      final IXdmNode aNode = _getNode (aCtx, aArgs);
      // Only elements of a schema-validated tree can be nilled
      if (aNode == null || !aNode.isElement ())
        return XdmSequence.EMPTY;
      return XdmSequence.of (aNode.isNilled () ? XdmAtomic.TRUE : XdmAtomic.FALSE);
    });
    _register01 (aLib, "base-uri", "node()?", "xs:anyURI?", (aCtx, aArgs) -> {
      final IXdmNode aNode = _getNode (aCtx, aArgs);
      return aNode == null ? XdmSequence.EMPTY : _anyUriOrEmpty (getBaseUri (aNode));
    });
    _register01 (aLib, "document-uri", "node()?", "xs:anyURI?", (aCtx, aArgs) -> {
      final IXdmNode aNode = _getNode (aCtx, aArgs);
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
      final IXdmNode aNode = _getNode (aCtx, aArgs);
      return aNode == null ? XdmSequence.EMPTY : XdmSequence.of (aNode.getRoot ());
    };
    // The zero-argument form never returns the empty sequence
    aLib.register (XdmConstants.NS_FN, "root", "() as node()", aRoot, true);
    aLib.register (XdmConstants.NS_FN, "root", "(node()?) as node()?", aRoot);
    _register01 (aLib, "has-children", "node()?", "xs:boolean", (aCtx, aArgs) -> {
      final IXdmNode aNode = _getNode (aCtx, aArgs);
      return XdmSequence.of (XdmAtomic.bool (aNode != null && aNode.getChildCount () > 0));
    });
    _register01 (aLib, "path", "node()?", "xs:string?", (aCtx, aArgs) -> {
      final IXdmNode aNode = _getNode (aCtx, aArgs);
      return aNode == null ? XdmSequence.EMPTY : _string (getPath (aNode));
    });
    _register01 (aLib,
                 "generate-id",
                 "node()?",
                 "xs:string",
                 (aCtx, aArgs) -> _string (_generateId (aCtx, _getNode (aCtx, aArgs))));
  }
}
