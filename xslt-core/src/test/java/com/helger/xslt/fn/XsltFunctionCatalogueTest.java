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
package com.helger.xslt.fn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.compile.XsltExtensionRegistry;

/**
 * The XSLT-defined functions of the standard function namespace (XSLT 3.0 sections 18 and 20) need a declared
 * signature like the F&amp;O built-ins, as the function conversion rules are applied from it. This lists them all: a
 * function registered by the engine that is neither in the F&amp;O catalogue nor here fails the test.
 *
 * @author Philip Helger
 */
public final class XsltFunctionCatalogueTest
{
  private static final String [] SIGNATURES = { "current() as item()",
                                                 "document(item()*) as node()*",
                                                 "document(item()*, node()) as node()*",
                                                 "key(xs:string, xs:anyAtomicType*) as node()*",
                                                 "key(xs:string, xs:anyAtomicType*, node()) as node()*",
                                                 "current-output-uri() as xs:anyURI?",
                                                 "copy-of() as item()*",
                                                 "copy-of(item()*) as item()*",
                                                 "snapshot() as item()*",
                                                 "snapshot(item()*) as item()*",
                                                 "system-property(xs:string) as xs:string",
                                                 "function-available(xs:string) as xs:boolean",
                                                 "function-available(xs:string, xs:integer) as xs:boolean",
                                                 "element-available(xs:string) as xs:boolean",
                                                 "type-available(xs:string) as xs:boolean",
                                                 "current-group() as item()*",
                                                 "current-grouping-key() as xs:anyAtomicType*",
                                                 "current-merge-group() as item()*",
                                                 "current-merge-group(xs:string) as item()*",
                                                 "current-merge-key() as xs:anyAtomicType*",
                                                 "regex-group(xs:integer) as xs:string",
                                                 "stream-available(xs:string?) as xs:boolean",
                                                 "accumulator-before(xs:string) as item()*",
                                                 "accumulator-after(xs:string) as item()*",
                                                 "unparsed-entity-uri(xs:string) as xs:anyURI",
                                                 "unparsed-entity-uri(xs:string, node()) as xs:anyURI",
                                                 "unparsed-entity-public-id(xs:string) as xs:string",
                                                 "unparsed-entity-public-id(xs:string, node()) as xs:string",
                                                 "available-system-properties() as xs:QName*" };

  private static String _describe (final List <SeqType> aParams, final SeqType aReturn)
  {
    final StringBuilder aSB = new StringBuilder ("(");
    for (int i = 0; i < aParams.size (); ++i)
    {
      if (i > 0)
        aSB.append (", ");
      aSB.append (SequenceTypeMatcher.getAsString (aParams.get (i)));
    }
    return aSB.append (") as ").append (SequenceTypeMatcher.getAsString (aReturn)).toString ();
  }

  private static FunctionLibrary _library ()
  {
    final FunctionLibrary ret = FunctionLibrary.getDefault ().createChild ();
    XsltExtensionRegistry.getInstance ().contributeFunctions (ret);
    return ret;
  }

  @Test
  public void testDeclaredSignatures ()
  {
    final FunctionLibrary aLib = _library ();
    final ICommonsList <String> aProblems = new CommonsArrayList <> ();
    for (final String sLine : SIGNATURES)
    {
      final int nParen = sLine.indexOf ('(');
      final XPathParser.FunctionSignature aWant = XPathParser.parseFunctionSignature (sLine.substring (nParen),
                                                                                       new StaticContext ());
      final FunctionDef aDef = aLib.getFunction (new XdmQName (XdmConstants.NS_FN, sLine.substring (0, nParen)),
                                                 aWant.paramTypes ().size ());
      if (aDef == null)
        aProblems.add (sLine + ": not registered");
      else
      {
        final String sWant = _describe (aWant.paramTypes (), aWant.returnType ());
        final String sGot = _describe (aDef.getParamTypes (), aDef.getReturnType ());
        if (!sWant.equals (sGot))
          aProblems.add (sLine + ": registered as " + sGot);
      }
    }
    if (!aProblems.isEmpty ())
      fail (String.join ("\n", aProblems));
  }

  @Test
  public void testNoFunctionWithoutSignature ()
  {
    final ICommonsSet <String> aListed = new CommonsHashSet <> ();
    for (final String sLine : SIGNATURES)
    {
      final int nParen = sLine.indexOf ('(');
      aListed.add (sLine.substring (0, nParen) +
                   "#" +
                   XPathParser.parseFunctionSignature (sLine.substring (nParen), new StaticContext ())
                              .paramTypes ()
                              .size ());
    }
    final FunctionLibrary aBase = FunctionLibrary.getDefault ();
    final ICommonsList <String> aExtra = new CommonsArrayList <> ();
    for (final FunctionDef aDef : _library ().getAllFunctions ())
      if (XdmConstants.NS_FN.equals (aDef.getName ().namespaceUri ()) &&
          aBase.getFunction (aDef.getName (), aDef.getArity ()) == null &&
          !aListed.contains (aDef.getName ().localName () + "#" + aDef.getArity ()))
        aExtra.add (aDef.getName ().localName () + "#" + aDef.getArity ());
    assertEquals ("Registered without a catalogued signature: " + aExtra, 0, aExtra.size ());
  }
}
