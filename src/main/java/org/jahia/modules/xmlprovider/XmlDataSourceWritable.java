package org.jahia.modules.xmlprovider;

import com.google.common.collect.Sets;
import net.sf.ehcache.Ehcache;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.external.ExternalData;
import org.jahia.modules.external.ExternalDataSource;
import org.jahia.modules.external.ExternalQuery;
import org.jahia.modules.external.query.QueryHelper;
import org.jahia.modules.xmlprovider.utils.XmlUtils;
import org.jahia.services.cache.ehcache.EhCacheProvider;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import to.XmlActivityTO;

/**
 * Created by Quentin on 12/09/15.
 */
public class XmlDataSourceWritable implements ExternalDataSource, ExternalDataSource.Writable, ExternalDataSource.Searchable {

    // Logger
    private static final Logger LOGGER = LoggerFactory.getLogger(org.jahia.modules.xmlprovider.XmlDataSourceWritable.class);

    // The XML file
    public static String XML_FILE_PATH = "";

    // Cache
    private EhCacheProvider ehCacheProvider;
    private Ehcache cache;
    private static final String CACHE_NAME           = "xml-cache";
    private static final String CACHE_XML_ACTVITIES  = "cacheXmlActivities";


    // Node types
    private static final String JNT_XML_ACTIVITY    = "jnt:xmlActivity";
    private static final String JNT_CONTENT_FOLDER  = "jnt:contentFolder";


    // Properties : strava
    private static final String ID          = "id";
    private static final String NAME        = "name";
    private static final String DISTANCE    = "distance";
    private static final String TYPE        = "type";
    private static final String MOVING_TIME = "moving_time";
    private static final String START_DATE  = "start_date";


    // Properties : JCR
    private static final String ROOT  = "root";


    // Constants
    private static final String ACTIVITY = "activity";
    private static final Logger logger   = LoggerFactory.getLogger(org.jahia.modules.xmlprovider.XmlDataSourceWritable.class);


    // CONSTRUCTOR
    public XmlDataSourceWritable(){}

    // GETTERS AND SETTERS
    public void setCacheProvider(EhCacheProvider ehCacheProvider) {
        this.ehCacheProvider = ehCacheProvider;
    }

    public String getXmlFilePath() {
        return XML_FILE_PATH;
    }

    public void setXmlFilePath(String xmlFilePath) {
        XmlDataSourceWritable.XML_FILE_PATH = xmlFilePath;
    }

    // METHODS
    public void start() {
        // Init method defined in the bean : XmlDataSourceWritable
        try {
            if (!ehCacheProvider.getCacheManager().cacheExists(CACHE_NAME)) {
                ehCacheProvider.getCacheManager().addCache(CACHE_NAME);
            }
            cache = ehCacheProvider.getCacheManager().getCache(CACHE_NAME);
        } catch (Exception e) {
            LOGGER.error("Error with the cache : " + e.getMessage());
        }
    }

