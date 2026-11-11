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
package com.helger.phoxtrot.xpath.fn;

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Implementation of a built-in or extension function, registered in a
 * {@link FunctionLibrary} with its signature (as text with
 * {@link FunctionLibrary#register(String, String, String, IXPathFunction)} or
 * as types with
 * {@link FunctionLibrary#register(com.helger.phoxtrot.xdm.XdmQName, List, com.helger.phoxtrot.xpath.ast.SeqType, IXPathFunction)}).
 * Before the call the evaluator applies the function conversion rules of XPath
 * 3.1 section 3.1.5.2 to every argument against its declared parameter type,
 * so an implementation can rely on the declared types:
 * <ul>
 * <li>Expected atomic type (e.g. <code>xs:string?</code>,
 * <code>xs:double*</code>): the argument is atomised; each
 * <code>xs:untypedAtomic</code> value (e.g. the value of a node without schema
 * type) is cast to the expected type (FORG0001 if that fails); numeric
 * promotion (<code>xs:decimal</code> to <code>xs:float</code> or
 * <code>xs:double</code>, <code>xs:float</code> to <code>xs:double</code>) and
 * URI promotion (<code>xs:anyURI</code> to <code>xs:string</code>) are
 * applied.</li>
 * <li>Expected function type: a function item is coerced (its arguments and
 * result are checked when it is called).</li>
 * <li>Any other expected type (<code>node()</code>, <code>item()*</code>,
 * maps, arrays): the value is passed as it is.</li>
 * <li>Then the value must match the type, including its cardinality
 * (<code>?</code>, <code>*</code>, <code>+</code> or exactly one), otherwise
 * the call fails with XPTY0004 before the implementation runs.</li>
 * <li>In XPath 1.0 compatibility mode (see
 * {@link com.helger.phoxtrot.xpath.context.StaticContext#setXPath10Compatibility(boolean)})
 * the XPath 1.0 rules come first: for an expected single atomic value only the
 * first item is used, and it is converted with <code>fn:string</code> for
 * <code>xs:string</code> and with <code>fn:number</code> for
 * <code>xs:double</code>.</li>
 * </ul>
 * The returned value is not checked against the declared return type: the
 * implementation must return a matching value. An error is raised by throwing
 * a {@link com.helger.phoxtrot.xpath.XPathException} with its code. An
 * implementation that reads the focus (context item, position or size) must be
 * registered as focus dependent.
 *
 * @author Philip Helger
 */
@FunctionalInterface
public interface IXPathFunction
{
  /**
   * @param aCtx
   *        Dynamic context of the call (focus = the caller's focus).
   * @param aArgs
   *        The converted arguments, one per parameter.
   * @return The result; it is not checked against the declared return type.
   */
  @NonNull
  XdmSequence call (@NonNull DynamicContext aCtx, @NonNull List <XdmSequence> aArgs);
}
