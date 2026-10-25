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
package com.helger.xslt.runtime;

import java.util.ArrayDeque;
import java.util.List;
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
import com.helger.xpath.XPathException;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.collation.ICollation;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.NodeSorter;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.EIDKind;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;
import com.helger.xslt.compile.StylesheetCompiler;
import com.helger.xslt.model.AttributeSet;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.EOnNoMatch;
import com.helger.xslt.model.KeyDefinition;
import com.helger.xslt.model.Mode;
import com.helger.xslt.model.OutputDefinition;
import com.helger.xslt.model.SpaceRules;
import com.helger.xslt.model.Template;
import com.helger.xslt.model.TemplateRule;
import com.helger.xslt.model.VariableDef;
import com.helger.xslt.pkg.PackageModel;
import com.helger.xslt.pkg.PackageUnit;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslModule;

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

  private record KeyIndexId (@NonNull XdmQName name, @NonNull XdmNode root, @Nullable PackageUnit unit)
  {}

  private final CompiledStylesheet m_aSheet;
  private final XsltTransformRequest m_aRequest;
  private final DynamicContext m_aRootDyn;
  private final DynamicContext m_aPatternDyn;
  private IXdmItem m_aGlobalContextItem;
  private final ICommonsMap <XdmQName, XdmSequence> m_aGlobalValues = new CommonsHashMap <> ();
  private final ICommonsSet <XdmQName> m_aGlobalsInProgress = new CommonsHashSet <> ();
  private final ICommonsMap <VariableDef, XdmSequence> m_aOriginalValues = new CommonsHashMap <> ();
  private final ICommonsSet <VariableDef> m_aOriginalsInProgress = new CommonsHashSet <> ();
  private final ICommonsMap <KeyIndexId, ICommonsMap <Object, ICommonsList <XdmNode>>> m_aKeyIndexes = new CommonsHashMap <> ();
  private final ICommonsSet <XdmQName> m_aKeysInProgress = new CommonsHashSet <> ();
  private final ICommonsList <String> m_aMessages = new CommonsArrayList <> ();
  private final ICommonsList <XsltTransformResult.SecondaryResult> m_aSecondaryResults = new CommonsArrayList <> ();
  private IXsltOutput m_aPrincipalOutput;
  private OutputDefinition m_aPrincipalOutputDefinition;
  private int m_nPrincipalSealedSize = -1;
  private final ICommonsMap <Object, Object> m_aRunData = new CommonsHashMap <> ();
  private final long m_nMaxSteps;
  private final int m_nMaxDepth;
  private long m_nSteps;
  private int m_nDepth;

  public Engine (@NonNull final CompiledStylesheet aSheet, @NonNull final XsltTransformRequest aRequest)
  {
    m_aSheet = aSheet;
    m_aRequest = aRequest;
    m_nMaxSteps = aRequest.getMaxSteps () > 0 ? aRequest.getMaxSteps () : DEFAULT_MAX_STEPS;
    m_nMaxDepth = aRequest.getMaxDepth () > 0 ? Math.min (aRequest.getMaxDepth (), DEFAULT_MAX_DEPTH) : DEFAULT_MAX_DEPTH;
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
    public XdmNode resolveDocument (@NonNull final String sAbsoluteUri)
    {
      final XdmNode aDoc = m_aDelegate == null ? null : m_aDelegate.resolveDocument (sAbsoluteUri);
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
    if (++m_nSteps > m_nMaxSteps)
      throw new XPathException ("XPDY0130", "The transformation executed more than " + m_nMaxSteps + " instructions");
    if ((m_nSteps & 1023) == 0)
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
    if (++m_nDepth > m_nMaxDepth)
    {
      m_nDepth--;
      throw new XPathException ("XPDY0130", "Templates and functions are nested more than " + m_nMaxDepth + " levels deep");
    }
  }

  public void leaveDepth ()
  {
    m_nDepth--;
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

  @NonNull
  private XdmSequence _computeGlobal (@NonNull final VariableDef aDef)
  {
    final XdmQName aName = aDef.getName ();
    final String sVisibility = aDef.getElement ().getAttr ("visibility");
    if (sVisibility != null && "abstract".equals (sVisibility.trim ()))
      throw XsltException.at (aDef.getElement (), "XTDE3052", "The abstract variable $" + aName.getEQName () + " was not overridden");
    XsltContext aCtx = createRootContext ().withState (XsltState.EMPTY);
    // A global of a used library package has no global context item
    if (m_aGlobalContextItem != null && (!m_aSheet.isMultiPackage () || m_aSheet.getPackages ().isInPrincipal (aDef.getElement ())))
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
    final XdmSequence aStatic = m_aSheet.staticValues ().get (aName);
    if (aStatic != null)
      return aStatic;
    final XdmSequence aKnown = m_aGlobalValues.get (aName);
    if (aKnown != null)
      return aKnown;
    final VariableDef aDef = m_aSheet.globals ().get (aName);
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
   * xsl:strip-space/xsl:preserve-space (XSLT 3.0 section 4.3). Returns the
   * same document if nothing is stripped.
   *
   * @param aDoc
   *        Document (or other root).
   * @return The stripped copy or the document itself.
   */
  @NonNull
  public XdmNode stripWhitespace (@NonNull final XdmNode aDoc)
  {
    final SpaceRules aRules = m_aSheet.getSpaceRules ();
    if (aRules.isNoStripping ())
      return aDoc;
    return WhitespaceStripper.strip (aDoc, aRules);
  }

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
   */
  @Nullable
  public TemplateRule findRule (@NonNull final Mode aMode, @NonNull final IXdmItem aItem, @NonNull final XsltContext aCtx)
  {
    return _findRule (aMode, aItem, aCtx, null, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

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
   * Bind the parameters of a template and run its body.
   *
   * @param aTemplate
   *        Template.
   * @param aBase
   *        Context with focus and state of the invocation, no variables.
   * @param aParams
   *        Supplied parameters.
   * @param bCallTemplate
   *        <code>true</code> for xsl:call-template (unknown non-tunnel
   *        parameters were rejected statically).
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

  private void _applyToChildren (@NonNull final XdmNode aNode,
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
   * Apply the built-in template rule of a mode (XSLT 3.0 section 6.7).
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
   */
  public void applyBuiltIn (@NonNull final Mode aMode,
                            @NonNull final IXdmItem aItem,
                            @NonNull final XsltContext aCtx,
                            @NonNull final List <ParamValue> aParams,
                            @NonNull final IXsltOutput aOut)
  {
    final EOnNoMatch eOnNoMatch = aMode.getOnNoMatch ();
    if (aItem instanceof IXdmFunction)
      throw new XPathException ("XTDE0555", "No template rule matches a function item, and there is no built-in rule for it");
    if (eOnNoMatch == EOnNoMatch.FAIL)
      throw new XPathException ("XTDE0555", "No template rule matches the item and the mode " + aMode + " has on-no-match='fail'");
    if (aItem instanceof final XdmArray aArray)
    {
      if (eOnNoMatch == EOnNoMatch.DEEP_SKIP)
        return;
      final XdmSequence.Builder aMembers = new XdmSequence.Builder ();
      for (final XdmSequence aMember : aArray.members ())
        aMembers.addAll (aMember);
      applyTemplates (aCtx, aMembers.build (), aMode, aParams, aOut);
      return;
    }
    if (!(aItem instanceof final XdmNode aNode))
    {
      // Atomic values and maps
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
        aOut.appendNew (NodeCopier.copyDeep (aNode, true));
        break;
      case SHALLOW_COPY:
        if (eKind == ENodeKind.ELEMENT)
        {
          final XdmNode aCopy = NodeCopier.copyShallow (aNode);
          NodeCopier.copyNamespaces (aNode, aCopy, true);
          final ContentOutput aContent = new ContentOutput (aCopy);
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
              final XdmNode aDoc = XdmNode.createDocument ();
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
   * xsl:apply-templates: process each item with the best matching rule of the
   * mode, or the built-in rule.
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
    {
      final IXdmItem aItem = aItems.get (i);
      final TemplateRule aRule = findRule (aMode, aItem, aCaller);
      if (aRule != null)
        _invokeRule (aRule, aMode, aCaller, aItem, i + 1, nSize, aParams, aOut);
      else
      {
        // The built-in rule runs with the focus on the item and keeps the mode
        final XsltContext aCtx = aCaller.withoutFocusAndVariables ()
                                        .withFocus (aItem, i + 1, nSize)
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
  }

  /**
   * xsl:next-match and xsl:apply-imports.
   *
   * @param aCtx
   *        Context of the instruction.
   * @param bImports
   *        <code>true</code> for xsl:apply-imports.
   * @param aParams
   *        Parameters.
   * @param aOut
   *        Output.
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
   * xsl:call-template (also the initial template).
   *
   * @param aCaller
   *        Context of the caller: its focus stays.
   * @param aTemplate
   *        The template.
   * @param aParams
   *        Parameters.
   * @param aOut
   *        Output.
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

  private static void _addToIndex (@NonNull final ICommonsMap <Object, ICommonsList <XdmNode>> aIndex,
                                   @NonNull final Object aKey,
                                   @NonNull final XdmNode aNode)
  {
    final ICommonsList <XdmNode> aList = aIndex.computeIfAbsent (aKey, k -> new CommonsArrayList <> ());
    if (aList.isEmpty () || aList.getLast () != aNode)
      aList.add (aNode);
  }

  private void _indexNode (@NonNull final XdmNode aNode,
                           @NonNull final ICommonsList <KeyDefinition> aDefs,
                           @NonNull final XsltContext aUseCtx,
                           @Nullable final ICollation aCollation,
                           final boolean bComposite,
                           @NonNull final ICommonsMap <Object, ICommonsList <XdmNode>> aIndex)
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

  @NonNull
  private ICommonsMap <Object, ICommonsList <XdmNode>> _buildKeyIndex (@NonNull final ICommonsList <KeyDefinition> aDefs,
                                                                     @NonNull final XdmNode aRoot,
                                                                     @NonNull final XsltContext aCtx)
  {
    final ICommonsMap <Object, ICommonsList <XdmNode>> ret = new CommonsHashMap <> ();
    final String sCollation = aDefs.getFirst ().getCollation ();
    final ICollation aCollation = CollationRegistry.CODEPOINT_URI.equals (sCollation) ? null
                                                                                       : CollationRegistry.getCollationOrThrow (sCollation);
    final boolean bComposite = aDefs.getFirst ().isComposite ();
    final XsltContext aUseCtx = createRootContext ().withState (XsltState.EMPTY);
    boolean bNamespaces = false;
    for (final KeyDefinition aDef : aDefs)
      if (aDef.getMatch ().pattern ().getSource ().contains ("namespace-node"))
        bNamespaces = true;
    final ArrayDeque <XdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final XdmNode aNode = aStack.pop ();
      _indexNode (aNode, aDefs, aUseCtx, aCollation, bComposite, ret);
      if (bNamespaces)
        for (final XdmNode aNS : aNode.getNamespaceNodes ())
          _indexNode (aNS, aDefs, aUseCtx, aCollation, bComposite, ret);
      for (int i = 0; i < aNode.getAttributeCount (); ++i)
        _indexNode (aNode.getAttributeAtIndex (i), aDefs, aUseCtx, aCollation, bComposite, ret);
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.push (aNode.getChildAtIndex (i));
    }
    return ret;
  }

  /**
   * fn:key.
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
   */
  @NonNull
  public XdmSequence findKeyNodes (@NonNull final XsltContext aCtx,
                                   @NonNull final XdmQName aName,
                                   @NonNull final XdmSequence aValues,
                                   @NonNull final XdmNode aTop)
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
    final XdmNode aRoot = aTop.getRoot ();
    final KeyIndexId aId = new KeyIndexId (aName, aRoot, aUnit);
    ICommonsMap <Object, ICommonsList <XdmNode>> aIndex = m_aKeyIndexes.get (aId);
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
      final ICommonsList <XdmNode> aNodes = aIndex.get (aKey);
      if (aNodes != null)
        for (final XdmNode aNode : aNodes)
          aResult.add (aNode);
    }
    else
      for (final IXdmItem aAtom : aAtoms)
      {
        final Object aKey = _getKeyValue ((XdmAtomic) aAtom, aCollation);
        final ICommonsList <XdmNode> aNodes = aKey == NAN_KEY ? null : aIndex.get (aKey);
        if (aNodes != null)
          for (final XdmNode aNode : aNodes)
            aResult.add (aNode);
      }
    XdmSequence ret = NodeSorter.sortAndDeduplicate (aResult.build ());
    if (aTop != aRoot)
    {
      final XdmSequence.Builder aFiltered = new XdmSequence.Builder ();
      for (final IXdmItem aItem : ret)
        for (XdmNode aCur = (XdmNode) aItem; aCur != null; aCur = aCur.getParent ())
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
  public XdmNode getDocument (@NonNull final XsltContext aCtx, @NonNull final String sUri)
  {
    String sDocUri = sUri;
    String sFragment = null;
    final int nHash = sUri.indexOf ('#');
    if (nHash >= 0)
    {
      sDocUri = sUri.substring (0, nHash);
      sFragment = sUri.substring (nHash + 1);
    }
    XdmNode aDoc;
    final String sKey = "fn:doc#" + sDocUri;
    final ICommonsMap <String, Object> aCache = m_aRootDyn.getRunCache ();
    if (aCache.get (sKey) instanceof final XdmNode aCached)
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
    final ArrayDeque <XdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aDoc);
    while (!aStack.isEmpty ())
    {
      final XdmNode aCur = aStack.pop ();
      if (aCur.isElement ())
      {
        final XdmNode aXmlId = aCur.getAttribute (XdmConstants.NS_XML, "id");
        if (aXmlId != null && sFragment.equals (aXmlId.getValue ().trim ()))
          return aCur;
        for (int i = 0; i < aCur.getAttributeCount (); ++i)
        {
          final XdmNode aAttr = aCur.getAttributeAtIndex (i);
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
