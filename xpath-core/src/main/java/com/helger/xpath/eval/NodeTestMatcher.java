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
package com.helger.xpath.eval;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Node tests of axis steps and the node kind tests of sequence types (XPath
 * 3.1 sections 3.3.2.1 and 2.5.5). This processor is not schema-aware: every
 * element is annotated <code>xs:untyped</code>, every attribute
 * <code>xs:untypedAtomic</code>, and nothing is nilled.
 *
 * @author Philip Helger
 */
@Immutable
public final class NodeTestMatcher
{
  @PresentForCodeCoverage
  private static final NodeTestMatcher INSTANCE = new NodeTestMatcher ();

  private NodeTestMatcher ()
  {}

  /**
   * @param aTypeName
   *        A type name of a kind test.
   * @param bElement
   *        <code>true</code> for an element, <code>false</code> for an
   *        attribute.
   * @return <code>true</code> if the type annotation of an unvalidated element
   *         (<code>xs:untyped</code>) or attribute
   *         (<code>xs:untypedAtomic</code>) is or derives from that type.
   */
  private static boolean _isUntypedAnnotationOf (@NonNull final XdmQName aTypeName, final boolean bElement)
  {
    if (!XdmConstants.NS_XS.equals (aTypeName.namespaceUri ()))
      return false;
    final String sLocal = aTypeName.localName ();
    if (bElement)
      return "untyped".equals (sLocal) || "anyType".equals (sLocal);
    return "untypedAtomic".equals (sLocal) ||
           "anyAtomicType".equals (sLocal) ||
           "anySimpleType".equals (sLocal) ||
           "anyType".equals (sLocal);
  }

  private static boolean _matchesKindName (@NonNull final NodeTest aTest, @NonNull final XdmNode aNode)
  {
    if (aTest.anyName () || aTest.name () == null)
      return true;
    return aTest.name ().equals (aNode.getName ());
  }

  private static boolean _matchesNameTest (@NonNull final NodeTest aTest, @Nullable final XdmQName aNodeName)
  {
    if (aTest.anyName ())
      return true;
    if (aNodeName == null)
      return false;
    final XdmQName aTestName = aTest.name ();
    if (aTestName == null)
      return false;
    if (aTest.anyNamespace ())
      return aTestName.localName ().equals (aNodeName.localName ());
    if (aTest.anyLocalName ())
      return aTestName.namespaceUri ().equals (aNodeName.namespaceUri ());
    return aTestName.equals (aNodeName);
  }

  private static boolean _matchesDocumentInner (@NonNull final NodeTest aInner, @NonNull final XdmNode aDoc)
  {
    XdmNode aElement = null;
    final int nCount = aDoc.getChildCount ();
    for (int i = 0; i < nCount; ++i)
    {
      final XdmNode aChild = aDoc.getChildAtIndex (i);
      switch (aChild.getKind ())
      {
        case ELEMENT:
          if (aElement != null)
            return false;
          aElement = aChild;
          break;
        case TEXT:
          if (!aChild.getValue ().isEmpty ())
            return false;
          break;
        default:
          break;
      }
    }
    return aElement != null && matchesKindTest (aInner, aElement);
  }

  /**
   * Match a node test of an axis step.
   *
   * @param aTest
   *        The node test.
   * @param aNode
   *        The candidate node.
   * @param ePrincipalKind
   *        The principal node kind of the axis: {@link ENodeKind#ATTRIBUTE} for
   *        the attribute axis, {@link ENodeKind#NAMESPACE} for the namespace
   *        axis, {@link ENodeKind#ELEMENT} for all others. Only name tests use
   *        it.
   * @return <code>true</code> if the node passes the test.
   */
  public static boolean matches (@NonNull final NodeTest aTest,
                                 @NonNull final XdmNode aNode,
                                 @NonNull final ENodeKind ePrincipalKind)
  {
    if (aTest.kind () == ENodeTestKind.NAME)
    {
      if (aNode.getKind () != ePrincipalKind)
        return false;
      return _matchesNameTest (aTest, aNode.getName ());
    }
    return matchesKindTest (aTest, aNode);
  }

