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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.xdm.XdmQName;

/**
 * Smoke test: parse every XPath 3.1 expression of the W3C QT3 test suite and
 * report how many parse. It never fails on the numbers; it is skipped when the
 * suite is not available. Location: system property <code>qt3.dir</code>,
 * default <code>/Users/philip/dev/git-thirdparty/qt3tests</code>.
 *
 * @author Philip Helger
 */
public final class Qt3ParseSmokeTest
{
  private static final String NS_CATALOG = "http://www.w3.org/2010/09/qt-fots-catalog";
  private static final ICommonsSet <String> UNSUPPORTED_FEATURES = new CommonsHashSet <> ("schemaImport",
                                                                                            "schemaValidation",
                                                                                            "staticTyping",
                                                                                            "typedData",
                                                                                            "xpath-1.0-compatibility",
                                                                                            "moduleImport",
                                                                                            "schema-location-hint");

  /** The static parts of a QT3 environment. */
  private static final class Env
  {
    private final ICommonsMap <String, String> m_aNamespaces = new CommonsHashMap <> ();
    private final ICommonsList <String> m_aVariables = new CommonsArrayList <> ();
    private boolean m_bSchema;
  }

  private int m_nApplicable;
  private int m_nSkippedFeature;
  private int m_nSkippedSchema;
  private int m_nExpectSuccess;
  private int m_nExpectSuccessParsed;
  private int m_nExpectError;
  private int m_nExpectErrorParsed;
  private int m_nExpectErrorRightCode;
  private int m_nExpectErrorWrongCode;
  private int m_nMissedStatic;
  private final ICommonsList <String> m_aMissedStaticSamples = new CommonsArrayList <> ();
  private final Map <String, Integer> m_aFailCodes = new TreeMap <> ();
  private final ICommonsList <String> m_aFailSamples = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aWrongCodeSamples = new CommonsArrayList <> ();
  private FunctionLibrary m_aLib;

  @NonNull
  private static ICommonsList <Element> _children (@NonNull final Element aParent, @NonNull final String sLocalName)
  {
    final ICommonsList <Element> ret = new CommonsArrayList <> ();
    for (Node n = aParent.getFirstChild (); n != null; n = n.getNextSibling ())
      if (n instanceof final Element aEl && sLocalName.equals (aEl.getLocalName ()))
        ret.add (aEl);
    return ret;
  }

  @NonNull
  private static Document _load (@NonNull final File aFile) throws Exception
  {
    final DocumentBuilderFactory aDBF = DocumentBuilderFactory.newDefaultInstance ();
    aDBF.setNamespaceAware (true);
    final DocumentBuilder aDB = aDBF.newDocumentBuilder ();
    return aDB.parse (aFile);
  }

  @NonNull
  private static Env _readEnv (@NonNull final Element aEnv)
  {
    final Env ret = new Env ();
    for (final Element e : _children (aEnv, "namespace"))
      ret.m_aNamespaces.put (e.getAttribute ("prefix"), e.getAttribute ("uri"));
    for (final Element e : _children (aEnv, "param"))
      ret.m_aVariables.add (e.getAttribute ("name"));
    for (final Element e : _children (aEnv, "source"))
    {
      final String sRole = e.getAttribute ("role");
      if (sRole.startsWith ("$"))
        ret.m_aVariables.add (sRole.substring (1));
    }
    ret.m_bSchema = !_children (aEnv, "schema").isEmpty ();
    return ret;
  }

  private static boolean _specApplies (@NonNull final String sValue)
  {
    for (final String sToken : sValue.trim ().split ("\\s+"))
      switch (sToken)
      {
        case "XP31":
        case "XP31+":
        case "XP30+":
        case "XP20+":
        case "XP10+":
          return true;
        default:
          break;
      }
    return false;
  }

  /** @return <code>null</code> if applicable, else the reason to skip. */
  @Nullable
  private static String _checkDependencies (@NonNull final Element aParent)
  {
    for (final Element e : _children (aParent, "dependency"))
    {
      final String sType = e.getAttribute ("type");
      final String sValue = e.getAttribute ("value");
      final boolean bSatisfied = !"false".equals (e.getAttribute ("satisfied"));
      if ("spec".equals (sType) && !_specApplies (sValue))
        return "spec";
      if ("feature".equals (sType) && bSatisfied && UNSUPPORTED_FEATURES.contains (sValue))
        return "feature";
    }
    return null;
  }

