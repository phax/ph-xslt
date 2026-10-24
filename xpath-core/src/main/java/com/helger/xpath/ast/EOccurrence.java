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
package com.helger.xpath.ast;

/**
 * Occurrence indicator of a sequence type.
 *
 * @author Philip Helger
 */
public enum EOccurrence
{
  /** Exactly one (no indicator). */
  ONE,
  /** <code>?</code> */
  ZERO_OR_ONE,
  /** <code>*</code> */
  ZERO_OR_MORE,
  /** <code>+</code> */
  ONE_OR_MORE;

  public boolean allowsEmpty ()
  {
    return this == ZERO_OR_ONE || this == ZERO_OR_MORE;
  }

  public boolean allowsMany ()
  {
    return this == ZERO_OR_MORE || this == ONE_OR_MORE;
  }
}
