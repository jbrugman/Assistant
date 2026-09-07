package nl.llm.storyteller.api.web;

import io.javalin.config.JavalinConfig;
import io.javalin.config.SizeUnit;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;
import nl.llm.storyteller.api.bundle.SessionBundleService;
import nl.llm.storyteller.api.persistence.SessionRecord;
import nl.llm.storyteller.api.persistence.StoryRepository;
import nl.llm.storyteller.api.session.SessionCookieService;
import nl.llm.storyteller.api.session.SessionService;
import nl.llm.storyteller.api.story.StoryTurnService;

import java.util.Map;
import java.util.Optional;

public final class WebController {
  private static final int STORY_PAGE_MESSAGES = 10;
  private final SessionService sessionService;
  private final SessionCookieService cookieService;
  private final StoryRepository storyRepository;
  private final StoryTurnService storyTurnService;
  private final SessionBundleService bundleService;

  public WebController(
    SessionService sessionService,
    SessionCookieService cookieService,
    StoryRepository storyRepository,
    StoryTurnService storyTurnService,
    SessionBundleService bundleService
  ) {
    this.sessionService = sessionService;
    this.cookieService = cookieService;
    this.storyRepository = storyRepository;
    this.storyTurnService = storyTurnService;
    this.bundleService = bundleService;
  }

  public void register(JavalinConfig config) {
    config.routes.get("/", this::home);
    config.routes.post("/web/sessions", this::createSession);
    config.routes.post("/import", this::importSession);
    config.routes.get("/export", this::exportSession);
    config.routes.get("/story", this::story);
    config.routes.get("/story/history", this::storyHistory);
    config.routes.post("/story/turns", this::createTurn);
    config.routes.post("/story/undo", this::undoTurn);
    config.routes.post("/story/infinite", this::toggleInfinite);
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
    cookieService.write(context, session.sessionId(), session.infinite());
    context.redirect("/story", HttpStatus.SEE_OTHER);
  }

  private void importSession(Context context) throws Exception {
    context.multipartConfig().maxFileSize(SessionBundleService.MAX_ARCHIVE_BYTES, SizeUnit.BYTES);
    context.multipartConfig().maxTotalRequestSize(
      SessionBundleService.MAX_ARCHIVE_BYTES + 1024 * 1024,
      SizeUnit.BYTES
    );
    UploadedFile upload = context.uploadedFile("bundle");
    if (upload == null) {
      throw new IllegalArgumentException("Select a session ZIP to import.");
    }
    SessionRecord session;
    try (var input = upload.content()) {
      session = bundleService.importArchive(input, upload.size(), upload.filename());
    }
    cookieService.write(context, session.sessionId(), session.infinite());
    context.redirect("/story", HttpStatus.SEE_OTHER);
  }

  private void exportSession(Context context) throws Exception {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      redirectToStart(context);
      return;
    }
    byte[] archive = bundleService.exportArchive(session.get().sessionId(), session.get().title());
    cookieService.write(context, session.get().sessionId(), session.get().infinite());
    context.contentType("application/zip");
    context.header(
      "Content-Disposition",
      "attachment; filename=\"story-session-" + session.get().sessionId() + ".zip\""
    );
    context.result(archive);
  }

  private void story(Context context) {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      redirectToStart(context);
      return;
    }
    cookieService.write(context, session.get().sessionId(), session.get().infinite());
    context.render("story.jte", Map.of(
      "page", StoryPage.from(session.get(), storyRepository.loadMessagesBefore(
        session.get().sessionId(), Integer.MAX_VALUE, STORY_PAGE_MESSAGES
      ))
    ));
  }

  private void storyHistory(Context context) {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      context.status(HttpStatus.UNAUTHORIZED);
      return;
    }
    int before = parseBeforeMessageIndex(context.queryParam("before"));
    StoryPage page = StoryPage.from(session.get(), storyRepository.loadMessagesBefore(
      session.get().sessionId(), before, STORY_PAGE_MESSAGES
    ));
    int oldest = page.exchanges().isEmpty() ? 0 : page.exchanges().getFirst().messageIndex();
    context.header("X-Story-Has-More", Boolean.toString(page.hasOlder()));
    context.header("X-Story-Oldest-Message", Integer.toString(oldest));
    cookieService.write(context, session.get().sessionId(), session.get().infinite());
    context.render("story-exchanges.jte", Map.of("exchanges", page.exchanges()));
  }

  private int parseBeforeMessageIndex(String value) {
    try {
      int before = Integer.parseInt(value == null ? "" : value);
      if (before < 1) {
        throw new NumberFormatException();
      }
      return before;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Query parameter 'before' must be a positive message index.", ex);
    }
  }

  private void createTurn(Context context) throws Exception {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      redirectToStart(context);
      return;
    }
    storyTurnService.execute(session.get().sessionId(), context.formParam("prompt"));
    cookieService.write(context, session.get().sessionId(), session.get().infinite());
    context.redirect("/story", HttpStatus.SEE_OTHER);
  }

  private void toggleInfinite(Context context) {
    Optional<SessionRecord> session = sessionService.toggleInfinite(cookieService.read(context));
    if (session.isEmpty()) {
      redirectToStart(context);
      return;
    }
    cookieService.write(context, session.get().sessionId(), session.get().infinite());
    context.redirect("/story", HttpStatus.SEE_OTHER);
  }

  private void undoTurn(Context context) {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      redirectToStart(context);
      return;
    }
    storyTurnService.undoLastTurn(session.get().sessionId());
    cookieService.write(context, session.get().sessionId(), session.get().infinite());
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
