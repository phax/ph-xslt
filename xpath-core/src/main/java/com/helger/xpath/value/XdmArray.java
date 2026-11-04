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
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
 * <p>
 * Arrays made by {@link #append(XdmSequence)} share one backing array: an array
 * never reads past its own size, so the newest array can append by writing the
 * next free slot, which makes building an array one <code>array:append</code>
 * at a time linear. A slot is only ever written once.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmArray implements IXdmItem
{
  public static final XdmArray EMPTY = new XdmArray (new XdmSequence [0]);

  // May be longer than the array, see m_nSize
  private final XdmSequence [] m_aMembers;
  private final int m_nSize;
  // How far the shared backing array is handed out; null if it is not shared
  private final AtomicInteger m_aClaimed;
  // The type of all members if each is a single atomic value, otherwise null
  private final EAtomicType m_eMemberType;

  private XdmArray (@NonNull final XdmSequence [] aMembers)
  {
    this (aMembers, aMembers.length, null, _getUniformMemberType (aMembers));
  }

  private XdmArray (@NonNull final XdmSequence [] aMembers,
                    @Nonnegative final int nSize,
                    @Nullable final AtomicInteger aClaimed,
                    @Nullable final EAtomicType eMemberType)
  {
    m_aMembers = aMembers;
    m_nSize = nSize;
    m_aClaimed = aClaimed;
    m_eMemberType = eMemberType;
  }

  @Nullable
  private static EAtomicType _getUniformMemberType (@NonNull final XdmSequence [] aMembers)
  {
    if (aMembers.length == 0)
      return null;
    final EAtomicType ret = aMembers[0].getSingleAtomicType ();
    if (ret != null)
      for (int i = 1; i < aMembers.length; ++i)
        if (aMembers[i].getSingleAtomicType () != ret)
          return null;
    return ret;
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
    return m_nSize;
  }

  public boolean isEmpty ()
  {
    return m_nSize == 0;
  }

  @NonNull
  public XdmSequence get (@Nonnegative final int nIndex)
  {
    if (nIndex >= m_nSize)
      throw new ArrayIndexOutOfBoundsException (nIndex);
    return m_aMembers[nIndex];
  }

  /**
   * @return The atomic type of all members, if each is a single atomic value,
   *         or <code>null</code> if it is unknown. A cheap way to test an
   *         <code>array(T)</code> type without looking at every member.
   */
  @Nullable
  public EAtomicType getUniformMemberType ()
  {
    return m_eMemberType;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XdmSequence> getAllMembers ()
  {
    return new CommonsArrayList <> (members ());
  }

  /** @return A read-only view of the members, without copying. */
  @NonNull
  public List <XdmSequence> members ()
  {
    return Collections.unmodifiableList (Arrays.asList (m_aMembers).subList (0, m_nSize));
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
    final XdmSequence [] aNew = Arrays.copyOf (m_aMembers, m_nSize);
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
    final int nSize = m_nSize;
    final EAtomicType eValueType = aValue.getSingleAtomicType ();
    // Kept up to date in O(1), so that an array(T) test needs no loop
    final EAtomicType eMemberType = nSize == 0 || eValueType == m_eMemberType ? eValueType : null;
    if (nSize < m_aMembers.length && m_aClaimed != null && m_aClaimed.compareAndSet (nSize, nSize + 1))
    {
      // The slot is ours: no other array has appended here
      m_aMembers[nSize] = aValue;
      return new XdmArray (m_aMembers, nSize + 1, m_aClaimed, eMemberType);
    }
    final XdmSequence [] aNew = Arrays.copyOf (m_aMembers, nSize + nSize / 2 + 4);
    aNew[nSize] = aValue;
    return new XdmArray (aNew, nSize + 1, new AtomicInteger (nSize + 1), eMemberType);
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
    final XdmSequence [] aNew = new XdmSequence [m_nSize + 1];
    System.arraycopy (m_aMembers, 0, aNew, 0, nIndex);
    aNew[nIndex] = aValue;
    System.arraycopy (m_aMembers, nIndex, aNew, nIndex + 1, m_nSize - nIndex);
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
    if (nFrom == 0 && nTo == m_nSize)
      return this;
    return new XdmArray (Arrays.copyOfRange (m_aMembers, nFrom, nTo));
  }

  /** @return A new array with the members in reverse order. */
  @NonNull
  public XdmArray reverse ()
  {
    final int n = m_nSize;
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
    return "array(" + m_nSize + ")";
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
      for (int i = 0; i < aArray.m_nSize; ++i)
        add (aArray.m_aMembers[i]);
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
