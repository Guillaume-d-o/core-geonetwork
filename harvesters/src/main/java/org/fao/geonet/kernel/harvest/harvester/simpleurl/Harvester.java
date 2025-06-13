//=============================================================================
//===	Copyright (C) 2001-2023 Food and Agriculture Organization of the
//===	United Nations (FAO-UN), United Nations World Food Programme (WFP)
//===	and United Nations Environment Programme (UNEP)
//===
//===	This program is free software; you can redistribute it and/or modify
//===	it under the terms of the GNU General Public License as published by
//===	the Free Software Foundation; either version 2 of the License, or (at
//===	your option) any later version.
//===
//===	This program is distributed in the hope that it will be useful, but
//===	WITHOUT ANY WARRANTY; without even the implied warranty of
//===	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
//===	General Public License for more details.
//===
//===	You should have received a copy of the GNU General Public License
//===	along with this program; if not, write to the Free Software
//===	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
//===
//===	Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
//===	Rome - Italy. email: geonetwork@osgeo.org
//==============================================================================

package org.fao.geonet.kernel.harvest.harvester.simpleurl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.CharStreams;
import jeeves.server.context.ServiceContext;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.fao.geonet.ApplicationContextHolder;
import org.fao.geonet.Logger;
import org.fao.geonet.exceptions.BadParameterEx;
import org.fao.geonet.kernel.GeonetworkDataDirectory;
import org.fao.geonet.kernel.harvest.harvester.HarvestError;
import org.fao.geonet.kernel.harvest.harvester.HarvestResult;
import org.fao.geonet.kernel.harvest.harvester.IHarvester;
import org.fao.geonet.lib.Lib;
import org.fao.geonet.util.Sha1Encoder;
import org.fao.geonet.utils.GeonetHttpRequestFactory;
import org.fao.geonet.utils.Log;
import org.fao.geonet.utils.Xml;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.Text;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpResponse;

import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.fao.geonet.utils.Xml.isRDFLike;
import static org.fao.geonet.utils.Xml.isXMLLike;

/**
 * Harvest metadata from a URL source.
 * <p>
 * The URL source can be a simple JSON, XML or RDF file or
 * an URL with indication on how to pass paging information.
 * <p>
 * This harvester has been tested with CKAN, OpenDataSoft,
 * OGC API Records, DCAT feeds.
 */
class Harvester implements IHarvester<HarvestResult> {
    public static final String LOGGER_NAME = "geonetwork.harvester.simpleurl";

    private final AtomicBoolean cancelMonitor;
    private Logger log;
    private final SimpleUrlParams params;
    private final ServiceContext context;

    @Autowired
    GeonetHttpRequestFactory requestFactory;

    /**
     * Contains a list of accumulated errors during the executing of this harvest.
     */
    private final List<HarvestError> errors;

    public Harvester(AtomicBoolean cancelMonitor, Logger log, ServiceContext context, SimpleUrlParams params, List<HarvestError> errors) {
        this.cancelMonitor = cancelMonitor;
        this.log = log;
        this.context = context;
        this.params = params;
        this.errors = errors;
    }

