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
import java.util.regex.Pattern;

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
 * are compiled by {@link XsdSchema} as XSD 1.1 (like go-xslt's XSLT host; XSLT
 * 3.0 leaves the version to the processor) and handed out as
 * a schema set that validates {@link com.helger.xpath.xdm.IXdmNode} trees in
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
  /**
   * A URI scheme followed by a colon (RFC 3986). At least two characters, so
   * that a Windows drive letter is not taken as a scheme.
   */
  private static final Pattern URI_SCHEME = Pattern.compile ("^[A-Za-z][A-Za-z0-9+.\\-]+:");

  /**
   * Create the provider. It holds no state: every {@link #compile(List)}
   * call is independent. Public for loading by name.
   */
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
    return createSchemaSet (aSchema, true);
  }

  /**
   * @param aSchema
   *        A compiled schema. May not be <code>null</code>.
   * @param bLenientEntities
   *        <code>true</code> to drop the XSD 1.1 rule that an xs:ENTITY value
   *        names an unparsed entity when a tree is validated in place. An XSLT
   *        host does so: it cannot tell a schema written against 1.0 (where
   *        the rule does not exist) from a 1.1 one. <code>false</code> applies
   *        the rule exactly like {@link XsdSchema#validate}.
   * @return The schema set over it.
   */
  @NonNull
  public static IXdmSchemaSet createSchemaSet (@NonNull final XsdSchema aSchema, final boolean bLenientEntities)
  {
    return new XsdBridge (aSchema, bLenientEntities);
  }

  /**
   * Turn a system ID into a local path. A system ID without a URI scheme is
   * read as a file system path (a space or a Windows drive letter is fine),
   * made absolute against the working directory; a <code>file:</code> URI is
   * converted.
   *
   * @param sSystemID
   *        The system ID.
   * @return The absolute, normalised path, or <code>null</code> for any other
   *         URI scheme.
   * @throws XdmSchemaException
   *         if the system ID cannot be read as a URI or path
   */
  @Nullable
  private static Path _toPath (@NonNull final String sSystemID) throws XdmSchemaException
  {
    try
    {
      if (!URI_SCHEME.matcher (sSystemID).find ())
        return Path.of (sSystemID).toAbsolutePath ().normalize ();
      final URI aURI = URI.create (sSystemID);
      if (!"file".equalsIgnoreCase (aURI.getScheme ()))
        return null;
      return Path.of (aURI).toAbsolutePath ().normalize ();
    }
    catch (final IllegalArgumentException ex)
    {
      throw new XdmSchemaException ("Invalid schema location '" + sSystemID + "'", ex);
    }
  }

  /**
   * Turn a schema source of the SPI into a compiler source. Inline text is
   * taken as is, with the base directory for its references taken from the
   * system ID: the directory itself if the system ID ends with
   * <code>/</code>, else its parent; none without a system ID. Otherwise the
   * document is read from the local file the system ID names.
   *
   * @param aSource
   *        The source.
   * @return The compiler source.
   * @throws XdmSchemaException
   *         if the system ID is invalid, not local (another URI scheme or
   *         missing for a source without text), not a regular file, or cannot
   *         be read
   */
  @NonNull
  private static XsdSchemaSource _toSource (@NonNull final XdmSchemaSource aSource) throws XdmSchemaException
  {
    final Path aPath = aSource.systemId () == null ? null : _toPath (aSource.systemId ());
    if (aSource.text () != null)
    {
      // The base of an inline schema may be a directory (xml:base="dir/"): its
      // references resolve inside it, not beside it
      final Path aBaseDir = aPath == null ? null : aSource.systemId ().endsWith ("/") ? aPath : aPath.getParent ();
      return XsdSchemaSource.ofText (aSource.text (), aBaseDir);
    }
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

  /**
   * {@inheritDoc}
   * <p>
   * The documents are compiled together as one XSD 1.1 schema, with
   * referenced documents (include, import, redefine, override) read from any
   * local file and never from the network. The result is a lenient schema set
   * as by {@link #createSchemaSet(XsdSchema)}. An invalid schema and a schema
   * the processor does not support are both reported as
   * {@link XdmSchemaException}.
   */
  @NonNull
  public IXdmSchemaSet compile (@NonNull final List <XdmSchemaSource> aSources) throws XdmSchemaException
  {
    final ICommonsList <XsdSchemaSource> aDocuments = new CommonsArrayList <> ();
    for (final XdmSchemaSource aSource : aSources)
      aDocuments.add (_toSource (aSource));
    try
    {
      return createSchemaSet (XsdSchema.compile (aDocuments,
                                                 EXsdVersion.V11,
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
