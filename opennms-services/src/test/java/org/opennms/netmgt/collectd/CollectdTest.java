/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2014 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.collectd;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.opennms.core.utils.InetAddressUtils.addr;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.opennms.core.test.ConfigurationTestUtils;
import org.opennms.core.test.MockLogAppender;
import org.opennms.netmgt.collection.api.CollectionAgent;
import org.opennms.netmgt.collection.api.CollectionException;
import org.opennms.netmgt.collection.api.CollectionInitializationException;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.CollectionSetVisitor;
import org.opennms.netmgt.collection.api.ServiceCollector;
import org.opennms.netmgt.collection.api.ServiceParameters;
import org.opennms.netmgt.collection.support.AbstractCollectionSet;
import org.opennms.netmgt.config.CollectdConfigFactory;
import org.opennms.netmgt.config.PollOutagesConfigFactory;
import org.opennms.netmgt.config.ThresholdingConfigFactory;
import org.opennms.netmgt.config.collectd.CollectdConfiguration;
import org.opennms.netmgt.config.collectd.Collector;
import org.opennms.netmgt.config.collectd.Filter;
import org.opennms.netmgt.config.collectd.Package;
import org.opennms.netmgt.config.collectd.Parameter;
import org.opennms.netmgt.config.collectd.Service;
import org.opennms.netmgt.dao.api.IpInterfaceDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.mock.MockTransactionTemplate;
import org.opennms.netmgt.filter.FilterDao;
import org.opennms.netmgt.filter.FilterDaoFactory;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.events.EventIpcManager;
import org.opennms.netmgt.model.events.EventIpcManagerFactory;
import org.opennms.netmgt.model.events.EventListener;
import org.opennms.netmgt.model.events.EventProxy;
import org.opennms.netmgt.poller.mock.MockScheduler;
import org.opennms.netmgt.rrd.RrdRepository;
import org.opennms.netmgt.scheduler.ReadyRunnable;
import org.opennms.netmgt.scheduler.Scheduler;
import org.opennms.test.mock.EasyMockUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

public class CollectdTest extends TestCase {

    private final EasyMockUtils m_easyMockUtils = new EasyMockUtils();

    private IpInterfaceDao m_ipIfDao;
    private FilterDao m_filterDao;
    private Collectd m_collectd;
    private MockScheduler m_scheduler;
    private CollectdConfiguration m_collectdConfig;
    private CollectdConfigFactory m_collectdConfigFactory;

