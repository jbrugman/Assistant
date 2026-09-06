package nl.llm.storyteller.api.session;

import io.javalin.http.Context;

import java.time.Duration;

public final class SessionCookieService {
  static final String COOKIE_NAME = "storyteller_session";
  private static final long INFINITE_MAX_AGE_SECONDS = Integer.MAX_VALUE;

  private final long maxAgeSeconds;

  public SessionCookieService(Duration inactivityTimeout) {
    maxAgeSeconds = inactivityTimeout.toSeconds();
  }

  public String read(Context context) {
    return context.cookie(COOKIE_NAME);
  }

  public void write(Context context, String sessionId) {
    context.header("Set-Cookie", cookieValue(sessionId, maxAgeSeconds));
  }

  public void write(Context context, String sessionId, boolean infinite) {
    context.header("Set-Cookie", cookieValue(
      sessionId,
      infinite ? INFINITE_MAX_AGE_SECONDS : maxAgeSeconds
    ));
  }

  public void clear(Context context) {
    context.header("Set-Cookie", cookieValue("", 0));
  }

  private String cookieValue(String value, long maxAge) {
    return COOKIE_NAME + "=" + value
      + "; Path=/; Max-Age=" + maxAge
      + "; HttpOnly; SameSite=Lax";
  }
}
