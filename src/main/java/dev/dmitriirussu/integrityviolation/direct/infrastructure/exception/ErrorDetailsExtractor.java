package dev.dmitriirussu.integrityviolation.direct.infrastructure.exception;


import org.postgresql.util.PSQLException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

public final class ErrorDetailsExtractor {

    private static final String UNKNOWN = "unknown";

    private ErrorDetailsExtractor() {}

    public static Details extract(DataAccessException e) {
        var cause = NestedExceptionUtils.getMostSpecificCause(e);

        var serverError = cause instanceof PSQLException pe
                ? pe.getServerErrorMessage()
                : null;

        var sqlState = cause instanceof SQLException se
                ? orUnknown(se.getSQLState())
                : UNKNOWN;

        var constraint = serverError != null
                ? orUnknown(serverError.getConstraint())
                : UNKNOWN;

        var column = serverError != null
                ? orUnknown(serverError.getColumn())
                : UNKNOWN;

        return new Details(sqlState, constraint, column);
    }

    private static String orUnknown(String value) {
        return value != null ? value : UNKNOWN;
    }

    public record Details(
            String sqlState,
            String constraint,
            String column
    ) {
    }
}
