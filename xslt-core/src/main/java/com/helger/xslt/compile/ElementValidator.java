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

import java.math.BigDecimal;
import java.util.Map;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XslText;
import com.helger.xslt.tree.XsltNames;

/**
 * Static validation of stylesheet elements (a port of go-xslt's validate.go):
 * allowed and required attributes (XTSE0090, XTSE0010), attribute values
 * (XTSE0020, XTSE0110, XTSE0280, ...), content models and positions (XTSE0010,
 * XTSE0260, ...). Unknown XSLT elements under forwards-compatible processing
 * are skipped with their content.
 *
 * @author Philip Helger
 */
@Immutable
public final class ElementValidator
{
  private static final ICommonsMap <String, ICommonsSet <String>> QNAME_ATTRS = new CommonsHashMap <> ();
  private static final ICommonsMap <String, ICommonsSet <String>> BOOL_ATTRS = new CommonsHashMap <> ();
  private static final ICommonsMap <String, ICommonsSet <String>> BOOL_AVT_ATTRS = new CommonsHashMap <> ();
  private static final ICommonsMap <String, Object []> SELECT_EXCLUSIVE = new CommonsHashMap <> ();
  private static final ICommonsSet <String> RESERVED_NS_NAMES = new CommonsHashSet <> ("template",
                                                                                       "mode",
                                                                                       "attribute-set",
                                                                                       "key",
                                                                                       "decimal-format",
                                                                                       "variable",
                                                                                       "param",
                                                                                       "function",
                                                                                       "output",
                                                                                       "character-map",
                                                                                       "accumulator");
  private static final ICommonsSet <String> LRE_XSL_ATTRS = new CommonsHashSet <> ("version",
                                                                                   "exclude-result-prefixes",
                                                                                   "extension-element-prefixes",
                                                                                   "xpath-default-namespace",
                                                                                   "expand-text",
                                                                                   "default-collation",
                                                                                   "use-when",
                                                                                   "default-validation",
                                                                                   "default-mode",
                                                                                   "use-attribute-sets",
                                                                                   "type",
                                                                                   "validation",
                                                                                   "inherit-namespaces");
  /** The XSLT elements allowed as children of xsl:stylesheet. */
  public static final ICommonsSet <String> TOP_LEVEL_DECLARATIONS = new CommonsHashSet <> ("accumulator",
                                                                                           "attribute-set",
                                                                                           "character-map",
                                                                                           "decimal-format",
                                                                                           "function",
                                                                                           "global-context-item",
                                                                                           "import",
                                                                                           "import-schema",
                                                                                           "include",
                                                                                           "key",
                                                                                           "mode",
                                                                                           "namespace-alias",
                                                                                           "output",
                                                                                           "param",
                                                                                           "preserve-space",
                                                                                           "strip-space",
                                                                                           "template",
                                                                                           "variable",
                                                                                           "use-package");
  private static final ICommonsMap <String, ICommonsSet <String>> ALLOWED_PARENTS = new CommonsHashMap <> ();
  private static final ICommonsSet <String> ONE_CHAR_ATTRS = new CommonsHashSet <> ("decimal-separator",
                                                                                    "grouping-separator",
                                                                                    "percent",
                                                                                    "per-mille",
                                                                                    "zero-digit",
                                                                                    "digit",
                                                                                    "pattern-separator",
                                                                                    "exponent-separator");
  private static final ICommonsSet <String> NON_INSTRUCTION_SIBLINGS = new CommonsHashSet <> ("fallback",
                                                                                              "param",
                                                                                              "on-completion",
                                                                                              "sort",
                                                                                              "with-param",
                                                                                              "catch");

