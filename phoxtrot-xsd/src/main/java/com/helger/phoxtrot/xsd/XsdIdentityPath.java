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
package com.helger.phoxtrot.xsd;

import java.util.ArrayDeque;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ops.XsdLexical;

/**
 * A selector or field of an identity constraint in the restricted XPath
 * subset of XSD 1.1 Part 1 section 3.11.6 (<code>(.//)? Step ('/' Step)*</code>
 * alternatives joined by <code>|</code>, a step being <code>.</code>, a child
 * name test or, as a field's last step, an attribute name test), evaluated
 * directly on the tree instead of by the XPath engine. The result is the node
 * sequence the XPath expression gives: in document order without duplicates.
 * A direct walk avoids the document-wide name index the XPath engine builds
 * for its early exit on names the document does not contain, which is the
 * dominant cost when a constraint is evaluated once on a large document.
 * <p>
 * Names are resolved when the path is parsed, exactly as the compiled XPath
 * resolves them: a prefix against the namespaces in scope at the
 * xs:selector or xs:field element, an unprefixed element name against the
 * xpathDefaultNamespace, an unprefixed attribute name to no namespace.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdIdentityPath
{
  private enum EStepKind
  {
    /** <code>.</code>: self::node(). */
    SELF,
    /** A child name test. */
    ELEMENT,
    /** An attribute name test. */
    ATTRIBUTE
  }

  /**
   * One step: its kind and the expanded name it tests, <code>null</code>
   * meaning any namespace or any local name.
   */
  private static final class Step
  {
    private final EStepKind m_eKind;
    private final String m_sNamespaceUri;
    private final String m_sLocalName;

    Step (@NonNull final EStepKind eKind, @Nullable final String sNamespaceUri, @Nullable final String sLocalName)
    {
      m_eKind = eKind;
      m_sNamespaceUri = sNamespaceUri;
      m_sLocalName = sLocalName;
    }

    /**
     * @return <code>true</code> if the name of the element or attribute node
     *         passes the name test of this step.
     */
    boolean matchesName (@NonNull final IXdmNode aNode)
    {
      final XdmQName aName = aNode.getName ();
      if (aName == null)
        return false;
      if (m_sNamespaceUri != null && !m_sNamespaceUri.equals (aName.namespaceUri ()))
        return false;
      return m_sLocalName == null || m_sLocalName.equals (aName.localName ());
    }
  }

  /** One alternative of the union: an optional <code>.//</code> and its steps. */
  private static final class Alternative
  {
    private final boolean m_bDescendant;
    private final ICommonsList <Step> m_aSteps;
    private final boolean m_bOrdered;

    Alternative (final boolean bDescendant, @NonNull final ICommonsList <Step> aSteps)
    {
      m_bDescendant = bDescendant;
      m_aSteps = aSteps;
      // Ordered: the nodes come in document order; flat: no node of the set is
      // an ancestor of another. A leading .// gives an ordered set that is not
      // flat. A child step from a flat ordered set keeps both properties, a
      // child step from a set that is not flat loses the order. The attributes
      // of a node precede its descendants in document order, so an attribute
      // step keeps the order and gives a flat set
      boolean bOrdered = true;
      boolean bFlat = !bDescendant;
      for (int i = bDescendant && aSteps.getFirstOrNull ().m_eKind == EStepKind.ELEMENT ? 1 : 0; i < aSteps.size (); i++)
        switch (aSteps.get (i).m_eKind)
        {
          case ELEMENT:
            bOrdered = bOrdered && bFlat;
            break;
          case ATTRIBUTE:
            bFlat = true;
            break;
          default:
            break;
        }
      m_bOrdered = bOrdered;
    }
  }

  private final ICommonsList <Alternative> m_aAlternatives;

  private XsdIdentityPath (@NonNull final ICommonsList <Alternative> aAlternatives)
  {
    m_aAlternatives = aAlternatives;
  }

  /**
   * Parse a name test (<code>*</code>, <code>p:*</code>, an NCName or a
   * QName) into a step.
   *
   * @return The step, or <code>null</code> if the name test is not of these
   *         forms or its prefix is not bound at the host element.
   */
  @Nullable
  private static Step _parseNameTest (@NonNull final EStepKind eKind,
                                      @NonNull final String sTest,
                                      @NonNull final IXdmNode aHost,
                                      @NonNull final String sDefaultNS)
  {
    if ("*".equals (sTest))
      return new Step (eKind, null, null);
    final int nColon = sTest.indexOf (':');
    if (nColon < 0)
    {
      if (!XsdLexical.isNCName (sTest))
        return null;
      return new Step (eKind, eKind == EStepKind.ELEMENT ? sDefaultNS : "", sTest);
    }
    final String sPrefix = sTest.substring (0, nColon);
    final String sLocal = sTest.substring (nColon + 1);
    if (!XsdLexical.isNCName (sPrefix))
      return null;
    final String sNamespaceUri = XsdNodes.lookupPrefix (aHost, sPrefix);
    if (sNamespaceUri == null)
      return null;
    if ("*".equals (sLocal))
      return new Step (eKind, sNamespaceUri, null);
    if (!XsdLexical.isNCName (sLocal))
      return null;
    return new Step (eKind, sNamespaceUri, sLocal);
  }

  /**
   * Parse one step: <code>.</code>, a name test with an optional
   * <code>child::</code>, or <code>@</code> / <code>attribute::</code> with a
   * name test.
   *
   * @return The step, or <code>null</code> for any other form.
   */
  @Nullable
  private static Step _parseStep (@NonNull final String sStep,
                                  @NonNull final IXdmNode aHost,
                                  @NonNull final String sDefaultNS)
  {
    if (".".equals (sStep))
      return new Step (EStepKind.SELF, null, null);
    if (sStep.startsWith ("@"))
      return _parseNameTest (EStepKind.ATTRIBUTE, sStep.substring (1), aHost, sDefaultNS);
    if (sStep.startsWith ("attribute::"))
      return _parseNameTest (EStepKind.ATTRIBUTE, sStep.substring ("attribute::".length ()), aHost, sDefaultNS);
    if (sStep.startsWith ("child::"))
      return _parseNameTest (EStepKind.ELEMENT, sStep.substring ("child::".length ()), aHost, sDefaultNS);
    if (sStep.contains ("::"))
      return null;
    return _parseNameTest (EStepKind.ELEMENT, sStep, aHost, sDefaultNS);
  }

  /**
   * Parse a selector or field that passed
   * {@link XsdIdentityCompiler#validateIdentityXPath(String, boolean, IXdmNode)}.
   * All whitespace is dropped first (no token of the subset contains any), the
   * expression is split at <code>|</code>, each alternative at <code>/</code>
   * after an optional leading <code>.//</code>.
   *
   * @param sExpr
   *        The <code>xpath</code> attribute value.
   * @param aHost
   *        The xs:selector or xs:field element, for prefix resolution.
   * @param sDefaultNS
   *        The resolved xpathDefaultNamespace, empty for none.
   * @return The parsed path, or <code>null</code> if the expression has a form
   *         this class does not handle (an attribute step before the last
   *         step, an empty step, an unbound prefix); the caller then evaluates
   *         the compiled XPath.
   */
  @Nullable
  static XsdIdentityPath parse (@NonNull final String sExpr,
                                @NonNull final IXdmNode aHost,
                                @NonNull final String sDefaultNS)
  {
    final String sCollapsed = String.join ("", XsdLexHelper.xmlFields (sExpr));
    final ICommonsList <Alternative> aAlternatives = new CommonsArrayList <> ();
    for (final String sPath : sCollapsed.split ("\\|", -1))
    {
      final boolean bDescendant = sPath.startsWith (".//");
      final String sRest = bDescendant ? sPath.substring (3) : sPath;
      if (sRest.isEmpty ())
        return null;
      final String [] aParts = sRest.split ("/", -1);
      final ICommonsList <Step> aSteps = new CommonsArrayList <> ();
      for (int i = 0; i < aParts.length; i++)
      {
        final Step aStep = _parseStep (aParts[i], aHost, sDefaultNS);
        if (aStep == null)
          return null;
        if (aStep.m_eKind == EStepKind.ATTRIBUTE && i < aParts.length - 1)
          return null;
        aSteps.add (aStep);
      }
      aAlternatives.add (new Alternative (bDescendant, aSteps));
    }
    return aAlternatives.isEmpty () ? null : new XsdIdentityPath (aAlternatives);
  }

  /**
   * Collect the descendants of a node (all kinds except attributes and
   * namespaces) in document order, iteratively; with <code>bSelf</code> the
   * node itself comes first. With <code>aFilter</code> only elements passing
   * its name test are collected.
   */
  private static void _collectDescendants (@NonNull final IXdmNode aNode,
                                           final boolean bSelf,
                                           @Nullable final Step aFilter,
                                           @NonNull final ICommonsList <IXdmNode> aOut)
  {
    if (bSelf)
      aOut.add (aNode);
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    for (int i = aNode.getChildCount () - 1; i >= 0; --i)
      aStack.push (aNode.getChildAtIndex (i));
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if (aFilter == null || (aCur.isElement () && aFilter.matchesName (aCur)))
        aOut.add (aCur);
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
  }

  /**
   * Evaluate one alternative from a context node.
   *
   * @return The selected nodes, free of duplicates, in document order if the
   *         alternative is ordered.
   */
  @NonNull
  private static ICommonsList <IXdmNode> _evaluateAlternative (@NonNull final Alternative aAlt,
                                                              @NonNull final IXdmNode aContext)
  {
    ICommonsList <IXdmNode> aCur = new CommonsArrayList <> ();
    int nFirst = 0;
    if (aAlt.m_bDescendant)
    {
      final Step aFirst = aAlt.m_aSteps.getFirstOrNull ();
      if (aFirst.m_eKind == EStepKind.ELEMENT)
      {
        // descendant-or-self::node()/child::N is descendant::N
        _collectDescendants (aContext, false, aFirst, aCur);
        nFirst = 1;
      }
      else
        _collectDescendants (aContext, true, null, aCur);
    }
    else
      aCur.add (aContext);
    for (int nStep = nFirst; nStep < aAlt.m_aSteps.size (); nStep++)
    {
      final Step aStep = aAlt.m_aSteps.get (nStep);
      if (aStep.m_eKind == EStepKind.SELF)
        continue;
      final ICommonsList <IXdmNode> aNext = new CommonsArrayList <> ();
      if (aStep.m_eKind == EStepKind.ELEMENT)
      {
        for (final IXdmNode aNode : aCur)
        {
          final int nCount = aNode.getChildCount ();
          for (int i = 0; i < nCount; ++i)
          {
            final IXdmNode aChild = aNode.getChildAtIndex (i);
            if (aChild.isElement () && aStep.matchesName (aChild))
              aNext.add (aChild);
          }
        }
      }
      else
      {
        for (final IXdmNode aNode : aCur)
        {
          final int nCount = aNode.getAttributeCount ();
          for (int i = 0; i < nCount; ++i)
          {
            final IXdmNode aAttr = aNode.getAttributeAtIndex (i);
            if (aStep.matchesName (aAttr))
              aNext.add (aAttr);
          }
        }
      }
      aCur = aNext;
    }
    return aCur;
  }

  /**
   * Evaluate the path with a node as context item.
   *
   * @param aContext
   *        The context node (the scope element for a selector, a target node
   *        for a field).
   * @return The selected nodes in document order without duplicates, as the
   *         equivalent XPath expression returns them.
   */
  @NonNull
  ICommonsList <IXdmNode> evaluate (@NonNull final IXdmNode aContext)
  {
    final ICommonsList <IXdmNode> ret;
    if (m_aAlternatives.size () == 1)
    {
      final Alternative aAlt = m_aAlternatives.getFirstOrNull ();
      ret = _evaluateAlternative (aAlt, aContext);
      if (aAlt.m_bOrdered)
        return ret;
    }
    else
    {
      ret = new CommonsArrayList <> ();
      for (final Alternative aAlt : m_aAlternatives)
        ret.addAll (_evaluateAlternative (aAlt, aContext));
    }
    // A union or a set that lost its order: sort and drop duplicates
    ret.sort (IXdmNode::compareDocumentOrder);
    final ICommonsList <IXdmNode> aUnique = new CommonsArrayList <> (ret.size ());
    IXdmNode aPrev = null;
    for (final IXdmNode aNode : ret)
      if (aNode != aPrev)
      {
        aUnique.add (aNode);
        aPrev = aNode;
      }
    return aUnique;
  }
}
