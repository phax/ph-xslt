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
package com.helger.phoxtrot.xpath;

import org.jspecify.annotations.NonNull;

/**
 * Why an expression or pattern is not motionless: the innermost construct that has the verdict of the whole on its
 * own (see {@link XPathStreamabilityAnalyzer}). It is a hint for the author, without position in the expression text.
 *
 * @param construct
 *        The kind of construct. Never <code>null</code>.
 * @param name
 *        The construct as text, e.g. <code>preceding::x</code>, <code>fn:id#1</code>, <code>$doc</code> or
 *        <code>for</code>. Never <code>null</code>.
 * @author Philip Helger
 */
public record XPathStreamabilityReason (@NonNull EStreamabilityConstruct construct, @NonNull String name)
{
  @Override
  public String toString ()
  {
    return construct + " " + name;
  }
}
