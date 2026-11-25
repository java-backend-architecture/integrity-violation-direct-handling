package dev.dmitriirussu.integrityviolation.direct.jpa;

import dev.dmitriirussu.integrityviolation.direct.infrastructure.exception.ErrorDetailsExtractor;
import dev.dmitriirussu.integrityviolation.direct.presentation.exception.WebExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class JpaDuplicateTest {

    @Autowired JpaUserRepository repository;
    @Autowired JdbcClient jdbc;
    @Autowired WebExceptionHandler handler;

    @BeforeEach
    void clean() {
        jdbc.sql("delete from users").update();
    }

    // Реальный Hibernate + реальный PostgreSQL: дубликат имени приходит
    // как общий DataIntegrityViolationException и даёт 409 в том же обработчике.
    @Test
    void jpaUniqueViolationIs409() {
        repository.saveAndFlush(new JpaUser("ann", "ann@mail.ru"));

        var e = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new JpaUser("ann", "bob@mail.ru")));

        assertThat(e).isNotInstanceOf(DuplicateKeyException.class);

        var problem = handler.integrity(e, new MockHttpServletRequest("POST", "/users"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("Name already exists");
        assertThat(ErrorDetailsExtractor.extract(e).constraint())
                .isEqualTo("uq_users_name");
    }
}

