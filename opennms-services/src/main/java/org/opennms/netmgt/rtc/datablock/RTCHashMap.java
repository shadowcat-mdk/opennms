/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2002-2014 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.rtc.datablock;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The RTCHashMap has either a nodeid or a nodeid/ip as key and provides
 * convenience methods to add and remove 'RTCNodes' with these values - each key
 * points to a list of 'RTCNode's
 *
 * @author <A HREF="mailto:sowmya@opennms.org">Sowmya Kumaraswamy </A>
 * @author <A HREF="http://www.opennms.org">OpenNMS.org </A>
 */
// FIXME: THIS IS INSANE
// FIXME: 2011-05-18 Seth: OK it is less insane now... but still insane
public class RTCHashMap {
	
    Map<RTCNodeKey,List<RTCNode>> m_map;
	
    /**
     * constructor
     *
     * @param initialCapacity a int.
     */
    public RTCHashMap(int initialCapacity) {
        m_map = new HashMap<RTCNodeKey,List<RTCNode>>(initialCapacity);
    }

    private List<Long> getNodeIDs() {
    	List<Long> nodes = new LinkedList<Long>();
    	for (Iterator<RTCNodeKey> it = m_map.keySet().iterator(); it.hasNext();) {
			RTCNodeKey key = it.next();
			nodes.add(key.getNodeID());
		}
    	return nodes;
    }
    
    /**
     * Add the node with nodeid as key
     * 
     * @param nodeid
     *            the nodeid
     * @param rtcN
     *            the RTCNode to add
     */
    private void add(long nodeid, RTCNode rtcN) {
        RTCNodeKey key = new RTCNodeKey(nodeid, null, null);

        List<RTCNode> nodesList = m_map.get(key);
        if (nodesList != null) {
            nodesList.add(rtcN);
        } else {
            // add current node to list
            nodesList = new ArrayList<RTCNode>();
            nodesList.add(rtcN);

            // add list to map
            m_map.put(key, nodesList);
        }
    }

    /**
     * Add the rtc node with nodeid and ip as key
     * 
     * @param nodeid
     *            the nodeid
     * @param inetAddress
     *            the ip
     * @param rtcN
     *            the RTCNode to add
     */
    private void add(long nodeid, InetAddress inetAddress, RTCNode rtcN) {
        RTCNodeKey key = new RTCNodeKey(nodeid, inetAddress, null);

        List<RTCNode> nodesList = m_map.get(key);
        if (nodesList != null) {
            nodesList.add(rtcN);
        } else {
            // add current node to list
            nodesList = new ArrayList<RTCNode>();
            nodesList.add(rtcN);

            // add list to map
            m_map.put(key, nodesList);
        }
    }
    
    private void add(long nodeid, InetAddress ip, String svcName, RTCNode rtcN) {
        m_map.put(new RTCNodeKey(nodeid, ip, svcName), Collections.singletonList(rtcN));
    }
    
    /**
     * Add an rtc node
     *
     * @param rtcN the rtcNode to add
     */
    public void add(RTCNode rtcN) {
    	add(rtcN.getNodeID(), rtcN);
    	add(rtcN.getNodeID(), rtcN.getIP(), rtcN);
    	add(rtcN.getNodeID(), rtcN.getIP(), rtcN.getSvcName(), rtcN);
    }
    
    /**
     * <p>delete</p>
     *
     * @param rtcN a {@link org.opennms.netmgt.rtc.datablock.RTCNode} object.
     */
    public void delete(RTCNode rtcN) {
    	delete(rtcN.getNodeID(), rtcN);
    	delete(rtcN.getNodeID(), rtcN.getIP(), rtcN);
    	delete(rtcN.getNodeID(), rtcN.getIP(), rtcN.getSvcName(), rtcN);
    }
    
    
    /**
     * Delete the node from list with nodeid as key
     * 
     * @param nodeid
     *            the nodeid
     * @param rtcN
     *            the RTCNode to delete
     */
    private void delete(long nodeid, RTCNode rtcN) {
        RTCNodeKey key = new RTCNodeKey(nodeid, null, null);

        List<RTCNode> nodesList = m_map.get(key);
        if (nodesList != null) {
            nodesList.remove(rtcN);
        }
    }

