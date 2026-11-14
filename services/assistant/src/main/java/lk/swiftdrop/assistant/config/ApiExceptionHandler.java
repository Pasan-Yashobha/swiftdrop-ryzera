package lk.swiftdrop.assistant.config;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns errors such as "the AI model is busy" into standard JSON problem details, whose {@code detail} is the safe
 * sentence the chat widget shows. Stack traces and raw exception messages are never returned.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
}
