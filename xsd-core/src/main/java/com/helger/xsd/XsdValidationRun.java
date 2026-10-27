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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.XdmNode;

/**
 * The per-call state of one instance validation (go-xslt keeps it in a
 * per-call shallow copy of <code>Schema</code>: <code>ids</code>,
 * <code>typeCache</code>, <code>childModels</code>, <code>unassessed</code>,
 * <code>unparsed</code> and the node annotation maps). The {@link XsdSchema}
 * itself is never changed by a validation. Nodes and components compare by
 * identity, so the maps are identity based like go-xslt's pointer maps.
 *
 * @author Philip Helger
 */
final class XsdValidationRun
{
  /**
   * How deep the validation walk follows nested instance elements on the
   * calling thread. Every level costs a handful of Java frames (up to 3.5
   * kilobytes while interpreted or compiled with deep inlining, which is what
   * bounds this to 100 levels on a 512 KB stack) and go-xslt relies on its growable stack
   * here, so a deeper document is validated on a thread with a big stack
   * ({@link #MAX_DEPTH_BIG_STACK}, see
   * {@link XsdInstanceValidator#validateRoot}); a deeper one still ends in
   * {@link XsdUnsupportedException}, never in a stack overflow.
   */
  static final int MAX_DEPTH = 100;

  /** The depth bound on a thread with a big stack (the parser's own limit is 10,000). */
  static final int MAX_DEPTH_BIG_STACK = 20_000;

  /** How far {@link #noteIDValues} follows a union that contains itself. */
  private static final int MAX_ID_UNION_DEPTH = 64;

  /**
   * The static, per-complex-type part of the child validation (go-xslt
   * <code>childModel</code>): what would otherwise be recomputed for every
   * element of the instance.
   */
  static final class ChildModel
  {
    final XsdParticle m_aParticle;
    final XsdOpenContent m_aOpenContent;
    final ICommonsMap <XsdName, XsdElementDecl> m_aDecls = new CommonsLinkedHashMap <> ();
    final ICommonsMap <XsdName, ICommonsList <String>> m_aFixedsByName = new CommonsHashMap <> ();
    final ICommonsList <XsdWildcard> m_aSkipWc = new CommonsArrayList <> ();
    final ICommonsList <XsdWildcard> m_aLaxWc = new CommonsArrayList <> ();
    final ICommonsList <XsdWildcard> m_aStrictWc = new CommonsArrayList <> ();

    ChildModel (@Nullable final XsdParticle aParticle, @Nullable final XsdOpenContent aOpenContent)
    {
      m_aParticle = aParticle;
      m_aOpenContent = aOpenContent;
    }

    /** Put a wildcard into the list of its processContents. */
    void addWildcard (@NonNull final XsdWildcard aWildcard)
    {
      switch (aWildcard.m_sProcess)
      {
        case XsdWildcard.PROCESS_SKIP:
          m_aSkipWc.add (aWildcard);
          break;
        case XsdWildcard.PROCESS_LAX:
          m_aLaxWc.add (aWildcard);
          break;
        default:
          m_aStrictWc.add (aWildcard);
          break;
      }
    }
  }

  /** The element and the node an ID value is bound to (go-xslt <code>idBinding</code>). */
  private record IdBinding (XdmNode el, XdmNode src)
  {}

  /** An attribute a value constraint supplied (go-xslt <code>defaultAttr</code>). */
  record DefaultAttr (@NonNull XsdName name, @NonNull String value, @Nullable IXsdType type)
  {}

  private final XsdSchema m_aSchema;
  private final XdmNode m_aDocument;

  private final ICommonsMap <XsdComplexType, ChildModel> m_aChildModels = new CommonsHashMap <> ();
  private final ICommonsMap <String, IdBinding> m_aBound = new CommonsHashMap <> ();
  private final ICommonsList <String> m_aRefs = new CommonsArrayList <> ();
  private final ICommonsMap <XdmNode, IXsdType> m_aTypeCache = new CommonsHashMap <> ();
  private final ICommonsSet <XdmNode> m_aUnassessed = new CommonsHashSet <> ();

  private XsdErrorCollector m_aCollector;
  private boolean m_bElementScope;
  private boolean m_bLenientEntities;
  private int m_nDepth;
  private int m_nMaxDepth = MAX_DEPTH;

  /*
   * The node annotation maps of phase V6 (ValidateNode annotates a caller's
   * tree). null while nothing records them, which is the case on the string
   * Validate path.
   */
  private ICommonsMap <XdmNode, IXsdType> m_aNodeTypes;
  private ICommonsSet <XdmNode> m_aNodeNilled;
  private ICommonsMap <XdmNode, ICommonsList <DefaultAttr>> m_aNodeDefaults;

