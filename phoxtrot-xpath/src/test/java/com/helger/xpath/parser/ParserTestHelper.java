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
package com.helger.xpath.parser;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.ast.ENodeTestKind;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.IExpr.Binding;
import com.helger.xpath.ast.IExpr.StepExpr;
import com.helger.xpath.ast.ItemType;
import com.helger.xpath.ast.NodeTest;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test helpers: a static context with a small function library and a compact
 * S-expression printer for the AST.
 *
 * @author Philip Helger
 */
final class ParserTestHelper
{
  @PresentForCodeCoverage
  private static final ParserTestHelper INSTANCE = new ParserTestHelper ();

  private ParserTestHelper ()
  {}

  @NonNull
  static String signature (final int nArity)
  {
    final StringBuilder aSB = new StringBuilder ("(");
    for (int i = 0; i < nArity; ++i)
    {
      if (i > 0)
        aSB.append (", ");
      aSB.append ("item()*");
    }
    return aSB.append (") as item()*").toString ();
  }

  static void register (@NonNull final FunctionLibrary aLib,
                        @NonNull final String sNs,
                        @NonNull final String sLocal,
                        final int... aArities)
  {
    for (final int n : aArities)
      aLib.register (sNs, sLocal, signature (n), (aCtx, aArgs) -> XdmSequence.EMPTY);
  }

  @NonNull
  static FunctionLibrary createLibrary ()
  {
    final FunctionLibrary ret = new FunctionLibrary ();
    final String fn = XdmConstants.NS_FN;
    register (ret, fn, "true", 0);
    register (ret, fn, "false", 0);
    register (ret, fn, "count", 1);
    register (ret, fn, "concat", 2);
    register (ret, fn, "string-join", 1, 2);
    register (ret, fn, "upper-case", 1);
    register (ret, fn, "abs", 1);
    register (ret, fn, "position", 0);
    register (ret, fn, "last", 0);
    register (ret, fn, "not", 1);
    register (ret, fn, "string", 0, 1);
    register (ret, fn, "sum", 1, 2);
    register (ret, fn, "for-each", 2);
    register (ret, fn, "filter", 2);
    register (ret, fn, "fold-left", 3);
    register (ret, fn, "name", 0, 1);
    register (ret, fn, "exists", 1);
    register (ret, fn, "empty", 1);
    register (ret, fn, "substring", 2, 3);
    register (ret, fn, "root", 0, 1);
    register (ret, fn, "data", 0, 1);
    register (ret, fn, "boolean", 1);
    register (ret, fn, "number", 0, 1);
    register (ret, fn, "reverse", 1);
    register (ret, fn, "contains", 2, 3);
    register (ret, fn, "string-length", 0, 1);
    register (ret, fn, "subsequence", 2, 3);
    register (ret, fn, "distinct-values", 1, 2);
    register (ret, fn, "translate", 3);
    register (ret, fn, "sort", 1, 2, 3);
    register (ret, XdmConstants.NS_XS, "integer", 1);
    register (ret, XdmConstants.NS_XS, "decimal", 1);
    register (ret, XdmConstants.NS_XS, "string", 1);
    register (ret, FunctionLibrary.NS_MAP, "get", 2);
    register (ret, FunctionLibrary.NS_MAP, "size", 1);
    register (ret, FunctionLibrary.NS_ARRAY, "size", 1);
    register (ret, FunctionLibrary.NS_ARRAY, "get", 2);
    register (ret, FunctionLibrary.NS_MATH, "pi", 0);
    return ret;
  }

  @NonNull
  static StaticContext createContext ()
  {
    final StaticContext ret = new StaticContext ();
    ret.setFunctionLibrary (createLibrary ());
    ret.declareNamespace ("p", "urn:p");
    ret.declareVariable (new XdmQName ("v"));
    ret.declareVariable (new XdmQName ("f"));
    ret.declareVariable (new XdmQName ("m"));
    ret.declareVariable (new XdmQName ("urn:p", "pv"));
    return ret;
  }

  @NonNull
  static String name (@Nullable final XdmQName aName)
  {
    if (aName == null)
      return "null";
    final String sNs = aName.namespaceUri ();
    final String sPrefix;
    if (sNs.equals (XdmConstants.NS_FN))
      sPrefix = "fn";
    else
      if (sNs.equals (XdmConstants.NS_XS))
        sPrefix = "xs";
      else
        if (sNs.equals (FunctionLibrary.NS_MAP))
          sPrefix = "map";
        else
          if (sNs.equals (FunctionLibrary.NS_ARRAY))
            sPrefix = "array";
          else
            if (sNs.equals (FunctionLibrary.NS_MATH))
              sPrefix = "math";
            else
              return aName.getEQName ();
    return sPrefix + ":" + aName.localName ();
  }

