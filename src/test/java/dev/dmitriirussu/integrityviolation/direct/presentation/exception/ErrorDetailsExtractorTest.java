package dev.dmitriirussu.integrityviolation.direct.presentation.exception;

import dev.dmitriirussu.integrityviolation.direct.infrastructure.exception.ErrorDetailsExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorDetailsExtractorTest {

    @Test
    void nonPostgresCauseKeepsSqlStateAndHasNoConstraintOrColumn() {
        var e = new DataIntegrityViolationException("x", new SQLException("boom", "23505"));

        var details = ErrorDetailsExtractor.extract(e);

        assertThat(details.sqlState()).isEqualTo("23505");
        assertThat(details.constraint()).isEqualTo("unknown");
        assertThat(details.column()).isEqualTo("unknown");
    }

    @Test
    void missingSqlStateGivesUnknown() {
        var e = new DataIntegrityViolationException("x", new SQLException("boom"));

        assertThat(ErrorDetailsExtractor.extract(e).sqlState()).isEqualTo("unknown");
    }

    @Test
    void exceptionWithoutCauseGivesAllUnknown() {
        var details = ErrorDetailsExtractor.extract(new DataIntegrityViolationException("x"));

        assertThat(details).isEqualTo(new ErrorDetailsExtractor.Details("unknown", "unknown", "unknown"));
    }
}
