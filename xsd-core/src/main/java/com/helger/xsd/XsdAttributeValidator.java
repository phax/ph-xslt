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
package com.helger.xsd;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.XdmNode;

/**
 * Attribute assessment of an element against a complex type (go-xslt
 * <code>attributes.go</code>). The attributes are checked against the
 * effective attribute uses and the attribute wildcard of the type.
 * <p>
 * Only the first error is reported; collecting all errors (the "missing
 * required attribute" loop of go-xslt <code>soft</code> and the wording from
 * the appinfo messages) is phase V7.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdAttributeValidator
{
  /** Up to this many uses a linear scan and a bit set replace the two maps. */
  private static final int INDEX_LINEAR_MAX = 64;

  @PresentForCodeCoverage
  private static final XsdAttributeValidator INSTANCE = new XsdAttributeValidator ();

  private XsdAttributeValidator ()
  {}

  /**
   * Answers the two questions validateAttributes asks about an element's
   * attribute uses - which use a name denotes (the last one of that name, as
   * a map filled in order would hold) and whether an attribute of a use's
   * name was present (go-xslt <code>attrUseIndex</code>).
   */
  private static final class AttrUseIndex
  {
    private final ICommonsList <XsdAttributeUse> m_aUses;
    private long m_nSeen;
    private final ICommonsMap <XsdName, XsdAttributeUse> m_aByName;
    private final ICommonsSet <XsdName> m_aSeenSet;

    AttrUseIndex (@NonNull final ICommonsList <XsdAttributeUse> aUses)
    {
      m_aUses = aUses;
      if (aUses.size () > INDEX_LINEAR_MAX)
      {
        m_aByName = new CommonsHashMap <> ();
        for (final XsdAttributeUse aUse : aUses)
          m_aByName.put (aUse.m_aDecl.m_aName, aUse);
        m_aSeenSet = new CommonsHashSet <> ();
      }
      else
      {
        m_aByName = null;
        m_aSeenSet = null;
      }
    }

    @Nullable
    XsdAttributeUse lookup (@NonNull final XsdName aName)
    {
      if (m_aByName != null)
        return m_aByName.get (aName);
      for (int i = m_aUses.size () - 1; i >= 0; i--)
        if (m_aUses.get (i).m_aDecl.m_aName.equals (aName))
          return m_aUses.get (i);
      return null;
    }

    void markPresent (@NonNull final XsdName aName)
    {
      if (m_aSeenSet != null)
      {
        m_aSeenSet.add (aName);
        return;
      }
      for (int i = 0; i < m_aUses.size (); i++)
        if (m_aUses.get (i).m_aDecl.m_aName.equals (aName))
          m_nSeen |= 1L << i;
    }

    boolean isPresent (@NonNull final XsdName aName)
    {
      if (m_aSeenSet != null)
        return m_aSeenSet.contains (aName);
      for (int i = 0; i < m_aUses.size (); i++)
        if (m_aUses.get (i).m_aDecl.m_aName.equals (aName) && (m_nSeen & 1L << i) != 0)
          return true;
      return false;
    }
  }

  /**
   * go-xslt <code>validateAttributes</code>: check an element's attributes
   * against a complex type's (effective) attribute uses and attribute
   * wildcard.
   *
   * @param aRun
   *        The validation run.
   * @param aType
   *        The governing complex type.
   * @param aElement
   *        The instance element.
   * @throws XsdInvalidException
   *         if an attribute is not valid
   */
  static void validateAttributes (@NonNull final XsdValidationRun aRun,
                                  @NonNull final XsdComplexType aType,
                                  @NonNull final XdmNode aElement)
  {
    final XsdEffectiveAttributes aEff = aRun.getSchema ().effectiveAttrs (aType);
    final ICommonsList <XsdAttributeUse> aUses = aEff.uses ();
    final XsdWildcard aWC = aEff.wildcard ();
    final AttrUseIndex aIndex = new AttrUseIndex (aUses);
    int nIDCount = 0;
    final int nAttrs = aElement.getAttributeCount ();
    for (int nAttr = 0; nAttr < nAttrs; nAttr++)
    {
      final XdmNode aAttr = aElement.getAttributeAtIndex (nAttr);
      final XsdName aName = XsdName.of (aAttr.getName ());
      final String sValue = aAttr.getValue ();
      XsdAttributeUse aUse = aIndex.lookup (aName);
      // xsi:* and xml:* attributes are always permitted, but when one is
      // explicitly declared (e.g. ref="xml:base" use="required", or
      // ref="xsi:type" with a fixed value) it must still be validated and
      // counted present - so only skip one that is not declared
      final boolean bXsi = XsdConstants.NS_XSI.equals (aName.space ());
      final boolean bXml = XsdConstants.NS_XML.equals (aName.space ());
      if (aUse == null && (bXsi || bXml))
      {
        // In 1.1 an undeclared xml:* attribute is not exempt: it needs a
        // named use or an admitting attribute wildcard. xsi:* stays
        // always-permitted; 1.0 keeps the lenient skip
        if (aRun.isXsd11 () && bXml && (aWC == null || !aWC.matches (aName)))
          throw XsdCompiler.invalid ("cvc-complex-type.3.2.2", "attribute " + aName + " is not allowed");
        continue;
      }
      // A prohibited attribute use denotes absence, not an active ban: the
      // attribute is simply not a named use, so it must instead be permitted
      // by the attribute wildcard (XSD 1.0 cvc-complex-type.3.2)
      if (aUse != null && aUse.m_bProhibited)
        aUse = null;
      if (aUse == null)
      {
        if (aWC == null || !aWC.matches (aName))
          throw XsdCompiler.invalid ("cvc-complex-type.3.2.2", "attribute " + aName + " is not allowed");
        // Permitted by the wildcard - but a strict/lax wildcard still has the
        // attribute assessed against its global declaration, which is how an
        // ID arriving through xs:anyAttribute gets counted and bound
        if (XsdWildcard.PROCESS_SKIP.equals (aWC.m_sProcess))
          continue;
        final XsdAttributeDecl aGlobal = aRun.getSchema ().m_aAttributes.get (aName);
        if (aGlobal == null)
        {
          // A strict wildcard demands assessment, but only where we actually
          // hold components for the namespace: matching one we never loaded a
          // schema for is routine and must not be an error
          if (XsdWildcard.PROCESS_STRICT.equals (aWC.m_sProcess))
            throw XsdCompiler.invalid ("cvc-assess-attr",
                                       "attribute " + aName + " has no declaration to assess it against");
          continue;
        }
        if (aGlobal.m_aType != null)
        {
          XsdInstanceValidator.yearZeroOK (aRun, aGlobal.m_aType, sValue);
          XsdSimpleTypeValidator.validateIn (aGlobal.m_aType, sValue, aAttr);
          aRun.noteIDValues (aGlobal.m_aType, sValue, aElement, aAttr);
          aRun.annotateAssertType (aAttr, aGlobal.m_aType);
          aRun.noteNodeType (aAttr, aGlobal.m_aType); // wildcard-assessed: the global declaration governs
          // XSD 1.0 permits at most one ID-typed attribute per element; 1.1
          // dropped the restriction (see noteIDValues, where one element may
          // bind several ID values)
          if (!aRun.isXsd11 () && aGlobal.m_aType.resolvePrim () == EAtomicType.ID && ++nIDCount > 1)
            throw XsdCompiler.invalid ("cvc-complex-type.5.1",
                                       "an element may carry at most one ID-typed attribute");
        }
        continue;
      }
      aIndex.markPresent (aName);
      final XsdSimpleType aDeclType = aUse.m_aDecl.m_aType;
      if (aDeclType != null)
      {
        XsdInstanceValidator.yearZeroOK (aRun, aDeclType, sValue);
        XsdSimpleTypeValidator.validateIn (aDeclType, sValue, aAttr);
        aRun.noteIDValues (aDeclType, sValue, aElement, aAttr);
        aRun.annotateAssertType (aAttr, aDeclType);
        aRun.noteNodeType (aAttr, aDeclType); // a named attribute use governs
        if (!aRun.isXsd11 () && aDeclType.resolvePrim () == EAtomicType.ID && ++nIDCount > 1)
          throw XsdCompiler.invalid ("cvc-complex-type.5.1", "an element may carry at most one ID-typed attribute");
      }
      if (aUse.m_bHasFixed)
      {
        // cvc-attribute.4 compares in the governing type's value space - no
        // blanket collapse: a preserve-whiteSpace string keeps its spacing
        // significant
        final boolean bEqual;
        if (aDeclType != null)
          bEqual = XsdSimpleTypeValidator.fixedEqual (aDeclType, sValue, aUse.m_sFixed);
        else
          bEqual = XsdLexHelper.lexEqualCollapsed (sValue, aUse.m_sFixed);
        if (!bEqual)
          throw XsdCompiler.invalid ("cvc-attribute.4",
                                     "attribute " +
                                                       aName +
                                                       " must equal the fixed value " +
                                                       XsdLexHelper.quote (aUse.m_sFixed));
      }
    }
    for (final XsdAttributeUse aUse : aUses)
    {
      final XsdName aUseName = aUse.m_aDecl.m_aName;
      if (aUse.m_bRequired && !aIndex.isPresent (aUseName))
        // Own wording (the appinfo msg:message on="missing") is phase V7
        throw XsdCompiler.invalid ("cvc-complex-type.4", "required attribute " + aUseName + " is missing");
      // An absent attribute with a value constraint contributes that value to
      // the instance, so an ID default still binds (and an IDREF default
      // still has to resolve)
      if (!aIndex.isPresent (aUseName) && !aUse.m_bProhibited && aUse.m_aDecl.m_aType != null)
        if (aUse.m_bHasDefault)
        {
          aRun.noteIDValues (aUse.m_aDecl.m_aType, aUse.m_sDefault, aElement, aElement);
          aRun.noteDefaultAttr (aElement, aUseName, aUse.m_sDefault, aUse.m_aDecl.m_aType);
        }
        else
          if (aUse.m_bHasFixed)
          {
            aRun.noteIDValues (aUse.m_aDecl.m_aType, aUse.m_sFixed, aElement, aElement);
            aRun.noteDefaultAttr (aElement, aUseName, aUse.m_sFixed, aUse.m_aDecl.m_aType);
          }
    }
  }
}
