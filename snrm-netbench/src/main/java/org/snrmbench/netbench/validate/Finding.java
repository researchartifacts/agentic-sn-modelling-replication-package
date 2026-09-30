package org.snrmbench.netbench.validate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** One validation finding. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "severity", "file", "line", "column", "element", "message"})
public record Finding(Code code, Severity severity, String file, Integer line, String column, String element,
                      String message) {

    public static Finding row(Code code, String file, Integer line, String column, String message) {
        return new Finding(code, code.severity(), file, line, column, null, message);
    }

    public static Finding element(Code code, String file, Integer line, String element, String message) {
        return new Finding(code, code.severity(), file, line, null, element, message);
    }

    @JsonIgnore // not a JSON property: Jackson would otherwise write it as "error"
    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
