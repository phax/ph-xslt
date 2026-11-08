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

import com.helger.collection.commons.ICommonsList;

/**
 * A term with occurrence bounds (go-xslt <code>ParticleView</code>).
 *
 * @param documentation
 *        The annotation of the particle's own xs:element (for a reference the
 *        reference's); needs documentation kept at compile time.
 * @param minOccurs
 *        The minimum occurrence.
 * @param maxOccurs
 *        The maximum occurrence, {@link XsdConstants#UNBOUNDED} for
 *        unbounded.
 * @param term
 *        The term.
 * @author Philip Helger
 */
public record XsdParticleView (@NonNull ICommonsList <XsdDocumentation> documentation,
                               int minOccurs,
                               int maxOccurs,
                               @NonNull IXsdTermView term)
{}
