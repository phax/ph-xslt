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

import java.nio.file.Path;
import java.util.BitSet;
import java.util.Collections;
import java.util.function.BiConsumer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.wrapper.Wrapper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xsd.XsdIdentityValidator.KeyTable;
import com.helger.xsd.XsdValidationRun.ChildModel;

/**
 * Instance validation (go-xslt <code>instance.go</code>): parse the instance,
 * assess it strictly from the document element, <code>xsi:type</code>,
 * <code>xsi:nil</code>, <code>xsi:schemaLocation</code> hints, ID/IDREF. The
 * per-call state go-xslt keeps on its per-call <code>Schema</code> copy lives
 * in {@link XsdValidationRun}, never in {@link XsdSchema}.
 * <p>
 * Only the first error is reported (go-xslt's non-collecting mode); collecting
 * all errors (<code>collect.go</code>, <code>explain.go</code>) is phase V7.
 * The XSD 1.1 seams that need phase V8 are <code>_selectAlternativeType</code>
 * and <code>_checkAsserts</code>. Nesting of instance elements is bounded (see
 * {@link #validateRoot}); every other walk over the instance is iterative.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdInstanceValidator
{
  /** Saturation bound of the occurrence arithmetic of {@link #_elementCapacityFor}. */
  private static final long CAPACITY_CAP = 1L << 50;

  /** The stack of the thread that validates a deeply nested instance. */
  private static final long BIG_STACK_BYTES = 512L * 1024 * 1024;

  /** The local name of a probe element no declaration can match. */
  private static final String PROBE_NAME = "\u0000wildcard-probe";

  @PresentForCodeCoverage
  private static final XsdInstanceValidator INSTANCE = new XsdInstanceValidator ();

  private XsdInstanceValidator ()
  {}

  /**
   * @return <code>true</code> for the characters go-xslt's
   *         <code>strings.TrimSpace</code> removes (Unicode White_Space).
   */
  private static boolean _isSpace (final char c)
  {
    return c == ' ' ||
           c >= '\t' && c <= '\r' ||
           c == '\u0085' ||
           c == ' ' ||
           c == ' ' ||
           c >= ' ' && c <= ' ' ||
           c == ' ' ||
           c == ' ' ||
           c == ' ' ||
           c == ' ' ||
           c == '　';
  }

  /** go-xslt <code>strings.TrimSpace</code>. */
  @NonNull
  private static String _trimSpace (@NonNull final String s)
  {
    int nStart = 0;
    int nEnd = s.length ();
    while (nStart < nEnd && _isSpace (s.charAt (nStart)))
      nStart++;
    while (nEnd > nStart && _isSpace (s.charAt (nEnd - 1)))
      nEnd--;
    return s.substring (nStart, nEnd);
  }

  private static boolean _isBlank (@NonNull final String s)
  {
    for (int i = 0; i < s.length (); i++)
      if (!_isSpace (s.charAt (i)))
        return false;
    return true;
  }

  /**
   * go-xslt <code>nameOf</code>.
   *
   * @param aNode
   *        An element or attribute.
   * @return Its expanded name.
   */
  @NonNull
  static XsdName nameOf (@NonNull final IXdmNode aNode)
  {
    return XsdName.of (aNode.getName ());
  }

  /** go-xslt <code>childElements</code>: the element children, in order (a fresh list). */
  @NonNull
  private static ICommonsList <IXdmNode> _childElements (@NonNull final IXdmNode aElement)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    final int nCount = aElement.getChildCount ();
    for (int i = 0; i < nCount; i++)
    {
      final IXdmNode aChild = aElement.getChildAtIndex (i);
      if (aChild.isElement ())
        ret.add (aChild);
    }
    return ret;
  }

  private static boolean _hasChildElements (@NonNull final IXdmNode aElement)
  {
    final int nCount = aElement.getChildCount ();
    for (int i = 0; i < nCount; i++)
      if (aElement.getChildAtIndex (i).isElement ())
        return true;
    return false;
  }

  private static boolean _hasNonWSText (@NonNull final IXdmNode aElement)
  {
    final int nCount = aElement.getChildCount ();
    for (int i = 0; i < nCount; i++)
    {
      final IXdmNode aChild = aElement.getChildAtIndex (i);
      if (aChild.isText () && !_isBlank (aChild.getValue ()))
        return true;
    }
    return false;
  }

  /** go-xslt <code>elementText</code>: the concatenated text children. */
  @NonNull
  private static String _elementText (@NonNull final IXdmNode aElement)
  {
    final int nCount = aElement.getChildCount ();
    if (nCount == 1 && aElement.getChildAtIndex (0).isText ())
      return aElement.getChildAtIndex (0).getValue ();
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < nCount; i++)
    {
      final IXdmNode aChild = aElement.getChildAtIndex (i);
      if (aChild.isText ())
        aSB.append (aChild.getValue ());
    }
    return aSB.toString ();
  }

  /**
   * go-xslt <code>elementHasAnyText</code>: whether the element has any text
   * children, whitespace included (the 1.1 reading of cvc-elt.3.2.1's "no
   * character children").
   */
  private static boolean _elementHasAnyText (@NonNull final IXdmNode aElement)
  {
    final int nCount = aElement.getChildCount ();
    for (int i = 0; i < nCount; i++)
    {
      final IXdmNode aChild = aElement.getChildAtIndex (i);
      if (aChild.isText () && !aChild.getValue ().isEmpty ())
        return true;
    }
    return false;
  }

  private static boolean _elementHasContent (@NonNull final IXdmNode aElement)
  {
    final int nCount = aElement.getChildCount ();
    for (int i = 0; i < nCount; i++)
    {
      final IXdmNode aChild = aElement.getChildAtIndex (i);
      if (aChild.isElement ())
        return true;
      if (aChild.isText () && !_isBlank (aChild.getValue ()))
        return true;
    }
    return false;
  }

  private static boolean _isNilled (@NonNull final IXdmNode aElement)
  {
    final String s = XsdNodes.attrNS (aElement, XsdConstants.NS_XSI, "nil");
    return s != null && ("true".equals (s) || "1".equals (s));
  }

  private static boolean _matchesAnyWildcard (@NonNull final ICommonsList <XsdWildcard> aWildcards,
                                              @NonNull final XsdName aName)
  {
    for (final XsdWildcard aWC : aWildcards)
      if (aWC.matches (aName))
        return true;
    return false;
  }

  /** A copy of an element whose local name no declaration can match. */
  @NonNull
  private static IXdmNode _probeOf (@NonNull final IXdmNode aElement)
  {
    final IXdmNode ret = IXdmNode.createElement (new XdmQName (aElement.getName ().namespaceUri (), PROBE_NAME));
    ret.setLocation (aElement.getLine (), aElement.getColumn ());
    return ret;
  }

  /**
   * go-xslt <code>elementPath</code>: render the position of an element as
   * <code>/Root[1]/Child[2]</code> (local names, 1-based index among
   * same-named element siblings).
   *
   * @param aElement
   *        The element.
   * @return The path.
   */
  @NonNull
  static String elementPath (@NonNull final IXdmNode aElement)
  {
    final ICommonsList <String> aSegs = new CommonsArrayList <> ();
    for (IXdmNode aCur = aElement; aCur != null && aCur.isElement (); aCur = aCur.getParent ())
    {
      int nIndex = 1;
      final IXdmNode aParent = aCur.getParent ();
      if (aParent != null)
      {
        nIndex = 0;
        final int nCount = aParent.getChildCount ();
        for (int i = 0; i < nCount; i++)
        {
          final IXdmNode aSibling = aParent.getChildAtIndex (i);
          if (aSibling.isElement () && aSibling.getName ().equals (aCur.getName ()))
            nIndex++;
          if (aSibling == aCur)
            break;
        }
      }
      aSegs.add (aCur.getName ().localName () + "[" + nIndex + "]");
    }
    final StringBuilder aSB = new StringBuilder ();
    for (int i = aSegs.size () - 1; i >= 0; i--)
      aSB.append ('/').append (aSegs.get (i));
    return aSB.toString ();
  }

  /**
   * go-xslt <code>stampLoc</code>: give an error a source location (the
   * innermost element being assessed when it occurred) if it does not carry
   * one. The deepest failing element stamps first; an ancestor then sees the
   * location already set and leaves it alone.
   */
  @NonNull
  private static XsdInvalidException _stamp (@NonNull final XsdInvalidException ex, @NonNull final IXdmNode aElement)
  {
    final XsdError aError = ex.getError ();
    final boolean bNoLocation = aError.line () == 0 && aError.column () == 0;
    final boolean bNoPath = aError.path ().isEmpty ();
    if (!bNoLocation && !bNoPath)
      return ex;
    return new XsdInvalidException (new XsdError (bNoLocation ? aElement.getLine () : aError.line (),
                                                  bNoLocation ? aElement.getColumn () : aError.column (),
                                                  aError.code (),
                                                  aError.message (),
                                                  aError.rule (),
                                                  bNoPath ? elementPath (aElement) : aError.path (),
                                                  aError.kind (),
                                                  aError.particle ()),
                                    ex.getMessages ());
  }

  /**
   * go-xslt <code>soft</code>: record an instance-validation error located at
   * an element and carry on, if the run collects all errors. Otherwise - and
   * for everything that is not an invalid verdict, notably
   * {@link XsdUnsupportedException} - the error is passed up unchanged.
   *
   * @param aRun
   *        The validation run.
   * @param ex
   *        The error.
   * @param aElement
   *        The element it is located at, if it has no location of its own.
   * @throws XsdInvalidException
   *         <code>ex</code> if the run does not collect
   * @throws XsdErrorCollector.Stopped
   *         if the error limit is reached
   */
  static void soft (@NonNull final XsdValidationRun aRun,
                    @NonNull final XsdInvalidException ex,
                    @NonNull final IXdmNode aElement)
  {
    final XsdErrorCollector aCollector = aRun.getCollector ();
    if (aCollector == null)
      throw ex;
    final XsdInvalidException aStamped = _stamp (ex, aElement);
    aCollector.add (new XsdErrorCollector.Entry (aStamped.getError (), aStamped.getMessages ()));
  }

  /** An invalid verdict located at an element. */
  @NonNull
  private static XsdInvalidException _invalidAt (@NonNull final String sCode,
                                                 @NonNull final String sMessage,
                                                 @NonNull final IXdmNode aElement)
  {
    return new XsdInvalidException (XsdError.of (sCode, sMessage).withLocation (aElement.getLine (), aElement.getColumn ()));
  }

  /**
   * go-xslt <code>describeChildren</code>: the actual child element sequence
   * for a cvc-complex-type.2.4 diagnostic.
   */
  @NonNull
  private static String _describeChildren (@NonNull final ICommonsList <IXdmNode> aKids)
  {
    if (aKids.isEmpty ())
      return "found: (none)";
    final ICommonsList <String> aNames = new CommonsArrayList <> ();
    for (final IXdmNode aKid : aKids)
      aNames.add (nameOf (aKid).toString ());
    return "found: " + String.join (", ", aNames);
  }

  /**
   * go-xslt <code>describeAllowedNames</code>: the deduplicated, sorted set of
   * element names that appear anywhere in a content model - a coarse "what is
   * legal here at all" hint.
   */
  @NonNull
  private static String _describeAllowedNames (@NonNull final ChildModel aModel)
  {
    if (aModel.m_aDecls.isEmpty ())
      return "(none)";
    final ICommonsList <String> aNames = new CommonsArrayList <> ();
    for (final XsdName aName : aModel.m_aDecls.keySet ())
      aNames.add (aName.toString ());
    Collections.sort (aNames);
    return String.join (", ", aNames);
  }

  /**
   * go-xslt <code>resolveInstanceType</code>: the type an <code>xsi:type</code>
   * names.
   *
   * @param aRun
   *        The validation run.
   * @param aElement
   *        The element carrying the attribute (for its namespace context).
   * @param sQName
   *        The attribute value.
   * @return The type. Never <code>null</code>.
   * @throws XsdInvalidException
   *         if it does not resolve (cvc-elt.4.2)
   */
  @NonNull
  static IXsdType resolveInstanceType (@NonNull final XsdValidationRun aRun,
                                       @NonNull final IXdmNode aElement,
                                       @NonNull final String sQName)
  {
    final XsdName aName = XsdNodes.resolveQName (aElement, sQName);
    if (XsdConstants.NS_XS.equals (aName.space ()))
    {
      final String sLocal = aName.local ();
      // XDM-only types are not schema types, and 1.1-only built-ins do not
      // resolve under 1.0 - same gate as the schema-side resolveType
      if ("untypedAtomic".equals (sLocal) ||
          "untyped".equals (sLocal) ||
          !aRun.isXsd11 () && XsdCompiler.isXsd11OnlyBuiltin (sLocal))
        throw XsdCompiler.invalid ("cvc-elt.4.2", "xsi:type " + aName + " is not a schema type");
      if ("error".equals (sLocal))
        return XsdSimpleType.ERROR;
      final XsdSimpleType aList = XsdCompiler.builtinList (sLocal);
      if (aList != null)
        return aList;
      final EAtomicType eAtom = XsdCompiler.builtinAtom (sLocal);
      if (eAtom != null)
        return XsdSimpleType.builtin (eAtom);
      if ("anyType".equals (sLocal))
        return XsdComplexType.anyType ();
      throw XsdCompiler.invalid ("cvc-elt.4.2", "xsi:type names an unknown built-in xs:" + sLocal);
    }
    final IXsdType aType = aRun.getSchema ().m_aTypes.get (aName);
    if (aType != null)
      return aType;
    throw XsdCompiler.invalid ("cvc-elt.4.2", "xsi:type " + aName + " cannot be resolved");
  }

  /**
   * go-xslt <code>yearZeroOK</code>: reject the year 0000 in the date family
   * under XSD 1.0 (1.1 adopts the proleptic calendar where it is valid), and
   * "+INF" for float and double.
   *
   * @param aRun
   *        The validation run.
   * @param aType
   *        The type the value is validated against.
   * @param sRaw
   *        The raw value.
   * @throws XsdInvalidException
   *         if the value is not valid under XSD 1.0
   */
  static void yearZeroOK (@NonNull final XsdValidationRun aRun,
                          @Nullable final XsdSimpleType aType,
                          @NonNull final String sRaw)
  {
    if (aRun.isXsd11 () || aType == null)
      return;
    switch (aType.resolvePrim ())
    {
      case DATE:
      case DATE_TIME:
      case G_YEAR:
      case G_YEAR_MONTH:
      {
        String sValue = _trimSpace (sRaw);
        if (sValue.startsWith ("-"))
          sValue = sValue.substring (1);
        if (sValue.startsWith ("0000"))
          throw XsdCompiler.invalid ("cvc-datatype-valid", "year 0000 is not a valid year in XSD 1.0");
        break;
      }
      case FLOAT:
      case DOUBLE:
        // "+INF" entered the float/double lexical space only in 1.1
        if ("+INF".equals (_trimSpace (sRaw)))
          throw XsdCompiler.invalid ("cvc-datatype-valid", "\"+INF\" is not a valid float/double lexical in XSD 1.0");
        break;
      default:
        break;
    }
  }

  /**
   * go-xslt <code>effectiveSimpleText</code>: the value to validate as an
   * element's simple content. cvc-elt.5.1: an element with no character or
   * element content but a default or fixed value constraint on its declaration
   * takes the constraint value as its schema-normalised value.
   */
  @NonNull
  private static String _effectiveSimpleText (@Nullable final XsdElementDecl aDecl, @NonNull final IXdmNode aElement)
  {
    if (aDecl != null && !_elementHasContent (aElement))
    {
      if (aDecl.m_bHasFixed)
        return aDecl.m_sFixed;
      if (aDecl.m_bHasDefault)
        return aDecl.m_sDefault;
    }
    return _elementText (aElement);
  }

  private static long _satMul (final long a, final long b)
  {
    return b > 0 && a > CAPACITY_CAP / b ? CAPACITY_CAP : a * b;
  }

  /**
   * go-xslt <code>elementCapacityFor</code> below one particle.
   *
   * @return The number of children the like-named element particles can
   *         consume, -1 if unbounded.
   */
  private static long _capacityRecursive (@NonNull final XsdParticle aParticle,
                                          @NonNull final XsdName aName,
                                          final long nMult,
                                          @NonNull final ICommonsSet <XsdModelGroup> aSeen,
                                          final int nDepth)
  {
    if (aParticle.m_nMax == 0)
      return 0;
    if (aParticle.m_aTerm instanceof final XsdElementDecl aDecl)
    {
      if (!aDecl.m_aName.equals (aName))
        return 0;
      if (aParticle.m_nMax == XsdConstants.UNBOUNDED || nMult < 0)
        return -1;
      return _satMul (nMult, aParticle.m_nMax);
    }
    if (aParticle.m_aTerm instanceof final XsdModelGroup aGroup)
    {
      // A cyclic model group is never exhausted; so is a group nested deeper
      // than the bound (go-xslt's stack is unbounded, ours is not)
      if (nDepth > XsdCompiler.MAX_NESTING || !aSeen.add (aGroup))
        return -1;
      final long nMult2 = aParticle.m_nMax != XsdConstants.UNBOUNDED && nMult > 0 ? _satMul (nMult, aParticle.m_nMax)
                                                                                  : -1;
      long nTotal = 0;
      for (final XsdParticle aSub : aGroup.m_aParticles)
      {
        final long nSub = _capacityRecursive (aSub, aName, nMult2, aSeen, nDepth + 1);
        if (nSub < 0)
        {
          nTotal = -1;
          break;
        }
        nTotal += nSub;
      }
      aSeen.remove (aGroup);
      return nTotal;
    }
    return 0;
  }

  /**
   * go-xslt <code>elementCapacityFor</code>.
   *
   * @return The total number of children the like-named element particles of
   *         the model can consume, or -1 when that is unbounded.
   */
  private static long _elementCapacityFor (@Nullable final XsdParticle aParticle, @NonNull final XsdName aName)
  {
    if (aParticle == null)
      return 0;
    return _capacityRecursive (aParticle, aName, 1, new CommonsHashSet <> (), 0);
  }

  /**
   * go-xslt <code>wildcardParseExists</code>: whether some parse of the
   * content model attributes child i to a wildcard. The probe rename keeps the
   * namespace (so namespace-constrained wildcards still admit it) but takes a
   * local name no declaration can match. The children may already carry probes
   * for children pinned to wildcards by earlier attribution decisions.
   */
  private static boolean _wildcardParseExists (@NonNull final XsdValidationRun aRun,
                                               @Nullable final XsdParticle aParticle,
                                               @Nullable final XsdOpenContent aOpenContent,
                                               @NonNull final ICommonsList <IXdmNode> aKids,
                                               final int nIndex)
  {
    final ICommonsList <IXdmNode> aCopy = new CommonsArrayList <> (aKids);
    aCopy.set (nIndex, _probeOf (aKids.get (nIndex)));
    final XsdMatchResult aResult = XsdContentMatcher.contentMatchesOpen (aRun.getSchema (),
                                                                         aParticle,
                                                                         aCopy,
                                                                         aOpenContent);
    return aResult.decided () && aResult.matched ();
  }

  /**
   * go-xslt <code>skipWildcardParseExcuses</code>: whether some parse of the
   * content model attributes child i to a processContents="skip" wildcard (in
   * which case that child is not assessed under that parse). The probe
   * replaces the child with a name no declaration can match: if the model
   * still matches structurally, a wildcard can absorb that position. The name
   * must match only skip wildcards - a lax or strict wildcard would still
   * assess it against its global declaration.
   */
  private static boolean _skipWildcardParseExcuses (@NonNull final XsdValidationRun aRun,
                                                    @NonNull final ChildModel aModel,
                                                    @NonNull final ICommonsList <IXdmNode> aKids,
                                                    final int nIndex,
                                                    @NonNull final XsdName aName)
  {
    if (!_matchesAnyWildcard (aModel.m_aSkipWc, aName))
      return false;
    final XsdOpenContent aOC = aModel.m_aOpenContent;
    if (aOC != null && aOC.m_aWildcard != null && XsdOpenContent.MODE_INTERLEAVE.equals (aOC.m_sMode))
    {
      final BitSet aOpen = XsdContentMatcher.greedyOpenAttribution (aRun.getSchema (),
                                                                              aModel.m_aParticle,
                                                                              aKids,
                                                                              aOC);
      if (aOpen != null && !aOpen.get (nIndex))
        return false; // the model takes this child greedily
    }
    // The lax and strict wildcards of the model (the open-content wildcard
    // joins its list unless it is a skip wildcard); the child model already
    // holds them that way
    if (_matchesAnyWildcard (aModel.m_aLaxWc, aName) || _matchesAnyWildcard (aModel.m_aStrictWc, aName))
      return false;
    return _wildcardParseExists (aRun, aModel.m_aParticle, aOC, aKids, nIndex);
  }

  /** go-xslt <code>substMatchesAnyDecl</code>. */
  private static boolean _substMatchesAnyDecl (@NonNull final XsdValidationRun aRun,
                                               @NonNull final ChildModel aModel,
                                               @NonNull final XsdName aName)
  {
    for (final XsdElementDecl aDecl : aModel.m_aDecls.values ())
      if (aRun.getSchema ().matchesElementName (aDecl, aName))
        return true;
    return false;
  }

  /**
   * go-xslt <code>baseChainContextType</code>: the type of a like-named
   * element particle in the base chain of a complex type (the
   * context-determined type a restriction removed from the model).
   */
  @Nullable
  private static IXsdType _baseChainContextType (@NonNull final XsdValidationRun aRun,
                                                 @NonNull final XsdComplexType aType,
                                                 @NonNull final XsdName aName)
  {
    XsdComplexType aCur = aType;
    for (int n = 0; n < 64; n++)
    {
      if (!(aCur.m_aBaseType instanceof final XsdComplexType aBase) || aBase == aCur)
        return null;
      final XsdElementDecl aDecl = aRun.childModelOf (aBase).m_aDecls.get (aName);
      if (aDecl != null)
        return aDecl.m_aType;
      aCur = aBase;
    }
    return null;
  }

  /**
   * XSD 1.1 conditional type assignment (go-xslt
   * <code>selectAlternativeType</code>): phase V8. A declaration with
   * alternatives cannot be compiled before.
   *
   * @return The assigned type, or <code>null</code> for the declared type.
   */
  @Nullable
  private static IXsdType _selectAlternativeType (@NonNull final XsdValidationRun aRun,
                                                  @NonNull final XsdElementDecl aDecl,
                                                  @NonNull final IXdmNode aElement)
  {
    if (!aRun.isXsd11 () || aDecl.m_aAlternatives.isEmpty ())
      return null;
    throw XsdCompiler.unsupported11 ("xs:alternative (conditional type assignment)");
  }

  /**
   * XSD 1.1 assertions (go-xslt <code>checkAsserts</code>): phase V8. A type
   * with assertions cannot be compiled before.
   */
  private static void _checkAsserts (@NonNull final XsdValidationRun aRun,
                                     @NonNull final XsdComplexType aType,
                                     @NonNull final IXdmNode aElement)
  {
    if (aRun.isXsd11 () && aRun.getSchema ().m_bHasAsserts)
      throw XsdCompiler.unsupported11 ("xs:assert");
  }

  /**
   * go-xslt <code>assessWildcardChild</code>: handle a child attributed to a
   * non-skip wildcard whose name also has a like-named model particle (dynamic
   * EDC, 1.1): the governing type must be validly derived from the
   * context-determined type, then the child is assessed against its global
   * declaration (or its xsi:type when no declaration exists; a lax match with
   * neither is accepted as-is).
   */
  private static void _assessWildcardChild (@NonNull final XsdValidationRun aRun,
                                            @Nullable final IXsdType aContextType,
                                            @NonNull final IXdmNode aKid,
                                            @NonNull final XsdName aName,
                                            @NonNull final ICommonsMap <XsdName, KeyTable> aScopes,
                                            @NonNull final ChildModel aModel)
  {
    IXsdType aGoverning = null;
    final String sXsiType = XsdNodes.attrNS (aKid, XsdConstants.NS_XSI, "type");
    final XsdElementDecl aGlobal = aRun.getSchema ().m_aElements.get (aName);
    if (sXsiType != null)
      aGoverning = resolveInstanceType (aRun, aKid, sXsiType);
    else
      if (aGlobal != null)
        aGoverning = aGlobal.m_aType;
    if (aGoverning != null && aContextType != null && !XsdDerivation.derivesFrom (aGoverning, aContextType))
      throw XsdCompiler.invalid ("cvc-assess-elt",
                                 "element " + aName + ": governing type is not validly derived from its context-determined type");
    if (aGlobal != null)
    {
      validateElementAgainst (aRun, aGlobal, aKid, aScopes, null);
      return;
    }
    if (sXsiType != null)
    {
      validateElementAgainst (aRun, new XsdElementDecl (aName), aKid, aScopes, null);
      return;
    }
    if (_matchesAnyWildcard (aModel.m_aStrictWc, aName) && !_matchesAnyWildcard (aModel.m_aLaxWc, aName))
      throw XsdCompiler.invalid ("cvc-assess-elt", "element " + aName + " has no declaration to assess it against");
  }

  /**
   * The attributes of an element that lax assessment descends into: the
   * element has no declaration, so it is assessed against the ur-type, whose
   * attribute wildcard is lax (XSD 1.0 section 3.4.4): an attribute with a
   * global declaration is validated against it, which is how an xml:id or an
   * ID arriving this way is counted and bound. The xsi attributes are left
   * alone, as are attributes without a declaration.
   */
  private static void _laxAssessAttributes (@NonNull final XsdValidationRun aRun, @NonNull final IXdmNode aElement)
  {
    final int nAttrs = aElement.getAttributeCount ();
    for (int i = 0; i < nAttrs; i++)
    {
      final IXdmNode aAttr = aElement.getAttributeAtIndex (i);
      final XsdName aName = nameOf (aAttr);
      if (XsdConstants.NS_XSI.equals (aName.space ()))
        continue;
      final XsdAttributeDecl aGlobal = aRun.getSchema ().m_aAttributes.get (aName);
      if (aGlobal == null || aGlobal.m_aType == null)
        continue;
      final String sValue = aAttr.getValue ();
      yearZeroOK (aRun, aGlobal.m_aType, sValue);
      XsdSimpleTypeValidator.validateIn (aGlobal.m_aType, sValue, aAttr);
      aRun.noteIDValues (aGlobal.m_aType, sValue, aElement, aAttr);
      aRun.annotateAssertType (aAttr, aGlobal.m_aType);
      aRun.noteNodeType (aAttr, aGlobal.m_aType);
    }
  }

  /**
   * Lax assessment of an element no declaration governs: an explicit
   * <code>xsi:type</code> supplies the type it is assessed against (validation
   * "as a type", like an undeclared document element), otherwise its
   * attributes are assessed. Not in go-xslt, which assesses neither.
   *
   * @return <code>true</code> if the children are to be assessed as well,
   *         <code>false</code> if an <code>xsi:type</code> governs the whole
   *         element.
   */
  private static boolean _laxAssessOwn (@NonNull final XsdValidationRun aRun, @NonNull final IXdmNode aElement)
  {
    final String sXsiType = XsdNodes.attrNS (aElement, XsdConstants.NS_XSI, "type");
    if (sXsiType != null)
    {
      validateAsType (aRun, resolveInstanceType (aRun, aElement, sXsiType), aElement);
      return false;
    }
    _laxAssessAttributes (aRun, aElement);
    return true;
  }

  /** One level of the iterative walk of {@link #_laxAssessSubtree}: an element and the next child to look at. */
  private static final class LaxFrame
  {
    final IXdmNode m_aNode;
    int m_nNext;

    LaxFrame (@NonNull final IXdmNode aNode)
    {
      m_aNode = aNode;
    }
  }

  /**
   * go-xslt <code>laxAssessSubtree</code>: carry out lax assessment (XSD 1.0
   * section 3.3.4 clause 2) below an element that a lax wildcard admitted but
   * no declaration governs: each descendant element is assessed against its
   * own global declaration as soon as one can be found, and descended into
   * otherwise. Without it a lax region is a black hole rather than a lenient
   * one. Iterative and depth first in document order, so the first error is
   * the one go-xslt's recursion reports.
   */
  private static void _laxAssessSubtree (@NonNull final XsdValidationRun aRun,
                                         @NonNull final IXdmNode aRoot,
                                         @NonNull final ICommonsMap <XsdName, KeyTable> aScopes)
  {
    if (!_laxAssessOwn (aRun, aRoot))
      return;
    final ICommonsList <LaxFrame> aStack = new CommonsArrayList <> ();
    aStack.add (new LaxFrame (aRoot));
    while (aStack.isNotEmpty ())
    {
      final LaxFrame aFrame = aStack.getLast ();
      if (aFrame.m_nNext >= aFrame.m_aNode.getChildCount ())
      {
        aStack.removeLast ();
        continue;
      }
      final IXdmNode aKid = aFrame.m_aNode.getChildAtIndex (aFrame.m_nNext++);
      if (!aKid.isElement ())
        continue;
      final XsdElementDecl aGlobal = aRun.getSchema ().m_aElements.get (nameOf (aKid));
      if (aGlobal != null)
        validateElementAgainst (aRun, aGlobal, aKid, aScopes, null);
      else
        if (_laxAssessOwn (aRun, aKid))
          aStack.add (new LaxFrame (aKid));
    }
  }

  /**
   * go-xslt <code>validateChildren</code>: check the child element sequence
   * against the content model and validate each child against its governing
   * declaration.
   */
  private static void _validateChildren (@NonNull final XsdValidationRun aRun,
                                         @NonNull final XsdComplexType aType,
                                         @NonNull final IXdmNode aElement,
                                         @NonNull final ICommonsList <IXdmNode> aKids,
                                         @NonNull final ICommonsMap <XsdName, KeyTable> aScopes)
  {
    final XsdSchema aSchema = aRun.getSchema ();
    final ChildModel aModel = aRun.childModelOf (aType);
    final XsdParticle aParticle = aModel.m_aParticle;
    final XsdOpenContent aOC = aModel.m_aOpenContent;
    final XsdMatchResult aMatch = XsdContentMatcher.contentMatchesOpen (aSchema, aParticle, aKids, aOC);
    if (!aMatch.decided ())
      throw new XsdUnsupportedException ("pathological content model - no verdict");
    if (!aMatch.matched ())
    {
      final XsdInvalidException aGeneric = _invalidAt ("cvc-complex-type.2.4",
                                                       "element " +
                                                                               nameOf (aElement) +
                                                                               ": content does not match its content model (" +
                                                                               _describeChildren (aKids) +
                                                                               "; allowed child elements: " +
                                                                               _describeAllowedNames (aModel) +
                                                                               ")",
                                                       aElement);
      if (!aRun.isCollecting ())
        throw aGeneric;
      // Collecting: say precisely what is wrong when that can be worked out (no
      // open content, a model the explainer can express); the generic message
      // above is the fallback
      if (aOC == null)
      {
        final ICommonsList <XsdErrorCollector.Entry> aCards = XsdExplain.cardinalityErrors (aSchema,
                                                                                            aElement,
                                                                                            aParticle,
                                                                                            aKids);
        if (aCards.isNotEmpty ())
        {
          for (final XsdErrorCollector.Entry aCard : aCards)
            aRun.getCollector ().add (aCard);
          throw new XsdErrorCollector.ModelMismatch ();
        }
      }
      soft (aRun, aGeneric, aElement);
      throw new XsdErrorCollector.ModelMismatch ();
    }
    final boolean b11 = aRun.isXsd11 ();
    ICommonsMap <XsdName, Integer> aElemUsed = null; // allocated on first use (1.1 EDC only)
    ICommonsList <IXdmNode> aPinned = aKids; // lazily copied; wildcard-attributed children get probed out
    boolean bPinnedCopied = false;
    for (int i = 0; i < aKids.size (); i++)
    {
      final IXdmNode aKid = aKids.get (i);
      final XsdName aName = nameOf (aKid);
      final XsdElementDecl aDecl = aModel.m_aDecls.get (aName);
      if (aDecl != null)
      {
        // XSD 1.1 dynamic Element Declarations Consistent: once the like-named
        // element particles are exhausted, a further same-named child is
        // attributed to a non-skip wildcard - its governing type (xsi:type,
        // else the global declaration's) must then be validly derived from the
        // like-named particle's type, and assessment runs against the global
        // declaration, not the local one
        if (b11 && (_matchesAnyWildcard (aModel.m_aLaxWc, aName) || _matchesAnyWildcard (aModel.m_aStrictWc, aName)))
        {
          final long nCap = _elementCapacityFor (aParticle, aName);
          final Integer aUsed = aElemUsed == null ? null : aElemUsed.get (aName);
          final long nUsed = aUsed == null ? 0 : aUsed.intValue ();
          if (nCap >= 0 && nUsed >= nCap && _wildcardParseExists (aRun, aParticle, aOC, aPinned, i))
          {
            _assessWildcardChild (aRun, aDecl.m_aType, aKid, aName, aScopes, aModel);
            if (!bPinnedCopied)
            {
              aPinned = new CommonsArrayList <> (aKids);
              bPinnedCopied = true;
            }
            aPinned.set (i, _probeOf (aKid));
            continue;
          }
        }
        if (b11)
        {
          if (aElemUsed == null)
            aElemUsed = new CommonsHashMap <> ();
          final Integer aUsed = aElemUsed.get (aName);
          aElemUsed.put (aName, Integer.valueOf (aUsed == null ? 1 : aUsed.intValue () + 1));
        }
        final int nSnap = aRun.collected ();
        RuntimeException aFailure = null;
        try
        {
          validateElementAgainst (aRun, aDecl, aKid, aScopes, aModel.m_aFixedsByName.get (aName));
        }
        catch (final XsdInvalidException | XsdUnsupportedException | XsdErrorCollector.Stopped ex)
        {
          aFailure = ex;
        }
        if (aFailure != null || aRun.collected () > nSnap)
        {
          // Attribution is by name, but the model may also offer a
          // processContents="skip" wildcard absorbing this child at this
          // position - under that parse the child is not assessed at all. A
          // child for which some parse validates is not an error; the errors
          // it already recorded while collecting are withdrawn.
          if (_skipWildcardParseExcuses (aRun, aModel, aKids, i, aName))
          {
            aRun.uncollect (nSnap);
            aRun.markUnassessed (aKid);
            continue;
          }
          if (aFailure instanceof final XsdInvalidException aInvalid)
            soft (aRun, aInvalid, aKid);
          else
            if (aFailure != null)
              throw aFailure;
        }
        continue;
      }
      // A child matched by a processContents="skip" wildcard is not assessed
      if (_matchesAnyWildcard (aModel.m_aSkipWc, aName))
      {
        aRun.markUnassessed (aKid);
        continue;
      }
      // Substitution-group member or a strict/lax wildcard match: its own
      // global declaration governs, if one exists
      final XsdElementDecl aGlobal = aSchema.m_aElements.get (aName);
      if (aGlobal != null)
      {
        // Dynamic EDC also reaches a wildcard-attributed child whose
        // like-named particle lives only in the base chain - a restriction
        // dropped it, but the context-determined type survives. A child that
        // can arrive as a substitution-group member is exempt: that
        // attribution is not a wildcard match.
        if (b11 &&
            (_matchesAnyWildcard (aModel.m_aLaxWc, aName) || _matchesAnyWildcard (aModel.m_aStrictWc, aName)) &&
            !_substMatchesAnyDecl (aRun, aModel, aName))
        {
          final IXsdType aContextType = _baseChainContextType (aRun, aType, aName);
          if (aContextType != null)
          {
            IXsdType aGoverning = aGlobal.m_aType;
            final String sXsiType = XsdNodes.attrNS (aKid, XsdConstants.NS_XSI, "type");
            if (sXsiType != null)
              try
              {
                aGoverning = resolveInstanceType (aRun, aKid, sXsiType);
              }
              catch (final XsdInvalidException ex)
              {
                // keeps the global declaration's type
              }
            if (aGoverning != null && !XsdDerivation.derivesFrom (aGoverning, aContextType))
              throw XsdCompiler.invalid ("cvc-assess-elt",
                                         "element " +
                                                          aName +
                                                          ": governing type is not validly derived from its context-determined type");
          }
        }
        try
        {
          validateElementAgainst (aRun, aGlobal, aKid, aScopes, null);
        }
        catch (final XsdInvalidException ex)
        {
          soft (aRun, ex, aKid);
        }
        continue;
      }
      // A strict wildcard demands that the match be assessed, so a name with
      // no declaration (and no xsi:type to supply one) is invalid. Attribution
      // is positional: if a lax or skip wildcard in the model also admits the
      // name, some parse assigns the child there and no strict assessment is
      // demanded.
      final boolean bHasXsiType = XsdNodes.attrNS (aKid, XsdConstants.NS_XSI, "type") != null;
      if (!bHasXsiType &&
          _matchesAnyWildcard (aModel.m_aStrictWc, aName) &&
          !_matchesAnyWildcard (aModel.m_aLaxWc, aName) &&
          !_matchesAnyWildcard (aModel.m_aSkipWc, aName))
        throw XsdCompiler.invalid ("cvc-assess-elt", "element " + aName + " has no declaration to assess it against");
      // Otherwise (skip/lax wildcard, or strict with xsi:type) accept as-is.
      // Accepting the element as-is is not the same as ignoring its contents:
      // XSD 1.0 section 3.3.4 clause 2 says an element a lax wildcard admits
      // but for which no declaration can be found is laxly assessed, and lax
      // assessment recurses. Only a processContents="skip" wildcard makes a
      // subtree opaque.
      if (_matchesAnyWildcard (aModel.m_aLaxWc, aName) && !_matchesAnyWildcard (aModel.m_aSkipWc, aName))
        _laxAssessSubtree (aRun, aKid, aScopes);
    }
  }

  /**
   * go-xslt <code>assessDeclaredChildren</code>: validate the children of an
   * element whose content model did not match, as far as their declarations
   * can be told by name.
   *
   * @return <code>true</code> if every child could be assigned
   */
  private static boolean _assessDeclaredChildren (@NonNull final XsdValidationRun aRun,
                                                  @NonNull final XsdComplexType aType,
                                                  @NonNull final ICommonsList <IXdmNode> aKids,
                                                  @NonNull final ICommonsMap <XsdName, KeyTable> aScopes)
  {
    final ChildModel aModel = aRun.childModelOf (aType);
    boolean bAll = true;
    for (final IXdmNode aKid : aKids)
    {
      final XsdName aName = nameOf (aKid);
      final XsdElementDecl aDecl = aModel.m_aDecls.get (aName);
      if (aDecl == null)
      {
        bAll = false;
        continue;
      }
      try
      {
        validateElementAgainst (aRun, aDecl, aKid, aScopes, aModel.m_aFixedsByName.get (aName));
      }
      catch (final XsdInvalidException ex)
      {
        soft (aRun, ex, aKid);
      }
    }
    return bAll;
  }

  /**
   * go-xslt <code>afterModelError</code> around <code>validateChildren</code>:
   * a content-model mismatch that was recorded still lets the declared children
   * and - when every child could be assigned - the element's own assertions be
   * checked.
   *
   * @return <code>true</code> if the content model did not match, which is
   *         recorded and finished; the caller is done with the element
   */
  private static boolean _validateChildrenOrMismatch (@NonNull final XsdValidationRun aRun,
                                                      @NonNull final XsdComplexType aType,
                                                      @NonNull final IXdmNode aElement,
                                                      @NonNull final ICommonsList <IXdmNode> aKids,
                                                      @NonNull final ICommonsMap <XsdName, KeyTable> aScopes)
  {
    try
    {
      _validateChildren (aRun, aType, aElement, aKids, aScopes);
      return false;
    }
    catch (final XsdErrorCollector.ModelMismatch ex)
    {
      if (_assessDeclaredChildren (aRun, aType, aKids, aScopes))
        _checkAsserts (aRun, aType, aElement);
      return true;
    }
  }

  /**
   * go-xslt <code>validateComplex</code>.
   */
  private static void _validateComplex (@NonNull final XsdValidationRun aRun,
                                        @NonNull final XsdComplexType aType,
                                        @NonNull final IXdmNode aElement,
                                        @NonNull final ICommonsMap <XsdName, KeyTable> aScopes,
                                        @Nullable final XsdElementDecl aDecl)
  {
    if (aType.m_bAbstract)
      throw XsdCompiler.invalid ("cvc-type.2", "type is abstract and cannot be used directly");
    try
    {
      XsdAttributeValidator.validateAttributes (aRun, aType, aElement);
    }
    catch (final XsdInvalidException ex)
    {
      soft (aRun, ex, aElement);
    }
    final ICommonsList <IXdmNode> aKids = _childElements (aElement);
    final boolean b11 = aRun.isXsd11 ();
    switch (aType.m_eKind)
    {
      case EMPTY:
        // An empty content type opened by appliesToEmpty admits wildcard
        // children; character data stays forbidden
        if (aRun.getSchema ().effectiveOpenContent (aType) != null)
        {
          if (_hasNonWSText (aElement))
            throw XsdCompiler.invalid ("cvc-complex-type.2.1", "open empty content admits no character data");
          if (_validateChildrenOrMismatch (aRun, aType, aElement, aKids, aScopes))
            return;
          break;
        }
        if (!aKids.isEmpty () || _hasNonWSText (aElement))
          throw XsdCompiler.invalid ("cvc-complex-type.2.1", "element must be empty");
        // 1.1 cvc-complex-type 2.1 forbids every character information item in
        // empty content - whitespace included
        if (b11 && !_elementText (aElement).isEmpty ())
          throw XsdCompiler.invalid ("cvc-complex-type.2.1", "empty content admits no character data at all");
        break;
      case SIMPLE:
        if (!aKids.isEmpty ())
          throw XsdCompiler.invalid ("cvc-complex-type.2.2", "simple content must not contain child elements");
        if (aType.m_aSimpleType != null)
        {
          final String sText = _effectiveSimpleText (aDecl, aElement);
          yearZeroOK (aRun, aType.m_aSimpleType, sText);
          XsdSimpleTypeValidator.validateIn (aType.m_aSimpleType, sText, aElement);
          aRun.annotateAssertType (aElement, aType.m_aSimpleType);
          aRun.noteIDValues (aType.m_aSimpleType, sText, _idBindingParent (aElement), aElement);
        }
        break;
      case ELEMENT_ONLY:
        if (_hasNonWSText (aElement))
          throw XsdCompiler.invalid ("cvc-complex-type.2.3", "element-only content must not contain character data");
        // An empty particle is the empty {content type} per the mapping rules
        // - and 1.1's cvc-complex-type 2.1 forbids even whitespace there
        if (b11 &&
            !_elementText (aElement).isEmpty () &&
            aRun.getSchema ().effectiveOpenContent (aType) == null &&
            XsdComponentChecks.isEmptyParticle (aRun.getSchema ().effectiveParticle (aType)))
          throw XsdCompiler.invalid ("cvc-complex-type.2.1", "empty content admits no character data at all");
        if (_validateChildrenOrMismatch (aRun, aType, aElement, aKids, aScopes))
          return;
        break;
      default:
        if (_validateChildrenOrMismatch (aRun, aType, aElement, aKids, aScopes))
          return;
        break;
    }
    _checkAsserts (aRun, aType, aElement);
  }

  /**
   * go-xslt <code>idBindingParent</code>: the element an ID carried as the
   * content of an element binds to, or <code>null</code> for the document
   * element: with no parent element the ID binds nothing at all - it
   * establishes no binding an IDREF could resolve against.
   */
  @Nullable
  private static IXdmNode _idBindingParent (@NonNull final IXdmNode aElement)
  {
    final IXdmNode aParent = aElement.getParent ();
    return aParent != null && aParent.isElement () ? aParent : null;
  }

  /**
   * go-xslt <code>validateAgainstType</code>.
   */
  private static void _validateAgainstType (@NonNull final XsdValidationRun aRun,
                                            @Nullable final IXsdType aType,
                                            @NonNull final IXdmNode aElement,
                                            @NonNull final ICommonsMap <XsdName, KeyTable> aScopes,
                                            @Nullable final XsdElementDecl aDecl)
  {
    // Every element reaches its governing type here, with xsi:type,
    // conditional type assignment and substitution already resolved - the one
    // place the node annotation can learn what a node was actually assessed
    // against (a no-op unless phase V6 enabled it)
    aRun.noteNodeType (aElement, aType);
    if (aType instanceof final XsdSimpleType aST)
    {
      final int nAttrs = aElement.getAttributeCount ();
      for (int i = 0; i < nAttrs; i++)
      {
        final IXdmNode aAttr = aElement.getAttributeAtIndex (i);
        final String sNS = aAttr.getName ().namespaceUri ();
        if (XsdConstants.NS_XSI.equals (sNS) || XsdConstants.NS_XML.equals (sNS))
          continue;
        throw XsdCompiler.invalid ("cvc-type.3.1.1",
                                   "attribute " + aAttr.getName ().localName () + " not allowed on a simple-typed element");
      }
      if (_hasChildElements (aElement))
        throw XsdCompiler.invalid ("cvc-type.3.1.2", "simple-typed element must not have child elements");
      final String sText = _effectiveSimpleText (aDecl, aElement);
      yearZeroOK (aRun, aST, sText);
      XsdSimpleTypeValidator.validateIn (aST, sText, aElement);
      aRun.annotateAssertType (aElement, aST);
      aRun.noteIDValues (aST, sText, _idBindingParent (aElement), aElement);
      return;
    }
    if (aType instanceof final XsdComplexType aCT)
    {
      _validateComplex (aRun, aCT, aElement, aScopes, aDecl);
      return;
    }
    throw new XsdUnsupportedException ("element " + nameOf (aElement) + " has no type to be assessed against");
  }

  /**
   * go-xslt <code>assessNilledElement</code>: the type checks that survive
   * xsi:nil="true": the attribute assessment (cvc-type 3.1.1 for simple types,
   * the full attribute rules for complex ones). Content and value-constraint
   * checks are waived.
   */
  private static void _assessNilledElement (@NonNull final XsdValidationRun aRun,
                                            @Nullable final IXsdType aType,
                                            @NonNull final IXdmNode aElement)
  {
    // A nilled element keeps its declared type annotation - only the value
    // checks are waived, so the annotation is as real as any other element's
    aRun.noteNodeType (aElement, aType);
    if (aType instanceof XsdSimpleType)
    {
      final int nAttrs = aElement.getAttributeCount ();
      for (int i = 0; i < nAttrs; i++)
      {
        final IXdmNode aAttr = aElement.getAttributeAtIndex (i);
        final String sNS = aAttr.getName ().namespaceUri ();
        if (XsdConstants.NS_XSI.equals (sNS) || XsdConstants.NS_XML.equals (sNS))
          continue;
        throw XsdCompiler.invalid ("cvc-type.3.1.1",
                                   "attribute " + aAttr.getName ().localName () + " not allowed on a simple-typed element");
      }
    }
    else
      if (aType instanceof final XsdComplexType aCT)
        XsdAttributeValidator.validateAttributes (aRun, aCT, aElement);
  }

  private static void _validateElementAgainstImpl (@NonNull final XsdValidationRun aRun,
                                                   @NonNull final XsdElementDecl aDecl,
                                                   @NonNull final IXdmNode aElement,
                                                   @NonNull final ICommonsMap <XsdName, KeyTable> aScopes,
                                                   @Nullable final ICommonsList <String> aExtraFixed)
  {
    IXsdType aType = aDecl.m_aType;
    final String sXsiType = XsdNodes.attrNS (aElement, XsdConstants.NS_XSI, "type");
    if (sXsiType != null)
    {
      final IXsdType aXsiType = resolveInstanceType (aRun, aElement, sXsiType);
      // cvc-elt.4.3: an xsi:type must be validly derived from the element's
      // declared type
      if (aDecl.m_aType != null && !XsdDerivation.derivesFrom (aXsiType, aDecl.m_aType))
        throw XsdCompiler.invalid ("cvc-elt.4.3",
                                   "xsi:type is not derived from the declared type of " + aDecl.m_aName);
      // @block on the element forbids xsi:type substitution by a blocked
      // derivation method (restriction/extension). cvc-elt.4.3 tests the
      // element's {disallowed substitutions} together with the declared
      // type's {prohibited substitutions} - a complex type's @block bars
      // substitution by that method just as the element's own @block does.
      ICommonsSet <String> aBlocked = aDecl.m_aBlocked;
      if (aDecl.m_aType instanceof final XsdComplexType aDeclCT && !aDeclCT.m_aBlock.isEmpty ())
      {
        aBlocked = new CommonsHashSet <> (aDecl.m_aBlocked);
        aBlocked.addAll (aDeclCT.m_aBlock);
      }
      if (!aBlocked.isEmpty () && aXsiType != aDecl.m_aType)
        for (final String sMethod : XsdDerivation.derivationMethodsTo (aXsiType, aDecl.m_aType))
          if (aBlocked.contains (sMethod))
            throw XsdCompiler.invalid ("cvc-elt.4.3",
                                       "xsi:type substitution by " + sMethod + " is blocked on " + aDecl.m_aName);
      aType = aXsiType;
    }
    else
    {
      final IXsdType aAlternative = _selectAlternativeType (aRun, aDecl, aElement);
      if (aAlternative != null)
        aType = aAlternative; // XSD 1.1 conditional type assignment
    }
    if (XsdNodes.attrNS (aElement, XsdConstants.NS_XSI, "nil") != null)
    {
      // cvc-elt.3.1: xsi:nil may appear only on a nillable element - its value
      // does not matter
      if (!aDecl.m_bNillable)
        throw XsdCompiler.invalid ("cvc-elt.3.1", "element " + aDecl.m_aName + " is not nillable");
      if (_isNilled (aElement))
      {
        // cvc-elt.3.2.1 demands no character children at all - under 1.1 even
        // pure whitespace nils the nil; 1.0 keeps the whitespace-tolerant
        // reading
        if (_elementHasContent (aElement) || aRun.isXsd11 () && _elementHasAnyText (aElement))
          throw XsdCompiler.invalid ("cvc-elt.3.2.1", "nilled element " + aDecl.m_aName + " must be empty");
        // cvc-elt.3.2.2: a nilled element must not carry a fixed value constraint
        if (aDecl.m_bHasFixed)
          throw XsdCompiler.invalid ("cvc-elt.3.2.2",
                                     "nilled element " + aDecl.m_aName + " has a fixed value constraint");
        // cvc-type 3.1.1/3.1.2 still apply to a nilled element - only the
        // value checks are waived
        _assessNilledElement (aRun, aType, aElement);
        // The element is valid and nilled: record the XDM [nilled] property
        // for the in-place annotation path. It keeps its type annotation -
        // nilled says the element has no value, not that it has no type.
        aRun.noteNodeNilled (aElement);
        return;
      }
    }

    // Build this element's key/unique tables, extending the in-scope set for
    // any keyref in this element or its descendants. ownScopes holds only the
    // tables whose scope element is this element: a keyref resolves against
    // the node table at its own scope, and tables propagate upward only - a
    // key on an ancestor is not visible to a descendant's keyref.
    ICommonsMap <XsdName, KeyTable> aChildScopes = aScopes;
    final ICommonsMap <XsdName, KeyTable> aOwnScopes = new CommonsHashMap <> ();
    if (!aDecl.m_aConstraints.isEmpty ())
    {
      aChildScopes = new CommonsHashMap <> (aScopes);
      for (final XsdIdentityConstraint aConstraint : aDecl.m_aConstraints)
      {
        if ("keyref".equals (aConstraint.m_sKind))
          continue;
        final KeyTable aTable = XsdIdentityValidator.buildKeyTable (aRun, aConstraint, aElement);
        aChildScopes.put (aConstraint.m_aName, aTable);
        aOwnScopes.put (aConstraint.m_aName, aTable);
      }
    }

    aRun.enterElement ();
    try
    {
      _validateAgainstType (aRun, aType, aElement, aChildScopes, aDecl);
    }
    finally
    {
      aRun.leaveElement ();
    }

    // cvc-elt.5.2.2.2: an element with a fixed value constraint and simple
    // content must have that exact value (compared in the type's value space).
    // When the content model holds several same-named declarations with
    // different fixed values, matching any of them is enough. cvc-elt.5.2.2.1:
    // an element with a fixed value and mixed content must have no element
    // children at all - the fixed value is the whole content.
    if (aDecl.m_bHasFixed && _hasChildElements (aElement))
      if (aType instanceof final XsdComplexType aCT && aCT.m_eKind == EXsdContentKind.MIXED)
        throw XsdCompiler.invalid ("cvc-elt.5.2.2.1",
                                   "element " + aDecl.m_aName.local () + " has a fixed value and must have no element children");
    if (aDecl.m_bHasFixed && !_hasChildElements (aElement) && _elementHasContent (aElement))
    {
      final String sContent = _elementText (aElement);
      final XsdSimpleType aST = XsdComponentParser.simpleContentType (aType);
      boolean bEqual = _fixedMatches (aST, sContent, aDecl.m_sFixed);
      if (aExtraFixed != null)
        for (final String sAlt : aExtraFixed)
          bEqual = bEqual || _fixedMatches (aST, sContent, sAlt);
      if (!bEqual)
        throw XsdCompiler.invalid ("cvc-elt.5.2.2.2", "element " + aDecl.m_aName.local () + " must equal its fixed value");
    }

    for (final XsdIdentityConstraint aConstraint : aDecl.m_aConstraints)
      if ("keyref".equals (aConstraint.m_sKind))
        XsdIdentityValidator.checkKeyref (aRun, aConstraint, aElement, aOwnScopes);
  }

  private static boolean _fixedMatches (@Nullable final XsdSimpleType aType,
                                        @NonNull final String sContent,
                                        @NonNull final String sFixed)
  {
    if (aType != null)
      return XsdSimpleTypeValidator.fixedEqual (aType, sContent, sFixed);
    return XsdLexHelper.lexEqualCollapsed (sContent, sFixed);
  }

  /**
   * go-xslt <code>validateElementAgainst</code>: validate an element against
   * an element declaration, honouring xsi:type, xsi:nil and the declaration's
   * identity constraints.
   *
   * @param aRun
   *        The validation run.
   * @param aDecl
   *        The declaration.
   * @param aElement
   *        The instance element.
   * @param aScopes
   *        The key/unique tables in scope for keyref resolution.
   * @param aExtraFixed
   *        Fixed values of further same-named declarations of the content
   *        model; may be <code>null</code>.
   * @throws XsdInvalidException
   *         if the element is not valid; the error carries the location of the
   *         innermost failing element
   */
  static void validateElementAgainst (@NonNull final XsdValidationRun aRun,
                                      @NonNull final XsdElementDecl aDecl,
                                      @NonNull final IXdmNode aElement,
                                      @NonNull final ICommonsMap <XsdName, KeyTable> aScopes,
                                      @Nullable final ICommonsList <String> aExtraFixed)
  {
    try
    {
      _validateElementAgainstImpl (aRun, aDecl, aElement, aScopes, aExtraFixed);
    }
    catch (final XsdInvalidException ex)
    {
      throw _stamp (ex, aElement);
    }
  }

  /**
   * go-xslt <code>validateElement</code>: validate a top-level (document)
   * element against its global declaration, or "as a type" when it has no
   * declaration but an explicit xsi:type.
   *
   * @param aRun
   *        The validation run.
   * @param aElement
   *        The document element.
   * @throws XsdInvalidException
   *         if the element is not valid
   */
  static void validateElement (@NonNull final XsdValidationRun aRun, @NonNull final IXdmNode aElement)
  {
    try
    {
      final XsdName aName = nameOf (aElement);
      final XsdElementDecl aDecl = aRun.getSchema ().m_aElements.get (aName);
      if (aDecl == null)
      {
        // A document element with no governing declaration may still be
        // assessed against an explicit xsi:type (validation "as a type")
        final String sXsiType = XsdNodes.attrNS (aElement, XsdConstants.NS_XSI, "type");
        if (sXsiType != null)
        {
          final IXsdType aType = resolveInstanceType (aRun, aElement, sXsiType);
          aRun.enterElement ();
          try
          {
            _validateAgainstType (aRun, aType, aElement, new CommonsHashMap <> (), null);
          }
          finally
          {
            aRun.leaveElement ();
          }
          return;
        }
        throw XsdCompiler.invalid ("cvc-elt.1", "element " + aName + " is not declared");
      }
      if (aDecl.m_bAbstract)
        throw XsdCompiler.invalid ("cvc-elt.2", "abstract element " + aName + " cannot appear directly");
      _validateElementAgainstImpl (aRun, aDecl, aElement, new CommonsHashMap <> (), null);
    }
    catch (final XsdInvalidException ex)
    {
      throw _stamp (ex, aElement);
    }
  }

  /**
   * Validate an element against a type ("validation as a type", the
   * <code>[xsl:]type</code> episode of the bridge), the same assessment
   * {@link #validateElement} makes for an undeclared element with an
   * <code>xsi:type</code>.
   *
   * @param aRun
   *        The validation run.
   * @param aType
   *        The type the element is assessed against.
   * @param aElement
   *        The element.
   * @throws XsdInvalidException
   *         if the element is not valid
   */
  static void validateAsType (@NonNull final XsdValidationRun aRun,
                              @NonNull final IXsdType aType,
                              @NonNull final IXdmNode aElement)
  {
    try
    {
      aRun.enterElement ();
      try
      {
        _validateAgainstType (aRun, aType, aElement, new CommonsHashMap <> (), null);
      }
      finally
      {
        aRun.leaveElement ();
      }
    }
    catch (final XsdInvalidException ex)
    {
      throw _stamp (ex, aElement);
    }
  }

  /**
   * go-xslt <code>checkLateHints</code>: enforce XSD 1.0's rule on where
   * schema location hints may appear (Structures section 4.3.2): an
   * xsi:schemaLocation or xsi:noNamespaceSchemaLocation on an element is an
   * error when an element or attribute in a namespace it addresses already
   * appeared before that element within the validation root. A hint that
   * repeats a namespace and location given earlier names nothing new and is no
   * error. XSD 1.1 dropped the rule, so it is a no-op there.
   *
   * @param aSchema
   *        The schema (for its version).
   * @param aRoot
   *        The validation root.
   * @return The error, or <code>null</code> if there is none.
   */
  @Nullable
  static XsdError checkLateHints (@NonNull final XsdSchema aSchema, @NonNull final IXdmNode aRoot)
  {
    if (aSchema.isXsd11 ())
      return null;
    final ICommonsSet <String> aSeen = new CommonsHashSet <> (); // namespaces of the elements and attributes so far
    final ICommonsSet <String> aHinted = new CommonsHashSet <> (); // {namespace, location} pairs hinted so far
    final ICommonsList <IXdmNode> aStack = new CommonsArrayList <> ();
    aStack.add (aRoot);
    while (aStack.isNotEmpty ())
    {
      final IXdmNode aEl = aStack.removeLast ();
      final ICommonsList <String> aAddressedNS = new CommonsArrayList <> ();
      final ICommonsList <String> aAddressedLoc = new CommonsArrayList <> ();
      final String sSchemaLocation = XsdNodes.attrNS (aEl, XsdConstants.NS_XSI, "schemaLocation");
      if (sSchemaLocation != null)
      {
        final ICommonsList <String> aFields = XsdLexHelper.xmlFields (sSchemaLocation);
        for (int i = 0; i + 1 < aFields.size (); i += 2)
        {
          aAddressedNS.add (aFields.get (i));
          aAddressedLoc.add (aFields.get (i + 1));
        }
      }
      final String sNoNS = XsdNodes.attrNS (aEl, XsdConstants.NS_XSI, "noNamespaceSchemaLocation");
      if (sNoNS != null)
      {
        aAddressedNS.add ("");
        aAddressedLoc.add (_trimSpace (sNoNS));
      }
      for (int i = 0; i < aAddressedNS.size (); i++)
      {
        final String sNS = aAddressedNS.get (i);
        final boolean bFirst = aHinted.add (sNS + "\u0000" + aAddressedLoc.get (i));
        if (bFirst && aSeen.contains (sNS))
          return new XsdError (aEl.getLine (),
                               aEl.getColumn (),
                               "",
                               "a schema location hint for namespace " +
                                     XsdLexHelper.quote (sNS) +
                                     " comes after an element or attribute in that namespace (XSD 1.0 section 4.3.2)",
                               "",
                               elementPath (aEl),
                               "",
                               "");
      }
      aSeen.add (aEl.getName ().namespaceUri ());
      final int nAttrs = aEl.getAttributeCount ();
      for (int i = 0; i < nAttrs; i++)
        aSeen.add (aEl.getAttributeAtIndex (i).getName ().namespaceUri ());
      for (int i = aEl.getChildCount () - 1; i >= 0; i--)
      {
        final IXdmNode aChild = aEl.getChildAtIndex (i);
        if (aChild.isElement ())
          aStack.add (aChild);
      }
    }
    return null;
  }

  /**
   * go-xslt <code>hintDocs</code>: read the schema documents referenced by the
   * document element's xsi:schemaLocation / xsi:noNamespaceSchemaLocation. A
   * hint that cannot be read is ignored. The locations resolve against the
   * schema's base directory, or the instance's own directory when the schema
   * has none (the empty schema set).
   */
  @NonNull
  private static ICommonsList <XsdSchemaSource> _hintDocs (@NonNull final XsdSchema aSchema,
                                                           @NonNull final IXdmNode aRoot,
                                                           @Nullable final Path aInstanceDir)
  {
    final ICommonsList <XsdSchemaSource> ret = new CommonsArrayList <> ();
    final Path aBaseDir = aSchema.m_aBaseDir != null ? aSchema.m_aBaseDir : aInstanceDir;
    if (aBaseDir == null)
      return ret;
    final ICommonsList <String> aLocations = new CommonsArrayList <> ();
    final String sSchemaLocation = XsdNodes.attrNS (aRoot, XsdConstants.NS_XSI, "schemaLocation");
    if (sSchemaLocation != null)
    {
      final ICommonsList <String> aFields = XsdLexHelper.xmlFields (sSchemaLocation);
      for (int i = 0; i + 1 < aFields.size (); i += 2)
        aLocations.add (aFields.get (i + 1)); // a known namespace may still gain declarations
    }
    final String sNoNS = XsdNodes.attrNS (aRoot, XsdConstants.NS_XSI, "noNamespaceSchemaLocation");
    if (sNoNS != null)
      aLocations.add (_trimSpace (sNoNS));
    for (final String sLocation : aLocations)
    {
      final Path aPath = XsdFileAccess.resolveLocation (aBaseDir, sLocation);
      if (aPath == null)
        continue;
      final byte [] aBytes = aSchema.m_aFileAccess.readFile (aPath);
      if (aBytes == null)
        continue; // hints are hints
      ret.add (XsdSchemaSource.ofBytes (aBytes, aPath, aPath.getParent ()));
    }
    return ret;
  }

  /** @return The number of nested element levels of a tree (iterative, level by level). */
  private static int _elementDepth (@NonNull final IXdmNode aRoot)
  {
    int nDepth = 0;
    ICommonsList <IXdmNode> aLevel = new CommonsArrayList <> (aRoot);
    while (aLevel.isNotEmpty ())
    {
      nDepth++;
      final ICommonsList <IXdmNode> aNext = new CommonsArrayList <> ();
      for (final IXdmNode aNode : aLevel)
      {
        final int nCount = aNode.getChildCount ();
        for (int i = 0; i < nCount; i++)
        {
          final IXdmNode aChild = aNode.getChildAtIndex (i);
          if (aChild.isElement ())
            aNext.add (aChild);
        }
      }
      aLevel = aNext;
    }
    return nDepth;
  }

  /**
   * Validate the document element (or the root of a subtree) of an instance,
   * on a thread with a big stack if it is nested deeper than
   * {@link XsdValidationRun#MAX_DEPTH} levels. go-xslt relies on its growable
   * stack for this; a Java thread has a fixed one.
   *
   * @param aRun
   *        The validation run.
   * @param aRoot
   *        The element to validate.
   * @throws XsdInvalidException
   *         if the element is not valid
   * @throws XsdUnsupportedException
   *         if the element is nested deeper than
   *         {@link XsdValidationRun#MAX_DEPTH_BIG_STACK} levels
   */
  static void validateRoot (@NonNull final XsdValidationRun aRun, @NonNull final IXdmNode aRoot)
  {
    validateRoot (aRun, aRoot, XsdInstanceValidator::validateElement);
  }

  /**
   * {@link #validateRoot(XsdValidationRun, IXdmNode)} with the assessment of
   * the root given: its declaration ({@link #validateElement}) or a type
   * ({@link #validateAsType}).
   *
   * @param aRun
   *        The validation run.
   * @param aRoot
   *        The element to validate.
   * @param aAssess
   *        The assessment of the root.
   * @throws XsdInvalidException
   *         if the element is not valid
   * @throws XsdUnsupportedException
   *         if the element is nested deeper than
   *         {@link XsdValidationRun#MAX_DEPTH_BIG_STACK} levels
   */
  static void validateRoot (@NonNull final XsdValidationRun aRun,
                            @NonNull final IXdmNode aRoot,
                            @NonNull final BiConsumer <XsdValidationRun, IXdmNode> aAssess)
  {
    final int nDepth = _elementDepth (aRoot);
    if (nDepth <= XsdValidationRun.MAX_DEPTH)
    {
      aRun.setMaxDepth (XsdValidationRun.MAX_DEPTH);
      aAssess.accept (aRun, aRoot);
      return;
    }
    if (nDepth > XsdValidationRun.MAX_DEPTH_BIG_STACK)
      throw new XsdUnsupportedException ("instance elements nested deeper than " +
                                         XsdValidationRun.MAX_DEPTH_BIG_STACK +
                                         " levels");
    aRun.setMaxDepth (XsdValidationRun.MAX_DEPTH_BIG_STACK);
    final Wrapper <RuntimeException> aFailure = new Wrapper <> ();
    final Thread aThread = new Thread (null, () -> {
      try
      {
        aAssess.accept (aRun, aRoot);
      }
      catch (final RuntimeException ex)
      {
        aFailure.set (ex);
      }
    }, "xsd-deep-instance", BIG_STACK_BYTES);
    aThread.start ();
    try
    {
      aThread.join ();
    }
    catch (final InterruptedException ex)
    {
      Thread.currentThread ().interrupt ();
      throw new XsdUnsupportedException ("validation interrupted", ex);
    }
    if (aFailure.get () != null)
      throw aFailure.get ();
  }

  /**
   * @param aOptions
   *        The options, <code>null</code> to stop at the first error (the
   *        verdict is the same; only the list of errors is shorter).
   */
  @NonNull
  private static XsdValidationResult _validateDocument (@NonNull final XsdSchema aSchemaIn,
                                                        @NonNull final IXdmNode aDocument,
                                                        @Nullable final Path aInstanceDir,
                                                        @Nullable final XsdValidationOptions aOptions)
  {
    XsdSchema aSchema = aSchemaIn;
    final IXdmNode aRoot = XsdNodes.rootElement (aDocument);
    if (aRoot == null)
      return XsdValidationResult.createInvalid (XsdError.of ("", "no document element"));
    // xsi:schemaLocation hints on the document element may supply schema
    // documents beyond the compiled set; they can only add components, and a
    // broken hint never invalidates the instance
    final ICommonsList <XsdSchemaSource> aHinted = _hintDocs (aSchema, aRoot, aInstanceDir);
    if (aHinted.isNotEmpty ())
    {
      final ICommonsList <XsdSchemaSource> aMerged = new CommonsArrayList <> (aSchema.m_aSourceDocs);
      aMerged.addAll (aHinted);
      try
      {
        aSchema = new XsdCompiler (aSchema.m_eVersion,
                                   new XsdCompileOptions ().setFileAccess (aSchema.m_aFileAccess)).compile (aMerged);
      }
      catch (final XsdInvalidException | XsdUnsupportedException ex)
      {
        // keeps the schema without the hints
      }
    }
    final XsdError aLateHint = checkLateHints (aSchema, aRoot);
    if (aLateHint != null)
      return XsdValidationResult.createInvalid (aLateHint);
    final XsdValidationRun aRun = new XsdValidationRun (aSchema, aDocument);
    final XsdErrorCollector aCollector = aOptions == null ? null : new XsdErrorCollector (aOptions.getMaxErrors ());
    final String sLanguage = aOptions == null ? "" : aOptions.getLanguage ();
    if (aCollector != null)
      aRun.setCollector (aCollector);
    try
    {
      try
      {
        validateRoot (aRun, aRoot);
      }
      catch (final XsdInvalidException ex)
      {
        soft (aRun, ex, aRoot);
      }
      catch (final XsdErrorCollector.Stopped ex)
      {
        // the limit is reached: the list is complete
      }
      catch (final XsdUnsupportedException ex)
      {
        // Once an error is known, anything further the walk trips over must not
        // turn that verdict into "no verdict": the instance IS invalid, the
        // list just ends early
        if (aCollector == null || aCollector.size () == 0)
          throw ex;
      }
      if (aCollector != null && aCollector.size () > 0)
        return new XsdValidationResult (false, aCollector.getSorted (sLanguage));
      aRun.checkIDRefs ();
    }
    catch (final XsdInvalidException ex)
    {
      return XsdValidationResult.createInvalid (ex.getError ());
    }
    catch (final XsdErrorCollector.Stopped ex)
    {
      return new XsdValidationResult (false, aCollector.getSorted (sLanguage));
    }
    return XsdValidationResult.createValid ();
  }

  /**
   * go-xslt <code>Schema.ValidateOpts</code> for an instance file. Not
   * well-formed input is invalid.
   *
   * @param aSchema
   *        The compiled schema.
   * @param aInstance
   *        The instance file.
   * @param aOptions
   *        The options.
   * @return The result, with all errors in document order. Never
   *         <code>null</code>.
   */
  @NonNull
  static XsdValidationResult validate (@NonNull final XsdSchema aSchema,
                                       @NonNull final Path aInstance,
                                       @NonNull final XsdValidationOptions aOptions)
  {
    final IXdmNode aDocument;
    try
    {
      aDocument = new XdmParser ().parse (aInstance);
    }
    catch (final XdmParseException ex)
    {
      return XsdValidationResult.createInvalid (XsdError.of ("", "instance not well-formed: " + ex.getMessage ()));
    }
    final Path aAbsolute = aInstance.toAbsolutePath ();
    return _validateDocument (aSchema, aDocument, aAbsolute.getParent (), aOptions);
  }

  /**
   * go-xslt <code>Schema.ValidateOpts</code> for instance text.
   *
   * @param aSchema
   *        The compiled schema.
   * @param sInstance
   *        The instance document text.
   * @param aOptions
   *        The options, <code>null</code> to stop at the first error.
   * @return The result. Never <code>null</code>.
   */
  @NonNull
  static XsdValidationResult validateText (@NonNull final XsdSchema aSchema,
                                           @NonNull final String sInstance,
                                           @Nullable final XsdValidationOptions aOptions)
  {
    final IXdmNode aDocument;
    try
    {
      aDocument = new XdmParser ().parse (sInstance);
    }
    catch (final XdmParseException ex)
    {
      return XsdValidationResult.createInvalid (XsdError.of ("", "instance not well-formed: " + ex.getMessage ()));
    }
    return _validateDocument (aSchema, aDocument, null, aOptions);
  }
}
