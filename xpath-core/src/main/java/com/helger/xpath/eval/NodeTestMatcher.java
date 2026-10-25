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
import com.helger.xpath.xdm.XdmSchemaType;
import com.helger.xpath.xdm.schema.EXdmSimpleVariety;
import com.helger.xpath.xdm.schema.IXdmAttributeDeclaration;
import com.helger.xpath.xdm.schema.IXdmElementDeclaration;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * Node tests of axis steps and the node kind tests of sequence types (XPath
 * 3.1 sections 3.3.2.1 and 2.5.5). Nodes of a schema-validated tree carry their
 * {@link XdmSchemaType}, which answers the derivation questions; only
 * built-in type names can occur in a kind test, as there are no in-scope
 * schema types. Otherwise, unless a node carries an explicit annotation with a
 * built-in type (see {@link XdmNode#getTypeAnnotation()}), every element is
 * annotated <code>xs:untyped</code>, every attribute
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

  /**
   * @return <code>true</code> if the schema type of a validated node is or
   *         derives from the type of a kind test (XPath 3.1 section 2.5.6.1).
   */
  private static boolean _isSchemaTypeOf (@NonNull final XdmSchemaType aSchemaType, @NonNull final XdmQName aTypeName)
  {
    final String sNamespace = aTypeName.namespaceUri ();
    final String sLocal = aTypeName.localName ();
    if (!XdmConstants.NS_XS.equals (sNamespace))
      return aSchemaType.isDerivedFrom (sNamespace, sLocal);
    switch (sLocal)
    {
      case "anyType":
        return true;
      case "anySimpleType":
        return aSchemaType.isSimple ();
      case "untyped":
      case "untypedAtomic":
        return false;
      case "anyAtomicType":
      {
        // Atomic: a simple type derived by restriction from a primitive type,
        // so neither a list nor a union
        final XdmQName aBase = aSchemaType.getAtomicBase ();
        return aSchemaType.isSimple () &&
               !aSchemaType.isList () &&
               aBase != null &&
               aSchemaType.isDerivedFrom (XdmConstants.NS_XS, aBase.localName ());
      }
      case "numeric":
        // The union of its member types
        return aSchemaType.isDerivedFrom (XdmConstants.NS_XS, "double") ||
               aSchemaType.isDerivedFrom (XdmConstants.NS_XS, "float") ||
               aSchemaType.isDerivedFrom (XdmConstants.NS_XS, "decimal");
      default:
        return aSchemaType.isDerivedFrom (XdmConstants.NS_XS, sLocal);
    }
  }

  private static boolean _isAnnotationOf (@NonNull final XdmNode aNode,
                                          @NonNull final XdmQName aTypeName,
                                          final boolean bElement)
  {
    final XdmSchemaType aSchemaType = aNode.getSchemaType ();
    if (aSchemaType != null)
      return _isSchemaTypeOf (aSchemaType, aTypeName);
    final XdmQName aAnnotation = aNode.getTypeAnnotation ();
    if (aAnnotation == null)
      return _isUntypedAnnotationOf (aTypeName, bElement);
    return isSchemaTypeDerivedFrom (resolveSchemaType (aAnnotation), resolveSchemaType (aTypeName));
  }

  /**
   * @return <code>true</code> for a pure union type: a union whose members are
   *         atomic types or pure union types.
   */
  private static boolean _isPureUnion (@NonNull final IXdmTypeDefinition aType)
  {
    if (aType.getVariety () != EXdmSimpleVariety.UNION)
      return false;
    for (final IXdmTypeDefinition aMember : aType.getMemberTypes ())
      if (aMember.getVariety () != EXdmSimpleVariety.ATOMIC && !_isPureUnion (aMember))
        return false;
    return true;
  }

  /**
   * derives-from(AT, ET) of XPath 3.1 section 2.5.6.1: derivation by
   * restriction or extension, or membership in a pure union type
   * (transitively).
   */
  private static boolean _derivesFrom (@NonNull final IXdmTypeDefinition aType, @NonNull final IXdmTypeDefinition aTarget)
  {
    if (aType.isDerivedFrom (aTarget))
      return true;
    if (_isPureUnion (aTarget))
      for (final IXdmTypeDefinition aMember : aTarget.getMemberTypes ())
        if (_derivesFrom (aType, aMember))
          return true;
    return false;
  }

  /**
   * @return <code>true</code> if the type annotation of the node is or derives
   *         from the type definition (of a schema declaration).
   */
  private static boolean _isAnnotationDerivedFrom (@NonNull final XdmNode aNode,
                                                   @NonNull final IXdmTypeDefinition aType,
                                                   final boolean bElement)
  {
    final XdmSchemaType aSchemaType = aNode.getSchemaType ();
    final XdmQName aTypeName = aType.getName ();
    if (aSchemaType != null)
    {
      final IXdmTypeDefinition aNodeType = aSchemaType.getDefinition ();
      if (aNodeType != null)
        return _derivesFrom (aNodeType, aType);
      return aTypeName != null && aSchemaType.isDerivedFrom (aTypeName.namespaceUri (), aTypeName.localName ());
    }
    return aTypeName != null && _isAnnotationOf (aNode, aTypeName, bElement);
  }

  /**
   * schema-element(N) (XPath 3.1 section 2.5.5.4): the name is N or in its
   * substitution group, the type annotation derives from the type of the
   * declaration of that name, and a nilled element needs a nillable
   * declaration.
   */
  private static boolean _matchesSchemaElement (@NonNull final NodeTest aTest, @NonNull final XdmNode aNode)
  {
    final IXdmElementDeclaration aHead = aTest.elementDeclaration ();
    if (aHead == null || !aNode.isElement ())
      return false;
    final IXdmElementDeclaration aDecl = aHead.getSubstitutionMember (aNode.getName ());
    if (aDecl == null)
      return false;
    if (aNode.isNilled () && !aDecl.isNillable ())
      return false;
    return _isAnnotationDerivedFrom (aNode, aDecl.getTypeDefinition (), true);
  }

  private static boolean _matchesSchemaAttribute (@NonNull final NodeTest aTest, @NonNull final XdmNode aNode)
  {
    final IXdmAttributeDeclaration aDecl = aTest.attributeDeclaration ();
    if (aDecl == null || !aNode.isAttribute () || !aDecl.getName ().equals (aNode.getName ()))
      return false;
    return _isAnnotationDerivedFrom (aNode, aDecl.getTypeDefinition (), false);
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
        if (aTest.typeName () == null)
          return true;
        // A nilled element only matches a type followed by '?'
        if (aNode.isNilled () && !aTest.nillable ())
          return false;
        if (aTest.typeDefinition () != null)
          return _isAnnotationDerivedFrom (aNode, aTest.typeDefinition (), true);
        return _isAnnotationOf (aNode, aTest.typeName (), true);
      case ATTRIBUTE:
        if (eKind != ENodeKind.ATTRIBUTE || !_matchesKindName (aTest, aNode))
          return false;
        if (aTest.typeDefinition () != null)
          return _isAnnotationDerivedFrom (aNode, aTest.typeDefinition (), false);
        return aTest.typeName () == null || _isAnnotationOf (aNode, aTest.typeName (), false);
      case DOCUMENT:
        if (eKind != ENodeKind.DOCUMENT)
          return false;
        return aTest.inner () == null || _matchesDocumentInner (aTest.inner (), aNode);
      case NAMESPACE_NODE:
        return eKind == ENodeKind.NAMESPACE;
      case SCHEMA_ELEMENT:
        return _matchesSchemaElement (aTest, aNode);
      case SCHEMA_ATTRIBUTE:
        return _matchesSchemaAttribute (aTest, aNode);
      default:
        return false;
    }
  }

  /**
   * Resolve the type name of a kind test to the built-in schema type hierarchy
   * used by the subtype judgement: the atomic types, the four special types and
   * the built-in list types.
   *
   * @param aTypeName
   *        Type name, may be <code>null</code>.
   * @return The chain position: an {@link EAtomicType} for atomic types, or one
   *         of the strings <code>anyType</code>, <code>anySimpleType</code>,
   *         <code>untyped</code>, <code>NMTOKENS</code>, <code>IDREFS</code>,
   *         <code>ENTITIES</code>; <code>null</code> for an unknown type.
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
      case "NMTOKENS":
      case "IDREFS":
      case "ENTITIES":
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
      return !"anyType".equals (aDerived) && !"untyped".equals (aDerived);
    if (aBase instanceof String)
      return aBase.equals (aDerived);
    if (aBase instanceof final EAtomicType eBase && aDerived instanceof final EAtomicType eDerived)
      return eDerived.isDerivedFrom (eBase);
    return false;
  }
}
