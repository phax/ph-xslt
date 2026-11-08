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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.model.AttributeSet;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.Mode;
import com.helger.xslt.model.Template;
import com.helger.xslt.model.UserFunction;
import com.helger.xslt.model.VariableDef;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * The static rules of xsl:expose in a package (XSLT 3.0 section 3.5.3.1):
 * XTSE0020 for invalid names, XTSE3020 for a name that matches no component,
 * XTSE3010/XTSE3025 for visibilities that conflict with the declaration.
 * Exposed modes are recorded; the package machinery itself is not
 * implemented.
 *
 * @author Philip Helger
 */
@Immutable
public final class ExposeChecker
{
  /** A component of the package. */
  private record Component (@NonNull String kind,
                            @NonNull XdmQName name,
                            int arity,
                            @Nullable String declaredVisibility)
  {}

  @PresentForCodeCoverage
  private static final ExposeChecker INSTANCE = new ExposeChecker ();

  private ExposeChecker ()
  {}

  /**
   * @return The trimmed <code>visibility</code> attribute of a declaration,
   *         or <code>null</code> if it has none.
   */
  @Nullable
  private static String _vis (@NonNull final XslElement aEl)
  {
    final String s = aEl.getAttr ("visibility");
    return s == null ? null : s.trim ();
  }

  /**
   * Collect the named components of the compiled package that xsl:expose
   * can refer to: named templates, stylesheet functions (with arity),
   * attribute sets (visibility of their first declaration), global variables
   * and parameters, and named modes. The arity is -1 for all but functions.
   *
   * @param aSheet
   *        The compiled stylesheet.
   * @return The components with their declared visibility, <code>null</code>
   *         where none is declared.
   */
  @NonNull
  private static ICommonsList <Component> _getComponents (@NonNull final CompiledStylesheet aSheet)
  {
    final ICommonsList <Component> ret = new CommonsArrayList <> ();
    for (final Template aTemplate : aSheet.templates ())
      if (aTemplate.getName () != null)
        ret.add (new Component ("template", aTemplate.getName (), -1, _vis (aTemplate.getElement ())));
    for (final UserFunction aFunc : aSheet.functions ())
      ret.add (new Component ("function", aFunc.getName (), aFunc.getArity (), aFunc.getVisibility ()));
    for (final AttributeSet aSet : aSheet.attributeSets ().values ())
      ret.add (new Component ("attribute-set", aSet.getName (), -1, _vis (aSet.decls ().getFirst ().element ())));
    for (final VariableDef aVar : aSheet.globals ().values ())
      ret.add (new Component ("variable", aVar.getName (), -1, _vis (aVar.getElement ())));
    for (final Mode aMode : aSheet.modes ().values ())
      if (!aMode.isUnnamed ())
        ret.add (new Component ("mode", aMode.getName (), -1, aMode.getVisibility ()));
    return ret;
  }

