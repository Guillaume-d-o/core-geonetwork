<?xml version="1.0" encoding="UTF-8"?>
<!-- 
  Convert a STAC Item to ISO19115-3 format
  
  This stylesheet transforms a STAC (SpatioTemporal Asset Catalog) Item to ISO19115-3 format.
  The STAC JSON is first converted to XML using the SimpleURLHarvester's JSON to XML conversion.
  
  Authors: 
    - Based on JSON to ISO19115-3 conversion and STAC specification
    
  Date: June 2023
-->
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:gmd="http://www.isotc211.org/2005/gmd"
    xmlns:gco="http://www.isotc211.org/2005/gco"
    xmlns:gfc="http://www.isotc211.org/2005/gfc"
    xmlns:gml="http://www.opengis.net/gml/3.2"
    xmlns:gmx="http://www.isotc211.org/2005/gmx"
    xmlns:mdb="http://standards.iso.org/iso/19115/-3/mdb/2.0"
    xmlns:mcc="http://standards.iso.org/iso/19115/-3/mcc/1.0"
    xmlns:mri="http://standards.iso.org/iso/19115/-3/mri/1.0"
    xmlns:cit="http://standards.iso.org/iso/19115/-3/cit/2.0"
    xmlns:mco="http://standards.iso.org/iso/19115/-3/mco/1.0"
    xmlns:gex="http://standards.iso.org/iso/19115/-3/gex/1.0"
    xmlns:dqm="http://standards.iso.org/iso/19157/-2/dqm/1.0"
    xmlns:lan="http://standards.iso.org/iso/19115/-3/lan/1.0"
    xmlns:mrd="http://standards.iso.org/iso/19115/-3/mrd/1.0"
    xmlns:srv="http://standards.iso.org/iso/19115/-3/srv/2.0"
    xmlns:mac="http://standards.iso.org/iso/19115/-3/mac/2.0"
    xmlns:mrl="http://standards.iso.org/iso/19115/-3/mrl/2.0"
    xmlns:mmi="http://standards.iso.org/iso/19115/-3/mmi/1.0"
    xmlns:gcx="http://standards.iso.org/iso/19115/-3/gcx/1.0"
    xmlns:gex11="http://www.isotc211.org/2005/gex"
    xmlns:mdq="http://standards.iso.org/iso/19157/-2/mdq/1.0"
    xmlns:cat="http://standards.iso.org/iso/19115/-3/cat/1.0"
    xmlns:mdt="http://standards.iso.org/iso/19115/-3/mdt/2.0"
    xmlns:mrc="http://standards.iso.org/iso/19115/-3/mrc/2.0"
    xmlns:mas="http://standards.iso.org/iso/19115/-3/mas/1.0"
    xmlns:geonet="http://www.fao.org/geonetwork"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    exclude-result-prefixes="#all">

  <xsl:output method="xml" indent="yes"/>
  
  <!-- Param supplied by the harvester with the UUID to use for the metadata -->
  <xsl:param name="uuid" select="''"/>
  
  <!-- Root template to convert STAC item to ISO19115-3 -->
  <xsl:template match="/">
    <xsl:apply-templates select="stacItem"/>
  </xsl:template>
  
  <!-- Main STAC item conversion template -->
  <xsl:template match="stacItem">
    <mdb:MD_Metadata>
      <!-- Metadata identifier using provided UUID or the STAC item ID -->
      <mdb:metadataIdentifier>
        <mcc:MD_Identifier>
          <mcc:code>
            <gco:CharacterString>
              <xsl:choose>
                <xsl:when test="normalize-space($uuid) != ''">
                  <xsl:value-of select="$uuid"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="uuid"/>
                </xsl:otherwise>
              </xsl:choose>
            </gco:CharacterString>
          </mcc:code>
          <mcc:codeSpace>
            <gco:CharacterString>urn:uuid</gco:CharacterString>
          </mcc:codeSpace>
        </mcc:MD_Identifier>
      </mdb:metadataIdentifier>
      
      <!-- Metadata default language -->
      <mdb:defaultLocale>
        <lan:PT_Locale>
          <lan:language>
            <lan:LanguageCode codeList="http://www.loc.gov/standards/iso639-2/" codeListValue="eng"/>
          </lan:language>
          <lan:characterEncoding>
            <lan:MD_CharacterSetCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#MD_CharacterSetCode" codeListValue="utf8"/>
          </lan:characterEncoding>
        </lan:PT_Locale>
      </mdb:defaultLocale>
      
      <!-- Metadata scope - default to dataset -->
      <mdb:metadataScope>
        <mdb:MD_MetadataScope>
          <mdb:resourceScope>
            <mcc:MD_ScopeCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#MD_ScopeCode" codeListValue="dataset"/>
          </mdb:resourceScope>
        </mdb:MD_MetadataScope>
      </mdb:metadataScope>
      
      <!-- Contact information -->
      <xsl:if test="properties/providers">
        <xsl:for-each select="properties/providers/*">
          <mdb:contact>
            <cit:CI_Responsibility>
              <cit:role>
                <cit:CI_RoleCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#CI_RoleCode" codeListValue="publisher"/>
              </cit:role>
              <cit:party>
                <cit:CI_Organisation>
                  <cit:name>
                    <gco:CharacterString><xsl:value-of select="name"/></gco:CharacterString>
                  </cit:name>
                  <xsl:if test="url">
                    <cit:contactInfo>
                      <cit:CI_Contact>
                        <cit:onlineResource>
                          <cit:CI_OnlineResource>
                            <cit:linkage>
                              <gco:CharacterString><xsl:value-of select="url"/></gco:CharacterString>
                            </cit:linkage>
                          </cit:CI_OnlineResource>
                        </cit:onlineResource>
                      </cit:CI_Contact>
                    </cit:contactInfo>
                  </xsl:if>
                </cit:CI_Organisation>
              </cit:party>
            </cit:CI_Responsibility>
          </mdb:contact>
        </xsl:for-each>
      </xsl:if>
      
      <!-- Date of metadata creation -->
      <mdb:dateInfo>
        <cit:CI_Date>
          <cit:date>
            <gco:DateTime>
              <xsl:choose>
                <xsl:when test="normalize-space(properties/datetime) != ''">
                  <xsl:value-of select="properties/datetime"/>
                </xsl:when>
                <xsl:when test="normalize-space(properties/created) != ''">
                  <xsl:value-of select="properties/created"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="current-dateTime()"/>
                </xsl:otherwise>
              </xsl:choose>
            </gco:DateTime>
          </cit:date>
          <cit:dateType>
            <cit:CI_DateTypeCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#CI_DateTypeCode" codeListValue="creation"/>
          </cit:dateType>
        </cit:CI_Date>
      </mdb:dateInfo>
      
      <!-- Metadata standards -->
      <mdb:metadataStandard>
        <cit:CI_Citation>
          <cit:title>
            <gco:CharacterString>ISO 19115-3</gco:CharacterString>
          </cit:title>
          <cit:edition>
            <gco:CharacterString>2018</gco:CharacterString>
          </cit:edition>
        </cit:CI_Citation>
      </mdb:metadataStandard>
      
      <!-- Resource information -->
      <mdb:identificationInfo>
        <mri:MD_DataIdentification>
          <mri:citation>
            <cit:CI_Citation>
              <!-- Resource title -->
              <cit:title>
                <gco:CharacterString>
                  <xsl:choose>
                    <xsl:when test="normalize-space(properties/title) != ''">
                      <xsl:value-of select="properties/title"/>
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:value-of select="id"/>
                    </xsl:otherwise>
                  </xsl:choose>
                </gco:CharacterString>
              </cit:title>
              
              <!-- Resource dates -->
              <xsl:if test="properties/datetime or properties/created or properties/updated">
                <xsl:if test="properties/created">
                  <cit:date>
                    <cit:CI_Date>
                      <cit:date>
                        <gco:DateTime><xsl:value-of select="properties/created"/></gco:DateTime>
                      </cit:date>
                      <cit:dateType>
                        <cit:CI_DateTypeCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#CI_DateTypeCode" codeListValue="creation"/>
                      </cit:dateType>
                    </cit:CI_Date>
                  </cit:date>
                </xsl:if>
                <xsl:if test="properties/updated">
                  <cit:date>
                    <cit:CI_Date>
                      <cit:date>
                        <gco:DateTime><xsl:value-of select="properties/updated"/></gco:DateTime>
                      </cit:date>
                      <cit:dateType>
                        <cit:CI_DateTypeCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#CI_DateTypeCode" codeListValue="revision"/>
                      </cit:dateType>
                    </cit:CI_Date>
                  </cit:date>
                </xsl:if>
                <xsl:if test="properties/datetime">
                  <cit:date>
                    <cit:CI_Date>
                      <cit:date>
                        <gco:DateTime><xsl:value-of select="properties/datetime"/></gco:DateTime>
                      </cit:date>
                      <cit:dateType>
                        <cit:CI_DateTypeCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#CI_DateTypeCode" codeListValue="publication"/>
                      </cit:dateType>
                    </cit:CI_Date>
                  </cit:date>
                </xsl:if>
              </xsl:if>
              
              <!-- Resource identifier -->
              <cit:identifier>
                <mcc:MD_Identifier>
                  <mcc:code>
                    <gco:CharacterString><xsl:value-of select="id"/></gco:CharacterString>
                  </mcc:code>
                </mcc:MD_Identifier>
              </cit:identifier>
            </cit:CI_Citation>
          </mri:citation>
          
          <!-- Abstract - use description or summary or construct from available fields -->
          <mri:abstract>
            <gco:CharacterString>
              <xsl:choose>
                <xsl:when test="normalize-space(properties/description) != ''">
                  <xsl:value-of select="properties/description"/>
                </xsl:when>
                <xsl:when test="normalize-space(properties/summary) != ''">
                  <xsl:value-of select="properties/summary"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:text>STAC Item </xsl:text>
                  <xsl:value-of select="id"/>
                  <xsl:if test="properties/title">
                    <xsl:text> - </xsl:text>
                    <xsl:value-of select="properties/title"/>
                  </xsl:if>
                </xsl:otherwise>
              </xsl:choose>
            </gco:CharacterString>
          </mri:abstract>
          
          <!-- Purpose - use STAC extension information if available -->
          <xsl:if test="properties/mission or properties/platform or properties/instruments or properties/constellation">
            <mri:purpose>
              <gco:CharacterString>
                <xsl:if test="properties/mission">Mission: <xsl:value-of select="properties/mission"/></xsl:if>
                <xsl:if test="properties/platform">
                  <xsl:if test="properties/mission"><xsl:text>; </xsl:text></xsl:if>
                  Platform: <xsl:value-of select="properties/platform"/>
                </xsl:if>
                <xsl:if test="properties/instruments">
                  <xsl:if test="properties/mission or properties/platform"><xsl:text>; </xsl:text></xsl:if>
                  Instruments: <xsl:value-of select="properties/instruments"/>
                </xsl:if>
                <xsl:if test="properties/constellation">
                  <xsl:if test="properties/mission or properties/platform or properties/instruments"><xsl:text>; </xsl:text></xsl:if>
                  Constellation: <xsl:value-of select="properties/constellation"/>
                </xsl:if>
              </gco:CharacterString>
            </mri:purpose>
          </xsl:if>
          
          <!-- Status - use STAC extension information if available -->
          <xsl:if test="properties/status">
            <mri:status>
              <mcc:MD_ProgressCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#MD_ProgressCode" codeListValue="{properties/status}"/>
            </mri:status>
          </xsl:if>
          
          <!-- Point of contact - use providers or creators if available -->
          <xsl:if test="properties/providers or properties/creators">
            <xsl:for-each select="properties/providers/*">
              <mri:pointOfContact>
                <cit:CI_Responsibility>
                  <cit:role>
                    <cit:CI_RoleCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#CI_RoleCode" codeListValue="originator"/>
                  </cit:role>
                  <cit:party>
                    <cit:CI_Organisation>
                      <cit:name>
                        <gco:CharacterString><xsl:value-of select="name"/></gco:CharacterString>
                      </cit:name>
                      <xsl:if test="url or description or roles">
                        <cit:contactInfo>
                          <cit:CI_Contact>
                            <xsl:if test="description">
                              <cit:address>
                                <cit:CI_Address>
                                  <cit:deliveryPoint>
                                    <gco:CharacterString><xsl:value-of select="description"/></gco:CharacterString>
                                  </cit:deliveryPoint>
                                </cit:CI_Address>
                              </cit:address>
                            </xsl:if>
                            <xsl:if test="url">
                              <cit:onlineResource>
                                <cit:CI_OnlineResource>
                                  <cit:linkage>
                                    <gco:CharacterString><xsl:value-of select="url"/></gco:CharacterString>
                                  </cit:linkage>
                                  <xsl:if test="roles">
                                    <cit:function>
                                      <cit:CI_OnLineFunctionCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#CI_OnLineFunctionCode" codeListValue="information"/>
                                    </cit:function>
                                  </xsl:if>
                                </cit:CI_OnlineResource>
                              </cit:onlineResource>
                            </xsl:if>
                          </cit:CI_Contact>
                        </cit:contactInfo>
                      </xsl:if>
                    </cit:CI_Organisation>
                  </cit:party>
                </cit:CI_Responsibility>
              </mri:pointOfContact>
            </xsl:for-each>
          </xsl:if>
          
          <!-- Resource maintenance (updated field) -->
          <xsl:if test="properties/updated">
            <mri:resourceMaintenance>
              <mmi:MD_MaintenanceInformation>
                <mmi:maintenanceDate>
                  <cit:CI_Date>
                    <cit:date>
                      <gco:DateTime><xsl:value-of select="properties/updated"/></gco:DateTime>
                    </cit:date>
                    <cit:dateType>
                      <cit:CI_DateTypeCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#CI_DateTypeCode" codeListValue="revision"/>
                    </cit:dateType>
                  </cit:CI_Date>
                </mmi:maintenanceDate>
              </mmi:MD_MaintenanceInformation>
            </mri:resourceMaintenance>
          </xsl:if>
          
          <!-- Keywords - use STAC extensions for keywords -->
          <xsl:if test="properties/keywords or stac_extensions or properties/themes or properties/collection">
            <mri:descriptiveKeywords>
              <mri:MD_Keywords>
                <xsl:for-each select="properties/keywords/*">
                  <mri:keyword>
                    <gco:CharacterString><xsl:value-of select="."/></gco:CharacterString>
                  </mri:keyword>
                </xsl:for-each>
                <xsl:if test="stac_extensions">
                  <xsl:for-each select="stac_extensions/*">
                    <mri:keyword>
                      <gco:CharacterString>STAC-Extension:<xsl:value-of select="."/></gco:CharacterString>
                    </mri:keyword>
                  </xsl:for-each>
                </xsl:if>
                <xsl:if test="properties/themes">
                  <xsl:for-each select="properties/themes/*">
                    <mri:keyword>
                      <gco:CharacterString>Theme:<xsl:value-of select="."/></gco:CharacterString>
                    </mri:keyword>
                  </xsl:for-each>
                </xsl:if>
                <xsl:if test="properties/collection">
                  <mri:keyword>
                    <gco:CharacterString>Collection:<xsl:value-of select="properties/collection"/></gco:CharacterString>
                  </mri:keyword>
                </xsl:if>
                <mri:type>
                  <mri:MD_KeywordTypeCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#MD_KeywordTypeCode" codeListValue="theme"/>
                </mri:type>
              </mri:MD_Keywords>
            </mri:descriptiveKeywords>
          </xsl:if>
          
          <!-- License constraints - use license field -->
          <xsl:if test="properties/license">
            <mri:resourceConstraints>
              <mco:MD_LegalConstraints>
                <mco:reference>
                  <cit:CI_Citation>
                    <cit:title>
                      <gco:CharacterString><xsl:value-of select="properties/license"/></gco:CharacterString>
                    </cit:title>
                  </cit:CI_Citation>
                </mco:reference>
                <mco:accessConstraints>
                  <mco:MD_RestrictionCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#MD_RestrictionCode" codeListValue="license"/>
                </mco:accessConstraints>
              </mco:MD_LegalConstraints>
            </mri:resourceConstraints>
          </xsl:if>
          
          <!-- Spatial representation type - typically raster for STAC Items -->
          <mri:spatialRepresentationType>
            <mcc:MD_SpatialRepresentationTypeCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#MD_SpatialRepresentationTypeCode" codeListValue="grid"/>
          </mri:spatialRepresentationType>
          
          <!-- Spatial resolution from gsd field if available -->
          <xsl:if test="properties/gsd">
            <mri:spatialResolution>
              <mri:MD_Resolution>
                <mri:distance>
                  <gco:Distance uom="m"><xsl:value-of select="properties/gsd"/></gco:Distance>
                </mri:distance>
              </mri:MD_Resolution>
            </mri:spatialResolution>
          </xsl:if>
          
          <!-- Temporal extent from temporal properties -->
          <xsl:if test="properties/datetime or properties/start_datetime or properties/end_datetime">
            <mri:extent>
              <gex:EX_Extent>
                <gex:temporalElement>
                  <gex:EX_TemporalExtent>
                    <gex:extent>
                      <gml:TimePeriod gml:id="timePeriod">
                        <gml:beginPosition>
                          <xsl:choose>
                            <xsl:when test="properties/start_datetime">
                              <xsl:value-of select="properties/start_datetime"/>
                            </xsl:when>
                            <xsl:when test="properties/datetime">
                              <xsl:value-of select="properties/datetime"/>
                            </xsl:when>
                          </xsl:choose>
                        </gml:beginPosition>
                        <gml:endPosition>
                          <xsl:choose>
                            <xsl:when test="properties/end_datetime">
                              <xsl:value-of select="properties/end_datetime"/>
                            </xsl:when>
                            <xsl:when test="properties/datetime">
                              <xsl:value-of select="properties/datetime"/>
                            </xsl:when>
                          </xsl:choose>
                        </gml:endPosition>
                      </gml:TimePeriod>
                    </gex:extent>
                  </gex:EX_TemporalExtent>
                </gex:temporalElement>
              </gex:EX_Extent>
            </mri:extent>
          </xsl:if>
          
          <!-- Spatial extent from STAC bbox -->
          <xsl:if test="bbox">
            <mri:extent>
              <gex:EX_Extent>
                <gex:geographicElement>
                  <gex:EX_GeographicBoundingBox>
                    <gex:westBoundLongitude>
                      <gco:Decimal><xsl:value-of select="bbox/*[1]"/></gco:Decimal>
                    </gex:westBoundLongitude>
                    <gex:eastBoundLongitude>
                      <gco:Decimal><xsl:value-of select="bbox/*[3]"/></gco:Decimal>
                    </gex:eastBoundLongitude>
                    <gex:southBoundLatitude>
                      <gco:Decimal><xsl:value-of select="bbox/*[2]"/></gco:Decimal>
                    </gex:southBoundLatitude>
                    <gex:northBoundLatitude>
                      <gco:Decimal><xsl:value-of select="bbox/*[4]"/></gco:Decimal>
                    </gex:northBoundLatitude>
                  </gex:EX_GeographicBoundingBox>
                </gex:geographicElement>
              </gex:EX_Extent>
            </mri:extent>
          </xsl:if>
        </mri:MD_DataIdentification>
      </mdb:identificationInfo>
      
      <!-- Resource distribution information - STAC assets -->
      <xsl:if test="assets">
        <mdb:distributionInfo>
          <mrd:MD_Distribution>
            <xsl:for-each select="assets/*">
              <mrd:transferOption>
                <mrd:MD_DigitalTransferOptions>
                  <mrd:onLine>
                    <cit:CI_OnlineResource>
                      <cit:linkage>
                        <gco:CharacterString><xsl:value-of select="href"/></gco:CharacterString>
                      </cit:linkage>
                      <xsl:if test="title">
                        <cit:name>
                          <gco:CharacterString><xsl:value-of select="title"/></gco:CharacterString>
                        </cit:name>
                      </xsl:if>
                      <xsl:if test="description">
                        <cit:description>
                          <gco:CharacterString><xsl:value-of select="description"/></gco:CharacterString>
                        </cit:description>
                      </xsl:if>
                      <xsl:if test="type">
                        <cit:protocol>
                          <gco:CharacterString><xsl:value-of select="type"/></gco:CharacterString>
                        </cit:protocol>
                      </xsl:if>
                      <cit:function>
                        <cit:CI_OnLineFunctionCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#CI_OnLineFunctionCode" codeListValue="download"/>
                      </cit:function>
                    </cit:CI_OnlineResource>
                  </mrd:onLine>
                </mrd:MD_DigitalTransferOptions>
              </mrd:transferOption>
            </xsl:for-each>
          </mrd:MD_Distribution>
        </mdb:distributionInfo>
      </xsl:if>
      
      <!-- Data quality info - mainly for cloud cover and other quality indicators -->
      <xsl:if test="properties/cloud_cover or properties/snow_cover">
        <mdb:dataQualityInfo>
          <mdq:DQ_DataQuality>
            <mdq:report>
              <mdq:DQ_QuantitativeAttributeAccuracy>
                <mdq:result>
                  <mdq:DQ_QuantitativeResult>
                    <mdq:value>
                      <gco:Record>
                        <xsl:if test="properties/cloud_cover">Cloud cover: <xsl:value-of select="properties/cloud_cover"/>%</xsl:if>
                        <xsl:if test="properties/snow_cover">
                          <xsl:if test="properties/cloud_cover"><xsl:text>; </xsl:text></xsl:if>
                          Snow cover: <xsl:value-of select="properties/snow_cover"/>%
                        </xsl:if>
                      </gco:Record>
                    </mdq:value>
                  </mdq:DQ_QuantitativeResult>
                </mdq:result>
              </mdq:DQ_QuantitativeAttributeAccuracy>
            </mdq:report>
          </mdq:DQ_DataQuality>
        </mdb:dataQualityInfo>
      </xsl:if>
      
      <!-- Additional metadata for provenance - use collection link if available -->
      <xsl:if test="links">
        <mdb:resourceLineage>
          <mrl:LI_Lineage>
            <mrl:source>
              <mrl:LI_Source>
                <mrl:sourceCitation>
                  <cit:CI_Citation>
                    <cit:title>
                      <gco:CharacterString>STAC Collection</gco:CharacterString>
                    </cit:title>
                    <xsl:for-each select="links/*[rel='collection']">
                      <cit:onlineResource>
                        <cit:CI_OnlineResource>
                          <cit:linkage>
                            <gco:CharacterString><xsl:value-of select="href"/></gco:CharacterString>
                          </cit:linkage>
                          <xsl:if test="title">
                            <cit:name>
                              <gco:CharacterString><xsl:value-of select="title"/></gco:CharacterString>
                            </cit:name>
                          </xsl:if>
                          <cit:function>
                            <cit:CI_OnLineFunctionCode codeList="http://standards.iso.org/iso/19115/resources/Codelists/cat/codelists.xml#CI_OnLineFunctionCode" codeListValue="information"/>
                          </cit:function>
                        </cit:CI_OnlineResource>
                      </cit:onlineResource>
                    </xsl:for-each>
                  </cit:CI_Citation>
                </mrl:sourceCitation>
              </mrl:LI_Source>
            </mrl:source>
          </mrl:LI_Lineage>
        </mdb:resourceLineage>
      </xsl:if>
    </mdb:MD_Metadata>
  </xsl:template>
  
</xsl:stylesheet>
