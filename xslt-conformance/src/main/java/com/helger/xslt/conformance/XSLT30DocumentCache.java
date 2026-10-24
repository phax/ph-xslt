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
package com.helger.xslt.conformance;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;

/**
 * Parses and caches the documents of one test set by absolute path, so the
 * same file yields the same node (the principal source and
 * <code>fn:doc</code> on its URI are identical). Unlike the QT3 cache, external
 * DTD subsets and entities are read: the suite's sources rely on local DTDs
 * for entities and ID attributes, and the Go harness reads them too. A file
 * that cannot be parsed is remembered with its error. One instance per test
 * set; not thread-safe.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XSLT30DocumentCache
{
  private final ICommonsMap <Path, XdmNode> m_aDocs = new CommonsHashMap <> ();
  private final ICommonsMap <Path, XdmParseException> m_aFailed = new CommonsHashMap <> ();

  /**
   * @param aFile
   *        Absolute, normalised path.
   * @return The parsed document.
   * @throws XdmParseException
   *         if the file does not exist or is not well-formed
   */
  @NonNull
  public XdmNode getDocumentOrThrow (@NonNull final Path aFile) throws XdmParseException
  {
    final XdmNode aCached = m_aDocs.get (aFile);
    if (aCached != null)
      return aCached;
    final XdmParseException aFailed = m_aFailed.get (aFile);
    if (aFailed != null)
      throw aFailed;
    if (!Files.isRegularFile (aFile))
    {
      final XdmParseException ex = new XdmParseException ("No such file: " + aFile, new NoSuchFileException (aFile.toString ()));
      m_aFailed.put (aFile, ex);
      throw ex;
    }
    try
    {
      final XdmNode aDoc = new XdmParser ().setLoadExternalEntities (true).parse (aFile);
      m_aDocs.put (aFile, aDoc);
      return aDoc;
    }
    catch (final XdmParseException ex)
    {
      m_aFailed.put (aFile, ex);
      throw ex;
    }
  }

  /**
   * @param aFile
   *        Absolute, normalised path.
   * @return The parsed document or <code>null</code> if the file does not
   *         exist or is not well-formed.
   */
  @Nullable
  public XdmNode getDocument (@NonNull final Path aFile)
  {
    try
    {
      return getDocumentOrThrow (aFile);
    }
    catch (final XdmParseException ex)
    {
      return null;
    }
  }
}
