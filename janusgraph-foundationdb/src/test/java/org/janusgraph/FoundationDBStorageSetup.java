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

package org.janusgraph;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import org.janusgraph.diskstorage.configuration.ModifiableConfiguration;
import org.janusgraph.diskstorage.configuration.WriteConfiguration;

import static org.janusgraph.diskstorage.foundationdb.FoundationDBConfigOptions.CLUSTER_FILE_PATH;
import static org.janusgraph.diskstorage.foundationdb.FoundationDBConfigOptions.DIRECTORY;
import static org.janusgraph.diskstorage.foundationdb.FoundationDBConfigOptions.SERIALIZABLE;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.*;

/**
 * @author Ted Wilmes (twilmes@gmail.com)
 */
public class FoundationDBStorageSetup extends StorageSetup {

    public static ModifiableConfiguration getFoundationDBConfiguration() {
        return buildGraphConfiguration()
                .set(STORAGE_BACKEND,"foundationdb")
                .set(DIRECTORY, "janusgraph-test-fdb")
                .set(DROP_ON_CLEAR, false)
                .set(CLUSTER_FILE_PATH, "src/test/resources/etc/fdb.cluster")
                .set(SERIALIZABLE, true)
                .set(STORAGE_BATCH, true);
    }

    public static WriteConfiguration getFoundationDBGraphConfiguration() {
        return getFoundationDBConfiguration().getConfiguration();
    }

    public static DockerComposeRule startFoundationDBDocker() {
        return DockerComposeRule.builder()
            .file("src/test/resources/docker-compose.yml")
            .waitingForService("db", HealthChecks.toHaveAllPortsOpen())
            .build();
    }
}