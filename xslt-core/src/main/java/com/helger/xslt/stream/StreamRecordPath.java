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
package com.helger.xslt.stream;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.ast.EAxis;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.xdm.IXdmNode;

/**
 * The expression selecting the records of a streamed document, from the
 * document node: child steps only, each an element name test or
 * <code>*</code>, the last one also <code>node()</code>, no predicates. A
 * port of go-xslt's <code>strbStep</code>. The grammar is this small because
 * it is the subset for which a record can never contain another record, so
 * that each record is read once, in full, and dropped.
 *
 * @author Philip Helger
 */
@Immutable
public final class StreamRecordPath
{
  private final ICommonsList <NodeTest> m_aSteps;

  private StreamRecordPath (@NonNull final ICommonsList <NodeTest> aSteps)
  {
    m_aSteps = aSteps;
  }

  @Nonnegative
  public int getLength ()
  {
    return m_aSteps.size ();
  }

  /**
   * @param nIndex
   *        0-based step index.
   * @param aNode
   *        The node at depth <code>nIndex + 1</code>.
   * @return <code>true</code> if the step selects it.
   */
  public boolean matches (@Nonnegative final int nIndex, @NonNull final IXdmNode aNode)
  {
    final NodeTest aTest = m_aSteps.get (nIndex);
    if (aTest.kind () == ENodeTestKind.ANY_NODE)
      return !aNode.isAttribute () && !aNode.isDocument ();
    if (!aNode.isElement ())
      return false;
    return aTest.anyName () || aTest.name ().equals (aNode.getName ());
  }

  /**
   * @param aExpr
   *        The select expression of the consumer, evaluated with the document
   *        node as context item.
   * @return The record path, or <code>null</code> if the expression is not
   *         one.
   */
  @Nullable
  public static StreamRecordPath of (@Nullable final IExpr aExpr)
  {
    if (!(aExpr instanceof final IExpr.PathExpr aPath) || aPath.start () != null)
      return null;
    final List <IExpr.StepExpr> aSteps = aPath.steps ();
    if (aSteps.isEmpty ())
      return null;
    final ICommonsList <NodeTest> aTests = new CommonsArrayList <> ();
    for (int i = 0; i < aSteps.size (); ++i)
    {
      final IExpr.StepExpr aStep = aSteps.get (i);
      if (aStep.axis () != EAxis.CHILD ||
          aStep.postfix () != null ||
          aStep.fused () != null ||
          !aStep.predicates ().isEmpty () ||
          aStep.test () == null)
        return null;
      final NodeTest aTest = aStep.test ();
      final boolean bLast = i == aSteps.size () - 1;
      if (aTest.kind () == ENodeTestKind.ANY_NODE)
      {
        if (!bLast)
          return null;
      }
      else
        if (aTest.kind () != ENodeTestKind.NAME ||
            aTest.anyNamespace () ||
            aTest.anyLocalName () ||
            !aTest.anyName () && aTest.name () == null)
          return null;
      aTests.add (aTest);
    }
    return new StreamRecordPath (aTests);
  }
}
