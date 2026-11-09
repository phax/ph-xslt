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
package com.helger.phoxtrot.xdm;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.base.enforce.ValueEnforcer;

/**
 * A text node. While a tree is built, text can be appended in pieces with
 * {@link #appendValue(String)}; the pieces are joined once, when the value is
 * read, so building a long text from many pieces stays linear. The pieces are
 * collected in a {@link StringBuilder} that takes the place of the value in
 * the value field until they are joined, so a text node needs no field of its
 * own for them.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmTextNode extends AbstractXdmValueNode
{
  // Guards joining the pending text of all text nodes (one lock per node would
  // cost memory on every node)
  private static final SimpleReadWriteLock PENDING_TEXT_LOCK = new SimpleReadWriteLock ();

  XdmTextNode (@NonNull final String sValue)
  {
    super (sValue);
  }

  /**
   * Replace a {@link StringBuilder} in the value field by its text, under the
   * lock shared by all text nodes. Readers of a finished tree may call this
   * concurrently: the field is read once outside the lock, and a
   * {@link String} seen there is safe to use without the lock (its fields are
   * final); a builder is only read under the lock, where the first reader
   * replaces it and every later one sees the string.
   *
   * @return The complete value.
   */
  @NonNull
  private String _joinPendingText ()
  {
    final Object aValue = internalGetRawValue ();
    if (aValue instanceof final String sValue)
      return sValue;
    return PENDING_TEXT_LOCK.writeLockedGet ( () -> {
      final Object aCur = internalGetRawValue ();
      if (aCur instanceof final String sCur)
        return sCur;
      final String ret = aCur.toString ();
      internalSetRawValue (ret);
      return ret;
    });
  }

  /**
   * Append text to the value, for building a tree only: the call must not
   * overlap with reading the value from another thread. The first append
   * puts a {@link StringBuilder} with the value and the text into the value
   * field, later ones append to it.
   *
   * @param sText
   *        The text to append. May not be <code>null</code>.
   */
  public void appendValue (@NonNull final String sText)
  {
    ValueEnforcer.notNull (sText, "Text");
    if (sText.isEmpty ())
      return;
    final Object aValue = internalGetRawValue ();
    if (aValue instanceof final StringBuilder aPending)
      aPending.append (sText);
    else
    {
      internalSetRawValue (new StringBuilder ((String) aValue).append (sText));
    }
  }

  /**
   * Joins pending appended text into the value first, so the result is the
   * complete text.
   */
  @Override
  @NonNull
  public String getValue ()
  {
    return _joinPendingText ();
  }

  /**
   * Replace the value; text appended but not yet joined is discarded.
   */
  @Override
  public void setValue (@NonNull final String sValue)
  {
    ValueEnforcer.notNull (sValue, "Value");
    PENDING_TEXT_LOCK.writeLocked ( () -> super.setValue (sValue));
  }

  @NonNull
  public ENodeKind getKind ()
  {
    return ENodeKind.TEXT;
  }

  /**
   * A text node has no location fields: with a parent it reports the line of
   * the parent (the element that contains the text), without a parent the
   * line kept in the details holder ({@link #setLocation(int, int)}), 0 if
   * none was set.
   */
  @Override
  @Nonnegative
  public int getLine ()
  {
    final AbstractXdmNode aParent = getParent ();
    return aParent != null ? aParent.getLine () : super.getLine ();
  }

  /**
   * Like {@link #getLine()}: the column of the parent, or without a parent
   * the column kept in the details holder.
   */
  @Override
  @Nonnegative
  public int getColumn ()
  {
    final AbstractXdmNode aParent = getParent ();
    return aParent != null ? aParent.getColumn () : super.getColumn ();
  }

}
