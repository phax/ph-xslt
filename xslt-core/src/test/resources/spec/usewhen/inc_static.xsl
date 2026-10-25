<?xml version="1.0"?>
<!-- Included module: its static variable precedes, in stylesheet tree order,
     everything the including module declares after the xsl:include. -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:variable name="inc-v" static="yes" select="'from-inc'"/>
</xsl:stylesheet>
