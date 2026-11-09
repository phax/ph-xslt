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
package com.helger.phoxtrot.xslt.compile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParser;

/**
 * Test class for class {@link FileResourceResolver}: documents are read
 * without node locations, stylesheet modules with.
 *
 * @author Philip Helger
 */
public final class FileResourceResolverTest
{
  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  @Test
  public void testLocations () throws IOException
  {
    final File aFile = m_aTempFolder.newFile ("doc.xml");
    Files.writeString (aFile.toPath (), "<a>\n  <b/>\n</a>", StandardCharsets.UTF_8);
    final String sUri = aFile.toPath ().toUri ().toString ();

    final IXdmNode aDoc = FileResourceResolver.INSTANCE.resolveDocument (sUri);
    final IXdmNode aB = aDoc.getChildAtIndex (0).getChildAtIndex (1);
    assertTrue (aB.isElement ());
    assertEquals (0, aB.getLine ());
    assertEquals (0, aB.getColumn ());
    assertEquals (sUri, aDoc.getDocumentUri ());

    final IXdmNode aModule = FileResourceResolver.INSTANCE.resolveStylesheetModule (sUri);
    assertEquals (2, aModule.getChildAtIndex (0).getChildAtIndex (1).getLine ());
    assertEquals (sUri, aModule.getDocumentUri ());

    // A resolver that delegates resolveDocument here gets a located module
    // through the default resolveStylesheetModule
    assertFalse (XdmParser.isRecordLocationsByDefault ());
    final IXdmNode aDelegated = XdmParser.getWithRecordLocations ( () -> FileResourceResolver.INSTANCE.resolveDocument (sUri));
    assertEquals (2, aDelegated.getChildAtIndex (0).getChildAtIndex (1).getLine ());
    assertFalse (XdmParser.isRecordLocationsByDefault ());

    assertNull (FileResourceResolver.INSTANCE.resolveDocument ("http://example.org/doc.xml"));
    assertNull (FileResourceResolver.INSTANCE.resolveStylesheetModule (sUri + ".missing"));
  }
}
