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

import java.util.Properties;

import javax.xml.transform.Templates;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.model.CompiledStylesheet;
import com.helger.phoxtrot.xslt.model.OutputDefinition;

/**
 * A compiled stylesheet for JAXP. It is immutable and may be used by any
 * number of threads at the same time; each thread creates its own
 * {@link PhoxtrotTransformer} with {@link #newTransformer()}. The
 * transformers start with the settings the factory had when the templates
 * were created (URI resolver, access to external resources, message
 * listener, step limit).
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class PhoxtrotTemplates implements Templates
{
  private final XsltExecutable m_aExecutable;
  private final JaxpSettings m_aSettings;
  // Never handed out, only copies
  private final Properties m_aDeclaredOutputProperties;
  private final Properties m_aDefaultOutputProperties;

  /**
   * @param aExecutable
   *        The compiled stylesheet.
   * @param aSettings
   *        The factory settings for the transformers.
   */
  PhoxtrotTemplates (@NonNull final XsltExecutable aExecutable, @NonNull final JaxpSettings aSettings)
  {
    ValueEnforcer.notNull (aExecutable, "Executable");
    ValueEnforcer.notNull (aSettings, "Settings");
    m_aExecutable = aExecutable;
    m_aSettings = aSettings;
    final OutputDefinition aOutputDef = aExecutable instanceof final CompiledStylesheet aSheet ? aSheet.getOutput () : null;
    m_aDeclaredOutputProperties = JaxpOutputProperties.fromDefinition (aOutputDef);
    m_aDefaultOutputProperties = JaxpOutputProperties.createDefaults (aOutputDef == null ? null : aOutputDef.getMethod ());
  }

  /**
   * @return The compiled stylesheet, to call phoxtrot directly (e.g. for an
   *         initial template or function, which JAXP cannot express). Never
   *         <code>null</code>.
   */
  @NonNull
  public XsltExecutable getExecutable ()
  {
    return m_aExecutable;
  }

  @NonNull
  JaxpSettings getSettings ()
  {
    return m_aSettings;
  }

  /**
   * @return A new transformer for one thread. Never <code>null</code>.
   */
  @NonNull
  public PhoxtrotTransformer newTransformer ()
  {
    return new PhoxtrotTransformer (this);
  }

  /**
   * @return A copy of the output properties of the stylesheet: the attributes
   *         of its unnamed <code>xsl:output</code>, with the engine defaults
   *         for its method as defaults (see
   *         {@link Properties#getProperty(String)}).
   */
  @NonNull
  @ReturnsMutableCopy
  public Properties getOutputProperties ()
  {
    final Properties aDefaults = new Properties ();
    aDefaults.putAll (m_aDefaultOutputProperties);
    final Properties ret = new Properties (aDefaults);
    ret.putAll (m_aDeclaredOutputProperties);
    return ret;
  }
}