    public HarvestResult harvest(Logger log) throws Exception {
        this.log = log;
        log.debug("Retrieving from harvester: " + params.getName());

        requestFactory = context.getBean(GeonetHttpRequestFactory.class);

        String[] urlList = params.url.split("\n");
        boolean error = false;
        Aligner aligner = new Aligner(cancelMonitor, context, params, log);
        Set<String> listOfUuids = new HashSet<>();

        for (String url : urlList) {
            log.debug("Loading URL: " + url);
            String content = retrieveUrl(url);
            if (cancelMonitor.get()) {
                return new HarvestResult();
            }
            log.debug("Response is: " + content);

            int numberOfRecordsToHarvest = -1;

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonObj = null;
            Element xmlObj = null;
            SimpleUrlResourceType type;

            if (isRDFLike(content)) type = SimpleUrlResourceType.RDFXML;
            else if (isXMLLike(content)) type = SimpleUrlResourceType.XML;
            else {
                if (isSTACLike(content)) {
                    type = SimpleUrlResourceType.STAC;
                } else {
                    type = SimpleUrlResourceType.JSON;
                }
            }

            if (type == SimpleUrlResourceType.XML
                || type == SimpleUrlResourceType.RDFXML) {
                xmlObj = Xml.loadString(content, false);
            } else {
                jsonObj = objectMapper.readTree(content);
            }

            // TODO: Add page support for Hydra in RDFXML feeds ?
            if (StringUtils.isNotEmpty(params.numberOfRecordPath)) {
                try {
                    if (type == SimpleUrlResourceType.XML) {
                        @SuppressWarnings("unchecked")
                        List<Namespace> namespaces = (List<Namespace>) xmlObj.getAdditionalNamespaces();
                        Object element = Xml.selectSingle(xmlObj, params.numberOfRecordPath, namespaces);
                        if (element != null) {
                            String s = getXmlElementTextValue(element);
                            numberOfRecordsToHarvest = Integer.parseInt(s);
                        }
                    } else if (type == SimpleUrlResourceType.JSON) {
                        numberOfRecordsToHarvest = jsonObj.at(params.numberOfRecordPath).asInt();
                    }
                    log.debug("Number of records to harvest: " + numberOfRecordsToHarvest);
                } catch (Exception e) {
                    errors.add(new HarvestError(context, e));
                    log.error(String.format("Failed to extract total in response at path %s. Error is: %s",
                        params.numberOfRecordPath, e.getMessage()));
                }
            }
            try {
                List<String> listOfUrlForPages = buildListOfUrl(params, numberOfRecordsToHarvest);
                for (int i = 0; i < listOfUrlForPages.size(); i++) {
                    if (i != 0) {
                        content = retrieveUrl(listOfUrlForPages.get(i));
                        if (type == SimpleUrlResourceType.XML) {
                            xmlObj = Xml.loadString(content, false);
                        } else {
                            jsonObj = objectMapper.readTree(content);
                        }
                    }
                    if (StringUtils.isNotEmpty(params.loopElement)
                        || type == SimpleUrlResourceType.RDFXML
                        || type == SimpleUrlResourceType.STAC) {
                        Map<String, Element> uuids = new HashMap<>();
                        try {
                            if (type == SimpleUrlResourceType.XML) {
                                collectRecordsFromXml(xmlObj, uuids, aligner);
                            } else if (type == SimpleUrlResourceType.RDFXML) {
                                collectRecordsFromRdf(xmlObj, uuids, aligner);
                            } else if (type == SimpleUrlResourceType.STAC) {
                                collectRecordsFromStac(jsonObj, uuids, aligner);
                            } else if (type == SimpleUrlResourceType.JSON) {
                                collectRecordsFromJson(jsonObj, uuids, aligner);
                            }
                            aligner.align(uuids, errors);
                            listOfUuids.addAll(uuids.keySet());
                        } catch (Exception e) {
                            errors.add(new HarvestError(this.context, e));
                            log.error(String.format("Failed to collect record in response at path %s. Error is: %s",
                                params.loopElement, e.getMessage()));
                        }
                    }
                }
            } catch (Exception t) {
                error = true;
                log.error("Unknown error trying to harvest");
                log.error(t.getMessage());
                log.error(t);
                errors.add(new HarvestError(context, t));
            } catch (Throwable t) {
                error = true;
                log.fatal("Something unknown and terrible happened while harvesting");
                log.fatal(t.getMessage());
                errors.add(new HarvestError(context, t));
            }

            log.info("Total records processed in all searches :" + listOfUuids.size());
            if (error) {
                log.warning("Due to previous errors the align process has not been called");
            }
        }
        aligner.cleanupRemovedRecords(listOfUuids);
        return aligner.getResult();
    }

    private void collectRecordsFromJson(JsonNode jsonObj,
                                        Map<String, Element> uuids,
                                        Aligner aligner) {
        JsonNode nodes = jsonObj.at(params.loopElement);
        log.debug(String.format("%d records found in JSON response.", nodes.size()));

        nodes.forEach(jsonRecord -> {
            String uuid = null;
            try {
                uuid = this.extractUuidFromIdentifier(jsonRecord.at(params.recordIdPath).asText());
            } catch (Exception e) {
                log.error(String.format("Failed to collect record UUID at path %s. Error is: %s",
                    params.recordIdPath, e.getMessage()));
            }
            String apiUrlPath = params.url.split("\\?")[0];
            URL apiUrl = null;
            try {
                apiUrl = new URL(apiUrlPath);
                String nodeUrl = new StringBuilder(apiUrl.getProtocol()).append("://").append(apiUrl.getAuthority()).toString();
                Element xml = convertJsonRecordToXml(jsonRecord, uuid, apiUrlPath, nodeUrl);
                uuids.put(uuid, xml);
            } catch (MalformedURLException e) {
                errors.add(new HarvestError(this.context, e));
                log.warning(String.format("Failed to parse JSON source URL. Error is: %s", e.getMessage()));
            }
        });
    }