  private static void _collectErrorCodes (@NonNull final Element aResult, @NonNull final ICommonsSet <String> aCodes)
  {
    for (Node n = aResult.getFirstChild (); n != null; n = n.getNextSibling ())
      if (n instanceof final Element aEl)
      {
        final String sName = aEl.getLocalName ();
        if ("error".equals (sName))
          aCodes.add (aEl.getAttribute ("code"));
        else
          if ("any-of".equals (sName))
            _collectErrorCodes (aEl, aCodes);
      }
  }

  private void _increment (@NonNull final String sCode)
  {
    m_aFailCodes.merge (sCode, Integer.valueOf (1), (a, b) -> Integer.valueOf (a.intValue () + b.intValue ()));
  }

  private void _runTestCase (@NonNull final Element aTC,
                             @NonNull final File aDir,
                             @NonNull final ICommonsMap <String, Env> aEnvs) throws Exception
  {
    final String sName = aTC.getAttribute ("name");
    final String sDep = _checkDependencies (aTC);
    if ("spec".equals (sDep))
      return;
    if (sDep != null)
    {
      m_nSkippedFeature++;
      return;
    }
    final ICommonsList <Element> aTests = _children (aTC, "test");
    if (aTests.isEmpty ())
      return;
    final Element aTest = aTests.getFirstOrNull ();
    final String sExpr;
    if (aTest.hasAttribute ("file"))
      sExpr = Files.readString (new File (aDir, aTest.getAttribute ("file")).toPath (), StandardCharsets.UTF_8);
    else
      sExpr = aTest.getTextContent ();

    final ICommonsList <Env> aTCEnvs = new CommonsArrayList <> ();
    for (final Element e : _children (aTC, "environment"))
    {
      if (e.hasAttribute ("ref"))
      {
        final Env aEnv = aEnvs.get (e.getAttribute ("ref"));
        if (aEnv != null)
          aTCEnvs.add (aEnv);
      }
      else
        aTCEnvs.add (_readEnv (e));
    }
    for (final Env aEnv : aTCEnvs)
      if (aEnv.m_bSchema)
      {
        m_nSkippedSchema++;
        return;
      }
    m_nApplicable++;

    final StaticContext aCtx = new StaticContext ();
    aCtx.setFunctionLibrary (m_aLib);
    for (final Env aEnv : aTCEnvs)
    {
      for (final Map.Entry <String, String> aEntry : aEnv.m_aNamespaces.entrySet ())
        if (aEntry.getKey ().isEmpty ())
          aCtx.setDefaultElementNamespace (aEntry.getValue ());
        else
          aCtx.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    }
    for (final Env aEnv : aTCEnvs)
      for (final String sVar : aEnv.m_aVariables)
      {
        final int nColon = sVar.indexOf (':');
        if (nColon < 0)
          aCtx.declareVariable (new XdmQName (sVar));
        else
        {
          final String sNs = aCtx.getNamespaceUri (sVar.substring (0, nColon));
          if (sNs != null)
            aCtx.declareVariable (new XdmQName (sNs, sVar.substring (nColon + 1)));
        }
      }

    final ICommonsSet <String> aCodes = new CommonsHashSet <> ();
    for (final Element e : _children (aTC, "result"))
      _collectErrorCodes (e, aCodes);
    final boolean bExpectSuccess = aCodes.isEmpty ();

    String sCode = null;
    String sMsg = null;
    try
    {
      XPathParser.parse (sExpr, aCtx);
    }
    catch (final XPathException ex)
    {
      sCode = ex.getCode ();
      sMsg = ex.getMessage ();
    }
    catch (final RuntimeException ex)
    {
      sCode = ex.getClass ().getSimpleName ();
      sMsg = ex.toString ();
    }
    if (bExpectSuccess)
    {
      m_nExpectSuccess++;
      if (sCode == null)
        m_nExpectSuccessParsed++;
      else
      {
        _increment (sCode);
        if (m_aFailSamples.size () < 400)
          m_aFailSamples.add (sName + " [" + sCode + "] " + sMsg.replace ('\n', ' '));
      }
    }
    else
    {
      m_nExpectError++;
      if (sCode == null)
      {
        m_nExpectErrorParsed++;
        boolean bOnlyStatic = true;
        for (final String s : aCodes)
          if (!s.startsWith ("XPST") && !s.startsWith ("XQST"))
            bOnlyStatic = false;
        if (bOnlyStatic)
        {
          m_nMissedStatic++;
          m_aMissedStaticSamples.add (sName + " expected " + aCodes + ": " + sExpr.replace ('\n', ' '));
        }
      }
      else
        if (aCodes.contains (sCode) || aCodes.contains ("*"))
          m_nExpectErrorRightCode++;
        else
        {
          m_nExpectErrorWrongCode++;
          if (m_aWrongCodeSamples.size () < 200)
            m_aWrongCodeSamples.add (sName + " expected " + aCodes + " got [" + sCode + "] " + sMsg.replace ('\n', ' '));
        }
    }
  }

