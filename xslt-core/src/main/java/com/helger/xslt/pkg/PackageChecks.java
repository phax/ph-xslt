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

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.StylesheetCompiler;
import com.helger.xslt.model.Mode;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XsltNames;

/**
 * The static rules of packages that work on the declarations themselves
 * (XSLT 3.0 section 3.5): the component model of a package, xsl:expose,
 * xsl:accept and xsl:override. Execution is flattened (a used package's
 * modules join the module list below the using package), so these rules need
 * their own view of which components a package declares and exposes.
 *
 * @author Philip Helger
 */
@Immutable
public final class PackageChecks
{
  /** The result of xsl:expose for one package. */
  public record Exposure (@NonNull ICommonsMap <XslElement, String> byElement,
                          @NonNull ICommonsMap <XdmQName, String> implicitModes)
  {}

  private record ExposeRule (@NonNull XslElement element,
                             @NonNull String kind,
                             @NonNull String visibility,
                             @NonNull ICommonsList <String> tokens)
  {}

  private static final ICommonsSet <String> COMPONENT_DECLS = new CommonsHashSet <> ("template",
                                                                                     "function",
                                                                                     "variable",
                                                                                     "param",
                                                                                     "attribute-set",
                                                                                     "mode");

  @PresentForCodeCoverage
  private static final PackageChecks INSTANCE = new PackageChecks ();

  private PackageChecks ()
  {}

  @NonNull
  private static String _attr (@NonNull final XslElement aEl, @NonNull final String sName, @NonNull final String sDefault)
  {
    final String s = aEl.getAttr (sName);
    return s == null ? sDefault : s;
  }

  /**
   * @param aEl
   *        A declaration.
   * @return Its declared visibility, "private" if none.
   */
  @NonNull
  public static String getDeclaredVisibility (@NonNull final XslElement aEl)
  {
    final String s = aEl.getAttr ("visibility");
    if (s != null && !s.trim ().isEmpty ())
      return s.trim ();
    return "private";
  }

  /**
   * @param aEl
   *        A top-level element.
   * @return The component it declares or <code>null</code> (a template rule,
   *         a non-component declaration).
   */
  @Nullable
  public static PackageComponent getComponent (@NonNull final XslElement aEl)
  {
    if (!aEl.isXsl () || !COMPONENT_DECLS.contains (aEl.getLocalName ()))
      return null;
    final String sLocal = aEl.getLocalName ();
    final String sKind = "param".equals (sLocal) ? "variable" : sLocal;
    final String sName = aEl.getAttr ("name");
    XdmQName aName = null;
    if (sName == null)
    {
      if (!"mode".equals (sLocal))
        return null;
    }
    else
    {
      aName = aEl.resolveQName (sName.trim (), false);
      if (aName == null)
        return null;
    }
    int nArity = 0;
    if ("function".equals (sKind))
      nArity = aEl.getXslChildren ("param").size ();
    String sVis = getDeclaredVisibility (aEl);
    if ("mode".equals (sLocal) && aName == null)
      sVis = "private";
    if ("param".equals (sLocal))
      sVis = "public";
    return new PackageComponent (sKind, aName, nArity, sVis, aEl);
  }

  /**
   * @param sVisibility
   *        A visibility.
   * @return <code>true</code> if a component with it is part of the package
   *         manifest seen by a using package.
   */
  public static boolean isExposed (@NonNull final String sVisibility)
  {
    return "public".equals (sVisibility) || "final".equals (sVisibility) || "abstract".equals (sVisibility);
  }

  private static boolean _isWildcardToken (@NonNull final String sToken)
  {
    return sToken.indexOf ('*') >= 0;
  }

  private static int _parseArity (@NonNull final String s)
  {
    if (s.isEmpty () || s.length () > 9)
      return -1;
    for (int i = 0; i < s.length (); ++i)
      if (s.charAt (i) < '0' || s.charAt (i) > '9')
        return -1;
    return Integer.parseInt (s);
  }

