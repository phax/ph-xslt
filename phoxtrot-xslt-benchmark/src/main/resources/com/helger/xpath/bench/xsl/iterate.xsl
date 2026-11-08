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
<!-- xsl:iterate over all lines with an array(xs:decimal) parameter that grows by one member per line -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:array="http://www.w3.org/2005/xpath-functions/array">
  <xsl:output method="text"/>

  <xsl:template match="/">
    <xsl:iterate select="invoice/lines/line">
      <xsl:param name="acc" as="array(xs:decimal)" select="[]"/>
      <xsl:param name="total" as="xs:decimal" select="0"/>
      <xsl:on-completion>
        <xsl:value-of select="array:size($acc) || '/' || $total || '/' || array:get($acc, array:size($acc))"/>
      </xsl:on-completion>
      <xsl:next-iteration>
        <xsl:with-param name="acc" select="array:append($acc, xs:decimal(price))"/>
        <xsl:with-param name="total" select="$total + xs:decimal(price) * xs:integer(qty)"/>
      </xsl:next-iteration>
    </xsl:iterate>
  </xsl:template>
</xsl:stylesheet>
