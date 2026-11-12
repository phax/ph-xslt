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
/**
 * XPath 3.1: compile an expression with {@link com.helger.phoxtrot.xpath.XPathCompiler} against a
 * {@link com.helger.phoxtrot.xpath.context.StaticContext} and evaluate the
 * {@link com.helger.phoxtrot.xpath.CompiledXPath} with a
 * {@link com.helger.phoxtrot.xpath.context.DynamicContext}, if needed on a thread of
 * {@link com.helger.phoxtrot.xpath.LargeStackRunner}.
 * <p>
 * Public API, kept source compatible within 0.x (a change is listed in the release notes):
 * <ul>
 * <li>this package: <code>XPathCompiler</code>, <code>CompiledXPath</code>, <code>XPathException</code>,
 * <code>EThreading</code>, <code>LargeStackRunner</code>, <code>XPathStreamabilityAnalyzer</code>,
 * <code>XPathStreamabilityResult</code>, <code>XPathStreamabilityReason</code>, <code>XPathStreamabilityBinding</code>,
 * <code>EStreamabilityVerdict</code>, <code>EStreamabilityConstruct</code>, <code>EStreamContextMode</code></li>
 * <li><code>context</code>: <code>StaticContext</code>, <code>DynamicContext</code>,
 * <code>IResourceResolver</code>, <code>ResourceUri</code></li>
 * <li><code>fn</code>: <code>FunctionLibrary</code>, <code>IXPathFunction</code></li>
 * <li><code>ast</code>: <code>SeqType</code>, <code>ItemType</code>, <code>EOccurrence</code></li>
 * <li><code>stream</code>: <code>EPosture</code>, <code>ESweep</code>, <code>PostureSweep</code>,
 * <code>EStreamFunctionCategory</code></li>
 * <li><code>value</code>: the value types (<code>XdmSequence</code>, <code>XdmAtomic</code>, <code>XdmMap</code>,
 * <code>XdmArray</code>, ...)</li>
 * <li><code>eval</code>: <code>XPathOperators</code></li>
 * </ul>
 * Everything else, in particular the expression nodes of <code>ast</code>, <code>eval</code>, <code>parser</code>,
 * <code>ops</code>, <code>pattern</code> and the rest of <code>stream</code>, is internal and may change in any release.
 */
package com.helger.phoxtrot.xpath;
