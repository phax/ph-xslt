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
package com.helger.xslt.ext.evaluate;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.fn.FnUri;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.compile.Avt;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.StaticContexts;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.instr.CompiledInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.UserFunction;
import com.helger.xslt.model.VariableDef;
import com.helger.xslt.runtime.ParamValue;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * xsl:evaluate (XSLT 3.0 section 10.4): an XPath expression constructed at run
 * time is compiled and evaluated. Its static context has the namespaces of the
 * instruction (or of the namespace-context node), only the variables supplied
 * by xsl:with-param and with-params, and the functions of the stylesheet
 * without the XSLT functions that depend on the context of the stylesheet and
 * without non-public stylesheet functions. Every static error of the target
 * expression is the dynamic error XTDE3160.
 *
 * @author Philip Helger
 */
public final class EvaluateExtension implements IXsltExtension
{
  /** Extension data key: the function library of dynamic expressions. */
  private static final Object KEY_LIBRARY = new Object ();

  /**
   * XSLT functions that are not in the static context of a dynamic expression
   * (XSLT 3.0 section 10.4.1).
   */
  private static final ICommonsSet <String> EXCLUDED_FUNCTIONS = new CommonsHashSet <> ("current",
                                                                                        "current-group",
                                                                                        "current-grouping-key",
                                                                                        "current-merge-group",
                                                                                        "current-merge-key",
                                                                                        "current-output-uri",
                                                                                        "regex-group",
                                                                                        "system-property",
                                                                                        "element-available",
                                                                                        "function-available",
                                                                                        "type-available",
                                                                                        "unparsed-entity-uri",
                                                                                        "unparsed-entity-public-id");

  /**
   * Check whether a stylesheet function may be called from a dynamic
   * expression (XSLT 3.0 section 10.4.1): only functions with visibility
   * public or final are in its static context.
   *
   * @param aFunc
   *        The stylesheet function.
   * @return <code>true</code> if the visibility is public or final.
   */
  private static boolean _isPublic (@NonNull final UserFunction aFunc)
  {
    final String s = aFunc.getVisibility ();
    return "public".equals (s) || "final".equals (s);
  }

  /**
   * Build the function library of dynamic expressions from the library of
   * the stylesheet: start with a child of the shared default XPath library and add every
   * function of the stylesheet library that is not already there by name and
   * arity, except the context-dependent XSLT functions of
   * {@link #EXCLUDED_FUNCTIONS} (in the fn namespace) and stylesheet
   * functions that are not public or final.
   *
   * @param aSheetLib
   *        The function library of the stylesheet.
   * @return A new child library of the shared default library. Never <code>null</code>.
   */
  @NonNull
  private static FunctionLibrary _createLibrary (@NonNull final FunctionLibrary aSheetLib)
  {
    final FunctionLibrary ret = FunctionLibrary.getDefault ().createChild ();
    for (final FunctionLibrary.FunctionDef aDef : aSheetLib.getAllFunctions ())
    {
      final XdmQName aName = aDef.getName ();
      if (ret.getFunction (aName, aDef.getArity ()) != null)
        continue;
      if (XdmConstants.NS_FN.equals (aName.namespaceUri ()) && EXCLUDED_FUNCTIONS.contains (aName.localName ()))
        continue;
      if (aDef.getImpl () instanceof final UserFunction aFunc && !_isPublic (aFunc))
        continue;
      ret.registerParsed (aName, aDef.getParamTypes (), aDef.getReturnType (), aDef.getImpl (), aDef.isFocusDependent ());
    }
    return ret;
  }

