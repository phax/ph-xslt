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
package com.helger.phoxtrot.xpath.stream;

import java.util.Collection;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.clone.ICloneable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.numeric.mutable.MutableBoolean;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * What the containing construct knows about the focus at the point an
 * expression is evaluated, plus the hooks the XSLT layer supplies to the
 * expression-level streamability analysis ({@link XPathStreamability}).
 * <p>
 * This is a mutable value object: the XSLT layer derives the context of an
 * inner construct with {@link #getClone()} and the setters. A clone shares the
 * "definite" flag ({@link #setDefiniteFlag(MutableBoolean)}) with the original,
 * so every rule that fires anywhere in one walk reports to the same flag.
 * <p>
 * Several properties are <code>null</code> when the caller did not supply
 * them; the analysis then takes the fail-closed answer the specification gives
 * for an unknown situation (see the individual setters). Not thread-safe.
 *
 * @author Philip Helger
 */
public final class StreamContext implements ICloneable <StreamContext>
{
  private EPosture m_eContextPosture;
  private boolean m_bContextDocument;
  private boolean m_bContextChildless;
  private EPosture m_eCurrentPosture;
  private boolean m_bCurrentChildless;
  // Both sets are copy-on-write: never modified once assigned, so clones may share them
  private ICommonsSet <XdmQName> m_aStreamingParams;
  private ICommonsSet <XdmQName> m_aMapArrayVariables;
  private boolean m_bStreamingParamRepeatable;
  private EPosture m_eStreamingParamPosture;
  private ESweep m_eStreamingParamSweep;
  private boolean m_bInHigherOrder;
  private boolean m_bFocusReset;
  private EAccumulatorRulePhase m_eAccumulatorPhase;
  private boolean m_bAccumulatorAfterConsumed;
  private EPosture m_eGroupPosture;
  private IStreamFunctionResolver m_aFunctionResolver;
  private MutableBoolean m_aDefinite;

  /**
   * @param eContextPosture
   *        The posture of the context item (XSLT 3.0 section 19.6).
   */
  public StreamContext (@NonNull final EPosture eContextPosture)
  {
    ValueEnforcer.notNull (eContextPosture, "ContextPosture");
    m_eContextPosture = eContextPosture;
    m_aStreamingParams = new CommonsHashSet <> ();
    m_aMapArrayVariables = new CommonsHashSet <> ();
    m_eStreamingParamSweep = ESweep.MOTIONLESS;
    m_eAccumulatorPhase = EAccumulatorRulePhase.NONE;
  }

  /**
   * Copy constructor for {@link #getClone()}. The streaming parameter and
   * map/array variable sets are shared, which is safe because every change
   * replaces the set instead of modifying it; the function resolver and the
   * "definite" flag are shared on purpose, so that a verdict reached on the
   * copy reaches the caller of the original.
   */
  private StreamContext (@NonNull final StreamContext aOther)
  {
    m_eContextPosture = aOther.m_eContextPosture;
    m_bContextDocument = aOther.m_bContextDocument;
    m_bContextChildless = aOther.m_bContextChildless;
    m_eCurrentPosture = aOther.m_eCurrentPosture;
    m_bCurrentChildless = aOther.m_bCurrentChildless;
    m_aStreamingParams = aOther.m_aStreamingParams;
    m_aMapArrayVariables = aOther.m_aMapArrayVariables;
    m_bStreamingParamRepeatable = aOther.m_bStreamingParamRepeatable;
    m_eStreamingParamPosture = aOther.m_eStreamingParamPosture;
    m_eStreamingParamSweep = aOther.m_eStreamingParamSweep;
    m_bInHigherOrder = aOther.m_bInHigherOrder;
    m_bFocusReset = aOther.m_bFocusReset;
    m_eAccumulatorPhase = aOther.m_eAccumulatorPhase;
    m_bAccumulatorAfterConsumed = aOther.m_bAccumulatorAfterConsumed;
    m_eGroupPosture = aOther.m_eGroupPosture;
    m_aFunctionResolver = aOther.m_aFunctionResolver;
    m_aDefinite = aOther.m_aDefinite;
  }

  /** @return The posture of the context item (XSLT 3.0 section 19.6). */
  @NonNull
  public EPosture getContextPosture ()
  {
    return m_eContextPosture;
  }

  /**
   * @param eContextPosture
   *        The posture of the context item (XSLT 3.0 section 19.6).
   * @return this for chaining
   */
  @NonNull
  public StreamContext setContextPosture (@NonNull final EPosture eContextPosture)
  {
    ValueEnforcer.notNull (eContextPosture, "ContextPosture");
    m_eContextPosture = eContextPosture;
    return this;
  }

  /** @return <code>true</code> if the context item is statically known to be a document node. */
  public boolean isContextDocument ()
  {
    return m_bContextDocument;
  }

  /**
   * The analysis has no type inferencer, and section 19.8.9.18 makes a leading
   * <code>/</code> collapse to the context item only when that is statically a
   * document node; without this flag every absolute path fails closed.
   *
   * @param bContextDocument
   *        <code>true</code> if the context item is statically known to be a
   *        document node.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setContextDocument (final boolean bContextDocument)
  {
    m_bContextDocument = bContextDocument;
    return this;
  }

  /** @return <code>true</code> if the context item cannot have children. */
  public boolean isContextChildless ()
  {
    return m_bContextChildless;
  }

  /**
   * Stands in for the static context item type section 19.8.1 needs to
   * downgrade absorption to inspection: an attribute, text, comment or
   * processing instruction node has no children. This is what makes the
   * motionless pattern <code>text()[starts-with(., '$')]</code> of the
   * specification come out motionless.
   *
   * @param bContextChildless
   *        <code>true</code> if the context item cannot have children.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setContextChildless (final boolean bContextChildless)
  {
    m_bContextChildless = bContextChildless;
    return this;
  }

  /**
   * @return The posture of the item <code>fn:current()</code> returns, or
   *         <code>null</code> if not supplied.
   */
  @Nullable
  public EPosture getCurrentPosture ()
  {
    return m_eCurrentPosture;
  }

  /**
   * <code>fn:current()</code> denotes the context item of the instruction whose
   * attribute holds the expression, which is not the context item the
   * expression itself walks once a predicate or a path step has moved the
   * focus. Its posture is not always striding: inside
   * <code>xsl:for-each select="1 to 5"</code> it is a grounded integer, so
   * <code>(20 to 21)[. gt current()]</code> is motionless.
   *
   * @param eCurrentPosture
   *        The posture of the item <code>fn:current()</code> returns.
   *        <code>null</code> (the default) leaves <code>fn:current()</code>
   *        striding.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setCurrentPosture (@Nullable final EPosture eCurrentPosture)
  {
    m_eCurrentPosture = eCurrentPosture;
    return this;
  }

  /** @return <code>true</code> if the node <code>fn:current()</code> returns cannot have children. */
  public boolean isCurrentChildless ()
  {
    return m_bCurrentChildless;
  }

  /**
   * The counterpart of {@link #setContextChildless(boolean)} for the node
   * <code>fn:current()</code> returns - in a pattern the node the whole
   * pattern matches, which is not the context item once a predicate sits on a
   * non-final step. It makes <code>text()[$x = current()]</code> motionless.
   *
   * @param bCurrentChildless
   *        <code>true</code> if that node cannot have children.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setCurrentChildless (final boolean bCurrentChildless)
  {
    m_bCurrentChildless = bCurrentChildless;
    return this;
  }

  /**
   * @param aName
   *        A variable name.
   * @return <code>true</code> if the variable is bound to the streaming
   *         parameter of a declared-streamable stylesheet function.
   */
  public boolean isStreamingParam (@Nullable final XdmQName aName)
  {
    return aName != null && m_aStreamingParams.contains (aName);
  }

  /** @return <code>true</code> if at least one streaming parameter is registered. */
  public boolean hasStreamingParams ()
  {
    return m_aStreamingParams.isNotEmpty ();
  }

  /** @return The names of the variables bound to a streaming parameter. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsSet <XdmQName> getAllStreamingParams ()
  {
    return m_aStreamingParams.getClone ();
  }

  /**
   * Register the variables bound to the streaming parameter of a
   * declared-streamable stylesheet function (section 19.8.8.12). A reference to
   * one is not grounded; its posture and sweep are
   * {@link #setStreamingParamPosture(EPosture)} and
   * {@link #setStreamingParamSweep(ESweep)}.
   *
   * @param aNames
   *        The variable names. Replaces the previous set.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setStreamingParams (@NonNull final Collection <XdmQName> aNames)
  {
    ValueEnforcer.notNullNoNullValue (aNames, "Names");
    m_aStreamingParams = new CommonsHashSet <> (aNames);
    return this;
  }

  /**
   * @param aName
   *        A variable bound to a streaming parameter.
   * @return this for chaining
   * @see #setStreamingParams(Collection)
   */
  @NonNull
  public StreamContext addStreamingParam (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    final ICommonsSet <XdmQName> aNew = m_aStreamingParams.getClone ();
    aNew.add (aName);
    m_aStreamingParams = aNew;
    return this;
  }

  /**
   * @param aName
   *        A variable name.
   * @return <code>true</code> if the declared type of the variable makes it
   *         statically known to hold a map or an array.
   */
  public boolean isMapArrayVariable (@Nullable final XdmQName aName)
  {
    return aName != null && m_aMapArrayVariables.contains (aName);
  }

  /** @return The names of the variables statically known to hold a map or an array. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsSet <XdmQName> getAllMapArrayVariables ()
  {
    return m_aMapArrayVariables.getClone ();
  }

  /**
   * Register the variables whose declared type makes them statically known to
   * hold a map or an array. Section 19.8.8.11 turns on exactly that knowledge:
   * the arguments of a dynamic call on such a variable are absorbed rather than
   * navigated. The set only ever makes the analysis more precise, never more
   * permissive than the Recommendation.
   *
   * @param aNames
   *        The variable names. Replaces the previous set.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setMapArrayVariables (@NonNull final Collection <XdmQName> aNames)
  {
    ValueEnforcer.notNullNoNullValue (aNames, "Names");
    m_aMapArrayVariables = new CommonsHashSet <> (aNames);
    return this;
  }

  /**
   * @param aName
   *        A variable statically known to hold a map or an array.
   * @return this for chaining
   * @see #setMapArrayVariables(Collection)
   */
  @NonNull
  public StreamContext addMapArrayVariable (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    final ICommonsSet <XdmQName> aNew = m_aMapArrayVariables.getClone ();
    aNew.add (aName);
    m_aMapArrayVariables = aNew;
    return this;
  }

  /**
   * @return <code>true</code> if a reference to the streaming parameter is
   *         permitted inside a higher-order operand.
   */
  public boolean isStreamingParamRepeatable ()
  {
    return m_bStreamingParamRepeatable;
  }

  /**
   * The category of the function still permits a reference to the streaming
   * parameter inside a higher-order operand (one evaluated repeatedly). This is
   * the "singular" test of the 2015 draft of section 19.8.8.11, which the
   * Recommendation dropped once the per-category reference rules covered the
   * same ground; it is kept because it only ever rejects more.
   *
   * @param bStreamingParamRepeatable
   *        <code>true</code> to permit such references.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setStreamingParamRepeatable (final boolean bStreamingParamRepeatable)
  {
    m_bStreamingParamRepeatable = bStreamingParamRepeatable;
    return this;
  }

  /**
   * @return The posture of a reference to the streaming parameter, or
   *         <code>null</code> if not supplied.
   */
  @Nullable
  public EPosture getStreamingParamPosture ()
  {
    return m_eStreamingParamPosture;
  }

  /**
   * The posture the "Rules for references to the streaming parameter" of
   * section 19.8.5 give such a reference: striding for every category but
   * ascent, which gives climbing. No category gives grounded, so
   * <code>null</code> (the default) and grounded both mean "registered without
   * saying what its references are", and a reference then fails closed.
   *
   * @param eStreamingParamPosture
   *        The posture of a reference to the streaming parameter.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setStreamingParamPosture (@Nullable final EPosture eStreamingParamPosture)
  {
    m_eStreamingParamPosture = eStreamingParamPosture;
    return this;
  }

  /** @return The sweep of a reference to the streaming parameter. */
  @NonNull
  public ESweep getStreamingParamSweep ()
  {
    return m_eStreamingParamSweep;
  }

  /**
   * The sweep the same rules give the reference: motionless (the default)
   * everywhere except in an absorbing function whose streaming parameter
   * permits more than one node, where reaching the second and later nodes means
   * advancing the stream.
   *
   * @param eStreamingParamSweep
   *        The sweep of a reference to the streaming parameter.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setStreamingParamSweep (@NonNull final ESweep eStreamingParamSweep)
  {
    ValueEnforcer.notNull (eStreamingParamSweep, "StreamingParamSweep");
    m_eStreamingParamSweep = eStreamingParamSweep;
    return this;
  }

  /** @return <code>true</code> while the walk is inside a higher-order operand. */
  public boolean isInHigherOrder ()
  {
    return m_bInHigherOrder;
  }

  /**
   * The context marked as being inside a higher-order operand - one the
   * containing construct may evaluate more than once. The "singular" test of
   * section 19.8.8.11 turns on it, so the XSLT layer must apply it wherever it
   * classifies a body the same way.
   *
   * @return A clone of this context inside a higher-order operand, or this
   *         object if it already is.
   */
  @NonNull
  public StreamContext getAsInHigherOrder ()
  {
    if (m_bInHigherOrder)
      return this;
    final StreamContext ret = getClone ();
    ret.m_bInHigherOrder = true;
    return ret;
  }

  /**
   * Internal: copy the higher-order state of another context.
   *
   * @param bInHigherOrder
   *        The new state.
   */
  void setInHigherOrder (final boolean bInHigherOrder)
  {
    m_bInHigherOrder = bInHigherOrder;
  }

  /**
   * @return <code>true</code> if the focus was re-established by an inner
   *         focus-setting container.
   */
  public boolean isFocusReset ()
  {
    return m_bFocusReset;
  }

  /**
   * The focus was re-established by an inner focus-setting container (a
   * predicate, a simple-map step, a postfix path step) rather than by the
   * containing instruction. Section 19.8.9.1 makes
   * <code>accumulator-after</code> free-ranging exactly then.
   *
   * @param bFocusReset
   *        The new value.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setFocusReset (final boolean bFocusReset)
  {
    m_bFocusReset = bFocusReset;
    return this;
  }

  /** @return The accumulator rule phase the construct sits in. */
  @NonNull
  public EAccumulatorRulePhase getAccumulatorPhase ()
  {
    return m_eAccumulatorPhase;
  }

  /**
   * Section 19.8.9.1 makes <code>accumulator-after</code> free-ranging in a
   * <code>phase="start"</code> rule and motionless in a <code>phase="end"</code>
   * one.
   *
   * @param eAccumulatorPhase
   *        The accumulator rule phase the construct sits in.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setAccumulatorPhase (@NonNull final EAccumulatorRulePhase eAccumulatorPhase)
  {
    ValueEnforcer.notNull (eAccumulatorPhase, "AccumulatorPhase");
    m_eAccumulatorPhase = eAccumulatorPhase;
    return this;
  }

  /**
   * @return <code>true</code> if a preceding instruction of the same sequence
   *         constructor already consumed the stream.
   */
  public boolean isAccumulatorAfterConsumed ()
  {
    return m_bAccumulatorAfterConsumed;
  }

  /**
   * A preceding instruction of the same sequence constructor already consumed
   * the stream, which the last clauses of section 19.8.9.1 make
   * <code>accumulator-after</code> motionless.
   *
   * @param bAccumulatorAfterConsumed
   *        The new value.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setAccumulatorAfterConsumed (final boolean bAccumulatorAfterConsumed)
  {
    m_bAccumulatorAfterConsumed = bAccumulatorAfterConsumed;
    return this;
  }

  /**
   * @return The posture of the select expression of the containing
   *         <code>xsl:for-each-group</code>, or <code>null</code> if there is
   *         none.
   */
  @Nullable
  public EPosture getGroupPosture ()
  {
    return m_eGroupPosture;
  }

  /**
   * The posture of the select expression of the containing
   * <code>xsl:for-each-group</code>, which section 19.8.9.4 hands to a
   * <code>current-group()</code> call. <code>null</code> (the default) means
   * there is no containing <code>xsl:for-each-group</code>, and
   * <code>current-group()</code> is then roaming and free-ranging.
   *
   * @param eGroupPosture
   *        The posture of the population.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setGroupPosture (@Nullable final EPosture eGroupPosture)
  {
    m_eGroupPosture = eGroupPosture;
    return this;
  }

  /** @return The stylesheet function resolver, or <code>null</code>. */
  @Nullable
  public IStreamFunctionResolver getFunctionResolver ()
  {
    return m_aFunctionResolver;
  }

  /**
   * Resolves calls to stylesheet functions (section 19.8.5). Without a
   * resolver, or for a name it does not know, a call is unclassifiable and
   * fails closed exactly as an extension function does.
   *
   * @param aFunctionResolver
   *        The resolver. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setFunctionResolver (@Nullable final IStreamFunctionResolver aFunctionResolver)
  {
    m_aFunctionResolver = aFunctionResolver;
    return this;
  }

  /** @return The flag that receives "definite" verdicts, or <code>null</code>. */
  @Nullable
  public MutableBoolean getDefiniteFlag ()
  {
    return m_aDefinite;
  }

  /**
   * Install a channel back to the caller that answers one question about a
   * walk that came out non-streamable: was that verdict reached by a rule, or
   * merely not disproved? The classification itself is identical either way.
   * It matters only to a caller deciding whether to report the verdict as
   * XTSE3430: "the Recommendation says this construct is roaming and
   * free-ranging" is a statement about the stylesheet, while "this analysis is
   * incomplete here" is a statement about this engine, and only the first
   * justifies rejecting a stylesheet the user asked to stream.
   * <p>
   * The flag is only ever set to <code>true</code> (see
   * {@link #markDefinite()}); the caller resets it before a walk and consults it
   * only when the walk came out non-streamable.
   *
   * @param aDefinite
   *        The flag. May be <code>null</code> to switch the channel off.
   * @return this for chaining
   */
  @NonNull
  public StreamContext setDefiniteFlag (@Nullable final MutableBoolean aDefinite)
  {
    m_aDefinite = aDefinite;
    return this;
  }

  /**
   * Record that a rule stated unconditionally by the Recommendation - not a
   * fail-closed default - produced a non-streamable verdict. Deliberately
   * one-way: several rules can fire in one walk, and a later recovery (the
   * scanning-expression reassessment of section 19.8.8.8, say) does not unset
   * it. That is safe because the flag says "a definite rule fired", never "the
   * body is non-streamable", and the caller combines both. A no-op without a
   * flag.
   */
  public void markDefinite ()
  {
    if (m_aDefinite != null)
      m_aDefinite.set (true);
  }

  /**
   * @return A copy with all properties of this context, to be changed for a
   *         nested construct without affecting this one. The function
   *         resolver and the "definite" flag stay shared with this context.
   */
  @NonNull
  public StreamContext getClone ()
  {
    return new StreamContext (this);
  }
}
