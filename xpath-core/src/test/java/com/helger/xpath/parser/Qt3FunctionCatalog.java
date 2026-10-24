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
package com.helger.xpath.parser;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.XdmConstants;

/**
 * The names and arities of all F&amp;O 3.1 functions (fn, math, map, array)
 * and the xs constructor functions, registered with dummy implementations.
 * Used by the QT3 parse smoke test, which needs XPST0017 checks against the
 * complete catalogue while the real function families are not yet ported. The
 * fn arity ranges are taken from go-xslt's <code>fn_arity.go</code>.
 *
 * @author Philip Helger
 */
final class Qt3FunctionCatalog
{
  /** "name minArity maxArity"; -1 means variadic. */
  private static final String [] FN = { "error 0 3",
                                            "trace 1 2",
                                            "doc 1 1",
                                            "doc-available 1 1",
                                            "collection 0 1",
                                            "uri-collection 0 1",
                                            "parse-xml 1 1",
                                            "parse-xml-fragment 1 1",
                                            "unparsed-text 1 2",
                                            "unparsed-text-lines 1 2",
                                            "unparsed-text-available 1 2",
                                            "environment-variable 1 1",
                                            "available-environment-variables 0 0",
                                            "transform 1 1",
                                            "load-xquery-module 1 2",
                                            "random-number-generator 0 1",
                                            "idref 1 2",
                                            "id 1 2",
                                            "element-with-id 1 2",
                                            "function-lookup 2 2",
                                            "function-name 1 1",
                                            "function-arity 1 1",
                                            "name 0 1",
                                            "local-name 0 1",
                                            "namespace-uri 0 1",
                                            "string 0 1",
                                            "data 0 1",
                                            "number 0 1",
                                            "string-length 0 1",
                                            "normalize-space 0 1",
                                            "document-uri 0 1",
                                            "base-uri 0 1",
                                            "static-base-uri 0 0",
                                            "root 0 1",
                                            "abs 1 1",
                                            "floor 1 1",
                                            "ceiling 1 1",
                                            "round 1 2",
                                            "round-half-to-even 1 2",
                                            "substring 2 3",
                                            "concat 2 -1",
                                            "contains-token 2 3",
                                            "node-name 0 1",
                                            "nilled 0 1",
                                            "format-integer 2 3",
                                            "format-number 2 3",
                                            "codepoints-to-string 1 1",
                                            "string-to-codepoints 1 1",
                                            "compare 2 3",
                                            "codepoint-equal 2 2",
                                            "collation-key 1 2",
                                            "string-join 1 2",
                                            "normalize-unicode 1 2",
                                            "upper-case 1 1",
                                            "lower-case 1 1",
                                            "translate 3 3",
                                            "contains 2 3",
                                            "starts-with 2 3",
                                            "ends-with 2 3",
                                            "substring-before 2 3",
                                            "substring-after 2 3",
                                            "matches 2 3",
                                            "replace 3 4",
                                            "tokenize 1 3",
                                            "analyze-string 2 3",
                                            "resolve-uri 1 2",
                                            "encode-for-uri 1 1",
                                            "iri-to-uri 1 1",
                                            "escape-html-uri 1 1",
                                            "true 0 0",
                                            "false 0 0",
                                            "boolean 1 1",
                                            "not 1 1",
                                            "lang 1 2",
                                            "years-from-duration 1 1",
                                            "months-from-duration 1 1",
                                            "days-from-duration 1 1",
                                            "hours-from-duration 1 1",
                                            "minutes-from-duration 1 1",
                                            "seconds-from-duration 1 1",
                                            "dateTime 2 2",
                                            "year-from-dateTime 1 1",
                                            "month-from-dateTime 1 1",
                                            "day-from-dateTime 1 1",
                                            "hours-from-dateTime 1 1",
                                            "minutes-from-dateTime 1 1",
                                            "seconds-from-dateTime 1 1",
                                            "timezone-from-dateTime 1 1",
                                            "year-from-date 1 1",
                                            "month-from-date 1 1",
                                            "day-from-date 1 1",
                                            "timezone-from-date 1 1",
                                            "hours-from-time 1 1",
                                            "minutes-from-time 1 1",
                                            "seconds-from-time 1 1",
                                            "timezone-from-time 1 1",
                                            "adjust-dateTime-to-timezone 1 2",
                                            "adjust-date-to-timezone 1 2",
                                            "adjust-time-to-timezone 1 2",
                                            "current-date 0 0",
                                            "current-time 0 0",
                                            "current-dateTime 0 0",
                                            "implicit-timezone 0 0",
                                            "format-dateTime 2 5",
                                            "format-date 2 5",
                                            "format-time 2 5",
                                            "parse-ietf-date 1 1",
                                            "resolve-QName 2 2",
                                            "QName 2 2",
                                            "prefix-from-QName 1 1",
                                            "local-name-from-QName 1 1",
                                            "namespace-uri-from-QName 1 1",
                                            "namespace-uri-for-prefix 2 2",
                                            "in-scope-prefixes 1 1",
                                            "path 0 1",
                                            "has-children 0 1",
                                            "innermost 1 1",
                                            "outermost 1 1",
                                            "generate-id 0 1",
                                            "empty 1 1",
                                            "exists 1 1",
                                            "head 1 1",
                                            "tail 1 1",
                                            "insert-before 3 3",
                                            "remove 2 2",
                                            "reverse 1 1",
                                            "subsequence 2 3",
                                            "unordered 1 1",
                                            "distinct-values 1 2",
                                            "index-of 2 3",
                                            "deep-equal 2 3",
                                            "zero-or-one 1 1",
                                            "one-or-more 1 1",
                                            "exactly-one 1 1",
                                            "count 1 1",
                                            "avg 1 1",
                                            "max 1 2",
                                            "min 1 2",
                                            "sum 1 2",
                                            "serialize 1 2",
                                            "position 0 0",
                                            "last 0 0",
                                            "default-collation 0 0",
                                            "default-language 0 0",
                                            "for-each 2 2",
                                            "filter 2 2",
                                            "fold-left 3 3",
                                            "fold-right 3 3",
                                            "for-each-pair 3 3",
                                            "sort 1 3",
                                            "apply 2 2",
                                            "parse-json 1 2",
                                            "json-doc 1 2",
                                            "json-to-xml 1 2",
                                            "xml-to-json 1 2" };