  /**
   * Create the static context of the target expression (XSLT 3.0 section
   * 10.4.1). It starts from the static context of the xsl:evaluate element
   * with the reduced function library built at compile time (or the full
   * stylesheet library if that is missing), the given variables, and the
   * default decimal formats. If a namespace-context node is given, all
   * namespaces except <code>xml</code> are replaced by the in-scope
   * namespaces of that node, and its default namespace (or none) becomes the
   * default element namespace. A non-<code>null</code> base URI replaces the
   * static base URI.
   *
   * @param aCtx
   *        Run-time context, for the stylesheet.
   * @param aEl
   *        The xsl:evaluate element.
   * @param aNamespaceNode
   *        The node selected by namespace-context, or <code>null</code>.
   * @param sBaseUri
   *        The resolved base-uri, or <code>null</code> to keep the static
   *        base URI of the element.
   * @param aVariables
   *        The names of the variables in scope of the target expression.
   * @return A new static context. Never <code>null</code>.
   */
  @NonNull
  private static StaticContext _createStaticContext (@NonNull final XsltContext aCtx,
                                                     @NonNull final XslElement aEl,
                                                     @Nullable final IXdmNode aNamespaceNode,
                                                     @Nullable final String sBaseUri,
                                                     @NonNull final Iterable <XdmQName> aVariables)
  {
    final CompiledStylesheet aSheet = aCtx.getEngine ().getStylesheet ();
    final FunctionLibrary aLib = (FunctionLibrary) aSheet.getExtensionData (KEY_LIBRARY);
    final StaticContext ret = StaticContexts.create (aEl,
                                                     aLib != null ? aLib : aSheet.getFunctionLibrary (),
                                                     aVariables,
                                                     null,
                                                     null);
    if (aNamespaceNode != null)
    {
      ret.removeAllNamespacesExceptXml ();
      String sDefault = "";
      for (final Map.Entry <String, String> aEntry : aNamespaceNode.getInScopeNamespaces ().entrySet ())
        if (aEntry.getKey ().isEmpty ())
          sDefault = aEntry.getValue ();
        else
          if (!"xml".equals (aEntry.getKey ()))
            ret.declareNamespace (aEntry.getKey (), aEntry.getValue ());
      ret.setDefaultElementNamespace (sDefault);
    }
    if (sBaseUri != null)
      ret.setBaseUri (sBaseUri);
    return ret;
  }