  /**
   * Match one token of xsl:accept/@names or xsl:expose/@names: "*",
   * "prefix:*", "*:local", "Q{uri}*", an EQName, and for a function an exact
   * name followed by "#" and the arity.
   *
   * @param aEl
   *        The element carrying the token (namespaces).
   * @param sTokenIn
   *        The token.
   * @param aComp
   *        The component.
   * @return <code>true</code> if the token selects the component.
   */
  public static boolean tokenMatches (@NonNull final XslElement aEl,
                                      @NonNull final String sTokenIn,
                                      @NonNull final PackageComponent aComp)
  {
    String sToken = sTokenIn;
    final XdmQName aName = aComp.name ();
    if ("function".equals (aComp.kind ()))
    {
      final int nHash = sToken.lastIndexOf ('#');
      if (nHash >= 0)
      {
        final int nArity = _parseArity (sToken.substring (nHash + 1));
        if (nArity >= 0)
        {
          if (nArity != aComp.arity ())
            return false;
          sToken = sToken.substring (0, nHash);
        }
      }
    }
    if ("*".equals (sToken))
      return true;
    if (aName == null)
      return false;
    if (sToken.startsWith ("Q{") && sToken.endsWith ("}*"))
      return aName.namespaceUri ().equals (sToken.substring (2, sToken.length () - 2).trim ());
    if (sToken.endsWith (":*"))
    {
      final String sUri = aEl.lookupNamespaceUri (sToken.substring (0, sToken.length () - 2));
      return sUri != null && aName.namespaceUri ().equals (sUri);
    }
    if (sToken.startsWith ("*:"))
      return aName.localName ().equals (sToken.substring (2));
    final XdmQName aTok = aEl.resolveQName (sToken, false);
    return aTok != null && aTok.equals (aName);
  }

  private static boolean _kindMatches (@NonNull final XslElement aAccept, @NonNull final PackageComponent aComp)
  {
    final String sKinds = _attr (aAccept, "component", "").trim ();
    if ("*".equals (sKinds))
      return true;
    for (final String sKind : XsltNames.tokenize (sKinds))
      if (sKind.equals (aComp.kind ()) || "*".equals (sKind))
        return true;
    return false;
  }

  /**
   * @param aAccept
   *        An xsl:accept element.
   * @param aComp
   *        A component.
   * @return <code>true</code> if the xsl:accept selects the component.
   */
  public static boolean acceptMatches (@NonNull final XslElement aAccept, @NonNull final PackageComponent aComp)
  {
    if (!_kindMatches (aAccept, aComp))
      return false;
    for (final String sToken : XsltNames.tokenize (_attr (aAccept, "names", "")))
      if (tokenMatches (aAccept, sToken, aComp))
        return true;
    return false;
  }

  /**
   * The visibility a used package's exported component ends up with under the
   * xsl:accept children of an xsl:use-package: an accept naming the component
   * explicitly beats a partial wildcard, which beats "*", and the last of
   * equals wins. An abstract component may only be accepted as abstract;
   * otherwise it becomes hidden.
   *
   * @param aUse
   *        The xsl:use-package element.
   * @param aComp
   *        The exported component.
   * @return The accepted visibility, "" if no xsl:accept applies.
   */
  @NonNull
  public static String getAcceptedVisibility (@NonNull final XslElement aUse, @NonNull final PackageComponent aComp)
  {
    String sExplicit = "";
    String sPartial = "";
    String sAll = "";
    for (final XslElement aAccept : aUse.getXslChildren ("accept"))
    {
      final String sVis = _attr (aAccept, "visibility", "").trim ();
      if (!acceptMatches (aAccept, aComp))
        continue;
      int nClass = 2;
      for (final String sToken : XsltNames.tokenize (_attr (aAccept, "names", "")))
      {
        if (!tokenMatches (aAccept, sToken, aComp))
          continue;
        if ("*".equals (sToken))
          continue;
        if (_isWildcardToken (sToken))
          nClass = Math.min (nClass, 1);
        else
          nClass = 0;
      }
      if (nClass == 0)
        sExplicit = sVis;
      else
        if (nClass == 1)
          sPartial = sVis;
        else
          sAll = sVis;
    }
    String ret = sExplicit;
    if (ret.isEmpty ())
      ret = sPartial;
    if (ret.isEmpty ())
      ret = sAll;
    if ("abstract".equals (aComp.visibility ()) && !"abstract".equals (ret))
      return "hidden";
    return ret;
  }

  /**
   * @param aUse
   *        The xsl:use-package element.
   * @param aComp
   *        The exported component.
   * @return <code>true</code> if some xsl:accept states the component's
   *         visibility.
   */
  public static boolean isAcceptStated (@NonNull final XslElement aUse, @NonNull final PackageComponent aComp)
  {
    for (final XslElement aAccept : aUse.getXslChildren ("accept"))
      if (acceptMatches (aAccept, aComp))
        return true;
    return false;
  }

  private static boolean _isExposePermitted (@NonNull final String sExposed, @NonNull final String sDeclared)
  {
    return switch (sExposed)
    {
      case "public" -> "public".equals (sDeclared);
      case "private" -> "public".equals (sDeclared) || "private".equals (sDeclared) || "final".equals (sDeclared);
      case "final" -> "public".equals (sDeclared) || "final".equals (sDeclared);
      case "abstract" -> "abstract".equals (sDeclared);
      default -> false;
    };
  }

