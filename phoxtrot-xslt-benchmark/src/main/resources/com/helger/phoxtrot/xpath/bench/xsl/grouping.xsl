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
<!-- xsl:for-each-group: group-by with a sorted key, group-adjacent, group-by on a computed key -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xsl:output method="text"/>

  <xsl:template match="/">
    <xsl:for-each-group select="invoice/lines/line" group-by="@cat">
      <xsl:sort select="current-grouping-key()"/>
      <xsl:value-of select="current-grouping-key() || '=' || count(current-group()) || ':' || sum(current-group()/xs:integer(qty))"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each-group>
    <xsl:text>;</xsl:text>
    <xsl:variable name="adjacent" as="xs:integer*">
      <xsl:for-each-group select="invoice/lines/line" group-adjacent="string(tax/@rate)">
        <xsl:sequence select="count(current-group())"/>
      </xsl:for-each-group>
    </xsl:variable>
    <xsl:value-of select="count($adjacent) || ':' || max($adjacent)"/>
    <xsl:text>;</xsl:text>
    <xsl:variable name="letters" as="xs:string*">
      <xsl:for-each-group select="invoice/lines/line" group-by="substring(sku, 1, 1)">
        <xsl:sequence select="current-grouping-key()"/>
      </xsl:for-each-group>
    </xsl:variable>
    <xsl:value-of select="count($letters)"/>
  </xsl:template>
</xsl:stylesheet>
