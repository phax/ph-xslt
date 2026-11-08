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
package com.helger.phoxtrot.xpath.bench;

import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.id.IHasID;
import com.helger.base.lang.EnumHelper;

/**
 * The XPath workloads that evaluate one expression with the document node as context item (see
 * {@link XPathWorkload}). Each expression returns one string or number, which is compared with the
 * result computed in Java.
 *
 * @author Philip Helger
 */
public enum EXPathCase implements IHasID <String>
{
  /** Child steps from the root: <code>/invoice/lines/line/item</code>. */
  CHILD ("child", "count(/invoice/lines/line/item)", aData -> Integer.toString (aData.getLineCount ())),
  /** <code>//name</code> from the root and descendant steps from every line. */
  DESCENDANT ("descendant",
              "count(//price) + count(//line//start)",
              aData -> Integer.toString (2 * aData.getLineCount ())),
  /** Positional predicates on the long child list of <code>lines</code>. */
  POSITIONAL ("positional",
              "let $c := count(/invoice/lines/line) " +
                            "return sum(for $i in 1 to 200 return xs:integer(/invoice/lines/line[max((1, $i * $c idiv 200))]/@id))" +
                            " || '/' || count(/invoice/lines/line[position() mod 10 = 0])" +
                            " || '/' || /invoice/lines/line[last()]/@id",
              BenchExpected::xpathPositional),
  /** Two chained value predicates, one with a cast. */
  PREDICATE ("predicate", "count(//line[@cat = 'A'][xs:decimal(price) gt 50])", BenchExpected::xpathPredicate),
  /**
   * Existence tests (early exit and empty paths by name index): a hit early in the document, a
   * missing element and attribute name, a full scan without hit, a hit late in a path.
   */
  EXISTS ("exists",
          "string-join((exists(//line[tax/@rate = '0']), empty(/invoice/lines/line/discount), not(//@missing)," +
                    " exists(/invoice/lines/line[sku = 'ZZZ-0000']), boolean(//line[@cat = 'C']/note)) ! string(.), ',')",
          BenchExpected::xpathExists),
  /** String functions per line: <code>concat</code>, <code>upper-case</code>, <code>substring</code>, <code>normalize-space</code>. */
  STRINGS ("strings",
           "sum(for $l in /invoice/lines/line return string-length(concat(upper-case($l/item), '-', substring($l/sku, 1, 3), '-', normalize-space($l/note))))",
           BenchExpected::xpathStrings),
  /** <code>matches</code> and <code>replace</code> per line. */
  REGEX ("regex",
         "count(/invoice/lines/line[matches(sku, '^[A-Z]{3}-[0-9]{4}$')])" +
                  " || '/' || string-length(string-join(/invoice/lines/line/item ! replace(., '([a-z]+) ([0-9]+)', '$2-$1-$2')))",
         BenchExpected::xpathRegex),
  /** Decimal, integer and double arithmetic and comparisons per line. */
  ARITHMETIC ("arithmetic",
              "sum(for $l in /invoice/lines/line return xs:decimal($l/qty) * xs:decimal($l/price))" +
                            " || '/' || count(/invoice/lines/line[xs:decimal(qty) * xs:decimal(price) ge 100])" +
                            " || '/' || sum(/invoice/lines/line/xs:integer(qty)) idiv 7" +
                            " || '/' || count(/invoice/lines/line[number(price) lt 20.5])",
              BenchExpected::xpathArithmetic),
  /** <code>for</code>, <code>let</code>, quantified expressions and date arithmetic. */
  FLWOR ("flwor",
         "string-join(for $c in sort(distinct-values(/invoice/lines/line/@cat))" +
                  " return let $ls := /invoice/lines/line[@cat = $c] return $c || '=' || count($ls) || ':' || sum($ls/xs:integer(qty)), ',')" +
                  " || '/' || sum(for $l in /invoice/lines/line, $p in $l/period return days-from-duration(xs:date($p/end) - xs:date($p/start)))" +
                  " || '/' || (every $l in /invoice/lines/line satisfies exists($l/@id))" +
                  " || '/' || (some $l in /invoice/lines/line satisfies xs:decimal($l/price) gt 199)",
         BenchExpected::xpathFlwor),
  /** Building and reading maps and arrays, inline functions with <code>fold-left</code>. */
  MAPS ("maps",
        "let $m := map:merge(for $l in /invoice/lines/line return map:entry(string($l/sku), xs:integer($l/qty)), map { 'duplicates': 'combine' })," +
                " $a := array:join(for $l in /invoice/lines/line return [xs:decimal($l/price)])," +
                " $cnt := fold-left(/invoice/lines/line, map { }, function ($acc, $l) { let $k := string($l/@cat) return map:put($acc, $k, ($acc($k), 0)[1] + 1) })" +
                " return map:size($m) || '/' || sum(map:keys($m) ! sum($m(.))) || '/' || array:size($a)" +
                " || '/' || array:fold-left($a, 0, function ($s, $p) { $s + $p })" +
                " || '/' || $cnt('A') || ',' || $cnt('B') || ',' || $cnt('C')",
        BenchExpected::xpathMaps);

  private final String m_sID;
  private final String m_sExpression;
  private final Function <BenchData, String> m_aExpected;

  EXPathCase (@NonNull final String sID,
              @NonNull final String sExpression,
              @NonNull final Function <BenchData, String> aExpected)
  {
    m_sID = sID;
    m_sExpression = sExpression;
    m_aExpected = aExpected;
  }

  @NonNull
  public String getID ()
  {
    return m_sID;
  }

  /** @return The XPath expression. Never <code>null</code>. */
  @NonNull
  public String getExpression ()
  {
    return m_sExpression;
  }

  /**
   * @param aData
   *        The document the expression is evaluated against.
   * @return The expected string value of the result. Never <code>null</code>.
   */
  @NonNull
  public String getExpected (@NonNull final BenchData aData)
  {
    return m_aExpected.apply (aData);
  }

  @Nullable
  public static EXPathCase getFromIDOrNull (@Nullable final String sID)
  {
    return EnumHelper.getFromIDOrNull (EXPathCase.class, sID);
  }
}
