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

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;

/**
 * Options of a schema compile.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XsdCompileOptions
{
  private boolean m_bKeepDocumentation;
  private XsdFileAccess m_aFileAccess = XsdFileAccess.LOCAL_FILES;

  /**
   * @return <code>true</code> if the <code>xs:documentation</code> of
   *         components and the source text of pattern facets are kept for the
   *         component model view (go-xslt <code>CompileWithDocs</code>). The
   *         verdicts are the same either way. Default <code>false</code>.
   */
  public boolean isKeepDocumentation ()
  {
    return m_bKeepDocumentation;
  }

  /**
   * @param bKeepDocumentation
   *        <code>true</code> to keep documentation.
   * @return this for chaining
   */
  @NonNull
  public XsdCompileOptions setKeepDocumentation (final boolean bKeepDocumentation)
  {
    m_bKeepDocumentation = bKeepDocumentation;
    return this;
  }

  /** @return The file access rule. Default {@link XsdFileAccess#LOCAL_FILES}. */
  @NonNull
  public XsdFileAccess getFileAccess ()
  {
    return m_aFileAccess;
  }

  /**
   * @param aFileAccess
   *        The file access rule for every document the validator reads on its
   *        own. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public XsdCompileOptions setFileAccess (@NonNull final XsdFileAccess aFileAccess)
  {
    ValueEnforcer.notNull (aFileAccess, "FileAccess");
    m_aFileAccess = aFileAccess;
    return this;
  }
}