  XsdValidationRun (@NonNull final XsdSchema aSchema)
  {
    this (aSchema, null);
  }

  /**
   * @param aSchema
   *        The schema validated against.
   * @param aDocument
   *        The document node of the instance (for its unparsed entities);
   *        may be <code>null</code>.
   */
  XsdValidationRun (@NonNull final XsdSchema aSchema, @Nullable final XdmNode aDocument)
  {
    m_aSchema = aSchema;
    m_aDocument = aDocument;
  }

  /**
   * Collect all errors instead of stopping at the first one (go-xslt
   * <code>Schema.coll</code>, set on the string <code>Validate</code> path
   * only; the in-place path of the bridge keeps its first-error behaviour).
   *
   * @param aCollector
   *        The collector. May not be <code>null</code>.
   */
  void setCollector (@NonNull final XsdErrorCollector aCollector)
  {
    m_aCollector = aCollector;
  }

  /** @return The collector; <code>null</code> when the first error ends the validation. */
  @Nullable
  XsdErrorCollector getCollector ()
  {
    return m_aCollector;
  }

  /** @return <code>true</code> if all errors are collected. */
  boolean isCollecting ()
  {
    return m_aCollector != null;
  }

  /** @return The number of errors collected so far (go-xslt <code>collected</code>); 0 when not collecting. */
  int collected ()
  {
    return m_aCollector == null ? 0 : m_aCollector.size ();
  }

  /**
   * Withdraw the errors recorded after a {@link #collected()} reading (go-xslt
   * <code>uncollect</code>; see the skip-wildcard excuse of the children
   * check).
   *
   * @param nCollected
   *        The reading.
   */
  void uncollect (final int nCollected)
  {
    if (m_aCollector != null)
      m_aCollector.truncate (nCollected);
  }

  /** @return The schema validated against. */
  @NonNull
  XsdSchema getSchema ()
  {
    return m_aSchema;
  }

  /** @return <code>true</code> if the schema is validated as XSD 1.1. */
  boolean isXsd11 ()
  {
    return m_aSchema.isXsd11 ();
  }

  /**
   * go-xslt <code>idTable.elementScope</code>: suppress the cvc-id.2
   * uniqueness conflict (ID/IDREF are document-level constraints).
   *
   * @param bElementScope
   *        <code>true</code> if the validation root is an element.
   */
  void setElementScope (final boolean bElementScope)
  {
    m_bElementScope = bElementScope;
  }

  /**
   * go-xslt <code>lenientEntities</code>: drop the XSD 1.1 rule that an
   * ENTITY value must name an unparsed entity.
   *
   * @param bLenientEntities
   *        <code>true</code> to drop it.
   */
  void setLenientEntities (final boolean bLenientEntities)
  {
    m_bLenientEntities = bLenientEntities;
  }

  /** Enable recording of the type annotations of validated nodes. */
  void enableNodeTypes ()
  {
    if (m_aNodeTypes == null)
      m_aNodeTypes = new CommonsHashMap <> ();
  }

  /** Enable recording of the [nilled] property of validated elements. */
  void enableNodeNilled ()
  {
    if (m_aNodeNilled == null)
      m_aNodeNilled = new CommonsHashSet <> ();
  }

  /** Enable recording of the attributes value constraints supplied. */
  void enableNodeDefaults ()
  {
    if (m_aNodeDefaults == null)
      m_aNodeDefaults = new CommonsHashMap <> ();
  }

  /** @return The recorded type annotations; <code>null</code> if not enabled. */
  @Nullable
  ICommonsMap <XdmNode, IXsdType> getNodeTypes ()
  {
    return m_aNodeTypes;
  }

  /** @return The recorded nilled elements; <code>null</code> if not enabled. */
  @Nullable
  ICommonsSet <XdmNode> getNodeNilled ()
  {
    return m_aNodeNilled;
  }

  /** @return The recorded supplied attributes; <code>null</code> if not enabled. */
  @Nullable
  ICommonsMap <XdmNode, ICommonsList <DefaultAttr>> getNodeDefaults ()
  {
    return m_aNodeDefaults;
  }

  /**
   * @param nMaxDepth
   *        The number of nested instance elements the walk may follow on the
   *        thread it runs on.
   */
  void setMaxDepth (final int nMaxDepth)
  {
    m_nMaxDepth = nMaxDepth;
  }

