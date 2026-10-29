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
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.XdmSchemaException;
import com.helger.xpath.xdm.schema.XdmSchemaSource;
import com.helger.xpath.xdm.schema.XdmSchemaSupport;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.schema.ValidationRequest;

/**
 * The schema-aware part of the XSLT30 harness (<code>-Dxslt30.schemaAware=true</code>),
 * a port of the schema handling of go-xslt's harness: the schemas of an
 * environment are offered to xsl:import-schema, and sources are validated as
 * the catalog asks (<code>&lt;source validation="strict|lax"&gt;</code>, or
 * lax for an environment with a <code>source-reference</code> schema).
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class XSLT30SchemaSupport
{
  private static final ConcurrentHashMap <List <Path>, Optional <IXdmSchemaSet>> CACHE = new ConcurrentHashMap <> ();

  @PresentForCodeCoverage
  private static final XSLT30SchemaSupport INSTANCE = new XSLT30SchemaSupport ();

  private XSLT30SchemaSupport ()
  {}

  /**
   * @return The entry schema documents of the environment: all with a file
   *         except those with role <code>secondary</code> (documents the entry
   *         schemas import themselves).
   */
  @NonNull
  private static ICommonsList <Path> _getEntrySchemaFiles (@NonNull final XSLT30Environment aEnv)
  {
    final ICommonsList <Path> ret = new CommonsArrayList <> ();
    for (final XSLT30Environment.Schema aSchema : aEnv.schemas ())
      if (!aSchema.file ().isEmpty () && !"secondary".equals (aSchema.role ()))
        ret.add (XSLT30ResourceResolver.getAbsolutePath (aEnv.baseDir (), aSchema.file ()));
    return ret;
  }

  /**
   * @param aEnv
   *        The environment, may be <code>null</code>.
   * @param aSuiteDir
   *        The root directory of the suite.
   * @return The schema documents offered to xsl:import-schema: the entry
   *         schemas of the environment, and the suite's copy of the schema of
   *         the XML representation of JSON (which fn:json-to-xml with
   *         validate=true refers to by namespace only).
   */
  @NonNull
  public static ICommonsList <XsltCompileOptions.HostSchema> getHostSchemas (@Nullable final XSLT30Environment aEnv,
                                                                            @NonNull final Path aSuiteDir)
  {
    final ICommonsList <XsltCompileOptions.HostSchema> ret = new CommonsArrayList <> ();
    if (aEnv != null)
      for (final XSLT30Environment.Schema aSchema : aEnv.schemas ())
        if (!aSchema.file ().isEmpty () && !"secondary".equals (aSchema.role ()))
          ret.add (new XsltCompileOptions.HostSchema (null,
                                                      XSLT30ResourceResolver.getFileUri (XSLT30ResourceResolver.getAbsolutePath (aEnv.baseDir (),
                                                                                                                                 aSchema.file ()))));
    final Path aJson = aSuiteDir.resolve ("tests/fn/json-to-xml/schema-for-json.xsd").toAbsolutePath ().normalize ();
    if (Files.isRegularFile (aJson))
      ret.add (new XsltCompileOptions.HostSchema (XdmConstants.NS_FN, XSLT30ResourceResolver.getFileUri (aJson)));
    return ret;
  }

  @NonNull
  private static Optional <IXdmSchemaSet> _compile (@NonNull final List <Path> aFiles)
  {
    final ICommonsList <XdmSchemaSource> aSources = new CommonsArrayList <> ();
    for (final Path aFile : aFiles)
      aSources.add (new XdmSchemaSource (XSLT30ResourceResolver.getFileUri (aFile), null));
    try
    {
      return Optional.of (XdmSchemaSupport.compile (aSources));
    }
    catch (final XdmSchemaException ex)
    {
      return Optional.empty ();
    }
  }

  @Nullable
  private static IXdmSchemaSet _getSchemaSet (@NonNull final List <Path> aFiles)
  {
    return CACHE.computeIfAbsent (List.copyOf (aFiles), XSLT30SchemaSupport::_compile).orElse (null);
  }

  private static boolean _declares (@Nullable final IXdmSchemaSet aSet, @NonNull final IXdmNode aRoot)
  {
    return aSet != null && aSet.getElementDeclaration (aRoot.getName ()) != null;
  }

  /**
   * The schema to validate a source against: the stylesheet's own schema if it
   * declares the document element (then annotations and the types the
   * stylesheet names are the same components), else the environment's.
   */
  @Nullable
  private static IXdmSchemaSet _chooseSchema (@NonNull final XsltExecutable aExecutable,
                                              @Nullable final XSLT30Environment aEnv,
                                              @NonNull final IXdmNode aRoot)
  {
    final IXdmSchemaSet aOwn = aExecutable.getSchemaSet ();
    if (_declares (aOwn, aRoot))
      return aOwn;
    if (aEnv != null)
    {
      final ICommonsList <Path> aFiles = _getEntrySchemaFiles (aEnv);
      if (aFiles.isNotEmpty ())
      {
        final IXdmSchemaSet aHost = _getSchemaSet (aFiles);
        if (aHost != null && (aFiles.size () == 1 || _declares (aHost, aRoot)))
          return aHost;
        // Each document on its own (their relative imports resolve then)
        for (final Path aFile : aFiles)
        {
          final IXdmSchemaSet aOne = _getSchemaSet (new CommonsArrayList <> (aFile));
          if (_declares (aOne, aRoot))
            return aOne;
        }
      }
    }
    return aOwn;
  }

  /**
   * @param aEnv
   *        The environment, may be <code>null</code>.
   * @param aSource
   *        The source.
   * @return The validation mode the catalog asks for the source (for the
   *         principal source also implied by a <code>source-reference</code>
   *         schema), or <code>null</code> for none.
   */
  @Nullable
  public static String getValidationMode (@Nullable final XSLT30Environment aEnv,
                                          final XSLT30Environment.@NonNull Source aSource)
  {
    final String sMode = aSource.validation ().strip ();
    if ("strict".equals (sMode) || "lax".equals (sMode))
      return sMode;
    if (sMode.isEmpty () && aEnv != null)
      for (final XSLT30Environment.Schema aSchema : aEnv.schemas ())
        if (!aSchema.file ().isEmpty () && "source-reference".equals (aSchema.role ()))
          return "lax";
    return null;
  }

  /**
   * Validate a source document in place (it must be a fresh tree).
   *
   * @param aDoc
   *        The document.
   * @param sMode
   *        <code>strict</code> or <code>lax</code>.
   * @param aExecutable
   *        The compiled stylesheet.
   * @param aEnv
   *        The environment, may be <code>null</code>.
   * @throws XPathException
   *         XTTE1510, XTTE1512, XTTE1515 if the source is not valid
   */
  public static void validateSource (@NonNull final IXdmNode aDoc,
                                     @NonNull final String sMode,
                                     @NonNull final XsltExecutable aExecutable,
                                     @Nullable final XSLT30Environment aEnv)
  {
    IXdmNode aRoot = null;
    for (final IXdmNode aChild : aDoc.getAllChildren ())
      if (aChild.isElement ())
        aRoot = aChild;
    if (aRoot == null)
      return;
    final IXdmSchemaSet aSchema = _chooseSchema (aExecutable, aEnv, aRoot);
    if (aSchema == null)
      return;
    final boolean bStrict = "strict".equals (sMode);
    if (!_declares (aSchema, aRoot) && aRoot.getAttribute (XdmConstants.NS_XSI, "type") == null)
    {
      if (bStrict)
        throw new XPathException ("XTTE1512",
                                  "The source document element " +
                                              aRoot.getName ().getEQName () +
                                              " has no top-level declaration in the schema");
      return;
    }
    try
    {
      aSchema.validateElement (aRoot, null, true);
    }
    catch (final XdmValidationException ex)
    {
      throw new XPathException (bStrict ? "XTTE1510" : "XTTE1515", "The source document is not valid: " + ex.getMessage ());
    }
    if (aExecutable.isStripInputTypeAnnotations ())
      ValidationRequest.stripAnnotations (aDoc);
  }
}
