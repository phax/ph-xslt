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

import java.util.Comparator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * A mode: its template rules, ordered best first (import precedence, then
 * priority, then declaration order), and the properties of its xsl:mode
 * declaration. Filled by the compiler and immutable afterwards.
 *
 * @author Philip Helger
 */
public final class Mode
{
  /** The name used for the unnamed mode. */
  public static final XdmQName UNNAMED = new XdmQName (XsltNames.NS_XSL, "unnamed");

  private final XdmQName m_aName;
  private EOnNoMatch m_eOnNoMatch = EOnNoMatch.TEXT_ONLY_COPY;
  private boolean m_bFailOnMultipleMatch;
  private boolean m_bWarningOnNoMatch;
  private boolean m_bWarningOnMultipleMatch;
  private String m_sVisibility;
  private String m_sTyped;
  private XslElement m_aDeclaration;
  private final ICommonsList <TemplateRule> m_aRules = new CommonsArrayList <> ();

  public Mode (@NonNull final XdmQName aName)
  {
    m_aName = aName;
  }

  /** @return The mode name, {@link #UNNAMED} for the unnamed mode. */
  @NonNull
  public XdmQName getName ()
  {
    return m_aName;
  }

  public boolean isUnnamed ()
  {
    return UNNAMED.equals (m_aName);
  }

  @NonNull
  public EOnNoMatch getOnNoMatch ()
  {
    return m_eOnNoMatch;
  }

  public void setOnNoMatch (@NonNull final EOnNoMatch eOnNoMatch)
  {
    m_eOnNoMatch = eOnNoMatch;
  }

  public boolean isFailOnMultipleMatch ()
  {
    return m_bFailOnMultipleMatch;
  }

  public void setFailOnMultipleMatch (final boolean b)
  {
    m_bFailOnMultipleMatch = b;
  }

  public boolean isWarningOnNoMatch ()
  {
    return m_bWarningOnNoMatch;
  }

  public void setWarningOnNoMatch (final boolean b)
  {
    m_bWarningOnNoMatch = b;
  }

  public boolean isWarningOnMultipleMatch ()
  {
    return m_bWarningOnMultipleMatch;
  }

  public void setWarningOnMultipleMatch (final boolean b)
  {
    m_bWarningOnMultipleMatch = b;
  }

  /** @return The declared visibility or <code>null</code>. */
  @Nullable
  public String getVisibility ()
  {
    return m_sVisibility;
  }

  public void setVisibility (@Nullable final String s)
  {
    m_sVisibility = s;
  }

  /**
   * @return The declared value of the typed attribute (yes, no, strict, lax,
   *         unspecified) or <code>null</code>.
   */
  @Nullable
  public String getTyped ()
  {
    return m_sTyped;
  }

  public void setTyped (@Nullable final String s)
  {
    m_sTyped = s;
  }

  /** @return The (highest precedence) xsl:mode declaration or <code>null</code>. */
  @Nullable
  public XslElement getDeclaration ()
  {
    return m_aDeclaration;
  }

  public void setDeclaration (@Nullable final XslElement aDeclaration)
  {
    m_aDeclaration = aDeclaration;
  }

  public void addRule (@NonNull final TemplateRule aRule)
  {
    m_aRules.add (aRule);
  }

  /** Sort the rules best first. Called once at the end of compilation. */
  public void sortRules ()
  {
    m_aRules.sort (Comparator.comparingInt (TemplateRule::getPrecedence)
                             .thenComparingDouble (TemplateRule::getPriority)
                             .thenComparingInt (TemplateRule::getOrder)
                             .reversed ());
  }

  /** @return The rules, best first (do not modify). */
  @NonNull
  public ICommonsList <TemplateRule> rules ()
  {
    return m_aRules;
  }

  @Override
  public String toString ()
  {
    return isUnnamed () ? "#unnamed" : m_aName.getEQName ();
  }
}