  static
  {
    for (final String s : new String [] { "template",
                                          "call-template",
                                          "variable",
                                          "param",
                                          "with-param",
                                          "attribute-set",
                                          "key",
                                          "function",
                                          "character-map",
                                          "accumulator",
                                          "decimal-format",
                                          "mode" })
      QNAME_ATTRS.put (s, new CommonsHashSet <> ("name"));
    BOOL_ATTRS.put ("param", new CommonsHashSet <> ("required", "tunnel", "static"));
    BOOL_ATTRS.put ("with-param", new CommonsHashSet <> ("tunnel"));
    BOOL_ATTRS.put ("variable", new CommonsHashSet <> ("static"));
    BOOL_ATTRS.put ("copy-of", new CommonsHashSet <> ("copy-namespaces", "copy-accumulators"));
    BOOL_ATTRS.put ("text", new CommonsHashSet <> ("disable-output-escaping"));
    BOOL_ATTRS.put ("value-of", new CommonsHashSet <> ("disable-output-escaping"));
    BOOL_ATTRS.put ("copy", new CommonsHashSet <> ("copy-namespaces", "inherit-namespaces"));
    BOOL_ATTRS.put ("element", new CommonsHashSet <> ("inherit-namespaces"));
    BOOL_ATTRS.put ("key", new CommonsHashSet <> ("composite"));
    BOOL_ATTRS.put ("for-each-group", new CommonsHashSet <> ("composite"));
    BOOL_ATTRS.put ("output",
                    new CommonsHashSet <> ("indent",
                                           "omit-xml-declaration",
                                           "byte-order-mark",
                                           "escape-uri-attributes",
                                           "include-content-type",
                                           "undeclare-prefixes",
                                           "allow-duplicate-names",
                                           "build-tree"));
    BOOL_ATTRS.put ("mode", new CommonsHashSet <> ("streamable", "warning-on-no-match", "warning-on-multiple-match"));
    BOOL_ATTRS.put ("attribute-set", new CommonsHashSet <> ("streamable"));
    BOOL_ATTRS.put ("function", new CommonsHashSet <> ("override", "override-extension-function", "cache", "identity-sensitive"));
    BOOL_AVT_ATTRS.put ("message", new CommonsHashSet <> ("terminate"));
    BOOL_AVT_ATTRS.put ("assert", new CommonsHashSet <> ("terminate"));

    SELECT_EXCLUSIVE.put ("attribute", new Object [] { "XTSE0840", new CommonsHashSet <> () });
    SELECT_EXCLUSIVE.put ("value-of", new Object [] { "XTSE0870", new CommonsHashSet <> () });
    SELECT_EXCLUSIVE.put ("processing-instruction", new Object [] { "XTSE0880", new CommonsHashSet <> () });
    SELECT_EXCLUSIVE.put ("comment", new Object [] { "XTSE0940", new CommonsHashSet <> () });
    SELECT_EXCLUSIVE.put ("namespace", new Object [] { "XTSE0910", new CommonsHashSet <> ("fallback") });
    SELECT_EXCLUSIVE.put ("sort", new Object [] { "XTSE1015", new CommonsHashSet <> () });
    SELECT_EXCLUSIVE.put ("perform-sort", new Object [] { "XTSE1040", new CommonsHashSet <> ("sort", "fallback") });
    SELECT_EXCLUSIVE.put ("break", new Object [] { "XTSE3125", new CommonsHashSet <> () });
    SELECT_EXCLUSIVE.put ("on-completion", new Object [] { "XTSE3125", new CommonsHashSet <> () });
    SELECT_EXCLUSIVE.put ("try", new Object [] { "XTSE3140", new CommonsHashSet <> ("catch", "fallback") });
    SELECT_EXCLUSIVE.put ("catch", new Object [] { "XTSE3150", new CommonsHashSet <> () });
    SELECT_EXCLUSIVE.put ("sequence", new Object [] { "XTSE3185", new CommonsHashSet <> ("fallback") });
    SELECT_EXCLUSIVE.put ("merge-key", new Object [] { "XTSE3200", new CommonsHashSet <> () });

    final String [] aTop = { "stylesheet", "transform", "package" };
    ALLOWED_PARENTS.put ("sort", new CommonsHashSet <> ("for-each", "for-each-group", "apply-templates", "perform-sort"));
    ALLOWED_PARENTS.put ("with-param",
                         new CommonsHashSet <> ("apply-templates",
                                                "call-template",
                                                "apply-imports",
                                                "next-match",
                                                "next-iteration",
                                                "evaluate"));
    ALLOWED_PARENTS.put ("param",
                         new CommonsHashSet <> ("stylesheet",
                                                "transform",
                                                "package",
                                                "template",
                                                "function",
                                                "iterate",
                                                "override"));
    ALLOWED_PARENTS.put ("when", new CommonsHashSet <> ("choose"));
    ALLOWED_PARENTS.put ("otherwise", new CommonsHashSet <> ("choose"));
    ALLOWED_PARENTS.put ("matching-substring", new CommonsHashSet <> ("analyze-string"));
    ALLOWED_PARENTS.put ("non-matching-substring", new CommonsHashSet <> ("analyze-string"));
    ALLOWED_PARENTS.put ("output-character", new CommonsHashSet <> ("character-map"));
    ALLOWED_PARENTS.put ("merge-source", new CommonsHashSet <> ("merge"));
    ALLOWED_PARENTS.put ("merge-action", new CommonsHashSet <> ("merge"));
    ALLOWED_PARENTS.put ("merge-key", new CommonsHashSet <> ("merge-source"));
    ALLOWED_PARENTS.put ("accumulator-rule", new CommonsHashSet <> ("accumulator"));
    ALLOWED_PARENTS.put ("catch", new CommonsHashSet <> ("try"));
    ALLOWED_PARENTS.put ("on-completion", new CommonsHashSet <> ("iterate"));
    ALLOWED_PARENTS.put ("context-item", new CommonsHashSet <> ("template"));
    ALLOWED_PARENTS.put ("function", new CommonsHashSet <> ("stylesheet", "transform", "package", "override"));
    ALLOWED_PARENTS.put ("attribute-set", new CommonsHashSet <> ("stylesheet", "transform", "package", "override"));
    ALLOWED_PARENTS.put ("accept", new CommonsHashSet <> ("use-package"));
    ALLOWED_PARENTS.put ("expose", new CommonsHashSet <> ("package"));
    ALLOWED_PARENTS.put ("override", new CommonsHashSet <> ("use-package"));
    for (final String s : new String [] { "key",
                                          "character-map",
                                          "decimal-format",
                                          "namespace-alias",
                                          "strip-space",
                                          "preserve-space",
                                          "output",
                                          "accumulator",
                                          "import",
                                          "include",
                                          "mode",
                                          "global-context-item",
                                          "import-schema" })
      ALLOWED_PARENTS.put (s, new CommonsHashSet <> (aTop));
  }

