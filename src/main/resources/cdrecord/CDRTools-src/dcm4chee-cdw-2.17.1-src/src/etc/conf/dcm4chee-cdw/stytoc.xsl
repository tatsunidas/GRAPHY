<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:redirect="http://xml.apache.org/xalan/redirect"
  extension-element-prefixes="redirect" version="1.0">
  <xsl:include href="sertoc.xsl"/>
  <xsl:template match="record[@type='PATIENT']" mode="toc">
    <xsl:variable name="patdir"
      select="substring(record/record/record/attr[@tag='(0004,1500)'],7,9)"/>
    <redirect:write file="{concat($patdir,'TOC.HTM')}">
      <html>
        <head>
          <title>Studies</title>
        </head>
        <body>
          <table>
            <tr>
              <td nowrap="nowrap">
                <a target="view">
                  <xsl:call-template name="href">
                    <xsl:with-param name="value" select="'INDEX.HTM'"/>
                  </xsl:call-template>
                  <xsl:call-template name="formatPN">
                    <xsl:with-param name="pn" select="attr[@tag='(0010,0010)']"
                    />
                  </xsl:call-template>
                </a>
                <br/><b>Studies:</b><br/>
                <xsl:for-each select="record[@type='STUDY']">
                  <xsl:sort data-type="text" order="ascending"
                    select="attr[@tag='(0008,0020)']"/>
                  <xsl:sort data-type="text" order="ascending"
                    select="attr[@tag='(0008,0030)']"/>
                  <xsl:variable name="stydir"
                    select="substring(record/record/attr[@tag='(0004,1500)'],16,9)"/>
                  <a target="serlist">
                    <xsl:call-template name="href">
                      <xsl:with-param name="value"
                        select="concat($stydir,'TOC.HTM')"/>
                    </xsl:call-template>
                    <xsl:call-template name="formatDateTime">
                      <xsl:with-param name="date"
                        select="attr[@tag='(0008,0020)']"/>
                      <xsl:with-param name="time"
                        select="attr[@tag='(0008,0030)']"/>
                    </xsl:call-template>
                  </a>
                  <br/>
                </xsl:for-each>
              </td>
            </tr>
          </table>
        </body>
      </html>
    </redirect:write>
    <xsl:apply-templates select="record[@type='STUDY']" mode="toc"/>
  </xsl:template>
</xsl:stylesheet>
