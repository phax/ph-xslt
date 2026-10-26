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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.Test;
import org.w3c.dom.ls.LSInput;

/**
 * Test class for class {@link XsdLocalOnlyResourceResolver}.
 *
 * @author Philip Helger
 */
public final class XsdLocalOnlyResourceResolverTest
{
  @Test
  public void testLocal ()
  {
    final XsdLocalOnlyResourceResolver aResolver = new XsdLocalOnlyResourceResolver ();
    assertNull (aResolver.resolveResource (null, null, null, null, "file:/a/b.xsd"));
    assertNull (aResolver.resolveResource (null, null, null, "c.xsd", "file:/a/b.xsd"));
    assertNull (aResolver.resolveResource (null, null, null, "../c d.xsd", "file:/a/b.xsd"));
    assertNull (aResolver.resolveResource (null, null, null, "file:///x/c.xsd", "file:/a/b.xsd"));
    assertNull (aResolver.resolveResource (null, null, null, "c.xsd", null));
    assertEquals (0, aResolver.getBlocked ().size ());
  }

  @Test
  public void testRemote () throws IOException
  {
    final XsdLocalOnlyResourceResolver aResolver = new XsdLocalOnlyResourceResolver ();
    final LSInput aInput = aResolver.resolveResource (null, null, null, "http://example.org/x.xsd", "file:/a/b.xsd");
    assertNotNull (aInput);
    assertEquals ("http://example.org/x.xsd", aInput.getSystemId ());
    try (final InputStream aIS = aInput.getByteStream ())
    {
      assertThrows (IOException.class, aIS::read);
    }
    // Relative against a remote base, and an unparsable remote reference
    assertNotNull (aResolver.resolveResource (null, null, null, "y.xsd", "https://example.org/a/"));
    assertNotNull (aResolver.resolveResource (null, null, null, "http://127.0.0.1/must not resolve", null));
    assertEquals (List.of ("http://example.org/x.xsd", "y.xsd", "http://127.0.0.1/must not resolve"),
                  aResolver.getBlocked ());
  }
}
