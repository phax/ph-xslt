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
<!-- Building and serialising JSON: an array with one map per line -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xsl:output method="json" encoding="UTF-8"/>

  <xsl:template match="/">
    <xsl:sequence select="array { for $l in invoice/lines/line return map { 'id': xs:integer($l/@id), 'sku': string($l/sku), 'amount': xs:decimal($l/qty) * xs:decimal($l/price), 'tags': array { string($l/@cat), string($l/tax/@rate) } } }"/>
  </xsl:template>
</xsl:stylesheet>
