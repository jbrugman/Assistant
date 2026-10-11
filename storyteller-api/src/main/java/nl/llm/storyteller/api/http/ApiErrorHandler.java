package nl.llm.storyteller.api.http;

import io.javalin.config.JavalinConfig;
import io.javalin.http.HttpStatus;
import io.javalin.http.HttpResponseException;
import io.javalin.router.exception.HttpResponseExceptionMapper;
import nl.llm.storyteller.api.http.dto.ErrorResponse;
import nl.llm.storyteller.api.story.KnowledgeGraphConflictException;

import java.io.IOException;

public final class ApiErrorHandler {
  private ApiErrorHandler() {
  }

  public static void register(JavalinConfig config) {
    config.routes.exception(HttpResponseException.class, (exception, context) -> {
      if (!context.path().startsWith("/v1/")) {
        HttpResponseExceptionMapper.INSTANCE.handle(exception, context);
        return;
      }
      context.status(exception.getStatus()).json(new ErrorResponse(
        switch (exception.getStatus()) {
          case 400 -> "invalid_request";
          case 413 -> "payload_too_large";
          default -> "http_error";
        }, exception.getMessage()
      ));
    });
    config.routes.exception(KnowledgeGraphConflictException.class, (exception, context) ->
      context.status(HttpStatus.CONFLICT).json(new ErrorResponse("graph_conflict", exception.getMessage()))
    );
    config.routes.exception(IllegalArgumentException.class, (exception, context) ->
      context.status(HttpStatus.BAD_REQUEST)
        .json(new ErrorResponse("invalid_request", exception.getMessage()))
    );
    config.routes.exception(IOException.class, (exception, context) ->
      context.status(HttpStatus.BAD_GATEWAY)
        .json(new ErrorResponse("model_backend_error", exception.getMessage()))
    );
    config.routes.exception(InterruptedException.class, (_, context) -> {
      Thread.currentThread().interrupt();
      context.status(HttpStatus.SERVICE_UNAVAILABLE)
        .json(new ErrorResponse("request_interrupted", "The model request was interrupted."));
    });
  }
}
