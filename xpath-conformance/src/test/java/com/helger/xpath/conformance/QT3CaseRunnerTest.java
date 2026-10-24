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

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;

/**
 * Test class for class {@link QT3CaseRunner}: the paths that do not need the
 * engine.
 *
 * @author Philip Helger
 */
public final class QT3CaseRunnerTest
{
  @Test
  public void testSkips () throws IOException
  {
    final Path aDir = Path.of ("target/does-not-exist").toAbsolutePath ();
    final QT3TestSet aSet = QT3CatalogReader.readTestSet ("""
        <test-set xmlns="http://www.w3.org/2010/09/qt-fots-catalog" name="s">
          <test-case name="no-assert"><test>1</test><result/></test-case>
          <test-case name="no-file"><test file="missing.xq"/><result><assert-true/></result></test-case>
        </test-set>
        """, aDir);
    final QT3CaseRunner aRunner = new QT3CaseRunner (OffsetDateTime.now ());
    final QT3Environment aEmpty = new QT3Environment ("", "", aDir);
    final QT3CaseRunner.CaseResult aNoAssert = aRunner.run (aSet.testCases ().get (0),
                                                            new CommonsArrayList <> (),
                                                            aEmpty,
                                                            aDir,
                                                            new QT3DocumentCache ());
    assertNotNull (aNoAssert);
    assertEquals (QT3Outcome.skip ("no-assertion"), aNoAssert.outcome ());
    assertEquals (QT3Outcome.skip ("test-file"),
                  aRunner.run (aSet.testCases ().get (1),
                               new CommonsArrayList <> (),
                               aEmpty,
                               aDir,
                               new QT3DocumentCache ()).outcome ());
  }
}
