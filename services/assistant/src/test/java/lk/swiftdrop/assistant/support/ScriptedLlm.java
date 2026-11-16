package lk.swiftdrop.assistant.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import lk.swiftdrop.assistant.llm.LlmClient;
import lk.swiftdrop.assistant.llm.LlmMessage;
import lk.swiftdrop.assistant.llm.LlmReply;
import lk.swiftdrop.assistant.llm.ToolDefinition;

/**
 * The "fake Gemini": a language model whose every answer is scripted by the test. It records what it was sent, so
 * tests can check the system prompt, the history and the tool results that reached the model.
 */
public final class ScriptedLlm implements LlmClient {
    public record Call(String systemPrompt, List<LlmMessage> conversation, List<ToolDefinition> tools) { }

    private final Deque<Function<List<LlmMessage>, LlmReply>> script = new ArrayDeque<>();
    private final List<Call> calls = new ArrayList<>();

    /** The next answer, computed from the conversation the model is shown. */
    public ScriptedLlm then(Function<List<LlmMessage>, LlmReply> answer) {
        script.addLast(answer);
        return this;
    }

    public ScriptedLlm thenReply(String text) { return then(conversation -> LlmReply.text(text)); }

    public ScriptedLlm thenCall(String tool, java.util.Map<String, Object> arguments) {
        return then(conversation -> new LlmReply("", List.of(new lk.swiftdrop.assistant.llm.ToolCall(tool, arguments)), null));
    }

    public List<Call> calls() { return calls; }

    public void reset() {
        script.clear();
        calls.clear();
    }

    @Override
    public synchronized LlmReply generate(String systemPrompt, List<LlmMessage> conversation, List<ToolDefinition> tools) {
        calls.add(new Call(systemPrompt, List.copyOf(conversation), List.copyOf(tools)));
        var next = script.pollFirst();
        if (next == null) throw new IllegalStateException("The scripted model has no more answers");
        return next.apply(conversation);
    }
}
