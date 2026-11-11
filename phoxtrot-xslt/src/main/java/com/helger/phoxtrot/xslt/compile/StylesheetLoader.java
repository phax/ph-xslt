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

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.schema.XdmSchemaSupport;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.IResourceResolver;
import com.helger.phoxtrot.xpath.context.ResourceUri;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.eval.SequenceTypeMatcher;
import com.helger.phoxtrot.xpath.eval.XPathEvaluator;
import com.helger.phoxtrot.xpath.fn.FnUri;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.ops.AtomicComparator;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.parser.XPathParser;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.fn.XsltFunctions;
import com.helger.phoxtrot.xslt.pkg.PackageModel;
import com.helger.phoxtrot.xslt.pkg.PackageRegistry;
import com.helger.phoxtrot.xslt.pkg.PackageUnit;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslModule;
import com.helger.phoxtrot.xslt.tree.XslText;
import com.helger.phoxtrot.xslt.tree.XsltNames;

/**
 * Loads the stylesheet modules and performs static processing (XSLT 3.0
 * sections 3.9 to 3.13): shadow attributes, use-when, static variables and
 * parameters, xsl:include and xsl:import, all in stylesheet tree order. Then
 * the modules are arranged by import precedence.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class StylesheetLoader
{
  /**
   * The top-level declarations of one import precedence: the declarations of a
   * module together with those of the modules it includes, in declaration
   * order. <code>importLo</code> is the index of the lowest level that belongs
   * to the module's import subtree (its own index if it imports nothing).
   */
  public record Level (@NonNull ICommonsList <XslElement> declarations, int importLo)
  {}

  /**
   * The deepest nesting the loader and the checks over the stylesheet tree
   * accept: of elements within a module (counting the elements of the
   * modules loaded from it) and of modules within xsl:include, xsl:import and
   * xsl:use-package. Deeper input is rejected with XPDY0130 (an
   * implementation limit) instead of exhausting the stack.
   */
  public static final int MAX_DEPTH = 1000;

  private final XsltCompileOptions m_aOptions;
  private final FunctionLibrary m_aStaticFunctions;
  private final ICommonsOrderedMap <XdmQName, XdmSequence> m_aStaticValues = new CommonsLinkedHashMap <> ();
  private final ICommonsMap <XslElement, XslModule> m_aLoadedModules = new CommonsHashMap <> ();
  private final ICommonsList <XslModule> m_aAllModules = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aLoadStack = new CommonsArrayList <> ();
  // Import precedence groups: a module and the modules it includes share one;
  // the stack holds the groups of the modules being loaded (import ancestors)
  private final ICommonsList <Object> m_aGroupStack = new CommonsArrayList <> ();
  private final ICommonsMap <XdmQName, Object> m_aStaticGroups = new CommonsHashMap <> ();
  private final PackageModel m_aPackages = new PackageModel ();
  private final ICommonsMap <XslElement, XslModule> m_aUsedPackages = new CommonsHashMap <> ();
  // The packages being loaded, innermost last
  private final ICommonsList <PackageUnit> m_aPackageStack = new CommonsArrayList <> ();
  private PackageRegistry m_aRegistry;
  // "variable" or "param" per static name
  private final ICommonsMap <XdmQName, String> m_aStaticKinds = new CommonsHashMap <> ();
  // The nesting depth of _build, including the elements of loaded modules
  private int m_nBuildDepth;

  /**
   * Create a loader. The function library for static expressions is a child
   * of the shared default library with the XSLT functions allowed in static expressions,
   * and the system-property function of the claims of the options (streaming,
   * backwards compatibility).
   *
   * @param aOptions
   *        The compile options (resolver, static parameter values,
   *        schema-awareness, streaming and backwards compatibility claims).
   */
  public StylesheetLoader (@NonNull final XsltCompileOptions aOptions)
  {
    m_aOptions = aOptions;
    m_aStaticFunctions = FunctionLibrary.getDefault ().createChild ();
    XsltFunctions.registerStatic (m_aStaticFunctions);
    XsltFunctions.registerClaims (m_aStaticFunctions, aOptions.isStreamingClaim (), aOptions.isBackwardsCompatibleClaim ());
  }

  /** @return The static variables and parameters with their values. */
  @NonNull
  public ICommonsOrderedMap <XdmQName, XdmSequence> getStaticValues ()
  {
    return m_aStaticValues;
  }

  /** @return The packages taking part, the principal one first. */
  @NonNull
  public PackageModel getPackages ()
  {
    return m_aPackages;
  }

  /** @return All loaded modules (a module imported twice appears twice). */
  @NonNull
  public ICommonsList <XslModule> getAllModules ()
  {
    return m_aAllModules;
  }

  /**
   * Create the static context for a static expression (XSLT 3.0 section 9.7)
   * on an element: its namespaces and base URI, the static function library,
   * the static variables declared so far and no decimal formats.
   *
   * @param aEl
   *        The element carrying the expression.
   * @return A new static context.
   */
  @NonNull
  private StaticContext _createStaticContext (@NonNull final XslElement aEl)
  {
    final StaticContext ret = StaticContexts.create (aEl, m_aStaticFunctions, m_aStaticValues.keySet (), null, null);
    // Static expressions of a schema-aware stylesheet see the built-in types
    // (the imported schemas are not known yet); this also makes
    // system-property('xsl:is-schema-aware') report "yes"
    if (m_aOptions.isSchemaAware () && XdmSchemaSupport.isAvailable ())
      ret.setSchemaSet (XdmSchemaSupport.getBuiltinSchemaSet ());
    return ret;
  }

  /**
   * @return The resolver of the options, or a new {@link FileResourceResolver}
   *         that loads external entities as the options say.
   */
  @NonNull
  private IResourceResolver _getResolver ()
  {
    final IResourceResolver ret = m_aOptions.getResolver ();
    return ret != null ? ret : new FileResourceResolver (m_aOptions.isLoadExternalEntities ());
  }

  /**
   * Evaluate an expression in the static context of an element (use-when,
   * static variables, shadow attributes).
   */
  @NonNull
  private XdmSequence _evaluateStatic (@NonNull final XslElement aEl, @NonNull final String sExpr)
  {
    try
    {
      final StaticContext aSC = _createStaticContext (aEl);
      final IExpr aExpr = XPathParser.parse (sExpr, aSC);
      DynamicContext aDC = new DynamicContext (aSC);
      aDC.setResourceResolver (_getResolver ());
      // fn:transform in a static expression inherits the options (see XsltTransformHandler)
      aDC.setHost (m_aOptions);
      for (final Map.Entry <XdmQName, XdmSequence> aEntry : m_aStaticValues.entrySet ())
        aDC = aDC.withVariable (aEntry.getKey (), aEntry.getValue ());
      return XPathEvaluator.evaluate (aExpr, aDC);
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aEl);
    }
  }

  /**
   * Evaluate an attribute value template statically, as needed for a shadow
   * attribute (XSLT 3.0 section 3.13.2): each expression part is evaluated
   * with {@link #_evaluateStatic(XslElement, String)}, its result atomized and
   * the string values joined with single spaces; fixed parts are copied.
   *
   * @param aEl
   *        The element carrying the attribute.
   * @param sValue
   *        The attribute value.
   * @return The expanded value.
   * @throws XsltException
   *         for a syntax error in the template or an error evaluating one of
   *         its expressions, located at the element
   */
  @NonNull
  private String _evaluateStaticAvt (@NonNull final XslElement aEl, @NonNull final String sValue)
  {
    final ICommonsList <Object> aParts;
    try
    {
      aParts = AvtParser.split (sValue);
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aEl);
    }
    final StringBuilder aSB = new StringBuilder ();
    for (final Object aPart : aParts)
      if (aPart instanceof final AvtParser.ExprPart aExpr)
      {
        boolean bFirst = true;
        for (final IXdmItem aItem : Atomizer.atomize (_evaluateStatic (aEl, aExpr.text ())))
        {
          if (!bFirst)
            aSB.append (' ');
          aSB.append (Atomizer.getStringValue (aItem));
          bFirst = false;
        }
      }
      else
        aSB.append ((String) aPart);
    return aSB.toString ();
  }

  /**
   * Replace the shadow attributes of an XSLT element (XSLT 3.0 section
   * 3.13.2): each attribute in no namespace whose local name starts with an
   * underscore and has at least one more character is evaluated as a static
   * attribute value template, removed, and its value set on the attribute
   * named without the underscore (overwriting one that is present). Literal
   * result elements are left alone.
   *
   * @param aEl
   *        The element, changed in place.
   */
  private void _expandShadowAttributes (@NonNull final XslElement aEl)
  {
    final boolean bXsl = aEl.isXsl ();
    for (final Map.Entry <XdmQName, String> aEntry : aEl.getAllAttributes ().entrySet ())
    {
      final XdmQName aName = aEntry.getKey ();
      final String sLocal = aName.localName ();
      if (sLocal.length () < 2 || sLocal.charAt (0) != '_')
        continue;
      final XdmQName aTarget;
      // Shadow attributes exist on XSLT elements only; xsl:_name on a literal
      // result element is an error reported by the validator (XTSE0805)
      if (bXsl && aName.namespaceUri ().isEmpty ())
        aTarget = new XdmQName ("", sLocal.substring (1));
      else
        continue;
      final String sValue = _evaluateStaticAvt (aEl, aEntry.getValue ());
      aEl.removeAttribute (aName);
      aEl.setAttribute (aTarget, sValue);
    }
  }

  /**
   * Conditional element inclusion (XSLT 3.0 section 3.13.1): evaluate the
   * [xsl:]use-when attribute of the element statically and take its effective
   * boolean value. An evaluation error of an unknown XSLT element in forwards
   * compatible mode excludes the element instead of being raised.
   *
   * @param aEl
   *        The element.
   * @return <code>true</code> if the element is excluded, <code>false</code>
   *         if it has no use-when attribute or the condition is true.
   * @throws XPathException
   *         the error of evaluating the condition otherwise
   */
  private boolean _isUseWhenFalse (@NonNull final XslElement aEl)
  {
    final String sUseWhen = aEl.getStandardAttr ("use-when");
    if (sUseWhen == null)
      return false;
    try
    {
      return !Atomizer.getEffectiveBooleanValue (_evaluateStatic (aEl, sUseWhen));
    }
    catch (final XPathException ex)
    {
      // An unknown XSLT element under forwards-compatible processing whose
      // use-when cannot be evaluated is simply excluded
      if (aEl.isXsl () && aEl.isForwardsCompatible () && !XsltElementSpecs.isKnown (aEl.getLocalName ()))
        return true;
      throw ex;
    }
  }

  /**
   * @param aSrc
   *        A source element.
   * @return <code>true</code> if it has an element child or a text child that
   *         is not whitespace-only (comments and processing instructions do
   *         not count).
   */
  private static boolean _hasContent (@NonNull final IXdmNode aSrc)
  {
    final int n = aSrc.getChildCount ();
    for (int i = 0; i < n; ++i)
    {
      final IXdmNode aChild = aSrc.getChildAtIndex (i);
      if (aChild.isElement () || (aChild.isText () && !XsltNames.isWhitespaceOnly (aChild.getValue ())))
        return true;
    }
    return false;
  }

  /**
   * Process a static xsl:variable or xsl:param (XSLT 3.0 section 9.6) when it
   * is met in tree order, so later static expressions can refer to it. The
   * value is the externally supplied static parameter value (xsl:param only),
   * else the value of the select expression, else the empty sequence if an
   * <code>as</code> attribute is present, otherwise a zero-length string; it is
   * then converted to the <code>as</code> type with the function conversion
   * rules (error XTTE0590). A second declaration of the same name is resolved
   * by import precedence: if the earlier one belongs to a module that imports
   * the current one (its group is still on the stack), the earlier value
   * stays; if it belongs to the same precedence group the new value replaces
   * it; otherwise both must agree in kind and be deep-equal, and the new
   * value takes over.
   *
   * @param aEl
   *        The static xsl:variable or xsl:param element.
   * @throws XsltException
   *         XTSE0020 if the name is not a valid QName, XTSE0010 if there is
   *         content without a select attribute, XTDE0050 for a required
   *         static parameter without a supplied value, XTSE3450 for an
   *         inconsistent redeclaration, or the error of evaluating or
   *         converting the value
   */
  private void _processStaticDeclaration (@NonNull final XslElement aEl)
  {
    final XdmQName aName = aEl.resolveQName (aEl.getAttr ("name") == null ? "" : aEl.getAttr ("name"), false);
    if (aName == null)
      throw XsltException.at (aEl, "XTSE0020", "The name of a static variable is not a valid QName");
    if (!aEl.hasAttr ("select") && _hasContent (aEl.getSource ()))
      throw XsltException.at (aEl, "XTSE0010", "A static xsl:" + aEl.getLocalName () + " must not have content");
    XdmSequence aValue = null;
    if (aEl.isXsl ("param"))
      aValue = m_aOptions.staticParams ().get (aName);
    if (aValue == null)
    {
      final String sSelect = aEl.getAttr ("select");
      if (sSelect != null)
        aValue = _evaluateStatic (aEl, sSelect);
      else
      {
        if (aEl.isXsl ("param") && XsltNames.isXsltTrue (aEl.getAttr ("required")))
          throw XsltException.at (aEl, "XTDE0050", "No value was supplied for the required static parameter $" + aName.getEQName ());
        aValue = aEl.hasAttr ("as") ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.string (""));
      }
    }
    final String sAs = aEl.getAttr ("as");
    if (sAs != null)
    {
      try
      {
        final StaticContext aSC = _createStaticContext (aEl);
        final SeqType aType = XPathParser.parseSequenceType (sAs, aSC);
        aValue = SequenceTypeMatcher.convert (aValue, aType, new DynamicContext (aSC), "XTTE0590");
      }
      catch (final XPathException ex)
      {
        throw XsltException.locate (ex, aEl);
      }
    }
    final Object aGroup = m_aGroupStack.isEmpty () ? null : m_aGroupStack.getLast ();
    final Object aPrevGroup = m_aStaticGroups.get (aName);
    if (aPrevGroup != null || m_aStaticValues.containsKey (aName))
    {
      // An earlier declaration of an importing module has the higher import
      // precedence and stays in effect
      if (aPrevGroup != aGroup && m_aGroupStack.contains (aPrevGroup))
        return;
      // An earlier declaration with lower import precedence must agree, in
      // kind (variable or param) as well as in value
      if (aPrevGroup != aGroup)
      {
        boolean bSame;
        try
        {
          bSame = aEl.getLocalName ().equals (m_aStaticKinds.get (aName)) &&
                  AtomicComparator.deepEqual (m_aStaticValues.get (aName),
                                              aValue,
                                              null,
                                              new DynamicContext (_createStaticContext (aEl)));
        }
        catch (final XPathException ex)
        {
          bSame = false;
        }
        if (!bSame)
          throw XsltException.at (aEl,
                                  "XTSE3450",
                                  "The static variable $" + aName.getEQName () + " is inconsistent with a declaration of lower import precedence");
      }
    }
    m_aStaticValues.put (aName, aValue);
    m_aStaticGroups.put (aName, aGroup);
    m_aStaticKinds.put (aName, aEl.getLocalName ());
  }

  /**
   * Find the first element in document order (depth-first, with an explicit
   * stack) whose <code>xml:id</code> or <code>id</code> attribute, trimmed,
   * equals the ID; used for a fragment identifier in the href of xsl:include
   * or xsl:import (XSLT 3.0 section 3.12).
   *
   * @param aRoot
   *        The node to search below (itself included).
   * @param sID
   *        The ID to find.
   * @return The element or <code>null</code> if there is none.
   */
  @Nullable
  private static IXdmNode _findById (@NonNull final IXdmNode aRoot, @NonNull final String sID)
  {
    final ICommonsList <IXdmNode> aStack = new CommonsArrayList <> ();
    aStack.add (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.removeLast ();
      if (aCur.isElement ())
      {
        final IXdmNode aXmlId = aCur.getAttribute (XdmConstants.NS_XML, "id");
        final IXdmNode aId = aCur.getAttribute ("", "id");
        if ((aXmlId != null && sID.equals (aXmlId.getValue ().trim ())) || (aId != null && sID.equals (aId.getValue ().trim ())))
          return aCur;
      }
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.add (aCur.getChildAtIndex (i));
    }
    return null;
  }

  /**
   * @param aDoc
   *        A document or element node.
   * @return The node itself if it is an element, else its first element
   *         child, or <code>null</code> if there is none.
   */
  @Nullable
  private static IXdmNode _getDocumentElement (@NonNull final IXdmNode aDoc)
  {
    if (aDoc.isElement ())
      return aDoc;
    final int n = aDoc.getChildCount ();
    for (int i = 0; i < n; ++i)
      if (aDoc.getChildAtIndex (i).isElement ())
        return aDoc.getChildAtIndex (i);
    return null;
  }

  /**
   * @param s
   *        A URI.
   * @return The URI without the fragment identifier (from the first
   *         <code>#</code> on).
   */
  @NonNull
  private static String _stripFragment (@NonNull final String s)
  {
    final int n = s.indexOf ('#');
    return n < 0 ? s : s.substring (0, n);
  }

  /**
   * Load the module referenced by an xsl:include or xsl:import (XSLT 3.0
   * sections 3.11.1 to 3.11.3). The href is resolved against the static base
   * URI of the element when that is absolute; a fragment identifier selects
   * the element with that ID (an embedded module, section 3.12). An empty URI,
   * or one equal to the base URI without its fragment, refers to the
   * document of the including module itself. The referenced element must be
   * an XSLT element other than xsl:package, or the outermost element of a
   * simplified stylesheet (with xsl:version). An xsl:import starts a new
   * import precedence group, an xsl:include joins the group of the including
   * module; the module is loaded recursively with
   * {@link #loadModule(IXdmNode, String, boolean)}.
   *
   * @param aIncludeEl
   *        The xsl:include or xsl:import element.
   * @return The loaded module.
   * @throws XsltException
   *         XTSE0010 without href, XTSE0180 (include) or XTSE0210 (import)
   *         if the module is already being loaded (a cycle), XTSE0165 if the
   *         href is invalid, the resource cannot be retrieved, has no
   *         document element or ID-matching element, is a package, or is no
   *         stylesheet module
   */
  @NonNull
  private XslModule _loadReferencedModule (@NonNull final XslElement aIncludeEl)
  {
    final String sHref = aIncludeEl.getAttr ("href");
    if (sHref == null)
      throw XsltException.at (aIncludeEl, "XTSE0010", "xsl:" + aIncludeEl.getLocalName () + " requires an href attribute");
    String sUri;
    final String sBase = aIncludeEl.getStaticBaseUri ();
    try
    {
      sUri = sBase != null && FnUri.isAbsoluteUri (sBase) ? FnUri.resolveUri (sHref.trim (), sBase) : sHref.trim ();
    }
    catch (final XPathException ex)
    {
      throw XsltException.at (aIncludeEl, "XTSE0165", "Invalid href '" + sHref + "': " + ex.getMessage ());
    }
    String sFragment = null;
    final int nHash = sUri.indexOf ('#');
    if (nHash >= 0)
    {
      sFragment = sUri.substring (nHash + 1);
      sUri = sUri.substring (0, nHash);
    }
    final String sKey = sUri + (sFragment == null ? "" : "#" + sFragment);
    if (m_aLoadStack.contains (sKey))
      throw XsltException.at (aIncludeEl,
                              aIncludeEl.isXsl ("import") ? "XTSE0210" : "XTSE0180",
                              "The stylesheet module '" + sKey + "' includes or imports itself");
    IXdmNode aDoc;
    if (sUri.isEmpty () || (sBase != null && sUri.equals (_stripFragment (sBase))))
      aDoc = aIncludeEl.getModule ().getSourceDocument ();
    else
    {
      try
      {
        aDoc = _getResolver ().resolveStylesheetModule (ResourceUri.of (sUri, _stripFragment (sHref.trim ()), sBase));
      }
      catch (final XPathException ex)
      {
        // A resolver that fails (e.g. a JAXP URIResolver that refuses the
        // URI): not retrievable either, with its reason
        final XsltException aEx = XsltException.at (aIncludeEl,
                                                    "XTSE0165",
                                                    "The stylesheet module '" + sUri + "' cannot be retrieved: " + ex.getMessage ());
        aEx.initCause (ex);
        throw aEx;
      }
      if (aDoc == null)
        throw XsltException.at (aIncludeEl, "XTSE0165", "The stylesheet module '" + sUri + "' cannot be retrieved");
      if (aDoc.getDocumentUri () == null && aDoc.isDocument ())
        aDoc.setDocumentUri (sUri);
    }
    IXdmNode aRoot;
    if (sFragment != null)
    {
      aRoot = _findById (aDoc, sFragment);
      if (aRoot == null)
        throw XsltException.at (aIncludeEl, "XTSE0165", "No element with ID '" + sFragment + "' in '" + sUri + "'");
    }
    else
      aRoot = _getDocumentElement (aDoc);
    if (aRoot == null)
      throw XsltException.at (aIncludeEl, "XTSE0165", "The stylesheet module '" + sUri + "' has no document element");
    if (XsltNames.NS_XSL.equals (aRoot.getName ().namespaceUri ()) && "package".equals (aRoot.getName ().localName ()))
      throw XsltException.at (aIncludeEl,
                              "XTSE0165",
                              "'" + sUri + "' is an xsl:package, which cannot be the target of xsl:" + aIncludeEl.getLocalName ());
    // Section 3.11.1: a resource that is neither an XSLT element nor a
    // simplified stylesheet module does not contain a stylesheet module
    if (!XsltNames.NS_XSL.equals (aRoot.getName ().namespaceUri ()) &&
        aRoot.getAttribute (XsltNames.NS_XSL, "version") == null)
      throw XsltException.at (aIncludeEl, "XTSE0165", "'" + sUri + "' does not contain a stylesheet module");
    m_aLoadStack.add (sKey);
    m_aGroupStack.add (aIncludeEl.isXsl ("import") || m_aGroupStack.isEmpty () ? new Object () : m_aGroupStack.getLast ());
    try
    {
      return loadModule (aRoot, sUri, false);
    }
    finally
    {
      m_aLoadStack.removeLast ();
      m_aGroupStack.removeLast ();
    }
  }

  /**
   * Load the package named by an xsl:use-package (XSLT 3.0 section 3.5.2)
   * through the {@link PackageRegistry} (created on first use) as a new
   * package unit with its own import precedence group, and record the use on
   * the using package (the innermost one being loaded).
   *
   * @param aUseEl
   *        The xsl:use-package element.
   * @return The outermost module of the used package.
   * @throws XsltException
   *         XTSE3005 if the package is already being loaded (it uses itself
   *         directly or indirectly), or the errors of resolving the package
   */
  @NonNull
  private XslModule _loadUsedPackage (@NonNull final XslElement aUseEl)
  {
    if (m_aRegistry == null)
      m_aRegistry = new PackageRegistry (m_aOptions);
    final PackageRegistry.ResolvedPackage aResolved = m_aRegistry.resolve (aUseEl);
    for (final PackageUnit aActive : m_aPackageStack)
      if (aActive.getKey ().equals (aResolved.key ()))
        throw XsltException.at (aUseEl, "XTSE3005", "The package '" + aResolved.name () + "' is used recursively");
    final PackageUnit aUsing = m_aPackageStack.getLast ();
    final PackageUnit aUnit = m_aPackages.createUnit (aResolved.key (), false);
    m_aPackageStack.add (aUnit);
    m_aGroupStack.add (new Object ());
    try
    {
      final XslModule ret = loadModule (aResolved.root (), aResolved.baseUri (), false);
      aUsing.uses ().add (new PackageUnit.Use (aUseEl, aUnit));
      return ret;
    }
    finally
    {
      m_aPackageStack.removeLast ();
      m_aGroupStack.removeLast ();
    }
  }

  /**
   * Build the processed element for a source element; <code>null</code> if
   * use-when excludes it. The attributes are copied, the element is appended
   * to its parent, its shadow attributes are expanded and its use-when is
   * evaluated (an excluded element is removed again with its whole subtree,
   * which is never built). On a top-level XSLT element a static variable or
   * parameter is processed, and an xsl:include, xsl:import or xsl:use-package
   * loads its target module at once, so all static processing happens in
   * stylesheet tree order. The children are built last.
   *
   * @throws XsltException
   *         XPDY0130 if the elements, counting those of the modules loaded
   *         from here, are nested deeper than {@link #MAX_DEPTH}
   */
  @Nullable
  private XslElement _build (@NonNull final XslModule aModule,
                             @NonNull final IXdmNode aSrc,
                             @Nullable final XslElement aParent,
                             final boolean bTopLevel)
  {
    final XslElement aEl = new XslElement (aModule, aSrc);
    if (m_nBuildDepth >= MAX_DEPTH)
      throw XsltException.at (aEl, "XPDY0130", "The stylesheet is nested more than " + MAX_DEPTH + " levels deep");
    m_nBuildDepth++;
    try
    {
      final int nAttrs = aSrc.getAttributeCount ();
      for (int i = 0; i < nAttrs; ++i)
      {
        final IXdmNode aAttr = aSrc.getAttributeAtIndex (i);
        aEl.setAttribute (aAttr.getName (), aAttr.getValue ());
      }
      if (aParent != null)
        aParent.appendChild (aEl);
      _expandShadowAttributes (aEl);
      if (_isUseWhenFalse (aEl))
      {
        if (aParent != null)
          aParent.removeLastChild ();
        return null;
      }
      if (bTopLevel && aEl.isXsl ())
      {
        if ((aEl.isXsl ("param") || aEl.isXsl ("variable")) && XsltNames.isXsltTrue (aEl.getAttr ("static")))
          _processStaticDeclaration (aEl);
        else
          if (aEl.isXsl ("include") || aEl.isXsl ("import"))
            m_aLoadedModules.put (aEl, _loadReferencedModule (aEl));
          else
            if (aEl.isXsl ("use-package"))
              m_aUsedPackages.put (aEl, _loadUsedPackage (aEl));
      }
      _buildChildren (aModule, aSrc, aEl);
      return aEl;
    }
    finally
    {
      m_nBuildDepth--;
    }
  }

  /**
   * Build the children of an element: adjacent text nodes are merged into one
   * {@link XslText} located at the first of them, elements are built with
   * {@link #_build(XslModule, IXdmNode, XslElement, boolean)}, comments and
   * processing instructions are dropped. Children of the outermost XSLT
   * element of a module that is not simplified are top-level.
   *
   * @param aModule
   *        The module being built.
   * @param aSrc
   *        The source element.
   * @param aEl
   *        The processed element the children are appended to.
   */
  private void _buildChildren (@NonNull final XslModule aModule, @NonNull final IXdmNode aSrc, @NonNull final XslElement aEl)
  {
    final boolean bChildrenTopLevel = aEl.getParent () == null && aEl.isXsl () && !aModule.isSimplified ();
    final StringBuilder aText = new StringBuilder ();
    int nTextLine = 0;
    int nTextColumn = 0;
    final int nChildren = aSrc.getChildCount ();
    for (int i = 0; i < nChildren; ++i)
    {
      final IXdmNode aChild = aSrc.getChildAtIndex (i);
      final ENodeKind eKind = aChild.getKind ();
      if (eKind == ENodeKind.TEXT)
      {
        if (aText.length () == 0)
        {
          nTextLine = aChild.getLine ();
          nTextColumn = aChild.getColumn ();
        }
        aText.append (aChild.getValue ());
      }
      else
        if (eKind == ENodeKind.ELEMENT)
        {
          if (aText.length () > 0)
          {
            aEl.appendChild (new XslText (aModule, aText.toString (), nTextLine, nTextColumn));
            aText.setLength (0);
          }
          _build (aModule, aChild, aEl, bChildrenTopLevel);
        }
    }
    if (aText.length () > 0)
      aEl.appendChild (new XslText (aModule, aText.toString (), nTextLine, nTextColumn));
  }

  /**
   * Load one module from its outermost element and build its processed tree,
   * including all modules and packages it references. For the principal
   * module the first import precedence group and the principal package unit
   * are started. The outermost element must be xsl:stylesheet, xsl:transform
   * or xsl:package, or a literal result element with xsl:version (a
   * simplified stylesheet, XSLT 3.0 section 3.8). If use-when on the
   * outermost element is false, the module keeps the element but none of its
   * children (XSLT 3.0 section 3.13.1); this is not checked for a simplified
   * principal module.
   *
   * @param aRootSrc
   *        The outermost element of the module.
   * @param sUri
   *        The module URI or <code>null</code>.
   * @param bPrincipal
   *        <code>true</code> for the principal module.
   * @return The module.
   * @throws XsltException
   *         XTSE0150 for a literal result element without xsl:version, XTSE0010
   *         for another XSLT element as outermost element, and the errors of
   *         static processing
   */
  @NonNull
  public XslModule loadModule (@NonNull final IXdmNode aRootSrc, @Nullable final String sUri, final boolean bPrincipal)
  {
    final XslModule aModule = new XslModule (sUri, aRootSrc.getRoot ());
    m_aAllModules.add (aModule);
    if (bPrincipal)
    {
      m_aGroupStack.add (new Object ());
      m_aPackageStack.add (m_aPackages.createUnit (sUri != null ? sUri : "#principal", true));
    }
    m_aPackages.addModule (m_aPackageStack.getLast (), aModule);
    final XdmQName aRootName = aRootSrc.getName ();
    if (!XsltNames.NS_XSL.equals (aRootName.namespaceUri ()))
    {
      if (aRootSrc.getAttribute (XsltNames.NS_XSL, "version") == null)
      {
        final XslElement aTmp = new XslElement (aModule, aRootSrc);
        throw XsltException.at (aTmp, "XTSE0150", "A simplified stylesheet requires an xsl:version attribute");
      }
      aModule.setSimplified (true);
    }
    else
    {
      final String sLocal = aRootName.localName ();
      if (!"stylesheet".equals (sLocal) && !"transform".equals (sLocal) && !"package".equals (sLocal))
      {
        final XslElement aTmp = new XslElement (aModule, aRootSrc);
        throw XsltException.at (aTmp, "XTSE0010", "The outermost element of a stylesheet module must be xsl:stylesheet");
      }
    }
    final XslElement aRoot = new XslElement (aModule, aRootSrc);
    final int nAttrs = aRootSrc.getAttributeCount ();
    for (int i = 0; i < nAttrs; ++i)
    {
      final IXdmNode aAttr = aRootSrc.getAttributeAtIndex (i);
      aRoot.setAttribute (aAttr.getName (), aAttr.getValue ());
    }
    aModule.setRoot (aRoot);
    _expandShadowAttributes (aRoot);
    // Section 3.13.1: use-when false on the outermost element excludes all its
    // children (but not the element or its attributes) - on the principal
    // module too
    if ((!bPrincipal || !aModule.isSimplified ()) && _isUseWhenFalse (aRoot))
      return aModule;
    _buildChildren (aModule, aRootSrc, aRoot);
    return aModule;
  }

  /**
   * @param aModule
   *        A loaded module.
   * @param nDepth
   *        The number of including modules above it.
   * @return <code>true</code> if it contains an xsl:use-package declaration,
   *         directly or in a module it includes (recursively); imported
   *         modules are not searched.
   * @throws XsltException
   *         XPDY0130 if the includes are nested deeper than
   *         {@link #MAX_DEPTH}
   */
  private boolean _containsUsePackage (@NonNull final XslModule aModule, final int nDepth)
  {
    if (aModule.getRoot () == null || aModule.isSimplified ())
      return false;
    if (nDepth >= MAX_DEPTH)
      throw XsltException.at (aModule.getRoot (), "XPDY0130", "The modules are nested more than " + MAX_DEPTH + " levels deep");
    for (final XslElement aChild : aModule.getRoot ().getElementChildren ())
    {
      if (aChild.isXsl ("use-package"))
        return true;
      final XslModule aSub = m_aLoadedModules.get (aChild);
      if (aSub != null && aChild.isXsl ("include") && _containsUsePackage (aSub, nDepth + 1))
        return true;
    }
    return false;
  }

  /**
   * Collect the import precedence levels of a module recursively, lowest
   * precedence first (XSLT 3.0 section 3.11.3). The levels of each imported
   * module come before the module's own level, in import order; an included
   * module contributes its imported levels likewise, while its own
   * declarations are merged into the including module's level at the point
   * of the xsl:include. A used package is placed like an imported module, and
   * the declarations inside its xsl:override elements join the using
   * module's level. The <code>importLo</code> of sub-levels is shifted by the
   * number of levels before them.
   *
   * @param aModule
   *        The module.
   * @param nDepth
   *        The number of modules above it.
   * @return The levels; the last one is the module's own.
   * @throws XsltException
   *         XTSE3008 if an imported module contains an xsl:use-package;
   *         XPDY0130 if the modules are nested deeper than
   *         {@link #MAX_DEPTH}
   */
  @NonNull
  private ICommonsList <Level> _gather (@NonNull final XslModule aModule, final int nDepth)
  {
    if (nDepth >= MAX_DEPTH)
      throw XsltException.at (aModule.getRoot (), "XPDY0130", "The modules are nested more than " + MAX_DEPTH + " levels deep");
    final ICommonsList <Level> ret = new CommonsArrayList <> ();
    final ICommonsList <XslElement> aOwn = new CommonsArrayList <> ();
    if (aModule.isSimplified ())
      aOwn.add (aModule.getRoot ());
    else
      for (final XslElement aChild : aModule.getRoot ().getElementChildren ())
      {
        final XslModule aUsed = m_aUsedPackages.get (aChild);
        if (aUsed != null)
        {
          // A used package sits below the using package, like an imported
          // module; the declarations of xsl:override join the using package
          final int nBase = ret.size ();
          for (final Level aLevel : _gather (aUsed, nDepth + 1))
            ret.add (new Level (aLevel.declarations (), aLevel.importLo () + nBase));
          for (final XslElement aOverride : aChild.getXslChildren ("override"))
            aOwn.addAll (aOverride.getElementChildren ());
          continue;
        }
        final XslModule aSub = m_aLoadedModules.get (aChild);
        if (aSub == null)
        {
          aOwn.add (aChild);
          continue;
        }
        final ICommonsList <Level> aSubLevels = _gather (aSub, nDepth + 1);
        final int nBase = ret.size ();
        final ICommonsList <Level> aRebased = new CommonsArrayList <> ();
        for (final Level aLevel : aSubLevels)
          aRebased.add (new Level (aLevel.declarations (), aLevel.importLo () + nBase));
        if (aChild.isXsl ("import"))
        {
          if (_containsUsePackage (aSub, 0))
            throw XsltException.at (aChild,
                                    "XTSE3008",
                                    "The module imported here contains an xsl:use-package; use xsl:include to bring one in");
          ret.addAll (aRebased);
        }
        else
        {
          ret.addAll (aRebased.subList (0, aRebased.size () - 1));
          aOwn.addAll (aRebased.getLast ().declarations ());
        }
      }
    ret.add (new Level (aOwn, 0));
    return ret;
  }

  /**
   * Arrange the declarations of all modules by import precedence and assign
   * the precedence to each top-level element. A declaration inside
   * xsl:override passes its precedence on to the xsl:override and its
   * xsl:use-package parent.
   *
   * @param aPrincipal
   *        The principal module.
   * @return The levels, lowest import precedence first; the index is the
   *         precedence.
   */
  @NonNull
  public ICommonsList <Level> arrangeByPrecedence (@NonNull final XslModule aPrincipal)
  {
    final ICommonsList <Level> ret = _gather (aPrincipal, 0);
    for (int i = 0; i < ret.size (); ++i)
      for (final XslElement aDecl : ret.get (i).declarations ())
      {
        aDecl.setPrecedence (i, ret.get (i).importLo ());
        if (PackageModel.isOverride (aDecl))
        {
          // xsl:override and xsl:use-package take the precedence of the
          // overriding declarations
          aDecl.getParent ().setPrecedence (i, ret.get (i).importLo ());
          aDecl.getParent ().getParent ().setPrecedence (i, ret.get (i).importLo ());
        }
      }
    return ret;
  }
}
