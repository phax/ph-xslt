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
package com.helger.phoxtrot.xslt.model;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * The xsl:strip-space and xsl:preserve-space declarations of a stylesheet
 * (XSLT 3.0 section 4.4.2). For an element name the matching rule with the
 * highest import precedence, then the highest priority, then the last one
 * wins.
 *
 * @author Philip Helger
 */
public final class SpaceRules
{
  /**
   * One name test of an elements attribute: a QName, <code>*</code>
   * (both wildcard flags set), <code>prefix:*</code> / <code>Q{uri}*</code>
   * (any local name) or <code>*:local</code> (any namespace), whether it comes
   * from xsl:strip-space (<code>strip</code>) or xsl:preserve-space, and the
   * import precedence of the declaration.
   */
  public record Rule (@NonNull String namespaceUri,
                      @NonNull String localName,
                      boolean anyNamespace,
                      boolean anyLocalName,
                      boolean strip,
                      int precedence)
  {
    /**
     * @return The default priority of the name test as for a pattern (XSLT 3.0
     *         section 6.5): -0.5 for <code>*</code>, -0.25 for a test with
     *         one wildcard part, 0 for a QName.
     */
    public double priority ()
    {
      if (anyNamespace && anyLocalName)
        return -0.5;
      if (anyNamespace || anyLocalName)
        return -0.25;
      return 0;
    }

    /**
     * @param aName
     *        An element name.
     * @return <code>true</code> if the namespace URI matches (or any namespace
     *         is allowed) and the local name matches (or any local name is
     *         allowed).
     */
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
   * Find the rule that decides for an element name: among the matching rules
   * the one with the highest import precedence, then the highest default
   * priority; on a tie the later rule wins (the <code>&gt;=</code>
   * comparison). The compiler reports a strip/preserve conflict between
   * otherwise equal name tests at the same precedence (XTSE0270), so the tie
   * break only matters between equal rules. xml:space on the source is not
   * looked at here; the caller ({@link
   * com.helger.phoxtrot.xslt.runtime.WhitespaceStripper}) handles it.
   *
   * @param aElementName
   *        Name of the parent element of a whitespace text node.
   * @return <code>true</code> if whitespace-only text children are stripped,
   *         <code>false</code> if the winning rule is a preserve rule or no
   *         rule matches (preserving is the default).
   */
  public boolean isStripped (@NonNull final XdmQName aElementName)
  {
    Rule aBest = null;
    for (final Rule aRule : m_aRules)
      if (aRule.matches (aElementName))
        if (aBest == null ||
            aRule.precedence () > aBest.precedence () ||
            (aRule.precedence () == aBest.precedence () && aRule.priority () >= aBest.priority ()))
          aBest = aRule;
    return aBest != null && aBest.strip ();
  }
}
