<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:redirect="http://xml.apache.org/xalan/redirect"
  extension-element-prefixes="redirect" version="1.0">
  <xsl:include href="patindex.xsl"/>
  <xsl:template match="dicomdir" mode="home">
    <xsl:param name="pagetitle"/>
    <redirect:write file="HOME.HTM">
      <html>
        <head>
          <title><xsl:value-of select="$pagetitle"/></title>
        </head>
        <body>
          <table width="100%">
            <tr>
              <td>
                <b>Home</b>
                <br/>
                <a target="_top">
                  <xsl:call-template name="href">
                    <xsl:with-param name="value" select="'INDEX.HTM'"/>
                  </xsl:call-template>FRAMES</a>
                <xsl:text disable-output-escaping="yes">&amp;nbsp;&amp;nbsp;</xsl:text>
                <a target="_top">
                  <xsl:call-template name="href">
                    <xsl:with-param name="value" select="'HOME.HTM'"/>
                  </xsl:call-template>NO FRAMES</a>
              </td>
              <td align="right">
                <a href="http://dcm4che.sourceforge.net" target="_blank">
                  <img border="0">
                    <xsl:call-template name="href">
                      <xsl:with-param name="name" select="'src'"/>
                      <xsl:with-param name="value" select="'LOGO.GIF'"/>
                    </xsl:call-template>
                  </img>
                </a>
              </td>
            </tr>
          </table>
          <p> </p>
          <table border="1" cellspacing="0" cellpadding="2">
            <tr align="left">
              <th>Patient Name</th>
              <th>ID</th>
              <th>Birth Date</th>
              <th>Sex</th>
              <th>Studies</th>
              <th>Files</th>
            </tr>
            <xsl:for-each select="record[@type='PATIENT']">
              <xsl:sort data-type="text" order="ascending"
                select="attr[@tag='(0010,0010)']"/>
              <xsl:variable name="patdir"
                select="substring(record/record/record/attr[@tag='(0004,1500)'],7,9)"/>
              <tr>
                <td>
                  <a>
                    <xsl:call-template name="href">
                      <xsl:with-param name="value"
                        select="concat($patdir,'INDEX.HTM')"/>
                    </xsl:call-template>
                    <xsl:call-template name="formatPN">
                      <xsl:with-param name="pn"
                        select="attr[@tag='(0010,0010)']"/>
                    </xsl:call-template>
                  </a>
                </td>
                <td>
                  <xsl:value-of select="attr[@tag='(0010,0020)']"/>
                </td>
                <td>
                  <xsl:call-template name="formatDate">
                    <xsl:with-param name="date"
                      select="attr[@tag='(0010,0030)']"/>
                  </xsl:call-template>
                </td>
                <td align="center">
                  <xsl:value-of select="attr[@tag='(0010,0040)']"/>
                </td>
                <td align="center">
                  <xsl:value-of select="count(record)"/>
                </td>
                <td align="center">
                  <xsl:value-of select="count(record/record/record)"/>
                </td>
              </tr>
            </xsl:for-each>
          </table>
        </body>
      </html>
    </redirect:write>
    <xsl:apply-templates select="record[@type='PATIENT']" mode="index">
      <xsl:with-param name="pagetitle" select="$pagetitle"/>
    </xsl:apply-templates>
  </xsl:template>
</xsl:stylesheet>
