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
package com.helger.phoxtrot.xpath.conformance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsTreeSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.collection.commons.ICommonsSortedSet;

/**
 * The accepted-failures check, a port of Go's
 * <code>checkAcceptedFailures</code>: every failing case must be named in
 * backticks in the accepted-failures markdown file, together with its reason.
 * Only names in a list position count: a backticked name in a heading line
 * (starting with <code>#</code>), or on a name-list line that holds nothing
 * but backticked names separated by commas or blanks (optionally a leading
 * <code>-</code> or <code>*</code> bullet and a trailing full stop). A
 * backticked word in prose (a case mentioned for comparison, a class name, a
 * system property) never accepts a failure.
 *
 * @author Philip Helger
 */
@Immutable
public final class QT3AcceptedFailures
{
  private static final Pattern BACKTICKED = Pattern.compile ("`([^`\\n]+)`");
  /** A line that is nothing but a list of backticked names. */
  private static final Pattern NAME_LIST_LINE = Pattern.compile ("(?:[-*][ \\t]+)?(?:`[^`\\n]+`[ \\t,]*)+\\.?");

  @PresentForCodeCoverage
  private static final QT3AcceptedFailures INSTANCE = new QT3AcceptedFailures ();

  private QT3AcceptedFailures ()
  {}

  /**
   * @param sLine
   *        One line of the markdown text.
   * @return <code>true</code> if the backticked names of the line count:
   *         a heading line or a name-list line.
   */
  private static boolean _isNamePosition (@NonNull final String sLine)
  {
    final String sStripped = sLine.strip ();
    return sStripped.startsWith ("#") || NAME_LIST_LINE.matcher (sStripped).matches ();
  }

  /**
   * @param sMarkdown
   *        The markdown text.
   * @return Every name in backticks in a list position (a heading or a
   *         name-list line, see the class description).
   */
  @NonNull
  @ReturnsMutableCopy
  public static ICommonsSet <String> getDocumentedNames (@NonNull final String sMarkdown)
  {
    final ICommonsSet <String> ret = new CommonsHashSet <> ();
    for (final String sLine : sMarkdown.split ("\\r?\\n", -1))
      if (_isNamePosition (sLine))
      {
        final Matcher aMatcher = BACKTICKED.matcher (sLine);
        while (aMatcher.find ())
          ret.add (aMatcher.group (1));
      }
    return ret;
  }

  /**
   * @param aDocumented
   *        The documented names.
   * @param aFails
   *        The failing case names.
   * @return The failing cases not documented, sorted and without duplicates.
   */
  @NonNull
  @ReturnsMutableCopy
  public static ICommonsSortedSet <String> getUndocumented (@NonNull final ICommonsSet <String> aDocumented,
                                                            @NonNull final Collection <String> aFails)
  {
    final ICommonsSortedSet <String> ret = new CommonsTreeSet <> ();
    for (final String sFail : aFails)
      if (!aDocumented.contains (sFail))
        ret.add (sFail);
    return ret;
  }

  /**
   * @param aFile
   *        The accepted-failures markdown file.
   * @param aFails
   *        The failing case names.
   * @return The failing cases not documented in the file.
   * @throws IOException
   *         if the file cannot be read
   */
  @NonNull
  @ReturnsMutableCopy
  public static ICommonsSortedSet <String> getUndocumented (@NonNull final Path aFile,
                                                            @NonNull final ICommonsList <String> aFails)
                                                                                                         throws IOException
  {
    return getUndocumented (getDocumentedNames (Files.readString (aFile, StandardCharsets.UTF_8)), aFails);
  }
}
