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

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.EBinaryOp;
import com.helger.xpath.ast.EComparisonOp;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.collation.ICollation;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.fn.FunctionCallSite;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.xpath.ops.AtomicArithmetic;
import com.helger.xpath.ops.AtomicCaster;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.ops.SchemaTypeOps;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;

/**
 * The XPath operators on already evaluated operands, with exactly the semantics of
 * {@link XPathEvaluator}, which uses these methods itself: comparisons, arithmetic, cast and
 * castable, and the static call of a library function with the function conversion rules. This is
 * the API for hosts that evaluate the structure of an expression themselves (generated code) and
 * need the operators of the interpreter. Everything that depends on the static context (default
 * collation, XPath 1.0 compatibility mode, schema types, the function library) comes from the
 * static context of the dynamic context passed in; run-wide caches (the index for general
 * equality against long sequences) come from its run cache.
 *
 * @author Philip Helger
 */
@Immutable
public final class XPathOperators
{
  private static final XdmSequence TRUE_SEQ = XdmSequence.of (XdmAtomic.TRUE);
  private static final XdmSequence FALSE_SEQ = XdmSequence.of (XdmAtomic.FALSE);

  /** Marks a sequence for which no equality index can be built. */
  private static final Object NO_INDEX = new Object ();
  private static final String RUN_CACHE_EQ_INDEX = "xpath:eq-index";

  @PresentForCodeCoverage
  private static final XPathOperators INSTANCE = new XPathOperators ();

  private XPathOperators ()
  {}

  @NonNull
  private static XdmSequence _bool (final boolean b)
  {
    return b ? TRUE_SEQ : FALSE_SEQ;
  }

  /** Atomise, without calling the atomiser for sequences that are atomic already. */
  @NonNull
  static XdmSequence atomize (@NonNull final XdmSequence aSeq)
  {
    for (final IXdmItem aItem : aSeq)
      if (!(aItem instanceof XdmAtomic))
        return Atomizer.atomize (aSeq);
    return aSeq;
  }

  /**
   * Atomise an operand that must be empty or one atomic value.
   *
   * @return <code>null</code> for the empty sequence.
   */
  @Nullable
  static XdmAtomic atomizeOptional (@NonNull final XdmSequence aSeq, @NonNull final String sWhat)
  {
    final XdmSequence aAtoms = atomize (aSeq);
    if (aAtoms.isEmpty ())
      return null;
    if (aAtoms.size () > 1)
      throw XPathException.type ("The " + sWhat + " is a sequence of " + aAtoms.size () + " items");
    return (XdmAtomic) aAtoms.get (0);
  }

  /**
   * The operand conversion of arithmetic (XPath 3.1 section 3.5): an <code>xs:untypedAtomic</code>
   * value is cast to <code>xs:double</code>, any other value is returned unchanged.
   *
   * @throws XPathException
   *         FORG0001 if the untyped value is not a valid <code>xs:double</code> lexical form
   */
  @NonNull
  static XdmAtomic untypedToDouble (@NonNull final XdmAtomic aValue, @NonNull final DynamicContext aCtx)
  {
    if (aValue.getType () == EAtomicType.UNTYPED_ATOMIC)
      return AtomicCaster.castFromString (aValue.getString (), EAtomicType.DOUBLE, aCtx.getStaticContext ());
    return aValue;
  }

  /**
   * @return The default collation of the static context, <code>null</code> for the codepoint
   *         collation.
   */
  @Nullable
  static ICollation getDefaultCollation (@NonNull final DynamicContext aCtx)
  {
    final String sUri = aCtx.getStaticContext ().getDefaultCollation ();
    return CollationRegistry.CODEPOINT_URI.equals (sUri) ? null : CollationRegistry.getCollationOrThrow (sUri);
  }

