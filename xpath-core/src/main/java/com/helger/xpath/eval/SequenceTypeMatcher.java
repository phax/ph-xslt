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

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.EOccurrence;
import com.helger.xpath.ast.ItemType;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.ops.AtomicCaster;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.ops.SchemaTypeOps;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * SequenceType matching (XPath 3.1 section 2.5.5), the subtype judgements
 * (section 2.5.6) and the function conversion rules (section 3.1.5.2).
 * Owner: evaluator workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class SequenceTypeMatcher
{
  /** <code>xs:anyAtomicType</code>: the parameter type of a map as function. */
  static final SeqType ANY_ATOMIC_ONE = SeqType.of (ItemType.atomic (EAtomicType.ANY_ATOMIC_TYPE), EOccurrence.ONE);
  /** <code>xs:integer</code>: the parameter type of an array as function. */
  static final SeqType INTEGER_ONE = SeqType.of (ItemType.atomic (EAtomicType.INTEGER), EOccurrence.ONE);

  @PresentForCodeCoverage
  private static final SequenceTypeMatcher INSTANCE = new SequenceTypeMatcher ();

  private SequenceTypeMatcher ()
  {}

  /**
   * @return <code>true</code> if the type is <code>item()*</code>, which every
   *         value matches without any conversion.
   */
  private static boolean _isItemStar (@NonNull final SeqType aType)
  {
    return !aType.empty () &&
           aType.occurrence () == EOccurrence.ZERO_OR_MORE &&
           aType.itemType ().kind () == ItemType.Kind.ITEM;
  }

  /**
   * @return <code>true</code> if a sequence of <code>nCount</code> items
   *         satisfies the occurrence indicator (exactly one, at most one, at
   *         least one, or any number).
   */
  private static boolean _matchesOccurrence (final int nCount, @NonNull final EOccurrence eOcc)
  {
    switch (eOcc)
    {
      case ONE:
        return nCount == 1;
      case ZERO_OR_ONE:
        return nCount <= 1;
      case ONE_OR_MORE:
        return nCount >= 1;
      default:
        return true;
    }
  }

  private static int _getMinOccurs (@NonNull final EOccurrence eOcc)
  {
    return eOcc == EOccurrence.ONE || eOcc == EOccurrence.ONE_OR_MORE ? 1 : 0;
  }

  private static boolean _isUnboundedOccurs (@NonNull final EOccurrence eOcc)
  {
    return eOcc.allowsMany ();
  }

  @NonNull
  private static String _getOccurrenceSuffix (@NonNull final EOccurrence eOcc)
  {
    switch (eOcc)
    {
      case ZERO_OR_ONE:
        return "?";
      case ZERO_OR_MORE:
        return "*";
      case ONE_OR_MORE:
        return "+";
      default:
        return "";
    }
  }

  /**
   * The notation of a node test for messages, close to the XPath syntax:
   * names as EQNames (<code>Q{uri}local</code>), <code>*</code> for a
   * wildcard, the type annotation of <code>element(N, T)</code> and
   * <code>attribute(N, T)</code> with <code>?</code> if nillable, and the
   * inner test of <code>document-node(...)</code>.
   */
  @NonNull
  private static String _getNodeTestAsString (@NonNull final NodeTest aTest)
  {
    final String sName = aTest.anyName () ? "*" : aTest.name () == null ? "" : aTest.name ().getEQName ();
    switch (aTest.kind ())
    {
      case NAME:
        return sName;
      case ANY_NODE:
        return "node()";
      case TEXT:
        return "text()";
      case COMMENT:
        return "comment()";
      case PROCESSING_INSTRUCTION:
        return "processing-instruction(" + sName + ")";
      case ELEMENT:
      case ATTRIBUTE:
      {
        final String sKind = aTest.kind () == ENodeTestKind.ELEMENT ? "element(" : "attribute(";
        if (aTest.typeName () != null)
          return sKind +
                 (sName.isEmpty () ? "*" : sName) +
                 ", " +
                 aTest.typeName ().getEQName () +
                 (aTest.nillable () ? "?" : "") +
                 ")";
        return sKind + sName + ")";
      }
      case DOCUMENT:
        return "document-node(" + (aTest.inner () == null ? "" : _getNodeTestAsString (aTest.inner ())) + ")";
      case NAMESPACE_NODE:
        return "namespace-node()";
      case SCHEMA_ELEMENT:
        return "schema-element(" + sName + ")";
      case SCHEMA_ATTRIBUTE:
        return "schema-attribute(" + sName + ")";
      default:
        return aTest.kind ().name ();
    }
  }

  /**
   * @param aType
   *        An item type.
   * @return A readable notation of the item type for messages.
   */
  @NonNull
  public static String getItemTypeAsString (@NonNull final ItemType aType)
  {
    switch (aType.kind ())
    {
      case ITEM:
        return "item()";
      case ATOMIC:
        if (aType.schemaType () != null)
          return aType.schemaType ().getName () != null ? aType.schemaType ().getName ().getEQName () : "(anonymous type)";
        return aType.atomicType ().getQualifiedName ();
      case NODE:
        return _getNodeTestAsString (aType.nodeTest ());
      case FUNCTION:
      {
        if (aType.wildcard () || aType.returnType () == null)
          return "function(*)";
        final StringBuilder aSB = new StringBuilder ("function(");
        final List <SeqType> aParams = aType.paramTypes ();
        if (aParams != null)
          for (int i = 0; i < aParams.size (); ++i)
          {
            if (i > 0)
              aSB.append (", ");
            aSB.append (getAsString (aParams.get (i)));
          }
        return aSB.append (") as ").append (getAsString (aType.returnType ())).toString ();
      }
      case MAP:
        if (aType.wildcard () || aType.valueType () == null)
          return "map(*)";
        return "map(" +
               aType.mapKeyType ().getQualifiedName () +
               ", " +
               getAsString (aType.valueType ()) +
               ")";
      case ARRAY:
        if (aType.wildcard () || aType.valueType () == null)
          return "array(*)";
        return "array(" + getAsString (aType.valueType ()) + ")";
      default:
        return aType.kind ().name ();
    }
  }

  /**
   * @param aType
   *        A sequence type.
   * @return A readable notation of the type for messages, e.g.
   *         <code>xs:string?</code>.
   */
  @NonNull
  public static String getAsString (@NonNull final SeqType aType)
  {
    if (aType.empty ())
      return "empty-sequence()";
    final String sItem = getItemTypeAsString (aType.itemType ());
    final String sSuffix = _getOccurrenceSuffix (aType.occurrence ());
    if (!sSuffix.isEmpty () && aType.itemType ().kind () == ItemType.Kind.FUNCTION && sItem.indexOf (" as ") > 0)
      return "(" + sItem + ")" + sSuffix;
    return sItem + sSuffix;
  }

  /**
   * @return A short description of the kind of one item for type error
   *         messages, e.g. "a value of type xs:string" or "an element node".
   */
  @NonNull
  private static String _describeItem (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final XdmAtomic aAtomic)
      return "a value of type " + aAtomic.getType ().getQualifiedName ();
    if (aItem instanceof final IXdmNode aNode)
      return "a " + aNode.getKind ().name ().toLowerCase (Locale.ROOT) + " node";
    if (aItem instanceof XdmMap)
      return "a map";
    if (aItem instanceof XdmArray)
      return "an array";
    return "a function item";
  }

  /**
   * @return A short description of a supplied value for type error messages:
   *         "an empty sequence", the description of a single item, or the
   *         size and the first item of a longer sequence.
   */
  @NonNull
  private static String _describe (@NonNull final XdmSequence aSeq)
  {
    if (aSeq.isEmpty ())
      return "an empty sequence";
    if (aSeq.size () > 1)
      return "a sequence of " + aSeq.size () + " items starting with " + _describeItem (aSeq.get (0));
    return _describeItem (aSeq.get (0));
  }

  /**
   * @return <code>true</code> if the item is a function item in the XDM sense:
   *         a function, a map or an array.
   */
  static boolean isFunctionItem (@NonNull final IXdmItem aItem)
  {
    return aItem instanceof IXdmFunction || aItem instanceof XdmMap || aItem instanceof XdmArray;
  }

  /**
   * Match an item against a function test (XPath 3.1 section 2.5.5.8).
   * <code>function(*)</code> accepts every function item, maps and arrays
   * included. For a typed test the arity must be equal, each parameter type
   * of the test must be a subtype of the item's parameter type
   * (contravariance) and the item's return type a subtype of the test's.
   * A map counts as <code>function(xs:anyAtomicType) as item()*</code>, but
   * its actual values (and the empty sequence, the result for an absent key)
   * are checked against the return type; an array counts as
   * <code>function(xs:integer) as item()*</code>, with its actual members
   * checked against the return type.
   *
   * @return <code>true</code> if the item matches; <code>false</code> for an
   *         item that is no function item at all.
   */
  private static boolean _matchesFunctionTest (@NonNull final ItemType aType, @NonNull final IXdmItem aItem)
  {
    if (aType.wildcard () || aType.returnType () == null)
      return isFunctionItem (aItem);
    final List <SeqType> aTestParams = aType.paramTypes ();
    final int nTestArity = aTestParams == null ? 0 : aTestParams.size ();
    if (aItem instanceof final XdmMap aMap)
    {
      // map(*) is function(xs:anyAtomicType) as item()*: contravariant
      // parameter, every value and the empty sequence must match the result
      if (nTestArity != 1 || !isSubtype (aTestParams.get (0), ANY_ATOMIC_ONE))
        return false;
      if (!matches (XdmSequence.EMPTY, aType.returnType ()))
        return false;
      for (final XdmMap.Entry aEntry : aMap.entries ())
        if (!matches (aEntry.value (), aType.returnType ()))
          return false;
      return true;
    }
    if (aItem instanceof final XdmArray aArray)
    {
      // array(*) is function(xs:integer) as item()*
      if (nTestArity != 1 || !isSubtype (aTestParams.get (0), INTEGER_ONE))
        return false;
      for (int i = 0; i < aArray.size (); ++i)
        if (!matches (aArray.get (i), aType.returnType ()))
          return false;
      return true;
    }
    if (aItem instanceof final IXdmFunction aFunc)
    {
      if (aFunc.getArity () != nTestArity)
        return false;
      final List <SeqType> aActualParams = aFunc.getParamTypes ();
      for (int i = 0; i < nTestArity; ++i)
        if (!isSubtype (aTestParams.get (i), aActualParams.get (i)))
          return false;
      return isSubtype (aFunc.getReturnType (), aType.returnType ());
    }
    return false;
  }

  /**
   * @param aSeq
   *        The value.
   * @param aType
   *        The sequence type.
   * @return <code>true</code> if the value matches the type (the
   *         <code>instance of</code> relation, no conversion).
   */
  public static boolean matches (@NonNull final XdmSequence aSeq, @NonNull final SeqType aType)
  {
    if (aType.empty ())
      return aSeq.isEmpty ();
    if (!_matchesOccurrence (aSeq.size (), aType.occurrence ()))
      return false;
    final ItemType aItemType = aType.itemType ();
    if (aItemType.kind () == ItemType.Kind.ITEM)
      return true;
    for (final IXdmItem aItem : aSeq)
      if (!matchesItem (aItem, aItemType))
        return false;
    return true;
  }

  /**
   * @param eActual
   *        The type of a key in the map.
   * @param eKeyType
   *        The key type of the <code>map(K, V)</code> test;
   *        <code>null</code> or <code>xs:anyAtomicType</code> accept every
   *        key.
   * @return <code>true</code> if the key type is derived from (or equal to)
   *         the key type of the test.
   */
  private static boolean _matchesMapKeyType (@NonNull final EAtomicType eActual, @Nullable final EAtomicType eKeyType)
  {
    return eKeyType == null || eKeyType == EAtomicType.ANY_ATOMIC_TYPE || eActual.isDerivedFrom (eKeyType);
  }

  /**
   * Test the value type of a map(K, V) or array(T) type on the summary of the
   * values: every value is one atomic item of the uniform type.
   *
   * @return The result, or <code>null</code> if the values must be looked at.
   */
  @Nullable
  private static Boolean _matchesUniformValues (@Nullable final EAtomicType eUniform, @NonNull final SeqType aValueType)
  {
    if (eUniform == null || aValueType.empty ())
      return null;
    final ItemType aItemType = aValueType.itemType ();
    if (aItemType.kind () != ItemType.Kind.ATOMIC || aItemType.schemaType () != null)
      return null;
    // Every value is one atomic item, which every occurrence indicator but
    // the empty sequence allows
    final EAtomicType eType = aItemType.atomicType ();
    return Boolean.valueOf (eType != EAtomicType.ERROR &&
                            (eType == EAtomicType.ANY_ATOMIC_TYPE || eUniform.isDerivedFrom (eType)));
  }

  /**
   * Test a map(K, V) type on the map's summary of its entries.
   *
   * @return The result, or <code>null</code> if the entries must be looked at.
   */
  @Nullable
  private static Boolean _matchesUniformMap (@NonNull final XdmMap aMap,
                                             @Nullable final EAtomicType eKeyType,
                                             @NonNull final SeqType aValueType)
  {
    final EAtomicType eUniformKey = aMap.getUniformKeyType ();
    if (eUniformKey == null)
      return null;
    final Boolean aValues = _matchesUniformValues (aMap.getUniformValueType (), aValueType);
    if (aValues == null)
      return null;
    return Boolean.valueOf (_matchesMapKeyType (eUniformKey, eKeyType) && aValues.booleanValue ());
  }

  /**
   * @param aItem
   *        The item.
   * @param aType
   *        The item type.
   * @return <code>true</code> if the item matches the item type.
   */
  public static boolean matchesItem (@NonNull final IXdmItem aItem, @NonNull final ItemType aType)
  {
    switch (aType.kind ())
    {
      case ITEM:
        return true;
      case ATOMIC:
      {
        if (!(aItem instanceof final XdmAtomic aAtomic))
          return false;
        if (aType.schemaType () != null)
          return SchemaTypeOps.isInstance (aAtomic, aType.schemaType ());
        final EAtomicType eType = aType.atomicType ();
        if (eType == EAtomicType.ERROR)
          return false;
        return eType == EAtomicType.ANY_ATOMIC_TYPE || aAtomic.getType ().isDerivedFrom (eType);
      }
      case NODE:
        return aItem instanceof final IXdmNode aNode && NodeTestMatcher.matchesKindTest (aType.nodeTest (), aNode);
      case FUNCTION:
        return _matchesFunctionTest (aType, aItem);
      case MAP:
      {
        if (!(aItem instanceof final XdmMap aMap))
          return false;
        if (aType.wildcard () || aType.valueType () == null)
          return true;
        final EAtomicType eKeyType = aType.mapKeyType ();
        // A map(K, V) parameter of xsl:iterate is checked on every step
        final Boolean aFast = _matchesUniformMap (aMap, eKeyType, aType.valueType ());
        if (aFast != null)
          return aFast.booleanValue ();
        for (final XdmMap.Entry aEntry : aMap.entries ())
        {
          if (!_matchesMapKeyType (aEntry.key ().getType (), eKeyType))
            return false;
          if (!matches (aEntry.value (), aType.valueType ()))
            return false;
        }
        return true;
      }
      case ARRAY:
      {
        if (!(aItem instanceof final XdmArray aArray))
          return false;
        if (aType.wildcard () || aType.valueType () == null)
          return true;
        // An array(T) parameter of xsl:iterate is checked on every step
        final Boolean aFast = _matchesUniformValues (aArray.getUniformMemberType (), aType.valueType ());
        if (aFast != null)
          return aFast.booleanValue ();
        for (int i = 0; i < aArray.size (); ++i)
          if (!matches (aArray.get (i), aType.valueType ()))
            return false;
        return true;
      }
      default:
        return false;
    }
  }

  /**
   * The subtype judgement on kind tests (XPath 3.1 section 2.5.6.2):
   * <code>node()</code> is the supertype of every test, otherwise the kinds
   * must be equal and
   * <ul>
   * <li><code>document-node(E)</code>: a test without inner test is the
   * supertype of every document test, otherwise the inner tests are compared
   * recursively;</li>
   * <li><code>processing-instruction(N)</code>: no name in B, or the same
   * name;</li>
   * <li><code>element</code>/<code>attribute</code>: the name of B is a
   * wildcard or equal to the name of A, and if B has a type annotation, the
   * annotation of A (<code>xs:anyType</code> or
   * <code>xs:anyAtomicType</code> if absent) must be derived from it; for
   * elements a nillable A (or one without annotation) requires a nillable
   * B;</li>
   * <li><code>schema-element</code>/<code>schema-attribute</code>: the same
   * name only, substitution groups are not considered;</li>
   * <li>a name test: B's wildcard (<code>*</code>, <code>*:local</code>,
   * <code>prefix:*</code>) must cover A's name or narrower wildcard;</li>
   * <li>any other kind (<code>text()</code>, <code>comment()</code>,
   * <code>namespace-node()</code>): equal kind suffices.</li>
   * </ul>
   *
   * @param aA
   *        Candidate subtype.
   * @param aB
   *        Candidate supertype.
   * @return <code>true</code> if every node matching aA also matches aB.
   */
  private static boolean _isNodeTestSubtype (@NonNull final NodeTest aA, @NonNull final NodeTest aB)
  {
    if (aB.kind () == ENodeTestKind.ANY_NODE)
      return true;
    if (aA.kind () != aB.kind ())
      return false;
    switch (aB.kind ())
    {
      case DOCUMENT:
        if (aB.inner () == null)
          return true;
        return aA.inner () != null && _isNodeTestSubtype (aA.inner (), aB.inner ());
      case PROCESSING_INSTRUCTION:
        return aB.name () == null || (aA.name () != null && aA.name ().localName ().equals (aB.name ().localName ()));
      case ELEMENT:
      case ATTRIBUTE:
      {
        final boolean bNameOK = aB.anyName () ||
                                aB.name () == null ||
                                (!aA.anyName () && aA.name () != null && aA.name ().equals (aB.name ()));
        if (!bNameOK)
          return false;
        if (aB.typeName () == null)
          return true;
        // element(N) is element(N, xs:anyType?); attribute(N) is
        // attribute(N, xs:anyAtomicType)
        final boolean bElement = aB.kind () == ENodeTestKind.ELEMENT;
        final Object aTypeA = aA.typeName () != null ? NodeTestMatcher.resolveSchemaType (aA.typeName ())
                                                     : bElement ? "anyType" : EAtomicType.ANY_ATOMIC_TYPE;
        final Object aTypeB = NodeTestMatcher.resolveSchemaType (aB.typeName ());
        if (!NodeTestMatcher.isSchemaTypeDerivedFrom (aTypeA, aTypeB))
          return false;
        if (bElement)
        {
          final boolean bNillableA = aA.typeName () == null || aA.nillable ();
          return !bNillableA || aB.nillable ();
        }
        return true;
      }
      case SCHEMA_ELEMENT:
      case SCHEMA_ATTRIBUTE:
        return aA.name () != null && aA.name ().equals (aB.name ());
      case NAME:
      {
        final XdmQName aNameB = aB.name ();
        if (aB.anyName ())
          return true;
        if (aA.anyName () || aA.name () == null || aNameB == null)
          return false;
        if (aB.anyNamespace ())
          return aA.name ().localName ().equals (aNameB.localName ()) && !aA.anyLocalName ();
        if (aB.anyLocalName ())
          return aA.name ().namespaceUri ().equals (aNameB.namespaceUri ()) && !aA.anyNamespace ();
        return !aA.anyNamespace () && !aA.anyLocalName () && aA.name ().equals (aNameB);
      }
      default:
        return true;
    }
  }

  /**
   * The function type of a map type (XPath 3.1 section 2.5.6.2):
   * <code>map(K, V)</code> is <code>function(xs:anyAtomicType) as V'</code>,
   * where <code>V'</code> is <code>V</code> with an occurrence that allows
   * the empty sequence (<code>1</code> becomes <code>?</code>, <code>+</code>
   * becomes <code>*</code>), because an absent key gives the empty sequence.
   * <code>map(*)</code> is <code>function(xs:anyAtomicType) as
   * item()*</code>.
   */
  @NonNull
  private static ItemType _getMapAsFunctionType (@NonNull final ItemType aMap)
  {
    SeqType aRet = SeqType.ANY;
    if (!aMap.wildcard () && aMap.valueType () != null)
    {
      final SeqType aValue = aMap.valueType ();
      if (aValue.empty ())
        aRet = aValue;
      else
      {
        EOccurrence eOcc = aValue.occurrence ();
        if (eOcc == EOccurrence.ONE)
          eOcc = EOccurrence.ZERO_OR_ONE;
        else
          if (eOcc == EOccurrence.ONE_OR_MORE)
            eOcc = EOccurrence.ZERO_OR_MORE;
        aRet = new SeqType (false, aValue.itemType (), eOcc, false);
      }
    }
    return new ItemType (ItemType.Kind.FUNCTION, null, null, false, List.of (ANY_ATOMIC_ONE), aRet, null, null);
  }

  /**
   * The function type of an array type (XPath 3.1 section 2.5.6.2):
   * <code>array(T)</code> is <code>function(xs:integer) as T</code>,
   * <code>array(*)</code> is <code>function(xs:integer) as item()*</code>.
   */
  @NonNull
  private static ItemType _getArrayAsFunctionType (@NonNull final ItemType aArray)
  {
    final SeqType aRet = !aArray.wildcard () && aArray.valueType () != null ? aArray.valueType () : SeqType.ANY;
    return new ItemType (ItemType.Kind.FUNCTION, null, null, false, List.of (INTEGER_ONE), aRet, null, null);
  }

  /**
   * The subtype judgement on item types (XPath 3.1 section 2.5.6.2).
   *
   * @param aA
   *        Candidate subtype.
   * @param aB
   *        Candidate supertype.
   * @return <code>true</code> if every item matching aA also matches aB.
   */
  public static boolean isItemSubtype (@NonNull final ItemType aA, @NonNull final ItemType aB)
  {
    switch (aB.kind ())
    {
      case ITEM:
        return true;
      case ATOMIC:
      {
        if (aA.kind () != ItemType.Kind.ATOMIC)
          return false;
        if (aB.schemaType () != null)
          return aA.schemaType () != null && aA.schemaType ().isDerivedFrom (aB.schemaType ());
        final EAtomicType eA = aA.atomicType ();
        final EAtomicType eB = aB.atomicType ();
        if (eA == EAtomicType.ERROR)
          return true;
        if (eB == EAtomicType.ERROR)
          return false;
        if (eA == EAtomicType.NUMERIC)
          return eB == EAtomicType.NUMERIC || eB == EAtomicType.ANY_ATOMIC_TYPE;
        return eA.isDerivedFrom (eB);
      }
      case NODE:
        return aA.kind () == ItemType.Kind.NODE && _isNodeTestSubtype (aA.nodeTest (), aB.nodeTest ());
      case FUNCTION:
      {
        ItemType aFuncA;
        switch (aA.kind ())
        {
          case FUNCTION:
            aFuncA = aA;
            break;
          case MAP:
            aFuncA = _getMapAsFunctionType (aA);
            break;
          case ARRAY:
            aFuncA = _getArrayAsFunctionType (aA);
            break;
          default:
            return false;
        }
        if (aB.wildcard () || aB.returnType () == null)
          return true;
        if (aFuncA.wildcard () || aFuncA.returnType () == null)
          return false;
        final List <SeqType> aParamsA = aFuncA.paramTypes () == null ? List.of () : aFuncA.paramTypes ();
        final List <SeqType> aParamsB = aB.paramTypes () == null ? List.of () : aB.paramTypes ();
        if (aParamsA.size () != aParamsB.size ())
          return false;
        for (int i = 0; i < aParamsA.size (); ++i)
          if (!isSubtype (aParamsB.get (i), aParamsA.get (i)))
            return false;
        return isSubtype (aFuncA.returnType (), aB.returnType ());
      }
      case MAP:
      {
        if (aA.kind () != ItemType.Kind.MAP)
          return false;
        if (aB.wildcard () || aB.valueType () == null)
          return true;
        if (aA.wildcard () || aA.valueType () == null)
          return aB.mapKeyType () == EAtomicType.ANY_ATOMIC_TYPE && isSubtype (SeqType.ANY, aB.valueType ());
        return aA.mapKeyType ().isDerivedFrom (aB.mapKeyType ()) && isSubtype (aA.valueType (), aB.valueType ());
      }
      case ARRAY:
      {
        if (aA.kind () != ItemType.Kind.ARRAY)
          return false;
        if (aB.wildcard () || aB.valueType () == null)
          return true;
        if (aA.wildcard () || aA.valueType () == null)
          return isSubtype (SeqType.ANY, aB.valueType ());
        return isSubtype (aA.valueType (), aB.valueType ());
      }
      default:
        return false;
    }
  }

  /**
   * The subtype judgement on sequence types (XPath 3.1 section 2.5.6.1).
   *
   * @param aA
   *        Candidate subtype; <code>null</code> means <code>item()*</code>.
   * @param aB
   *        Candidate supertype; <code>null</code> means <code>item()*</code>.
   * @return <code>true</code> if every value matching aA also matches aB.
   */
  public static boolean isSubtype (@Nullable final SeqType aA, @Nullable final SeqType aB)
  {
    final SeqType aRealA = aA != null ? aA : SeqType.ANY;
    final SeqType aRealB = aB != null ? aB : SeqType.ANY;
    if (aRealA.empty ())
      return aRealB.empty () || aRealB.occurrence ().allowsEmpty ();
    if (aRealA.itemType ().kind () == ItemType.Kind.ATOMIC && aRealA.itemType ().atomicType () == EAtomicType.ERROR)
    {
      // xs:error has no instances: xs:error? and xs:error* match only the
      // empty sequence, xs:error and xs:error+ match nothing at all
      if (!aRealA.occurrence ().allowsEmpty ())
        return true;
      return aRealB.empty () || aRealB.occurrence ().allowsEmpty ();
    }
    if (aRealB.empty ())
      return false;
    final EOccurrence eA = aRealA.occurrence ();
    final EOccurrence eB = aRealB.occurrence ();
    if (_getMinOccurs (eA) < _getMinOccurs (eB))
      return false;
    if (!_isUnboundedOccurs (eB) && _isUnboundedOccurs (eA))
      return false;
    return isItemSubtype (aRealA.itemType (), aRealB.itemType ());
  }

  /**
   * @return The function type of a function item, a map or an array.
   */
  @NonNull
  private static ItemType _getFunctionItemType (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final IXdmFunction aFunc)
      return new ItemType (ItemType.Kind.FUNCTION,
                           null,
                           null,
                           false,
                           aFunc.getParamTypes (),
                           aFunc.getReturnType (),
                           null,
                           null);
    if (aItem instanceof XdmMap)
      return _getMapAsFunctionType (new ItemType (ItemType.Kind.MAP, null, null, true, null, null, null, null));
    return _getArrayAsFunctionType (new ItemType (ItemType.Kind.ARRAY, null, null, true, null, null, null, null));
  }

  /**
   * The function conversion rules for an expected function type (XPath 3.1
   * section 3.1.5.2): the occurrence is checked first, then every item must
   * be a function item (maps and arrays included). An item that already
   * matches the expected type, or any item for <code>function(*)</code>, is
   * kept. Otherwise function coercion (section 3.1.5.3) applies: the arity
   * must be the expected one, and the item is wrapped in a
   * {@link CoercedFunctionItem} that checks arguments and result at call
   * time, unless its own function type is a subtype of the expected one.
   *
   * @return The supplied sequence itself if no item was wrapped, a new
   *         sequence otherwise.
   * @throws XPathException
   *         with <code>sErrorCode</code> for a wrong number of items, an item
   *         that is no function item, or a function of another arity
   */
  @NonNull
  private static XdmSequence _convertFunctions (@NonNull final XdmSequence aSeq,
                                                @NonNull final SeqType aType,
                                                @NonNull final String sErrorCode)
  {
    if (!_matchesOccurrence (aSeq.size (), aType.occurrence ()))
      throw XPathException.of (sErrorCode,
                               "Required type is " + getAsString (aType) + ", but supplied was " + _describe (aSeq));
    final ItemType aExpected = aType.itemType ();
    final boolean bTyped = !aExpected.wildcard () && aExpected.returnType () != null;
    final int nExpectedArity = aExpected.paramTypes () == null ? 0 : aExpected.paramTypes ().size ();
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    boolean bChanged = false;
    for (final IXdmItem aItem : aSeq)
    {
      if (!isFunctionItem (aItem))
        throw XPathException.of (sErrorCode,
                                 "Required type is " + getAsString (aType) + ", but supplied was " + _describeItem (aItem));
      if (!bTyped || matchesItem (aItem, aExpected))
      {
        aBuilder.add (aItem);
        continue;
      }
      // Function coercion (section 3.1.5.3): same arity required, the type
      // checks of arguments and result are deferred to the call
      final int nArity = XPathEvaluator.getFunctionArity (aItem);
      if (nArity != nExpectedArity)
        throw XPathException.of (sErrorCode,
                                 "Required type is " +
                                             getAsString (aType) +
                                             ", but supplied was a function of arity " +
                                             nArity);
      if (isItemSubtype (_getFunctionItemType (aItem), aExpected))
        aBuilder.add (aItem);
      else
      {
        aBuilder.add (new CoercedFunctionItem (aItem, aExpected));
        bChanged = true;
      }
    }
    return bChanged ? aBuilder.build () : aSeq;
  }

  /**
   * Convert one atomic value towards an expected atomic type (XPath 3.1
   * section 3.1.5.2):
   * <ul>
   * <li><code>xs:untypedAtomic</code> is cast to the expected type
   * (<code>xs:double</code> for the union <code>xs:numeric</code>); it stays
   * as it is for <code>xs:anyAtomicType</code>, <code>xs:untypedAtomic</code>
   * and <code>xs:error</code>;</li>
   * <li>a value of the expected type or a subtype stays as it is;</li>
   * <li>numeric promotion: <code>xs:float</code> and <code>xs:decimal</code>
   * (with its subtypes) to <code>xs:double</code>, <code>xs:decimal</code>
   * to <code>xs:float</code>;</li>
   * <li>URI promotion: <code>xs:anyURI</code> to <code>xs:string</code>.</li>
   * </ul>
   * Any other value is returned unchanged; the caller's final match check
   * then reports the mismatch.
   *
   * @return The converted value, or the same instance if nothing changed.
   * @throws XPathException
   *         XPTY0117 for an untyped value where a type derived from
   *         <code>xs:QName</code> or <code>xs:NOTATION</code> is expected; a
   *         cast error such as FORG0001 if the untyped value is not valid for
   *         the expected type
   */
  @NonNull
  private static XdmAtomic _convertAtomic (@NonNull final XdmAtomic aValue,
                                           @NonNull final EAtomicType eTarget,
                                           @NonNull final DynamicContext aCtx)
  {
    final EAtomicType eType = aValue.getType ();
    if (eType == EAtomicType.UNTYPED_ATOMIC)
    {
      if (eTarget == EAtomicType.ANY_ATOMIC_TYPE || eTarget == EAtomicType.UNTYPED_ATOMIC)
        return aValue;
      // xs:numeric is a union: an untyped value becomes xs:double
      final EAtomicType eCastTarget = eTarget == EAtomicType.NUMERIC ? EAtomicType.DOUBLE : eTarget;
      if (eCastTarget == EAtomicType.ERROR)
        return aValue;
      if (eCastTarget.isDerivedFrom (EAtomicType.QNAME) || eCastTarget.isDerivedFrom (EAtomicType.NOTATION))
        throw XPathException.of ("XPTY0117",
                                 "An xs:untypedAtomic value cannot be converted to the namespace-sensitive type " +
                                             eCastTarget.getQualifiedName ());
      return AtomicCaster.castFromString (aValue.getString (), eCastTarget, aCtx.getStaticContext ());
    }
    // The exact type first, before the walk up the derivation chain
    if (eType == eTarget || eTarget == EAtomicType.ANY_ATOMIC_TYPE || eType.isDerivedFrom (eTarget))
      return aValue;
    // Numeric promotion
    if (eTarget == EAtomicType.DOUBLE && (eType == EAtomicType.FLOAT || eType.isDerivedFrom (EAtomicType.DECIMAL)))
      return AtomicCaster.cast (aValue, EAtomicType.DOUBLE, aCtx.getStaticContext ());
    if (eTarget == EAtomicType.FLOAT && eType.isDerivedFrom (EAtomicType.DECIMAL))
      return AtomicCaster.cast (aValue, EAtomicType.FLOAT, aCtx.getStaticContext ());
    // URI promotion
    if (eTarget == EAtomicType.STRING && eType == EAtomicType.ANY_URI)
      return XdmAtomic.string (aValue.getString ());
    return aValue;
  }

  /**
   * The conversion of {@link #_convertAtomics(XdmSequence, SeqType, DynamicContext, String)} for a
   * sequence of exactly one item and a built-in target type: an atomic value is converted with
   * {@link #_convertAtomic(XdmAtomic, EAtomicType, DynamicContext)} without a sequence builder;
   * any other item is atomised and each of its values is converted. The match check against the
   * whole sequence type is left to the caller.
   *
   * @param aSeq
   *        A sequence of exactly one item.
   * @param eTarget
   *        The built-in target type.
   * @param aCtx
   *        Context.
   * @return The supplied sequence itself if an atomic value stayed unchanged, a new sequence
   *         otherwise.
   * @throws XPathException
   *         FOTY0013 if a map or function item must be atomised; the errors of the conversion of
   *         the value
   */
  @NonNull
  private static XdmSequence _convertSingleAtomic (@NonNull final XdmSequence aSeq,
                                                   @NonNull final EAtomicType eTarget,
                                                   @NonNull final DynamicContext aCtx)
  {
    final IXdmItem aItem = aSeq.get (0);
    if (aItem instanceof final XdmAtomic aAtomic)
    {
      final XdmAtomic aConv = _convertAtomic (aAtomic, eTarget, aCtx);
      return aConv == aAtomic ? aSeq : XdmSequence.of (aConv);
    }
    if (aItem instanceof final IXdmNode aNode)
    {
      // A node without schema type or annotation: one value, no sequence
      final XdmAtomic aUntyped = Atomizer.getUntypedValue (aNode);
      if (aUntyped != null)
        return XdmSequence.of (_convertAtomic (aUntyped, eTarget, aCtx));
    }
    // Atomisation
    final XdmSequence aAtoms = Atomizer.atomize (aSeq);
    if (aAtoms.size () == 1)
      return XdmSequence.of (_convertAtomic ((XdmAtomic) aAtoms.get (0), eTarget, aCtx));
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final IXdmItem aAtom : aAtoms)
      aBuilder.add (_convertAtomic ((XdmAtomic) aAtom, eTarget, aCtx));
    return aBuilder.build ();
  }

  /**
   * Does a sequence already match a built-in atomic sequence type without any conversion? Its
   * size must satisfy the occurrence indicator and every item must be an atomic value whose type
   * is the target type or derived from it. Such values are exactly the ones
   * {@link #_convertAtomic(XdmAtomic, EAtomicType, DynamicContext)} returns unchanged (an
   * xs:untypedAtomic value only for the targets xs:untypedAtomic and xs:anyAtomicType), and the
   * final match check of the conversion would accept them.
   *
   * @return <code>true</code> if the conversion is the identity; <code>false</code> at the first
   *         item that is no atomic value or not of the target type, and for xs:error.
   */
  private static boolean _isMatchingAtomics (@NonNull final XdmSequence aSeq, @NonNull final SeqType aType)
  {
    if (!_matchesOccurrence (aSeq.size (), aType.occurrence ()))
      return false;
    final EAtomicType eTarget = aType.itemType ().atomicType ();
    if (eTarget == EAtomicType.ERROR)
      return false;
    final int nSize = aSeq.size ();
    for (int i = 0; i < nSize; ++i)
    {
      if (!(aSeq.get (i) instanceof final XdmAtomic aAtomic))
        return false;
      final EAtomicType eType = aAtomic.getType ();
      if (eType != eTarget && eTarget != EAtomicType.ANY_ATOMIC_TYPE && !eType.isDerivedFrom (eTarget))
        return false;
    }
    return true;
  }

  /**
   * The function conversion rules for an expected atomic type (XPath 3.1
   * section 3.1.5.2): non-atomic items are atomised, then each atomic value
   * is converted with
   * {@link #_convertAtomic(XdmAtomic, EAtomicType, DynamicContext)}. For a
   * user-defined (schema) target type only <code>xs:untypedAtomic</code>
   * values are converted, by a cast to that type which may give several
   * values for a list type; other values are kept. The result is finally
   * matched against the whole sequence type, occurrence included.
   *
   * @return The supplied sequence itself if nothing changed, a new sequence
   *         otherwise.
   * @throws XPathException
   *         with <code>sErrorCode</code> if the converted value does not match
   *         the type; FOTY0013 if a map or function item must be atomised;
   *         the errors of the conversion of single values
   */
  @NonNull
  private static XdmSequence _convertAtomics (@NonNull final XdmSequence aSeq,
                                              @NonNull final SeqType aType,
                                              @NonNull final DynamicContext aCtx,
                                              @NonNull final String sErrorCode)
  {
    final EAtomicType eTarget = aType.itemType ().atomicType ();
    final IXdmTypeDefinition aSchemaTarget = aType.itemType ().schemaType ();
    if (aSchemaTarget == null && aSeq.size () == 1)
    {
      // One item, the most frequent case: no sequence builder
      final XdmSequence ret = _convertSingleAtomic (aSeq, eTarget, aCtx);
      // One value of exactly the target type matches every occurrence
      // indicator but the empty sequence, which convert has handled
      if (ret.size () == 1 &&
          eTarget != EAtomicType.ERROR &&
          ret.get (0) instanceof final XdmAtomic aResult &&
          aResult.getType () == eTarget)
        return ret;
      if (!matches (ret, aType))
        throw XPathException.of (sErrorCode,
                                 "Required type is " + getAsString (aType) + ", but supplied was " + _describe (ret));
      return ret;
    }
    if (aSchemaTarget == null && _isMatchingAtomics (aSeq, aType))
    {
      // Every value is already of the target type or a subtype: the
      // conversion would keep each one as it is
      return aSeq;
    }
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    boolean bChanged = false;
    for (final IXdmItem aItem : aSeq)
    {
      if (aItem instanceof final XdmAtomic aAtomic)
      {
        if (aSchemaTarget != null)
        {
          // xs:untypedAtomic is cast to the user-defined type
          if (aAtomic.getType () == EAtomicType.UNTYPED_ATOMIC)
          {
            bChanged = true;
            aBuilder.addAll (SchemaTypeOps.cast (aAtomic, aSchemaTarget, aCtx.getStaticContext ()));
          }
          else
            aBuilder.add (aAtomic);
          continue;
        }
        final XdmAtomic aConv = _convertAtomic (aAtomic, eTarget, aCtx);
        bChanged |= aConv != aAtomic;
        aBuilder.add (aConv);
      }
      else
      {
        // Atomisation
        bChanged = true;
        for (final IXdmItem aAtom : Atomizer.atomize (XdmSequence.of (aItem)))
        {
          final XdmAtomic aAtomic = (XdmAtomic) aAtom;
          if (aSchemaTarget != null)
          {
            if (aAtomic.getType () == EAtomicType.UNTYPED_ATOMIC)
              aBuilder.addAll (SchemaTypeOps.cast (aAtomic, aSchemaTarget, aCtx.getStaticContext ()));
            else
              aBuilder.add (aAtomic);
          }
          else
            aBuilder.add (_convertAtomic (aAtomic, eTarget, aCtx));
        }
      }
    }
    final XdmSequence ret = bChanged ? aBuilder.build () : aSeq;
    if (!matches (ret, aType))
      throw XPathException.of (sErrorCode,
                               "Required type is " + getAsString (aType) + ", but supplied was " + _describe (ret));
    return ret;
  }

  /**
   * The function conversion rules without the XPath 1.0 compatibility
   * conversions: the atomic conversions for an atomic item type, function
   * coercion for a function item type, and a plain match check for every
   * other item type (nodes, maps, arrays, <code>item()</code>).
   *
   * @throws XPathException
   *         with <code>sErrorCode</code> if the value does not match after
   *         conversion
   */
  @NonNull
  private static XdmSequence _convertNoCompat (@NonNull final XdmSequence aSeq,
                                               @NonNull final SeqType aType,
                                               @NonNull final DynamicContext aCtx,
                                               @NonNull final String sErrorCode)
  {
    switch (aType.itemType ().kind ())
    {
      case ATOMIC:
        return _convertAtomics (aSeq, aType, aCtx, sErrorCode);
      case FUNCTION:
        return _convertFunctions (aSeq, aType, sErrorCode);
      default:
        if (!matches (aSeq, aType))
          throw XPathException.of (sErrorCode,
                                   "Required type is " + getAsString (aType) + ", but supplied was " + _describe (aSeq));
        return aSeq;
    }
  }

  /**
   * The XPath 1.0 compatibility mode conversions (XPath 3.1 section 3.1.5.2),
   * applied to a value that is not of the expected type: the first item for a
   * single item type, then fn:string for xs:string and fn:number for xs:double
   * (and xs:numeric, whose members include xs:double).
   */
  @NonNull
  private static XdmSequence _applyXPath10Conversions (@NonNull final XdmSequence aSeq, @NonNull final SeqType aType)
  {
    XdmSequence ret = aSeq;
    if (!aType.occurrence ().allowsMany () && ret.size () > 1)
      ret = XdmSequence.of (ret.getFirst ());
    if (aType.itemType ().kind () == ItemType.Kind.ATOMIC)
    {
      final EAtomicType eTarget = aType.itemType ().atomicType ();
      if (eTarget == EAtomicType.STRING)
        ret = XdmSequence.of (XdmAtomic.string (ret.isEmpty () ? "" : Atomizer.getStringValue (ret.getFirst ())));
      else
        if (eTarget == EAtomicType.DOUBLE || eTarget == EAtomicType.NUMERIC)
        {
          double dValue = Double.NaN;
          final XdmSequence aAtoms = Atomizer.atomize (ret);
          if (aAtoms.size () == 1)
            try
            {
              dValue = AtomicCaster.cast ((XdmAtomic) aAtoms.getFirst (), EAtomicType.DOUBLE, null).getAsDouble ();
            }
            catch (final XPathException ex)
            {
              // Not a number: NaN
            }
          ret = XdmSequence.of (XdmAtomic.dbl (dValue));
        }
    }
    return ret;
  }

  /**
   * Apply the function conversion rules: atomisation where an atomic type is
   * expected, xs:untypedAtomic cast to the expected type, numeric and URI
   * promotion, function coercion for function types, then check the result.
   *
   * @param aSeq
   *        The supplied value.
   * @param aType
   *        The required type.
   * @param aCtx
   *        Context.
   * @param sErrorCode
   *        The error code for a mismatch (XPTY0004 for arguments).
   * @return The converted value.
   */
  @NonNull
  public static XdmSequence convert (@NonNull final XdmSequence aSeq,
                                     @NonNull final SeqType aType,
                                     @NonNull final DynamicContext aCtx,
                                     @NonNull final String sErrorCode)
  {
    if (_isItemStar (aType))
      return aSeq;
    if (aType.empty ())
    {
      if (!aSeq.isEmpty ())
        throw XPathException.of (sErrorCode, "Required type is empty-sequence(), but supplied was " + _describe (aSeq));
      return aSeq;
    }
    // XPath 3.1 section 3.1.5.2: in XPath 1.0 compatibility mode the extra
    // conversions come first, for every value not of the expected type
    if (aCtx.getStaticContext ().isXPath10Compatibility () && !matches (aSeq, aType))
      return _convertNoCompat (_applyXPath10Conversions (aSeq, aType), aType, aCtx, sErrorCode);
    return _convertNoCompat (aSeq, aType, aCtx, sErrorCode);
  }
}
