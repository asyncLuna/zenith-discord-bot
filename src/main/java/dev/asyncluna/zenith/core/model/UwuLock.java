package dev.asyncluna.zenith.core.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "uwu_locks")
public record UwuLock(@Id String discordId, Instant createdAt) {}
