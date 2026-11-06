<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright (C) 2026 Philip Helger (www.helger.com)
    philip[at]helger[dot]com

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

            http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
<!-- Recursion: a divide-and-conquer stylesheet function over all prices, a recursive named template per line -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:f="urn:bench:functions" exclude-result-prefixes="#all">
  <xsl:output method="text"/>

  <xsl:function name="f:sum" as="xs:decimal">
    <xsl:param name="s" as="xs:decimal*"/>
    <xsl:variable name="n" select="count($s)"/>
    <xsl:sequence select="if ($n le 1) then sum($s) else f:sum(subsequence($s, 1, $n idiv 2)) + f:sum(subsequence($s, $n idiv 2 + 1))"/>
  </xsl:function>

  <xsl:template name="reverse">
    <xsl:param name="s" as="xs:string"/>
    <xsl:if test="$s ne ''">
      <xsl:call-template name="reverse">
        <xsl:with-param name="s" select="substring($s, 2)"/>
      </xsl:call-template>
      <xsl:value-of select="substring($s, 1, 1)"/>
    </xsl:if>
  </xsl:template>

  <xsl:template match="/">
    <xsl:value-of select="f:sum(invoice/lines/line/xs:decimal(price))"/>
    <xsl:text>;</xsl:text>
    <xsl:for-each select="invoice/lines/line">
      <xsl:call-template name="reverse">
        <xsl:with-param name="s" select="string(item)"/>
      </xsl:call-template>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>
