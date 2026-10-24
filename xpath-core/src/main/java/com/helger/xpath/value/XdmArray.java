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
package com.helger.xpath.value;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.IXdmItem;

/**
 * An immutable XPath 3.1 array: an ordered list of members, each a sequence.
 * Members are 0-based in this API (XPath positions are 1-based). Modifying
 * methods return a new array.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmArray implements IXdmItem
{
  public static final XdmArray EMPTY = new XdmArray (new XdmSequence [0]);

  private final XdmSequence [] m_aMembers;

  private XdmArray (@NonNull final XdmSequence [] aMembers)
  {
    m_aMembers = aMembers;
  }

  /**
   * @param aMembers
   *        The members; the list is copied.
   * @return The array
   */
  @NonNull
  public static XdmArray of (@NonNull final List <XdmSequence> aMembers)
  {
    ValueEnforcer.notNull (aMembers, "Members");
    return aMembers.isEmpty () ? EMPTY : new XdmArray (aMembers.toArray (new XdmSequence [0]));
  }

  /**
   * @param aMembers
   *        The members; the array is copied.
   * @return The array
   */
  @NonNull
  public static XdmArray of (@NonNull final XdmSequence... aMembers)
  {
    ValueEnforcer.notNull (aMembers, "Members");
    return aMembers.length == 0 ? EMPTY : new XdmArray (aMembers.clone ());
  }

  /**
   * @param aItems
   *        The items.
   * @return An array with one member per item (a curly array constructor).
   */
  @NonNull
  public static XdmArray ofItems (@NonNull final XdmSequence aItems)
  {
    ValueEnforcer.notNull (aItems, "Items");
    final XdmSequence [] aMembers = new XdmSequence [aItems.size ()];
    for (int i = 0; i < aMembers.length; ++i)
      aMembers[i] = XdmSequence.of (aItems.get (i));
    return aMembers.length == 0 ? EMPTY : new XdmArray (aMembers);
  }

  @Nonnegative
  public int size ()
  {
    return m_aMembers.length;
  }

  public boolean isEmpty ()
  {
    return m_aMembers.length == 0;
  }

  @NonNull
  public XdmSequence get (@Nonnegative final int nIndex)
  {
    return m_aMembers[nIndex];
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XdmSequence> getAllMembers ()
  {
    return new CommonsArrayList <> (m_aMembers);
  }

  /** @return A read-only view of the members, without copying. */
  @NonNull
  public List <XdmSequence> members ()
  {
    return Collections.unmodifiableList (Arrays.asList (m_aMembers));
  }

  /**
   * @param nIndex
   *        0-based index of the member to replace.
   * @param aValue
   *        The new member.
   * @return A new array with the member at the index replaced.
   */
  @NonNull
  public XdmArray put (@Nonnegative final int nIndex, @NonNull final XdmSequence aValue)
  {
    final XdmSequence [] aNew = m_aMembers.clone ();
    aNew[nIndex] = aValue;
    return new XdmArray (aNew);
  }

  /**
   * @param aValue
   *        The new member.
   * @return A new array with the member appended.
   */
  @NonNull
  public XdmArray append (@NonNull final XdmSequence aValue)
  {
    final XdmSequence [] aNew = Arrays.copyOf (m_aMembers, m_aMembers.length + 1);
    aNew[m_aMembers.length] = aValue;
    return new XdmArray (aNew);
  }

  /**
   * @param nIndex
   *        0-based position, 0 to {@link #size()} inclusive.
   * @param aValue
   *        The new member.
   * @return A new array with the member inserted before the index.
   */
  @NonNull
  public XdmArray insertBefore (@Nonnegative final int nIndex, @NonNull final XdmSequence aValue)
  {
    final XdmSequence [] aNew = new XdmSequence [m_aMembers.length + 1];
    System.arraycopy (m_aMembers, 0, aNew, 0, nIndex);
    aNew[nIndex] = aValue;
    System.arraycopy (m_aMembers, nIndex, aNew, nIndex + 1, m_aMembers.length - nIndex);
    return new XdmArray (aNew);
  }

  /**
   * @param nFrom
   *        0-based start, inclusive.
   * @param nTo
   *        0-based end, exclusive.
   * @return The members in the range as a new array.
   */
  @NonNull
  public XdmArray subArray (@Nonnegative final int nFrom, @Nonnegative final int nTo)
  {
    if (nFrom >= nTo)
      return EMPTY;
    if (nFrom == 0 && nTo == m_aMembers.length)
      return this;
    return new XdmArray (Arrays.copyOfRange (m_aMembers, nFrom, nTo));
  }

  /** @return A new array with the members in reverse order. */
  @NonNull
  public XdmArray reverse ()
  {
    final int n = m_aMembers.length;
    if (n < 2)
      return this;
    final XdmSequence [] aNew = new XdmSequence [n];
    for (int i = 0; i < n; ++i)
      aNew[i] = m_aMembers[n - 1 - i];
    return new XdmArray (aNew);
  }

  @Override
  public String toString ()
  {
    return "array(" + m_aMembers.length + ")";
  }

  /**
   * Collects members without copying per added member. A builder must not be
   * used after {@link #build()}.
   */
  @NotThreadSafe
  public static final class Builder
  {
    private XdmSequence [] m_aBuf;
    private int m_nSize = 0;

    public Builder ()
    {
      this (8);
    }

    public Builder (@Nonnegative final int nExpectedSize)
    {
      m_aBuf = new XdmSequence [Math.max (nExpectedSize, 1)];
    }

    @NonNull
    public Builder add (@NonNull final XdmSequence aMember)
    {
      ValueEnforcer.notNull (aMember, "Member");
      if (m_nSize == m_aBuf.length)
        m_aBuf = Arrays.copyOf (m_aBuf, m_nSize * 2);
      m_aBuf[m_nSize++] = aMember;
      return this;
    }

    @NonNull
    public Builder addAll (@NonNull final XdmArray aArray)
    {
      for (final XdmSequence aMember : aArray.m_aMembers)
        add (aMember);
      return this;
    }

    @Nonnegative
    public int size ()
    {
      return m_nSize;
    }

    @NonNull
    public XdmArray build ()
    {
      return m_nSize == 0 ? EMPTY : new XdmArray (Arrays.copyOf (m_aBuf, m_nSize));
    }
  }
}