  /**
   * Match a kind test (anything but a name test) as used in sequence types.
   *
   * @param aTest
   *        The kind test. A name test is matched as an element name test.
   * @param aNode
   *        The candidate node.
   * @return <code>true</code> if the node passes the test.
   */
  public static boolean matchesKindTest (@NonNull final NodeTest aTest, @NonNull final XdmNode aNode)
  {
    final ENodeKind eKind = aNode.getKind ();
    switch (aTest.kind ())
    {
      case NAME:
        return eKind == ENodeKind.ELEMENT && _matchesNameTest (aTest, aNode.getName ());
      case ANY_NODE:
        return true;
      case TEXT:
        return eKind == ENodeKind.TEXT;
      case COMMENT:
        return eKind == ENodeKind.COMMENT;
      case PROCESSING_INSTRUCTION:
        return eKind == ENodeKind.PROCESSING_INSTRUCTION &&
               (aTest.name () == null || aTest.name ().localName ().equals (aNode.getName ().localName ()));
      case ELEMENT:
        if (eKind != ENodeKind.ELEMENT || !_matchesKindName (aTest, aNode))
          return false;
        return aTest.typeName () == null || _isUntypedAnnotationOf (aTest.typeName (), true);
      case ATTRIBUTE:
        if (eKind != ENodeKind.ATTRIBUTE || !_matchesKindName (aTest, aNode))
          return false;
        return aTest.typeName () == null || _isUntypedAnnotationOf (aTest.typeName (), false);
      case DOCUMENT:
        if (eKind != ENodeKind.DOCUMENT)
          return false;
        return aTest.inner () == null || _matchesDocumentInner (aTest.inner (), aNode);
      case NAMESPACE_NODE:
        return eKind == ENodeKind.NAMESPACE;
      case SCHEMA_ELEMENT:
      case SCHEMA_ATTRIBUTE:
        // No schema components are in scope, so no declaration exists
        return false;
      default:
        return false;
    }
  }

  /**
   * Resolve the type name of a kind test to the built-in schema type hierarchy
   * used by the subtype judgement: the atomic types and the four special types.
   *
   * @param aTypeName
   *        Type name, may be <code>null</code>.
   * @return The chain position: an {@link EAtomicType} for atomic types, or one
   *         of the strings <code>anyType</code>, <code>anySimpleType</code>,
   *         <code>untyped</code>; <code>null</code> for an unknown type.
   */
  @Nullable
  static Object resolveSchemaType (@Nullable final XdmQName aTypeName)
  {
    if (aTypeName == null || !XdmConstants.NS_XS.equals (aTypeName.namespaceUri ()))
      return null;
    final String sLocal = aTypeName.localName ();
    switch (sLocal)
    {
      case "anyType":
      case "anySimpleType":
      case "untyped":
        return sLocal;
      default:
        return EAtomicType.getFromLocalName (sLocal);
    }
  }

  /**
   * The derivation judgement for the built-in schema types: anyType is the
   * root, anySimpleType and untyped derive from it, anyAtomicType from
   * anySimpleType, and the atomic types follow {@link EAtomicType}.
   *
   * @param aDerived
   *        A result of {@link #resolveSchemaType(XdmQName)}.
   * @param aBase
   *        A result of {@link #resolveSchemaType(XdmQName)}.
   * @return <code>true</code> if aDerived is aBase or derived from it.
   */
  static boolean isSchemaTypeDerivedFrom (@Nullable final Object aDerived, @Nullable final Object aBase)
  {
    if (aDerived == null || aBase == null)
      return false;
    if ("anyType".equals (aBase))
      return true;
    if ("anySimpleType".equals (aBase))
      return "anySimpleType".equals (aDerived) || aDerived instanceof EAtomicType;
    if ("untyped".equals (aBase))
      return "untyped".equals (aDerived);
    if (aBase instanceof final EAtomicType eBase && aDerived instanceof final EAtomicType eDerived)
      return eDerived.isDerivedFrom (eBase);
    return false;
  }
}
