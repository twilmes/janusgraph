package org.janusgraph.blueprints.process;

import com.palantir.docker.compose.DockerComposeRule;
import org.apache.tinkerpop.gremlin.GraphProviderClass;
import org.apache.tinkerpop.gremlin.process.ProcessStandardSuite;
import org.janusgraph.FoundationDBStorageSetup;
import org.janusgraph.blueprints.FoundationDBMultiQueryGraphProvider;
import org.janusgraph.core.JanusGraph;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

/**
 * @author Ted Wilmes (twilmes@gmail.com)
 */
@RunWith(ProcessStandardSuite.class)
@GraphProviderClass(provider = FoundationDBMultiQueryGraphProvider.class, graph = JanusGraph.class)
public class FoundationDBJanusGraphMultiQueryProcessTest {

    @ClassRule
    public static DockerComposeRule docker = FoundationDBStorageSetup.startFoundationDBDocker();
}