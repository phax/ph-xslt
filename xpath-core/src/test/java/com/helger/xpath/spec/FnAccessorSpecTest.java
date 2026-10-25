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

import static org.junit.Assert.assertTrue;

import static com.helger.xpath.spec.FnCoreTestHelper.coreRun;
import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.eval;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;

/**
 * Port of go-xslt <code>internal/xpath/fn_accessor_spec_test.go</code> (commit 04ccada).
 *
 * @author Philip Helger
 */
public final class FnAccessorSpecTest
{
  /**
   * Covers the accessor functions of F&O 3.1 §2 (node-name,
   * nilled, data, base-uri, document-uri) and fn:static-base-uri (the
   * context functions).
   */
  @Test
  public void accessorSpec ()
  {
    final String sDoc = "<r xmlns:p=\"urn:p\" xmlns=\"urn:d\"><a p:x=\"1\">t<!--c--><?pi d?></a></r>";
    coreRun (sDoc,
      // §2.1 fn:node-name
      ok ("local-name-from-QName(node-name(/*))", "r"),
      ok ("namespace-uri-from-QName(node-name(/*))", "urn:d"),
      ok ("namespace-uri-from-QName(node-name(//@*:x))", "urn:p"),
      ok ("local-name-from-QName(node-name(//processing-instruction()))", "pi"),
      ok ("local-name-from-QName(node-name(/*/namespace::p))", "p"),
      ok ("namespace-uri-from-QName(node-name(/*/namespace::p))", ""),
      // document, text, comment and default-namespace nodes have no name
      ok ("node-name(/)", "()"),
      ok ("node-name(//text())", "()"),
      ok ("node-name(//comment())", "()"),
      ok ("node-name(/*/namespace::*[local-name() = ''])", "()"),
      ok ("node-name(())", "()"),
      ok ("/*/*/node-name() ! local-name-from-QName(.)", "a"),
      err ("node-name(\"string\")", "XPTY0004"),
      err ("(1)!node-name()", "XPTY0004"),

      // §2.2 fn:nilled: an unvalidated element is never nilled; other kinds ()
      ok ("nilled(/*)", "false"),
      ok ("nilled(//text())", "()"),
      ok ("nilled(//@*:x)", "()"),
      ok ("nilled(/)", "()"),
      ok ("nilled(())", "()"),
      ok ("/*/nilled()", "false"),
      err ("nilled(1)", "XPTY0004"),
      err ("nilled(/*/*/node())", "XPTY0004"),
      err ("(1)!nilled()", "XPTY0004"),

      // §2.4 fn:data
      ok ("data(/*)", "t"),
      ok ("data((1, 'a', //@*:x))", "1|a|1"),
      ok ("data(//@*:x) instance of xs:untypedAtomic", "true"),
      ok ("data([1, [2, 3]])", "1|2|3"),
      ok ("/*/*/@*/data()", "1"),

      // §2.5 fn:base-uri / §2.6 fn:document-uri with no base anywhere
      ok ("base-uri(())", "()"),
      ok ("base-uri(/*/namespace::p)", "()"),
      err ("(1)!base-uri()", "XPTY0004"),
      ok ("document-uri(())", "()"),
      ok ("document-uri(/*)", "()"),
      ok ("document-uri(/)", "()"),

      // static-base-uri: absent here
      ok ("static-base-uri()", "()")
    );

    // With a static base URI and a retrieved document.
    final FnSpecResolver aRes = new FnSpecResolver ().doc ("http://example.com/docs/r.xml",
                                                          FnSpecResolver.doc ("<r><e/></r>", "http://example.com/docs/r.xml"));
    coreRun (env ().staticSetup (aSC -> aSC.setBaseUri ("http://example.com/q/main.xq")).dynamicSetup (aCtx -> {
      aCtx.setResourceResolver (aRes);
      return aCtx;
    }),
      ok ("static-base-uri()", "http://example.com/q/main.xq"),
      ok ("static-base-uri() instance of xs:anyURI", "true"),
      ok ("document-uri(doc('http://example.com/docs/r.xml'))", "http://example.com/docs/r.xml"),
      ok ("document-uri(doc('http://example.com/docs/r.xml')) instance of xs:anyURI", "true"),
      ok ("doc('http://example.com/docs/r.xml')/document-uri()", "http://example.com/docs/r.xml"),
      ok ("document-uri(doc('http://example.com/docs/r.xml')/r)", "()"),
      ok ("base-uri(doc('http://example.com/docs/r.xml')//e)", "http://example.com/docs/r.xml"),
      ok ("doc('http://example.com/docs/r.xml')//e/base-uri()", "http://example.com/docs/r.xml")
    );
  }

