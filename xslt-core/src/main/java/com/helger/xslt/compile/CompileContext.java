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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.pattern.XsltPattern;
import com.helger.xpath.pattern.XsltPatternParser;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.instr.CoreInstructions;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.instr.SortKeyDef;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.VariableDef;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XsltNames;

/**
 * The context in which stylesheet elements are compiled: the stylesheet being
 * built and the local variables in scope. Immutable; a construct that brings
 * variables into scope for its content compiles the content with
 * {@link #withVariable(XdmQName)}. This is the main API for instruction
 * compilers.
 *
 * @author Philip Helger
 */
@Immutable
public final class CompileContext
{
  private record Scope (@NonNull XdmQName name, @Nullable Scope next)
  {}

  private final StylesheetCompiler m_aCompiler;
  private final Scope m_aScope;
  private final XdmQName m_aHiddenGlobal;

  CompileContext (@NonNull final StylesheetCompiler aCompiler, @Nullable final Scope aScope)
  {
    this (aCompiler, aScope, null);
  }

  private CompileContext (@NonNull final StylesheetCompiler aCompiler,
                          @Nullable final Scope aScope,
                          @Nullable final XdmQName aHiddenGlobal)
  {
    m_aCompiler = aCompiler;
    m_aScope = aScope;
    m_aHiddenGlobal = aHiddenGlobal;
  }

  /**
   * @param aName
   *        A global variable.
   * @return A context in which that global variable is not in scope (a global
   *         variable is not in scope in its own definition).
   */
  @NonNull
  public CompileContext withoutGlobalVariable (@NonNull final XdmQName aName)
  {
    return new CompileContext (m_aCompiler, m_aScope, aName);
  }

  /** @return The compiler (global stylesheet information). */
  @NonNull
  public StylesheetCompiler getCompiler ()
  {
    return m_aCompiler;
  }

  /** @return The stylesheet being built. */
  @NonNull
  public CompiledStylesheet getStylesheet ()
  {
    return m_aCompiler.getStylesheet ();
  }

  @NonNull
  public FunctionLibrary getFunctionLibrary ()
  {
    return m_aCompiler.getStylesheet ().getFunctionLibrary ();
  }

  /**
   * @param aName
   *        Variable name.
   * @return A context in which the variable is in scope as well.
   */
  @NonNull
  public CompileContext withVariable (@NonNull final XdmQName aName)
  {
    return new CompileContext (m_aCompiler, new Scope (aName, m_aScope), m_aHiddenGlobal);
  }

  /** @return A context with only the global variables in scope. */
  @NonNull
  public CompileContext withGlobalScopeOnly ()
  {
    return m_aScope == null ? this : new CompileContext (m_aCompiler, null, m_aHiddenGlobal);
  }

  /** @return The names of all variables in scope (globals and locals). */
  @NonNull
  public ICommonsOrderedSet <XdmQName> getVariablesInScope ()
  {
    final ICommonsOrderedSet <XdmQName> ret = new CommonsLinkedHashSet <> (m_aCompiler.getGlobalVariableNames ());
    if (m_aHiddenGlobal != null)
      ret.remove (m_aHiddenGlobal);
    for (Scope aCur = m_aScope; aCur != null; aCur = aCur.next ())
      ret.add (aCur.name ());
    return ret;
  }

  /**
   * @param aEl
   *        Element.
   * @return A new XPath static context for expressions on that element.
   */
  @NonNull
  public StaticContext createStaticContext (@NonNull final XslElement aEl)
  {
    final ICommonsOrderedSet <XdmQName> aVariables = getVariablesInScope ();
    if (m_aCompiler.getPackages () != null && m_aCompiler.getPackages ().isMultiPackage ())
      aVariables.removeIf (x -> m_aCompiler.isGlobalVariableHidden (aEl, x));
    return StaticContexts.create (aEl,
                                  m_aCompiler.getFunctionLibraryFor (aEl),
                                  aVariables,
                                  m_aCompiler.getDefaultDecimalFormat (aEl),
                                  m_aCompiler.getDecimalFormats (aEl));
  }

