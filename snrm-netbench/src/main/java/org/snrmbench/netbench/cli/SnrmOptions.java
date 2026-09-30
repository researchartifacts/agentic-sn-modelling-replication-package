package org.snrmbench.netbench.cli;

import org.snrmbench.netbench.verify.L3;
import org.snrmbench.netbench.verify.Report;
import org.snrmbench.netbench.verify.SnrmClient;
import picocli.CommandLine.Option;

import java.io.PrintWriter;

/** The --snrm-* options shared by generate, verify and verify-batch. */
final class SnrmOptions {

    @Option(names = "--snrm-url", paramLabel = "<base>",
            description = "A running SNRM's server root (e.g. http://localhost:8080): also validate with SNRM.")
    String url;

    @Option(names = "--snrm-token", paramLabel = "<jwt>", description = "SNRM bearer token (instead of a login).")
    String token;

    @Option(names = "--snrm-user", paramLabel = "<u>", description = "SNRM user name to log in with.")
    String user;

    @Option(names = "--snrm-pass", paramLabel = "<p>", description = "SNRM password to log in with.")
    String pass;

    @Option(names = "--snrm-project-id", paramLabel = "<id>",
            description = "The SNRM project to validate and import into (default: none for validation; a "
                    + "scratch project for --l3).")
    String projectId;

    boolean requested() {
        return url != null;
    }

    /** A one-line reason the options are unusable, or null. */
    String problem() {
        if (url == null) {
            return token != null || user != null || pass != null || projectId != null
                    ? "--snrm-token, --snrm-user, --snrm-pass and --snrm-project-id require --snrm-url" : null;
        }
        if (token != null && (user != null || pass != null)) {
            return "give --snrm-token or --snrm-user with --snrm-pass, not both";
        }
        if (token == null && (user == null || pass == null)) {
            return "--snrm-url needs --snrm-token, or --snrm-user and --snrm-pass";
        }
        try {
            SnrmClient.checkUrl(url);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /** {@link #problem}, plus {@code --l3} requiring {@code --snrm-url} and {@code --keep} requiring {@code --l3}. */
    String problem(boolean l3, boolean keep) {
        String p = problem();
        return p != null ? p : l3 && url == null ? "--l3 requires --snrm-url"
                : keep && !l3 ? "--keep requires --l3" : null;
    }

    SnrmClient client() {
        return new SnrmClient(url, token, user, pass);
    }

    /** Says on stderr what the report records quietly: SNRM answering the validation with an error, L3 ending in an error, or L3 failing to delete what it created. */
    static void warn(Report report, boolean keep, PrintWriter err, String where) {
        if (report.snrm() != null && report.snrm().report().has("error")) {
            err.println(where + ": SNRM validation of the candidate failed: " + report.snrm().report().get("error")
                    .asText());
        }
        L3 l3 = report.l3();
        if (l3 != null && l3.status().equals(L3.ERROR)) {
            err.println(where + ": l3: " + l3.message());
        }
        if (l3 != null && !keep && !l3.cleanedUp()) {
            err.println(where + ": l3 could not delete everything it created on SNRM");
        }
    }
}
