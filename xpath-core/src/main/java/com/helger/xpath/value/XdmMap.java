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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.ops.AtomicComparator;
import com.helger.xpath.xdm.IXdmItem;

/**
 * An immutable XPath 3.1 map. Keys are atomic values compared with the
 * op:same-key rule (see {@link AtomicComparator#getMapKey(XdmAtomic)}); the
 * original key item is kept for <code>map:keys</code>. Iteration order is
 * insertion order, which is the implementation-defined order XPath allows.
 * <p>
 * A map is a persistent hash trie: {@link #put(XdmAtomic, XdmSequence)} and
 * {@link #remove(XdmAtomic)} share everything but the path to the changed entry
 * with the map they were called on, so accumulating n entries one
 * <code>map:put</code> at a time does not copy the map n times. Every entry
 * carries a running number that defines the iteration order; the ordered view
 * is built on first use. Use {@link Builder} to construct a map from many
 * entries.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmMap implements IXdmItem
{
  /** A map entry. */
  public record Entry (@NonNull XdmAtomic key, @NonNull XdmSequence value)
  {}

  /** An entry as stored in the trie. Never changed after it was made. */
  private static final class Slot
  {
    private final Object m_aMapKey;
    private final int m_nHash;
    // Position in the iteration order; a replaced entry keeps it
    private final long m_nOrder;
    private final Entry m_aEntry;

    private Slot (@NonNull final Object aMapKey, final int nHash, final long nOrder, @NonNull final Entry aEntry)
    {
      m_aMapKey = aMapKey;
      m_nHash = nHash;
      m_nOrder = nOrder;
      m_aEntry = aEntry;
    }
  }

  /**
   * A trie node, never changed after it was made. With a non-zero bitmap it is
   * a 32-way branch over five bits of the hash per level, where each kid is a
   * {@link Slot} or a sub {@link Node}. A zero bitmap marks a collision list:
   * {@link Slot}s that share their path, compared by key. Depth is limited: seven
   * levels exhaust the 32-bit hash and a collision list ends a path, so the
   * recursions below are bounded.
   */
  private static final class Node
  {
    private final int m_nBitmap;
    private final Object [] m_aKids;

    private Node (final int nBitmap, @NonNull final Object [] aKids)
    {
      m_nBitmap = nBitmap;
      m_aKids = aKids;
    }
  }

  public static final XdmMap EMPTY = new XdmMap (null, 0, 0, null, null, new Entry [0]);

  private final Node m_aRoot;
  private final int m_nSize;
  private final long m_nNextOrder;
  // The type of all keys / of all values, each a single atomic item. Null means
  // only that it is unknown: replacing and removing entries leaves a type that
  // no entry has any more in place, which is sound, but never restores one
  private final EAtomicType m_eKeyType;
  private final EAtomicType m_eValueType;
  // The entries in iteration order, built on first use
  private volatile Entry [] m_aOrdered;

  private XdmMap (@Nullable final Node aRoot,
                  @Nonnegative final int nSize,
                  final long nNextOrder,
                  @Nullable final EAtomicType eKeyType,
                  @Nullable final EAtomicType eValueType,
                  final Entry @Nullable [] aOrdered)
  {
    m_aRoot = aRoot;
    m_nSize = nSize;
    m_nNextOrder = nNextOrder;
    m_eKeyType = eKeyType;
    m_eValueType = eValueType;
    m_aOrdered = aOrdered;
  }

  @Nullable
  private static Slot _find (@Nullable final Node aRoot, final int nHash, @NonNull final Object aMapKey)
  {
    Node aNode = aRoot;
    int nShift = 0;
    while (aNode != null)
    {
      if (aNode.m_nBitmap == 0)
      {
        for (final Object aKid : aNode.m_aKids)
          if (((Slot) aKid).m_aMapKey.equals (aMapKey))
            return (Slot) aKid;
        return null;
      }
      final int nBit = 1 << ((nHash >>> nShift) & 31);
      if ((aNode.m_nBitmap & nBit) == 0)
        return null;
      final Object aKid = aNode.m_aKids[Integer.bitCount (aNode.m_nBitmap & (nBit - 1))];
      if (aKid instanceof final Slot aSlot)
        return aSlot.m_nHash == nHash && aSlot.m_aMapKey.equals (aMapKey) ? aSlot : null;
      aNode = (Node) aKid;
      nShift += 5;
    }
    return null;
  }

  @NonNull
  private static Object [] _insertAt (@NonNull final Object [] aKids, final int nIndex, @NonNull final Object aKid)
  {
    final Object [] ret = new Object [aKids.length + 1];
    System.arraycopy (aKids, 0, ret, 0, nIndex);
    ret[nIndex] = aKid;
    System.arraycopy (aKids, nIndex, ret, nIndex + 1, aKids.length - nIndex);
    return ret;
  }

  @NonNull
  private static Object [] _removeAt (@NonNull final Object [] aKids, final int nIndex)
  {
    final Object [] ret = new Object [aKids.length - 1];
    System.arraycopy (aKids, 0, ret, 0, nIndex);
    System.arraycopy (aKids, nIndex + 1, ret, nIndex, ret.length - nIndex);
    return ret;
  }

  @NonNull
  private static Object [] _replaceAt (@NonNull final Object [] aKids, final int nIndex, @NonNull final Object aKid)
  {
    final Object [] ret = aKids.clone ();
    ret[nIndex] = aKid;
    return ret;
  }

  /** @return The subtree holding two slots whose hashes differ. */
  @NonNull
  private static Node _split (final int nShift, @NonNull final Slot aFirst, @NonNull final Slot aSecond)
  {
    final int nFirst = (aFirst.m_nHash >>> nShift) & 31;
    final int nSecond = (aSecond.m_nHash >>> nShift) & 31;
    if (nFirst == nSecond)
      return new Node (1 << nFirst, new Object [] { _split (nShift + 5, aFirst, aSecond) });
    return new Node (1 << nFirst | 1 << nSecond,
                     nFirst < nSecond ? new Object [] { aFirst, aSecond } : new Object [] { aSecond, aFirst });
  }

  /** @return The node with the slot in place of any slot of the same key. */
  @NonNull
  private static Node _assoc (@Nullable final Node aNode, final int nShift, @NonNull final Slot aNew)
  {
    if (aNode == null)
      return new Node (1 << ((aNew.m_nHash >>> nShift) & 31), new Object [] { aNew });
    final Object [] aKids = aNode.m_aKids;
    if (aNode.m_nBitmap == 0)
    {
      for (int i = 0; i < aKids.length; ++i)
        if (((Slot) aKids[i]).m_aMapKey.equals (aNew.m_aMapKey))
          return new Node (0, _replaceAt (aKids, i, aNew));
      return new Node (0, _insertAt (aKids, aKids.length, aNew));
    }
    final int nBit = 1 << ((aNew.m_nHash >>> nShift) & 31);
    final int nIndex = Integer.bitCount (aNode.m_nBitmap & (nBit - 1));
    if ((aNode.m_nBitmap & nBit) == 0)
      return new Node (aNode.m_nBitmap | nBit, _insertAt (aKids, nIndex, aNew));
    final Object aKid = aKids[nIndex];
    final Node aSub;
    if (aKid instanceof final Slot aOld)
    {
      if (aOld.m_aMapKey.equals (aNew.m_aMapKey))
        return new Node (aNode.m_nBitmap, _replaceAt (aKids, nIndex, aNew));
      aSub = aOld.m_nHash == aNew.m_nHash ? new Node (0, new Object [] { aOld, aNew })
                                          : _split (nShift + 5, aOld, aNew);
    }
    else
      aSub = _assoc ((Node) aKid, nShift + 5, aNew);
    return new Node (aNode.m_nBitmap, _replaceAt (aKids, nIndex, aSub));
  }

  /**
   * @return The node without the key, which must be present; <code>null</code>
   *         if nothing is left of it.
   */
  @Nullable
  private static Node _dissoc (@NonNull final Node aNode,
                               final int nShift,
                               final int nHash,
                               @NonNull final Object aMapKey)
  {
    final Object [] aKids = aNode.m_aKids;
    if (aNode.m_nBitmap == 0)
    {
      int nIndex = 0;
      while (!((Slot) aKids[nIndex]).m_aMapKey.equals (aMapKey))
        nIndex++;
      return aKids.length == 1 ? null : new Node (0, _removeAt (aKids, nIndex));
    }
    final int nBit = 1 << ((nHash >>> nShift) & 31);
    final int nIndex = Integer.bitCount (aNode.m_nBitmap & (nBit - 1));
    if (aKids[nIndex] instanceof final Node aSubNode)
    {
      final Node aSub = _dissoc (aSubNode, nShift + 5, nHash, aMapKey);
      if (aSub != null)
        return new Node (aNode.m_nBitmap, _replaceAt (aKids, nIndex, aSub));
    }
    return aKids.length == 1 ? null : new Node (aNode.m_nBitmap & ~nBit, _removeAt (aKids, nIndex));
  }

  private static int _collect (@NonNull final Node aNode, @NonNull final Slot [] aTarget, final int nStart)
  {
    int ret = nStart;
    for (final Object aKid : aNode.m_aKids)
      if (aKid instanceof final Slot aSlot)
        aTarget[ret++] = aSlot;
      else
        ret = _collect ((Node) aKid, aTarget, ret);
    return ret;
  }

  private Entry @NonNull [] _getOrdered ()
  {
    Entry [] ret = m_aOrdered;
    if (ret == null)
    {
      // Racing threads compute the same content
      final Slot [] aSlots = new Slot [m_nSize];
      _collect (m_aRoot, aSlots, 0);
      Arrays.sort (aSlots, Comparator.comparingLong (x -> x.m_nOrder));
      ret = new Entry [m_nSize];
      for (int i = 0; i < m_nSize; ++i)
        ret[i] = aSlots[i].m_aEntry;
      m_aOrdered = ret;
    }
    return ret;
  }

  @Nullable
  private static EAtomicType _getSingleAtomicType (@NonNull final XdmSequence aValue)
  {
    return aValue.isSingleton () && aValue.get (0) instanceof final XdmAtomic aAtomic ? aAtomic.getType () : null;
  }

  /**
   * @param aKey
   *        The key.
   * @param aValue
   *        The value.
   * @return A map with a single entry (<code>map:entry</code>).
   */
  @NonNull
  public static XdmMap of (@NonNull final XdmAtomic aKey, @NonNull final XdmSequence aValue)
  {
    return new Builder (1).put (aKey, aValue).build ();
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

  /**
   * @return The atomic type of all keys, or <code>null</code> if it is unknown.
   *         A cheap way to test a <code>map(K, V)</code> type without looking
   *         at every entry.
   */
  @Nullable
  public EAtomicType getUniformKeyType ()
  {
    return m_eKeyType;
  }

  /**
   * @return The atomic type of all values, if each is a single atomic item, or
   *         <code>null</code> if it is unknown.
   * @see #getUniformKeyType()
   */
  @Nullable
  public EAtomicType getUniformValueType ()
  {
    return m_eValueType;
  }

  public boolean containsKey (@NonNull final XdmAtomic aKey)
  {
    return getEntry (aKey) != null;
  }

  /**
   * @param aKey
   *        The key to look up.
   * @return <code>null</code> if the key is absent.
   */
  @Nullable
  public XdmSequence get (@NonNull final XdmAtomic aKey)
  {
    final Entry aEntry = getEntry (aKey);
    return aEntry == null ? null : aEntry.value ();
  }

  /**
   * @param aKey
   *        The key to look up.
   * @return The entry with the key as it was stored, or <code>null</code> if
   *         the key is absent.
   */
  @Nullable
  public Entry getEntry (@NonNull final XdmAtomic aKey)
  {
    final Object aMapKey = AtomicComparator.getMapKey (aKey);
    final Slot aSlot = _find (m_aRoot, aMapKey.hashCode (), aMapKey);
    return aSlot == null ? null : aSlot.m_aEntry;
  }

  /**
   * @param aKey
   *        The key.
   * @param aValue
   *        The value.
   * @return A new map with the entry added or replaced. A replaced entry keeps
   *         its position but takes the new key (and its type).
   */
  @NonNull
  public XdmMap put (@NonNull final XdmAtomic aKey, @NonNull final XdmSequence aValue)
  {
    final Object aMapKey = AtomicComparator.getMapKey (aKey);
    final int nHash = aMapKey.hashCode ();
    final Slot aOld = _find (m_aRoot, nHash, aMapKey);
    final long nOrder = aOld == null ? m_nNextOrder : aOld.m_nOrder;
    final Node aRoot = _assoc (m_aRoot, 0, new Slot (aMapKey, nHash, nOrder, new Entry (aKey, aValue)));
    final EAtomicType eKeyType = aKey.getType ();
    final EAtomicType eValueType = _getSingleAtomicType (aValue);
    final boolean bFirst = m_nSize == 0;
    return new XdmMap (aRoot,
                       aOld == null ? m_nSize + 1 : m_nSize,
                       aOld == null ? m_nNextOrder + 1 : m_nNextOrder,
                       bFirst || eKeyType == m_eKeyType ? eKeyType : null,
                       bFirst || eValueType == m_eValueType ? eValueType : null,
                       null);
  }

  /**
   * @param aKey
   *        The key to remove.
   * @return A new map without the key.
   */
  @NonNull
  public XdmMap remove (@NonNull final XdmAtomic aKey)
  {
    final Object aMapKey = AtomicComparator.getMapKey (aKey);
    final int nHash = aMapKey.hashCode ();
    if (_find (m_aRoot, nHash, aMapKey) == null)
      return this;
    if (m_nSize == 1)
      return EMPTY;
    return new XdmMap (_dissoc (m_aRoot, 0, nHash, aMapKey),
                       m_nSize - 1,
                       m_nNextOrder,
                       m_eKeyType,
                       m_eValueType,
                       null);
  }

  /**
   * @param aKeys
   *        The keys to remove.
   * @return A new map without any of the keys.
   */
  @NonNull
  public XdmMap removeAll (@NonNull final Iterable <? extends IXdmItem> aKeys)
  {
    XdmMap ret = this;
    for (final IXdmItem aKey : aKeys)
      ret = ret.remove ((XdmAtomic) aKey);
    return ret;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Entry> getAllEntries ()
  {
    return new CommonsArrayList <> (_getOrdered ());
  }

  /**
   * @return A read-only view of the entries in iteration order, without
   *         copying.
   */
  @NonNull
  public Collection <Entry> entries ()
  {
    return Collections.unmodifiableList (Arrays.asList (_getOrdered ()));
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XdmAtomic> getAllKeys ()
  {
    final Entry [] aEntries = _getOrdered ();
    final ICommonsList <XdmAtomic> ret = new CommonsArrayList <> (aEntries.length);
    for (final Entry aEntry : aEntries)
      ret.add (aEntry.key ());
    return ret;
  }

  @Override
  public String toString ()
  {
    return "map(" + m_nSize + ")";
  }

  /**
   * Collects the entries of a map without building a new map per entry. A
   * builder must not be used after {@link #build()}.
   */
  @NotThreadSafe
  public static final class Builder
  {
    private Map <Object, Entry> m_aMap;

    public Builder ()
    {
      m_aMap = new LinkedHashMap <> ();
    }

    public Builder (@Nonnegative final int nExpectedSize)
    {
      m_aMap = LinkedHashMap.newLinkedHashMap (nExpectedSize);
    }

    /**
     * @param aBase
     *        A map whose entries the builder starts with.
     */
    public Builder (@NonNull final XdmMap aBase)
    {
      ValueEnforcer.notNull (aBase, "Base");
      m_aMap = LinkedHashMap.newLinkedHashMap (aBase.size ());
      for (final Entry aEntry : aBase._getOrdered ())
        m_aMap.put (AtomicComparator.getMapKey (aEntry.key ()), aEntry);
    }

    @Nonnegative
    public int size ()
    {
      return m_aMap.size ();
    }

    public boolean containsKey (@NonNull final XdmAtomic aKey)
    {
      return m_aMap.containsKey (AtomicComparator.getMapKey (aKey));
    }

    /**
     * @param aKey
     *        The key to look up.
     * @return <code>null</code> if the key is absent.
     */
    @Nullable
    public XdmSequence get (@NonNull final XdmAtomic aKey)
    {
      final Entry aEntry = m_aMap.get (AtomicComparator.getMapKey (aKey));
      return aEntry == null ? null : aEntry.value ();
    }

    /**
     * Add an entry or replace an existing one (key and value). The position
     * of a replaced entry is kept.
     *
     * @param aKey
     *        The key.
     * @param aValue
     *        The value.
     * @return this for chaining
     */
    @NonNull
    public Builder put (@NonNull final XdmAtomic aKey, @NonNull final XdmSequence aValue)
    {
      ValueEnforcer.notNull (aKey, "Key");
      ValueEnforcer.notNull (aValue, "Value");
      m_aMap.put (AtomicComparator.getMapKey (aKey), new Entry (aKey, aValue));
      return this;
    }

    /**
     * Add an entry only if the key is absent.
     *
     * @param aKey
     *        The key.
     * @param aValue
     *        The value.
     * @return <code>true</code> if it was added, <code>false</code> if the key
     *         was already present.
     */
    public boolean putIfAbsent (@NonNull final XdmAtomic aKey, @NonNull final XdmSequence aValue)
    {
      ValueEnforcer.notNull (aKey, "Key");
      ValueEnforcer.notNull (aValue, "Value");
      return m_aMap.putIfAbsent (AtomicComparator.getMapKey (aKey), new Entry (aKey, aValue)) == null;
    }

    @NonNull
    public Builder remove (@NonNull final XdmAtomic aKey)
    {
      m_aMap.remove (AtomicComparator.getMapKey (aKey));
      return this;
    }

    @NonNull
    public XdmMap build ()
    {
      final Map <Object, Entry> aMap = m_aMap;
      m_aMap = null;
      if (aMap.isEmpty ())
        return EMPTY;
      final Entry [] aOrdered = new Entry [aMap.size ()];
      Node aRoot = null;
      EAtomicType eKeyType = null;
      EAtomicType eValueType = null;
      int nOrder = 0;
      for (final Map.Entry <Object, Entry> aItem : aMap.entrySet ())
      {
        final Entry aEntry = aItem.getValue ();
        final EAtomicType eEntryKeyType = aEntry.key ().getType ();
        final EAtomicType eEntryValueType = _getSingleAtomicType (aEntry.value ());
        eKeyType = nOrder == 0 || eEntryKeyType == eKeyType ? eEntryKeyType : null;
        eValueType = nOrder == 0 || eEntryValueType == eValueType ? eEntryValueType : null;
        aRoot = _assoc (aRoot, 0, new Slot (aItem.getKey (), aItem.getKey ().hashCode (), nOrder, aEntry));
        aOrdered[nOrder++] = aEntry;
      }
      return new XdmMap (aRoot, nOrder, nOrder, eKeyType, eValueType, aOrdered);
    }
  }
}
