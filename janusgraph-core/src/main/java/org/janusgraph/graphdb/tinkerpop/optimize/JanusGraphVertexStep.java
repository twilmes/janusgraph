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

package org.janusgraph.graphdb.tinkerpop.optimize;

import com.google.common.collect.Iterables;
import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.EmptyStep;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import org.janusgraph.core.BaseVertexQuery;
import org.janusgraph.core.JanusGraphElement;
import org.janusgraph.core.JanusGraphMultiVertexQuery;
import org.janusgraph.core.JanusGraphVertex;
import org.janusgraph.core.JanusGraphVertexQuery;
import org.janusgraph.graphdb.query.BaseQuery;
import org.janusgraph.graphdb.query.Query;
import org.janusgraph.graphdb.query.JanusGraphPredicate;
import org.janusgraph.graphdb.query.profile.QueryProfiler;
import org.janusgraph.graphdb.query.vertex.BasicVertexCentricQueryBuilder;
import org.janusgraph.graphdb.tinkerpop.profile.TP3ProfileWrapper;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.Profiling;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.VertexStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.process.traversal.util.FastNoSuchElementException;
import org.apache.tinkerpop.gremlin.process.traversal.util.MutableMetrics;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class JanusGraphVertexStep<E extends Element> extends VertexStep<E> implements HasStepFolder<Vertex, E>, Profiling, MultiQueriable<Vertex,E> {

    public JanusGraphVertexStep(VertexStep<E> originalStep) {
        super(originalStep.getTraversal(), originalStep.getReturnClass(), originalStep.getDirection(), originalStep.getEdgeLabels());
        originalStep.getLabels().forEach(this::addLabel);
        this.hasContainers = new ArrayList<>();
        this.limit = Query.NO_LIMIT;
    }

    private boolean initialized = false;
    private boolean useMultiQuery = false;
    private boolean firstNested = false;
    private Map<JanusGraphVertex, Iterable<? extends JanusGraphElement>> multiQueryResults = null;
    private QueryProfiler queryProfiler = QueryProfiler.NO_OP;

    @Override
    public void setUseMultiQuery(boolean useMultiQuery, boolean firstNested) {
        this.useMultiQuery = useMultiQuery;
        this.firstNested = firstNested;
    }

    public <Q extends BaseVertexQuery> Q makeQuery(Q query) {
        query.labels(getEdgeLabels());
        query.direction(getDirection());
        for (HasContainer condition : hasContainers) {
            query.has(condition.getKey(), JanusGraphPredicate.Converter.convert(condition.getBiPredicate()), condition.getValue());
        }
        for (OrderEntry order : orders) query.orderBy(order.key, order.order);
        if (limit != BaseQuery.NO_LIMIT) query.limit(limit);
        ((BasicVertexCentricQueryBuilder) query).profiler(queryProfiler);
        return query;
    }


    List<Traverser.Admin<Vertex>> vertices = new LinkedList<>();


    Map<Vertex, Integer> startsCountMap = new HashMap<>();

    private void incrStartsCountMap(Vertex elem) {
        Integer elemCount = startsCountMap.getOrDefault(elem, 0);
        elemCount++;
        startsCountMap.put(elem, elemCount);
    }

//    private void decrStartsCountMap(Vertex elem) {
//        Integer elemCount = startsCountMap.get(elem);
//        if (elemCount != null) {
//            startsCountMap.put(elem, elemCount-1);
//        }
//    }

    private int getStartsCount(Vertex elem) {
        return startsCountMap.getOrDefault(elem, 0);
    }

    @SuppressWarnings("deprecation")
    private void regularInit() {
//        assert !initialized;
        initialized = true;
        if (useMultiQuery) {
            if (!starts.hasNext()) {
                //initialized = false;
                throw FastNoSuchElementException.instance();
            }
            JanusGraphMultiVertexQuery mquery = JanusGraphTraversalUtil.getTx(traversal).multiQuery();
            List<Traverser.Admin<Vertex>> vertices = new ArrayList<>();
            starts.forEachRemaining(v -> {
                vertices.add(v);
                mquery.addVertex(v.get());
            });
            starts.add(vertices.iterator());
            assert vertices.size() > 0;
            makeQuery(mquery);

            multiQueryResults = (Vertex.class.isAssignableFrom(getReturnClass())) ? mquery.vertices() : mquery.edges();
            triggerCount = getTriggerCount(multiQueryResults);
//            initialized = true;
        }
    }

    private boolean nestedInitialized = false;
    boolean isRepeatable = false;

    int getTriggerCount(Map<JanusGraphVertex, Iterable<? extends JanusGraphElement>> mqResults) {
        int empties = (int) multiQueryResults.values().stream().filter(r -> !r.iterator().hasNext()).count();
        return empties + (int)multiQueryResults.values().stream().map(vals -> IteratorUtils.count(vals)).mapToLong(i -> i).sum();
    }

    private void initializeNested() {
        if (triggerCount == 0 && !starts.hasNext()) {
            System.out.println("MQ Nested throwing FNSE " + this.toString());
            throw FastNoSuchElementException.instance();
        }

        if (firstNested && !initialized && !nestedInitialized) {
            starts.forEachRemaining(v -> {
                vertices.add(v);
                System.out.println("MQ Adding nested start " + v.get());
            });

            boolean more = this.getTraversal().getParent().asStep().getPreviousStep().hasNext();
            System.out.println("NEXT? " + more);

            if (!more) {
                JanusGraphMultiVertexQuery mquery = JanusGraphTraversalUtil.getTx(traversal).multiQuery();
                mquery.addAllVertices(vertices.stream().map(Traverser::get).collect(Collectors.toList()));
                starts.add(vertices.iterator());
                makeQuery(mquery);
                vertices.forEach(v -> incrStartsCountMap(v.get()));

                multiQueryResults = (Vertex.class.isAssignableFrom(getReturnClass())) ? mquery.vertices() : mquery.edges();
                initialized = true;
                nestedInitialized = true;
                System.out.println("MQ Nested MQ results: " + multiQueryResults);
                triggerCount = getTriggerCount(multiQueryResults);
            }
        } else {
            if (triggerCount == 0) { //multiQueryResults.isEmpty()) {
                JanusGraphMultiVertexQuery mquery = JanusGraphTraversalUtil.getTx(traversal).multiQuery();
                List<Traverser.Admin<Vertex>> vertices = new ArrayList<>();
                starts.forEachRemaining(v -> {
                    vertices.add(v);
                    mquery.addVertex(v.get());
                    System.out.println("MQ Adding regular start " + v.get());
                });
                starts.add(vertices.iterator());
                assert vertices.size() > 0;
                makeQuery(mquery);
                multiQueryResults = (Vertex.class.isAssignableFrom(getReturnClass())) ? mquery.vertices() : mquery.edges();
                System.out.println("MQ non-nested MQ results: " + multiQueryResults);
                triggerCount = getTriggerCount(multiQueryResults);
            }
        }
    }

    int triggerCount = 0;

    @Override
    protected Traverser.Admin<E> processNextStart() {
//        if (!initialized) initialize();
//        if (initialized && !starts.hasNext()) initialize();
        if (useMultiQuery) {
            // check if initialized should be flipped back to false
//            if (firstNested) {
            if (isRepeatable) {
                initializeNested();
            } else {
//                if (!initialized && triggerCount == 0)
                if (!initialized)
                    regularInit();
            }
//            if (firstNested) {
//                if (useMultiQuery && !initialized) initialize();
//                if (useMultiQuery && initialized && multiQueryResults.isEmpty()) initialize();
//            } else {
//                if (!initialized && (multiQueryResults == null || multiQueryResults.isEmpty())) regularInit();
//            }
        }

        if (triggerCount > 0)
            triggerCount--;

        Traverser.Admin<E> result = null;
        try {
            result = super.processNextStart();
        } catch (FastNoSuchElementException e) {
            System.out.println("Next: Exception");
            //if (isRepeatable)
                //initialized = false;
            throw e;
        }



        //System.out.println("Next: " + result);

        return result;
    }

    @Override
    protected Iterator<E> flatMap(final Traverser.Admin<Vertex> traverser) {
        if (useMultiQuery) {
            System.out.println(this.toString() + "\t Trigger Count: "+ triggerCount);
            assert multiQueryResults != null;


            if (multiQueryResults.get(traverser.get()) == null) {
                //triggerCount--;
                return Collections.emptyIterator();
                //throw FastNoSuchElementException.instance();
            }

            return (Iterator<E>) multiQueryResults.get(traverser.get()).iterator();
//            Iterator<E> it = (Iterator<E>) multiQueryResults.get(traverser.get()).iterator();
//            //decrStartsCountMap(traverser.get());
//
//            if (getStartsCount(traverser.get()) == 0)
//                System.out.println("Removed: " + multiQueryResults.remove(traverser.get()));
//            //triggerCount--;
//            //triggerCount = (int)multiQueryResults.values().stream().map(vals -> IteratorUtils.count(vals)).mapToLong(i -> i).sum();
//            if (multiQueryResults.values().stream().filter(r -> !r.iterator().hasNext()).count() == multiQueryResults.size()) {
//                System.out.println("!!!Emptying");
//                //triggerCount = (int)multiQueryResults.values().stream().filter(r -> !r.iterator().hasNext()).count();
//                multiQueryResults = Collections.emptyMap();
//            }
//            return it;
        } else {
            JanusGraphVertexQuery query = makeQuery((JanusGraphTraversalUtil.getJanusGraphVertex(traverser)).query());
            return (Vertex.class.isAssignableFrom(getReturnClass())) ? query.vertices().iterator() : query.edges().iterator();
        }
    }

    @Override
    public void reset() {
        super.reset();
        this.initialized = false;
    }

    @Override
    public JanusGraphVertexStep<E> clone() {
        final JanusGraphVertexStep<E> clone = (JanusGraphVertexStep<E>) super.clone();
        clone.initialized = false;
        return clone;
    }

    /*
    ===== HOLDER =====
     */

    private final List<HasContainer> hasContainers;
    private int limit = BaseQuery.NO_LIMIT;
    private List<OrderEntry> orders = new ArrayList<>();


    @Override
    public void addAll(Iterable<HasContainer> has) {
        HasStepFolder.splitAndP(hasContainers, has);
    }

    @Override
    public void orderBy(String key, Order order) {
        orders.add(new OrderEntry(key, order));
    }

    @Override
    public void setLimit(int limit) {
        this.limit = limit;
    }

    @Override
    public int getLimit() {
        return this.limit;
    }

    @Override
    public String toString() {
        return this.hasContainers.isEmpty() ? super.toString() : StringFactory.stepString(this, this.hasContainers);
    }

    @Override
    public void setMetrics(MutableMetrics metrics) {
        queryProfiler = new TP3ProfileWrapper(metrics);
    }
}
