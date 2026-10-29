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
package com.helger.xslt.pkg;

import java.io.StringReader;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.FileResourceResolver;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * The library packages available to xsl:use-package in one compilation, and
 * their resolution by name and package-version ranges (XSLT 3.0 section
 * 3.5.2). Where several versions qualify, the highest one is used.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class PackageRegistry
{
  /**
   * A resolved package.
   *
   * @param key
   *        The identity of the package (its URI, else its position).
   * @param name
   *        The package name.
   * @param version
   *        The package version.
   * @param root
   *        The xsl:package element of its source.
   * @param baseUri
   *        The URI of the package module or <code>null</code>.
   */
  public record ResolvedPackage (@NonNull String key,
                                 @NonNull String name,
                                 @NonNull PackageVersion version,
                                 @NonNull IXdmNode root,
                                 @Nullable String baseUri)
  {}

  private static final class Entry
  {
    private final XsltCompileOptions.PackageSource m_aSource;
    private final int m_nIndex;
    private boolean m_bLoaded;
    private boolean m_bNotPackage;
    private IXdmNode m_aRoot;
    private String m_sName;
    private String m_sVersion;

    Entry (final XsltCompileOptions.@NonNull PackageSource aSource, final int nIndex)
    {
      m_aSource = aSource;
      m_nIndex = nIndex;
    }
  }

  private final XsltCompileOptions m_aOptions;
  private final ICommonsList <Entry> m_aEntries = new CommonsArrayList <> ();

  public PackageRegistry (@NonNull final XsltCompileOptions aOptions)
  {
    m_aOptions = aOptions;
    int i = 0;
    for (final XsltCompileOptions.PackageSource aSource : aOptions.packages ())
      m_aEntries.add (new Entry (aSource, i++));
  }

  @Nullable
  private static IXdmNode _getDocumentElement (@Nullable final IXdmNode aNode)
  {
    if (aNode == null || aNode.isElement ())
      return aNode;
    for (int i = 0; i < aNode.getChildCount (); ++i)
      if (aNode.getChildAtIndex (i).isElement ())
        return aNode.getChildAtIndex (i);
    return null;
  }

  @Nullable
  private IXdmNode _parse (final XsltCompileOptions.@NonNull PackageSource aSource)
  {
    if (aSource.node () != null)
      return _getDocumentElement (aSource.node ());
    try
    {
      if (aSource.text () != null)
      {
        final InputSource aIS = new InputSource (new StringReader (aSource.text ()));
        if (aSource.baseUri () != null)
          aIS.setSystemId (aSource.baseUri ());
        final IXdmNode aDoc = new XdmParser ().setLoadExternalEntities (m_aOptions.isLoadExternalEntities ()).parse (aIS);
        if (aSource.baseUri () != null && aDoc.getDocumentUri () == null)
          aDoc.setDocumentUri (aSource.baseUri ());
        return _getDocumentElement (aDoc);
      }
    }
    catch (final XdmParseException ex)
    {
      return null;
    }
    if (aSource.baseUri () == null)
      return null;
    final IResourceResolver aResolver = m_aOptions.getResolver () != null ? m_aOptions.getResolver ()
                                                                         : new FileResourceResolver (m_aOptions.isLoadExternalEntities ());
    try
    {
      final IXdmNode aDoc = aResolver.resolveDocument (aSource.baseUri ());
      if (aDoc != null && aDoc.isDocument () && aDoc.getDocumentUri () == null)
        aDoc.setDocumentUri (aSource.baseUri ());
      return _getDocumentElement (aDoc);
    }
    catch (final RuntimeException ex)
    {
      return null;
    }
  }

  private void _load (@NonNull final Entry aEntry)
  {
    if (aEntry.m_bLoaded)
      return;
    aEntry.m_bLoaded = true;
    final IXdmNode aRoot = _parse (aEntry.m_aSource);
    if (aRoot != null &&
        XsltNames.NS_XSL.equals (aRoot.getName ().namespaceUri ()) &&
        "package".equals (aRoot.getName ().localName ()))
    {
      aEntry.m_aRoot = aRoot;
      final IXdmNode aName = aRoot.getAttribute ("", "name");
      final IXdmNode aVersion = aRoot.getAttribute ("", "package-version");
      aEntry.m_sName = aName == null ? null : aName.getValue ().trim ();
      aEntry.m_sVersion = aVersion == null ? null : aVersion.getValue ().trim ();
    }
    else
      if (aRoot != null)
        aEntry.m_bNotPackage = true;
    if (aEntry.m_aSource.name () != null)
      aEntry.m_sName = aEntry.m_aSource.name ().trim ();
    if (aEntry.m_aSource.version () != null && !aEntry.m_aSource.version ().isBlank ())
      aEntry.m_sVersion = aEntry.m_aSource.version ().trim ();
  }

  /**
   * Find a package by name and package-version ranges.
   *
   * @param sName
   *        The package name.
   * @param aRanges
   *        The acceptable versions.
   * @return The package with the highest version that matches or
   *         <code>null</code> if none matches.
   */
  @Nullable
  public ResolvedPackage find (@NonNull final String sName, @NonNull final PackageVersionRanges aRanges)
  {
    Entry aBest = null;
    PackageVersion aBestVersion = null;
    for (final Entry aEntry : m_aEntries)
    {
      _load (aEntry);
      if (aEntry.m_aRoot == null || !sName.equals (aEntry.m_sName))
        continue;
      final PackageVersion aVersion = PackageVersion.parse (aEntry.m_sVersion == null || aEntry.m_sVersion.isEmpty () ? PackageVersion.DEFAULT_VERSION
                                                                                                                      : aEntry.m_sVersion);
      if (aVersion == null || !aRanges.matches (aVersion))
        continue;
      if (aBest == null || aVersion.compareTo (aBestVersion) > 0)
      {
        aBest = aEntry;
        aBestVersion = aVersion;
      }
    }
    if (aBest == null)
      return null;
    final String sBaseUri = aBest.m_aSource.baseUri () != null ? aBest.m_aSource.baseUri () : aBest.m_aRoot.getRoot ().getDocumentUri ();
    final String sKey = sBaseUri != null ? sBaseUri : "#package" + aBest.m_nIndex;
    return new ResolvedPackage (sKey, sName, aBestVersion, aBest.m_aRoot, sBaseUri);
  }

  /**
   * Resolve the package an xsl:use-package names.
   *
   * @param aUse
   *        The xsl:use-package element.
   * @return The package with the highest version that matches.
   * @throws XsltException
   *         XTSE0010 without a name, XTSE0020 for invalid package-version
   *         ranges, XTSE0165 if the source of that name is not a package,
   *         XTSE3000 if no package matches
   */
  @NonNull
  public ResolvedPackage resolve (@NonNull final XslElement aUse)
  {
    if (aUse.getAttr ("name") == null)
      throw XsltException.at (aUse, "XTSE0010", "xsl:use-package requires a name attribute");
    final String sName = aUse.getAttr ("name").trim ();
    final String sRanges = aUse.getAttr ("package-version") == null ? "*" : aUse.getAttr ("package-version").trim ();
    final PackageVersionRanges aRanges = PackageVersionRanges.parse (sRanges);
    if (aRanges == null)
      throw XsltException.at (aUse, "XTSE0020", "'" + sRanges + "' is not a valid package-version range");
    final ResolvedPackage ret = find (sName, aRanges);
    if (ret == null)
    {
      // Section 3.11.1 applied to packages: the resource supplied for the name
      // does not contain a package
      for (final Entry aEntry : m_aEntries)
        if (aEntry.m_bNotPackage && sName.equals (aEntry.m_sName))
          throw XsltException.at (aUse, "XTSE0165", "The package source for '" + sName + "' is not an xsl:package");
      throw XsltException.at (aUse,
                              "XTSE3000",
                              "No package named '" + sName + "' with a version matching '" + sRanges + "' is available");
    }
    return ret;
  }
}
