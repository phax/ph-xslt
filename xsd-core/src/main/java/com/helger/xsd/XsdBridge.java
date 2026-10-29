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

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.EIDKind;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmSchemaType;
import com.helger.xpath.xdm.XdmTreeBuilder;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.IXdmAttributeDeclaration;
import com.helger.xpath.xdm.schema.IXdmElementDeclaration;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xsd.XsdValidationRun.DefaultAttr;

/**
 * The XSLT/XPath schema-awareness bridge (go-xslt <code>bridge.go</code>):
 * component lookup by name, derivation tests, casting to schema types and
 * validation of an existing tree in place, exposed as the
 * <code>IXdmSchemaSet</code> SPI of <code>xpath-xdm</code>.
 * <p>
 * The walk runs on a detached, text-normalised clone of the subtree (a
 * constructed tree has empty and adjacent text nodes, which the validator
 * never sees from the parser, and its ancestors must not be reachable from
 * identity constraints), and the annotations are written back to the caller's
 * nodes only after the validation succeeded. The state of one validation is an
 * {@link XsdValidationRun}; the schema is never changed.
 *
 * @author Philip Helger
 */
@ThreadSafe
final class XsdBridge implements IXdmSchemaSet
{
  private static final XdmQName ANY_TYPE = new XdmQName (XdmConstants.NS_XS, "anyType", "xs");
  /** The local name of an anonymous type starts with this; no NCName can. */
  private static final String ANONYMOUS_PREFIX = "#t";

  /** The cloned subtree of a validation and, in document order, its elements and attributes with the originals. */
  private record Cloned (@NonNull IXdmNode root,
                         @NonNull ICommonsList <IXdmNode> clones,
                         @NonNull ICommonsList <IXdmNode> originals)
  {}

  /** The annotation of a node: its type and the type its value was validated against. */
  private record TypeKey (@NonNull IXsdType type, @NonNull IXsdType valueType)
  {
    @Override
    public boolean equals (final Object o)
    {
      return o instanceof final TypeKey aOther && aOther.type == type && aOther.valueType == valueType;
    }

    @Override
    public int hashCode ()
    {
      return System.identityHashCode (type) * 31 + System.identityHashCode (valueType);
    }
  }

  private final XsdSchema m_aSchema;
  private final SimpleReadWriteLock m_aRWLock = new SimpleReadWriteLock ();
  /** The anonymous-type registry (go-xslt <code>anonReg</code>), both directions. */
  private final ICommonsMap <IXsdType, XdmQName> m_aAnonymousNames = new CommonsHashMap <> ();
  private final ICommonsMap <String, IXsdType> m_aAnonymousTypes = new CommonsHashMap <> ();

  XsdBridge (@NonNull final XsdSchema aSchema)
  {
    m_aSchema = aSchema;
  }

  @NonNull
  XsdSchema getSchema ()
  {
    return m_aSchema;
  }

  /**
   * go-xslt <code>anonName</code>: an anonymous type gets a stable identity,
   * assigned in the order it is asked for. Per component and per schema set;
   * the name cannot be spelled in a schema document or a stylesheet.
   */
  @NonNull
  XdmQName getAnonymousName (@NonNull final IXsdType aType)
  {
    final XdmQName aExisting = m_aRWLock.readLockedGet ( () -> m_aAnonymousNames.get (aType));
    if (aExisting != null)
      return aExisting;
    return m_aRWLock.writeLockedGet ( () -> {
      XdmQName aName = m_aAnonymousNames.get (aType);
      if (aName == null)
      {
        aName = new XdmQName ("", ANONYMOUS_PREFIX + (m_aAnonymousNames.size () + 1));
        m_aAnonymousNames.put (aType, aName);
        m_aAnonymousTypes.put (aName.localName (), aType);
      }
      return aName;
    });
  }

  @Nullable
  private IXsdType _getAnonymousType (@NonNull final XsdName aName)
  {
    if (!aName.space ().isEmpty () || !aName.local ().startsWith (ANONYMOUS_PREFIX))
      return null;
    return m_aRWLock.readLockedGet ( () -> m_aAnonymousTypes.get (aName.local ()));
  }

