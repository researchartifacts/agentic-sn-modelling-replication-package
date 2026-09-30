package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.Conventions;
import org.snrmbench.netbench.schema.Field;
import org.snrmbench.netbench.util.Numbers;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Compares one scored field of a truth entity with the same field of its candidate entity and classifies the result. */
public final class FieldComparator {

    /** One side of a comparison. */
    public record Value(String value, String second, TimeUnit periodUnit) {
    }

    private FieldComparator() {
    }

    /** The cells of a field of an entity; a null entity reads as blank. */
    public static Value of(Field field, Entity entity, TimeUnit periodUnit) {
        if (entity == null) {
            return new Value("", "", periodUnit);
        }
        if (field.type() == Field.Type.EXISTS) {
            return new Value(Boolean.TRUE.toString(), "", periodUnit);
        }
        return new Value(entity.cell(field.column()), field.second() == null ? "" : entity.cell(field.second()),
                periodUnit);
    }

    /** The cells of a field, with numbers read leniently when lenient: a numeric cell that only the locale-lenient grammar parses (1,200) is replaced by its plain form, so every later comparison sees the plain number. */
    public static Value of(Field field, Entity entity, TimeUnit periodUnit, boolean lenient) {
        Value v = of(field, entity, periodUnit);
        if (!lenient || entity == null) {
            return v;
        }
        return switch (field.type()) {
            case NUMBER, DURATION, RATE -> new Value(plain(v.value()), v.second(), periodUnit);
            case GEO -> new Value(plain(v.value()), plain(v.second()), periodUnit);
            default -> v;
        };
    }

    private static String plain(String cell) {
        return Numbers.parseLenient(cell).map(BigDecimal::toPlainString).orElse(cell);
    }

    /** Blank: the value cell is empty (units are ignored); for geo, both cells are. */
    public static boolean blank(Field field, Value v) {
        return v.value().isEmpty() && (field.type() != Field.Type.GEO || v.second().isEmpty());
    }

    /** The class of a field. */
    public static FactClass classify(Field field, Value truth, Value candidate, boolean stated,
                                     Optional<Conventions.Value> convention, BigDecimal tolerance) {
        boolean candidateBlank = blank(field, candidate);
        if (convention.isPresent()) {
            boolean ok = candidateBlank || (convention.get() == Conventions.Value.ZERO
                    && Numbers.parse(candidate.value()).map(v -> v.signum() == 0).orElse(false));
            return ok ? FactClass.CONVENTION_OK : FactClass.CONVENTION_VIOLATION;
        }
        if (!stated) {
            return candidateBlank ? FactClass.CORRECT_BLANK : FactClass.FILLED_UNSTATED;
        }
        if (field.type() == Field.Type.NODE_TYPE) {
            return equal(field, truth, candidate, tolerance) ? FactClass.CORRECT : FactClass.WRONG_TYPE;
        }
        if (candidateBlank) {
            return blank(field, truth) ? FactClass.CORRECT : FactClass.OMITTED;
        }
        if (equal(field, truth, candidate, tolerance)) {
            return FactClass.CORRECT;
        }
        return sameNumberOtherUnit(field, truth, candidate) ? FactClass.WRONG_UNIT : FactClass.WRONG;
    }

    /** Whether two sides hold the same value of a field. */
    public static boolean equal(Field field, Value a, Value b, BigDecimal tolerance) {
        return switch (field.type()) {
            case EXISTS -> a.value().equals(b.value());
            case NODE_TYPE, TEXT, ENUM -> Normaliser.name(a.value()).equals(Normaliser.name(b.value()));
            case NUMBER -> numberEqual(a.value(), b.value(), tolerance);
            case DURATION, RATE -> quantityEqual(field.type() == Field.Type.DURATION, a, b, tolerance);
            case GEO -> {
                Optional<List<String>> ga = geo(a);
                yield ga.isPresent() && ga.equals(geo(b));
            }
        };
    }

