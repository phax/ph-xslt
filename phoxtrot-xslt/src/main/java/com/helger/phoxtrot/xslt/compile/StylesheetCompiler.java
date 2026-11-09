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
package com.helger.phoxtrot.xslt.compile;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.CommonsLinkedHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsOrderedSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.schema.XdmSchemaSupport;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.collation.CollationRegistry;
import com.helger.phoxtrot.xpath.context.IResourceResolver;
import com.helger.phoxtrot.xpath.eval.SequenceTypeMatcher;
import com.helger.phoxtrot.xpath.fn.FnUri;
import com.helger.phoxtrot.xpath.fn.FormatDecimalFormat;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.pattern.CompiledXsltPattern;
import com.helger.phoxtrot.xpath.pattern.XsltPattern;
import com.helger.phoxtrot.xpath.pattern.XsltPatternParser;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.fn.XsltFunctions;
import com.helger.phoxtrot.xslt.instr.IInstruction;
import com.helger.phoxtrot.xslt.instr.SequenceConstructor;
import com.helger.phoxtrot.xslt.model.AttributeSet;
import com.helger.phoxtrot.xslt.model.CompiledStylesheet;
import com.helger.phoxtrot.xslt.model.EOnNoMatch;
import com.helger.phoxtrot.xslt.model.KeyDefinition;
import com.helger.phoxtrot.xslt.model.Mode;
import com.helger.phoxtrot.xslt.model.OutputDefinition;
import com.helger.phoxtrot.xslt.model.SpaceRules;
import com.helger.phoxtrot.xslt.model.Template;
import com.helger.phoxtrot.xslt.model.TemplateRule;
import com.helger.phoxtrot.xslt.model.UserFunction;
import com.helger.phoxtrot.xslt.model.VariableDef;
import com.helger.phoxtrot.xslt.pkg.PackageChecks;
import com.helger.phoxtrot.xslt.pkg.PackageComponent;
import com.helger.phoxtrot.xslt.pkg.PackageModel;
import com.helger.phoxtrot.xslt.pkg.PackageUnit;
import com.helger.phoxtrot.xslt.schema.SchemaImportCompiler;
import com.helger.phoxtrot.xslt.stream.StreamVerdicts;
import com.helger.phoxtrot.xslt.stream.StreamabilityIndex;
import com.helger.phoxtrot.xslt.stream.XsltStreamability;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslModule;
import com.helger.phoxtrot.xslt.tree.XslNode;
import com.helger.phoxtrot.xslt.tree.XslText;
import com.helger.phoxtrot.xslt.tree.XsltNames;

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

  /**
   * A reference to an attribute set ([xsl:]use-attribute-sets), checked when
   * all attribute sets are known.
   */
  private record AttributeSetRef (@NonNull XslElement element, @NonNull XdmQName name)
  {}

  /**
   * The xsl:function that currently wins for a name and arity, with the import
   * precedence of its declaration.
   */
  private record FunctionEntry (@NonNull UserFunction function, int precedence)
  {}

  /** The key of an xsl:namespace-alias: its package and literal namespace URI. */
  private record AliasKey (@NonNull PackageUnit unit, @NonNull String literalUri)
  {}

  /**
   * Declarations that clash with another one of the same name (or key) and the
   * same import precedence. Such a clash is only an error if no declaration
   * with the same name and a higher import precedence exists (XSLT 3.0
   * XTSE0630, XTSE0660, XTSE0770, XTSE0810), so the error is raised only by
   * {@link #check(String)} once all declarations are known.
   */
  private static final class DuplicateTracker
  {
    private final ICommonsMap <Object, Integer> m_aHighest = new CommonsHashMap <> ();
    private final ICommonsOrderedMap <Object, Object []> m_aDuplicates = new CommonsLinkedHashMap <> ();

    /**
     * Note a declaration.
     *
     * @param aKey
     *        The name or key of the declaration.
     * @param aEl
     *        The declaration.
     * @param sDuplicateMessage
     *        The error message if the declaration clashes with an earlier one
     *        of the same import precedence, <code>null</code> if it does not.
     */
    void add (@NonNull final Object aKey, @NonNull final XslElement aEl, @Nullable final String sDuplicateMessage)
    {
      final int nPrecedence = aEl.getPrecedence ();
      final Integer aHighest = m_aHighest.get (aKey);
      if (aHighest == null || aHighest.intValue () < nPrecedence)
        m_aHighest.put (aKey, Integer.valueOf (nPrecedence));
      if (sDuplicateMessage != null)
      {
        final Object [] aPrev = m_aDuplicates.get (aKey);
        if (aPrev == null || ((XslElement) aPrev[0]).getPrecedence () < nPrecedence)
          m_aDuplicates.put (aKey, new Object [] { aEl, sDuplicateMessage });
      }
    }

    /**
     * Raise the error for the first clash (in the order noted) for which no
     * declaration with a higher import precedence was noted.
     *
     * @param sCode
     *        The error code.
     * @throws XsltException
     *         With the code, at the clashing declaration
     */
    void check (@NonNull final String sCode)
    {
      for (final Map.Entry <Object, Object []> aEntry : m_aDuplicates.entrySet ())
      {
        final XslElement aEl = (XslElement) aEntry.getValue ()[0];
        if (aEl.getPrecedence () == m_aHighest.get (aEntry.getKey ()).intValue ())
          throw XsltException.at (aEl, sCode, (String) aEntry.getValue ()[1]);
      }
    }
  }

  /**
   * The declared signature of a stylesheet function: the parameter types and
   * the return type, each <code>item()*</code> where no <code>as</code>
   * attribute is given.
   */
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
  private final DuplicateTracker m_aDuplicateGlobals = new DuplicateTracker ();
  private final DuplicateTracker m_aDuplicateFunctions = new DuplicateTracker ();
  private final DuplicateTracker m_aDuplicateTemplates = new DuplicateTracker ();
  private final DuplicateTracker m_aDuplicateAliases = new DuplicateTracker ();
  private final ICommonsOrderedSet <OutputDefinition> m_aMergedOutputs = new CommonsLinkedHashSet <> ();
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
  private final StreamabilityIndex m_aStreamabilityIndex;

  /**
   * Create a compiler for one compilation. The streamability index is only
   * created when the streamability analysis runs at the end, that is when
   * {@link XsltCompileOptions#isEnforceStreamability()} or
   * {@link XsltCompileOptions#isStreamingClaim()} is set.
   *
   * @param aOptions
   *        The compile options.
   */
  private StylesheetCompiler (@NonNull final XsltCompileOptions aOptions)
  {
    m_aOptions = aOptions;
    m_aRegistry = XsltExtensionRegistry.getInstance ();
    // Only the streamability analysis needs the operands afterwards: it runs
    // when streamability declarations are binding, or when streaming is
    // claimed and its verdicts select the streamed execution
    m_aStreamabilityIndex = aOptions.isEnforceStreamability () || aOptions.isStreamingClaim () ? new StreamabilityIndex ()
                                                                                                 : null;
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

  /**
   * @param aEl
   *        A stylesheet element.
   * @return <code>true</code> if the element is processed with XSLT 1.0
   *         behaviour (XSLT 3.0 section 3.9.1): the compilation claims
   *         backwards compatibility
   *         ({@link XsltCompileOptions#isBackwardsCompatibleClaim()}) and the
   *         effective version of the element is below 2.0
   *         ({@link XslElement#isXslt10Version()}).
   */
  public boolean isXslt10Behaviour (@NonNull final XslElement aEl)
  {
    return m_aOptions.isBackwardsCompatibleClaim () && aEl.isXslt10Version ();
  }

  @NonNull
  public XsltExtensionRegistry getRegistry ()
  {
    return m_aRegistry;
  }

  /**
   * @return The index the expressions, patterns and sequence types compiled
   *         are recorded in for the streamability analysis, or
   *         <code>null</code> if the analysis does not run (neither
   *         {@link XsltCompileOptions#isEnforceStreamability()} nor
   *         {@link XsltCompileOptions#isStreamingClaim()}).
   */
  @Nullable
  public StreamabilityIndex getStreamabilityIndex ()
  {
    return m_aStreamabilityIndex;
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
   * Look up the xsl:namespace-alias of the principal package for a literal
   * namespace URI (XSLT 3.0 section 11.1.4).
   *
   * @param sStylesheetUri
   *        A namespace URI used in the stylesheet, the empty string for no
   *        namespace.
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
   *         are local to a package). Outside a multi-package compilation, or
   *         for a package without own entry, this is the stylesheet-wide one;
   *         <code>null</code> if no unnamed xsl:decimal-format is declared.
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

  /**
   * Walk up from an element to the declaration that contains it: the
   * top-level element below the module root, or the element directly below an
   * xsl:override. The module root itself and a parentless element are
   * returned unchanged.
   *
   * @param aEl
   *        An element in a stylesheet module.
   * @return The containing declaration, never <code>null</code>.
   */
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
   *         in an overriding function with xsl:original. The library of the
   *         containing overriding declaration wins over the one of the
   *         package; without either, the stylesheet-wide library is returned.
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
   * component is kept (XSLT 3.0 section 3.5.3.4). Any other name, and
   * xsl:original outside a declaration that registered an original name, is
   * returned unchanged.
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
   * whose name is declared in several packages is local to its package. If
   * the name, qualified by the package of the referring element
   * ({@link PackageModel#qualify}), is one of those local attribute sets, the
   * qualified name is returned, otherwise the name itself.
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

  /**
   * Find the attribute sets that must be kept apart per package: a
   * non-overriding xsl:attribute-set whose name is declared in at least two
   * packages and whose visibility (as changed by xsl:expose, else as
   * declared) is <code>private</code>. Their package-qualified names are
   * collected for {@link #mapAttributeSetName(XslElement, XdmQName)}.
   *
   * @param aDecls
   *        All top-level declarations.
   */
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

  /**
   * Create a fresh name <code>xsl:original-N</code> under which the component
   * overridden by a declaration is kept, and remember it for
   * {@link #mapOriginalName(XslElement, XdmQName)}.
   *
   * @param aDecl
   *        The overriding declaration.
   * @return The new name.
   */
  @NonNull
  private XdmQName _createOriginalName (@NonNull final XslElement aDecl)
  {
    final XdmQName ret = new XdmQName (XsltNames.NS_XSL, "original-" + (++m_nOriginalCounter));
    m_aOriginalNames.put (aDecl, ret);
    return ret;
  }

  /**
   * <code>fn:function-lookup</code> in a used package finds the package's own
   * functions: a function declared only by the principal package does not
   * exist there, an override is not seen (the package's own original is), and
   * an abstract function does not exist.
   */
  private void _restrictLookupInUsedPackage (@NonNull final PackageUnit aUnit, @NonNull final FunctionLibrary aLib)
  {
    for (final FunctionEntry aEntry : m_aFunctionsByKey.values ())
    {
      final UserFunction aFunc = aEntry.function ();
      UserFunction aOwn = aFunc;
      if (m_aPackages.isInPrincipal (aFunc.getElement ()))
      {
        aOwn = m_aOriginalFunctions.get (aFunc.getElement ());
        if (aOwn != null && m_aPackages.getUnit (aOwn.getElement ()) != aUnit)
          aOwn = null;
      }
      if (aOwn == null || "abstract".equals (aOwn.getVisibility ()))
        aLib.hideFromLookup (aFunc.getName (), aFunc.getArity ());
      else
        if (aOwn != aFunc)
        {
          final FunctionSignature aSig = m_aFunctionSignatures.get (aOwn);
          aLib.registerForLookup (aOwn.getName (), aSig.paramTypes (), aSig.returnType (), aOwn, true);
        }
    }
  }

  /**
   * Build the function libraries that differ from the stylesheet-wide one. In
   * a multi-package compilation every package gets a child library, registered
   * as its scope with the package model, in which the functions the package
   * sees as hidden are hidden, the functions shadowed by a visible homonym
   * that the package itself declares are registered again, and (for a used
   * package) <code>fn:function-lookup</code> is restricted by
   * {@link #_restrictLookupInUsedPackage(PackageUnit, FunctionLibrary)}. Then
   * every overriding xsl:function gets a child library of its package's one in
   * which xsl:original names the overridden function for static calls and
   * function references, but not for <code>fn:function-lookup</code> (XSLT 3.0
   * section 3.5.3.4).
   */
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
        if (aUnit != m_aPackages.getPrincipal ())
          _restrictLookupInUsedPackage (aUnit, aLib);
        m_aUnitLibraries.put (aUnit, aLib);
      }
    for (final Map.Entry <XslElement, UserFunction> aEntry : m_aOriginalFunctions.entrySet ())
    {
      final FunctionLibrary aBase = m_aUnitLibraries.get (m_aPackages.getUnit (aEntry.getKey ()));
      final FunctionLibrary aLib = (aBase != null ? aBase : m_aStylesheet.getFunctionLibrary ()).createChild ();
      final FunctionSignature aSig = m_aFunctionSignatures.get (aEntry.getValue ());
      aLib.registerParsed (XSL_ORIGINAL, aSig.paramTypes (), aSig.returnType (), aEntry.getValue (), false);
      // A static call or function reference only, not fn:function-lookup
      aLib.hideFromLookup (XSL_ORIGINAL, aSig.paramTypes ().size ());
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
   * @return The default mode in scope for it ([xsl:]default-mode, XSLT 3.0
   *         section 3.7.2), taken from the innermost ancestor-or-self with the
   *         attribute; {@link Mode#UNNAMED} if there is none or the value is
   *         empty or <code>#unnamed</code>.
   * @throws XsltException
   *         XTSE0020 if the value is not a valid QName
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

  /**
   * @return <code>true</code> if the element has a child element or a text
   *         child that is not whitespace only.
   */
  private static boolean _hasContent (@NonNull final XslElement aEl)
  {
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof XslElement || !((XslText) aNode).isWhitespaceOnly ())
        return true;
    return false;
  }

  /**
   * Reject a pattern that calls one of the functions that are not allowed in
   * patterns: <code>fn:current-group</code> (XTSE1060),
   * <code>fn:current-grouping-key</code> (XTSE1070),
   * <code>fn:current-merge-group</code> (XTSE3470) and
   * <code>fn:current-merge-key</code> (XTSE3500). A pattern that is not a
   * {@link CompiledXsltPattern} is not inspected.
   *
   * @param aEl
   *        The element carrying the pattern, for the error location.
   * @param aPattern
   *        The compiled pattern.
   * @throws XsltException
   *         with the code of the first forbidden function found
   */
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

  /**
   * Record an xsl:namespace-alias for the package of the element (XSLT 3.0
   * section 11.1.4), keyed by the literal namespace URI (empty for
   * <code>#default</code> without a default namespace). The declaration with
   * the higher import precedence wins; at equal precedence the later one
   * wins, which also takes its result prefix. A declaration that maps the
   * same URI to a different result URI than an earlier one with the same
   * precedence is noted in {@link #m_aDuplicateAliases}: XTSE0810 is raised
   * once all declarations are known, unless one with a higher precedence
   * exists.
   *
   * @param aEl
   *        The xsl:namespace-alias element.
   * @throws XsltException
   *         XTSE0812 if a prefix other than <code>#default</code> is not
   *         bound
   */
  private void _prepareNamespaceAlias (@NonNull final XslElement aEl)
  {
    final String sStyPrefix = aEl.getAttr ("stylesheet-prefix").trim ();
    final String sResPrefix = aEl.getAttr ("result-prefix").trim ();
    final String sStyUri = aEl.lookupNamespaceUri ("#default".equals (sStyPrefix) ? "" : sStyPrefix);
    final String sResUri = aEl.lookupNamespaceUri ("#default".equals (sResPrefix) ? "" : sResPrefix);
    if ((!"#default".equals (sStyPrefix) && sStyUri == null) || (!"#default".equals (sResPrefix) && sResUri == null))
      throw XsltException.at (aEl, "XTSE0812", "A prefix of xsl:namespace-alias is not bound");
    final String sKey = sStyUri == null ? "" : sStyUri;
    final NamespaceAlias aAlias = new NamespaceAlias ("#default".equals (sResPrefix) ? "" : sResPrefix,
                                                      sResUri == null ? "" : sResUri,
                                                      aEl.getPrecedence ());
    final ICommonsMap <String, NamespaceAlias> aAliases = m_aAliases.computeIfAbsent (m_aPackages.getUnit (aEl), k -> new CommonsHashMap <> ());
    final NamespaceAlias aPrev = aAliases.get (sKey);
    final boolean bConflict = aPrev != null &&
                              aPrev.precedence () == aAlias.precedence () &&
                              !aPrev.resultUri ().equals (aAlias.resultUri ());
    m_aDuplicateAliases.add (new AliasKey (m_aPackages.getUnit (aEl), sKey),
                             aEl,
                             bConflict ? "Conflicting xsl:namespace-alias declarations for '" + sStyPrefix + "'" : null);
    if (aPrev == null || aPrev.precedence () <= aAlias.precedence ())
      aAliases.put (sKey, aAlias);
  }

  /**
   * @return The first code point of the string, 0 for the empty string.
   */
  private static int _firstCodePoint (@NonNull final String s)
  {
    return s.isEmpty () ? 0 : s.codePointAt (0);
  }

  /**
   * Build the decimal formats from xsl:decimal-format declarations (XSLT 3.0
   * section 5.4). The declarations are grouped by name (the unnamed one under
   * an internal key); for each attribute the value of the declarations with
   * the highest import precedence that specify it is taken, unspecified
   * attributes keep the default of {@link FormatDecimalFormat}. Single
   * character properties take the first code point of the value. The result is
   * stored in the default decimal format and the named decimal formats of this
   * compiler, which the caller resets per package in a multi-package
   * compilation.
   *
   * @param aDecls
   *        The xsl:decimal-format declarations to combine.
   * @throws XsltException
   *         XTSE1290 if two declarations with that highest precedence give
   *         different values for one attribute; XTSE1295 if zero-digit is not
   *         a digit with numeric value zero; XTSE1300 if the decimal
   *         separator, grouping separator, percent, per-mille, digit and
   *         pattern separator are not distinct or one of them is among the ten
   *         digits starting at zero-digit (XSLT 3.0 section 5.4 does not list
   *         the exponent separator)
   */
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
                             aFormat.getPatternSeparator () };
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

  /**
   * Create the {@link UserFunction} for an xsl:function before any body is
   * compiled, so that all expressions can call it (XSLT 3.0 section 10.3). The
   * parameters and the declared types are compiled, <code>cache="yes"</code>
   * or <code>new-each-time="no"</code> turns on memoisation (section 10.3.8),
   * and the signature is recorded. An overriding function remembers the
   * function it replaces as its xsl:original. A function shadowed by a visible
   * homonym from another package is only kept for the scope of its own
   * package. Otherwise the function is registered in the stylesheet-wide
   * library if no function with this name and arity, or only one with a lower
   * import precedence, is registered yet. A second function with the same
   * name, arity and import precedence is noted in
   * {@link #m_aDuplicateFunctions}: XTSE0770 is raised once all functions are
   * known, unless one with a higher precedence exists.
   *
   * @param aEl
   *        The xsl:function element.
   * @throws XsltException
   *         XTSE0770 if a schema-aware stylesheet declares a one-argument
   *         function named like an imported simple type outside the XML
   *         Schema namespace (it would clash with the constructor function)
   */
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
    // A constructor function of an imported simple type has the same name
    if (aParams.size () == 1 &&
        m_aStylesheet.isSchemaAware () &&
        !XdmConstants.NS_XS.equals (aName.namespaceUri ()) &&
        m_aStylesheet.getSchemaSet ().getTypeDefinition (aName) != null &&
        !m_aStylesheet.getSchemaSet ().getTypeDefinition (aName).isComplex ())
      throw XsltException.at (aEl,
                              "XTSE0770",
                              "The function " + aName.getEQName () + "#1 clashes with the constructor function of a schema type");
    final String sKey = aName.getEQName () + "#" + aParams.size ();
    final FunctionEntry aPrev = m_aFunctionsByKey.get (sKey);
    final boolean bDuplicate = aPrev != null && aPrev.precedence () == aEl.getPrecedence ();
    m_aDuplicateFunctions.add (sKey, aEl, bDuplicate ? "Duplicate function " + sKey : null);
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

  /**
   * Make the name of a global xsl:variable or xsl:param known before any
   * expression is compiled, and track the highest import precedence it is
   * declared with, which decides the winning declaration in
   * {@link #_compileGlobal(XslElement, boolean)} (XSLT 3.0 section 9.5). A
   * second binding with the same name and import precedence is noted in
   * {@link #m_aDuplicateGlobals}: XTSE0630 is raised once all bindings are
   * known, unless one with a higher precedence exists.
   *
   * @param aEl
   *        The global xsl:variable or xsl:param element.
   */
  private void _prepareGlobal (@NonNull final XslElement aEl)
  {
    final XdmQName aName = createGlobalContext ().resolveQName (aEl, aEl.getAttr ("name"), false);
    final Integer aPrev = m_aGlobalPrecedences.get (aName);
    final boolean bDuplicate = aPrev != null && aPrev.intValue () == aEl.getPrecedence ();
    m_aDuplicateGlobals.add (aName, aEl, bDuplicate ? "Duplicate global variable $" + aName.getEQName () : null);
    if (aPrev == null || aPrev.intValue () < aEl.getPrecedence ())
      m_aGlobalPrecedences.put (aName, Integer.valueOf (aEl.getPrecedence ()));
    m_aGlobalNames.add (aName);
  }

  /**
   * Apply an xsl:mode declaration to its mode (XSLT 3.0 section 6.6.1). The
   * declaration with the highest import precedence (the later one at equal
   * precedence) becomes the mode's declaration. For each attribute given, the
   * value is normalised (booleans to <code>yes</code>/<code>no</code>,
   * use-accumulators to the sorted expanded names) and applied if no value
   * with a higher precedence was seen. A different value at the same
   * precedence is only noted in <code>aSeen</code>: the caller raises XTSE0545
   * for it unless a value with a higher precedence replaced it later.
   *
   * @param aEl
   *        The xsl:mode element.
   * @param aSeen
   *        Per mode and attribute: the normalised value, its precedence and
   *        the element of a conflicting declaration or <code>null</code>.
   */
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
        case "streamable" -> aMode.setStreamable (XsltNames.isXsltTrue (sValue));
        default ->
        {
          // use-accumulators has no effect here
        }
      }
    }
  }

  /**
   * Compile an xsl:template (XSLT 3.0 sections 6 and 10.1): the match pattern
   * with the pattern function checks, the modes (<code>#all</code>,
   * <code>#default</code>, <code>#unnamed</code> or names; the default mode
   * without a mode attribute), the priority, the result type, an optional
   * xsl:context-item, the parameters and the body. In a schema-aware
   * stylesheet the pattern is rewritten for a mode with
   * <code>typed="strict"</code> or <code>"lax"</code> (section 6.6.3; strict
   * wins if any of the modes is strict). An overriding named template keeps
   * the overridden one reachable under a fresh xsl:original name. The
   * template is added to the stylesheet; a named template is also registered
   * by name if it has the highest import precedence so far. A second named
   * template with the same name and import precedence is noted in
   * {@link #m_aDuplicateTemplates}: XTSE0660 is raised once all templates are
   * compiled, unless one with a higher precedence exists.
   *
   * @param aEl
   *        The xsl:template element.
   * @throws XsltException
   *         XTSE0530 for a priority that is not an <code>xs:decimal</code>;
   *         XTSE0010 for more than one xsl:context-item or one that is not
   *         the first child; XTSE0020 for an invalid <code>use</code> or
   *         <code>use="absent"</code> on a template without a name; XTSE3088
   *         for <code>use="absent"</code> together with <code>as</code>; the
   *         error of the typed-mode rewrite at the element
   */
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
    if (aPattern != null && m_aStylesheet.isSchemaAware ())
    {
      // Schema-aware: in a mode with typed="strict" or "lax", an element name
      // in the first step of the pattern names an element declaration
      Boolean aStrict = null;
      for (final XdmQName aMode : aModes)
      {
        final String sTyped = m_aStylesheet.getMode (aMode).getTyped ();
        if ("strict".equals (sTyped))
          aStrict = Boolean.TRUE;
        else
          if ("lax".equals (sTyped) && aStrict == null)
            aStrict = Boolean.FALSE;
      }
      if (aStrict != null)
        try
        {
          aPattern = new CompiledPattern (XsltPatternParser.rewriteForTypedMode (aPattern.pattern (),
                                                                                 m_aStylesheet.getSchemaSet (),
                                                                                 aStrict.booleanValue ()),
                                          aPattern.staticContext ());
        }
        catch (final XPathException ex)
        {
          throw XsltException.at (aEl, ex.getCode (), ex.getMessage ());
        }
    }
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
      final boolean bDuplicate = aPrev != null && aPrev.intValue () == aEl.getPrecedence ();
      m_aDuplicateTemplates.add (aName, aEl, bDuplicate ? "Duplicate named template " + aName.getEQName () : null);
      if (aPrev == null || aPrev.intValue () < aEl.getPrecedence ())
      {
        m_aNamedTemplatePrecedence.put (aName, Integer.valueOf (aEl.getPrecedence ()));
        m_aStylesheet.namedTemplates ().put (aName, aTemplate);
      }
    }
  }

  /**
   * Compile a simplified stylesheet module (XSLT 3.0 section 3.8): the
   * literal result element at the root becomes the body of a template rule
   * matching <code>/</code> in the unnamed mode.
   *
   * @param aEl
   *        The root element of the simplified module.
   */
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

  /**
   * A cheap, conservative test whether a declaration may refer to
   * xsl:original: some attribute value of the element or of a descendant
   * element contains the text <code>original</code>. Text content is not
   * inspected. Iterative, so deep trees cannot overflow the stack.
   *
   * @param aEl
   *        The declaration.
   * @return <code>true</code> if such an attribute value exists.
   */
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

  /**
   * @return The global variable or parameter with this name declared in the
   *         package itself with the highest import precedence (the last one
   *         compiled at equal precedence), or <code>null</code> if the package
   *         declares none.
   */
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

  /**
   * The declaration of a global variable a package sees looking down its
   * xsl:use-package dependencies: its own declaration if there is one, else
   * the first found depth first in the used packages, in the order of the
   * xsl:use-package elements. The recursion stops once it is deeper than the
   * number of packages, so a cycle cannot loop.
   *
   * @param aUnit
   *        The package.
   * @param aName
   *        The variable name.
   * @param nDepth
   *        Current recursion depth, 0 at the start.
   * @return The declaration found or <code>null</code>.
   */
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
   * name. For every name declared more than once, each package's view is the
   * one found by {@link #_getGlobalViewDown(PackageUnit, XdmQName, int)},
   * replaced by a declaration in an xsl:override of the xsl:use-package
   * chain above it (the outermost user wins). A view that differs from the
   * winning global is stored in the stylesheet's package globals.
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

  /**
   * Compile a global xsl:variable or xsl:param (XSLT 3.0 section 9.5). The
   * variable does not see itself. An overriding declaration that may mention
   * xsl:original (see {@link #_mentionsOriginal(XslElement)}) gets
   * <code>$xsl:original</code> bound to the previously compiled declaration of
   * the same name, recorded as its original variable (section 3.5.3.4). Every
   * compiled declaration is kept for the per-package views; it becomes the
   * stylesheet's global binding if its import precedence is the highest for
   * the name.
   *
   * @param aEl
   *        The declaration.
   * @param bParam
   *        <code>true</code> for xsl:param.
   */
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

  /**
   * Compile an xsl:key declaration (XSLT 3.0 section 20.2.1): the match
   * pattern with the pattern function checks, the key value from either the
   * use attribute or the content, the effective collation (the collation
   * attribute resolved against the static base URI, else the default
   * collation), the composite flag and whether the declaration is processed
   * with XSLT 1.0 behaviour (section 20.2.2). Several declarations of one key
   * are added side by side; only those of the same package are compared.
   *
   * @param aEl
   *        The xsl:key element.
   * @throws XsltException
   *         XTSE1205 if both or neither of use attribute and content are
   *         given; XTSE1210 if the collation is not supported; XTSE1220 or
   *         XTSE1222 if another declaration of the key in the same package
   *         has a different collation or composite value
   */
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
    m_aStylesheet.addKey (new KeyDefinition (aEl, aName, aMatch, aUse, aBody, bComposite, sCollation, isXslt10Behaviour (aEl)));
  }

  /**
   * Read the serialization parameter document named by the parameter-document
   * attribute of an xsl:output (XSLT 3.0 section 26, Serialization 3.1
   * section 3.1) at compile time. The URI is resolved against an absolute
   * static base URI and loaded through the configured resolver, else a
   * {@link FileResourceResolver}. Each child element of the document element
   * in the output namespace contributes its <code>value</code> attribute under
   * its local name; <code>output:use-character-maps</code> adds its
   * <code>output:character-map</code> entries as inline character mappings.
   * The document element must be <code>output:serialization-parameters</code>.
   * Nothing happens without the attribute.
   *
   * @param aEl
   *        The xsl:output element.
   * @param aAttrs
   *        Receives the parameters by name, replacing the attributes of the
   *        xsl:output itself (the parameter document takes precedence).
   * @param aDef
   *        Receives the character mappings.
   * @throws XsltException
   *         XTSE0020 if the URI is invalid or the document cannot be
   *         retrieved; SEPM0017 if its document element is not
   *         output:serialization-parameters or a character map entry has a
   *         character that is not a single character
   */
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
    final IXdmNode aDoc = aResolver.resolveDocument (sUri);
    if (aDoc == null)
      throw XsltException.at (aEl, "XTSE0020", "The parameter document '" + sUri + "' cannot be retrieved");
    IXdmNode aRoot = null;
    for (int i = 0; i < aDoc.getChildCount (); ++i)
      if (aDoc.getChildAtIndex (i).isElement ())
        aRoot = aDoc.getChildAtIndex (i);
    if (aRoot == null ||
        !SerializationParams.NS_OUTPUT.equals (aRoot.getName ().namespaceUri ()) ||
        !"serialization-parameters".equals (aRoot.getName ().localName ()))
      throw XsltException.at (aEl, "SEPM0017", "'" + sUri + "' is not a serialization parameter document");
    for (int i = 0; i < aRoot.getChildCount (); ++i)
    {
      final IXdmNode aParam = aRoot.getChildAtIndex (i);
      if (!aParam.isElement () || !SerializationParams.NS_OUTPUT.equals (aParam.getName ().namespaceUri ()))
        continue;
      if ("use-character-maps".equals (aParam.getName ().localName ()))
      {
        for (int j = 0; j < aParam.getChildCount (); ++j)
        {
          final IXdmNode aMap = aParam.getChildAtIndex (j);
          final IXdmNode aChar = aMap.isElement () ? aMap.getAttribute ("", "character") : null;
          final IXdmNode aString = aMap.isElement () ? aMap.getAttribute ("", "map-string") : null;
          // An invalid parameter document is a serialization error (XSLT 3.0
          // section 26.1, Serialization 3.1 section 3.1)
          if (aChar != null && aChar.getValue ().codePointCount (0, aChar.getValue ().length ()) != 1)
            throw XsltException.at (aEl, "SEPM0017", "output:character-map/@character must be a single character");
          if (aChar != null && aString != null)
            aDef.addInlineCharacterMapping (aChar.getValue ().codePointAt (0), aString.getValue ());
        }
        continue;
      }
      final IXdmNode aValue = aParam.getAttribute ("", "value");
      if (aValue != null)
        aAttrs.put (aParam.getName ().localName (), aValue.getValue ());
    }
  }

  /**
   * Merge an xsl:output declaration into its output definition (XSLT 3.0
   * section 26): the named one (package qualified) or, for an unnamed
   * declaration of the principal package, the principal output definition. An
   * unnamed declaration in a used package is ignored. The parameters from a
   * parameter document take precedence over the attributes (XSLT 3.0 section
   * 26.1); the merge by import precedence is done by
   * {@link OutputDefinition#merge(XslElement, int, ICommonsMap)}; its
   * conflicts are checked once all declarations are compiled.
   *
   * @param aEl
   *        The xsl:output element.
   */
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
    for (final String sAttr : OutputDefinition.ATTRIBUTES)
    {
      final String v = aEl.getAttr (sAttr);
      if (v != null)
        aAttrs.put (sAttr, v);
    }
    _readParameterDocument (aEl, aAttrs, aDef);
    aDef.merge (aEl, aEl.getPrecedence (), aAttrs);
    m_aMergedOutputs.add (aDef);
  }

  /**
   * Add the whitespace stripping rules of an xsl:strip-space or
   * xsl:preserve-space declaration (XSLT 3.0 section 4.4.2), one per name test
   * in the elements attribute: <code>*</code>, <code>*:local</code>,
   * <code>prefix:*</code>, <code>Q{uri}*</code> or an EQName. An unprefixed
   * name is put into the namespace of an inherited xpath-default-namespace
   * (section 5.1.2). Each rule carries the declaration's import precedence.
   *
   * @param aEl
   *        The declaration.
   * @param bStrip
   *        <code>true</code> for xsl:strip-space.
   * @throws XsltException
   *         XTSE0010 for an invalid <code>*:local</code> test; XTSE0280 for an
   *         unbound prefix in <code>prefix:*</code>; XTSE0270 if the same name
   *         test is already given with the opposite strip setting at the same
   *         import precedence
   */
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

  /**
   * Compile an xsl:attribute-set declaration (XSLT 3.0 section 10.2) and add
   * it as one more declaration of the attribute set with its (possibly
   * package-local) name. The names in use-attribute-sets are mapped for
   * xsl:original and package-local sets and registered for the checks at the
   * end. An overriding declaration first moves all previous declarations of
   * the name to a fresh xsl:original name (section 3.5.3.4), so that it
   * replaces them instead of being merged with them.
   *
   * @param aEl
   *        The xsl:attribute-set element.
   */
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

  /**
   * Apply an xsl:global-context-item declaration (XSLT 3.0 section 3.5.6). In
   * a library package it is ignored unless it says
   * <code>use="required"</code>. In the principal package it sets the
   * stylesheet's global context item; all such declarations must agree in use
   * and in the normalised required type.
   *
   * @param aEl
   *        The xsl:global-context-item element.
   * @throws XsltException
   *         XTTE0590 for <code>use="required"</code> in a library package;
   *         XTSE3087 for a second declaration in one module or declarations
   *         that differ
   */
  private void _compileGlobalContextItem (@NonNull final XslElement aEl)
  {
    final String sAs = aEl.getAttr ("as");
    final String sUse = aEl.getAttr ("use") == null ? "optional" : aEl.getAttr ("use").trim ();
    if (!m_aPackages.isInPrincipal (aEl))
    {
      // A declaration in a library package is ignored, unless it requires a
      // context item, which a library package cannot be given
      if ("required".equals (sUse))
        throw XsltException.at (aEl, "XTTE0590", "A library package declares xsl:global-context-item use='required'");
      return;
    }
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

  /**
   * Compile one top-level element by its kind. A non-XSLT element is the body
   * of a simplified stylesheet if it is the root of a simplified module, else
   * a user-defined data element that is ignored (XSLT 3.0 section 3.7.3).
   * Static variables and parameters are compiled only if a static value was
   * computed for them. Declarations already handled in the preparation
   * (decimal-format, namespace-alias, mode) or by the loader (include,
   * import) are skipped; xsl:import-schema was compiled before everything else
   * by a schema-aware processor. xsl:expose of the principal package is kept
   * for {@link ExposeChecker}. Other names are given to a registered
   * {@link IDeclarationCompiler}.
   *
   * @param aEl
   *        The top-level element.
   * @throws XsltException
   *         XTSE0130 for a user-defined data element in no namespace;
   *         XTSE0010 for xsl:expose outside xsl:package or an unknown XSLT
   *         declaration outside forwards compatible mode; XTSE1650 for
   *         xsl:import-schema without schema awareness; and the errors of the
   *         declaration compiled
   */
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

  /**
   * Check the syntax of a package version (XSLT 3.0 section 3.5.1): one or
   * more dot-separated non-empty runs of ASCII digits, optionally followed by
   * hyphen-separated NCNames.
   *
   * @param s
   *        The package-version value, not trimmed.
   * @return <code>true</code> if it is a valid package version.
   */
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

  /**
   * The check for <code>declared-modes="yes"</code> (XSLT 3.0 section
   * 3.5.4.1): every mode named or implied (the default mode) by a template
   * rule or an xsl:apply-templates in the subtree must have an xsl:mode
   * declaration. <code>#all</code> and <code>#current</code> name no
   * particular mode. Recurses over the element children, so the depth follows
   * the nesting of the stylesheet; it is limited to
   * {@link StylesheetLoader#MAX_DEPTH}.
   *
   * @param aEl
   *        The root of the subtree to check.
   * @param nDepth
   *        The nesting depth of the element below the module root.
   * @throws XsltException
   *         XTSE3085 for a mode without declaration; XPDY0130 for a tree
   *         nested too deeply
   */
  private void _checkDeclaredModesRecursive (@NonNull final XslElement aEl, final int nDepth)
  {
    if (nDepth >= StylesheetLoader.MAX_DEPTH)
      throw XsltException.at (aEl, "XPDY0130", "The stylesheet is nested more than " + StylesheetLoader.MAX_DEPTH + " levels deep");
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
      _checkDeclaredModesRecursive (aChild, nDepth + 1);
  }

  /**
   * Checks that apply only when the root of a package is an xsl:package: the
   * syntax of package-version (XTSE0020), the boolean declared-modes
   * (XTSE0020) and, unless declared-modes is false, that all modes used are
   * declared (XTSE3085) in all modules of the package.
   */
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
          _checkDeclaredModesRecursive (aModule.getRoot (), 0);
  }

  /**
   * Distribute the template rules over the modes (XSLT 3.0 section 6.4). The
   * unnamed mode and the default mode always exist. A template with an
   * explicit priority yields one rule; without one, each alternative of a
   * union pattern becomes its own rule with its default priority (section
   * 6.5). Every rule gets the next declaration order number. A template for
   * <code>#all</code> goes into every mode known at this point, otherwise into
   * its listed modes. Finally the rules of every mode are sorted.
   */
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

  /**
   * Depth first search for an attribute set that uses itself, directly or
   * indirectly, through use-attribute-sets (XTSE0720, XSLT 3.0 section 10.2).
   * Unknown names are ignored here (XTSE0710 is checked separately). The
   * recursion depth is the number of distinct attribute sets on one use
   * chain, limited to {@link StylesheetLoader#MAX_DEPTH}.
   *
   * @param aName
   *        The attribute set to visit.
   * @param aStack
   *        The names on the current path.
   * @param aDone
   *        The names already found free of cycles.
   * @throws XsltException
   *         XTSE0720 at the first declaration of the set closing a cycle;
   *         XPDY0130 for a use chain longer than the limit
   */
  private void _checkAttributeSetCycleRecursive (@NonNull final XdmQName aName,
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
    if (aStack.size () >= StylesheetLoader.MAX_DEPTH)
      throw XsltException.at (aSet.decls ().getFirst ().element (),
                              "XPDY0130",
                              "The attribute sets use each other more than " + StylesheetLoader.MAX_DEPTH + " levels deep");
    aStack.add (aName);
    for (final AttributeSet.Decl aDecl : aSet.decls ())
      for (final XdmQName aUse : aDecl.uses ())
        _checkAttributeSetCycleRecursive (aUse, aStack, aDone);
    aStack.removeLast ();
    aDone.add (aName);
  }

  /**
   * The checks across declarations once all are compiled. Each recorded
   * xsl:call-template must find a named template not hidden from its package
   * (XTSE0650), pass only non-tunnel parameters the template declares as
   * non-tunnel (XTSE0680, unless the call is processed with XSLT 1.0
   * behaviour) and supply all its required non-tunnel parameters
   * (XTSE0690). Each use-attribute-sets reference must find a non-hidden
   * attribute set (XTSE0710); a streamable attribute set may only use
   * attribute sets all of whose declarations are streamable (XTSE0730). Then
   * the declarations of every attribute set are sorted by import precedence
   * and checked for cycles (XTSE0720).
   *
   * @throws XsltException
   *         for the first violation found
   */
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
        // Not an error for an xsl:call-template processed with XSLT 1.0
        // behaviour (XSLT 3.0 section 10.1.2)
        if (!bFound && !isXslt10Behaviour (aRef.element ()))
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
    // A streamable attribute set may only use streamable attribute sets
    // (XTSE0730)
    for (final AttributeSetRef aRef : m_aAttributeSetRefs)
      if (aRef.element ().isXsl ("attribute-set") && XsltNames.isXsltTrue (aRef.element ().getAttr ("streamable")))
        for (final AttributeSet.Decl aDecl : m_aStylesheet.getAttributeSet (aRef.name ()).decls ())
          if (!XsltNames.isXsltTrue (aDecl.element ().getAttr ("streamable")))
            throw XsltException.at (aRef.element (),
                                    "XTSE0730",
                                    "The streamable attribute set uses the attribute set " +
                                                aRef.name ().getEQName () +
                                                ", which is not declared streamable");
    // Circular attribute sets (XTSE0720)
    for (final AttributeSet aSet : m_aStylesheet.attributeSets ().values ())
    {
      aSet.decls ().sort ( (a, b) -> Integer.compare (a.element ().getPrecedence (), b.element ().getPrecedence ()));
      final ICommonsList <XdmQName> aStack = new CommonsArrayList <> ();
      final ICommonsSet <XdmQName> aDone = new CommonsHashSet <> ();
      _checkAttributeSetCycleRecursive (aSet.getName (), aStack, aDone);
    }
  }

  /**
   * @param aModules
   *        All modules of the stylesheet.
   * @return <code>true</code> if any module has an xsl:result-document, which
   *         may write the principal result with another output definition.
   */
  private static boolean _hasResultDocument (@NonNull final ICommonsList <XslModule> aModules)
  {
    for (final XslModule aModule : aModules)
    {
      final XslElement aRoot = aModule.getRoot ();
      if (aRoot == null)
        continue;
      final ArrayDeque <XslElement> aStack = new ArrayDeque <> ();
      aStack.push (aRoot);
      while (!aStack.isEmpty ())
      {
        final XslElement aEl = aStack.pop ();
        if (aEl.isXsl ("result-document"))
          return true;
        for (final XslElement aChild : aEl.getElementChildren ())
          aStack.push (aChild);
      }
    }
    return false;
  }

  /**
   * The whole compilation. In order:
   * <ol>
   * <li>Load the principal module and all included, imported and used
   * modules ({@link StylesheetLoader}), arrange the declarations by import
   * precedence (lowest first) and validate each module tree
   * ({@link ElementValidator}); the input-type-annotations values of all
   * modules must agree (XTSE0265). Analyse the package model.</li>
   * <li>Create the stylesheet with the default function library plus the
   * extension functions (and the streaming claim functions if claimed), the
   * static values from the loader and the default mode.</li>
   * <li>Preparation, so that expressions can refer to everything: in a
   * schema-aware compilation the imported schemas first, then namespace
   * aliases, global names, decimal formats (per package with several
   * packages), stylesheet functions and modes (XTSE0545 for an unresolved
   * conflict of mode attributes), then the per-package function libraries and
   * package-local attribute sets.</li>
   * <li>Compile every declaration, build the modes, the per-package views of
   * globals and run the package checks; for a principal xsl:package also the
   * abstract component checks, xsl:expose and the modes made private.</li>
   * <li>The checks across declarations, the registered
   * {@link ICompileFinisher}s, and the streamability analysis if enabled (its
   * verdicts are kept only with the streaming claim).</li>
   * <li>Record whether any module has an xsl:result-document, and the host
   * scope of the principal stylesheet element for
   * {@link com.helger.phoxtrot.xslt.XsltExecutable#createStaticContext}.</li>
   * </ol>
   *
   * @param aSource
   *        Document or element node of the principal module.
   * @return The compiled stylesheet.
   * @throws XsltException
   *         XTSE0010 for a document without element, and any static error
   *         found on the way
   */
  @NonNull
  private CompiledStylesheet _compile (@NonNull final IXdmNode aSource)
  {
    IXdmNode aRootSrc = aSource;
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

    // The XSLT and stylesheet functions go into a child of the shared default library
    final FunctionLibrary aLib = FunctionLibrary.getDefault ().createChild ();
    m_aRegistry.contributeFunctions (aLib);
    XsltFunctions.registerClaims (aLib, m_aOptions.isStreamingClaim (), m_aOptions.isBackwardsCompatibleClaim ());
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
    m_aDuplicateAliases.check ("XTSE0810");
    m_aDuplicateGlobals.check ("XTSE0630");
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
    m_aDuplicateFunctions.check ("XTSE0770");
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
    m_aDuplicateTemplates.check ("XTSE0660");
    for (final OutputDefinition aDef : m_aMergedOutputs)
      aDef.checkConflicts ();

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
    // XTSE3430/XTSE3155: what the stylesheet declares streamable must be
    // guaranteed streamable (XSLT 3.0 section 19)
    if (m_aStreamabilityIndex != null)
    {
      final StreamVerdicts aVerdicts = XsltStreamability.check (this,
                                                                m_aStreamabilityIndex,
                                                                m_aOptions.isEnforceStreamability ());
      // Only a processor claiming streaming executes streamed
      if (m_aOptions.isStreamingClaim ())
        m_aStylesheet.setStreamVerdicts (aVerdicts);
    }
    // Decides per run whether the principal result can be drained
    m_aStylesheet.setHasResultDocument (_hasResultDocument (m_aStylesheet.modules ()));
    // For host expressions (XsltExecutable.createStaticContext): the scope of
    // the principal stylesheet element
    final XslElement aRoot = aPrincipal.getRoot ();
    if (aRoot != null)
    {
      final ICommonsOrderedSet <XdmQName> aVariables = aGlobal.getVariablesInScope ();
      if (m_aPackages.isMultiPackage ())
        aVariables.removeIf (x -> isGlobalVariableHidden (aRoot, x));
      m_aStylesheet.setHostScope (new CompiledStylesheet.HostScope (aRoot,
                                                                    getFunctionLibraryFor (aRoot),
                                                                    new CommonsArrayList <> (aVariables),
                                                                    getDefaultDecimalFormat (aRoot),
                                                                    new CommonsHashMap <> (getDecimalFormats (aRoot))));
    }
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
   * @throws IllegalStateException
   *         if a schema-aware compilation is requested but Apache Xerces-J is
   *         not on the class path
   */
  @NonNull
  public static CompiledStylesheet compile (@NonNull final IXdmNode aSource, @NonNull final XsltCompileOptions aOptions)
  {
    if (aOptions.isSchemaAware () && !XdmSchemaSupport.isAvailable ())
      throw new IllegalStateException ("A schema-aware stylesheet needs Apache Xerces-J (xerces:xercesImpl) on the class path");
    return new StylesheetCompiler (aOptions)._compile (aSource);
  }
}
