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
package com.helger.phoxtrot.xpath.bench;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * The <code>xsl:number</code> workload of {@link EXsltCase#NUMBER}, see {@link XsltWorkload}. Its
 * time grows with the square of the number of lines, so it runs with smaller sizes than the other
 * XSLT workloads, chosen to show the growth.
 *
 * @author Philip Helger
 */
@State (Scope.Benchmark)
@BenchmarkMode (Mode.AverageTime)
@OutputTimeUnit (TimeUnit.MILLISECONDS)
@Warmup (iterations = 3, time = 1)
@Measurement (iterations = 5, time = 1)
@Fork (value = 1, jvmArgsAppend = { "-Xmx3g", "-Xss8m" })
public class XsltNumberBenchmark
{
  /** The number of invoice lines (about ten elements each). */
  @Param ({ "500", "1000", "2000" })
  public int m_nSize;

  private IWorkload m_aWorkload;

  @Setup
  public void setup () throws Exception
  {
    m_aWorkload = EWorkloadGroup.XSLT.createVerified ("number", m_nSize);
  }

  @Benchmark
  public Object run () throws Exception
  {
    return m_aWorkload.run ();
  }
}