  /**
   * Build (or get from the run cache) a hash index of a long sequence for general equality: kind 0
   * = numbers as doubles (untypedAtomic cast to xs:double), 1 = integers exactly, 2 = strings by
   * codepoints.
   *
   * @return The set of keys or <code>null</code> if no index is possible.
   */
  @Nullable
  @SuppressWarnings ("unchecked")
  private static Set <Object> _getEqualityIndex (@NonNull final XdmSequence aSeq,
                                                 final int nKind,
                                                 @NonNull final DynamicContext aCtx)
  {
    final Map <String, Object> aCache = aCtx.getRunCache ();
    IdentityHashMap <XdmSequence, Object []> aIndexes = (IdentityHashMap <XdmSequence, Object []>) aCache.get (RUN_CACHE_EQ_INDEX);
    if (aIndexes == null)
    {
      aIndexes = new IdentityHashMap <> ();
      aCache.put (RUN_CACHE_EQ_INDEX, aIndexes);
    }
    final Object [] aSlots = aIndexes.computeIfAbsent (aSeq, k -> new Object [3]);
    if (aSlots[nKind] == null)
    {
      final Set <Object> aSet = new HashSet <> ();
      Object aResult = aSet;
      for (final IXdmItem aItem : atomize (aSeq))
      {
        final XdmAtomic aAtom = (XdmAtomic) aItem;
        final EAtomicType eType = aAtom.getType ();
        if (nKind == 0 && eType == EAtomicType.UNTYPED_ATOMIC)
        {
          try
          {
            final double d = AtomicCaster.castFromString (aAtom.getString (),
                                                          EAtomicType.DOUBLE,
                                                          aCtx.getStaticContext ()).getAsDouble ();
            // NaN equals nothing; +0 and -0 are equal
            if (!Double.isNaN (d))
              aSet.add (Double.valueOf (d == 0 ? 0d : d));
          }
          catch (final XPathException ex)
          {
            aResult = NO_INDEX;
            break;
          }
        }
        else
          if (nKind == 1 && eType.isIntegerFamily ())
            aSet.add (aAtom.getBigInteger ());
          else
            if (nKind == 2 && aAtom.isStringLike ())
              aSet.add (aAtom.getString ());
            else
            {
              aResult = NO_INDEX;
              break;
            }
      }
      aSlots[nKind] = aResult;
    }
    return aSlots[nKind] == NO_INDEX ? null : (Set <Object>) aSlots[nKind];
  }

  /**
   * General equality of one atomic value against a long sequence via a hash index (the same long
   * sequence is typically compared many times, e.g. in a predicate).
   *
   * @return The result or <code>null</code> if the fast path does not apply.
   */
  @Nullable
  private static Boolean _getIndexedEquality (@NonNull final XdmSequence aLeft,
                                              @NonNull final XdmSequence aRight,
                                              @NonNull final DynamicContext aCtx)
  {
    final boolean bLeftBig = aLeft.size () >= 64;
    final XdmSequence aBig = bLeftBig ? aLeft : aRight;
    final XdmSequence aOne = bLeftBig ? aRight : aLeft;
    if (aBig.size () < 64 || aOne.size () != 1 || !(aOne.get (0) instanceof final XdmAtomic aProbe))
      return null;
    if (getDefaultCollation (aCtx) != null)
      return null;
    final EAtomicType eType = aProbe.getType ();
    if (eType == EAtomicType.UNTYPED_ATOMIC)
      return null;
    if (eType.isIntegerFamily ())
    {
      final Set <Object> aInts = _getEqualityIndex (aBig, 1, aCtx);
      if (aInts != null)
        return Boolean.valueOf (aInts.contains (aProbe.getBigInteger ()));
    }
    if (eType.isNumeric ())
    {
      final Set <Object> aDoubles = _getEqualityIndex (aBig, 0, aCtx);
      if (aDoubles != null)
      {
        final double d = aProbe.getAsDouble ();
        return Boolean.valueOf (!Double.isNaN (d) && aDoubles.contains (Double.valueOf (d == 0 ? 0d : d)));
      }
      return null;
    }
    if (aProbe.isStringLike ())
    {
      final Set <Object> aStrings = _getEqualityIndex (aBig, 2, aCtx);
      if (aStrings != null)
        return Boolean.valueOf (aStrings.contains (aProbe.getString ()));
    }
    return null;
  }

  /**
   * A general comparison of operands that are atomised already, with the default collation and,
   * in XPath 1.0 compatibility mode, its comparison rules; without the equality index.
   */
  static boolean generalCompareAtoms (@NonNull final EComparisonOp eOp,
                                      @NonNull final XdmSequence aLeftAtoms,
                                      @NonNull final XdmSequence aRightAtoms,
                                      @NonNull final DynamicContext aCtx)
  {
    return AtomicComparator.generalCompare (eOp, aLeftAtoms, aRightAtoms, getDefaultCollation (aCtx), aCtx);
  }

