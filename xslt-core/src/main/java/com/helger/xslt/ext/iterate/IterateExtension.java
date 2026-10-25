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
package com.helger.xslt.ext.iterate;

import org.jspecify.annotations.NonNull;

import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XsltExtensionRegistry;

/**
 * The iteration family (XSLT 3.0 section 7.1): xsl:iterate, xsl:break,
 * xsl:next-iteration and xsl:on-completion.
 *
 * @author Philip Helger
 */
public final class IterateExtension implements IXsltExtension
{
  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("iterate", IterateInstruction::compileIterate);
    aRegistry.registerInstruction ("break", IterateInstruction::compileBreak);
    aRegistry.registerInstruction ("next-iteration", IterateInstruction::compileNextIteration);
  }
}
