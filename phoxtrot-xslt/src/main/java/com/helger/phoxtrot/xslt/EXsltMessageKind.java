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
package com.helger.phoxtrot.xslt;

/**
 * What produced an {@link XsltMessage}.
 *
 * @author Philip Helger
 */
public enum EXsltMessageKind
{
  /** An executed <code>xsl:message</code> instruction (XSLT 3.0 section 23.1). */
  MESSAGE,
  /** A failed <code>xsl:assert</code> instruction (XSLT 3.0 section 23.2). */
  ASSERTION,
  /**
   * A warning of the processor, e.g. for <code>warning-on-no-match</code> or
   * <code>warning-on-multiple-match</code> of <code>xsl:mode</code>.
   */
  WARNING
}