  /**
   * An arithmetic operand in XPath 1.0 compatibility mode (XPath 3.1 section 3.5): only the first
   * atomised item counts, an empty operand is NaN, and booleans, strings, untyped and numeric
   * values are converted with fn:number.
   */
  @NonNull
  private static XdmAtomic _arithmeticOperand10 (@NonNull final XdmSequence aSeq)
  {
    final XdmSequence aAtoms = atomize (aSeq);
    if (aAtoms.isEmpty ())
      return XdmAtomic.dbl (Double.NaN);
    final XdmAtomic aValue = (XdmAtomic) aAtoms.get (0);
    final EAtomicType eType = aValue.getType ();
    if (aValue.isNumeric () ||
        eType == EAtomicType.BOOLEAN ||
        eType.isStringFamily () ||
        eType == EAtomicType.UNTYPED_ATOMIC)
      return XdmAtomic.dbl (AtomicComparator.toNumber (aValue));
    return aValue;
  }

  /**
   * Cast to one of the built-in list types (<code>xs:IDREFS</code>, <code>xs:NMTOKENS</code>,
   * <code>xs:ENTITIES</code>, XPath 3.1 section 3.14.2): the string value is split at XML
   * whitespace and each token is cast to the item type of the list.
   *
   * @param eItemType
   *        The item type of the list, e.g. <code>xs:IDREF</code> for <code>xs:IDREFS</code>.
   * @return The sequence of the cast tokens, at least one item.
   * @throws XPathException
   *         XPTY0004 if the value is neither of a string type nor untyped; FORG0001 if the value
   *         holds no token or a token is not valid for the item type
   */
  @NonNull
  private static XdmSequence _castToList (@NonNull final XdmAtomic aValue,
                                          @NonNull final EAtomicType eItemType,
                                          @NonNull final DynamicContext aCtx)
  {
    if (!aValue.getType ().isStringFamily () && aValue.getType () != EAtomicType.UNTYPED_ATOMIC)
      throw XPathException.type ("Only strings can be cast to a list type, not " +
                                 aValue.getType ().getQualifiedName ());
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final String sToken : aValue.getString ().split ("[ \\t\\r\\n]+"))
      if (!sToken.isEmpty ())
        aBuilder.add (AtomicCaster.castFromString (sToken, eItemType, aCtx.getStaticContext ()));
    if (aBuilder.size () == 0)
      throw XPathException.invalidValue ("A value of a list type must have at least one item");
    return aBuilder.build ();
  }

  /**
   * Cast one atomic value to a built-in atomic type. The union target <code>xs:numeric</code>
   * keeps a numeric value as it is and casts anything else to <code>xs:double</code>, its first
   * member type.
   *
   * @throws XPathException
   *         if the cast is not allowed (XPTY0004) or the value is not valid for the target type
   *         (FORG0001 and the other cast errors of F&amp;O 3.1 section 19)
   */
  @NonNull
  private static XdmAtomic _castAtomic (@NonNull final XdmAtomic aValue,
                                        @NonNull final EAtomicType eTarget,
                                        @NonNull final DynamicContext aCtx)
  {
    if (eTarget == EAtomicType.NUMERIC)
    {
      // Casting to the union xs:numeric tries its members in order
      if (aValue.isNumeric ())
        return aValue;
      return AtomicCaster.cast (aValue, EAtomicType.DOUBLE, aCtx.getStaticContext ());
    }
    return AtomicCaster.cast (aValue, eTarget, aCtx.getStaticContext ());
  }

  /**
   * The castable counterpart of {@link #_castAtomic(XdmAtomic, EAtomicType, DynamicContext)}: a
   * numeric value is always castable to <code>xs:numeric</code>, anything else if it is castable
   * to <code>xs:double</code>.
   *
   * @return <code>true</code> if the cast would succeed.
   */
  private static boolean _isCastable (@NonNull final XdmAtomic aValue,
                                      @NonNull final EAtomicType eTarget,
                                      @NonNull final DynamicContext aCtx)
  {
    if (eTarget == EAtomicType.NUMERIC)
      return aValue.isNumeric () || AtomicCaster.isCastable (aValue, EAtomicType.DOUBLE, aCtx.getStaticContext ());
    return AtomicCaster.isCastable (aValue, eTarget, aCtx.getStaticContext ());
  }

  /**
   * Cast or castable to a user-defined simple type of the in-scope schema definitions.
   */
  @NonNull
  private static XdmSequence _schemaCast (@NonNull final XdmSequence aValue,
                                          @NonNull final SeqType aType,
                                          @NonNull final IXdmTypeDefinition aTarget,
                                          final boolean bCastable,
                                          @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aAtoms = atomize (aValue);
    if (aAtoms.isEmpty ())
    {
      final boolean bAllowEmpty = aType.occurrence ().allowsEmpty ();
      if (bCastable)
        return _bool (bAllowEmpty);
      if (bAllowEmpty)
        return XdmSequence.EMPTY;
      throw XPathException.type ("An empty sequence cannot be cast to a user-defined type");
    }
    if (aAtoms.size () > 1)
    {
      if (bCastable)
        return FALSE_SEQ;
      throw XPathException.type ("A sequence of " + aAtoms.size () + " items cannot be cast");
    }
    final XdmAtomic aAtomic = (XdmAtomic) aAtoms.get (0);
    if (!bCastable)
      return SchemaTypeOps.cast (aAtomic, aTarget, aCtx.getStaticContext ());
    try
    {
      SchemaTypeOps.cast (aAtomic, aTarget, aCtx.getStaticContext ());
      return TRUE_SEQ;
    }
    catch (final XPathException ex)
    {
      return FALSE_SEQ;
    }
  }

  /**
   * Cast or castable.
   *
   * @return The cast value, or for castable the boolean result.
   */
  @NonNull
  private static XdmSequence _cast (@NonNull final XdmSequence aValue,
                                    @NonNull final SeqType aType,
                                    final boolean bCastable,
                                    @NonNull final DynamicContext aCtx)
  {
    final IXdmTypeDefinition aSchemaTarget = aType.itemType ().schemaType ();
    if (aSchemaTarget != null)
      return _schemaCast (aValue, aType, aSchemaTarget, bCastable, aCtx);
    final EAtomicType eTarget = aType.itemType ().atomicType ();
    if (eTarget == EAtomicType.ANY_ATOMIC_TYPE || eTarget == EAtomicType.NOTATION)
      throw XPathException.of ("XPST0080", eTarget.getQualifiedName () + " is not a valid cast target");
    final XdmSequence aAtoms = atomize (aValue);
    if (aAtoms.isEmpty ())
    {
      final boolean bAllowEmpty = aType.occurrence ().allowsEmpty ();
      if (bCastable)
        return _bool (bAllowEmpty);
      if (bAllowEmpty)
        return XdmSequence.EMPTY;
      throw XPathException.type ("An empty sequence cannot be cast to " + eTarget.getQualifiedName ());
    }
    if (aAtoms.size () > 1)
    {
      if (bCastable)
        return FALSE_SEQ;
      throw XPathException.type ("A sequence of " + aAtoms.size () + " items cannot be cast");
    }
    final XdmAtomic aAtomic = (XdmAtomic) aAtoms.get (0);
    if (aType.listCast ())
    {
      if (!bCastable)
        return _castToList (aAtomic, eTarget, aCtx);
      try
      {
        _castToList (aAtomic, eTarget, aCtx);
        return TRUE_SEQ;
      }
      catch (final XPathException ex)
      {
        return FALSE_SEQ;
      }
    }
    if (bCastable)
      return _bool (_isCastable (aAtomic, eTarget, aCtx));
    return XdmSequence.of (_castAtomic (aAtomic, eTarget, aCtx));
  }

  /**
   * The name of a function for an error message: the conventional prefixes <code>fn:</code>,
   * <code>math:</code>, <code>map:</code> and <code>array:</code> for the standard namespaces, the
   * EQName (<code>Q{uri}local</code>) for any other namespace, and "function item" for an
   * anonymous function (<code>null</code>).
   */
  @NonNull
  private static String _functionDisplayName (@Nullable final XdmQName aName)
  {
    if (aName == null)
      return "function item";
    final String sNs = aName.namespaceUri ();
    if (XdmConstants.NS_FN.equals (sNs))
      return "fn:" + aName.localName ();
    if (FunctionLibrary.NS_MATH.equals (sNs))
      return "math:" + aName.localName ();
    if (FunctionLibrary.NS_MAP.equals (sNs))
      return "map:" + aName.localName ();
    if (FunctionLibrary.NS_ARRAY.equals (sNs))
      return "array:" + aName.localName ();
    return aName.getEQName ();
  }

  /**
   * Apply the function conversion rules to one argument; a failure names the function and the
   * argument position.
   */
  @NonNull
  static XdmSequence convertArgument (@NonNull final XdmSequence aValue,
                                      @NonNull final SeqType aType,
                                      @NonNull final DynamicContext aCtx,
                                      @Nullable final XdmQName aFunctionName,
                                      final int nArgIndex)
  {
    try
    {
      return SequenceTypeMatcher.convert (aValue, aType, aCtx, "XPTY0004");
    }
    catch (final XPathException ex)
    {
      if (ex.getClass () != XPathException.class || !XPathException.ERR_NAMESPACE.equals (ex.getCodeName ().namespaceUri ()))
        throw ex;
      throw new XPathException (ex.getCode (),
                                _functionDisplayName (aFunctionName) +
                                                ", argument " +
                                                (nArgIndex + 1) +
                                                ": " +
                                                ex.getDescription (),
                                ex);
    }
  }

  /** Call the static implementation of a library function with converted arguments. */
  @NonNull
  static XdmSequence invokeBuiltIn (@NonNull final FunctionDef aDef,
                                    @NonNull final DynamicContext aCtx,
                                    @NonNull final List <XdmSequence> aArgs)
  {
    aCtx.enterCall ();
    try
    {
      return aDef.getImpl ().call (aCtx, aArgs);
    }
    finally
    {
      aCtx.leaveCall ();
    }
  }

  /**
   * A general comparison (<code>=</code>, <code>!=</code>, <code>&lt;</code>, <code>&lt;=</code>,
   * <code>&gt;</code>, <code>&gt;=</code>): both operands are atomised and compared pairwise with
   * the default collation of the static context; true if any pair compares true. In XPath 1.0
   * compatibility mode its comparison rules apply. For <code>=</code> one atomic value against a
   * long sequence is answered from a hash index of the sequence kept in the run cache of the
   * dynamic context (pass the same sequence object again to reuse it).
   *
   * @param eOp
   *        A general comparison operator.
   * @param aLeft
   *        The left operand, not atomised.
   * @param aRight
   *        The right operand, not atomised.
   * @param aCtx
   *        The dynamic context.
   * @return The result.
   * @throws IllegalArgumentException
   *         if the operator is not a general comparison.
   * @throws XPathException
   *         XPTY0004 for values that cannot be compared, FOTY0013 for function items.
   */
  public static boolean generalCompare (@NonNull final EComparisonOp eOp,
                                        @NonNull final XdmSequence aLeft,
                                        @NonNull final XdmSequence aRight,
                                        @NonNull final DynamicContext aCtx)
  {
    if (!eOp.isGeneral ())
      throw new IllegalArgumentException ("Not a general comparison: " + eOp);
    if (eOp == EComparisonOp.GENERAL_EQ && !aCtx.getStaticContext ().isXPath10Compatibility ())
    {
      final Boolean aFast = _getIndexedEquality (aLeft, aRight, aCtx);
      if (aFast != null)
        return aFast.booleanValue ();
    }
    return generalCompareAtoms (eOp, atomize (aLeft), atomize (aRight), aCtx);
  }

  /**
   * A value comparison (<code>eq</code>, <code>ne</code>, <code>lt</code>, <code>le</code>,
   * <code>gt</code>, <code>ge</code>): each operand is atomised to at most one value, an
   * xs:untypedAtomic value is compared as xs:string, strings with the default collation of the
   * static context.
   *
   * @param eOp
   *        A value comparison operator.
   * @param aLeft
   *        The left operand, not atomised.
   * @param aRight
   *        The right operand, not atomised.
   * @param aCtx
   *        The dynamic context.
   * @return The empty sequence if an operand is empty, the boolean result otherwise.
   * @throws IllegalArgumentException
   *         if the operator is not a value comparison.
   * @throws XPathException
   *         XPTY0004 for an operand of more than one item or values that cannot be compared.
   */
  @NonNull
  public static XdmSequence valueCompare (@NonNull final EComparisonOp eOp,
                                          @NonNull final XdmSequence aLeft,
                                          @NonNull final XdmSequence aRight,
                                          @NonNull final DynamicContext aCtx)
  {
    if (!eOp.isValue ())
      throw new IllegalArgumentException ("Not a value comparison: " + eOp);
    XdmAtomic aL = atomizeOptional (aLeft, "left operand of a value comparison");
    if (aL == null)
      return XdmSequence.EMPTY;
    XdmAtomic aR = atomizeOptional (aRight, "right operand of a value comparison");
    if (aR == null)
      return XdmSequence.EMPTY;
    if (aL.getType () == EAtomicType.UNTYPED_ATOMIC)
      aL = XdmAtomic.string (aL.getString ());
    if (aR.getType () == EAtomicType.UNTYPED_ATOMIC)
      aR = XdmAtomic.string (aR.getString ());
    return _bool (AtomicComparator.valueCompare (eOp, aL, aR, getDefaultCollation (aCtx), aCtx));
  }

  /**
   * A node comparison (<code>is</code>, <code>&lt;&lt;</code>, <code>&gt;&gt;</code>).
   *
   * @param eOp
   *        A node comparison operator.
   * @param aLeft
   *        The left operand.
   * @param aRight
   *        The right operand.
   * @return The empty sequence if an operand is empty, the boolean result otherwise.
   * @throws IllegalArgumentException
   *         if the operator is not a node comparison.
   * @throws XPathException
   *         XPTY0004 if an operand is not a single node.
   */
  @NonNull
  public static XdmSequence nodeCompare (@NonNull final EComparisonOp eOp,
                                         @NonNull final XdmSequence aLeft,
                                         @NonNull final XdmSequence aRight)
  {
    if (eOp.isGeneral () || eOp.isValue ())
      throw new IllegalArgumentException ("Not a node comparison: " + eOp);
    if (aLeft.isEmpty () || aRight.isEmpty ())
      return XdmSequence.EMPTY;
    if (!aLeft.isSingleton () ||
        !aRight.isSingleton () ||
        !(aLeft.get (0) instanceof final IXdmNode aL) ||
        !(aRight.get (0) instanceof final IXdmNode aR))
      throw XPathException.type ("The operands of a node comparison must be single nodes");
    return switch (eOp)
    {
      case NODE_IS -> _bool (aL == aR);
      case NODE_PRECEDES -> _bool (IXdmNode.compareDocumentOrder (aL, aR) < 0);
      default -> _bool (IXdmNode.compareDocumentOrder (aL, aR) > 0);
    };
  }

  /**
   * Any comparison: {@link #generalCompare(EComparisonOp, XdmSequence, XdmSequence, DynamicContext)},
   * {@link #valueCompare(EComparisonOp, XdmSequence, XdmSequence, DynamicContext)} or
   * {@link #nodeCompare(EComparisonOp, XdmSequence, XdmSequence)} by the kind of the operator.
   *
   * @param eOp
   *        The comparison operator.
   * @param aLeft
   *        The left operand, not atomised.
   * @param aRight
   *        The right operand, not atomised.
   * @param aCtx
   *        The dynamic context.
   * @return The result as the comparison expression returns it: a boolean, or the empty sequence
   *         for a value or node comparison with an empty operand.
   */
  @NonNull
  public static XdmSequence compare (@NonNull final EComparisonOp eOp,
                                     @NonNull final XdmSequence aLeft,
                                     @NonNull final XdmSequence aRight,
                                     @NonNull final DynamicContext aCtx)
  {
    if (eOp.isGeneral ())
      return _bool (generalCompare (eOp, aLeft, aRight, aCtx));
    if (eOp.isValue ())
      return valueCompare (eOp, aLeft, aRight, aCtx);
    return nodeCompare (eOp, aLeft, aRight);
  }

  /**
   * A binary arithmetic operator (<code>+</code>, <code>-</code>, <code>*</code>,
   * <code>div</code>, <code>idiv</code>, <code>mod</code>). Each operand is atomised to at most
   * one value and an xs:untypedAtomic value is cast to xs:double. In XPath 1.0 compatibility mode
   * only the first atomised item counts, an empty operand is NaN, and booleans, strings and
   * untyped values are converted with fn:number.
   *
   * @param eOp
   *        An arithmetic operator.
   * @param aLeft
   *        The left operand, not atomised.
   * @param aRight
   *        The right operand, not atomised.
   * @param aCtx
   *        The dynamic context.
   * @return The empty sequence if an operand is empty (not in XPath 1.0 compatibility mode), the
   *         result value otherwise.
   * @throws IllegalArgumentException
   *         for <code>and</code> and <code>or</code>.
   * @throws XPathException
   *         XPTY0004 for operands of more than one item or of types the operator does not accept,
   *         FOAR0001 for a division by zero, FOAR0002 for an overflow.
   */
  @NonNull
  public static XdmSequence arithmetic (@NonNull final EBinaryOp eOp,
                                        @NonNull final XdmSequence aLeft,
                                        @NonNull final XdmSequence aRight,
                                        @NonNull final DynamicContext aCtx)
  {
    if (eOp == EBinaryOp.AND || eOp == EBinaryOp.OR)
      throw new IllegalArgumentException ("Not an arithmetic operator: " + eOp);
    if (aCtx.getStaticContext ().isXPath10Compatibility ())
      return XdmSequence.of (AtomicArithmetic.arithmetic (eOp,
                                                          _arithmeticOperand10 (aLeft),
                                                          _arithmeticOperand10 (aRight),
                                                          aCtx));
    final XdmAtomic aL = atomizeOptional (aLeft, "left operand of an arithmetic expression");
    if (aL == null)
      return XdmSequence.EMPTY;
    final XdmAtomic aR = atomizeOptional (aRight, "right operand of an arithmetic expression");
    if (aR == null)
      return XdmSequence.EMPTY;
    return XdmSequence.of (AtomicArithmetic.arithmetic (eOp,
                                                        untypedToDouble (aL, aCtx),
                                                        untypedToDouble (aR, aCtx),
                                                        aCtx));
  }

  /**
   * Unary minus or plus, with the operand rules of
   * {@link #arithmetic(EBinaryOp, XdmSequence, XdmSequence, DynamicContext)}.
   *
   * @param bNegate
   *        <code>true</code> for unary minus, <code>false</code> for unary plus.
   * @param aOperand
   *        The operand, not atomised.
   * @param aCtx
   *        The dynamic context.
   * @return The empty sequence for an empty operand (not in XPath 1.0 compatibility mode), the
   *         result value otherwise.
   * @throws XPathException
   *         XPTY0004 for an operand of more than one item or a non-numeric operand.
   */
  @NonNull
  public static XdmSequence unaryArithmetic (final boolean bNegate,
                                             @NonNull final XdmSequence aOperand,
                                             @NonNull final DynamicContext aCtx)
  {
    final XdmAtomic aNum;
    if (aCtx.getStaticContext ().isXPath10Compatibility ())
      aNum = _arithmeticOperand10 (aOperand);
    else
    {
      final XdmAtomic aValue = atomizeOptional (aOperand, "operand of a unary operator");
      if (aValue == null)
        return XdmSequence.EMPTY;
      aNum = untypedToDouble (aValue, aCtx);
    }
    if (bNegate)
      return XdmSequence.of (AtomicArithmetic.negate (aNum));
    if (!aNum.isNumeric ())
      throw XPathException.type ("The operand of unary plus must be numeric, not " +
                                 aNum.getType ().getQualifiedName ());
    return XdmSequence.of (aNum);
  }

  /**
   * <code>cast as</code>: atomise the operand and cast it to an atomic type, a list type
   * (<code>xs:NMTOKENS</code> etc.) or a user-defined simple type of the in-scope schema
   * definitions.
   *
   * @param aValue
   *        The operand, not atomised.
   * @param aType
   *        The SingleType of the cast expression (an atomic, list or schema type, optionally with
   *        <code>?</code>).
   * @param aCtx
   *        The dynamic context (its static context for namespaces and schema types).
   * @return The cast value; the empty sequence for an empty operand with <code>?</code>.
   * @throws XPathException
   *         XPST0080 for xs:anyAtomicType and xs:NOTATION, XPTY0004 for an empty operand without
   *         <code>?</code> or more than one item, FORG0001 and others for a value that cannot be
   *         cast.
   */
  @NonNull
  public static XdmSequence cast (@NonNull final XdmSequence aValue,
                                  @NonNull final SeqType aType,
                                  @NonNull final DynamicContext aCtx)
  {
    return _cast (aValue, aType, false, aCtx);
  }

  /**
   * <code>castable as</code>: would
   * {@link #cast(XdmSequence, SeqType, DynamicContext)} succeed?
   *
   * @param aValue
   *        The operand, not atomised.
   * @param aType
   *        The SingleType of the castable expression.
   * @param aCtx
   *        The dynamic context.
   * @return <code>true</code> if the value can be cast.
   * @throws XPathException
   *         XPST0080 for xs:anyAtomicType and xs:NOTATION, and errors of the atomisation.
   */
  public static boolean castable (@NonNull final XdmSequence aValue,
                                  @NonNull final SeqType aType,
                                  @NonNull final DynamicContext aCtx)
  {
    return ((XdmAtomic) _cast (aValue, aType, true, aCtx).get (0)).getBoolean ();
  }

  /**
   * Resolve a static function call against the function library of the static context.
   *
   * @param aCtx
   *        The dynamic context.
   * @param aName
   *        The expanded function name.
   * @param nArity
   *        The number of arguments.
   * @return The function definition.
   * @throws XPathException
   *         XPST0017 if there is no such function.
   */
  @NonNull
  public static FunctionDef getFunctionDef (@NonNull final DynamicContext aCtx,
                                            @NonNull final XdmQName aName,
                                            final int nArity)
  {
    final FunctionDef ret = aCtx.getStaticContext ().getFunctionLibrary ().getFunction (aName, nArity);
    if (ret == null)
      throw XPathException.of ("XPST0017", "Unknown function " + aName.getEQName () + "#" + nArity);
    return ret;
  }

  /**
   * Resolve a static function call against the function library of the static context, through
   * the resolution cache of the call site: the same definition as
   * {@link #getFunctionDef(DynamicContext, XdmQName, int)}, without building the lookup key again
   * while the library and its registrations stay the same.
   *
   * @param aCtx
   *        The dynamic context.
   * @param aSite
   *        The resolution cache of the call site, one per call expression.
   * @param aName
   *        The expanded function name.
   * @param nArity
   *        The number of arguments.
   * @return The function definition.
   * @throws XPathException
   *         XPST0017 if there is no such function.
   */
  @NonNull
  public static FunctionDef getFunctionDef (@NonNull final DynamicContext aCtx,
                                            @NonNull final FunctionCallSite aSite,
                                            @NonNull final XdmQName aName,
                                            final int nArity)
  {
    final FunctionDef ret = aSite.getFunction (aCtx.getStaticContext ().getFunctionLibrary (), aName, nArity);
    if (ret == null)
      throw XPathException.of ("XPST0017", "Unknown function " + aName.getEQName () + "#" + nArity);
    return ret;
  }

  /**
   * A static function call with evaluated arguments: the function conversion rules of the
   * signature are applied to each argument in order (atomisation, xs:untypedAtomic casting,
   * numeric promotion, the XPath 1.0 compatibility rules where the mode is on, cardinality), then
   * the static implementation runs with the focus of the given context, within
   * {@link DynamicContext#enterCall()}/{@link DynamicContext#leaveCall()}. The result is not
   * checked against the declared return type, as for a static call in the evaluator.
   *
   * @param aDef
   *        The function definition, e.g. from
   *        {@link #getFunctionDef(DynamicContext, XdmQName, int)}.
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The evaluated arguments, one per parameter.
   * @return The result.
   * @throws IllegalArgumentException
   *         if the number of arguments is not the arity of the function.
   * @throws XPathException
   *         XPTY0004 (naming the function and argument) if an argument does not match its type,
   *         and the errors of the function.
   */
  @NonNull
  public static XdmSequence callFunction (@NonNull final FunctionDef aDef,
                                          @NonNull final DynamicContext aCtx,
                                          @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.size () != aDef.getArity ())
      throw new IllegalArgumentException ("The function " +
                                          aDef.getName ().getEQName () +
                                          "#" +
                                          aDef.getArity () +
                                          " is called with " +
                                          aArgs.size () +
                                          " arguments");
    final List <SeqType> aParamTypes = aDef.getParamTypes ();
    final ICommonsList <XdmSequence> aConverted = new CommonsArrayList <> (aArgs.size ());
    for (int i = 0; i < aArgs.size (); ++i)
      aConverted.add (convertArgument (aArgs.get (i), aParamTypes.get (i), aCtx, aDef.getName (), i));
    return invokeBuiltIn (aDef, aCtx, aConverted);
  }
}
