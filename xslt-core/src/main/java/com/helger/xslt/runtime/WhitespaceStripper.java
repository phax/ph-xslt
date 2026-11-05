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

import java.util.ArrayDeque;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmSchemaType;
import com.helger.xpath.xdm.schema.EXdmContentType;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xslt.model.SpaceRules;
import com.helger.xslt.tree.XsltNames;

/**
 * Removes whitespace text nodes from source documents according to
 * xsl:strip-space and xsl:preserve-space (XSLT 3.0 section 4.4.2). Since the
 * tree model cannot remove nodes, a stripped copy is built (iteratively) when
 * anything must be stripped.
 *
 * @author Philip Helger
 */
@Immutable
public final class WhitespaceStripper
{
  @PresentForCodeCoverage
  private static final WhitespaceStripper INSTANCE = new WhitespaceStripper ();

  private WhitespaceStripper ()
  {}

  /**
   * One element (or the root) still to copy: its source node, the copy its
   * attributes and children are appended to, and whether xml:space="preserve"
   * is inherited from its ancestors.
   */
  private record Job (@NonNull IXdmNode source, @NonNull IXdmNode target, boolean preserve)
  {}

  /**
   * @param aElement
   *        The element.
   * @param bInherited
   *        Whether the nearest ancestor xml:space says preserve.
   * @return <code>true</code> if xml:space on the element (whitespace
   *         trimmed) is <code>preserve</code>, <code>false</code> if it is
   *         <code>default</code>, otherwise (absent or another value) the
   *         inherited setting.
   */
  private static boolean _isPreserve (@NonNull final IXdmNode aElement, final boolean bInherited)
  {
    final IXdmNode aSpace = aElement.getAttribute (XdmConstants.NS_XML, "space");
    if (aSpace == null)
      return bInherited;
    final String s = aSpace.getValue ().trim ();
    if ("preserve".equals (s))
      return true;
    if ("default".equals (s))
      return false;
    return bInherited;
  }

  /**
   * @return <code>true</code> if the element is annotated with a simple type
   *         or a complex type with simple content: its whitespace text is
   *         never stripped (XSLT 3.0 section 4.4.2). Without a schema type
   *         the type annotation decides: any annotation other than
   *         <code>xs:anyType</code> counts as simple.
   */
  private static boolean _hasSimpleContentType (@NonNull final IXdmNode aElement)
  {
    final XdmSchemaType aSchemaType = aElement.getSchemaType ();
    if (aSchemaType == null)
      return aElement.getTypeAnnotation () != null && !aElement.getTypeAnnotation ().isSameName (XdmConstants.NS_XS, "anyType");
    final IXdmTypeDefinition aDef = aSchemaType.getDefinition ();
    if (aDef != null)
      return aDef.getContentType () == EXdmContentType.SIMPLE;
    return aSchemaType.isSimple () || aSchemaType.getAtomicBase () != null;
  }

  /**
   * @return <code>true</code> if the text node is a whitespace text node to
   *         strip: no xml:space="preserve" is in effect, its parent is an
   *         element without a simple content type and the space rules strip
   *         the parent's name. Text children of a document node are never
   *         stripped.
   */
  private static boolean _isStripped (@NonNull final IXdmNode aText, final boolean bPreserve, @NonNull final SpaceRules aRules)
  {
    final IXdmNode aParent = aText.getParent ();
    return !bPreserve &&
           aParent != null &&
           aParent.isElement () &&
           XsltNames.isWhitespaceOnly (aText.getValue ()) &&
           !_hasSimpleContentType (aParent) &&
           aRules.isStripped (aParent.getName ());
  }

