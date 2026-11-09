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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.phoxtrot.xdm.IXdmItem;

/**
 * Follows the execution of one transformation (see
 * {@link XsltTransformRequest#setTraceListener(IXsltTraceListener)}): the start
 * and end of the run, every invoked template rule, named template and
 * stylesheet function, and on request every executed instruction. All methods
 * do nothing by default, so an implementation overrides only what it needs.
 * <p>
 * The methods are called synchronously on the thread that runs the
 * transformation: normally the large-stack worker thread of
 * {@link com.helger.phoxtrot.xslt.runtime.LargeStackRunner}, not the thread
 * that called <code>transform</code> (see {@link EXsltThreading}). A listener
 * is used by one transformation at a time unless the implementation is
 * thread-safe. A runtime exception thrown by a method ends the transformation
 * and reaches the caller of <code>transform</code>; an exception thrown by an
 * enter method is not followed by the matching leave call.
 * <p>
 * Every enter call is followed by exactly one leave call for the same
 * template, function or instruction, also when its body ends with a dynamic
 * error (an error caught by an enclosing <code>xsl:try</code>, a terminating
 * <code>xsl:message</code>, a failed <code>xsl:assert</code>), so the calls
 * nest like the invocations and can be paired with a stack. The calls in
 * detail:
 * <ul>
 * <li>{@link #onTransformStart()} and {@link #onTransformEnd()} enclose a
 * complete run, including the evaluation of global variables and the
 * serialisation of the principal result. A transformation started by
 * <code>fn:transform</code> uses the listener of its caller, so its start and
 * end calls are nested inside the template or function of the caller that
 * evaluated <code>fn:transform</code>.</li>
 * <li>Template rules are reported for <code>xsl:apply-templates</code>,
 * <code>xsl:next-match</code>, <code>xsl:apply-imports</code> and the initial
 * mode, named templates for <code>xsl:call-template</code> and the initial
 * template. The built-in template rules (on-no-match) are not reported; the
 * template rules they invoke for children are. The enter call comes after
 * the rule was selected and before the parameters are evaluated.</li>
 * <li>Stylesheet functions are reported for every evaluation of their body,
 * including calls through a function item and the initial function. A call
 * answered from the cache of a memoised function (<code>cache="yes"</code>,
 * <code>new-each-time="no"</code>) runs no body and is not reported.</li>
 * <li>Instructions are only reported when {@link #isTraceInstructions()}
 * returns <code>true</code> at the start of the run: every instruction of
 * every sequence constructor, including <code>xsl:variable</code> and
 * <code>xsl:param</code> in a sequence constructor and literal result elements,
 * but not text nodes and not the attribute value templates of an element. A
 * local variable whose value is evaluated lazily may do its work after its
 * leave call. Instruction tracing calls the listener for every executed
 * instruction and can slow a transformation down considerably.</li>
 * <li>A streamed run (a principal source read as a stream) reports the
 * templates applied to the streamed nodes like any other. The context item is
 * then a node of a streamed document
 * ({@link com.helger.phoxtrot.xdm.XdmDocumentNode#isStreamed()}): only the
 * current record and its ancestors are available, and its subtree may be
 * dropped after the leave call.</li>
 * </ul>
 * Not reported as templates or functions: pattern matching, XPath
 * evaluation, the bodies of keys, accumulators and attribute sets (their
 * instructions are reported with instruction tracing).
 *
 * @author Philip Helger
 */
public interface IXsltTraceListener
{
  /**
   * Called once when a transformation starts, before any other call.
   */
  default void onTransformStart ()
  {}

  /**
   * Called once when a transformation ends, after all other calls, also when
   * it ends with an error.
   */
  default void onTransformEnd ()
  {}

  /**
   * Called when a template rule is invoked for an item.
   *
   * @param aTemplate
   *        The template rule. Never <code>null</code>.
   * @param aContextItem
   *        The item the rule was applied to (its context item). Never
   *        <code>null</code>.
   */
  default void onTemplateRuleEnter (@NonNull final XsltTraceTemplate aTemplate, @NonNull final IXdmItem aContextItem)
  {}

  /**
   * Called when a template rule ends, normally or with an error. Paired with
   * the preceding {@link #onTemplateRuleEnter(XsltTraceTemplate, IXdmItem)}
   * with the same arguments.
   *
   * @param aTemplate
   *        The template rule. Never <code>null</code>.
   * @param aContextItem
   *        The item the rule was applied to. Never <code>null</code>.
   */
  default void onTemplateRuleLeave (@NonNull final XsltTraceTemplate aTemplate, @NonNull final IXdmItem aContextItem)
  {}

  /**
   * Called when a named template is invoked by
   * <code>xsl:call-template</code> or as initial template.
   *
   * @param aTemplate
   *        The template. Never <code>null</code>.
   * @param aContextItem
   *        The context item of the caller that the template keeps, or
   *        <code>null</code> if it is absent.
   */
  default void onNamedTemplateEnter (@NonNull final XsltTraceTemplate aTemplate, @Nullable final IXdmItem aContextItem)
  {}

  /**
   * Called when a named template ends, normally or with an error. Paired with
   * the preceding {@link #onNamedTemplateEnter(XsltTraceTemplate, IXdmItem)}
   * with the same arguments.
   *
   * @param aTemplate
   *        The template. Never <code>null</code>.
   * @param aContextItem
   *        The context item of the template, or <code>null</code> if it is
   *        absent.
   */
  default void onNamedTemplateLeave (@NonNull final XsltTraceTemplate aTemplate, @Nullable final IXdmItem aContextItem)
  {}

  /**
   * Called when the body of a stylesheet function starts.
   *
   * @param aFunction
   *        The function. Never <code>null</code>.
   */
  default void onFunctionEnter (@NonNull final XsltTraceFunction aFunction)
  {}

  /**
   * Called when the body of a stylesheet function ends, normally or with an
   * error, before its result is converted to the declared type. Paired with
   * the preceding {@link #onFunctionEnter(XsltTraceFunction)} with the same
   * argument.
   *
   * @param aFunction
   *        The function. Never <code>null</code>.
   */
  default void onFunctionLeave (@NonNull final XsltTraceFunction aFunction)
  {}

  /**
   * Asked once at the start of each run.
   *
   * @return <code>true</code> to receive
   *         {@link #onInstructionEnter(XsltTraceInstruction)} and
   *         {@link #onInstructionLeave(XsltTraceInstruction)} for every
   *         executed instruction, <code>false</code> (the default) for
   *         templates and functions only.
   */
  default boolean isTraceInstructions ()
  {
    return false;
  }

  /**
   * Called before an instruction runs, if {@link #isTraceInstructions()}
   * returned <code>true</code>.
   *
   * @param aInstruction
   *        The instruction. Never <code>null</code>.
   */
  default void onInstructionEnter (@NonNull final XsltTraceInstruction aInstruction)
  {}

  /**
   * Called after an instruction ran, normally or with an error. Paired with
   * the preceding {@link #onInstructionEnter(XsltTraceInstruction)} with the
   * same argument.
   *
   * @param aInstruction
   *        The instruction. Never <code>null</code>.
   */
  default void onInstructionLeave (@NonNull final XsltTraceInstruction aInstruction)
  {}
}
