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
package com.helger.xpath.spec;

import static org.junit.Assert.fail;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;

/**
 * Helper for the specification-based XPath tests, ported from go-xslt's
 * <code>*_spec_test.go</code> files. A case is an expression and either the
 * expected string value of its result (see {@link #str(XdmSequence)}) or the
 * W3C error code it must raise. A table run checks all cases and reports all
 * mismatches at once.
 *
 * @author Philip Helger
 */
public final class SpecTestHelper
{
  /**
   * One expression and what the specification says it yields.
   *
   * @param expr
   *        The XPath expression.
   * @param want
   *        The expected string value of the result (ignored if code is set).
   * @param code
   *        The expected error code or <code>null</code>.
   */
  public record SpecCase (@NonNull String expr, @Nullable String want, @Nullable String code)
  {}

  /**
   * The environment of an evaluation: source document (the context item is
   * its document node), namespaces, variables and further adjustments.
   */
  public static final class Env
  {
    private String m_sSource = "<r/>";
    private IXdmItem m_aContextItem;
    private boolean m_bNoContext;
    private final ICommonsOrderedMap <String, String> m_aNamespaces = new CommonsLinkedHashMap <> ();
    private final ICommonsOrderedMap <XdmQName, XdmSequence> m_aVars = new CommonsLinkedHashMap <> ();
    private Consumer <StaticContext> m_aStaticSetup;
    private UnaryOperator <DynamicContext> m_aDynamicSetup;

    /**
     * @param sSource
     *        The source document; its document node is the context item.
     * @return this
     */
    @NonNull
    public Env src (@NonNull final String sSource)
    {
      m_sSource = sSource;
      return this;
    }

    /**
     * @param aItem
     *        The context item (instead of the source document).
     * @return this
     */
    @NonNull
    public Env contextItem (@NonNull final IXdmItem aItem)
    {
      m_aContextItem = aItem;
      return this;
    }

    /** @return this, with an absent focus */
    @NonNull
    public Env noContext ()
    {
      m_bNoContext = true;
      return this;
    }

    @NonNull
    public Env ns (@NonNull final String sPrefix, @NonNull final String sUri)
    {
      m_aNamespaces.put (sPrefix, sUri);
      return this;
    }

    /**
     * @param sLocalName
     *        Variable name (no namespace).
     * @param aValue
     *        Value.
     * @return this
     */
    @NonNull
    public Env var (@NonNull final String sLocalName, @NonNull final XdmSequence aValue)
    {
      m_aVars.put (new XdmQName (sLocalName), aValue);
      return this;
    }

    @NonNull
    public Env staticSetup (@NonNull final Consumer <StaticContext> aSetup)
    {
      m_aStaticSetup = aSetup;
      return this;
    }

    @NonNull
    public Env dynamicSetup (@NonNull final UnaryOperator <DynamicContext> aSetup)
    {
      m_aDynamicSetup = aSetup;
      return this;
    }
  }

  @PresentForCodeCoverage
  private static final SpecTestHelper INSTANCE = new SpecTestHelper ();

  private SpecTestHelper ()
  {}

  @NonNull
  public static SpecCase ok (@NonNull final String sExpr, @NonNull final String sWant)
  {
    return new SpecCase (sExpr, sWant, null);
  }

  @NonNull
  public static SpecCase err (@NonNull final String sExpr, @NonNull final String sCode)
  {
    return new SpecCase (sExpr, null, sCode);
  }

  @NonNull
  public static Env env ()
  {
    return new Env ();
  }

  @NonNull
  public static Env env (@NonNull final String sSource)
  {
    return new Env ().src (sSource);
  }

  @NonNull
  public static XdmNode parse (@NonNull final String sXml)
  {
    try
    {
      return new XdmParser ().parse (sXml);
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalArgumentException ("Failed to parse " + sXml, ex);
    }
  }

