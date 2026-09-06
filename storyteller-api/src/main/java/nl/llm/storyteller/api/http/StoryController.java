package nl.llm.storyteller.api.http;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import nl.llm.storyteller.api.http.dto.ErrorResponse;
import nl.llm.storyteller.api.http.dto.StoryTurnRequest;
import nl.llm.storyteller.api.http.dto.StoryTurnResponse;
import nl.llm.storyteller.api.session.SessionService;
import nl.llm.storyteller.api.story.StoryTurnResult;
import nl.llm.storyteller.api.story.StoryTurnService;

public final class StoryController {
  private final SessionService sessionService;
  private final StoryTurnService storyTurnService;

  public StoryController(SessionService sessionService, StoryTurnService storyTurnService) {
    this.sessionService = sessionService;
    this.storyTurnService = storyTurnService;
  }

  public void register(JavalinConfig config) {
    config.routes.post("/v1/sessions/{sessionId}/turns", this::createTurn);
  }

  private void createTurn(Context context) throws Exception {
    String sessionId = context.pathParam("sessionId");
    if (sessionService.findActive(sessionId).isEmpty()) {
      context.status(HttpStatus.NOT_FOUND)
        .json(new ErrorResponse("session_not_found", "Session was not found."));
      return;
    }

    StoryTurnRequest request = context.bodyAsClass(StoryTurnRequest.class);
    StoryTurnResult result = storyTurnService.execute(sessionId, request.prompt());
    context.json(new StoryTurnResponse(
      sessionId,
      result.userMessageIndex(),
      result.assistantMessageIndex(),
      result.response()
    ));
  }
}
