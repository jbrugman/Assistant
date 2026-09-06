@import nl.llm.storyteller.api.web.StoryPage
@import nl.llm.storyteller.api.web.StoryExchange
@param StoryPage page

<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${page.session().title() == null ? "Storyteller" : page.session().title()}</title>
    <link rel="stylesheet" href="/storyteller.css">
  </head>
  <body>
    <main id="story-page" class="story-page">
      <header class="story-header">
        <div>
          <p class="eyebrow">Storyteller</p>
          <h1>${page.session().title() == null ? "Untitled story" : page.session().title()}</h1>
        </div>
        <div class="session-actions">
          <span class="session-status">Session active</span>
          <form method="post" action="/story/stop"
                onsubmit="return confirm('Stop this story? The session and all stored story data will be permanently deleted.')">
            <button type="submit" class="stop-story">Stop story</button>
          </form>
        </div>
      </header>

      <section id="conversation" class="conversation" aria-live="polite">
        <div class="conversation-heading">
          <span>Prompt</span>
          <span class="response-heading">
            <span class="response-label">Response</span>
            <span class="heading-actions">
              <button id="response-layout-toggle" type="button" aria-pressed="false">Single</button>
              <button id="response-width-toggle" type="button" aria-pressed="false">Maximize</button>
            </span>
          </span>
        </div>
        @if(page.exchanges().isEmpty())
          <div class="empty-state">
            <h2>Begin the story</h2>
            <p>Describe the opening scene or tell the storyteller what should happen next.</p>
          </div>
        @endif
        @for(StoryExchange exchange : page.exchanges())
          <article class="exchange">
            <div class="exchange-prompt">
              <p class="message-role">Prompt</p>
              <div class="message-content">${exchange.prompt()}</div>
            </div>
            <div class="exchange-response">
              <p class="message-role">Response</p>
              <div class="message-content">${exchange.response()}</div>
            </div>
          </article>
        @endfor
      </section>

      <form id="composer" method="post" action="/story/turns" class="composer">
        <label for="prompt">What happens next?</label>
        <textarea id="prompt" name="prompt" rows="3" maxlength="100000" required autofocus></textarea>
        <button type="submit">Continue story</button>
      </form>
    </main>
    <script>
      const conversation = document.getElementById("conversation");
      const latestExchange = conversation.querySelector(".exchange:last-child");
      const heading = conversation.querySelector(".conversation-heading");
      const scrollToLatestExchange = () => {
        if (!latestExchange) {
          return;
        }
        const latestTop = latestExchange.getBoundingClientRect().top
          - conversation.getBoundingClientRect().top
          + conversation.scrollTop
          - heading.offsetHeight;
        conversation.scrollTop = latestTop;
      };
      scrollToLatestExchange();

      const responseWidthToggle = document.getElementById("response-width-toggle");
      const responseLayoutToggle = document.getElementById("response-layout-toggle");
      const storyPage = document.getElementById("story-page");
      responseLayoutToggle.addEventListener("click", () => {
        const singleColumn = storyPage.classList.toggle("single-column");
        responseLayoutToggle.textContent = singleColumn ? "Dual" : "Single";
        responseLayoutToggle.setAttribute("aria-pressed", singleColumn);
        requestAnimationFrame(scrollToLatestExchange);
      });

      responseWidthToggle.addEventListener("click", () => {
        const maximized = storyPage.classList.toggle("response-maximized");
        responseWidthToggle.textContent = maximized ? "Normal" : "Maximize";
        responseWidthToggle.setAttribute("aria-pressed", maximized);
        requestAnimationFrame(scrollToLatestExchange);
      });

      const composer = document.getElementById("composer");
      const prompt = document.getElementById("prompt");
      const submitButton = composer.querySelector("button[type='submit']");
      let submitting = false;

      composer.addEventListener("submit", event => {
        if (submitting) {
          event.preventDefault();
          return;
        }
        submitting = true;
        composer.setAttribute("aria-busy", "true");
        prompt.readOnly = true;
        submitButton.disabled = true;
        submitButton.textContent = "Writing…";
      });

      prompt.addEventListener("keydown", event => {
        if (event.key === "Enter" && event.shiftKey) {
          event.preventDefault();
          composer.requestSubmit();
        }
      });
    </script>
  </body>
</html>
