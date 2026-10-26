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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.EXsdVersion;

/**
 * Shared helpers of the spec tests ported from go-xslt's
 * <code>internal/xsd/*_test.go</code>. go-xslt fails a test on
 * <code>ErrUnsupported</code>; while the port is incomplete (phases V3 to V5)
 * an unsupported outcome is tolerated and counted as undecided, but a decided
 * verdict must be the expected one.
 *
 * @author Philip Helger
 */
final class XsdTestSupport
{
  /** Run a case under XSD 1.0 and 1.1. */
  static final int VER_BOTH = 0;
  /** Run a case under XSD 1.0 only. */
  static final int VER_10 = 1;
  /** Run a case under XSD 1.1 only. */
  static final int VER_11 = 2;
  /** Added to a selector: a case go-xslt itself gets wrong (skipped). */
  static final int SKIP_BUG = 10;

  private static final Logger LOGGER = LoggerFactory.getLogger (XsdTestSupport.class);

  @PresentForCodeCoverage
  private static final XsdTestSupport INSTANCE = new XsdTestSupport ();

  /** A schema verdict. */
  enum EVerdict
  {
    VALID,
    INVALID,
    UNSUPPORTED
  }

  /**
   * An instance check of a case.
   *
   * @param instance
   *        The instance document.
   * @param valid
   *        The expected verdict.
   */
  record DocCheck (@NonNull String instance, boolean valid)
  {}

  /**
   * A schema case.
   *
   * @param name
   *        The case name.
   * @param ver
   *        The version selector, possibly plus {@link #SKIP_BUG}.
   * @param schema
   *        The schema document (or body, see {@link #reprSchema(String)}).
   * @param valid
   *        The expected schema verdict.
   * @param docs
   *        Instance checks against the compiled schema.
   */
  record Case (@NonNull String name, int ver, @NonNull String schema, boolean valid, @NonNull DocCheck... docs)
  {}

  /**
   * The result of a compile.
   *
   * @param verdict
   *        The verdict.
   * @param schema
   *        The schema if valid.
   * @param message
   *        The message of an invalid or unsupported verdict.
   */
  record Compiled (@NonNull EVerdict verdict, @Nullable XsdSchema schema, @Nullable String message)
  {}

  /** Counts of decided and undecided outcomes and the wrong verdicts. */
  static final class Tally
  {
    int m_nDecided;
    int m_nUndecided;
    final ICommonsList <String> m_aWrong = new CommonsArrayList <> ();

    void log (@NonNull final String sWhat)
    {
      LOGGER.info (sWhat + ": " + m_nDecided + " decided, " + m_nUndecided + " not decidable yet (stubs of later phases)");
    }
  }

  private XsdTestSupport ()
  {}

  @NonNull
  static ICommonsList <EXsdVersion> versionsOf (final int nSel)
  {
    switch (nSel % SKIP_BUG)
    {
      case VER_10:
        return new CommonsArrayList <> (EXsdVersion.V10);
      case VER_11:
        return new CommonsArrayList <> (EXsdVersion.V11);
      default:
        return new CommonsArrayList <> (EXsdVersion.V10, EXsdVersion.V11);
    }
  }

  /** go-xslt <code>reprSchema</code>: wrap a body in xs:schema unless it already is one. */
  @NonNull
  static String reprSchema (@NonNull final String sBody)
  {
    if (sBody.strip ().startsWith ("<xs:schema"))
      return sBody;
    return "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" + sBody + "</xs:schema>";
  }

  /** @return The directory of a test resource directory. */
  @NonNull
  static Path resourceDir (@NonNull final String sName)
  {
    try
    {
      return Paths.get (XsdTestSupport.class.getResource ("/" + sName).toURI ());
    }
    catch (final URISyntaxException ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  @NonNull
  static Compiled compile (@NonNull final String sSchema, @Nullable final Path aBaseDir, @NonNull final EXsdVersion eVersion)
  {
    try
    {
      final XsdSchema aSchema = XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (sSchema, aBaseDir)),
                                                   eVersion,
                                                   null);
      return new Compiled (EVerdict.VALID, aSchema, null);
    }
    catch (final XsdInvalidException ex)
    {
      return new Compiled (EVerdict.INVALID, null, ex.getMessage ());
    }
    catch (final XsdUnsupportedException ex)
    {
      return new Compiled (EVerdict.UNSUPPORTED, null, ex.getMessage ());
    }
  }

  @NonNull
  static EVerdict validate (@NonNull final XsdSchema aSchema, @NonNull final String sInstance)
  {
    try
    {
      return aSchema.validateText (sInstance).valid () ? EVerdict.VALID : EVerdict.INVALID;
    }
    catch (final XsdInvalidException ex)
    {
      return EVerdict.INVALID;
    }
    catch (final XsdUnsupportedException ex)
    {
      return EVerdict.UNSUPPORTED;
    }
  }

  /**
   * Run cases: compile each schema under each selected version, compare the
   * decided verdicts, and run the instance checks of valid schemas.
   */
  @NonNull
  static Tally runCases (@NonNull final Case [] aCases, @Nullable final Path aBaseDir, final boolean bWrapBody)
  {
    final Tally ret = new Tally ();
    for (final Case aCase : aCases)
    {
      if (aCase.ver () >= SKIP_BUG)
        continue;
      for (final EXsdVersion eVersion : versionsOf (aCase.ver ()))
      {
        final String sLabel = aCase.name () + "/" + eVersion.getID ();
        final Compiled aCompiled = compile (bWrapBody ? reprSchema (aCase.schema ()) : aCase.schema (),
                                            aBaseDir,
                                            eVersion);
        if (aCompiled.verdict () == EVerdict.UNSUPPORTED)
        {
          ret.m_nUndecided++;
          continue;
        }
        ret.m_nDecided++;
        final boolean bValid = aCompiled.verdict () == EVerdict.VALID;
        if (bValid != aCase.valid ())
        {
          ret.m_aWrong.add (sLabel + ": schema valid=" + bValid + ", want " + aCase.valid () + " (" + aCompiled.message () + ")");
          continue;
        }
        if (aCompiled.schema () != null)
          for (final DocCheck aDoc : aCase.docs ())
          {
            final EVerdict eDoc = validate (aCompiled.schema (), aDoc.instance ());
            if (eDoc == EVerdict.UNSUPPORTED)
            {
              ret.m_nUndecided++;
              continue;
            }
            ret.m_nDecided++;
            if ((eDoc == EVerdict.VALID) != aDoc.valid ())
              ret.m_aWrong.add (sLabel + ": " + aDoc.instance () + " valid=" + (eDoc == EVerdict.VALID) + ", want " + aDoc.valid ());
          }
      }
    }
    return ret;
  }
}
