/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2013-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.vaadin.dashboard.dashlets;

import org.opennms.netmgt.dao.api.GraphDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.ResourceDao;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsResource;
import org.opennms.netmgt.model.OnmsResourceType;
import org.opennms.netmgt.model.PrefabGraph;
import org.opennms.web.api.Util;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.util.*;

/**
 * This helper class iterates through the resourceTypes and resources and return a list of graph urls.
 *
 * @author Christian Pape
 */
public class RrdGraphHelper {
    /**
     * the resource dao instance
     */
    private ResourceDao m_resourceDao;
    /**
     * the graph dao instance
     */
    private GraphDao m_graphDao;
    /**
     * the node dao instance
     */
    private NodeDao m_nodeDao;
    /**
     * the {@link TransactionOperations} instance
     */
    private TransactionOperations m_transactionOperations;

    /**
     * Default constructor for instantiating new objects
     */
    public RrdGraphHelper() {
    }

    /**
     * Creates the image url for a given graph parameter and width/height.
     *
     * @param query         the parameter combination for this report graph
     * @param width         the width
     * @param height        the height
     * @param calendarField the calendar field to substract from
     * @param calendarDiff  the value to be substracted
     * @return the url string
     */
    public String imageUrlForGraph(String query, int width, int height, int calendarField, int calendarDiff) {
        Calendar cal = new GregorianCalendar();
        long end = cal.getTime().getTime();
        cal.add(calendarField, -calendarDiff);
        long start = cal.getTime().getTime();
        return "/opennms/graph/graph.png?" + query + "&start=" + start + "&end=" + end + (width > 0 ? "&width=" + width : "") + (height > 0 ? "&height=" + height : "");
    }

    /**
     * Creates the image url for a given graph parameter and width/height.
     *
     * @param query  the parameter combination for this report graph
     * @param width  the width
     * @param height the height
     * @return the url string
     */
    public String imageUrlForGraph(String query, int width, int height) {
        return imageUrlForGraph(query, width, height, Calendar.HOUR_OF_DAY, 1);
    }

    /**
     * Returns the graph entries name/title mapping for a given resourceId.
     *
     * @param resourceId the resourceId
     * @return a map of names/titles found
     */
    public Map<String, String> getGraphNameTitleMappingForResourceId(final String resourceId) {
        return m_transactionOperations.execute(new TransactionCallback<Map<String, String>>() {
            @Override
            public Map<String, String> doInTransaction(TransactionStatus transactionStatus) {
                OnmsResource resource = m_resourceDao.getResourceById(resourceId);
                PrefabGraph[] queries = m_graphDao.getPrefabGraphsForResource(resource);

                Map<String, String> graphResults = new TreeMap<String, String>();

                for (PrefabGraph query : queries) {
                    graphResults.put(query.getName(), query.getTitle());
                }

                return graphResults;
            }
        });
    }

    /**
     * Returns the graph entries title/name mapping for a given resourceId.
     *
     * @param resourceId the resourceId
     * @return a map of titles/names found
     */
    public Map<String, String> getGraphTitleNameMappingForResourceId(final String resourceId) {
        return m_transactionOperations.execute(new TransactionCallback<Map<String, String>>() {
            @Override
            public Map<String, String> doInTransaction(TransactionStatus transactionStatus) {
                OnmsResource resource = m_resourceDao.getResourceById(resourceId);
                PrefabGraph[] queries = m_graphDao.getPrefabGraphsForResource(resource);

                Map<String, String> graphResults = new TreeMap<String, String>();

                for (PrefabGraph query : queries) {
                    graphResults.put(query.getTitle(), query.getName());
                }

                return graphResults;
            }
        });
    }

    /**
     * Returns the graph entries for a given resourceId.
     *
     * @param resourceId the resourceId
     * @return a map of graphs found
     */
    public Map<String, String> getGraphResultsForResourceId(final String resourceId) {
        return m_transactionOperations.execute(new TransactionCallback<Map<String, String>>() {
            @Override
            public Map<String, String> doInTransaction(TransactionStatus transactionStatus) {
                OnmsResource resource = m_resourceDao.getResourceById(resourceId);
                PrefabGraph[] queries = m_graphDao.getPrefabGraphsForResource(resource);

                Map<String, String> graphResults = new TreeMap<String, String>();

                for (PrefabGraph query : queries) {
                    graphResults.put(query.getName(), "resourceId=" + resourceId + "&report=" + query.getName());
                }

                return graphResults;
            }
        });
    }

