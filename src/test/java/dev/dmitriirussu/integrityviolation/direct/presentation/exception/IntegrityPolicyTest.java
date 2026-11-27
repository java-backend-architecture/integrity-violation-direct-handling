package dev.dmitriirussu.integrityviolation.direct.presentation.exception;

import dev.dmitriirussu.integrityviolation.direct.infrastructure.exception.ErrorDetailsExtractor;
import dev.dmitriirussu.integrityviolation.direct.infrastructure.exception.IntegrityPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrityPolicyTest {

    @ParameterizedTest(name = "{0} / {1} / {2} -> {3}")
    @CsvSource({
            // каждое ограничение из VALIDATION_CHECKS
            "23514, ck_users_name_no_outer_whitespace, unknown, validation_schema_mismatch",
            "23514, ck_users_email_normalized,         unknown, validation_schema_mismatch",
            "23514, ck_users_name_length,              unknown, validation_schema_mismatch",
            "23514, ck_users_email_length,             unknown, validation_schema_mismatch",

            // самостоятельный бизнес-CHECK
            "23514, ck_users_business_rule,            unknown, integrity_violation",

            // NOT NULL
            "23502, unknown,                           name,    validation_schema_mismatch",
            "23502, unknown,                           email,   validation_schema_mismatch",
            "23502, unknown,                           id,      integrity_violation",

            // всё прочее
            "23503, fk_any,                            unknown, integrity_violation",
            "23502,                                    unknown, unknown, integrity_violation",
            "unknown,                                  unknown, unknown, integrity_violation"
    })
    void classifiesBySpecificRule(
            String state,
            String constraint,
            String column,
            String expected
    ) {
        assertThat(IntegrityPolicy.reason(state, constraint, column))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"23505, true", "23514, false", "unknown, false"})
    void isDuplicateRecognizesOnlyUniqueViolation(String state, boolean expected) {
        assertThat(IntegrityPolicy.isDuplicate(state)).isEqualTo(expected);
    }
}