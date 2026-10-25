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
package com.helger.xslt.runtime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.model.Mode;
import com.helger.xslt.model.TemplateRule;

/**
 * The XSLT part of the dynamic context that XPath does not know: current
 * template rule and mode, tunnel parameters, captured regex groups and the
 * extension slots of instruction families. Immutable; it travels as the host
 * state of the {@link com.helger.xpath.context.DynamicContext}, so XSLT
 * functions called from XPath (current-group(), regex-group(), ...) see the
 * state of the instruction the expression belongs to.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsltState
{
  /** A state with nothing set. */
  public static final XsltState EMPTY = new XsltState (null, null, null, null, null);

  private record Tunnel (@NonNull XdmQName name, @NonNull XdmSequence value, @Nullable Tunnel next)
  {}

  private record Slot (@NonNull XsltStateKey <?> key, @Nullable Object value, @Nullable Slot next)
  {}

  private final TemplateRule m_aCurrentRule;
  private final Mode m_aCurrentMode;
  private final Tunnel m_aTunnel;
  private final String [] m_aRegexGroups;
  private final Slot m_aSlots;

  private XsltState (@Nullable final TemplateRule aCurrentRule,
                     @Nullable final Mode aCurrentMode,
                     @Nullable final Tunnel aTunnel,
                     @Nullable final String [] aRegexGroups,
                     @Nullable final Slot aSlots)
  {
    m_aCurrentRule = aCurrentRule;
    m_aCurrentMode = aCurrentMode;
    m_aTunnel = aTunnel;
    m_aRegexGroups = aRegexGroups;
    m_aSlots = aSlots;
  }

  @Nullable
  private static Slot _filterSlots (@Nullable final Slot aSlots, final boolean bTemplateCall)
  {
    // Iterative: keep the innermost binding of each key that survives the call
    if (aSlots == null)
      return null;
    final ICommonsList <Slot> aKept = new CommonsArrayList <> ();
    boolean bChanged = false;
    for (Slot aCur = aSlots; aCur != null; aCur = aCur.next ())
    {
      final XsltStateKey.EScope eScope = aCur.key ().getScope ();
      final boolean bKeep = eScope == XsltStateKey.EScope.KEPT ||
                            (bTemplateCall && eScope == XsltStateKey.EScope.CLEARED_IN_FUNCTIONS);
      boolean bDuplicate = false;
      if (bKeep)
        for (final Slot aPrev : aKept)
          if (aPrev.key () == aCur.key ())
          {
            bDuplicate = true;
            break;
          }
      if (bKeep && !bDuplicate)
        aKept.add (aCur);
      else
        bChanged = true;
    }
    if (!bChanged)
      return aSlots;
    Slot ret = null;
    for (int i = aKept.size () - 1; i >= 0; --i)
    {
      final Slot aSlot = aKept.get (i);
      ret = new Slot (aSlot.key (), aSlot.value (), ret);
    }
    return ret;
  }

  /**
   * @return The current template rule (for xsl:apply-imports and
   *         xsl:next-match), or <code>null</code> if there is none.
   */
  @Nullable
  public TemplateRule getCurrentRule ()
  {
    return m_aCurrentRule;
  }

  /**
   * @return The current mode, or <code>null</code> before any template rule
   *         was invoked (the default mode then applies).
   */
  @Nullable
  public Mode getCurrentMode ()
  {
    return m_aCurrentMode;
  }

  /**
   * @param aRule
   *        The new current template rule. May be <code>null</code>.
   * @param aMode
   *        The new current mode. May be <code>null</code>.
   * @return The derived state.
   */
  @NonNull
  public XsltState withCurrentRule (@Nullable final TemplateRule aRule, @Nullable final Mode aMode)
  {
    return new XsltState (aRule, aMode, m_aTunnel, m_aRegexGroups, m_aSlots);
  }

  /**
   * @param aName
   *        Parameter name.
   * @return The value of the tunnel parameter or <code>null</code> if it is
   *         not set.
   */
  @Nullable
  public XdmSequence getTunnelParam (@NonNull final XdmQName aName)
  {
    for (Tunnel aCur = m_aTunnel; aCur != null; aCur = aCur.next ())
      if (aCur.name ().equals (aName))
        return aCur.value ();
    return null;
  }

  /** @return All tunnel parameters, innermost binding of each name. */
  @NonNull
  public ICommonsOrderedMap <XdmQName, XdmSequence> getAllTunnelParams ()
  {
    final ICommonsOrderedMap <XdmQName, XdmSequence> ret = new CommonsLinkedHashMap <> ();
    for (Tunnel aCur = m_aTunnel; aCur != null; aCur = aCur.next ())
      if (!ret.containsKey (aCur.name ()))
        ret.put (aCur.name (), aCur.value ());
    return ret;
  }

  /**
   * @param aName
   *        Parameter name.
   * @param aValue
   *        Value.
   * @return A state with the tunnel parameter set (shadowing an older binding).
   */
  @NonNull
  public XsltState withTunnelParam (@NonNull final XdmQName aName, @NonNull final XdmSequence aValue)
  {
    return new XsltState (m_aCurrentRule, m_aCurrentMode, new Tunnel (aName, aValue, m_aTunnel), m_aRegexGroups, m_aSlots);
  }

  /**
   * @return The captured substrings of the innermost xsl:analyze-string match
   *         (index 0 is the whole match), or <code>null</code> if there are
   *         none.
   */
  @Nullable
  public String [] getRegexGroups ()
  {
    return m_aRegexGroups;
  }

  @NonNull
  public XsltState withRegexGroups (@Nullable final String [] aGroups)
  {
    return new XsltState (m_aCurrentRule, m_aCurrentMode, m_aTunnel, aGroups, m_aSlots);
  }

  /**
   * @param aKey
   *        Slot key.
   * @param <T>
   *        Value type.
   * @return The value or <code>null</code> if the slot is not set.
   */
  @Nullable
  @SuppressWarnings ("unchecked")
  public <T> T get (@NonNull final XsltStateKey <T> aKey)
  {
    for (Slot aCur = m_aSlots; aCur != null; aCur = aCur.next ())
      if (aCur.key () == aKey)
        return (T) aCur.value ();
    return null;
  }

  /**
   * @param aKey
   *        Slot key.
   * @return <code>true</code> if the slot is set (possibly to
   *         <code>null</code>).
   */
  public boolean isSet (@NonNull final XsltStateKey <?> aKey)
  {
    for (Slot aCur = m_aSlots; aCur != null; aCur = aCur.next ())
      if (aCur.key () == aKey)
        return true;
    return false;
  }

  /**
   * @param aKey
   *        Slot key.
   * @param aValue
   *        The value; <code>null</code> marks the slot as explicitly absent.
   * @param <T>
   *        Value type.
   * @return The derived state.
   */
  @NonNull
  public <T> XsltState with (@NonNull final XsltStateKey <T> aKey, @Nullable final T aValue)
  {
    // Replace a binding of the same key at the head, so repeated updates do not
    // grow the list
    final Slot aRest = m_aSlots != null && m_aSlots.key () == aKey ? m_aSlots.next () : m_aSlots;
    return new XsltState (m_aCurrentRule, m_aCurrentMode, m_aTunnel, m_aRegexGroups, new Slot (aKey, aValue, aRest));
  }

  /**
   * @param aKey
   *        Slot key.
   * @return The derived state in which the slot is not set.
   */
  @NonNull
  public XsltState without (@NonNull final XsltStateKey <?> aKey)
  {
    if (!isSet (aKey))
      return this;
    return with (aKey, null);
  }

  /**
   * @return The state at the start of a template invocation: tunnel
   *         parameters kept, regex groups and the slots that do not survive
   *         template calls cleared. Rule and mode are set by the caller.
   */
  @NonNull
  public XsltState forTemplateCall ()
  {
    final Slot aSlots = _filterSlots (m_aSlots, true);
    if (m_aRegexGroups == null && aSlots == m_aSlots)
      return this;
    return new XsltState (m_aCurrentRule, m_aCurrentMode, m_aTunnel, null, aSlots);
  }

  /**
   * @return The state in the body of a stylesheet function: no current rule
   *         or mode, no tunnel parameters, no regex groups, only the slots
   *         marked {@link XsltStateKey.EScope#KEPT}.
   */
  @NonNull
  public XsltState forFunctionCall ()
  {
    final Slot aSlots = _filterSlots (m_aSlots, false);
    if (m_aCurrentRule == null && m_aCurrentMode == null && m_aTunnel == null && m_aRegexGroups == null && aSlots == null)
      return EMPTY;
    return new XsltState (null, null, null, null, aSlots);
  }

  /**
   * @return A state without the tunnel parameters (e.g. for templates called
   *         from a stylesheet function).
   */
  @NonNull
  public XsltState withoutTunnelParams ()
  {
    if (m_aTunnel == null)
      return this;
    return new XsltState (m_aCurrentRule, m_aCurrentMode, null, m_aRegexGroups, m_aSlots);
  }
}
