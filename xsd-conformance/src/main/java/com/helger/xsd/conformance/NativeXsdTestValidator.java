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
 * The native validator of <code>xsd-core</code> as {@link IXsdTestValidator}.
 * Schema documents and the files they reference are read from the local file
 * system only ({@link XsdFileAccess#LOCAL_FILES}); the network is never used.
 * Every construct the native validator does not implement yet is reported as
 * unsupported, never as a verdict. XSD 1.1 is not implemented yet (phase V8):
 * every XSD 1.1 case is unsupported.
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
    return eVersion == EXsdVersion.XSD_10;
  }

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
                                                        com.helger.xpath.context.EXsdVersion.V10,
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
