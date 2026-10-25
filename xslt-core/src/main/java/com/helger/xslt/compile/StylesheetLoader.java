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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.fn.FnUri;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltException;
import com.helger.xslt.fn.XsltFunctions;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslModule;
import com.helger.xslt.tree.XslText;
import com.helger.xslt.tree.XsltNames;

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
  /** The top-level declarations of one import precedence. */
  public record Level (@NonNull ICommonsList <XslElement> declarations, int importLo)
  {}

  private final XsltCompileOptions m_aOptions;
  private final FunctionLibrary m_aStaticFunctions;
  private final ICommonsOrderedMap <XdmQName, XdmSequence> m_aStaticValues = new CommonsLinkedHashMap <> ();
  private final ICommonsMap <XslElement, XslModule> m_aLoadedModules = new CommonsHashMap <> ();
  private final ICommonsList <XslModule> m_aAllModules = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aLoadStack = new CommonsArrayList <> ();

  public StylesheetLoader (@NonNull final XsltCompileOptions aOptions)
  {
    m_aOptions = aOptions;
    m_aStaticFunctions = FunctionLibrary.createDefault ();
    XsltFunctions.registerStatic (m_aStaticFunctions);
  }

  /** @return The static variables and parameters with their values. */
  @NonNull
  public ICommonsOrderedMap <XdmQName, XdmSequence> getStaticValues ()
  {
    return m_aStaticValues;
  }

  /** @return All loaded modules (a module imported twice appears twice). */
  @NonNull
  public ICommonsList <XslModule> getAllModules ()
  {
    return m_aAllModules;
  }

  @NonNull
  private StaticContext _createStaticContext (@NonNull final XslElement aEl)
  {
    return StaticContexts.create (aEl, m_aStaticFunctions, m_aStaticValues.keySet (), null, null);
  }

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
      for (final Map.Entry <XdmQName, XdmSequence> aEntry : m_aStaticValues.entrySet ())
        aDC = aDC.withVariable (aEntry.getKey (), aEntry.getValue ());
      return XPathEvaluator.evaluate (aExpr, aDC);
    }
    catch (final XPathException ex)
    {
      throw XsltException.locate (ex, aEl);
    }
  }

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
      if (bXsl && aName.namespaceUri ().isEmpty ())
        aTarget = new XdmQName ("", sLocal.substring (1));
      else
        if (!bXsl && XsltNames.NS_XSL.equals (aName.namespaceUri ()))
          aTarget = new XdmQName (XsltNames.NS_XSL, sLocal.substring (1), aName.prefix ());
        else
          continue;
      final String sValue = _evaluateStaticAvt (aEl, aEntry.getValue ());
      aEl.removeAttribute (aName);
      aEl.setAttribute (aTarget, sValue);
    }
  }

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

  private static boolean _hasContent (@NonNull final XdmNode aSrc)
  {
    final int n = aSrc.getChildCount ();
    for (int i = 0; i < n; ++i)
    {
      final XdmNode aChild = aSrc.getChildAtIndex (i);
      if (aChild.isElement () || aChild.isText () && !XsltNames.isWhitespaceOnly (aChild.getValue ()))
        return true;
    }
    return false;
  }

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
    m_aStaticValues.put (aName, aValue);
  }

  @NonNull
  private static XdmNode _findById (@NonNull final XdmNode aRoot, @NonNull final String sID)
  {
    final ICommonsList <XdmNode> aStack = new CommonsArrayList <> ();
    aStack.add (aRoot);
    while (!aStack.isEmpty ())
    {
      final XdmNode aCur = aStack.removeLast ();
      if (aCur.isElement ())
      {
        final XdmNode aXmlId = aCur.getAttribute (XdmConstants.NS_XML, "id");
        final XdmNode aId = aCur.getAttribute ("", "id");
        if (aXmlId != null && sID.equals (aXmlId.getValue ().trim ()) || aId != null && sID.equals (aId.getValue ().trim ()))
          return aCur;
      }
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.add (aCur.getChildAtIndex (i));
    }
    return null;
  }

  @Nullable
  private static XdmNode _getDocumentElement (@NonNull final XdmNode aDoc)
  {
    if (aDoc.isElement ())
      return aDoc;
    final int n = aDoc.getChildCount ();
    for (int i = 0; i < n; ++i)
      if (aDoc.getChildAtIndex (i).isElement ())
        return aDoc.getChildAtIndex (i);
    return null;
  }

  @NonNull
  private static String _stripFragment (@NonNull final String s)
  {
    final int n = s.indexOf ('#');
    return n < 0 ? s : s.substring (0, n);
  }

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
      throw XsltException.at (aIncludeEl, "XTSE0180", "The stylesheet module '" + sKey + "' includes or imports itself");
    XdmNode aDoc;
    if (sUri.isEmpty () || sBase != null && sUri.equals (_stripFragment (sBase)))
      aDoc = aIncludeEl.getModule ().getSourceDocument ();
    else
    {
      aDoc = _getResolver ().resolveDocument (sUri);
      if (aDoc == null)
        throw XsltException.at (aIncludeEl, "XTSE0165", "The stylesheet module '" + sUri + "' cannot be retrieved");
      if (aDoc.getDocumentUri () == null && aDoc.isDocument ())
        aDoc.setDocumentUri (sUri);
    }
    XdmNode aRoot;
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
    m_aLoadStack.add (sKey);
    try
    {
      return loadModule (aRoot, sUri, false);
    }
    finally
    {
      m_aLoadStack.removeLast ();
    }
  }

  /**
   * Build the processed element for a source element; <code>null</code> if
   * use-when excludes it.
   */
  @Nullable
  private XslElement _build (@NonNull final XslModule aModule,
                             @NonNull final XdmNode aSrc,
                             @Nullable final XslElement aParent,
                             final boolean bTopLevel)
  {
    final XslElement aEl = new XslElement (aModule, aSrc);
    final int nAttrs = aSrc.getAttributeCount ();
    for (int i = 0; i < nAttrs; ++i)
    {
      final XdmNode aAttr = aSrc.getAttributeAtIndex (i);
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
    }
    _buildChildren (aModule, aSrc, aEl);
    return aEl;
  }

  private void _buildChildren (@NonNull final XslModule aModule, @NonNull final XdmNode aSrc, @NonNull final XslElement aEl)
  {
    final boolean bChildrenTopLevel = aEl.getParent () == null && aEl.isXsl () && !aModule.isSimplified ();
    final StringBuilder aText = new StringBuilder ();
    int nTextLine = 0;
    int nTextColumn = 0;
    final int nChildren = aSrc.getChildCount ();
    for (int i = 0; i < nChildren; ++i)
    {
      final XdmNode aChild = aSrc.getChildAtIndex (i);
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
   * Load one module from its outermost element.
   *
   * @param aRootSrc
   *        The outermost element of the module.
   * @param sUri
   *        The module URI or <code>null</code>.
   * @param bPrincipal
   *        <code>true</code> for the principal module.
   * @return The module.
   */
  @NonNull
  public XslModule loadModule (@NonNull final XdmNode aRootSrc, @Nullable final String sUri, final boolean bPrincipal)
  {
    final XslModule aModule = new XslModule (sUri, aRootSrc.getRoot ());
    m_aAllModules.add (aModule);
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
      final XdmNode aAttr = aRootSrc.getAttributeAtIndex (i);
      aRoot.setAttribute (aAttr.getName (), aAttr.getValue ());
    }
    aModule.setRoot (aRoot);
    _expandShadowAttributes (aRoot);
    if (!bPrincipal && _isUseWhenFalse (aRoot))
      return aModule;
    _buildChildren (aModule, aRootSrc, aRoot);
    return aModule;
  }

  @NonNull
  private ICommonsList <Level> _gather (@NonNull final XslModule aModule)
  {
    final ICommonsList <Level> ret = new CommonsArrayList <> ();
    final ICommonsList <XslElement> aOwn = new CommonsArrayList <> ();
    if (aModule.isSimplified ())
      aOwn.add (aModule.getRoot ());
    else
      for (final XslElement aChild : aModule.getRoot ().getElementChildren ())
      {
        final XslModule aSub = m_aLoadedModules.get (aChild);
        if (aSub == null)
        {
          aOwn.add (aChild);
          continue;
        }
        final ICommonsList <Level> aSubLevels = _gather (aSub);
        final int nBase = ret.size ();
        final ICommonsList <Level> aRebased = new CommonsArrayList <> ();
        for (final Level aLevel : aSubLevels)
          aRebased.add (new Level (aLevel.declarations (), aLevel.importLo () + nBase));
        if (aChild.isXsl ("import"))
          ret.addAll (aRebased);
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
   * the precedence to each top-level element.
   *
   * @param aPrincipal
   *        The principal module.
   * @return The levels, lowest import precedence first; the index is the
   *         precedence.
   */
  @NonNull
  public ICommonsList <Level> arrangeByPrecedence (@NonNull final XslModule aPrincipal)
  {
    final ICommonsList <Level> ret = _gather (aPrincipal);
    for (int i = 0; i < ret.size (); ++i)
      for (final XslElement aDecl : ret.get (i).declarations ())
        aDecl.setPrecedence (i, ret.get (i).importLo ());
    return ret;
  }
}
