package dev.dmitriirussu.integrityviolation.direct.presentation;

import dev.dmitriirussu.integrityviolation.direct.infrastructure.UserWriteRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
public class UserWriteController {
    private final UserWriteRepository repository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UUID create(@Valid @RequestBody UserRequest request) {
        UUID id = repository.create(request.name(), request.email());
        log.info("event=user_created id={}", id);
        return id;
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateById(
            @PathVariable UUID id,
            @Valid @RequestBody UserRequest request
    ) {
        if (!repository.updateById(id, request.name(), request.email())) {
            throw new ResponseStatusException(NOT_FOUND, "User not found");
        }
        log.info("event=user_updated id={}", id);
    }
}

