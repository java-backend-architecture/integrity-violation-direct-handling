package dev.dmitriirussu.integrityviolation.direct.infrastructure.exception;

import java.util.Map;
import java.util.Set;

public final class IntegrityPolicy {

    private static final Map<String, String> CONSTRAINT_LABELS = Map.of(
            "uq_users_name", "Name",
            "uq_users_email", "Email"
    );

    // Ограничения и столбцы, дублирующие входную валидацию.
    // Часть из них недостижима через HTTP (Jakarta ловит раньше).
    // Набор защищает от других путей записи и от изменений DTO.
    private static final Set<String> VALIDATION_CHECKS = Set.of(
            "ck_users_name_no_outer_whitespace",
            "ck_users_email_normalized",
            "ck_users_name_length",
            "ck_users_email_length"
    );

    private static final Set<String> VALIDATED_COLUMNS = Set.of(
            "name", "email"
    );

    private IntegrityPolicy() {
    }

    public static String duplicateLabel(String constraint) {
        return CONSTRAINT_LABELS.getOrDefault(constraint, "Resource");
    }

    // 23514 = check_violation, 23502 = not_null_violation.
    // Рассогласованием считаем только нарушения правил,
    // дублирующих входную валидацию.
    public static String reason(String state, String constraint, String column) {
        return switch (state) {
            case "23514" ->
                    VALIDATION_CHECKS.contains(constraint)
                            ? "validation_schema_mismatch" : "integrity_violation";
            case "23502" ->
                    VALIDATED_COLUMNS.contains(column)
                            ? "validation_schema_mismatch" : "integrity_violation";
            default -> "integrity_violation";
        };
    }

    public static boolean isDuplicate(ErrorDetailsExtractor.Details d) {
        return "23505".equals(d.sqlState());
    }
}
