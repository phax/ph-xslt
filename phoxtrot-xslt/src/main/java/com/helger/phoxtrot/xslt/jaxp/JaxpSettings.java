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

import javax.xml.transform.URIResolver;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.phoxtrot.xslt.EXsltThreading;
import com.helger.phoxtrot.xslt.IXsltMessageListener;
import com.helger.phoxtrot.xslt.IXsltTraceListener;

/**
 * The settings of a {@link PhoxtrotTransformerFactory} at the time a
 * {@link PhoxtrotTemplates} was created, which the transformers of the
 * templates start with.
 *
 * @param uriResolver
 *        The URI resolver of the factory, the initial one of each transformer.
 * @param accessExternalStylesheet
 *        The schemes allowed for direct access by <code>document()</code>
 *        and friends.
 * @param loadExternalEntities
 *        Whether documents read at run time load external DTDs and entities.
 * @param messageListener
 *        The message listener, <code>null</code> to report messages to the
 *        error listener.
 * @param traceListener
 *        The trace listener, <code>null</code> for none.
 * @param maxSteps
 *        The step limit of a transformation, 0 for the engine default.
 * @param threading
 *        The thread the transformations run on, the initial choice of each
 *        transformer.
 * @author Philip Helger
 */
record JaxpSettings (@Nullable URIResolver uriResolver,
                     @NonNull String accessExternalStylesheet,
                     boolean loadExternalEntities,
                     @Nullable IXsltMessageListener messageListener,
                     @Nullable IXsltTraceListener traceListener,
                     long maxSteps,
                     @NonNull EXsltThreading threading)
{}
