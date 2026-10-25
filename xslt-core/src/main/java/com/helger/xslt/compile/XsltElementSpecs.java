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
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xslt.tree.XsltNames;

/**
 * The static shape of every XSLT 3.0 element (a port of go-xslt's
 * validate.go table): allowed and required attributes and the content model.
 * It covers the elements of all instruction families, so a family needs no
 * entry of its own.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsltElementSpecs
{
  /**
   * The shape of one element.
   *
   * @param attrs
   *        Allowed attributes in no namespace (besides the standard ones).
   * @param required
   *        Required attributes.
   * @param children
   *        Allowed XSLT child elements, <code>null</code> if unchecked, empty
   *        if the element must be empty.
   * @param noText
   *        <code>true</code> if non-whitespace text is not allowed.
   */
  public record Spec (@NonNull ICommonsSet <String> attrs,
                      @NonNull ICommonsSet <String> required,
                      @Nullable ICommonsSet <String> children,
                      boolean noText)
  {}

  /** The standard attributes allowed on every XSLT element. */
  public static final ICommonsSet <String> STANDARD_ATTRIBUTES = new CommonsHashSet <> ("version",
                                                                                       "exclude-result-prefixes",
                                                                                       "extension-element-prefixes",
                                                                                       "xpath-default-namespace",
                                                                                       "expand-text",
                                                                                       "default-collation",
                                                                                       "use-when",
                                                                                       "default-validation",
                                                                                       "default-mode");

  private static final ICommonsMap <String, Spec> SPECS = new CommonsHashMap <> ();

  @PresentForCodeCoverage
  private static final XsltElementSpecs INSTANCE = new XsltElementSpecs ();

  private XsltElementSpecs ()
  {}

  @NonNull
  private static ICommonsSet <String> _set (@NonNull final String s)
  {
    final ICommonsSet <String> ret = new CommonsHashSet <> ();
    for (final String sToken : XsltNames.tokenize (s))
      ret.add (sToken);
    return ret;
  }

  private static void _add (@NonNull final String sName,
                            @NonNull final String sAttrs,
                            @NonNull final String sRequired,
                            @NonNull final String sChildren,
                            final boolean bNoText)
  {
    final ICommonsSet <String> aChildren = sChildren.isEmpty () ? null : "empty".equals (sChildren) ? new CommonsHashSet <> () : _set (sChildren);
    SPECS.put (sName, new Spec (_set (sAttrs), _set (sRequired), aChildren, bNoText));
  }

  static
  {
    _add ("stylesheet", "id input-type-annotations", "version", "", false);
    _add ("transform", "id input-type-annotations", "version", "", false);
    _add ("package", "id input-type-annotations name package-version declared-modes", "version", "", false);
    _add ("use-package", "name package-version", "name", "accept override", true);
    _add ("accept", "component names visibility", "component names visibility", "empty", true);
    _add ("expose", "component names visibility", "component names visibility", "empty", true);
    _add ("override", "", "", "template function variable param attribute-set", true);
    _add ("template", "match name priority mode as visibility", "", "", false);
    _add ("apply-templates", "select mode", "", "sort with-param", true);
    _add ("apply-imports", "", "", "with-param", true);
    _add ("next-match", "", "", "with-param fallback", true);
    _add ("call-template", "name", "name", "with-param", true);
    _add ("for-each", "select", "select", "", false);
    _add ("for-each-group", "select group-by group-adjacent group-starting-with group-ending-with collation composite bind-group bind-grouping-key", "select", "", false);
    _add ("if", "test", "test", "", false);
    _add ("choose", "", "", "when otherwise", true);
    _add ("when", "test", "test", "", false);
    _add ("otherwise", "", "", "", false);
    _add ("value-of", "select separator disable-output-escaping", "", "", false);
    _add ("copy-of", "select copy-accumulators copy-namespaces type validation", "select", "empty", true);
    _add ("copy", "select copy-namespaces inherit-namespaces use-attribute-sets type validation", "", "", false);
    _add ("text", "disable-output-escaping", "", "empty", false);
    _add ("sequence", "select", "", "", false);
    _add ("variable", "name select as static visibility", "name", "", false);
    _add ("param", "name select as required tunnel static", "name", "", false);
    _add ("with-param", "name select as tunnel", "name", "", false);
    _add ("attribute", "name namespace select separator type validation", "name", "", false);
    _add ("element", "name namespace inherit-namespaces use-attribute-sets type validation", "name", "", false);
    _add ("attribute-set", "name use-attribute-sets visibility streamable", "name", "attribute", true);
    _add ("namespace", "name select", "name", "", false);
    _add ("comment", "select", "", "", false);
    _add ("processing-instruction", "name select", "name", "", false);
    _add ("document", "validation type", "", "", false);
    _add ("sort", "select lang order collation stable case-order data-type", "", "", false);
    _add ("key", "name match use composite collation", "name match", "", false);
    _add ("decimal-format", "name decimal-separator grouping-separator infinity minus-sign exponent-separator NaN percent per-mille zero-digit digit pattern-separator", "", "empty", true);
    _add ("import", "href", "href", "empty", true);
    _add ("include", "href", "href", "empty", true);
    _add ("strip-space", "elements", "elements", "empty", true);
    _add ("preserve-space", "elements", "elements", "empty", true);
    _add ("output", "name method allow-duplicate-names build-tree byte-order-mark cdata-section-elements doctype-public doctype-system encoding escape-uri-attributes html-version include-content-type indent item-separator json-node-output-method media-type normalization-form omit-xml-declaration parameter-document standalone suppress-indentation undeclare-prefixes use-character-maps version", "", "empty", true);
    _add ("namespace-alias", "stylesheet-prefix result-prefix", "stylesheet-prefix result-prefix", "empty", true);
    _add ("character-map", "name use-character-maps", "name", "output-character", true);
    _add ("output-character", "character string", "character string", "empty", true);
    _add ("number", "value select level count from format lang letter-value ordinal start-at grouping-separator grouping-size", "", "", false);
    _add ("message", "select terminate error-code", "", "", false);
    _add ("assert", "test select error-code", "test", "", false);
    _add ("function", "name as visibility override override-extension-function cache new-each-time identity-sensitive streamability", "name", "", false);
    _add ("analyze-string", "select regex flags", "select regex", "matching-substring non-matching-substring fallback", true);
    _add ("matching-substring", "", "", "", false);
    _add ("non-matching-substring", "", "", "", false);
    _add ("fallback", "", "", "", false);
    _add ("where-populated", "", "", "", false);
    _add ("fork", "", "", "sequence for-each-group", true);
    _add ("on-empty", "select", "", "", false);
    _add ("on-non-empty", "select", "", "", false);
    _add ("iterate", "select", "select", "", false);
    _add ("next-iteration", "", "", "with-param", true);
    _add ("break", "select", "", "", false);
    _add ("on-completion", "select", "", "", false);
    _add ("try", "select rollback-output", "", "", false);
    _add ("catch", "errors select", "", "", false);
    _add ("perform-sort", "select", "", "", false);
    _add ("merge", "", "", "merge-source merge-action fallback", true);
    _add ("merge-action", "", "", "", false);
    _add ("merge-key", "select lang order collation case-order data-type", "", "", false);
    _add ("mode", "name streamable use-accumulators on-no-match on-multiple-match warning-on-no-match warning-on-multiple-match typed visibility", "", "empty", true);
    _add ("accumulator", "name initial-value as streamable visibility", "name initial-value", "accumulator-rule", true);
    _add ("accumulator-rule", "match phase select", "match", "", false);
    _add ("source-document", "href streamable use-accumulators validation type", "href", "", false);
    _add ("result-document", "format href validation type method allow-duplicate-names build-tree byte-order-mark cdata-section-elements doctype-public doctype-system encoding escape-uri-attributes html-version include-content-type indent item-separator json-node-output-method media-type normalization-form omit-xml-declaration output-version parameter-document standalone suppress-indentation undeclare-prefixes use-character-maps version", "", "", false);
    _add ("map", "", "", "", false);
    _add ("map-entry", "key select", "key", "", false);
    _add ("evaluate", "xpath as base-uri with-params context-item namespace-context schema-aware", "xpath", "", false);
    _add ("global-context-item", "as use", "", "empty", true);
    _add ("context-item", "as use", "", "empty", true);
    _add ("merge-source", "name for-each-item for-each-source select streamable use-accumulators sort-before-merge validation type", "select", "merge-key", true);
    _add ("import-schema", "namespace schema-location", "", "", false);
  }

  /**
   * @param sLocalName
   *        Local name of an XSLT element.
   * @return Its shape or <code>null</code> if XSLT 3.0 has no such element.
   */
  @Nullable
  public static Spec getSpec (@NonNull final String sLocalName)
  {
    return SPECS.get (sLocalName);
  }

  /**
   * @param sLocalName
   *        Local name.
   * @return <code>true</code> if XSLT 3.0 defines an element of that name.
   */
  public static boolean isKnown (@NonNull final String sLocalName)
  {
    return SPECS.containsKey (sLocalName);
  }
}
