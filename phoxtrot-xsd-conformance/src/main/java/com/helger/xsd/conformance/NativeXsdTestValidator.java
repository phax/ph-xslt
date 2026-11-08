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
package com.helger.xsd.conformance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.xsd.XsdCompileOptions;
import com.helger.xsd.XsdFileAccess;
import com.helger.xsd.XsdInvalidException;
import com.helger.xsd.XsdSchema;
import com.helger.xsd.XsdUnsupportedException;
import com.helger.xsd.XsdValidationResult;

/**
 * The native validator of <code>phoxtrot-xsd</code> as {@link IXsdTestValidator}.
 * Schema documents and the files they reference are read from the local file
 * system only ({@link XsdFileAccess#LOCAL_FILES}); the network is never used.
 * A construct the native validator does not implement is reported as
 * unsupported, never as a verdict. Both XSD 1.0 and 1.1 are supported.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class NativeXsdTestValidator implements IXsdTestValidator
{
  private static final String DISPLAY_NAME = "ph-xslt native XSD";

  /** A schema compiled by the native validator. */
  private static final class NativeSchema implements IXsdTestSchema
  {
    private final XsdSchema m_aSchema;

    NativeSchema (@NonNull final XsdSchema aSchema)
    {
      m_aSchema = aSchema;
    }

    /**
     * Validate the instance with the native validator.
     *
     * @param aInstance
     *        The local instance file.
     * @return Valid; invalid with the first reported error (or the message of
     *         an {@link XsdInvalidException}); or unsupported with the message
     *         of an {@link XsdUnsupportedException}.
     */
    @NonNull
    public XsdVerdict validate (@NonNull final Path aInstance)
    {
      try
      {
        final XsdValidationResult aResult = m_aSchema.validate (aInstance);
        if (aResult.valid ())
          return XsdVerdict.valid ();
        return XsdVerdict.invalid (aResult.errors ().isEmpty () ? null : aResult.errors ().getFirstOrNull ().toString ());
      }
      catch (final XsdInvalidException ex)
      {
        return XsdVerdict.invalid (ex.getMessage ());
      }
      catch (final XsdUnsupportedException ex)
      {
        return XsdVerdict.unsupported (ex.getMessage ());
      }
    }
  }

  @NonNull
  public String getDisplayName ()
  {
    return DISPLAY_NAME;
  }

  public boolean isVersionSupported (@NonNull final EXsdVersion eVersion)
  {
    return true;
  }

  /**
   * The <code>test</code> of an <code>xs:alternative</code> is compiled by
   * the full XPath engine of <code>phoxtrot-xpath</code>, so any XPath is
   * accepted in XSD 1.1, not only the required subset.
   */
  @Override
  public boolean isFullXPathInCtaSupported (@NonNull final EXsdVersion eVersion)
  {
    return eVersion == EXsdVersion.XSD_11;
  }

  /**
   * Compile the schema documents with
   * {@link XsdSchema#compileFiles(List, com.helger.xpath.context.EXsdVersion, XsdCompileOptions)}
   * as XSD 1.0 or 1.1 with local file access only. An
   * {@link XsdInvalidException} or an {@link IOException} reading a document
   * makes the set invalid, an {@link XsdUnsupportedException} makes it
   * unsupported.
   */
  @NonNull
  public XsdCompileResult compile (@NonNull final List <Path> aSchemaDocuments, @NonNull final EXsdVersion eVersion)
  {
    if (!isVersionSupported (eVersion))
      return XsdCompileResult.failed (XsdVerdict.unsupported ("XSD " +
                                                              eVersion.getID () +
                                                              " is not implemented by " +
                                                              getDisplayName ()));
    final XsdCompileOptions aOptions = new XsdCompileOptions ().setFileAccess (XsdFileAccess.LOCAL_FILES);
    try
    {
      // Qualified: the harness has its own EXsdVersion of the same name
      final XsdSchema aSchema = XsdSchema.compileFiles (aSchemaDocuments,
                                                        eVersion == EXsdVersion.XSD_11 ? com.helger.xpath.context.EXsdVersion.V11
                                                                                       : com.helger.xpath.context.EXsdVersion.V10,
                                                        aOptions);
      return XsdCompileResult.valid (new NativeSchema (aSchema));
    }
    catch (final XsdInvalidException ex)
    {
      return XsdCompileResult.failed (XsdVerdict.invalid (ex.getMessage ()));
    }
    catch (final XsdUnsupportedException ex)
    {
      return XsdCompileResult.failed (XsdVerdict.unsupported (ex.getMessage ()));
    }
    catch (final IOException ex)
    {
      return XsdCompileResult.failed (XsdVerdict.invalid ("cannot read a schema document: " + ex.getMessage ()));
    }
  }
}
