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
import java.util.Map;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.fn.FnAccessor;
import com.helger.xpath.xdm.EIDKind;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Copies nodes the way xsl:copy-of does: deep, without type annotations,
 * iteratively (the depth of a tree never threatens the Java stack).
 *
 * @author Philip Helger
 */
@Immutable
public final class NodeCopier
{
  @PresentForCodeCoverage
  private static final NodeCopier INSTANCE = new NodeCopier ();

  private NodeCopier ()
  {}

  private record Job (@NonNull IXdmNode source, @NonNull IXdmNode target)
  {}

  /**
   * Create an attribute node; an <code>xml:id</code> attribute is an ID
   * (XDM 3.1 section 6.3.3) with its value whitespace normalized.
   *
   * @param aName
   *        Name.
   * @param sValue
   *        Value.
   * @return The new attribute.
   */
  @NonNull
  public static IXdmNode createAttribute (@NonNull final XdmQName aName, @NonNull final String sValue)
  {
    if (aName.isSameName (XdmConstants.NS_XML, "id"))
    {
      final IXdmNode ret = IXdmNode.createAttribute (aName, sValue.trim ().replaceAll ("[ \\t\\r\\n]+", " "));
      ret.setIDKind (EIDKind.ID);
      return ret;
    }
    return IXdmNode.createAttribute (aName, sValue);
  }

  /**
   * Copy one node without its children (and without its attributes).
   *
   * @param aNode
   *        Node to copy.
   * @return The new parentless node.
   */
  @NonNull
  public static IXdmNode copyShallow (@NonNull final IXdmNode aNode)
  {
    return switch (aNode.getKind ())
    {
      case DOCUMENT -> IXdmNode.createDocument ();
      case ELEMENT -> IXdmNode.createElement (aNode.getName ());
      case ATTRIBUTE -> createAttribute (aNode.getName (), aNode.getValue ());
      case TEXT -> IXdmNode.createText (aNode.getValue ());
      case COMMENT -> IXdmNode.createComment (aNode.getValue ());
      case PROCESSING_INSTRUCTION -> IXdmNode.createProcessingInstruction (aNode.getName ().localName (),
                                                                          aNode.getValue ());
      case NAMESPACE -> IXdmNode.createNamespace (aNode.getName () == null ? "" : aNode.getName ().localName (),
                                                 aNode.getValue ());
    };
  }

  /**
   * Declare the namespaces of a source element on its copy.
   *
   * @param aSource
   *        The source element.
   * @param aCopy
   *        The new element.
   * @param bAllInScope
   *        <code>true</code> for all in-scope namespaces (the root of a copy),
   *        <code>false</code> for the declarations of the element only.
   */
  public static void copyNamespaces (@NonNull final IXdmNode aSource,
                                     @NonNull final IXdmNode aCopy,
                                     final boolean bAllInScope)
  {
    final Map <String, String> aNamespaces = bAllInScope ? aSource.getInScopeNamespaces ()
                                                         : aSource.getDeclaredNamespaces ();
    for (final Map.Entry <String, String> aEntry : aNamespaces.entrySet ())
      if (!"xml".equals (aEntry.getKey ()) && !(bAllInScope && aEntry.getValue ().isEmpty ()))
        aCopy.declareNamespace (aEntry.getKey (), aEntry.getValue ());
  }

  /**
   * Copy the unparsed entities of a document node to another document node.
   *
   * @param aSource
   *        Source node (nothing happens unless it is a document node).
   * @param aTarget
   *        Target node (nothing happens unless it is a document node).
   */
  public static void copyUnparsedEntities (@NonNull final IXdmNode aSource, @NonNull final IXdmNode aTarget)
  {
    if (!aSource.isDocument () || !aTarget.isDocument ())
      return;
    for (final String sName : aSource.getAllUnparsedEntityNames ())
    {
      final String [] aEntity = aSource.getUnparsedEntity (sName);
      aTarget.addUnparsedEntity (sName, aEntity[0], aEntity[1]);
    }
  }

