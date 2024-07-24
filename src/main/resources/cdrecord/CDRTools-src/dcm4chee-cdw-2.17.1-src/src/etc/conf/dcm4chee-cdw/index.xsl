<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml" encoding="ISO-8859-1" indent="yes"
    doctype-public="-//W3C//DTD XHTML 1.0 Transitional//EN"
    doctype-system="http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd"/>
  <xsl:include href="common.xsl"/>
  <xsl:param name="seqno" select="1"/>
  <xsl:param name="size" select="2"/>
  <xsl:param name="fsid" select="'DISK007'"/>
  <xsl:template match="/dicomdir">
    <html xmlns="http://www.w3.org/1999/xhtml">
      <head>
        <title>"PDI - IHE Portable Data for Imaging"</title>
      </head>
      <body>
        <div align="center">
          <h2>PDI - IHE Portable Data for Imaging</h2>
          <h2>Sample CD</h2>
          <!-- if File-set ID defined or several disks -->
          <xsl:if test="$fsid!='' or $size &gt; 1">
            <h2>
              <xsl:text>Media</xsl:text>
              <!-- if several disks -->
              <xsl:if test="$size &gt; 1">[<xsl:value-of select="$seqno"
                  />/<xsl:value-of select="$size"/>]</xsl:if>
              <!-- if File-set ID -->
              <xsl:if test="$fsid!=''"> ID: <xsl:value-of select="$fsid"/>
              </xsl:if>
            </h2>
          </xsl:if>
        </div>
        <p>This is a sample CD concerning the IHE Portable Data for Imaging
          (PDI) Integration Profile.</p>
        <p>PDI provides reliable export, import, display and print of images and
          reports using DICOM General Purpose CD-R media.</p>
        <h3>Institution/Privacy</h3>
        <p>This disk was created by:</p>
        <p>
          <b>YOUR COMPANY NAME</b>
          <br/>STREET
          <br/>CITY
          <br/>COUNTRY
          <br/>
          <a href="http://your.company.url">http://your.company.url</a>
        </p>
        <p>If this were a real IHE PDI CD, this section would list any privacy
          restrictions defined by the Institution.</p>
        <xsl:if test="attr[@tag='(2200,0008)']!='NO' and $seqno = 1">
          <h3>Web Content</h3>
          <p>DICOM content on the CD has been rendered for display as <a>
              <xsl:call-template name="href">
                <xsl:with-param name="value" select="'IHE_PDI/HOME.HTM'"/>
              </xsl:call-template>web content</a>.</p>
        </xsl:if>
        <h3>Manifest of Importable Data</h3>
        <xsl:variable name="pats"
          select="record[record/record/record[@seqno=$seqno]]"/>
        <xsl:variable name="studies"
          select="record/record[record/record[@seqno=$seqno]]"/>
        <xsl:variable name="series"
          select="record/record/record[record[@seqno=$seqno]]"/>
        <xsl:variable name="insts"
          select="record/record/record/record[@seqno=$seqno]"/>
        <p>This disk contains <xsl:value-of select="count($insts)"/> DICOM SOP
          Instances in <xsl:value-of select="count($series)"/> Series of
            <xsl:value-of select="count($studies)"/> Studie(s) from
            <xsl:value-of select="count($pats)"/> Patient(s):</p>
        <p>
          <xsl:for-each select="$pats">Patient: <xsl:call-template
              name="formatPN">
              <xsl:with-param name="pn" select="attr[@tag='(0010,0010)']"/>
            </xsl:call-template>
            <xsl:for-each select="record[record/record[@seqno=$seqno]]">
              <br/>Study <xsl:call-template name="formatDateTime">
                <xsl:with-param name="date" select="attr[@tag='(0008,0020)']"/>
                <xsl:with-param name="time" select="attr[@tag='(0008,0030)']"/>
              </xsl:call-template>
              <xsl:text>: </xsl:text>
              <xsl:value-of select="attr[@tag='(0008,1030)']"/>
              <xsl:for-each select="record[record[@seqno=$seqno]]">
                <br/>
                <xsl:value-of select="attr[@tag='(0008,0060)']"/>
                <xsl:text> Series #</xsl:text>
                <xsl:value-of select="attr[@tag='(0020,0011)']"/>
                <xsl:text>: </xsl:text>
                <xsl:value-of select="attr[@tag='(0008,103E)']"/>
                <br/>
                <xsl:for-each select="record[@seqno=$seqno]">
                  <code>
                    <xsl:value-of select="attr[@tag='(0004,1500)']"/>
                  </code>
                  <br/>
                  <xsl:text/>
                </xsl:for-each>
              </xsl:for-each>
            </xsl:for-each>
            <br/>
          </xsl:for-each>
        </p>
        <xsl:if test="attr[@tag='(2200,0009)']!='NO' and $seqno = 1">
          <h3>DICOM Viewer</h3>
          <p>This CD contains a DICOM Viewer application. Follow this <a
              href="FIXME">link</a> to launch the application.</p>
          <p>System Requirements: <table border="0">
              <tr>
                <td>Processor:</td>
                <td>P3/500MHz</td>
              </tr>
              <tr>
                <td>Memory:</td>
                <td>min 256MB</td>
              </tr>
              <tr>
                <td>Operation System:</td>
                <td>WinNT/2k/XP/Me</td>
              </tr>
            </table>
          </p>
        </xsl:if>
        <h3>README File</h3>
        <p>For technical details about this disk and contact information for the
          source, see: <a>
            <xsl:call-template name="href">
              <xsl:with-param name="value" select="'README.TXT'"/>
            </xsl:call-template>README.TXT</a>.</p>
        <h3>Disclaimer</h3>
        <p>The data is for demonstration purposes only and not for diagnostic
          imaging.</p>
        <xsl:if test="attr[@tag='(2200,0008)']!='NO' and $seqno = 1">
          <p>Not all DICOM content on the CD has been rendered for display as
            web content. Key Image Notes, Presentation States and Structured
            Report objects have not been rendered.</p>
        </xsl:if>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>