  private static final String [] MATH = { "pi 0 0",
                                          "exp 1 1",
                                          "exp10 1 1",
                                          "log 1 1",
                                          "log10 1 1",
                                          "pow 2 2",
                                          "sqrt 1 1",
                                          "sin 1 1",
                                          "cos 1 1",
                                          "tan 1 1",
                                          "asin 1 1",
                                          "acos 1 1",
                                          "atan 1 1",
                                          "atan2 2 2" };

  private static final String [] MAP = { "merge 1 2",
                                         "size 1 1",
                                         "keys 1 1",
                                         "contains 2 2",
                                         "get 2 2",
                                         "find 2 2",
                                         "put 3 3",
                                         "entry 2 2",
                                         "remove 2 2",
                                         "for-each 2 2" };

  private static final String [] ARRAY = { "size 1 1",
                                           "get 2 2",
                                           "put 3 3",
                                           "append 2 2",
                                           "subarray 2 3",
                                           "remove 2 2",
                                           "insert-before 3 3",
                                           "head 1 1",
                                           "tail 1 1",
                                           "reverse 1 1",
                                           "join 1 1",
                                           "for-each 2 2",
                                           "filter 2 2",
                                           "fold-left 3 3",
                                           "fold-right 3 3",
                                           "for-each-pair 3 3",
                                           "sort 1 3",
                                           "flatten 1 1" };

  @PresentForCodeCoverage
  private static final Qt3FunctionCatalog INSTANCE = new Qt3FunctionCatalog ();

  private Qt3FunctionCatalog ()
  {}

  private static void _register (@NonNull final FunctionLibrary aLib,
                                 @NonNull final String sNs,
                                 @NonNull final String [] aRows)
  {
    for (final String sRow : aRows)
    {
      final String [] aParts = sRow.split (" ");
      final int nMin = Integer.parseInt (aParts[1]);
      final int nMax = Integer.parseInt (aParts[2]);
      // Variadic functions: register the minimum; the parser knows fn:concat
      ParserTestHelper.register (aLib, sNs, aParts[0], nMin);
      for (int n = nMin + 1; n <= nMax; ++n)
        if (!(aParts[0].startsWith ("format-date") || aParts[0].equals ("format-time")) || n == 5)
          ParserTestHelper.register (aLib, sNs, aParts[0], n);
    }
  }

  @NonNull
  static FunctionLibrary createLibrary ()
  {
    final FunctionLibrary ret = new FunctionLibrary ();
    _register (ret, XdmConstants.NS_FN, FN);
    _register (ret, FunctionLibrary.NS_MATH, MATH);
    _register (ret, FunctionLibrary.NS_MAP, MAP);
    _register (ret, FunctionLibrary.NS_ARRAY, ARRAY);
    for (final EAtomicType e : EAtomicType.values ())
      if (e != EAtomicType.NOTATION && e != EAtomicType.ANY_ATOMIC_TYPE)
        ParserTestHelper.register (ret, XdmConstants.NS_XS, e.getLocalName (), 1);
    for (final String s : new String [] { "NMTOKENS", "IDREFS", "ENTITIES" })
      ParserTestHelper.register (ret, XdmConstants.NS_XS, s, 1);
    return ret;
  }
}