    /** wrong_unit: the raw numbers are equal and both units resolve, but to different units. */
    public static boolean sameNumberOtherUnit(Field field, Value a, Value b) {
        if (field.type() != Field.Type.DURATION && field.type() != Field.Type.RATE) {
            return false;
        }
        Optional<BigDecimal> va = Numbers.parse(a.value());
        Optional<BigDecimal> vb = Numbers.parse(b.value());
        Optional<TimeUnit> ua = Normaliser.unit(a.second(), a.periodUnit());
        Optional<TimeUnit> ub = Normaliser.unit(b.second(), b.periodUnit());
        return va.isPresent() && vb.isPresent() && ua.isPresent() && ub.isPresent()
                && va.get().compareTo(vb.get()) == 0 && ua.get() != ub.get();
    }

    /** The value as a report shows it: null when blank, {@code lat,lng} for geo. */
    public static String display(Field field, Value v) {
        if (blank(field, v)) {
            return null;
        }
        return field.type() == Field.Type.GEO ? v.value() + "," + v.second() : v.value();
    }

    /** The unit as a report shows it: the canonical name the unit resolves to (a blank unit shows as the period unit), the raw token when it is unknown, null for unitless fields and blank values. */
    public static String unitDisplay(Field field, Value v) {
        if ((field.type() != Field.Type.DURATION && field.type() != Field.Type.RATE) || v.value().isEmpty()) {
            return null;
        }
        return Normaliser.unit(v.second(), v.periodUnit()).map(Enum::name)
                .orElse(v.second().isEmpty() ? null : v.second());
    }

    // ---- numbers ----------------------------------------------------------------------------

    private static boolean numberEqual(String a, String b, BigDecimal tolerance) {
        Optional<BigDecimal> va = Numbers.parse(a);
        Optional<BigDecimal> vb = Numbers.parse(b);
        if (va.isEmpty() || vb.isEmpty()) {
            return false;
        }
        if (va.get().compareTo(vb.get()) == 0) {
            return true;
        }
        return !bothIntegers(va.get(), vb.get()) && within(va.get(), vb.get(), tolerance);
    }

    private static boolean quantityEqual(boolean duration, Value a, Value b, BigDecimal tolerance) {
        Optional<BigDecimal> va = Numbers.parse(a.value());
        Optional<BigDecimal> vb = Numbers.parse(b.value());
        Optional<TimeUnit> ua = Normaliser.unit(a.second(), a.periodUnit());
        Optional<TimeUnit> ub = Normaliser.unit(b.second(), b.periodUnit());
        if (va.isEmpty() || vb.isEmpty() || ua.isEmpty() || ub.isEmpty()) {
            return false;
        }
        BigDecimal sa = BigDecimal.valueOf(ua.get().seconds());
        BigDecimal sb = BigDecimal.valueOf(ub.get().seconds());
        // durations: a*sa == b*sb; rates: a/sa == b/sb, i.e. a*sb == b*sa
        boolean exact = duration
                ? va.get().multiply(sa).compareTo(vb.get().multiply(sb)) == 0
                : va.get().multiply(sb).compareTo(vb.get().multiply(sa)) == 0;
        if (exact) {
            return true;
        }
        if (bothIntegers(va.get(), vb.get())) {
            return false;
        }
        BigDecimal na = duration ? va.get().multiply(sa) : va.get().divide(sa, MathContext.DECIMAL128);
        BigDecimal nb = duration ? vb.get().multiply(sb) : vb.get().divide(sb, MathContext.DECIMAL128);
        return within(na, nb, tolerance);
    }

    private static boolean bothIntegers(BigDecimal a, BigDecimal b) {
        return Numbers.isInteger(a) && Numbers.isInteger(b);
    }

    private static boolean within(BigDecimal a, BigDecimal b, BigDecimal tolerance) {
        return a.subtract(b).abs().compareTo(tolerance.multiply(a.abs().max(b.abs()))) <= 0;
    }

    /** lat and lng rounded half-up to two decimals ("" for a blank cell); empty when either does not parse. */
    private static Optional<List<String>> geo(Value v) {
        List<String> out = new ArrayList<>(2);
        for (String cell : List.of(v.value(), v.second())) {
            if (cell.isEmpty()) {
                out.add("");
                continue;
            }
            Optional<BigDecimal> n = Numbers.parse(cell);
            if (n.isEmpty()) {
                return Optional.empty();
            }
            out.add(n.get().setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        return Optional.of(out);
    }
}
