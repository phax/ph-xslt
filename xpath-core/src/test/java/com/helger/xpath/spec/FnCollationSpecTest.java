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
package com.helger.xpath.spec;

import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;

/**
 * Port of go-xslt's <code>fn_collation_test.go</code>: collations (F&amp;O 3.1
 * §5.3) and collation-sensitive substring matching (§5.5).
 * <code>TestCollationHostAPI</code> is not ported: it tests go-xslt's host
 * entry points (<code>ResolveCollator</code>, <code>CollatorForLang</code>),
 * which have no Java counterpart of the same shape.
 *
 * @author Philip Helger
 */
public final class FnCollationSpecTest
{
  private static final String UCA = "http://www.w3.org/2013/collation/UCA";
  private static final String HTML = "http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive";
  private static final String CP = "http://www.w3.org/2005/xpath-functions/collation/codepoint";
  // The UCA tailoring of the F&O 3.1 section 5.5 substring examples
  private static final String BLANK = UCA + "?lang=en;alternate=blanked;strength=primary";

  /**
   * @return A fn:compare call under the given collation URI.
   */
  @NonNull
  private static String _cmp (@NonNull final String sA, @NonNull final String sB, @NonNull final String sUri)
  {
    return "compare('" + sA + "', '" + sB + "', '" + sUri + "')";
  }

