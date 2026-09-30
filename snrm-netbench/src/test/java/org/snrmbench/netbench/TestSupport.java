package org.snrmbench.netbench;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Shared helpers for tests. */
public final class TestSupport {

    public static final String[] SAMPLES = {
            "minimal-6-node", "mixed-units-10-node", "multi-echelon-50-node", "simulation-verification-3-node"};

    private TestSupport() {
    }

    /** A bundled SNRM sample directory under src/test/resources/snrm-samples. */
    public static Path sample(String name) {
        try {
            return Paths.get(TestSupport.class.getResource("/snrm-samples/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
