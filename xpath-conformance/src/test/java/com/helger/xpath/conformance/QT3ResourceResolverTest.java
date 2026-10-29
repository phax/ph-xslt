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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.IXdmNode;

/**
 * Test class for class {@link QT3ResourceResolver}.
 *
 * @author Philip Helger
 */
public final class QT3ResourceResolverTest
{
  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  @Test
  public void testDecodeText ()
  {
    final byte [] aUtf8 = "äb".getBytes (StandardCharsets.UTF_8);
    assertEquals ("äb", QT3ResourceResolver.decodeText (aUtf8, null, null));
    // Declared ISO-8859-1 wins
    assertEquals ("Ã¤b", QT3ResourceResolver.decodeText (aUtf8, "ISO-8859-1", null));
    // Requested encoding when there is no BOM
    assertEquals ("Ã¤b", QT3ResourceResolver.decodeText (aUtf8, null, "iso-8859-1"));
    // BOMs
    assertEquals ("ab", QT3ResourceResolver.decodeText (new byte [] { (byte) 0xfe, (byte) 0xff, 0, 'a', 0, 'b' }, null, null));
    assertEquals ("ab", QT3ResourceResolver.decodeText (new byte [] { (byte) 0xff, (byte) 0xfe, 'a', 0, 'b', 0 }, null, null));
    assertEquals ("ab",
                  QT3ResourceResolver.decodeText (new byte [] { (byte) 0xef, (byte) 0xbb, (byte) 0xbf, 'a', 'b' },
                                                  null,
                                                  "iso-8859-1"));
    // Malformed UTF-8 is unavailable
    assertNull (QT3ResourceResolver.decodeText (new byte [] { 'a', (byte) 0xc3 }, null, null));
  }

  @Test
  public void testResolve () throws IOException
  {
    final Path aDir = m_aTmp.getRoot ().toPath ().toRealPath ();
    Files.createDirectories (aDir.resolve ("sub"));
    Files.writeString (aDir.resolve ("sub/doc.xml"), "<doc>1</doc>", StandardCharsets.UTF_8);
    Files.writeString (aDir.resolve ("sub/other.xml"), "<other/>", StandardCharsets.UTF_8);
    Files.writeString (aDir.resolve ("sub/bad.xml"), "<bad>", StandardCharsets.UTF_8);
    Files.writeString (aDir.resolve ("sub/t.txt"), "text", StandardCharsets.UTF_8);

    final String sXml = """
        <test-set xmlns="http://www.w3.org/2010/09/qt-fots-catalog" name="s">
          <environment name="e">
            <source role="." file="sub/doc.xml" uri="http://x/doc.xml"/>
            <source role="$bad" file="sub/bad.xml"/>
            <resource file="sub/t.txt" uri="http://x/t.txt"/>
            <collection uri="http://x/coll">
              <source file="sub/doc.xml"/>
              <source file="sub/other.xml"/>
            </collection>
          </environment>
        </test-set>
        """;
    final QT3Environment aEnv = QT3CatalogReader.readTestSet (sXml, aDir).environments ().get ("e");
    final QT3DocumentCache aCache = new QT3DocumentCache ();
    final QT3ResourceResolver aResolver = new QT3ResourceResolver (aEnv, aCache);

    final IXdmNode aDoc = aResolver.getSourceDocument (aEnv.getAllSources ().getFirstOrNull ());
    assertNotNull (aDoc);
    final String sFileUri = QT3ResourceResolver.getFileUri (aDir.resolve ("sub/doc.xml"));
    assertEquals (sFileUri, aDoc.getDocumentUri ());
    // Stable: the source, its declared URI and its file URI are the same node
    assertSame (aDoc, aResolver.resolveDocument ("http://x/doc.xml"));
    assertSame (aDoc, aResolver.resolveDocument (sFileUri));
    // A malformed source is unavailable
    assertNull (aResolver.getSourceDocument (aEnv.getAllSources ().get (1)));
    // Undeclared URIs are unavailable, except existing file URIs
    assertNull (aResolver.resolveDocument ("http://x/other.xml"));
    assertNotNull (aResolver.resolveDocument (QT3ResourceResolver.getFileUri (aDir.resolve ("sub/other.xml"))));
    assertNull (aResolver.resolveDocument (QT3ResourceResolver.getFileUri (aDir.resolve ("sub/missing.xml"))));

    assertEquals ("text", aResolver.resolveText ("http://x/t.txt", null));
    // Relative references and file URIs for unparsed text
    assertEquals ("text", aResolver.resolveText ("sub/t.txt", null));
    assertEquals ("text", aResolver.resolveText (QT3ResourceResolver.getFileUri (aDir.resolve ("sub/t.txt")), null));
    assertNull (aResolver.resolveText ("http://x/none.txt", null));
    assertNull (aResolver.resolveText ("sub/none.txt", null));

    final ICommonsList <IXdmNode> aColl = aResolver.resolveCollection ("http://x/coll");
    assertNotNull (aColl);
    assertEquals (2, aColl.size ());
    assertSame (aDoc, aColl.getFirstOrNull ());
    assertNull (aResolver.resolveCollection (""));
  }
}
