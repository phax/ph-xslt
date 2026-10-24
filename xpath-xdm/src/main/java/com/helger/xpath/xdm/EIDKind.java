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
package com.helger.xpath.xdm;

/**
 * The DTD attribute type of an attribute node, as far as it matters for
 * <code>fn:id</code>, <code>fn:idref</code> and the XDM <code>is-id</code> /
 * <code>is-idrefs</code> accessors.
 *
 * @author Philip Helger
 */
public enum EIDKind
{
  NONE,
  ID,
  IDREF,
  IDREFS
}
