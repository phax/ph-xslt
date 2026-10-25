<?xml version="1.0"?>
<!-- Includes a module whose static select fails: the error must surface
     through two levels of the include graph. -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:include href="imp_badselect.xsl"/>
</xsl:stylesheet>
