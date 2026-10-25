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
package com.helger.xpath.ops;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmSchemaType;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * fn:data and effective boolean value. Owner: atomic-values workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class Atomizer
{
  @PresentForCodeCoverage
  private static final Atomizer INSTANCE = new Atomizer ();

  private Atomizer ()
  {}

  @NonNull
  private static String _describe (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof XdmMap)
      return "a map";
    if (aItem instanceof XdmArray)
      return "an array";
    if (aItem instanceof IXdmFunction)
      return "a function item";
    return aItem.getClass ().getSimpleName ();
  }

  /**
   * The namespaces of a node, for typed values of type xs:QName and
   * xs:NOTATION.
   */
  @NonNull
  private static StaticContext _getNamespaceContext (@NonNull final XdmNode aNode)
  {
    final XdmNode aElement = aNode.isElement () ? aNode : aNode.getParent ();
    final StaticContext ret = new StaticContext ();
    if (aElement != null)
      for (final Map.Entry <String, String> aEntry : aElement.getInScopeNamespaces ().entrySet ())
        if (aEntry.getKey ().isEmpty ())
          ret.setDefaultElementNamespace (aEntry.getValue ());
        else
          ret.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    return ret;
  }

  /**
   * Typed value of a node of a schema-validated tree (XDM 3.1 sections 6.2.4
   * and 6.3.4).
   */
  private static void _addSchemaTypedValue (@NonNull final XdmNode aNode,
                                            @NonNull final XdmSchemaType aSchemaType,
                                            final XdmSequence.@NonNull Builder aBuilder)
  {
    if (aNode.isNilled ())
      return;
    final XdmQName aBase = aSchemaType.getAtomicBase ();
    final EAtomicType eType = aBase == null ? null : EAtomicType.getFromLocalName (aBase.localName ());
    if (eType == null)
    {
      // Complex content (mixed or element-only cannot be told apart), or a
      // simple type whose built-in base is unknown
      aBuilder.add (XdmAtomic.untyped (aNode.getStringValue ()));
      return;
    }
    final StaticContext aNamespaces = eType == EAtomicType.QNAME || eType == EAtomicType.NOTATION
                                                                                                  ? _getNamespaceContext (aNode)
                                                                                                  : null;
    final String sValue = aNode.getStringValue ();
    if (aSchemaType.isList ())
    {
      for (final String sToken : XsdLexical.collapseWhitespace (sValue).split (" "))
        if (!sToken.isEmpty ())
          aBuilder.add (AtomicCaster.castFromString (sToken, eType, aNamespaces));
    }
    else
      aBuilder.add (AtomicCaster.castFromString (sValue, eType, aNamespaces));
  }

  /**
   * Typed value of a node validated with the schema component model available
   * (Apache Xerces-J): content types are known, and values of user-defined
   * types, lists and unions are typed item by item.
   *
   * @return <code>false</code> if the value cannot be computed this way.
   */
  private static boolean _addDefinitionTypedValue (@NonNull final XdmNode aNode,
                                                   @NonNull final IXdmTypeDefinition aType,
                                                   final XdmSequence.@NonNull Builder aBuilder)
  {
    if (aNode.isNilled ())
      return true;
    switch (aType.getContentType ())
    {
      case EMPTY:
        return true;
      case ELEMENT_ONLY:
        throw XPathException.of ("FOTY0012",
                                 "The element " +
                                             aNode.getName ().getEQName () +
                                             " has element-only content and therefore no typed value");
      case MIXED:
        aBuilder.add (XdmAtomic.untyped (aNode.getStringValue ()));
        return true;
      default:
        break;
    }
    final IXdmTypeDefinition aSimple = aType.getSimpleContentType ();
    if (aSimple == null || aSimple.getVariety () == null)
    {
      aBuilder.add (XdmAtomic.untyped (aNode.getStringValue ()));
      return true;
    }
    final StaticContext aNamespaces = aSimple.isNamespaceSensitive () ? _getNamespaceContext (aNode) : null;
    try
    {
      aBuilder.addAll (SchemaTypeOps.toAtomics (aSimple.validate (aNode.getStringValue (),
                                                                  aNamespaces == null ? null
                                                                                      : sPrefix -> sPrefix.isEmpty () ? aNamespaces.getDefaultElementNamespace ()
                                                                                                                      : aNamespaces.getNamespaceUri (sPrefix)),
                                                aNamespaces));
      return true;
    }
    catch (final XdmValidationException ex)
    {
      // Not valid (e.g. changed after validation): fall back
      return false;
    }
  }

  private static void _addTypedValue (@NonNull final XdmNode aNode, final XdmSequence.@NonNull Builder aBuilder)
  {
    final XdmSchemaType aSchemaType = aNode.getSchemaType ();
    if (aSchemaType != null)
    {
      final IXdmTypeDefinition aDefinition = aSchemaType.getDefinition ();
      if (aDefinition == null || !_addDefinitionTypedValue (aNode, aDefinition, aBuilder))
        _addSchemaTypedValue (aNode, aSchemaType, aBuilder);
      return;
    }
    final XdmQName aAnnotation = aNode.getTypeAnnotation ();
    if (aAnnotation != null && XdmConstants.NS_XS.equals (aAnnotation.namespaceUri ()))
    {
      // A node annotated with a built-in atomic type has a typed value of it
      final EAtomicType eType = EAtomicType.getFromLocalName (aAnnotation.localName ());
      if (eType != null)
      {
        aBuilder.add (AtomicCaster.castFromString (aNode.getStringValue (), eType, null));
        return;
      }
    }
    aBuilder.add (switch (aNode.getKind ())
    {
      case COMMENT, PROCESSING_INSTRUCTION, NAMESPACE -> XdmAtomic.string (aNode.getStringValue ());
      default -> XdmAtomic.untyped (aNode.getStringValue ());
    });
  }

  private static void _atomizeNonArray (@NonNull final IXdmItem aItem, final XdmSequence.@NonNull Builder aBuilder)
  {
    if (aItem instanceof final XdmAtomic aAtomic)
      aBuilder.add (aAtomic);
    else
      if (aItem instanceof final XdmNode aNode)
        _addTypedValue (aNode, aBuilder);
      else
        throw XPathException.of ("FOTY0013", "Cannot atomize " + _describe (aItem));
  }

  /**
   * Add the atomised form of one item; arrays are flattened without recursion.
   */
  private static void _atomizeItem (@NonNull final IXdmItem aItem, final XdmSequence.@NonNull Builder aBuilder)
  {
    if (!(aItem instanceof final XdmArray aArray))
    {
      _atomizeNonArray (aItem, aBuilder);
      return;
    }
    // Explicit stack of member iterators: the nesting depth is input
    // controlled
    final ArrayDeque <Iterator <IXdmItem>> aStack = new ArrayDeque <> ();
    for (int i = aArray.size () - 1; i >= 0; --i)
      aStack.push (aArray.get (i).iterator ());
    while (!aStack.isEmpty ())
    {
      final Iterator <IXdmItem> aIt = aStack.peek ();
      if (!aIt.hasNext ())
      {
        aStack.pop ();
        continue;
      }
      final IXdmItem aCur = aIt.next ();
      if (aCur instanceof final XdmArray aInner)
      {
        for (int i = aInner.size () - 1; i >= 0; --i)
          aStack.push (aInner.get (i).iterator ());
      }
      else
        _atomizeNonArray (aCur, aBuilder);
    }
  }

  /**
   * Atomise a sequence: nodes give their typed value (xs:untypedAtomic for
   * documents, elements, attributes and text without schema; xs:string for
   * comments, processing instructions and namespace nodes), arrays are
   * flattened recursively, atomic values stay.
   *
   * @param aSeq
   *        The sequence to atomise.
   * @return A sequence of {@link XdmAtomic} only. The input itself if it is
   *         already atomic.
   * @throws com.helger.xpath.XPathException
   *         FOTY0013 for maps and function items.
   */
  @NonNull
  public static XdmSequence atomize (@NonNull final XdmSequence aSeq)
  {
    final int nSize = aSeq.size ();
    int nFirstNonAtomic = 0;
    while (nFirstNonAtomic < nSize && aSeq.get (nFirstNonAtomic) instanceof XdmAtomic)
      nFirstNonAtomic++;
    if (nFirstNonAtomic == nSize)
      return aSeq;
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (int i = 0; i < nFirstNonAtomic; ++i)
      aBuilder.add (aSeq.get (i));
    for (int i = nFirstNonAtomic; i < nSize; ++i)
      _atomizeItem (aSeq.get (i), aBuilder);
    return aBuilder.build ();
  }

  /**
   * The typed value of a node: for a node of a schema-validated tree the
   * value cast to the built-in base of its schema type (a sequence for list
   * types, empty for nilled elements, the untyped string value for complex
   * content); the value cast to the annotated type for a node annotated with
   * a built-in atomic type; xs:string for comments, processing instructions
   * and namespace nodes; otherwise xs:untypedAtomic of the string value.
   * <p>
   * The JDK validator does not tell element-only from mixed content, so an
   * element with element-only content has the untyped string value instead of
   * raising FOTY0012.
   *
   * @param aNode
   *        The node.
   * @return The typed value, zero or more atomic values.
   */
  @NonNull
  public static XdmSequence getTypedValues (@NonNull final XdmNode aNode)
  {
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    _addTypedValue (aNode, aBuilder);
    return aBuilder.build ();
  }

  /**
   * The typed value of a node whose typed value is a single atomic value, see
   * {@link #getTypedValues(XdmNode)}.
   *
   * @param aNode
   *        The node.
   * @return The typed value.
   * @throws com.helger.xpath.XPathException
   *         XPTY0004 if the typed value is not exactly one atomic value
   *         (nilled elements, list types).
   */
  @NonNull
  public static XdmAtomic getTypedValue (@NonNull final XdmNode aNode)
  {
    final XdmSequence aValues = getTypedValues (aNode);
    if (aValues.size () != 1)
      throw XPathException.of ("XPTY0004",
                               "The typed value of the node consists of " + aValues.size () + " atomic values");
    return (XdmAtomic) aValues.get (0);
  }

  /**
   * @param aSeq
   *        Any sequence.
   * @return The effective boolean value (XPath 3.1 section 2.4.3).
   * @throws com.helger.xpath.XPathException
   *         FORG0006 where the EBV is not defined.
   */
  public static boolean getEffectiveBooleanValue (@NonNull final XdmSequence aSeq)
  {
    if (aSeq.isEmpty ())
      return false;
    final IXdmItem aFirst = aSeq.get (0);
    if (aFirst instanceof XdmNode)
      return true;
    if (aSeq.size () > 1)
      throw XPathException.of ("FORG0006",
                               "The effective boolean value of a sequence of " +
                                           aSeq.size () +
                                           " items starting with an atomic value or function is not defined");
    if (aFirst instanceof final XdmAtomic aAtomic)
    {
      final EAtomicType eType = aAtomic.getType ();
      if (eType == EAtomicType.BOOLEAN)
        return aAtomic.getBoolean ();
      if (aAtomic.isStringLike ())
        return !aAtomic.getString ().isEmpty ();
      if (eType.isNumeric ())
      {
        final Object aPayload = aAtomic.getValue ();
        if (aPayload instanceof final BigInteger aBI)
          return aBI.signum () != 0;
        if (aPayload instanceof final BigDecimal aBD)
          return aBD.signum () != 0;
        final double d = aAtomic.getAsDouble ();
        return d != 0 && !Double.isNaN (d);
      }
      throw XPathException.of ("FORG0006",
                               "The effective boolean value of a value of type " +
                                           eType.getQualifiedName () +
                                           " is not defined");
    }
    throw XPathException.of ("FORG0006", "The effective boolean value of " + _describe (aFirst) + " is not defined");
  }

  /**
   * @param aItem
   *        The item.
   * @return The string value of an item as fn:string defines it.
   * @throws com.helger.xpath.XPathException
   *         FOTY0014 for function items, maps and arrays.
   */
  @NonNull
  public static String getStringValue (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final XdmAtomic aAtomic)
      return aAtomic.getLexical ();
    if (aItem instanceof final XdmNode aNode)
      return aNode.getStringValue ();
    throw XPathException.of ("FOTY0014", "The string value of " + _describe (aItem) + " is not defined");
  }
}