  private static boolean _exposeMatches (@NonNull final ExposeRule aRule,
                                         @NonNull final String sToken,
                                         @NonNull final PackageComponent aComp)
  {
    if (!"*".equals (aRule.kind ()) && !aRule.kind ().equals (aComp.kind ()))
      return false;
    final boolean bWildcard = _isWildcardToken (sToken);
    if (!bWildcard && "function".equals (aComp.kind ()) && sToken.indexOf ('#') < 0)
      return false;
    if (!bWildcard && !"function".equals (aComp.kind ()) && sToken.indexOf ('#') >= 0)
      return false;
    return tokenMatches (aRule.element (), sToken, aComp);
  }

  /**
   * The effective visibility of the components a package manifest declares
   * under its xsl:expose elements (XSLT 3.0 section 3.5.3.1): the last
   * xsl:expose naming the component explicitly, else its own visibility,
   * else the last partial wildcard, else the last "*", else private.
   *
   * @param aRoot
   *        The xsl:package element.
   * @return The exposure or <code>null</code> if the package has no
   *         xsl:expose.
   */
  @Nullable
  public static Exposure getExposure (@NonNull final XslElement aRoot)
  {
    final ICommonsList <XslElement> aExposes = new CommonsArrayList <> ();
    final ICommonsList <PackageComponent> aComps = new CommonsArrayList <> ();
    for (final XslElement aChild : aRoot.getElementChildren ())
    {
      if (!aChild.isXsl ())
        continue;
      if (aChild.isXsl ("expose"))
      {
        aExposes.add (aChild);
        continue;
      }
      final PackageComponent aComp = getComponent (aChild);
      if (aComp == null || aChild.isXsl ("param"))
        continue;
      if ("mode".equals (aComp.kind ()) && aComp.name () == null)
        continue;
      aComps.add (aComp);
    }
    if (aExposes.isEmpty ())
      return null;
    // Modes used only by template rules are components too
    final ICommonsSet <XdmQName> aDeclaredModes = new CommonsHashSet <> ();
    for (final PackageComponent aComp : aComps)
      if ("mode".equals (aComp.kind ()))
        aDeclaredModes.add (aComp.name ());
    final ICommonsSet <XdmQName> aImplicit = new CommonsHashSet <> ();
    for (final XslElement aChild : aRoot.getXslChildren ("template"))
    {
      final String sMode = aChild.getAttr ("mode");
      if (sMode == null)
        continue;
      for (final String sToken : XsltNames.tokenize (sMode))
      {
        if (sToken.startsWith ("#"))
          continue;
        final XdmQName aName = aChild.resolveQName (sToken, false);
        if (aName != null && !aDeclaredModes.contains (aName) && aImplicit.add (aName))
          aComps.add (new PackageComponent ("mode", aName, 0, "private", null));
      }
    }

    final ICommonsList <ExposeRule> aRules = new CommonsArrayList <> ();
    for (final XslElement aEx : aExposes)
    {
      final String sKind = _attr (aEx, "component", "").trim ();
      final String sVis = _attr (aEx, "visibility", "").trim ();
      switch (sKind)
      {
        case "template", "function", "attribute-set", "variable", "mode", "*":
          break;
        default:
          throw XsltException.at (aEx, "XTSE0020", "component='" + sKind + "' is not allowed on xsl:expose");
      }
      switch (sVis)
      {
        case "public", "private", "final", "abstract":
          break;
        default:
          throw XsltException.at (aEx, "XTSE0020", "visibility='" + sVis + "' is not allowed on xsl:expose");
      }
      final ICommonsList <String> aTokens = new CommonsArrayList <> ();
      for (final String sToken : XsltNames.tokenize (_attr (aEx, "names", "")))
      {
        if (sToken.startsWith ("#"))
          throw XsltException.at (aEx, "XTSE0020", "'" + sToken + "' is not a valid name in xsl:expose/@names");
        final int nColon = sToken.indexOf (':');
        if (nColon > 0 && !sToken.startsWith ("Q{") && !sToken.startsWith ("*:"))
          if (aEx.lookupNamespaceUri (sToken.substring (0, nColon)) == null)
            throw XsltException.at (aEx, "XTSE0020", "The prefix of '" + sToken + "' in xsl:expose/@names is not bound");
        if ("*".equals (sKind) && !_isWildcardToken (sToken))
          throw XsltException.at (aEx, "XTSE3022", "xsl:expose with component='*' requires a wildcard name, not '" + sToken + "'");
        aTokens.add (sToken);
      }
      aRules.add (new ExposeRule (aEx, sKind, sVis, aTokens));
    }

    // XTSE3020: an explicit name must select a component
    for (final ExposeRule aRule : aRules)
      for (final String sToken : aRule.tokens ())
      {
        if (_isWildcardToken (sToken))
          continue;
        boolean bFound = false;
        for (final PackageComponent aComp : aComps)
          if (_exposeMatches (aRule, sToken, aComp))
          {
            bFound = true;
            break;
          }
        if (!bFound)
          throw XsltException.at (aRule.element (),
                                  "XTSE3020",
                                  "xsl:expose names '" + sToken + "', which matches no " + ("*".equals (aRule.kind ()) ? "component" : aRule.kind ()) + " in the package");
      }

    final ICommonsMap <XslElement, String> aByElement = new CommonsHashMap <> ();
    final ICommonsMap <XdmQName, String> aImplicitOut = new CommonsHashMap <> ();
    for (final PackageComponent aComp : aComps)
    {
      String sExplicit = "";
      String sPartial = "";
      String sAll = "";
      for (final ExposeRule aRule : aRules)
        for (final String sToken : aRule.tokens ())
        {
          if (!_exposeMatches (aRule, sToken, aComp))
            continue;
          if ("*".equals (sToken))
            sAll = aRule.visibility ();
          else
            if (_isWildcardToken (sToken))
              sPartial = aRule.visibility ();
            else
              sExplicit = aRule.visibility ();
        }
      final XslElement aDecl = aComp.element ();
      final String sDeclared = aDecl == null || aDecl.getAttr ("visibility") == null ? "" : aDecl.getAttr ("visibility").trim ();
      if (!sExplicit.isEmpty () && !sDeclared.isEmpty () && !_isExposePermitted (sExplicit, sDeclared))
        throw XsltException.at (aDecl,
                                "XTSE3010",
                                "xsl:expose makes the " + aComp.getLabel () + " " + sExplicit + ", which is inconsistent with its declared visibility '" + sDeclared + "'");
      String sVis = "private";
      if (!sExplicit.isEmpty ())
        sVis = sExplicit;
      else
        if (!sDeclared.isEmpty ())
          sVis = sDeclared;
        else
          if (!sPartial.isEmpty ())
            sVis = sPartial;
          else
            if (!sAll.isEmpty ())
              sVis = sAll;
      if ("abstract".equals (sVis) && !"abstract".equals (sDeclared))
        throw XsltException.at (aDecl != null ? aDecl : aRoot,
                                "XTSE3025",
                                "xsl:expose cannot make the " + aComp.getLabel () + " abstract; it was not declared with visibility='abstract'");
      if (aDecl == null)
        aImplicitOut.put (aComp.name (), sVis);
      else
        aByElement.put (aDecl, sVis);
    }
    return new Exposure (aByElement, aImplicitOut);
  }

