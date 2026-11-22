/** Assistant service (Member 5): AI chat that answers with MCP tools. */

/** One MCP tool the assistant used to answer. The user's token is never part of it. */
export interface ToolTrace {
  tool: string;
  arguments: Record<string, unknown>;
  failed: boolean;
}

export interface ChatReply {
  conversationId: string;
  reply: string;
  toolCalls: ToolTrace[];
}