  /**
   * Deep copy of a node.
   *
   * @param aNode
   *        The node to copy.
   * @param bCopyNamespaces
   *        <code>true</code> to copy the namespaces of elements
   *        (copy-namespaces="yes"); with <code>false</code> only the
   *        namespaces the names need are declared later by the namespace
   *        fixup.
   * @return The new parentless node.
   */
  /**
   * Copy the schema-related properties (type annotation, nilled, ID kind) of an
   * element or attribute.
   *
   * @param aSource
   *        The original.
   * @param aTarget
   *        The copy.
   */
  public static void copyAnnotation (@NonNull final IXdmNode aSource, @NonNull final IXdmNode aTarget)
  {
    aTarget.setSchemaType (aSource.getSchemaType ());
    if (aSource.getSchemaType () == null)
      aTarget.setTypeAnnotation (aSource.getTypeAnnotation ());
    aTarget.setNilled (aSource.isNilled ());
    // The is-id and is-idrefs properties go with the copy (also under
    // validation="strip")
    if (aSource.getIDKind () != EIDKind.NONE)
      aTarget.setIDKind (aSource.getIDKind ());
  }

  @NonNull
  public static IXdmNode copyDeep (@NonNull final IXdmNode aNode, final boolean bCopyNamespaces)
  {
    return copyDeep (aNode, bCopyNamespaces, false);
  }

  /**
   * @param aNode
   *        The node to copy.
   * @param bCopyNamespaces
   *        <code>true</code> to copy the namespaces of elements.
   * @param bCopyAnnotations
   *        <code>true</code> to keep the type annotations (validation
   *        "preserve" of a schema-aware processor), <code>false</code> for
   *        untyped copies.
   * @return The new parentless node.
   */
  @NonNull
  public static IXdmNode copyDeep (@NonNull final IXdmNode aNode,
                                  final boolean bCopyNamespaces,
                                  final boolean bCopyAnnotations)
  {
    final IXdmNode ret = copyShallow (aNode);
    if (bCopyAnnotations && (aNode.isElement () || aNode.isAttribute ()))
      copyAnnotation (aNode, ret);
    // The copy keeps the base URI of the original, unless it has an xml:base
    // attribute of its own
    if (aNode.isDocument () || aNode.isElement () && aNode.getAttribute (XdmConstants.NS_XML, "base") == null)
    {
      final String sBase = FnAccessor.getBaseUri (aNode);
      if (sBase != null)
        ret.setBaseUri (sBase);
    }
    if (!aNode.isDocument () && !aNode.isElement ())
      return ret;
    if (aNode.isElement () && bCopyNamespaces)
      copyNamespaces (aNode, ret, true);
    copyUnparsedEntities (aNode, ret);
    final ArrayDeque <Job> aStack = new ArrayDeque <> ();
    aStack.push (new Job (aNode, ret));
    while (!aStack.isEmpty ())
    {
      final Job aJob = aStack.pop ();
      final IXdmNode aSrc = aJob.source ();
      final IXdmNode aDst = aJob.target ();
      final int nAttrs = aSrc.getAttributeCount ();
      for (int i = 0; i < nAttrs; ++i)
      {
        final IXdmNode aAttr = aSrc.getAttributeAtIndex (i);
        final IXdmNode aAttrCopy = createAttribute (aAttr.getName (), aAttr.getValue ());
        if (bCopyAnnotations)
          copyAnnotation (aAttr, aAttrCopy);
        aDst.addAttribute (aAttrCopy);
      }
      final int nChildren = aSrc.getChildCount ();
      for (int i = 0; i < nChildren; ++i)
      {
        final IXdmNode aChild = aSrc.getChildAtIndex (i);
        final IXdmNode aChildCopy = copyShallow (aChild);
        aDst.appendChild (aChildCopy);
        if (aChild.isElement ())
        {
          if (bCopyAnnotations)
            copyAnnotation (aChild, aChildCopy);
          if (bCopyNamespaces)
            copyNamespaces (aChild, aChildCopy, false);
          aStack.push (new Job (aChild, aChildCopy));
        }
      }
    }
    return ret;
  }
}