    private void collectRecordsFromRdf(Element xmlObj,
                                       Map<String, Element> uuids,
                                       Aligner aligner) {
        Map<String, Element> rdfNodes = null;
        try {
            rdfNodes = RDFUtils.getAllUuids(xmlObj);
        } catch (Exception e) {
            errors.add(new HarvestError(this.context, e));
            log.error(String.format("Failed to find records in RDF graph. Error is: %s",
                e.getMessage()));
        }
        if (rdfNodes != null) {
            log.debug(String.format("%d records found in RDFXML response.", rdfNodes.size()));

            // TODO: Add param
            boolean hashUuid = true;
            rdfNodes.forEach((uuid, xml) -> {
                if (hashUuid) {
                    uuid = Sha1Encoder.encodeString(uuid);
                }
                Element output = applyConversion(xml, uuid);
                if (output != null) {
                    uuids.put(uuid, output);
                }
            });
        }
    }

    private void collectRecordsFromXml(Element xmlObj,
                                       Map<String, Element> uuids,
                                       Aligner aligner) {
        List<Element> xmlNodes = null;
        try {
            @SuppressWarnings("unchecked")
            List<Element> nodes = (List<Element>) Xml.selectNodes(xmlObj, params.loopElement, xmlObj.getAdditionalNamespaces());
            xmlNodes = nodes;
        } catch (JDOMException e) {
            log.error(String.format("Failed to query records using %s. Error is: %s",
                params.loopElement, e.getMessage()));
        }

        if (xmlNodes != null) {
            log.debug(String.format("%d records found in XML response.", xmlNodes.size()));

            xmlNodes.forEach(element -> {
                String uuid =
                    null;
                try {
                    @SuppressWarnings("unchecked")
                    List<Namespace> namespaces = (List<Namespace>) element.getAdditionalNamespaces();
                    Object value = Xml.selectSingle(element, params.recordIdPath, namespaces);
                    uuid = getXmlElementTextValue(value);
                    uuids.put(uuid, applyConversion(element, null));
                } catch (JDOMException e) {
                    log.error(String.format("Failed to extract UUID for record. Error is %s.",
                        e.getMessage()));
                    aligner.getResult().badFormat++;
                    aligner.getResult().totalMetadata++;
                }
            });
        }
    }

    /**
     * Process STAC resources and extract metadata records.
     * This method can handle both STAC collections with multiple items
     * and individual STAC items.
     * 
     * @param jsonObj The JSON object containing STAC content
     * @param uuids Map to store the harvested records with their UUIDs
     * @param aligner The aligner instance
     */
    private void collectRecordsFromStac(JsonNode jsonObj,
                                       Map<String, Element> uuids,
                                       Aligner aligner) {
        // Determine if this is a collection or a single item
        boolean isCollection = false;
        JsonNode items = null;
        
        // Check if this is a collection with features
        if (jsonObj.has("features") && jsonObj.get("features").isArray() && jsonObj.get("features").size() > 0) {
            isCollection = true;
            items = jsonObj.get("features");
        } else if (jsonObj.at("/features").isArray() && jsonObj.at("/features").size() > 0) {
            isCollection = true;
            items = jsonObj.at("/features");
        }
        
        if (isCollection) {
            // Process collection of STAC items
            String itemsPath = StringUtils.isNotEmpty(params.loopElement) ? 
                              params.loopElement : "/features";
            
            // Use direct property or JsonPath depending on how it was configured
            items = itemsPath.startsWith("/") ? jsonObj.at(itemsPath) : jsonObj.get(itemsPath);
            
            log.debug(String.format("%d records found in STAC collection.", items.size()));
            
            // Process each item in the collection
            items.forEach(stacItem -> {
                processStacItem(stacItem, uuids);
            });
        } else {
            // This is a single STAC item
            log.debug("Processing single STAC item");
            processStacItem(jsonObj, uuids);
        }
    }
    
