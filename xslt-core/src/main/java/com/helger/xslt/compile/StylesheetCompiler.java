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
package com.helger.xslt.compile;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.fn.FnUri;
import com.helger.xpath.fn.FormatDecimalFormat;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.pattern.CompiledXsltPattern;
import com.helger.xpath.pattern.XsltPattern;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.schema.XdmSchemaSupport;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltException;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.model.AttributeSet;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.EOnNoMatch;
import com.helger.xslt.model.KeyDefinition;
import com.helger.xslt.model.Mode;
import com.helger.xslt.model.OutputDefinition;
import com.helger.xslt.model.SpaceRules;
import com.helger.xslt.model.Template;
import com.helger.xslt.model.TemplateRule;
import com.helger.xslt.model.UserFunction;
import com.helger.xslt.model.VariableDef;
import com.helger.xslt.pkg.PackageChecks;
import com.helger.xslt.pkg.PackageComponent;
import com.helger.xslt.pkg.PackageModel;
import com.helger.xslt.pkg.PackageUnit;
import com.helger.xslt.schema.SchemaImportCompiler;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslModule;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XslText;
import com.helger.xslt.tree.XsltNames;

/**
 * Compiles a stylesheet: static processing and module loading
 * ({@link StylesheetLoader}), static validation, then the declarations in
 * order of import precedence, then the checks across declarations.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class StylesheetCompiler
{
  /** An xsl:namespace-alias. */
  public record NamespaceAlias (@NonNull String resultPrefix, @NonNull String resultUri, int precedence)
  {}

  /** A reference from xsl:call-template, checked when all templates are known. */
  private record CallTemplateRef (@NonNull XslElement element,
                                  @NonNull XdmQName name,
                                  @NonNull ICommonsList <XdmQName> nonTunnelParams)
  {}

  private record AttributeSetRef (@NonNull XslElement element, @NonNull XdmQName name)
  {}

  private record FunctionEntry (@NonNull UserFunction function, int precedence)
  {}

  private record FunctionSignature (@NonNull ICommonsList <SeqType> paramTypes, @NonNull SeqType returnType)
  {}

  private static final XdmQName INITIAL_TEMPLATE = new XdmQName (XsltNames.NS_XSL, "initial-template");
  /** The name xsl:original, by which an overriding component refers to the one it overrides. */
  public static final XdmQName XSL_ORIGINAL = new XdmQName (XsltNames.NS_XSL, "original");

  private final XsltCompileOptions m_aOptions;
  private final XsltExtensionRegistry m_aRegistry;
  private CompiledStylesheet m_aStylesheet;
  private final ICommonsOrderedSet <XdmQName> m_aGlobalNames = new CommonsLinkedHashSet <> ();
  private final ICommonsMap <XdmQName, Integer> m_aGlobalPrecedences = new CommonsHashMap <> ();
  private FormatDecimalFormat m_aDefaultDecimalFormat;
  private final ICommonsMap <XdmQName, FormatDecimalFormat> m_aDecimalFormats = new CommonsHashMap <> ();
  private final ICommonsMap <PackageUnit, ICommonsMap <String, NamespaceAlias>> m_aAliases = new CommonsHashMap <> ();
  private final ICommonsMap <PackageUnit, Object []> m_aDecimalFormatsByUnit = new CommonsHashMap <> ();
  private final ICommonsMap <String, FunctionEntry> m_aFunctionsByKey = new CommonsHashMap <> ();
  private final ICommonsList <UserFunction> m_aHomonymLosers = new CommonsArrayList <> ();
  private final ICommonsMap <XslElement, UserFunction> m_aFunctionsByElement = new CommonsHashMap <> ();
  private final ICommonsList <CallTemplateRef> m_aCallTemplateRefs = new CommonsArrayList <> ();
  private final ICommonsList <AttributeSetRef> m_aAttributeSetRefs = new CommonsArrayList <> ();
  private final ICommonsMap <XdmQName, Integer> m_aNamedTemplatePrecedence = new CommonsHashMap <> ();
  private int m_nOrder;
  private final ICommonsSet <XslModule> m_aGlobalContextItemModules = new CommonsHashSet <> ();
  private final ICommonsList <XslElement> m_aExposes = new CommonsArrayList <> ();
  private String m_sGlobalContextItemAs;
  private PackageModel m_aPackages;
  private final ICommonsMap <UserFunction, FunctionSignature> m_aFunctionSignatures = new CommonsHashMap <> ();
  private final ICommonsMap <XslElement, UserFunction> m_aOriginalFunctions = new CommonsHashMap <> ();
  private final ICommonsMap <PackageUnit, FunctionLibrary> m_aUnitLibraries = new CommonsHashMap <> ();
  private final ICommonsMap <XslElement, FunctionLibrary> m_aDeclLibraries = new CommonsHashMap <> ();
  private final ICommonsMap <XslElement, XdmQName> m_aOriginalNames = new CommonsHashMap <> ();
  private final ICommonsMap <XdmQName, VariableDef> m_aLastGlobals = new CommonsHashMap <> ();
  private final ICommonsList <VariableDef> m_aAllGlobals = new CommonsArrayList <> ();
  private final ICommonsSet <XdmQName> m_aLocalAttributeSets = new CommonsHashSet <> ();
  private int m_nOriginalCounter;

  private StylesheetCompiler (@NonNull final XsltCompileOptions aOptions)
  {
    m_aOptions = aOptions;
    m_aRegistry = XsltExtensionRegistry.getInstance ();
  }

  @NonNull
  public CompiledStylesheet getStylesheet ()
  {
    return m_aStylesheet;
  }

  /** @return The packages taking part, <code>null</code> before loading. */
  @Nullable
  public PackageModel getPackages ()
  {
    return m_aPackages;
  }

  @NonNull
  public XsltCompileOptions getOptions ()
  {
    return m_aOptions;
  }

  @NonNull
  public XsltExtensionRegistry getRegistry ()
  {
    return m_aRegistry;
  }

  /** @return The names of all global variables and parameters. */
  @NonNull
  public ICommonsOrderedSet <XdmQName> getGlobalVariableNames ()
  {
    return m_aGlobalNames;
  }

  @Nullable
  public FormatDecimalFormat getDefaultDecimalFormat ()
  {
    return m_aDefaultDecimalFormat;
  }

  @NonNull
  public ICommonsMap <XdmQName, FormatDecimalFormat> getDecimalFormats ()
  {
    return m_aDecimalFormats;
  }

  /**
   * @param sStylesheetUri
   *        A namespace URI used in the stylesheet.
   * @return The alias for it or <code>null</code>.
   */
  @Nullable
  public NamespaceAlias getNamespaceAlias (@NonNull final String sStylesheetUri)
  {
    final ICommonsMap <String, NamespaceAlias> aMap = m_aAliases.get (m_aPackages.getPrincipal ());
    return aMap == null ? null : aMap.get (sStylesheetUri);
  }

  /**
   * @param aEl
   *        The literal result element (namespace aliases are local to a
   *        package).
   * @param sStylesheetUri
   *        A namespace URI used in the stylesheet.
   * @return The alias for it or <code>null</code>.
   */
  @Nullable
  public NamespaceAlias getNamespaceAlias (@NonNull final XslElement aEl, @NonNull final String sStylesheetUri)
  {
    final ICommonsMap <String, NamespaceAlias> aMap = m_aAliases.get (m_aPackages.getUnit (aEl));
    return aMap == null ? null : aMap.get (sStylesheetUri);
  }

  /**
   * @param aEl
   *        A literal result element.
   * @param sUri
   *        A namespace URI.
   * @return <code>true</code> if an xsl:namespace-alias of the element's
   *         package has this URI as its result namespace.
   */
  public boolean isNamespaceAliasResult (@NonNull final XslElement aEl, @NonNull final String sUri)
  {
    final ICommonsMap <String, NamespaceAlias> aMap = m_aAliases.get (m_aPackages.getUnit (aEl));
    if (aMap != null)
      for (final NamespaceAlias aAlias : aMap.values ())
        if (aAlias.resultUri ().equals (sUri))
          return true;
    return false;
  }

  /**
   * @param aEl
   *        An element.
   * @return The unnamed decimal format for expressions on it (decimal formats
   *         are local to a package).
   */
  @Nullable
  public FormatDecimalFormat getDefaultDecimalFormat (@NonNull final XslElement aEl)
  {
    final Object [] aFormats = m_aDecimalFormatsByUnit.get (m_aPackages.getUnit (aEl));
    return aFormats == null ? m_aDefaultDecimalFormat : (FormatDecimalFormat) aFormats[0];
  }

  /**
   * @param aEl
   *        An element.
   * @return The named decimal formats for expressions on it.
   */
  @NonNull
  @SuppressWarnings ("unchecked")
  public ICommonsMap <XdmQName, FormatDecimalFormat> getDecimalFormats (@NonNull final XslElement aEl)
  {
    final Object [] aFormats = m_aDecimalFormatsByUnit.get (m_aPackages.getUnit (aEl));
    return aFormats == null ? m_aDecimalFormats : (ICommonsMap <XdmQName, FormatDecimalFormat>) aFormats[1];
  }

  @NonNull
  private static XslElement _getDeclaration (@NonNull final XslElement aEl)
  {
    XslElement ret = aEl;
    while (ret.getParent () != null && ret.getParent ().getParent () != null && !ret.getParent ().isXsl ("override"))
      ret = ret.getParent ();
    return ret;
  }

  /**
   * @param aEl
   *        An element.
   * @return The functions visible to expressions on that element: in a
   *         package that uses other packages without their hidden functions,
   *         in an overriding function with xsl:original.
   */
  @NonNull
  public FunctionLibrary getFunctionLibraryFor (@NonNull final XslElement aEl)
  {
    if (m_aDeclLibraries.isNotEmpty ())
    {
      final FunctionLibrary aLib = m_aDeclLibraries.get (_getDeclaration (aEl));
      if (aLib != null)
        return aLib;
    }
    if (m_aUnitLibraries.isNotEmpty ())
    {
      final FunctionLibrary aLib = m_aUnitLibraries.get (m_aPackages.getUnit (aEl));
      if (aLib != null)
        return aLib;
    }
    return m_aStylesheet.getFunctionLibrary ();
  }

  /**
   * @param aEl
   *        An element.
   * @param aName
   *        A global variable.
   * @return <code>true</code> if the code at the element cannot see the
   *         variable because a used package hides it.
   */
  public boolean isGlobalVariableHidden (@NonNull final XslElement aEl, @NonNull final XdmQName aName)
  {
    return m_aPackages != null && m_aPackages.isHidden (aEl, PackageComponent.getSymbolicName ("variable", aName, 0));
  }

  /**
   * Map the name xsl:original in a reference to a template or attribute set
   * inside an overriding declaration to the name under which the overridden
   * component is kept.
   *
   * @param aEl
   *        The referring element.
   * @param aName
   *        The referenced name.
   * @return The name to use.
   */
  @NonNull
  public XdmQName mapOriginalName (@NonNull final XslElement aEl, @NonNull final XdmQName aName)
  {
    if (!XSL_ORIGINAL.equals (aName) || m_aOriginalNames.isEmpty ())
      return aName;
    final XdmQName ret = m_aOriginalNames.get (_getDeclaration (aEl));
    return ret != null ? ret : aName;
  }

  /**
   * Map the name in a reference to an attribute set: a private attribute set
   * whose name is declared in several packages is local to its package.
   *
   * @param aEl
   *        The referring element.
   * @param aName
   *        The referenced name.
   * @return The name to use.
   */
  @NonNull
  public XdmQName mapAttributeSetName (@NonNull final XslElement aEl, @NonNull final XdmQName aName)
  {
    if (m_aLocalAttributeSets.isEmpty ())
      return aName;
    final XdmQName aQualified = PackageModel.qualify (aEl, aName);
    return m_aLocalAttributeSets.contains (aQualified) ? aQualified : aName;
  }

  private void _findLocalAttributeSets (@NonNull final ICommonsList <XslElement> aDecls)
  {
    final ICommonsMap <XdmQName, ICommonsSet <PackageUnit>> aUnits = new CommonsHashMap <> ();
    for (final XslElement aEl : aDecls)
      if (aEl.isXsl ("attribute-set") && !PackageModel.isOverride (aEl))
      {
        final PackageComponent aComp = PackageChecks.getComponent (aEl);
        if (aComp != null)
          aUnits.computeIfAbsent (aComp.name (), k -> new CommonsHashSet <> ()).add (m_aPackages.getUnit (aEl));
      }
    for (final XslElement aEl : aDecls)
      if (aEl.isXsl ("attribute-set") && !PackageModel.isOverride (aEl))
      {
        final PackageComponent aComp = PackageChecks.getComponent (aEl);
        if (aComp == null || aUnits.get (aComp.name ()).size () < 2)
          continue;
        final PackageChecks.Exposure aExposure = m_aPackages.getExposure (PackageChecks.getModuleRoot (aEl));
        final String sVisibility = aExposure != null && aExposure.byElement ().containsKey (aEl) ? aExposure.byElement ().get (aEl)
                                                                                                   : aComp.visibility ();
        if ("private".equals (sVisibility))
          m_aLocalAttributeSets.add (PackageModel.qualify (aEl, aComp.name ()));
      }
  }

  @NonNull
  private XdmQName _createOriginalName (@NonNull final XslElement aDecl)
  {
    final XdmQName ret = new XdmQName (XsltNames.NS_XSL, "original-" + (++m_nOriginalCounter));
    m_aOriginalNames.put (aDecl, ret);
    return ret;
  }

  private void _buildPackageFunctionScopes ()
  {
    // In a compilation with several packages each package has its own
    // function scope; the scope also identifies the package of an expression
    // at run time (keys are local to a package)
    if (m_aPackages.isMultiPackage ())
      for (final PackageUnit aUnit : m_aPackages.getUnits ())
      {
        final FunctionLibrary aLib = m_aStylesheet.getFunctionLibrary ().createChild ();
        m_aPackages.registerScope (aLib, aUnit);
        for (final FunctionEntry aEntry : m_aFunctionsByKey.values ())
        {
          final UserFunction aFunc = aEntry.function ();
          if (aUnit.hidden ().contains (PackageComponent.getSymbolicName ("function", aFunc.getName (), aFunc.getArity ())))
            aLib.hide (aFunc.getName (), aFunc.getArity ());
        }
        for (final UserFunction aFunc : m_aHomonymLosers)
          if (m_aPackages.getUnit (aFunc.getElement ()) == aUnit)
          {
            final FunctionSignature aSig = m_aFunctionSignatures.get (aFunc);
            aLib.registerParsed (aFunc.getName (), aSig.paramTypes (), aSig.returnType (), aFunc, true);
          }
        m_aUnitLibraries.put (aUnit, aLib);
      }
    for (final Map.Entry <XslElement, UserFunction> aEntry : m_aOriginalFunctions.entrySet ())
    {
      final FunctionLibrary aBase = m_aUnitLibraries.get (m_aPackages.getUnit (aEntry.getKey ()));
      final FunctionLibrary aLib = (aBase != null ? aBase : m_aStylesheet.getFunctionLibrary ()).createChild ();
      final FunctionSignature aSig = m_aFunctionSignatures.get (aEntry.getValue ());
      aLib.registerParsed (XSL_ORIGINAL, aSig.paramTypes (), aSig.returnType (), aEntry.getValue (), false);
      m_aPackages.registerScope (aLib, m_aPackages.getUnit (aEntry.getKey ()));
      m_aDeclLibraries.put (aEntry.getKey (), aLib);
    }
  }

  /** @return A compile context with only global variables in scope. */
  @NonNull
  public CompileContext createGlobalContext ()
  {
    return new CompileContext (this, null);
  }

  /**
   * Record an xsl:call-template for the checks at the end (XTSE0650, XTSE0680,
   * XTSE0690).
   *
   * @param aEl
   *        The instruction.
   * @param aName
   *        Template name.
   * @param aNonTunnelParams
   *        Names of the non-tunnel xsl:with-param children.
   */
  public void registerCallTemplate (@NonNull final XslElement aEl,
                                    @NonNull final XdmQName aName,
                                    @NonNull final ICommonsList <XdmQName> aNonTunnelParams)
  {
    m_aCallTemplateRefs.add (new CallTemplateRef (aEl, aName, aNonTunnelParams));
  }

  /**
   * Record a use-attribute-sets reference for the check at the end (XTSE0710).
   *
   * @param aEl
   *        The element with the attribute.
   * @param aName
   *        The attribute set name.
   */
  public void registerAttributeSetRef (@NonNull final XslElement aEl, @NonNull final XdmQName aName)
  {
    m_aAttributeSetRefs.add (new AttributeSetRef (aEl, aName));
  }

  /**
   * @param aEl
   *        An element.
   * @return The default mode in scope for it ([xsl:]default-mode).
   */
  @NonNull
  public static XdmQName getDefaultMode (@NonNull final XslElement aEl)
  {
    final XslElement aOwner = aEl.getStandardAttrOwner ("default-mode");
    if (aOwner == null)
      return Mode.UNNAMED;
    final String s = aOwner.getStandardAttr ("default-mode").trim ();
    if (s.isEmpty () || "#unnamed".equals (s))
      return Mode.UNNAMED;
    final XdmQName ret = aOwner.resolveQName (s, false);
    if (ret == null)
      throw XsltException.at (aOwner, "XTSE0020", "default-mode='" + s + "' is not a valid QName");
    return ret;
  }

  private static boolean _hasContent (@NonNull final XslElement aEl)
  {
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof XslElement || !((XslText) aNode).isWhitespaceOnly ())
        return true;
    return false;
  }

  private void _checkPatternFunctions (@NonNull final XslElement aEl, @NonNull final XsltPattern aPattern)
  {
    if (!(aPattern instanceof final CompiledXsltPattern aCP))
      return;
    final String [] [] aForbidden = { { "current-group", "XTSE1060" },
                                      { "current-grouping-key", "XTSE1070" },
                                      { "current-merge-group", "XTSE3470" },
                                      { "current-merge-key", "XTSE3500" } };
    for (final String [] aEntry : aForbidden)
    {
      final Predicate <XdmQName> aFilter = n -> XdmConstants.NS_FN.equals (n.namespaceUri ()) &&
                                                 aEntry[0].equals (n.localName ());
      if (aCP.containsFunction (aFilter))
        throw XsltException.at (aEl, aEntry[1], aEntry[0] + "() may not be used in a pattern");
    }
  }

  /**
   * Compile a pattern on an element including the checks for functions not
   * allowed in patterns.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        Element.
   * @param sPattern
   *        Pattern text.
   * @return The compiled pattern.
   */
  @NonNull
  public CompiledPattern compileCheckedPattern (@NonNull final CompileContext aCtx,
                                                @NonNull final XslElement aEl,
                                                @NonNull final String sPattern)
  {
    final CompiledPattern ret = aCtx.compilePattern (aEl, sPattern);
    _checkPatternFunctions (aEl, ret.pattern ());
    return ret;
  }

  private void _prepareNamespaceAlias (@NonNull final XslElement aEl)
  {
    final String sStyPrefix = aEl.getAttr ("stylesheet-prefix").trim ();
    final String sResPrefix = aEl.getAttr ("result-prefix").trim ();
    final String sStyUri = aEl.lookupNamespaceUri ("#default".equals (sStyPrefix) ? "" : sStyPrefix);
    final String sResUri = aEl.lookupNamespaceUri ("#default".equals (sResPrefix) ? "" : sResPrefix);
    if (!"#default".equals (sStyPrefix) && sStyUri == null || !"#default".equals (sResPrefix) && sResUri == null)
      throw XsltException.at (aEl, "XTSE0812", "A prefix of xsl:namespace-alias is not bound");
    final String sKey = sStyUri == null ? "" : sStyUri;
    final NamespaceAlias aAlias = new NamespaceAlias ("#default".equals (sResPrefix) ? "" : sResPrefix,
                                                      sResUri == null ? "" : sResUri,
                                                      aEl.getPrecedence ());
    final ICommonsMap <String, NamespaceAlias> aAliases = m_aAliases.computeIfAbsent (m_aPackages.getUnit (aEl), k -> new CommonsHashMap <> ());
    final NamespaceAlias aPrev = aAliases.get (sKey);
    if (aPrev != null && aPrev.precedence () == aAlias.precedence () && !aPrev.resultUri ().equals (aAlias.resultUri ()))
      throw XsltException.at (aEl, "XTSE0810", "Conflicting xsl:namespace-alias declarations for '" + sStyPrefix + "'");
    if (aPrev == null || aPrev.precedence () <= aAlias.precedence ())
      aAliases.put (sKey, aAlias);
  }

  private static int _firstCodePoint (@NonNull final String s)
  {
    return s.isEmpty () ? 0 : s.codePointAt (0);
  }

  private void _prepareDecimalFormats (@NonNull final ICommonsList <XslElement> aDecls)
  {
    final ICommonsMap <XdmQName, ICommonsList <XslElement>> aByName = new CommonsHashMap <> ();
    final XdmQName aDefaultKey = new XdmQName (XsltNames.NS_XSL, "#default");
    for (final XslElement aEl : aDecls)
    {
      final String sName = aEl.getAttr ("name");
      final XdmQName aName = sName == null ? aDefaultKey : createGlobalContext ().resolveQName (aEl, sName, false);
      aByName.computeIfAbsent (aName, k -> new CommonsArrayList <> ()).add (aEl);
    }
    final String [] aAttrs = { "decimal-separator",
                               "grouping-separator",
                               "percent",
                               "per-mille",
                               "zero-digit",
                               "digit",
                               "pattern-separator",
                               "minus-sign",
                               "infinity",
                               "NaN",
                               "exponent-separator" };
    for (final Map.Entry <XdmQName, ICommonsList <XslElement>> aEntry : aByName.entrySet ())
    {
      final FormatDecimalFormat.Builder aBuilder = FormatDecimalFormat.builder ();
      XslElement aErrEl = aEntry.getValue ().getFirst ();
      for (final String sAttr : aAttrs)
      {
        int nMax = Integer.MIN_VALUE;
        for (final XslElement aEl : aEntry.getValue ())
          if (aEl.hasAttr (sAttr))
            nMax = Math.max (nMax, aEl.getPrecedence ());
        if (nMax == Integer.MIN_VALUE)
          continue;
        String sValue = null;
        for (final XslElement aEl : aEntry.getValue ())
        {
          if (aEl.getPrecedence () != nMax || !aEl.hasAttr (sAttr))
            continue;
          final String v = aEl.getAttr (sAttr);
          if (sValue != null && !sValue.equals (v))
            throw XsltException.at (aEl, "XTSE1290", "Conflicting xsl:decimal-format declarations for " + sAttr);
          sValue = v;
          if ("zero-digit".equals (sAttr))
            aErrEl = aEl;
        }
        switch (sAttr)
        {
          case "decimal-separator" -> aBuilder.decimalSeparator (_firstCodePoint (sValue));
          case "grouping-separator" -> aBuilder.groupingSeparator (_firstCodePoint (sValue));
          case "percent" -> aBuilder.percent (_firstCodePoint (sValue));
          case "per-mille" -> aBuilder.perMille (_firstCodePoint (sValue));
          case "zero-digit" ->
          {
            final int c = _firstCodePoint (sValue);
            if (!Character.isDigit (c) || Character.getNumericValue (c) != 0)
              throw XsltException.at (aErrEl, "XTSE1295", "zero-digit must be a digit with value zero");
            aBuilder.zeroDigit (c);
          }
          case "digit" -> aBuilder.digit (_firstCodePoint (sValue));
          case "pattern-separator" -> aBuilder.patternSeparator (_firstCodePoint (sValue));
          case "minus-sign" -> aBuilder.minusSign (_firstCodePoint (sValue));
          case "infinity" -> aBuilder.infinity (sValue);
          case "NaN" -> aBuilder.nan (sValue);
          case "exponent-separator" -> aBuilder.exponentSeparator (_firstCodePoint (sValue));
          default ->
          {
            // not reached
          }
        }
      }
      final FormatDecimalFormat aFormat = aBuilder.build ();
      final int [] aSyms = { aFormat.getDecimalSeparator (),
                             aFormat.getGroupingSeparator (),
                             aFormat.getPercent (),
                             aFormat.getPerMille (),
                             aFormat.getDigit (),
                             aFormat.getPatternSeparator (),
                             aFormat.getExponentSeparator () };
      final ICommonsSet <Integer> aSeen = new CommonsHashSet <> ();
      for (final int c : aSyms)
      {
        if (!aSeen.add (Integer.valueOf (c)))
          throw XsltException.at (aErrEl, "XTSE1300", "The picture symbols of xsl:decimal-format must be distinct");
        if (c >= aFormat.getZeroDigit () && c <= aFormat.getZeroDigit () + 9)
          throw XsltException.at (aErrEl, "XTSE1300", "A picture symbol of xsl:decimal-format collides with the digits");
      }
      if (aEntry.getKey ().equals (aDefaultKey))
        m_aDefaultDecimalFormat = aFormat;
      else
        m_aDecimalFormats.put (aEntry.getKey (), aFormat);
    }
  }

  private void _prepareFunction (@NonNull final XslElement aEl)
  {
    final CompileContext aCtx = createGlobalContext ();
    final XdmQName aName = aCtx.resolveQName (aEl, aEl.getAttr ("name"), false);
    final ICommonsList <VariableDef> aParams = new CommonsArrayList <> ();
    final ICommonsList <SeqType> aParamTypes = new CommonsArrayList <> ();
    for (final XslElement aParamEl : aEl.getXslChildren ("param"))
    {
      final XdmQName aParamName = aCtx.resolveQName (aParamEl, aParamEl.getAttr ("name"), false);
      final String sAs = aParamEl.getAttr ("as");
      final SeqType aType = sAs == null ? null : aCtx.compileSequenceType (aParamEl, sAs);
      aParams.add (new VariableDef (aParamEl, VariableDef.EKind.FUNCTION_PARAM, aParamName, null, null, aType, false, true));
      aParamTypes.add (aType != null ? aType : aCtx.compileSequenceType (aParamEl, "item()*"));
    }
    final String sAs = aEl.getAttr ("as");
    final SeqType aAs = sAs == null ? null : aCtx.compileSequenceType (aEl, sAs);
    final String sVisibility = aEl.getAttr ("visibility");
    final UserFunction aFunc = new UserFunction (aEl,
                                                 aName,
                                                 aParams,
                                                 aAs,
                                                 sVisibility == null ? null : sVisibility.trim ());
    final String sKey = aName.getEQName () + "#" + aParams.size ();
    final FunctionEntry aPrev = m_aFunctionsByKey.get (sKey);
    if (aPrev != null && aPrev.precedence () == aEl.getPrecedence ())
      throw XsltException.at (aEl, "XTSE0770", "Duplicate function " + aName.getEQName () + "#" + aParams.size ());
    aFunc.setMemoize (XsltNames.isXsltTrue (aEl.getAttr ("cache")) || "no".equals (aEl.getAttr ("new-each-time")));
    m_aFunctionsByElement.put (aEl, aFunc);
    final SeqType aReturnType = aAs != null ? aAs : aCtx.compileSequenceType (aEl, "item()*");
    m_aFunctionSignatures.put (aFunc, new FunctionSignature (aParamTypes, aReturnType));
    if (aPrev != null && PackageModel.isOverride (aEl))
      m_aOriginalFunctions.put (aEl, aPrev.function ());
    if (m_aPackages != null && !PackageModel.isOverride (aEl))
    {
      final PackageComponent aComp = PackageChecks.getComponent (aEl);
      if (aComp != null && m_aPackages.isShadowedByVisibleHomonym (aEl, aComp))
      {
        // Only the code of its own package still calls it
        m_aHomonymLosers.add (aFunc);
        return;
      }
    }
    if (aPrev == null || aPrev.precedence () < aEl.getPrecedence ())
    {
      m_aFunctionsByKey.put (sKey, new FunctionEntry (aFunc, aEl.getPrecedence ()));
      // Registered as context dependent, so that a function item (f#1)
      // captures the transformation it was created in: it may be called after
      // that transformation ended (fn:transform returning a function item)
      m_aStylesheet.getFunctionLibrary ().registerParsed (aName, aParamTypes, aReturnType, aFunc, true);
    }
  }

  private void _prepareGlobal (@NonNull final XslElement aEl)
  {
    final XdmQName aName = createGlobalContext ().resolveQName (aEl, aEl.getAttr ("name"), false);
    final Integer aPrev = m_aGlobalPrecedences.get (aName);
    if (aPrev != null && aPrev.intValue () == aEl.getPrecedence ())
      throw XsltException.at (aEl, "XTSE0630", "Duplicate global variable $" + aName.getEQName ());
    if (aPrev == null || aPrev.intValue () < aEl.getPrecedence ())
      m_aGlobalPrecedences.put (aName, Integer.valueOf (aEl.getPrecedence ()));
    m_aGlobalNames.add (aName);
  }

  private void _prepareMode (@NonNull final XslElement aEl, @NonNull final ICommonsMap <Mode, ICommonsMap <String, Object []>> aSeen)
  {
    final XdmQName aName = aEl.hasAttr ("name") ? createGlobalContext ().resolveQName (aEl, aEl.getAttr ("name"), false)
                                                : Mode.UNNAMED;
    final Mode aMode = m_aStylesheet.getOrCreateMode (aName);
    final ICommonsMap <String, Object []> aAttrs = aSeen.computeIfAbsent (aMode, k -> new CommonsHashMap <> ());
    if (aMode.getDeclaration () == null || aMode.getDeclaration ().getPrecedence () <= aEl.getPrecedence ())
      aMode.setDeclaration (aEl);
    for (final String sAttr : new String [] { "on-no-match",
                                              "on-multiple-match",
                                              "warning-on-no-match",
                                              "warning-on-multiple-match",
                                              "visibility",
                                              "streamable",
                                              "typed",
                                              "use-accumulators" })
    {
      final String v = aEl.getAttr (sAttr);
      if (v == null)
        continue;
      String sValue = v.trim ();
      if ("streamable".equals (sAttr) || sAttr.startsWith ("warning-"))
        sValue = XsltNames.isXsltTrue (sValue) ? "yes" : "no";
      else
        if ("use-accumulators".equals (sAttr))
        {
          final ICommonsList <String> aNames = new CommonsArrayList <> ();
          for (final String sToken : XsltNames.tokenize (sValue))
            aNames.add (sToken.startsWith ("#") ? sToken : createGlobalContext ().resolveQName (aEl, sToken, false).getEQName ());
          aNames.sort (String::compareTo);
          sValue = String.join (" ", aNames);
        }
      final Object [] aPrev = aAttrs.get (sAttr);
      if (aPrev != null)
      {
        final int nPrevPrec = ((Integer) aPrev[1]).intValue ();
        if (nPrevPrec > aEl.getPrecedence ())
          continue;
        if (nPrevPrec == aEl.getPrecedence () && !aPrev[0].equals (sValue))
        {
          // A conflict, unless a declaration with higher precedence follows
          aPrev[2] = aEl;
          continue;
        }
      }
      aAttrs.put (sAttr, new Object [] { sValue, Integer.valueOf (aEl.getPrecedence ()), null });
      switch (sAttr)
      {
        case "on-no-match" -> aMode.setOnNoMatch (EOnNoMatch.getFromIDOrNull (sValue));
        case "on-multiple-match" -> aMode.setFailOnMultipleMatch ("fail".equals (sValue));
        case "warning-on-no-match" -> aMode.setWarningOnNoMatch (XsltNames.isXsltTrue (sValue));
        case "warning-on-multiple-match" -> aMode.setWarningOnMultipleMatch (XsltNames.isXsltTrue (sValue));
        case "visibility" -> aMode.setVisibility (sValue);
        case "typed" -> aMode.setTyped (sValue);
        default ->
        {
          // streamable and use-accumulators have no effect here
        }
      }
    }
  }

  private void _compileTemplate (@NonNull final XslElement aEl)
  {
    final CompileContext aGlobal = createGlobalContext ();
    CompiledPattern aPattern = null;
    final String sMatch = aEl.getAttr ("match");
    if (sMatch != null)
      aPattern = compileCheckedPattern (aGlobal, aEl, sMatch);
    final XdmQName aName = aGlobal.resolveQNameAttr (aEl, "name", false);
    final ICommonsList <XdmQName> aModes = new CommonsArrayList <> ();
    boolean bAllModes = false;
    final String sMode = aEl.getAttr ("mode");
    if (sMode == null)
      aModes.add (getDefaultMode (aEl));
    else
      for (final String sToken : XsltNames.tokenize (sMode))
        switch (sToken)
        {
          case "#all" -> bAllModes = true;
          case "#default" -> aModes.add (getDefaultMode (aEl));
          case "#unnamed" -> aModes.add (Mode.UNNAMED);
          default -> aModes.add (aGlobal.resolveQName (aEl, sToken, false));
        }
    for (final XdmQName aMode : aModes)
      m_aStylesheet.getOrCreateMode (aMode);
    Double aPriority = null;
    final String sPriority = aEl.getAttr ("priority");
    if (sPriority != null)
    {
      final String s = sPriority.trim ();
      if (!XsltNames.isDecimalLexical (s))
        throw XsltException.at (aEl, "XTSE0530", "priority='" + sPriority + "' is not a valid number");
      aPriority = Double.valueOf (s);
    }
    final String sAs = aEl.getAttr ("as");
    final Template aTemplate = new Template (aEl,
                                             aPattern,
                                             aName,
                                             aModes,
                                             bAllModes,
                                             aPriority,
                                             sAs == null ? null : aGlobal.compileSequenceType (aEl, sAs));
    final ICommonsList <XslElement> aContextItems = aEl.getXslChildren ("context-item");
    if (aContextItems.size () > 1)
      throw XsltException.at (aContextItems.get (1), "XTSE0010", "Only one xsl:context-item is allowed");
    if (aContextItems.isNotEmpty ())
    {
      final XslElement aCI = aContextItems.getFirst ();
      XslNode aFirst = null;
      for (final XslNode aNode : aEl.getChildren ())
        if (aNode instanceof XslElement || !((XslText) aNode).isWhitespaceOnly ())
        {
          aFirst = aNode;
          break;
        }
      if (aFirst != aCI)
        throw XsltException.at (aCI, "XTSE0010", "xsl:context-item must be the first child of xsl:template");
      final String sUse = aCI.getAttr ("use") == null ? "optional" : aCI.getAttr ("use").trim ();
      if (!"required".equals (sUse) && !"optional".equals (sUse) && !"absent".equals (sUse))
        throw XsltException.at (aCI, "XTSE0020", "use='" + sUse + "' is not allowed on xsl:context-item");
      if ("absent".equals (sUse) && aCI.hasAttr ("as"))
        throw XsltException.at (aCI, "XTSE3088", "use='absent' cannot be combined with an as attribute");
      // A template rule always has a context item
      if (aName == null && "absent".equals (sUse))
        throw XsltException.at (aCI, "XTSE0020", "use='absent' is not allowed on xsl:context-item of a template without a name");
      final String sCIAs = aCI.getAttr ("as");
      aTemplate.setContextItem (new Template.ContextItemDecl (sCIAs == null ? null : aGlobal.compileSequenceType (aCI, sCIAs),
                                                              sUse));
    }
    CompileContext aCtx = aGlobal;
    for (final XslElement aParamEl : aEl.getXslChildren ("param"))
    {
      final VariableDef aParam = aCtx.compileVariable (aParamEl, VariableDef.EKind.TEMPLATE_PARAM);
      aTemplate.addParam (aParam);
      aCtx = aCtx.withVariable (aParam.getName ());
    }
    if (aName != null && PackageModel.isOverride (aEl))
    {
      // xsl:call-template name="xsl:original" calls the overridden template
      final Template aOriginal = m_aStylesheet.getNamedTemplate (aName);
      if (aOriginal != null)
        m_aStylesheet.namedTemplates ().put (_createOriginalName (aEl), aOriginal);
    }
    aTemplate.setBody (aCtx.compileContent (aEl));
    m_aStylesheet.addTemplate (aTemplate);
    if (aName != null)
    {
      final Integer aPrev = m_aNamedTemplatePrecedence.get (aName);
      if (aPrev != null && aPrev.intValue () == aEl.getPrecedence ())
        throw XsltException.at (aEl, "XTSE0660", "Duplicate named template " + aName.getEQName ());
      if (aPrev == null || aPrev.intValue () < aEl.getPrecedence ())
      {
        m_aNamedTemplatePrecedence.put (aName, Integer.valueOf (aEl.getPrecedence ()));
        m_aStylesheet.namedTemplates ().put (aName, aTemplate);
      }
    }
  }

  private void _compileSimplifiedTemplate (@NonNull final XslElement aEl)
  {
    final CompileContext aGlobal = createGlobalContext ();
    final CompiledPattern aPattern = aGlobal.compilePattern (aEl, "/");
    final Template aTemplate = new Template (aEl,
                                             aPattern,
                                             null,
                                             new CommonsArrayList <> (Mode.UNNAMED),
                                             false,
                                             null,
                                             null);
    m_aStylesheet.getOrCreateMode (Mode.UNNAMED);
    final IInstruction aInstr = aGlobal.compileInstruction (aEl);
    final ICommonsList <IInstruction> aList = new CommonsArrayList <> ();
    if (aInstr != null)
      aList.add (aInstr);
    aTemplate.setBody (new SequenceConstructor (aEl, aList));
    m_aStylesheet.addTemplate (aTemplate);
  }

  private static boolean _mentionsOriginal (@NonNull final XslElement aEl)
  {
    final ICommonsList <XslElement> aStack = new CommonsArrayList <> (aEl);
    while (aStack.isNotEmpty ())
    {
      final XslElement aCur = aStack.removeLast ();
      for (final String sValue : aCur.getAllAttributes ().values ())
        if (sValue.contains ("original"))
          return true;
      for (final XslNode aChild : aCur.getChildren ())
        if (aChild instanceof final XslElement aChildEl)
          aStack.add (aChildEl);
    }
    return false;
  }

  @Nullable
  private VariableDef _getOwnGlobal (@NonNull final PackageUnit aUnit, @NonNull final XdmQName aName)
  {
    VariableDef ret = null;
    for (final VariableDef aVar : m_aAllGlobals)
      if (aVar.getName ().equals (aName) &&
          m_aPackages.getUnit (aVar.getElement ()) == aUnit &&
          (ret == null || aVar.getElement ().getPrecedence () >= ret.getElement ().getPrecedence ()))
        ret = aVar;
    return ret;
  }

  @Nullable
  private VariableDef _getGlobalViewDown (@NonNull final PackageUnit aUnit, @NonNull final XdmQName aName, final int nDepth)
  {
    final VariableDef ret = _getOwnGlobal (aUnit, aName);
    if (ret != null || nDepth > m_aPackages.getUnits ().size ())
      return ret;
    for (final PackageUnit.Use aUse : aUnit.uses ())
    {
      final VariableDef aSub = _getGlobalViewDown (aUse.used (), aName, nDepth + 1);
      if (aSub != null)
        return aSub;
    }
    return null;
  }

  /**
   * With several packages, a package may see another declaration of a global
   * variable than the one with the highest import precedence: its own
   * override when two packages override one used package differently, or the
   * declaration of the package it uses when two used packages declare the same
   * name.
   */
  private void _computePackageGlobals ()
  {
    final ICommonsMap <PackageUnit, PackageUnit.Use> aUsers = new CommonsHashMap <> ();
    final ICommonsMap <PackageUnit.Use, PackageUnit> aUserUnits = new CommonsHashMap <> ();
    for (final PackageUnit aUnit : m_aPackages.getUnits ())
      for (final PackageUnit.Use aUse : aUnit.uses ())
      {
        aUsers.put (aUse.used (), aUse);
        aUserUnits.put (aUse, aUnit);
      }
    final ICommonsSet <XdmQName> aNames = new CommonsHashSet <> ();
    final ICommonsSet <XdmQName> aMulti = new CommonsHashSet <> ();
    for (final VariableDef aVar : m_aAllGlobals)
      if (!aNames.add (aVar.getName ()))
        aMulti.add (aVar.getName ());
    for (final XdmQName aName : aMulti)
    {
      final VariableDef aWinner = m_aStylesheet.globals ().get (aName);
      for (final PackageUnit aUnit : m_aPackages.getUnits ())
      {
        VariableDef aView = _getGlobalViewDown (aUnit, aName, 0);
        // An override by a using package replaces the declaration
        PackageUnit aCur = aUnit;
        int nGuard = 0;
        while (aUsers.containsKey (aCur) && nGuard++ < m_aPackages.getUnits ().size ())
        {
          final PackageUnit.Use aUse = aUsers.get (aCur);
          for (final XslElement aOverride : aUse.element ().getXslChildren ("override"))
            for (final VariableDef aVar : m_aAllGlobals)
              if (aVar.getName ().equals (aName) && aVar.getElement ().getParent () == aOverride)
                aView = aVar;
          aCur = aUserUnits.get (aUse);
        }
        if (aView != null && aView != aWinner)
          m_aStylesheet.packageGlobals ().computeIfAbsent (aUnit, k -> new CommonsHashMap <> ()).put (aName, aView);
      }
    }
  }

  private void _compileGlobal (@NonNull final XslElement aEl, final boolean bParam)
  {
    final XdmQName aOwnName = createGlobalContext ().resolveQName (aEl, aEl.getAttr ("name"), false);
    CompileContext aCtx = createGlobalContext ().withoutGlobalVariable (aOwnName);
    // Only bound if it may be referenced: evaluating it could fail (an
    // abstract variable has no value)
    final VariableDef aOriginal = PackageModel.isOverride (aEl) && _mentionsOriginal (aEl) ? m_aLastGlobals.get (aOwnName) : null;
    if (aOriginal != null)
      aCtx = aCtx.withVariable (XSL_ORIGINAL);
    final VariableDef aVar = aCtx.compileVariable (aEl, bParam ? VariableDef.EKind.GLOBAL_PARAM : VariableDef.EKind.GLOBAL_VARIABLE);
    if (aOriginal != null)
      m_aStylesheet.originalVariables ().put (aVar, aOriginal);
    m_aLastGlobals.put (aOwnName, aVar);
    m_aAllGlobals.add (aVar);
    final Integer aWinner = m_aGlobalPrecedences.get (aVar.getName ());
    if (aWinner != null && aWinner.intValue () == aEl.getPrecedence ())
      m_aStylesheet.globals ().put (aVar.getName (), aVar);
  }

  private void _compileKey (@NonNull final XslElement aEl)
  {
    final CompileContext aCtx = createGlobalContext ();
    final XdmQName aName = aCtx.resolveQName (aEl, aEl.getAttr ("name"), false);
    final CompiledPattern aMatch = compileCheckedPattern (aCtx, aEl, aEl.getAttr ("match"));
    final boolean bContent = _hasContent (aEl);
    XPathExpr aUse = null;
    SequenceConstructor aBody = null;
    if (aEl.hasAttr ("use"))
    {
      if (bContent)
        throw XsltException.at (aEl, "XTSE1205", "xsl:key has both a use attribute and content");
      aUse = aCtx.compileExpression (aEl, aEl.getAttr ("use"));
    }
    else
    {
      if (!bContent)
        throw XsltException.at (aEl, "XTSE1205", "xsl:key requires a use attribute or content");
      aBody = aCtx.compileContent (aEl);
    }
    String sCollation;
    if (aEl.hasAttr ("collation"))
    {
      sCollation = CollationRegistry.resolveUri (aEl.getAttr ("collation").trim (), aEl.getStaticBaseUri ());
      if (CollationRegistry.getCollation (sCollation) == null)
        throw XsltException.at (aEl, "XTSE1210", "The collation '" + sCollation + "' of xsl:key is not supported");
    }
    else
      sCollation = StaticContexts.getDefaultCollation (aEl);
    final boolean bComposite = XsltNames.isXsltTrue (aEl.getAttr ("composite"));
    final ICommonsList <KeyDefinition> aPrev = m_aStylesheet.getKey (aName);
    if (aPrev != null)
      for (final KeyDefinition aOther : aPrev)
      {
        if (m_aPackages.getUnit (aOther.getElement ()) != m_aPackages.getUnit (aEl))
          continue;
        if (!aOther.getCollation ().equals (sCollation))
          throw XsltException.at (aEl, "XTSE1220", "The declarations of key " + aName.getEQName () + " use different collations");
        if (aOther.isComposite () != bComposite)
          throw XsltException.at (aEl, "XTSE1222", "The declarations of key " + aName.getEQName () + " differ in composite");
      }
    m_aStylesheet.addKey (new KeyDefinition (aEl, aName, aMatch, aUse, aBody, bComposite, sCollation));
  }

  private void _readParameterDocument (@NonNull final XslElement aEl,
                                       @NonNull final ICommonsMap <String, String> aAttrs,
                                       @NonNull final OutputDefinition aDef)
  {
    final String sHref = aEl.getAttr ("parameter-document");
    if (sHref == null)
      return;
    final String sBase = aEl.getStaticBaseUri ();
    String sUri = sHref.trim ();
    try
    {
      if (sBase != null && FnUri.isAbsoluteUri (sBase))
        sUri = FnUri.resolveUri (sUri, sBase);
    }
    catch (final XPathException ex)
    {
      throw XsltException.at (aEl, "XTSE0020", "Invalid parameter-document URI '" + sHref + "'");
    }
    final IResourceResolver aResolver = m_aOptions.getResolver () != null ? m_aOptions.getResolver ()
                                                                         : new FileResourceResolver (m_aOptions.isLoadExternalEntities ());
    final XdmNode aDoc = aResolver.resolveDocument (sUri);
    if (aDoc == null)
      throw XsltException.at (aEl, "XTSE0020", "The parameter document '" + sUri + "' cannot be retrieved");
    XdmNode aRoot = null;
    for (int i = 0; i < aDoc.getChildCount (); ++i)
      if (aDoc.getChildAtIndex (i).isElement ())
        aRoot = aDoc.getChildAtIndex (i);
    if (aRoot == null || !SerializationParams.NS_OUTPUT.equals (aRoot.getName ().namespaceUri ()))
      throw XsltException.at (aEl, "XTSE0020", "'" + sUri + "' is not a serialization parameter document");
    for (int i = 0; i < aRoot.getChildCount (); ++i)
    {
      final XdmNode aParam = aRoot.getChildAtIndex (i);
      if (!aParam.isElement () || !SerializationParams.NS_OUTPUT.equals (aParam.getName ().namespaceUri ()))
        continue;
      if ("use-character-maps".equals (aParam.getName ().localName ()))
      {
        for (int j = 0; j < aParam.getChildCount (); ++j)
        {
          final XdmNode aMap = aParam.getChildAtIndex (j);
          final XdmNode aChar = aMap.isElement () ? aMap.getAttribute ("", "character") : null;
          final XdmNode aString = aMap.isElement () ? aMap.getAttribute ("", "map-string") : null;
          // An invalid parameter document is a serialization error (XSLT 3.0
          // section 26.1, Serialization 3.1 section 3.1)
          if (aChar != null && aChar.getValue ().codePointCount (0, aChar.getValue ().length ()) != 1)
            throw XsltException.at (aEl, "SEPM0017", "output:character-map/@character must be a single character");
          if (aChar != null && aString != null)
            aDef.addInlineCharacterMapping (aChar.getValue ().codePointAt (0), aString.getValue ());
        }
        continue;
      }
      final XdmNode aValue = aParam.getAttribute ("", "value");
      if (aValue != null)
        aAttrs.put (aParam.getName ().localName (), aValue.getValue ());
    }
  }

  private void _compileOutput (@NonNull final XslElement aEl)
  {
    final ICommonsMap <String, String> aAttrs = new CommonsHashMap <> ();
    final String sName = aEl.getAttr ("name");
    // Only the unnamed xsl:output of the principal package describes the
    // principal result; named outputs are local to a package
    if (sName == null && !m_aPackages.isInPrincipal (aEl))
      return;
    final OutputDefinition aDef = sName != null ? m_aStylesheet.getOrCreateNamedOutput (PackageModel.qualify (aEl,
                                                                                                             createGlobalContext ().resolveQName (aEl,
                                                                                                                                                  sName,
                                                                                                                                                  false)))
                                                : m_aStylesheet.getOutput ();
    _readParameterDocument (aEl, aAttrs, aDef);
    for (final String sAttr : OutputDefinition.ATTRIBUTES)
    {
      final String v = aEl.getAttr (sAttr);
      if (v != null)
        aAttrs.put (sAttr, v);
    }
    aDef.merge (aEl, aEl.getPrecedence (), aAttrs);
  }

  private void _compileSpace (@NonNull final XslElement aEl, final boolean bStrip)
  {
    final String sXpathDefault = aEl.getInheritedStandardAttr ("xpath-default-namespace");
    for (final String sToken : XsltNames.tokenize (aEl.getAttr ("elements")))
    {
      final SpaceRules.Rule aRule;
      if ("*".equals (sToken))
        aRule = new SpaceRules.Rule ("", "", true, true, bStrip, aEl.getPrecedence ());
      else
        if (sToken.startsWith ("*:"))
        {
          if (!XsltNames.isNCName (sToken.substring (2)))
            throw XsltException.at (aEl, "XTSE0010", "'" + sToken + "' is not a valid name test");
          aRule = new SpaceRules.Rule ("", sToken.substring (2), true, false, bStrip, aEl.getPrecedence ());
        }
        else
          if (sToken.endsWith (":*") && !sToken.startsWith ("Q{"))
          {
            final String sUri = aEl.lookupNamespaceUri (sToken.substring (0, sToken.length () - 2));
            if (sUri == null)
              throw XsltException.at (aEl, "XTSE0280", "The prefix of '" + sToken + "' is not bound");
            aRule = new SpaceRules.Rule (sUri, "", false, true, bStrip, aEl.getPrecedence ());
          }
          else
            if (sToken.startsWith ("Q{") && sToken.endsWith ("}*"))
              aRule = new SpaceRules.Rule (sToken.substring (2, sToken.length () - 2), "", false, true, bStrip, aEl.getPrecedence ());
            else
            {
              XdmQName aName = createGlobalContext ().resolveQName (aEl, sToken, false);
              if (!aName.hasPrefix () && !sToken.startsWith ("Q{") && sXpathDefault != null)
                aName = new XdmQName (sXpathDefault.trim (), aName.localName ());
              aRule = new SpaceRules.Rule (aName.namespaceUri (), aName.localName (), false, false, bStrip, aEl.getPrecedence ());
            }
      for (final SpaceRules.Rule aOther : m_aStylesheet.getSpaceRules ().rules ())
        if (aOther.strip () != bStrip &&
            aOther.precedence () == aRule.precedence () &&
            aOther.namespaceUri ().equals (aRule.namespaceUri ()) &&
            aOther.localName ().equals (aRule.localName ()) &&
            aOther.anyNamespace () == aRule.anyNamespace () &&
            aOther.anyLocalName () == aRule.anyLocalName ())
          throw XsltException.at (aEl, "XTSE0270", "Conflicting xsl:strip-space and xsl:preserve-space for '" + sToken + "'");
      m_aStylesheet.getSpaceRules ().addRule (aRule);
    }
  }

  private void _compileAttributeSet (@NonNull final XslElement aEl)
  {
    final CompileContext aCtx = createGlobalContext ();
    final XdmQName aName = mapAttributeSetName (aEl, aCtx.resolveQName (aEl, aEl.getAttr ("name"), false));
    if (PackageModel.isOverride (aEl))
    {
      // An overriding attribute set replaces the overridden one, which stays
      // reachable as xsl:original
      final AttributeSet aOriginal = m_aStylesheet.attributeSets ().remove (aName);
      if (aOriginal != null)
      {
        final XdmQName aOriginalName = _createOriginalName (aEl);
        final AttributeSet aRenamed = new AttributeSet (aOriginalName);
        aRenamed.decls ().addAll (aOriginal.decls ());
        m_aStylesheet.attributeSets ().put (aOriginalName, aRenamed);
      }
    }
    final ICommonsList <XdmQName> aUses = new CommonsArrayList <> ();
    final String sUses = aEl.getAttr ("use-attribute-sets");
    if (sUses != null)
      for (final String sToken : XsltNames.tokenize (sUses))
      {
        final XdmQName aUse = mapAttributeSetName (aEl, mapOriginalName (aEl, aCtx.resolveQName (aEl, sToken, false)));
        aUses.add (aUse);
        registerAttributeSetRef (aEl, aUse);
      }
    final SequenceConstructor aBody = aCtx.compileSequenceConstructor (aEl, aEl.getChildren ());
    m_aStylesheet.attributeSets ().computeIfAbsent (aName, AttributeSet::new).addDecl (new AttributeSet.Decl (aEl, aUses, aBody));
  }

  private void _compileGlobalContextItem (@NonNull final XslElement aEl)
  {
    final String sAs = aEl.getAttr ("as");
    final String sUse = aEl.getAttr ("use") == null ? "optional" : aEl.getAttr ("use").trim ();
    final CompiledStylesheet.GlobalContextItem aPrev = m_aStylesheet.getGlobalContextItem ();
    final CompiledStylesheet.GlobalContextItem aNew = new CompiledStylesheet.GlobalContextItem (sAs == null ? null
                                                                                                           : createGlobalContext ().compileSequenceType (aEl,
                                                                                                                                                         sAs),
                                                                                              sUse);
    if (!m_aGlobalContextItemModules.add (aEl.getModule ()))
      throw XsltException.at (aEl, "XTSE3087", "A stylesheet module may contain only one xsl:global-context-item");
    final String sAsNorm = aNew.as () == null ? null : SequenceTypeMatcher.getAsString (aNew.as ());
    if (aPrev != null && (!aPrev.use ().equals (aNew.use ()) || !Objects.equals (m_sGlobalContextItemAs, sAsNorm)))
      throw XsltException.at (aEl, "XTSE3087", "Conflicting xsl:global-context-item declarations");
    m_sGlobalContextItemAs = sAsNorm;
    m_aStylesheet.setGlobalContextItem (aNew);
  }

  private void _compileDeclaration (@NonNull final XslElement aEl)
  {
    if (!aEl.isXsl ())
    {
      if (aEl.getModule ().isSimplified () && aEl == aEl.getModule ().getRoot ())
        _compileSimplifiedTemplate (aEl);
      else
        if (aEl.getName ().namespaceUri ().isEmpty ())
          throw XsltException.at (aEl, "XTSE0130", "A top-level element must have a non-null namespace URI");
      return;
    }
    final String sLocal = aEl.getLocalName ();
    switch (sLocal)
    {
      case "template" -> _compileTemplate (aEl);
      case "variable", "param" ->
      {
        if (!XsltNames.isXsltTrue (aEl.getAttr ("static")) || m_aStylesheet.staticValues ().containsKey (createGlobalContext ().resolveQName (aEl, aEl.getAttr ("name"), false)))
          _compileGlobal (aEl, "param".equals (sLocal));
      }
      case "function" ->
      {
        final UserFunction aFunc = m_aFunctionsByElement.get (aEl);
        CompileContext aCtx = createGlobalContext ();
        for (final VariableDef aParam : aFunc.getParams ())
          aCtx = aCtx.withVariable (aParam.getName ());
        aFunc.setBody (aCtx.compileContent (aEl));
        m_aStylesheet.functions ().add (aFunc);
      }
      case "key" -> _compileKey (aEl);
      case "output" -> _compileOutput (aEl);
      case "strip-space" -> _compileSpace (aEl, true);
      case "preserve-space" -> _compileSpace (aEl, false);
      case "attribute-set" -> _compileAttributeSet (aEl);
      case "global-context-item" -> _compileGlobalContextItem (aEl);
      case "decimal-format", "namespace-alias", "mode", "include", "import" ->
      {
        // handled before
      }
      case "expose" ->
      {
        if (!aEl.getParent ().isXsl ("package"))
          throw XsltException.at (aEl, "XTSE0010", "xsl:expose is only allowed in xsl:package");
        // The xsl:expose of a used package is applied by the package model
        if (m_aPackages.isInPrincipal (aEl))
          m_aExposes.add (aEl);
      }
      case "import-schema" ->
      {
        // Compiled before all other declarations by a schema-aware processor
        if (!m_aStylesheet.isSchemaAware ())
          throw XsltException.at (aEl, "XTSE1650", "xsl:import-schema requires a schema-aware processor");
      }
      default ->
      {
        final IDeclarationCompiler aCompiler = m_aRegistry.getDeclarationCompiler (sLocal);
        if (aCompiler != null)
          aCompiler.compile (createGlobalContext (), aEl);
        else
          if (!aEl.isForwardsCompatible ())
            throw XsltException.at (aEl,
                                    "XTSE0010",
                                    ElementValidator.TOP_LEVEL_DECLARATIONS.contains (sLocal) ? "xsl:" + sLocal + " is not supported"
                                                                                              : "xsl:" + sLocal + " is not allowed at the top level");
      }
    }
  }

  private static boolean _isPackageVersion (@NonNull final String s)
  {
    if (s.isEmpty ())
      return false;
    final String [] aParts = s.split ("-", -1);
    for (final String sNum : aParts[0].split ("\\.", -1))
      if (sNum.isEmpty () || !sNum.chars ().allMatch (c -> c >= '0' && c <= '9'))
        return false;
    for (int i = 1; i < aParts.length; ++i)
      if (!XsltNames.isNCName (aParts[i]))
        return false;
    return true;
  }

  private void _checkDeclaredModesRecursive (@NonNull final XslElement aEl)
  {
    final boolean bTemplateRule = aEl.isXsl ("template") && aEl.hasAttr ("match");
    if (bTemplateRule || aEl.isXsl ("apply-templates"))
    {
      final ICommonsList <XdmQName> aRefs = new CommonsArrayList <> ();
      final String sMode = aEl.getAttr ("mode");
      if (sMode == null)
        aRefs.add (getDefaultMode (aEl));
      else
        for (final String sToken : XsltNames.tokenize (sMode))
          switch (sToken)
          {
            case "#all", "#current" ->
            {
              // no particular mode
            }
            case "#unnamed" -> aRefs.add (Mode.UNNAMED);
            case "#default" -> aRefs.add (getDefaultMode (aEl));
            default -> aRefs.add (createGlobalContext ().resolveQName (aEl, sToken, false));
          }
      for (final XdmQName aRef : aRefs)
      {
        final Mode aMode = m_aStylesheet.getMode (aRef);
        if (aMode == null || aMode.getDeclaration () == null)
          throw XsltException.at (aEl,
                                  "XTSE3085",
                                  "The mode " + (Mode.UNNAMED.equals (aRef) ? "#unnamed" : aRef.getEQName ()) + " is not declared by an xsl:mode declaration");
      }
    }
    for (final XslElement aChild : aEl.getElementChildren ())
      _checkDeclaredModesRecursive (aChild);
  }

  /** Checks that apply only when the principal module is an xsl:package. */
  private void _checkPackage (@NonNull final XslElement aRoot, @NonNull final ICommonsList <XslModule> aModules)
  {
    final String sVersion = aRoot.getAttr ("package-version");
    if (sVersion != null && !_isPackageVersion (sVersion))
      throw XsltException.at (aRoot, "XTSE0020", "package-version='" + sVersion + "' is not a valid package version");
    final String sDeclared = aRoot.getAttr ("declared-modes");
    if (sDeclared != null && !XsltNames.isXsltBooleanLexical (sDeclared))
      throw XsltException.at (aRoot, "XTSE0020", "declared-modes='" + sDeclared + "' is not a boolean");
    if (sDeclared == null || XsltNames.isXsltTrue (sDeclared))
      for (final XslModule aModule : aModules)
        if (aModule.getRoot () != null)
          _checkDeclaredModesRecursive (aModule.getRoot ());
  }

  private void _buildModes ()
  {
    m_aStylesheet.getOrCreateMode (Mode.UNNAMED);
    m_aStylesheet.getOrCreateMode (m_aStylesheet.getDefaultModeName ());
    for (final Template aTemplate : m_aStylesheet.templates ())
    {
      final CompiledPattern aPattern = aTemplate.getPattern ();
      if (aPattern == null)
        continue;
      final ICommonsList <TemplateRule> aRules = new CommonsArrayList <> ();
      if (aTemplate.getPriority () != null)
        aRules.add (new TemplateRule (aTemplate, aPattern, aTemplate.getPriority ().doubleValue (), m_nOrder++));
      else
        for (final XsltPattern aAlt : aPattern.pattern ().getAlternatives ())
          aRules.add (new TemplateRule (aTemplate,
                                        new CompiledPattern (aAlt, aPattern.staticContext ()),
                                        aAlt.getDefaultPriority (),
                                        m_nOrder++));
      if (aTemplate.isAllModes ())
      {
        for (final Mode aMode : m_aStylesheet.modes ().values ())
          for (final TemplateRule aRule : aRules)
            aMode.addRule (aRule);
      }
      else
        for (final XdmQName aModeName : aTemplate.getModes ())
          for (final TemplateRule aRule : aRules)
            m_aStylesheet.getOrCreateMode (aModeName).addRule (aRule);
    }
    for (final Mode aMode : m_aStylesheet.modes ().values ())
      aMode.sortRules ();
  }

  private void _checkAttributeSetCycle (@NonNull final XdmQName aName,
                                        @NonNull final ICommonsList <XdmQName> aStack,
                                        @NonNull final ICommonsSet <XdmQName> aDone)
  {
    if (aDone.contains (aName))
      return;
    final AttributeSet aSet = m_aStylesheet.getAttributeSet (aName);
    if (aSet == null)
      return;
    if (aStack.contains (aName))
      throw XsltException.at (aSet.decls ().getFirst ().element (), "XTSE0720", "The attribute set " + aName.getEQName () + " uses itself");
    aStack.add (aName);
    for (final AttributeSet.Decl aDecl : aSet.decls ())
      for (final XdmQName aUse : aDecl.uses ())
        _checkAttributeSetCycle (aUse, aStack, aDone);
    aStack.removeLast ();
    aDone.add (aName);
  }

  private void _checkReferences ()
  {
    for (final CallTemplateRef aRef : m_aCallTemplateRefs)
    {
      final Template aTemplate = m_aStylesheet.getNamedTemplate (aRef.name ());
      if (aTemplate == null || m_aPackages.isHidden (aRef.element (), PackageComponent.getSymbolicName ("template", aRef.name (), 0)))
        throw XsltException.at (aRef.element (), "XTSE0650", "No template named " + aRef.name ().getEQName ());
      for (final XdmQName aParamName : aRef.nonTunnelParams ())
      {
        boolean bFound = false;
        for (final VariableDef aParam : aTemplate.params ())
          if (aParam.getName ().equals (aParamName) && !aParam.isTunnel ())
            bFound = true;
        if (!bFound)
          throw XsltException.at (aRef.element (),
                                  "XTSE0680",
                                  "The template " + aRef.name ().getEQName () + " has no parameter $" + aParamName.getEQName ());
      }
      for (final VariableDef aParam : aTemplate.params ())
        if (aParam.isRequired () && !aParam.isTunnel () && !aRef.nonTunnelParams ().contains (aParam.getName ()))
          throw XsltException.at (aRef.element (),
                                  "XTSE0690",
                                  "The required parameter $" + aParam.getName ().getEQName () + " is not supplied");
    }
    for (final AttributeSetRef aRef : m_aAttributeSetRefs)
      if (m_aStylesheet.getAttributeSet (aRef.name ()) == null ||
          m_aPackages.isHidden (aRef.element (), PackageComponent.getSymbolicName ("attribute-set", aRef.name (), 0)))
        throw XsltException.at (aRef.element (), "XTSE0710", "No attribute set named " + aRef.name ().getEQName ());
    // Circular attribute sets (XTSE0720)
    for (final AttributeSet aSet : m_aStylesheet.attributeSets ().values ())
    {
      aSet.decls ().sort ( (a, b) -> Integer.compare (a.element ().getPrecedence (), b.element ().getPrecedence ()));
      final ICommonsList <XdmQName> aStack = new CommonsArrayList <> ();
      final ICommonsSet <XdmQName> aDone = new CommonsHashSet <> ();
      _checkAttributeSetCycle (aSet.getName (), aStack, aDone);
    }
  }

  @NonNull
  private CompiledStylesheet _compile (@NonNull final XdmNode aSource)
  {
    XdmNode aRootSrc = aSource;
    if (aSource.isDocument ())
    {
      aRootSrc = null;
      for (int i = 0; i < aSource.getChildCount (); ++i)
        if (aSource.getChildAtIndex (i).isElement ())
        {
          aRootSrc = aSource.getChildAtIndex (i);
          break;
        }
      if (aRootSrc == null)
        throw new XsltException ("XTSE0010", "The stylesheet has no document element", m_aOptions.getBaseUri (), 0, 0);
    }
    final StylesheetLoader aLoader = new StylesheetLoader (m_aOptions);
    final XslModule aPrincipal = aLoader.loadModule (aRootSrc, m_aOptions.getBaseUri (), true);
    final ICommonsList <StylesheetLoader.Level> aLevels = aLoader.arrangeByPrecedence (aPrincipal);
    String sInputTypeAnnotations = null;
    for (final XslModule aModule : aLoader.getAllModules ())
      if (aModule.getRoot () != null)
      {
        ElementValidator.validateTree (aModule.getRoot (), m_aOptions.isSchemaAware ());
        final String s = aModule.getRoot ().getAttr ("input-type-annotations");
        if (s != null && aModule.getRoot ().isXsl () && !"unspecified".equals (s.trim ()))
        {
          if (sInputTypeAnnotations != null && !sInputTypeAnnotations.equals (s.trim ()))
            throw XsltException.at (aModule.getRoot (), "XTSE0265", "Conflicting input-type-annotations values");
          sInputTypeAnnotations = s.trim ();
        }
      }
    m_aPackages = aLoader.getPackages ();
    m_aPackages.analyse ();

    final FunctionLibrary aLib = FunctionLibrary.createDefault ();
    m_aRegistry.contributeFunctions (aLib);
    m_aStylesheet = new CompiledStylesheet (aLib, aPrincipal);
    m_aStylesheet.setPackages (m_aPackages);
    m_aStylesheet.modules ().addAll (aLoader.getAllModules ());
    for (final Map.Entry <XdmQName, XdmSequence> aEntry : aLoader.getStaticValues ().entrySet ())
      m_aStylesheet.staticValues ().put (aEntry.getKey (), aEntry.getValue ());
    if (!aPrincipal.isSimplified ())
      m_aStylesheet.setDefaultModeName (getDefaultMode (aPrincipal.getRoot ()));

    // Preparation: everything expressions may refer to
    final ICommonsList <XslElement> aAll = new CommonsArrayList <> ();
    for (final StylesheetLoader.Level aLevel : aLevels)
      aAll.addAll (aLevel.declarations ());
    if (m_aOptions.isSchemaAware ())
    {
      // The in-scope schema definitions must exist before any expression is
      // compiled
      m_aStylesheet.setSchemaSet (SchemaImportCompiler.compile (aAll, m_aPackages, m_aOptions));
      m_aStylesheet.setStripInputTypeAnnotations ("strip".equals (sInputTypeAnnotations));
    }
    final ICommonsList <XslElement> aDecimalFormats = new CommonsArrayList <> ();
    final ICommonsMap <Mode, ICommonsMap <String, Object []>> aModeAttrs = new CommonsHashMap <> ();
    for (final XslElement aEl : aAll)
      if (aEl.isXsl ())
        switch (aEl.getLocalName ())
        {
          case "namespace-alias" -> _prepareNamespaceAlias (aEl);
          case "decimal-format" -> aDecimalFormats.add (aEl);
          case "variable", "param" -> _prepareGlobal (aEl);
          default ->
          {
            // later
          }
        }
    if (m_aPackages.isMultiPackage ())
    {
      for (final PackageUnit aUnit : m_aPackages.getUnits ())
      {
        m_aDefaultDecimalFormat = null;
        m_aDecimalFormats.clear ();
        _prepareDecimalFormats (aDecimalFormats.getAll (x -> m_aPackages.getUnit (x) == aUnit));
        m_aDecimalFormatsByUnit.put (aUnit, new Object [] { m_aDefaultDecimalFormat, new CommonsHashMap <> (m_aDecimalFormats) });
      }
      final Object [] aPrincipalFormats = m_aDecimalFormatsByUnit.get (m_aPackages.getPrincipal ());
      m_aDefaultDecimalFormat = (FormatDecimalFormat) aPrincipalFormats[0];
      m_aDecimalFormats.clear ();
    }
    else
      _prepareDecimalFormats (aDecimalFormats);
    for (final XslElement aEl : aAll)
      if (aEl.isXsl ("function"))
        _prepareFunction (aEl);
      else
        if (aEl.isXsl ("mode"))
          _prepareMode (aEl, aModeAttrs);
    for (final Map.Entry <Mode, ICommonsMap <String, Object []>> aEntry : aModeAttrs.entrySet ())
      for (final Map.Entry <String, Object []> aAttr : aEntry.getValue ().entrySet ())
        if (aAttr.getValue ()[2] != null)
          throw XsltException.at ((XslElement) aAttr.getValue ()[2],
                                  "XTSE0545",
                                  "Conflicting xsl:mode/@" + aAttr.getKey () + " declarations for mode " + aEntry.getKey ());

    _buildPackageFunctionScopes ();
    if (m_aPackages.isMultiPackage ())
      _findLocalAttributeSets (aAll);
    for (final XslElement aEl : aAll)
      _compileDeclaration (aEl);

    if (m_aStylesheet.getNamedTemplate (INITIAL_TEMPLATE) == null)
    {
      // nothing to do: xsl:initial-template is optional
    }
    _buildModes ();
    if (m_aPackages.isMultiPackage ())
      _computePackageGlobals ();
    for (final PackageUnit aUnit : m_aPackages.getUnits ())
      if (aUnit.getRoot () != null && aUnit.getRoot ().isXsl ("package"))
        _checkPackage (aUnit.getRoot (), aUnit.modules ());
    if (aPrincipal.getRoot ().isXsl ("package"))
    {
      m_aStylesheet.setPackage (true);
      final ICommonsList <XslElement> aRoots = new CommonsArrayList <> ();
      for (final XslModule aModule : m_aPackages.getPrincipal ().modules ())
        if (aModule.getRoot () != null && !aModule.isSimplified ())
          aRoots.add (aModule.getRoot ());
      PackageChecks.checkOwnAbstract (aRoots);
      ExposeChecker.check (createGlobalContext (), m_aExposes);
      // A mode used only implicitly that xsl:expose makes private
      final PackageChecks.Exposure aExposure = m_aPackages.getExposure (aPrincipal.getRoot ());
      if (aExposure != null)
        for (final Map.Entry <XdmQName, String> aEntry : aExposure.implicitModes ().entrySet ())
          if ("private".equals (aEntry.getValue ()))
            m_aStylesheet.privateModes ().add (aEntry.getKey ());
    }
    _checkReferences ();
    final CompileContext aGlobal = createGlobalContext ();
    for (final ICompileFinisher aFinisher : m_aRegistry.getAllCompileFinishers ())
      aFinisher.finish (aGlobal);
    return m_aStylesheet;
  }

  /**
   * Compile a stylesheet.
   *
   * @param aSource
   *        Document or element node of the principal module.
   * @param aOptions
   *        Options.
   * @return The compiled stylesheet.
   * @throws XsltException
   *         for static errors
   */
  @NonNull
  public static CompiledStylesheet compile (@NonNull final XdmNode aSource, @NonNull final XsltCompileOptions aOptions)
  {
    if (aOptions.isSchemaAware () && !XdmSchemaSupport.isAvailable ())
      throw new IllegalStateException ("A schema-aware stylesheet needs Apache Xerces-J (xerces:xercesImpl) on the class path");
    return new StylesheetCompiler (aOptions)._compile (aSource);
  }
}
