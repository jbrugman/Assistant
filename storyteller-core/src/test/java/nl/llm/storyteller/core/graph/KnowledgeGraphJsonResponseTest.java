package nl.llm.storyteller.core.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeGraphJsonResponseTest {
  @ParameterizedTest
  @CsvSource(
    delimiter = '|',
    nullValues = "<null>",
    textBlock = """
      '  {"entities":{}}  '|'{"entities":{}}'
      '```json
      {"entities":{}}
      ```'|'{"entities":{}}'
      '```broken'|'```broken'
      <null>|''
      """
  )
  @DisplayName("""
    Given plain, fenced, malformed, or absent model output,
    When graph JSON is extracted,
    Then only a complete Markdown fence should be removed
    """)
  void extractsGraphJson(String response, String expected) {
    assertEquals(expected, KnowledgeGraphJsonResponse.extract(response));
  }
}
