package dev.dmitriirussu.integrityviolation.direct.presentation.exception;

import dev.dmitriirussu.integrityviolation.direct.infrastructure.UserWriteRepository;
import dev.dmitriirussu.integrityviolation.direct.infrastructure.exception.ErrorDetailsExtractor;
import dev.dmitriirussu.integrityviolation.direct.infrastructure.exception.IntegrityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Контракт схемы: каждое правило в БД именованное, и это имя достаётся из ошибки Postgres
// через getConstraint(). Без HTTP и без хендлеров: репозиторий напрямую.
// Упадёт, если кто-то переименует констрейнт в schema.sql, а CONSTRAINT_LABELS и логи об этом не узнают.
@SpringBootTest
class ConstraintNameTest {

    @Autowired UserWriteRepository repository;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void seed() {
        jdbc.sql("delete from users").update();
        repository.create("jack", "jack@mail.ru");
    }

    // Через репозиторий: JACK@mail.ru становится jack@mail.ru ещё до вставки
    // и падает на UNIQUE. Это проверка нормализации приложения, а не CHECK в БД.
    @ParameterizedTest(name = "name={0}, email={1} -> {2}")
    @CsvSource({
            "jack, bob@mail.ru,  uq_users_name",
            "bob,  JACK@mail.ru, uq_users_email",
            "a,    bob@mail.ru,  ck_users_name_length",
            "bob,  b@mail.ru,    ck_users_email_length"
    })
    void violationReportsItsConstraintName(String name, String email, String expected) {
        var e = assertThrows(DataAccessException.class,
                () -> repository.create(name, email));

        var details = ErrorDetailsExtractor.extract(e);

        assertThat(details.constraint()).isEqualTo(expected);
    }

    // NOT NULL: имени ограничения нет, зато есть SQLState 23502 и имя столбца.
    @ParameterizedTest(name = "name={0}, email={1} -> column {2}")
    @CsvSource(nullValues = "NULL", value = {
            "NULL, bob@mail.ru, name",
            "bob,  NULL,        email"
    })
    void notNullReportsColumn(String name, String email, String expected) {
        var e = assertThrows(DataAccessException.class,
                () -> repository.create(name, email));

        var details = ErrorDetailsExtractor.extract(e);

        assertThat(details.sqlState()).isEqualTo("23502");
        assertThat(details.column()).isEqualTo(expected);
    }

    // Прямой INSERT мимо репозитория: нормализации приложения нет, срабатывает схема.
    @Test
    void directInsertBypassingRepositoryHitsDbChecks() {
        assertViolates("john\t", "test@mail.ru", "ck_users_name_no_outer_whitespace");
        assertViolates(" ann", "ann@mail.ru", "ck_users_name_no_outer_whitespace");
        assertViolates("bob", "BOB@MAIL.RU", "ck_users_email_normalized");
        assertViolates("bob", " bob@mail.ru", "ck_users_email_normalized");
    }

    private void assertViolates(String name, String email, String expected) {
        var e = assertThrows(DataAccessException.class, () ->
                jdbc.sql("insert into users (name, email) values (?, ?)")
                        .params(name, email).update());

        var details = ErrorDetailsExtractor.extract(e);

        assertThat(details.constraint()).isEqualTo(expected);
    }

    // Явный NULL отменяет DEFAULT: нарушается NOT NULL для id,
    // а не правило из DTO.
    @Test
    void explicitNullIdHitsNotNullOnId() {
        var e = assertThrows(DataAccessException.class, () ->
                jdbc.sql("""
                        insert into users (id, name, email)
                        values (null, 'bob', 'bob@mail.ru')
                        """).update());

        var details = ErrorDetailsExtractor.extract(e);

        assertThat(details.sqlState()).isEqualTo("23502");
        assertThat(details.column()).isEqualTo("id");

        assertThat(IntegrityPolicy.reason(
                details.sqlState(),
                details.constraint(),
                details.column()
        )).isEqualTo("integrity_violation");
    }
}