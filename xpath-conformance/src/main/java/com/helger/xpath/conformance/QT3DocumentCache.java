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

import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;

/**
 * Parses and caches the documents of one test set by absolute path, so the
 * same file yields the same node: a source document and the result of
 * <code>fn:doc</code> on its URI are identical, which is what
 * <code>fn:doc</code>'s stability rule asks for. A file that cannot be parsed
 * is remembered as unavailable. One instance per test set; not thread-safe.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class QT3DocumentCache
{
  private final ICommonsMap <Path, XdmNode> m_aDocs = new CommonsHashMap <> ();
  private final ICommonsSet <Path> m_aFailed = new CommonsHashSet <> ();

  /**
   * @param aFile
   *        Absolute, normalised path.
   * @return The parsed document or <code>null</code> if the file does not exist
   *         or is not well-formed.
   */
  @Nullable
  public XdmNode getDocument (@NonNull final Path aFile)
  {
    final XdmNode aCached = m_aDocs.get (aFile);
    if (aCached != null)
      return aCached;
    if (m_aFailed.contains (aFile))
      return null;
    if (!Files.isRegularFile (aFile))
    {
      m_aFailed.add (aFile);
      return null;
    }
    try
    {
      final XdmNode aDoc = new XdmParser ().parse (aFile);
      m_aDocs.put (aFile, aDoc);
      return aDoc;
    }
    catch (final XdmParseException ex)
    {
      m_aFailed.add (aFile);
      return null;
    }
  }
}
