package nl.llm.storyteller.api.http;

import io.javalin.config.JavalinConfig;
import io.javalin.http.HttpStatus;
import nl.llm.storyteller.api.http.dto.ErrorResponse;

import java.io.IOException;

public final class ApiErrorHandler {
  private ApiErrorHandler() {
  }

  public static void register(JavalinConfig config) {
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
