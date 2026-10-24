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

import java.util.Collection;
import java.util.Collections;
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
 * Modifying methods return a new map (copy on write; a persistent structure
 * can replace this later without changing the API). Use {@link Builder} to
 * construct a map from many entries in linear time.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmMap implements IXdmItem
{
  /** A map entry. */
  public record Entry (@NonNull XdmAtomic key, @NonNull XdmSequence value)
  {}

  public static final XdmMap EMPTY = new XdmMap (new LinkedHashMap <> ());

  private final Map <Object, Entry> m_aEntries;

  private XdmMap (@NonNull final Map <Object, Entry> aEntries)
  {
    m_aEntries = aEntries;
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
    return m_aEntries.size ();
  }

  public boolean isEmpty ()
  {
    return m_aEntries.isEmpty ();
  }

  public boolean containsKey (@NonNull final XdmAtomic aKey)
  {
    return m_aEntries.containsKey (AtomicComparator.getMapKey (aKey));
  }

  /**
   * @param aKey
   *        The key to look up.
   * @return <code>null</code> if the key is absent.
   */
  @Nullable
  public XdmSequence get (@NonNull final XdmAtomic aKey)
  {
    final Entry aEntry = m_aEntries.get (AtomicComparator.getMapKey (aKey));
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
    return m_aEntries.get (AtomicComparator.getMapKey (aKey));
  }

  /**
   * @param aKey
   *        The key.
   * @param aValue
   *        The value.
   * @return A new map with the entry added or replaced.
   */
  @NonNull
  public XdmMap put (@NonNull final XdmAtomic aKey, @NonNull final XdmSequence aValue)
  {
    final Map <Object, Entry> aNew = new LinkedHashMap <> (m_aEntries);
    final Object aMapKey = AtomicComparator.getMapKey (aKey);
    // The entry keeps its position but takes the new key (and its type)
    aNew.put (aMapKey, new Entry (aKey, aValue));
    return new XdmMap (aNew);
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
    if (!m_aEntries.containsKey (aMapKey))
      return this;
    final Map <Object, Entry> aNew = new LinkedHashMap <> (m_aEntries);
    aNew.remove (aMapKey);
    return new XdmMap (aNew);
  }

  /**
   * @param aKeys
   *        The keys to remove.
   * @return A new map without any of the keys.
   */
  @NonNull
  public XdmMap removeAll (@NonNull final Iterable <? extends IXdmItem> aKeys)
  {
    Map <Object, Entry> aNew = null;
    for (final IXdmItem aKey : aKeys)
    {
      final Object aMapKey = AtomicComparator.getMapKey ((XdmAtomic) aKey);
      if ((aNew == null ? m_aEntries : aNew).containsKey (aMapKey))
      {
        if (aNew == null)
          aNew = new LinkedHashMap <> (m_aEntries);
        aNew.remove (aMapKey);
      }
    }
    return aNew == null ? this : new XdmMap (aNew);
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Entry> getAllEntries ()
  {
    final Collection <Entry> aValues = m_aEntries.values ();
    return new CommonsArrayList <> (aValues);
  }

  /**
   * @return A read-only view of the entries in iteration order, without
   *         copying.
   */
  @NonNull
  public Collection <Entry> entries ()
  {
    return Collections.unmodifiableCollection (m_aEntries.values ());
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XdmAtomic> getAllKeys ()
  {
    final ICommonsList <XdmAtomic> ret = new CommonsArrayList <> (m_aEntries.size ());
    for (final Entry aEntry : m_aEntries.values ())
      ret.add (aEntry.key ());
    return ret;
  }

  @Override
  public String toString ()
  {
    return "map(" + m_aEntries.size () + ")";
  }

  /**
   * Builds a map from many entries in linear time. A builder must not be used
   * after {@link #build()}.
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
      m_aMap = new LinkedHashMap <> (aBase.m_aEntries);
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
      return aMap.isEmpty () ? EMPTY : new XdmMap (aMap);
    }
  }
}
