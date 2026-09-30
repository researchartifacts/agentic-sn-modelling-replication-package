package org.snrmbench.netbench.model;

import java.util.List;
import java.util.Optional;

/** An immutable supply network: the in-memory form of both B and the extractor's output. */
public record Network(TimeBase timeBase, List<Tier> tiers, List<Product> products, List<Node> nodes,
                      List<Link> links, List<NodeProduct> nodeProducts) {

    public Network {
        tiers = List.copyOf(tiers);
        products = List.copyOf(products);
        nodes = List.copyOf(nodes);
        links = List.copyOf(links);
        nodeProducts = List.copyOf(nodeProducts);
    }

    public Optional<Node> node(String name) {
        return nodes.stream().filter(n -> n.name().equals(name)).findFirst();
    }
}
