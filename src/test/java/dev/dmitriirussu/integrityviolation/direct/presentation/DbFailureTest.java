package dev.dmitriirussu.integrityviolation.direct.presentation;

import dev.dmitriirussu.integrityviolation.direct.infrastructure.UserWriteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // Boot 4: проверь импорт
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Репозиторий замокан: сломать SQL в рантайме иначе нельзя, а проверить надо только то,
// что клиент получает обобщённые ответы и не видит внутренности.
@SpringBootTest
@AutoConfigureMockMvc
class DbFailureTest {

    private static final String VALID = "{\"name\":\"bob\",\"email\":\"bob@mail.ru\"}";

    @Autowired MockMvc mvc;
    @MockitoBean UserWriteRepository repository;

    @Test
    void unknownDbErrorHidesDetails() throws Exception {
        when(repository.create(any(), any()))
                .thenThrow(new BadSqlGrammarException("insert", "insert int users", new SQLException("syntax")));

        mvc.perform(post())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Database failure"))
                .andExpect(content().string(not(containsString("insert int users"))));
    }

    @Test
    void unexpectedExceptionHidesDetails() throws Exception {
        when(repository.create(any(), any())).thenThrow(new IllegalStateException("boom"));

        mvc.perform(post())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Internal error"))
                .andExpect(content().string(not(containsString("boom"))));
    }

    // Решает SQLState 23505, а не тип исключения
    @Test
    void uniqueViolationInPlainIntegrityExceptionIs409() throws Exception {
        when(repository.create(any(), any()))
                .thenThrow(new DataIntegrityViolationException("dup", new SQLException("dup", "23505")));

        mvc.perform(post())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Resource already exists"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post() {
        return MockMvcRequestBuilders.post("/api/users")
                .contentType(MediaType.APPLICATION_JSON).content(VALID);
    }
}
