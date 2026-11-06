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
<!-- Building and serialising an XML result: attribute value templates, concat, normalize-space -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" exclude-result-prefixes="#all">
  <xsl:output method="xml" encoding="UTF-8"/>

  <xsl:template match="/">
    <report>
      <xsl:for-each select="invoice/lines/line">
        <row id="{@id}" cat="{@cat}" amount="{xs:decimal(qty) * xs:decimal(price)}">
          <xsl:value-of select="concat(item, ' (', sku, ')')"/>
          <n><xsl:value-of select="normalize-space(note)"/></n>
        </row>
      </xsl:for-each>
    </report>
  </xsl:template>
</xsl:stylesheet>
