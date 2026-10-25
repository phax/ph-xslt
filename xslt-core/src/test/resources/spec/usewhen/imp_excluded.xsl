<?xml version="1.0"?>
<!-- A secondary module whose root use-when is false: all of its children are
     excluded (XSLT 3.0 3.13.1), including the static variable and the template. -->
<xsl:stylesheet version="3.0" use-when="false()" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:variable name="excl" static="yes" select="'excluded'"/>
  <xsl:template match="/" priority="10">EXCLUDED</xsl:template>
</xsl:stylesheet>
