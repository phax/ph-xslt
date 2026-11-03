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
package com.helger.xpath.eval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.ast.EAxis;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.fn.FnAccessor;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for the name index on the descendant axes
 * ({@link AxisNavigator#collectNamedDescendants}) and the fusion of
 * <code>//name</code> into one descendant step.
 *
 * @author Philip Helger
 */
public final class IndexedDescendantTest
{
  private static final String INVOICE = "<Invoice>" +
                                        "<InvoiceLine id='1'><SubInvoiceLine id='1.1'><SubInvoiceLine id='1.1.1'/></SubInvoiceLine></InvoiceLine>" +
                                        "<InvoiceLine id='2'><Note><SubInvoiceLine id='2.1' flag='y'/></Note></InvoiceLine>" +
                                        "</Invoice>";

  @NonNull
  private static IXdmNode _parse (@NonNull final String sXml)
  {
    try
    {
      return new XdmParser ().parse (sXml);
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalArgumentException (ex);
    }
  }

  @NonNull
  private static String _eval (@NonNull final String sExpr, @NonNull final IXdmItem aContext)
  {
    final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, new StaticContext ());
    final DynamicContext aCtx = aCompiled.createDynamicContext ().withFocus (aContext, 1, 1);
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aCompiled.evaluate (aCtx))
    {
      if (!aSB.isEmpty ())
        aSB.append (' ');
      aSB.append (FnAccessor.getStringValue (aItem));
    }
    return aSB.toString ();
  }

  @Test
  public void testFusion ()
  {
    final StaticContext aSC = new StaticContext ();
    final String [] aFused = { "//x", "//x[@a]", "a//x[@a = 1 and not(b)]", "//x[exists(y) or b | c]", "//*",
                                // The nested [1] counts among the y children of x
                                "//x[y[1]]" };
    for (final String s : aFused)
    {
      final IExpr.PathExpr aPath = (IExpr.PathExpr) XPathParser.parse (s, aSC);
      final IExpr.StepExpr aFusedStep = aPath.steps ().get (aPath.absolute () ? 0 : 1).fused ();
      assertNotNull (s, aFusedStep);
      assertEquals (EAxis.DESCENDANT, aFusedStep.axis ());
    }
    // A position, something that may be a number, another axis, a postfix step
    final String [] aNotFused = { "//x[1]", "//x[position() > 1]", "//x[last()]", "//x[@a][. = last()]", "//x[count(y)]",
                                  "//x[string(.)]", "//@a", "//(x|y)" };
    for (final String s : aNotFused)
    {
      final IExpr.PathExpr aPath = (IExpr.PathExpr) XPathParser.parse (s, aSC);
      assertNull (s, aPath.steps ().get (0).fused ());
    }
  }

  @Test
  public void testRecursiveStructures ()
  {
    final IXdmNode aDoc = _parse (INVOICE);
    assertEquals ("1.1 1.1.1 2.1", _eval ("//SubInvoiceLine/@id", aDoc));
    assertEquals ("1.1.1", _eval ("//SubInvoiceLine//SubInvoiceLine/@id", aDoc));
    assertEquals ("1.1 1.1.1 2.1", _eval ("//InvoiceLine//SubInvoiceLine/@id", aDoc));
    assertEquals ("1.1 1.1.1 2.1", _eval ("//SubInvoiceLine/descendant-or-self::SubInvoiceLine/@id", aDoc));
    assertEquals ("2.1", _eval ("//SubInvoiceLine[@flag]/@id", aDoc));
    assertEquals ("3", _eval ("count(//SubInvoiceLine | //SubInvoiceLine//SubInvoiceLine)", aDoc));
    // Not fused: the first SubInvoiceLine child of each parent
    assertEquals ("1.1 1.1.1 2.1", _eval ("//SubInvoiceLine[1]/@id", aDoc));
    // The first descendant overall, and per context node
    assertEquals ("1.1", _eval ("descendant::SubInvoiceLine[1]/@id", aDoc));
    assertEquals ("1.1 2.1", _eval ("//InvoiceLine/descendant::SubInvoiceLine[1]/@id", aDoc));
    assertEquals ("1.1.1", _eval ("//SubInvoiceLine/descendant::SubInvoiceLine[last()]/@id", aDoc));
    // Context nodes not in document order
    assertEquals ("1.1 1.1.1 2.1", _eval ("(//InvoiceLine[2], //InvoiceLine[1])//SubInvoiceLine/@id", aDoc));
    assertEquals ("", _eval ("//Missing", aDoc));
  }

  @Test
  public void testChangedTree ()
  {
    final IXdmNode aDoc = _parse (INVOICE);
    assertEquals ("3", _eval ("count(//SubInvoiceLine)", aDoc));
    final IXdmNode aNote = aDoc.getChildAtIndex (0).getChildAtIndex (1).getChildAtIndex (0);
    final IXdmNode aNew = IXdmNode.createElement (new XdmQName ("SubInvoiceLine"));
    aNew.addAttribute (IXdmNode.createAttribute (new XdmQName ("id"), "2.2"));
    aNote.appendChild (aNew);
    // Unnumbered now: walks the tree
    assertEquals ("1.1 1.1.1 2.1 2.2", _eval ("//SubInvoiceLine/@id", aDoc));
    aDoc.assignDocumentOrder ();
    assertEquals ("1.1 1.1.1 2.1 2.2", _eval ("//SubInvoiceLine/@id", aDoc));
    aNote.removeChild (aNote.getChildAtIndex (0));
    aDoc.assignDocumentOrder ();
    assertEquals ("1.1 1.1.1 2.2", _eval ("//SubInvoiceLine/@id", aDoc));
  }

  private static void _addRandomChildren (@NonNull final IXdmNode aParent,
                                          @NonNull final Random aRandom,
                                          final int nDepth)
  {
    final int nCount = nDepth > 5 ? 0 : aRandom.nextInt (4);
    for (int i = 0; i < nCount; ++i)
    {
      final IXdmNode aChild;
      if (aRandom.nextInt (5) == 0)
        aChild = IXdmNode.createText ("t");
      else
      {
        aChild = IXdmNode.createElement (new XdmQName (String.valueOf ((char) ('a' + aRandom.nextInt (3)))));
        if (aRandom.nextBoolean ())
          aChild.addAttribute (IXdmNode.createAttribute (new XdmQName ("x"), "1"));
        _addRandomChildren (aChild, aRandom, nDepth + 1);
      }
      aParent.appendChild (aChild);
    }
  }

  @Test
  public void testAgainstTraversal ()
  {
    final Random aRandom = new Random (7);
    for (int nTree = 0; nTree < 50; ++nTree)
    {
      final IXdmNode aDoc = IXdmNode.createDocument ();
      final IXdmNode aRoot = IXdmNode.createElement (new XdmQName ("a"));
      aDoc.appendChild (aRoot);
      _addRandomChildren (aRoot, aRandom, 0);
      final List <IXdmNode> aAll = new ArrayList <> ();
      AxisNavigator.collect (EAxis.DESCENDANT_OR_SELF, aDoc, null, aAll);
      final List <IXdmNode> aCandidates = new ArrayList <> (aAll);
      for (final IXdmNode aNode : aAll)
        aCandidates.addAll (aNode.getAllAttributes ());

      // Not numbered yet
      assertFalse (AxisNavigator.collectNamedDescendants (EAxis.DESCENDANT, List.of (aRoot), new XdmQName ("a"), new ArrayList <> ()));
      aDoc.assignDocumentOrder ();

      for (int nRun = 0; nRun < 20; ++nRun)
      {
        final List <IXdmNode> aContext = new ArrayList <> ();
        for (final IXdmNode aNode : aCandidates)
          if (aRandom.nextInt (4) == 0)
            aContext.add (aNode);
        aContext.sort (Comparator.comparingInt (IXdmNode::getOrder));
        for (final EAxis eAxis : new EAxis [] { EAxis.DESCENDANT, EAxis.DESCENDANT_OR_SELF })
          for (final String sName : new String [] { "a", "b", "c" })
          {
            final XdmQName aName = new XdmQName (sName);
            final NodeTest aTest = new NodeTest (ENodeTestKind.NAME, false, false, false, aName, null, false, null);
            final XdmSequence.Builder aExpected = new XdmSequence.Builder ();
            for (final IXdmNode aNode : aContext)
            {
              final List <IXdmNode> aOne = new ArrayList <> ();
              AxisNavigator.collect (eAxis, aNode, x -> NodeTestMatcher.matches (aTest, x, ENodeKind.ELEMENT), aOne);
              for (final IXdmNode aFound : aOne)
                aExpected.add (aFound);
            }
            final List <IXdmNode> aActual = new ArrayList <> ();
            assertTrue (AxisNavigator.collectNamedDescendants (eAxis, aContext, aName, aActual));
            assertEquals (NodeSorter.sortAndDeduplicate (aExpected.build ()).getAsList (), aActual);
          }
      }
      // Not in document order
      if (aAll.size () > 2)
        assertFalse (AxisNavigator.collectNamedDescendants (EAxis.DESCENDANT,
                                                            List.of (aAll.get (2), aAll.get (1)),
                                                            new XdmQName ("a"),
                                                            new ArrayList <> ()));
    }
  }
}
