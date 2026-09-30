package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.TierRole;

import java.util.List;

/** The structure of a network before names and attributes. */
public record Skeleton(List<TierRole> tierRoles, List<List<Integer>> tiers, List<SkNode> nodes, List<SkLink> links,
                       int lateralAdded, String lateralNote) {

    /** A node: canonical id, tier index and position within the tier. */
    public record SkNode(int id, int tier, int position, TierRole role) {
        public NodeType type() {
            return role.type();
        }
    }

    /** A link between canonical ids; {@code lateral} marks the extra same-tier DC links of {@code --lateral-links}. */
    public record SkLink(int source, int target, boolean lateral) {
    }

    public Skeleton {
        tierRoles = List.copyOf(tierRoles);
        tiers = tiers.stream().map(List::copyOf).toList();
        nodes = List.copyOf(nodes);
        links = List.copyOf(links);
    }

    public SkNode node(int id) {
        return nodes.get(id);
    }

    /** Links whose two ends have the same SNRM type: each one is a LATERAL_LINK warning in B. */
    public int sameTypeLinkCount() {
        return (int) links.stream().filter(l -> node(l.source()).type() == node(l.target()).type()).count();
    }
}