  /**
   * go-xslt <code>typeByName</code>: resolve a type name over the schema's
   * global types and the built-ins, with the rules of an instance's
   * <code>xsi:type</code> - the XDM-only names are not schema types, and the
   * built-ins of XSD 1.1 do not exist under 1.0.
   *
   * @param aName
   *        The expanded name.
   * @return The type, or <code>null</code>.
   */
  @Nullable
  IXsdType typeByName (@NonNull final XsdName aName)
  {
    if (XsdConstants.NS_XS.equals (aName.space ()))
    {
      final String sLocal = aName.local ();
      if ("untyped".equals (sLocal) ||
          "untypedAtomic".equals (sLocal) ||
          "numeric".equals (sLocal) ||
          !m_aSchema.isXsd11 () && ("anyAtomicType".equals (sLocal) || XsdCompiler.isXsd11OnlyBuiltin (sLocal)))
        return null;
      if ("error".equals (sLocal))
        return XsdSimpleType.ERROR;
      final XsdSimpleType aList = XsdCompiler.builtinList (sLocal);
      if (aList != null)
        return aList;
      final EAtomicType eAtom = XsdCompiler.builtinAtom (sLocal);
      if (eAtom != null)
      {
        final XsdSimpleType aSimple = XsdSimpleType.builtin (eAtom);
        if (eAtom == EAtomicType.DATE_TIME_STAMP)
        {
          aSimple.m_aFacets.m_sExplicitTZ = "required";
          aSimple.m_aFacets.m_aFixed.add ("explicitTimezone");
        }
        return aSimple;
      }
      if ("anyType".equals (sLocal))
        return XsdComplexType.anyType ();
      return m_aSchema.m_aTypes.get (aName);
    }
    final IXsdType aAnonymous = _getAnonymousType (aName);
    return aAnonymous != null ? aAnonymous : m_aSchema.m_aTypes.get (aName);
  }

  @NonNull
  XsdTypeDefinition definitionOf (@NonNull final IXsdType aType)
  {
    return new XsdTypeDefinition (this, aType, XsdTypeDefinition.nameOf (aType));
  }

  @Nullable
  public IXdmTypeDefinition getTypeDefinition (@NonNull final XdmQName aName)
  {
    final IXsdType aType = typeByName (XsdName.of (aName));
    return aType == null ? null : definitionOf (aType);
  }

  @Nullable
  public IXdmElementDeclaration getElementDeclaration (@NonNull final XdmQName aName)
  {
    final XsdElementDecl aDecl = m_aSchema.m_aElements.get (XsdName.of (aName));
    return aDecl == null ? null : new XsdElementDeclaration (this, aDecl);
  }

  @Nullable
  public IXdmAttributeDeclaration getAttributeDeclaration (@NonNull final XdmQName aName)
  {
    final XsdAttributeDecl aDecl = m_aSchema.m_aAttributes.get (XsdName.of (aName));
    return aDecl == null ? null : new XsdAttributeDeclaration (this, aDecl);
  }

