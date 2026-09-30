package org.snrmbench.netbench.model;

import java.util.List;

public record Tier(int index, TierRole role, NodeType type, List<String> nodeNames) {

    public Tier {
        nodeNames = List.copyOf(nodeNames);
    }
}