  @PresentForCodeCoverage
  private static final ElementValidator INSTANCE = new ElementValidator ();

  private ElementValidator ()
  {}

  private static void _checkBoolean (@NonNull final XslElement aEl, @NonNull final String sName, @NonNull final String sValue)
  {
    if (!XsltNames.isXsltBooleanLexical (sValue))
      throw XsltException.at (aEl, "XTSE0020", sName + "='" + sValue + "' is not an XSLT boolean");
  }

  private static void _checkPrefixBound (@NonNull final XslElement aEl, @NonNull final String sQName)
  {
    final String s = sQName.trim ();
    final int n = s.indexOf (':');
    if (n <= 0 || s.startsWith ("Q{"))
      return;
    if (aEl.lookupNamespaceUri (s.substring (0, n)) == null)
      throw XsltException.at (aEl, "XTSE0280", "The prefix in '" + s + "' has no namespace binding");
  }

  private static void _checkExcludeResultPrefixes (@NonNull final XslElement aEl, @NonNull final String sValue)
  {
    for (final String sToken : XsltNames.tokenize (sValue))
    {
      if ("#all".equals (sToken))
        continue;
      if ("#default".equals (sToken))
      {
        if (aEl.lookupNamespaceUri ("") == null)
          throw XsltException.at (aEl, "XTSE0809", "#default in exclude-result-prefixes, but there is no default namespace");
        continue;
      }
      if (aEl.lookupNamespaceUri (sToken) == null)
        throw XsltException.at (aEl, "XTSE0808", "The prefix '" + sToken + "' in exclude-result-prefixes is not bound");
    }
  }

  private static boolean _isLastInstruction (@NonNull final XslElement aParent, @NonNull final XslElement aChild)
  {
    boolean bSeen = false;
    for (final XslNode aNode : aParent.getChildren ())
    {
      if (aNode == aChild)
      {
        bSeen = true;
        continue;
      }
      if (!bSeen)
        continue;
      if (aNode instanceof final XslElement aEl)
      {
        if (aEl.isXsl () && NON_INSTRUCTION_SIBLINGS.contains (aEl.getLocalName ()))
          continue;
        return false;
      }
      if (aNode instanceof final XslText aText && !aText.isWhitespaceOnly ())
        return false;
    }
    return bSeen;
  }

  private static boolean _hasIterateAncestor (@NonNull final XslElement aEl)
  {
    for (XslElement aCur = aEl.getParent (); aCur != null; aCur = aCur.getParent ())
      if (aCur.isXsl ("iterate"))
        return true;
    return false;
  }

  private static void _checkIterateExit (@NonNull final XslElement aEl)
  {
    XslElement aIterate = null;
    XslElement aCur = aEl;
    while (true)
    {
      final XslElement aParent = aCur.getParent ();
      if (aParent == null)
        break;
      if (!_isLastInstruction (aParent, aCur))
      {
        if (_hasIterateAncestor (aEl))
          throw XsltException.at (aEl, "XTSE3120", "xsl:" + aEl.getLocalName () + " must be the last instruction in its sequence constructor");
        throw XsltException.at (aEl, "XTSE0010", "xsl:" + aEl.getLocalName () + " must appear within xsl:iterate");
      }
      if (aParent.isXsl ())
      {
        final String sLocal = aParent.getLocalName ();
        if ("iterate".equals (sLocal))
        {
          aIterate = aParent;
          break;
        }
        if ("if".equals (sLocal) || "try".equals (sLocal) || "catch".equals (sLocal) || "fork".equals (sLocal))
        {
          aCur = aParent;
          continue;
        }
        if (("when".equals (sLocal) || "otherwise".equals (sLocal)) &&
            aParent.getParent () != null &&
            aParent.getParent ().isXsl ("choose"))
        {
          aCur = aParent.getParent ();
          continue;
        }
      }
      break;
    }
    if (aIterate == null)
    {
      if (_hasIterateAncestor (aEl))
        throw XsltException.at (aEl, "XTSE3120", "xsl:" + aEl.getLocalName () + " is not in a permitted position within xsl:iterate");
      throw XsltException.at (aEl, "XTSE0010", "xsl:" + aEl.getLocalName () + " must appear within xsl:iterate");
    }
    if (!aEl.isXsl ("next-iteration"))
      return;
    final ICommonsSet <XdmQName> aDeclared = new CommonsHashSet <> ();
    for (final XslElement aParam : aIterate.getXslChildren ("param"))
    {
      final String sName = aParam.getAttr ("name");
      if (sName != null)
        aDeclared.add (aParam.resolveQName (sName, false));
    }
    for (final XslElement aWithParam : aEl.getXslChildren ("with-param"))
    {
      final String sName = aWithParam.getAttr ("name");
      if (sName != null && !aDeclared.contains (aWithParam.resolveQName (sName, false)))
        throw XsltException.at (aWithParam,
                                "XTSE3130",
                                "xsl:next-iteration/xsl:with-param '" + sName + "' matches no parameter of the enclosing xsl:iterate");
    }
  }