  /**
   * go-xslt <code>cloneForValidation</code>: deep-copy an element into a
   * detached tree with merged adjacent text nodes and no empty ones, and
   * record every element and attribute with its original. Comments and
   * processing instructions are left out; they have no part in validity.
   * Iterative.
   */
  @NonNull
  private static Cloned _cloneForValidation (@NonNull final IXdmNode aElement)
  {
    final ICommonsList <IXdmNode> aClones = new CommonsArrayList <> ();
    final ICommonsList <IXdmNode> aOriginals = new CommonsArrayList <> ();
    final IXdmNode aRoot = IXdmNode.createElement (aElement.getName ());
    final ArrayDeque <IXdmNode []> aWork = new ArrayDeque <> ();
    aWork.push (new IXdmNode [] { aElement, aRoot });
    while (!aWork.isEmpty ())
    {
      final IXdmNode [] aPair = aWork.pop ();
      final IXdmNode aOrig = aPair[0];
      final IXdmNode aCopy = aPair[1];
      aCopy.setLocation (aOrig.getLine (), aOrig.getColumn ());
      aClones.add (aCopy);
      aOriginals.add (aOrig);
      for (final Map.Entry <String, String> aEntry : aOrig.getDeclaredNamespaces ().entrySet ())
        aCopy.declareNamespace (aEntry.getKey (), aEntry.getValue ());
      for (final IXdmNode aAttr : aOrig.getAllAttributes ())
      {
        final IXdmNode aAttrCopy = IXdmNode.createAttribute (aAttr.getName (), aAttr.getValue ());
        aAttrCopy.setLocation (aAttr.getLine (), aAttr.getColumn ());
        aCopy.addAttribute (aAttrCopy);
        aClones.add (aAttrCopy);
        aOriginals.add (aAttr);
      }
      final ICommonsList <IXdmNode []> aChildren = new CommonsArrayList <> ();
      StringBuilder aText = null;
      final int nChildren = aOrig.getChildCount ();
      for (int i = 0; i <= nChildren; i++)
      {
        final IXdmNode aChild = i < nChildren ? aOrig.getChildAtIndex (i) : null;
        if (aChild != null && aChild.isText ())
        {
          if (aText == null)
            aText = new StringBuilder ();
          aText.append (aChild.getValue ());
          continue;
        }
        if (aText != null && aText.length () > 0)
        {
          aCopy.appendChild (IXdmNode.createText (aText.toString ()));
          aText = null;
        }
        if (aChild != null && aChild.isElement ())
        {
          final IXdmNode aChildCopy = IXdmNode.createElement (aChild.getName ());
          aCopy.appendChild (aChildCopy);
          aChildren.add (new IXdmNode [] { aChild, aChildCopy });
        }
      }
      for (int i = aChildren.size () - 1; i >= 0; i--)
        aWork.push (aChildren.get (i));
    }
    // Detaching loses the bindings declared on an ancestor, which xsi:type and
    // QName values resolve against
    final ICommonsOrderedMap <String, String> aDeclared = aElement.getDeclaredNamespaces ();
    for (final Map.Entry <String, String> aEntry : aElement.getInScopeNamespaces ().entrySet ())
      if (!"xml".equals (aEntry.getKey ()) && !aDeclared.containsKey (aEntry.getKey ()))
        aRoot.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    aRoot.assignDocumentOrder ();
    return new Cloned (aRoot, aClones, aOriginals);
  }

  private static boolean _isXmlWhitespace (@NonNull final String sText)
  {
    for (int i = 0; i < sText.length (); i++)
    {
      final char c = sText.charAt (i);
      if (c != ' ' && c != '\t' && c != '\r' && c != '\n')
        return false;
    }
    return true;
  }

  private static boolean _isSingleToken (@NonNull final String sValue)
  {
    return XsdLexHelper.xmlFields (sValue).size () == 1;
  }

  /**
   * XDM 3.1 sections 6.2.3 and 6.3.3: <code>is-id</code> if the typed value is
   * a single xs:ID (also as the only item of a list), <code>is-idrefs</code>
   * if an item of the typed value is an xs:IDREF.
   */
  @NonNull
  private static EIDKind _getSchemaIDKind (@NonNull final XdmSchemaType aType, @NonNull final String sValue)
  {
    final XdmQName aBase = aType.getAtomicBase ();
    if (aBase == null)
      return EIDKind.NONE;
    switch (aBase.localName ())
    {
      case "ID":
        return !aType.isList () || _isSingleToken (sValue) ? EIDKind.ID : EIDKind.NONE;
      case "IDREF":
        return aType.isList () ? EIDKind.IDREFS : EIDKind.IDREF;
      default:
        return EIDKind.NONE;
    }
  }