  /**
   * Enter one more level of nested instance elements.
   *
   * @throws XsdUnsupportedException
   *         beyond the maximum depth
   */
  void enterElement ()
  {
    if (++m_nDepth > m_nMaxDepth)
      throw new XsdUnsupportedException ("instance elements nested deeper than " + m_nMaxDepth + " levels");
  }

  /** Leave a level entered with {@link #enterElement()}. */
  void leaveElement ()
  {
    m_nDepth--;
  }

  /** go-xslt <code>noteNodeType</code>. */
  void noteNodeType (@Nullable final XdmNode aNode, @Nullable final IXsdType aType)
  {
    if (m_aNodeTypes == null || aNode == null || aType == null)
      return;
    m_aNodeTypes.put (aNode, aType);
  }

  /** go-xslt <code>noteNodeNilled</code>. */
  void noteNodeNilled (@Nullable final XdmNode aNode)
  {
    if (m_aNodeNilled == null || aNode == null)
      return;
    m_aNodeNilled.add (aNode);
  }

  /** go-xslt <code>noteDefaultAttr</code>. */
  void noteDefaultAttr (@Nullable final XdmNode aElement,
                        @NonNull final XsdName aName,
                        @NonNull final String sValue,
                        @Nullable final IXsdType aType)
  {
    if (m_aNodeDefaults == null || aElement == null)
      return;
    m_aNodeDefaults.computeIfAbsent (aElement, k -> new CommonsArrayList <> ())
                   .add (new DefaultAttr (aName, sValue, aType));
  }

  /**
   * go-xslt <code>annotateAssertType</code>: records the type a simple-typed
   * node validated against for the typed view of later XSD 1.1 assertion
   * evaluations. Nothing records it before phase V8 (no assertion can be
   * compiled yet).
   *
   * @param aNode
   *        The element or attribute.
   * @param aType
   *        The type its content validated against.
   */
  void annotateAssertType (@Nullable final XdmNode aNode, @Nullable final XsdSimpleType aType)
  {
    // V8
  }

  /**
   * go-xslt <code>markUnassessed</code>: a subtree absorbed by a skip wildcard
   * (identity constraints must not select into it; 1.1 only).
   *
   * @param aElement
   *        The root of the subtree.
   */
  void markUnassessed (@NonNull final XdmNode aElement)
  {
    if (isXsd11 ())
      m_aUnassessed.add (aElement);
  }

  /**
   * go-xslt <code>idTable.bindID</code>: record that an element carries an ID
   * with the given value, sourced from a node (the attribute node, or the
   * element itself for content IDs). The same source repeating a value is
   * never a conflict. Beyond that, 1.1 tolerates two bindings of one value on
   * the same element; 1.0 keeps strict document-wide uniqueness.
   */
  private void _bindID (@NonNull final String sValue, @NonNull final XdmNode aElement, @NonNull final XdmNode aSource)
  {
    final IdBinding aPrev = m_aBound.get (sValue);
    if (aPrev != null && aPrev.src () != aSource && !m_bElementScope)
      if (!isXsd11 () || aPrev.el () != aElement)
        throw XsdCompiler.invalid ("cvc-id.2", "duplicate ID value " + XsdLexHelper.quote (sValue));
    m_aBound.put (sValue, new IdBinding (aElement, aSource));
  }

  /**
   * go-xslt <code>idTable.check</code>: resolve the collected IDREF values
   * against the bound IDs.
   *
   * @throws XsdInvalidException
   *         if an IDREF has no ID
   */
  void checkIDRefs ()
  {
    for (final String sRef : m_aRefs)
      if (!m_aBound.containsKey (sRef))
        throw XsdCompiler.invalid ("cvc-id.1", "IDREF " + XsdLexHelper.quote (sRef) + " does not match any ID");
  }

  private boolean _isUnparsedEntity (@NonNull final String sName)
  {
    return m_aDocument != null && m_aDocument.getUnparsedEntity (sName) != null;
  }