  @Test
  public void testParseAll () throws Exception
  {
    final File aRoot = new File (System.getProperty ("qt3.dir", "/Users/philip/dev/git-thirdparty/qt3tests"));
    final File aCatalogFile = new File (aRoot, "catalog.xml");
    Assume.assumeTrue ("QT3 suite not found at " + aRoot, aCatalogFile.isFile ());

    m_aLib = Qt3FunctionCatalog.createLibrary ();
    final Document aCatalog = _load (aCatalogFile);
    final ICommonsMap <String, Env> aGlobalEnvs = new CommonsHashMap <> ();
    for (final Element e : _children (aCatalog.getDocumentElement (), "environment"))
      aGlobalEnvs.put (e.getAttribute ("name"), _readEnv (e));

    int nSets = 0;
    for (final Element aSetRef : _children (aCatalog.getDocumentElement (), "test-set"))
    {
      final File aSetFile = new File (aRoot, aSetRef.getAttribute ("file"));
      final Document aSetDoc = _load (aSetFile);
      final Element aSet = aSetDoc.getDocumentElement ();
      if (!NS_CATALOG.equals (aSet.getNamespaceURI ()) || _checkDependencies (aSet) != null)
        continue;
      nSets++;
      final ICommonsMap <String, Env> aEnvs = new CommonsHashMap <> (aGlobalEnvs);
      for (final Element e : _children (aSet, "environment"))
        aEnvs.put (e.getAttribute ("name"), _readEnv (e));
      for (final Element aTC : _children (aSet, "test-case"))
        _runTestCase (aTC, aSetFile.getParentFile (), aEnvs);
    }

    final StringBuilder aSB = new StringBuilder ();
    aSB.append ("QT3 parse smoke test (").append (nSets).append (" test sets applicable to XP31)\n");
    aSB.append ("  skipped (unsupported feature): ").append (m_nSkippedFeature).append ('\n');
    aSB.append ("  skipped (schema environment):  ").append (m_nSkippedSchema).append ('\n');
    aSB.append ("  applicable test cases:         ").append (m_nApplicable).append ('\n');
    aSB.append ("  expecting a result:            ")
       .append (m_nExpectSuccess)
       .append (", parsed: ")
       .append (m_nExpectSuccessParsed)
       .append (String.format (Locale.ROOT, " (%.2f%%)", Double.valueOf (100.0 * m_nExpectSuccessParsed / Math.max (1, m_nExpectSuccess))))
       .append ('\n');
    aSB.append ("  expecting an error:            ")
       .append (m_nExpectError)
       .append (", parsed (error left to evaluation): ")
       .append (m_nExpectErrorParsed)
       .append (", expected static error: ")
       .append (m_nExpectErrorRightCode)
       .append (", other static error: ")
       .append (m_nExpectErrorWrongCode)
       .append ('\n');
    aSB.append ("  expecting only a static error but parsed: ").append (m_nMissedStatic).append ('\n');
    aSB.append ("  parse failures of result cases by code: ").append (m_aFailCodes).append ('\n');
    for (final String s : m_aFailSamples)
      aSB.append ("    FAIL ").append (s).append ('\n');
    for (final String s : m_aWrongCodeSamples)
      aSB.append ("    WRONGCODE ").append (s).append ('\n');
    for (final String s : m_aMissedStaticSamples)
      aSB.append ("    MISSEDSTATIC ").append (s).append ('\n');
    final String sReport = aSB.toString ();
    final File aOut = new File ("target/qt3-parse-smoke.txt");
    Files.writeString (aOut.toPath (), sReport, StandardCharsets.UTF_8);
    System.out.println (sReport.substring (0, sReport.indexOf ("  parse failures")));
  }
}
