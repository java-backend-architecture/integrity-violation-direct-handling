package dev.dmitriirussu.integrityviolation.direct.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record UserRequest(
        @NotBlank @Size(min = 2, max = 5)
        String name,
        @NotBlank @Email @Size(min = 11, max = 12)
        String email
) {
    public UserRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
