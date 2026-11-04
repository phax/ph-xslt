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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xsd.XsdValidationRun.DefaultAttr;

/**
 * XSD 1.1 assertions (xs:assert, xs:assertion) and conditional type assignment
 * (xs:alternative) at validation time (go-xslt <code>xsd11.go</code>). Both
 * evaluate an XPath test compiled with the schema against the element being
 * validated. They are enforced only in 1.1 mode.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdAssertions
{
  /** The variable <code>$value</code> of assertions. */
  static final XdmQName VAR_VALUE = new XdmQName ("value");

  /** The maximum length of a complex type's base chain that is followed. */
  private static final int MAX_BASE_CHAIN = 64;

  @PresentForCodeCoverage
  private static final XsdAssertions INSTANCE = new XsdAssertions ();

  private XsdAssertions ()
  {}

  private record CloneStep (@NonNull IXdmNode src, @NonNull IXdmNode dstParent)
  {}

  /**
   * go-xslt <code>typedSimpleValue</code>: cast an already validated lexical to
   * its primitive's typed atomic; on a cast the engine cannot do, the untyped
   * reading stands in.
   */
  @NonNull
  private static XdmAtomic _typedSimpleValue (@NonNull final String sValue, @Nullable final EAtomicType ePrim)
  {
    if (ePrim != null)
    {
      final XdmAtomic aAtomic = XsdValueCompare.castOrNull (sValue, ePrim);
      if (aAtomic != null)
        return aAtomic;
    }
    return XdmAtomic.untyped (sValue);
  }

  private static boolean _isTrue (@NonNull final CompiledXPath aXPath, @NonNull final DynamicContext aCtx)
  {
    try
    {
      return Atomizer.getEffectiveBooleanValue (aXPath.evaluate (aCtx));
    }
    catch (final XPathException ex)
    {
      // Any dynamic error makes an assertion not satisfied, and a type
      // alternative not selected (3.13.4.1, 3.12.4)
      return false;
    }
    catch (final RuntimeException ex)
    {
      // Same for an error of the engine outside the XPath error codes
      return false;
    }
  }

  /**
   * The schema type an assertion copy of a node gets: the type its content was
   * validated against, for a union the member that accepted the value.
   */
  private static void _annotate (@NonNull final XsdValidationRun aRun,
                                 @NonNull final IXdmNode aCopy,
                                 @NonNull final IXdmNode aSrc)
  {
    final XsdSimpleType aType = aRun.getAssertType (aSrc);
    if (aType != null)
      aCopy.setSchemaType (aRun.getAssertSchemaType (aType, aSrc.getStringValue (), aSrc));
  }

  @NonNull
  private static IXdmNode _cloneShallow (@NonNull final XsdValidationRun aRun,
                                         @NonNull final IXdmNode aSrc,
                                         final boolean bRoot)
  {
    if (aSrc.getKind () == ENodeKind.TEXT)
      return IXdmNode.createText (aSrc.getValue ());
    final IXdmNode ret = IXdmNode.createElement (aSrc.getName ());
    ret.setLocation (aSrc.getLine (), aSrc.getColumn ());
    for (final Map.Entry <String, String> aEntry : aSrc.getDeclaredNamespaces ().entrySet ())
      ret.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    // Attributes are value-cloned so that their typed annotation lives only on
    // the assertion tree
    for (final IXdmNode aAttr : aSrc.getAllAttributes ())
    {
      final IXdmNode aCopy = IXdmNode.createAttribute (aAttr.getName (), aAttr.getValue ());
      _annotate (aRun, aCopy, aAttr);
      ret.addAttribute (aCopy);
    }
    // Attributes the element omitted but whose use carries a default or fixed
    // value constraint are part of the post-schema-validation element the
    // assertion is evaluated against
    final ICommonsMap <IXdmNode, ICommonsList <DefaultAttr>> aDefaults = aRun.getNodeDefaults ();
    final ICommonsList <DefaultAttr> aList = aDefaults == null ? null : aDefaults.get (aSrc);
    if (aList != null)
      for (final DefaultAttr aDefault : aList)
      {
        if (aSrc.getAttribute (aDefault.name ().space (), aDefault.name ().local ()) != null)
          continue;
        final IXdmNode aCopy = IXdmNode.createAttribute (new XdmQName (aDefault.name ().space (),
                                                                       aDefault.name ().local ()),
                                                         aDefault.value ());
        if (aDefault.type () instanceof final XsdSimpleType aST)
          aCopy.setSchemaType (aRun.getAssertSchemaType (aST, aDefault.value (), aSrc));
        ret.addAttribute (aCopy);
      }
    // An already validated simple-typed descendant atomizes to its typed
    // value; the assertion root itself stays untyped - its own type is what is
    // being validated
    if (!bRoot)
      _annotate (aRun, ret, aSrc);
    return ret;
  }

  /**
   * go-xslt <code>assertTree</code>: the XDM tree an assertion is evaluated
   * against (3.13.4.2) - a detached copy of the element, so that '/', '//' and
   * the ancestor axes cannot escape it, with comment and processing
   * instruction nodes filtered out. Iterative, as the depth is
   * input-controlled.
   */
  @NonNull
  private static IXdmNode _assertTree (@NonNull final XsdValidationRun aRun, @NonNull final IXdmNode aElement)
  {
    final IXdmNode ret = _cloneShallow (aRun, aElement, true);
    final ArrayDeque <CloneStep> aStack = new ArrayDeque <> ();
    for (int i = aElement.getChildCount () - 1; i >= 0; --i)
      aStack.push (new CloneStep (aElement.getChildAtIndex (i), ret));
    while (!aStack.isEmpty ())
    {
      final CloneStep aStep = aStack.pop ();
      final IXdmNode aSrc = aStep.src ();
      final ENodeKind eKind = aSrc.getKind ();
      if (eKind != ENodeKind.ELEMENT && eKind != ENodeKind.TEXT)
        continue;
      final IXdmNode aCopy = _cloneShallow (aRun, aSrc, false);
      aStep.dstParent ().appendChild (aCopy);
      for (int i = aSrc.getChildCount () - 1; i >= 0; --i)
        aStack.push (new CloneStep (aSrc.getChildAtIndex (i), aCopy));
    }
    // The tree is detached, so the namespace bindings declared on ancestors of
    // the assertion root would be lost: materialise its in-scope bindings
    final ICommonsOrderedMap <String, String> aDeclared = ret.getDeclaredNamespaces ();
    for (final Map.Entry <String, String> aEntry : aElement.getInScopeNamespaces ().entrySet ())
      if (!"xml".equals (aEntry.getKey ()) && !aDeclared.containsKey (aEntry.getKey ()))
        ret.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    return ret;
  }

  /**
   * go-xslt <code>inheritedAttrs</code>: the nearest-ancestor values of
   * {inheritable} attributes not overridden on the element itself (3.3.4.3).
   */
  @NonNull
  private static ICommonsList <IXdmNode> _inheritedAttrs (@NonNull final XsdValidationRun aRun,
                                                         @NonNull final IXdmNode aElement)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    final ICommonsSet <XsdName> aHave = new CommonsHashSet <> ();
    for (final IXdmNode aAttr : aElement.getAllAttributes ())
      aHave.add (new XsdName (aAttr.getName ().namespaceUri (), aAttr.getName ().localName ()));
    for (IXdmNode aAnc = aElement.getParent (); aAnc != null && aAnc.isElement (); aAnc = aAnc.getParent ())
    {
      if (!(aRun.elementTypeOf (aAnc) instanceof final XsdComplexType aCT))
        continue;
      for (final XsdAttributeUse aUse : aRun.getSchema ().effectiveAttrs (aCT).uses ())
      {
        final XsdName aName = aUse.m_aDecl.m_aName;
        if (!aUse.m_bInheritable || aHave.contains (aName))
          continue;
        final IXdmNode aAttr = aAnc.getAttribute (aName.space (), aName.local ());
        if (aAttr != null)
        {
          ret.add (aAttr);
          aHave.add (aName);
        }
      }
    }
    return ret;
  }

  /**
   * go-xslt <code>effectiveAsserts</code>: the assertions of a complex type
   * and its base chain.
   *
   * @param aType
   *        The complex type.
   * @return The assertions. Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  static ICommonsList <XsdAssertion> effectiveAsserts (@NonNull final XsdComplexType aType)
  {
    final ICommonsList <XsdAssertion> ret = new CommonsArrayList <> ();
    XsdComplexType aCur = aType;
    for (int n = 0; aCur != null && n < MAX_BASE_CHAIN; n++)
    {
      ret.addAll (aCur.m_aAsserts);
      if (!(aCur.m_aBaseType instanceof final XsdComplexType aBase) || aCur.m_sDerivation.isEmpty ())
        break;
      aCur = aBase;
    }
    return ret;
  }

  /**
   * go-xslt <code>evalSimpleAssertions</code>: run one restriction step's
   * xs:assertion facets against the typed <code>$value</code>. The focus is
   * absent, and any dynamic error or non-true result makes the value invalid.
   *
   * @param aAsserts
   *        The assertions of the step.
   * @param aValue
   *        The typed value.
   * @throws XsdInvalidException
   *         if an assertion is not satisfied
   */
  static void evalSimpleAssertions (@NonNull final ICommonsList <XsdAssertion> aAsserts,
                                    @NonNull final XdmSequence aValue)
  {
    for (final XsdAssertion aAssert : aAsserts)
    {
      final DynamicContext aCtx = aAssert.m_aXPath.createDynamicContext ().withVariable (VAR_VALUE, aValue);
      if (!_isTrue (aAssert.m_aXPath, aCtx))
        throw new XsdInvalidException (XsdError.of ("cvc-assertions-valid", "assertion on the value failed"),
                                       aAssert.m_aMsgs);
    }
  }

  /**
   * The typed <code>$value</code> of a single atomic lexical.
   *
   * @param sValue
   *        The whitespace-normalised lexical.
   * @param ePrim
   *        Its primitive. May be <code>null</code>.
   * @return The typed value.
   */
  @NonNull
  static XdmSequence typedAtomicValue (@NonNull final String sValue, @Nullable final EAtomicType ePrim)
  {
    return XdmSequence.of (_typedSimpleValue (sValue, ePrim));
  }

  /**
   * The typed <code>$value</code> of a list: the sequence of typed items.
   *
   * @param aItems
   *        The list items.
   * @param ePrim
   *        The primitive of the item type. May be <code>null</code>.
   * @return The typed value.
   */
  @NonNull
  static XdmSequence typedListValue (@NonNull final ICommonsList <String> aItems, @Nullable final EAtomicType ePrim)
  {
    final ICommonsList <IXdmItem> aSeq = new CommonsArrayList <> (aItems.size ());
    for (final String sItem : aItems)
      aSeq.add (_typedSimpleValue (sItem, ePrim));
    return XdmSequence.of (aSeq);
  }

  /**
   * go-xslt <code>typedValueOf</code>: the typed <code>$value</code> of a
   * simple-typed lexical - an atomic, a sequence of typed items for a list, or
   * the accepting member's reading for a union.
   */
  @NonNull
  private static XdmSequence _typedValueOf (@NonNull final XsdSimpleType aType, @NonNull final String sRaw)
  {
    final String sValue = XsdWhiteSpace.apply (aType.effectiveWhiteSpace (), sRaw);
    switch (aType.m_eVariety)
    {
      case LIST:
        return typedListValue (XsdLexHelper.xmlFields (sValue), aType.m_aItem == null ? null : aType.m_aItem.resolvePrim ());
      case UNION:
      {
        final XsdSimpleType aMember = XsdSimpleTypeValidator.firstMemberRaw (aType, sValue);
        return aMember != null ? _typedValueOf (aMember, sValue) : XdmSequence.of (XdmAtomic.untyped (sValue));
      }
      default:
        return typedAtomicValue (sValue, aType.resolvePrim ());
    }
  }

  /**
   * go-xslt <code>checkAsserts</code>: evaluate a complex type's assertions
   * against an element (1.1 only).
   *
   * @param aRun
   *        The validation run.
   * @param aType
   *        The governing complex type.
   * @param aElement
   *        The element.
   * @param aAsserts
   *        The effective assertions of the type.
   * @throws XsdInvalidException
   *         on the first failed assertion, unless the run collects all errors
   */
  static void checkAsserts (@NonNull final XsdValidationRun aRun,
                            @NonNull final XsdComplexType aType,
                            @NonNull final IXdmNode aElement,
                            @NonNull final ICommonsList <XsdAssertion> aAsserts)
  {
    // $value is bound in element-level assertions (3.13.4.1): the typed simple
    // content when the content type is simple, and the empty sequence
    // otherwise
    XdmSequence aValue = XdmSequence.EMPTY;
    if (aType.m_eKind == EXsdContentKind.SIMPLE && aType.m_aSimpleType != null)
      aValue = _typedValueOf (aType.m_aSimpleType, XsdInstanceValidator.elementText (aElement));
    final IXdmNode aTree = _assertTree (aRun, aElement);
    for (final XsdAssertion aAssert : aAsserts)
    {
      final DynamicContext aCtx = aAssert.m_aXPath.createDynamicContext ()
                                                  .withFocus (aTree, 1, 1)
                                                  .withVariable (VAR_VALUE, aValue);
      if (!_isTrue (aAssert.m_aXPath, aCtx))
        XsdInstanceValidator.soft (aRun,
                                   new XsdInvalidException (XsdError.of ("cvc-assertion",
                                                                         "assertion failed on " +
                                                                                           XsdInstanceValidator.nameOf (aElement))
                                                                    .withLocation (aElement.getLine (),
                                                                                   aElement.getColumn ()),
                                                            aAssert.m_aMsgs),
                                   aElement);
    }
  }

  /**
   * go-xslt <code>selectAlternativeType</code>: conditional type assignment.
   *
   * @param aRun
   *        The validation run.
   * @param aDecl
   *        The element declaration with its alternatives.
   * @param aElement
   *        The element.
   * @return The type selected by the alternatives, or <code>null</code> to keep
   *         the declared type.
   */
  @Nullable
  static IXsdType selectAlternativeType (@NonNull final XsdValidationRun aRun,
                                         @NonNull final XsdElementDecl aDecl,
                                         @NonNull final IXdmNode aElement)
  {
    // The test sees the element's attributes, inherited ones included
    // (3.4.4.1), but not its children (3.12.4: the [children] of the copy are
    // empty). The parent is not kept: the in-scope namespaces are copied, and
    // a test cannot legitimately depend on anything else outside the element
    final IXdmNode aCopy = IXdmNode.createElement (aElement.getName ());
    aCopy.setLocation (aElement.getLine (), aElement.getColumn ());
    for (final Map.Entry <String, String> aEntry : aElement.getInScopeNamespaces ().entrySet ())
      if (!"xml".equals (aEntry.getKey ()))
        aCopy.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    for (final IXdmNode aAttr : aElement.getAllAttributes ())
      aCopy.addAttribute (IXdmNode.createAttribute (aAttr.getName (), aAttr.getValue ()));
    for (final IXdmNode aAttr : _inheritedAttrs (aRun, aElement))
      aCopy.addAttribute (IXdmNode.createAttribute (aAttr.getName (), aAttr.getValue ()));
    for (final XsdTypeAlternative aAlt : aDecl.m_aAlternatives)
    {
      if (aAlt.m_aXPath == null)
      {
        // The default alternative
        return aAlt.m_aType;
      }
      final DynamicContext aCtx = aAlt.m_aXPath.createDynamicContext ().withFocus (aCopy, 1, 1);
      if (_isTrue (aAlt.m_aXPath, aCtx))
        return aAlt.m_aType;
    }
    return null;
  }
}
