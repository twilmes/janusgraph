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


package org.janusgraph.diskstorage.cache;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.janusgraph.StorageSetup;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.janusgraph.core.JanusGraphVertex;
import org.janusgraph.core.PropertyKey;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.inmemory.InMemoryStoreManager;
import org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration;
import org.janusgraph.graphdb.database.StandardJanusGraph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;

import static org.janusgraph.StorageSetup.getHomeDir;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.DROP_ON_CLEAR;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_BACKEND;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_DIRECTORY;
import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.buildGraphConfiguration;

public class InMemoryTest {

    public static void main(String... args) throws InterruptedException, IOException, BackendException {
        JanusGraph graph = JanusGraphFactory.open(StorageSetup.getInMemoryConfiguration()
            .set(GraphDatabaseConfiguration.USE_MULTIQUERY, true).set(GraphDatabaseConfiguration.DB_CACHE, false));
//            .set(GraphDatabaseConfiguration.STORAGE_BATCH, true));
//        JanusGraph graph = JanusGraphFactory.open(buildGraphConfiguration()
//            .set(STORAGE_BACKEND,"berkeleyje")
//            .set(STORAGE_DIRECTORY, getHomeDir("berkeleyje"))
//            .set(DROP_ON_CLEAR, false)
//            .set(GraphDatabaseConfiguration.USE_MULTIQUERY, true).set(GraphDatabaseConfiguration.DB_CACHE, false));

//        JanusGraphManagement mgmt = graph.openManagement();
//        mgmt.makeVertexLabel("Person").make();
//        mgmt.makeEdgeLabel("knows").make();
//        PropertyKey qid = mgmt.makePropertyKey("qid").dataType(String.class).make();
//        mgmt.buildIndex("byQid", Vertex.class).addKey(qid).buildCompositeIndex();
//        mgmt.commit();
//
        GraphTraversalSource g = graph.traversal();
//
//        long start = System.currentTimeMillis();
//
////        final int vertexCount = 5_000_000;
////        final int edgeCount = 30_000_000;
//        final int vertexCount = 10_000;
//        final int edgeCount = 60_000;
//        final int loaderCount = 5;
//
//        List<Thread> threads = new ArrayList<>();
//        for (int i = 0; i < loaderCount; i++) {
//            final Thread inserter = new Thread(new VertexWriter(graph, vertexCount, i + "_"));
//            inserter.start();
//            threads.add(inserter);
//        }
//
//        for (Thread t : threads) t.join();
//        threads = new ArrayList<>();
//
//        for (int i = 0; i < loaderCount; i++) {
//            final Thread inserter = new Thread(new EdgeWriter(graph, vertexCount, edgeCount, i + "_"));
//            inserter.start();
//            threads.add(inserter);
//        }
//
//        for (Thread t : threads) t.join();
//
//        long end = System.currentTimeMillis();
//        long delta = end - start;
//
//        System.out.println("Load time: " + delta);
//        double ps = (double) ((vertexCount * loaderCount) + (edgeCount * loaderCount)) / (delta / 1000.0);
//        System.out.println("Elements / second: " + ps);


//        g.V().has("qid", within("0_123", "0_321", "0_222")).out().next();
//
//        System.out.println("Pausing...");
//        System.in.read();
//
//        g.withComputer().V().count().next();
//
//        long total = 0l;
//        Random rand = new Random();
//        int sampleCount = 100;
//        for (int i = 0; i < sampleCount; i++) {
//            final String randPrefix = rand.nextInt(5) + "_";
//            final int randOffset = rand.nextInt(vertexCount);
//            String startQid = randPrefix + randOffset;
//            long startTime = System.currentTimeMillis();
//            Long count = g.V().has("qid", startQid)
//                .both().simplePath()
//                .both().simplePath()
//                .both().simplePath()
//                .both().simplePath()
//                .both().simplePath()
//                .both().simplePath()
//                .count().next();
//            long runDelta = System.currentTimeMillis() - startTime;
//            System.out.println("Runtime: " + runDelta);
//            System.out.println("Count: " + count);
//            total += runDelta;
//        }
//        double avg = total / (double) sampleCount;
//        System.out.println("Avg: " + avg);
//
//        System.out.println("Before: " + g.V().count().next());

//        start = System.currentTimeMillis();
//        System.out.println("Dumping db...");
        InMemoryStoreManager imsm = (InMemoryStoreManager) ((StandardJanusGraph) graph).getBackend().getStoreManager();
//        File path = new File("/home/twilmes/qomplx-db");
//        imsm.makeSnapshot(path, ForkJoinPool.commonPool());
//        delta = System.currentTimeMillis() - start;

//        System.out.println("Saved DB in " + delta);
        imsm.clearStorage();
        imsm.restoreFromSnapshot(new File("/home/twilmes/qomplx-db"), false, ForkJoinPool.commonPool());

        ((StandardJanusGraph) graph).getBackend().initialize(((StandardJanusGraph) graph).getConfiguration().getConfiguration());

        graph.tx().rollback();

        System.out.println("Verify index: " + graph.traversal().V().has("qid", "f0_COMP00001@Domain1").next());


        System.out.println("Gathering samples...");
        List<Object> sampleIds = g.V().sample(100).id().toList();
        System.out.println("Samples gathered...");

                long total = 0l;
        Random rand = new Random();
        int sampleCount = 100;
        for (int i = 0; i < sampleCount; i++) {
//            final String randPrefix = rand.nextInt(5) + "_";
//            final int randOffset = rand.nextInt(vertexCount);
//            String startQid = randPrefix + randOffset;
            long startTime = System.currentTimeMillis();
            Long count = g.withComputer().V(sampleIds.get(i)).both().both().both().both().count().next();
//                .both().simplePath()
//                .both().simplePath()
//                .both().simplePath()
//                .both().simplePath()
//                .both().simplePath()
//                .both().simplePath()
//                .count().next();
            long runDelta = System.currentTimeMillis() - startTime;
            System.out.println("Runtime: " + runDelta);
            System.out.println("Count: " + count);
            total += runDelta;
        }
        double avg = total / (double) sampleCount;
        System.out.println("Avg: " + avg);


//        System.out.println("After: " + graph.traversal().V().count().next());

        graph.close();
    }

