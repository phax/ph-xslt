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
package com.helger.phoxtrot.xslt.jaxp;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xslt.XsltException;

/**
 * Maps phoxtrot errors to JAXP exceptions and reports them to an
 * {@link ErrorListener}.
 *
 * @author Philip Helger
 */
@Immutable
final class JaxpErrors
{
  private static final Logger LOGGER = LoggerFactory.getLogger (JaxpErrors.class);

  @PresentForCodeCoverage
  private static final JaxpErrors INSTANCE = new JaxpErrors ();

  /**
   * A stylesheet location: module URI, 1-based line and column, -1 where
   * unknown (as {@link SourceLocator} wants it).
   */
  record Location (@Nullable String systemId, int line, int column) implements SourceLocator
  {
    @Nullable
    public String getPublicId ()
    {
      return null;
    }

    @Nullable
    public String getSystemId ()
    {
      return systemId;
    }

    public int getLineNumber ()
    {
      return line;
    }

    public int getColumnNumber ()
    {
      return column;
    }

    /**
     * @return A location, or <code>null</code> if nothing is known.
     */
    @Nullable
    static Location of (@Nullable final String sSystemId, final int nLine, final int nColumn)
    {
      if (sSystemId == null && nLine <= 0)
        return null;
      return new Location (sSystemId, nLine > 0 ? nLine : -1, nColumn > 0 ? nColumn : -1);
    }
  }

  /** The default error listener: warnings and errors are logged, fatal errors thrown. */
  static final class LoggingErrorListener implements ErrorListener
  {
    public void warning (@NonNull final TransformerException ex)
    {
      LOGGER.warn (ex.getMessageAndLocation ());
    }

    public void error (@NonNull final TransformerException ex)
    {
      LOGGER.error (ex.getMessageAndLocation ());
    }

    public void fatalError (@NonNull final TransformerException ex) throws TransformerException
    {
      throw ex;
    }
  }

  private JaxpErrors ()
  {}

  /**
   * @return The location of a located {@link XsltException}, otherwise
   *         <code>null</code>.
   */
  @Nullable
  static Location getLocation (@NonNull final XPathException ex)
  {
    if (ex instanceof final XsltException aXEx && aXEx.isLocated ())
      return Location.of (aXEx.getModuleUri (), aXEx.getLine (), aXEx.getColumn ());
    return null;
  }

  /**
   * @return A transformer exception with the message of the error (its code,
   *         a colon and the description), its stylesheet location and the
   *         error as cause.
   */
  @NonNull
  static TransformerException toTransformerException (@NonNull final XPathException ex)
  {
    return new TransformerException (ex.getMessage (), getLocation (ex), ex);
  }

  /**
   * @return A configuration exception with the message of the error (its
   *         code, a colon and the description), its stylesheet location and
   *         the error as cause.
   */
  @NonNull
  static TransformerConfigurationException toConfigurationException (@NonNull final XPathException ex)
  {
    return new TransformerConfigurationException (ex.getMessage (), getLocation (ex), ex);
  }

  /**
   * Report a fatal error to the listener. If the listener throws, its
   * exception is returned instead of the error, so that the caller throws
   * what the listener chose.
   *
   * @param aListener
   *        The listener.
   * @param ex
   *        The error.
   * @return The exception to throw. Never <code>null</code>.
   */
  @NonNull
  static TransformerException reportFatal (@NonNull final ErrorListener aListener, @NonNull final TransformerException ex)
  {
    try
    {
      aListener.fatalError (ex);
      return ex;
    }
    catch (final TransformerException ex2)
    {
      return ex2;
    }
  }

  /**
   * Report a fatal error of the compilation: like
   * {@link #reportFatal(ErrorListener, TransformerException)}, but what the
   * listener throws is wrapped into a configuration exception if it is none.
   */
  @NonNull
  static TransformerConfigurationException reportFatalConfiguration (@NonNull final ErrorListener aListener,
                                                                     @NonNull final TransformerConfigurationException ex)
  {
    final TransformerException ret = reportFatal (aListener, ex);
    if (ret instanceof final TransformerConfigurationException aTCE)
      return aTCE;
    return new TransformerConfigurationException (ret.getMessage (), ret.getLocator (), ret);
  }
}
