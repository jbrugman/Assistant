package nl.llm.storyteller.api.http;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import nl.llm.storyteller.api.http.dto.EditAssistantMessageRequest;
import nl.llm.storyteller.api.http.dto.EditAssistantMessageResponse;
import nl.llm.storyteller.api.http.dto.ErrorResponse;
import nl.llm.storyteller.api.http.dto.StoryTurnRequest;
import nl.llm.storyteller.api.http.dto.StoryTurnResponse;
import nl.llm.storyteller.api.session.SessionService;
import nl.llm.storyteller.api.story.StoryTurnResult;
import nl.llm.storyteller.api.story.StoryTurnService;

import java.io.IOException;
import java.util.Optional;

public final class StoryController {
  private final SessionService sessionService;
  private final StoryTurnService storyTurnService;

  public StoryController(SessionService sessionService, StoryTurnService storyTurnService) {
    this.sessionService = sessionService;
    this.storyTurnService = storyTurnService;
  }

  public void register(JavalinConfig config) {
    config.routes.post("/v1/sessions/{sessionId}/turns", this::createTurn);
    config.routes.post("/v1/sessions/{sessionId}/messages/{messageIndex}/edit", this::editAssistantMessage);
  }

  private void createTurn(Context context) throws IOException, InterruptedException {
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

  private void editAssistantMessage(Context context) {
    String sessionId = context.pathParam("sessionId");
    if (sessionService.findActive(sessionId).isEmpty()) {
      context.status(HttpStatus.NOT_FOUND)
        .json(new ErrorResponse("session_not_found", "Session was not found."));
      return;
    }
    int messageIndex = parseMessageIndex(context.pathParam("messageIndex"));
    EditAssistantMessageRequest request = context.bodyAsClass(EditAssistantMessageRequest.class);
    Optional<String> updatedContent = storyTurnService.editAssistantResponse(sessionId, messageIndex, request.content());
    if (updatedContent.isEmpty()) {
      context.status(HttpStatus.NOT_FOUND)
        .json(new ErrorResponse("assistant_message_not_found", "Assistant message was not found."));
      return;
    }
    context.json(new EditAssistantMessageResponse(sessionId, messageIndex, updatedContent.get()));
  }

  private int parseMessageIndex(String value) {
    try {
      int messageIndex = Integer.parseInt(value);
      if (messageIndex < 0) {
        throw new NumberFormatException();
      }
      return messageIndex;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Message index must be zero or greater.", ex);
    }
  }
}
