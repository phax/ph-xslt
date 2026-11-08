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
package com.helger.phoxtrot.xslt.runtime;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.EIDKind;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmDocumentNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmStreamReader;
import com.helger.phoxtrot.xdm.XdmStreamUnsupportedException;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.collation.CollationRegistry;
import com.helger.phoxtrot.xpath.collation.ICollation;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.IResourceResolver;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.eval.NodeSorter;
import com.helger.phoxtrot.xpath.eval.SequenceTypeMatcher;
import com.helger.phoxtrot.xpath.fn.FnAccessor;
import com.helger.phoxtrot.xpath.ops.AtomicComparator;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.pattern.PatternFinalNames;
import com.helger.phoxtrot.xpath.value.XdmArray;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.XsltTransformResult;
import com.helger.phoxtrot.xslt.compile.StylesheetCompiler;
import com.helger.phoxtrot.xslt.model.AttributeSet;
import com.helger.phoxtrot.xslt.model.CompiledStylesheet;
import com.helger.phoxtrot.xslt.model.EOnNoMatch;
import com.helger.phoxtrot.xslt.model.KeyDefinition;
import com.helger.phoxtrot.xslt.model.Mode;
import com.helger.phoxtrot.xslt.model.OutputDefinition;
import com.helger.phoxtrot.xslt.model.SpaceRules;
import com.helger.phoxtrot.xslt.model.Template;
import com.helger.phoxtrot.xslt.model.TemplateRule;
import com.helger.phoxtrot.xslt.model.VariableDef;
import com.helger.phoxtrot.xslt.pkg.PackageModel;
import com.helger.phoxtrot.xslt.pkg.PackageUnit;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslModule;
import com.helger.phoxtrot.xslt.tree.XsltNames;