    @Override
    protected void setUp() throws Exception {
        EventIpcManager m_eventIpcManager;
        NodeDao m_nodeDao;

        MockLogAppender.setupLogging();

        Resource threshdResource = new ClassPathResource("/etc/thresholds.xml");
        File homeDir = threshdResource.getFile().getParentFile().getParentFile();
        System.setProperty("opennms.home", homeDir.getAbsolutePath());

        // Test setup
        m_eventIpcManager = m_easyMockUtils.createMock(EventIpcManager.class);
        EventIpcManagerFactory.setIpcManager(m_eventIpcManager);
        m_nodeDao = m_easyMockUtils.createMock(NodeDao.class);
        m_ipIfDao = m_easyMockUtils.createMock(IpInterfaceDao.class);
        m_scheduler = new MockScheduler();

        m_eventIpcManager.addEventListener(isA(EventListener.class));
        expectLastCall().anyTimes();
        m_eventIpcManager.addEventListener(isA(EventListener.class), isACollection(String.class));
        expectLastCall().anyTimes();
        m_eventIpcManager.addEventListener(isA(EventListener.class), isA(String.class));
        expectLastCall().anyTimes();
        m_eventIpcManager.removeEventListener(isA(EventListener.class));
        expectLastCall().anyTimes();

//        MockNetwork m_network = new MockNetwork();
//        m_network.setCriticalService("ICMP");
//        m_network.addNode(1, "Router");
//        m_network.addInterface("192.168.1.1");
//        m_network.addService("ICMP");
//        m_network.addService("SMTP");
//        m_network.addInterface("192.168.1.2");
//        m_network.addService("ICMP");
//        m_network.addService("SMTP");
//        m_network.addNode(2, "Server");
//        m_network.addInterface("192.168.1.3");
//        m_network.addService("ICMP");
//        m_network.addService("HTTP");
//        m_network.addNode(3, "Firewall");
//        m_network.addInterface("192.168.1.4");
//        m_network.addService("SMTP");
//        m_network.addService("HTTP");
//        m_network.addInterface("192.168.1.5");
//        m_network.addService("SMTP");
//        m_network.addService("HTTP");
//
//        MockDatabase m_db = new MockDatabase();
//        m_db.populate(m_network);
//
//        DataSourceFactory.setInstance(m_db);

        // Mock the FilterDao without using EasyMockUtils so that it can be verified separately
        m_filterDao = EasyMock.createMock(FilterDao.class);
        List<InetAddress> allIps = new ArrayList<InetAddress>();
        allIps.add(addr("192.168.1.1"));
        allIps.add(addr("192.168.1.2"));
        allIps.add(addr("192.168.1.3"));
        allIps.add(addr("192.168.1.4"));
        allIps.add(addr("192.168.1.5"));
        expect(m_filterDao.getActiveIPAddressList("IPADDR IPLIKE *.*.*.*")).andReturn(allIps).anyTimes();
        expect(m_filterDao.getActiveIPAddressList("IPADDR IPLIKE 1.1.1.1")).andReturn(new ArrayList<InetAddress>(0)).anyTimes();
        EasyMock.replay(m_filterDao);
        FilterDaoFactory.setInstance(m_filterDao);

        // This call will also ensure that the poll-outages.xml file can parse IPv4
        // and IPv6 addresses.
        Resource resource = new ClassPathResource("etc/poll-outages.xml");
        PollOutagesConfigFactory factory = new PollOutagesConfigFactory(resource);
        factory.afterPropertiesSet();
        PollOutagesConfigFactory.setInstance(factory);

        final MockTransactionTemplate transTemplate = new MockTransactionTemplate();
        transTemplate.afterPropertiesSet();

        m_collectd = new Collectd();
        m_collectd.setEventIpcManager(m_eventIpcManager);
        //m_collectd.setCollectdConfigFactory(m_collectdConfigFactory);
        m_collectd.setNodeDao(m_nodeDao);
        m_collectd.setIpInterfaceDao(m_ipIfDao);
        m_collectd.setFilterDao(m_filterDao);
        m_collectd.setScheduler(m_scheduler);
        m_collectd.setTransactionTemplate(transTemplate);
        //m_collectd.afterPropertiesSet();

        
        ThresholdingConfigFactory.setInstance(new ThresholdingConfigFactory(ConfigurationTestUtils.getInputStreamForConfigFile("thresholds.xml")));
    }

    private static Package getCollectionPackageThatMatchesSNMP() {
        Package pkg = new Package();
        pkg.setName("pkg");
        Filter filter = new Filter();
        filter.setContent("IPADDR IPLIKE *.*.*.*");
        pkg.setFilter(filter);
        Service svc = new Service();
        pkg.addService(svc);
        svc.setName("SNMP");
        svc.setStatus("on");
        Parameter parm = new Parameter();
        parm.setKey("collection");
        parm.setValue("default");
        svc.addParameter(parm);
        parm = new Parameter();
        parm.setKey("thresholding-enabled");
        parm.setValue("true");
        svc.addParameter(parm);
        svc.setStatus("on");

        return pkg;
    }

