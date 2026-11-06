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
<!-- xsl:number with level="single", "multiple" and "any" for every line -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xsl:output method="text"/>

  <xsl:template match="/">
    <xsl:for-each select="invoice/lines/line">
      <xsl:number level="single"/>
      <xsl:text>|</xsl:text>
      <xsl:for-each select="period/start">
        <xsl:number level="multiple" count="line | period | start" format="1.1.1"/>
      </xsl:for-each>
      <xsl:text>|</xsl:text>
      <xsl:for-each select="price">
        <xsl:number level="any" count="price | qty"/>
      </xsl:for-each>
      <xsl:text>;</xsl:text>
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>
