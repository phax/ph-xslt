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
package com.helger.phoxtrot.xslt.jaxp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltException;

/**
 * Test class for the resolvers of the JAXP adapter: the entity resolver and
 * {@link XMLConstants#ACCESS_EXTERNAL_DTD} for the documents and modules of
 * {@link JaxpResourceResolver}, the scheme check of {@link XMLConstants#ACCESS_EXTERNAL_STYLESHEET}
 * (nested <code>jar:</code> URIs included) and a {@link URIResolver} that refuses.
 *
 * @author Philip Helger
 */
public final class JaxpResolverTest
{
  private static final String XSL_START = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>";
  private static final String XSL_END = "</xsl:stylesheet>";
  private static final String SOURCE = "<root/>";
  private static final String REFUSAL = "blocked by policy";

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  /**
   * Answers from a map of file names to texts, returns <code>null</code> for any other system ID
   * and records the file name of every call.
   */
  private static final class RecordingEntityResolver implements EntityResolver
  {
    private final Map <String, String> m_aTexts;
    private final ICommonsList <String> m_aCalls = new CommonsArrayList <> ();

    RecordingEntityResolver (@NonNull final Map <String, String> aTexts)
    {
      m_aTexts = aTexts;
    }

    @Nullable
    public InputSource resolveEntity (final String sPublicID, final String sSystemID)
    {
      // The parser passes absolute file URIs; the name is enough here
      final String sName = Path.of (URI.create (sSystemID)).getFileName ().toString ();
      m_aCalls.add (sName);
      final String sText = m_aTexts.get (sName);
      return sText == null ? null : new InputSource (new StringReader (sText));
    }
  }

  @NonNull
  private static StreamSource _source (@NonNull final String sXml)
  {
    return new StreamSource (new StringReader (sXml));
  }

  @NonNull
  private static String _transform (@NonNull final Transformer aTransformer,
                                    @NonNull final Source aSource) throws TransformerException
  {
    final StringWriter aSW = new StringWriter ();
    aTransformer.transform (aSource, new StreamResult (aSW));
    return aSW.toString ();
  }

  @NonNull
  private static String _textSheet (@NonNull final String sSelect)
  {
    return XSL_START +
           "<xsl:output method='text'/><xsl:template match='/'><xsl:value-of select=\"" +
           sSelect +
           "\"/></xsl:template>" +
           XSL_END;
  }

