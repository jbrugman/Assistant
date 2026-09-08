package nl.llm.storyteller.api.web;

import io.javalin.config.JavalinConfig;
import io.javalin.config.SizeUnit;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;
import nl.llm.storyteller.api.bundle.SessionBundleService;
import nl.llm.storyteller.api.persistence.SessionRecord;
import nl.llm.storyteller.api.persistence.SessionPrompts;
import nl.llm.storyteller.api.persistence.StoryImage;
import nl.llm.storyteller.api.persistence.StoryRepository;
import nl.llm.storyteller.api.session.InvalidFixedProtagonistsException;
import nl.llm.storyteller.api.session.SessionCookieService;
import nl.llm.storyteller.api.session.SessionPromptService;
import nl.llm.storyteller.api.session.SessionService;
import nl.llm.storyteller.api.story.StoryTurnService;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public final class WebController {
  private static final int STORY_PAGE_MESSAGES = 10;
  private final SessionService sessionService;
  private final SessionCookieService cookieService;
  private final StoryRepository storyRepository;
  private final StoryTurnService storyTurnService;
  private final SessionBundleService bundleService;
  private final SessionPromptService promptService;

  public WebController(
    SessionService sessionService,
    SessionCookieService cookieService,
    StoryRepository storyRepository,
    StoryTurnService storyTurnService,
    SessionBundleService bundleService,
    SessionPromptService promptService
  ) {
    this.sessionService = sessionService;
    this.cookieService = cookieService;
    this.storyRepository = storyRepository;
    this.storyTurnService = storyTurnService;
    this.bundleService = bundleService;
    this.promptService = promptService;
  }

  public void register(JavalinConfig config) {
    config.routes.get("/", this::home);
    config.routes.post("/web/sessions", this::createSession);
    config.routes.post("/import", this::importSession);
    config.routes.get("/export", this::exportSession);
    config.routes.get("/story", this::story);
    config.routes.get("/story/history", this::storyHistory);
    config.routes.get("/story/images/{messageIndex}", this::storyImage);
    config.routes.get("/story/settings", this::settings);
    config.routes.post("/story/settings", this::saveSettings);
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

  private void importSession(Context context) throws IOException {
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

  private void exportSession(Context context) throws IOException {
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

  private void storyImage(Context context) {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      context.status(HttpStatus.NOT_FOUND);
      return;
    }
    int messageIndex = parseMessageIndex(context.pathParam("messageIndex"));
    Optional<StoryImage> image = storyRepository.loadImage(session.get().sessionId(), messageIndex);
    if (image.isEmpty()) {
      context.status(HttpStatus.NOT_FOUND);
      return;
    }
    context.header("Cache-Control", "private, no-store");
    context.contentType(image.get().mediaType());
    context.result(image.get().content());
  }

  private void settings(Context context) {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      redirectToStart(context);
      return;
    }
    cookieService.write(context, session.get().sessionId(), session.get().infinite());
    context.render("settings.jte", Map.of(
      "page", StorySettingsPage.valid(promptService.load(session.get().sessionId()))
    ));
  }

  private void saveSettings(Context context) {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      redirectToStart(context);
      return;
    }
    String systemPrompt = context.formParam("systemPrompt");
    String fixedProtagonists = context.formParam("fixedProtagonists");
    String rules = context.formParam("rules");
    try {
      promptService.save(session.get().sessionId(), systemPrompt, fixedProtagonists, rules);
    } catch (InvalidFixedProtagonistsException ex) {
      context.render("settings.jte", Map.of("page", StorySettingsPage.invalid(
        new SessionPrompts(valueOrEmpty(systemPrompt), valueOrEmpty(fixedProtagonists), valueOrEmpty(rules)), ex
      )));
      return;
    }
    cookieService.write(context, session.get().sessionId(), session.get().infinite());
    context.redirect("/story", HttpStatus.SEE_OTHER);
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
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

  private void createTurn(Context context) throws IOException, InterruptedException {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      redirectToStart(context);
      return;
    }
    context.multipartConfig().maxFileSize(StoryImageUpload.MAX_BYTES, SizeUnit.BYTES);
    context.multipartConfig().maxTotalRequestSize(StoryImageUpload.MAX_BYTES + 1024 * 1024, SizeUnit.BYTES);
    StoryImage image = StoryImageUpload.read(context.uploadedFile("image"));
    storyTurnService.execute(session.get().sessionId(), context.formParam("prompt"), image);
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