    /**
     * get all the content in json object.
     *
     * @return
     * @throws RepositoryException
     */
    private JSONArray queryXML() throws RepositoryException {
        try {
            if(StringUtils.isEmpty(XML_FILE_PATH))
                return  new JSONArray("[]");
            else
                logger.info("queryXML(), parsing the file:" + XML_FILE_PATH);


            StringBuilder jsonData = new StringBuilder();
            File xmlFile = new File(XML_FILE_PATH);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);

            //optional, but recommended
            doc.getDocumentElement().normalize();

            logger.info("queryXML(), XML Root element :" + doc.getDocumentElement().getNodeName());

            NodeList nList = doc.getElementsByTagName("activity");

            for (int index = 0; index < nList.getLength(); index++) {
                Node nNode = nList.item(index);
                logger.info("queryXML(), XML Current Element :" + nNode.getNodeName());

                if (nNode.getNodeType() == Node.ELEMENT_NODE) {

                    Element eElement = (Element) nNode;
                    if(eElement != null) {
                        if (jsonData.length() != 0) {
                            jsonData.append(",");
                        }
                        jsonData.append((new XmlActivityTO(eElement)).toJsonString());
                    }
                }
            }

            JSONArray jsonArray = new JSONArray("[" + jsonData.toString() + "]");
            cache.put(new net.sf.ehcache.Element(CACHE_XML_ACTVITIES, jsonArray));
            return jsonArray;
        }
         catch (Exception e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * get all the content in json object by id.
     *
     * @param id
     * @return
     * @throws RepositoryException
     */
    private JSONObject queryXMLJSONObject(int id) throws RepositoryException {
        try {
            return (JSONObject) getCacheXMLActivities(true).get(id);
        } catch (Exception e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * get the data from the cache.
     *
     * @param deleteCache
     * @return
     * @throws RepositoryException
     */
    private JSONArray getCacheXMLActivities(boolean deleteCache) throws RepositoryException {
        JSONArray activities;
        if (cache.get(CACHE_XML_ACTVITIES) != null && !deleteCache) {
            activities = (JSONArray) cache.get(CACHE_XML_ACTVITIES).getObjectValue();
        } else {
            LOGGER.info("Refresh the activities");
            activities = queryXML();
        }
        return activities;
    }


    // IMPLEMENTS : ExternalDataSource

    /**
     * get all the children's by path
     *
     * @param path
     * @return
     * @throws RepositoryException
     */
    @Override
    public List<String> getChildren(String path) throws RepositoryException {
        List<String> r = new ArrayList<>();
        if (path.equals("/")) {
            try {
                JSONArray activities = getCacheXMLActivities(true);
                for (int i = 1; i <= activities.length(); i++) {
                    JSONObject activity = (JSONObject) activities.get(i - 1);
                    r.add(XmlUtils.displayNumberTwoDigits(i) + "-" + ACTIVITY + "-" + activity.get(ID));
                }
            } catch (JSONException e) {
                throw new RepositoryException(e);
            }
        }
        return r;
    }

    /**
     * get item by id.
     *
     * @param identifier
     * @return
     * @throws ItemNotFoundException
     */
    @Override
    public ExternalData getItemByIdentifier(String identifier) throws ItemNotFoundException {
        if (identifier.equals(ROOT)) {
            return new ExternalData(identifier, "/", JNT_CONTENT_FOLDER, new HashMap<String, String[]>());
        }
        Map<String, String[]> properties = new HashMap<>();
        String[] idActivity = identifier.split("-");
        if (idActivity.length == 3) {
            try {
                JSONArray activities = getCacheXMLActivities(false);
                // Find the activity by its identifier
                int numActivity = Integer.parseInt(idActivity[0]) - 1;
                JSONObject activity = (JSONObject) activities.get(numActivity);
                // Add some properties
                properties.put(ID,          new String[]{ activity.getString(ID)   });
                properties.put(NAME,        new String[]{ activity.getString(NAME) });
                properties.put(TYPE,        new String[]{ activity.getString(TYPE) });
                properties.put(DISTANCE,    new String[]{ XmlUtils.displayDistance(activity.getString(DISTANCE))      });
                properties.put(MOVING_TIME, new String[]{ XmlUtils.displayMovingTime(activity.getString(MOVING_TIME)) });
                properties.put(START_DATE,  new String[]{ XmlUtils.displayStartDate(activity.getString(START_DATE))   });
                // Return the external data (a node)
                ExternalData data = new ExternalData(identifier, "/" + identifier, JNT_XML_ACTIVITY, properties);
                return data;
            } catch (Exception e) {
                throw new ItemNotFoundException(identifier);
            }
        } else {
            // Node not again created
            throw new ItemNotFoundException(identifier);
        }
    }

    /**
     * get item by path.
     *
     * @param path
     * @return
     * @throws PathNotFoundException
     */
    @Override
    public ExternalData getItemByPath(String path) throws PathNotFoundException {
        String[] splitPath = path.split("/");
        try {
            if (splitPath.length <= 1) {
                return getItemByIdentifier(ROOT);
            } else {
                return getItemByIdentifier(splitPath[1]);
            }
        } catch (ItemNotFoundException e) {
            throw new PathNotFoundException(e);
        }
    }


    // Implements : ExternalDataSource and ExternalDataSource.Searchable

    /**
     * get all the children's by query
     *
     * @param query
     * @return
     * @throws RepositoryException
     */
    @Override
    public List<String> search(ExternalQuery query) throws RepositoryException {
        List<String> paths = new ArrayList<>();
        String nodeType = QueryHelper.getNodeType(query.getSource());
        if (NodeTypeRegistry.getInstance().getNodeType(JNT_XML_ACTIVITY).isNodeType(nodeType)) {
            try {
                JSONArray activities = getCacheXMLActivities(false);
                for (int i = 1; i <= activities.length(); i++) {
                    JSONObject activity = (JSONObject) activities.get(i - 1);
                    String path = "/" + XmlUtils.displayNumberTwoDigits(i) + "-" + ACTIVITY + "-" + activity.get(ID);
                    paths.add(path);
                }
            } catch (JSONException e) {
                throw new RepositoryException(e);
            }
        }
        return paths;
    }

    // Implements : ExternalDataSource.Writable

    /**
     * save a new item.
     *
     * @param data
     * @throws RepositoryException
     */
    @Override
    public void saveItem(ExternalData data) throws RepositoryException {
        try{
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(XML_FILE_PATH);

            Map<String, String[]>  activityProps = data.getProperties();
            String activityId = activityProps.containsKey(ID) ? activityProps.get(ID)[0] : "";
            boolean isNew = true;

            Element rootElement = doc.getDocumentElement();
            NodeList nList = doc.getElementsByTagName("activity");

            for (int index = 0; index < nList.getLength(); index++) {
                Node nNode = nList.item(index);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;

                    XmlActivityTO activityTO = new XmlActivityTO(eElement);
                    if(activityTO.getId().trim().equals(activityId.trim())) {
                        setValue(NAME, eElement, activityProps.containsKey(NAME) ? activityProps.get(NAME)[0] : "");
                        setValue(DISTANCE, eElement, activityProps.containsKey(DISTANCE) ? activityProps.get(DISTANCE)[0] : "");
                        setValue(TYPE, eElement, activityProps.containsKey(TYPE) ? activityProps.get(TYPE)[0] : "");
                        setValue(MOVING_TIME, eElement, activityProps.containsKey(MOVING_TIME) ? activityProps.get(MOVING_TIME)[0] : "");
                        setValue(START_DATE, eElement, activityProps.containsKey(START_DATE) ? activityProps.get(START_DATE)[0] : "");
                        logger.info("saveItem(), XML update Current Element :" + nNode.getNodeName());
                        isNew = false;
                    }
                }
            }

            if(isNew){
                Element newActivity = doc.createElement("activity");

                Element idElement = doc.createElement(ID);
                idElement.appendChild( doc.createTextNode(activityProps.containsKey(ID) ? activityProps.get(ID)[0] : ""));

                Element nameElement = doc.createElement(NAME);
                nameElement.appendChild(doc.createTextNode(activityProps.containsKey(NAME) ? activityProps.get(NAME)[0] : ""));

                Element distanceElement = doc.createElement(DISTANCE);
                distanceElement.appendChild(doc.createTextNode(activityProps.containsKey(DISTANCE) ? activityProps.get(DISTANCE)[0] : ""));

                Element typeElement = doc.createElement(TYPE);
                typeElement.appendChild(doc.createTextNode(activityProps.containsKey(TYPE) ? activityProps.get(TYPE)[0] : ""));

                Element movingTimeElement = doc.createElement(MOVING_TIME);
                movingTimeElement.appendChild(doc.createTextNode(activityProps.containsKey(MOVING_TIME) ? activityProps.get(MOVING_TIME)[0] : ""));

                Element startDateElement = doc.createElement(START_DATE);
                startDateElement.appendChild(doc.createTextNode(activityProps.containsKey(START_DATE) ? activityProps.get(START_DATE)[0] : ""));

                rootElement.appendChild(newActivity);
                newActivity.appendChild(idElement);
                newActivity.appendChild(nameElement);
                newActivity.appendChild(distanceElement);
                newActivity.appendChild(typeElement);
                newActivity.appendChild(movingTimeElement);
                newActivity.appendChild(startDateElement);
            }

            // write the content into xml file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(XML_FILE_PATH));
            transformer.transform(source, result);

            LOGGER.info("saveItem(), Done......");

        }catch(Exception e){
            LOGGER.error("saveItem(), can't save the item, ", e);
        }
    }

    /**
     * set value.
     *
     * @param tag
     * @param element
     * @param input
     */
    private static void setValue(String tag, Element element , String input) {
        NodeList nodes = element.getElementsByTagName(tag).item(0).getChildNodes();
        Node node = (Node) nodes.item(0);
        node.setTextContent(input);
    }

    /**
     * getSupportedNodeTypes
     *
     * @return Set<String>
     */
    @Override
    public Set<String> getSupportedNodeTypes() {
        return Sets.newHashSet(JNT_CONTENT_FOLDER, JNT_XML_ACTIVITY);
    }

    /**
     * isSupportsHierarchicalIdentifiers
     *
     * @return boolean
     */
    @Override
    public boolean isSupportsHierarchicalIdentifiers() {
        return false;
    }

    /**
     * isSupportsUuid
     *
     * @return boolean
     */
    @Override
    public boolean isSupportsUuid() {
        return false;
    }

    /**
     * itemExists
     *
     * @param path
     * @return boolean
     */
    @Override
    public boolean itemExists(String path) {
        return false;
    }

    /**
     * move
     *
     * @param oldPath
     * @param newPath
     * @throws RepositoryException
     */
    @Override
    public void move(String oldPath, String newPath) throws RepositoryException {
        LOGGER.info("Move : oldPath=" + oldPath + " newPath=" + newPath);
    }

    /**
     * order
     *
     * @param path
     * @param children
     * @throws RepositoryException
     */
    @Override
    public void order(String path, List<String> children) throws RepositoryException {
        LOGGER.info("Order : path=" + path);
    }

    /**
     * removeItemByPath
     *
     * @param path
     * @throws RepositoryException
     */
    @Override
    public void removeItemByPath(String path) throws RepositoryException {
        LOGGER.info("Remove item by path : path=" + path);
    }

}
