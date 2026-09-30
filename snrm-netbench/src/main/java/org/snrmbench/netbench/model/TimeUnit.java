package org.snrmbench.netbench.model;

import java.util.Locale;

/** SNRM's time-unit set; MONTH and YEAR are fixed-length approximations. */
public enum TimeUnit {
    SECOND(1L, "second", "seconds"),
    MINUTE(60L, "minute", "minutes"),
    HOUR(3_600L, "hour", "hours"),
    DAY(86_400L, "day", "days"),
    WEEK(604_800L, "week", "weeks"),
    MONTH(2_592_000L, "month", "months"),
    YEAR(31_536_000L, "year", "years");

    private final long seconds;
    private final String singular;
    private final String plural;

    TimeUnit(long seconds, String singular, String plural) {
        this.seconds = seconds;
        this.singular = singular;
        this.plural = plural;
    }

    public long seconds() {
        return seconds;
    }

    /** The unit as a word in a description ("day"). */
    public String singular() {
        return singular;
    }

    /** The plural word ("days"). */
    public String plural() {
        return plural;
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
