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
<!-- Template dispatch: four modes, value and structure predicates, explicit priorities, a union pattern -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xsl:output method="text"/>
  <xsl:mode name="classify" on-no-match="deep-skip"/>
  <xsl:mode name="fields" on-no-match="deep-skip"/>
  <xsl:mode name="dates" on-no-match="deep-skip"/>

  <xsl:template match="/">
    <xsl:apply-templates select="invoice/lines/line" mode="classify"/>
  </xsl:template>

  <xsl:template match="line[@cat = 'A'][xs:decimal(price) gt 100]" mode="classify" priority="3">X<xsl:apply-templates mode="fields"/></xsl:template>
  <xsl:template match="line[@cat = 'A']" mode="classify" priority="2">A<xsl:apply-templates mode="fields"/></xsl:template>
  <xsl:template match="line[@cat = 'B']" mode="classify" priority="2">B<xsl:apply-templates mode="fields"/></xsl:template>
  <xsl:template match="line[tax/@rate = '0']" mode="classify" priority="1">Z<xsl:apply-templates mode="fields"/></xsl:template>
  <xsl:template match="line" mode="classify">O<xsl:apply-templates mode="fields"/></xsl:template>

  <xsl:template match="item" mode="fields">i</xsl:template>
  <xsl:template match="sku[starts-with(., 'A')]" mode="fields">a</xsl:template>
  <xsl:template match="sku" mode="fields">s</xsl:template>
  <xsl:template match="qty[. = '1']" mode="fields">1</xsl:template>
  <xsl:template match="qty | price" mode="fields">n</xsl:template>
  <xsl:template match="tax[@rate = '19']" mode="fields">T</xsl:template>
  <xsl:template match="tax" mode="fields">t</xsl:template>
  <xsl:template match="note" mode="fields">o</xsl:template>
  <xsl:template match="period" mode="fields"><xsl:apply-templates mode="dates"/></xsl:template>
  <xsl:template match="text()" mode="fields"/>

  <xsl:template match="start" mode="dates">&lt;</xsl:template>
  <xsl:template match="end" mode="dates">&gt;</xsl:template>
</xsl:stylesheet>
