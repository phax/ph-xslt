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
package com.helger.xslt.ext.charactermap;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.OutputDefinition;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslModule;
import com.helger.xslt.tree.XsltNames;

/**
 * xsl:character-map and xsl:output-character (XSLT 3.0 section 26.1), and the
 * use-character-maps attribute of xsl:output and xsl:result-document: the
 * declarations are collected per stylesheet (the highest import precedence
 * wins, XTSE1580 for two of the same name and precedence), references are
 * checked after compilation (XTSE1590 for an unknown map, XTSE1600 for a
 * circular use-character-maps chain), and the merged mapping is handed to the
 * serializer through an output customizer.
 *
 * @author Philip Helger
 */
public final class CharacterMapExtension implements IXsltExtension
{
  /** The key of the character maps in the extension data of a stylesheet. */
  private static final Object DATA_KEY = new Object ();

  /** One compiled xsl:character-map. */
  private record CharacterMap (@NonNull XslElement element,
                               int precedence,
                               @NonNull ICommonsList <XdmQName> uses,
                               @NonNull ICommonsOrderedMap <Integer, String> mappings)
  {}

  @NonNull
  @SuppressWarnings ("unchecked")
  private static ICommonsMap <XdmQName, CharacterMap> _getMaps (@NonNull final CompiledStylesheet aSheet)
  {
    Object aData = aSheet.getExtensionData (DATA_KEY);
    if (aData == null)
    {
      aData = new CommonsHashMap <XdmQName, CharacterMap> ();
      aSheet.putExtensionData (DATA_KEY, aData);
    }
    return (ICommonsMap <XdmQName, CharacterMap>) aData;
  }

  @NonNull
  private static ICommonsList <XdmQName> _resolveNames (@NonNull final XslElement aEl, @Nullable final String sValue)
  {
    final ICommonsList <XdmQName> ret = new CommonsArrayList <> ();
    if (sValue != null)
      for (final String sToken : XsltNames.tokenize (sValue))
      {
        final XdmQName aName = aEl.resolveQName (sToken, false);
        if (aName == null)
          throw XsltException.at (aEl,
                                  "XTSE0280",
                                  "'" + sToken + "' in use-character-maps is not a valid QName with a bound prefix");
        ret.add (aName);
      }
    return ret;
  }

  private static void _compileCharacterMap (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XdmQName aName = aCtx.resolveQNameAttr (aEl, "name", false);
    final ICommonsMap <XdmQName, CharacterMap> aMaps = _getMaps (aCtx.getStylesheet ());
    final CharacterMap aPrev = aMaps.get (aName);
    if (aPrev != null && aPrev.precedence () == aEl.getPrecedence ())
      throw XsltException.at (aEl,
                              "XTSE1580",
                              "There are two xsl:character-map declarations named " +
                                   aName.getEQName () +
                                   " with the same import precedence");
    if (aPrev != null && aPrev.precedence () > aEl.getPrecedence ())
      return;
    final ICommonsOrderedMap <Integer, String> aMappings = new CommonsLinkedHashMap <> ();
    for (final XslElement aChild : aEl.getXslChildren ("output-character"))
    {
      final String sChar = aChild.getAttr ("character");
      final String sString = aChild.getAttr ("string");
      if (sChar == null || sChar.codePointCount (0, sChar.length ()) != 1)
        throw XsltException.at (aChild,
                                "XTSE0020",
                                "The character attribute of xsl:output-character must be a single character");
      aMappings.put (Integer.valueOf (sChar.codePointAt (0)), sString == null ? "" : sString);
    }
    aMaps.put (aName, new CharacterMap (aEl, aEl.getPrecedence (), _resolveNames (aEl, aEl.getAttr ("use-character-maps")), aMappings));
  }

  /**
   * XTSE1600: a character map must not use itself, directly or indirectly.
   * Iterative depth-first search with three colours.
   */
  private static void _checkCycles (@NonNull final ICommonsMap <XdmQName, CharacterMap> aMaps)
  {
    final ICommonsMap <XdmQName, Integer> aColour = new CommonsHashMap <> ();
    final Integer aGrey = Integer.valueOf (1);
    final Integer aBlack = Integer.valueOf (2);
    for (final XdmQName aStart : aMaps.keySet ())
    {
      if (aColour.containsKey (aStart))
        continue;
      // Stack of (name, index of the next use to visit)
      final ArrayDeque <Object []> aStack = new ArrayDeque <> ();
      aColour.put (aStart, aGrey);
      aStack.push (new Object [] { aStart, Integer.valueOf (0) });
      while (!aStack.isEmpty ())
      {
        final Object [] aTop = aStack.peek ();
        final XdmQName aName = (XdmQName) aTop[0];
        final int nNext = ((Integer) aTop[1]).intValue ();
        final CharacterMap aMap = aMaps.get (aName);
        if (nNext >= aMap.uses ().size ())
        {
          aColour.put (aName, aBlack);
          aStack.pop ();
          continue;
        }
        aTop[1] = Integer.valueOf (nNext + 1);
        final XdmQName aDep = aMap.uses ().get (nNext);
        final Integer aDepColour = aColour.get (aDep);
        if (aGrey.equals (aDepColour))
          throw XsltException.at (aMap.element (),
                                  "XTSE1600",
                                  "The character map " + aDep.getEQName () + " uses itself, directly or indirectly");
        if (aDepColour == null)
        {
          aColour.put (aDep, aGrey);
          aStack.push (new Object [] { aDep, Integer.valueOf (0) });
        }
      }
    }
  }