  /**
   * The type a node's value was validated against: for a union, the member
   * that accepted the value (go-xslt <code>acceptingUnionMember</code>); the
   * node's own type otherwise.
   */
  @NonNull
  private static IXsdType _valueTypeOf (@NonNull final IXsdType aType, @NonNull final String sValue, @NonNull final IXdmNode aAt)
  {
    if (aType instanceof final XsdSimpleType aSimple && aSimple != XsdSimpleType.ABSENT && aSimple.m_eVariety == EXsdVariety.UNION)
    {
      final XsdSimpleType aMember = XsdTypeDefinition.acceptingMember (aSimple, sValue, aAt);
      if (aMember != null)
        return aMember;
    }
    return aType;
  }

  @NonNull
  private XdmSchemaType _schemaTypeOf (@NonNull final ICommonsMap <TypeKey, XdmSchemaType> aCache,
                                       @NonNull final IXsdType aType,
                                       @NonNull final IXsdType aValueType)
  {
    return aCache.computeIfAbsent (new TypeKey (aType, aValueType),
                                   k -> XdmSchemaType.of (definitionOf (k.type ()), definitionOf (k.valueType ())));
  }

  /**
   * A default attribute in a namespace needs a prefix bound to it on the
   * element: an existing one, or a new declaration (namespace fixup as in XSD
   * 1.1 section 3.4.5.1).
   */
  @NonNull
  private static XdmQName _fixupAttributeName (@NonNull final IXdmNode aElement, @NonNull final XsdName aName)
  {
    if (aName.space ().isEmpty ())
      return new XdmQName (aName.local ());
    if (XdmConstants.NS_XML.equals (aName.space ()))
      return new XdmQName (aName.space (), aName.local (), "xml");
    final ICommonsOrderedMap <String, String> aInScope = aElement.getInScopeNamespaces ();
    for (final Map.Entry <String, String> aEntry : aInScope.entrySet ())
      if (!aEntry.getKey ().isEmpty () && aName.space ().equals (aEntry.getValue ()))
        return new XdmQName (aName.space (), aName.local (), aEntry.getKey ());
    int n = 1;
    while (aInScope.containsKey ("ns" + n))
      n++;
    final String sPrefix = "ns" + n;
    aElement.declareNamespace (sPrefix, aName.space ());
    return new XdmQName (aName.space (), aName.local (), sPrefix);
  }

  /**
   * Write the types the validation recorded onto the caller's nodes
   * (go-xslt <code>annotate</code>). A node that was not assessed (absorbed by
   * a skip wildcard) has its annotation replaced by xs:anyType, because a
   * stale one would be worse than none.
   */
  private void _annotate (@NonNull final Cloned aCloned, @NonNull final XsdValidationRun aRun)
  {
    final ICommonsMap <IXdmNode, IXsdType> aTypes = aRun.getNodeTypes ();
    final ICommonsMap <TypeKey, XdmSchemaType> aCache = new CommonsHashMap <> ();
    for (int i = 0; i < aCloned.clones ().size (); i++)
    {
      final IXdmNode aCopy = aCloned.clones ().get (i);
      final IXdmNode aOrig = aCloned.originals ().get (i);
      final boolean bElement = aOrig.isElement ();
      final IXsdType aType = aTypes.get (aCopy);
      if (aType == null)
      {
        aOrig.setSchemaType (null);
        aOrig.setNilled (false);
        aOrig.setTypeAnnotation (bElement ? ANY_TYPE : null);
        continue;
      }
      final boolean bNilled = bElement && aRun.getNodeNilled ().contains (aCopy);
      final IXsdType aValueType = bNilled ? aType : _valueTypeOf (aType, aCopy.getStringValue (), aCopy);
      final XdmSchemaType aSchemaType = _schemaTypeOf (aCache, aType, aValueType);
      aOrig.setSchemaType (aSchemaType);
      aOrig.setTypeAnnotation (null);
      if (bElement)
      {
        // [nilled] is assigned unconditionally: this episode replaces what a
        // previous one left
        aOrig.setNilled (bNilled);
        if (aSchemaType.getAtomicBase () != null && !bNilled)
          aOrig.setIDKind (_getSchemaIDKind (aSchemaType, aOrig.getStringValue ()));
      }
      else
      {
        final EIDKind eKind = _getSchemaIDKind (aSchemaType, aOrig.getValue ());
        if (eKind != EIDKind.NONE)
        {
          aOrig.setIDKind (eKind);
          aOrig.setValue (XdmTreeBuilder.normaliseTokenised (aOrig.getValue ()));
        }
      }
    }
  }