  /**
   * Walk the tree (iteratively, carrying the inherited xml:space setting per
   * element) until the first text node that {@link #strip(IXdmNode, SpaceRules)}
   * would remove.
   *
   * @return <code>true</code> if at least one text node is stripped.
   */
  private static boolean _needsStripping (@NonNull final IXdmNode aRoot, @NonNull final SpaceRules aRules)
  {
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    final ArrayDeque <Boolean> aPreserve = new ArrayDeque <> ();
    aStack.push (aRoot);
    aPreserve.push (Boolean.FALSE);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      boolean bPreserve = aPreserve.pop ().booleanValue ();
      if (aCur.isElement ())
        bPreserve = _isPreserve (aCur, bPreserve);
      for (int i = 0; i < aCur.getChildCount (); ++i)
      {
        final IXdmNode aChild = aCur.getChildAtIndex (i);
        if (aChild.isText ())
        {
          if (_isStripped (aChild, bPreserve, aRules))
            return true;
        }
        else
          if (aChild.isElement ())
          {
            aStack.push (aChild);
            aPreserve.push (Boolean.valueOf (bPreserve));
          }
      }
    }
    return false;
  }

  /**
   * Copy one node without its children and attributes, keeping what the
   * stripped tree must still report for it: location, type annotation, schema
   * type, nilled property, ID kind, base URI and, for an element, its own
   * namespace declarations.
   */
  @NonNull
  private static IXdmNode _copyNode (@NonNull final IXdmNode aSrc)
  {
    final IXdmNode ret = NodeCopier.copyShallow (aSrc);
    ret.setLocation (aSrc.getLine (), aSrc.getColumn ());
    ret.setTypeAnnotation (aSrc.getTypeAnnotation ());
    ret.setSchemaType (aSrc.getSchemaType ());
    ret.setNilled (aSrc.isNilled ());
    ret.setIDKind (aSrc.getIDKind ());
    ret.setBaseUri (aSrc.getBaseUri ());
    if (aSrc.isElement ())
      NodeCopier.copyNamespaces (aSrc, ret, false);
    return ret;
  }

  /**
   * Strip the whitespace text nodes of a tree. Only a document or element
   * root can be stripped, and only if a scan finds a text node to remove;
   * otherwise the input is returned unchanged. The copy is built
   * iteratively: an element root also gets all its in-scope namespaces, a
   * document root keeps its document URI and unparsed entities, attributes
   * and all other children are copied one by one (keeping type annotations,
   * base URIs and locations), and the copy is numbered in document order. xml:space attributes are kept.
   *
   * @param aRoot
   *        The root of a tree (normally a document node).
   * @param aRules
   *        The space rules of the stylesheet.
   * @return The root itself if nothing is stripped, else the root of a
   *         stripped copy.
   */
  @NonNull
  public static IXdmNode strip (@NonNull final IXdmNode aRoot, @NonNull final SpaceRules aRules)
  {
    if (!(aRoot.isDocument () || aRoot.isElement ()) || !_needsStripping (aRoot, aRules))
      return aRoot;
    final IXdmNode ret = _copyNode (aRoot);
    if (aRoot.isElement ())
      NodeCopier.copyNamespaces (aRoot, ret, true);
    ret.setDocumentUri (aRoot.getDocumentUri ());
    if (aRoot.isDocument ())
      for (final String sName : aRoot.getAllUnparsedEntityNames ())
      {
        final String [] aEntity = aRoot.getUnparsedEntity (sName);
        ret.addUnparsedEntity (sName, aEntity[0], aEntity[1]);
      }
    final ArrayDeque <Job> aStack = new ArrayDeque <> ();
    aStack.push (new Job (aRoot, ret, false));
    while (!aStack.isEmpty ())
    {
      final Job aJob = aStack.pop ();
      final IXdmNode aSrc = aJob.source ();
      final boolean bPreserve = aSrc.isElement () ? _isPreserve (aSrc, aJob.preserve ()) : aJob.preserve ();
      for (int i = 0; i < aSrc.getAttributeCount (); ++i)
        aJob.target ().addAttribute (_copyNode (aSrc.getAttributeAtIndex (i)));
      for (int i = 0; i < aSrc.getChildCount (); ++i)
      {
        final IXdmNode aChild = aSrc.getChildAtIndex (i);
        if (aChild.isText () && _isStripped (aChild, bPreserve, aRules))
          continue;
        final IXdmNode aCopy = _copyNode (aChild);
        aJob.target ().appendChild (aCopy);
        if (aChild.isElement ())
          aStack.push (new Job (aChild, aCopy, bPreserve));
      }
    }
    ret.assignDocumentOrder ();
    return ret;
  }
}