  /**
   * F&amp;O 3.1 §5.3.3: the UCA collation family and its parameters. Only
   * orderings that UCA (UTS #10) / CLDR fix unambiguously are asserted.
   */
  @Test
  public void testCollationUCAParameters ()
  {
    run (// strength (§5.3.3 table: primary A=a=Ä=ä; secondary A=a, Ä=ä, ä≠a;
        // tertiary A≠a≠Ä≠ä).
        ok (_cmp ("a", "A", UCA + "?strength=primary"), "0"),
        ok (_cmp ("a", "ä", UCA + "?strength=primary"), "0"),
        ok (_cmp ("A", "ä", UCA + "?strength=1"), "0"),
        ok (_cmp ("a", "A", UCA + "?strength=secondary"), "0"),
        ok (_cmp ("Ä", "ä", UCA + "?strength=2"), "0"),
        ok (_cmp ("a", "ä", UCA + "?strength=secondary"), "-1"),
        ok (_cmp ("a", "A", UCA + "?strength=tertiary"), "-1"),
        ok (_cmp ("a", "A", UCA + "?strength=3"), "-1"),
        ok (_cmp ("a", "A", UCA), "-1"),
        ok (_cmp ("a", "b", UCA), "-1"),
        ok (_cmp ("b", "a", UCA), "1"),
        ok (_cmp ("abc", "abc", UCA), "0"),
        // The REC's fn:compare example (§5.3.6).
        ok ("compare('Strasse', 'Straße', 'http://www.w3.org/2013/collation/UCA?lang=de;strength=primary')", "0"),
        // If two parameters use the same keyword the last one wins.
        ok (_cmp ("a", "A", UCA + "?strength=primary;strength=tertiary"), "-1"),
        ok (_cmp ("a", "A", UCA + "?strength=tertiary;strength=primary"), "0"),
        // caseFirst=upper|lower.
        ok (_cmp ("A", "a", UCA + "?caseFirst=upper"), "-1"),
        ok (_cmp ("a", "A", UCA + "?caseFirst=upper"), "1"),
        ok (_cmp ("a", "A", UCA + "?caseFirst=lower"), "-1"),
        ok (_cmp ("Ab", "ab", UCA + "?caseFirst=upper"), "-1"),
        // case is a tertiary difference: a primary difference still wins.
        ok (_cmp ("a", "B", UCA + "?caseFirst=upper"), "-1"),
        ok (_cmp ("A", "a", UCA + "?caseFirst=upper;strength=secondary"), "0"),
        ok (_cmp ("Ä", "a", UCA + "?caseFirst=upper;strength=secondary"), "1"),
        // caseLevel=yes with primary strength: accents ignored, case kept.
        ok (_cmp ("a", "A", UCA + "?strength=primary;caseLevel=yes"), "-1"),
        ok (_cmp ("a", "á", UCA + "?strength=primary;caseLevel=yes"), "0"),
        ok (_cmp ("a", "a", UCA + "?strength=primary;caseLevel=yes"), "0"),
        // numeric=yes: chap2 sorts before chap12.
        ok (_cmp ("chap2", "chap12", UCA + "?numeric=yes"), "-1"),
        ok (_cmp ("chap2", "chap12", UCA + "?numeric=no"), "1"),
        ok (_cmp ("chap2", "chap12", UCA), "1"),
        ok (_cmp ("Chap2", "chap12", UCA + "?numeric=yes;caseFirst=upper"), "-1"),
        // backwards=yes: the last accent is the most significant (the classic
        // French ordering cote < côte < coté < côté).
        ok (_cmp ("côte", "coté", UCA + "?backwards=yes"), "-1"),
        ok (_cmp ("côte", "coté", UCA + "?backwards=no"), "1"),
        ok (_cmp ("côte", "coté", UCA), "1"),
        ok (_cmp ("cote", "côte", UCA + "?backwards=yes"), "-1"),
        ok (_cmp ("coté", "côté", UCA + "?backwards=yes"), "-1"),
        // CLDR's fr-CA tailoring orders accents backwards by default.
        ok (_cmp ("côte", "coté", UCA + "?lang=fr-CA"), "-1"),
        ok (_cmp ("côte", "coté", UCA + "?lang=fr-CA;backwards=no"), "1"),
        ok (_cmp ("côte", "coté", UCA + "?lang=fr"), "1"),
        // lang: Swedish sorts ä after z; German (and the root) before.
        ok (_cmp ("ä", "z", UCA + "?lang=sv"), "1"),
        ok (_cmp ("ä", "z", UCA + "?lang=de"), "-1"),
        ok (_cmp ("ä", "z", UCA), "-1"),
        // alternate: non-ignorable (data base sorts before datatype), shifted
        // (the variable characters only count at the quaternary level), blanked
        // (only at the identical level).
        ok (_cmp ("data base", "datatype", UCA + "?alternate=non-ignorable"), "-1"),
        ok (_cmp ("data-base", "database", UCA + "?alternate=non-ignorable"), "-1"),
        ok (_cmp ("data-base", "database", UCA + "?alternate=shifted"), "0"),
        ok (_cmp ("data base", "database", UCA + "?alternate=shifted"), "0"),
        ok (_cmp ("data-base", "Database", UCA + "?alternate=shifted"), "-1"),
        ok (_cmp ("data-base", "database", UCA + "?alternate=shifted;strength=quaternary"), "-1"),
        ok (_cmp ("database", "data-base", UCA + "?alternate=shifted;strength=4"), "1"),
        ok (_cmp ("data base", "data-base", UCA + "?alternate=shifted;strength=quaternary"), "-1"),
        ok (_cmp ("data-base", "data-base", UCA + "?alternate=shifted;strength=quaternary"), "0"),
        ok (_cmp ("data-base", "database", UCA + "?alternate=blanked;strength=quaternary"), "0"),
        ok (_cmp ("data-base", "database", UCA + "?alternate=blanked;strength=identical"), "-1"),
        ok (_cmp ("data-base", "data-base", UCA + "?alternate=blanked;strength=5"), "0"),
        ok (_cmp ("data-base", "database", UCA + "?alternate=blanked"), "0"),
        // maxVariable selects the variable groups: space < punct < symbol <
        // currency, each including the earlier ones.
        ok (_cmp ("data base", "database", UCA + "?alternate=shifted;maxVariable=space"), "0"),
        ok (_cmp ("data-base", "database", UCA + "?alternate=shifted;maxVariable=space"), "-1"),
        ok (_cmp ("data-base", "database", UCA + "?alternate=shifted;maxVariable=punct"), "0"),
        ok (_cmp ("a+b", "ab", UCA + "?alternate=shifted;maxVariable=symbol"), "0"),
        ok (_cmp ("a b", "ab", UCA + "?alternate=shifted;maxVariable=symbol"), "0"),
        ok (_cmp ("a$b", "ab", UCA + "?alternate=shifted;maxVariable=currency"), "0"),
        ok (_cmp ("a+b", "ab", UCA + "?alternate=shifted;maxVariable=currency"), "0"),
        ok (_cmp ("a+b", "ab", UCA + "?alternate=shifted;maxVariable=punct"), "-1"),
        // fallback: unknown keywords/values are ignored unless fallback=no.
        ok (_cmp ("a", "A", UCA + "?fallback=yes;strength=primary"), "0"),
        ok (_cmp ("a", "A", UCA + "?foo=bar;strength=primary"), "0"),
        ok (_cmp ("a", "A", UCA + "?strength=bogus"), "-1"),
        ok (_cmp ("a", "b", UCA + "?version=6.2.0"), "-1"),
        ok (_cmp ("a", "b", UCA + "?version=96.5"), "-1"),
        ok (_cmp ("a", "b", UCA + "?fallback=no;version=6.2.0"), "-1"),
        ok (_cmp ("a", "b", UCA + "?reorder=Grek,Latn"), "-1"),
        ok (_cmp ("a", "b", UCA + "?"), "-1"),
        // §5.3.5: an unsupported collation is FOCH0002.
        err (_cmp ("a", "b", "http://example.com/no-such-collation"), "FOCH0002"),
        err (_cmp ("a", "b", UCA + "x?strength=primary"), "FOCH0002"),
        // §5.3.3: with fallback=no an unrecognized keyword or value rejects the
        // collation as unsupported (same error as an unrecognized URI).
        err (_cmp ("a", "b", UCA + "?fallback=no;foo=bar"), "FOCH0002"),
        err (_cmp ("a", "b", UCA + "?fallback=no;strength=bogus"), "FOCH0002"),
        err (_cmp ("a", "b", UCA + "?fallback=no;lang=not!a!language"), "FOCH0002"),
        // fallback=no must use a conformant collation or fail; UCA 96.5 does
        // not exist.
        err (_cmp ("a", "b", UCA + "?fallback=no;version=96.5"), "FOCH0002"));
  }