  private void _noteIDValues (@Nullable final XsdSimpleType aType,
                              @NonNull final String sValue,
                              @Nullable final XdmNode aBind,
                              @NonNull final XdmNode aSource,
                              final int nDepth)
  {
    if (aType == null || nDepth > MAX_ID_UNION_DEPTH)
      return;
    switch (aType.m_eVariety)
    {
      case ATOMIC:
      {
        final EAtomicType ePrim = aType.resolvePrim ();
        if (ePrim != EAtomicType.ID && ePrim != EAtomicType.IDREF && ePrim != EAtomicType.ENTITY)
          return; // (and spare collapsing a value that may be megabytes long)
        final String sV = XsdWhiteSpace.apply (XsdWhiteSpace.COLLAPSE, sValue);
        if (ePrim == EAtomicType.ID)
        {
          if (aBind != null) // root-content ID: nothing to bind to
            _bindID (sV, aBind, aSource);
        }
        else
          if (ePrim == EAtomicType.IDREF)
            m_aRefs.add (sV);
          else
          {
            // ENTITY: 1.1 enforces that the value names an unparsed entity
            // declared in the instance's DTD
            if (isXsd11 () && !m_bLenientEntities && !_isUnparsedEntity (sV))
              throw XsdCompiler.invalid ("cvc-datatype-valid",
                                         XsdLexHelper.quote (sV) + " does not name an unparsed entity");
          }
        return;
      }
      case LIST:
      {
        if (aType.m_aItem == null)
          return;
        // Recurse per item token: the item type may itself be a union whose
        // members carry ID/IDREF
        for (final String sToken : XsdLexHelper.xmlFields (sValue))
          _noteIDValues (aType.m_aItem, sToken, aBind, aSource, nDepth + 1);
        return;
      }
      default:
      {
        // The value belongs to the first member type it validates against;
        // only that member decides whether it is an ID or an IDREF
        for (final XsdSimpleType aMember : aType.m_aMembers)
        {
          boolean bValid = true;
          try
          {
            XsdSimpleTypeValidator.validate (aMember, sValue);
          }
          catch (final XsdInvalidException ex)
          {
            bValid = false;
          }
          if (bValid)
          {
            _noteIDValues (aMember, sValue, aBind, aSource, nDepth + 1);
            return;
          }
        }
      }
    }
  }

  /**
   * go-xslt <code>noteIDValues</code>: record the ID / IDREF values a
   * validated simple value carries, so the document-level ID/IDREF binding can
   * be checked once the whole instance has been assessed.
   *
   * @param aType
   *        The type the value validated against.
   * @param sValue
   *        The raw value.
   * @param aBind
   *        The element an ID binds to: the element carrying the attribute, or
   *        for an ID in element content that element's parent (so several ID
   *        children of one element may share a value); <code>null</code> for
   *        none.
   * @param aSource
   *        The node the value sits on.
   * @throws XsdInvalidException
   *         on a duplicate ID or an ENTITY that is not declared
   */
  void noteIDValues (@Nullable final XsdSimpleType aType,
                     @NonNull final String sValue,
                     @Nullable final XdmNode aBind,
                     @NonNull final XdmNode aSource)
  {
    _noteIDValues (aType, sValue, aBind, aSource, 0);
  }

  /**
   * Collect the fixed values per element name and the wildcards by
   * processContents of a content model (go-xslt
   * <code>collectFixedsByName</code> and <code>collectWildcards</code>),
   * iteratively; a group reached through several references is visited once.
   */
  private static void _collectModel (@NonNull final ChildModel aModel)
  {
    if (aModel.m_aParticle == null)
      return;
    final ICommonsSet <XsdModelGroup> aSeen = new CommonsHashSet <> ();
    final ICommonsList <XsdParticle> aStack = new CommonsArrayList <> ();
    aStack.add (aModel.m_aParticle);
    while (aStack.isNotEmpty ())
    {
      final XsdParticle aParticle = aStack.removeLast ();
      if (aParticle.m_aTerm instanceof final XsdElementDecl aDecl)
      {
        if (aDecl.m_bHasFixed)
          aModel.m_aFixedsByName.computeIfAbsent (aDecl.m_aName, k -> new CommonsArrayList <> ()).add (aDecl.m_sFixed);
      }
      else
        if (aParticle.m_aTerm instanceof final XsdWildcard aWC)
          aModel.addWildcard (aWC);
        else
          if (aParticle.m_aTerm instanceof final XsdModelGroup aMG && aSeen.add (aMG))
            for (int i = aMG.m_aParticles.size () - 1; i >= 0; i--)
              aStack.add (aMG.m_aParticles.get (i));
    }
  }

