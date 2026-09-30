package org.snrmbench.netbench.cli;

import org.snrmbench.netbench.schema.SchemaDoc;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.concurrent.Callable;

/** netbench schema: prints the canonical schema that the LLM instruction file embeds verbatim. */
@Command(name = "schema", mixinStandardHelpOptions = true,
        description = "Print the canonical CSV schema: files, columns, enums, unit tokens, unit rules, conventions.")
public final class SchemaCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--format", defaultValue = "markdown", paramLabel = "markdown|json",
            description = "Output format (default: ${DEFAULT-VALUE}).")
    String format;

    @Override
    public Integer call() {
        String text = switch (format.trim().toLowerCase(Locale.ROOT)) {
            case "markdown" -> SchemaDoc.markdown();
            case "json" -> SchemaDoc.json();
            default -> null;
        };
        if (text == null) {
            spec.commandLine().getErr().println("schema: --format must be markdown or json (got \"" + format + "\")");
            return 2;
        }
        PrintWriter out = spec.commandLine().getOut();
        out.print(text);
        out.flush();
        return 0;
    }
}
