package nl.llm.storyteller.core.graph;

public final class KnowledgeGraphJsonResponse {
  private KnowledgeGraphJsonResponse() {
  }

  public static String extract(String response) {
    String json = response == null ? "" : response.trim();
    if (!json.startsWith("```")) {
      return json;
    }

    int firstNewline = json.indexOf('\n');
    int closingFence = json.lastIndexOf("```");
    return firstNewline >= 0 && closingFence > firstNewline
      ? json.substring(firstNewline + 1, closingFence).trim()
      : json;
  }
}
