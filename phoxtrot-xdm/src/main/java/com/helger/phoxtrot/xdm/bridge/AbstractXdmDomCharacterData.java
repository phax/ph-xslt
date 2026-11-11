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
package com.helger.phoxtrot.xdm.bridge;

import org.jspecify.annotations.NonNull;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;

import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * The text and comment nodes of an {@link XdmDomView}.
 *
 * @author Philip Helger
 */
abstract sealed class AbstractXdmDomCharacterData extends AbstractXdmDomNode implements CharacterData permits
                                                  XdmDomText, XdmDomComment
{
  AbstractXdmDomCharacterData (@NonNull final XdmDomView aView, @NonNull final IXdmNode aNode)
  {
    super (aView, aNode);
  }

  @Override
  public final String getNodeValue ()
  {
    return getXdmNode ().getValue ();
  }

  public final String getData ()
  {
    return getXdmNode ().getValue ();
  }

  public final void setData (final String sData)
  {
    throw createReadOnly ();
  }

  public final int getLength ()
  {
    return getData ().length ();
  }

  public final String substringData (final int nOffset, final int nCount)
  {
    final String sData = getData ();
    if (nOffset < 0 || nOffset > sData.length () || nCount < 0)
      throw new DOMException (DOMException.INDEX_SIZE_ERR,
                              "Offset " + nOffset + " and count " + nCount + " do not fit a length of " + sData.length ());
    return sData.substring (nOffset, (int) Math.min (sData.length (), (long) nOffset + nCount));
  }

  public final void appendData (final String sArg)
  {
    throw createReadOnly ();
  }

  public final void insertData (final int nOffset, final String sArg)
  {
    throw createReadOnly ();
  }

  public final void deleteData (final int nOffset, final int nCount)
  {
    throw createReadOnly ();
  }

  public final void replaceData (final int nOffset, final int nCount, final String sArg)
  {
    throw createReadOnly ();
  }
}
