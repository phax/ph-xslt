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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.jspecify.annotations.NonNull;

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
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmSequence implements Iterable <IXdmItem>
{
  public static final XdmSequence EMPTY = new XdmSequence (new IXdmItem [0]);

  private final IXdmItem [] m_aItems;

  private XdmSequence (@NonNull final IXdmItem [] aItems)
  {
    m_aItems = aItems;
  }

  @NonNull
  public static XdmSequence of (@NonNull final IXdmItem aItem)
  {
    ValueEnforcer.notNull (aItem, "Item");
    return new XdmSequence (new IXdmItem [] { aItem });
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
    return aItems.length == 0 ? EMPTY : new XdmSequence (aItems.clone ());
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
    return aItems.isEmpty () ? EMPTY : new XdmSequence (aItems.toArray (new IXdmItem [0]));
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
    {
      System.arraycopy (aPart.m_aItems, 0, aRet, nPos, aPart.m_aItems.length);
      nPos += aPart.m_aItems.length;
    }
    return new XdmSequence (aRet);
  }

  @Nonnegative
  public int size ()
  {
    return m_aItems.length;
  }

  public boolean isEmpty ()
  {
    return m_aItems.length == 0;
  }

  public boolean isSingleton ()
  {
    return m_aItems.length == 1;
  }

  @NonNull
  public IXdmItem get (@Nonnegative final int nIndex)
  {
    return m_aItems[nIndex];
  }

  /**
   * @return The first item.
   * @throws NoSuchElementException
   *         if the sequence is empty.
   */
  @NonNull
  public IXdmItem getFirst ()
  {
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
    if (nFrom == 0 && nTo == m_aItems.length)
      return this;
    return new XdmSequence (Arrays.copyOfRange (m_aItems, nFrom, nTo));
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IXdmItem> getAsList ()
  {
    return new CommonsArrayList <> (m_aItems);
  }

  @Override
  public Iterator <IXdmItem> iterator ()
  {
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
   * call.
   */
  public static final class Builder
  {
    private IXdmItem [] m_aBuf = new IXdmItem [8];
    private int m_nSize = 0;

    @NonNull
    public Builder add (@NonNull final IXdmItem aItem)
    {
      if (m_nSize == m_aBuf.length)
        m_aBuf = Arrays.copyOf (m_aBuf, m_nSize * 2);
      m_aBuf[m_nSize++] = aItem;
      return this;
    }

    @NonNull
    public Builder addAll (@NonNull final XdmSequence aSeq)
    {
      for (final IXdmItem aItem : aSeq.m_aItems)
        add (aItem);
      return this;
    }

    @Nonnegative
    public int size ()
    {
      return m_nSize;
    }

    @NonNull
    public XdmSequence build ()
    {
      return m_nSize == 0 ? EMPTY : new XdmSequence (Arrays.copyOf (m_aBuf, m_nSize));
    }
  }
}
