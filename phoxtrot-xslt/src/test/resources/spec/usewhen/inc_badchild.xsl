<?xml version="1.0"?>
<!-- A dynamic error in a shadow attribute on a declaration of a secondary module. -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/" _mode="{1 idiv 0}">X</xsl:template>
</xsl:stylesheet>
