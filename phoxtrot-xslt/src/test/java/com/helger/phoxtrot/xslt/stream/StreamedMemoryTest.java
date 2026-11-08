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
package com.helger.phoxtrot.xslt.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Bounded memory of the streamed execution: a generated document of about
 * 250 MB is transformed by a streamable stylesheet in a JVM with a 32 MB heap,
 * which the same transformation without streaming cannot finish. A quarter of
 * it is transformed by one stylesheet per other streamed construct
 * (xsl:iterate, xsl:for-each-group, xsl:fork, xsl:merge of two such
 * documents, predicates with fn:generate-id, snapshots with accumulator
 * values) in the same heap. A further case copies every record into a result
 * of the same size, written to a stream while it is built; built in full
 * first, it does not fit either. The document is generated while it is read
 * and never stored. The size can be set with
 * <code>-Dstreamed.memory.records=&lt;n&gt;</code> (about 90 bytes each).
 *
 * @author Philip Helger
 */
public final class StreamedMemoryTest
{
  private static final Logger LOGGER = LoggerFactory.getLogger (StreamedMemoryTest.class);
  private static final long RECORDS = Long.getLong ("streamed.memory.records", 2_800_000L).longValue ();
  private static final String HEAP = "-Xmx32m";

  private record Outcome (int exitCode, @NonNull String output)
  {}

  @NonNull
  private static Outcome _fork (@NonNull final String sMode,
                                final long nRecords,
                                @NonNull final String sShape) throws IOException, InterruptedException
  {
    final String sJava = Path.of (System.getProperty ("java.home"), "bin", "java").toString ();
    final ICommonsList <String> aCmd = new CommonsArrayList <> (sJava,
                                                                HEAP,
                                                                "-cp",
                                                                System.getProperty ("java.class.path"),
                                                                StreamedMemoryMain.class.getName (),
                                                                Long.toString (nRecords),
                                                                sMode,
                                                                sShape);
    final Process aProcess = new ProcessBuilder (aCmd).redirectErrorStream (true).start ();
    final String sOutput = new String (aProcess.getInputStream ().readAllBytes (), StandardCharsets.UTF_8);
    return new Outcome (aProcess.waitFor (), sOutput);
  }

  @Test
  public void testBoundedMemory () throws IOException, InterruptedException
  {
    for (final String sShape : StreamedMemoryMain.SHAPES)
    {
      // The other constructs with a quarter of the document (still well
      // beyond the heap when read in full), to keep the test short
      final long nRecords = "for-each".equals (sShape) ? RECORDS : RECORDS / 4;
      final Outcome aStreamed = _fork ("streamed", nRecords, sShape);
      LOGGER.info ("Streamed run of " + sShape + " with " + HEAP + ":\n" + aStreamed.output ());
      assertEquals (sShape + ": " + aStreamed.output (), 0, aStreamed.exitCode ());
      assertTrue (sShape + ": " + aStreamed.output (),
                  aStreamed.output ().contains ("result=" + StreamedMemoryMain.getExpected (sShape, nRecords) + "\n"));
      assertTrue (sShape + ": " + aStreamed.output (), aStreamed.output ().contains ("streamed-runs=1\n"));
    }

    // The same document read in full does not fit
    final Outcome aBuffered = _fork ("buffered", RECORDS, "for-each");
    LOGGER.info ("Buffered run with " + HEAP + ":\n" + aBuffered.output ());
    assertEquals (aBuffered.output (), 3, aBuffered.exitCode ());
  }

  private static long _expectedCopyBytes (final long nRecords)
  {
    long ret = StreamedMemoryMain.COPY_HEAD.length () + StreamedMemoryMain.COPY_TAIL.length ();
    for (long i = 1; i <= nRecords; ++i)
      ret += ("<rec id=\"r" + i + "\" g=\"" + i / 4 + "\"><v>" + i % 7 + "</v><pad>xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx</pad></rec>").length ();
    return ret;
  }

  @Test
  public void testBoundedOutput () throws IOException, InterruptedException
  {
    final Outcome aDrained = _fork ("copy", RECORDS, "for-each");
    LOGGER.info ("Streamed copy written while it is built, with " + HEAP + ":\n" + aDrained.output ());
    assertEquals (aDrained.output (), 0, aDrained.exitCode ());
    assertTrue (aDrained.output (), aDrained.output ().contains ("out-bytes=" + _expectedCopyBytes (RECORDS) + "\n"));
    assertTrue (aDrained.output (),
                aDrained.output ().contains ("</pad></rec>" + StreamedMemoryMain.COPY_TAIL + "\n"));
    assertTrue (aDrained.output (), aDrained.output ().contains ("streamed-runs=1\n"));
    assertTrue (aDrained.output (), aDrained.output ().contains ("drained-runs=1\n"));

    // The same result built in full before it is written does not fit
    final Outcome aUndrained = _fork ("copy-undrained", RECORDS, "for-each");
    LOGGER.info ("Streamed copy built in full, with " + HEAP + ":\n" + aUndrained.output ());
    assertEquals (aUndrained.output (), 3, aUndrained.exitCode ());
  }
}
