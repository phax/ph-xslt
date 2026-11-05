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
package com.helger.xslt.stream;

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
 * 200 MB is transformed by a streamable stylesheet in a JVM with a 32 MB heap,
 * which the same transformation without streaming cannot finish. The document
 * is generated while it is read and never stored. The size can be set with
 * <code>-Dstreamed.memory.records=&lt;n&gt;</code> (about 75 bytes each).
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
  private static Outcome _fork (@NonNull final String sMode, final long nRecords) throws IOException, InterruptedException
  {
    final String sJava = Path.of (System.getProperty ("java.home"), "bin", "java").toString ();
    final ICommonsList <String> aCmd = new CommonsArrayList <> (sJava,
                                                                HEAP,
                                                                "-cp",
                                                                System.getProperty ("java.class.path"),
                                                                StreamedMemoryMain.class.getName (),
                                                                Long.toString (nRecords),
                                                                sMode);
    final Process aProcess = new ProcessBuilder (aCmd).redirectErrorStream (true).start ();
    final String sOutput = new String (aProcess.getInputStream ().readAllBytes (), StandardCharsets.UTF_8);
    return new Outcome (aProcess.waitFor (), sOutput);
  }

  @NonNull
  private static String _expected (final long nRecords)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (long i = StreamedMemoryMain.HIT_EVERY; i <= nRecords; i += StreamedMemoryMain.HIT_EVERY)
      aSB.append ('r').append (i).append (',');
    return aSB.toString ();
  }

  @Test
  public void testBoundedMemory () throws IOException, InterruptedException
  {
    final Outcome aStreamed = _fork ("streamed", RECORDS);
    LOGGER.info ("Streamed run with " + HEAP + ":\n" + aStreamed.output ());
    assertEquals (aStreamed.output (), 0, aStreamed.exitCode ());
    assertTrue (aStreamed.output (), aStreamed.output ().contains ("result=" + _expected (RECORDS) + "\n"));
    assertTrue (aStreamed.output (), aStreamed.output ().contains ("streamed-runs=1\n"));

    // The same document read in full does not fit
    final Outcome aBuffered = _fork ("buffered", RECORDS);
    LOGGER.info ("Buffered run with " + HEAP + ":\n" + aBuffered.output ());
    assertEquals (aBuffered.output (), 3, aBuffered.exitCode ());
  }
}
