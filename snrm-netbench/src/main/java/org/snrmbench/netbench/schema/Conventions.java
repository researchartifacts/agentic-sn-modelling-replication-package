package org.snrmbench.netbench.schema;

import org.snrmbench.netbench.model.NodeType;

import java.util.Optional;

/** Convention fields: values fixed by the SNRM samples' conventions, never stated in a description and never counted as facts. */
public final class Conventions {

    /** The value a convention field holds. */
    public enum Value {
        /** A blank cell (a customer's capacity: a 0 would cap it at nothing). */
        BLANK,
        /** The literal {@code 0}; a blank cell is accepted as well. */
        ZERO
    }

    private Conventions() {
    }

    /** The convention for a field of an entity whose node (the node itself, or the node of a node-product row) has the given type; empty when the field is a fact. */
    public static Optional<Value> of(Field field, NodeType nodeType) {
        if (nodeType == null) {
            return Optional.empty();
        }
        boolean customer = nodeType == NodeType.CUSTOMER;
        return switch (field) {
            case NODE_CAPACITY -> customer ? Optional.of(Value.BLANK) : Optional.empty();
            case NODE_PROCESSING_TIME, NODE_FIXED_COST, NODE_VAR_COST, NODE_FAILURE_PROB,
                 NP_INITIAL_INVENTORY, NP_SAFETY_STOCK, NP_HOLDING_COST ->
                    customer ? Optional.of(Value.ZERO) : Optional.empty();
            case NP_DEMAND -> customer ? Optional.empty() : Optional.of(Value.ZERO);
            default -> Optional.empty();
        };
    }

    public static boolean isConvention(Field field, NodeType nodeType) {
        return of(field, nodeType).isPresent();
    }
}