    @Override
    protected void runTest() throws Throwable {
        super.runTest();

        // FIXME: we get a Threshd warning still if we enable this  :(
        // MockLogAppender.assertNoWarningsOrGreater();

        EasyMock.verify(m_filterDao);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private static OnmsIpInterface getInterface() {
        OnmsNode node = new OnmsNode();
        node.setId(1);
        OnmsIpInterface iface = new OnmsIpInterface("192.168.1.1", node);
        iface.setId(1);
        return iface;
    }

    public void testCreate() throws Exception {

        setupCollector("SNMP", false);
        setupTransactionManager();

        // Use a mock scheduler to track calls to the Collectd scheduler
        Scheduler m_scheduler = m_easyMockUtils.createMock(Scheduler.class);
        m_collectd.setScheduler(m_scheduler);

        // Expect one task to be scheduled
        m_scheduler.schedule(eq(0L), isA(ReadyRunnable.class));

        // Expect the scheduler to be started and stopped during Collectd
        // start() and stop()
        m_scheduler.start();
        m_scheduler.stop();

        m_easyMockUtils.replayAll();

        // Initialize Collectd
        m_collectd.afterPropertiesSet();

        // Start and stop collectd
        m_collectd.start();
        m_collectd.stop();

        m_easyMockUtils.verifyAll();
    }

    /**
     * Test override of read community string and max repetitions in Collectd configuration parameters
     */
    public void testOverrides() {
    	Map<String, Object> map = new HashMap<String, Object>();
    	map.put("max-repetitions", "11");
    	map.put("read-community", "notPublic");
		ServiceParameters params = new ServiceParameters(map);
		
		int reps = params.getSnmpMaxRepetitions(6);
		assertEquals("Overriding max repetitions failed.", 11, reps);
		params = new ServiceParameters(map);
		map.remove("max-repetitions");
		map.put("maxRepetitions", "11");
		assertEquals("Overriding max repetitions failed.", 11, reps);
		
		String s = params.getSnmpReadCommunity("public");
		assertEquals("Overriding read community failed.", "notPublic", s);
		map.remove("read-community");
		map.put("readCommunity", "notPublic");
		params = new ServiceParameters(map);
		s = params.getSnmpReadCommunity("public");
		assertEquals("Overriding read community failed.", "notPublic", s);
    }

    /**
     * 
     * @throws Exception
     */
    public void testNoMatchingSpecs() throws Exception {

        setupCollector("SNMP", false);
        expect(m_ipIfDao.findByServiceType("SNMP")).andReturn(new ArrayList<OnmsIpInterface>(0));
        setupTransactionManager();

        m_easyMockUtils.replayAll();

        m_collectd.afterPropertiesSet();

        m_collectd.start();

        m_scheduler.next();

        assertEquals(0, m_scheduler.getEntryCount());

        m_collectd.stop();
        
        m_easyMockUtils.verifyAll();
    }

    public void testOneMatchingSpec() throws Exception {
        OnmsIpInterface iface = getInterface();

        setupCollector("SNMP", true);
        setupInterface(iface);
        setupTransactionManager();
  
        expect(m_collectdConfig.getPackages()).andReturn(Collections.singletonList(getCollectionPackageThatMatchesSNMP()));
        expect(m_collectdConfigFactory.interfaceInPackage(iface, getCollectionPackageThatMatchesSNMP())).andReturn(true);
        
        m_easyMockUtils.replayAll();

        assertEquals("scheduler entry count", 0, m_scheduler.getEntryCount());

        m_collectd.afterPropertiesSet();

        m_collectd.start();
        
        m_scheduler.next();

        assertEquals("scheduler entry count", 1, m_scheduler.getEntryCount());

        m_scheduler.next();

        m_collectd.stop();

        m_easyMockUtils.verifyAll();
    }

    @SuppressWarnings("unchecked")
    private static <K> Collection<K> isACollection(Class<K> innerClass) {
        return isA(Collection.class);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> isAMap(Class<K> keyClass, Class<V> valueClass) {
        return isA(Map.class);
    }

    /**
     * Add a dummy transaction manager that has mock calls to commit() and rollback()
     */
    private void setupTransactionManager() {
        PlatformTransactionManager m_transactionManager = m_easyMockUtils.createMock(PlatformTransactionManager.class);
        TransactionTemplate transactionTemplate = new TransactionTemplate(m_transactionManager);
        m_collectd.setTransactionTemplate(transactionTemplate);
        
        expect(m_transactionManager.getTransaction(isA(TransactionDefinition.class))).andReturn(new SimpleTransactionStatus()).anyTimes();
        m_transactionManager.rollback(isA(TransactionStatus.class));
        expectLastCall().anyTimes();
        m_transactionManager.commit(isA(TransactionStatus.class));
        expectLastCall().anyTimes();
    }

    private void setupInterface(OnmsIpInterface iface) {
        expect(m_ipIfDao.findByServiceType("SNMP")).andReturn(Collections.singletonList(iface));
        expect(m_ipIfDao.load(iface.getId())).andReturn(iface).atLeastOnce();
    }

    private void setupCollector(String svcName, boolean successfulInit) throws CollectionInitializationException {
        ServiceCollector svcCollector = m_easyMockUtils.createMock(ServiceCollector.class);
        if (successfulInit) {
            svcCollector.initialize(isA(CollectionAgent.class), isAMap(String.class, Object.class));
        }
        svcCollector.initialize(Collections.<String,String>emptyMap());
        MockServiceCollector.setDelegate(svcCollector);

        // Tell the config to use the MockServiceCollector for the specified service
        Collector collector = new Collector();
        collector.setService(svcName);
        collector.setClassName(MockServiceCollector.class.getName());

        m_collectdConfigFactory = m_easyMockUtils.createMock(CollectdConfigFactory.class);
        m_collectdConfig = m_easyMockUtils.createMock(CollectdConfiguration.class);
        expect(m_collectdConfigFactory.getCollectdConfig()).andReturn(m_collectdConfig).anyTimes();
        expect(m_collectdConfig.getCollectors()).andReturn(Collections.singletonList(collector)).anyTimes();
        expect(m_collectdConfig.getThreads()).andReturn(1).anyTimes();

        m_collectd.setCollectdConfigFactory(m_collectdConfigFactory);
    }

    
    public static class MockServiceCollector implements ServiceCollector {
        private static ServiceCollector s_delegate;

        public MockServiceCollector() {
            
        }
        
        public static void setDelegate(ServiceCollector delegate) {
            s_delegate = delegate;
        }
        
        @Override
        public CollectionSet collect(CollectionAgent agent, EventProxy eproxy, Map<String, Object> parameters) throws CollectionException {
            return new AbstractCollectionSet() {
                private Date m_timestamp = new Date();
                @Override
                public int getStatus() {
                    return ServiceCollector.COLLECTION_SUCCEEDED;
                }

                @Override
                public void visit(CollectionSetVisitor visitor) {
                    visitor.visitCollectionSet(this);   
                    visitor.completeCollectionSet(this);
                }

                @Override
                public Date getCollectionTimestamp() {
                    return m_timestamp;
                }
            };
        }

        @Override
        public void initialize(Map<String, String> parameters) throws CollectionInitializationException {
            s_delegate.initialize(parameters);
        }

        @Override
        public void initialize(CollectionAgent agent, Map<String, Object> parameters) throws CollectionInitializationException {
            s_delegate.initialize(agent, parameters);
        }

        @Override
        public void release() {
            s_delegate.release();
        }

        @Override
        public void release(CollectionAgent agent) {
            s_delegate.release(agent);
        }

        @Override
        public RrdRepository getRrdRepository(String collectionName) {
            RrdRepository repo = new RrdRepository();
            repo.setRrdBaseDir(new File("/usr/local/opennms/share/rrd/snmp/"));
            repo.setRraList(Collections.singletonList("RRA:AVERAGE:0.5:1:8928"));
            repo.setStep(300);
            repo.setHeartBeat(2 * 300);
            return repo;
        }
    }

}
