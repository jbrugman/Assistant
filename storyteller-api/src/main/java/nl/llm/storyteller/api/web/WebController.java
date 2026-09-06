package nl.llm.storyteller.api.web;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import nl.llm.storyteller.api.persistence.SessionRecord;
import nl.llm.storyteller.api.persistence.StoryRepository;
import nl.llm.storyteller.api.session.SessionCookieService;
import nl.llm.storyteller.api.session.SessionService;
import nl.llm.storyteller.api.story.StoryTurnService;

import java.util.Map;
import java.util.Optional;

public final class WebController {
  private final SessionService sessionService;
  private final SessionCookieService cookieService;
  private final StoryRepository storyRepository;
  private final StoryTurnService storyTurnService;

  public WebController(
    SessionService sessionService,
    SessionCookieService cookieService,
    StoryRepository storyRepository,
    StoryTurnService storyTurnService
  ) {
    this.sessionService = sessionService;
    this.cookieService = cookieService;
    this.storyRepository = storyRepository;
    this.storyTurnService = storyTurnService;
  }

  public void register(JavalinConfig config) {
    config.routes.get("/", this::home);
    config.routes.post("/web/sessions", this::createSession);
    config.routes.get("/story", this::story);
    config.routes.post("/story/turns", this::createTurn);
    config.routes.post("/story/stop", this::stopStory);
  }

  private void home(Context context) {
    if (activeSession(context).isPresent()) {
      context.redirect("/story", HttpStatus.SEE_OTHER);
      return;
    }
    context.render("start.jte");
  }

  private void createSession(Context context) {
    SessionRecord session = sessionService.create(context.formParam("title"));
    cookieService.write(context, session.sessionId());
    context.redirect("/story", HttpStatus.SEE_OTHER);
  }

  private void story(Context context) {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      redirectToStart(context);
      return;
    }
    cookieService.write(context, session.get().sessionId());
    context.render("story.jte", Map.of(
      "page", StoryPage.from(session.get(), storyRepository.loadMessages(session.get().sessionId()))
    ));
  }

  private void createTurn(Context context) throws Exception {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      redirectToStart(context);
      return;
    }
    storyTurnService.execute(session.get().sessionId(), context.formParam("prompt"));
    cookieService.write(context, session.get().sessionId());
    context.redirect("/story", HttpStatus.SEE_OTHER);
  }

  private void stopStory(Context context) {
    sessionService.delete(cookieService.read(context));
    cookieService.clear(context);
    context.redirect("/", HttpStatus.SEE_OTHER);
  }

  private Optional<SessionRecord> activeSession(Context context) {
    return sessionService.findActive(cookieService.read(context));
  }

  private void redirectToStart(Context context) {
    cookieService.clear(context);
    context.redirect("/", HttpStatus.SEE_OTHER);
  }
}
