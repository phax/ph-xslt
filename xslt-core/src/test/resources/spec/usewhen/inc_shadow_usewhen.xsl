<?xml version="1.0"?>
<!-- An included module whose root use-when is a SHADOW attribute computed
     from a static parameter the including module declares before the
     xsl:include (XSLT 3.0 3.13.1 + 3.13.2). -->
<xsl:stylesheet version="3.0" _use-when="{$INC}" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:variable name="from-inc" static="yes" select="'included'"/>
  <xsl:template match="/">INCLUDED</xsl:template>
</xsl:stylesheet>
