package dev.dmitriirussu.integrityviolation.direct.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // Boot 4: проверь импорт
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Весь стек: HTTP -> валидация -> контроллер -> репозиторий -> реальный встроенный Postgres.
// Моков нет: ценность проекта именно в связке с реальными констрейнтами.
@SpringBootTest
@AutoConfigureMockMvc
// Даёт доступ к тому, что приложение напечатало в лог: так проверяем event= и constraint=.
@ExtendWith(OutputCaptureExtension.class)
class UserApiTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    // Встроенный Postgres живёт весь прогон класса, поэтому каждый тест начинает с пустой таблицы.
    @BeforeEach
    void clean() {
        jdbc.sql("delete from users").update();
    }

    @Test
    void createReturnsIdAndNormalizesInput() throws Exception {
        // trim имени и lowercase email делает DTO
        var body = post(json("  ann  ", "  ANN@MAIL.RU  "))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var id = UUID.fromString(body.replace("\"", ""));

        assertThat(jdbc.sql("select name from users where id = ?").param(id).query(String.class).single())
                .isEqualTo("ann");
        assertThat(jdbc.sql("select email from users where id = ?").param(id).query(String.class).single())
                .isEqualTo("ann@mail.ru");
    }

    @Test
    void duplicateNameIs409(CapturedOutput output) throws Exception {
        post(json("jack", "jack@mail.ru")).andExpect(status().isCreated());

        post(json("jack", "bob@mail.ru"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Name already exists"));
        assertThat(output).contains("event=duplicate_key").contains("constraint=uq_users_name");
    }

    @Test
    void duplicateEmailIs409IgnoringCase(CapturedOutput output) throws Exception {
        post(json("jack", "jack@mail.ru")).andExpect(status().isCreated());

        // JACK@MAIL.RU после lowercase в DTO совпадает с jack@mail.ru
        post(json("bob", "JACK@MAIL.RU"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already exists"));
        assertThat(output).contains("constraint=uq_users_email");
    }

    @Test
    void updateChangesRow() throws Exception {
        var id = create("jack", "jack@mail.ru");

        put(id, json("john", "john@mail.ru")).andExpect(status().isNoContent());

        assertThat(jdbc.sql("select name from users where id = ?").param(id).query(String.class).single())
                .isEqualTo("john");
    }

    @Test
    void updateOfMissingUserIs404() throws Exception {
        // данные валидные, иначе 400 от валидации придёт раньше, чем контроллер вернёт 404
        put(UUID.randomUUID(), json("zed", "zed@mail.ru"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found"));
    }

    @Test
    void updateToTakenEmailIs409() throws Exception {
        create("jack", "jack@mail.ru");
        var ann = create("ann", "ann@mail.ru");

        put(ann, json("ann", "jack@mail.ru"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already exists"));
    }

    // Всё, что должна отсечь валидация или Jackson ДО базы. Главное утверждение:
    // в логе нет ни одного event=db_*, то есть до БД запрос не дошёл.
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":null,\"email\":\"bob@mail.ru\"}",
            "{\"name\":\"a\",\"email\":\"bob@mail.ru\"}",
            "{\"name\":\"abcdef\",\"email\":\"bob@mail.ru\"}",
            "{\"name\":\"   \",\"email\":\"bob@mail.ru\"}",
            "{\"name\":\"bob\",\"email\":null}",
            "{\"name\":\"bob\",\"email\":\"b@mail.ru\"}",
            "{\"name\":\"bob\",\"email\":\"notanemail1\"}",
            "{}",
            "{bad"
    })
    void invalidRequestStopsBeforeDatabase(String body, CapturedOutput output) throws Exception {
        post(body).andExpect(status().isBadRequest());
        assertThat(output).doesNotContain("event=db_");
    }

    // Единственный путь до CHECK в базе через HTTP: @Size считает единицы UTF-16,
    // а length() в Postgres символы. Эмодзи это 2 для Java и 1 для Postgres.
    // Это и есть тот самый «рассинхрон валидации и схемы», ради которого существует integrity.
    @Test
    void validationAndSchemaMismatchGoesToIntegrityHandler(CapturedOutput output) throws Exception {
        post(json("\\uD83D\\uDE00", "emo@mail.ru")) // JSON-эскейп суррогатной пары
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Internal error"));
        assertThat(output)
                .contains("event=db_integrity_violation")
                .contains("reason=validation_schema_mismatch")
                .contains("constraint=ck_users_name_length");
        post(json("\\uD83D\\uDE00", "emo@mail.ru"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Internal error"))
                .andExpect(content().string(not(containsString("ck_users"))));
    }

    // Страховка от главной ловушки: общий @ExceptionHandler(Exception.class) не должен
    // перехватывать MVC-исключения. Это работает только благодаря ResponseEntityExceptionHandler.
    @Test
    void mvcExceptionsAreNotSwallowedByGenericHandler() throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete("/api/users")).andExpect(status().isMethodNotAllowed());
        mvc.perform(MockMvcRequestBuilders.put("/api/users/abc")
                        .contentType(MediaType.APPLICATION_JSON).content(json("zed", "zed@mail.ru")))
                .andExpect(status().isBadRequest());
    }

    private UUID create(String name, String email) throws Exception {
        var body = post(json(name, email)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replace("\"", ""));
    }

    private ResultActions post(String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/users")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions put(UUID id, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put("/api/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String json(String name, String email) {
        return "{\"name\":%s,\"email\":%s}".formatted(quote(name), quote(email));
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
