package nl.llm.storyteller.api.web;

import io.javalin.config.JavalinConfig;
import io.javalin.config.SizeUnit;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;
import nl.llm.storyteller.api.bundle.SessionBundleService;
import nl.llm.storyteller.db.SessionPrompts;
import nl.llm.storyteller.db.SessionMemoryRepository;
import nl.llm.storyteller.db.SessionRecord;
import nl.llm.storyteller.db.StoryImage;
import nl.llm.storyteller.db.StoryRepository;
import nl.llm.storyteller.api.session.InvalidFixedProtagonistsException;
import nl.llm.storyteller.api.session.InvalidKnowledgeGraphException;
import nl.llm.storyteller.api.session.SessionCookieService;
import nl.llm.storyteller.api.session.SessionSettingsService;
import nl.llm.storyteller.api.session.SessionService;
import nl.llm.storyteller.api.story.StoryTurnService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WebController {
  private static final int STORY_PAGE_MESSAGES = 10;
  private final SessionService sessionService;
  private final SessionCookieService cookieService;
  private final StoryRepository storyRepository;
  private final StoryTurnService storyTurnService;
  private final SessionBundleService bundleService;
  private final SessionSettingsService settingsService;
  private final SessionMemoryRepository memoryRepository;

  public WebController(
    SessionService sessionService,
    SessionCookieService cookieService,
    StoryRepository storyRepository,
    StoryTurnService storyTurnService,
    SessionBundleService bundleService,
    SessionSettingsService settingsService,
    SessionMemoryRepository memoryRepository
  ) {
    this.sessionService = sessionService;
    this.cookieService = cookieService;
    this.storyRepository = storyRepository;
    this.storyTurnService = storyTurnService;
    this.bundleService = bundleService;
    this.settingsService = settingsService;
    this.memoryRepository = memoryRepository;
  }

  public void register(JavalinConfig config) {
    config.routes.get("/", this::home);
    config.routes.post("/web/sessions", this::createSession);
    config.routes.post("/web/sessions/resume", this::resumeSession);
    config.routes.get("/story/resume/{sessionId}", this::resumeSession);
    config.routes.post("/import", this::importSession);
    config.routes.get("/export", this::exportSession);
    config.routes.get("/story", this::story);
    config.routes.get("/story/history", this::storyHistory);
    config.routes.get("/story/images/{messageIndex}", this::storyImage);
    config.routes.get("/story/settings", this::settings);
    config.routes.post("/story/settings", this::saveSettings);
    config.routes.get("/story/memory", this::memory);
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

  private void resumeSession(Context context) {
    String sessionId = context.pathParamMap().containsKey("sessionId")
      ? context.pathParam("sessionId")
      : context.formParam("sessionId");
    SessionRecord session = sessionService.resumeInfinite(sessionId)
      .orElseThrow(() -> new IllegalArgumentException("Infinite session not found."));
    cookieService.write(context, session.sessionId(), true);
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
      "page", storyPage(session.get(), Integer.MAX_VALUE)
    ));
  }

  private void storyHistory(Context context) {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      context.status(HttpStatus.UNAUTHORIZED);
      return;
    }
    int before = parseBeforeMessageIndex(context.queryParam("before"));
    StoryPage page = storyPage(session.get(), before);
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
    var settings = settingsService.load(session.get().sessionId());
    context.render("settings.jte", Map.of(
      "page", StorySettingsPage.valid(settings.prompts(), settingsService.formatKnowledgeGraph(settings))
    ));
  }

  private void memory(Context context) {
    Optional<SessionRecord> session = activeSession(context);
    if (session.isEmpty()) {
      redirectToStart(context);
      return;
    }
    cookieService.write(context, session.get().sessionId(), session.get().infinite());
    context.render("memory.jte", Map.of(
      "page", new StoryMemoryPage(memoryRepository.load(session.get().sessionId()))
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
    String knowledgeGraph = context.formParam("knowledgeGraph");
    SessionPrompts submittedPrompts = new SessionPrompts(
      valueOrEmpty(systemPrompt), valueOrEmpty(fixedProtagonists), valueOrEmpty(rules)
    );
    try {
      settingsService.save(session.get().sessionId(), systemPrompt, fixedProtagonists, rules, knowledgeGraph);
    } catch (InvalidFixedProtagonistsException ex) {
      context.render("settings.jte", Map.of("page", StorySettingsPage.invalidYaml(
        submittedPrompts,
        valueOrEmpty(knowledgeGraph),
        ex
      )));
      return;
    } catch (InvalidKnowledgeGraphException ex) {
      context.render("settings.jte", Map.of("page", StorySettingsPage.invalidKnowledgeGraph(
        submittedPrompts,
        valueOrEmpty(knowledgeGraph),
        ex
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
    String prompt = context.formParam("prompt");
    try {
      storyTurnService.execute(
        session.get().sessionId(),
        prompt,
        image,
        pastMessageIndexes(context.formParams("pastExchange"))
      );
    } catch (IOException ex) {
      cookieService.write(context, session.get().sessionId(), session.get().infinite());
      context.status(HttpStatus.SERVICE_UNAVAILABLE);
      context.render("story.jte", Map.of(
        "page", storyPage(session.get(), Integer.MAX_VALUE).withBackendError(prompt)
      ));
      return;
    }
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

  private StoryPage storyPage(SessionRecord session, int beforeMessageIndex) {
    return StoryPage.from(
      session,
      storyRepository.loadMessagesBefore(session.sessionId(), beforeMessageIndex, STORY_PAGE_MESSAGES),
      storyTurnService.referenceBeforeMessageIndex(session.sessionId())
    );
  }

  private List<Integer> pastMessageIndexes(List<String> values) {
    if (values.size() > StoryTurnService.MAX_PAST_EXCHANGES) {
      throw new IllegalArgumentException("Select at most three past exchanges.");
    }
    try {
      return values.stream().map(Integer::valueOf).toList();
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Past exchange indexes must be integers.", ex);
    }
  }

  private void redirectToStart(Context context) {
    cookieService.clear(context);
    context.redirect("/", HttpStatus.SEE_OTHER);
  }
}
