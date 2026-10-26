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
package com.helger.xsd;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.xpath.context.EXsdVersion;

/**
 * Local file access only, optionally confined to directories (go-xslt's
 * <code>fsaccess.Guard</code> idea).
 *
 * @author Philip Helger
 */
public final class XsdFileAccessTest
{
  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  @Test
  public void testResolveLocation ()
  {
    final Path aBase = Path.of ("/base/dir");
    assertEquals (Path.of ("/base/dir/a.xsd"), XsdFileAccess.resolveLocation (aBase, "a.xsd"));
    assertEquals (Path.of ("/base/b.xsd"), XsdFileAccess.resolveLocation (aBase, "../b.xsd"));
    assertEquals (Path.of ("/abs/c.xsd"), XsdFileAccess.resolveLocation (aBase, "/abs/c.xsd"));
    assertEquals (Path.of ("/abs/d.xsd"), XsdFileAccess.resolveLocation (aBase, "file:///abs/d.xsd"));
    // Never the network
    assertNull (XsdFileAccess.resolveLocation (aBase, "http://www.w3.org/2001/XMLSchema.xsd"));
    assertNull (XsdFileAccess.resolveLocation (aBase, "https://example.org/x.xsd"));
    assertNull (XsdFileAccess.resolveLocation (aBase, "urn:x:y"));
    // Without a base only absolute locations resolve
    assertNull (XsdFileAccess.resolveLocation (null, "a.xsd"));
  }

  @Test
  public void testConfinement () throws IOException
  {
    final Path aAllowed = m_aTempFolder.newFolder ("allowed").toPath ();
    final Path aOther = m_aTempFolder.newFolder ("other").toPath ();
    final Path aInside = aAllowed.resolve ("in.xsd");
    final Path aOutside = aOther.resolve ("out.xsd");
    Files.writeString (aInside, "in", StandardCharsets.UTF_8);
    Files.writeString (aOutside, "out", StandardCharsets.UTF_8);
    final XsdFileAccess aConfined = XsdFileAccess.confinedTo (aAllowed);
    assertFalse (aConfined.isUnrestricted ());
    assertArrayEquals ("in".getBytes (StandardCharsets.UTF_8), aConfined.readFile (aInside));
    assertNull (aConfined.readFile (aOutside));
    // Escaping through ".." is resolved before the check
    assertNull (aConfined.readFile (aAllowed.resolve ("../other/out.xsd")));
    assertTrue (XsdFileAccess.LOCAL_FILES.isUnrestricted ());
    assertArrayEquals ("out".getBytes (StandardCharsets.UTF_8), XsdFileAccess.LOCAL_FILES.readFile (aOutside));
    assertNull (XsdFileAccess.LOCAL_FILES.readFile (aOther.resolve ("missing.xsd")));
  }

  @Test
  public void testConfinedCompile () throws IOException
  {
    final Path aAllowed = m_aTempFolder.newFolder ("schema").toPath ();
    final Path aOther = m_aTempFolder.newFolder ("elsewhere").toPath ();
    Files.writeString (aOther.resolve ("inc.xsd"),
                       "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"inc\" type=\"xs:string\"/></xs:schema>");
    final String sMain = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:include schemaLocation=\"../elsewhere/inc.xsd\"/>" +
                         "<xs:element name=\"main\" type=\"xs:string\"/></xs:schema>";
    // Unrestricted: the include contributes its element
    final XsdSchema aOpen = XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (sMain, aAllowed)),
                                               EXsdVersion.V10,
                                               null);
    assertEquals (2, aOpen.getView ().getAllGlobalElements ().size ());
    // Confined: the include outside the directory is unavailable, like a
    // missing file
    final XsdSchema aConfined = XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (sMain, aAllowed)),
                                                   EXsdVersion.V10,
                                                   new XsdCompileOptions ().setFileAccess (XsdFileAccess.confinedTo (aAllowed)));
    assertEquals (1, aConfined.getView ().getAllGlobalElements ().size ());
    assertSame (aConfined.getFileAccess (), aConfined.getFileAccess ());
  }
}
