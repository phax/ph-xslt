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
package com.helger.xslt.pkg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslModule;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XsltNames;

/**
 * The packages of one compilation (XSLT 3.0 section 3.5). Execution is
 * flattened: a used package's modules join the module list below the using
 * package, like an imported module, and the declarations of xsl:override join
 * the using package. What the flattening loses is kept here: which package
 * each module belongs to, and which used-package components are hidden from
 * the code of each package. The hiding is applied where names are resolved.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class PackageModel
{
  /**
   * What one xsl:use-package makes of the used package's components, as
   * symbolic names: <code>hidden</code> from the using package,
   * <code>visible</code> to it, accepted as <code>abstracts</code>, and those
   * whose visibility an xsl:accept <code>stated</code>.
   */
  private record UsedVisibility (@NonNull ICommonsSet <String> hidden,
                                 @NonNull ICommonsSet <String> visible,
                                 @NonNull ICommonsSet <String> abstracts,
                                 @NonNull ICommonsSet <String> stated)
  {}

  private final ICommonsList <PackageUnit> m_aUnits = new CommonsArrayList <> ();
  private final ICommonsMap <XslModule, PackageUnit> m_aByModule = new CommonsHashMap <> ();
  private final ICommonsMap <String, ICommonsList <PackageComponent>> m_aExports = new CommonsHashMap <> ();
  private final ICommonsSet <String> m_aAcceptedExplicit = new CommonsHashSet <> ();
  private final ICommonsMap <XslElement, PackageChecks.Exposure> m_aExposures = new CommonsHashMap <> ();
  private final ICommonsMap <Object, PackageUnit> m_aScopes = new CommonsHashMap <> ();

  /**
   * @param sKey
   *        Identity of the package source.
   * @param bPrincipal
   *        <code>true</code> for the principal package.
   * @return A new unit.
   */
  @NonNull
  public PackageUnit createUnit (@NonNull final String sKey, final boolean bPrincipal)
  {
    final PackageUnit ret = new PackageUnit (sKey, bPrincipal);
    m_aUnits.add (ret);
    return ret;
  }

  /**
   * Add a module to a package and remember which package it belongs to. A
   * module of a library package gets the index of its unit as package
   * qualifier (see {@link #qualify(XslNode, XdmQName)}); modules of the
   * principal package keep the empty qualifier.
   *
   * @param aUnit
   *        The package.
   * @param aModule
   *        A module of it.
   */
  public void addModule (@NonNull final PackageUnit aUnit, @NonNull final XslModule aModule)
  {
    if (!aUnit.isPrincipal ())
      aModule.setPackageQualifier (Integer.toString (m_aUnits.indexOf (aUnit)));
    aUnit.modules ().add (aModule);
    m_aByModule.put (aModule, aUnit);
  }

  /**
   * Record an object that identifies the package of an expression at run
   * time (its function library).
   *
   * @param aScope
   *        The object.
   * @param aUnit
   *        The package.
   */
  public void registerScope (@NonNull final Object aScope, @NonNull final PackageUnit aUnit)
  {
    m_aScopes.put (aScope, aUnit);
  }

  /**
   * @param aScope
   *        An object registered with {@link #registerScope(Object, PackageUnit)}.
   * @return The package, the principal one for an unknown object.
   */
  @NonNull
  public PackageUnit getUnitForScope (@Nullable final Object aScope)
  {
    final PackageUnit ret = aScope == null ? null : m_aScopes.get (aScope);
    return ret != null ? ret : getPrincipal ();
  }

  /**
   * @param sQualifier
   *        The package qualifier of a module, empty in the principal package.
   * @param aName
   *        The name.
   * @return The name itself for an empty qualifier, else a name whose
   *         namespace URI has the character U+001F and the qualifier
   *         appended (local name and prefix unchanged).
   */
  @NonNull
  private static XdmQName _qualify (@NonNull final String sQualifier, @NonNull final XdmQName aName)
  {
    if (sQualifier.isEmpty ())
      return aName;
    return new XdmQName (aName.namespaceUri () + "\u001F" + sQualifier, aName.localName (), aName.prefix ());
  }

  /**
   * Qualify the name of a package-local declaration (named xsl:output,
   * xsl:character-map) or of a reference to one with the package it is
   * written in, so that equal names in different packages differ.
   *
   * @param aNode
   *        The declaration or reference.
   * @param aName
   *        The name.
   * @return The qualified name, the name itself in the principal package.
   */
  @NonNull
  public static XdmQName qualify (@NonNull final XslNode aNode, @NonNull final XdmQName aName)
  {
    return _qualify (aNode.getModule ().getPackageQualifier (), aName);
  }

  /**
   * Like {@link #qualify(XslNode, XdmQName)} for a name used at run time by an
   * expression.
   *
   * @param aScope
   *        The function library of the expression's static context.
   * @param aName
   *        The name.
   * @return The qualified name.
   */
  @NonNull
  public XdmQName qualifyForScope (@Nullable final Object aScope, @NonNull final XdmQName aName)
  {
    final XslModule aModule = getUnitForScope (aScope).getPackageModule ();
    return aModule == null ? aName : _qualify (aModule.getPackageQualifier (), aName);
  }

  /** @return All packages, the principal one first. */
  @NonNull
  public ICommonsList <PackageUnit> getUnits ()
  {
    return m_aUnits;
  }

  /** @return The principal package. */
  @NonNull
  public PackageUnit getPrincipal ()
  {
    return m_aUnits.getFirstOrNull ();
  }

  /** @return <code>true</code> if more than one package takes part. */
  public boolean isMultiPackage ()
  {
    return m_aUnits.size () > 1;
  }

  /**
   * @param aNode
   *        A stylesheet node.
   * @return The package it is written in.
   */
  @NonNull
  public PackageUnit getUnit (@NonNull final XslNode aNode)
  {
    final PackageUnit ret = m_aByModule.get (aNode.getModule ());
    return ret != null ? ret : getPrincipal ();
  }

  /**
   * @param aNode
   *        A stylesheet node.
   * @return <code>true</code> if it is written in the principal package.
   */
  public boolean isInPrincipal (@NonNull final XslNode aNode)
  {
    return getUnit (aNode).isPrincipal ();
  }

  /**
   * @param aNode
   *        A stylesheet node.
   * @param sSymbolicName
   *        The symbolic name of a component.
   * @return <code>true</code> if the code at the node cannot see that
   *         component because a used package keeps it to itself.
   */
  public boolean isHidden (@NonNull final XslNode aNode, @NonNull final String sSymbolicName)
  {
    return isMultiPackage () && getUnit (aNode).hidden ().contains (sSymbolicName);
  }

  /**
   * @param sSymbolicName
   *        The symbolic name of a component of a used package.
   * @return <code>true</code> if the principal package accepted it with an
   *         explicit xsl:accept.
   */
  public boolean isAcceptedExplicitly (@NonNull final String sSymbolicName)
  {
    return m_aAcceptedExplicit.contains (sSymbolicName);
  }

  /**
   * @param aEl
   *        A declaration.
   * @return <code>true</code> if it is a child of xsl:override.
   */
  public static boolean isOverride (@NonNull final XslElement aEl)
  {
    return aEl.getParent () != null && aEl.getParent ().isXsl ("override");
  }

  /**
   * @param aUnit
   *        A package.
   * @return The top-level elements of all its non-simplified modules (the
   *         package module and the modules it includes or imports), in module
   *         order.
   */
  @NonNull
  private static ICommonsList <XslElement> _getTopLevelElements (@NonNull final PackageUnit aUnit)
  {
    final ICommonsList <XslElement> ret = new CommonsArrayList <> ();
    for (final XslModule aModule : aUnit.modules ())
      if (aModule.getRoot () != null && !aModule.isSimplified ())
        ret.addAll (aModule.getRoot ().getElementChildren ());
    return ret;
  }

  /**
   * @param aRoot
   *        A package root.
   * @return The exposure of the package's xsl:expose elements (computed
   *         once per root and cached), or <code>null</code> for an
   *         xsl:stylesheet root or an xsl:package without xsl:expose.
   * @throws XsltException
   *         The static errors of {@link PackageChecks#getExposure(XslElement, Iterable)}.
   */
  public PackageChecks.@Nullable Exposure getExposure (@NonNull final XslElement aRoot)
  {
    if (!m_aExposures.containsKey (aRoot))
      m_aExposures.put (aRoot,
                        aRoot.isXsl ("package") ? PackageChecks.getExposure (aRoot, _getTopLevelElements (getUnit (aRoot))) : null);
    return m_aExposures.get (aRoot);
  }

  /**
   * The components a package exposes to a using package: its own public,
   * final and abstract components (after xsl:expose), declared in any of its
   * modules (of several declarations with one symbolic name the one with the
   * highest import precedence), plus those it accepts from the packages it
   * uses with an exposed visibility (under the visibility of that
   * xsl:accept). xsl:param counts as a public variable. The result is cached
   * by package key, so two uses of one package share it.
   *
   * @param aUnit
   *        The package.
   * @return The manifest; empty while the package module is not loaded.
   */
  @NonNull
  public ICommonsList <PackageComponent> getExports (@NonNull final PackageUnit aUnit)
  {
    final ICommonsList <PackageComponent> aCached = m_aExports.get (aUnit.getKey ());
    if (aCached != null)
      return aCached;
    final XslElement aRoot = aUnit.getRoot ();
    final ICommonsList <PackageComponent> ret = new CommonsArrayList <> ();
    if (aRoot != null)
    {
      final PackageChecks.Exposure aExposure = getExposure (aRoot);
      final ICommonsMap <String, XslElement> aOwnWinners = new CommonsHashMap <> ();
      final ICommonsList <XslElement> aAll = _getTopLevelElements (aUnit);
      for (final XslElement aChild : aAll)
      {
        final PackageComponent aComp = PackageChecks.getComponent (aChild);
        if (aComp != null)
        {
          final XslElement aPrev = aOwnWinners.get (aComp.getSymbolicName ());
          if (aPrev == null || aPrev.getPrecedence () < aChild.getPrecedence ())
            aOwnWinners.put (aComp.getSymbolicName (), aChild);
        }
      }
      for (final XslElement aChild : aAll)
      {
        if (aChild.isXsl ("use-package"))
        {
          PackageUnit aUsed = null;
          for (final PackageUnit.Use aUse : aUnit.uses ())
            if (aUse.element () == aChild)
              aUsed = aUse.used ();
          if (aUsed == null)
            continue;
          final ICommonsList <PackageComponent> aSub = getExports (aUsed);
          for (final XslElement aAccept : aChild.getXslChildren ("accept"))
          {
            final String sVis = aAccept.getAttr ("visibility") == null ? "" : aAccept.getAttr ("visibility").trim ();
            if (!PackageChecks.isExposed (sVis))
              continue;
            for (final PackageComponent aComp : aSub)
              if (PackageChecks.acceptMatches (aAccept, aComp))
                ret.add (aComp.withVisibility (sVis));
          }
          continue;
        }
        PackageComponent aComp = PackageChecks.getComponent (aChild);
        if (aComp == null || aOwnWinners.get (aComp.getSymbolicName ()) != aChild)
          continue;
        if (aExposure != null && aExposure.byElement ().containsKey (aChild))
          aComp = aComp.withVisibility (aExposure.byElement ().get (aChild));
        if (PackageChecks.isExposed (aComp.visibility ()))
          ret.add (aComp);
      }
    }
    m_aExports.put (aUnit.getKey (), ret);
    return ret;
  }

  /**
   * Work out what one xsl:use-package hides from and shows to the using
   * package. An exported component is visible unless its accepted visibility
   * is "hidden" (see {@link PackageChecks#getAcceptedVisibility(XslElement,
   * PackageComponent)}). Hidden as well are: the used package's own
   * declarations it does not export (except xsl:param and the unnamed mode);
   * what it accepts from its own used packages without exporting it, unless
   * an exposed xsl:override in that use replaces it (xsl:param excepted); and
   * whatever is already hidden from the used package and not exported by it.
   *
   * @param aUse
   *        The xsl:use-package with the unit it loaded.
   * @return The sets for this use.
   */
  @NonNull
  private UsedVisibility _getUsedVisibility (final PackageUnit.@NonNull Use aUse)
  {
    final ICommonsList <PackageComponent> aExports = getExports (aUse.used ());
    final UsedVisibility ret = new UsedVisibility (new CommonsHashSet <> (),
                                                   new CommonsHashSet <> (),
                                                   new CommonsHashSet <> (),
                                                   new CommonsHashSet <> ());
    final ICommonsSet <String> aExported = new CommonsHashSet <> ();
    for (final PackageComponent aComp : aExports)
    {
      final String sSym = aComp.getSymbolicName ();
      aExported.add (sSym);
      ret.visible ().add (sSym);
      if (PackageChecks.isAcceptStated (aUse.element (), aComp))
        ret.stated ().add (sSym);
      final String sFinal = PackageChecks.getAcceptedVisibility (aUse.element (), aComp);
      if ("abstract".equals (sFinal))
        ret.abstracts ().add (sSym);
      if ("hidden".equals (sFinal))
      {
        ret.visible ().remove (sSym);
        ret.hidden ().add (sSym);
      }
    }
    // Everything the used package declares in any module but does not export
    // is hidden
    for (final XslElement aChild : _getTopLevelElements (aUse.used ()))
    {
      if (aChild.isXsl ("param"))
        continue;
      final PackageComponent aComp = PackageChecks.getComponent (aChild);
      if (aComp == null || ("mode".equals (aComp.kind ()) && aComp.name () == null))
        continue;
      if (!aExported.contains (aComp.getSymbolicName ()))
        ret.hidden ().add (aComp.getSymbolicName ());
    }
    // So is what the used package accepts from its own used packages without
    // exposing it (section 3.5.3.2: accepted as private there), and what is
    // hidden from the used package itself
    // (an overriding component with an exposed visibility replaces it)
    for (final PackageUnit.Use aSubUse : aUse.used ().uses ())
    {
      final ICommonsSet <String> aOverridden = new CommonsHashSet <> ();
      for (final XslElement aOverride : aSubUse.element ().getXslChildren ("override"))
        for (final XslElement aDecl : aOverride.getElementChildren ())
        {
          final PackageComponent aComp = PackageChecks.getComponent (aDecl);
          if (aComp != null && PackageChecks.isExposed (aComp.visibility ()))
            aOverridden.add (aComp.getSymbolicName ());
        }
      for (final PackageComponent aComp : getExports (aSubUse.used ()))
        if (!"variable".equals (aComp.kind ()) || aComp.element () == null || !aComp.element ().isXsl ("param"))
          if (!aExported.contains (aComp.getSymbolicName ()) && !aOverridden.contains (aComp.getSymbolicName ()))
            ret.hidden ().add (aComp.getSymbolicName ());
    }
    for (final String sSym : aUse.used ().hidden ())
      if (!aExported.contains (sSym))
        ret.hidden ().add (sSym);
    return ret;
  }

  /**
   * A component of a used package that a using package accepts as hidden
   * while another package it uses makes a homonym visible: a reference in the
   * using package means the visible one (section 3.5.3.2), so this one must
   * not take the place of the flattened component.
   *
   * @param aDecl
   *        The declaration of the component.
   * @param aComp
   *        The component.
   * @return <code>true</code> if a visible homonym wins.
   */
  public boolean isShadowedByVisibleHomonym (@NonNull final XslElement aDecl, @NonNull final PackageComponent aComp)
  {
    if (!isMultiPackage ())
      return false;
    final PackageUnit aOwner = getUnit (aDecl);
    final String sSym = aComp.getSymbolicName ();
    for (final PackageUnit aUser : m_aUnits)
      for (final PackageUnit.Use aUse : aUser.uses ())
        if (aUse.used () == aOwner && "hidden".equals (PackageChecks.getAcceptedVisibility (aUse.element (), aComp)))
          for (final PackageUnit.Use aOther : aUser.uses ())
            if (aOther.used () != aOwner)
              for (final PackageComponent aExported : getExports (aOther.used ()))
                if (aExported.getSymbolicName ().equals (sSym) &&
                    !"hidden".equals (PackageChecks.getAcceptedVisibility (aOther.element (), aExported)))
                  return true;
    return false;
  }

  /**
   * @param aUnit
   *        A package.
   * @return The top-level elements of all its non-simplified modules, with
   *         every xsl:use-package replaced by the declarations of its
   *         xsl:override children.
   */
  @NonNull
  private static ICommonsList <XslElement> _getOwnDeclarations (@NonNull final PackageUnit aUnit)
  {
    final ICommonsList <XslElement> ret = new CommonsArrayList <> ();
    for (final XslModule aModule : aUnit.modules ())
      if (aModule.getRoot () != null && !aModule.isSimplified ())
        for (final XslElement aChild : aModule.getRoot ().getElementChildren ())
        {
          if (aChild.isXsl ("use-package"))
          {
            for (final XslElement aOverride : aChild.getXslChildren ("override"))
              ret.addAll (aOverride.getElementChildren ());
            continue;
          }
          ret.add (aChild);
        }
    return ret;
  }

  /**
   * Apply the package-level rules for one using package and record what it
   * cannot see. For the principal package: every component accepted as
   * abstract must be overridden by an own declaration (XTSE3080), and the
   * symbolic names some xsl:accept stated are remembered for
   * {@link #isAcceptedExplicitly(String)}. For every package: a declaration
   * outside xsl:override (xsl:param excepted) with the symbolic name of a
   * visible accepted component, or a template rule naming a visible accepted
   * mode, is XTSE3050. Finally every name hidden by one use is added to
   * {@link PackageUnit#hidden()}, unless another use makes it visible or the
   * package declares it itself.
   *
   * @param aUnit
   *        The using package.
   * @param aInfos
   *        The result of {@link #_getUsedVisibility(PackageUnit.Use)} for
   *        each of its uses.
   * @throws XsltException
   *         XTSE3080 or XTSE3050 as described.
   */
  private void _finishHidden (@NonNull final PackageUnit aUnit, @NonNull final ICommonsList <UsedVisibility> aInfos)
  {
    final ICommonsList <XslElement> aOwn = _getOwnDeclarations (aUnit);
    final ICommonsSet <String> aOwnDeclared = new CommonsHashSet <> ();
    for (final XslElement aEl : aOwn)
    {
      final PackageComponent aComp = PackageChecks.getComponent (aEl);
      if (aComp != null)
        aOwnDeclared.add (aComp.getSymbolicName ());
    }
    final ICommonsSet <String> aVisible = new CommonsHashSet <> ();
    for (final UsedVisibility aInfo : aInfos)
      aVisible.addAll (aInfo.visible ());
    if (aUnit.isPrincipal ())
    {
      // XTSE3080: a top-level package cannot contain an abstract component
      for (final UsedVisibility aInfo : aInfos)
        for (final String sSym : aInfo.abstracts ())
          if (!aOwnDeclared.contains (sSym))
            throw XsltException.at (aUnit.getRoot (), "XTSE3080", "A top-level package cannot contain the abstract component " + sSym);
      for (final UsedVisibility aInfo : aInfos)
        m_aAcceptedExplicit.addAll (aInfo.stated ());
    }
    // XTSE3050: an accepted component may only be replaced with xsl:override
    for (final XslElement aEl : aOwn)
    {
      if (isOverride (aEl))
        continue;
      final PackageComponent aComp = PackageChecks.getComponent (aEl);
      if (aComp != null && !aEl.isXsl ("param") && aVisible.contains (aComp.getSymbolicName ()))
        throw XsltException.at (aEl,
                                "XTSE3050",
                                "The " + aComp.getLabel () + " is accepted from a used package and cannot be redeclared outside xsl:override");
      if (aEl.isXsl ("template") && aEl.hasAttr ("match") && aEl.getAttr ("mode") != null)
        for (final String sToken : XsltNames.tokenize (aEl.getAttr ("mode")))
        {
          if (sToken.startsWith ("#"))
            continue;
          final XdmQName aMode = aEl.resolveQName (sToken, false);
          if (aMode != null && aVisible.contains (PackageComponent.getSymbolicName ("mode", aMode, 0)))
            throw XsltException.at (aEl,
                                    "XTSE3050",
                                    "The mode '" + sToken + "' comes from a used package; template rules are added to it with xsl:override");
        }
    }
    for (final UsedVisibility aInfo : aInfos)
      for (final String sSym : aInfo.hidden ())
        if (!aVisible.contains (sSym) && !aOwnDeclared.contains (sSym))
          aUnit.hidden ().add (sSym);
  }

  /**
   * Apply the static rules of xsl:use-package, xsl:accept and xsl:override to
   * every package that uses others, and compute for each the used-package
   * components its code cannot see ({@link PackageUnit#hidden()}). Units are
   * processed in reverse creation order, so a used package (loaded after its
   * user) is finished before the package using it.
   *
   * @throws XsltException
   *         The static errors of
   *         {@link PackageChecks#checkUsePackage(XslElement, String, ICommonsList, ICommonsMap)},
   *         XTSE3080 and XTSE3050.
   */
  public void analyse ()
  {
    // Used packages first, so that their own hiding is known
    for (int i = m_aUnits.size () - 1; i >= 0; --i)
    {
      final PackageUnit aUnit = m_aUnits.get (i);
      if (aUnit.uses ().isEmpty ())
        continue;
      final ICommonsMap <String, String> aAccepted = new CommonsHashMap <> ();
      final ICommonsList <UsedVisibility> aInfos = new CommonsArrayList <> ();
      for (final PackageUnit.Use aUse : aUnit.uses ())
      {
        PackageChecks.checkUsePackage (aUse.element (), aUse.used ().getKey (), getExports (aUse.used ()), aAccepted);
        aInfos.add (_getUsedVisibility (aUse));
      }
      _finishHidden (aUnit, aInfos);
    }
  }
}