  /**
   * @param sDeclared
   *        Visibility of a component in the used package.
   * @param sAccepted
   *        The visibility of xsl:accept.
   * @return <code>true</code> if the combination is allowed (XTSE3040).
   */
  private static boolean _isVisibilityCompatible (@NonNull final String sDeclared, @NonNull final String sAccepted)
  {
    if (sAccepted.isEmpty ())
      return true;
    return switch (sDeclared)
    {
      case "public", "abstract" -> "public".equals (sAccepted) ||
                                   "private".equals (sAccepted) ||
                                   "final".equals (sAccepted) ||
                                   "abstract".equals (sAccepted) ||
                                   "hidden".equals (sAccepted);
      case "final" -> "private".equals (sAccepted) || "final".equals (sAccepted) || "hidden".equals (sAccepted);
      default -> false;
    };
  }

  private static void _checkNoApplyImports (@NonNull final XslElement aDecl)
  {
    final ICommonsList <XslElement> aStack = new CommonsArrayList <> (aDecl.getElementChildren ());
    while (aStack.isNotEmpty ())
    {
      final XslElement aCur = aStack.removeLast ();
      if (aCur.isXsl ("apply-imports"))
        throw XsltException.at (aCur, "XTSE3460", "xsl:apply-imports is not allowed in a template rule declared in xsl:override");
      aStack.addAll (aCur.getElementChildren ());
    }
  }