  @NonNull
  private static String _literal (@NonNull final XdmAtomic aValue)
  {
    return switch (aValue.getType ())
    {
      case STRING -> "'" + aValue.getString () + "'";
      case INTEGER -> aValue.getBigInteger ().toString ();
      case DECIMAL -> aValue.getBigDecimal ().toPlainString () + "dec";
      case DOUBLE -> aValue.getValue ().toString () + "dbl";
      default -> aValue.getType ().name ();
    };
  }

  @NonNull
  static String nodeTest (@NonNull final NodeTest aTest)
  {
    final ENodeTestKind eKind = aTest.kind ();
    switch (eKind)
    {
      case NAME:
        if (aTest.anyName ())
          return "*";
        if (aTest.anyNamespace ())
          return "*:" + aTest.name ().localName ();
        if (aTest.anyLocalName ())
          return "Q{" + aTest.name ().namespaceUri () + "}*";
        return name (aTest.name ());
      case ANY_NODE:
        return "node()";
      case TEXT:
        return "text()";
      case COMMENT:
        return "comment()";
      case NAMESPACE_NODE:
        return "namespace-node()";
      case PROCESSING_INSTRUCTION:
        return "processing-instruction(" + (aTest.name () == null ? "" : aTest.name ().localName ()) + ")";
      case ELEMENT:
      case ATTRIBUTE:
      {
        final StringBuilder aSB = new StringBuilder (eKind == ENodeTestKind.ELEMENT ? "element(" : "attribute(");
        if (aTest.anyName ())
        {
          if (aTest.typeName () != null)
            aSB.append ('*');
        }
        else
          aSB.append (name (aTest.name ()));
        if (aTest.typeName () != null)
          aSB.append (',').append (name (aTest.typeName ())).append (aTest.nillable () ? "?" : "");
        return aSB.append (')').toString ();
      }
      case DOCUMENT:
        return "document-node(" + (aTest.inner () == null ? "" : nodeTest (aTest.inner ())) + ")";
      default:
        return eKind.name ();
    }
  }

  @NonNull
  static String itemType (@NonNull final ItemType aType)
  {
    return switch (aType.kind ())
    {
      case ITEM -> "item()";
      case ATOMIC -> "xs:" + aType.atomicType ().getLocalName ();
      case NODE -> nodeTest (aType.nodeTest ());
      case FUNCTION -> aType.wildcard () ? "function(*)"
                                         : "function(" +
                                           String.join (",", aType.paramTypes ().stream ().map (ParserTestHelper::seqType).toList ()) +
                                           ") as " +
                                           seqType (aType.returnType ());
      case MAP -> aType.wildcard () ? "map(*)"
                                    : "map(xs:" + aType.mapKeyType ().getLocalName () + "," + seqType (aType.valueType ()) + ")";
      case ARRAY -> aType.wildcard () ? "array(*)" : "array(" + seqType (aType.valueType ()) + ")";
    };
  }

  @NonNull
  static String seqType (@NonNull final SeqType aType)
  {
    if (aType.empty ())
      return "empty-sequence()";
    final String sOcc = switch (aType.occurrence ())
    {
      case ONE -> "";
      case ZERO_OR_ONE -> "?";
      case ZERO_OR_MORE -> "*";
      case ONE_OR_MORE -> "+";
    };
    final String sItem = itemType (aType.itemType ());
    return (aType.listCast () ? "list:" : "") +
           (aType.itemType ().kind () == ItemType.Kind.FUNCTION && !aType.itemType ().wildcard () ? "(" + sItem + ")"
                                                                                                    : sItem) +
           sOcc;
  }