  /**
   * Compile xsl:evaluate (XSLT 3.0 section 10.4) and mark the stylesheet as
   * needing the dynamic function library (built by the compile finisher in
   * {@link #register}). At run time:
   * <ol>
   * <li>the xpath expression is evaluated to a string (items joined with a
   * space);</li>
   * <li>schema-aware must be yes/no (or the boolean synonyms), otherwise
   * XTDE0030;</li>
   * <li>the variables are the xsl:with-param values, overridden by the
   * entries of the with-params map; a with-params value that is not a single
   * map, or a key that is not an <code>xs:QName</code>, raises XTTE3165;</li>
   * <li>namespace-context must select exactly one node, otherwise
   * XTTE3170;</li>
   * <li>base-uri is resolved against the static base URI of the
   * instruction;</li>
   * <li>the target expression is parsed with the static context of
   * {@link #_createStaticContext}, including the schema components of the
   * stylesheet if schema-aware is yes; any parse or static error becomes
   * XTDE3160;</li>
   * <li>context-item selecting more than one item raises XTTE3210; an empty
   * result or no attribute leaves the focus absent;</li>
   * <li>the expression is evaluated with only that focus, no current item
   * and only the supplied variables;
   * the result is converted to the as type by the function conversion rules,
   * a failure being reported as XPTY0004, and appended to the output.</li>
   * </ol>
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The xsl:evaluate element.
   * @return The compiled instruction. Never <code>null</code>.
   */
  @NonNull
  private static IInstruction _compileEvaluate (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aXPath = aCtx.compileRequiredExpression (aEl, "xpath");
    final XPathExpr aContextItem = aCtx.compileOptionalExpression (aEl, "context-item");
    final XPathExpr aNamespaceContext = aCtx.compileOptionalExpression (aEl, "namespace-context");
    final XPathExpr aWithParams = aCtx.compileOptionalExpression (aEl, "with-params");
    final Avt aBaseUri = aCtx.compileOptionalAvt (aEl, "base-uri");
    final Avt aSchemaAware = aCtx.compileOptionalAvt (aEl, "schema-aware");
    final String sAs = aEl.getAttr ("as");
    final SeqType aAs = sAs == null ? null : aCtx.compileSequenceType (aEl, sAs);
    final ICommonsList <VariableDef> aParams = aCtx.compileWithParams (aEl);
    aCtx.getStylesheet ().putExtensionData (KEY_LIBRARY, Boolean.TRUE);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final String sExpr = aXPath.evaluateString (aRtCtx, " ");
      boolean bSchemaAware = false;
      if (aSchemaAware != null)
      {
        final String s = aSchemaAware.evaluate (aRtCtx);
        if (!XsltNames.isXsltBooleanLexical (s))
          throw new XPathException ("XTDE0030", "schema-aware='" + s + "' is not yes or no");
        bSchemaAware = XsltNames.isXsltTrue (s);
      }
      // The variables of the target expression; with-params wins
      final ICommonsOrderedMap <XdmQName, XdmSequence> aVars = new CommonsLinkedHashMap <> ();
      for (final ParamValue aParam : ParamValue.evaluate (aParams, aRtCtx))
        aVars.put (aParam.name (), aParam.value ());
      if (aWithParams != null)
      {
        final XdmSequence aMapSeq = aWithParams.evaluate (aRtCtx);
        if (aMapSeq.size () != 1 || !(aMapSeq.get (0) instanceof final XdmMap aMap))
          throw new XPathException ("XTTE3165", "The with-params attribute of xsl:evaluate must be a map");
        for (final XdmMap.Entry aEntry : aMap.entries ())
        {
          final XdmAtomic aKey = aEntry.key ();
          if (aKey.getType () != EAtomicType.QNAME)
            throw new XPathException ("XTTE3165", "The keys of the with-params map of xsl:evaluate must be xs:QName values");
          aVars.put ((XdmQName) aKey.getValue (), aEntry.value ());
        }
      }
      // The namespace context
      IXdmNode aNamespaceNode = null;
      if (aNamespaceContext != null)
      {
        final XdmSequence aNS = aNamespaceContext.evaluate (aRtCtx);
        if (aNS.size () != 1 || !(aNS.get (0) instanceof final IXdmNode aNode))
          throw new XPathException ("XTTE3170", "The namespace-context attribute of xsl:evaluate must select a single node");
        aNamespaceNode = aNode;
      }
      String sBaseUri = null;
      if (aBaseUri != null)
      {
        final String sStatic = aEl.getStaticBaseUri ();
        final String s = aBaseUri.evaluate (aRtCtx);
        sBaseUri = sStatic == null ? s : FnUri.resolveUri (s, sStatic);
      }
      // Compile the target expression
      final StaticContext aSC = _createStaticContext (aRtCtx, aEl, aNamespaceNode, sBaseUri, aVars.keySet ());
      // schema-aware="yes": the schema components of the stylesheet are in
      // scope (XSLT 3.0 section 10.4.1)
      if (bSchemaAware)
        aSC.setSchemaSet (aRtCtx.getEngine ().getStylesheet ().getSchemaSet ());
      final IExpr aExpr;
      try
      {
        aExpr = XPathParser.parse (sExpr, aSC);
      }
      catch (final XPathException ex)
      {
        throw new XPathException ("XTDE3160",
                                  "The xsl:evaluate expression '" + sExpr + "' has a static error: " + ex.getMessage (),
                                  ex);
      }
      // The focus
      IXdmItem aFocus = null;
      if (aContextItem != null)
      {
        final XdmSequence aItems = aContextItem.evaluate (aRtCtx);
        if (aItems.size () > 1)
          throw new XPathException ("XTTE3210", "The context-item attribute of xsl:evaluate selects " + aItems.size () + " items");
        if (!aItems.isEmpty ())
          aFocus = aItems.get (0);
      }
      DynamicContext aDyn = aRtCtx.getDynamicContext ()
                                  .withoutFocusAndVariables ()
                                  .withFocus (aFocus, aFocus == null ? 0 : 1, aFocus == null ? 0 : 1)
                                  .withCurrentItem (null)
                                  .withStaticContext (aSC);
      for (final Map.Entry <XdmQName, XdmSequence> aEntry : aVars.entrySet ())
        aDyn = aDyn.withVariable (aEntry.getKey (), aEntry.getValue ());
      XdmSequence aResult = XPathEvaluator.evaluate (aExpr, aDyn);
      if (aAs != null)
        aResult = SequenceTypeMatcher.convert (aResult, aAs, aDyn, "XPTY0004");
      aRtOut.appendAll (aResult);
    });
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("evaluate", EvaluateExtension::_compileEvaluate);
    aRegistry.registerSystemPropertyYes ("supports-dynamic-evaluation");
    aRegistry.registerCompileFinisher (aCtx -> {
      final CompiledStylesheet aSheet = aCtx.getStylesheet ();
      if (aSheet.getExtensionData (KEY_LIBRARY) != null)
        aSheet.putExtensionData (KEY_LIBRARY, _createLibrary (aSheet.getFunctionLibrary ()));
    });
  }
}
