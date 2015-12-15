/*
 * This file is part of Gradoop.
 *
 * Gradoop is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Gradoop is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Gradoop. If not, see <http://www.gnu.org/licenses/>.
 */

package org.gradoop.model.impl.operators.summarization;

import org.apache.flink.api.common.operators.Order;
import org.apache.flink.api.java.DataSet;
import org.gradoop.model.api.EPGMEdge;
import org.gradoop.model.api.EPGMGraphHead;
import org.gradoop.model.api.EPGMVertex;
import org.gradoop.model.impl.LogicalGraph;
import org.gradoop.model.impl.operators.summarization.functions.VertexToGroupVertexMapper;
import org.gradoop.model.impl.operators.summarization.functions.VertexGroupItemToRepresentativeFilter;
import org.gradoop.model.impl.operators.summarization.functions.VertexGroupItemToSummarizedVertexFilter;
import org.gradoop.model.impl.operators.summarization.functions.VertexGroupItemToSummarizedVertexMapper;
import org.gradoop.model.impl.operators.summarization.functions.VertexGroupItemToVertexWithRepresentativeMapper;
import org.gradoop.model.impl.operators.summarization.functions.VertexGroupReducer;
import org.gradoop.model.impl.operators.summarization.tuples.VertexForGrouping;
import org.gradoop.model.impl.operators.summarization.tuples.VertexGroupItem;
import org.gradoop.model.impl.operators.summarization.tuples.VertexWithRepresentative;

/**
 * Summarization implementation that requires sorting of vertex groups to chose
 * a group representative.
 *
 * Algorithmic idea:
 *
 * 1) group vertices by label / property / both
 * 2) sort groups by vertex identifier ascending
 * 3a) reduce group 1
 * - build summarized vertex from each group (group count, group label/prop)
 * 3b) reduce group 2
 * - build {@link VertexWithRepresentative} tuples for each group element
 * 4) join output from 3b) with edges
 * - replace source / target vertex id with vertex group representative
 * 5) group edges on source/target vertex and possibly edge label / property
 * 6) build summarized edges
 *
 * @param <G> EPGM graph head type
 * @param <V> EPGM vertex type
 * @param <E> EPGM edge type
 */
public class SummarizationGroupSort<
  G extends EPGMGraphHead,
  V extends EPGMVertex,
  E extends EPGMEdge>
  extends Summarization<G, V, E> {

  /**
   * Creates summarization.
   *
   * @param vertexGroupingKey property key to summarize vertices
   * @param edgeGroupingKey   property key to summarize edges
   * @param useVertexLabels   summarize on vertex label true/false
   * @param useEdgeLabels     summarize on edge label true/false
   */
  public SummarizationGroupSort(String vertexGroupingKey,
    String edgeGroupingKey, boolean useVertexLabels, boolean useEdgeLabels) {
    super(vertexGroupingKey, edgeGroupingKey, useVertexLabels, useEdgeLabels);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected LogicalGraph<G, V, E> summarizeInternal(
    LogicalGraph<G, V, E> graph) {

    DataSet<VertexForGrouping> verticesForGrouping = graph.getVertices()
      // map vertices to a compact representation
      .map(new VertexToGroupVertexMapper<V>(
        getVertexGroupingKey(), useVertexLabels()));

    // sort group by vertex id ascending
    DataSet<VertexGroupItem> sortedGroupedVertices =
      // group vertices by label / property / both
      groupVertices(verticesForGrouping)
        // sort group by vertex id ascending
        .sortGroup(0, Order.ASCENDING)
        // create vertex group items
        .reduceGroup(new VertexGroupReducer());

    DataSet<V> summarizedVertices = sortedGroupedVertices
      // filter group representative tuples
      .filter(new VertexGroupItemToSummarizedVertexFilter())
        // build summarized vertex
      .map(new VertexGroupItemToSummarizedVertexMapper<>(
        config.getVertexFactory(), getVertexGroupingKey(), useVertexLabels()));

    DataSet<VertexWithRepresentative> vertexToRepresentativeMap =
      sortedGroupedVertices
        // filter group element tuples
        .filter(new VertexGroupItemToRepresentativeFilter())
          // build vertex to group representative tuple
        .map(new VertexGroupItemToVertexWithRepresentativeMapper());

    // build summarized edges
    DataSet<E> summarizedEdges = buildSummarizedEdges(
      graph, vertexToRepresentativeMap);

    return LogicalGraph.fromDataSets(
      summarizedVertices, summarizedEdges, graph.getConfig());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getName() {
    return SummarizationGroupSort.class.getName();
  }
}