  /**
   * F&amp;O 3.1 §5.3.4 html-ascii-case-insensitive and the codepoint collation.
   */
  @Test
  public void testCollationHTMLAndCodepoint ()
  {
    run (ok (_cmp ("ABC", "abc", HTML), "0"),
        ok (_cmp ("aBc", "AbC", HTML), "0"),
        // only A-Z / a-z match each other.
        ok (_cmp ("Á", "á", HTML) + " = 0", "false"),
        ok (_cmp ("a", "b", HTML) + " = 0", "false"),
        ok (_cmp ("ABC", "abc", CP), "-1"),
        // The REC's fn:contains-token example (§5.4.?).
        ok ("contains-token(\"red green blue\", \"RED\", \"http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive\")", "true"),
        ok ("contains(\"ABCDEF\", \"cde\", '" + HTML + "')", "true"),
        ok ("contains(\"ABCDEF\", \"xyz\", '" + HTML + "')", "false"),
        ok ("starts-with(\"ABCDEF\", \"abc\", '" + HTML + "')", "true"),
        ok ("starts-with(\"ABCDEF\", \"bcd\", '" + HTML + "')", "false"),
        ok ("ends-with(\"ABCDEF\", \"def\", '" + HTML + "')", "true"),
        ok ("ends-with(\"ABCDEF\", \"cde\", '" + HTML + "')", "false"),
        ok ("substring-before(\"ABCDEF\", \"cd\", '" + HTML + "')", "AB"),
        ok ("substring-after(\"ABCDEF\", \"cd\", '" + HTML + "')", "EF"),
        ok ("substring-after(\"ABCDEF\", \"x\", '" + HTML + "')", ""),
        ok ("contains(\"ABC\", \"B\", '" + CP + "')", "true"),
        ok ("contains(\"ABC\", \"b\", '" + CP + "')", "false"),
        ok ("starts-with(\"ABC\", \"A\", '" + CP + "')", "true"),
        ok ("ends-with(\"ABC\", \"C\", '" + CP + "')", "true"),
        err ("contains(\"a\", \"a\", \"http://example.com/no-such-collation\")", "FOCH0002"),
        err ("starts-with(\"a\", \"a\", \"http://example.com/no-such-collation\")", "FOCH0002"),
        err ("contains(\"a\", \"a\", (\"a\", \"b\"))", "XPTY0004"),
        err ("contains(\"a\", \"a\", ())", "XPTY0004"));
  }