  private static void _validateModeList (@NonNull final XslElement aEl, @NonNull final String sMode)
  {
    final String [] aTokens = XsltNames.tokenize (sMode);
    if (aTokens.length == 0)
      throw XsltException.at (aEl, "XTSE0550", "Empty mode list");
    final ICommonsSet <String> aSeen = new CommonsHashSet <> ();
    for (final String sToken : aTokens)
    {
      if (!aSeen.add (sToken))
        throw XsltException.at (aEl, "XTSE0550", "Duplicate mode '" + sToken + "'");
      if ("#all".equals (sToken))
      {
        if (aTokens.length > 1)
          throw XsltException.at (aEl, "XTSE0550", "#all must be the only mode");
      }
      else
        if (!"#default".equals (sToken) && !"#unnamed".equals (sToken))
        {
          if (!XsltNames.isEQName (sToken))
            throw XsltException.at (aEl, "XTSE0550", "The mode '" + sToken + "' is not a valid QName");
          _checkPrefixBound (aEl, sToken);
        }
    }
  }

  private static boolean _isValidationOk (@NonNull final String s)
  {
    return "strip".equals (s) || "preserve".equals (s) || "lax".equals (s);
  }

  private static void _validateLiteralResultElement (@NonNull final XslElement aEl)
  {
    if (aEl.getAttribute (XsltNames.NS_XSL, "type") != null)
      throw XsltException.at (aEl, "XTSE1660", "xsl:type requires a schema-aware processor");
    final String sValidation = aEl.getAttribute (XsltNames.NS_XSL, "validation");
    if (sValidation != null && !_isValidationOk (sValidation.trim ()))
      throw XsltException.at (aEl, "XTSE1660", "xsl:validation='" + sValidation + "' requires a schema-aware processor");
    final String sDefVal = aEl.getAttribute (XsltNames.NS_XSL, "default-validation");
    if (sDefVal != null && !_isValidationOk (sDefVal.trim ()))
      throw XsltException.at (aEl, "XTSE1660", "xsl:default-validation='" + sDefVal + "' requires a schema-aware processor");
    final String sVersion = aEl.getAttribute (XsltNames.NS_XSL, "version");
    if (sVersion != null)
    {
      final String s = sVersion.trim ();
      if (!XsltNames.isDecimalLexical (s))
        throw XsltException.at (aEl, "XTSE0110", "xsl:version='" + sVersion + "' is not a valid number");
      if (new BigDecimal (s).compareTo (BigDecimal.valueOf (2)) < 0)
        throw XsltException.at (aEl,
                                "XTDE0160",
                                "xsl:version='" + sVersion + "' requests backwards compatible behaviour, which this processor does not support");
    }
    for (final XdmQName aName : aEl.getAllAttributes ().keySet ())
      if (XsltNames.NS_XSL.equals (aName.namespaceUri ()) &&
          !LRE_XSL_ATTRS.contains (aName.localName ()) &&
          !aEl.isForwardsCompatible ())
        throw XsltException.at (aEl, "XTSE0805", "xsl:" + aName.localName () + " is not allowed on a literal result element");
    final String sExclude = aEl.getAttribute (XsltNames.NS_XSL, "exclude-result-prefixes");
    if (sExclude != null)
      _checkExcludeResultPrefixes (aEl, sExclude);
    final String sExt = aEl.getAttribute (XsltNames.NS_XSL, "extension-element-prefixes");
    if (sExt != null)
      for (final String sToken : XsltNames.tokenize (sExt))
        if (aEl.lookupNamespaceUri ("#default".equals (sToken) ? "" : sToken) == null)
          throw XsltException.at (aEl, "XTSE1430", "The prefix '" + sToken + "' in extension-element-prefixes is not bound");
    final String sInherit = aEl.getAttribute (XsltNames.NS_XSL, "inherit-namespaces");
    if (sInherit != null)
      _checkBoolean (aEl, "xsl:inherit-namespaces", sInherit);
    final String sExpand = aEl.getAttribute (XsltNames.NS_XSL, "expand-text");
    if (sExpand != null)
      _checkBoolean (aEl, "xsl:expand-text", sExpand);
  }

  private static void _checkSortFirst (@NonNull final XslElement aEl, @NonNull final String sFirst, @NonNull final String sCode)
  {
    boolean bSeenBody = false;
    for (final XslNode aNode : aEl.getChildren ())
    {
      if (aNode instanceof final XslText aText)
      {
        if (!aText.isWhitespaceOnly ())
          bSeenBody = true;
        continue;
      }
      final XslElement aChild = (XslElement) aNode;
      if (aChild.isXsl (sFirst))
      {
        if (bSeenBody)
          throw XsltException.at (aEl, sCode, "xsl:" + sFirst + " must come first in xsl:" + aEl.getLocalName ());
        continue;
      }
      if (aChild.isXsl ("context-item") && aEl.isXsl ("template"))
        continue;
      bSeenBody = true;
    }
  }

