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

import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Port of go-xslt <code>internal/xpath/fn_spec_fixes_test.go</code> (commit 04ccada):
 * regression tests for wrong results on valid input, each expectation taken
 * from the F&amp;O 3.1 REC text quoted beside it.
 *
 * @author Philip Helger
 */
public final class FnSpecFixesSpecTest
{

  @Test
  public void replaceQFlagLiteralReplacement ()
  {
    // §5.6.4: "If the q flag is present, the replacement string is used as
    // is." — with 'i' too, the pattern stays literal as well.
    run (
      ok ("replace(\"A.b\", \"a.\", \"\\\", \"iq\")", "\\b"),
      ok ("replace(\"A.b\", \"a.\", \"$1\", \"iq\")", "$1b"),
      ok ("replace(\"AxB\", \"a.\", \"z\", \"iq\")", "AxB"),
      ok ("replace(\"a.b\", \"a.\", \"\\\", \"q\")", "\\b")
    );
  }

  @Test
  public void namespaceURIForPrefixUndeclaredDefault ()
  {
    // §10.2.5: "If $element has no default namespace, the function returns
    // the empty sequence." — xmlns="" undeclares it (the REC's own example).
    run ("<z:a xmlns=\"http://example.org/one\" xmlns:z=\"http://example.org/two\"><b xmlns=\"\"/></z:a>",
      ok ("count(namespace-uri-for-prefix(\"\", //b))", "0"),
      ok ("count(namespace-uri-for-prefix((), //b))", "0"),
      ok ("namespace-uri-for-prefix(\"\", /*)", "http://example.org/one"),
      ok ("namespace-uri-for-prefix(\"z\", //b)", "http://example.org/two")
    );
  }

  @Test
  public void formatDateWidthAndDefaults ()
  {
    run (
      ok ("format-date(xs:date('2002-12-31'), '[FNn,3-3]')", "Tue"),
      // §9.8.4.4: the year IS reduced modulo 10^max-width.
      ok ("format-date(xs:date('2002-12-31'), '[Y,2-2]')", "02"),
      ok ("format-date(xs:date('2002-12-31'), '[Y01]')", "02")
    );
  }

  @Test
  @Ignore ("format-date: default presentation of F, ignored maximum width and name padding not fixed yet (date/time functions are another workstream)")
  public void formatDateWidthAndDefaultsOpen ()
  {
    run (
      // §9.8.4.1 table: the default presentation modifier of F is n.
      ok ("format-date(xs:date('2002-12-31'), '[F]')", "tuesday"),
      // §9.8.4.3: with a width modifier on a decimal digit pattern "The
      // maximum width, if specified, is ignored."
      ok ("format-date(xs:date('2002-12-31'), '[D,1-1]')", "31"),
      ok ("format-date(xs:date('2002-12-31'), '[M,1-1]')", "12"),
      // §9.8.4.3: "If FN is shorter than the minimum width, then it is
      // padded by appending spaces to the end of the name."
      ok ("format-date(xs:date('2002-12-31'), '[MNn,10]')", "December  ")
    );
  }

  @Test
  public void currentDateTimeIsDateTimeStamp ()
  {
    // §15.3: fn:current-dateTime() as xs:dateTimeStamp; the operators on it
    // still return plain xs:dateTime.
    run (
      ok ("current-dateTime() instance of xs:dateTimeStamp", "true"),
      ok ("current-dateTime() instance of xs:dateTime", "true"),
      ok ("adjust-dateTime-to-timezone(current-dateTime(), ()) instance of xs:dateTimeStamp", "false"),
      ok ("current-dateTime() eq current-dateTime()", "true")
    );
  }

  @Test
  @Ignore ("op:add-dayTimeDuration-to-dateTime keeps xs:dateTimeStamp; not fixed yet (date/time arithmetic is another workstream)")
  public void currentDateTimeIsDateTimeStampOpen ()
  {
    // §15.3 / §10.8.10: adding a duration to an xs:dateTimeStamp gives a
    // plain xs:dateTime.
    run (ok ("(current-dateTime() + xs:dayTimeDuration('PT1H')) instance of xs:dateTimeStamp", "false"));
  }

  @Test
  public void randomNumberGeneratorFunctionsAnonymous ()
  {
    // §4.9.1: next and permute have "name: absent"; §16.1.2 function-name
    // returns () for an anonymous function.
    run (
      ok ("count(function-name(random-number-generator()?next))", "0"),
      ok ("count(function-name(random-number-generator()?permute))", "0"),
      ok ("function-arity(random-number-generator()?permute)", "1")
    );
  }
}
