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

import org.junit.Test;

/**
 * Port of go-xslt's <code>fn_qname_spec_test.go</code>: the QName functions of
 * F&amp;O 3.1 §10.1-§10.2.
 *
 * @author Philip Helger
 */
public final class FnQNameSpecTest
{
  /**
   * The error conditions and edge cases of the F&amp;O 3.1 §10.1-§10.2 QName
   * functions.
   */
  @Test
  public void testQNameFunctionsSpec ()
  {
    FnCoreTestHelper.coreRun (env ("<r xmlns:p=\"urn:p\" xmlns=\"urn:d\"><c xmlns=\"\"><e xmlns:q=\"urn:q\" a=\"1\"/></c></r>"),
                             // §10.1.2 fn:QName
                             ok ("namespace-uri-from-QName(QName(\"http://www.example.com/example\", \"person\"))", "http://www.example.com/example"),
                             ok ("prefix-from-QName(QName(\"http://www.example.com/example\", \"ht:person\"))", "ht"),
                             ok ("local-name-from-QName(QName(\"\", \"person\"))", "person"),
                             ok ("local-name-from-QName(QName((), \"person\"))", "person"),
                             ok ("QName(\"urn:x\", \"a:b\") eq QName(\"urn:x\", \"c:b\")", "true"),
                             ok ("QName(\"urn:x\", \"b\") eq QName(\"urn:y\", \"b\")", "false"),
                             err ("QName(\"urn:x\", \"a b\")", "FOCA0002"),
                             err ("QName(\"urn:x\", \"1a\")", "FOCA0002"),
                             err ("QName(\"\", \"p:a\")", "FOCA0002"),
                             err ("QName((), \"p:a\")", "FOCA0002"),
                             err ("QName(1, \"a\")", "XPTY0004"),
                             err ("QName(\"urn:x\", 1)", "XPTY0004"),
                             // §10.2.2-§10.2.4 accessors; the result types are xs:NCName/xs:anyURI.
                             ok ("prefix-from-QName(QName(\"urn:x\", \"a\"))", "()"),
                             ok ("prefix-from-QName(QName(\"urn:x\", \"p:a\")) instance of xs:NCName", "true"),
                             ok ("local-name-from-QName(QName(\"urn:x\", \"p:a\")) instance of xs:NCName", "true"),
                             ok ("namespace-uri-from-QName(QName(\"urn:x\", \"p:a\")) instance of xs:anyURI", "true"),
                             err ("local-name-from-QName(1)", "XPTY0004"),
                             err ("prefix-from-QName(xs:date(\"2020-01-01\"))", "XPTY0004"),
                             err ("namespace-uri-from-QName((QName(\"a\", \"b\"), QName(\"a\", \"c\")))", "XPTY0004"),
                             err ("local-name-from-QName((QName(\"a\", \"b\"), QName(\"a\", \"c\")))", "XPTY0004"),
                             // §10.2.5 fn:namespace-uri-for-prefix
                             ok ("namespace-uri-for-prefix(\"p\", //e)", "urn:p"),
                             ok ("namespace-uri-for-prefix(\"q\", //e)", "urn:q"),
                             ok ("namespace-uri-for-prefix(\"xml\", //e)", "http://www.w3.org/XML/1998/namespace"),
                             ok ("namespace-uri-for-prefix(\"\", /*)", "urn:d"),
                             ok ("namespace-uri-for-prefix((), /*)", "urn:d"),
                             // q is declared only on a descendant.
                             ok ("namespace-uri-for-prefix(\"q\", /*)", "()"),
                             err ("namespace-uri-for-prefix(\"p\", //@a)", "XPTY0004"),
                             err ("namespace-uri-for-prefix(\"p\", 1)", "XPTY0004"),
                             err ("namespace-uri-for-prefix(\"p\", ())", "XPTY0004"),
                             // §10.2.6 fn:in-scope-prefixes ("xml" is always in scope).
                             ok ("string-join(sort(in-scope-prefixes(//e)), ',')", "p,q,xml"),
                             ok ("string-join(sort(in-scope-prefixes(/*)), ',')", ",p,xml"),
                             err ("in-scope-prefixes(//@a)", "XPTY0004"),
                             err ("in-scope-prefixes(/)", "XPTY0004"),
                             // §10.2.1 fn:resolve-QName
                             ok ("namespace-uri-from-QName(resolve-QName(\"hello\", //e))", ""),
                             ok ("namespace-uri-from-QName(resolve-QName(\"hello\", /*))", "urn:d"),
                             // Left out: go-xslt accepts resolve-QName(" q:hello ", //e) (leading and
                             // trailing whitespace stripped). The REC only asks for "the correct lexical
                             // form for an instance of xs:QName" (FOCA0002 otherwise); whether whitespace
                             // collapsing applies first is not settled there, and this port raises
                             // FOCA0002.
                             err ("resolve-QName(\"zz:hello\", //e)", "FONS0004"),
                             err ("resolve-QName(\"p:+thing\", //e)", "FOCA0002"),
                             err ("resolve-QName(\"p:thi:ng\", //e)", "FOCA0002"),
                             err ("resolve-QName(\"+thing\", //e)", "FOCA0002"),
                             err ("resolve-QName(\"\", //e)", "FOCA0002"),
                             err ("resolve-QName(\"a\", 1)", "XPTY0004"),
                             err ("resolve-QName(\"a\", //@a)", "XPTY0004"));
  }
}
