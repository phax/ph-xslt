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
package com.helger.xsd;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.xdm.schema.IXdmSchemaProvider;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.XdmSchemaException;
import com.helger.xpath.xdm.schema.XdmSchemaSource;

/**
 * The native schema processor as {@link IXdmSchemaProvider}: schema documents
 * are compiled by {@link XsdSchema} (XSD 1.0 until phase V8) and handed out as
 * a schema set that validates {@link com.helger.xpath.xdm.XdmNode} trees in
 * place (go-xslt <code>bridge.go</code>). Schema documents and the files they
 * reference are read from the local file system only.
 * <p>
 * Loaded by name by <code>XdmSchemaSupport</code> if this module is on the
 * class path.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class XsdSchemaProvider implements IXdmSchemaProvider
{
  public XsdSchemaProvider ()
  {}

  /**
   * @param aSchema
   *        A compiled schema. May not be <code>null</code>.
   * @return The schema set over it.
   */
  @NonNull
  public static IXdmSchemaSet createSchemaSet (@NonNull final XsdSchema aSchema)
  {
    return new XsdBridge (aSchema);
  }

  @Nullable
  private static Path _toPath (@NonNull final String sSystemID) throws XdmSchemaException
  {
    try
    {
      final URI aURI = URI.create (sSystemID);
      if (aURI.getScheme () == null)
        return Path.of (sSystemID).toAbsolutePath ().normalize ();
      if (!"file".equalsIgnoreCase (aURI.getScheme ()))
        return null;
      return Path.of (aURI).toAbsolutePath ().normalize ();
    }
    catch (final IllegalArgumentException ex)
    {
      throw new XdmSchemaException ("Invalid schema location '" + sSystemID + "'", ex);
    }
  }

  @NonNull
  private static XsdSchemaSource _toSource (@NonNull final XdmSchemaSource aSource) throws XdmSchemaException
  {
    final Path aPath = aSource.systemId () == null ? null : _toPath (aSource.systemId ());
    if (aSource.text () != null)
      return XsdSchemaSource.ofText (aSource.text (), aPath == null ? null : aPath.getParent ());
    if (aPath == null)
      throw new XdmSchemaException ("Cannot read the schema document " + aSource.systemId (),
                                    new IOException ("Only local files are read"));
    if (!Files.isRegularFile (aPath))
      throw new XdmSchemaException ("Cannot read the schema document " + aSource.systemId (),
                                    new IOException ("No such file"));
    try
    {
      return XsdSchemaSource.ofFile (aPath);
    }
    catch (final IOException ex)
    {
      throw new XdmSchemaException ("Cannot read the schema document " + aSource.systemId (), ex);
    }
  }

  @NonNull
  public IXdmSchemaSet compile (@NonNull final List <XdmSchemaSource> aSources) throws XdmSchemaException
  {
    final ICommonsList <XsdSchemaSource> aDocuments = new CommonsArrayList <> ();
    for (final XdmSchemaSource aSource : aSources)
      aDocuments.add (_toSource (aSource));
    try
    {
      return createSchemaSet (XsdSchema.compile (aDocuments,
                                                 EXsdVersion.V10,
                                                 new XsdCompileOptions ().setFileAccess (XsdFileAccess.LOCAL_FILES)));
    }
    catch (final XsdInvalidException ex)
    {
      throw new XdmSchemaException ("Invalid schema: " + ex.getMessage (), ex);
    }
    catch (final XsdUnsupportedException ex)
    {
      throw new XdmSchemaException ("Unsupported schema: " + ex.getMessage (), ex);
    }
  }
}
