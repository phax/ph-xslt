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

import java.time.OffsetDateTime;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.context.IResourceResolver;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.runtime.Engine;
import com.helger.phoxtrot.xslt.runtime.ExecutionBudget;

/**
 * One invocation of a compiled stylesheet (XSLT 3.0 section 2.3). Exactly one
 * way of starting is used: an initial template, an initial function, or apply
 * templates to the initial match selection in the initial mode (the default
 * when nothing else is set and a source is given).
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XsltTransformRequest
{
  /**
   * The value of {@link #setMaxSteps(long)} for "no step limit": the
   * transformation may execute any number of instructions. The nesting depth
   * limit ({@link #setMaxDepth(int)}) and the interrupt hook still apply.
   */
  public static final long UNLIMITED_STEPS = Long.MAX_VALUE;

  /** A template parameter of the initial template or initial mode. */
  public record TemplateParam (@NonNull XdmQName name, @NonNull XdmSequence value, boolean tunnel)
  {}

  private IXdmItem m_aGlobalContextItem;
  private XdmSequence m_aInitialMatchSelection;
  private XdmQName m_aInitialMode;
  private XdmQName m_aInitialTemplate;
  private XdmQName m_aInitialFunction;
  private final ICommonsList <XdmSequence> m_aFunctionArgs = new CommonsArrayList <> ();
  private final ICommonsOrderedMap <XdmQName, XdmSequence> m_aParams = new CommonsLinkedHashMap <> ();
  private final ICommonsList <TemplateParam> m_aTemplateParams = new CommonsArrayList <> ();
  private final ICommonsOrderedMap <String, String> m_aOutputOverrides = new CommonsLinkedHashMap <> ();
  private String m_sBaseOutputUri;
  private IResourceResolver m_aResolver;
  private OffsetDateTime m_aCurrentDateTime;
  private BooleanSupplier m_aInterrupt;
  private IXsltMessageListener m_aMessageListener;
  private long m_nMaxSteps;
  private int m_nMaxDepth;
  private ExecutionBudget m_aSharedBudget;
  private boolean m_bRawDelivery;
  private boolean m_bSourceStreamed;
  private String m_sStreamedSourceUri;
  private EXsltThreading m_eThreading = EXsltThreading.DEFAULT;

  /** @return The global context item (normally the source document node). */
  @Nullable
  public IXdmItem getGlobalContextItem ()
  {
    return m_aGlobalContextItem;
  }

  @NonNull
  public XsltTransformRequest setGlobalContextItem (@Nullable final IXdmItem aItem)
  {
    m_aGlobalContextItem = aItem;
    return this;
  }

  /** @return The initial match selection; defaults to the global context item. */
  @Nullable
  public XdmSequence getInitialMatchSelection ()
  {
    return m_aInitialMatchSelection;
  }

  @NonNull
  public XsltTransformRequest setInitialMatchSelection (@Nullable final XdmSequence aSeq)
  {
    m_aInitialMatchSelection = aSeq;
    return this;
  }

  /**
   * @return The initial mode, <code>null</code> for the default mode. The
   *         names <code>#unnamed</code> and <code>#default</code> (in no
   *         namespace) stand for the unnamed and the default mode.
   */
  @Nullable
  public XdmQName getInitialMode ()
  {
    return m_aInitialMode;
  }

  @NonNull
  public XsltTransformRequest setInitialMode (@Nullable final XdmQName aMode)
  {
    m_aInitialMode = aMode;
    return this;
  }

  /**
   * @return The initial named template (XSLT 3.0 section 2.3.4) or
   *         <code>null</code>. Used unless an initial function is set; the
   *         template must exist and be callable from outside the stylesheet
   *         (else XTDE0040).
   */
  @Nullable
  public XdmQName getInitialTemplate ()
  {
    return m_aInitialTemplate;
  }

  /**
   * @param aName
   *        The initial named template; <code>xsl:initial-template</code> in the
   *        XSLT namespace is the conventional default.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setInitialTemplate (@Nullable final XdmQName aName)
  {
    m_aInitialTemplate = aName;
    return this;
  }

  /**
   * @return The initial function (XSLT 3.0 section 2.3.5) or
   *         <code>null</code>. It takes precedence over an initial template
   *         and mode: the public or final function of that name with the
   *         arity of {@link #functionArgs()} is called (else XTDE0041), each
   *         argument converted to the declared parameter type (XPTY0004 if
   *         it cannot be).
   */
  @Nullable
  public XdmQName getInitialFunction ()
  {
    return m_aInitialFunction;
  }

  @NonNull
  public XsltTransformRequest setInitialFunction (@Nullable final XdmQName aName)
  {
    m_aInitialFunction = aName;
    return this;
  }

  /** @return The arguments of the initial function (modifiable). */
  @NonNull
  public ICommonsList <XdmSequence> functionArgs ()
  {
    return m_aFunctionArgs;
  }

  /** @return Stylesheet parameter values by name (modifiable). */
  @NonNull
  public ICommonsOrderedMap <XdmQName, XdmSequence> params ()
  {
    return m_aParams;
  }

  /** @return Parameters for the initial template or mode (modifiable). */
  @NonNull
  public ICommonsList <TemplateParam> templateParams ()
  {
    return m_aTemplateParams;
  }

  /**
   * Serialization parameters supplied by the calling application for the
   * principal result, which override those of the output definition the
   * principal result is serialized with (XSLT 3.0 section 26.1 lets the
   * invocation API supply them). The keys are the attribute names of
   * xsl:output (e.g. <code>indent</code>, <code>method</code>,
   * <code>encoding</code>), the values their lexical form; see
   * {@link com.helger.phoxtrot.xslt.model.OutputDefinition#getWithOverrides(java.util.Map)}
   * for the accepted names and the form of name lists. They apply only where
   * the principal result is serialized; secondary results and the raw
   * result are not affected.
   *
   * @return The overrides by attribute name (modifiable), empty by default.
   */
  @NonNull
  public ICommonsOrderedMap <String, String> outputOverrides ()
  {
    return m_aOutputOverrides;
  }

  /**
   * @return The base output URI (XSLT 3.0 sections 25.1 and 25.3): the href
   *         of xsl:result-document is resolved against it, and it is the
   *         current output URI of the principal result. <code>null</code> if
   *         none is set.
   */
  @Nullable
  public String getBaseOutputUri ()
  {
    return m_sBaseOutputUri;
  }

  @NonNull
  public XsltTransformRequest setBaseOutputUri (@Nullable final String sUri)
  {
    m_sBaseOutputUri = sUri;
    return this;
  }

  /** @return The resolver for fn:doc, document(), unparsed-text etc. */
  @Nullable
  public IResourceResolver getResolver ()
  {
    return m_aResolver;
  }

  @NonNull
  public XsltTransformRequest setResolver (@Nullable final IResourceResolver aResolver)
  {
    m_aResolver = aResolver;
    return this;
  }

  /**
   * @return The value of fn:current-dateTime() for the transformation, its
   *         offset also being the implicit timezone; <code>null</code> to use
   *         the time at which the transformation starts.
   */
  @Nullable
  public OffsetDateTime getCurrentDateTime ()
  {
    return m_aCurrentDateTime;
  }

  @NonNull
  public XsltTransformRequest setCurrentDateTime (@Nullable final OffsetDateTime aValue)
  {
    m_aCurrentDateTime = aValue;
    return this;
  }

  /**
   * @return A hook polled during evaluation (in loops and calls); when it
   *         returns <code>true</code>, the transformation ends with FOER0000.
   *         <code>null</code> for none.
   */
  @Nullable
  public BooleanSupplier getInterrupt ()
  {
    return m_aInterrupt;
  }

  @NonNull
  public XsltTransformRequest setInterrupt (@Nullable final BooleanSupplier aInterrupt)
  {
    m_aInterrupt = aInterrupt;
    return this;
  }

  /**
   * @return The listener that receives the messages and warnings of the
   *         transformation while it runs, <code>null</code> for none.
   */
  @Nullable
  public IXsltMessageListener getMessageListener ()
  {
    return m_aMessageListener;
  }

  /**
   * Set a listener for the messages (<code>xsl:message</code>, failed
   * <code>xsl:assert</code>) and processor warnings of the transformation. It
   * is called synchronously in the order the messages are produced, on the
   * thread that runs the transformation (normally the large-stack worker
   * thread, see {@link IXsltMessageListener}). The messages are still
   * collected in {@link XsltTransformResult#messages()} and, when the
   * transformation fails, in {@link XsltException#getCollectedMessages()}.
   * A transformation started by <code>fn:transform</code> reports its
   * messages to the listener of its caller, unless the
   * <code>enable-messages</code> option is false. Without a listener nothing
   * changes.
   *
   * @param aMessageListener
   *        The listener; <code>null</code> (the default) for none.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setMessageListener (@Nullable final IXsltMessageListener aMessageListener)
  {
    m_aMessageListener = aMessageListener;
    return this;
  }

  /**
   * @return The maximum number of instructions to execute as set by
   *         {@link #setMaxSteps(long)}: 0 (the initial value) or a negative
   *         value for the default of {@link Engine#DEFAULT_MAX_STEPS}
   *         (20,000,000), {@link #UNLIMITED_STEPS} for no limit, any other
   *         value for that limit.
   */
  public long getMaxSteps ()
  {
    return m_nMaxSteps;
  }

  /**
   * Set the maximum number of instructions the transformation may execute;
   * the instruction that exceeds it fails the transformation with XPDY0130.
   * One step is counted for each instruction of a sequence constructor that
   * is executed (so a loop body of three instructions counts three per
   * iteration) and for each accumulator rule applied; the XPath evaluation
   * inside an instruction is not counted.
   * <ul>
   * <li>0 (the initial value) or a negative value: the default of
   * {@link Engine#DEFAULT_MAX_STEPS} (20,000,000) instructions.</li>
   * <li>{@link #UNLIMITED_STEPS} ({@link Long#MAX_VALUE}): no step limit. The
   * nesting depth limit and the interrupt hook still apply, so a run that
   * never ends can only be stopped by {@link #setInterrupt(BooleanSupplier)}.</li>
   * <li>Any other positive value: that number of instructions.</li>
   * </ul>
   * The limit is ignored when a shared budget is set
   * ({@link #setSharedBudget(ExecutionBudget)}), as for a transformation run by
   * <code>fn:transform</code>, which spends from the budget of its caller;
   * <code>xsl:evaluate</code> also counts against the budget of the
   * transformation it runs in.
   *
   * @param nMaxSteps
   *        The maximum number of instructions to execute, see above.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setMaxSteps (final long nMaxSteps)
  {
    m_nMaxSteps = nMaxSteps;
    return this;
  }

  /** @return The maximum nesting depth of templates and functions, 0 for the default (10,000). */
  public int getMaxDepth ()
  {
    return m_nMaxDepth;
  }

  /**
   * @param nMaxDepth
   *        The maximum nesting depth of templates and functions; it can only
   *        lower the default of 10,000. 0 for the default.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setMaxDepth (final int nMaxDepth)
  {
    m_nMaxDepth = nMaxDepth;
    return this;
  }

  /**
   * @return The execution budget this transformation spends from instead of
   *         its own, or <code>null</code> for an own budget.
   */
  @Nullable
  public ExecutionBudget getSharedBudget ()
  {
    return m_aSharedBudget;
  }

  /**
   * @param aBudget
   *        The execution budget of a calling transformation (fn:transform):
   *        steps and nesting depth are then counted against it, and
   *        {@link #getMaxSteps()} and {@link #getMaxDepth()} are ignored.
   *        <code>null</code> for an own budget.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setSharedBudget (@Nullable final ExecutionBudget aBudget)
  {
    m_aSharedBudget = aBudget;
    return this;
  }

  /**
   * @return <code>true</code> if the principal result is only wanted raw: no
   *         result tree is built and nothing is serialized. Default
   *         <code>false</code>.
   */
  public boolean isRawDelivery ()
  {
    return m_bRawDelivery;
  }

  /**
   * @param bRawDelivery
   *        <code>true</code> to deliver the principal result only raw, as the
   *        sequence the initial template, mode or function returned (e.g.
   *        fn:transform with delivery-format raw); the result tree is then
   *        <code>null</code> and the serialized result empty, so a result
   *        containing function items or maps is no error.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setRawDelivery (final boolean bRawDelivery)
  {
    m_bRawDelivery = bRawDelivery;
    return this;
  }

  /**
   * @return <code>true</code> if the host declared the principal source a
   *         streamed document. Default <code>false</code>.
   */
  public boolean isSourceStreamed ()
  {
    return m_bSourceStreamed;
  }

  /**
   * Declare that the host supplies the principal source as a streamed document
   * (XSLT 3.0 leaves it to the host how an input reaches the
   * processor; the W3C test catalogue says so with
   * <code>&lt;source streaming="true"/&gt;</code>). The source is still a
   * complete tree: the flag selects the rules that depend on the declaration
   * (XTDE3350 for a focus saved in a function item, section 10.3.6; global
   * variables without context item when the initial mode is streamable). To
   * have the source read as a stream, use
   * {@link #setStreamedSourceUri(String)}.
   *
   * @param bSourceStreamed
   *        <code>true</code> if the principal source is streamed.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setSourceStreamed (final boolean bSourceStreamed)
  {
    m_bSourceStreamed = bSourceStreamed;
    return this;
  }

  /**
   * @return The URI of the principal source to read as a stream, or
   *         <code>null</code>.
   */
  @Nullable
  public String getStreamedSourceUri ()
  {
    return m_sStreamedSourceUri;
  }

  /**
   * Supply the principal source as a document to be read as a stream: it is
   * the initial match selection of the initial mode. The document is opened
   * with the resolver of this request
   * ({@link com.helger.phoxtrot.xpath.context.IResourceResolver#openDocumentStream(String, java.util.function.Predicate)}).
   * <p>
   * When the stylesheet claims streaming, the initial mode is declared
   * streamable and proven guaranteed streamable, and the template rule for the
   * document node has the shape the dispatcher runs, the document is read one
   * record at a time and the global context item is only what
   * {@link #setGlobalContextItem(IXdmItem)} supplies (XSLT 3.0 section 2.3:
   * the two are independent; a streamed document cannot be the context of
   * global variables). Otherwise the document is read in full
   * ({@link com.helger.phoxtrot.xpath.context.IResourceResolver#resolveDocument(String)});
   * when the initial mode is not streamable it is then also the global context
   * item if none is supplied, as for a document passed to
   * {@link #setGlobalContextItem(IXdmItem)}.
   *
   * @param sAbsoluteUri
   *        Absolute URI of the source, or <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setStreamedSourceUri (@Nullable final String sAbsoluteUri)
  {
    m_sStreamedSourceUri = sAbsoluteUri;
    return this;
  }

  /**
   * @return The thread the transformation runs on. Default
   *         {@link EXsltThreading#DEFAULT}.
   */
  @NonNull
  public EXsltThreading getThreading ()
  {
    return m_eThreading;
  }

  /**
   * Choose the thread the transformation runs on (see
   * {@link EXsltThreading}): a new thread with a large stack per call (the
   * default), a thread of a shared pool, which saves the thread start of
   * many small transformations, or the calling thread, which saves the
   * thread change as well but must have a stack deep enough for the template
   * and function nesting the input causes (otherwise XPDY0130 earlier than
   * {@link #setMaxDepth(int)} says).
   *
   * @param eThreading
   *        The threading mode. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public XsltTransformRequest setThreading (@NonNull final EXsltThreading eThreading)
  {
    ValueEnforcer.notNull (eThreading, "Threading");
    m_eThreading = eThreading;
    return this;
  }
}
