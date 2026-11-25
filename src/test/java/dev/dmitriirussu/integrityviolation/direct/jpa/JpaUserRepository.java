package dev.dmitriirussu.integrityviolation.direct.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface JpaUserRepository extends JpaRepository<JpaUser, UUID> {}
