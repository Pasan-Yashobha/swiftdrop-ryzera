package lk.swiftdrop.assistant.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The message length limit is checked in {@link ChatService}, where it is configurable. */
public record ChatRequest(@NotBlank String message, @Size(max = 64) String conversationId) { }
