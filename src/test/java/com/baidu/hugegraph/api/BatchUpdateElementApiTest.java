/*
 * Copyright 2017 HugeGraph Authors
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.baidu.hugegraph.api;

import static com.baidu.hugegraph.api.graph.structure.UpdateStrategy.INTERSECTION;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.baidu.hugegraph.api.graph.structure.BatchEdgeRequest;
import com.baidu.hugegraph.api.graph.structure.BatchVertexRequest;
import com.baidu.hugegraph.api.graph.structure.UpdateStrategy;
import com.baidu.hugegraph.driver.SchemaManager;
import com.baidu.hugegraph.exception.ServerException;
import com.baidu.hugegraph.structure.GraphElement;
import com.baidu.hugegraph.structure.graph.Edge;
import com.baidu.hugegraph.structure.graph.Vertex;
import com.baidu.hugegraph.structure.schema.VertexLabel;
import com.baidu.hugegraph.testutil.Assert;
import com.baidu.hugegraph.testutil.Whitebox;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class BatchUpdateElementApiTest extends BaseApiTest {

    private static final int BATCH_SIZE = 5;

    @BeforeClass
    public static void prepareSchema() {
        SchemaManager schema = schema();
        schema.propertyKey("name").asText().ifNotExist().create();
        schema.propertyKey("price").asInt().ifNotExist().create();
        schema.propertyKey("date").asDate().ifNotExist().create();
        schema.propertyKey("set").asText().valueSet().ifNotExist().create();
        schema.propertyKey("list").asText().valueList().ifNotExist().create();

        schema.vertexLabel("object")
              .properties("name", "price", "date", "set", "list")
              .primaryKeys("name")
              .nullableKeys("price", "date", "set", "list")
              .ifNotExist()
              .create();

        schema.edgeLabel("updates")
              .sourceLabel("object")
              .targetLabel("object")
              .properties("name", "price", "date", "set", "list")
              .nullableKeys("name", "price", "date", "set", "list")
              .ifNotExist()
              .create();
    }

    @Override
    @After
    public void teardown() {
        vertexAPI.list(-1).results().forEach(v -> vertexAPI.delete(v.id()));
        edgeAPI.list(-1).results().forEach(e -> edgeAPI.delete(e.id()));
    }

    /* Vertex Test */
    @Test
    public void testVertexBatchUpdateStrategySum() {
        BatchVertexRequest req = batchVertexRequest("price", 1, -1,
                                                    UpdateStrategy.SUM);
        List<Vertex> vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "price", 0);

        req = batchVertexRequest("price", 2, 3, UpdateStrategy.SUM);
        vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "price", 5);
    }

    @Test
    public void testVertexBatchUpdateStrategyBigger() {
        // TODO: Add date comparison after fixing the date serialization bug
        BatchVertexRequest req = batchVertexRequest("price", -3, 1,
                                                    UpdateStrategy.BIGGER);

        List<Vertex> vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "price", 1);

        req = batchVertexRequest("price", 7, 3, UpdateStrategy.BIGGER);
        vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "price", 7);
    }

    @Test
    public void testVertexBatchUpdateStrategySmaller() {
        BatchVertexRequest req = batchVertexRequest("price", -3, 1,
                                                    UpdateStrategy.SMALLER);

        List<Vertex> vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "price", -3);

        req = batchVertexRequest("price", 7, 3, UpdateStrategy.SMALLER);
        vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "price", 3);
    }

    @Test
    public void testVertexBatchUpdateStrategyUnion() {
        BatchVertexRequest req = batchVertexRequest("set", "old", "new",
                                                    UpdateStrategy.UNION);

        List<Vertex> vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "set", "new", "old");

        req = batchVertexRequest("set", "old", "old", UpdateStrategy.UNION);
        vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "set", "old");
    }

    @Test
    public void testVertexBatchUpdateStrategyIntersection() {
        BatchVertexRequest req = batchVertexRequest("set", "old", "new",
                                                    INTERSECTION);

        List<Vertex> vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "set");

        req = batchVertexRequest("set", "old", "old", INTERSECTION);
        vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "set", "old");
    }

    @Test
    public void testVertexBatchUpdateStrategyAppend() {
        BatchVertexRequest req = batchVertexRequest("list", "old", "old",
                                                    UpdateStrategy.APPEND);

        List<Vertex> vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "list", "old", "old");

        req = batchVertexRequest("list", "old", "new", UpdateStrategy.APPEND);
        vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "list", "old", "new");
    }

    @Test
    public void testVertexBatchUpdateStrategyEliminate() {
        BatchVertexRequest req = batchVertexRequest("list", "old", "old",
                                                    UpdateStrategy.ELIMINATE);

        List<Vertex> vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "list");

        req = batchVertexRequest("list", "old", "x", UpdateStrategy.ELIMINATE);
        vertices = vertexAPI.update(req);
        assertBatchResponse(vertices, "list", "old");
    }

    @Test
    public void testVertexEmptyUpdateStrategy() {
        BatchVertexRequest req = batchVertexRequest("set", "old", "old",
                                                    UpdateStrategy.UNION);

        Assert.assertThrows(ServerException.class, () -> {
            List<Vertex> vertices = Whitebox.getInternalState(req, "vertices");
            vertices.set(1, null);
            Whitebox.setInternalState(req, "vertices", vertices);
            vertexAPI.update(req);
        });

        Assert.assertThrows(ServerException.class, () -> {
            Whitebox.setInternalState(req, "vertices", null);
            vertexAPI.update(req);
        });

        Assert.assertThrows(ServerException.class, () -> {
            Whitebox.setInternalState(req, "vertices", ImmutableList.of());
            vertexAPI.update(req);
        });

        Assert.assertThrows(ServerException.class, () -> {
            Whitebox.setInternalState(req, "createIfNotExist", false);
            vertexAPI.update(req);
        });

        Assert.assertThrows(ServerException.class, () -> {
            Whitebox.setInternalState(req, "updateStrategies", null);
            vertexAPI.update(req);
        });

        Assert.assertThrows(ServerException.class, () -> {
            Whitebox.setInternalState(req, "updateStrategies",
                                      ImmutableMap.of());
            vertexAPI.update(req);
        });
    }

    /* Edge Test */
    @Test
    public void testEdgeBatchUpdateStrategySum() {
        BatchEdgeRequest req = batchEdgeRequest("price", -1, 1,
                                                UpdateStrategy.SUM);
        List<Edge> edges = edgeAPI.update(req);
        assertBatchResponse(edges, "price", 0);

        req = batchEdgeRequest("price", 2, 3, UpdateStrategy.SUM);
        edges = edgeAPI.update(req);
        assertBatchResponse(edges, "price", 5);
    }

    @Test
    public void testEdgeBatchUpdateStrategyBigger() {
        // TODO: Add date comparison after fixing the date serialization bug
        BatchEdgeRequest req = batchEdgeRequest("price", -3, 1,
                                                UpdateStrategy.BIGGER);
        List<Edge> edges = edgeAPI.update(req);
        assertBatchResponse(edges, "price", 1);

        req = batchEdgeRequest("price", 7, 3, UpdateStrategy.BIGGER);
        edges = edgeAPI.update(req);
        assertBatchResponse(edges, "price", 7);
    }

    @Test
    public void testEdgeBatchUpdateStrategySmaller() {
        BatchEdgeRequest req = batchEdgeRequest("price", -3, 1,
                                                UpdateStrategy.SMALLER);
        List<Edge> edges = edgeAPI.update(req);
        assertBatchResponse(edges, "price", -3);

        req = batchEdgeRequest("price", 7, 3, UpdateStrategy.SMALLER);
        edges = edgeAPI.update(req);
        assertBatchResponse(edges, "price", 3);
    }

    @Test
    public void testEdgeBatchUpdateStrategyUnion() {
        BatchEdgeRequest req = batchEdgeRequest("set", "old", "new",
                                                UpdateStrategy.UNION);
        List<Edge> edges = edgeAPI.update(req);
        assertBatchResponse(edges, "set", "new", "old");

        req = batchEdgeRequest("set", "old", "old", UpdateStrategy.UNION);
        edges = edgeAPI.update(req);
        assertBatchResponse(edges, "set", "old");
    }

    @Test
    public void testEdgeBatchUpdateStrategyIntersection() {
        BatchEdgeRequest req = batchEdgeRequest("set", "old", "new",
                                                INTERSECTION);
        List<Edge> edges = edgeAPI.update(req);
        assertBatchResponse(edges, "set");

        req = batchEdgeRequest("set", "old", "old", INTERSECTION);
        edges = edgeAPI.update(req);
        assertBatchResponse(edges, "set", "old");
    }

    @Test
    public void testEdgeBatchUpdateStrategyAppend() {
        BatchEdgeRequest req = batchEdgeRequest("list", "old", "old",
                                                UpdateStrategy.APPEND);
        List<Edge> edges = edgeAPI.update(req);
        assertBatchResponse(edges, "list", "old", "old");

        req = batchEdgeRequest("list", "old", "new", UpdateStrategy.APPEND);
        edges = edgeAPI.update(req);
        assertBatchResponse(edges, "list", "old", "new");
    }

    @Test
    public void testEdgeBatchUpdateStrategyEliminate() {
        BatchEdgeRequest req = batchEdgeRequest("list", "old", "old",
                                                UpdateStrategy.ELIMINATE);
        List<Edge> edges = edgeAPI.update(req);
        assertBatchResponse(edges, "list");

        req = batchEdgeRequest("list", "old", "new", UpdateStrategy.ELIMINATE);
        edges = edgeAPI.update(req);
        assertBatchResponse(edges, "list", "old");
    }

    @Test
    public void testEdgeEmptyUpdateStrategy() {
        BatchEdgeRequest req = batchEdgeRequest("list", "old", "old",
                                                UpdateStrategy.ELIMINATE);

        Assert.assertThrows(ServerException.class, () -> {
            List<Edge> edges = Whitebox.getInternalState(req, "edges");
            edges.set(1, null);
            Whitebox.setInternalState(req, "edges", edges);
            edgeAPI.update(req);
        });

        Assert.assertThrows(ServerException.class, () -> {
            Whitebox.setInternalState(req, "edges", null);
            edgeAPI.update(req);
        });

        Assert.assertThrows(ServerException.class, () -> {
            Whitebox.setInternalState(req, "edges", ImmutableList.of());
            edgeAPI.update(req);
        });

        Assert.assertThrows(ServerException.class, () -> {
            Whitebox.setInternalState(req, "createIfNotExist", false);
            edgeAPI.update(req);
        });

        Assert.assertThrows(ServerException.class, () -> {
            Whitebox.setInternalState(req, "updateStrategies", null);
            edgeAPI.update(req);
        });

        Assert.assertThrows(ServerException.class, () -> {
            Whitebox.setInternalState(req, "updateStrategies",
                                      ImmutableMap.of());
            edgeAPI.update(req);
        });
    }

    private BatchVertexRequest batchVertexRequest(String key, Object oldData,
                                                  Object newData,
                                                  UpdateStrategy strategy) {
        // Init old & new vertices
        graph().addVertices(this.createNVertexBatch("object", oldData,
                                                         BATCH_SIZE));
        List<Vertex> vertices = this.createNVertexBatch("object", newData,
                                                        BATCH_SIZE);

        Map<String, UpdateStrategy> strategies = ImmutableMap.of(key, strategy);
        BatchVertexRequest req;
        req = new BatchVertexRequest.Builder().vertices(vertices)
                                              .updatingStrategies(strategies)
                                              .createIfNotExist(true)
                                              .build();
        return req;
    }

    private BatchEdgeRequest batchEdgeRequest(String key, Object oldData,
                                              Object newData,
                                              UpdateStrategy strategy) {
        // Init old vertices & edges
        graph().addVertices(this.createNVertexBatch("object", oldData,
                                                    BATCH_SIZE * 2));
        graph().addEdges(this.createNEdgesBatch("object", "updates",
                                                oldData, BATCH_SIZE));
        List<Edge> edges = this.createNEdgesBatch("object", "updates",
                                                  newData, BATCH_SIZE);

        Map<String, UpdateStrategy> strategies = ImmutableMap.of(key, strategy);
        BatchEdgeRequest req;
        req = new BatchEdgeRequest.Builder().edges(edges)
                                            .updatingStrategies(strategies)
                                            .checkVertex(false)
                                            .createIfNotExist(true)
                                            .build();
        return req;
    }

    private List<Vertex> createNVertexBatch(String vertexLabel,
                                            Object symbol, int num) {
        List<Vertex> vertices = new ArrayList<>(num);
        for (int i = 1; i <= num; i++) {
            Vertex vertex = new Vertex(vertexLabel);
            vertex.property("name", String.valueOf(i));
            if (symbol instanceof Number) {
                vertex.property("price", (int) symbol * i);
            }
            vertex.property("date", new Date(System.currentTimeMillis() + i));
            vertex.property("set", ImmutableSet.of(String.valueOf(symbol) + i));
            vertex.property("list",
                            ImmutableList.of(String.valueOf(symbol) + i));
            vertices.add(vertex);
        }
        return vertices;
    }

    private List<Edge> createNEdgesBatch(String vertexLabel, String edgeLabel,
                                         Object symbol, int num) {
        VertexLabel vLabel = schema().getVertexLabel(vertexLabel);

        List<Edge> edges = new ArrayList<>(num);
        for (int i = 1; i <= num; i++) {
            Edge edge = new Edge(edgeLabel);
            edge.sourceLabel(vertexLabel);
            edge.targetLabel(vertexLabel);
            edge.sourceId(vLabel.id() + ":" + i);
            edge.targetId(vLabel.id() + ":" + i * 2);
            edge.property("name", String.valueOf(i));
            if (symbol instanceof Number) {
                edge.property("price", (int) symbol * i);
            }
            edge.property("date", new Date(System.currentTimeMillis() + i));
            edge.property("set", ImmutableSet.of(String.valueOf(symbol) + i));
            edge.property("list", ImmutableList.of(String.valueOf(symbol) + i));
            edges.add(edge);
        }
        return edges;
    }

    private static void assertBatchResponse(List<? extends GraphElement> list,
                                            String property, int result) {
        Assert.assertEquals(5, list.size());
        list.forEach(element -> {
            String index = String.valueOf(element.property("name"));
            Object value = element.property(property);
            Assert.assertTrue(value instanceof Number);
            Assert.assertEquals(result * Integer.valueOf(index), value);
        });
    }

    private static void assertBatchResponse(List<? extends GraphElement> list,
                                            String property, String... data) {
        Assert.assertEquals(5, list.size());
        list.forEach(element -> {
            String index = String.valueOf(element.property("name"));
            Object value = element.property(property);
            Assert.assertTrue(value instanceof List);
            if (data.length == 0) {
                Assert.assertTrue(((List<?>) value).isEmpty());
            } else if (data.length == 1) {
                Assert.assertEquals(ImmutableList.of(data[0] + index), value);
            } else {
                Assert.assertEquals(ImmutableList.of(data[0] + index,
                                                     data[1] + index), value);
            }
        });
    }
}
