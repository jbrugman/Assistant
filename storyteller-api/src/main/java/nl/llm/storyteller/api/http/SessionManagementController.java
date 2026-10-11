package nl.llm.storyteller.api.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.config.SizeUnit;
import nl.llm.storyteller.api.bundle.SessionBundleService;
import nl.llm.storyteller.api.http.dto.ErrorResponse;
import nl.llm.storyteller.api.http.dto.SessionLifetimeRequest;
import nl.llm.storyteller.api.http.dto.SessionResponse;
import nl.llm.storyteller.api.session.SessionCookieService;
import nl.llm.storyteller.api.session.SessionService;
import nl.llm.storyteller.core.JsonSupport;

import java.io.IOException;

public final class SessionManagementController {
  private final SessionService sessionService;
  private final SessionCookieService cookieService;
  private final SessionBundleService bundleService;

  public SessionManagementController(
    SessionService sessionService, SessionCookieService cookieService, SessionBundleService bundleService
  ) {
    this.sessionService = sessionService;
    this.cookieService = cookieService;
    this.bundleService = bundleService;
  }

  public void register(JavalinConfig config) {
    config.routes.post("/v1/session/stop", this::stop);
    config.routes.patch("/v1/sessions/{sessionId}", this::lifetime);
    config.routes.delete("/v1/sessions/{sessionId}", this::delete);
    config.routes.post("/v1/sessions/{sessionId}/resume", this::resume);
    config.routes.post("/v1/session-imports", this::importBundle);
    config.routes.get("/v1/sessions/{sessionId}/export", this::exportBundle);
  }

  private void stop(Context context) {
    sessionService.delete(cookieService.read(context));
    cookieService.clear(context);
    context.status(HttpStatus.NO_CONTENT);
  }

  private void lifetime(Context context) {
    SessionLifetimeRequest request;
    try {
      request = JsonSupport.OBJECT_MAPPER.readerFor(SessionLifetimeRequest.class)
        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(context.body());
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Session patch must contain a boolean infinite field.", ex);
    }
    if (request == null || request.infinite() == null || !request.infinite().isBoolean()) {
      throw new IllegalArgumentException("Session patch must contain a boolean infinite field.");
    }
    var session = sessionService.setInfinite(context.pathParam("sessionId"), request.infinite().booleanValue());
    if (session.isEmpty()) {
      notFound(context);
      return;
    }
    if (session.get().sessionId().equals(cookieService.read(context))) {
      cookieService.write(context, session.get().sessionId(), session.get().infinite());
    }
    context.json(SessionResponse.from(session.get()));
  }

  private void delete(Context context) {
    var session = sessionService.findActive(context.pathParam("sessionId"));
    if (session.isEmpty()) {
      notFound(context);
      return;
    }
    if (session.get().infinite()) {
      context.status(HttpStatus.CONFLICT).json(new ErrorResponse(
        "session_infinite", "Disable infinite lifetime before deleting the session."
      ));
      return;
    }
    sessionService.delete(session.get().sessionId());
    if (session.get().sessionId().equals(cookieService.read(context))) {
      cookieService.clear(context);
    }
    context.status(HttpStatus.NO_CONTENT);
  }

  private void resume(Context context) {
    var session = sessionService.resumeInfinite(context.pathParam("sessionId"));
    if (session.isEmpty()) {
      context.status(HttpStatus.NOT_FOUND).json(new ErrorResponse(
        "session_not_resumable", "Infinite session was not found."
      ));
      return;
    }
    cookieService.write(context, session.get().sessionId(), true);
    context.json(SessionResponse.from(session.get()));
  }

  private void importBundle(Context context) throws IOException {
    context.multipartConfig().maxFileSize(SessionBundleService.MAX_ARCHIVE_BYTES, SizeUnit.BYTES);
    context.multipartConfig().maxTotalRequestSize(SessionBundleService.MAX_ARCHIVE_BYTES + 1024 * 1024, SizeUnit.BYTES);
    var upload = ApiMultipart.file(context, "bundle");
    if (upload == null) {
      throw new IllegalArgumentException("Select a session ZIP to import.");
    }
    try (var input = upload.content()) {
      var session = bundleService.importArchive(input, upload.size(), upload.filename());
      cookieService.write(context, session.sessionId(), session.infinite());
      context.status(HttpStatus.CREATED).json(SessionResponse.from(session));
    }
  }

  private void exportBundle(Context context) throws IOException {
    var session = sessionService.findActive(context.pathParam("sessionId"));
    if (session.isEmpty()) {
      notFound(context);
      return;
    }
    byte[] archive = bundleService.exportArchive(session.get().sessionId(), session.get().title());
    context.contentType("application/zip");
    context.header("Content-Disposition", "attachment; filename=\"story-session-" + session.get().sessionId() + ".zip\"");
    context.result(archive);
  }

  private void notFound(Context context) {
    context.status(HttpStatus.NOT_FOUND).json(new ErrorResponse("session_not_found", "Session was not found."));
  }
}