  /**
   * @return The file URI of a document with an external DTD (default attribute <code>x</code>) and
   *         an external entity (<code>&amp;e;</code>), both files next to it.
   */
  @NonNull
  private String _writeDocumentWithDtd () throws Exception
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    Files.writeString (aDir.resolve ("data.dtd"), "<!ATTLIST data x CDATA 'from-file'><!ENTITY e SYSTEM 'ent.txt'>");
    Files.writeString (aDir.resolve ("ent.txt"), "entity-from-file");
    Files.writeString (aDir.resolve ("data.xml"), "<!DOCTYPE data SYSTEM 'data.dtd'><data>&e;</data>");
    return aDir.resolve ("data.xml").toUri ().toString ();
  }

  @NonNull
  private static PhoxtrotTransformerFactory _createLoadingFactory ()
  {
    final PhoxtrotTransformerFactory ret = new PhoxtrotTransformerFactory ();
    ret.setAttribute (PhoxtrotTransformerFactory.ATTR_LOAD_EXTERNAL_ENTITIES, Boolean.TRUE);
    return ret;
  }

  @Test
  public void testEntityResolverForDocument () throws Exception
  {
    final String sDataUri = _writeDocumentWithDtd ();
    final String sSheet = _textSheet ("let $d := document('" +
                                      sDataUri +
                                      "')/data return string-join(($d/@x, '|', string($d)), ' ')");
    final RecordingEntityResolver aResolver = new RecordingEntityResolver (Map.of ("data.dtd",
                                                                                   "<!ATTLIST data x CDATA 'from-resolver'><!ENTITY e 'entity-from-resolver'>"));

    // Without external entities: nothing is read, the resolver is not called
    PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_ENTITY_RESOLVER, aResolver);
    assertSame (aResolver, aTF.getAttribute (PhoxtrotTransformerFactory.ATTR_ENTITY_RESOLVER));
    assertEquals ("|", _transform (aTF.newTemplates (_source (sSheet)).newTransformer (), _source (SOURCE)).trim ());
    assertTrue (aResolver.m_aCalls.isEmpty ());

    // With external entities: the resolver of the factory is asked first
    aTF = _createLoadingFactory ();
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_ENTITY_RESOLVER, aResolver);
    final Templates aTemplates = aTF.newTemplates (_source (sSheet));
    final PhoxtrotTransformer aT = (PhoxtrotTransformer) aTemplates.newTransformer ();
    assertSame (aResolver, aT.getEntityResolver ());
    assertEquals ("from-resolver | entity-from-resolver", _transform (aT, _source (SOURCE)));
    assertEquals ("[data.dtd]", aResolver.m_aCalls.toString ());

    // A resolver that answers nothing: the parser reads the files itself
    final RecordingEntityResolver aEmpty = new RecordingEntityResolver (Map.of ());
    aT.setEntityResolver (aEmpty);
    assertEquals ("from-file | entity-from-file", _transform (aT, _source (SOURCE)));
    assertEquals ("[data.dtd, ent.txt]", aEmpty.m_aCalls.toString ());

    // Reset returns to the factory's resolver
    aT.reset ();
    assertSame (aResolver, aT.getEntityResolver ());
  }

  @Test
  public void testEntityResolverForSourceAndInclude () throws Exception
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    Files.writeString (aDir.resolve ("inc.dtd"), "<!ENTITY greeting 'from-file'>");
    Files.writeString (aDir.resolve ("inc.xsl"),
                       "<!DOCTYPE xsl:stylesheet SYSTEM 'inc.dtd'>" +
                                                 XSL_START +
                                                 "<xsl:template name='greet'>&greeting;</xsl:template>" +
                                                 XSL_END);
    final StreamSource aMain = _source (XSL_START +
                                        "<xsl:output method='text'/><xsl:include href='inc.xsl'/>" +
                                        "<xsl:template match='/'><xsl:call-template name='greet'/>|<xsl:value-of select='/*/@x'/></xsl:template>" +
                                        XSL_END);
    aMain.setSystemId (aDir.resolve ("main.xsl").toUri ().toString ());
    final String sDataUri = _writeDocumentWithDtd ();

    final RecordingEntityResolver aResolver = new RecordingEntityResolver (Map.of ("inc.dtd",
                                                                                   "<!ENTITY greeting 'from-resolver'>",
                                                                                   "data.dtd",
                                                                                   "<!ATTLIST data x CDATA 'source-from-resolver'>"));
    final PhoxtrotTransformerFactory aTF = _createLoadingFactory ();
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_ENTITY_RESOLVER, aResolver);
    final Transformer aT = aTF.newTemplates (aMain).newTransformer ();
    // The included module was parsed with the resolver
    assertEquals ("[inc.dtd]", aResolver.m_aCalls.toString ());
    // And the source of the transformation too
    assertEquals ("from-resolver|source-from-resolver", _transform (aT, new StreamSource (sDataUri)));
    assertEquals ("[inc.dtd, data.dtd]", aResolver.m_aCalls.toString ());
  }

  @Test
  public void testAccessExternalDtd () throws Exception
  {
    final String sDataUri = _writeDocumentWithDtd ();
    final String sSheet = _textSheet ("document('" + sDataUri + "')/data/@x");

    // Secure processing: no external DTD from the parser itself, even with
    // external entities switched on and the document itself allowed
    final PhoxtrotTransformerFactory aTF = _createLoadingFactory ();
    aTF.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
    assertEquals ("", aTF.getAttribute (XMLConstants.ACCESS_EXTERNAL_DTD));
    aTF.setAttribute (XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "file");
    try
    {
      _transform (aTF.newTemplates (_source (sSheet)).newTransformer (), _source (SOURCE));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("FODC0002"));
    }

    // The same for the source of the transformation
    try
    {
      _transform (aTF.newTemplates (_source (_textSheet ("'x'"))).newTransformer (), new StreamSource (sDataUri));
      fail ();
    }
    catch (final TransformerException ex)
    {
      // expected
    }

    // The entity resolver still gives access
    final PhoxtrotTransformer aT = aTF.newTemplates (_source (sSheet)).newTransformer ();
    aT.setEntityResolver (new RecordingEntityResolver (Map.of ("data.dtd",
                                                               "<!ATTLIST data x CDATA 'from-resolver'><!ENTITY e ''>")));
    assertEquals ("from-resolver", _transform (aT, _source (SOURCE)));

    // And so does allowing the file scheme
    aTF.setAttribute (XMLConstants.ACCESS_EXTERNAL_DTD, "file");
    assertEquals ("from-file", _transform (aTF.newTemplates (_source (sSheet)).newTransformer (), _source (SOURCE)));
  }

  /**
   * @return A schema-aware stylesheet that imports namespace urn:t from the location and tests a
   *         value against its type t:code.
   */
  @NonNull
  private static String _schemaSheet (@NonNull final String sLocation)
  {
    return "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform' xmlns:t='urn:t'>" +
           "<xsl:import-schema namespace='urn:t' schema-location='" +
           sLocation +
           "'/>" +
           "<xsl:output method='text'/><xsl:template match='/'><xsl:value-of select=\"'123' castable as t:code\"/></xsl:template>" +
           XSL_END;
  }

  @Test
  public void testImportSchemaAccess () throws Exception
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    final String sXsdStart = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t'>";
    final String sType = "<xs:simpleType name='code'><xs:restriction base='xs:string'><xs:pattern value='[0-9]+'/></xs:restriction></xs:simpleType>";
    Files.writeString (aDir.resolve ("inc.xsd"), sXsdStart + sType + "</xs:schema>");
    Files.writeString (aDir.resolve ("main.xsd"), sXsdStart + "<xs:include schemaLocation='inc.xsd'/></xs:schema>");
    final String sMainUri = aDir.resolve ("main.xsd").toUri ().toString ();
    final String sMainXsd = Files.readString (aDir.resolve ("main.xsd"));
    final String sSheet = _schemaSheet (sMainUri);

    // Default: the schema and its include are read from the files
    PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_SCHEMA_AWARE, Boolean.TRUE);
    assertEquals ("true", _transform (aTF.newTemplates (_source (sSheet)).newTransformer (), _source (SOURCE)));

    // Secure processing: neither is read, so the type is unknown
    aTF.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
    try
    {
      aTF.newTemplates (_source (sSheet));
      fail ();
    }
    catch (final TransformerConfigurationException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XQST0052"));
    }

    // The URI resolver gives the schema document, but its include is still
    // not read from the file
    final ICommonsList <String> aCalls = new CommonsArrayList <> ();
    aTF.setURIResolver ((sHref, sBase) -> {
      aCalls.add (sHref);
      final StreamSource ret = new StreamSource (new StringReader (sMainXsd));
      ret.setSystemId (sHref);
      return ret;
    });
    try
    {
      aTF.newTemplates (_source (sSheet));
      fail ();
    }
    catch (final TransformerConfigurationException ex)
    {
      // The include is unavailable like a missing file, so the type is unknown
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XQST0052"));
    }
    assertEquals ("[" + sMainUri + "]", aCalls.toString ());

    // Allowing local files reads the include as well
    aTF.setAttribute (XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "file");
    assertEquals ("true", _transform (aTF.newTemplates (_source (sSheet)).newTransformer (), _source (SOURCE)));

    // A schema document without references only needs the URI resolver
    aTF = new PhoxtrotTransformerFactory ();
    aTF.setAttribute (PhoxtrotTransformerFactory.ATTR_SCHEMA_AWARE, Boolean.TRUE);
    aTF.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
    aTF.setURIResolver ((sHref, sBase) -> _source (sXsdStart + sType + "</xs:schema>"));
    assertEquals ("true",
                  _transform (aTF.newTemplates (_source (_schemaSheet ("http://example.org/t.xsd"))).newTransformer (),
                              _source (SOURCE)));
  }

  @Test
  public void testIsAccessAllowed ()
  {
    assertTrue (JaxpResourceResolver.isAccessAllowed ("http://example.org/a.xml", "all"));
    assertTrue (JaxpResourceResolver.isAccessAllowed ("http://example.org/a.xml", " ALL "));
    assertFalse (JaxpResourceResolver.isAccessAllowed ("file:/a.xml", ""));
    assertTrue (JaxpResourceResolver.isAccessAllowed ("file:/a.xml", "jar, file"));

    // Schemes are compared case-insensitively
    assertTrue (JaxpResourceResolver.isAccessAllowed ("FILE:/a.xml", "file"));
    assertTrue (JaxpResourceResolver.isAccessAllowed ("file:/a.xml", " FILE "));

    // A URI without scheme is refused
    assertFalse (JaxpResourceResolver.isAccessAllowed ("a.xml", "file"));
    assertFalse (JaxpResourceResolver.isAccessAllowed (":a.xml", "file"));

    // A jar URI needs jar and its nested URI to be allowed
    assertTrue (JaxpResourceResolver.isAccessAllowed ("jar:file:/x.jar!/a.xml", "jar,file"));
    assertFalse (JaxpResourceResolver.isAccessAllowed ("jar:file:/x.jar!/a.xml", "jar"));
    assertFalse (JaxpResourceResolver.isAccessAllowed ("jar:file:/x.jar!/a.xml", "file"));
    assertFalse (JaxpResourceResolver.isAccessAllowed ("jar:http://example.org/x.jar!/a.xml", "jar,file"));
    assertTrue (JaxpResourceResolver.isAccessAllowed ("jar:http://example.org/x.jar!/a.xml", "jar,http"));
    assertFalse (JaxpResourceResolver.isAccessAllowed ("JAR:HTTP://example.org/x.jar!/a.xml", "jar,file"));

    // Nested jar URIs are checked all the way down
    assertFalse (JaxpResourceResolver.isAccessAllowed ("jar:jar:http://example.org/x.jar!/y.jar!/a.xml", "jar,file"));
    assertTrue (JaxpResourceResolver.isAccessAllowed ("jar:jar:file:/x.jar!/y.jar!/a.xml", "jar,file"));
    assertFalse (JaxpResourceResolver.isAccessAllowed ("jar:", "jar"));
  }

  @Test
  public void testNestedJarRefusedAtRunTime () throws Exception
  {
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    aTF.setAttribute (XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "jar,file");
    // Refused by the scheme check before anything is opened
    final Transformer aT = aTF.newTemplates (_source (_textSheet ("doc-available('jar:http://example.invalid/x.jar!/a.xml')")))
                              .newTransformer ();
    assertEquals ("false", _transform (aT, _source (SOURCE)));
  }

  @NonNull
  private static PhoxtrotTransformerFactory _createRefusingFactory (@NonNull final ICommonsList <String> aCalls)
  {
    final PhoxtrotTransformerFactory ret = new PhoxtrotTransformerFactory ();
    ret.setURIResolver ((sHref, sBase) -> {
      aCalls.add (sHref);
      throw new TransformerException (REFUSAL);
    });
    return ret;
  }

  @Test
  public void testRefusingResolverForTransform () throws Exception
  {
    final ICommonsList <String> aCalls = new CommonsArrayList <> ();
    final Transformer aT = _createRefusingFactory (aCalls).newTemplates (_source (_textSheet ("string(transform(map { 'stylesheet-location' : 'http://example.org/xsl/other.xsl' })?output)")))
                                                          .newTransformer ();
    try
    {
      _transform (aT, _source (SOURCE));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("FOXT0002"));
      assertTrue (ex.getMessage (), ex.getMessage ().contains (REFUSAL));
    }
    assertTrue (aCalls.toString (), aCalls.contains ("http://example.org/xsl/other.xsl"));
  }

  @Test
  public void testRefusingResolverForModules ()
  {
    for (final String sInstruction : new String [] { "include", "import" })
    {
      final ICommonsList <String> aCalls = new CommonsArrayList <> ();
      final StreamSource aMain = _source (XSL_START + "<xsl:" + sInstruction + " href='mod.xsl'/>" + XSL_END);
      aMain.setSystemId ("http://example.org/xsl/main.xsl");
      try
      {
        _createRefusingFactory (aCalls).newTemplates (aMain);
        fail ();
      }
      catch (final TransformerConfigurationException ex)
      {
        assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XTSE0165"));
        assertTrue (ex.getMessage (), ex.getMessage ().contains (REFUSAL));
        assertNotNull (ex.getLocator ());
        assertEquals ("http://example.org/xsl/main.xsl", ex.getLocator ().getSystemId ());
        assertTrue (ex.getLocator ().getLineNumber () > 0);
      }
      // The URI resolver gets the href as written
      assertEquals ("[mod.xsl]", aCalls.toString ());
    }
  }

  @Test
  public void testRefusingResolverForParameterDocument ()
  {
    final ICommonsList <String> aCalls = new CommonsArrayList <> ();
    final StreamSource aMain = _source (XSL_START + "<xsl:output parameter-document='params.xml'/>" + XSL_END);
    aMain.setSystemId ("http://example.org/xsl/main.xsl");
    try
    {
      _createRefusingFactory (aCalls).newTemplates (aMain);
      fail ();
    }
    catch (final TransformerConfigurationException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XTSE0020"));
      assertTrue (ex.getMessage (), ex.getMessage ().contains (REFUSAL));
      assertNotNull (ex.getLocator ());
      assertTrue (ex.getLocator ().getLineNumber () > 0);
    }
    assertEquals ("[params.xml]", aCalls.toString ());
  }

  @Test
  public void testRefusingResolverForPackage ()
  {
    final ICommonsList <String> aCalls = new CommonsArrayList <> ();
    final URIResolver aRefusing = (sHref, sBase) -> {
      aCalls.add (sHref);
      throw new TransformerException (REFUSAL);
    };
    final XsltCompileOptions aOptions = new XsltCompileOptions ().setResolver (new JaxpResourceResolver (aRefusing,
                                                                                                         "all",
                                                                                                         new JaxpParserSettings (false,
                                                                                                                                 "all",
                                                                                                                                 null)))
                                                                 .addPackage (new XsltCompileOptions.PackageSource (null,
                                                                                                                    null,
                                                                                                                    "http://example.org/pkg/lib.xsl"));
    try
    {
      XsltCompiler.compile ("<xsl:package version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
                            "<xsl:use-package name='urn:lib'/>" +
                            "</xsl:package>",
                            aOptions);
      fail ();
    }
    catch (final XsltException ex)
    {
      // Not found, with the reason why the only source could not be read
      assertEquals ("XTSE3000", ex.getCode ());
      assertTrue (ex.getMessage (), ex.getMessage ().contains (REFUSAL));
      assertTrue (ex.isLocated ());
    }
    assertEquals ("[http://example.org/pkg/lib.xsl]", aCalls.toString ());
  }

  @Test
  public void testRefusingResolverAtRunTime () throws Exception
  {
    final String sUri = "http://example.org/data/a.xml";
    // The functions that fail, with the error code and the reason of the
    // resolver
    for (final String [] aCase : new String [] [] { { "document('" + sUri + "')", "FODC0002" },
                                                    { "doc('" + sUri + "')", "FODC0002" },
                                                    { "unparsed-text('" + sUri + "')", "FOUT1170" },
                                                    { "json-doc('" + sUri + "')", "FOUT1170" } })
    {
      final ICommonsList <String> aCalls = new CommonsArrayList <> ();
      final Transformer aT = _createRefusingFactory (aCalls).newTemplates (_source (_textSheet ("string(" +
                                                                                                aCase[0] +
                                                                                                ")")))
                                                            .newTransformer ();
      try
      {
        _transform (aT, _source (SOURCE));
        fail (aCase[0]);
      }
      catch (final TransformerException ex)
      {
        assertTrue (aCase[0] + ": " + ex.getMessage (), ex.getMessage ().startsWith (aCase[1]));
        assertTrue (aCase[0] + ": " + ex.getMessage (), ex.getMessage ().contains (REFUSAL));
      }
      assertEquals (aCase[0], "[" + sUri + "]", aCalls.toString ());
    }

    // The functions that only ask
    for (final String sExpr : new String [] { "doc-available('" + sUri + "')",
                                              "unparsed-text-available('" + sUri + "')" })
    {
      final ICommonsList <String> aCalls = new CommonsArrayList <> ();
      final Transformer aT = _createRefusingFactory (aCalls).newTemplates (_source (_textSheet (sExpr)))
                                                            .newTransformer ();
      assertEquals (sExpr, "false", _transform (aT, _source (SOURCE)));
      assertEquals (sExpr, "[" + sUri + "]", aCalls.toString ());
    }
  }

  @Test
  public void testFactoryAndTransformerResolver () throws Exception
  {
    final String sUri = "http://example.org/data/a.xml";
    final PhoxtrotTransformerFactory aTF = new PhoxtrotTransformerFactory ();
    final URIResolver aFactoryResolver = (sHref, sBase) -> _source ("<from-factory/>");
    aTF.setURIResolver (aFactoryResolver);
    final Transformer aT = aTF.newTemplates (_source (_textSheet ("name(doc('" + sUri + "')/*)"))).newTransformer ();
    assertSame (aFactoryResolver, aT.getURIResolver ());
    assertEquals ("from-factory", _transform (aT, _source (SOURCE)));

    // The transformer's resolver replaces the factory's at run time
    aT.setURIResolver ((sHref, sBase) -> _source ("<from-transformer/>"));
    assertEquals ("from-transformer", _transform (aT, _source (SOURCE)));

    // Without any resolver the URI is read directly, which secure processing
    // does not allow
    aT.setURIResolver (null);
    assertNull (aT.getURIResolver ());
    aTF.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, true);
    final Transformer aSecure = aTF.newTemplates (_source (_textSheet ("doc-available('" + sUri + "')")))
                                   .newTransformer ();
    aSecure.setURIResolver (null);
    assertEquals ("false", _transform (aSecure, _source (SOURCE)));

    // Reset returns to the factory's resolver
    aT.reset ();
    assertSame (aFactoryResolver, aT.getURIResolver ());
  }

  @Test
  public void testBrokenDocument () throws Exception
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    Files.writeString (aDir.resolve ("broken.xml"), "<a><b></a>");
    final String sUri = aDir.resolve ("broken.xml").toUri ().toString ();
    final Transformer aT = new PhoxtrotTransformerFactory ().newTemplates (_source (_textSheet ("doc-available('" +
                                                                                                sUri +
                                                                                                "')")))
                                                            .newTransformer ();
    // Unavailable (and logged as unreadable, not silently)
    assertEquals ("false", _transform (aT, _source (SOURCE)));
  }
}
