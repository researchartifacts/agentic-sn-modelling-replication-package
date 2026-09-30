package org.snrmbench.netbench.cli;

import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.validate.SnrmRules;
import org.snrmbench.netbench.validate.ValidationResult;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/** {@code netbench validate}: SNRM's import rules over any directory, as JSON. */
@Command(name = "validate", mixinStandardHelpOptions = true,
        description = "Check a directory of CSV files against SNRM's import rules; exit 0 without errors, 1 with.")
public final class ValidateCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = "--network", required = true, description = "Directory of canonical CSV files.")
    Path network;

    @Override
    public Integer call() {
        if (!Files.isDirectory(network)) {
            System.err.println("netbench validate: " + network + " is not a directory");
            return 2;
        }
        ValidationResult r;
        try {
            r = SnrmRules.validate(LenientCsvReader.readDirectory(network));
        } catch (UncheckedIOException e) {
            System.err.println("netbench validate: cannot read " + network + ": " + e.getCause().getMessage());
            return 2;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("network", network.toString());
        out.put("valid", r.valid());
        out.put("error_count", r.errorCount());
        out.put("warning_count", r.warningCount());
        out.put("findings", r.findings());
        spec.commandLine().getOut().print(Json.pretty(out));
        spec.commandLine().getOut().flush();
        return r.valid() ? 0 : 1;
    }
}
