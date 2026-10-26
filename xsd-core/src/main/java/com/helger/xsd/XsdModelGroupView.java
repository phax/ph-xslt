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
 * A sequence, choice or all group (go-xslt <code>ModelGroupView</code>).
 *
 * @param compositor
 *        The compositor.
 * @param particles
 *        The particles, in order.
 * @author Philip Helger
 */
public record XsdModelGroupView (@NonNull EXsdCompositor compositor, @NonNull ICommonsList <XsdParticleView> particles) implements
                                IXsdTermView
{}
