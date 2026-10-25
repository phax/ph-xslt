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
package com.helger.xslt.spec;

import static com.helger.xslt.spec.XsltSpecTestHelper.err;
import static com.helger.xslt.spec.XsltSpecTestHelper.ok;
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunAll;
import static com.helger.xslt.spec.XsltSpecTestHelper.specSheet;
import static com.helger.xslt.spec.XsltSpecTestHelper.specWrite;
import static org.junit.Assert.fail;

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltCompiler;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.compile.FileResourceResolver;

/**
 * Port of go-xslt's <code>internal/xslt/resolver_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class ResolverSpecTest
{
  /**
   * The host-supplied collections of go-xslt's test: the default collection,
   * "mine" and "frag", relative to one directory.
   */
  private static final class CollectionResolver implements IResourceResolver
  {
    private final Path m_aDir;

    CollectionResolver (@NonNull final Path aDir)
    {
      m_aDir = aDir;
    }

    @NonNull
    private XdmNode _doc (@NonNull final String sRel)
    {
      return FileResourceResolver.INSTANCE.resolveDocument (m_aDir.resolve (sRel).toUri ().toString ());
    }

    @Nullable
    public XdmNode resolveDocument (@NonNull final String sAbsoluteUri)
    {
      return FileResourceResolver.INSTANCE.resolveDocument (sAbsoluteUri);
    }

    @Nullable
    public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
    {
      return FileResourceResolver.INSTANCE.resolveText (sAbsoluteUri, sEncoding);
    }

    @Override
    @Nullable
    public ICommonsList <? extends IXdmItem> resolveCollection (@NonNull final String sAbsoluteUri)
    {
      if (sAbsoluteUri.isEmpty ())
        return new CommonsArrayList <> (_doc ("c/one.xml"), _doc ("c/two.xml"));
      if (sAbsoluteUri.endsWith ("/mine"))
        return new CommonsArrayList <> (_doc ("c/one.xml"));
      if (sAbsoluteUri.endsWith ("/frag"))
      {
        // ids.xml#x1: the element with that xml:id
        final XdmNode aRoot = _doc ("ids.xml").getChildAtIndex (0);
        for (int i = 0; i < aRoot.getChildCount (); ++i)
          if (aRoot.getChildAtIndex (i).isElement ())
            return new CommonsArrayList <> (aRoot.getChildAtIndex (i));
      }
      return null;
    }
  }

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  /**
   * F&amp;O 3.1 §14.6 fn:collection / fn:uri-collection with host-supplied
   * collections (XSLT 3.0 leaves available collections to the host), plus
   * fn:doc, fn:doc-available and fn:unparsed-text* resolved against the run's
   * base directory.
   */
  @Test
  public void resolverSpecCollections ()
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    specWrite (aDir, "c/one.xml", "<d n=\"1\"/>");
    specWrite (aDir, "c/two.xml", "<d n=\"2\"/>");
    specWrite (aDir, "c/skip.txt", "not xml");
    specWrite (aDir, "ids.xml", "<r><e xml:id=\"x1\">X</e></r>");
    specWrite (aDir, "lines.txt", "a\nb\r\nc");
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    aOptions.setBaseUri (aDir.resolve ("main.xsl").toUri ().toString ());
    // go-xslt also runs collection('c?select=*.xml'), a directory collection
    // of its own resolver; the collection URIs a processor understands are
    // implementation-defined (F&O 3.1 §14.6.6), so that case is not ported.
    final XsltExecutable aExec = XsltCompiler.compile (specSheet ("""
        <xsl:template name="default"><xsl:value-of select="sort(collection()/d/@n)"/></xsl:template>
        <xsl:template name="named"><xsl:value-of select="count(collection('mine'))"/></xsl:template>
        <xsl:template name="frag"><xsl:value-of select="collection('frag')"/></xsl:template>
        <xsl:template name="text"><xsl:value-of select="unparsed-text('lines.txt') = concat('a', codepoints-to-string(10), 'b', codepoints-to-string(13), codepoints-to-string(10), 'c'), unparsed-text-lines('lines.txt'), unparsed-text-available('lines.txt'), unparsed-text-available('nope.txt')" separator="|"/></xsl:template>
        <xsl:template name="doc"><xsl:value-of select="doc('c/one.xml')/d/@n, doc-available('c/two.xml'), doc-available('missing.xml'), doc('c/one.xml') is doc('c/one.xml')" separator="|"/></xsl:template>
        <xsl:template name="docerr"><xsl:value-of select="doc('missing.xml')"/></xsl:template>
        <xsl:template name="texterr"><xsl:value-of select="unparsed-text('missing.txt')"/></xsl:template>
        """), aOptions);
    final String [] [] aCases = { { "default", "1 2", null },
                                  { "named", "1", null },
                                  { "frag", "X", null },
                                  { "text", "true|a|b|c|true|false", null },
                                  { "doc", "1|true|false|true", null },
                                  // F&O §14.6.1: FODC0002 when the document cannot
                                  // be retrieved.
                                  { "docerr", null, "FODC0002" },
                                  // F&O §14.8.1: FOUT1170 when the resource cannot
                                  // be retrieved.
                                  { "texterr", null, "FOUT1170" } };
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : aCases)
    {
      try
      {
        final String sOut = aExec.transform (new XsltTransformRequest ().setInitialTemplate (new XdmQName (aCase[0]))
                                                                        .setResolver (new CollectionResolver (aDir)))
                                 .principalSerialized ();
        if (aCase[2] != null)
          aErrors.add (aCase[0] + ": want " + aCase[2] + ", got output '" + sOut + "'");
        else
          if (!sOut.equals (aCase[1]))
            aErrors.add (aCase[0] + ": got '" + sOut + "' want '" + aCase[1] + "'");
      }
      catch (final XPathException ex)
      {
        if (aCase[2] == null || !aCase[2].equals (ex.getCode ()))
          aErrors.add (aCase[0] + ": want " + aCase[2] + ", got " + ex.getCode () + ": " + ex.getMessage ());
      }
    }
    if (aErrors.isNotEmpty ())
      fail (String.join ("\n", aErrors));
  }

  /**
   * F&amp;O 3.1 §14.6.1/§14.6.4: a resource that cannot be parsed as XML is
   * FODC0002 for fn:doc, and doc-available() is false.
   */
  @Test
  public void resolverSpecNonXMLResource ()
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    specWrite (aDir, "skip.txt", "not xml");
    specRunAll (aDir,
                err ("doc of non-XML", "<xsl:template match=\"/\"><xsl:value-of select=\"count(doc('skip.txt'))\"/></xsl:template>", null, "FODC0002"),
                ok ("doc-available of non-XML",
                    "<xsl:template match=\"/\"><xsl:value-of select=\"doc-available('skip.txt')\"/></xsl:template>",
                    null,
                    "false"));
  }

  /**
   * XSLT 3.0 §18.1.2 xsl:source-document, unstreamed (and streamable="yes",
   * which a processor without the streaming feature evaluates like an
   * unstreamed instruction).
   */
  @Test
  public void sourceDocSpec ()
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    specWrite (aDir, "in.xml", "<r><i>1</i><i>2</i><i>3</i></r>");
    specRunAll (aDir,
                ok ("unstreamed", """
                    <xsl:template match="/"><xsl:source-document href="in.xml" streamable="no"><xsl:value-of select="sum(r/i)"/></xsl:source-document></xsl:template>""",
                    null,
                    "6"),
                ok ("streamed", """
                    <xsl:template match="/"><xsl:source-document href="in.xml" streamable="yes"><xsl:value-of select="sum(r/i)"/></xsl:source-document></xsl:template>""",
                    null,
                    "6"),
                ok ("streamed for-each", """
                    <xsl:template match="/"><xsl:source-document href="in.xml" streamable="yes"><xsl:for-each select="r/i"><xsl:value-of select="."/>;</xsl:for-each></xsl:source-document></xsl:template>""",
                    null,
                    "1;2;3;"),
                ok ("href AVT", """
                    <xsl:template match="/"><xsl:source-document href="{'in' || '.xml'}"><xsl:value-of select="count(r/i)"/></xsl:source-document></xsl:template>""",
                    null,
                    "3"),
                // The resource cannot be retrieved: FODC0002 per §18.1.2.
                err ("missing document", """
                    <xsl:template match="/"><xsl:source-document href="missing.xml"><xsl:value-of select="."/></xsl:source-document></xsl:template>""",
                     null,
                     "FODC0002"),
                err ("missing streamed document", """
                    <xsl:template match="/"><xsl:source-document href="missing.xml" streamable="yes"><xsl:value-of select="count(*)"/></xsl:source-document></xsl:template>""",
                     null,
                     "FODC0002"));
  }
}
