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
<!-- A streamed xsl:source-document: xsl:iterate over all lines, each line copied before it is read -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xsl:output method="text"/>
  <xsl:param name="href" as="xs:string" required="yes"/>

  <xsl:template name="xsl:initial-template">
    <xsl:source-document href="{$href}" streamable="yes">
      <xsl:iterate select="invoice/lines/line">
        <xsl:param name="n" as="xs:integer" select="0"/>
        <xsl:param name="total" as="xs:decimal" select="0"/>
        <xsl:on-completion>
          <xsl:value-of select="$n || '/' || $total"/>
        </xsl:on-completion>
        <xsl:variable name="line" select="copy-of(.)"/>
        <xsl:next-iteration>
          <xsl:with-param name="n" select="$n + 1"/>
          <xsl:with-param name="total" select="$total + xs:decimal($line/price) * xs:integer($line/qty)"/>
        </xsl:next-iteration>
      </xsl:iterate>
    </xsl:source-document>
  </xsl:template>
</xsl:stylesheet>
