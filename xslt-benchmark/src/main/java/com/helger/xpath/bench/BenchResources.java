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
package com.helger.xpath.bench;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.NonNull;

/**
 * Access to the stylesheets and schemas of the benchmark, which live next to this class in the
 * class path.
 *
 * @author Philip Helger
 */
public final class BenchResources
{
  private BenchResources ()
  {}

  /**
   * @param sName
   *        The resource name relative to this package, e.g. <code>xsl/identity.xsl</code>.
   * @return The resource text, read as UTF-8. Never <code>null</code>.
   * @throws IllegalArgumentException
   *         if there is no such resource
   */
  @NonNull
  public static String getText (@NonNull final String sName)
  {
    try (final InputStream aIS = BenchResources.class.getResourceAsStream (sName))
    {
      if (aIS == null)
        throw new IllegalArgumentException ("No benchmark resource '" + sName + "'");
      return new String (aIS.readAllBytes (), StandardCharsets.UTF_8);
    }
    catch (final IOException ex)
    {
      throw new UncheckedIOException (ex);
    }
  }
}
