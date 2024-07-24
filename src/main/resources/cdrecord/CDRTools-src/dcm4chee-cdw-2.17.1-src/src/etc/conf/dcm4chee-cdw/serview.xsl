<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:redirect="http://xml.apache.org/xalan/redirect"
  extension-element-prefixes="redirect" version="1.0">
  <xsl:template match="record[@type='SERIES']" mode="index">
    <xsl:param name="pagetitle"/>
    <xsl:variable name="serdir"
      select="substring(record/attr[@tag='(0004,1500)'],7,27)"/>
    <redirect:write file="{concat($serdir,'INDEX.HTM')}">
      <html>
        <head>
          <title>
            <xsl:value-of select="$pagetitle"/>
          </title>
        </head>
        <body>
          <table width="100%">
            <tr>
              <td>
                <a>
                  <xsl:call-template name="href">
                    <xsl:with-param name="value" select="'../../../HOME.HTM'"/>
                  </xsl:call-template>
                  <b>Home</b>
                </a> &gt; <a>
                  <xsl:call-template name="href">
                    <xsl:with-param name="value" select="'../../INDEX.HTM'"/>
                  </xsl:call-template>
                  <b>Patient</b>
                </a> &gt; <a>
                  <xsl:call-template name="href">
                    <xsl:with-param name="value" select="'../INDEX.HTM'"/>
                  </xsl:call-template>
                  <b>Study</b>
                </a> &gt; <b>Series</b>
                <br/>
                <a target="_top">
                  <xsl:call-template name="href">
                    <xsl:with-param name="value" select="'../../../INDEX.HTM'"/>
                  </xsl:call-template>FRAMES</a>
                <xsl:text disable-output-escaping="yes">&amp;nbsp;&amp;nbsp;</xsl:text>
                <a target="_top">
                  <xsl:call-template name="href">
                    <xsl:with-param name="value" select="'INDEX.HTM'"/>
                  </xsl:call-template>NO FRAMES</a>
              </td>
              <td align="right">
                <a href="http://dcm4che.sourceforge.net" target="_blank">
                  <img border="0">
                    <xsl:call-template name="href">
                      <xsl:with-param name="name" select="'src'"/>
                      <xsl:with-param name="value" select="'../../../LOGO.GIF'"
                      />
                    </xsl:call-template>
                  </img>
                </a>
              </td>
            </tr>
          </table>
          <p/>
          <table border="1" cellspacing="0" cellpadding="2">
            <tr align="left">
              <th>Patient Name</th>
              <th>ID</th>
              <th>Birth Date</th>
              <th>Sex</th>
              <th>Studies</th>
              <th>Files</th>
            </tr>
            <tr>
              <td>
                <a>
                  <xsl:call-template name="href">
                    <xsl:with-param name="value" select="'../../INDEX.HTM'"/>
                  </xsl:call-template>
                  <xsl:call-template name="formatPN">
                    <xsl:with-param name="pn"
                      select="../../attr[@tag='(0010,0010)']"/>
                  </xsl:call-template>
                </a>
              </td>
              <td>
                <xsl:value-of select="../../attr[@tag='(0010,0020)']"/>
              </td>
              <td>
                <xsl:call-template name="formatDate">
                  <xsl:with-param name="date"
                    select="../../attr[@tag='(0010,0030)']"/>
                </xsl:call-template>
              </td>
              <td align="center">
                <xsl:value-of select="../../attr[@tag='(0010,0040)']"/>
              </td>
              <td align="center">
                <xsl:value-of select="count(../../record)"/>
              </td>
              <td align="center">
                <xsl:value-of select="count(../../record/record/record)"/>
              </td>
            </tr>
          </table>
          <p/>
          <table border="1" cellspacing="0" cellpadding="2">
            <tr align="left">
              <th>Study Date/Time</th>
              <th>ID</th>
              <th>Description</th>
              <th>Ref.Physican</th>
              <th>Series</th>
              <th>Files</th>
            </tr>
            <tr>
              <td>
                <a>
                  <xsl:call-template name="href">
                    <xsl:with-param name="value" select="'../INDEX.HTM'"/>
                  </xsl:call-template>
                  <xsl:call-template name="formatDateTime">
                    <xsl:with-param name="date"
                      select="../attr[@tag='(0008,0020)']"/>
                    <xsl:with-param name="time"
                      select="../attr[@tag='(0008,0030)']"/>
                  </xsl:call-template>
                </a>
              </td>
              <td>
                <xsl:value-of select="../attr[@tag='(0020,0010)']"/>
              </td>
              <td>
                <xsl:value-of select="../attr[@tag='(0008,1030)']"/>
              </td>
              <td>
                <xsl:call-template name="formatPN">
                  <xsl:with-param name="pn" select="../attr[@tag='(0008,0090)']"
                  />
                </xsl:call-template>
              </td>
              <td align="center">
                <xsl:value-of select="count(../record)"/>
              </td>
              <td align="center">
                <xsl:value-of select="count(../record/record)"/>
              </td>
            </tr>
          </table>
          <p/>
          <table border="1" cellspacing="0" cellpadding="2">
            <tr align="left">
              <th>Series</th>
              <th>Time</th>
              <th>Description</th>
              <th>Model Name</th>
              <th>Files</th>
            </tr>
            <tr>
              <td>
                <xsl:value-of select="attr[@tag='(0008,0060)']"/>
                <xsl:text>-</xsl:text>
                <xsl:value-of select="attr[@tag='(0020,0011)']"/>
              </td>
              <td>
                <xsl:call-template name="formatTime">
                  <xsl:with-param name="time" select="attr[@tag='(0008,0031)']"
                  />
                </xsl:call-template>
              </td>
              <td>
                <xsl:value-of select="attr[@tag='(0008,103E)']"/>
              </td>
              <td>
                <xsl:value-of select="attr[@tag='(0008,1090)']"/>
              </td>
              <td align="center">
                <xsl:value-of select="count(record)"/>
              </td>
            </tr>
          </table>
          <p/>
          <table cellspacing="0" cellpadding="2">
            <xsl:attribute name="border">
              <xsl:choose>
                <xsl:when test="record/@type='IMAGE'">0</xsl:when>
                <xsl:otherwise>1</xsl:otherwise>
              </xsl:choose>
            </xsl:attribute>
            <xsl:apply-templates select="record">
              <xsl:sort data-type="number" order="ascending"
                select="attr[@tag='(0020,0013)']"/>
            </xsl:apply-templates>
          </table>
        </body>
      </html>
    </redirect:write>
  </xsl:template>
  <xsl:template match="record[@type='IMAGE']">
    <tr>
      <td>
        <b>Image</b>&#160;<xsl:value-of select="attr[@tag='(0020,0013)']"
          />&#160;<xsl:value-of select="attr[@tag='(0008,0008)']"/>
        <table>
          <tr>
            <xsl:call-template name="frame">
              <xsl:with-param name="imgdir"
                select="substring(attr[@tag='(0004,1500)'],34)"/>
              <xsl:with-param name="frame" select="1"/>
              <xsl:with-param name="frames" select="attr[@tag='(0028,0008)']"/>
            </xsl:call-template>
          </tr>
        </table>
      </td>
    </tr>
  </xsl:template>
  <xsl:template name="frame">
    <xsl:param name="imgdir"/>
    <xsl:param name="frame"/>
    <xsl:param name="frames"/>
    <td>
      <img>
        <xsl:call-template name="href">
          <xsl:with-param name="name" select="'src'"/>
          <xsl:with-param name="value"
            select="concat($imgdir,'/',$frame,'.JPG')"/>
        </xsl:call-template>
      </img>
      <xsl:if test="$frames!=''">
        <br/>Frame&#160;<xsl:value-of select="$frame"/>
      </xsl:if>
    </td>
    <xsl:if test="$frames &gt; $frame">
      <xsl:call-template name="frame">
        <xsl:with-param name="imgdir" select="$imgdir"/>
        <xsl:with-param name="frame" select="$frame+1"/>
        <xsl:with-param name="frames" select="$frames"/>
      </xsl:call-template>
    </xsl:if>
  </xsl:template>
  <xsl:template match="record[@type='RT DOSE']">
    <xsl:if test="position()=1">
      <tr align="left">
        <th>#</th>
        <th>Date/Time</th>
        <th>Dose Type</th>
        <th>Comment</th>
      </tr>
    </xsl:if>
    <tr>
      <td>
        <xsl:value-of select="attr[@tag='(0020,0013)']"/>
      </td>
      <td>
        <xsl:call-template name="formatDateTime">
          <xsl:with-param name="date" select="attr[@tag='(0008,0023)']"/>
          <xsl:with-param name="time" select="attr[@tag='(0008,0033)']"/>
        </xsl:call-template>
      </td>
      <td>
        <xsl:value-of select="attr[@tag='(3004,000A)']"/>
      </td>
      <td>
        <xsl:value-of select="attr[@tag='(3004,0006)']"/>
      </td>
    </tr>
  </xsl:template>
  <xsl:template match="record[@type='RT STRUCTURE SET']">
    <xsl:if test="position()=1">
      <tr align="left">
        <th>#</th>
        <th>Date/Time</th>
        <th>Label</th>
      </tr>
    </xsl:if>
    <tr>
      <td>
        <xsl:value-of select="attr[@tag='(0020,0013)']"/>
      </td>
      <td>
        <xsl:call-template name="formatDateTime">
          <xsl:with-param name="date" select="attr[@tag='(3006,0008)']"/>
          <xsl:with-param name="time" select="attr[@tag='(3006,0009)']"/>
        </xsl:call-template>
      </td>
      <td>
        <xsl:value-of select="attr[@tag='(3006,0002)']"/>
      </td>
    </tr>
  </xsl:template>
  <xsl:template match="record[@type='RT PLAN']">
    <xsl:if test="position()=1">
      <tr align="left">
        <th>#</th>
        <th>Date/Time</th>
        <th>Label</th>
      </tr>
    </xsl:if>
    <tr>
      <td>
        <xsl:value-of select="attr[@tag='(0020,0013)']"/>
      </td>
      <td>
        <xsl:call-template name="formatDateTime">
          <xsl:with-param name="date" select="attr[@tag='(300A,0008)']"/>
          <xsl:with-param name="time" select="attr[@tag='(300A,0009)']"/>
        </xsl:call-template>
      </td>
      <td>
        <xsl:value-of select="attr[@tag='(300A,0002)']"/>
      </td>
    </tr>
  </xsl:template>
  <xsl:template match="record[@type='RT TREATMENT RECORD']">
    <xsl:if test="position()=1">
      <tr align="left">
        <th>#</th>
        <th>Date/Time</th>
        <th>Label</th>
      </tr>
    </xsl:if>
    <tr>
      <td>
        <xsl:value-of select="attr[@tag='(0020,0013)']"/>
      </td>
      <td>
        <xsl:call-template name="formatDateTime">
          <xsl:with-param name="date" select="attr[@tag='(3008,0250)']"/>
          <xsl:with-param name="time" select="attr[@tag='(3008,0251)']"/>
        </xsl:call-template>
      </td>
      <td>
        <xsl:value-of select="attr[@tag='(300A,0002)']"/>
      </td>
    </tr>
  </xsl:template>
  <xsl:template match="record[@type='PRESENTATION']">
    <xsl:if test="position()=1">
      <tr align="left">
        <th>#</th>
        <th>Date/Time</th>
        <th>Label</th>
        <th>Description</th>
        <th>Creator</th>
      </tr>
    </xsl:if>
    <tr>
      <td>
        <xsl:value-of select="attr[@tag='(0020,0013)']"/>
      </td>
      <td>
        <xsl:call-template name="formatDateTime">
          <xsl:with-param name="date" select="attr[@tag='(0070,0082)']"/>
          <xsl:with-param name="time" select="attr[@tag='(0070,0083)']"/>
        </xsl:call-template>
      </td>
      <td>
        <xsl:value-of select="attr[@tag='(0070,0080)']"/>
      </td>
      <td>
        <xsl:value-of select="attr[@tag='(0070,0081)']"/>
      </td>
      <td>
        <xsl:call-template name="formatPN">
          <xsl:with-param name="pn" select="attr[@tag='(0070,0084)']"/>
        </xsl:call-template>
      </td>
    </tr>
  </xsl:template>
  <xsl:template match="record[@type='WAVEFORM']">
    <xsl:if test="position()=1">
      <tr align="left">
        <th>#</th>
        <th>Date/Time</th>
      </tr>
    </xsl:if>
    <tr>
      <td>
        <xsl:value-of select="attr[@tag='(0020,0013)']"/>
      </td>
      <td>
        <xsl:call-template name="formatDateTime">
          <xsl:with-param name="date" select="attr[@tag='(0008,0023)']"/>
          <xsl:with-param name="time" select="attr[@tag='(0008,0033)']"/>
        </xsl:call-template>
      </td>
    </tr>
  </xsl:template>
  <xsl:template match="record[@type='SR DOCUMENT']">
    <xsl:if test="position()=1">
      <tr align="left">
        <th>#</th>
        <th>Date/Time</th>
        <th>Document Title</th>
        <th>Completed/Verified</th>
      </tr>
    </xsl:if>
    <tr>
      <td>
        <xsl:value-of select="attr[@tag='(0020,0013)']"/>
      </td>
      <td>
        <xsl:call-template name="formatDateTime">
          <xsl:with-param name="date" select="attr[@tag='(0008,0023)']"/>
          <xsl:with-param name="time" select="attr[@tag='(0008,0033)']"/>
        </xsl:call-template>
      </td>
      <td>
        <xsl:value-of
          select="attr[@tag='(0040,A043)']/item/attr[@tag='(0008,0104)']"/>
      </td>
      <td>
        <xsl:value-of select="attr[@tag='(0040,A491)']"/>/<xsl:value-of
          select="attr[@tag='(0040,A493)']"/>
      </td>
    </tr>
  </xsl:template>
  <xsl:template match="record[@type='KEY OBJECT DOC']">
    <xsl:if test="position()=1">
      <tr align="left">
        <th>#</th>
        <th>Date/Time</th>
        <th>Selection</th>
      </tr>
    </xsl:if>
    <tr>
      <td>
        <xsl:value-of select="attr[@tag='(0020,0013)']"/>
      </td>
      <td>
        <xsl:call-template name="formatDateTime">
          <xsl:with-param name="date" select="attr[@tag='(0008,0023)']"/>
          <xsl:with-param name="time" select="attr[@tag='(0008,0033)']"/>
        </xsl:call-template>
      </td>
      <td>
        <xsl:value-of
          select="attr[@tag='(0040,A043)']/item/attr[@tag='(0008,0104)']"/>
      </td>
    </tr>
  </xsl:template>
  <xsl:template match="record[@type='ENCAP DOC']">
    <xsl:if test="position()=1">
      <tr align="left">
        <th>#</th>
        <th>Date/Time</th>
        <th>Document Title</th>
        <th>Completed/Verified</th>
      </tr>
    </xsl:if>
    <tr>
      <td>
        <xsl:value-of select="attr[@tag='(0020,0013)']"/>
      </td>
      <td>
        <xsl:call-template name="formatDateTime">
          <xsl:with-param name="date" select="attr[@tag='(0008,0023)']"/>
          <xsl:with-param name="time" select="attr[@tag='(0008,0033)']"/>
        </xsl:call-template>
      </td>
      <td>
        <xsl:variable name="title" select="attr[@tag='(0042,0010)']"/>
        <xsl:choose>
          <xsl:when test="$title">
            <xsl:value-of select="$title"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of
              select="attr[@tag='(0040,A043)']/item/attr[@tag='(0008,0104)']"/>
          </xsl:otherwise>
        </xsl:choose>
      </td>
      <td>
        <xsl:value-of select="attr[@tag='(0040,A491)']"/>/<xsl:value-of
          select="attr[@tag='(0040,A493)']"/>
      </td>
    </tr>
  </xsl:template>
</xsl:stylesheet>
