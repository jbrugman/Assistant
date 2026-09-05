package nl.llm.storyteller.api.http;

import io.javalin.config.JavalinConfig;
import io.javalin.http.HttpStatus;
import nl.llm.storyteller.api.http.dto.ErrorResponse;

public final class ApiErrorHandler {
  private ApiErrorHandler() {
  }

  public static void register(JavalinConfig config) {
    config.routes.exception(IllegalArgumentException.class, (exception, context) ->
      context.status(HttpStatus.BAD_REQUEST)
        .json(new ErrorResponse("invalid_request", exception.getMessage()))
    );
  }
}
