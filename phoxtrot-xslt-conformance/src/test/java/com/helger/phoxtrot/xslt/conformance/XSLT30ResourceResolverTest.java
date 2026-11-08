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
package com.helger.phoxtrot.xslt.conformance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.phoxtrot.xpath.XPathException;

/**
 * Test class for class {@link XSLT30ResourceResolver}.
 *
 * @author Philip Helger
 */
public final class XSLT30ResourceResolverTest
{
  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  @Test
  public void testCollectionMembers () throws Exception
  {
    final Path aDir = m_aTmp.getRoot ().toPath ().toRealPath ();
    Files.writeString (aDir.resolve ("a.xml"), "<a/>", StandardCharsets.UTF_8);
    Files.writeString (aDir.resolve ("b.xml"), "<b/>", StandardCharsets.UTF_8);
    final XSLT30ResourceResolver aResolver = new XSLT30ResourceResolver (null, aDir, new XSLT30DocumentCache ());
    final String sUri = aDir.toUri () + "?select=*.xml";
    assertEquals (2, aResolver.resolveCollection (sUri).size ());

    // A member that does not parse is an error, never left out silently
    Files.writeString (aDir.resolve ("c.xml"), "<c>", StandardCharsets.UTF_8);
    final XSLT30ResourceResolver aResolver2 = new XSLT30ResourceResolver (null, aDir, new XSLT30DocumentCache ());
    try
    {
      aResolver2.resolveCollection (sUri);
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("FODC0002", ex.getCode ());
    }
  }
}
