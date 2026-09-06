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
