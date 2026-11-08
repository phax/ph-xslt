<?xml version="1.0"?>
<!-- A dynamic error in a secondary module's root use-when. -->
<xsl:stylesheet version="3.0" use-when="1 idiv 0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/">X</xsl:template>
</xsl:stylesheet>