  /**
   * F&amp;O 3.1 §5.5 examples: substring matching on collation units, where
   * characters the collation ignores neither match nor obstruct.
   */
  @Test
  public void testCollationSubstringExamples ()
  {
    run (// §5.5.1 fn:contains.
        ok ("contains(\"abcdefghi\", \"-d-e-f-\", '" + BLANK + "')", "true"),
        ok ("contains(\"a*b*c*d*e*f*g*h*i*\", \"d-ef-\", '" + BLANK + "')", "true"),
        ok ("contains(\"abcd***e---f*--*ghi\", \"def\", '" + BLANK + "')", "true"),
        ok ("contains((), \"--***-*---\", '" + BLANK + "')", "true"),
        ok ("contains(\"abcdefghi\", \"xyz\", '" + BLANK + "')", "false"),
        // §5.5.2 fn:starts-with.
        ok ("starts-with(\"abcdefghi\", \"-a-b-c-\", '" + BLANK + "')", "true"),
        ok ("starts-with(\"a*b*c*d*e*f*g*h*i*\", \"a-bc-\", '" + BLANK + "')", "true"),
        ok ("starts-with(\"abcd***e---f*--*ghi\", \"abcdef\", '" + BLANK + "')", "true"),
        ok ("starts-with((), \"--***-*---\", '" + BLANK + "')", "true"),
        ok ("starts-with(\"-abcdefghi\", \"-abc\", '" + BLANK + "')", "true"),
        ok ("starts-with(\"abcdefghi\", \"bcd\", '" + BLANK + "')", "false"),
        ok ("starts-with(\"abc\", \"abc\", '" + BLANK + "')", "true"),
        // §5.5.3 fn:ends-with.
        ok ("ends-with(\"abcdefghi\", \"-g-h-i-\", '" + BLANK + "')", "true"),
        ok ("ends-with(\"abcd***e---f*--*ghi\", \"defghi\", '" + BLANK + "')", "true"),
        ok ("ends-with((), \"--***-*---\", '" + BLANK + "')", "true"),
        ok ("ends-with(\"abcdefghi\", \"ghi-\", '" + BLANK + "')", "true"),
        ok ("ends-with(\"abcdefghi\", \"fgh\", '" + BLANK + "')", "false"),
        // §5.5.4 fn:substring-before.
        ok ("substring-before(\"abcdefghi\", \"--d-e-\", '" + BLANK + "')", "abc"),
        ok ("substring-before(\"abc--d-e-fghi\", \"--d-e-\", '" + BLANK + "')", "abc--"),
        ok ("substring-before(\"a*b*c*d*e*f*g*h*i*\", \"***cde\", '" + BLANK + "')", "a*b*"),
        ok ("substring-before(\"Eureka!\", \"--***-*---\", '" + BLANK + "')", ""),
        ok ("substring-before(\"abc\", \"x\", '" + BLANK + "')", ""),
        // §5.5.5 fn:substring-after.
        ok ("substring-after(\"abcdefghi\", \"--d-e-\", '" + BLANK + "')", "fghi"),
        ok ("substring-after(\"abc--d-e-fghi\", \"--d-e-\", '" + BLANK + "')", "-fghi"),
        ok ("substring-after(\"a*b*c*d*e*f*g*h*i*\", \"***cde***\", '" + BLANK + "')", "*f*g*h*i*"),
        ok ("substring-after(\"Eureka!\", \"--***-*---\", '" + BLANK + "')", "Eureka!"),
        // Case-insensitive matching under a primary-strength UCA collation.
        ok ("contains(\"Hello World\", \"WORLD\", '" + UCA + "?strength=primary')", "true"),
        ok ("starts-with(\"Hello\", \"hE\", '" + UCA + "?strength=primary')", "true"),
        ok ("ends-with(\"Hello\", \"LO\", '" + UCA + "?strength=primary')", "true"),
        ok ("ends-with(\"Hello\", \"LL\", '" + UCA + "?strength=primary')", "false"),
        // A whole digit run matches under numeric=yes.
        ok ("contains(\"see Chapter-10 now\", \"Chapter-10\", '" + UCA + "?numeric=yes')", "true"),
        ok ("starts-with(\"10 apples\", \"10\", '" + UCA + "?numeric=yes')", "true"),
        ok ("ends-with(\"page 12\", \"12\", '" + UCA + "?numeric=yes')", "true"));
  }

  /**
   * F&amp;O 3.1 §5.5 leaves a match that splits a number under numeric=yes
   * undefined; an implementation may report no match or raise FOCH0004 - it
   * must not report a match, since "10" and "100" are different numbers.
   */
  @Test
  public void testCollationNumericPartialMatch ()
  {
    for (final String sExpr : new String [] { "contains(\"Chapter-100\", \"Chapter-10\", '" + UCA + "?numeric=yes')",
                                              "starts-with(\"100 apples\", \"10\", '" + UCA + "?numeric=yes')",
                                              "ends-with(\"page 112\", \"12\", '" + UCA + "?numeric=yes')" })
    {
      final String sCode = SpecTestHelper.errorCode (sExpr, env ());
      if (sCode != null)
        assertEquals (sExpr, "FOCH0004", sCode);
      else
        assertEquals (sExpr, "false", SpecTestHelper.str (SpecTestHelper.eval (sExpr)));
    }
  }