  /**
   * Check every xsl:expose of the package and record its effect on modes and
   * named templates. Each token of <code>names</code> is either
   * <code>*</code>, <code>*:local</code>, <code>prefix:*</code>,
   * <code>Q{uri}*</code> or an EQName (no-namespace default), optionally
   * followed by <code>#arity</code>; the arity restricts an exact name only.
   * For each matching component of the requested kind:
   * <ul>
   * <li>exposing as abstract fails with XTSE3025 for a wildcard, a mode or a
   * component without declared visibility, and with XTSE3010 if the
   * declared visibility is not abstract;</li>
   * <li>exposing an explicitly named component that is declared abstract
   * with another visibility fails with XTSE3010;</li>
   * <li>public or final exposure adds a mode or template to the exposed
   * ones, explicitly named private exposure to the private ones.</li>
   * </ul>
   * The checks against the declared visibility are a simplification of the
   * table in XSLT 3.0 section 3.5.3.1: only the abstract cases are
   * reported.
   *
   * @param aCtx
   *        Compile context.
   * @param aExposes
   *        The xsl:expose elements of the package.
   * @throws XsltException
   *         XTSE0020 for an unknown <code>component</code> or
   *         <code>visibility</code> value, a malformed name token or arity,
   *         or an unbound prefix; XTSE3022 for <code>component="*"</code>
   *         with a non-wildcard name; XTSE3020 for a function name without
   *         arity or a non-wildcard name that matches no component;
   *         XTSE3010 and XTSE3025 as above.
   */
  public static void check (@NonNull final CompileContext aCtx, @NonNull final ICommonsList <XslElement> aExposes)
  {
    final CompiledStylesheet aSheet = aCtx.getStylesheet ();
    final ICommonsList <Component> aComponents = _getComponents (aSheet);
    for (final XslElement aEl : aExposes)
    {
      final String sComponent = aEl.getAttr ("component").trim ();
      final String sVisibility = aEl.getAttr ("visibility").trim ();
      switch (sComponent)
      {
        case "template", "function", "attribute-set", "variable", "mode", "*":
          break;
        default:
          throw XsltException.at (aEl, "XTSE0020", "component='" + sComponent + "' is not allowed on xsl:expose");
      }
      switch (sVisibility)
      {
        case "public", "private", "final", "abstract":
          break;
        default:
          throw XsltException.at (aEl, "XTSE0020", "visibility='" + sVisibility + "' is not allowed on xsl:expose");
      }
      for (final String sToken : XsltNames.tokenize (aEl.getAttr ("names")))
      {
        String sName = sToken;
        int nArity = -1;
        final int nHash = sName.lastIndexOf ('#');
        if (nHash > 0 && !sName.endsWith ("}"))
        {
          try
          {
            nArity = Integer.parseInt (sName.substring (nHash + 1));
          }
          catch (final NumberFormatException ex)
          {
            throw XsltException.at (aEl, "XTSE0020", "'" + sToken + "' is not a valid component name");
          }
          sName = sName.substring (0, nHash);
        }
        if (sName.startsWith ("#"))
          throw XsltException.at (aEl, "XTSE0020", "'" + sToken + "' is not a valid component name");
        final boolean bAll = "*".equals (sName);
        boolean bWildcard = bAll;
        String sWildUri = null;
        String sWildLocal = null;
        XdmQName aExact = null;
        if (!bAll)
          if (sName.startsWith ("*:"))
          {
            bWildcard = true;
            sWildLocal = sName.substring (2);
          }
          else
            if (sName.endsWith (":*") && !sName.startsWith ("Q{"))
            {
              bWildcard = true;
              sWildUri = aEl.lookupNamespaceUri (sName.substring (0, sName.length () - 2));
              if (sWildUri == null)
                throw XsltException.at (aEl, "XTSE0020", "The prefix of '" + sToken + "' is not bound");
            }
            else
              if (sName.startsWith ("Q{") && sName.endsWith ("}*"))
              {
                bWildcard = true;
                sWildUri = sName.substring (2, sName.length () - 2);
              }
              else
              {
                aExact = aEl.resolveQName (sName, false);
                if (aExact == null)
                  throw XsltException.at (aEl, "XTSE0020", "'" + sToken + "' is not a valid component name");
              }
        if ("*".equals (sComponent) && !bWildcard)
          throw XsltException.at (aEl, "XTSE3022", "xsl:expose with component='*' requires a wildcard name, not '" + sToken + "'");
        if ("function".equals (sComponent) && !bWildcard && nArity < 0)
          throw XsltException.at (aEl, "XTSE3020", "A function name in xsl:expose needs an arity");
        boolean bFound = false;
        for (final Component aComp : aComponents)
        {
          if (!"*".equals (sComponent) && !sComponent.equals (aComp.kind ()))
            continue;
          final XdmQName aCompName = aComp.name ();
          final boolean bMatch = bAll ||
                                 (aExact != null && aExact.equals (aCompName) && (nArity < 0 || nArity == aComp.arity ())) ||
                                 (sWildUri != null && sWildUri.equals (aCompName.namespaceUri ())) ||
                                 (sWildLocal != null && sWildLocal.equals (aCompName.localName ()));
          if (!bMatch)
            continue;
          bFound = true;
          final String sDeclared = aComp.declaredVisibility ();
          if ("abstract".equals (sVisibility))
          {
            if (bWildcard || "mode".equals (aComp.kind ()) || sDeclared == null)
              throw XsltException.at (aEl, "XTSE3025", "xsl:expose cannot make " + aComp.kind () + " " + aCompName.getEQName () + " abstract");
            if (!"abstract".equals (sDeclared))
              throw XsltException.at (aEl, "XTSE3010", "xsl:expose conflicts with the declared visibility of " + aCompName.getEQName ());
          }
          else
            if ("abstract".equals (sDeclared) && !bWildcard)
              throw XsltException.at (aEl, "XTSE3010", "xsl:expose conflicts with the declared visibility of " + aCompName.getEQName ());
          if ("mode".equals (aComp.kind ()) && "private".equals (sVisibility) && !bWildcard)
            aSheet.privateModes ().add (aCompName);
          if ("mode".equals (aComp.kind ()) && ("public".equals (sVisibility) || "final".equals (sVisibility)))
            aSheet.exposedModes ().add (aCompName);
          if ("template".equals (aComp.kind ()) && ("public".equals (sVisibility) || "final".equals (sVisibility)))
            aSheet.exposedTemplates ().add (aCompName);
          if ("template".equals (aComp.kind ()) && "private".equals (sVisibility) && !bWildcard)
            aSheet.privateTemplates ().add (aCompName);
        }
        if (!bFound && !bWildcard)
          throw XsltException.at (aEl, "XTSE3020", "xsl:expose names '" + sToken + "', which matches no " + sComponent + " of the package");
      }
    }
  }
}
