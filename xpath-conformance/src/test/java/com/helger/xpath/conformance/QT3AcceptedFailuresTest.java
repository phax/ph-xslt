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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;

/**
 * Test class for class {@link QT3AcceptedFailures}.
 *
 * @author Philip Helger
 */
public final class QT3AcceptedFailuresTest
{
  @Test
  public void testDocumentedNames ()
  {
    final ICommonsSet <String> aNames = QT3AcceptedFailures.getDocumentedNames ("""
        # Title
        ### Group
        `fn-a-1`, `fn-a-2`,
        `fn-b-3`

        **Status:** Accepted. Text with `` and a
        multi-line `not
        a name`.
        """);
    assertEquals (3, aNames.size ());
    assertTrue (aNames.contains ("fn-a-1"));
    assertTrue (aNames.contains ("fn-b-3"));
  }

  @Test
  public void testOnlyListPositions ()
  {
    final ICommonsSet <String> aNames = QT3AcceptedFailures.getDocumentedNames ("""
        # Title with `qt3.md`
        Prose with `QT3IT`, `-Dqt3.only=x` and the passing twin `fn-c-1`.
        ### `fn-d-1`, `fn-d-2`
        **Status:** As in `other.md`.
        Two cases, `fn-c-2` and `fn-c-3`, pass.
          `fn-e-1`, `fn-e-2`,
        `fn-e-3`.
        - `fn-f-1`
        * `fn-f-2`, `fn-f-3`
        `fn-g-1` passes since the fix.
        """);
    assertEquals (new CommonsHashSet <> ("qt3.md",
                                         "fn-d-1",
                                         "fn-d-2",
                                         "fn-e-1",
                                         "fn-e-2",
                                         "fn-e-3",
                                         "fn-f-1",
                                         "fn-f-2",
                                         "fn-f-3"),
                  aNames);
  }

  /**
   * The accepted-failures files of all three harnesses: the failing case
   * names stay accepted, case names that the prose only mentions do not.
   */
  @Test
  public void testRepositoryFiles () throws IOException
  {
    final ICommonsSet <String> aQT3 = QT3AcceptedFailures.getDocumentedNames (Files.readString (Path.of ("docs/accepted-failures/qt3.md")));
    assertTrue (aQT3.contains ("fn-transform-901"));
    assertTrue (aQT3.contains ("fn-load-xquery-module-074"));
    assertFalse (aQT3.contains ("QT3IT"));
    assertFalse (aQT3.contains ("fn:transform"));

    final Path aXsdDir = Path.of ("../xsd-conformance/docs/accepted-failures");
    final Path aXsltDir = Path.of ("../xslt-conformance/docs/accepted-failures");
    if (Files.isDirectory (aXsdDir) && Files.isDirectory (aXsltDir))
    {
      final ICommonsSet <String> aNative = QT3AcceptedFailures.getDocumentedNames (Files.readString (aXsdDir.resolve ("xsd-native.md")));
      assertTrue (aNative.contains ("particlesZ001.i"));
      assertTrue (aNative.contains ("idB005"));
      // Valid twins named in the reasons
      assertFalse (aNative.contains ("particlesT003"));

      final ICommonsSet <String> aXerces = QT3AcceptedFailures.getDocumentedNames (Files.readString (aXsdDir.resolve ("xsd.md")));
      assertTrue (aXerces.contains ("missing001.xsd"));
      assertFalse (aXerces.contains ("xs:integer"));

      final ICommonsSet <String> aStreaming = QT3AcceptedFailures.getDocumentedNames (Files.readString (aXsltDir.resolve ("xslt30-streaming.md")));
      // The last line of a name list ends with a full stop
      assertTrue (aStreaming.contains ("si-map-008"));
      assertTrue (aStreaming.contains ("sf-reverse-001"));
      assertFalse (aStreaming.contains ("system-property-013"));

      final ICommonsSet <String> aXslt = QT3AcceptedFailures.getDocumentedNames (Files.readString (aXsltDir.resolve ("xslt30.md")));
      assertTrue (aXslt.contains ("copy-1221"));
      // Mentioned as passing
      assertFalse (aXslt.contains ("copy-1220"));

      final ICommonsSet <String> aSA = QT3AcceptedFailures.getDocumentedNames (Files.readString (aXsltDir.resolve ("xslt30-schema-aware.md")));
      assertTrue (aSA.contains ("strip-space-009"));
      assertFalse (aSA.contains ("merge-049"));
    }
  }

  @Test
  public void testUndocumented ()
  {
    final ICommonsSet <String> aDocumented = QT3AcceptedFailures.getDocumentedNames ("`a` `c`");
    assertEquals (List.of ("b", "d"),
                  new CommonsArrayList <> (QT3AcceptedFailures.getUndocumented (aDocumented,
                                                                                List.of ("d", "a", "b", "b", "c"))));
  }

  @Test
  public void testModuleFile () throws IOException
  {
    // The module's own file exists and starts empty
    final Path aFile = Path.of (QT3Settings.DEFAULT_ACCEPTED_FILE);
    assertTrue (Files.isRegularFile (aFile));
    assertEquals (List.of ("x"),
                  new CommonsArrayList <> (QT3AcceptedFailures.getUndocumented (aFile,
                                                                                new CommonsArrayList <> ("x"))));
  }
}