    /**
     * Process an individual STAC item and add it to the UUID map.
     * 
     * @param stacItem The STAC item to process
     * @param uuids Map to store the harvested records with their UUIDs
     */
    private void processStacItem(JsonNode stacItem, Map<String, Element> uuids) {
        String uuid = null;
        try {
            // Use STAC item ID as UUID if available
            if (StringUtils.isNotEmpty(params.recordIdPath)) {
                // Handle both direct property and JsonPath
                if (params.recordIdPath.startsWith("/")) {
                    uuid = stacItem.at(params.recordIdPath).asText();
                } else {
                    uuid = stacItem.has(params.recordIdPath) ? 
                          stacItem.get(params.recordIdPath).asText() : null;
                }
                
                if (StringUtils.isNotEmpty(uuid)) {
                    uuid = extractUuidFromIdentifier(uuid);
                }
            } else if (stacItem.has("id")) {
                uuid = stacItem.get("id").asText();
            }

            // If no UUID found, hash the entire item
            if (StringUtils.isEmpty(uuid)) {
                uuid = Sha1Encoder.encodeString(stacItem.toString());
            }
        } catch (Exception e) {
            log.error(String.format("Failed to extract ID from STAC item. Error is: %s", e.getMessage()));
        }

        try {
            String apiUrlPath = params.url.split("\\?")[0];
            URL apiUrl = new URL(apiUrlPath);
            String nodeUrl = new StringBuilder(apiUrl.getProtocol()).append("://").append(apiUrl.getAuthority()).toString();
            Element xml = convertStacItemToXml(stacItem, uuid, apiUrlPath, nodeUrl);
            uuids.put(uuid, xml);
        } catch (Exception e) {
            errors.add(new HarvestError(this.context, e));
            log.warning(String.format("Failed to process STAC item. Error is: %s", e.getMessage()));
        }
    }

    /**
     * Converts a STAC item to XML for processing.
     */
    private Element convertStacItemToXml(JsonNode stacItem, String uuid, String apiUrl, String nodeUrl) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String itemAsXml = XML.toString(
                new JSONObject(
                    objectMapper.writeValueAsString(stacItem)), "stacItem");
            itemAsXml = Xml.stripNonValidXMLCharacters(itemAsXml)
                .replace("<@", "<")
                .replace("</@", "</")
                .replaceAll("(:)(?![^<>]*<)", "_"); // this removes colon from property names
            Element itemAsElement = Xml.loadString(itemAsXml, false);
            itemAsElement.addContent(new Element("uuid").setText(uuid));
            itemAsElement.addContent(new Element("apiUrl").setText(apiUrl));
            itemAsElement.addContent(new Element("nodeUrl").setText(nodeUrl));

            // Add STAC metadata if available in the response
            if (stacItem.has(STAC_VERSION)) {
                itemAsElement.addContent(new Element("stacVersion").setText(stacItem.get(STAC_VERSION).asText()));
            }

