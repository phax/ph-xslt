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
package com.helger.xsd.conformance;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.IXsdTermView;
import com.helger.xsd.IXsdTypeDefinitionView;
import com.helger.xsd.XsdAttributeDeclView;
import com.helger.xsd.XsdAttributeUseView;
import com.helger.xsd.XsdComplexTypeView;
import com.helger.xsd.XsdConstants;
import com.helger.xsd.XsdElementView;
import com.helger.xsd.XsdInvalidException;
import com.helger.xsd.XsdModelGroupView;
import com.helger.xsd.XsdParticleView;
import com.helger.xsd.XsdSchema;
import com.helger.xsd.XsdSchemaView;
import com.helger.xsd.XsdSimpleTypeView;
import com.helger.xsd.XsdUnsupportedException;
import com.helger.xsd.XsdWildcardView;

/**
 * Compiles every schema document of the xsdtests suite that the native
 * compiler accepts (as a stand-alone entry schema) and walks its whole public
 * component model, with a guard against the cycles recursive types make. It
 * checks nothing about the values - only that the model can be walked for any
 * schema the compiler accepts, without an exception or a <code>null</code>
 * where the API promises a value (go-xslt
 * <code>TestSchemaModelWalksTheXSDCorpus</code>).
 *
 * @author Philip Helger
 */
public final class XsdSchemaModelWalkTest
{
  private static final Logger LOGGER = LoggerFactory.getLogger (XsdSchemaModelWalkTest.class);

  /** The walk of one schema; the stack is explicit, so deep models are no problem. */
  private static final class Walker
  {
    private final Path m_aFile;
    private final ICommonsList <String> m_aProblems;
    private final Set <Object> m_aSeen = new CommonsHashSet <> ();
    private final ICommonsList <Object> m_aStack = new CommonsArrayList <> ();

    Walker (final Path aFile, final ICommonsList <String> aProblems)
    {
      m_aFile = aFile;
      m_aProblems = aProblems;
    }

    private void _problem (final String sWhat)
    {
      m_aProblems.add (m_aFile + ": " + sWhat);
    }

    private void _push (final Object aItem)
    {
      if (aItem != null && m_aSeen.add (aItem))
        m_aStack.add (aItem);
    }

    private void _particle (final XsdParticleView p)
    {
      if (p == null || p.term () == null)
      {
        _problem ("null particle/term");
        return;
      }
      if (p.minOccurs () < 0 || p.maxOccurs () != XsdConstants.UNBOUNDED && p.maxOccurs () < p.minOccurs ())
        _problem ("bad occurs " + p.minOccurs () + ".." + p.maxOccurs ());
      final IXsdTermView aTerm = p.term ();
      if (aTerm instanceof final XsdElementView aElement)
        _push (aElement);
      else
        if (aTerm instanceof final XsdModelGroupView aGroup)
        {
          for (final XsdParticleView aSub : aGroup.particles ())
            _particle (aSub);
        }
        else
          if (!(aTerm instanceof XsdWildcardView))
            _problem ("unknown term " + aTerm);
    }

    void walk (final XsdSchemaView s)
    {
      for (final XsdElementView aElement : s.getAllGlobalElements ())
      {
        _push (aElement);
        s.getAllSubstitutionMembers (aElement.getName ());
      }
      for (final IXsdTypeDefinitionView aType : s.getAllGlobalTypes ())
        _push (aType);
      for (final XsdAttributeDeclView aAttr : s.getAllGlobalAttributes ())
        _push (aAttr.getType ());
      while (m_aStack.isNotEmpty ())
      {
        final Object aItem = m_aStack.removeLast ();
        if (aItem instanceof final XsdElementView aElement)
        {
          aElement.getName ();
          aElement.getSubstitutionGroups ();
          if (aElement.getType () == null)
            _problem ("element " + aElement.getName () + " has no type");
          else
            _push (aElement.getType ());
        }
        else
          if (aItem instanceof final XsdSimpleTypeView aST)
          {
            aST.getName ();
            aST.getBuiltinBase ();
            aST.getVariety ();
            aST.getEnumeration ();
            aST.getFacets ();
            _push (aST.getBase ());
            _push (aST.getItemType ());
            for (final XsdSimpleTypeView aMember : aST.getMemberTypes ())
              _push (aMember);
          }
          else
            if (aItem instanceof final XsdComplexTypeView aCT)
            {
              aCT.getName ();
              aCT.getBuiltinBase ();
              aCT.isMixed ();
              aCT.getContentKind ();
              aCT.getDerivation ();
              aCT.getAttributeWildcard ();
              _push (aCT.getSimpleContent ());
              _push (aCT.getBase ());
              final XsdParticleView aModel = aCT.getContentModel ();
              if (aModel != null)
                _particle (aModel);
              aCT.getContentParticles ();
              for (final XsdAttributeUseView aUse : aCT.getAttributes ())
                _push (aUse.type ());
            }
      }
      s.getAllUnresolved ();
    }
  }

  @Test
  public void testWalkTheCorpus () throws IOException
  {
    final XsdSettings aSettings = XsdSettings.fromSystemProperties ();
    Assume.assumeTrue ("xsdtests suite not present at " + aSettings.suiteDir (),
                       Files.isRegularFile (aSettings.getSuiteFile ()));
    final ICommonsList <Path> aFiles = new CommonsArrayList <> ();
    try (final Stream <Path> aStream = Files.walk (aSettings.suiteDir ()))
    {
      aStream.filter (p -> p.toString ().endsWith (".xsd") && Files.isRegularFile (p)).sorted ().forEach (aFiles::add);
    }
    final ICommonsList <String> aProblems = new CommonsArrayList <> ();
    int nCompiled = 0;
    for (final Path aFile : aFiles)
    {
      final ICommonsList <Path> aOne = new CommonsArrayList <> ();
      aOne.add (aFile);
      final XsdSchema aSchema;
      try
      {
        aSchema = XsdSchema.compileFiles (aOne, EXsdVersion.V10, null);
      }
      catch (final XsdInvalidException | XsdUnsupportedException | IOException ex)
      {
        continue;
      }
      nCompiled++;
      try
      {
        new Walker (aFile, aProblems).walk (aSchema.getView ());
      }
      catch (final RuntimeException ex)
      {
        aProblems.add (aFile + ": walk failed: " + ex);
      }
    }
    LOGGER.info (aFiles.size () + " schema documents, " + nCompiled + " compiled and walked");
    assertTrue (String.join ("\n", aProblems), aProblems.isEmpty ());
    assertTrue (nCompiled > 0);
  }
}
