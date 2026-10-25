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
package com.helger.xpath.conformance;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;

/**
 * Reads the FOTS catalog and test-set files with the JDK DOM. Elements are
 * matched by local name, like the Go harness does.
 *
 * @author Philip Helger
 */
@Immutable
public final class QT3CatalogReader
{
  @PresentForCodeCoverage
  private static final QT3CatalogReader INSTANCE = new QT3CatalogReader ();

  private QT3CatalogReader ()
  {}

  @NonNull
  private static Document _parse (@NonNull final InputSource aSource) throws IOException
  {
    try
    {
      final DocumentBuilderFactory aFactory = DocumentBuilderFactory.newDefaultInstance ();
      aFactory.setNamespaceAware (true);
      aFactory.setValidating (false);
      aFactory.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
      aFactory.setFeature ("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      final DocumentBuilder aBuilder = aFactory.newDocumentBuilder ();
      return aBuilder.parse (aSource);
    }
    catch (final ParserConfigurationException | SAXException ex)
    {
      throw new IOException ("Failed to parse FOTS file: " + ex.getMessage (), ex);
    }
  }

  @NonNull
  private static String _localName (@NonNull final Node aNode)
  {
    final String ret = aNode.getLocalName ();
    return ret != null ? ret : aNode.getNodeName ();
  }

  @NonNull
  private static ICommonsList <Element> _childElements (@NonNull final Element aParent, @Nullable final String sLocalName)
  {
    final ICommonsList <Element> ret = new CommonsArrayList <> ();
    for (Node aChild = aParent.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
      if (aChild instanceof final Element aElement && (sLocalName == null || sLocalName.equals (_localName (aElement))))
        ret.add (aElement);
    return ret;
  }

  @Nullable
  private static Element _firstChildElement (@NonNull final Element aParent, @NonNull final String sLocalName)
  {
    for (Node aChild = aParent.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
      if (aChild instanceof final Element aElement && sLocalName.equals (_localName (aElement)))
        return aElement;
    return null;
  }

  /** The direct text and CDATA content, like Go's <code>xml:",chardata"</code>. */
  @NonNull
  private static String _directText (@NonNull final Element aElement)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (Node aChild = aElement.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
    {
      final short nType = aChild.getNodeType ();
      if (nType == Node.TEXT_NODE || nType == Node.CDATA_SECTION_NODE)
        aSB.append (aChild.getNodeValue ());
    }
    return aSB.toString ();
  }

  @NonNull
  private static String _attr (@NonNull final Element aElement, @NonNull final String sName)
  {
    // Returns "" when absent
    return aElement.getAttribute (sName);
  }

  @NonNull
  private static ICommonsList <QT3Dependency> _readDependencies (@NonNull final Element aParent)
  {
    final ICommonsList <QT3Dependency> ret = new CommonsArrayList <> ();
    for (final Element aDep : _childElements (aParent, "dependency"))
      ret.add (new QT3Dependency (_attr (aDep, "type"), _attr (aDep, "value"), _attr (aDep, "satisfied")));
    return ret;
  }

  private static QT3Environment.@NonNull Source _readSource (@NonNull final Element aSource)
  {
    return new QT3Environment.Source (_attr (aSource, "role"),
                                      _attr (aSource, "file"),
                                      _attr (aSource, "uri"),
                                      _attr (aSource, "validation"));
  }

  @NonNull
  private static ICommonsOrderedMap <String, String> _allAttributes (@NonNull final Element aElement,
                                                                     final boolean bByLocalName)
  {
    final ICommonsOrderedMap <String, String> ret = new CommonsLinkedHashMap <> ();
    final NamedNodeMap aAttrs = aElement.getAttributes ();
    for (int i = 0; i < aAttrs.getLength (); ++i)
    {
      final Attr aAttr = (Attr) aAttrs.item (i);
      ret.put (bByLocalName ? _localName (aAttr) : aAttr.getName (), aAttr.getValue ());
    }
    return ret;
  }

  /**
   * Read one environment element.
   *
   * @param aEnv
   *        The <code>&lt;environment&gt;</code> element.
   * @param aBaseDir
   *        Directory of the containing file.
   * @return The environment.
   */
  @NonNull
  static QT3Environment readEnvironment (@NonNull final Element aEnv, @NonNull final Path aBaseDir)
  {
    final QT3Environment ret = new QT3Environment (_attr (aEnv, "name"), _attr (aEnv, "ref"), aBaseDir);
    for (final Element aChild : _childElements (aEnv, null))
    {
      switch (_localName (aChild))
      {
        case "source":
          ret.addSource (_readSource (aChild));
          break;
        case "resource":
          ret.addResource (new QT3Environment.Resource (_attr (aChild, "file"),
                                                        _attr (aChild, "uri"),
                                                        _attr (aChild, "encoding"),
                                                        _attr (aChild, "media-type")));
          break;
        case "namespace":
          ret.addNamespace (new QT3Environment.Namespace (_attr (aChild, "prefix"), _attr (aChild, "uri")));
          break;
        case "param":
        {
          final String sName = _attr (aChild, "name");
          final int nColon = sName.indexOf (':');
          String sNsUri = nColon > 0 ? aChild.lookupNamespaceURI (sName.substring (0, nColon)) : null;
          if (sNsUri == null)
            sNsUri = "";
          ret.addParam (new QT3Environment.Param (sName,
                                                  sNsUri,
                                                  _attr (aChild, "select"),
                                                  _attr (aChild, "as"),
                                                  _attr (aChild, "declared")));
          break;
        }
        case "static-base-uri":
          ret.setStaticBaseUri (_attr (aChild, "uri"));
          break;
        case "schema":
          ret.addSchema (new QT3Environment.Schema (_attr (aChild, "uri"), _attr (aChild, "file")));
          break;
        case "collection":
        {
          final ICommonsList <QT3Environment.Source> aSources = new CommonsArrayList <> ();
          for (final Element aSource : _childElements (aChild, "source"))
            aSources.add (_readSource (aSource));
          ret.addCollection (new QT3Environment.Collection (_attr (aChild, "uri"), aSources));
          break;
        }
        case "collation":
          ret.addCollation (new QT3Environment.Collation (_attr (aChild, "uri"),
                                                          "true".equals (_attr (aChild, "default"))));
          break;
        case "decimal-format":
          ret.addDecimalFormat (new QT3Environment.DecimalFormat (_allAttributes (aChild, false)));
          break;
        case "context-item":
          ret.setContextItemSelect (_attr (aChild, "select"));
          break;
        default:
          // description, created, modified etc. are not relevant
          break;
      }
    }
    return ret;
  }

  @NonNull
  static QT3Assertion readAssertion (@NonNull final Element aElement)
  {
    final ICommonsList <QT3Assertion> aChildren = new CommonsArrayList <> ();
    for (final Element aChild : _childElements (aElement, null))
      aChildren.add (readAssertion (aChild));
    return new QT3Assertion (_localName (aElement), _allAttributes (aElement, true), _directText (aElement), aChildren);
  }

  @NonNull
  private static QT3TestCase _readTestCase (@NonNull final Element aTC, @NonNull final Path aDir)
  {
    final Element aEnv = _firstChildElement (aTC, "environment");
    final Element aTest = _firstChildElement (aTC, "test");
    final Element aResult = _firstChildElement (aTC, "result");
    QT3Assertion aAssertion = null;
    if (aResult != null)
    {
      final ICommonsList <Element> aAsserts = _childElements (aResult, null);
      if (aAsserts.isNotEmpty ())
        aAssertion = readAssertion (aAsserts.getFirstOrNull ());
    }
    return new QT3TestCase (_attr (aTC, "name"),
                            _readDependencies (aTC),
                            aEnv == null ? null : readEnvironment (aEnv, aDir),
                            aTest == null ? "" : _directText (aTest),
                            aTest == null ? "" : _attr (aTest, "file"),
                            aAssertion);
  }

  /**
   * Read the catalog.
   *
   * @param aSource
   *        The catalog XML.
   * @param aRootDir
   *        The suite directory.
   * @return The catalog.
   * @throws IOException
   *         on read or parse errors
   */
  @NonNull
  public static QT3Catalog readCatalog (@NonNull final InputSource aSource, @NonNull final Path aRootDir)
                                                                                                         throws IOException
  {
    final Element aRoot = _parse (aSource).getDocumentElement ();
    final ICommonsMap <String, QT3Environment> aEnvs = new CommonsHashMap <> ();
    for (final Element aEnv : _childElements (aRoot, "environment"))
    {
      final QT3Environment aParsed = readEnvironment (aEnv, aRootDir);
      aEnvs.put (aParsed.getName (), aParsed);
    }
    final ICommonsList <QT3Catalog.TestSetRef> aSets = new CommonsArrayList <> ();
    for (final Element aSet : _childElements (aRoot, "test-set"))
      aSets.add (new QT3Catalog.TestSetRef (_attr (aSet, "name"), _attr (aSet, "file")));
    return new QT3Catalog (aRootDir, aEnvs, aSets);
  }

  /**
   * Read the catalog from a file.
   *
   * @param aCatalogFile
   *        Path of catalog.xml.
   * @return The catalog.
   * @throws IOException
   *         on read or parse errors
   */
  @NonNull
  public static QT3Catalog readCatalog (@NonNull final Path aCatalogFile) throws IOException
  {
    final Path aAbs = aCatalogFile.toAbsolutePath ().normalize ();
    return readCatalog (new InputSource (aAbs.toUri ().toString ()), aAbs.getParent ());
  }

  /**
   * Read a test-set file.
   *
   * @param aSource
   *        The test-set XML.
   * @param aDir
   *        The directory of the test-set file.
   * @return The test set.
   * @throws IOException
   *         on read or parse errors
   */
  @NonNull
  public static QT3TestSet readTestSet (@NonNull final InputSource aSource, @NonNull final Path aDir)
                                                                                                    throws IOException
  {
    final Element aRoot = _parse (aSource).getDocumentElement ();
    final ICommonsMap <String, QT3Environment> aEnvs = new CommonsHashMap <> ();
    for (final Element aEnv : _childElements (aRoot, "environment"))
    {
      final QT3Environment aParsed = readEnvironment (aEnv, aDir);
      aEnvs.put (aParsed.getName (), aParsed);
    }
    final ICommonsList <QT3TestCase> aCases = new CommonsArrayList <> ();
    for (final Element aTC : _childElements (aRoot, "test-case"))
      aCases.add (_readTestCase (aTC, aDir));
    return new QT3TestSet (_attr (aRoot, "name"), aDir, _readDependencies (aRoot), aEnvs, aCases);
  }

  /**
   * Read a test-set file.
   *
   * @param aFile
   *        Path of the test-set file.
   * @return The test set.
   * @throws IOException
   *         on read or parse errors
   */
  @NonNull
  public static QT3TestSet readTestSet (@NonNull final Path aFile) throws IOException
  {
    final Path aAbs = aFile.toAbsolutePath ().normalize ();
    return readTestSet (new InputSource (aAbs.toUri ().toString ()), aAbs.getParent ());
  }

  /**
   * Convenience for tests: read a test set from a string.
   *
   * @param sXml
   *        The XML.
   * @param aDir
   *        The directory relative references resolve against.
   * @return The test set.
   * @throws IOException
   *         on parse errors
   */
  @NonNull
  public static QT3TestSet readTestSet (@NonNull final String sXml, @NonNull final Path aDir) throws IOException
  {
    return readTestSet (new InputSource (new StringReader (sXml)), aDir);
  }
}