  /**
   * Evaluate an expression.
   *
   * @param sExpr
   *        Expression.
   * @param aEnv
   *        Environment.
   * @return The result.
   * @throws XPathException
   *         on a static or dynamic error
   */
  @NonNull
  public static XdmSequence eval (@NonNull final String sExpr, @NonNull final Env aEnv)
  {
    final StaticContext aSC = new StaticContext ();
    for (final var aEntry : aEnv.m_aNamespaces.entrySet ())
      aSC.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    for (final XdmQName aName : aEnv.m_aVars.keySet ())
      aSC.declareVariable (aName);
    if (aEnv.m_aStaticSetup != null)
      aEnv.m_aStaticSetup.accept (aSC);
    final CompiledXPath aXP = XPathCompiler.compile (sExpr, aSC);
    DynamicContext aCtx = aXP.createDynamicContext ();
    if (!aEnv.m_bNoContext)
    {
      final IXdmItem aItem = aEnv.m_aContextItem != null ? aEnv.m_aContextItem : parse (aEnv.m_sSource);
      aCtx = aCtx.withFocus (aItem, 1, 1);
    }
    for (final var aEntry : aEnv.m_aVars.entrySet ())
      aCtx = aCtx.withVariable (aEntry.getKey (), aEntry.getValue ());
    if (aEnv.m_aDynamicSetup != null)
      aCtx = aEnv.m_aDynamicSetup.apply (aCtx);
    return aXP.evaluate (aCtx);
  }

  @NonNull
  public static XdmSequence eval (@NonNull final String sExpr)
  {
    return eval (sExpr, env ());
  }

  /**
   * The string form go-xslt's tests compare (its <code>ToString</code>): the
   * string value of the first item; the empty string for an empty sequence,
   * a map, an array or a function.
   *
   * @param aSeq
   *        Sequence.
   * @return The string.
   */
  @NonNull
  public static String str (@NonNull final XdmSequence aSeq)
  {
    if (aSeq.isEmpty ())
      return "";
    final IXdmItem aItem = aSeq.get (0);
    if (aItem instanceof final XdmAtomic aAtomic)
      return aAtomic.getLexical ();
    if (aItem instanceof final XdmNode aNode)
      return aNode.getStringValue ();
    return "";
  }

  @NonNull
  public static String str (@NonNull final String sExpr)
  {
    return str (eval (sExpr));
  }

  /**
   * @param sExpr
   *        Expression.
   * @param aEnv
   *        Environment.
   * @return The error code, or <code>null</code> if the expression succeeds.
   */
  @Nullable
  public static String errorCode (@NonNull final String sExpr, @NonNull final Env aEnv)
  {
    try
    {
      eval (sExpr, aEnv);
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  /**
   * Check one case.
   *
   * @param aEnv
   *        Environment.
   * @param aCase
   *        Case.
   * @return <code>null</code> if it passes, the description of the mismatch
   *         otherwise.
   */
  @Nullable
  public static String check (@NonNull final Env aEnv, @NonNull final SpecCase aCase)
  {
    final XdmSequence aResult;
    try
    {
      aResult = eval (aCase.expr (), aEnv);
    }
    catch (final XPathException ex)
    {
      if (aCase.code () == null)
        return aCase.expr () + ": unexpected error " + ex.getCode () + ": " + ex.getMessage ();
      if (!aCase.code ().equals (ex.getCode ()))
        return aCase.expr () + ": error " + ex.getCode () + " (" + ex.getMessage () + "), want " + aCase.code ();
      return null;
    }
    catch (final RuntimeException ex)
    {
      return aCase.expr () + ": unexpected exception " + ex;
    }
    final String sGot = str (aResult);
    if (aCase.code () != null)
      return aCase.expr () + ": got '" + sGot + "', want error " + aCase.code ();
    if (!sGot.equals (aCase.want ()))
      return aCase.expr () + " = '" + sGot + "', want '" + aCase.want () + "'";
    return null;
  }

  /**
   * Run a table of cases and fail with all mismatches.
   *
   * @param aEnv
   *        Environment.
   * @param aCases
   *        Cases.
   */
  public static void run (@NonNull final Env aEnv, @NonNull final SpecCase... aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final SpecCase aCase : aCases)
    {
      final String sError = check (aEnv, aCase);
      if (sError != null)
        aErrors.add (sError);
    }
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " of " + aCases.length + " cases failed:\n  " + String.join ("\n  ", aErrors));
  }

  public static void run (@NonNull final String sSource, @NonNull final SpecCase... aCases)
  {
    run (env (sSource), aCases);
  }

  public static void run (@NonNull final SpecCase... aCases)
  {
    run (env (), aCases);
  }
}
