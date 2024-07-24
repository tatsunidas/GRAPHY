<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:redirect="http://xml.apache.org/xalan/redirect"
  extension-element-prefixes="redirect" version="1.0">
  <xsl:param name="linkcase"/>
  <xsl:template name="formatPN">
    <xsl:param name="pn"/>
    <xsl:value-of select="translate($pn,'^',',')"/>
  </xsl:template>
  <xsl:template name="formatDate">
    <xsl:param name="date"/>
    <xsl:variable name="len" select="string-length($date)"/>
    <xsl:choose>
      <xsl:when test="$len = 10">
        <xsl:value-of select="$date"/>
      </xsl:when>
      <xsl:when test="$len = 8">
        <xsl:value-of select="substring($date,1,4)"/>
        <xsl:text>-</xsl:text>
        <xsl:value-of select="substring($date,5,2)"/>
        <xsl:text>-</xsl:text>
        <xsl:value-of select="substring($date,7)"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:text>????-??-??</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template name="formatTime">
    <xsl:param name="time"/>
    <xsl:variable name="len" select="string-length($time)"/>
    <xsl:choose>
      <xsl:when test="contains($time,':')">
        <xsl:value-of select="substring($time,1,5)"/>
      </xsl:when>
      <xsl:when test="$len &gt; 3">
        <xsl:value-of select="substring($time,1,2)"/>
        <xsl:text>:</xsl:text>
        <xsl:value-of select="substring($time,3,2)"/>
      </xsl:when>
      <xsl:when test="$len &gt; 1">
        <xsl:value-of select="substring($time,1,2)"/>
        <xsl:text>:??</xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <xsl:text>??:??</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template name="formatDateTime">
    <xsl:param name="date"/>
    <xsl:param name="time"/>
    <xsl:variable name="d">
      <xsl:call-template name="formatDate">
        <xsl:with-param name="date" select="$date"/>
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="t">
      <xsl:call-template name="formatTime">
        <xsl:with-param name="time" select="$time"/>
      </xsl:call-template>
    </xsl:variable>
    <xsl:value-of select="concat($d,' ',$t)"/>
  </xsl:template>
  <xsl:template name="href">
    <xsl:param name="name" select="'href'"/>
    <xsl:param name="value"/>
    <xsl:attribute name="{$name}">
      <xsl:choose>
        <xsl:when test="$linkcase='lower'">
          <xsl:value-of
            select="translate($value,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')"
          />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$value"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:attribute>
  </xsl:template>
</xsl:stylesheet>
