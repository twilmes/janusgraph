// Copyright 2017 JanusGraph Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.janusgraph.graphdb.berkeleyje;

import com.google.common.base.Preconditions;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import org.janusgraph.BerkeleyStorageSetup;
import org.janusgraph.core.JanusGraphException;
import org.janusgraph.core.JanusGraphFactory;
import org.janusgraph.diskstorage.Backend;
import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.berkeleyje.BerkeleyJEStoreManager;
import org.janusgraph.diskstorage.berkeleyje.BerkeleyJEStoreManager.IsolationLevel;
import org.janusgraph.diskstorage.configuration.ConfigElement;
import org.janusgraph.diskstorage.configuration.ConfigOption;
import org.janusgraph.diskstorage.configuration.ModifiableConfiguration;
import org.janusgraph.diskstorage.configuration.WriteConfiguration;
import org.janusgraph.diskstorage.keycolumnvalue.keyvalue.OrderedKeyValueStoreManagerAdapter;
import org.janusgraph.graphdb.JanusGraphTest;
import org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.ALLOW_SETTING_VERTEX_ID;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.ALLOW_CUSTOM_VERTEX_ID_TYPES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BerkeleyGraphTest extends JanusGraphTest {

    private static final Logger log =
            LoggerFactory.getLogger(BerkeleyGraphTest.class);

    public EnvironmentConfig getCurrentEnvironmentConfig() {
        BerkeleyJEStoreManager storeManager = (BerkeleyJEStoreManager) ((OrderedKeyValueStoreManagerAdapter) graph.getBackend().getStoreManager()).getManager();
        Environment environment = storeManager.getEnvironment();
        return environment.getConfig();
    }

    @Override
    public WriteConfiguration getConfiguration() {
        ModifiableConfiguration modifiableConfiguration = BerkeleyStorageSetup.getBerkeleyJEConfiguration();
        String methodName = testInfo.getTestMethod().toString();
        if (methodName.equals("testConsistencyEnforcement")) {
            IsolationLevel iso = IsolationLevel.SERIALIZABLE;
            log.debug("Forcing isolation level {} for test method {}", iso, methodName);
            modifiableConfiguration.set(BerkeleyJEStoreManager.ISOLATION_LEVEL, iso.toString());
        } else {
            IsolationLevel iso = null;
            if (modifiableConfiguration.has(BerkeleyJEStoreManager.ISOLATION_LEVEL)) {
                iso = ConfigOption.getEnumValue(modifiableConfiguration.get(BerkeleyJEStoreManager.ISOLATION_LEVEL),IsolationLevel.class);
            }
            log.debug("Using isolation level {} (null means adapter default) for test method {}", iso, methodName);
        }
        return modifiableConfiguration.getConfiguration();
    }

    @Override
    @Test
    public void testClearStorage() throws Exception {
        tearDown();
        config.set(ConfigElement.getPath(GraphDatabaseConfiguration.DROP_ON_CLEAR), true);
        Backend backend = getBackend(config, false);
        assertTrue(backend.getStoreManager().exists(), "graph should exist before clearing storage");
        clearGraph(config);
        backend.close();
        backend = getBackend(config, false);
        assertFalse(backend.getStoreManager().exists(), "graph should not exist after clearing storage");
        backend.close();
    }

    @Test
    public void testVertexCentricQuerySmall() {
        testVertexCentricQuery(1450 /*noVertices*/);
    }

    @Override
    @Test
    @Disabled("#4153: Tests fails with NPE for some reason")
    public void testConsistencyEnforcement() {
        // Check that getConfiguration() explicitly set serializable isolation
        // This could be enforced with a JUnit assertion instead of a Precondition,
        // but a failure here indicates a problem in the test itself rather than the
        // system-under-test, so a Precondition seems more appropriate
        IsolationLevel effective = ConfigOption.getEnumValue(config.get(ConfigElement.getPath(BerkeleyJEStoreManager.ISOLATION_LEVEL), String.class),IsolationLevel.class);
        Preconditions.checkState(IsolationLevel.SERIALIZABLE.equals(effective));
        super.testConsistencyEnforcement();
    }

    @Override
    @Test
    @Disabled("BerkeleyJE support only hour discrete TTL, we try speed up internal clock but tests so flaky. See BerkeleyStorageSetup")
    public void testEdgeTTLTiming() {
    }

    @Override
    @Test
    @Disabled
    public void testEdgeTTLWithTransactions() {
    }

    @Override
    @Test
    @Disabled
    public void testUnsettingTTL() {
    }

    @Override
    @Test
    @Disabled
    public void testVertexTTLWithCompositeIndex() {
    }

    @Override
    @Test
    @Disabled("TODO: Figure out why this is failing in BerkeleyDB!!")
    public void testConcurrentConsistencyEnforcement() {
    }

    @Disabled("Unable to run on GitHub Actions.")
    @Test
    public void testIDBlockAllocationTimeout() throws BackendException {
        config.set("ids.authority.wait-time", Duration.of(0L, ChronoUnit.NANOS));
        config.set("ids.renew-timeout", Duration.of(1L, ChronoUnit.MILLIS));
        close();
        JanusGraphFactory.drop(graph);
        open(config);
        assertThrows(JanusGraphException.class, () -> graph.addVertex());

        assertTrue(graph.isOpen());

        close(); // must be able to close cleanly

        // Must be able to reopen
        open(config);

        assertEquals(0L, (long)graph.traversal().V().count().next());
    }

    @Override
    public void clopenForStaleIndex(){
        // We set LOCK_MODE to READ_UNCOMMITTED here to mitigate an issue with deadlock problem described in
        // https://github.com/JanusGraph/janusgraph/issues/1623
        clopen(option(BerkeleyJEStoreManager.LOCK_MODE), LockMode.READ_UNCOMMITTED.toString());
    }

    @Test
    public void testCannotUseCustomStringId() {
        JanusGraphException ex = assertThrows(JanusGraphException.class,
            () -> clopen(option(ALLOW_SETTING_VERTEX_ID), true, option(ALLOW_CUSTOM_VERTEX_ID_TYPES), true));
        assertEquals("allow-custom-vid-types is not supported for OrderedKeyValueStore", ex.getMessage());
    }

    @Test
    public void testExposedConfigurations() throws BackendException {
        clopen(option(BerkeleyJEStoreManager.EXT_LOCK_TIMEOUT), "4321 ms");
        assertEquals(4321, getCurrentEnvironmentConfig().getLockTimeout(TimeUnit.MILLISECONDS));
        close();
        WriteConfiguration configuration = getConfiguration();
        clearGraph(configuration);
        configuration.set(BerkeleyJEStoreManager.BERKELEY_EXTRAS_NS.toStringWithoutRoot()+"."+EnvironmentConfig.LOCK_TIMEOUT, "12345 ms");
        open(configuration);
        assertEquals(12345, getCurrentEnvironmentConfig().getLockTimeout(TimeUnit.MILLISECONDS));
        close();
        clearGraph(configuration);
        configuration.set(BerkeleyJEStoreManager.BERKELEY_EXTRAS_NS.toStringWithoutRoot()+"."+EnvironmentConfig.ENV_IS_TRANSACTIONAL, "true");
        open(configuration);
        assertTrue(getCurrentEnvironmentConfig().getTransactional());
        close();
        clearGraph(configuration);
    }

}