    static class VertexWriter implements Runnable {

        private final String prefix;
        private final JanusGraph graph;
        private final int vertexCount;

        public VertexWriter(JanusGraph graph, int vertexCount, String prefix) {
            this.graph = graph;
            this.prefix = prefix;
            this.vertexCount = vertexCount;
        }

        @Override
        public void run() {
            for (int i = 0; i < vertexCount; i++) {
                graph.addVertex(T.label, "Person", "qid", prefix + i);
                if (i % 100 == 0) {
                    graph.tx().commit();
                }
                if (i % 1_000_000 == 0) {
                    System.out.println("vertex thread: " + this.toString() + ": " + i);
                }
            }
            graph.tx().commit();
        }
    }

    static class EdgeWriter implements Runnable {

        private final String prefix;
        private final JanusGraph graph;
        private final int vertexCount;
        private final int edgeCount;

        public EdgeWriter(JanusGraph graph, int vertexCount, int edgeCount, String prefix) {
            this.graph = graph;
            this.prefix = prefix;
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
        }

        @Override
        public void run() {
            final Random rand = new Random();
            for (int i = 0; i < edgeCount; i++) {
                final String randPrefix = rand.nextInt(5) + "_";
                final int randOffset = rand.nextInt(vertexCount);

                final String endRandPrefix = rand.nextInt(5) + "_";
                final int endRandOffset = rand.nextInt(vertexCount);

                JanusGraphVertex endVertex = graph.query().has("qid", endRandPrefix + endRandOffset).vertices().iterator().next();

                graph.query().has("qid", randPrefix + randOffset).vertices().iterator().next().addEdge("knows", endVertex);
                if (i % 100 == 0) {
                    graph.tx().commit();
                }
                if (i % 1_000_000 == 0) {
                    System.out.println("edge thread: " + this.toString() + ": " + i);
                }
            }
            graph.tx().commit();
        }
    }

}
