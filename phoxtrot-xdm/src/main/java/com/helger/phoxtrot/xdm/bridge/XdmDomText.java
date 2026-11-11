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
import org.w3c.dom.Text;

import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * A text node of an {@link XdmDomView}. XDM merges adjacent text, so a text node is always its whole text.
 *
 * @author Philip Helger
 */
final class XdmDomText extends AbstractXdmDomCharacterData implements Text
{
  XdmDomText (@NonNull final XdmDomView aView, @NonNull final IXdmNode aNode)
  {
    super (aView, aNode);
  }

  public String getNodeName ()
  {
    return "#text";
  }

  public short getNodeType ()
  {
    return TEXT_NODE;
  }

  public Text splitText (final int nOffset)
  {
    throw createReadOnly ();
  }

  public boolean isElementContentWhitespace ()
  {
    return false;
  }

  public String getWholeText ()
  {
    return getData ();
  }

  public Text replaceWholeText (final String sContent)
  {
    throw createReadOnly ();
  }
}
