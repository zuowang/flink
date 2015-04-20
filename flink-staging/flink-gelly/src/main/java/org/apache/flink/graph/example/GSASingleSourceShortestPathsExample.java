/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.graph.example;

import org.apache.flink.api.common.ProgramDescription;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.graph.Edge;
import org.apache.flink.graph.Graph;
import org.apache.flink.graph.Vertex;
import org.apache.flink.graph.example.utils.SingleSourceShortestPathsData;
import org.apache.flink.graph.gsa.ApplyFunction;
import org.apache.flink.graph.gsa.GatherFunction;
import org.apache.flink.graph.gsa.SumFunction;
import org.apache.flink.graph.gsa.Neighbor;
import org.apache.flink.graph.utils.Tuple3ToEdgeMap;

/**
 * This is an implementation of the Single Source Shortest Paths algorithm, using a gather-sum-apply iteration
 */
public class GSASingleSourceShortestPathsExample implements ProgramDescription {

	// --------------------------------------------------------------------------------------------
	//  Program
	// --------------------------------------------------------------------------------------------

	public static void main(String[] args) throws Exception {

		if(!parseParameters(args)) {
			return;
		}

		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<Edge<Long, Double>> edges = getEdgeDataSet(env);

		Graph<Long, Double, Double> graph = Graph.fromDataSet(edges, new InitVertices(srcVertexId), env);

		// Execute the GSA iteration
		Graph<Long, Double, Double> result = graph
				.runGatherSumApplyIteration(new CalculateDistances(), new ChooseMinDistance(),
						new UpdateDistance(), maxIterations);

		// Extract the vertices as the result
		DataSet<Vertex<Long, Double>> singleSourceShortestPaths = result.getVertices();

		// emit result
		if(fileOutput) {
			singleSourceShortestPaths.writeAsCsv(outputPath, "\n", " ");
		} else {
			singleSourceShortestPaths.print();
		}

		env.execute("GSA Single Source Shortest Paths Example");
	}

	@SuppressWarnings("serial")
	private static final class InitVertices implements MapFunction<Long, Double>{

		private long srcId;

		public InitVertices(long srcId) {
			this.srcId = srcId;
		}

		public Double map(Long id) {
			if (id.equals(srcId)) {
				return 0.0;
			}
			else {
				return Double.POSITIVE_INFINITY;
			}
		}
	}

	// --------------------------------------------------------------------------------------------
	//  Single Source Shortest Path UDFs
	// --------------------------------------------------------------------------------------------

	@SuppressWarnings("serial")
	private static final class CalculateDistances extends GatherFunction<Double, Double, Double> {

		public Double gather(Neighbor<Double, Double> richEdge) {
			return richEdge.getSrcVertexValue() + richEdge.getEdgeValue();
		}
	};

	@SuppressWarnings("serial")
	private static final class ChooseMinDistance extends SumFunction<Double, Double, Double> {

		public Double sum(Double newValue, Double currentValue) {
			return Math.min(newValue, currentValue);
		}
	};

	@SuppressWarnings("serial")
	private static final class UpdateDistance extends ApplyFunction<Double, Double, Double> {

		public void apply(Double newDistance, Double oldDistance) {
			if (newDistance < oldDistance) {
				setResult(newDistance);
			}
		}
	};

	// --------------------------------------------------------------------------------------------
	//  Util methods
	// --------------------------------------------------------------------------------------------

	private static boolean fileOutput = false;

	private static Long srcVertexId = 1l;

	private static String edgesInputPath = null;

	private static String outputPath = null;

	private static int maxIterations = 5;

	private static boolean parseParameters(String[] args) {

		if (args.length > 0) {
			if(args.length != 4) {
				System.err.println("Usage: GSASingleSourceShortestPaths <source vertex id>" +
						" <input edges path> <output path> <num iterations>");
				return false;
			}

			fileOutput = true;
			srcVertexId = Long.parseLong(args[0]);
			edgesInputPath = args[1];
			outputPath = args[2];
			maxIterations = Integer.parseInt(args[3]);
		} else {
				System.out.println("Executing GSASingle Source Shortest Paths example "
						+ "with default parameters and built-in default data.");
				System.out.println("  Provide parameters to read input data from files.");
				System.out.println("  See the documentation for the correct format of input files.");
				System.out.println("Usage: GSASingleSourceShortestPaths <source vertex id>" +
						" <input edges path> <output path> <num iterations>");
		}
		return true;
	}

	private static DataSet<Edge<Long, Double>> getEdgeDataSet(ExecutionEnvironment env) {
		if (fileOutput) {
			return env.readCsvFile(edgesInputPath)
					.fieldDelimiter("\t")
					.lineDelimiter("\n")
					.types(Long.class, Long.class, Double.class)
					.map(new Tuple3ToEdgeMap<Long, Double>());
		} else {
			return SingleSourceShortestPathsData.getDefaultEdgeDataSet(env);
		}
	}

	@Override
	public String getDescription() {
		return "GSA Single Source Shortest Paths";
	}
}