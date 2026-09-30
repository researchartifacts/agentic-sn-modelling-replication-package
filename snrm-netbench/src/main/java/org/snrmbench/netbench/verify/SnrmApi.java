package org.snrmbench.netbench.verify;

/** Every SNRM endpoint path and every request and response field name this tool assumes, in one place, so that correcting them against snrm-core is a one-file change. */
public final class SnrmApi {

    private SnrmApi() {
    }

    // ---- authentication: the SNRM design document "POST /auth/login -> Obtain JWT" --------

    public static final String LOGIN_PATH = "/api/v1/auth/login";
    public static final String LOGIN_USERNAME = "username";
    public static final String LOGIN_PASSWORD = "password";
    /** The JWT in the login response; sent back as {@code Authorization: Bearer <token>}. */
    public static final String LOGIN_TOKEN = "token";

    // ---- projects: the SNRM design document "GET/POST /projects, GET/PUT/DELETE /projects/{id}" ---------

    public static final String PROJECTS_PATH = "/api/v1/projects";
    /** {@code %s} = project id. */
    public static final String PROJECT_PATH = "/api/v1/projects/%s";
    public static final String PROJECT_NAME = "name";
    public static final String PROJECT_ID = "id";

    // ---- import: (multipart field names and the validation report JSON) -----------------

    public static final String IMPORT_PATH = "/api/v1/networks/import";
    /** One part per CSV file, each carrying its file name. */
    public static final String IMPORT_PART_FILES = "files";
    public static final String IMPORT_PART_NAME = "name";
    /** The project selector. */
    public static final String IMPORT_PART_PROJECT = "projectId";
    public static final String IMPORT_PART_VALIDATE_ONLY = "validateOnly";
    /** Report: true when the file set has no errors. */
    public static final String REPORT_VALID = "valid";
    public static final String REPORT_ERRORS = "errors";
    public static final String REPORT_WARNINGS = "warnings";
    /** Each item of {@code errors[]} / {@code warnings[]} names its validation code here. */
    public static final String REPORT_ITEM_CODE = "code";
    /** Report of a committed import: the id of the network it created. */
    public static final String REPORT_NETWORK_ID = "networkId";

    // ---- networks: the SNRM design document ------------------------------------------------------------

    /** {@code %s} = network id. */
    public static final String NETWORK_PATH = "/api/v1/networks/%s";
    /** {@code %s} = network id; the node list, used to name NODE_CRITICALITY rows. */
    public static final String NETWORK_NODES_PATH = "/api/v1/networks/%s/nodes";
    /** The node list is either a top-level JSON array or an array under this field. */
    public static final String NODES = "nodes";
    public static final String NODE_ID = "id";
    public static final String NODE_NAME = "name";

    // ---- metrics: the SNRM design document,, ------------------------------------------

    /** {@code %s} = network id. */
    public static final String TOPOLOGICAL_PATH = "/api/v1/networks/%s/metrics/topological";
    /** A metric list is either a top-level JSON array or an array under this field (the topological suite and a run's results alike). */
    public static final String METRICS = "metrics";
    public static final String METRIC_CODE = "code";
    public static final String METRIC_VALUE = "value";
    /** The element a node-scoped metric row (NODE_CRITICALITY) belongs to: a node id. */
    public static final String METRIC_SCOPE_ID = "scopeId";

    // ---- simulations and jobs: the SNRM design document,; params are ---------------------------------

    public static final String SIMULATIONS_PATH = "/api/v1/simulations";
    /** {@code %s} = run id. */
    public static final String SIMULATION_PATH = "/api/v1/simulations/%s";
    /** {@code %s} = run id. */
    public static final String SIMULATION_RESULTS_PATH = "/api/v1/simulations/%s/results";
    public static final String SIMULATION_NETWORK_ID = "networkId";
    public static final String SIMULATION_PARAMS = "params";
    public static final String SIMULATION_JOB_ID = "jobId";
    public static final String SIMULATION_RUN_ID = "runId";
    // params; safetyStockPriority is deliberately never sent, so SNRM's default applies
    public static final String PARAM_SEED = "seed";
    public static final String PARAM_REPLICATIONS = "replications";
    public static final String PARAM_DEMAND_NOISE_CV = "demandNoiseCv";
    public static final String PARAM_TIMING_JITTER_PERIODS = "timingJitterPeriods";
    public static final String PARAM_RECORD_ELEMENT_TIMESERIES = "recordElementTimeseries";

    /** {@code %s} = job id. */
    public static final String JOB_PATH = "/api/v1/jobs/%s";
    public static final String JOB_STATUS = "status";
    public static final String JOB_MESSAGE = "message";
    public static final String JOB_DONE = "DONE";
    public static final String JOB_FAILED = "FAILED";
    public static final String JOB_CANCELLED = "CANCELLED";
}