  @NonNull
  private static String _list (@NonNull final List <IExpr> aList)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IExpr e : aList)
      aSB.append (' ').append (print (e));
    return aSB.toString ();
  }

  @NonNull
  private static String _preds (@NonNull final List <IExpr> aList)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IExpr e : aList)
      aSB.append ('[').append (print (e)).append (']');
    return aSB.toString ();
  }

  @NonNull
  private static String _bindings (@NonNull final List <Binding> aList, @NonNull final String sSep)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final Binding b : aList)
      aSB.append (" ($").append (name (b.name ())).append (sSep).append (print (b.expr ())).append (')');
    return aSB.toString ();
  }

  @NonNull
  static String step (@NonNull final StepExpr aStep)
  {
    if (aStep.postfix () != null)
      return "{" + print (aStep.postfix ()) + "}" + _preds (aStep.predicates ());
    return aStep.axis ().getName () + "::" + nodeTest (aStep.test ()) + _preds (aStep.predicates ());
  }

  @NonNull
  private static String _lower (@NonNull final Enum <?> e)
  {
    return e.name ().toLowerCase (Locale.ROOT);
  }

  /**
   * @param aExpr
   *        Expression to print.
   * @return A compact, deterministic S-expression rendering.
   */
  @NonNull
  static String print (@Nullable final IExpr aExpr)
  {
    if (aExpr == null)
      return "*";
    return switch (aExpr)
    {
      case final IExpr.LiteralExpr e -> _literal (e.value ());
      case final IExpr.VarRefExpr e -> "$" + name (e.name ());
      case final IExpr.ContextItemExpr e -> ".";
      case final IExpr.FunctionCallExpr e -> "(" + name (e.name ()) + _list (e.args ()) + ")";
      case final IExpr.PlaceholderExpr e -> "?";
      case final IExpr.NamedFunctionRefExpr e -> name (e.name ()) + "#" + e.arity ();
      case final IExpr.InlineFunctionExpr e ->
      {
        final StringBuilder aSB = new StringBuilder ("(function (");
        boolean bFirst = true;
        for (final IExpr.Param p : e.params ())
        {
          if (!bFirst)
            aSB.append (", ");
          bFirst = false;
          aSB.append ('$').append (name (p.name ()));
          if (p.type () != null)
            aSB.append (" as ").append (seqType (p.type ()));
        }
        aSB.append (')');
        if (e.returnType () != null)
          aSB.append (" as ").append (seqType (e.returnType ()));
        yield aSB.append (' ').append (print (e.body ())).append (')').toString ();
      }
      case final IExpr.DynamicCallExpr e -> "(dyn " + print (e.base ()) + _list (e.args ()) + ")";
      case final IExpr.SequenceExpr e -> "(seq" + _list (e.items ()) + ")";
      case final IExpr.BinaryExpr e -> "(" + _lower (e.op ()) + " " + print (e.left ()) + " " + print (e.right ()) + ")";
      case final IExpr.UnaryExpr e -> "(" + (e.negate () ? "neg " : "plus ") + print (e.operand ()) + ")";
      case final IExpr.ComparisonExpr e -> "(" +
                                           _lower (e.op ()) +
                                           " " +
                                           print (e.left ()) +
                                           " " +
                                           print (e.right ()) +
                                           ")";
      case final IExpr.SetExpr e -> "(" + _lower (e.op ()) + " " + print (e.left ()) + " " + print (e.right ()) + ")";
      case final IExpr.RangeExpr e -> "(to " + print (e.from ()) + " " + print (e.to ()) + ")";
      case final IExpr.StringConcatExpr e -> "(concat" + _list (e.parts ()) + ")";
      case final IExpr.IfExpr e -> "(if " +
                                   print (e.condition ()) +
                                   " " +
                                   print (e.thenExpr ()) +
                                   " " +
                                   print (e.elseExpr ()) +
                                   ")";
      case final IExpr.ForExpr e -> "(for" + _bindings (e.bindings (), " in ") + " " + print (e.body ()) + ")";
      case final IExpr.LetExpr e -> "(let" + _bindings (e.bindings (), " := ") + " " + print (e.body ()) + ")";
      case final IExpr.QuantifiedExpr e -> "(" +
                                           (e.every () ? "every" : "some") +
                                           _bindings (e.bindings (), " in ") +
                                           " " +
                                           print (e.satisfies ()) +
                                           ")";
      case final IExpr.SimpleMapExpr e -> "(map!" + _list (e.steps ()) + ")";
      case final IExpr.PathExpr e ->
      {
        final StringBuilder aSB = new StringBuilder ("(path");
        if (e.absolute ())
          aSB.append (" /");
        if (e.start () != null)
          aSB.append (" ").append (print (e.start ()));
        for (final IExpr.StepExpr s : e.steps ())
          aSB.append (' ').append (step (s));
        yield aSB.append (')').toString ();
      }
      case final IExpr.FilterExpr e -> "(filter " + print (e.primary ()) + _preds (e.predicates ()) + ")";
      case final IExpr.InstanceOfExpr e -> "(instance " + print (e.operand ()) + " " + seqType (e.type ()) + ")";
      case final IExpr.TreatExpr e -> "(treat " + print (e.operand ()) + " " + seqType (e.type ()) + ")";
      case final IExpr.CastExpr e -> "(" +
                                     (e.castable () ? "castable " : "cast ") +
                                     print (e.operand ()) +
                                     " " +
                                     seqType (e.type ()) +
                                     ")";
      case final IExpr.MapConstructorExpr e ->
      {
        final StringBuilder aSB = new StringBuilder ("(mapc");
        for (int i = 0; i < e.keys ().size (); ++i)
          aSB.append (" (").append (print (e.keys ().get (i))).append (' ').append (print (e.values ().get (i))).append (')');
        yield aSB.append (')').toString ();
      }
      case final IExpr.ArrayConstructorExpr e -> "(" + (e.curly () ? "array{}" : "array") + _list (e.items ()) + ")";
      case final IExpr.LookupExpr e -> e.base () == null ? "(ulookup " + print (e.key ()) + ")"
                                                         : "(lookup " + print (e.base ()) + " " + print (e.key ()) + ")";
    };
  }

  /**
   * Parse with the default test context and print.
   *
   * @param sExpr
   *        Expression
   * @return The printed AST
   */
  @NonNull
  static String parse (@NonNull final String sExpr)
  {
    return print (XPathParser.parse (sExpr, createContext ()));
  }
}
