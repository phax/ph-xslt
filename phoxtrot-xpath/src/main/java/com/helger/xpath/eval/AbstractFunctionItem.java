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
package com.helger.xpath.eval;

import com.helger.xpath.value.IXdmFunction;

/**
 * Base class of the function items the evaluator creates.
 *
 * @author Philip Helger
 */
public abstract class AbstractFunctionItem implements IXdmFunction
{
  /**
   * @return <code>true</code> if the result of {@link #call} is known to match
   *         the declared return type, so the caller may skip the conversion
   *         (built-in functions are trusted to honour their signature).
   */
  boolean isResultTrusted ()
  {
    return false;
  }
}