  /**
   * go-xslt <code>childModelOf</code>.
   *
   * @param aType
   *        The complex type.
   * @return Its child model; cached per run.
   */
  @NonNull
  ChildModel childModelOf (@NonNull final XsdComplexType aType)
  {
    final ChildModel aCached = m_aChildModels.get (aType);
    if (aCached != null)
      return aCached;
    final ChildModel ret = new ChildModel (m_aSchema.effectiveParticle (aType),
                                           m_aSchema.effectiveOpenContent (aType));
    XsdSchema.contentDecls (ret.m_aParticle, ret.m_aDecls);
    _collectModel (ret);
    // Children admitted by the open-content wildcard are assessed per its
    // processContents, exactly like model wildcards
    if (ret.m_aOpenContent != null && ret.m_aOpenContent.m_aWildcard != null)
      ret.addWildcard (ret.m_aOpenContent.m_aWildcard);
    m_aChildModels.put (aType, ret);
    return ret;
  }

  /**
   * go-xslt <code>elementTypeOf</code>: resolve the type governing an element
   * node, walking the ancestor chain down from the document element
   * (iteratively; the result is cached per node).
   *
   * @param aElement
   *        The element.
   * @return The type, or <code>null</code> if none can be determined.
   */
  @Nullable
  IXsdType elementTypeOf (@NonNull final XdmNode aElement)
  {
    if (m_aTypeCache.containsKey (aElement))
      return m_aTypeCache.get (aElement);
    // The uncached ancestors up to the first cached one, innermost first
    final ICommonsList <XdmNode> aChain = new CommonsArrayList <> ();
    XdmNode aCur = aElement;
    while (aCur != null && aCur.isElement () && !m_aTypeCache.containsKey (aCur))
    {
      aChain.add (aCur);
      aCur = aCur.getParent ();
    }
    for (int i = aChain.size () - 1; i >= 0; i--)
    {
      final XdmNode aEl = aChain.get (i);
      final XsdName aName = XsdInstanceValidator.nameOf (aEl);
      XsdElementDecl aDecl = null;
      final XdmNode aParent = aEl.getParent ();
      if (aParent != null && aParent.isElement () && m_aTypeCache.get (aParent) instanceof final XsdComplexType aPT)
        aDecl = childModelOf (aPT).m_aDecls.get (aName);
      if (aDecl == null)
      {
        // The document element, a substitution-group member, or a child
        // admitted by a lax wildcard (whose own global declaration governs,
        // as in validateChildren) - and the same fallback keeps the walk
        // going when an ancestor is itself unresolvable
        aDecl = m_aSchema.m_aElements.get (aName);
      }
      IXsdType aType = aDecl == null ? null : aDecl.m_aType;
      // An xsi:type on the instance overrides the declared type
      final String sXsiType = XsdNodes.attrNS (aEl, XsdConstants.NS_XSI, "type");
      if (sXsiType != null)
        try
        {
          aType = XsdInstanceValidator.resolveInstanceType (this, aEl, sXsiType);
        }
        catch (final XsdInvalidException ex)
        {
          // keeps the declared type
        }
      m_aTypeCache.put (aEl, aType);
    }
    return m_aTypeCache.get (aElement);
  }

  private boolean _icNodeVisible (@NonNull final XdmNode aNode)
  {
    for (XdmNode aCur = aNode; aCur != null && aCur.isElement (); aCur = aCur.getParent ())
    {
      if (m_aUnassessed.contains (aCur))
        return false;
      final XdmNode aPar = aCur.getParent ();
      if (aPar == null || !aPar.isElement ())
        break;
      if (!(elementTypeOf (aPar) instanceof final XsdComplexType aPT))
        continue;
      final XsdName aName = XsdInstanceValidator.nameOf (aCur);
      final ChildModel aModel = childModelOf (aPT);
      if (aModel.m_aDecls.containsKey (aName))
        continue;
      // Mirror validateChildren's attribution order: a skip wildcard absorbs
      // the child before the global-declaration fallback is consulted
      for (final XsdWildcard aWC : aModel.m_aSkipWc)
        if (aWC.matches (aName))
          return false;
    }
    return true;
  }

  /**
   * go-xslt <code>icFilter</code>: drop identity-constraint-selected nodes
   * that sit inside a skip-absorbed subtree (1.1 section 3.11.4: only
   * strictly-assessed nodes reach the target and qualified node sets).
   *
   * @param aNodes
   *        The selected nodes.
   * @return The nodes that count.
   */
  @NonNull
  ICommonsList <XdmNode> icFilter (@NonNull final ICommonsList <XdmNode> aNodes)
  {
    if (!isXsd11 ())
      return aNodes;
    final ICommonsList <XdmNode> ret = new CommonsArrayList <> ();
    for (final XdmNode aNode : aNodes)
      if (_icNodeVisible (aNode))
        ret.add (aNode);
    return ret;
  }
}