    /**
     * Returns a map of resources for a given resourceType.
     *
     * @param nodeId the nodeId to search for resourceTypes
     * @return the map of resources
     */
    public Map<OnmsResourceType, List<OnmsResource>> getResourceTypeMapForNodeId(int nodeId) {
        return getResourceTypeMapForNodeId(String.valueOf(nodeId));
    }

    /**
     * Returns a map of resources for a given resourceType.
     *
     * @param nodeId the nodeId to search for resourceTypes
     * @return the map of resources
     */
    public Map<OnmsResourceType, List<OnmsResource>> getResourceTypeMapForNodeId(final String nodeId) {
        return m_transactionOperations.execute(new TransactionCallback<Map<OnmsResourceType, List<OnmsResource>>>() {
            @Override
            public Map<OnmsResourceType, List<OnmsResource>> doInTransaction(TransactionStatus transactionStatus) {
                OnmsResource resource = m_resourceDao.getResourceById("node[" + nodeId + "]");

                Map<OnmsResourceType, List<OnmsResource>> resourceTypeMap = new LinkedHashMap<OnmsResourceType, List<OnmsResource>>();
                for (OnmsResource childResource : resource.getChildResources()) {
                    if (!resourceTypeMap.containsKey(childResource.getResourceType())) {
                        resourceTypeMap.put(childResource.getResourceType(), new LinkedList<OnmsResource>());
                    }
                    resourceTypeMap.get(childResource.getResourceType()).add(checkLabelForQuotes(childResource));
                }

                return resourceTypeMap;
            }
        });
    }

    /**
     * Returns a list of nodes with resources
     *
     * @return a list of nodes
     */
    public List<OnmsNode> getNodesWithResources() {
        return m_transactionOperations.execute(new TransactionCallback<List<OnmsNode>>() {
            @Override
            public List<OnmsNode> doInTransaction(TransactionStatus transactionStatus) {
                List<OnmsNode> onmsNodeList = m_nodeDao.findAll();
                for (int i = onmsNodeList.size() - 1; i >= 0; i--) {
                    OnmsResource resource = m_resourceDao.getResourceById("node[" + onmsNodeList.get(i).getId() + "]");
                    if (resource.getChildResources().size() == 0) {
                        onmsNodeList.remove(i);
                    }
                }
                return onmsNodeList;
            }
        });
    }

    /**
     * Checks a resource label for quotes.
     *
     * @param childResource the child resource to check
     * @return the resource
     */
    private OnmsResource checkLabelForQuotes(OnmsResource childResource) {
        String lbl = Util.convertToJsSafeString(childResource.getLabel());
        OnmsResource resource = new OnmsResource(childResource.getName(), lbl, childResource.getResourceType(), childResource.getAttributes());
        resource.setParent(childResource.getParent());
        resource.setEntity(childResource.getEntity());
        resource.setLink(childResource.getLink());
        return resource;
    }

    /**
     * This method sets the node dao.
     *
     * @param nodeDao the node dao to set
     */
    public void setNodeDao(NodeDao nodeDao) {
        m_nodeDao = nodeDao;
    }

    /**
     * This method sets the graph dao.
     *
     * @param graphDao the graph dao to set
     */
    public void setGraphDao(GraphDao graphDao) {
        m_graphDao = graphDao;
    }

    /**
     * This method sets the resource dao.
     *
     * @param resourceDao the resource dao to set
     */
    public void setResourceDao(ResourceDao resourceDao) {
        m_resourceDao = resourceDao;
    }

    /**
     * This method sets the {@link TransactionOperations} instance.
     *
     * @param transactionOperations the instance to be set
     */
    public void setTransactionOperations(TransactionOperations transactionOperations) {
        m_transactionOperations = transactionOperations;
    }
}
