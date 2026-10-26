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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.context.EXsdVersion;

/**
 * A compiled set of schema documents: the schema component model (go-xslt
 * <code>Schema</code>). Compile with {@link #compile(List, EXsdVersion, XsdCompileOptions)}
 * or {@link #compileFiles(List, EXsdVersion, XsdCompileOptions)}, validate
 * instances with {@link #validate(Path)}, and read the components through
 * {@link #getView()}.
 * <p>
 * A compiled schema is immutable and may be shared between threads; the state
 * of one validation lives in the validation run, never here.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class XsdSchema
{
  final EXsdVersion m_eVersion;
  /** The directory of the entry document, <code>null</code> if unknown. */
  final Path m_aBaseDir;
  /** Checks every schema document read from disk. */
  final XsdFileAccess m_aFileAccess;
  /** The schema documents the compile was given (for hint merges); empty for the empty schema set. */
  final ICommonsList <XsdSchemaSource> m_aSourceDocs = new CommonsArrayList <> ();
  final ICommonsOrderedMap <XsdName, XsdElementDecl> m_aElements = new CommonsLinkedHashMap <> ();
  final ICommonsOrderedMap <XsdName, IXsdType> m_aTypes = new CommonsLinkedHashMap <> ();
  final ICommonsOrderedMap <XsdName, XsdAttributeDecl> m_aAttributes = new CommonsLinkedHashMap <> ();
  /** Substitution groups: head to direct members. */
  final ICommonsOrderedMap <XsdName, ICommonsList <XsdElementDecl>> m_aSubstMembers = new CommonsLinkedHashMap <> ();
  /** Set at compile time when any xs:assert was parsed (XSD 1.1). */
  boolean m_bHasAsserts;

  XsdSchema (@NonNull final EXsdVersion eVersion,
             @Nullable final Path aBaseDir,
             @NonNull final XsdFileAccess aFileAccess)
  {
    m_eVersion = eVersion;
    m_aBaseDir = aBaseDir;
    m_aFileAccess = aFileAccess;
  }

  @Nullable
  private static XsdModelGroup _allGroupOf (@Nullable final XsdParticle aParticle)
  {
    if (aParticle == null || aParticle.m_nMax != 1)
      return null;
    if (aParticle.m_aTerm instanceof final XsdModelGroup aMG && aMG.m_eCompositor == EXsdCompositor.ALL)
      return aMG;
    return null;
  }

  @Nullable
  private XsdParticle _combineExtension (@NonNull final XsdComplexType aCT, @Nullable final XsdParticle aBaseParticle)
  {
    if (aBaseParticle == null)
      return aCT.m_aParticle;
    if (aCT.m_aParticle == null)
      return aBaseParticle;
    if (isXsd11 ())
    {
      // XSD 1.1: extending an xs:all with an xs:all yields one all group
      // holding the members of both
      final XsdModelGroup aBG = _allGroupOf (aBaseParticle);
      final XsdModelGroup aEG = _allGroupOf (aCT.m_aParticle);
      if (aBG != null && aEG != null)
      {
        final int nMin = Math.max (aBaseParticle.m_nMin, aCT.m_aParticle.m_nMin);
        final XsdModelGroup aMerged = new XsdModelGroup (EXsdCompositor.ALL);
        aMerged.m_aParticles.addAll (aBG.m_aParticles);
        aMerged.m_aParticles.addAll (aEG.m_aParticles);
        return new XsdParticle (nMin, 1, aMerged);
      }
    }
    final XsdModelGroup aSeq = new XsdModelGroup (EXsdCompositor.SEQUENCE);
    aSeq.m_aParticles.add (aBaseParticle);
    aSeq.m_aParticles.add (aCT.m_aParticle);
    return new XsdParticle (1, 1, aSeq);
  }

  @NonNull
  private static ICommonsList <XsdAttributeUse> _mergeAttrs (@NonNull final ICommonsList <XsdAttributeUse> aBase,
                                                             @NonNull final ICommonsList <XsdAttributeUse> aOwn)
  {
    final ICommonsList <XsdAttributeUse> ret = new CommonsArrayList <> (aBase.size () + aOwn.size ());
    final ICommonsSet <XsdName> aSkip = new CommonsHashSet <> ();
    for (final XsdAttributeUse aUse : aOwn)
      aSkip.add (aUse.m_aDecl.m_aName);
    for (final XsdAttributeUse aUse : aBase)
      if (!aSkip.contains (aUse.m_aDecl.m_aName))
        ret.add (aUse);
    for (final XsdAttributeUse aUse : aOwn)
      if (!aUse.m_bProhibited)
        ret.add (aUse);
    return ret;
  }

  @Nullable
  private XsdOpenContent _effectiveOpenContent (@NonNull final XsdComplexType aCT, final int nDepth)
  {
    if (nDepth > 64)
      return null;
    final ICommonsSet <XsdComplexType> aSeen = new CommonsHashSet <> ();
    XsdComplexType aCur = aCT;
    while (aCur != null && !aSeen.contains (aCur))
    {
      aSeen.add (aCur);
      if (aCur.m_aOpenContent != null)
      {
        if (XsdOpenContent.MODE_NONE.equals (aCur.m_aOpenContent.m_sMode))
        {
          // On an extension step mode="none" adds nothing of its own; on a
          // restriction it shuts open content off
          if (!XsdDerivation.EXTENSION.equals (aCur.m_sDerivation))
            return null;
        }
        else
        {
          final XsdOpenContent aOC = aCur.m_aOpenContent;
          if (XsdDerivation.EXTENSION.equals (aCur.m_sDerivation) &&
              aCur.m_aBaseType instanceof final XsdComplexType aBase)
          {
            // An extension's own open content unions with the base's
            final XsdOpenContent aBaseEff = _effectiveOpenContent (aBase, nDepth + 1);
            if (aBaseEff != null && aBaseEff.m_aWildcard != null && aOC.m_aWildcard != null)
            {
              final XsdWildcard aUnion = new XsdWildcard ();
              aUnion.m_sProcess = aOC.m_aWildcard.m_sProcess;
              aUnion.m_aParts.add (aOC.m_aWildcard);
              aUnion.m_aParts.add (aBaseEff.m_aWildcard);
              return new XsdOpenContent (aOC.m_sMode, aUnion);
            }
          }
          return aOC;
        }
      }
      // A restriction without its own xs:openContent has none
      if (XsdDerivation.RESTRICTION.equals (aCur.m_sDerivation))
        return null;
      aCur = aCur.m_aBaseType instanceof final XsdComplexType aBase ? aBase : null;
    }
    return null;
  }

  /**
   * @return A description of missing functionality that names where it was
   *         hit (the stub's class and method), for diagnostics.
   */
  @NonNull
  private static String _describeUnsupported (@NonNull final UnsupportedOperationException ex)
  {
    final StackTraceElement [] aTrace = ex.getStackTrace ();
    final String sWhere = aTrace.length == 0 ? "" : " (" + aTrace[0].getClassName ().replace ("com.helger.xsd.", "") + "." + aTrace[0].getMethodName () + ")";
    return "Not supported: " + ex.getMessage () + sWhere;
  }

  /**
   * The one place where missing functionality becomes "no verdict" (go-xslt's
   * <code>ErrUnsupported</code> discipline): every
   * {@link UnsupportedOperationException} that escapes the implementation - a
   * phase stub or an explicit {@link XsdUnsupportedException} - leaves the
   * public API as {@link XsdUnsupportedException}. Nothing else catches it.
   */
  private static <T> T _noVerdictOnMissingFunctionality (@NonNull final Supplier <T> aAction)
  {
    try
    {
      return aAction.get ();
    }
    catch (final XsdUnsupportedException ex)
    {
      throw ex;
    }
    catch (final UnsupportedOperationException ex)
    {
      throw new XsdUnsupportedException (_describeUnsupported (ex), ex);
    }
  }

  /** @return The XSD version the schema was compiled for. */
  @NonNull
  public EXsdVersion getVersion ()
  {
    return m_eVersion;
  }

  /** @return The directory of the entry schema document, may be <code>null</code>. */
  @Nullable
  public Path getBaseDir ()
  {
    return m_aBaseDir;
  }

  /** @return The file access rule. */
  @NonNull
  public XsdFileAccess getFileAccess ()
  {
    return m_aFileAccess;
  }

  /** @return <code>true</code> if the schema was compiled for XSD 1.1. */
  boolean isXsd11 ()
  {
    return m_eVersion == EXsdVersion.V11;
  }

  /**
   * The effective content model of a complex type: an extension prepends the
   * base's effective particle (go-xslt <code>effectiveParticle</code>). A new
   * wrapper particle is created per call for an extension.
   *
   * @param aCT
   *        The complex type.
   * @return The particle, <code>null</code> for empty content.
   */
  @Nullable
  XsdParticle effectiveParticle (@NonNull final XsdComplexType aCT)
  {
    // Iterative form of go-xslt's recursion over the extension chain
    final ICommonsList <XsdComplexType> aChain = new CommonsArrayList <> ();
    final ICommonsSet <XsdComplexType> aSeen = new CommonsHashSet <> ();
    XsdComplexType aCur = aCT;
    while (XsdDerivation.EXTENSION.equals (aCur.m_sDerivation) && !aSeen.contains (aCur))
    {
      aSeen.add (aCur);
      if (!(aCur.m_aBaseType instanceof final XsdComplexType aBase))
        break;
      aChain.add (aCur);
      aCur = aBase;
    }
    XsdParticle ret = aCur.m_aParticle;
    for (int i = aChain.size () - 1; i >= 0; i--)
      ret = _combineExtension (aChain.get (i), ret);
    return ret;
  }

  /**
   * The effective attribute uses and wildcard of a complex type (go-xslt
   * <code>effectiveAttrs</code>): own uses overlay the base's by name, a
   * prohibited use removes the inherited one; an extension's wildcard is the
   * union with the base's, a restriction has only its own.
   *
   * @param aCT
   *        The complex type.
   * @return The effective attributes. Never <code>null</code>.
   */
  @NonNull
  XsdEffectiveAttributes effectiveAttrs (@NonNull final XsdComplexType aCT)
  {
    final ICommonsList <XsdComplexType> aChain = new CommonsArrayList <> ();
    final ICommonsSet <XsdComplexType> aSeen = new CommonsHashSet <> ();
    XsdComplexType aCur = aCT;
    while (aCur.m_aBaseType instanceof final XsdComplexType aBase &&
           !aCur.m_sDerivation.isEmpty () &&
           !aSeen.contains (aCur))
    {
      aSeen.add (aCur);
      aChain.add (aCur);
      aCur = aBase;
    }
    ICommonsList <XsdAttributeUse> aUses = aCur.m_aAttrUses;
    XsdWildcard aWC = aCur.m_aAttrWildcard;
    for (int i = aChain.size () - 1; i >= 0; i--)
    {
      final XsdComplexType aStep = aChain.get (i);
      final ICommonsList <XsdAttributeUse> aMerged = _mergeAttrs (aUses, aStep.m_aAttrUses);
      XsdWildcard aStepWC = aStep.m_aAttrWildcard;
      final boolean bExtension = XsdDerivation.EXTENSION.equals (aStep.m_sDerivation);
      if (aStepWC == null)
      {
        // A restriction without its own anyAttribute has no attribute
        // wildcard; only an extension inherits
        if (bExtension)
          aStepWC = aWC;
      }
      else
        if (aWC != null && bExtension)
        {
          // Attribute Wildcard Union
          final XsdWildcard aUnion = new XsdWildcard ();
          aUnion.m_sProcess = aStepWC.m_sProcess;
          aUnion.m_aParts.add (aStepWC);
          aUnion.m_aParts.add (aWC);
          aStepWC = aUnion;
        }
      aUses = aMerged;
      aWC = aStepWC;
    }
    return new XsdEffectiveAttributes (aUses, aWC);
  }

  /**
   * The nearest {open content} of the derivation chain (XSD 1.1, go-xslt
   * <code>effectiveOpenContent</code>); an explicit mode "none" shadows an
   * inherited one.
   *
   * @param aCT
   *        The complex type.
   * @return The open content or <code>null</code>.
   */
  @Nullable
  XsdOpenContent effectiveOpenContent (@NonNull final XsdComplexType aCT)
  {
    return _effectiveOpenContent (aCT, 0);
  }

  /**
   * go-xslt <code>substitutionAllowed</code>: whether a member may stand in
   * for a head in a content model. The head's {disallowed substitutions} - its
   * own block plus the block of its declared type - bars substitution outright
   * when it contains "substitution", and otherwise bars a member whose type
   * reaches the head's type through a blocked derivation method.
   */
  boolean substitutionAllowed (@NonNull final XsdElementDecl aHead, @NonNull final XsdElementDecl aMember)
  {
    final ICommonsSet <String> aBlocked = new CommonsHashSet <> (aHead.m_aBlocked);
    if (aHead.m_aType instanceof final XsdComplexType aHT)
      aBlocked.addAll (aHT.m_aBlock);
    if (aBlocked.isEmpty ())
      return true;
    if (aBlocked.contains (XsdDerivation.SUBSTITUTION))
      return false;
    if (aHead.m_aType == null || aMember.m_aType == null || aMember.m_aType == aHead.m_aType)
      return true;
    for (final String sMethod : XsdDerivation.derivationMethodsTo (aMember.m_aType, aHead.m_aType))
      if (aBlocked.contains (sMethod))
        return false;
    return true;
  }

  /**
   * go-xslt <code>matchesElementName</code>: whether an instance element named
   * <code>aName</code> is governed by the declaration, directly or through its
   * substitution group (which expands only from the global declaration).
   */
  boolean matchesElementName (@NonNull final XsdElementDecl aDecl, @NonNull final XsdName aName)
  {
    if (aName.equals (aDecl.m_aName) && !aDecl.m_bAbstract)
      return true;
    if (m_aElements.get (aDecl.m_aName) != aDecl)
      return false;
    final ICommonsSet <XsdName> aSeen = new CommonsHashSet <> ();
    final ICommonsList <XsdName> aStack = new CommonsArrayList <> ();
    aStack.add (aDecl.m_aName);
    while (aStack.isNotEmpty ())
    {
      final XsdName aHead = aStack.removeLast ();
      final ICommonsList <XsdElementDecl> aMembers = m_aSubstMembers.get (aHead);
      if (aMembers == null)
        continue;
      for (final XsdElementDecl aMember : aMembers)
      {
        if (aSeen.contains (aMember.m_aName))
          continue;
        aSeen.add (aMember.m_aName);
        if (aMember.m_aName.equals (aName) && !aMember.m_bAbstract && substitutionAllowed (aDecl, aMember))
          return true;
        aStack.add (aMember.m_aName);
      }
    }
    return false;
  }

  /**
   * go-xslt <code>contentDecls</code>: the element declarations of a content
   * model by name (a UPA-conforming model binds each name to one
   * declaration; a later particle wins). Iterative.
   *
   * @param aParticle
   *        The content model; <code>null</code> for none.
   * @param aOut
   *        Receives name to declaration.
   */
  static void contentDecls (@Nullable final XsdParticle aParticle,
                            @NonNull final ICommonsMap <XsdName, XsdElementDecl> aOut)
  {
    if (aParticle == null)
      return;
    final ICommonsList <XsdParticle> aStack = new CommonsArrayList <> ();
    aStack.add (aParticle);
    while (aStack.isNotEmpty ())
    {
      final XsdParticle p = aStack.removeLast ();
      if (p.m_aTerm instanceof final XsdElementDecl aDecl)
        aOut.put (aDecl.m_aName, aDecl);
      else
        if (p.m_aTerm instanceof final XsdModelGroup aMG)
          for (int i = aMG.m_aParticles.size () - 1; i >= 0; i--)
            aStack.add (aMG.m_aParticles.get (i));
    }
  }

  /** @return The direct substitution-group members of a head, never <code>null</code>. */
  @NonNull
  ICommonsList <XsdElementDecl> substMembersOf (@NonNull final XsdName aHead)
  {
    final ICommonsList <XsdElementDecl> ret = m_aSubstMembers.get (aHead);
    return ret == null ? new CommonsArrayList <> () : ret;
  }

  /** @return The read-only component model of this schema. */
  @NonNull
  public XsdSchemaView getView ()
  {
    return new XsdSchemaView (this);
  }

  /**
   * Compile schema documents (go-xslt <code>Compile</code>). The first document
   * is the entry document; every other one contributes its components too. No
   * documents at all is the schema of the built-in components only: an
   * instance validated against it is assessed by its own
   * <code>xsi:schemaLocation</code> hints and <code>xsi:type</code>.
   *
   * @param aSources
   *        The schema documents. May not be <code>null</code>.
   * @param eVersion
   *        The XSD version.
   * @param aOptions
   *        The options; <code>null</code> for the defaults.
   * @return The compiled schema. Never <code>null</code>.
   * @throws XsdInvalidException
   *         if the schema set is in error
   * @throws XsdUnsupportedException
   *         if the schema uses a construct that is not implemented (no
   *         verdict)
   */
  @NonNull
  public static XsdSchema compile (@NonNull final List <XsdSchemaSource> aSources,
                                   @NonNull final EXsdVersion eVersion,
                                   @Nullable final XsdCompileOptions aOptions)
  {
    ValueEnforcer.notNull (aSources, "Sources");
    ValueEnforcer.notNull (eVersion, "Version");
    final XsdCompileOptions aRealOptions = aOptions != null ? aOptions : new XsdCompileOptions ();
    return _noVerdictOnMissingFunctionality ( () -> new XsdCompiler (eVersion, aRealOptions).compile (aSources));
  }

  /**
   * Compile schema document files.
   *
   * @param aFiles
   *        The schema documents; the first is the entry document.
   * @param eVersion
   *        The XSD version.
   * @param aOptions
   *        The options; <code>null</code> for the defaults.
   * @return The compiled schema. Never <code>null</code>.
   * @throws IOException
   *         if a given file cannot be read
   * @throws XsdInvalidException
   *         if the schema set is in error
   * @throws XsdUnsupportedException
   *         if the schema uses a construct that is not implemented
   */
  @NonNull
  public static XsdSchema compileFiles (@NonNull final List <Path> aFiles,
                                        @NonNull final EXsdVersion eVersion,
                                        @Nullable final XsdCompileOptions aOptions) throws IOException
  {
    ValueEnforcer.notNull (aFiles, "Files");
    final ICommonsList <XsdSchemaSource> aSources = new CommonsArrayList <> ();
    for (final Path aFile : aFiles)
      aSources.add (XsdSchemaSource.ofFile (aFile));
    return compile (aSources, eVersion, aOptions);
  }

  /**
   * Assess the schema-validity of an instance document, strictly from its
   * document element (go-xslt <code>Schema.Validate</code>).
   *
   * @param aInstance
   *        The instance file.
   * @return The result. Never <code>null</code>.
   * @throws XsdUnsupportedException
   *         if the instance or schema uses a construct that is not implemented
   *         (no verdict)
   */
  @NonNull
  public XsdValidationResult validate (@NonNull final Path aInstance)
  {
    ValueEnforcer.notNull (aInstance, "Instance");
    return _noVerdictOnMissingFunctionality ( () -> XsdInstanceValidator.validate (this, aInstance));
  }

  /**
   * Assess the schema-validity of instance document text (go-xslt
   * <code>Schema.Validate</code>).
   *
   * @param sInstance
   *        The instance document text.
   * @return The result. Never <code>null</code>.
   * @throws XsdUnsupportedException
   *         if the instance or schema uses a construct that is not implemented
   *         (no verdict)
   */
  @NonNull
  public XsdValidationResult validateText (@NonNull final String sInstance)
  {
    ValueEnforcer.notNull (sInstance, "Instance");
    return _noVerdictOnMissingFunctionality ( () -> XsdInstanceValidator.validateText (this, sInstance));
  }

  @Override
  public String toString ()
  {
    return "XsdSchema[" +
           m_eVersion.getID () +
           ", " +
           m_aElements.size () +
           " elements, " +
           m_aTypes.size () +
           " types, " +
           m_aAttributes.size () +
           " attributes]";
  }
}
