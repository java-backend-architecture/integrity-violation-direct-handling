package dev.dmitriirussu.integrityviolation.direct.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserWriteRepository {
    private final JdbcClient client;

    public UUID create(String name, String email) {
        var sql = """
            insert into users (name, email)
            values (trim(?), lower(trim(?)))
            returning id
            """;

        return client.sql(sql).params(name, email).query(UUID.class).single();
    }

    public boolean updateById(UUID id, String name, String email) {
        var sql = """
            update users
            set name = trim(?),
                email = lower(trim(?))
            where id = ?
            """;

        return client.sql(sql).params(name, email, id).update() == 1;
    }
}
