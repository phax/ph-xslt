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
package com.helger.xpath.conformance;

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;

/**
 * The FOTS <code>catalog.xml</code>.
 *
 * @param rootDir
 *        The suite directory.
 * @param environments
 *        Catalog level named environments.
 * @param testSets
 *        The test-set references in catalog order.
 * @author Philip Helger
 */
public record QT3Catalog (@NonNull Path rootDir,
                          @NonNull ICommonsMap <String, QT3Environment> environments,
                          @NonNull ICommonsList <TestSetRef> testSets)
{
  /**
   * A <code>&lt;test-set name="..." file="..."/&gt;</code>.
   *
   * @param name
   *        Test set name.
   * @param file
   *        File relative to the catalog.
   */
  public record TestSetRef (@NonNull String name, @NonNull String file)
  {}
}