  private static void _checkKnown (@NonNull final ICommonsMap <XdmQName, CharacterMap> aMaps,
                                   @NonNull final XslElement aEl,
                                   @NonNull final List <XdmQName> aNames)
  {
    for (final XdmQName aName : aNames)
      if (!aMaps.containsKey (aName))
        throw XsltException.at (aEl, "XTSE1590", "There is no character map named " + aName.getEQName ());
  }

  /**
   * Check the use-character-maps attribute of xsl:result-document (XTSE1590)
   * and the declarations themselves after compilation.
   */
  private static void _finish (@NonNull final CompileContext aCtx)
  {
    final CompiledStylesheet aSheet = aCtx.getStylesheet ();
    final ICommonsMap <XdmQName, CharacterMap> aMaps = _getMaps (aSheet);
    for (final CharacterMap aMap : aMaps.values ())
      _checkKnown (aMaps, aMap.element (), aMap.uses ());
    _checkCycles (aMaps);
    // use-character-maps of xsl:output and xsl:result-document anywhere in
    // the stylesheet
    for (final XslModule aModule : aSheet.modules ())
    {
      final XslElement aRoot = aModule.getRoot ();
      if (aRoot == null)
        continue;
      final ArrayDeque <XslElement> aStack = new ArrayDeque <> ();
      aStack.push (aRoot);
      while (!aStack.isEmpty ())
      {
        final XslElement aEl = aStack.pop ();
        if ((aEl.isXsl ("output") || aEl.isXsl ("result-document")) && aEl.hasAttr ("use-character-maps"))
          _checkKnown (aMaps, aEl, _resolveNames (aEl, aEl.getAttr ("use-character-maps")));
        for (final XslElement aChild : aEl.getElementChildren ())
          aStack.push (aChild);
      }
    }
  }

  /**
   * Merge the mappings of the named character maps: the maps a map uses come
   * before its own mappings, and later mappings replace earlier ones for the
   * same character.
   *
   * @param aSheet
   *        The stylesheet.
   * @param aNames
   *        The names from use-character-maps, in order.
   * @return The merged mapping (code point to replacement), never
   *         <code>null</code>.
   */
  @NonNull
  public static ICommonsOrderedMap <Integer, String> resolve (@NonNull final CompiledStylesheet aSheet,
                                                             @NonNull final List <XdmQName> aNames)
  {
    final ICommonsOrderedMap <Integer, String> ret = new CommonsLinkedHashMap <> ();
    if (aNames.isEmpty ())
      return ret;
    final ICommonsMap <XdmQName, CharacterMap> aMaps = _getMaps (aSheet);
    final ICommonsSet <XdmQName> aVisited = new CommonsHashSet <> ();
    for (final XdmQName aTop : aNames)
    {
      // Post-order: the maps used by a map are applied before its own
      // mappings
      final ArrayDeque <Object []> aStack = new ArrayDeque <> ();
      if (aVisited.add (aTop) && aMaps.containsKey (aTop))
        aStack.push (new Object [] { aTop, Integer.valueOf (0) });
      while (!aStack.isEmpty ())
      {
        final Object [] aEntry = aStack.peek ();
        final CharacterMap aMap = aMaps.get ((XdmQName) aEntry[0]);
        final int nNext = ((Integer) aEntry[1]).intValue ();
        if (nNext < aMap.uses ().size ())
        {
          aEntry[1] = Integer.valueOf (nNext + 1);
          final XdmQName aDep = aMap.uses ().get (nNext);
          if (aVisited.add (aDep) && aMaps.containsKey (aDep))
            aStack.push (new Object [] { aDep, Integer.valueOf (0) });
          continue;
        }
        aStack.pop ();
        ret.putAll (aMap.mappings ());
      }
    }
    return ret;
  }

  /**
   * Apply the character maps named by an output definition to serialization
   * parameters. Mappings given inline (in a serialization parameter document)
   * that are already in the parameters take precedence.
   *
   * @param aSheet
   *        The stylesheet.
   * @param aDefinition
   *        The output definition.
   * @param aParams
   *        The parameters to modify.
   */
  public static void applyCharacterMaps (@NonNull final CompiledStylesheet aSheet,
                                         @NonNull final OutputDefinition aDefinition,
                                         @NonNull final SerializationParams aParams)
  {
    final ICommonsList <XdmQName> aNames = aDefinition.getUseCharacterMaps ();
    if (aNames.isEmpty ())
      return;
    final ICommonsOrderedMap <Integer, String> aMerged = resolve (aSheet, aNames);
    if (aMerged.isEmpty ())
      return;
    for (final Map.Entry <Integer, String> aEntry : aParams.getCharacterMap ().entrySet ())
      aMerged.put (aEntry.getKey (), aEntry.getValue ());
    aParams.setCharacterMap (aMerged);
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerDeclaration ("character-map", CharacterMapExtension::_compileCharacterMap);
    aRegistry.registerCompileFinisher (CharacterMapExtension::_finish);
    aRegistry.registerOutputCustomizer (CharacterMapExtension::applyCharacterMaps);
  }
}
