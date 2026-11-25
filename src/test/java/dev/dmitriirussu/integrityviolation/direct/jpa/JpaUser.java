package dev.dmitriirussu.integrityviolation.direct.jpa;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "users")
class JpaUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)// только для теста: в проде id выдаёт БД (default gen_random_uuid())
    private UUID id;
    private String name;
    private String email;

    protected JpaUser() {}

    JpaUser(String name, String email) {
        this.name = name;
        this.email = email;
    }
}