/**
 * The state of one transformation: global variables (evaluated on first use,
 * XTDE0640 for circular definitions), template rule selection and invocation,
 * built-in template rules, keys, documents, messages, secondary results and
 * the execution budget (XPDY0130 when the number of instructions or the
 * nesting depth exceeds the limits).
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class Engine
{
  /** The current output URI (fn:current-output-uri). */
  public static final XsltStateKey <String> CURRENT_OUTPUT_URI = new XsltStateKey <> ("current-output-uri",
                                                                                     XsltStateKey.EScope.CLEARED_IN_FUNCTIONS);

  /** Default maximum number of executed instructions. */
  public static final long DEFAULT_MAX_STEPS = 20_000_000L;
  /** Default maximum nesting depth of templates and functions. */
  public static final int DEFAULT_MAX_DEPTH = 10_000;

  // NaN equals nothing: a unique key that is never searched for
  private static final Object NAN_KEY = new Object ();
  // The key indexes built from the name index of the document, for tests
  private static final AtomicLong KEY_INDEXES_FROM_NAMES = new AtomicLong (0);

  /**
   * Identifies one built key index: a key name, the root of the tree it
   * indexes and, in a multi-package stylesheet, the package whose xsl:key
   * declarations it uses (<code>null</code> otherwise).
   */
  private record KeyIndexId (@NonNull XdmQName name, @NonNull IXdmNode root, @Nullable PackageUnit unit)
  {}

  private final CompiledStylesheet m_aSheet;
  private final XsltTransformRequest m_aRequest;
  private final DynamicContext m_aRootDyn;
  private final DynamicContext m_aPatternDyn;
  private IXdmItem m_aGlobalContextItem;
  private boolean m_bGlobalVariablesWithoutFocus;
  private final ICommonsMap <XdmQName, XdmSequence> m_aGlobalValues = new CommonsHashMap <> ();
  private final ICommonsSet <XdmQName> m_aGlobalsInProgress = new CommonsHashSet <> ();
  private final ICommonsMap <VariableDef, XdmSequence> m_aOriginalValues = new CommonsHashMap <> ();
  private final ICommonsSet <VariableDef> m_aOriginalsInProgress = new CommonsHashSet <> ();
  private final ICommonsMap <KeyIndexId, ICommonsMap <Object, ICommonsList <IXdmNode>>> m_aKeyIndexes = new CommonsHashMap <> ();
  private final ICommonsSet <XdmQName> m_aKeysInProgress = new CommonsHashSet <> ();
  private final ICommonsList <String> m_aMessages = new CommonsArrayList <> ();
  private final ICommonsList <XsltTransformResult.SecondaryResult> m_aSecondaryResults = new CommonsArrayList <> ();
  private IXsltOutput m_aPrincipalOutput;
  private OutputDefinition m_aPrincipalOutputDefinition;
  private int m_nPrincipalSealedSize = -1;
  private final ICommonsMap <Object, Object> m_aRunData = new CommonsHashMap <> ();
  private final ExecutionBudget m_aBudget;

  /**
   * Set up the run-wide state of one transformation. The execution budget is
   * the shared one of the request (a nested <code>fn:transform</code>) or a new
   * one with the requested limits; a requested maximum depth is capped at
   * {@link #DEFAULT_MAX_DEPTH}, a non-positive limit means the default. The
   * dynamic context gets the function library of the stylesheet, the static
   * base URI of the principal module, the current date/time and interrupt of
   * the request, a resolver that applies xsl:strip-space to every document it
   * returns, and this engine as resolver of global variables. Two contexts are
   * derived from it: the root context, whose state always holds
   * {@link #CURRENT_OUTPUT_URI} (the base output URI of the request, or the
   * empty string) so that its absence marks temporary output state, and the
   * pattern context with an empty state.
   *
   * @param aSheet
   *        The compiled stylesheet.
   * @param aRequest
   *        The transformation request.
   */
  public Engine (@NonNull final CompiledStylesheet aSheet, @NonNull final XsltTransformRequest aRequest)
  {
    m_aSheet = aSheet;
    m_aRequest = aRequest;
    if (aRequest.getSharedBudget () != null)
      m_aBudget = aRequest.getSharedBudget ();
    else
      m_aBudget = new ExecutionBudget (aRequest.getMaxSteps () > 0 ? aRequest.getMaxSteps () : DEFAULT_MAX_STEPS,
                                       aRequest.getMaxDepth () > 0 ? Math.min (aRequest.getMaxDepth (), DEFAULT_MAX_DEPTH)
                                                                   : DEFAULT_MAX_DEPTH);
    final StaticContext aSC = new StaticContext ();
    aSC.setFunctionLibrary (aSheet.getFunctionLibrary ());
    final XslModule aPrincipal = aSheet.getPrincipalModule ();
    if (aPrincipal.getRoot () != null)
      aSC.setBaseUri (aPrincipal.getRoot ().getStaticBaseUri ());
    final DynamicContext aDyn = new DynamicContext (aSC);
    aDyn.setHost (this);
    if (aRequest.getCurrentDateTime () != null)
      aDyn.setCurrentDateTime (aRequest.getCurrentDateTime ());
    aDyn.setInterrupt (aRequest.getInterrupt ());
    aDyn.setResourceResolver (new StrippingResolver (aRequest.getResolver ()));
    aDyn.setContextGlobalVariableResolver (this::_resolveGlobal);
    final String sOutputUri = aRequest.getBaseOutputUri ();
    XsltState aState = XsltState.EMPTY;
    // Always set, so that its absence marks temporary output state
    aState = aState.with (CURRENT_OUTPUT_URI, sOutputUri != null ? sOutputUri : "");
    m_aRootDyn = aDyn.withHostState (aState);
    m_aPatternDyn = aDyn.withHostState (XsltState.EMPTY);
  }

  /**
   * A resolver that applies xsl:strip-space to the documents it returns, so
   * that documents read by document() and fn:doc are stripped too.
   */
  private final class StrippingResolver implements IResourceResolver
  {
    private final IResourceResolver m_aDelegate;

    StrippingResolver (@Nullable final IResourceResolver aDelegate)
    {
      m_aDelegate = aDelegate;
    }

    @Nullable
    public IXdmNode resolveDocument (@NonNull final String sAbsoluteUri)
    {
      final IXdmNode aDoc = m_aDelegate == null ? null : m_aDelegate.resolveDocument (sAbsoluteUri);
      return aDoc == null ? null : stripWhitespace (aDoc);
    }

    @Nullable
    public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
    {
      return m_aDelegate == null ? null : m_aDelegate.resolveText (sAbsoluteUri, sEncoding);
    }

    @Override
    @Nullable
    public ICommonsList <? extends IXdmItem> resolveCollection (@NonNull final String sAbsoluteUri)
    {
      return m_aDelegate == null ? null : m_aDelegate.resolveCollection (sAbsoluteUri);
    }

    @Override
    @Nullable
    public ICommonsList <String> resolveUriCollection (@NonNull final String sAbsoluteUri)
    {
      return m_aDelegate == null ? null : m_aDelegate.resolveUriCollection (sAbsoluteUri);
    }
  }

  @NonNull
  public CompiledStylesheet getStylesheet ()
  {
    return m_aSheet;
  }

  @NonNull
  public XsltTransformRequest getRequest ()
  {
    return m_aRequest;
  }

  /**
   * Count one executed instruction.
   *
   * @throws XPathException
   *         XPDY0130 when the budget is used up, FOER0000 when cancelled
   */
  public void step ()
  {
    if (m_aBudget.step ())
      m_aRootDyn.checkInterrupt ();
  }

  /**
   * Enter a template or function invocation.
   *
   * @throws XPathException
   *         XPDY0130 when nested too deeply
   */
  public void enterDepth ()
  {
    m_aBudget.enterDepth ();
  }

  public void leaveDepth ()
  {
    m_aBudget.leaveDepth ();
  }

  /**
   * @return The execution budget of this transformation (shared with the
   *         transformations it starts with fn:transform).
   */
  @NonNull
  public ExecutionBudget getBudget ()
  {
    return m_aBudget;
  }

  /**
   * @return The context patterns are matched in: no local variables, empty
   *         XSLT state, the run-wide settings of this transformation.
   */
  @NonNull
  public DynamicContext getPatternContext ()
  {
    return m_aPatternDyn;
  }

  /**
   * @return A context without focus and variables, with the initial XSLT
   *         state.
   */
  @NonNull
  public XsltContext createRootContext ()
  {
    return new XsltContext (this, m_aRootDyn);
  }

  /** @return The global context item or <code>null</code>. */
  @Nullable
  public IXdmItem getGlobalContextItem ()
  {
    return m_aGlobalContextItem;
  }

  public void setGlobalContextItem (@Nullable final IXdmItem aItem)
  {
    m_aGlobalContextItem = aItem;
  }

  /**
   * Evaluate global variables (not parameters) without context item: the
   * principal source is declared streamed and the initial mode is streamable,
   * so a global variable cannot navigate the streamed document (go-xslt
   * <code>streamedVarsNoFocus</code>, <code>streamable-116</code>).
   *
   * @param bWithoutFocus
   *        <code>true</code> to evaluate them without focus.
   */
  public void setGlobalVariablesWithoutFocus (final boolean bWithoutFocus)
  {
    m_bGlobalVariablesWithoutFocus = bWithoutFocus;
  }

  /**
   * Per-run data of instruction families (accumulator values, ...).
   *
   * @param aKey
   *        Key.
   * @param aFactory
   *        Creates the value on first use.
   * @param <T>
   *        Value type.
   * @return The value.
   */
  @NonNull
  @SuppressWarnings ("unchecked")
  public <T> T getRunData (@NonNull final Object aKey, @NonNull final Supplier <T> aFactory)
  {
    return (T) m_aRunData.computeIfAbsent (aKey, k -> aFactory.get ());
  }

  /**
   * Evaluate one global variable or parameter (XSLT 3.0 section 9.5), without
   * caching. An abstract variable that was not overridden raises XTDE3052. The
   * evaluation context has an empty XSLT state; the global context item is the
   * focus (with no current item) unless it is absent, the declaration belongs
   * to a used library package rather than the principal one, or it is a
   * variable (not a parameter) while global variables are evaluated without
   * focus (see {@link #setGlobalVariablesWithoutFocus(boolean)}). A declaration
   * in xsl:override sees the value of the declaration it overrides as
   * <code>$xsl:original</code>. For a parameter the value supplied in the
   * request is converted to the declared type; with no supplied value a
   * required parameter raises XTDE0050 and any other falls back to its
   * default (the select expression or content).
   *
   * @param aDef
   *        The global variable or parameter.
   * @return The value.
   */
  @NonNull
  private XdmSequence _computeGlobal (@NonNull final VariableDef aDef)
  {
    final XdmQName aName = aDef.getName ();
    final String sVisibility = aDef.getElement ().getAttr ("visibility");
    if (sVisibility != null && "abstract".equals (sVisibility.trim ()))
      throw XsltException.at (aDef.getElement (), "XTDE3052", "The abstract variable $" + aName.getEQName () + " was not overridden");
    XsltContext aCtx = createRootContext ().withState (XsltState.EMPTY);
    // A global of a used library package has no global context item
    if (m_aGlobalContextItem != null &&
        (!m_aSheet.isMultiPackage () || m_aSheet.getPackages ().isInPrincipal (aDef.getElement ())) &&
        !(m_bGlobalVariablesWithoutFocus && aDef.getKind () != VariableDef.EKind.GLOBAL_PARAM))
    {
      // The global context item is the focus, but there is no current item
      aCtx = new XsltContext (this, aCtx.withFocus (m_aGlobalContextItem, 1, 1).getDynamicContext ().withCurrentItem (null));
    }
    final VariableDef aOriginal = m_aSheet.originalVariables ().get (aDef);
    if (aOriginal != null)
      aCtx = aCtx.withVariable (StylesheetCompiler.XSL_ORIGINAL, _getOriginalValue (aOriginal));
    XdmSequence aValue = null;
    if (aDef.getKind () == VariableDef.EKind.GLOBAL_PARAM)
    {
      final XdmSequence aSupplied = m_aRequest.params ().get (aName);
      if (aSupplied != null)
        aValue = aDef.convert (aSupplied, aCtx);
      else
        if (aDef.isRequired ())
          throw XsltException.at (aDef.getElement (), "XTDE0050", "No value was supplied for the required parameter $" + aName.getEQName ());
    }
    if (aValue == null)
      aValue = aDef.evaluate (aCtx);
    return aValue;
  }

  /**
   * The value of a global declaration looked up as a declaration rather than by
   * name: the overridden original seen as <code>$xsl:original</code>, or the
   * declaration a package sees for a name. Cached per declaration (separately
   * from the by-name cache of {@link #getGlobalValue(VariableDef)}); a
   * re-entrant request for the same declaration raises XTDE0640 (XSLT 3.0
   * section 9.11), and errors are located at the declaration.
   *
   * @param aDef
   *        The declaration.
   * @return Its value, evaluated on first use.
   */
  @NonNull
  private XdmSequence _getOriginalValue (@NonNull final VariableDef aDef)
  {
    final XdmSequence aKnown = m_aOriginalValues.get (aDef);
    if (aKnown != null)
      return aKnown;
    if (!m_aOriginalsInProgress.add (aDef))
      throw XsltException.at (aDef.getElement (), "XTDE0640", "Circular definition of the global variable $" + aDef.getName ().getEQName ());
    try
    {
      final XdmSequence ret = _computeGlobal (aDef);
      m_aOriginalValues.put (aDef, ret);
      return ret;
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aDef.getElement ());
    }
    finally
    {
      m_aOriginalsInProgress.remove (aDef);
    }
  }

  /**
   * Resolve a global variable reference for the XPath evaluator. In a
   * stylesheet with package-specific globals, the package of the referencing
   * expression is found from its function library, and a declaration that
   * package sees for the name wins. Otherwise the declaration with the highest
   * import precedence applies: the compile-time value of a static variable or
   * parameter is returned unless a non-static declaration of the same name is
   * in effect, then an already computed value, then the value computed now.
   *
   * @param aDyn
   *        The context of the reference.
   * @param aName
   *        The variable name.
   * @return The value or <code>null</code> if there is no such global
   *         variable.
   */
  @Nullable
  private XdmSequence _resolveGlobal (@NonNull final DynamicContext aDyn, @NonNull final XdmQName aName)
  {
    if (m_aSheet.packageGlobals ().isNotEmpty ())
    {
      final PackageUnit aUnit = m_aSheet.getPackages ().getUnitForScope (aDyn.getStaticContext ().getFunctionLibrary ());
      final ICommonsMap <XdmQName, VariableDef> aOwn = m_aSheet.packageGlobals ().get (aUnit);
      final VariableDef aDef = aOwn == null ? null : aOwn.get (aName);
      if (aDef != null)
        return _getOriginalValue (aDef);
    }
    final VariableDef aDef = m_aSheet.globals ().get (aName);
    // A non-static declaration with higher import precedence than a static
    // one of the same name is in effect at run time (static-027)
    final XdmSequence aStatic = m_aSheet.staticValues ().get (aName);
    if (aStatic != null && (aDef == null || XsltNames.isXsltTrue (aDef.getElement ().getAttr ("static"))))
      return aStatic;
    final XdmSequence aKnown = m_aGlobalValues.get (aName);
    if (aKnown != null)
      return aKnown;
    if (aDef == null)
      return null;
    return getGlobalValue (aDef);
  }

  /**
   * @param aDef
   *        A global variable or parameter.
   * @return Its value, evaluated on first use.
   * @throws XPathException
   *         XTDE0640 for a circular definition, XTDE0050 for a missing
   *         required parameter
   */
  @NonNull
  public XdmSequence getGlobalValue (@NonNull final VariableDef aDef)
  {
    final XdmQName aName = aDef.getName ();
    final XdmSequence aKnown = m_aGlobalValues.get (aName);
    if (aKnown != null)
      return aKnown;
    if (!m_aGlobalsInProgress.add (aName))
      throw XsltException.at (aDef.getElement (), "XTDE0640", "Circular definition of the global variable $" + aName.getEQName ());
    try
    {
      final XdmSequence aValue = _computeGlobal (aDef);
      m_aGlobalValues.put (aName, aValue);
      return aValue;
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aDef.getElement ());
    }
    finally
    {
      m_aGlobalsInProgress.remove (aName);
    }
  }

  /**
   * Evaluate all global parameters, so that missing required parameters and
   * type errors are reported even if the parameter is never used.
   */
  public void checkGlobalParams ()
  {
    for (final VariableDef aDef : m_aSheet.globals ().values ())
      if (aDef.getKind () == VariableDef.EKind.GLOBAL_PARAM && !m_aSheet.staticValues ().containsKey (aDef.getName ()))
        if (aDef.isRequired () || m_aRequest.params ().containsKey (aDef.getName ()))
          getGlobalValue (aDef);
  }

  /**
   * Strip whitespace text nodes from a document according to
   * xsl:strip-space/xsl:preserve-space (XSLT 3.0 section 4.4.2). Returns the
   * same document if nothing is stripped.
   *
   * @param aDoc
   *        Document (or other root).
   * @return The stripped copy or the document itself.
   */
  @NonNull
  public IXdmNode stripWhitespace (@NonNull final IXdmNode aDoc)
  {
    final SpaceRules aRules = m_aSheet.getSpaceRules ();
    if (aRules.isNoStripping ())
      return aDoc;
    return WhitespaceStripper.strip (aDoc, aRules);
  }

  /**
   * Find the first rule of a mode that matches an item, scanning the rules in
   * their sorted order (import precedence, then priority, then declaration
   * order, best first; XSLT 3.0 section 6.4). Only rules whose import
   * precedence lies in the given range are considered (xsl:apply-imports),
   * and with <code>aAfter</code> the scan starts behind that rule
   * (xsl:next-match). For a plain selection in a mode with
   * on-multiple-match="fail", the following rules of the same precedence and
   * priority are tested as well and another matching template raises
   * XTDE0540.
   *
   * @param aMode
   *        The mode.
   * @param aItem
   *        The item to match.
   * @param aCtx
   *        Context the patterns are matched in.
   * @param aAfter
   *        The rule after which the scan starts, or <code>null</code> to start
   *        at the best rule.
   * @param nMinPrecedence
   *        Lowest import precedence considered.
   * @param nMaxPrecedence
   *        Highest import precedence considered.
   * @return The matching rule, or <code>null</code> if none matches or
   *         <code>aAfter</code> is not a rule of the mode.
   */
  @Nullable
  private TemplateRule _findRule (@NonNull final Mode aMode,
                                  @NonNull final IXdmItem aItem,
                                  @NonNull final XsltContext aCtx,
                                  @Nullable final TemplateRule aAfter,
                                  final int nMinPrecedence,
                                  final int nMaxPrecedence)
  {
    final ICommonsList <TemplateRule> aRules = aMode.rules ();
    int nStart = 0;
    if (aAfter != null)
    {
      nStart = aRules.indexOf (aAfter) + 1;
      if (nStart == 0)
        return null;
    }
    final int nSize = aRules.size ();
    for (int i = nStart; i < nSize; ++i)
    {
      final TemplateRule aRule = aRules.get (i);
      final int nPrec = aRule.getPrecedence ();
      if (nPrec < nMinPrecedence || nPrec > nMaxPrecedence)
        continue;
      if (!aRule.getPattern ().matches (aItem, aCtx))
        continue;
      if (aMode.isFailOnMultipleMatch () && aAfter == null)
        for (int j = i + 1; j < nSize; ++j)
        {
          final TemplateRule aOther = aRules.get (j);
          if (aOther.getPrecedence () != nPrec || aOther.getPriority () != aRule.getPriority ())
            break;
          if (aOther.getTemplate () != aRule.getTemplate () && aOther.getPattern ().matches (aItem, aCtx))
            throw XsltException.at (aRule.getTemplate ().getElement (),
                                    "XTDE0540",
                                    "Several template rules match the item and the mode specifies on-multiple-match='fail'");
        }
      return aRule;
    }
    return null;
  }

  /**
   * @param aMode
   *        Mode.
   * @param aItem
   *        Item.
   * @param aCtx
   *        Context.
   * @return The best matching rule or <code>null</code>.
   * @throws XPathException
   *         XTDE0540 if the mode has on-multiple-match="fail" and several
   *         rules of the best precedence and priority match
   */
  @Nullable
  public TemplateRule findRule (@NonNull final Mode aMode, @NonNull final IXdmItem aItem, @NonNull final XsltContext aCtx)
  {
    return _findRule (aMode, aItem, aCtx, null, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  /**
   * @return <code>true</code> if both sequences hold the very same item
   *         objects in the same order, i.e. a type conversion left the
   *         sequence unchanged.
   */
  private static boolean _isSameItems (@NonNull final XdmSequence a, @NonNull final XdmSequence b)
  {
    if (a == b)
      return true;
    if (a.size () != b.size ())
      return false;
    for (int i = 0; i < a.size (); ++i)
      if (a.get (i) != b.get (i))
        return false;
    return true;
  }

  /**
   * Bind the parameters of a template and run its body. The supplied tunnel
   * parameters are added to the tunnel parameters of the state. A declared
   * xsl:context-item is checked first: use="required" without a context item
   * raises XTTE3090, use="absent" removes the focus, and a context item that
   * does not match the declared type raises XTTE0590. Each declared parameter
   * takes the supplied value of the same name (a tunnel parameter from the
   * state, any other from the non-tunnel supplied values), converted to its
   * declared type; without one a required parameter raises XTDE0700 and any
   * other is evaluated from its default. Supplied parameters the template does
   * not declare are ignored. The body runs one nesting level deeper; with an
   * <code>as</code> type its result is buffered and converted (XTTE0505 when
   * it does not match), and the buffered output is replayed unchanged if the
   * conversion kept the very same items.
   *
   * @param aTemplate
   *        Template.
   * @param aBase
   *        Context with focus and state of the invocation, no variables.
   * @param aParams
   *        Supplied parameters.
   * @param aOut
   *        Output.
   */
  private void _runTemplate (@NonNull final Template aTemplate,
                             @NonNull final XsltContext aBase,
                             @NonNull final List <ParamValue> aParams,
                             @NonNull final IXsltOutput aOut)
  {
    XsltState aState = aBase.getState ();
    for (final ParamValue aParam : aParams)
      if (aParam.tunnel ())
        aState = aState.withTunnelParam (aParam.name (), aParam.value ());
    XsltContext aCtx = aBase.withState (aState);
    final Template.ContextItemDecl aCI = aTemplate.getContextItem ();
    if (aCI != null)
    {
      final IXdmItem aItem = aCtx.getContextItem ();
      if ("required".equals (aCI.use ()) && aItem == null)
        throw XsltException.at (aTemplate.getElement (), "XTTE3090", "The template requires a context item");
      if ("absent".equals (aCI.use ()))
        aCtx = aCtx.withFocus (null, 0, 0);
      else
        if (aItem != null && aCI.as () != null &&
            !SequenceTypeMatcher.matches (XdmSequence.of (aItem), aCI.as ()))
          throw XsltException.at (aTemplate.getElement (), "XTTE0590", "The context item does not match the declared type");
    }
    for (final VariableDef aParam : aTemplate.params ())
    {
      XdmSequence aValue = null;
      if (aParam.isTunnel ())
        aValue = aState.getTunnelParam (aParam.getName ());
      else
        for (final ParamValue aSupplied : aParams)
          if (!aSupplied.tunnel () && aSupplied.name ().equals (aParam.getName ()))
          {
            aValue = aSupplied.value ();
            break;
          }
      try
      {
        if (aValue == null)
        {
          if (aParam.isRequired ())
            throw XsltException.at (aParam.getElement (),
                                    "XTDE0700",
                                    "No value was supplied for the required parameter $" + aParam.getName ().getEQName ());
          aValue = aParam.evaluate (aCtx);
        }
        else
          aValue = aParam.convert (aValue, aCtx);
      }
      catch (final XPathException ex)
      {
        throw XsltException.locate (ex, aParam.getElement ());
      }
      aCtx = aCtx.withVariable (aParam.getName (), aValue);
    }
    enterDepth ();
    try
    {
      if (aTemplate.getAs () == null)
        aTemplate.getBody ().execute (aCtx, aOut);
      else
      {
        final BufferedOutput aBuffer = new BufferedOutput ();
        aTemplate.getBody ().execute (aCtx, aBuffer);
        final XdmSequence aRaw = aBuffer.getSequence ();
        final XdmSequence aConverted;
        try
        {
          aConverted = SequenceTypeMatcher.convert (aRaw,
                                                                           aTemplate.getAs (),
                                                                           aCtx.getDynamicContext (),
                                                                           "XTTE0505");
        }
        catch (final XPathException ex)
        {
          throw XsltException.locate (ex, aTemplate.getElement ());
        }
        if (_isSameItems (aRaw, aConverted))
          aBuffer.replayAll (aOut);
        else
          aOut.appendAll (aConverted);
      }
    }
    finally
    {
      leaveDepth ();
    }
  }

  /**
   * Invoke a template rule for an item (apply-templates, next-match,
   * apply-imports).
   */
  private void _invokeRule (@NonNull final TemplateRule aRule,
                            @NonNull final Mode aMode,
                            @NonNull final XsltContext aCaller,
                            @NonNull final IXdmItem aItem,
                            final int nPosition,
                            final int nSize,
                            @NonNull final List <ParamValue> aParams,
                            @NonNull final IXsltOutput aOut)
  {
    final XsltContext aBase = aCaller.withoutFocusAndVariables ()
                                     .withFocus (aItem, nPosition, nSize)
                                     .withState (aCaller.getState ().forTemplateCall ().withCurrentRule (aRule, aMode));
    _runTemplate (aRule.getTemplate (), aBase, aParams, aOut);
  }

  /**
   * Apply templates in the same mode to the children of a node, preceded by
   * its attributes if requested, passing the parameters on (the implicit
   * xsl:apply-templates of a built-in rule).
   *
   * @param aNode
   *        The node.
   * @param bAttributes
   *        <code>true</code> to process the attributes before the children.
   * @param aMode
   *        The mode.
   * @param aCtx
   *        Context of the built-in rule.
   * @param aParams
   *        Parameters passed on.
   * @param aOut
   *        Output.
   */
  private void _applyToChildren (@NonNull final IXdmNode aNode,
                                 final boolean bAttributes,
                                 @NonNull final Mode aMode,
                                 @NonNull final XsltContext aCtx,
                                 @NonNull final List <ParamValue> aParams,
                                 @NonNull final IXsltOutput aOut)
  {
    final XdmSequence.Builder aItems = new XdmSequence.Builder ();
    if (bAttributes)
      for (int i = 0; i < aNode.getAttributeCount (); ++i)
        aItems.add (aNode.getAttributeAtIndex (i));
    for (int i = 0; i < aNode.getChildCount (); ++i)
      aItems.add (aNode.getChildAtIndex (i));
    applyTemplates (aCtx, aItems.build (), aMode, aParams, aOut);
  }

  /**
   * Apply the built-in template rule of a mode (XSLT 3.0 section 6.7). Any
   * item in a mode with on-no-match="fail" raises XTDE0555. An array is copied
   * unchanged with shallow-copy and deep-copy, ignored with deep-skip, and
   * with text-only-copy and shallow-skip templates are applied to its
   * flattened members. Atomic values, maps and other function items are copied
   * unchanged (shallow-copy, deep-copy) or dropped (the skip variants and
   * text-only-copy); with text-only-copy an atomic value produces its lexical
   * form as text. For nodes:
   * <ul>
   * <li>deep-skip: templates are applied to the children of a document node;
   * every other node produces nothing.</li>
   * <li>shallow-skip: templates are applied to the attributes and children of
   * a document or element node; other nodes produce nothing.</li>
   * <li>deep-copy: a deep copy that keeps type annotations.</li>
   * <li>shallow-copy: an element is copied without content but with its base
   * URI and namespaces, and the results of applying templates to its
   * attributes and children become its content; for a document node the
   * results are written into the document the output is building, if it
   * builds one, otherwise into a new document node with the base URI of the
   * source; any other node is copied shallowly.</li>
   * <li>text-only-copy: templates are applied to the children of a document
   * or element node, text and attribute nodes produce their string value as
   * text, other nodes produce nothing.</li>
   * </ul>
   *
   * @param aMode
   *        The mode.
   * @param aItem
   *        The item no rule matched.
   * @param aCtx
   *        Context (state of the caller).
   * @param aParams
   *        Parameters passed on to the implicit xsl:apply-templates.
   * @param aOut
   *        Output.
   * @throws XPathException
   *         XTDE0555 with on-no-match="fail"
   */
  public void applyBuiltIn (@NonNull final Mode aMode,
                            @NonNull final IXdmItem aItem,
                            @NonNull final XsltContext aCtx,
                            @NonNull final List <ParamValue> aParams,
                            @NonNull final IXsltOutput aOut)
  {
    final EOnNoMatch eOnNoMatch = aMode.getOnNoMatch ();
    if (eOnNoMatch == EOnNoMatch.FAIL)
      throw new XPathException ("XTDE0555", "No template rule matches the item and the mode " + aMode + " has on-no-match='fail'");
    if (aItem instanceof final XdmArray aArray &&
        (eOnNoMatch == EOnNoMatch.TEXT_ONLY_COPY || eOnNoMatch == EOnNoMatch.SHALLOW_SKIP))
    {
      final XdmSequence.Builder aMembers = new XdmSequence.Builder ();
      for (final XdmSequence aMember : aArray.members ())
        aMembers.addAll (aMember);
      applyTemplates (aCtx, aMembers.build (), aMode, aParams, aOut);
      return;
    }
    if (!(aItem instanceof final IXdmNode aNode))
    {
      // Atomic values and function items, including maps and arrays
      switch (eOnNoMatch)
      {
        case TEXT_ONLY_COPY:
          if (aItem instanceof final XdmAtomic aAtomic)
            aOut.appendText (aAtomic.getLexical ());
          break;
        case SHALLOW_COPY:
        case DEEP_COPY:
          aOut.append (aItem);
          break;
        default:
          break;
      }
      return;
    }
    final ENodeKind eKind = aNode.getKind ();
    switch (eOnNoMatch)
    {
      case DEEP_SKIP:
        if (eKind == ENodeKind.DOCUMENT)
          _applyToChildren (aNode, false, aMode, aCtx, aParams, aOut);
        break;
      case SHALLOW_SKIP:
        if (eKind == ENodeKind.DOCUMENT || eKind == ENodeKind.ELEMENT)
          _applyToChildren (aNode, true, aMode, aCtx, aParams, aOut);
        break;
      case DEEP_COPY:
        // As xsl:copy-of with validation="preserve": annotations are kept
        aOut.appendNew (NodeCopier.copyDeep (aNode, true, true));
        break;
      case SHALLOW_COPY:
        if (eKind == ENodeKind.ELEMENT)
        {
          final IXdmNode aCopy = NodeCopier.copyShallow (aNode);
          // Like xsl:copy, the copy keeps the base URI (base-uri-053)
          final String sBase = FnAccessor.getBaseUri (aNode);
          if (sBase != null)
            aCopy.setBaseUri (sBase);
          NodeCopier.copyNamespaces (aNode, aCopy, true);
          final ContentOutput aContent = ContentOutput.createFor (aCopy, aOut, true);
          _applyToChildren (aNode, true, aMode, aCtx, aParams, aContent);
          aOut.appendNew (aContent.getParent ());
        }
        else
          if (eKind == ENodeKind.DOCUMENT)
          {
            final IXsltOutput aDocOut = aOut.getDocumentContentOutput ();
            if (aDocOut != null)
              _applyToChildren (aNode, false, aMode, aCtx, aParams, aDocOut);
            else
            {
              final IXdmNode aDoc = IXdmNode.createDocument ();
              final String sBase = FnAccessor.getBaseUri (aNode);
              if (sBase != null)
                aDoc.setBaseUri (sBase);
              _applyToChildren (aNode, false, aMode, aCtx, aParams, new ContentOutput (aDoc));
              aOut.appendNew (aDoc);
            }
          }
          else
            aOut.appendNew (NodeCopier.copyShallow (aNode));
        break;
      default:
        // text-only-copy
        if (eKind == ENodeKind.DOCUMENT || eKind == ENodeKind.ELEMENT)
          _applyToChildren (aNode, false, aMode, aCtx, aParams, aOut);
        else
          if (eKind == ENodeKind.TEXT || eKind == ENodeKind.ATTRIBUTE)
            aOut.appendText (aNode.getStringValue ());
        break;
    }
  }

  /**
   * XSLT 3.0 section 6.6.3: typed="yes", "strict" and "lax" require the
   * elements and attributes a mode processes to be typed (XTTE3100), typed="no"
   * requires them to be untyped (XTTE3110).
   */
  private static void _checkTypedMode (@NonNull final Mode aMode, @NonNull final IXdmItem aItem)
  {
    final String sTyped = aMode.getTyped ();
    if (sTyped == null || !(aItem instanceof final IXdmNode aNode) || (!aNode.isElement () && !aNode.isAttribute ()))
      return;
    final XdmQName aType = aNode.getTypeAnnotation ();
    final boolean bTyped = aType != null &&
                           !(XdmConstants.NS_XS.equals (aType.namespaceUri ()) &&
                             ("untyped".equals (aType.localName ()) || "untypedAtomic".equals (aType.localName ())));
    switch (sTyped)
    {
      case "yes", "true", "1", "strict", "lax":
        if (!bTyped)
          throw new XPathException ("XTTE3100",
                                    "The mode is declared typed='" + sTyped + "' but the node " + aNode.getName ().getLexicalName () + " is untyped");
        break;
      case "no", "false", "0":
        if (bTyped)
          throw new XPathException ("XTTE3110",
                                    "The mode is declared typed='no' but the node " + aNode.getName ().getLexicalName () + " has a type annotation");
        break;
      default:
        break;
    }
  }

  /**
   * xsl:apply-templates: process each item with the best matching rule of the
   * mode, or the built-in rule, with its position and the size of the
   * selection as focus. See
   * {@link #applyTemplates(XsltContext, IXdmItem, int, int, Mode, List, IXsltOutput)}.
   *
   * @param aCaller
   *        Context of the caller.
   * @param aItems
   *        The items (already sorted).
   * @param aMode
   *        Mode.
   * @param aParams
   *        Evaluated xsl:with-param values.
   * @param aOut
   *        Output.
   */
  public void applyTemplates (@NonNull final XsltContext aCaller,
                              @NonNull final XdmSequence aItems,
                              @NonNull final Mode aMode,
                              @NonNull final List <ParamValue> aParams,
                              @NonNull final IXsltOutput aOut)
  {
    final int nSize = aItems.size ();
    for (int i = 0; i < nSize; ++i)
      applyTemplates (aCaller, aItems.get (i), i + 1, nSize, aMode, aParams, aOut);
  }

  /**
   * Apply templates to one item of a selection (also used for xsl:apply-templates
   * over the records of a streamed document). The typed attribute of the mode
   * is checked (XTTE3100/XTTE3110). With a matching rule, the rule runs with
   * the item as focus, no local variables, and the caller's state with this
   * rule as current rule and this mode as current mode. Otherwise the built-in
   * rule runs one nesting level deeper with the same focus and the current rule
   * cleared, keeping the mode for its implicit xsl:apply-templates.
   *
   * @param aCaller
   *        Context of the instruction.
   * @param aItem
   *        The item.
   * @param nPosition
   *        Its position in the selection.
   * @param nSize
   *        The size of the selection.
   * @param aMode
   *        Mode.
   * @param aParams
   *        Evaluated xsl:with-param values.
   * @param aOut
   *        Output.
   */
  public void applyTemplates (@NonNull final XsltContext aCaller,
                              @NonNull final IXdmItem aItem,
                              final int nPosition,
                              final int nSize,
                              @NonNull final Mode aMode,
                              @NonNull final List <ParamValue> aParams,
                              @NonNull final IXsltOutput aOut)
  {
    _checkTypedMode (aMode, aItem);
    final TemplateRule aRule = findRule (aMode, aItem, aCaller);
    if (aRule != null)
      _invokeRule (aRule, aMode, aCaller, aItem, nPosition, nSize, aParams, aOut);
    else
    {
      // The built-in rule runs with the focus on the item and keeps the mode
      final XsltContext aCtx = aCaller.withoutFocusAndVariables ()
                                      .withFocus (aItem, nPosition, nSize)
                                      .withState (aCaller.getState ().withCurrentRule (null, aMode));
      enterDepth ();
      try
      {
        applyBuiltIn (aMode, aItem, aCtx, aParams, aOut);
      }
      finally
      {
        leaveDepth ();
      }
    }
  }

  /**
   * xsl:next-match and xsl:apply-imports (XSLT 3.0 section 6.8). Both work in
   * the current mode (the default mode if none is set) on the context item,
   * keeping its position and size. xsl:next-match looks for the next matching
   * rule after the current rule in the sorted rule list; xsl:apply-imports
   * looks for the best matching rule among the modules imported by the module
   * of the current rule, i.e. with an import precedence from the lowest
   * precedence of those modules up to just below that of the current template.
   * If no rule is found the built-in rule of the mode runs, with the current
   * rule cleared.
   *
   * @param aCtx
   *        Context of the instruction.
   * @param bImports
   *        <code>true</code> for xsl:apply-imports.
   * @param aParams
   *        Parameters.
   * @param aOut
   *        Output.
   * @throws XPathException
   *         XTDE0560 if there is no current template rule or the context item
   *         is absent
   */
  public void applyNextMatch (@NonNull final XsltContext aCtx,
                              final boolean bImports,
                              @NonNull final List <ParamValue> aParams,
                              @NonNull final IXsltOutput aOut)
  {
    final XsltState aState = aCtx.getState ();
    final TemplateRule aCurrent = aState.getCurrentRule ();
    if (aCurrent == null)
      throw new XPathException ("XTDE0560",
                                "xsl:" + (bImports ? "apply-imports" : "next-match") + " is used while there is no current template rule");
    final Mode aMode = aState.getCurrentMode () != null ? aState.getCurrentMode ()
                                                        : m_aSheet.getOrCreateMode (m_aSheet.getDefaultModeName ());
    final IXdmItem aItem = aCtx.getContextItem ();
    if (aItem == null)
      throw new XPathException ("XTDE0560",
                                "xsl:" + (bImports ? "apply-imports" : "next-match") + " is used while the context item is absent");
    final TemplateRule aNext;
    if (bImports)
    {
      final Template aTemplate = aCurrent.getTemplate ();
      aNext = _findRule (aMode, aItem, aCtx, null, aTemplate.getImportLo (), aTemplate.getPrecedence () - 1);
    }
    else
      aNext = _findRule (aMode, aItem, aCtx, aCurrent, Integer.MIN_VALUE, Integer.MAX_VALUE);
    if (aNext != null)
      _invokeRule (aNext, aMode, aCtx, aItem, aCtx.getPosition (), aCtx.getSize (), aParams, aOut);
    else
    {
      enterDepth ();
      try
      {
        applyBuiltIn (aMode, aItem, aCtx.withState (aState.withCurrentRule (null, aMode)), aParams, aOut);
      }
      finally
      {
        leaveDepth ();
      }
    }
  }

  /**
   * xsl:call-template (also the initial template). The template runs with the
   * focus of the caller, no local variables, and the caller's state as
   * prepared by {@link XsltState#forTemplateCall()}.
   *
   * @param aCaller
   *        Context of the caller: its focus stays.
   * @param aTemplate
   *        The template.
   * @param aParams
   *        Parameters.
   * @param aOut
   *        Output.
   * @throws XPathException
   *         XTDE3052 if the template is abstract and was not overridden
   */
  public void callTemplate (@NonNull final XsltContext aCaller,
                            @NonNull final Template aTemplate,
                            @NonNull final List <ParamValue> aParams,
                            @NonNull final IXsltOutput aOut)
  {
    final String sVisibility = aTemplate.getElement ().getAttr ("visibility");
    if (sVisibility != null && "abstract".equals (sVisibility.trim ()))
      throw new XPathException ("XTDE3052", "The abstract template " + aTemplate.getName ().getEQName () + " was not overridden");
    final XsltContext aBase = aCaller.withoutFocusAndVariables ()
                                     .withFocus (aCaller.getContextItem (), aCaller.getPosition (), aCaller.getSize ())
                                     .withState (aCaller.getState ().forTemplateCall ());
    _runTemplate (aTemplate, aBase, aParams, aOut);
  }

  /**
   * Evaluate one named attribute set (XSLT 3.0 section 10.2.4): for each of
   * its declarations in order, first the attribute sets it uses, then its own
   * attribute instructions, evaluated with the focus of the caller but no
   * local variables. A missing set raises XTSE0710, a set that is already
   * being evaluated further up raises XTSE0720 (the recursion depth is bounded
   * by the number of attribute sets), an abstract declaration raises
   * XTDE3052.
   *
   * @param aName
   *        The attribute set name.
   * @param aCtx
   *        Context of the instruction.
   * @param aOut
   *        The output of the element being constructed.
   * @param aActive
   *        The names of the attribute sets being evaluated (modified while
   *        this set is evaluated).
   */
  private void _applyAttributeSet (@NonNull final XdmQName aName,
                                   @NonNull final XsltContext aCtx,
                                   @NonNull final IXsltOutput aOut,
                                   @NonNull final ICommonsList <XdmQName> aActive)
  {
    final AttributeSet aSet = m_aSheet.getAttributeSet (aName);
    if (aSet == null)
      throw new XPathException ("XTSE0710", "No attribute set named " + aName.getEQName ());
    if (aActive.contains (aName))
      throw new XPathException ("XTSE0720", "The attribute set " + aName.getEQName () + " uses itself");
    aActive.add (aName);
    // Attribute sets are evaluated with the focus of the caller but no local
    // variables
    final XsltContext aSetCtx = aCtx.withoutFocusAndVariables ().withFocus (aCtx.getContextItem (), aCtx.getPosition (), aCtx.getSize ());
    for (final AttributeSet.Decl aDecl : aSet.decls ())
    {
      final String sVisibility = aDecl.element ().getAttr ("visibility");
      if (sVisibility != null && "abstract".equals (sVisibility.trim ()))
        throw new XPathException ("XTDE3052", "The abstract attribute set " + aName.getEQName () + " was not overridden");
      for (final XdmQName aUse : aDecl.uses ())
        _applyAttributeSet (aUse, aCtx, aOut, aActive);
      aDecl.body ().execute (aSetCtx, aOut);
    }
    aActive.removeLast ();
  }

  /**
   * Add the attributes of attribute sets (use-attribute-sets).
   *
   * @param aNames
   *        The attribute set names.
   * @param aCtx
   *        Context of the instruction.
   * @param aOut
   *        The output of the element being constructed.
   */
  public void applyAttributeSets (@NonNull final List <XdmQName> aNames,
                                  @NonNull final XsltContext aCtx,
                                  @NonNull final IXsltOutput aOut)
  {
    final ICommonsList <XdmQName> aActive = new CommonsArrayList <> ();
    for (final XdmQName aName : aNames)
      _applyAttributeSet (aName, aCtx, aOut, aActive);
  }

  /**
   * The lookup key of an atomic key value, built so that values equal under
   * <code>eq</code> map to equal keys. String-like values give the string
   * prefixed with "S" or, with a collation, its collation key. Numeric values give their xs:double value with
   * negative zero normalised to zero; NaN gives a unique object that is never
   * searched for in a single-value lookup. Other values use
   * {@link AtomicComparator#getMapKey(XdmAtomic)}.
   *
   * @param aValue
   *        The atomic value.
   * @param aCollation
   *        The collation of the key, or <code>null</code> for the codepoint
   *        collation.
   * @return The lookup key.
   */
  @NonNull
  private static Object _getKeyValue (@NonNull final XdmAtomic aValue, @Nullable final ICollation aCollation)
  {
    if (aValue.isStringLike ())
    {
      final String s = aValue.getString ();
      return aCollation == null ? "S" + s : aCollation.getCollationKey (s);
    }
    if (aValue.isNumeric ())
    {
      // eq promotes to xs:double when the types differ
      final double d = aValue.getAsDouble ();
      if (Double.isNaN (d))
        return NAN_KEY;
      return Double.valueOf (d == 0 ? 0d : d);
    }
    return AtomicComparator.getMapKey (aValue);
  }

  /**
   * Add a node to the entry of a key in an index. A node that is already the
   * last one in the entry (the same node with several equal key values) is
   * not added twice.
   */
  private static void _addToIndex (@NonNull final ICommonsMap <Object, ICommonsList <IXdmNode>> aIndex,
                                   @NonNull final Object aKey,
                                   @NonNull final IXdmNode aNode)
  {
    final ICommonsList <IXdmNode> aList = aIndex.computeIfAbsent (aKey, k -> new CommonsArrayList <> ());
    if (aList.isEmpty () || aList.getLast () != aNode)
      aList.add (aNode);
  }

  /**
   * Add one node to a key index: for every xsl:key declaration whose match
   * pattern matches the node, the key specifier (use attribute or content) is
   * evaluated with the node as focus and atomised. With composite keys the
   * whole sequence of values forms one key, otherwise each value is a key of
   * its own.
   *
   * @param aNode
   *        The node.
   * @param aDefs
   *        The declarations of the key.
   * @param aUseCtx
   *        Context without focus and with empty state.
   * @param aCollation
   *        The collation, or <code>null</code> for the codepoint collation.
   * @param bComposite
   *        <code>true</code> for a composite key.
   * @param aIndex
   *        The index to fill.
   */
  private void _indexNode (@NonNull final IXdmNode aNode,
                           @NonNull final ICommonsList <KeyDefinition> aDefs,
                           @NonNull final XsltContext aUseCtx,
                           @Nullable final ICollation aCollation,
                           final boolean bComposite,
                           @NonNull final ICommonsMap <Object, ICommonsList <IXdmNode>> aIndex)
  {
    for (final KeyDefinition aDef : aDefs)
    {
      if (!aDef.getMatch ().matches (aNode, aUseCtx))
        continue;
      final XsltContext aFocus = aUseCtx.withFocus (aNode, 1, 1);
      final XdmSequence aValues;
      if (aDef.getUse () != null)
        aValues = aDef.getUse ().evaluate (aFocus);
      else
      {
        final SequenceOutput aOut = new SequenceOutput ();
        aDef.getBody ().execute (aFocus, aOut);
        aValues = aOut.getSequence ();
      }
      final XdmSequence aAtoms = Atomizer.atomize (aValues);
      if (bComposite)
      {
        final ICommonsList <Object> aKey = new CommonsArrayList <> ();
        for (final IXdmItem aAtom : aAtoms)
          aKey.add (_getKeyValue ((XdmAtomic) aAtom, aCollation));
        _addToIndex (aIndex, aKey, aNode);
      }
      else
        for (final IXdmItem aAtom : aAtoms)
          _addToIndex (aIndex, _getKeyValue ((XdmAtomic) aAtom, aCollation), aNode);
    }
  }

  /**
   * The candidates of a key index from the name index of the document: if the
   * match pattern of every declaration names the elements and attributes it
   * can match ({@link com.helger.phoxtrot.xpath.pattern.XsltPattern#getFinalNames()}),
   * the elements and attributes of these names, merged in document order.
   * Every node the walk of the whole tree would find matching is among them,
   * and they come in the order of the walk, so testing only these against the
   * full patterns gives the same index.
   *
   * @param aDefs
   *        The declarations of the key.
   * @param aRoot
   *        The root of the tree.
   * @return The candidates, or <code>null</code> if a pattern can match other
   *         nodes, the root is no document node, the document is streamed or
   *         the tree is not numbered (no name index): then the whole tree must
   *         be walked.
   */
  @Nullable
  private static List <IXdmNode> _getKeyCandidatesFromNames (@NonNull final ICommonsList <KeyDefinition> aDefs,
                                                             @NonNull final IXdmNode aRoot)
  {
    if (!(aRoot instanceof final XdmDocumentNode aDoc) || aDoc.isStreamed ())
      return null;
    final ICommonsList <XdmQName> aElementNames = new CommonsArrayList <> ();
    final ICommonsList <XdmQName> aAttributeNames = new CommonsArrayList <> ();
    for (final KeyDefinition aDef : aDefs)
    {
      final PatternFinalNames aNames = aDef.getMatch ().pattern ().getFinalNames ();
      if (aNames == null)
        return null;
      for (final XdmQName aName : aNames.elementNames ())
        if (!aElementNames.contains (aName))
          aElementNames.add (aName);
      for (final XdmQName aName : aNames.attributeNames ())
        if (!aAttributeNames.contains (aName))
          aAttributeNames.add (aName);
    }
    // Distinct names give disjoint lists, each in document order
    final ICommonsList <List <IXdmNode>> aLists = new CommonsArrayList <> ();
    for (final XdmQName aName : aElementNames)
    {
      final List <IXdmNode> aNodes = aDoc.getElementsByName (aName);
      if (aNodes == null)
        return null;
      if (!aNodes.isEmpty ())
        aLists.add (aNodes);
    }
    for (final XdmQName aName : aAttributeNames)
    {
      final List <IXdmNode> aNodes = aDoc.getAttributesByName (aName);
      if (aNodes == null)
        return null;
      if (!aNodes.isEmpty ())
        aLists.add (aNodes);
    }
    if (aLists.isEmpty ())
      return List.of ();
    if (aLists.size () == 1)
      return aLists.getFirst ();
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    for (final List <IXdmNode> aNodes : aLists)
      ret.addAll (aNodes);
    ret.sort ( (a, b) -> Integer.compare (a.getOrder (), b.getOrder ()));
    return ret;
  }

  /**
   * @return The number of key indexes built so far (in this JVM) from the
   *         name index of the document instead of a walk of the whole tree.
   *         For tests that need to know which path ran: the index is the same
   *         either way.
   */
  public static long getKeyIndexFromNamesCount ()
  {
    return KEY_INDEXES_FROM_NAMES.get ();
  }

  /**
   * Build the index of a key over the whole tree below a root in document
   * order. If the match patterns allow it, only the candidates from the name
   * index of the document are tested
   * ({@link #_getKeyCandidatesFromNames(ICommonsList, IXdmNode)}); otherwise
   * the tree is walked iteratively: every node, its attributes and, only if
   * one of the match patterns mentions <code>namespace-node</code>, its
   * namespace nodes. The collation and the composite setting are taken from
   * the first declaration.
   *
   * @param aDefs
   *        The declarations of the key (not empty).
   * @param aRoot
   *        The root of the tree.
   * @param aCtx
   *        Context of the fn:key call (not used).
   * @return The index from lookup key to the nodes in document order.
   * @throws XPathException
   *         if the collation is not known
   */
  @NonNull
  private ICommonsMap <Object, ICommonsList <IXdmNode>> _buildKeyIndex (@NonNull final ICommonsList <KeyDefinition> aDefs,
                                                                     @NonNull final IXdmNode aRoot,
                                                                     @NonNull final XsltContext aCtx)
  {
    final ICommonsMap <Object, ICommonsList <IXdmNode>> ret = new CommonsHashMap <> ();
    final String sCollation = aDefs.getFirst ().getCollation ();
    final ICollation aCollation = CollationRegistry.CODEPOINT_URI.equals (sCollation) ? null
                                                                                       : CollationRegistry.getCollationOrThrow (sCollation);
    final boolean bComposite = aDefs.getFirst ().isComposite ();
    final XsltContext aUseCtx = createRootContext ().withState (XsltState.EMPTY);
    final List <IXdmNode> aCandidates = _getKeyCandidatesFromNames (aDefs, aRoot);
    if (aCandidates != null)
    {
      KEY_INDEXES_FROM_NAMES.incrementAndGet ();
      for (final IXdmNode aNode : aCandidates)
        _indexNode (aNode, aDefs, aUseCtx, aCollation, bComposite, ret);
      return ret;
    }
    boolean bNamespaces = false;
    for (final KeyDefinition aDef : aDefs)
      if (aDef.getMatch ().pattern ().getSource ().contains ("namespace-node"))
        bNamespaces = true;
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aNode = aStack.pop ();
      _indexNode (aNode, aDefs, aUseCtx, aCollation, bComposite, ret);
      if (bNamespaces)
        for (final IXdmNode aNS : aNode.getNamespaceNodes ())
          _indexNode (aNS, aDefs, aUseCtx, aCollation, bComposite, ret);
      for (int i = 0; i < aNode.getAttributeCount (); ++i)
        _indexNode (aNode.getAttributeAtIndex (i), aDefs, aUseCtx, aCollation, bComposite, ret);
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.push (aNode.getChildAtIndex (i));
    }
    return ret;
  }

  /**
   * fn:key (XSLT 3.0 section 20.2.2). In a multi-package stylesheet only the
   * xsl:key declarations of the package of the calling expression are used.
   * The index is built on first use per key name, tree root and package, and
   * cached for the run. Values are atomised; with a non-composite key each
   * value is looked up separately (NaN finds nothing), with a composite key
   * the whole sequence is one lookup key. The result is restricted to
   * <code>aTop</code> and its descendants when it is not the root.
   *
   * @param aCtx
   *        Context.
   * @param aName
   *        Key name.
   * @param aValues
   *        Searched values (atomized).
   * @param aTop
   *        The node whose tree is searched; only its descendants-or-self
   *        are returned.
   * @return Matching nodes in document order.
   * @throws XPathException
   *         XTDE1260 if there is no such key (in the calling package),
   *         XTSE3430 if the tree is a streamed document, XTDE0640 if building
   *         the index of a key needs the same key again
   */
  @NonNull
  public XdmSequence findKeyNodes (@NonNull final XsltContext aCtx,
                                   @NonNull final XdmQName aName,
                                   @NonNull final XdmSequence aValues,
                                   @NonNull final IXdmNode aTop)
  {
    ICommonsList <KeyDefinition> aDefs = m_aSheet.getKey (aName);
    PackageUnit aUnit = null;
    if (aDefs != null && m_aSheet.isMultiPackage ())
    {
      // Keys are local to a package
      final PackageModel aPackages = m_aSheet.getPackages ();
      final PackageUnit aCaller = aPackages.getUnitForScope (aCtx.getDynamicContext ().getStaticContext ().getFunctionLibrary ());
      aUnit = aCaller;
      aDefs = aDefs.getAll (x -> aPackages.getUnit (x.getElement ()) == aCaller);
      if (aDefs.isEmpty ())
        aDefs = null;
    }
    if (aDefs == null)
      throw new XPathException ("XTDE1260", "There is no key named " + aName.getEQName ());
    final IXdmNode aRoot = aTop.getRoot ();
    // Building the index would read the whole document, of which a streamed
    // one holds only a part (go-xslt raises the same run-time error)
    if (aRoot instanceof final XdmDocumentNode aDoc && aDoc.isStreamed ())
      throw new XPathException ("XTSE3430",
                                "The key " + aName.getEQName () + " cannot be used over a document that is read as a stream");
    final KeyIndexId aId = new KeyIndexId (aName, aRoot, aUnit);
    ICommonsMap <Object, ICommonsList <IXdmNode>> aIndex = m_aKeyIndexes.get (aId);
    if (aIndex == null)
    {
      if (!m_aKeysInProgress.add (aName))
        throw new XPathException ("XTDE0640", "The definition of the key " + aName.getEQName () + " depends on itself");
      try
      {
        aIndex = _buildKeyIndex (aDefs, aRoot, aCtx);
      }
      finally
      {
        m_aKeysInProgress.remove (aName);
      }
      m_aKeyIndexes.put (aId, aIndex);
    }
    final String sCollation = aDefs.getFirst ().getCollation ();
    final ICollation aCollation = CollationRegistry.CODEPOINT_URI.equals (sCollation) ? null
                                                                                       : CollationRegistry.getCollationOrThrow (sCollation);
    final XdmSequence.Builder aResult = new XdmSequence.Builder ();
    final XdmSequence aAtoms = Atomizer.atomize (aValues);
    if (aDefs.getFirst ().isComposite ())
    {
      final ICommonsList <Object> aKey = new CommonsArrayList <> ();
      for (final IXdmItem aAtom : aAtoms)
        aKey.add (_getKeyValue ((XdmAtomic) aAtom, aCollation));
      final ICommonsList <IXdmNode> aNodes = aIndex.get (aKey);
      if (aNodes != null)
        for (final IXdmNode aNode : aNodes)
          aResult.add (aNode);
    }
    else
      for (final IXdmItem aAtom : aAtoms)
      {
        final Object aKey = _getKeyValue ((XdmAtomic) aAtom, aCollation);
        final ICommonsList <IXdmNode> aNodes = aKey == NAN_KEY ? null : aIndex.get (aKey);
        if (aNodes != null)
          for (final IXdmNode aNode : aNodes)
            aResult.add (aNode);
      }
    XdmSequence ret = NodeSorter.sortAndDeduplicate (aResult.build ());
    if (aTop != aRoot)
    {
      final XdmSequence.Builder aFiltered = new XdmSequence.Builder ();
      for (final IXdmItem aItem : ret)
        for (IXdmNode aCur = (IXdmNode) aItem; aCur != null; aCur = aCur.getParent ())
          if (aCur == aTop)
          {
            aFiltered.add (aItem);
            break;
          }
      ret = aFiltered.build ();
    }
    return ret;
  }


  /**
   * Make <code>fn:doc</code> and <code>document()</code> return the source
   * document itself for its document URI, instead of reading it a second time
   * as another tree.
   *
   * @param aNode
   *        The global context item. Only a document node with a document URI
   *        is registered.
   */
  public void registerSourceDocument (@NonNull final IXdmNode aNode)
  {
    final String sDocUri = aNode.getDocumentUri ();
    if (aNode.isDocument () && sDocUri != null && !sDocUri.isEmpty ())
      m_aRootDyn.getRunCache ().putIfAbsent ("fn:doc#" + sDocUri, aNode);
  }

  /**
   * Load a document for document() (cached per run together with fn:doc,
   * whitespace stripped).
   *
   * @param aCtx
   *        Context.
   * @param sUri
   *        Absolute URI, possibly with a fragment identifier.
   * @return The document (or the element identified by the fragment), or
   *         <code>null</code> if the fragment identifies nothing.
   * @throws XPathException
   *         FODC0002 if the document cannot be retrieved
   */
  @Nullable
  public IXdmNode getDocument (@NonNull final XsltContext aCtx, @NonNull final String sUri)
  {
    String sDocUri = sUri;
    String sFragment = null;
    final int nHash = sUri.indexOf ('#');
    if (nHash >= 0)
    {
      sDocUri = sUri.substring (0, nHash);
      sFragment = sUri.substring (nHash + 1);
    }
    IXdmNode aDoc;
    final String sKey = "fn:doc#" + sDocUri;
    final ICommonsMap <String, Object> aCache = m_aRootDyn.getRunCache ();
    if (aCache.get (sKey) instanceof final IXdmNode aCached)
      aDoc = aCached;
    else
    {
      // document('') and the URI of a stylesheet module give the module
      aDoc = sDocUri.isEmpty () ? m_aSheet.getPrincipalModule ().getSourceDocument () : null;
      for (final XslModule aModule : m_aSheet.modules ())
        if (sDocUri.equals (aModule.getUri ()))
        {
          aDoc = aModule.getSourceDocument ();
          break;
        }
      if (aDoc != null)
        aDoc = stripWhitespace (aDoc);
      else
      {
        aDoc = m_aRootDyn.getResourceResolver ().resolveDocument (sDocUri);
        if (aDoc == null)
          throw new XPathException ("FODC0002", "The document '" + sDocUri + "' cannot be retrieved");
      }
      aCache.put (sKey, aDoc);
    }
    if (sFragment == null || sFragment.isEmpty ())
      return aDoc;
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aDoc);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if (aCur.isElement ())
      {
        final IXdmNode aXmlId = aCur.getAttribute (XdmConstants.NS_XML, "id");
        if (aXmlId != null && sFragment.equals (aXmlId.getValue ().trim ()))
          return aCur;
        for (int i = 0; i < aCur.getAttributeCount (); ++i)
        {
          final IXdmNode aAttr = aCur.getAttributeAtIndex (i);
          if (aAttr.getIDKind () != EIDKind.NONE && sFragment.equals (aAttr.getValue ().trim ()))
            return aCur;
        }
      }
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return null;
  }

  /**
   * Open a document for streamed processing with the resolver of the request,
   * applying xsl:strip-space while it is read.
   *
   * @param sAbsoluteUri
   *        Absolute URI.
   * @return The open reader, or <code>null</code> if the document is to be
   *         read in full (no resolver that streams it, a fragment identifier,
   *         or a document the reader does not stream).
   * @throws XPathException
   *         FODC0002 if the document cannot be read
   */
  @Nullable
  public XdmStreamReader openStreamedDocument (@NonNull final String sAbsoluteUri)
  {
    final IResourceResolver aResolver = m_aRequest.getResolver ();
    if (aResolver == null || sAbsoluteUri.indexOf ('#') >= 0)
      return null;
    final SpaceRules aRules = m_aSheet.getSpaceRules ();
    try
    {
      return aResolver.openDocumentStream (sAbsoluteUri, aRules.isNoStripping () ? null : aRules::isStripped);
    }
    catch (final XdmStreamUnsupportedException ex)
    {
      return null;
    }
    catch (final XdmParseException ex)
    {
      throw new XPathException ("FODC0002", "The document '" + sAbsoluteUri + "' cannot be retrieved: " + ex.getMessage ());
    }
  }

  /**
   * @param sMessage
   *        The text of an xsl:message.
   */
  public void addMessage (@NonNull final String sMessage)
  {
    m_aMessages.add (sMessage);
  }

  @NonNull
  public ICommonsList <String> getMessages ()
  {
    return m_aMessages;
  }

  /**
   * @param aResult
   *        A secondary result (xsl:result-document).
   */
  public void addSecondaryResult (final XsltTransformResult.@NonNull SecondaryResult aResult)
  {
    m_aSecondaryResults.add (aResult);
  }

  @NonNull
  public ICommonsList <XsltTransformResult.SecondaryResult> getSecondaryResults ()
  {
    return m_aSecondaryResults;
  }

  /**
   * @param aCtx
   *        A context.
   * @return The context in temporary output state (XSLT 3.0 section 25.2):
   *         there is no current output URI and xsl:result-document is not
   *         allowed.
   */
  @NonNull
  public static XsltContext withTemporaryOutputState (@NonNull final XsltContext aCtx)
  {
    return aCtx.get (CURRENT_OUTPUT_URI) == null ? aCtx : aCtx.with (CURRENT_OUTPUT_URI, null);
  }

  /**
   * @param aCtx
   *        A context.
   * @return <code>true</code> if the context is in temporary output state
   *         (in a variable, function, key, sort key, pattern, ...).
   */
  public static boolean isTemporaryOutputState (@NonNull final XsltContext aCtx)
  {
    return aCtx.get (CURRENT_OUTPUT_URI) == null;
  }

  /**
   * @param aOut
   *        The output the principal result is written to.
   */
  public void setPrincipalOutput (@Nullable final IXsltOutput aOut)
  {
    m_aPrincipalOutput = aOut;
  }

  /** @return The output of the principal result, or <code>null</code>. */
  @Nullable
  public IXsltOutput getPrincipalOutput ()
  {
    return m_aPrincipalOutput;
  }

  /**
   * @param aDef
   *        The output definition that replaces the unnamed one for the
   *        principal result (an xsl:result-document without href).
   */
  public void setPrincipalOutputDefinition (@Nullable final OutputDefinition aDef)
  {
    m_aPrincipalOutputDefinition = aDef;
  }

  /** @return The replacement output definition of the principal result, or <code>null</code>. */
  @Nullable
  public OutputDefinition getPrincipalOutputDefinition ()
  {
    return m_aPrincipalOutputDefinition;
  }

  /**
   * Mark the principal result as complete (written by an xsl:result-document
   * without href): anything written to it afterwards is XTDE1490.
   *
   * @param nSize
   *        The number of items in the principal output at that point.
   */
  public void sealPrincipalOutput (final int nSize)
  {
    m_nPrincipalSealedSize = nSize;
  }

  /** @return The size at which the principal output was sealed, or -1. */
  public int getPrincipalSealedSize ()
  {
    return m_nPrincipalSealedSize;
  }

  /** @return The stylesheet element of the principal module, or <code>null</code>. */
  @Nullable
  public XslElement getPrincipalRoot ()
  {
    return m_aSheet.getPrincipalModule ().getRoot ();
  }
}
