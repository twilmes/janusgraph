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

package org.janusgraph.blueprints.process;

import com.palantir.docker.compose.DockerComposeRule;
import org.apache.tinkerpop.gremlin.process.ProcessStandardSuite;
import org.janusgraph.FoundationDBStorageSetup;
import org.janusgraph.blueprints.FoundationDBGraphProvider;
import org.janusgraph.core.JanusGraph;
import org.apache.tinkerpop.gremlin.GraphProviderClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

/**
 * @author Ted Wilmes (twilmes@gmail.com)
 */
@RunWith(ProcessStandardSuite.class)
@GraphProviderClass(provider = FoundationDBGraphProvider.class, graph = JanusGraph.class)
public class FoundationDBJanusGraphProcessTest {

    @ClassRule
    public static DockerComposeRule docker = FoundationDBStorageSetup.startFoundationDBDocker();
}
