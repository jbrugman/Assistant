package nl.llm.storyteller.api.http;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import nl.llm.storyteller.api.http.dto.CreateSessionRequest;
import nl.llm.storyteller.api.http.dto.ErrorResponse;
import nl.llm.storyteller.api.http.dto.SessionResponse;
import nl.llm.storyteller.api.persistence.SessionRecord;
import nl.llm.storyteller.api.session.SessionCookieService;
import nl.llm.storyteller.api.session.SessionService;

import java.util.Optional;

public final class SessionController {
  private final SessionService sessionService;
  private final SessionCookieService cookieService;

  public SessionController(SessionService sessionService, SessionCookieService cookieService) {
    this.sessionService = sessionService;
    this.cookieService = cookieService;
  }

  public void register(JavalinConfig config) {
    config.routes.post("/v1/sessions", this::createSession);
    config.routes.get("/v1/session", this::getCurrentSession);
    config.routes.get("/v1/sessions/{sessionId}", this::getSession);
  }

  private void createSession(Context context) {
    CreateSessionRequest request = context.body().isBlank()
      ? new CreateSessionRequest(null)
      : context.bodyAsClass(CreateSessionRequest.class);
    SessionRecord session = sessionService.create(request.title());
    cookieService.write(context, session.sessionId());
    context.status(HttpStatus.CREATED).json(SessionResponse.from(session));
  }

  private void getCurrentSession(Context context) {
    String sessionId = cookieService.read(context);
    if (sessionId == null || sessionId.isBlank()) {
      error(context, HttpStatus.NOT_FOUND, "session_not_found", "No active session cookie was provided.");
      return;
    }

    Optional<SessionRecord> session = sessionService.findActive(sessionId);
    if (session.isEmpty()) {
      cookieService.clear(context);
      error(context, HttpStatus.GONE, "session_gone", "The active session has expired or was deleted.");
      return;
    }

    cookieService.write(context, sessionId);
    context.json(SessionResponse.from(session.get()));
  }

  private void getSession(Context context) {
    String sessionId = context.pathParam("sessionId");
    Optional<SessionRecord> session = sessionService.findActive(sessionId);
    if (session.isEmpty()) {
      error(context, HttpStatus.NOT_FOUND, "session_not_found", "Session was not found.");
      return;
    }
    context.json(SessionResponse.from(session.get()));
  }

  private void error(Context context, HttpStatus status, String code, String message) {
    context.status(status).json(new ErrorResponse(code, message));
  }
}
