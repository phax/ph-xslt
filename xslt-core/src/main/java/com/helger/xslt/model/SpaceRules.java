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
package com.helger.xslt.model;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.XdmQName;

/**
 * The xsl:strip-space and xsl:preserve-space declarations of a stylesheet
 * (XSLT 3.0 section 4.3). For an element name the matching rule with the
 * highest import precedence, then the highest priority, then the last one
 * wins.
 *
 * @author Philip Helger
 */
public final class SpaceRules
{
  /** One name test. */
  public record Rule (@NonNull String namespaceUri,
                      @NonNull String localName,
                      boolean anyNamespace,
                      boolean anyLocalName,
                      boolean strip,
                      int precedence)
  {
    /** @return The default priority of the name test. */
    public double priority ()
    {
      if (anyNamespace && anyLocalName)
        return -0.5;
      if (anyNamespace || anyLocalName)
        return -0.25;
      return 0;
    }

    public boolean matches (@NonNull final XdmQName aName)
    {
      return (anyNamespace || namespaceUri.equals (aName.namespaceUri ())) &&
             (anyLocalName || localName.equals (aName.localName ()));
    }
  }

  private final ICommonsList <Rule> m_aRules = new CommonsArrayList <> ();

  public void addRule (@NonNull final Rule aRule)
  {
    m_aRules.add (aRule);
  }

  /** @return <code>true</code> if there is no xsl:strip-space at all. */
  public boolean isNoStripping ()
  {
    for (final Rule aRule : m_aRules)
      if (aRule.strip ())
        return false;
    return true;
  }

  /** @return All rules (do not modify). */
  @NonNull
  public ICommonsList <Rule> rules ()
  {
    return m_aRules;
  }

  /**
   * @param aElementName
   *        Name of the parent element of a whitespace text node.
   * @return <code>true</code> if whitespace-only text children are stripped.
   */
  public boolean isStripped (@NonNull final XdmQName aElementName)
  {
    Rule aBest = null;
    for (final Rule aRule : m_aRules)
      if (aRule.matches (aElementName))
        if (aBest == null ||
            aRule.precedence () > aBest.precedence () ||
            aRule.precedence () == aBest.precedence () && aRule.priority () >= aBest.priority ())
          aBest = aRule;
    return aBest != null && aBest.strip ();
  }
}
