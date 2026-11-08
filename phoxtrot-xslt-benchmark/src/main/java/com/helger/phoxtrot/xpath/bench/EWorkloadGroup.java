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
package com.helger.phoxtrot.xpath.bench;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.id.IHasID;
import com.helger.base.lang.EnumHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * The workload groups, one JMH benchmark class each, and the cases within each group. This is the
 * one place that creates workloads, for the JMH benchmarks and for {@link MainProfile}.
 *
 * @author Philip Helger
 */
public enum EWorkloadGroup implements IHasID <String>
{
  /** Building a tree: {@link ParseWorkload}. */
  PARSE ("parse"),
  /** XPath expressions and the rule set: {@link XPathWorkload}, {@link XPathRulesWorkload}. */
  XPATH ("xpath"),
  /** Stylesheets: {@link XsltWorkload}. */
  XSLT ("xslt"),
  /** Schema validation: {@link XsdWorkload}. */
  XSD ("xsd"),
  /** Streamed and buffered <code>xsl:source-document</code>: {@link StreamingWorkload}. */
  STREAMING ("streaming"),
  /** Compiling stylesheets and expressions: {@link CompileWorkload}. */
  COMPILE ("compile");

  /** The case of the XPath group that runs the rule set. */
  public static final String XPATH_RULES = "rules";

  private final String m_sID;

  EWorkloadGroup (@NonNull final String sID)
  {
    m_sID = sID;
  }

  @NonNull
  public String getID ()
  {
    return m_sID;
  }

  /** @return The IDs of all cases of this group. Never <code>null</code>. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getAllCaseIDs ()
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    switch (this)
    {
      case PARSE:
        ret.add ("parse");
        break;
      case XPATH:
        for (final EXPathCase eCase : EXPathCase.values ())
          ret.add (eCase.getID ());
        ret.add (XPATH_RULES);
        break;
      case XSLT:
        for (final EXsltCase eCase : EXsltCase.values ())
          ret.add (eCase.getID ());
        break;
      case XSD:
        ret.add ("1.0");
        ret.add ("1.1");
        break;
      case STREAMING:
        ret.add ("streamed");
        ret.add ("buffered");
        break;
      case COMPILE:
        ret.add ("xslt");
        ret.add ("xpath");
        break;
    }
    return ret;
  }

  @NonNull
  private IWorkload _create (@NonNull final String sCase, final int nSize) throws Exception
  {
    switch (this)
    {
      case PARSE:
        if ("parse".equals (sCase))
          return new ParseWorkload (nSize);
        break;
      case XPATH:
        if (XPATH_RULES.equals (sCase))
          return new XPathRulesWorkload (nSize);
        final EXPathCase eXPathCase = EXPathCase.getFromIDOrNull (sCase);
        if (eXPathCase != null)
          return new XPathWorkload (eXPathCase, nSize);
        break;
      case XSLT:
        final EXsltCase eXsltCase = EXsltCase.getFromIDOrNull (sCase);
        if (eXsltCase != null)
          return new XsltWorkload (eXsltCase, nSize);
        break;
      case XSD:
        return new XsdWorkload (sCase, nSize);
      case STREAMING:
        if ("streamed".equals (sCase) || "buffered".equals (sCase))
          return new StreamingWorkload ("streamed".equals (sCase), nSize);
        break;
      case COMPILE:
        return new CompileWorkload (sCase);
    }
    throw new IllegalArgumentException ("Unknown case '" + sCase + "' of group '" + m_sID + "'");
  }

  /**
   * Create and prepare a workload, run it once and check its result.
   *
   * @param sCase
   *        The case, one of {@link #getAllCaseIDs()}.
   * @param nSize
   *        The number of invoice lines of the input document; ignored by the compile group.
   * @return The prepared and checked workload. Never <code>null</code>.
   * @throws Exception
   *         if the preparation or the run fails
   * @throws IllegalStateException
   *         if the result of the run is not the expected one
   */
  @NonNull
  public IWorkload createVerified (@NonNull final String sCase, final int nSize) throws Exception
  {
    final IWorkload ret = _create (sCase, nSize);
    ret.runAndVerify ();
    return ret;
  }

  @Nullable
  public static EWorkloadGroup getFromIDOrNull (@Nullable final String sID)
  {
    return EnumHelper.getFromIDOrNull (EWorkloadGroup.class, sID);
  }
}
