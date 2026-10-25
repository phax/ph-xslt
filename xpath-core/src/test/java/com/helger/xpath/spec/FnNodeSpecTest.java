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

import static com.helger.xpath.spec.FnCoreTestHelper.coreRun;
import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Port of go-xslt <code>internal/xpath/fn_node_spec_test.go</code> (commit 04ccada).
 *
 * @author Philip Helger
 */
public final class FnNodeSpecTest
{
  /** The F&O 3.1 §14.5.2 fn:path example document (the parse-xml form). */
  private static final String ND_PATH_DOC = "<?xml version=\"1.0\"?><p xmlns=\"http://example.com/one\" xml:lang=\"de\" author=\"Friedrich von Schiller\">Freude, schöner Götterfunken,<br/>Tochter aus Elysium,<br/>Wir betreten feuertrunken,<br/>Himmlische, dein Heiligtum.</p>";

  /**
   * Covers fn:path with the REC's examples plus the step
   * forms for comments, processing instructions and namespace nodes.
   */
  @Test
  public void nodeSpecPath ()
  {
    coreRun (ND_PATH_DOC,
      ok ("path(/)", "/"),
      ok ("path(/*:p)", "/Q{http://example.com/one}p[1]"),
      ok ("path(/*:p/@xml:lang)", "/Q{http://example.com/one}p[1]/@Q{http://www.w3.org/XML/1998/namespace}lang"),
      ok ("path(/*:p/@author)", "/Q{http://example.com/one}p[1]/@author"),
      ok ("path(/*:p/*:br[2])", "/Q{http://example.com/one}p[1]/Q{http://example.com/one}br[2]"),
      ok ("path(//text()[starts-with(normalize-space(), 'Tochter')])", "/Q{http://example.com/one}p[1]/text()[2]"),
      ok ("path(())", "()"),
      // the context item is the default argument
      ok ("/*:p/*:br[1]/path()", "/Q{http://example.com/one}p[1]/Q{http://example.com/one}br[1]")
    );
    coreRun ("<r xmlns:q=\"urn:q\"><!--c1--><?pi a?><x/><!--c2--><?pi b?><?other?><y xmlns=\"urn:d\"/></r>",
      ok ("path(/r/comment()[2])", "/Q{}r[1]/comment()[2]"),
      ok ("path(/r/processing-instruction('pi')[2])", "/Q{}r[1]/processing-instruction(pi)[2]"),
      ok ("path(/r/processing-instruction('other'))", "/Q{}r[1]/processing-instruction(other)[1]"),
      ok ("path(/r/namespace::q)", "/Q{}r[1]/namespace::q"),
      ok ("path(/r/*:y/namespace::*[local-name() = ''])", "/Q{}r[1]/Q{urn:d}y[1]/namespace::*[Q{http://www.w3.org/2005/xpath-functions}local-name()=\"\"]")
    );
  }

  /**
   * Covers fn:path of a tree whose root is not a
   * document node: the path starts with a call to fn:root() (F&O 3.1 §14.5.2,
   * the $emp example).
   */
  @Test
  public void nodeSpecPathParentless ()
  {
    final XdmNode aEmp = XdmNode.createElement (new XdmQName ("employee"));
    aEmp.addAttribute (XdmNode.createAttribute (new XdmQName (XdmConstants.NS_XML, "id", "xml"), "ID21256"));
    final XdmNode aEmpNr = XdmNode.createElement (new XdmQName ("empnr"));
    aEmpNr.appendChild (XdmNode.createText ("E21543"));
    aEmp.appendChild (aEmpNr);
    final String sRoot = "Q{http://www.w3.org/2005/xpath-functions}root()";
    run (env ().contextItem (aEmp),
      ok ("path(.)", sRoot),
      ok ("path(@xml:id)", sRoot + "/@Q{http://www.w3.org/XML/1998/namespace}id"),
      ok ("path(empnr)", sRoot + "/Q{}empnr[1]")
    );
  }

  /**
   * Covers fn:has-children, fn:innermost and
   * fn:outermost (F&O 3.1 §14.5.4-§14.5.6).
   */
  @Test
  public void nodeSpecInnermostOutermost ()
  {
    final String sDoc = "<r><a><b/></a><c/></r>";
    coreRun (sDoc,
      // has-children($node) = exists($node/child::node())
      ok ("has-children(/r)", "true"),
      ok ("has-children(/r/c)", "false"),
      ok ("has-children(/)", "true"),
      ok ("has-children(())", "false"),
      ok ("/r/a/has-children()", "true"),
      ok ("/r/a/b/has-children()", "false"),
      err ("has-children(1)", "XPTY0004"),
      err ("has-children(/r/*)", "XPTY0004"),

      // innermost: drop every node that is an ancestor of another input node
      ok ("innermost(//*) ! name()", "b|c"),
      ok ("innermost((//b, //a, //b)) ! name()", "b"),
      ok ("innermost(()) ! name()", "()"),
      err ("innermost((/r, 1))", "XPTY0004"),
      // outermost: drop every node that has an ancestor in the input
      ok ("outermost(//*) ! name()", "r"),
      ok ("outermost((//c, //b, //a)) ! name()", "a|c"),
      ok ("outermost(()) ! name()", "()"),
      err ("outermost(('x'))", "XPTY0004")
    );
    // no focus: XPDY0002
    run (env ().noContext (), err ("has-children()", "XPDY0002"));
  }
}