  /**
   * Compile an XPath expression written on an element.
   *
   * @param aEl
   *        The element (namespaces, base URI, ...).
   * @param sExpr
   *        The expression text.
   * @return The compiled expression.
   */
  @NonNull
  public XPathExpr compileExpression (@NonNull final XslElement aEl, @NonNull final String sExpr)
  {
    try
    {
      final StaticContext aSC = createStaticContext (aEl);
      final IExpr aExpr = XPathParser.parse (sExpr, aSC);
      return new XPathExpr (sExpr, aExpr, aSC, aEl);
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aEl);
    }
  }

  /**
   * @param aEl
   *        Element.
   * @param sAttr
   *        Attribute name (no namespace).
   * @return The compiled expression of a required attribute.
   * @throws XsltException
   *         XTSE0010 if the attribute is missing
   */
  @NonNull
  public XPathExpr compileRequiredExpression (@NonNull final XslElement aEl, @NonNull final String sAttr)
  {
    final String s = aEl.getAttr (sAttr);
    if (s == null)
      throw XsltException.at (aEl, "XTSE0010", "xsl:" + aEl.getLocalName () + " requires the attribute " + sAttr);
    return compileExpression (aEl, s);
  }

  /**
   * @param aEl
   *        Element.
   * @param sAttr
   *        Attribute name (no namespace).
   * @return The compiled expression or <code>null</code> if the attribute is
   *         missing.
   */
  @Nullable
  public XPathExpr compileOptionalExpression (@NonNull final XslElement aEl, @NonNull final String sAttr)
  {
    final String s = aEl.getAttr (sAttr);
    return s == null ? null : compileExpression (aEl, s);
  }

  @NonNull
  private Avt _compileAvt (@NonNull final XslElement aEl, @NonNull final String sText, final boolean bTvt)
  {
    final ICommonsList <Object> aParts;
    try
    {
      aParts = AvtParser.split (sText);
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aEl);
    }
    final ICommonsList <Object> aCompiled = new CommonsArrayList <> ();
    for (final Object aPart : aParts)
      if (aPart instanceof final AvtParser.ExprPart aExpr)
        aCompiled.add (compileExpression (aEl, aExpr.text ()));
      else
        aCompiled.add (aPart);
    return new Avt (aCompiled, bTvt);
  }

  /**
   * Compile an attribute value template (or text value template).
   *
   * @param aEl
   *        The element for the static context.
   * @param sText
   *        The template text.
   * @return The compiled template.
   */
  @NonNull
  public Avt compileAvt (@NonNull final XslElement aEl, @NonNull final String sText)
  {
    return _compileAvt (aEl, sText, false);
  }

  /**
   * Compile a text value template (XSLT 3.0 section 5.6.2).
   *
   * @param aEl
   *        The element for the static context.
   * @param sText
   *        The text.
   * @return The compiled template.
   */
  @NonNull
  public Avt compileTextValueTemplate (@NonNull final XslElement aEl, @NonNull final String sText)
  {
    return _compileAvt (aEl, sText, true);
  }

  /**
   * @param aEl
   *        Element.
   * @param sAttr
   *        Attribute name (no namespace).
   * @return The compiled AVT or <code>null</code> if the attribute is
   *         missing.
   */
  @Nullable
  public Avt compileOptionalAvt (@NonNull final XslElement aEl, @NonNull final String sAttr)
  {
    final String s = aEl.getAttr (sAttr);
    return s == null ? null : compileAvt (aEl, s);
  }

  /**
   * Compile a pattern; only global variables are in scope.
   *
   * @param aEl
   *        Element.
   * @param sPattern
   *        Pattern text.
   * @return The compiled pattern.
   */
  @NonNull
  public CompiledPattern compilePattern (@NonNull final XslElement aEl, @NonNull final String sPattern)
  {
    try
    {
      final StaticContext aSC = withGlobalScopeOnly ().createStaticContext (aEl);
      final XsltPattern aPattern = XsltPatternParser.parse (sPattern, aSC);
      return new CompiledPattern (aPattern, aSC);
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aEl);
    }
  }

  /**
   * @param aEl
   *        Element.
   * @param sType
   *        A SequenceType.
   * @return The parsed type.
   */
  @NonNull
  public SeqType compileSequenceType (@NonNull final XslElement aEl, @NonNull final String sType)
  {
    try
    {
      return XPathParser.parseSequenceType (sType, createStaticContext (aEl));
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aEl);
    }
  }

  /**
   * Resolve a QName-valued attribute.
   *
   * @param aEl
   *        Element.
   * @param sAttr
   *        Attribute name.
   * @param bUseDefaultNamespace
   *        <code>true</code> if an unprefixed name is in the default
   *        namespace.
   * @return The name or <code>null</code> if the attribute is missing.
   * @throws XsltException
   *         XTSE0020 for an invalid name, XTSE0280 for an unbound prefix
   */
  @Nullable
  public XdmQName resolveQNameAttr (@NonNull final XslElement aEl, @NonNull final String sAttr, final boolean bUseDefaultNamespace)
  {
    final String s = aEl.getAttr (sAttr);
    if (s == null)
      return null;
    return resolveQName (aEl, s, bUseDefaultNamespace);
  }

  /**
   * @param aEl
   *        Element.
   * @param sLexical
   *        The name.
   * @param bUseDefaultNamespace
   *        <code>true</code> if an unprefixed name is in the default
   *        namespace.
   * @return The resolved name.
   * @throws XsltException
   *         XTSE0020 for an invalid name, XTSE0280 for an unbound prefix
   */
  @NonNull
  public XdmQName resolveQName (@NonNull final XslElement aEl, @NonNull final String sLexical, final boolean bUseDefaultNamespace)
  {
    final String s = sLexical.trim ();
    if (!XsltNames.isEQName (s))
      throw XsltException.at (aEl, "XTSE0020", "'" + sLexical + "' is not a valid QName");
    final XdmQName ret = aEl.resolveQName (s, bUseDefaultNamespace);
    if (ret == null)
      throw XsltException.at (aEl, "XTSE0280", "The prefix of '" + sLexical + "' is not bound");
    return ret;
  }

  /**
   * @param aNode
   *        A stylesheet node.
   * @return <code>true</code> if text value templates are enabled for it
   *         ([xsl:]expand-text of the nearest ancestor that has one).
   */
  public static boolean isExpandText (@NonNull final XslNode aNode)
  {
    final XslElement aStart = aNode instanceof final XslElement aEl ? aEl : aNode.getParent ();
    if (aStart == null)
      return false;
    final String s = aStart.getInheritedStandardAttr ("expand-text");
    return s != null && XsltNames.isXsltTrue (s);
  }

  /**
   * Compile a sequence constructor.
   *
   * @param aParent
   *        The element containing it (location).
   * @param aChildren
   *        The children forming it.
   * @return The compiled sequence constructor.
   */
  @NonNull
  public SequenceConstructor compileSequenceConstructor (@NonNull final XslElement aParent,
                                                         @NonNull final List <XslNode> aChildren)
  {
    return CoreInstructions.compileSequenceConstructor (this, aParent, aChildren);
  }

  /**
   * Compile the content of an element, leaving out xsl:param, xsl:sort,
   * xsl:with-param and xsl:context-item children.
   *
   * @param aEl
   *        The element.
   * @return The compiled sequence constructor.
   */
  @NonNull
  public SequenceConstructor compileContent (@NonNull final XslElement aEl)
  {
    final ICommonsList <XslNode> aChildren = new CommonsArrayList <> ();
    for (final XslNode aNode : aEl.getChildren ())
    {
      if (aNode instanceof final XslElement aChild && aChild.isXsl ())
      {
        final String sLocal = aChild.getLocalName ();
        if ("param".equals (sLocal) ||
            "sort".equals (sLocal) ||
            "with-param".equals (sLocal) ||
            "context-item".equals (sLocal) && aEl.isXsl ("template"))
          continue;
      }
      aChildren.add (aNode);
    }
    return compileSequenceConstructor (aEl, aChildren);
  }

  /**
   * Compile an instruction element (XSLT instruction, extension instruction or
   * literal result element).
   *
   * @param aEl
   *        The element.
   * @return The instruction or <code>null</code>.
   */
  @Nullable
  public IInstruction compileInstruction (@NonNull final XslElement aEl)
  {
    return CoreInstructions.compileInstruction (this, aEl);
  }

  /**
   * Compile xsl:variable, xsl:param or xsl:with-param.
   *
   * @param aEl
   *        The element.
   * @param eKind
   *        What kind of definition it is.
   * @return The compiled definition.
   */
  @NonNull
  public VariableDef compileVariable (@NonNull final XslElement aEl, final VariableDef.@NonNull EKind eKind)
  {
    return CoreInstructions.compileVariable (this, aEl, eKind);
  }

  /**
   * @param aEl
   *        The instruction (xsl:apply-templates, xsl:call-template, ...).
   * @return Its xsl:with-param children, compiled.
   */
  @NonNull
  public ICommonsList <VariableDef> compileWithParams (@NonNull final XslElement aEl)
  {
    final ICommonsList <VariableDef> ret = new CommonsArrayList <> ();
    for (final XslElement aChild : aEl.getXslChildren ("with-param"))
      ret.add (compileVariable (aChild, VariableDef.EKind.WITH_PARAM));
    return ret;
  }

  /**
   * @param aEl
   *        The instruction (xsl:for-each, xsl:apply-templates,
   *        xsl:perform-sort, xsl:for-each-group, ...).
   * @return Its xsl:sort children, compiled.
   */
  @NonNull
  public ICommonsList <SortKeyDef> compileSortKeys (@NonNull final XslElement aEl)
  {
    final ICommonsList <SortKeyDef> ret = new CommonsArrayList <> ();
    for (final XslElement aChild : aEl.getXslChildren ("sort"))
      ret.add (SortKeyDef.compile (this, aChild));
    return ret;
  }
}
