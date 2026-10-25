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
package com.helger.xpath.ops;

import java.util.function.UnaryOperator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.EXdmSimpleVariety;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xpath.xdm.schema.XdmTypedToken;

/**
 * Atomic values of user-defined simple types (schema-awareness): casting to
 * them, the typed value of a validated node, and the instance check.
 *
 * @author Philip Helger
 */
@Immutable
public final class SchemaTypeOps
{
  @PresentForCodeCoverage
  private static final SchemaTypeOps INSTANCE = new SchemaTypeOps ();

  private SchemaTypeOps ()
  {}

  /**
   * @param aType
   *        A type definition.
   * @return <code>true</code> if it is a built-in type (named, in the xs
   *         namespace).
   */
  public static boolean isBuiltin (@NonNull final IXdmTypeDefinition aType)
  {
    final XdmQName aName = aType.getName ();
    return aName != null && XdmConstants.NS_XS.equals (aName.namespaceUri ());
  }

  /**
   * Turn validated tokens into atomic values, annotated with their
   * user-defined type.
   *
   * @param aTokens
   *        The tokens of a validated simple value.
   * @param aSC
   *        The static context for namespace-sensitive values, may be
   *        <code>null</code>.
   * @return The atomic values.
   */
  @NonNull
  public static XdmSequence toAtomics (@NonNull final Iterable <XdmTypedToken> aTokens,
                                       @Nullable final StaticContext aSC)
  {
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (final XdmTypedToken aToken : aTokens)
    {
      EAtomicType eType = EAtomicType.getFromLocalName (aToken.builtinType ().localName ());
      if (eType == null)
        eType = EAtomicType.UNTYPED_ATOMIC;
      final XdmAtomic aValue;
      if (eType == EAtomicType.UNTYPED_ATOMIC)
        aValue = XdmAtomic.untyped (aToken.lexical ());
      else
        if (eType == EAtomicType.NOTATION)
        {
          // xs:NOTATION is abstract, but a value of a type derived from it
          // has the same value space as xs:QName
          aValue = XdmAtomic.of (EAtomicType.NOTATION,
                                 AtomicCaster.castFromString (aToken.lexical (), EAtomicType.QNAME, aSC).getValue ());
        }
        else
          aValue = AtomicCaster.castFromString (aToken.lexical (), eType, aSC);
      aBuilder.add (isBuiltin (aToken.type ()) ? aValue : aValue.withSchemaType (aToken.type ()));
    }
    return aBuilder.build ();
  }

  /**
   * Cast an atomic value to a user-defined simple type (XPath 3.1 section
   * 3.14.2 and the casting rules of F&amp;O 3.1 section 19.3).
   *
   * @param aValue
   *        The value.
   * @param aType
   *        The target type (atomic, list or union).
   * @param aSC
   *        The static context (namespaces for QName values).
   * @return The cast result: one value, or one per item for a list type.
   * @throws XPathException
   *         FORG0001 if the value is not valid for the type, XPTY0004 if the
   *         cast is not allowed
   */
  @NonNull
  public static XdmSequence cast (@NonNull final XdmAtomic aValue,
                                  @NonNull final IXdmTypeDefinition aType,
                                  @NonNull final StaticContext aSC)
  {
    final String sLexical;
    if (aType.getVariety () == EXdmSimpleVariety.ATOMIC)
    {
      final XdmQName aBuiltin = aType.getBuiltinAtomicType ();
      final EAtomicType eBuiltin = aBuiltin == null ? null : EAtomicType.getFromLocalName (aBuiltin.localName ());
      if (eBuiltin == null)
        throw XPathException.type ("Cannot cast to " + _describe (aType));
      if ((eBuiltin.isDerivedFrom (EAtomicType.QNAME) || eBuiltin.isDerivedFrom (EAtomicType.NOTATION)) &&
          !aValue.getType ().isStringFamily () &&
          aValue.getType () != EAtomicType.UNTYPED_ATOMIC &&
          aValue.getType () != EAtomicType.QNAME &&
          aValue.getType () != EAtomicType.NOTATION)
        throw XPathException.type ("Cannot cast " + aValue.getType ().getQualifiedName () + " to " + _describe (aType));
      // Cast to the built-in base first: that gives the casting errors of
      // the primitive type and the canonical lexical form
      final XdmAtomic aBase = eBuiltin.isDerivedFrom (EAtomicType.QNAME) || eBuiltin.isDerivedFrom (EAtomicType.NOTATION)
                                                                                                                       ? aValue
                                                                                                                       : AtomicCaster.cast (aValue,
                                                                                                                                            eBuiltin,
                                                                                                                                            aSC);
      sLexical = aBase.getLexical ();
    }
    else
    {
      if (aType.getVariety () == EXdmSimpleVariety.UNION && !aValue.isStringLike ())
      {
        // A value that is not a string is cast to the member types in turn
        // (F&O 3.1 section 19.3.5)
        XPathException aLast = null;
        for (final IXdmTypeDefinition aMember : aType.getMemberTypes ())
          try
          {
            if (isBuiltin (aMember))
            {
              final EAtomicType eMember = EAtomicType.getFromLocalName (aMember.getName ().localName ());
              if (eMember != null)
                return XdmSequence.of (AtomicCaster.cast (aValue, eMember, aSC));
            }
            else
              return cast (aValue, aMember, aSC);
          }
          catch (final XPathException ex)
          {
            aLast = ex;
          }
        throw aLast != null ? aLast : XPathException.type ("Cannot cast to " + _describe (aType));
      }
      if (aType.getVariety () == EXdmSimpleVariety.LIST && !aValue.isStringLike ())
        throw XPathException.type ("Cannot cast " + aValue.getType ().getQualifiedName () + " to the list type " + _describe (aType));
      sLexical = aValue.getLexical ();
    }
    final UnaryOperator <String> aResolver = sPrefix -> sPrefix.isEmpty () ? aSC.getDefaultElementNamespace ()
                                                                           : aSC.getNamespaceUri (sPrefix);
    try
    {
      return toAtomics (aType.validate (sLexical, aResolver), aSC);
    }
    catch (final XdmValidationException ex)
    {
      throw XPathException.of ("FORG0001", "Cannot cast '" + sLexical + "' to " + _describe (aType) + ": " + ex.getMessage ());
    }
  }

  @NonNull
  private static String _describe (@NonNull final IXdmTypeDefinition aType)
  {
    final XdmQName aName = aType.getName ();
    return aName != null ? aName.getEQName () : "an anonymous type";
  }

  /**
   * The instance check of an atomic value against a user-defined atomic or
   * union type (or a built-in member of a union).
   *
   * @param aValue
   *        The value.
   * @param aType
   *        The type.
   * @return <code>true</code> if the value is an instance of the type.
   */
  public static boolean isInstance (@NonNull final XdmAtomic aValue, @NonNull final IXdmTypeDefinition aType)
  {
    if (aType.getVariety () == EXdmSimpleVariety.UNION)
    {
      for (final IXdmTypeDefinition aMember : aType.getMemberTypes ())
        if (isInstance (aValue, aMember))
          return true;
      return false;
    }
    final IXdmTypeDefinition aAnnotation = aValue.getSchemaType ();
    if (aAnnotation != null)
      return aAnnotation.isDerivedFrom (aType);
    if (!isBuiltin (aType))
      return false;
    final EAtomicType eType = EAtomicType.getFromLocalName (aType.getName ().localName ());
    return eType != null && aValue.getType ().isDerivedFrom (eType);
  }
}