  /**
   * Covers fn:error and fn:trace (F&O 3.1 §3.1.1, §3.2.1)
   * and math:exp10 (§4.8.3).
   */
  @Test
  public void errorTraceSpec ()
  {
    final String sNsErr = "http://www.w3.org/2005/xqt-errors";
    // expression, code namespace, code local name, description, rendering of
    // the error object ("-" for none)
    final String [] [] aCases = { // fn:error() raises err:FOER0000
                                  { "error()", sNsErr, "FOER0000", "", "-" },
                                  { "error(())", sNsErr, "FOER0000", "", "-" },
                                  { "error((), 'only a description')", sNsErr, "FOER0000", "only a description", "-" },
                                  // the REC example
                                  { "error(QName('http://www.example.com/HR', 'myerr:toohighsal'), 'Does not apply because salary is too high')",
                                    "http://www.example.com/HR",
                                    "toohighsal",
                                    "Does not apply because salary is too high",
                                    "-" },
                                  { "error(QName('urn:e', 'E1'), 'd', (1, 2))", "urn:e", "E1", "d", "1|2" },
                                  { "error(QName('urn:e', 'E1'), '', 'v')", "urn:e", "E1", "", "v" } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
    {
      try
      {
        eval (aCase[0]);
        aErrors.add (aCase[0] + ": no error");
      }
      catch (final XPathException ex)
      {
        final XdmQName aCode = ex.getCodeName ();
        if (!aCode.namespaceUri ().equals (aCase[1]) || !aCode.localName ().equals (aCase[2]))
          aErrors.add (aCase[0] + ": code " + aCode + ", want Q{" + aCase[1] + "}" + aCase[2]);
        final String sDesc = ex.getDescription () == null ? "" : ex.getDescription ();
        if (!sDesc.equals (aCase[3]))
          aErrors.add (aCase[0] + ": description '" + sDesc + "', want '" + aCase[3] + "'");
        final XdmSequence aValue = ex.getErrorObject ();
        if (aCase[4].equals ("-"))
        {
          if (aValue != null && !aValue.isEmpty ())
            aErrors.add (aCase[0] + ": unexpected error object " + FnCoreTestHelper.render (aValue));
        }
        else
          if (aValue == null || !FnCoreTestHelper.render (aValue).equals (aCase[4]))
            aErrors.add (aCase[0] + ": error object " + aValue + ", want " + aCase[4]);
        if (!ex.getMessage ().contains (aCase[2]))
          aErrors.add (aCase[0] + ": message '" + ex.getMessage () + "' lacks the code " + aCase[2]);
      }
    }
    assertTrue (aErrors.toString (), aErrors.isEmpty ());
    // go-xslt expects error(QName('urn:e', 'E1'), (), 'v') to raise Q{urn:e}E1,
    // but $description is xs:string (F&O 3.1 §3.1.1), so the empty sequence
    // fails the function conversion rules first.
    run (err ("error(QName('urn:e', 'E1'), (), 'v')", "XPTY0004"));

    // fn:trace returns its first argument unchanged (its output goes to a
    // processor log).
    coreRun (
      ok ("trace((1, 2, 3), 'label')", "1|2|3"),
      ok ("trace('x')", "x"),
      ok ("trace(())", "()"),
      // §4.8.3 math:exp10
      ok ("math:exp10(())", "()"),
      ok ("math:exp10(0)", "1"),
      ok ("math:exp10(1)", "10"),
      ok ("math:exp10(0.5)", "3.1622776601683795"),
      ok ("math:exp10(-1)", "0.1"),
      ok ("math:exp10(0) instance of xs:double", "true")
    );
  }
}