  /**
   * go-xslt <code>supplyDefaults</code>: materialise the default and fixed
   * attributes the validation supplied on the caller's elements.
   *
   * @return <code>true</code> if an attribute was added.
   */
  private boolean _supplyDefaults (@NonNull final Cloned aCloned, @NonNull final XsdValidationRun aRun)
  {
    final ICommonsMap <IXdmNode, ICommonsList <DefaultAttr>> aDefaults = aRun.getNodeDefaults ();
    if (aDefaults.isEmpty ())
      return false;
    final ICommonsMap <TypeKey, XdmSchemaType> aCache = new CommonsHashMap <> ();
    boolean bChanged = false;
    for (int i = 0; i < aCloned.clones ().size (); i++)
    {
      final IXdmNode aCopy = aCloned.clones ().get (i);
      final IXdmNode aOrig = aCloned.originals ().get (i);
      if (!aOrig.isElement ())
        continue;
      final ICommonsList <DefaultAttr> aList = aDefaults.get (aCopy);
      if (aList == null)
        continue;
      for (final DefaultAttr aDefault : aList)
      {
        // Only an absent use is recorded, but the same original can be reached
        // by more than one episode
        if (aOrig.getAttribute (aDefault.name ().space (), aDefault.name ().local ()) != null)
          continue;
        final IXdmNode aAttr = IXdmNode.createAttribute (_fixupAttributeName (aOrig, aDefault.name ()), aDefault.value ());
        aAttr.setLocation (aOrig.getLine (), aOrig.getColumn ());
        aOrig.addAttribute (aAttr);
        bChanged = true;
        if (aDefault.type () != null)
        {
          final IXsdType aValueType = _valueTypeOf (aDefault.type (), aDefault.value (), aOrig);
          final XdmSchemaType aSchemaType = _schemaTypeOf (aCache, aDefault.type (), aValueType);
          aAttr.setSchemaType (aSchemaType);
          final EIDKind eKind = _getSchemaIDKind (aSchemaType, aDefault.value ());
          if (eKind != EIDKind.NONE)
          {
            aAttr.setIDKind (eKind);
            aAttr.setValue (XdmTreeBuilder.normaliseTokenised (aDefault.value ()));
          }
        }
      }
    }
    return bChanged;
  }

  /**
   * go-xslt <code>stripElementOnlyWhitespace</code>: a whitespace-only text
   * node cannot survive in an element whose type has element-only content
   * (XDM 3.1 section 6.7.3).
   *
   * @return <code>true</code> if a node was removed.
   */
  private static boolean _stripElementOnlyWhitespace (@NonNull final Cloned aCloned, @NonNull final XsdValidationRun aRun)
  {
    boolean bChanged = false;
    final ICommonsMap <IXdmNode, IXsdType> aTypes = aRun.getNodeTypes ();
    for (int i = 0; i < aCloned.clones ().size (); i++)
    {
      final IXdmNode aOrig = aCloned.originals ().get (i);
      if (!aOrig.isElement () || aOrig.getChildCount () == 0)
        continue;
      if (!(aTypes.get (aCloned.clones ().get (i)) instanceof final XsdComplexType aCT) ||
          aCT.m_eKind != EXsdContentKind.ELEMENT_ONLY)
        continue;
      for (final IXdmNode aChild : aOrig.getAllChildren ())
        if (aChild.isText () && _isXmlWhitespace (aChild.getValue ()))
        {
          aOrig.removeChild (aChild);
          bChanged = true;
        }
    }
    return bChanged;
  }

