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
package com.helger.xsd;

import java.util.ArrayDeque;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * xs:override (XSD 1.1 4.2.5), implemented as the override transformation of
 * the specification (go-xslt <code>override.go</code>): the overridden document
 * is rewritten - matching top-level components replaced, xs:include rewritten
 * to an xs:override carrying the override's children, nested xs:override
 * merged (replace matching, keep the rest, append unmatched), xs:redefine
 * children replaced in place, xs:import kept verbatim - and the rewritten
 * document is then collected as usual. Unmatched override children are
 * discarded at the schema level.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdOverride
{
  @PresentForCodeCoverage
  private static final XsdOverride INSTANCE = new XsdOverride ();

  private XsdOverride ()
  {}

  private record CloneStep (@NonNull IXdmNode src, @NonNull IXdmNode dstParent)
  {}

  @NonNull
  private static String _collapsedName (@NonNull final IXdmNode aNode)
  {
    final String sName = XsdNodes.attr (aNode, "name");
    return sName == null ? "" : XsdWhiteSpace.collapse (sName);
  }

  @Nullable
  private static IXdmNode _cloneShallow (@NonNull final IXdmNode aSrc)
  {
    final IXdmNode ret;
    switch (aSrc.getKind ())
    {
      case ELEMENT:
      {
        ret = IXdmNode.createElement (aSrc.getName ());
        for (final Map.Entry <String, String> aEntry : aSrc.getDeclaredNamespaces ().entrySet ())
          ret.declareNamespace (aEntry.getKey (), aEntry.getValue ());
        for (final IXdmNode aAttr : aSrc.getAllAttributes ())
        {
          final IXdmNode aAttrCopy = IXdmNode.createAttribute (aAttr.getName (), aAttr.getValue ());
          aAttrCopy.setLocation (aAttr.getLine (), aAttr.getColumn ());
          ret.addAttribute (aAttrCopy);
        }
        break;
      }
      case TEXT:
        ret = IXdmNode.createText (aSrc.getValue ());
        break;
      case COMMENT:
        ret = IXdmNode.createComment (aSrc.getValue ());
        break;
      case PROCESSING_INSTRUCTION:
        ret = IXdmNode.createProcessingInstruction (aSrc.getName ().localName (), aSrc.getValue ());
        break;
      default:
        return null;
    }
    ret.setLocation (aSrc.getLine (), aSrc.getColumn ());
    return ret;
  }

  /**
   * go-xslt <code>cloneSchemaTree</code>: a deep copy of an element
   * (attributes, namespace declarations, children). Iterative, as the depth is
   * input-controlled.
   */
  @Nullable
  private static IXdmNode _cloneTree (@NonNull final IXdmNode aSrc)
  {
    final IXdmNode ret = _cloneShallow (aSrc);
    if (ret == null)
      return null;
    final ArrayDeque <CloneStep> aStack = new ArrayDeque <> ();
    for (int i = aSrc.getChildCount () - 1; i >= 0; --i)
      aStack.push (new CloneStep (aSrc.getChildAtIndex (i), ret));
    while (!aStack.isEmpty ())
    {
      final CloneStep aStep = aStack.pop ();
      final IXdmNode aCopy = _cloneShallow (aStep.src ());
      if (aCopy == null)
        continue;
      aStep.dstParent ().appendChild (aCopy);
      for (int i = aStep.src ().getChildCount () - 1; i >= 0; --i)
        aStack.push (new CloneStep (aStep.src ().getChildAtIndex (i), aCopy));
    }
    return ret;
  }

  /**
   * go-xslt <code>transplantClone</code>: clone a node for insertion into
   * another document, materialising its in-scope namespace bindings on the
   * clone so that QName attribute values keep resolving with the overriding
   * document's prefixes (and never fall through to the host document's).
   */
  @NonNull
  private static IXdmNode _transplantClone (@NonNull final IXdmNode aSrc)
  {
    final IXdmNode ret = _cloneTree (aSrc);
    final ICommonsOrderedMap <String, String> aDeclared = ret.getDeclaredNamespaces ();
    final ICommonsOrderedMap <String, String> aInScope = aSrc.getInScopeNamespaces ();
    // Pin "no default namespace" against the host's default
    if (!aInScope.containsKey (""))
      aInScope.put ("", "");
    for (final Map.Entry <String, String> aEntry : aInScope.entrySet ())
      if (!"xml".equals (aEntry.getKey ()) && !aDeclared.containsKey (aEntry.getKey ()))
        ret.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    return ret;
  }

  /**
   * go-xslt <code>ovMatch</code>: the override component matching a node by
   * {kind, name} - the same element name and a whitespace-collapsed equal
   * <code>@name</code>.
   */
  @Nullable
  private static IXdmNode _match (@NonNull final ICommonsList <IXdmNode> aComps, @NonNull final IXdmNode aNode)
  {
    if (!XsdNodes.isXSElement (aNode) || !XsdNodes.hasAttr (aNode, "name"))
      return null;
    final String sName = _collapsedName (aNode);
    final String sLocal = XsdNodes.localName (aNode);
    for (final IXdmNode aComp : aComps)
      if (XsdNodes.localName (aComp).equals (sLocal) && _collapsedName (aComp).equals (sName))
        return aComp;
    return null;
  }

  @NonNull
  private static IXdmNode _newOverride (@NonNull final IXdmNode aFrom)
  {
    final IXdmNode ret = IXdmNode.createElement (new XdmQName (XsdConstants.NS_XS, "override"));
    ret.setLocation (aFrom.getLine (), aFrom.getColumn ());
    final String sLoc = XsdNodes.attr (aFrom, "schemaLocation");
    if (sLoc != null)
      ret.addAttribute (IXdmNode.createAttribute (new XdmQName ("schemaLocation"), sLoc));
    return ret;
  }

  private static void _signatureRecursive (@NonNull final StringBuilder aSB, @NonNull final IXdmNode aNode)
  {
    switch (aNode.getKind ())
    {
      case TEXT:
      {
        final String sValue = aNode.getValue ().strip ();
        if (!sValue.isEmpty ())
          aSB.append ("t:").append (sValue).append ('\u0001');
        break;
      }
      case ELEMENT:
      {
        aSB.append ("e:").append (XsdNodes.namespaceUri (aNode)).append ('|').append (XsdNodes.localName (aNode));
        final ICommonsList <String> aAttrs = new CommonsArrayList <> ();
        for (final IXdmNode aAttr : aNode.getAllAttributes ())
          aAttrs.add (XsdNodes.namespaceUri (aAttr) + "|" + XsdNodes.localName (aAttr) + "=" + aAttr.getValue ());
        aAttrs.sort (null);
        for (final String sAttr : aAttrs)
          aSB.append ('\u0001').append (sAttr);
        aSB.append ('(');
        for (int i = 0; i < aNode.getChildCount (); ++i)
          _signatureRecursive (aSB, aNode.getChildAtIndex (i));
        aSB.append (')');
        break;
      }
      default:
        break;
    }
  }

  /**
   * go-xslt <code>overrideComponents</code>: the component children of an
   * xs:override element (the XSD element children carrying <code>@name</code>,
   * annotations excluded).
   *
   * @param aOverride
   *        The xs:override element.
   * @return The component children. Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  static ICommonsList <IXdmNode> components (@NonNull final IXdmNode aOverride)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    for (final IXdmNode aChild : XsdNodes.children (aOverride))
      if (XsdNodes.isXSElement (aChild) &&
          XsdNodes.hasAttr (aChild, "name") &&
          !"annotation".equals (XsdNodes.localName (aChild)))
        ret.add (aChild);
    return ret;
  }

  /**
   * go-xslt <code>checkOverrideUniqueness</code> (src-override): the {kind,
   * name} identities of one override's children must be distinct.
   *
   * @param aOverride
   *        The xs:override element.
   * @throws XsdInvalidException
   *         on a duplicate
   */
  static void checkUniqueness (@NonNull final IXdmNode aOverride)
  {
    final ICommonsSet <String> aSeen = new CommonsHashSet <> ();
    for (final IXdmNode aChild : components (aOverride))
      if (!aSeen.add (XsdNodes.localName (aChild) + " " + _collapsedName (aChild)))
        throw XsdCompiler.invalid ("src-override",
                                   "duplicate " +
                                                   XsdNodes.localName (aChild) +
                                                   " " +
                                                   XsdLexHelper.quote (XsdNodes.attrOrEmpty (aChild, "name")) +
                                                   " in one xs:override");
  }

  /**
   * go-xslt <code>ovSignature</code>: an order-insensitive fingerprint of an
   * override's element children, used to key the collection dedup.
   *
   * @param aOverride
   *        The xs:override element.
   * @return The signature. Never <code>null</code>.
   */
  @NonNull
  static String signature (@NonNull final IXdmNode aOverride)
  {
    final ICommonsList <String> aParts = new CommonsArrayList <> ();
    for (final IXdmNode aChild : XsdNodes.children (aOverride))
      if (aChild.getKind () == ENodeKind.ELEMENT)
      {
        final StringBuilder aSB = new StringBuilder ();
        _signatureRecursive (aSB, aChild);
        aParts.add (aSB.toString ());
      }
    aParts.sort (null);
    return String.join ("\u0002", aParts);
  }

  /**
   * go-xslt <code>applyOverride</code>: the 4.2.5-transformed deep copy of an
   * xs:schema element under an xs:override element.
   *
   * @param aTarget
   *        The overridden xs:schema element.
   * @param aOverride
   *        The xs:override element.
   * @return The rewritten xs:schema element, in a new document. Never
   *         <code>null</code>.
   */
  @NonNull
  static IXdmNode apply (@NonNull final IXdmNode aTarget, @NonNull final IXdmNode aOverride)
  {
    final ICommonsList <IXdmNode> aComps = components (aOverride);
    final IXdmNode ret = _cloneShallow (aTarget);
    for (final IXdmNode aChild : XsdNodes.children (aTarget))
    {
      if (!XsdNodes.isXSElement (aChild))
      {
        final IXdmNode aCopy = _cloneTree (aChild);
        if (aCopy != null)
          ret.appendChild (aCopy);
        continue;
      }
      switch (XsdNodes.localName (aChild))
      {
        case "import":
          ret.appendChild (_cloneTree (aChild));
          break;
        case "include":
        {
          // An include becomes an override carrying all of the override's
          // children
          final IXdmNode aNew = _newOverride (aChild);
          for (final IXdmNode aOC : XsdNodes.children (aOverride))
            if (aOC.getKind () == ENodeKind.ELEMENT)
              aNew.appendChild (_transplantClone (aOC));
          ret.appendChild (aNew);
          break;
        }
        case "override":
        {
          // Merge: replace matching children, keep the rest, then append the
          // override components matching none of the original children
          final IXdmNode aNew = _newOverride (aChild);
          for (final IXdmNode aSub : XsdNodes.children (aChild))
          {
            final IXdmNode aMatch = _match (aComps, aSub);
            final IXdmNode aCopy = aMatch != null ? _transplantClone (aMatch) : _cloneTree (aSub);
            if (aCopy != null)
              aNew.appendChild (aCopy);
          }
          final ICommonsList <IXdmNode> aOwnComps = components (aChild);
          for (final IXdmNode aComp : aComps)
            if (_match (aOwnComps, aComp) == null)
              aNew.appendChild (_transplantClone (aComp));
          ret.appendChild (aNew);
          break;
        }
        case "redefine":
        {
          // Children replaced in place, nothing appended
          final IXdmNode aNew = _cloneShallow (aChild);
          for (final IXdmNode aSub : XsdNodes.children (aChild))
          {
            final IXdmNode aMatch = _match (aComps, aSub);
            final IXdmNode aCopy = aMatch != null ? _transplantClone (aMatch) : _cloneTree (aSub);
            if (aCopy != null)
              aNew.appendChild (aCopy);
          }
          ret.appendChild (aNew);
          break;
        }
        default:
        {
          final IXdmNode aMatch = _match (aComps, aChild);
          ret.appendChild (aMatch != null ? _transplantClone (aMatch) : _cloneTree (aChild));
          break;
        }
      }
    }
    final IXdmNode aDoc = IXdmNode.createDocument ();
    aDoc.appendChild (ret);
    return ret;
  }
}