  private static boolean _isEmptyContent (@NonNull final XslElement aEl)
  {
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof XslElement || !((XslText) aNode).isWhitespaceOnly ())
        return false;
    return true;
  }

  private static void _checkVocabulary (@NonNull final XslElement aEl, @NonNull final String sAttr, @NonNull final String... aAllowed)
  {
    final String v = aEl.getAttr (sAttr);
    if (v == null)
      return;
    final String s = v.trim ();
    for (final String a : aAllowed)
      if (a.equals (s))
        return;
    throw XsltException.at (aEl, "XTSE0020", sAttr + "='" + s + "' is not allowed on xsl:" + aEl.getLocalName ());
  }

  private static void _validateXslElement (@NonNull final XslElement aEl)
  {
    final String sLocal = aEl.getLocalName ();
    final String sVersion = aEl.getAttr ("version");
    if (sVersion != null && !XsltNames.isDecimalLexical (sVersion.trim ()))
      throw XsltException.at (aEl, "XTSE0110", "version='" + sVersion + "' is not a valid number");
    final String sDefVal = aEl.getAttr ("default-validation");
    if (sDefVal != null && !_isValidationOk (sDefVal.trim ()))
      throw XsltException.at (aEl, "XTSE1660", "default-validation='" + sDefVal + "' requires a schema-aware processor");
    final String sExpand = aEl.getAttr ("expand-text");
    if (sExpand != null)
      _checkBoolean (aEl, "expand-text", sExpand);
    final String sValidation = aEl.getAttr ("validation");
    if (sValidation != null && "strict".equals (sValidation.trim ()))
      throw XsltException.at (aEl, "XTSE1660", "validation='strict' requires a schema-aware processor");
    if (aEl.hasAttr ("type") && !"function".equals (sLocal))
      throw XsltException.at (aEl, "XTSE1660", "The type attribute requires a schema-aware processor");
    final String sDefColl = aEl.getAttr ("default-collation");
    if (sDefColl != null)
    {
      boolean bFound = false;
      for (final String sToken : XsltNames.tokenize (sDefColl))
        if (CollationRegistry.getCollation (CollationRegistry.resolveUri (sToken, aEl.getStaticBaseUri ())) != null)
        {
          bFound = true;
          break;
        }
      if (!bFound)
        throw XsltException.at (aEl, "XTSE0125", "default-collation='" + sDefColl + "' names no supported collation");
    }
    if ("break".equals (sLocal) || "next-iteration".equals (sLocal))
      _checkIterateExit (aEl);

    final ICommonsSet <String> aParents = ALLOWED_PARENTS.get (sLocal);
    final XslElement aParent = aEl.getParent ();
    if (aParents != null && aParent != null)
    {
      if (aParent.isXsl () && !aParents.contains (aParent.getLocalName ()))
        throw XsltException.at (aEl, "XTSE0010", "xsl:" + sLocal + " is not allowed inside xsl:" + aParent.getLocalName ());
      if (!aParent.isXsl () && !"sort".equals (sLocal) && !"with-param".equals (sLocal))
        throw XsltException.at (aEl, "XTSE0010", "xsl:" + sLocal + " is not allowed inside a literal result element");
    }

    final XsltElementSpecs.Spec aSpec = XsltElementSpecs.getSpec (sLocal);
    if (aSpec == null)
      return;
    for (final XdmQName aName : aEl.getAllAttributes ().keySet ())
    {
      if (aName.namespaceUri ().isEmpty ())
      {
        final String sAttr = aName.localName ();
        if (aSpec.attrs ().contains (sAttr) ||
            XsltElementSpecs.STANDARD_ATTRIBUTES.contains (sAttr) ||
            aEl.isForwardsCompatible ())
          continue;
        throw XsltException.at (aEl, "XTSE0090", "The attribute '" + sAttr + "' is not allowed on xsl:" + sLocal);
      }
      if (XsltNames.NS_XSL.equals (aName.namespaceUri ()))
        throw XsltException.at (aEl, "XTSE0090", "The attribute xsl:" + aName.localName () + " is not allowed on xsl:" + sLocal);
    }
    for (final String sReq : aSpec.required ())
      if (!aEl.hasAttr (sReq))
        throw XsltException.at (aEl, "XTSE0010", "xsl:" + sLocal + " requires the attribute " + sReq);

    if ("template".equals (sLocal))
    {
      final boolean bMatch = aEl.hasAttr ("match");
      final boolean bName = aEl.hasAttr ("name");
      if (!bMatch && !bName)
        throw XsltException.at (aEl, "XTSE0500", "xsl:template requires a match or name attribute");
      if (!bMatch && (aEl.hasAttr ("mode") || aEl.hasAttr ("priority")))
        throw XsltException.at (aEl, "XTSE0500", "mode and priority require a match attribute");
      if (aEl.hasAttr ("mode"))
        _validateModeList (aEl, aEl.getAttr ("mode"));
      if (!bName && aEl.hasAttr ("visibility"))
        throw XsltException.at (aEl, "XTSE0500", "A template without a name cannot have a visibility");
    }

    final ICommonsSet <String> aQNames = QNAME_ATTRS.get (sLocal);
    if (aQNames != null)
      for (final String sAttr : aQNames)
      {
        final String v = aEl.getAttr (sAttr);
        if (v != null)
        {
          if (!XsltNames.isEQName (v.trim ()))
            throw XsltException.at (aEl, "XTSE0020", sAttr + "='" + v + "' is not a valid QName");
          _checkPrefixBound (aEl, v);
        }
      }
    if (RESERVED_NS_NAMES.contains (sLocal))
    {
      final String sName = aEl.getAttr ("name");
      if (sName != null && !sName.isEmpty ())
      {
        final XdmQName aName = aEl.resolveQName (sName, false);
        if (aName != null)
        {
          final boolean bInitial = "template".equals (sLocal) &&
                                   XsltNames.NS_XSL.equals (aName.namespaceUri ()) &&
                                   "initial-template".equals (aName.localName ());
          if (!bInitial && (XsltNames.NS_XSL.equals (aName.namespaceUri ()) ||
                            XdmConstants.NS_XML.equals (aName.namespaceUri ()) ||
                            "function".equals (sLocal) && XsltNames.isReservedNamespace (aName.namespaceUri ())))
            throw XsltException.at (aEl, "XTSE0080", "'" + sName + "' uses a reserved namespace");
          if ("function".equals (sLocal) && aName.namespaceUri ().isEmpty ())
            throw XsltException.at (aEl, "XTSE0740", "A stylesheet function must have a prefixed name");
        }
      }
    }
    final ICommonsSet <String> aBools = BOOL_ATTRS.get (sLocal);
    if (aBools != null)
      for (final String sAttr : aBools)
      {
        final String v = aEl.getAttr (sAttr);
        if (v != null)
          _checkBoolean (aEl, sAttr, v);
      }
    final ICommonsSet <String> aBoolAvts = BOOL_AVT_ATTRS.get (sLocal);
    if (aBoolAvts != null)
      for (final String sAttr : aBoolAvts)
      {
        final String v = aEl.getAttr (sAttr);
        if (v != null && v.indexOf ('{') < 0 && v.indexOf ('}') < 0)
          _checkBoolean (aEl, sAttr, v);
      }
    if ("decimal-format".equals (sLocal))
      for (final Map.Entry <XdmQName, String> aEntry : aEl.getAllAttributes ().entrySet ())
        if (aEntry.getKey ().namespaceUri ().isEmpty () &&
            ONE_CHAR_ATTRS.contains (aEntry.getKey ().localName ()) &&
            aEntry.getValue ().codePointCount (0, aEntry.getValue ().length ()) != 1)
          throw XsltException.at (aEl,
                                  "XTSE0020",
                                  "xsl:decimal-format/@" + aEntry.getKey ().localName () + " must be a single character");
    if ("global-context-item".equals (sLocal))
    {
      final String sUse = aEl.getAttr ("use");
      if (sUse != null && !"required".equals (sUse.trim ()) && !"optional".equals (sUse.trim ()) && !"absent".equals (sUse.trim ()))
        throw XsltException.at (aEl, "XTSE0020", "use='" + sUse + "' is not allowed on xsl:global-context-item");
      if (aEl.hasAttr ("as") && sUse != null && "absent".equals (sUse.trim ()))
        throw XsltException.at (aEl, "XTSE3089", "use='absent' cannot be combined with an as attribute");
    }
    if ("output".equals (sLocal))
    {
      final String sStandalone = aEl.getAttr ("standalone");
      if (sStandalone != null && !XsltNames.isXsltBooleanLexical (sStandalone) && !"omit".equals (sStandalone.trim ()))
        throw XsltException.at (aEl, "XTSE0020", "standalone='" + sStandalone + "' is not allowed");
      final String sMethod = aEl.getAttr ("method");
      if (sMethod != null)
      {
        final String s = sMethod.trim ();
        if (!XsltNames.isEQName (s))
          throw XsltException.at (aEl, "XTSE1570", "method='" + sMethod + "' is not a valid QName");
        if (s.indexOf (':') < 0 && !s.startsWith ("Q{"))
          switch (s)
          {
            case "xml", "html", "xhtml", "text", "json", "adaptive":
              break;
            default:
              throw XsltException.at (aEl, "XTSE1570", "method='" + sMethod + "' is not a known output method");
          }
      }
    }
    if ("mode".equals (sLocal))
    {
      _checkVocabulary (aEl, "on-no-match", "deep-copy", "shallow-copy", "deep-skip", "shallow-skip", "text-only-copy", "fail");
      _checkVocabulary (aEl, "on-multiple-match", "use-last", "fail");
      _checkVocabulary (aEl, "typed", "yes", "no", "true", "false", "1", "0", "lax", "strict", "unspecified");
      _checkVocabulary (aEl, "visibility", "public", "private", "final", "abstract");
      if (!aEl.hasAttr ("name") && aEl.hasAttr ("visibility") && !"private".equals (aEl.getAttr ("visibility").trim ()))
        throw XsltException.at (aEl, "XTSE0020", "The unnamed mode can only be private");
    }
    if ("apply-templates".equals (sLocal) && aEl.hasAttr ("mode"))
    {
      final String m = aEl.getAttr ("mode").trim ();
      if (!"#current".equals (m) && !"#default".equals (m) && !"#unnamed".equals (m))
      {
        if (!XsltNames.isEQName (m))
          throw XsltException.at (aEl, "XTSE0020", "mode='" + m + "' is not a valid mode name");
        _checkPrefixBound (aEl, m);
      }
    }

    // Content model
    final ICommonsSet <String> aChildren = aSpec.children ();
    if (aChildren != null)
      for (final XslNode aNode : aEl.getChildren ())
      {
        if (aNode instanceof final XslText aText)
        {
          if (aChildren.isEmpty () && !"text".equals (sLocal) && !aText.getValue ().isEmpty () && aText.isXmlSpacePreserve ())
            throw XsltException.at (aEl, "XTSE0260", "xsl:" + sLocal + " must be empty");
          continue;
        }
        final XslElement aChild = (XslElement) aNode;
        if (aChildren.isEmpty ())
          throw XsltException.at (aEl, aChild.isXsl () ? "XTSE0260" : "XTSE0010", "xsl:" + sLocal + " must be empty");
        if (!aChild.isXsl () || !aChildren.contains (aChild.getLocalName ()))
        {
          if (aChild.isXsl () && aChild.isForwardsCompatible () && !XsltElementSpecs.isKnown (aChild.getLocalName ()))
            continue;
          throw XsltException.at (aChild, "XTSE0010", "<" + aChild.getName ().getLexicalName () + "> is not allowed inside xsl:" + sLocal);
        }
      }
    if (aSpec.noText ())
      for (final XslNode aNode : aEl.getChildren ())
        if (aNode instanceof final XslText aText && !aText.isWhitespaceOnly ())
          throw XsltException.at (aEl, "XTSE0010", "Text is not allowed inside xsl:" + sLocal);

    final Object [] aExcl = SELECT_EXCLUSIVE.get (sLocal);
    if (aExcl != null && aEl.hasAttr ("select"))
    {
      @SuppressWarnings ("unchecked")
      final ICommonsSet <String> aAllowed = (ICommonsSet <String>) aExcl[1];
      for (final XslNode aNode : aEl.getChildren ())
      {
        final boolean bBad = aNode instanceof final XslText aText ? !aText.isWhitespaceOnly ()
                                                                  : !((XslElement) aNode).isXsl () ||
                                                                    !aAllowed.contains (((XslElement) aNode).getLocalName ());
        if (bBad)
          throw XsltException.at (aEl, (String) aExcl[0], "xsl:" + sLocal + " must have empty content when select is present");
      }
    }

    if ("param".equals (sLocal))
    {
      final String sTunnel = aEl.getAttr ("tunnel");
      if (sTunnel != null && XsltNames.isXsltTrue (sTunnel) && aParent != null && aParent.isXsl ())
      {
        final String sP = aParent.getLocalName ();
        if ("stylesheet".equals (sP) || "transform".equals (sP) || "package".equals (sP) || "function".equals (sP))
          throw XsltException.at (aEl, "XTSE0020", "A tunnel parameter is not allowed on xsl:" + sP);
      }
      final String sRequired = aEl.getAttr ("required");
      if (sRequired != null && !XsltNames.isXsltTrue (sRequired) && aParent != null && aParent.isXsl ("function"))
        throw XsltException.at (aEl, "XTSE0020", "A parameter of xsl:function is always required");
      if (sRequired != null && XsltNames.isXsltTrue (sRequired) && aEl.hasAttr ("select"))
        throw XsltException.at (aEl, "XTSE0010", "A required parameter cannot have a select attribute");
      if (aParent != null && aParent.isXsl ("function") && (aEl.hasAttr ("select") || !_isEmptyContent (aEl)))
        throw XsltException.at (aEl, "XTSE0760", "A parameter of xsl:function cannot have a default value");
    }
    if ("param".equals (sLocal) || "variable".equals (sLocal))
    {
      final String sStatic = aEl.getAttr ("static");
      if (sStatic != null && XsltNames.isXsltTrue (sStatic))
      {
        final boolean bTop = aParent != null &&
                             aParent.isXsl () &&
                             ("stylesheet".equals (aParent.getLocalName ()) ||
                              "transform".equals (aParent.getLocalName ()) ||
                              "package".equals (aParent.getLocalName ()) ||
                              "override".equals (aParent.getLocalName ()));
        if (!bTop)
          throw XsltException.at (aEl, "XTSE0090", "static='yes' is only allowed on a top-level xsl:" + sLocal);
        final String sVis = aEl.getAttr ("visibility");
        if (sVis != null && !"private".equals (sVis.trim ()))
          throw XsltException.at (aEl, "XTSE0020", "A static variable must be private");
      }
    }
    if ("function".equals (sLocal))
    {
      final String sNet = aEl.getAttr ("new-each-time");
      if (sNet != null && !"yes".equals (sNet) && !"no".equals (sNet) && !"maybe".equals (sNet))
        throw XsltException.at (aEl, "XTSE0020", "new-each-time='" + sNet + "' is not yes, no or maybe");
      final String sOv = aEl.getAttr ("override");
      final String sOvExt = aEl.getAttr ("override-extension-function");
      if (sOv != null && sOvExt != null && XsltNames.isXsltTrue (sOv) != XsltNames.isXsltTrue (sOvExt))
        throw XsltException.at (aEl, "XTSE0020", "override and override-extension-function conflict");
    }
    final String sExclude = aEl.getAttr ("exclude-result-prefixes");
    if (sExclude != null)
      _checkExcludeResultPrefixes (aEl, sExclude);
    final String sExt = aEl.getAttr ("extension-element-prefixes");
    if (sExt != null)
      for (final String sToken : XsltNames.tokenize (sExt))
      {
        if ("#all".equals (sToken))
          continue;
        final String sUri = aEl.lookupNamespaceUri ("#default".equals (sToken) ? "" : sToken);
        if (sUri == null)
          throw XsltException.at (aEl, "XTSE1430", "The prefix '" + sToken + "' in extension-element-prefixes is not bound");
        if (XsltNames.isReservedNamespace (sUri))
          throw XsltException.at (aEl, "XTSE0800", "The prefix '" + sToken + "' in extension-element-prefixes names a reserved namespace");
      }

    switch (sLocal)
    {
      case "number":
        if (aEl.hasAttr ("value"))
          for (final String s : new String [] { "select", "count", "from", "level" })
            if (aEl.hasAttr (s))
              throw XsltException.at (aEl, "XTSE0975", "The value attribute of xsl:number excludes " + s);
        break;
      case "for-each-group":
      {
        int n = 0;
        for (final String s : new String [] { "group-by", "group-adjacent", "group-starting-with", "group-ending-with" })
          if (aEl.hasAttr (s))
            n++;
        if (n != 1)
          throw XsltException.at (aEl, "XTSE1080", "xsl:for-each-group requires exactly one grouping attribute");
        _checkSortFirst (aEl, "sort", "XTSE0010");
        break;
      }
      case "analyze-string":
      {
        int nState = 0;
        boolean bAny = false;
        for (final XslElement aChild : aEl.getElementChildren ())
          switch (aChild.getLocalName ())
          {
            case "matching-substring":
              if (nState > 0)
                throw XsltException.at (aEl, "XTSE0010", "xsl:matching-substring must come first");
              bAny = true;
              nState = 1;
              break;
            case "non-matching-substring":
              if (nState > 1)
                throw XsltException.at (aEl, "XTSE0010", "xsl:non-matching-substring must precede xsl:fallback");
              bAny = true;
              nState = 2;
              break;
            default:
              nState = 2;
              break;
          }
        if (!bAny)
          throw XsltException.at (aEl, "XTSE1130", "xsl:analyze-string requires xsl:matching-substring or xsl:non-matching-substring");
        break;
      }
      case "choose":
      {
        boolean bWhen = false;
        boolean bOtherwise = false;
        for (final XslElement aChild : aEl.getElementChildren ())
          if (aChild.isXsl ("when"))
          {
            if (bOtherwise)
              throw XsltException.at (aEl, "XTSE0010", "xsl:when must precede xsl:otherwise");
            bWhen = true;
          }
          else
            if (aChild.isXsl ("otherwise"))
            {
              if (bOtherwise)
                throw XsltException.at (aEl, "XTSE0010", "xsl:choose allows at most one xsl:otherwise");
              bOtherwise = true;
            }
        if (!bWhen)
          throw XsltException.at (aEl, "XTSE0010", "xsl:choose requires at least one xsl:when");
        break;
      }
      case "template":
      case "function":
        _checkSortFirst (aEl, "param", "XTSE0010");
        break;
      case "for-each":
      case "perform-sort":
        _checkSortFirst (aEl, "sort", "XTSE0010");
        break;
      default:
        break;
    }

    // Duplicate with-param (XTSE0670) and param (XTSE0580) names
    final ICommonsSet <XdmQName> aWithParams = new CommonsHashSet <> ();
    final ICommonsSet <XdmQName> aParams = new CommonsHashSet <> ();
    for (final XslElement aChild : aEl.getElementChildren ())
    {
      if (!aChild.isXsl ("with-param") && !aChild.isXsl ("param"))
        continue;
      final String sName = aChild.getAttr ("name");
      final XdmQName aName = sName == null ? null : aChild.resolveQName (sName, false);
      if (aName == null)
        continue;
      if (aChild.isXsl ("with-param") ? !aWithParams.add (aName) : !aParams.add (aName))
        throw XsltException.at (aChild,
                                aChild.isXsl ("with-param") ? "XTSE0670" : "XTSE0580",
                                "Duplicate xsl:" + aChild.getLocalName () + " '" + sName + "'");
    }
  }

  private static boolean _isStylesheetRoot (@NonNull final XslElement aEl)
  {
    return aEl.isXsl ("stylesheet") || aEl.isXsl ("transform") || aEl.isXsl ("package");
  }

  /**
   * Validate a module tree.
   *
   * @param aEl
   *        The element to start at (module root).
   */
  public static void validateTree (@NonNull final XslElement aEl)
  {
    if (aEl.isXsl () && aEl.isForwardsCompatible () && !XsltElementSpecs.isKnown (aEl.getLocalName ()))
      return;
    if (aEl.isXsl ())
      _validateXslElement (aEl);
    else
      _validateLiteralResultElement (aEl);
    final boolean bSheet = _isStylesheetRoot (aEl);
    for (final XslNode aNode : aEl.getChildren ())
    {
      if (aNode instanceof final XslText aText)
      {
        if (bSheet && !aText.isWhitespaceOnly ())
          throw XsltException.at (aEl, "XTSE0120", "Text is not allowed at the top level of a stylesheet");
        continue;
      }
      final XslElement aChild = (XslElement) aNode;
      if (bSheet && aChild.isXsl () && !TOP_LEVEL_DECLARATIONS.contains (aChild.getLocalName ()) && aChild.isForwardsCompatible ())
        continue;
      validateTree (aChild);
    }
  }
}