    /**
     * Delete the rtc node from list with nodeid and ip as key
     * 
     * @param nodeid
     *            the nodeid
     * @param inetAddress
     *            the ip
     * @param rtcN
     *            the RTCNode to add
     */
    private void delete(long nodeid, InetAddress inetAddress, RTCNode rtcN) {
        RTCNodeKey key = new RTCNodeKey(nodeid, inetAddress, null);

        List<RTCNode> nodesList = m_map.get(key);
        if (nodesList != null) {
            nodesList.remove(rtcN);
        }
    }
    
    private void delete(long nodeid, InetAddress ip, String svcName, RTCNode rtcN) {
    	RTCNodeKey key = new RTCNodeKey(nodeid, ip, svcName);
    	m_map.remove(key);
    }

    /**
     * Check if this IP has already been validated for this category
     *
     * @param nodeid
     *            the node id whose interface is to be validated
     * @param ip
     *            the ip to be validated
     * @param catLabel
     *            the category whose rule this ip is to pass
     * @return true if ip has already been validated, false otherwise
     */
    public boolean isIpValidated(long nodeid, InetAddress ip, String catLabel) {
        for (RTCNode node : getRTCNodes(nodeid, ip)) {
            if (node.belongsTo(catLabel)) {
                return true;
            }
        }

        return false;

    }

    /**
     * Get the value (uptime) for a category in the last 'rollingWindow'
     * starting at current time
     *
     * @param catLabel
     *            the category to which the node should belong to
     * @param curTime
     *            the current time
     * @param rollingWindow
     *            the window for which value is to be calculated
     * @return the value(uptime) for the node
     */
    public double getValue(String catLabel, long curTime, long rollingWindow) {
        // the value (uptime)
        double value = 0.0;

        // total outage time
        long outageTime = 0;

        // number of entries for this node
        int count = 0;

        // downtime for a node
        long downTime = 0;

        // get all nodes in the hashtable
        for (Long key : getNodeIDs()) {
            List<RTCNode> valList = getRTCNodes(key.longValue());
            if (valList == null || valList.size() == 0)
                continue;

            for (RTCNode node : valList) {
                downTime = node.getDownTime(catLabel, curTime, rollingWindow);
                if (downTime < 0)
                // node does not belong to category
                // or RTCConstants.SERVICE_NOT_FOUND_VALUE
                // or node / interface / service unmanaged
                {
                    continue;
                }

                outageTime += downTime;

                count++;

            }

        }

        double dOut = outageTime * 1.0;
        double dRoll = rollingWindow * 1.0;

        if (count > 0) {
            value = 100 * (1 - (dOut / (dRoll * count)));
        } else {
            value = 100.0;
        }

        return value;
    }

    /**
     * Get the value (uptime) for the a node that belongs to the category in the
     * last 'rollingWindow' starting at current time
     *
     * @param nodeid
     *            the node for which value is to be calculated
     * @param catLabel
     *            the category to which the node should belong to
     * @param curTime
     *            the current time
     * @param rollingWindow
     *            the window for which value is to be calculated
     * @return the value(uptime) for the node
     */
    public double getValue(long nodeid, String catLabel, long curTime, long rollingWindow) {
        // the value (uptime)
        double value = 0.0;

        // total outage time
        long outageTime = 0;

        // number of entries for this node
        int count = 0;

        // downtime for a node
        long downTime = 0;

        // get nodeslist
        for (RTCNode node : getRTCNodes(nodeid)) {
            if (node.getNodeID() == nodeid) {
                downTime = node.getDownTime(catLabel, curTime, rollingWindow);
                if (downTime < 0)
                // node does not belong to category
                // or RTCConstants.SERVICE_NOT_FOUND_VALUE
                // or node / interface / service unmanaged
                {
                    continue;
                }

                outageTime += downTime;

                count++;

            }
        }

        double dOut = outageTime * 1.0;
        double dRoll = rollingWindow * 1.0;

        if (count > 0) {
            value = 100 * (1 - (dOut / (dRoll * count)));
        } else {
            value = 100.0;
        }

        return value;
    }