            return applyConversion(itemAsElement, uuid);
        } catch (Exception e) {
            log.error(String.format("Failed to convert STAC item %s to XML. Error is: %s",
                uuid, e.getMessage()));
        }
        return null;
    }

    private Element convertJsonRecordToXml(JsonNode jsonRecord, String uuid, String apiUrl, String nodeUrl) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String recordAsXml = XML.toString(
                new JSONObject(
                    objectMapper.writeValueAsString(jsonRecord)), "record");
            recordAsXml = Xml.stripNonValidXMLCharacters(recordAsXml)
                .replace("<@", "<")
                .replace("</@", "</")
                .replaceAll("(:)(?![^<>]*<)", "_"); // this removes colon from property names
            Element recordAsElement = Xml.loadString(recordAsXml, false);
            recordAsElement.addContent(new Element("uuid").setText(uuid));
            recordAsElement.addContent(new Element("apiUrl").setText(apiUrl));
            recordAsElement.addContent(new Element("nodeUrl").setText(nodeUrl));
            return applyConversion(recordAsElement, null);
        } catch (Exception e) {
            log.error(String.format("Failed to convert JSON record %s to XML. Error is: %s",
                uuid, e.getMessage()));
        }
        return null;
    }

    private Element applyConversion(Element input, String uuid) {
        if (StringUtils.isNotEmpty(params.toISOConversion)) {
            try {
                // First try to get the conversion from the standard location
                Path xslPath = ApplicationContextHolder.get().getBean(GeonetworkDataDirectory.class)
                    .getXsltConversion(params.toISOConversion);
                
                // If not found, try to find it in the STAC-specific location
                if (xslPath == null || !Files.exists(xslPath)) {
                    Path webappDir = ApplicationContextHolder.get().getBean(GeonetworkDataDirectory.class).getWebappDir();
                    xslPath = webappDir.resolve("xslt/services/stac/" + params.toISOConversion + ".xsl");
                    log.info("Looking for STAC conversion at " + xslPath);
                }
                
                HashMap<String, Object> xslParams = new HashMap<>();
                if (uuid != null) {
                    xslParams.put("uuid", uuid);
                }
                return Xml.transform(input, xslPath, xslParams);
            } catch (Exception e) {
                errors.add(new HarvestError(this.context, e));
                log.error(String.format("Failed to apply conversion %s to record %s. Error is: %s",
                    params.toISOConversion, uuid, e.getMessage()));
                return null;
            }
        } else {
            return input;
        }
    }

    /**
     * Read the response of the URL.
     */
    private String retrieveUrl(String url) throws Exception {
        if (!Lib.net.isUrlValid(url))
            throw new BadParameterEx("Invalid URL", url);
        HttpGet httpMethod = null;
        ClientHttpResponse httpResponse = null;

        try {
            httpMethod = new HttpGet(createUrl(url));
            httpResponse = requestFactory.execute(httpMethod);
            int status = httpResponse.getRawStatusCode();
            Log.debug(LOGGER_NAME, "Request status code: " + status);
            return CharStreams.toString(new InputStreamReader(httpResponse.getBody()));
        } finally {
            if (httpMethod != null) {
                httpMethod.releaseConnection();
            }
            if (httpResponse != null) {
                try {
                    httpResponse.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    private static final String STAC_VERSION = "stac_version";
    private static final String LINKS = "links";
    private static final String REL = "rel";
    private static final String FEATURES = "/features";
    private static final String TYPE = "type";
    private static final String PROPERTIES = "properties";
    private static final String ASSETS = "assets";
    private static final String FEATURE = "Feature";
    private static final String FEATURECOLLECTION = "FeatureCollection";

    /**
     * Determines if a JSON content is STAC-like by checking for key STAC characteristics.
     * This method checks for STAC version field, STAC-specific links,
     * STAC features and other STAC-specific properties.
     * 
     * @param content The JSON content to check
     * @return true if the content appears to be STAC, false otherwise
     */
    private boolean isSTACLike(String content) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonObj = objectMapper.readTree(content);

            // 1. Check for stac_version field which is required in STAC spec
            if (jsonObj.has(STAC_VERSION)) {
                return true;
            }

            // 2. Check for common STAC links
            if (hasStacLinks(jsonObj)) {
                return true;
            }

            // 3. Check for features array with STAC properties
            if (hasStacFeatures(jsonObj)) {
                return true;
            }
            
            // 4. Check if it's a STAC Item (has type "Feature" and properties/assets)
            if (jsonObj.has(TYPE) && 
                (FEATURE.equals(jsonObj.get(TYPE).asText()) || 
                 FEATURECOLLECTION.equals(jsonObj.get(TYPE).asText())) &&
                (jsonObj.has(PROPERTIES) || jsonObj.has(ASSETS))) {
                return true;
            }
        } catch (Exception e) {
            // If we can't parse as JSON, it's not a STAC response
            return false;
        }
        return false;
    }

    /**
     * Checks if a JSON object has STAC-specific links
     * 
     * @param jsonObj The JSON object to check
     * @return true if STAC-specific links are found, false otherwise
     */
    private boolean hasStacLinks(JsonNode jsonObj) {
        if (jsonObj.has(LINKS) && jsonObj.get(LINKS).isArray()) {
            for (JsonNode link : jsonObj.get(LINKS)) {
                if (link.has(REL)) {
                    String rel = link.get(REL).asText();
                    if (rel.equals("self") || rel.equals("items") ||
                        rel.equals("collections") || rel.equals("root") ||
                        rel.equals("parent") || rel.equals("child") ||
                        rel.equals("collection")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if a JSON object has STAC features
     * 
     * @param jsonObj The JSON object to check
     * @return true if STAC features are found, false otherwise
     */
    private boolean hasStacFeatures(JsonNode jsonObj) {
        // Handle both "/features" path notation and direct "features" property
        JsonNode features = jsonObj.has("features") ? 
            jsonObj.get("features") : jsonObj.at(FEATURES);
            
        if (features != null && features.isArray() && features.size() > 0) {
            JsonNode firstFeature = features.get(0);
            if (firstFeature.has(TYPE) &&
                firstFeature.get(TYPE).asText().equals(FEATURE) &&
                (firstFeature.has(PROPERTIES) || firstFeature.has(ASSETS))) {
                return true;
            }
        }
        return false;
    }

    private URI createUrl(String jsonUrl) throws URISyntaxException {
        return new URI(jsonUrl);
    }
    
    /**
     * Extracts text value from various element types
     */
    private String getXmlElementTextValue(Object element) {
        String s = null;
        if (element instanceof Text) {
            s = ((Text) element).getTextNormalize();
        } else if (element instanceof Attribute) {
            s = ((Attribute) element).getValue();
        } else if (element instanceof String) {
            s = (String) element;
        } else if (element instanceof Element) {
            s = ((Element) element).getText();
        }
        return s;
    }

    /**
     * Extracts UUID from an identifier, handling URL formats
     */
    private String extractUuidFromIdentifier(final String identifier) {
        String uuid = identifier;
        if (Lib.net.isUrlValid(uuid)) {
            uuid = uuid.replaceFirst(".*/([^/?]+).*", "$1");
        }
        return uuid;
    }

    /**
     * Builds a list of URLs for paging through results
     */
    @VisibleForTesting
    protected List<String> buildListOfUrl(SimpleUrlParams params, int numberOfRecordsToHarvest) {
        List<String> urlList = new ArrayList<>();
        if (StringUtils.isEmpty(params.pageSizeParam)) {
            urlList.add(params.url);
            return urlList;
        }

        int numberOfRecordsPerPage = -1;
        final String pageSizeParamValue = params.url.replaceAll(".*[?&]" + params.pageSizeParam + "=([0-9]+).*", "$1");
        if (StringUtils.isNumeric(pageSizeParamValue)) {
            numberOfRecordsPerPage = Integer.parseInt(pageSizeParamValue);
        } else {
            log.warning(String.format(
                "Page size param '%s' not found or is not a numeric in URL '%s'. Can't build a list of pages.",
                params.pageSizeParam, params.url));
            urlList.add(params.url);
            return urlList;
        }

        final String pageFromParamValue = params.url.replaceAll(".*[?&]" + params.pageFromParam + "=([0-9]+).*", "$1");
        boolean startAtZero = false;
        if (StringUtils.isNumeric(pageFromParamValue)) {
            startAtZero = Integer.parseInt(pageFromParamValue) == 0;
        } else {
            log.warning(String.format(
                "Page from param '%s' not found or is not a numeric in URL '%s'. Can't build a list of pages.",
                params.pageFromParam, params.url));
            urlList.add(params.url);
            return urlList;
        }


        int numberOfPages = Math.abs((numberOfRecordsToHarvest + (startAtZero ? -1 : 0)) / numberOfRecordsPerPage) + 1;

        for (int i = 0; i < numberOfPages; i++) {
            int from = i * numberOfRecordsPerPage + (startAtZero ? 0 : 1);
            int size = i == numberOfPages - 1 ? // Last page
                numberOfRecordsToHarvest - from + (startAtZero ? 0 : 1) :
                numberOfRecordsPerPage;
            String url = params.url
                .replaceAll(params.pageFromParam + "=[0-9]+", params.pageFromParam + "=" + from)
                .replaceAll(params.pageSizeParam + "=[0-9]+", params.pageSizeParam + "=" + size);
            urlList.add(url);
        }

        return urlList;
    }
}