  /**
   * go-xslt <code>ValidateNode</code>: validate an element in place.
   *
   * @param aElement
   *        The element.
   * @param aType
   *        The type to validate against, or <code>null</code> for the
   *        declaration of the element name.
   * @param bDocument
   *        <code>true</code> if the document-level constraints apply (ID
   *        uniqueness and IDREF targets): an element-rooted episode must not
   *        report an IDREF to an ID outside the subtree.
   * @return <code>true</code> if the structure of the tree changed.
   * @throws XdmValidationException
   *         if it is not valid; nothing is changed then
   */
  private boolean _validateInPlace (@NonNull final IXdmNode aElement,
                                    @Nullable final IXsdType aType,
                                    final boolean bDocument) throws XdmValidationException
  {
    // Schema location hints are never followed: the component set of an XSLT
    // host is fixed by xsl:import-schema. Their position still matters (XSD
    // 1.0 section 4.3.2).
    final XsdError aLateHint = XsdInstanceValidator.checkLateHints (m_aSchema, aElement);
    if (aLateHint != null)
      throw new XdmValidationException (aLateHint.toString (), new XsdInvalidException (aLateHint));
    final Cloned aCloned = _cloneForValidation (aElement);
    final XsdValidationRun aRun = new XsdValidationRun (m_aSchema, aElement.getOwnerDocument ());
    aRun.setElementScope (!bDocument);
    // The XSD 1.1 rule that an xs:ENTITY value names an unparsed entity is
    // not applied: the schema may be a 1.0 one
    aRun.setLenientEntities (true);
    aRun.enableNodeTypes ();
    aRun.enableNodeNilled ();
    aRun.enableNodeDefaults ();
    try
    {
      if (aType == null)
        XsdInstanceValidator.validateRoot (aRun, aCloned.root ());
      else
        XsdInstanceValidator.validateRoot (aRun,
                                           aCloned.root (),
                                           (r, e) -> XsdInstanceValidator.validateAsType (r, aType, e));
      if (bDocument)
        aRun.checkIDRefs ();
    }
    catch (final XsdInvalidException ex)
    {
      throw new XdmValidationException (ex.getError ().toString (), ex, ex.getError ().code ().startsWith ("cvc-id."));
    }
    _annotate (aCloned, aRun);
    final boolean bAdded = _supplyDefaults (aCloned, aRun);
    final boolean bStripped = _stripElementOnlyWhitespace (aCloned, aRun);
    return bAdded || bStripped;
  }

  public void validateElement (@NonNull final IXdmNode aElement,
                               @Nullable final IXdmTypeDefinition aType,
                               final boolean bDocumentLevel) throws XdmValidationException
  {
    IXsdType aXsdType = null;
    if (aType != null)
    {
      if (!(aType instanceof final XsdTypeDefinition aDef))
        throw new IllegalArgumentException ("Not a type definition of this schema processor: " + aType);
      aXsdType = aDef.getType ();
    }
    try
    {
      if (_validateInPlace (aElement, aXsdType, bDocumentLevel))
        aElement.assignDocumentOrder ();
    }
    catch (final XdmValidationException ex)
    {
      throw new XdmValidationException ("Element " + aElement.getName ().getEQName () + " is not valid: " + ex.getMessage (),
                                        ex,
                                        ex.isDocumentConstraint ());
    }
  }

  public void validateDocument (@NonNull final IXdmNode aDocument) throws XdmValidationException
  {
    if (!aDocument.isDocument ())
      throw new IllegalArgumentException ("Not a document node");
    final IXdmNode aRoot = XsdNodes.rootElement (aDocument);
    if (aRoot == null)
      throw new IllegalArgumentException ("The document has no document element");
    try
    {
      if (_validateInPlace (aRoot, null, true))
        aDocument.assignDocumentOrder ();
    }
    catch (final XdmValidationException ex)
    {
      throw new XdmValidationException ("Document is not valid: " + ex.getMessage (), ex, ex.isDocumentConstraint ());
    }
  }
}
