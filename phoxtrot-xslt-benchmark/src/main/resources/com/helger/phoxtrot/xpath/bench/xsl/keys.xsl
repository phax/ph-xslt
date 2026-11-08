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
<!-- Keys: three xsl:key declarations, one lookup per category, one per line and one through a parent step -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xsl:output method="text"/>
  <xsl:key name="byCat" match="line" use="@cat"/>
  <xsl:key name="bySku" match="line" use="sku"/>
  <xsl:key name="byRate" match="tax" use="@rate"/>

  <xsl:template match="/">
    <xsl:value-of select="for $c in ('A', 'B', 'C') return $c || '=' || count(key('byCat', $c))" separator=","/>
    <xsl:text>;</xsl:text>
    <xsl:value-of select="sum(for $l in invoice/lines/line return count(key('bySku', $l/sku)))"/>
    <xsl:text>;</xsl:text>
    <xsl:value-of select="count(key('byRate', '0')/..)"/>
  </xsl:template>
</xsl:stylesheet>
