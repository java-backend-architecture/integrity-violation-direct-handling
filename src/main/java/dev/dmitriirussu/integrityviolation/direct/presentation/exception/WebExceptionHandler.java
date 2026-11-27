package dev.dmitriirussu.integrityviolation.direct.presentation.exception;

import dev.dmitriirussu.integrityviolation.direct.infrastructure.exception.ErrorDetailsExtractor;
import dev.dmitriirussu.integrityviolation.direct.infrastructure.exception.IntegrityPolicy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import org.springframework.http.HttpStatus;

@Slf4j
@RestControllerAdvice
public class WebExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity(DataIntegrityViolationException e, HttpServletRequest req) {
        var details = ErrorDetailsExtractor.extract(e);

        var state = details.sqlState();
        var constraint = details.constraint();
        var column = details.column();

        if (IntegrityPolicy.isDuplicate(state)) {
            log.info(
                    "event=duplicate_key method={} path={} constraint={}",
                    req.getMethod(), req.getRequestURI(), constraint
            );

            var label = IntegrityPolicy.duplicateLabel(constraint);
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, label + " already exists"
            );
        }

        var reason = IntegrityPolicy.reason(state, constraint, column);

        log.error(
                "event=db_integrity_violation reason={} sqlstate={} method={} path={} constraint={} column={}",
                reason, state, req.getMethod(), req.getRequestURI(), constraint, column
        );

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"
        );
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail dataAccess(DataAccessException e, HttpServletRequest req) {
        log.error(
                "event=db_failure method={} path={}",
                req.getMethod(), req.getRequestURI(), e
        );

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Database failure"
        );
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e, HttpServletRequest req) {
        log.error(
                "event=unexpected_error method={} path={}",
                req.getMethod(), req.getRequestURI(), e
        );

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"
        );
    }
}