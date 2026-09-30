package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeProduct;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.Quantity;
import org.jgrapht.alg.flow.EdmondsKarpMFImpl;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.Map;

/** The feasibility assertion: on the node-split graph. */
public final class Feasibility {

    /** The maximum flow and the total per-period demand it must reach. */
    public record Result(double maxFlow, double totalDemand) {
        public boolean feasible() {
            return maxFlow >= totalDemand * (1 - 1e-9);
        }
    }

    private static final double INFINITE = 1e15;

    private Feasibility() {
    }

    public static Result check(Network net) {
        BigDecimal periodSeconds = net.timeBase().periodSeconds();
        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> g =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        int source = 0;
        int sink = 1;
        g.addVertex(source);
        g.addVertex(sink);
        Map<String, Integer> in = new LinkedHashMap<>();
        Map<String, Double> customerDemand = new LinkedHashMap<>();
        for (NodeProduct np : net.nodeProducts()) {
            if (np.demand() != null && np.demand().value() != null) {
                customerDemand.merge(np.node(), perPeriod(np.demand(), periodSeconds), Double::sum);
            }
        }
        double total = 0;
        int next = 2;
        for (Node n : net.nodes()) {
            int vin = next++;
            int vout = next++;
            in.put(n.name(), vin);
            g.addVertex(vin);
            g.addVertex(vout);
            edge(g, vin, vout, capacity(n.capacity(), periodSeconds));
            if (n.tierIndex() == 0) {
                edge(g, source, vin, INFINITE);
            }
            if (n.type() == NodeType.CUSTOMER) {
                double d = customerDemand.getOrDefault(n.name(), 0.0);
                total += d;
                edge(g, vout, sink, d);
            }
        }
        for (Link l : net.links()) {
            edge(g, in.get(l.source()) + 1, in.get(l.target()), capacity(l.capacity(), periodSeconds));
        }
        double flow = new EdmondsKarpMFImpl<>(g).getMaximumFlowValue(source, sink);
        return new Result(flow, total);
    }

    private static void edge(SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> g, int a, int b, double w) {
        DefaultWeightedEdge e = g.addEdge(a, b);
        g.setEdgeWeight(e, w);
    }

    /** A blank capacity is uncapped. */
    private static double capacity(Quantity q, BigDecimal periodSeconds) {
        return q == null || q.value() == null ? INFINITE : perPeriod(q, periodSeconds);
    }

    private static double perPeriod(Quantity q, BigDecimal periodSeconds) {
        return q.value().multiply(periodSeconds)
                .divide(BigDecimal.valueOf(q.unit().seconds()), MathContext.DECIMAL128).doubleValue();
    }
}
