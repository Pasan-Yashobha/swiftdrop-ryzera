package lk.swiftdrop.assistant.chat;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The chat endpoint the assistant widget calls. The user is identified only by the verified JWT. */
@RestController
@RequestMapping("/api/v1/assistant")
public class ChatController {
    private final ChatService chat;

    public ChatController(ChatService chat) { this.chat = chat; }

    @PostMapping("/chat")
    public ChatReply chat(@Valid @RequestBody ChatRequest request, Authentication user) {
        return chat.chat(user.getName(), authorization(user), request.message(), request.conversationId());
    }

    /** "New chat": forgets a conversation. Deleting one that is not yours, or does not exist, does nothing. */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> forget(@PathVariable String id, Authentication user) {
        chat.forget(user.getName(), id);
        return ResponseEntity.noContent().build();
    }

    private static String authorization(Authentication user) {
        if (user instanceof JwtAuthenticationToken jwt) {
            return "Bearer " + jwt.getToken().getTokenValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again");
    }
}
