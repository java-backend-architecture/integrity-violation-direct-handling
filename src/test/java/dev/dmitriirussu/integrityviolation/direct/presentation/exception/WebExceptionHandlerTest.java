package dev.dmitriirussu.integrityviolation.direct.presentation.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class WebExceptionHandlerTest {

    private final WebExceptionHandler handler = new WebExceptionHandler();
    private final MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/users");

    // Решает SQLState, имени ограничения нет: подпись по умолчанию
    @Test
    void uniqueViolationWithoutConstraintNameIs409WithGenericLabel() {
        var e = new DataIntegrityViolationException("dup", new SQLException("dup", "23505"));

        var problem = handler.integrity(e, req);

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("Resource already exists");
    }

    // FK, id = NULL, бизнес-CHECK: 500 без деталей, причина только в логе
    @Test
    void otherIntegrityViolationIs500AndLogsReason(CapturedOutput output) {
        var e = new DataIntegrityViolationException("fk", new SQLException("fk", "23503"));

        var problem = handler.integrity(e, req);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isEqualTo("Internal error");
        assertThat(output)
                .contains("event=db_integrity_violation")
                .contains("reason=integrity_violation")
                .contains("sqlstate=23503");
    }

    @Test
    void integrityViolationDoesNotLeakRowValuesToLog(CapturedOutput output) {
        var e = new DataIntegrityViolationException("check",
                new SQLException("Failing row contains (secret@mail.ru)", "23514"));

        handler.integrity(e, req);

        assertThat(output)
                .contains("event=db_integrity_violation")
                .doesNotContain("secret@mail.ru");
    }
}
