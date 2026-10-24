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
package com.helger.xpath.context;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.BooleanSupplier;

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
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * The dynamic context of an evaluation: the focus (context item, position,
 * size), variable bindings and run-wide settings. Focus and variable changes
 * create a derived context that shares the run-wide settings
 * ({@link #withFocus(IXdmItem, int, int)}, {@link #withVariable(XdmQName, XdmSequence)}),
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
    private ICommonsOrderedMap <String, String> m_aEnvironmentVariables = new CommonsLinkedHashMap <> ();
    private String m_sDefaultLanguage = DEFAULT_LANGUAGE;
  }

  /** The default language unless set otherwise. */
  public static final String DEFAULT_LANGUAGE = "en";

  /** Maximum depth of nested function calls before XPDY0130 is raised. */
  public static final int MAX_CALL_DEPTH = 10_000;

  private final StaticContext m_aStatic;
  private final RunState m_aRun;
  private final IXdmItem m_aItem;
  private final int m_nPosition;
  private final int m_nSize;
  // Persistent linked list of bindings, innermost first
  private final VarBinding m_aVars;

  private record VarBinding (XdmQName name, XdmSequence value, VarBinding next)
  {}

  private DynamicContext (@NonNull final StaticContext aStatic,
                          @NonNull final RunState aRun,
                          @Nullable final IXdmItem aItem,
                          final int nPosition,
                          final int nSize,
                          @Nullable final VarBinding aVars)
  {
    m_aStatic = aStatic;
    m_aRun = aRun;
    m_aItem = aItem;
    m_nPosition = nPosition;
    m_nSize = nSize;
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
    this (ValueEnforcer.notNull (aStatic, "Static"), new RunState (), null, 0, 0, null);
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

  @NonNull
  public DynamicContext withFocus (@Nullable final IXdmItem aItem, final int nPosition, final int nSize)
  {
    return new DynamicContext (m_aStatic, m_aRun, aItem, nPosition, nSize, m_aVars);
  }

  @NonNull
  public DynamicContext withVariable (@NonNull final XdmQName aName, @NonNull final XdmSequence aValue)
  {
    return new DynamicContext (m_aStatic, m_aRun, m_aItem, m_nPosition, m_nSize, new VarBinding (aName, aValue, m_aVars));
  }

  /** @return A context with the same run state and static context but no focus and no variables. */
  @NonNull
  public DynamicContext withoutFocusAndVariables ()
  {
    return new DynamicContext (m_aStatic, m_aRun, null, 0, 0, null);
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
        return aCur.value ();
    return null;
  }

  @NonNull
  public OffsetDateTime getCurrentDateTime ()
  {
    return m_aRun.m_aCurrentDateTime;
  }

  /** Fix the value <code>fn:current-dateTime</code> returns. */
  public void setCurrentDateTime (@NonNull final OffsetDateTime aValue)
  {
    m_aRun.m_aCurrentDateTime = aValue;
    m_aRun.m_nImplicitTimezoneMinutes = aValue.getOffset ().getTotalSeconds () / 60;
  }

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
      public XdmNode resolveDocument (final String sAbsoluteUri)
      {
        return null;
      }

      public String resolveText (final String sAbsoluteUri, final String sEncoding)
      {
        return null;
      }
    };
  }

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
   * Called by the evaluator in loops and calls.
   *
   * @throws XPathException
   *         FOER0000 if the interrupt hook asked for cancellation.
   */
  public void checkInterrupt ()
  {
    if (m_aRun.m_aInterrupt != null && m_aRun.m_aInterrupt.getAsBoolean ())
      throw new XPathException ("FOER0000", "Evaluation was cancelled");
  }

  /** Enter a function call. @throws XPathException XPDY0130 when too deep. */
  public void enterCall ()
  {
    if (++m_aRun.m_nCallDepth > MAX_CALL_DEPTH)
      throw new XPathException ("XPDY0130", "Function call nesting is too deep");
  }

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
}
