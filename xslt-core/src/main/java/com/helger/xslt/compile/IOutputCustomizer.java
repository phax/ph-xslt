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
package com.helger.xslt.compile;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.serialize.SerializationParams;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.OutputDefinition;

/**
 * Adjusts the serialization parameters created from an output definition
 * (e.g. character maps from use-character-maps).
 *
 * @author Philip Helger
 */
@FunctionalInterface
public interface IOutputCustomizer
{
  /**
   * @param aStylesheet
   *        The stylesheet.
   * @param aDefinition
   *        The output definition the parameters come from.
   * @param aParams
   *        The parameters to modify.
   */
  void customize (@NonNull CompiledStylesheet aStylesheet,
                  @NonNull OutputDefinition aDefinition,
                  @NonNull SerializationParams aParams);
}
