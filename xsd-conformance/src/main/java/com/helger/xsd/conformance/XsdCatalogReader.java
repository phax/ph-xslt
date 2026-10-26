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
package com.helger.xsd.conformance;

import java.io.IOException;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jspecify.annotations.NonNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Reads <code>suite.xml</code> and the <code>.testSet</code> files of the
 * xsdtests suite with the JDK DOM. Elements and the <code>xlink:href</code>
 * attribute are matched by local name, like the Go harness does.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdCatalogReader
{
  @PresentForCodeCoverage
  private static final XsdCatalogReader INSTANCE = new XsdCatalogReader ();

  private XsdCatalogReader ()
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
      throw new IOException ("Failed to parse xsdtests file: " + ex.getMessage (), ex);
    }
  }

  @NonNull
  private static Document _parse (@NonNull final Path aFile) throws IOException
  {
    return _parse (new InputSource (aFile.toUri ().toString ()));
  }

  @NonNull
  private static String _localName (@NonNull final Node aNode)
  {
    final String ret = aNode.getLocalName ();
    return ret != null ? ret : aNode.getNodeName ();
  }

  @NonNull
  private static ICommonsList <Element> _childElements (@NonNull final Element aParent, @NonNull final String sLocalName)
  {
    final ICommonsList <Element> ret = new CommonsArrayList <> ();
    for (Node aChild = aParent.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
      if (aChild instanceof final Element aElement && sLocalName.equals (_localName (aElement)))
        ret.add (aElement);
    return ret;
  }

  @NonNull
  private static String _attr (@NonNull final Element aElement, @NonNull final String sName)
  {
    // Returns "" when absent
    return aElement.getAttribute (sName);
  }

  /** An attribute matched by local name in any namespace, "" when absent. */
  @NonNull
  private static String _attrByLocalName (@NonNull final Element aElement, @NonNull final String sLocalName)
  {
    final NamedNodeMap aAttrs = aElement.getAttributes ();
    for (int i = 0; i < aAttrs.getLength (); ++i)
    {
      final Attr aAttr = (Attr) aAttrs.item (i);
      if (sLocalName.equals (_localName (aAttr)))
        return aAttr.getValue ();
    }
    return "";
  }

  @NonNull
  private static ICommonsList <XsdExpected> _readExpected (@NonNull final Element aTest)
  {
    final ICommonsList <XsdExpected> ret = new CommonsArrayList <> ();
    for (final Element aExp : _childElements (aTest, "expected"))
      ret.add (new XsdExpected (_attr (aExp, "validity"), _attr (aExp, "version")));
    return ret;
  }

  @NonNull
  private static ICommonsList <String> _readCurrentStatuses (@NonNull final Element aTest)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final Element aCurrent : _childElements (aTest, "current"))
      ret.add (_attr (aCurrent, "status"));
    return ret;
  }

  @NonNull
  private static XsdSchemaTest _readSchemaTest (@NonNull final Element aTest)
  {
    final ICommonsList <String> aDocs = new CommonsArrayList <> ();
    for (final Element aDoc : _childElements (aTest, "schemaDocument"))
      aDocs.add (_attrByLocalName (aDoc, "href"));
    return new XsdSchemaTest (_attr (aTest, "name"),
                              _attr (aTest, "version"),
                              aDocs,
                              _readExpected (aTest),
                              _readCurrentStatuses (aTest));
  }

  @NonNull
  private static XsdInstanceTest _readInstanceTest (@NonNull final Element aTest)
  {
    final ICommonsList <Element> aDocs = _childElements (aTest, "instanceDocument");
    return new XsdInstanceTest (_attr (aTest, "name"),
                                _attr (aTest, "version"),
                                aDocs.isEmpty () ? "" : _attrByLocalName (aDocs.getFirstOrNull (), "href"),
                                _readExpected (aTest),
                                _readCurrentStatuses (aTest));
  }

  @NonNull
  private static XsdTestGroup _readGroup (@NonNull final Element aGroup)
  {
    // Like the Go struct field, the last schemaTest wins (the suite has at most one)
    XsdSchemaTest aSchemaTest = null;
    for (final Element aTest : _childElements (aGroup, "schemaTest"))
      aSchemaTest = _readSchemaTest (aTest);
    final ICommonsList <XsdInstanceTest> aInstances = new CommonsArrayList <> ();
    for (final Element aTest : _childElements (aGroup, "instanceTest"))
      aInstances.add (_readInstanceTest (aTest));
    return new XsdTestGroup (_attr (aGroup, "name"), _attr (aGroup, "version"), aSchemaTest, aInstances);
  }

  /**
   * Read the test-set references of <code>suite.xml</code>.
   *
   * @param aSuiteFile
   *        The <code>suite.xml</code> file.
   * @return The <code>xlink:href</code> of every
   *         <code>&lt;testSetRef&gt;</code> in document order, relative to
   *         the suite directory.
   * @throws IOException
   *         if the file cannot be read or parsed
   */
  @NonNull
  @ReturnsMutableCopy
  public static ICommonsList <String> readSuite (@NonNull final Path aSuiteFile) throws IOException
  {
    final Element aRoot = _parse (aSuiteFile).getDocumentElement ();
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final Element aRef : _childElements (aRoot, "testSetRef"))
      ret.add (_attrByLocalName (aRef, "href"));
    return ret;
  }

  /**
   * Read a test set from text.
   *
   * @param aSource
   *        The test-set XML.
   * @param aDir
   *        The directory of the test-set file.
   * @return The test set.
   * @throws IOException
   *         if the text cannot be parsed
   */
  @NonNull
  public static XsdTestSet readTestSet (@NonNull final InputSource aSource, @NonNull final Path aDir) throws IOException
  {
    final Element aRoot = _parse (aSource).getDocumentElement ();
    final ICommonsList <XsdTestGroup> aGroups = new CommonsArrayList <> ();
    for (final Element aGroup : _childElements (aRoot, "testGroup"))
      aGroups.add (_readGroup (aGroup));
    return new XsdTestSet (_attr (aRoot, "name"), _attr (aRoot, "version"), aDir, aGroups);
  }

  /**
   * Read a test-set file.
   *
   * @param aFile
   *        The <code>.testSet</code> file.
   * @return The test set.
   * @throws IOException
   *         if the file cannot be read or parsed
   */
  @NonNull
  public static XsdTestSet readTestSet (@NonNull final Path aFile) throws IOException
  {
    final Path aAbs = aFile.toAbsolutePath ().normalize ();
    return readTestSet (new InputSource (aAbs.toUri ().toString ()), aAbs.getParent ());
  }
}
