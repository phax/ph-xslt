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
package com.helger.xpath.fn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Checks the declared signature of every built-in function of the fn, math, map and array namespaces against the
 * F&amp;O 3.1 function catalogue (<code>builtin-signatures.txt</code>, one line per arity, generated from the
 * specification's own function-catalog.xml, parameter names left out). The function conversion rules are applied
 * from these signatures, so a wrong or missing one would silently check too much or too little.
 *
 * @author Philip Helger
 */
public final class BuiltinSignatureCatalogueTest
{
  private static final String RESOURCE = "builtin-signatures.txt";

  private static String _namespace (final String sPrefix)
  {
    return switch (sPrefix)
    {
      case "fn" -> XdmConstants.NS_FN;
      case "math" -> FunctionLibrary.NS_MATH;
      case "map" -> FunctionLibrary.NS_MAP;
      case "array" -> FunctionLibrary.NS_ARRAY;
      default -> throw new IllegalStateException (sPrefix);
    };
  }

  private static ICommonsList <String> _readLines () throws IOException
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    try (final InputStream aIS = BuiltinSignatureCatalogueTest.class.getResourceAsStream (RESOURCE);
         final BufferedReader aReader = new BufferedReader (new InputStreamReader (aIS, StandardCharsets.UTF_8)))
    {
      String sLine;
      while ((sLine = aReader.readLine ()) != null)
        if (!sLine.isBlank ())
          ret.add (sLine);
    }
    return ret;
  }

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

  @Test
  public void testEveryCatalogueLineIsRegistered () throws IOException
  {
    final FunctionLibrary aLib = FunctionLibrary.getDefault ();
    final ICommonsList <String> aProblems = new CommonsArrayList <> ();
    int nChecked = 0;
    for (final String sLine : _readLines ())
    {
      final int nColon = sLine.indexOf (':');
      final int nParen = sLine.indexOf ('(');
      final String sNamespace = _namespace (sLine.substring (0, nColon));
      final XdmQName aName = new XdmQName (sNamespace, sLine.substring (nColon + 1, nParen));
      String sSig = sLine.substring (nParen);
      boolean bVariadic = false;
      if (sSig.contains (", ...)"))
      {
        // fn:concat, the only variadic function: two or more xs:anyAtomicType? arguments
        bVariadic = true;
        sSig = sSig.replace (", ...)", ")");
      }
      final boolean bNoReturn = sSig.endsWith (" as none");
      if (bNoReturn)
        sSig = sSig.substring (0, sSig.length () - " none".length ()) + " item()*";
      final XPathParser.FunctionSignature aWant = XPathParser.parseFunctionSignature (sSig, new StaticContext ());
      final FunctionDef aDef = aLib.getFunction (aName, aWant.paramTypes ().size ());
      if (aDef == null)
      {
        // Registered by the host (xslt-core), as it needs a stylesheet engine
        if (!"fn:transform(map(*)) as map(*)".equals (sLine))
          aProblems.add (sLine + ": not registered");
        continue;
      }
      ++nChecked;
      final String sWant = _describe (aWant.paramTypes (), aWant.returnType ());
      final String sGot = _describe (aDef.getParamTypes (), aDef.getReturnType ());
      if (bNoReturn || bVariadic)
      {
        final String sWantParams = sWant.substring (0, sWant.indexOf (") as ") + 1);
        final String sGotParams = sGot.substring (0, sGot.indexOf (") as ") + 1);
        if (!sWantParams.equals (sGotParams))
          aProblems.add (sLine + ": registered as " + sGot);
      }
      else
        if (!sWant.equals (sGot))
          aProblems.add (sLine + ": registered as " + sGot);
    }
    if (!aProblems.isEmpty ())
      fail (aProblems.size () + " signatures differ:\n" + String.join ("\n", aProblems));
    assertEquals (272, nChecked);
  }

  @Test
  public void testNothingRegisteredBeyondTheCatalogue () throws IOException
  {
    final ICommonsSet <String> aKnown = new CommonsHashSet <> ();
    for (final String sLine : _readLines ())
    {
      final int nColon = sLine.indexOf (':');
      final int nParen = sLine.indexOf ('(');
      final String sSig = sLine.substring (nParen);
      final XPathParser.FunctionSignature aSig = XPathParser.parseFunctionSignature (sSig.replace (", ...)", ")")
                                                                                      .replaceAll (" as none$",
                                                                                                   " as item()*"),
                                                                                     new StaticContext ());
      aKnown.add ("{" + _namespace (sLine.substring (0, nColon)) + "}" + sLine.substring (nColon + 1, nParen) +
                  "#" +
                  aSig.paramTypes ().size ());
    }
    final ICommonsList <String> aExtra = new CommonsArrayList <> ();
    for (final FunctionDef aDef : FunctionLibrary.getDefault ().getAllFunctions ())
    {
      final String sNs = aDef.getName ().namespaceUri ();
      if (!sNs.equals (XdmConstants.NS_FN) &&
          !sNs.equals (FunctionLibrary.NS_MATH) &&
          !sNs.equals (FunctionLibrary.NS_MAP) &&
          !sNs.equals (FunctionLibrary.NS_ARRAY))
        continue;
      final String sKey = "{" + sNs + "}" + aDef.getName ().localName () + "#" + aDef.getArity ();
      if (!aKnown.contains (sKey) && !aDef.getName ().localName ().equals ("concat"))
        aExtra.add (sKey);
    }
    assertEquals ("Registered but not in the catalogue: " + aExtra, 0, aExtra.size ());
  }
}
