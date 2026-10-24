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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
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