  private static void _checkOverrideRuleMode (@NonNull final XslElement aDecl)
  {
    final String sMode = aDecl.getAttr ("mode");
    final boolean bDefaultUnnamed = Mode.UNNAMED.equals (StylesheetCompiler.getDefaultMode (aDecl));
    if (sMode == null)
    {
      if (bDefaultUnnamed)
        throw XsltException.at (aDecl, "XTSE3440", "A template rule in xsl:override must specify a named mode");
      return;
    }
    for (final String sToken : XsltNames.tokenize (sMode))
      switch (sToken)
      {
        case "#all", "#unnamed":
          throw XsltException.at (aDecl, "XTSE3440", "mode='" + sToken + "' is not allowed on a template rule in xsl:override");
        case "#default":
          if (bDefaultUnnamed)
            throw XsltException.at (aDecl, "XTSE3440", "mode='#default' in xsl:override resolves to the unnamed mode");
          break;
        default:
          break;
      }
  }

  @NonNull
  private static String _normType (@Nullable final String s)
  {
    if (s == null)
      return "";
    return String.join (" ", XsltNames.tokenize (s));
  }

  @NonNull
  private static ICommonsList <String> _typeNameTokens (@NonNull final String sType)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    int i = 0;
    final int n = sType.length ();
    while (i < n)
    {
      final char c = sType.charAt (i);
      if (c == '_' || Character.isLetter (c))
      {
        int j = i;
        while (j < n)
        {
          final char d = sType.charAt (j);
          if (d == '_' || d == '-' || d == '.' || d == ':' || Character.isLetterOrDigit (d))
            j++;
          else
            break;
        }
        if (j >= n || sType.charAt (j) != '(')
          ret.add (sType.substring (i, j));
        i = j;
        continue;
      }
      i++;
    }
    return ret;
  }

  private static boolean _bothBuiltinTypes (@NonNull final String sA, @NonNull final String sB)
  {
    for (final String sType : new String [] { sA, sB })
      for (final String sTok : _typeNameTokens (sType))
        if (!sTok.startsWith ("xs:"))
          return false;
    return true;
  }

  @Nullable
  private static XslElement _findSimpleType (@NonNull final XslElement aStart, @NonNull final String sLocal)
  {
    XslElement aTop = aStart;
    while (aTop.getParent () != null)
      aTop = aTop.getParent ();
    final ICommonsList <XslElement> aStack = new CommonsArrayList <> (aTop);
    while (aStack.isNotEmpty ())
    {
      final XslElement aCur = aStack.removeLast ();
      for (final XslElement aChild : aCur.getElementChildren ())
      {
        if (XdmConstants.NS_XS.equals (aChild.getName ().namespaceUri ()) &&
            "simpleType".equals (aChild.getLocalName ()) &&
            sLocal.equals (aChild.getAttr ("name")))
          return aChild;
        if ("import-schema".equals (aChild.getLocalName ()) || "schema".equals (aChild.getLocalName ()))
          aStack.add (aChild);
      }
    }
    return null;
  }

  @Nullable
  private static ICommonsList <String> _inlineUnionMembers (@NonNull final XslElement aEl,
                                                            @NonNull final String sName,
                                                            final int nDepth)
  {
    if (nDepth > 8)
      return null;
    final int nColon = sName.indexOf (':');
    final XslElement aDecl = _findSimpleType (aEl, nColon >= 0 ? sName.substring (nColon + 1) : sName);
    if (aDecl == null)
      return null;
    for (final XslElement aChild : aDecl.getElementChildren ())
      if (XdmConstants.NS_XS.equals (aChild.getName ().namespaceUri ()) && "union".equals (aChild.getLocalName ()))
      {
        final ICommonsList <String> ret = new CommonsArrayList <> ();
        for (final String sMember : XsltNames.tokenize (_attr (aChild, "memberTypes", "")))
        {
          if (sMember.startsWith ("xs:"))
          {
            ret.add (sMember);
            continue;
          }
          final ICommonsList <String> aSub = _inlineUnionMembers (aEl, sMember, nDepth + 1);
          if (aSub == null)
            return null;
          ret.addAll (aSub);
        }
        return ret.isEmpty () ? null : ret;
      }
    return null;
  }

  @Nullable
  private static String _resolveTypeSet (@NonNull final XslElement aEl, @NonNull final String sTypeIn)
  {
    String sType = sTypeIn.trim ();
    String sOcc = "";
    if (!sType.isEmpty () && "?*+".indexOf (sType.charAt (sType.length () - 1)) >= 0)
    {
      sOcc = sType.substring (sType.length () - 1);
      sType = sType.substring (0, sType.length () - 1).trim ();
    }
    if (sType.startsWith ("xs:"))
      return sType + sOcc;
    for (final char c : "() |,".toCharArray ())
      if (sType.indexOf (c) >= 0)
        return null;
    final ICommonsList <String> aMembers = _inlineUnionMembers (aEl, sType, 0);
    if (aMembers == null)
      return null;
    aMembers.sort (String::compareTo);
    return "union(" + String.join (",", aMembers) + ")" + sOcc;
  }

  private static boolean _typesDiffer (@NonNull final XslElement aA,
                                       @NonNull final String sA,
                                       @NonNull final XslElement aB,
                                       @NonNull final String sB)
  {
    if (_bothBuiltinTypes (sA, sB))
      return true;
    final String sRA = _resolveTypeSet (aA, sA);
    final String sRB = _resolveTypeSet (aB, sB);
    return sRA != null && sRB != null && !sRA.equals (sRB);
  }

  private static boolean _typesSame (@NonNull final XslElement aA,
                                     @NonNull final String sA,
                                     @NonNull final XslElement aB,
                                     @NonNull final String sB)
  {
    if (_bothBuiltinTypes (sA, sB))
      return false;
    final String sRA = _resolveTypeSet (aA, sA);
    final String sRB = _resolveTypeSet (aB, sB);
    return sRA != null && sRB != null && sRA.equals (sRB);
  }

  @NonNull
  private static String _newEachTime (@NonNull final XslElement aEl)
  {
    return switch (_attr (aEl, "new-each-time", "maybe").trim ())
    {
      case "yes", "true", "1" -> "yes";
      case "no", "false", "0" -> "no";
      default -> "maybe";
    };
  }

  private static boolean _flag (@NonNull final XslElement aEl, @NonNull final String sAttr)
  {
    final String s = aEl.getAttr (sAttr);
    return s != null && XsltNames.isXsltTrue (s.trim ());
  }

  @NonNull
  private static ICommonsMap <XdmQName, XslElement> _templateParams (@NonNull final XslElement aTemplate)
  {
    final ICommonsMap <XdmQName, XslElement> ret = new CommonsHashMap <> ();
    for (final XslElement aParam : aTemplate.getXslChildren ("param"))
    {
      final XdmQName aName = aParam.resolveQName (_attr (aParam, "name", ""), false);
      if (aName != null)
        ret.put (aName, aParam);
    }
    return ret;
  }

  @NonNull
  private static String _templateParamType (@NonNull final XslElement aParam)
  {
    final String s = aParam.getAttr ("as");
    return s == null ? "item()*" : _normType (s);
  }

  @NonNull
  private static String [] _contextItem (@NonNull final XslElement aTemplate)
  {
    String sUse = "optional";
    String sAs = "item()";
    for (final XslElement aCI : aTemplate.getXslChildren ("context-item"))
    {
      if (aCI.getAttr ("use") != null)
        sUse = aCI.getAttr ("use").trim ();
      if (aCI.getAttr ("as") != null)
        sAs = _normType (aCI.getAttr ("as"));
    }
    return new String [] { sUse, sAs.replace (" ", "") };
  }

  private static void _checkTemplateOverrideSignature (@NonNull final XslElement aDecl, @NonNull final PackageComponent aTarget)
  {
    final XslElement aOld = aTarget.element ();
    final String sName = aTarget.name () == null ? "" : aTarget.name ().getEQName ();
    final ICommonsMap <XdmQName, XslElement> aNewParams = _templateParams (aDecl);
    final ICommonsMap <XdmQName, XslElement> aOldParams = _templateParams (aOld);
    for (final Map.Entry <XdmQName, XslElement> aEntry : aOldParams.entrySet ())
    {
      final XslElement aOp = aEntry.getValue ();
      final XslElement aNp = aNewParams.get (aEntry.getKey ());
      if (aNp == null && _flag (aOp, "tunnel"))
        continue;
      if (aNp == null)
        throw XsltException.at (aDecl,
                                "XTSE3070",
                                "The overriding template " + sName + " lacks the parameter $" + aEntry.getKey ().getEQName () + " of the overridden template");
      if (_flag (aNp, "tunnel") != _flag (aOp, "tunnel"))
        throw XsltException.at (aNp, "XTSE3070", "Parameter $" + aEntry.getKey ().getEQName () + " of the overriding template differs in tunnel");
      if (!_templateParamType (aNp).equals (_templateParamType (aOp)))
        throw XsltException.at (aNp, "XTSE3070", "Parameter $" + aEntry.getKey ().getEQName () + " of the overriding template has a different type");
      if (_flag (aNp, "required") != _flag (aOp, "required"))
        throw XsltException.at (aNp, "XTSE3070", "Parameter $" + aEntry.getKey ().getEQName () + " of the overriding template differs in required");
    }
    for (final Map.Entry <XdmQName, XslElement> aEntry : aNewParams.entrySet ())
      if (!aOldParams.containsKey (aEntry.getKey ()) && _flag (aEntry.getValue (), "required"))
        throw XsltException.at (aEntry.getValue (), "XTSE3070", "The overriding template " + sName + " adds a required parameter");
    final String [] aNewCI = _contextItem (aDecl);
    final String [] aOldCI = _contextItem (aOld);
    if (!aNewCI[0].equals (aOldCI[0]) || !aNewCI[1].equals (aOldCI[1]))
      throw XsltException.at (aDecl, "XTSE3070", "The overriding template " + sName + " declares a different context item");
  }

  private static void _checkOverrideSignature (@NonNull final XslElement aDecl, @NonNull final PackageComponent aTarget)
  {
    final XslElement aOld = aTarget.element ();
    if (aOld == null)
      return;
    final String sA = _normType (aDecl.getAttr ("as"));
    final String sB = _normType (aOld.getAttr ("as"));
    if (!sA.equals (sB) && !sA.isEmpty () && !sB.isEmpty () && _typesDiffer (aDecl, sA, aOld, sB))
      throw XsltException.at (aDecl,
                              "XTSE3070",
                              "The overriding " + aTarget.getLabel () + " declares as='" + sA + "' but the overridden one declares as='" + sB + "'");
    if (aDecl.isXsl ("template"))
    {
      _checkTemplateOverrideSignature (aDecl, aTarget);
      return;
    }
    if (!aDecl.isXsl ("function"))
      return;
    if (!_newEachTime (aDecl).equals (_newEachTime (aOld)))
      throw XsltException.at (aDecl, "XTSE3070", "The overriding " + aTarget.getLabel () + " differs in new-each-time");
    final ICommonsList <XslElement> aPA = aDecl.getXslChildren ("param");
    final ICommonsList <XslElement> aPB = aOld.getXslChildren ("param");
    if (aPA.size () != aPB.size ())
      throw XsltException.at (aDecl, "XTSE3070", "The overriding " + aTarget.getLabel () + " has a different arity");
    for (int i = 0; i < aPA.size (); ++i)
    {
      final String sPA = _normType (aPA.get (i).getAttr ("as"));
      final String sPB = _normType (aPB.get (i).getAttr ("as"));
      if (!sPA.equals (sPB) && !sPA.isEmpty () && !sPB.isEmpty () && !_typesSame (aPA.get (i), sPA, aPB.get (i), sPB))
        throw XsltException.at (aDecl,
                                "XTSE3070",
                                "The overriding " + aTarget.getLabel () + " declares parameter " + (i + 1) + " as '" + sPA + "' but the overridden one as '" + sPB + "'");
    }
  }

  private static void _checkOverrideDecl (@NonNull final XslElement aDecl,
                                          @NonNull final ICommonsMap <String, PackageComponent> aByName,
                                          @NonNull final ICommonsSet <String> aOverridden)
  {
    final boolean bHasName = aDecl.hasAttr ("name");
    if (aDecl.isXsl ("template") && aDecl.hasAttr ("match") && !bHasName)
    {
      _checkOverrideRuleMode (aDecl);
      final String sMode = aDecl.getAttr ("mode");
      if (sMode != null)
        for (final String sToken : XsltNames.tokenize (sMode))
        {
          if (sToken.startsWith ("#"))
            continue;
          final XdmQName aMode = aDecl.resolveQName (sToken, false);
          final PackageComponent aTarget = aByName.get (PackageComponent.getSymbolicName ("mode", aMode, 0));
          if (aTarget == null || !"public".equals (aTarget.visibility ()) && !"abstract".equals (aTarget.visibility ()))
            throw XsltException.at (aDecl, "XTSE3060", "The mode '" + sToken + "' cannot be overridden: it is not public or abstract in the used package");
        }
      _checkNoApplyImports (aDecl);
      return;
    }
    if (!bHasName)
      return;
    final PackageComponent aComp = getComponent (aDecl);
    if (aComp == null)
      return;
    final String sSym = aComp.getSymbolicName ();
    final PackageComponent aTarget = aByName.get (sSym);
    if (aTarget == null)
      throw XsltException.at (aDecl, "XTSE3058", "xsl:override declares the " + aComp.getLabel () + ", which is not a component of the used package");
    if (!"public".equals (aTarget.visibility ()) && !"abstract".equals (aTarget.visibility ()))
      throw XsltException.at (aDecl,
                              "XTSE3060",
                              "The " + aComp.getLabel () + " has visibility '" + aTarget.visibility () + "' in the used package and cannot be overridden");
    aOverridden.add (sSym);
    _checkOverrideSignature (aDecl, aTarget);
    _checkNoApplyImports (aDecl);
  }

  /**
   * The static rules of one xsl:use-package against the manifest of the
   * package it uses: xsl:override (XTSE3058, XTSE3060, XTSE3070, XTSE3440,
   * XTSE3460), xsl:accept (XTSE3030, XTSE3040, XTSE3051) and homonymous
   * components accepted from two packages (XTSE3050).
   *
   * @param aUse
   *        The xsl:use-package element.
   * @param sUsedKey
   *        The identity of the used package.
   * @param aExports
   *        The manifest of the used package.
   * @param aAccepted
   *        Symbolic names accepted by earlier xsl:use-package elements of the
   *        same package manifest, to the identity of the package they came
   *        from. Modified.
   */
  public static void checkUsePackage (@NonNull final XslElement aUse,
                                      @NonNull final String sUsedKey,
                                      @NonNull final ICommonsList <PackageComponent> aExports,
                                      @NonNull final ICommonsMap <String, String> aAccepted)
  {
    final ICommonsMap <String, PackageComponent> aByName = new CommonsHashMap <> ();
    for (final PackageComponent aComp : aExports)
      aByName.put (aComp.getSymbolicName (), aComp);

    final ICommonsSet <String> aOverridden = new CommonsHashSet <> ();
    for (final XslElement aOverride : aUse.getXslChildren ("override"))
      for (final XslElement aDecl : aOverride.getElementChildren ())
        if (aDecl.isXsl ())
          _checkOverrideDecl (aDecl, aByName, aOverridden);

    for (final XslElement aAccept : aUse.getXslChildren ("accept"))
    {
      final String sVis = _attr (aAccept, "visibility", "").trim ();
      boolean bAllWild = true;
      boolean bMatchedAny = false;
      for (final String sToken : XsltNames.tokenize (_attr (aAccept, "names", "")))
      {
        if (_isWildcardToken (sToken))
        {
          for (final PackageComponent aComp : aExports)
            if (acceptMatches (aAccept, aComp))
            {
              bMatchedAny = true;
              break;
            }
          continue;
        }
        bAllWild = false;
        for (final PackageComponent aComp : aExports)
        {
          if (!tokenMatches (aAccept, sToken, aComp) || !_kindMatches (aAccept, aComp))
            continue;
          bMatchedAny = true;
          if (aOverridden.contains (aComp.getSymbolicName ()))
            throw XsltException.at (aAccept, "XTSE3051", "xsl:accept names '" + sToken + "', a component overridden by the same xsl:use-package");
          if (!_isVisibilityCompatible (aComp.visibility (), sVis))
            throw XsltException.at (aAccept,
                                    "XTSE3040",
                                    "Cannot accept the " + aComp.getLabel () + " (visibility " + aComp.visibility () + ") with visibility '" + sVis + "'");
        }
      }
      if (!bMatchedAny && !bAllWild)
        throw XsltException.at (aAccept, "XTSE3030", "xsl:accept matches no component of the used package");
    }

    for (final PackageComponent aComp : aExports)
    {
      final String sSym = aComp.getSymbolicName ();
      if ("hidden".equals (getAcceptedVisibility (aUse, aComp)))
        continue;
      final String sPrev = aAccepted.get (sSym);
      if (sPrev != null && !sPrev.equals (sUsedKey))
        throw XsltException.at (aUse, "XTSE3050", "The " + aComp.getLabel () + " is accepted from more than one used package");
      aAccepted.put (sSym, sUsedKey);
    }
  }

  /**
   * XTSE3080 for the components a top-level package declares itself.
   *
   * @param aRoots
   *        The module roots of the principal package.
   */
  public static void checkOwnAbstract (@NonNull final Iterable <XslElement> aRoots)
  {
    for (final XslElement aRoot : aRoots)
      for (final XslElement aChild : aRoot.getElementChildren ())
      {
        final PackageComponent aComp = getComponent (aChild);
        if (aComp != null && !aChild.isXsl ("param"))
        {
          final String sVis = aChild.getAttr ("visibility");
          if (sVis != null && "abstract".equals (sVis.trim ()))
            throw XsltException.at (aChild, "XTSE3080", "A top-level package cannot declare the abstract " + aComp.getLabel ());
        }
      }
  }

  /**
   * @param aNode
   *        A stylesheet node.
   * @return The outermost element of its module.
   */
  @NonNull
  public static XslElement getModuleRoot (@NonNull final XslNode aNode)
  {
    XslElement ret = aNode instanceof final XslElement aEl ? aEl : aNode.getParent ();
    while (ret.getParent () != null)
      ret = ret.getParent ();
    return ret;
  }
}
