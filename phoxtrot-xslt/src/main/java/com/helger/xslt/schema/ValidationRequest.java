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
package com.helger.xslt.schema;

import java.util.ArrayDeque;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.EIDKind;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmSchemaType;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.EXdmSimpleVariety;
import com.helger.xpath.xdm.schema.IXdmAttributeDeclaration;
import com.helger.xpath.xdm.schema.IXdmElementDeclaration;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xpath.xdm.schema.XdmTypedToken;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * The compiled <code>[xsl:]validation</code> / <code>[xsl:]type</code> request
 * of one node constructing instruction (XSLT 3.0 section 25.4), the run-time
 * half of schema-awareness: it gives constructed nodes their type annotations.
 * A port of go-xslt's <code>schema_validation.go</code>; the validation itself
 * is done by the schema set (Apache Xerces-J).
 * <p>
 * {@link #compile(CompileContext, XslElement, EKind)} returns
 * <code>null</code> for a basic processor, so a stylesheet that does not claim
 * schema-awareness runs exactly as before.
 *
 * @author Philip Helger
 */
@Immutable
public final class ValidationRequest
{
  /** The effective validation mode. */
  public enum EMode
  {
    STRIP,
    PRESERVE,
    STRICT,
    LAX
  }

  /**
   * The construct the request is written on: "preserve" means something
   * different for each.
   */
  public enum EKind
  {
    /** xsl:element and literal result elements */
    ELEMENT,
    /** xsl:attribute */
    ATTRIBUTE,
    /** xsl:copy */
    COPY,
    /** xsl:copy-of */
    COPY_OF,
    /** xsl:document, xsl:result-document */
    DOCUMENT,
    /** xsl:source-document, xsl:merge-source: no request without attributes */
    SOURCE_DOCUMENT
  }

  private static final XdmQName ANY_TYPE = new XdmQName (XdmConstants.NS_XS, "anyType", "xs");
  private static final XdmQName UNTYPED_ATOMIC = new XdmQName (XdmConstants.NS_XS, "untypedAtomic", "xs");

  private final EMode m_eMode;
  private final IXdmTypeDefinition m_aType;
  // xs:untyped or xs:untypedAtomic as [xsl:]type (no type definitions)
  private final String m_sSpecialType;
  private final EKind m_eKind;
  private final IXdmSchemaSet m_aSchemaSet;

  private ValidationRequest (@NonNull final EMode eMode,
                             @Nullable final IXdmTypeDefinition aType,
                             @Nullable final String sSpecialType,
                             @NonNull final EKind eKind,
                             @NonNull final IXdmSchemaSet aSchemaSet)
  {
    m_eMode = eMode;
    m_aType = aType;
    m_sSpecialType = sSpecialType;
    m_eKind = eKind;
    m_aSchemaSet = aSchemaSet;
  }

  /**
   * Resolve the value of a <code>[xsl:]type</code> attribute. A
   * URIQualifiedName is taken as written; an unprefixed name is in the
   * namespace of the inherited <code>[xsl:]xpath-default-namespace</code>
   * (no namespace without one), the default namespace for elements and
   * types; a prefixed name uses the namespaces in scope on the element.
   *
   * @param aEl
   *        The element carrying the attribute.
   * @param sType
   *        The attribute value, surrounding whitespace allowed.
   * @return The expanded type name.
   * @throws XsltException
   *         XTSE1520 if the value is not a lexical QName or EQName, XTSE0280
   *         if its prefix is not bound.
   */
  @NonNull
  private static XdmQName _resolveTypeName (@NonNull final XslElement aEl, @NonNull final String sType)
  {
    final String sLexical = sType.trim ();
    if (sLexical.startsWith ("Q{"))
    {
      final int n = sLexical.indexOf ('}');
      if (n > 0 && XsltNames.isNCName (sLexical.substring (n + 1)))
        return new XdmQName (sLexical.substring (2, n).trim (), sLexical.substring (n + 1));
      throw XsltException.at (aEl, "XTSE1520", "type='" + sType + "' is not a valid QName");
    }
    if (!XsltNames.isLexicalQName (sLexical))
      throw XsltException.at (aEl, "XTSE1520", "type='" + sType + "' is not a valid QName");
    final int nColon = sLexical.indexOf (':');
    if (nColon < 0)
    {
      // An unprefixed type name is in the default namespace for elements and
      // types
      final String sDefault = aEl.getInheritedStandardAttr ("xpath-default-namespace");
      return new XdmQName (sDefault == null ? "" : sDefault.trim (), sLexical);
    }
    final String sPrefix = sLexical.substring (0, nColon);
    final String sUri = aEl.lookupNamespaceUri (sPrefix);
    if (sUri == null)
      throw XsltException.at (aEl, "XTSE0280", "The prefix '" + sPrefix + "' of type '" + sType + "' is not bound");
    return new XdmQName (sUri, sLexical.substring (nColon + 1), sPrefix);
  }

  /**
   * Compile the validation request of an instruction (XSLT 3.0 section
   * 25.4.1). A <code>type</code> attribute (in no namespace on an XSLT
   * element, in the XSLT namespace on a literal result element) wins over
   * <code>validation</code>: <code>xs:untyped</code> and
   * <code>xs:untypedAtomic</code> become special requests, any other name
   * must resolve to a type definition of the in-scope schema. Without
   * <code>type</code>, a <code>validation</code> value of strict, lax or
   * preserve selects that mode and any other value strip (the two
   * attributes together and invalid values are rejected by
   * {@link com.helger.xslt.compile.ElementValidator}). Without either, the
   * inherited <code>[xsl:]default-validation</code> applies: preserve if it
   * says so, else strip; it does not apply to xsl:source-document.
   *
   * @param aCtx
   *        The compile context.
   * @param aEl
   *        The instruction (or literal result element).
   * @param eKind
   *        The construct.
   * @return The request, or <code>null</code> if the stylesheet is not
   *         schema-aware (or for xsl:source-document without validation
   *         attributes).
   * @throws XsltException
   *         XTSE1520 for a type name that is invalid or names no type
   *         definition, XTSE0280 for an unbound prefix in it, XTSE1530 for a
   *         complex type on xsl:attribute, and XTTE1545 for a
   *         namespace-sensitive type on xsl:attribute (the constructed
   *         attribute is parentless when it is validated).
   */
  @Nullable
  public static ValidationRequest compile (@NonNull final CompileContext aCtx,
                                           @NonNull final XslElement aEl,
                                           @NonNull final EKind eKind)
  {
    final IXdmSchemaSet aSchemaSet = aCtx.getStylesheet ().getSchemaSet ();
    if (aSchemaSet == null)
      return null;
    final String sValidation = aEl.isXsl () ? aEl.getAttr ("validation") : aEl.getAttribute (XsltNames.NS_XSL, "validation");
    final String sType = aEl.isXsl () ? aEl.getAttr ("type") : aEl.getAttribute (XsltNames.NS_XSL, "type");
    if (sType != null)
    {
      final XdmQName aName = _resolveTypeName (aEl, sType);
      if (aName.isSameName (XdmConstants.NS_XS, "untyped") || aName.isSameName (XdmConstants.NS_XS, "untypedAtomic"))
        return new ValidationRequest (EMode.STRIP, null, aName.localName (), eKind, aSchemaSet);
      final IXdmTypeDefinition aType = aSchemaSet.getTypeDefinition (aName);
      if (aType == null)
        throw XsltException.at (aEl, "XTSE1520", "type='" + sType + "' names no type definition in the in-scope schema components");
      if (eKind == EKind.ATTRIBUTE && aType.isComplex ())
        throw XsltException.at (aEl, "XTSE1530", "The type attribute of xsl:attribute must not refer to a complex type");
      // A parentless attribute cannot be validated against a
      // namespace-sensitive type
      if (eKind == EKind.ATTRIBUTE && aType.isNamespaceSensitive ())
        throw XsltException.at (aEl,
                                "XTTE1545",
                                "type='" + sType + "' is namespace-sensitive and cannot validate a parentless attribute");
      return new ValidationRequest (EMode.STRIP, aType, null, eKind, aSchemaSet);
    }
    if (sValidation != null)
    {
      final EMode eMode = switch (sValidation.trim ())
      {
        case "strict" -> EMode.STRICT;
        case "lax" -> EMode.LAX;
        case "preserve" -> EMode.PRESERVE;
        default -> EMode.STRIP;
      };
      return new ValidationRequest (eMode, null, null, eKind, aSchemaSet);
    }
    // default-validation does not apply to xsl:source-document
    if (eKind == EKind.SOURCE_DOCUMENT)
      return null;
    final String sDefault = aEl.getInheritedStandardAttr ("default-validation");
    final EMode eMode = sDefault != null && "preserve".equals (sDefault.trim ()) ? EMode.PRESERVE : EMode.STRIP;
    return new ValidationRequest (eMode, null, null, eKind, aSchemaSet);
  }

  /**
   * @return <code>true</code> if nodes copied by the instruction keep their
   *         type annotations before the request is applied (preserve).
   */
  public boolean isPreserve ()
  {
    return m_eMode == EMode.PRESERVE && m_aType == null && m_sSpecialType == null;
  }

  /**
   * @return <code>true</code> for plain strip (no type): copies are made
   *         without annotations, and there is nothing to apply to them.
   */
  public boolean isStrip ()
  {
    return m_eMode == EMode.STRIP && m_aType == null && m_sSpecialType == null;
  }

  /**
   * @return <code>true</code> if the request performs a validation episode
   *         (strict, lax or a type).
   */
  public boolean isValidating ()
  {
    return m_eMode == EMode.STRICT || m_eMode == EMode.LAX || m_aType != null;
  }

  /**
   * Strip: every element of the subtree becomes <code>xs:untyped</code>,
   * every attribute <code>xs:untypedAtomic</code>, nothing is nilled. The ID
   * properties stay. The subtree is walked iteratively with an explicit
   * stack; clearing the schema type and type annotation is what makes a node
   * untyped.
   *
   * @param aNode
   *        The node (any kind).
   */
  public static void stripAnnotations (@NonNull final IXdmNode aNode)
  {
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aNode);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if (aCur.isElement () || aCur.isAttribute ())
      {
        aCur.setSchemaType (null);
        aCur.setTypeAnnotation (null);
        aCur.setNilled (false);
      }
      for (final IXdmNode aAttr : aCur.getAllAttributes ())
      {
        aAttr.setSchemaType (null);
        aAttr.setTypeAnnotation (null);
      }
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
  }

  /**
   * @param aNode
   *        An element or attribute.
   * @return <code>true</code> if its typed value is namespace-sensitive (of
   *         type xs:QName or xs:NOTATION or derived from them). With a type
   *         definition the definition decides; with only an atomic base type
   *         its local name is compared; an untyped node is never
   *         namespace-sensitive.
   */
  public static boolean isNamespaceSensitive (@NonNull final IXdmNode aNode)
  {
    final XdmSchemaType aSchemaType = aNode.getSchemaType ();
    if (aSchemaType == null)
      return false;
    final IXdmTypeDefinition aDef = aSchemaType.getDefinition ();
    if (aDef != null)
      return aDef.isNamespaceSensitive ();
    final XdmQName aBase = aSchemaType.getAtomicBase ();
    return aBase != null && ("QName".equals (aBase.localName ()) || "NOTATION".equals (aBase.localName ()));
  }

  /**
   * @param aNode
   *        A node.
   * @return <code>true</code> if the node or a node of its subtree (attributes
   *         included) has namespace-sensitive content.
   */
  public static boolean hasNamespaceSensitiveContent (@NonNull final IXdmNode aNode)
  {
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aNode);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if ((aCur.isElement () || aCur.isAttribute ()) && isNamespaceSensitive (aCur))
        return true;
      for (final IXdmNode aAttr : aCur.getAllAttributes ())
        if (isNamespaceSensitive (aAttr))
          return true;
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return false;
  }

  /**
   * XTTE0950: copying with validation="preserve" must not separate
   * namespace-sensitive content from its namespaces (XSLT 3.0 section
   * 11.9.1). An attribute fails if it is namespace-sensitive itself, as it
   * is copied without its parent element; any other node fails only with
   * <code>copy-namespaces="no"</code> and namespace-sensitive content
   * anywhere in its subtree.
   *
   * @param aNode
   *        The node to copy.
   * @param bCopyNamespaces
   *        The copy-namespaces value.
   * @throws XPathException
   *         XTTE0950
   */
  public static void checkPreserveCopy (@NonNull final IXdmNode aNode, final boolean bCopyNamespaces)
  {
    if (aNode.isAttribute () ? isNamespaceSensitive (aNode) : !bCopyNamespaces && hasNamespaceSensitiveContent (aNode))
      throw new XPathException ("XTTE0950",
                                "A node with namespace-sensitive content cannot be copied with validation='preserve' " +
                                            (aNode.isAttribute () ? "without its parent element" : "and copy-namespaces='no'"));
  }

  /**
   * Annotate an element as <code>xs:anyType</code> (not nilled) without
   * touching its content; a no-op for other node kinds.
   *
   * @param aNode
   *        The node.
   */
  private static void _setAnyType (@NonNull final IXdmNode aNode)
  {
    if (!aNode.isElement ())
      return;
    aNode.setSchemaType (null);
    aNode.setTypeAnnotation (ANY_TYPE);
    aNode.setNilled (false);
  }

  /**
   * Apply <code>validation="preserve"</code>, whose meaning depends on the
   * construct (XSLT 3.0 section 25.4.1.1): a new element (xsl:element,
   * literal result element) and an element made by xsl:copy are annotated
   * <code>xs:anyType</code> while their content keeps its annotations; an
   * attribute made by xsl:attribute is stripped (xs:untypedAtomic); a node
   * copied by xsl:copy (attribute) or xsl:copy-of and document nodes keep
   * all annotations.
   *
   * @param aNode
   *        The constructed or copied node.
   */
  private void _applyPreserve (@NonNull final IXdmNode aNode)
  {
    switch (m_eKind)
    {
      case ELEMENT:
        // The new element is xs:anyType, its content keeps its annotations
        _setAnyType (aNode);
        break;
      case ATTRIBUTE:
        stripAnnotations (aNode);
        break;
      case COPY:
        // xsl:copy does not copy the content, so the element cannot keep its
        // type; a copied attribute keeps its annotation
        _setAnyType (aNode);
        break;
      default:
        // xsl:copy-of and documents keep all annotations
        break;
    }
  }

  /**
   * @return The node a validation episode runs on: the node itself for an
   *         element or attribute, or the single element child of a document
   *         node; <code>null</code> for other node kinds and for a document
   *         node with no or several element children or with a non-empty
   *         text child (comments and processing instructions are ignored).
   */
  @Nullable
  private static IXdmNode _getTarget (@NonNull final IXdmNode aNode)
  {
    if (aNode.isElement () || aNode.isAttribute ())
      return aNode;
    if (!aNode.isDocument ())
      return null;
    IXdmNode ret = null;
    for (final IXdmNode aChild : aNode.getAllChildren ())
      if (aChild.isElement ())
      {
        if (ret != null)
          return null;
        ret = aChild;
      }
      else
        if (aChild.isText () && !aChild.getValue ().isEmpty ())
          return null;
    return ret;
  }

  /**
   * Build the prefix resolver used to validate namespace-sensitive values
   * (xs:QName, xs:NOTATION) of an attribute. <code>xml</code> is always
   * bound; the empty prefix resolves to the default namespace of the element
   * or to "" (no namespace) if there is none; other prefixes use the
   * namespaces in scope on the element. Without an element only the empty
   * prefix resolves (to no namespace).
   *
   * @param aElement
   *        The parent element of the attribute, or <code>null</code>.
   * @return The resolver, returning <code>null</code> for an unbound prefix.
   */
  @NonNull
  private static UnaryOperator <String> _getNamespaceResolver (@Nullable final IXdmNode aElement)
  {
    return sPrefix -> {
      if (aElement == null)
        return sPrefix.isEmpty () ? "" : null;
      if ("xml".equals (sPrefix))
        return XdmConstants.NS_XML;
      final String sUri = aElement.lookupNamespaceUri (sPrefix);
      return sUri == null && sPrefix.isEmpty () ? "" : sUri;
    };
  }

  /**
   * Validate an attribute value against a simple type and annotate it. The
   * schema type records the declared type and, for a single token of a
   * union type, the member type that matched. The is-id and is-idrefs
   * properties follow the type: a single token derived from xs:ID makes it
   * an ID, a token derived from xs:IDREF makes it an IDREF (single token,
   * not a list type) or IDREFS (otherwise); the ID kind is left unchanged
   * when no such token occurs.
   *
   * @param aAttr
   *        The attribute; its parent, if any, resolves prefixes.
   * @param aType
   *        The simple type to validate against.
   * @throws XdmValidationException
   *         if the value is not valid for the type
   */
  private static void _validateAttributeValue (@NonNull final IXdmNode aAttr,
                                               @NonNull final IXdmTypeDefinition aType) throws XdmValidationException
  {
    final ICommonsList <XdmTypedToken> aTokens = aType.validate (aAttr.getValue (), _getNamespaceResolver (aAttr.getParent ()));
    final IXdmTypeDefinition aValueType = aTokens.size () == 1 && aType.getMemberTypes ().isNotEmpty () ? aTokens.get (0).type ()
                                                                                                        : aType;
    aAttr.setTypeAnnotation (null);
    aAttr.setSchemaType (XdmSchemaType.of (aType, aValueType));
    // The is-id and is-idrefs properties follow the type
    EIDKind eIDKind = EIDKind.NONE;
    for (final XdmTypedToken aToken : aTokens)
      switch (aToken.builtinType ().localName ())
      {
        case "ID":
          if (aTokens.size () == 1)
            eIDKind = EIDKind.ID;
          break;
        case "IDREF":
          eIDKind = aTokens.size () == 1 && aType.getVariety () != EXdmSimpleVariety.LIST ? EIDKind.IDREF : EIDKind.IDREFS;
          break;
        default:
          break;
      }
    if (eIDKind != EIDKind.NONE)
      aAttr.setIDKind (eIDKind);
  }

  /**
   * @return The EQName of a named node for error messages, else its node
   *         kind.
   */
  @NonNull
  private static String _describe (@NonNull final IXdmNode aNode)
  {
    return aNode.getName () != null ? aNode.getName ().getEQName () : aNode.getKind ().toString ();
  }

  /**
   * Lax assessment of an element that has neither a top-level declaration
   * nor an <code>xsi:type</code> (XSLT 3.0 section 25.4.1.1, which makes the
   * recursive checking that XML Schema leaves optional mandatory): the
   * element's validity is not known, so it is annotated
   * <code>xs:anyType</code>; each of its attributes is validated against its
   * top-level declaration, or annotated <code>xs:untypedAtomic</code> if
   * there is none; each child element is validated by the schema set if it
   * has a top-level declaration or an <code>xsi:type</code>, and is
   * otherwise assessed laxly in the same way. The walk is iterative.
   * Document-level constraints are not checked across the laxly assessed
   * children.
   *
   * @param aElement
   *        The element without declaration.
   * @throws XdmValidationException
   *         if an attribute or a child element with a declaration is invalid
   */
  private void _assessLax (@NonNull final IXdmNode aElement) throws XdmValidationException
  {
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aElement);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      _setAnyType (aCur);
      for (final IXdmNode aAttr : aCur.getAllAttributes ())
      {
        final IXdmAttributeDeclaration aDecl = m_aSchemaSet.getAttributeDeclaration (aAttr.getName ());
        if (aDecl != null)
          _validateAttributeValue (aAttr, aDecl.getTypeDefinition ());
        else
          stripAnnotations (aAttr);
      }
      for (final IXdmNode aChild : aCur.getAllChildren ())
        if (aChild.isElement ())
        {
          if (m_aSchemaSet.getElementDeclaration (aChild.getName ()) != null ||
              aChild.getAttribute (XdmConstants.NS_XSI, "type") != null)
            m_aSchemaSet.validateElement (aChild, null, false);
          else
            aStack.push (aChild);
        }
    }
  }

  /**
   * Strict or lax validation (XSLT 3.0 section 25.4.1.1) of the constructed
   * node, or of the single element child of a constructed document node
   * (section 25.4.2). An attribute is validated against its top-level
   * attribute declaration. An element is validated against its top-level
   * element declaration or its <code>xsi:type</code> by the schema set. If
   * neither exists, strict fails; lax annotates an attribute
   * <code>xs:untypedAtomic</code> and assesses an element laxly (see
   * {@link #_assessLax}). Other node kinds are left alone.
   *
   * @param aNode
   *        The constructed or copied node.
   * @param bDocumentLevel
   *        <code>true</code> to check the document-level constraints (ID
   *        uniqueness, IDREF targets) as well.
   * @throws XPathException
   *         XTTE1550 for a document node of the wrong shape, XTTE1512 for a
   *         missing declaration under strict, XTTE1555 for a violated
   *         document-level constraint, XTTE1510 (strict) or XTTE1515 (lax)
   *         for any other validation failure.
   */
  private void _validate (@NonNull final IXdmNode aNode, final boolean bDocumentLevel)
  {
    final IXdmNode aTarget = _getTarget (aNode);
    if (aTarget == null)
    {
      if (aNode.isDocument ())
        throw new XPathException ("XTTE1550", "A validated document node must have exactly one element child and no text nodes");
      return;
    }
    final boolean bStrict = m_eMode == EMode.STRICT;
    try
    {
      if (aTarget.isAttribute ())
      {
        final IXdmAttributeDeclaration aDecl = m_aSchemaSet.getAttributeDeclaration (aTarget.getName ());
        if (aDecl == null)
        {
          if (bStrict)
            throw new XPathException ("XTTE1512", "No top-level declaration for the attribute " + _describe (aTarget));
          // Lax: the validity is not known
          stripAnnotations (aTarget);
          return;
        }
        _validateAttributeValue (aTarget, aDecl.getTypeDefinition ());
        return;
      }
      final IXdmElementDeclaration aDecl = m_aSchemaSet.getElementDeclaration (aTarget.getName ());
      if (aDecl == null && aTarget.getAttribute (XdmConstants.NS_XSI, "type") == null)
      {
        if (bStrict)
          throw new XPathException ("XTTE1512", "No top-level declaration for the element " + _describe (aTarget));
        // Lax without a declaration: the attributes and children are assessed
        _assessLax (aTarget);
        return;
      }
      m_aSchemaSet.validateElement (aTarget, null, bDocumentLevel);
    }
    catch (final XdmValidationException ex)
    {
      if (ex.isDocumentConstraint ())
        throw new XPathException ("XTTE1555", "Document-level constraints are not satisfied: " + ex.getMessage ());
      throw new XPathException (bStrict ? "XTTE1510" : "XTTE1515", ex.getMessage ());
    }
  }

  /**
   * Validation against the type named by <code>[xsl:]type</code> (XSLT 3.0
   * section 25.4.1.2). <code>xs:untyped</code> strips all annotations;
   * <code>xs:untypedAtomic</code> strips them too and annotates an element
   * <code>xs:untypedAtomic</code>, failing for an element with element
   * children. Any other type validates the attribute value or element
   * (for a document node: its single element child) through the schema
   * set.
   *
   * @param aNode
   *        The constructed or copied node.
   * @param bDocumentLevel
   *        <code>true</code> to check the document-level constraints as
   *        well.
   * @throws XPathException
   *         XTTE1540 for content that does not match the type, XTTE1550 for
   *         a document node of the wrong shape, XTTE1535 for a complex type
   *         and an attribute node, XTTE1545 for a parentless attribute and a
   *         namespace-sensitive type, XTTE1555 for a violated document-level
   *         constraint.
   */
  private void _validateAgainstType (@NonNull final IXdmNode aNode, final boolean bDocumentLevel)
  {
    if ("untyped".equals (m_sSpecialType))
    {
      stripAnnotations (aNode);
      return;
    }
    if ("untypedAtomic".equals (m_sSpecialType))
    {
      // As xs:string, annotated xs:untypedAtomic; element children fail
      if (aNode.isElement ())
        for (final IXdmNode aChild : aNode.getAllChildren ())
          if (aChild.isElement ())
            throw new XPathException ("XTTE1540", "An element with element children cannot be validated against xs:untypedAtomic");
      stripAnnotations (aNode);
      if (aNode.isElement ())
        aNode.setTypeAnnotation (UNTYPED_ATOMIC);
      return;
    }
    final IXdmNode aTarget = _getTarget (aNode);
    if (aTarget == null)
    {
      if (aNode.isDocument ())
        throw new XPathException ("XTTE1550", "A validated document node must have exactly one element child and no text nodes");
      return;
    }
    try
    {
      if (aTarget.isAttribute ())
      {
        if (m_aType.isComplex ())
          throw new XPathException ("XTTE1535", "The type attribute refers to a complex type, but an attribute node is validated");
        if (aTarget.getParent () == null && m_aType.isNamespaceSensitive ())
          throw new XPathException ("XTTE1545", "A parentless attribute cannot be validated against a namespace-sensitive type");
        _validateAttributeValue (aTarget, m_aType);
        return;
      }
      m_aSchemaSet.validateElement (aTarget, m_aType, bDocumentLevel);
    }
    catch (final XdmValidationException ex)
    {
      if (ex.isDocumentConstraint ())
        throw new XPathException ("XTTE1555", "Document-level constraints are not satisfied: " + ex.getMessage ());
      throw new XPathException ("XTTE1540", ex.getMessage ());
    }
  }

  /**
   * Apply the request to the node the instruction has just constructed (or
   * copied). Child nodes are validated before their parents, so the request of
   * the parent overrides those of its content, as section 25.4.1 requires.
   * Document-level constraints are checked for a document node and for the
   * requests of xsl:document, xsl:result-document, xsl:source-document and
   * xsl:merge-source. A type request validates against the type; otherwise
   * strict and lax validate, preserve applies the per-construct rules, and
   * strip removes all annotations of the subtree.
   *
   * @param aNode
   *        The node.
   * @throws XPathException
   *         XTTE1510, XTTE1512, XTTE1515, XTTE1535, XTTE1540, XTTE1545,
   *         XTTE1550, XTTE1555
   */
  public void apply (@NonNull final IXdmNode aNode)
  {
    final boolean bDocumentLevel = aNode.isDocument () || m_eKind == EKind.DOCUMENT || m_eKind == EKind.SOURCE_DOCUMENT;
    if (m_aType != null || m_sSpecialType != null)
      _validateAgainstType (aNode, bDocumentLevel);
    else
      switch (m_eMode)
      {
        case STRICT:
        case LAX:
          _validate (aNode, bDocumentLevel);
          break;
        case PRESERVE:
          _applyPreserve (aNode);
          break;
        default:
          stripAnnotations (aNode);
          break;
      }
  }

  @Override
  public String toString ()
  {
    return "ValidationRequest[" + m_eMode + ", " + m_eKind + (m_aType != null ? ", " + m_aType : "") + "]";
  }
}
