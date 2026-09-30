package org.snrmbench.netbench.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** netbench: generator of SNRM networks with descriptions, and the checker of extracted ones. */
@Command(name = "netbench", mixinStandardHelpOptions = true, version = "netbench 0.1.0",
        description = "Generates SNRM supply networks with natural-language descriptions, and scores LLM-extracted "
                + "networks against them.",
        subcommands = {GenerateCommand.class, VerifyCommand.class, ExtractCommand.class, ValidateCommand.class,
                BatchCommand.class, VerifyBatchCommand.class, SchemaCommand.class})
public final class Main implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Runs a command line and returns its exit code (used by tests). */
    public static int run(String... args) {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        return new CommandLine(new Main()).setOut(out).execute(args);
    }

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
