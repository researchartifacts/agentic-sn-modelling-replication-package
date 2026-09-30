package org.snrmbench.netbench.describe;

import java.util.List;

/** A document section: an optional {@code ##} heading and its paragraphs of statements. */
public record Section(String heading, List<List<Statement>> paragraphs) {

    public Section {
        paragraphs = paragraphs.stream().map(List::copyOf).toList();
    }
}
