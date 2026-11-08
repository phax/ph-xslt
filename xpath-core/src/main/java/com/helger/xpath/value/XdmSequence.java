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

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.IXdmItem;

/**
 * An immutable XDM sequence. A sequence never contains another sequence and the
 * empty sequence is the shared {@link #EMPTY}. A single item is its own
 * singleton sequence. Items are 0-based in this API (XPath positions are
 * 1-based).
 * <p>
 * A sequence of exactly one item keeps the item in a field of its own, without
 * an item array; every other sequence keeps an array that is never shared with
 * a caller.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmSequence implements Iterable <IXdmItem>
{
  private static final IXdmItem [] NO_ITEMS = new IXdmItem [0];

  public static final XdmSequence EMPTY = new XdmSequence (NO_ITEMS);

  // The items of a sequence of zero or at least two items; null for one item
  private final IXdmItem [] m_aItems;
  // The only item of a sequence of one item; null otherwise
  private final IXdmItem m_aSingle;

  private XdmSequence (@NonNull final IXdmItem [] aItems)
  {
    m_aItems = aItems;
    m_aSingle = null;
  }

  private XdmSequence (@NonNull final IXdmItem aSingle)
  {
    m_aItems = null;
    m_aSingle = aSingle;
  }

  /**
   * @param aItem
   *        The item.
   * @return A new sequence with exactly this item.
   */
  @NonNull
  public static XdmSequence of (@NonNull final IXdmItem aItem)
  {
    ValueEnforcer.notNull (aItem, "Item");
    return new XdmSequence (aItem);
  }

  /**
   * @param aItems
   *        The items; the array is copied.
   * @return The sequence
   */
  @NonNull
  public static XdmSequence of (@NonNull final IXdmItem... aItems)
  {
    ValueEnforcer.notNull (aItems, "Items");
    return switch (aItems.length)
    {
      case 0 -> EMPTY;
      case 1 -> new XdmSequence (aItems[0]);
      default -> new XdmSequence (Arrays.copyOf (aItems, aItems.length, IXdmItem [].class));
    };
  }

  /**
   * @param aItems
   *        The items; the list is copied.
   * @return The sequence
   */
  @NonNull
  public static XdmSequence of (@NonNull final List <? extends IXdmItem> aItems)
  {
    ValueEnforcer.notNull (aItems, "Items");
    return switch (aItems.size ())
    {
      case 0 -> EMPTY;
      case 1 -> new XdmSequence (aItems.get (0));
      default -> new XdmSequence (aItems.toArray (new IXdmItem [aItems.size ()]));
    };
  }

  /**
   * Concatenate sequences (the comma operator).
   *
   * @param aParts
   *        The sequences to join, none <code>null</code>.
   * @return The joined sequence
   */
  @NonNull
  public static XdmSequence concat (@NonNull final XdmSequence... aParts)
  {
    int nTotal = 0;
    XdmSequence aOnlyNonEmpty = null;
    int nNonEmpty = 0;
    for (final XdmSequence aPart : aParts)
      if (!aPart.isEmpty ())
      {
        nTotal += aPart.size ();
        aOnlyNonEmpty = aPart;
        nNonEmpty++;
      }
    if (nNonEmpty == 0)
      return EMPTY;
    if (nNonEmpty == 1)
      return aOnlyNonEmpty;
    final IXdmItem [] aRet = new IXdmItem [nTotal];
    int nPos = 0;
    for (final XdmSequence aPart : aParts)
      nPos = aPart._copyTo (aRet, nPos);
    return new XdmSequence (aRet);
  }

  /**
   * Copy the items of this sequence into an array.
   *
   * @return The position after the last copied item.
   */
  private int _copyTo (@NonNull final IXdmItem [] aTarget, @Nonnegative final int nPos)
  {
    if (m_aItems == null)
    {
      aTarget[nPos] = m_aSingle;
      return nPos + 1;
    }
    System.arraycopy (m_aItems, 0, aTarget, nPos, m_aItems.length);
    return nPos + m_aItems.length;
  }

  @Nonnegative
  public int size ()
  {
    return m_aItems == null ? 1 : m_aItems.length;
  }

  public boolean isEmpty ()
  {
    return m_aItems != null && m_aItems.length == 0;
  }

  /**
   * @return The type of the only item if this sequence is exactly one atomic
   *         value, otherwise <code>null</code>.
   */
  @Nullable
  public EAtomicType getSingleAtomicType ()
  {
    return m_aSingle instanceof final XdmAtomic aAtomic ? aAtomic.getType () : null;
  }

  public boolean isSingleton ()
  {
    return m_aItems == null;
  }

  /**
   * @param nIndex
   *        0-based item index.
   * @return The item at the index.
   * @throws ArrayIndexOutOfBoundsException
   *         if the index is negative or not below {@link #size()}
   */
  @NonNull
  public IXdmItem get (@Nonnegative final int nIndex)
  {
    if (m_aItems != null)
      return m_aItems[nIndex];
    if (nIndex != 0)
      throw new ArrayIndexOutOfBoundsException (nIndex);
    return m_aSingle;
  }

  /**
   * @return The first item.
   * @throws NoSuchElementException
   *         if the sequence is empty.
   */
  @NonNull
  public IXdmItem getFirst ()
  {
    if (m_aItems == null)
      return m_aSingle;
    if (m_aItems.length == 0)
      throw new NoSuchElementException ();
    return m_aItems[0];
  }

  /**
   * @param nFrom
   *        0-based start, inclusive
   * @param nTo
   *        0-based end, exclusive
   * @return The subsequence
   */
  @NonNull
  public XdmSequence subSequence (@Nonnegative final int nFrom, @Nonnegative final int nTo)
  {
    if (nFrom >= nTo)
      return EMPTY;
    if (nFrom == 0 && nTo == size ())
      return this;
    final IXdmItem [] aItems = m_aItems == null ? new IXdmItem [] { m_aSingle } : m_aItems;
    if (nTo - nFrom == 1 && nFrom < aItems.length)
      return new XdmSequence (aItems[nFrom]);
    // Arrays.copyOfRange pads a range beyond the end with null, as before
    final IXdmItem [] aRange = Arrays.copyOfRange (aItems, nFrom, nTo);
    return aRange.length == 1 ? new XdmSequence (aRange[0]) : new XdmSequence (aRange);
  }

  /** @return A new list with the items in sequence order. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IXdmItem> getAsList ()
  {
    if (m_aItems == null)
    {
      final ICommonsList <IXdmItem> ret = new CommonsArrayList <> (1);
      ret.add (m_aSingle);
      return ret;
    }
    return new CommonsArrayList <> (m_aItems);
  }

  /**
   * @return An iterator over the items in sequence order, reading the item
   *         array (or the single item) directly; it does not support
   *         <code>remove</code>.
   */
  @Override
  public Iterator <IXdmItem> iterator ()
  {
    if (m_aItems == null)
      return new Iterator <> ()
      {
        private boolean m_bDone = false;

        public boolean hasNext ()
        {
          return !m_bDone;
        }

        public IXdmItem next ()
        {
          if (m_bDone)
            throw new NoSuchElementException ();
          m_bDone = true;
          return m_aSingle;
        }
      };
    return new Iterator <> ()
    {
      private int m_nIndex = 0;

      public boolean hasNext ()
      {
        return m_nIndex < m_aItems.length;
      }

      public IXdmItem next ()
      {
        if (m_nIndex >= m_aItems.length)
          throw new NoSuchElementException ();
        return m_aItems[m_nIndex++];
      }
    };
  }

  /**
   * Collects items without the copying that {@link #of(List)} does for every
   * call. No array is allocated for the first item; {@link #build()} hands the
   * buffer over to the sequence when it is exactly full and copies it only on
   * the next {@link #add(IXdmItem)}.
   */
  public static final class Builder
  {
    private final int m_nInitialCapacity;
    private IXdmItem m_aFirst;
    private IXdmItem [] m_aBuf;
    private int m_nSize = 0;
    // Whether m_aBuf is owned by a built sequence and must not be written
    private boolean m_bShared = false;

    /** A builder whose buffer starts with room for 8 items. */
    public Builder ()
    {
      this (8);
    }

    /**
     * @param nInitialCapacity
     *        The number of items the buffer has room for when the second item
     *        is added; at least 2 are used.
     */
    public Builder (@Nonnegative final int nInitialCapacity)
    {
      m_nInitialCapacity = Math.max (2, nInitialCapacity);
    }

    /**
     * Make room for more items at once: the buffer gets at least the room for
     * the current and the additional items (growing to at least double its
     * size, so that repeated calls stay linear), and is no longer shared with a
     * built sequence. Nothing happens while the items fit into the first-item
     * field.
     */
    private void _reserve (@Nonnegative final int nAdditional)
    {
      final int nTotal = m_nSize + nAdditional;
      if (nTotal <= 1)
        return;
      if (m_aBuf == null)
      {
        m_aBuf = new IXdmItem [Math.max (m_nInitialCapacity, nTotal)];
        if (m_nSize == 1)
          m_aBuf[0] = m_aFirst;
        m_aFirst = null;
      }
      else
        if (m_bShared || nTotal > m_aBuf.length)
        {
          m_aBuf = Arrays.copyOf (m_aBuf, nTotal > m_aBuf.length ? Math.max (m_aBuf.length * 2, nTotal) : m_aBuf.length);
          m_bShared = false;
        }
    }

    /**
     * Append one item, allocating the buffer for the second item and doubling
     * it when it is full. Unlike {@link XdmSequence#of(IXdmItem)} the item is
     * not checked for <code>null</code>.
     *
     * @param aItem
     *        The item to append.
     * @return this for chaining
     */
    @NonNull
    public Builder add (@NonNull final IXdmItem aItem)
    {
      if (m_aBuf == null)
      {
        if (m_nSize == 0)
        {
          m_aFirst = aItem;
          m_nSize = 1;
          return this;
        }
        m_aBuf = new IXdmItem [m_nInitialCapacity];
        m_aBuf[0] = m_aFirst;
        m_aFirst = null;
      }
      else
        if (m_nSize == m_aBuf.length || m_bShared)
        {
          m_aBuf = Arrays.copyOf (m_aBuf, m_nSize == m_aBuf.length ? m_nSize * 2 : m_aBuf.length);
          m_bShared = false;
        }
      m_aBuf[m_nSize++] = aItem;
      return this;
    }

    /**
     * Append all items of a sequence, in order: into an empty builder by
     * sharing the item array of the sequence until the next change, otherwise
     * copied at once.
     *
     * @param aSeq
     *        The sequence whose items are appended.
     * @return this for chaining
     */
    @NonNull
    public Builder addAll (@NonNull final XdmSequence aSeq)
    {
      if (aSeq.m_aItems == null)
        return add (aSeq.m_aSingle);
      final int nCount = aSeq.m_aItems.length;
      if (nCount > 1 && m_nSize == 0)
      {
        // The array of a sequence is never written: share it until the next
        // change
        m_aFirst = null;
        m_aBuf = aSeq.m_aItems;
        m_nSize = nCount;
        m_bShared = true;
      }
      else
        if (nCount > 1)
        {
          _reserve (nCount);
          System.arraycopy (aSeq.m_aItems, 0, m_aBuf, m_nSize, nCount);
          m_nSize += nCount;
        }
        else
          if (nCount == 1)
            add (aSeq.m_aItems[0]);
      return this;
    }

    @Nonnegative
    public int size ()
    {
      return m_nSize;
    }

    /**
     * @return {@link XdmSequence#EMPTY} if no item was added, the single-item
     *         sequence for one item, otherwise a sequence over the buffer
     *         (taken over if it is exactly full, else a trimmed copy). The
     *         builder stays usable and later additions do not affect the
     *         result.
     */
    @NonNull
    public XdmSequence build ()
    {
      if (m_nSize == 0)
        return EMPTY;
      if (m_nSize == 1)
        return new XdmSequence (m_aBuf == null ? m_aFirst : m_aBuf[0]);
      if (m_nSize == m_aBuf.length)
      {
        m_bShared = true;
        return new XdmSequence (m_aBuf);
      }
      return new XdmSequence (Arrays.copyOf (m_aBuf, m_nSize));
    }
  }

  /**
   * A list that collects items for one sequence, for code that fills a
   * {@link List} (such as the axis navigation): the same buffer handling as
   * {@link Builder}, with the list operations <code>add</code>,
   * <code>addAll</code>, <code>get</code> and <code>size</code>. Items are
   * not checked for <code>null</code>.
   *
   * @param <T>
   *        The item type the filling code works with.
   */
  public static final class ListBuilder <T extends IXdmItem> extends AbstractList <T> implements RandomAccess
  {
    private final Builder m_aBuilder = new Builder ();

    @Override
    public boolean add (@NonNull final T aItem)
    {
      m_aBuilder.add (aItem);
      return true;
    }

    /**
     * Append all items of a collection in its iteration order. Into an empty
     * list of more than one item they are copied at once with
     * {@link Collection#toArray(Object[])}, and that array becomes the buffer.
     */
    @Override
    public boolean addAll (@NonNull final Collection <? extends T> aItems)
    {
      final int nCount = aItems.size ();
      if (nCount == 0)
        return false;
      final Builder aBuilder = m_aBuilder;
      if (aBuilder.m_nSize == 0 && nCount > 1)
      {
        final IXdmItem [] aArray = aItems.toArray (new IXdmItem [nCount]);
        aBuilder.m_aFirst = null;
        aBuilder.m_aBuf = aArray;
        aBuilder.m_nSize = aArray.length;
        aBuilder.m_bShared = false;
        return true;
      }
      aBuilder._reserve (nCount);
      for (final T aItem : aItems)
        aBuilder.add (aItem);
      return true;
    }

    @Override
    @SuppressWarnings ("unchecked")
    public T get (@Nonnegative final int nIndex)
    {
      if (nIndex < 0 || nIndex >= m_aBuilder.m_nSize)
        throw new IndexOutOfBoundsException (nIndex);
      return (T) (m_aBuilder.m_aBuf == null ? m_aBuilder.m_aFirst : m_aBuilder.m_aBuf[nIndex]);
    }

    @Override
    public int size ()
    {
      return m_aBuilder.m_nSize;
    }

    /**
     * @return The sequence of the collected items, as
     *         {@link Builder#build()} gives it.
     */
    @NonNull
    public XdmSequence build ()
    {
      return m_aBuilder.build ();
    }
  }
}