  /**
   * A relative collation URI is resolved against the static base URI (F&amp;O
   * 3.1 §5.3.5).
   */
  @Test
  public void testCollationRelativeURI ()
  {
    run (env ().staticSetup (x -> x.setBaseUri ("http://www.w3.org/2005/xpath-functions/")),
         ok ("substring-before(\"abcABC\", \"B\", \"collation/html-ascii-case-insensitive\")", "a"));
  }

  /**
   * With no $collation argument the default collation from the context applies
   * (F&amp;O 3.1 §5.3.5 rule 2).
   */
  @Test
  public void testCollationDefaultFromContext ()
  {
    run (env ().staticSetup (x -> x.setDefaultCollation (UCA + "?strength=primary")),
        ok ("contains(\"ABC\", \"b\")", "true"),
        ok ("starts-with(\"ABC\", \"ab\")", "true"),
        ok ("ends-with(\"ABC\", \"bc\")", "true"),
        ok ("substring-before(\"ABC\", \"b\")", "A"),
        ok ("substring-after(\"ABC\", \"b\")", "C"),
        ok ("count(distinct-values((\"a\", \"A\")))", "1"),
        ok ("count(index-of((\"a\", \"A\"), \"a\"))", "2"),
        ok ("deep-equal(\"a\", \"A\")", "true"),
        ok ("max((\"b\", \"A\")) = \"b\"", "true"),
        ok ("compare(\"a\", \"A\")", "0"));
  }

  /**
   * fn:collation-key: collation-key($A, $C) eq collation-key($B, $C) iff
   * compare($A, $B, $C) eq 0, and lt iff compare is -1. Checked pairwise over a
   * set of strings under several collations.
   */
  @Test
  public void testCollationKeyMirrorsCompare ()
  {
    final String [] aWords = { "a", "A", "b", "B", "\u00E4", "\u00C4", "ab", "Ab", "chap2", "chap12", "data-base", "database", "" };
    final String [] aColls = { "",
                               CP,
                               HTML,
                               UCA,
                               UCA + "?strength=primary",
                               UCA + "?strength=secondary",
                               UCA + "?lang=sv",
                               UCA + "?numeric=yes",
                               UCA + "?caseFirst=upper",
                               UCA + "?lang=de;strength=1" };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String sColl : aColls)
    {
      final String sCollArg = sColl.isEmpty () ? "" : ", '" + sColl + "'";
      for (final String sA : aWords)
        for (final String sB : aWords)
        {
          final String sExpr = "let $ka := collation-key('" +
                               sA +
                               "'" +
                               sCollArg +
                               "), $kb := collation-key('" +
                               sB +
                               "'" +
                               sCollArg +
                               "), $c := compare('" +
                               sA +
                               "', '" +
                               sB +
                               "'" +
                               sCollArg +
                               ") return ($ka eq $kb) = ($c eq 0) and ($ka lt $kb) = ($c eq -1)";
          try
          {
            if (!"true".equals (SpecTestHelper.str (SpecTestHelper.eval (sExpr))))
              aErrors.add ("collation '" + sColl + "': collation-key order of '" + sA + "' vs '" + sB + "' disagrees with compare");
          }
          catch (final XPathException ex)
          {
            aErrors.add ("collation '" + sColl + "', '" + sA + "' vs '" + sB + "': " + ex.getMessage ());
          }
        }
    }
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " pairs failed:\n  " + String.join ("\n  ", aErrors));
    run (// The REC examples, $C := UCA?strength=primary.
         ok ("let $C := 'http://www.w3.org/2013/collation/UCA?strength=primary' return map:merge((map{collation-key(\"A\", $C):1}, map{collation-key(\"a\", $C):2}), map{\"duplicates\":\"use-last\"})(collation-key(\"A\", $C))", "2"),
         ok ("let $C := 'http://www.w3.org/2013/collation/UCA?strength=primary' return let $M := map{collation-key(\"A\", $C):1, collation-key(\"B\", $C):2} return $M(collation-key(\"a\", $C))", "1"),
         ok ("collation-key(\"abc\") instance of xs:base64Binary", "true"),
         err ("collation-key(\"a\", \"http://example.com/no-such-collation\")", "FOCH0002"),
         err ("collation-key(\"a\", \"" + UCA + "?fallback=no;foo=bar\")", "FOCH0002"));
  }
}
