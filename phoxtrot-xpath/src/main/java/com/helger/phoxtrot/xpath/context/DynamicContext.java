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
package com.helger.phoxtrot.xpath.context;

import java.time.OffsetDateTime;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * The dynamic context of an evaluation: the focus (context item, position,
 * size), variable bindings and run-wide settings. Focus and variable changes
 * create a derived context that shares the run-wide settings
 * ({@link #withFocus(IXdmItem, int, int)}, {@link #withVariable(XdmQName, XdmSequence)},
 * {@link #withLazyVariable(XdmQName, Supplier)}),
 * so a context is never modified while another evaluation reads it. The
 * run-wide part (current date/time, resolver, interrupt, document cache) is
 * shared by reference.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class DynamicContext
{
  /** Shared by all contexts derived from one root context. */
  private static final class RunState
  {
    private OffsetDateTime m_aCurrentDateTime = OffsetDateTime.now ();
    private int m_nImplicitTimezoneMinutes = m_aCurrentDateTime.getOffset ().getTotalSeconds () / 60;
    private IResourceResolver m_aResolver = null;
    private BooleanSupplier m_aInterrupt = null;
    private final ICommonsMap <String, Object> m_aCache = new CommonsHashMap <> ();
    private int m_nCallDepth = 0;
    private long m_nSteps = 0;
    private long m_nMaxSteps = UNLIMITED_STEPS;
    private Object m_aHost = null;
    private ICommonsOrderedMap <String, String> m_aEnvironmentVariables = new CommonsLinkedHashMap <> ();
    private String m_sDefaultLanguage = DEFAULT_LANGUAGE;
    private BiFunction <DynamicContext, XdmQName, XdmSequence> m_aGlobalVariableResolver = null;
    private Map <Object, Object> m_aMemo = null;
    private IXdmItem m_aLoopFocus = null;
  }

  /** The token of a variable that is not bound locally (see {@link #getVariableToken(XdmQName)}). */
  private static final Object NOT_LOCAL_TOKEN = new Object ();

  /** The default language unless set otherwise. */
  public static final String DEFAULT_LANGUAGE = "en";

  /** Maximum depth of nested function calls before XPDY0130 is raised. */
  public static final int MAX_CALL_DEPTH = 10_000;

  /** The value of {@link #setMaxSteps(long)} for "no step limit", the default. */
  public static final long UNLIMITED_STEPS = Long.MAX_VALUE;

  private final StaticContext m_aStatic;
  private final RunState m_aRun;
  private final IXdmItem m_aItem;
  private final IXdmItem m_aCurrentItem;
  private final int m_nPosition;
  private final int m_nSize;
  private final Object m_aHostState;
  // Persistent linked list of bindings, innermost first
  private final VarBinding m_aVars;

  /**
   * A variable bound to a value or, if <code>lazy</code> is not
   * <code>null</code>, to a value computed on first use.
   */
  private record VarBinding (XdmQName name, XdmSequence value, LazyValue lazy, VarBinding next)
  {}

  /**
   * The memoising thunk of a lazily bound variable: evaluated on first use,
   * then the value is kept. A failure is kept as well and raised again on
   * every later use, so that every reference sees the same outcome.
   */
  private static final class LazyValue
  {
    private Supplier <XdmSequence> m_aSupplier;
    private XdmSequence m_aValue;
    private RuntimeException m_aFailure;

    /**
     * @param aSupplier
     *        Computes the value; called at most once.
     */
    LazyValue (@NonNull final Supplier <XdmSequence> aSupplier)
    {
      m_aSupplier = aSupplier;
    }

    /**
     * @return The value, computed by the supplier on the first call and kept.
     * @throws RuntimeException
     *         the exception the supplier raised, on the first and on every
     *         later call
     * @throws IllegalStateException
     *         if the supplier asks for this same value while computing it (a
     *         variable that depends on itself)
     */
    @NonNull
    XdmSequence get ()
    {
      if (m_aValue != null)
        return m_aValue;
      if (m_aFailure != null)
        throw m_aFailure;
      final Supplier <XdmSequence> aSupplier = m_aSupplier;
      if (aSupplier == null)
        throw new IllegalStateException ("A lazily bound variable depends on its own value");
      // Released before the evaluation: the supplier (and the context it holds)
      // is no longer needed afterwards, and a re-entry is detected
      m_aSupplier = null;
      try
      {
        final XdmSequence aValue = aSupplier.get ();
        ValueEnforcer.notNull (aValue, "Value");
        m_aValue = aValue;
        return aValue;
      }
      catch (final RuntimeException ex)
      {
        m_aFailure = ex;
        throw ex;
      }
    }
  }

  private DynamicContext (@NonNull final StaticContext aStatic,
                          @NonNull final RunState aRun,
                          @Nullable final IXdmItem aItem,
                          @Nullable final IXdmItem aCurrentItem,
                          final int nPosition,
                          final int nSize,
                          @Nullable final Object aHostState,
                          @Nullable final VarBinding aVars)
  {
    m_aStatic = aStatic;
    m_aRun = aRun;
    m_aItem = aItem;
    m_aCurrentItem = aCurrentItem;
    m_nPosition = nPosition;
    m_nSize = nSize;
    m_aHostState = aHostState;
    m_aVars = aVars;
  }

  /**
   * Create a root context without focus.
   *
   * @param aStatic
   *        The static context of the expression.
   */
  public DynamicContext (@NonNull final StaticContext aStatic)
  {
    this (ValueEnforcer.notNull (aStatic, "Static"), new RunState (), null, null, 0, 0, null, null);
  }

  @NonNull
  public StaticContext getStaticContext ()
  {
    return m_aStatic;
  }

  /** @return The context item or <code>null</code> if the focus is absent. */
  @Nullable
  public IXdmItem getContextItem ()
  {
    return m_aItem;
  }

  /** @return The 1-based context position; 0 if the focus is absent. */
  @Nonnegative
  public int getPosition ()
  {
    return m_nPosition;
  }

  @Nonnegative
  public int getSize ()
  {
    return m_nSize;
  }

  /**
   * @param aItem
   *        The new context item, <code>null</code> for an absent focus.
   * @param nPosition
   *        The 1-based context position, 0 with an absent focus.
   * @param nSize
   *        The context size, 0 with an absent focus.
   * @return A derived context with that focus. Variables, current item, host
   *         state and run state are kept.
   */
  @NonNull
  public DynamicContext withFocus (@Nullable final IXdmItem aItem, final int nPosition, final int nSize)
  {
    return new DynamicContext (m_aStatic, m_aRun, aItem, m_aCurrentItem, nPosition, nSize, m_aHostState, m_aVars);
  }

  /**
   * @param aName
   *        Variable name.
   * @param aValue
   *        The value.
   * @return A derived context in which the variable is bound to the value,
   *         shadowing an earlier binding of the same name. This context is
   *         not changed.
   */
  @NonNull
  public DynamicContext withVariable (@NonNull final XdmQName aName, @NonNull final XdmSequence aValue)
  {
    return new DynamicContext (m_aStatic,
                               m_aRun,
                               m_aItem,
                               m_aCurrentItem,
                               m_nPosition,
                               m_nSize,
                               m_aHostState,
                               new VarBinding (aName, aValue, null, m_aVars));
  }

  /**
   * Bind a variable whose value is computed when it is first used, and only
   * then: a variable that is never used is never evaluated, so a dynamic error
   * in its value is not raised (XPath 3.1 section 2.3.4 leaves the order of
   * evaluation open). The value is computed once; later uses, also from
   * derived contexts, get the same value (or the same error).
   *
   * @param aName
   *        Variable name. May not be <code>null</code>.
   * @param aSupplier
   *        Computes the value. It must capture the context the variable is
   *        declared in (focus, variables), not the one it is used in. It is
   *        called at most once and must not return <code>null</code>.
   * @return The derived context.
   */
  @NonNull
  public DynamicContext withLazyVariable (@NonNull final XdmQName aName, @NonNull final Supplier <XdmSequence> aSupplier)
  {
    ValueEnforcer.notNull (aName, "Name");
    ValueEnforcer.notNull (aSupplier, "Supplier");
    return new DynamicContext (m_aStatic,
                               m_aRun,
                               m_aItem,
                               m_aCurrentItem,
                               m_nPosition,
                               m_nSize,
                               m_aHostState,
                               new VarBinding (aName, null, new LazyValue (aSupplier), m_aVars));
  }

  /**
   * @return A context with the same run state, static context and host state
   *         but no focus, no current item and no variables.
   */
  @NonNull
  public DynamicContext withoutFocusAndVariables ()
  {
    return new DynamicContext (m_aStatic, m_aRun, null, null, 0, 0, m_aHostState, null);
  }

  /**
   * Derive a context that evaluates an expression compiled with another static
   * context (XSLT: every stylesheet element has its own namespaces) while
   * keeping focus, current item, variables and the run-wide state.
   *
   * @param aStatic
   *        The other static context.
   * @return The derived context.
   */
  @NonNull
  public DynamicContext withStaticContext (@NonNull final StaticContext aStatic)
  {
    ValueEnforcer.notNull (aStatic, "Static");
    return new DynamicContext (aStatic, m_aRun, m_aItem, m_aCurrentItem, m_nPosition, m_nSize, m_aHostState, m_aVars);
  }

  /**
   * @return The XSLT current item (<code>fn:current()</code>, XSLT 3.0 section
   *         20.4.1), or <code>null</code> if none is set. It survives focus
   *         changes inside the expression.
   */
  @Nullable
  public IXdmItem getCurrentItem ()
  {
    return m_aCurrentItem;
  }

  /**
   * @param aCurrentItem
   *        The new XSLT current item, may be <code>null</code>.
   * @return A derived context with that current item and everything else
   *         kept.
   */
  @NonNull
  public DynamicContext withCurrentItem (@Nullable final IXdmItem aCurrentItem)
  {
    return new DynamicContext (m_aStatic, m_aRun, m_aItem, aCurrentItem, m_nPosition, m_nSize, m_aHostState, m_aVars);
  }

  /**
   * @return The per-context state of the host (the XSLT engine: current
   *         template rule, current group, captured regex groups, ...), or
   *         <code>null</code>. Unlike {@link #getHost()} it belongs to this
   *         context and is carried into every context derived from it, also
   *         into closures of inline function items.
   */
  @Nullable
  public Object getHostState ()
  {
    return m_aHostState;
  }

  /**
   * @param aHostState
   *        The new per-context host state. May be <code>null</code>.
   * @return A derived context with that host state.
   */
  @NonNull
  public DynamicContext withHostState (@Nullable final Object aHostState)
  {
    return new DynamicContext (m_aStatic, m_aRun, m_aItem, m_aCurrentItem, m_nPosition, m_nSize, aHostState, m_aVars);
  }

  /**
   * @return The run-wide host object (the XSLT engine of the current
   *         transformation), or <code>null</code> for plain XPath. Host
   *         functions use it to reach their runtime state.
   */
  @Nullable
  public Object getHost ()
  {
    return m_aRun.m_aHost;
  }

  /**
   * Set the run-wide host object; it is visible in every context of the
   * run, including those derived before this call.
   *
   * @param aHost
   *        The host object, may be <code>null</code>.
   */
  public void setHost (@Nullable final Object aHost)
  {
    m_aRun.m_aHost = aHost;
  }

  /**
   * @param aName
   *        Variable name.
   * @return The value or <code>null</code> if the variable is not bound.
   */
  @Nullable
  public XdmSequence getVariable (@NonNull final XdmQName aName)
  {
    for (VarBinding aCur = m_aVars; aCur != null; aCur = aCur.next ())
      if (aCur.name ().equals (aName))
        return aCur.lazy () != null ? aCur.lazy ().get () : aCur.value ();
    final BiFunction <DynamicContext, XdmQName, XdmSequence> aResolver = m_aRun.m_aGlobalVariableResolver;
    return aResolver == null ? null : aResolver.apply (this, aName);
  }

  /**
   * Set the run-wide resolver for variables that are not bound in a context
   * (XSLT global variables and parameters, evaluated on first use). It is
   * asked after the local bindings, so a local binding shadows it.
   *
   * @param aResolver
   *        Returns the value of a variable or <code>null</code> if it is not
   *        known. May be <code>null</code> to have none.
   */
  public void setGlobalVariableResolver (@Nullable final Function <XdmQName, XdmSequence> aResolver)
  {
    m_aRun.m_aGlobalVariableResolver = aResolver == null ? null : (aCtx, aName) -> aResolver.apply (aName);
  }

  /**
   * Like {@link #setGlobalVariableResolver(Function)}, but the resolver also
   * receives the context in which the variable is referenced (e.g. to find
   * the XSLT package of the referencing expression from its static context).
   *
   * @param aResolver
   *        Returns the value of a variable or <code>null</code> if it is not
   *        known. May be <code>null</code> to have none.
   */
  public void setContextGlobalVariableResolver (@Nullable final BiFunction <DynamicContext, XdmQName, XdmSequence> aResolver)
  {
    m_aRun.m_aGlobalVariableResolver = aResolver;
  }

  /**
   * @return The value of <code>fn:current-dateTime</code>, stable for the
   *         whole run: the time the root context was created unless set
   *         with {@link #setCurrentDateTime(OffsetDateTime)}.
   */
  @NonNull
  public OffsetDateTime getCurrentDateTime ()
  {
    return m_aRun.m_aCurrentDateTime;
  }

  /**
   * Fix the value <code>fn:current-dateTime</code> returns. The implicit
   * timezone is set to the offset of that value as well.
   */
  public void setCurrentDateTime (@NonNull final OffsetDateTime aValue)
  {
    m_aRun.m_aCurrentDateTime = aValue;
    m_aRun.m_nImplicitTimezoneMinutes = aValue.getOffset ().getTotalSeconds () / 60;
  }

  /**
   * @return The implicit timezone as an offset in minutes from UTC; by
   *         default the offset of the current date/time.
   */
  public int getImplicitTimezoneMinutes ()
  {
    return m_aRun.m_nImplicitTimezoneMinutes;
  }

  public void setImplicitTimezoneMinutes (final int nMinutes)
  {
    m_aRun.m_nImplicitTimezoneMinutes = nMinutes;
  }

  /** @return The resolver, never <code>null</code>; a resolver that finds nothing by default. */
  @NonNull
  public IResourceResolver getResourceResolver ()
  {
    final IResourceResolver ret = m_aRun.m_aResolver;
    return ret != null ? ret : new IResourceResolver ()
    {
      public IXdmNode resolveDocument (final String sAbsoluteUri)
      {
        return null;
      }

      public String resolveText (final String sAbsoluteUri, final String sEncoding)
      {
        return null;
      }
    };
  }

  /**
   * @param aResolver
   *        The run-wide resource resolver, <code>null</code> for one that
   *        resolves nothing.
   */
  public void setResourceResolver (@Nullable final IResourceResolver aResolver)
  {
    m_aRun.m_aResolver = aResolver;
  }

  /**
   * @param aInterrupt
   *        Polled regularly by long-running loops; return <code>true</code> to
   *        cancel with an error.
   */
  public void setInterrupt (@Nullable final BooleanSupplier aInterrupt)
  {
    m_aRun.m_aInterrupt = aInterrupt;
  }

  /**
   * @return The step limit of the run (see {@link #setMaxSteps(long)}),
   *         {@link #UNLIMITED_STEPS} by default.
   */
  public long getMaxSteps ()
  {
    return m_aRun.m_nMaxSteps;
  }

  /**
   * Limit the steps of the run, shared by every context derived from this one.
   * A step is one call of {@link #checkInterrupt()} (an iteration of a loop of
   * the evaluator or of a higher-order function) or of {@link #enterCall()} (a
   * function call), so the limit bounds the work of an expression that loops or
   * recurses without end. The step that exceeds the limit fails with XPDY0130.
   * An XSLT transformation has its own step limit
   * (<code>XsltTransformRequest.setMaxSteps</code>) and leaves this one
   * unlimited.
   *
   * @param nMaxSteps
   *        The maximum number of steps; {@link #UNLIMITED_STEPS} (the
   *        default) for no limit, a value of 0 or below allows no step at all.
   */
  public void setMaxSteps (final long nMaxSteps)
  {
    m_aRun.m_nMaxSteps = nMaxSteps;
  }

  /**
   * @return The number of steps of the run so far (see
   *         {@link #setMaxSteps(long)}).
   */
  public long getStepCount ()
  {
    return m_aRun.m_nSteps;
  }

  /**
   * Count one step of the run.
   *
   * @throws XPathException
   *         XPDY0130 if the step limit is exceeded.
   */
  private void _countStep ()
  {
    final RunState aRun = m_aRun;
    if (++aRun.m_nSteps > aRun.m_nMaxSteps)
      throw new XPathException ("XPDY0130", "The step limit of " + aRun.m_nMaxSteps + " was exceeded");
  }

  /**
   * Called by the evaluator in loops and calls. Counts one step (see
   * {@link #setMaxSteps(long)}).
   *
   * @throws XPathException
   *         FOER0000 if the interrupt hook asked for cancellation, XPDY0130 if
   *         the step limit is exceeded.
   */
  public void checkInterrupt ()
  {
    _countStep ();
    if (m_aRun.m_aInterrupt != null && m_aRun.m_aInterrupt.getAsBoolean ())
      throw new XPathException ("FOER0000", "Evaluation was cancelled");
  }

  /**
   * Enter a function call. Counts one step (see {@link #setMaxSteps(long)}).
   *
   * @throws XPathException
   *         XPDY0130 when too deep or when the step limit is exceeded.
   */
  public void enterCall ()
  {
    _countStep ();
    if (++m_aRun.m_nCallDepth > MAX_CALL_DEPTH)
      throw new XPathException ("XPDY0130", "Function call nesting is too deep");
  }

  /** Leave a function call entered with {@link #enterCall()}. */
  public void leaveCall ()
  {
    m_aRun.m_nCallDepth--;
  }

  /**
   * @return The environment variables <code>fn:environment-variable</code>
   *         and <code>fn:available-environment-variables</code> expose. Empty
   *         by default: the process environment is not exposed unless the
   *         host sets it explicitly (privacy).
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedMap <String, String> getEnvironmentVariables ()
  {
    return m_aRun.m_aEnvironmentVariables.getClone ();
  }

  /**
   * @param aVariables
   *        The environment variables to expose, e.g.
   *        <code>System.getenv ()</code>. <code>null</code> exposes none.
   */
  public void setEnvironmentVariables (@Nullable final Map <String, String> aVariables)
  {
    m_aRun.m_aEnvironmentVariables = aVariables == null ? new CommonsLinkedHashMap <> ()
                                                        : new CommonsLinkedHashMap <> (aVariables);
  }

  /**
   * @return The default language of the dynamic context (an xs:language
   *         value), returned by <code>fn:default-language</code> and used by
   *         the formatting functions when no language is given. Default is
   *         {@link #DEFAULT_LANGUAGE}.
   */
  @NonNull
  public String getDefaultLanguage ()
  {
    return m_aRun.m_sDefaultLanguage;
  }

  /**
   * @param sLanguage
   *        The default language, e.g. <code>fr-CA</code>. May neither be
   *        <code>null</code> nor empty.
   */
  public void setDefaultLanguage (@NonNull final String sLanguage)
  {
    m_aRun.m_sDefaultLanguage = ValueEnforcer.notEmpty (sLanguage, "Language");
  }

  /** @return A per-run cache for functions (e.g. documents by URI) that must be stable within a run. */
  @NonNull
  public ICommonsMap <String, Object> getRunCache ()
  {
    return m_aRun.m_aCache;
  }

  /**
   * An identity token for the binding of a variable in this context, for
   * memoising evaluations that depend on the variable (see
   * {@link #getRunMemo()}): two contexts of one run give the same token for a
   * name exactly if they see the same local binding, which always has the same
   * value. All variables that are not bound locally (global variables of the
   * run, or unknown ones) share one token.
   *
   * @param aName
   *        The variable name.
   * @return The token. Never <code>null</code>.
   */
  @NonNull
  public Object getVariableToken (@NonNull final XdmQName aName)
  {
    for (VarBinding aCur = m_aVars; aCur != null; aCur = aCur.next ())
      if (aCur.name ().equals (aName))
        return aCur;
    return NOT_LOCAL_TOKEN;
  }

  /**
   * @return A per-run memo by identity for the evaluator, e.g. the last value
   *         of a function call by its expression node. Created on first use.
   *         Never <code>null</code>.
   */
  @NonNull
  public Map <Object, Object> getRunMemo ()
  {
    Map <Object, Object> ret = m_aRun.m_aMemo;
    if (ret == null)
    {
      ret = new IdentityHashMap <> ();
      m_aRun.m_aMemo = ret;
    }
    return ret;
  }

  /**
   * @return The context item of the innermost <code>for</code>,
   *         <code>some</code> or <code>every</code> expression being evaluated
   *         in this run, for the evaluator. <code>null</code> if there is none or
   *         it had no context item.
   */
  @Nullable
  public IXdmItem getLoopFocus ()
  {
    return m_aRun.m_aLoopFocus;
  }

  /**
   * Set the context item of the innermost loop being evaluated, for the
   * evaluator. The caller restores the previous value when the loop ends.
   *
   * @param aItem
   *        The context item of the loop. May be <code>null</code>.
   * @return The previous value. May be <code>null</code>.
   */
  @Nullable
  public IXdmItem setLoopFocus (@Nullable final IXdmItem aItem)
  {
    final IXdmItem ret = m_aRun.m_aLoopFocus;
    m_aRun.m_aLoopFocus = aItem;
    return ret;
  }
}
