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
package com.helger.phoxtrot.xdm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Test class for {@link XdmParser#setEntityResolver(EntityResolver)} and
 * {@link XdmParser#setAccessExternalDtd(String)}.
 *
 * @author Philip Helger
 */
public final class XdmParserEntityResolverTest
{
  private static final String BASE = "http://example.org/doc/";

  /**
   * Answers from a map of absolute system IDs to texts, returns <code>null</code> for any other
   * system ID and records every system ID it was asked for.
   */
  private static final class MapResolver implements EntityResolver
  {
    private final Map <String, String> m_aTexts;
    private final ICommonsList <String> m_aCalls = new CommonsArrayList <> ();

    MapResolver (@NonNull final Map <String, String> aTexts)
    {
      m_aTexts = aTexts;
    }

    @Nullable
    public InputSource resolveEntity (final String sPublicID, final String sSystemID)
    {
      m_aCalls.add (sSystemID);
      final String sText = m_aTexts.get (sSystemID);
      // No system ID on purpose: the parser fills in the entity's one
      return sText == null ? null : new InputSource (new StringReader (sText));
    }
  }

  @NonNull
  private static InputSource _source (@NonNull final String sXml)
  {
    final InputSource ret = new InputSource (new StringReader (sXml));
    ret.setSystemId (BASE + "a.xml");
    return ret;
  }

  @Test
  public void testExternalDtdAndEntity () throws XdmParseException
  {
    final MapResolver aResolver = new MapResolver (Map.of (BASE + "a.dtd",
                                                           "<!ATTLIST a x CDATA 'def'><!ENTITY e SYSTEM 'sub/e.xml'>",
                                                           BASE + "sub/e.xml",
                                                           "<b>ext</b>"));
    final IXdmNode aDoc = new XdmParser ().setLoadExternalEntities (true)
                                          .setEntityResolver (aResolver)
                                          .parse (_source ("<!DOCTYPE a SYSTEM 'a.dtd'><a>&e;</a>"));
    final IXdmNode aRoot = aDoc.getChildAtIndex (0);
    assertEquals ("def", aRoot.getAttribute ("", "x").getValue ());
    assertEquals ("ext", aRoot.getStringValue ());
    // The entity's element keeps the entity's URI as base URI
    final IXdmNode aB = aRoot.getChildAtIndex (0);
    assertEquals ("b", aB.getName ().localName ());
    assertEquals (BASE + "sub/e.xml", aB.getBaseUri ());
    // The document element has the base URI of the document
    assertNull (aRoot.getBaseUri ());
    assertEquals (BASE + "a.xml", aDoc.getDocumentUri ());
    assertEquals (new CommonsArrayList <> (BASE + "a.dtd", BASE + "sub/e.xml"), aResolver.m_aCalls);
  }

  @Test
  public void testResolverReturnsNull () throws Exception
  {
    final Path aDir = Files.createTempDirectory ("xdmresolver");
    try
    {
      final Path aDtd = Files.writeString (aDir.resolve ("a.dtd"),
                                           "<!ATTLIST a x CDATA 'def'>",
                                           StandardCharsets.UTF_8);
      final Path aDoc = Files.writeString (aDir.resolve ("a.xml"),
                                           "<!DOCTYPE a SYSTEM 'a.dtd'><a/>",
                                           StandardCharsets.UTF_8);
      try
      {
        final MapResolver aResolver = new MapResolver (Map.of ());
        final IXdmNode aRoot = new XdmParser ().setLoadExternalEntities (true)
                                               .setEntityResolver (aResolver)
                                               .parse (aDoc)
                                               .getChildAtIndex (0);
        // The parser read the file itself
        assertEquals ("def", aRoot.getAttribute ("", "x").getValue ());
        assertEquals (1, aResolver.m_aCalls.size ());
        assertEquals (aDtd.toRealPath (), Path.of (URI.create (aResolver.m_aCalls.getFirstOrNull ())).toRealPath ());
      }
      finally
      {
        Files.delete (aDoc);
        Files.delete (aDtd);
      }
    }
    finally
    {
      Files.delete (aDir);
    }
  }

  @Test
  public void testAccessExternalDtd () throws Exception
  {
    final Path aDir = Files.createTempDirectory ("xdmaccess");
    try
    {
      final Path aDtd = Files.writeString (aDir.resolve ("a.dtd"),
                                           "<!ATTLIST a x CDATA 'def'>",
                                           StandardCharsets.UTF_8);
      final Path aDoc = Files.writeString (aDir.resolve ("a.xml"),
                                           "<!DOCTYPE a SYSTEM 'a.dtd'><a/>",
                                           StandardCharsets.UTF_8);
      try
      {
        assertEquals (XdmParser.DEFAULT_ACCESS_EXTERNAL_DTD, new XdmParser ().getAccessExternalDtd ());

        // No scheme allowed: the parser may not open the DTD itself
        try
        {
          new XdmParser ().setLoadExternalEntities (true).setAccessExternalDtd ("").parse (aDoc);
          fail ();
        }
        catch (final XdmParseException ex)
        {
          assertTrue (ex.getMessage (), ex.getMessage ().contains ("accessExternalDTD"));
        }

        // Another scheme only
        try
        {
          new XdmParser ().setLoadExternalEntities (true).setAccessExternalDtd ("http").parse (aDoc);
          fail ();
        }
        catch (final XdmParseException ex)
        {
          // expected
        }

        // The file scheme, case-insensitive
        IXdmNode aRoot = new XdmParser ().setLoadExternalEntities (true)
                                         .setAccessExternalDtd ("FILE")
                                         .parse (aDoc)
                                         .getChildAtIndex (0);
        assertEquals ("def", aRoot.getAttribute ("", "x").getValue ());

        // What the entity resolver returns is not checked
        final MapResolver aResolver = new MapResolver (Map.of (aDtd.toUri ().toString (), "<!ATTLIST a x CDATA 'res'>"));
        aRoot = new XdmParser ().setLoadExternalEntities (true)
                                .setAccessExternalDtd ("")
                                .setEntityResolver ( (sPublicID, sSystemID) -> {
                                  // The parser passes the URI in its own spelling (file:/ or file:///)
                                  final String sKey = Path.of (URI.create (sSystemID)).toUri ().toString ();
                                  return aResolver.resolveEntity (sPublicID, sKey);
                                })
                                .parse (aDoc)
                                .getChildAtIndex (0);
        assertEquals ("res", aRoot.getAttribute ("", "x").getValue ());

        // The setting is per parse: the reused parser of this thread allows all again
        aRoot = new XdmParser ().setLoadExternalEntities (true).parse (aDoc).getChildAtIndex (0);
        assertEquals ("def", aRoot.getAttribute ("", "x").getValue ());
      }
      finally
      {
        Files.delete (aDoc);
        Files.delete (aDtd);
      }
    }
    finally
    {
      Files.delete (aDir);
    }
  }

  @Test
  public void testNotCalledWithoutExternalEntities () throws XdmParseException
  {
    final MapResolver aResolver = new MapResolver (Map.of (BASE + "a.dtd",
                                                           "<!ATTLIST a x CDATA 'def'>",
                                                           BASE + "e.xml",
                                                           "<b>ext</b>"));
    final XdmParser aParser = new XdmParser ().setEntityResolver (aResolver);
    assertTrue (XdmParser.DEFAULT_LOAD_EXTERNAL_ENTITIES == aParser.isLoadExternalEntities ());
    final IXdmNode aRoot = aParser.parse (_source ("<!DOCTYPE a SYSTEM 'a.dtd' [<!ENTITY e SYSTEM 'e.xml'>]><a>&e;</a>"))
                                  .getChildAtIndex (0);
    assertNull (aRoot.getAttribute ("", "x"));
    assertEquals ("", aRoot.getStringValue ());
    assertTrue (aResolver.m_aCalls.isEmpty ());
  }

  @Test
  public void testEntityResolver2 () throws XdmParseException
  {
    final ICommonsList <String> aCalls = new CommonsArrayList <> ();
    final EntityResolver2 aResolver = new EntityResolver2 ()
    {
      public InputSource getExternalSubset (final String sName, final String sBaseURI)
      {
        throw new IllegalStateException ("external subset asked");
      }

      public InputSource resolveEntity (final String sName,
                                        final String sPublicID,
                                        final String sBaseURI,
                                        final String sSystemID)
      {
        aCalls.add (sSystemID + "@" + sBaseURI);
        return new InputSource (new StringReader ("<b/>"));
      }

      public InputSource resolveEntity (final String sPublicID, final String sSystemID)
      {
        throw new IllegalStateException ("SAX1 call");
      }
    };
    final IXdmNode aRoot = new XdmParser ().setLoadExternalEntities (true)
                                           .setEntityResolver (aResolver)
                                           .parse (_source ("<!DOCTYPE a [<!ENTITY e SYSTEM 'e.xml'>]><a>&e;</a>"))
                                           .getChildAtIndex (0);
    // The SAX2 call with the system ID as written; the source without system
    // ID gets the absolute one
    assertEquals (BASE + "e.xml", aRoot.getChildAtIndex (0).getBaseUri ());
    assertEquals (new CommonsArrayList <> ("e.xml@" + BASE + "a.xml"), aCalls);
  }

  @Test
  public void testResolverException ()
  {
    final EntityResolver aResolver = (sPublicID, sSystemID) -> { throw new SAXException ("refused " + sSystemID); };
    try
    {
      new XdmParser ().setLoadExternalEntities (true)
                      .setEntityResolver (aResolver)
                      .parse (_source ("<!DOCTYPE a SYSTEM 'a.dtd'><a/>"));
      fail ();
    }
    catch (final XdmParseException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().contains ("refused " + BASE + "a.dtd"));
    }
  }

  @Test
  public void testIOExceptionFromResolver ()
  {
    final EntityResolver aResolver = (sPublicID, sSystemID) -> { throw new IOException ("unreachable " + sSystemID); };
    try
    {
      new XdmParser ().setLoadExternalEntities (true)
                      .setEntityResolver (aResolver)
                      .parse (_source ("<!DOCTYPE a [<!ENTITY e SYSTEM 'e.xml'>]><a>&e;</a>"));
      fail ();
    }
    catch (final XdmParseException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().contains ("unreachable " + BASE + "e.xml"));
    }
  }
}
