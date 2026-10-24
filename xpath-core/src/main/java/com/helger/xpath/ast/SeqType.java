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
package com.helger.xpath.ast;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A SequenceType: an item type with an occurrence indicator, or
 * <code>empty-sequence()</code>.
 *
 * @param empty
 *        <code>true</code> for empty-sequence(); then item is null.
 * @param itemType
 *        The item type, null for empty-sequence().
 * @param occurrence
 *        The occurrence indicator.
 * @param listCast
 *        Marks the built-in list types xs:IDREFS, xs:NMTOKENS, xs:ENTITIES as
 *        a cast target: itemType is then the item type of the list.
 * @author Philip Helger
 */
public record SeqType (boolean empty, @Nullable ItemType itemType, @NonNull EOccurrence occurrence, boolean listCast)
{
  /** <code>item()*</code> */
  public static final SeqType ANY = new SeqType (false, ItemType.ITEM, EOccurrence.ZERO_OR_MORE, false);
  public static final SeqType EMPTY_SEQUENCE = new SeqType (true, null, EOccurrence.ZERO_OR_ONE, false);

  @NonNull
  public static SeqType of (@NonNull final ItemType aItemType, @NonNull final EOccurrence eOccurrence)
  {
    return new SeqType (false, aItemType, eOccurrence, false);
  }
}
