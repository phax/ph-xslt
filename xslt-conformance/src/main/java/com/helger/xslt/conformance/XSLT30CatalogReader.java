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
package com.helger.xslt.conformance;

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
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Reads the xslt30-test catalog and test-set files with the JDK DOM. Elements
 * are matched by local name, like the Go harness does. Namespace prefixes in
 * names and expressions resolve with normal XML scoping in the catalog file.
 *
 * @author Philip Helger
 */
@Immutable
public final class XSLT30CatalogReader
{
  @PresentForCodeCoverage
  private static final XSLT30CatalogReader INSTANCE = new XSLT30CatalogReader ();

  private XSLT30CatalogReader ()
  {}

  @NonNull
  private static Document _parse (@NonNull final InputSource aSource) throws IOException
  {
    try
    {
      final DocumentBuilderFactory aFactory = DocumentBuilderFactory.newInstance ();
      aFactory.setNamespaceAware (true);
      aFactory.setValidating (false);
      aFactory.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
      aFactory.setFeature ("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      final DocumentBuilder aBuilder = aFactory.newDocumentBuilder ();
      return aBuilder.parse (aSource);
    }
    catch (final ParserConfigurationException | SAXException ex)
    {
      throw new IOException ("Failed to parse catalog file: " + ex.getMessage (), ex);
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

  private static boolean _isYes (@NonNull final String sValue)
  {
    final String s = sValue.strip ();
    return "yes".equals (s) || "true".equals (s) || "1".equals (s);
  }

  /**
   * @param aElement
   *        A catalog element.
   * @return The prefixed namespace declarations in scope on it, the innermost
   *         one winning. The default namespace (the catalog's own) is not
   *         included.
   */
  @NonNull
  static ICommonsOrderedMap <String, String> getInScopeNamespaces (@NonNull final Element aElement)
  {
    final ICommonsOrderedMap <String, String> ret = new CommonsLinkedHashMap <> ();
    for (Node aNode = aElement; aNode instanceof final Element aCur; aNode = aCur.getParentNode ())
    {
      final NamedNodeMap aAttrs = aCur.getAttributes ();
      for (int i = 0; i < aAttrs.getLength (); ++i)
      {
        final Attr aAttr = (Attr) aAttrs.item (i);
        if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals (aAttr.getNamespaceURI ()) &&
            !XMLConstants.XMLNS_ATTRIBUTE.equals (aAttr.getLocalName ()))
          ret.putIfAbsent (aAttr.getLocalName (), aAttr.getValue ());
      }
    }
    return ret;
  }

  /**
   * Resolve a lexical QName or EQName written in the catalog.
   *
   * @param sLexical
   *        The name.
   * @param aNamespaces
   *        The namespaces in scope.
   * @return The expanded name, <code>null</code> if it is empty or the prefix
   *         is unbound.
   *         The prefix <code>xsl</code> is bound to the XSLT namespace when
   *         the catalog does not bind it.
   */
  @Nullable
  static XdmQName resolveName (@NonNull final String sLexical, @NonNull final ICommonsOrderedMap <String, String> aNamespaces)
  {
    final String sName = sLexical.strip ();
    if (sName.isEmpty ())
      return null;
    if (sName.startsWith ("Q{"))
    {
      final int nEnd = sName.indexOf ('}');
      if (nEnd < 0)
        return null;
      return new XdmQName (sName.substring (2, nEnd), sName.substring (nEnd + 1));
    }
    final int nColon = sName.indexOf (':');
    if (nColon < 0)
      return new XdmQName (sName);
    final String sPrefix = sName.substring (0, nColon);
    String sUri = aNamespaces.get (sPrefix);
    if (sUri == null && "xsl".equals (sPrefix))
      sUri = XdmConstants.NS_XSL;
    if (sUri == null)
      return null;
    return new XdmQName (sUri, sName.substring (nColon + 1), sPrefix);
  }

  @NonNull
  private static ICommonsList <XSLT30Dependency> _readDependencies (@NonNull final Element aParent)
  {
    final ICommonsList <XSLT30Dependency> ret = new CommonsArrayList <> ();
    for (final Element aDeps : _childElements (aParent, "dependencies"))
      for (final Element aDep : _childElements (aDeps, null))
        ret.add (new XSLT30Dependency (_localName (aDep), _attr (aDep, "value"), _attr (aDep, "satisfied")));
    return ret;
  }

  private static XSLT30Environment.@NonNull Source _readSource (@NonNull final Element aSource)
  {
    final Element aContent = _firstChildElement (aSource, "content");
    return new XSLT30Environment.Source (_attr (aSource, "role"),
                                         _attr (aSource, "file"),
                                         _attr (aSource, "uri"),
                                         _attr (aSource, "select"),
                                         _attr (aSource, "validation"),
                                         _attr (aSource, "streaming"),
                                         aContent == null ? null : _directText (aContent),
                                         "true".equals (_attr (aSource, "defines-stylesheet").strip ()));
  }

  private static XSLT30Environment.@NonNull Module _readModule (@NonNull final Element aModule)
  {
    return new XSLT30Environment.Module (_attr (aModule, "file"),
                                         _attr (aModule, "role"),
                                         _attr (aModule, "uri"),
                                         _attr (aModule, "package-version"));
  }

  @NonNull
  private static XSLT30Param _readParam (@NonNull final Element aParam)
  {
    final ICommonsOrderedMap <String, String> aNamespaces = getInScopeNamespaces (aParam);
    final String sName = _attr (aParam, "name");
    return new XSLT30Param (resolveName (sName, aNamespaces),
                            sName,
                            _attr (aParam, "select"),
                            _attr (aParam, "as").strip (),
                            _isYes (_attr (aParam, "static")),
                            _isYes (_attr (aParam, "tunnel")),
                            aNamespaces);
  }

  @NonNull
  private static ICommonsList <XSLT30Param> _readParams (@NonNull final Element aParent)
  {
    final ICommonsList <XSLT30Param> ret = new CommonsArrayList <> ();
    for (final Element aParam : _childElements (aParent, "param"))
      ret.add (_readParam (aParam));
    return ret;
  }

  /**
   * Read one environment element.
   *
   * @param aEnv
   *        The <code>&lt;environment&gt;</code> element.
   * @param aBaseDir
   *        Directory of the containing file.
   * @param bCatalogLevel
   *        <code>true</code> if it is declared in the catalog.
   * @return The environment.
   */
  @NonNull
  static XSLT30Environment readEnvironment (@NonNull final Element aEnv,
                                            @NonNull final Path aBaseDir,
                                            final boolean bCatalogLevel)
  {
    final ICommonsList <XSLT30Environment.Source> aSources = new CommonsArrayList <> ();
    final ICommonsList <XSLT30Environment.Module> aStylesheets = new CommonsArrayList <> ();
    final ICommonsList <XSLT30Environment.Module> aPackages = new CommonsArrayList <> ();
    final ICommonsList <XSLT30Environment.Schema> aSchemas = new CommonsArrayList <> ();
    final ICommonsList <XSLT30Environment.Resource> aResources = new CommonsArrayList <> ();
    final ICommonsList <XSLT30Environment.Collection> aCollections = new CommonsArrayList <> ();
    final ICommonsList <XSLT30Param> aParams = new CommonsArrayList <> ();
    for (final Element aChild : _childElements (aEnv, null))
    {
      switch (_localName (aChild))
      {
        case "source":
          aSources.add (_readSource (aChild));
          break;
        case "stylesheet":
          aStylesheets.add (_readModule (aChild));
          break;
        case "package":
          aPackages.add (_readModule (aChild));
          break;
        case "schema":
          aSchemas.add (new XSLT30Environment.Schema (_attr (aChild, "uri"),
                                                      _attr (aChild, "file"),
                                                      _attr (aChild, "role")));
          break;
        case "resource":
          aResources.add (new XSLT30Environment.Resource (_attr (aChild, "file"),
                                                          _attr (aChild, "uri"),
                                                          _attr (aChild, "encoding"),
                                                          _attr (aChild, "media-type")));
          break;
        case "collection":
        {
          final ICommonsList <XSLT30Environment.Source> aMembers = new CommonsArrayList <> ();
          for (final Element aSource : _childElements (aChild, "source"))
            aMembers.add (_readSource (aSource));
          aCollections.add (new XSLT30Environment.Collection (_attr (aChild, "uri"), aMembers));
          break;
        }
        case "param":
          aParams.add (_readParam (aChild));
          break;
        default:
          // description, collation, output etc. are not used
          break;
      }
    }
    return new XSLT30Environment (_attr (aEnv, "name"),
                                  _attr (aEnv, "ref"),
                                  aBaseDir,
                                  bCatalogLevel,
                                  aSources,
                                  aStylesheets,
                                  aPackages,
                                  aSchemas,
                                  aResources,
                                  aCollections,
                                  aParams);
  }

  /**
   * Read one assertion element with its nested assertions.
   *
   * @param aElement
   *        The element.
   * @return The assertion.
   */
  @NonNull
  static XSLT30Assertion readAssertion (@NonNull final Element aElement)
  {
    final ICommonsList <XSLT30Assertion> aChildren = new CommonsArrayList <> ();
    for (final Element aChild : _childElements (aElement, null))
      aChildren.add (readAssertion (aChild));
    final ICommonsOrderedMap <String, String> aAttrMap = new CommonsLinkedHashMap <> ();
    final NamedNodeMap aAttrs = aElement.getAttributes ();
    for (int i = 0; i < aAttrs.getLength (); ++i)
    {
      final Attr aAttr = (Attr) aAttrs.item (i);
      if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals (aAttr.getNamespaceURI ()))
        aAttrMap.put (_localName (aAttr), aAttr.getValue ());
    }
    return new XSLT30Assertion (_localName (aElement),
                                aAttrMap,
                                _directText (aElement),
                                getInScopeNamespaces (aElement),
                                aChildren);
  }

  private static XSLT30Invocation.@Nullable Entry _readEntry (@Nullable final Element aEntry)
  {
    if (aEntry == null)
      return null;
    final ICommonsOrderedMap <String, String> aNamespaces = getInScopeNamespaces (aEntry);
    final String sName = _attr (aEntry, "name");
    return new XSLT30Invocation.Entry (sName.isBlank () ? null : resolveName (sName, aNamespaces),
                                 sName.strip (),
                                 _attr (aEntry, "select"),
                                 _readParams (aEntry),
                                 aNamespaces);
  }

  @NonNull
  private static XSLT30Invocation _readTest (@Nullable final Element aTest, @NonNull final Element aTestCase)
  {
    if (aTest == null)
      return new XSLT30Invocation (new CommonsArrayList <> (),
                                   new CommonsArrayList <> (),
                                   null,
                                   null,
                                   null,
                                   new CommonsArrayList <> (),
                                   null,
                                   null,
                                   getInScopeNamespaces (aTestCase));
    final ICommonsList <XSLT30Environment.Module> aStylesheets = new CommonsArrayList <> ();
    for (final Element aChild : _childElements (aTest, "stylesheet"))
      aStylesheets.add (_readModule (aChild));
    final ICommonsList <XSLT30Environment.Module> aPackages = new CommonsArrayList <> ();
    for (final Element aChild : _childElements (aTest, "package"))
      aPackages.add (_readModule (aChild));
    final Element aContextItem = _firstChildElement (aTest, "context-item");
    final Element aOutput = _firstChildElement (aTest, "output");
    return new XSLT30Invocation (aStylesheets,
                                 aPackages,
                                 _readEntry (_firstChildElement (aTest, "initial-template")),
                                 _readEntry (_firstChildElement (aTest, "initial-mode")),
                                 _readEntry (_firstChildElement (aTest, "initial-function")),
                                 _readParams (aTest),
                                 aContextItem == null ? null : _attr (aContextItem, "select"),
                                 aOutput == null ? null : new XSLT30Invocation.Output (_attr (aOutput, "file"),
                                                                                       _attr (aOutput, "serialize").strip (),
                                                                                       _attr (aOutput, "result-var").strip (),
                                                                                       _attr (aOutput, "tree").strip ()),
                                 getInScopeNamespaces (aTest));
  }

  @NonNull
  private static XSLT30TestCase _readTestCase (@NonNull final Element aTC, @NonNull final Path aDir)
  {
    final Element aEnv = _firstChildElement (aTC, "environment");
    final Element aResult = _firstChildElement (aTC, "result");
    XSLT30Assertion aAssertion = null;
    if (aResult != null)
    {
      final ICommonsList <Element> aAsserts = _childElements (aResult, null);
      if (aAsserts.isNotEmpty ())
        aAssertion = readAssertion (aAsserts.getFirstOrNull ());
    }
    return new XSLT30TestCase (_attr (aTC, "name"),
                               _readDependencies (aTC),
                               aEnv == null ? null : readEnvironment (aEnv, aDir, false),
                               _readTest (_firstChildElement (aTC, "test"), aTC),
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
  public static XSLT30Catalog readCatalog (@NonNull final InputSource aSource, @NonNull final Path aRootDir)
                                                                                                            throws IOException
  {
    final Element aRoot = _parse (aSource).getDocumentElement ();
    final ICommonsMap <String, XSLT30Environment> aEnvs = new CommonsHashMap <> ();
    for (final Element aEnv : _childElements (aRoot, "environment"))
    {
      final XSLT30Environment aParsed = readEnvironment (aEnv, aRootDir, true);
      aEnvs.put (aParsed.name (), aParsed);
    }
    final ICommonsList <XSLT30Catalog.TestSetRef> aSets = new CommonsArrayList <> ();
    for (final Element aSet : _childElements (aRoot, "test-set"))
      aSets.add (new XSLT30Catalog.TestSetRef (_attr (aSet, "name"), _attr (aSet, "file")));
    return new XSLT30Catalog (aRootDir, aEnvs, aSets);
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
  public static XSLT30Catalog readCatalog (@NonNull final Path aCatalogFile) throws IOException
  {
    final Path aAbs = aCatalogFile.toAbsolutePath ().normalize ();
    return readCatalog (new InputSource (aAbs.toUri ().toString ()), aAbs.getParent ());
  }

  /**
   * Read a test-set file.
   *
   * @param aSource
   *        The test-set XML.
   * @param aFile
   *        The test-set file, <code>null</code> if not read from a file.
   * @param aDir
   *        The directory of the test-set file.
   * @return The test set.
   * @throws IOException
   *         on read or parse errors
   */
  @NonNull
  public static XSLT30TestSet readTestSet (@NonNull final InputSource aSource,
                                           @Nullable final Path aFile,
                                           @NonNull final Path aDir) throws IOException
  {
    final Element aRoot = _parse (aSource).getDocumentElement ();
    final ICommonsMap <String, XSLT30Environment> aEnvs = new CommonsHashMap <> ();
    for (final Element aEnv : _childElements (aRoot, "environment"))
    {
      final XSLT30Environment aParsed = readEnvironment (aEnv, aDir, false);
      aEnvs.put (aParsed.name (), aParsed);
    }
    final ICommonsList <XSLT30TestCase> aCases = new CommonsArrayList <> ();
    for (final Element aTC : _childElements (aRoot, "test-case"))
      aCases.add (_readTestCase (aTC, aDir));
    return new XSLT30TestSet (_attr (aRoot, "name"), aFile, aDir, _readDependencies (aRoot), aEnvs, aCases);
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
  public static XSLT30TestSet readTestSet (@NonNull final Path aFile) throws IOException
  {
    final Path aAbs = aFile.toAbsolutePath ().normalize ();
    return readTestSet (new InputSource (aAbs.toUri ().toString ()), aAbs, aAbs.getParent ());
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
  public static XSLT30TestSet readTestSet (@NonNull final String sXml, @NonNull final Path aDir) throws IOException
  {
    return readTestSet (new InputSource (new StringReader (sXml)), null, aDir);
  }
}
