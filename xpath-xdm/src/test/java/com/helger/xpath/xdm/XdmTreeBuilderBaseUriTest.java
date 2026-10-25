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
package com.helger.xpath.xdm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.SAXParserFactory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * Test class for the base URI handling of class {@link XdmTreeBuilder}.
 *
 * @author Philip Helger
 */
public final class XdmTreeBuilderBaseUriTest
{
  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  @Test
  public void testExternalEntityBase () throws Exception
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    Files.createDirectories (aDir.resolve ("sub"));
    final Path aMain = aDir.resolve ("main.xml");
    Files.writeString (aMain,
                       "<!DOCTYPE r [ <!ENTITY e SYSTEM 'sub/ent.xml'> ]><r>&e;<x/></r>",
                       StandardCharsets.UTF_8);
    Files.writeString (aDir.resolve ("sub/ent.xml"), "<a><b/></a><?p x?>", StandardCharsets.UTF_8);

    // A plain parser: XdmParser blocks external entities under secure processing
    final SAXParserFactory aFactory = SAXParserFactory.newDefaultInstance ();
    aFactory.setNamespaceAware (true);
    final XMLReader aReader = aFactory.newSAXParser ().getXMLReader ();
    final XdmTreeBuilder aBuilder = new XdmTreeBuilder ();
    aReader.setContentHandler (aBuilder);
    aReader.parse (new InputSource (aMain.toUri ().toString ()));

    final XdmNode aDoc = aBuilder.getDocument ();
    final XdmNode aRoot = aDoc.getChildAtIndex (0);
    final String sEntityUri = aDir.resolve ("sub/ent.xml").toUri ().toString ();
    assertNull (aRoot.getBaseUri ());
    final XdmNode aA = aRoot.getChildAtIndex (0);
    assertEquals ("a", aA.getName ().localName ());
    // Only the outermost nodes of the entity carry its URI
    assertEquals (sEntityUri.replace ("file:///", "file:/"), aA.getBaseUri ().replace ("file:///", "file:/"));
    assertNull (aA.getChildAtIndex (0).getBaseUri ());
    assertEquals (aA.getBaseUri (), aRoot.getChildAtIndex (1).getBaseUri ());
    assertNull (aRoot.getChildAtIndex (2).getBaseUri ());
  }
}