    /**
     * Get the count of services for a node in the context of the the specified
     * category
     *
     * @param nodeid
     *            the node for which servicecount is needed
     * @param catLabel
     *            the category to which the node should belong to
     * @return the service count for the nodeid in the context of the specfied
     *         category
     */
    public int getServiceCount(long nodeid, String catLabel) {
        // the count
        int count = 0;

        // get nodeslist
        for (RTCNode node : getRTCNodes(nodeid)) {
            if (node.belongsTo(catLabel))
                count++;
        }

        return count;
    }

    /**
     * Get the count of services currently down for a node in the context of the
     * the specified category
     *
     * @param nodeid
     *            the node for which servicecount is needed
     * @param catLabel
     *            the category to which the node should belong to
     * @return the service down count for the nodeid in the context of the
     *         specfied category
     */
    public int getServiceDownCount(long nodeid, String catLabel) {
        // the count
        int count = 0;

        // get nodeslist
        for (RTCNode node : getRTCNodes(nodeid)) {
            if (node.belongsTo(catLabel) && node.isServiceCurrentlyDown()) {
                count++;
            }
        }

        return count;
    }

	/**
	 * <p>getRTCNode</p>
	 *
	 * @param key a {@link org.opennms.netmgt.rtc.datablock.RTCNodeKey} object.
	 * @return a {@link org.opennms.netmgt.rtc.datablock.RTCNode} object.
	 */
	public RTCNode getRTCNode(RTCNodeKey key) {
		List<RTCNode> nodes = m_map.get(key);
		if (nodes == null) return null;
		if (nodes.size() != 1) {
		    throw new IllegalStateException("Could not find single RTCNode that matched key: " + key.toString());
		}
		return nodes.get(0);
	}
	
	/**
	 * <p>getRTCNode</p>
	 *
	 * @param nodeid a long.
	 * @param ipaddr a {@link java.lang.String} object.
	 * @param svcname a {@link java.lang.String} object.
	 * @return a {@link org.opennms.netmgt.rtc.datablock.RTCNode} object.
	 */
	public RTCNode getRTCNode(long nodeid, InetAddress ipaddr, String svcname) {
		return getRTCNode(new RTCNodeKey(nodeid, ipaddr, svcname));
	}
	
	/**
	 * <p>getRTCNodes</p>
	 *
	 * @param nodeid a long.
	 * @return a {@link java.util.List} object.
	 */
	public List<RTCNode> getRTCNodes(long nodeid) {
		RTCNodeKey key = new RTCNodeKey(nodeid, null, null);
		List<RTCNode> nodes = m_map.get(key);
		if (nodes == null) return Collections.emptyList();
		return Collections.unmodifiableList(nodes); 
	}
	
	/**
	 * <p>getRTCNodes</p>
	 *
	 * @param nodeid a long.
	 * @param ip a {@link java.lang.String} object.
	 * @return a {@link java.util.List} object.
	 */
	public List<RTCNode> getRTCNodes(long nodeid, InetAddress ip) {
		RTCNodeKey key = new RTCNodeKey(nodeid, ip, null);
		List<RTCNode> nodes = m_map.get(key);
		if (nodes == null) return Collections.emptyList();
		return Collections.unmodifiableList(nodes);
	}

	/**
	 * <p>deleteNode</p>
	 *
	 * @param nodeid a long.
	 */
	public void deleteNode(long nodeid) {
	    // Construct a new ArrayList to contain the members of this collection
	    // to avoid running into a java.util.ConcurrentModificationException
	    // on the Collections.unmodifiableList() view.
		for (RTCNode node : new ArrayList<RTCNode>(getRTCNodes(nodeid))) {
			delete(node);
		}
	}
}
