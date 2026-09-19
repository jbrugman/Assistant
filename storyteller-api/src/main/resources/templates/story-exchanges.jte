@import java.util.List
@import nl.llm.storyteller.api.web.StoryExchange

@param List<StoryExchange> exchanges

@for(StoryExchange exchange : exchanges)
  <article class="exchange" data-message-index="${exchange.messageIndex()}">
    <div class="exchange-prompt">
      <p class="message-role">Prompt</p>
      @if(exchange.referenceEligible())
        <label class="past-reference">
          <input type="checkbox" name="pastExchange" value="${exchange.messageIndex()}" form="composer">
          Use as past context
        </label>
      @endif
      @if(exchange.hasImage())
        <a class="prompt-image-link" href="/story/images/${exchange.messageIndex()}"
           aria-label="Show the full image supplied with this prompt">
          <img class="prompt-thumbnail" src="/story/images/${exchange.messageIndex()}"
               alt="Image supplied with this prompt" title="Show full image">
        </a>
      @endif
      <div class="message-content">${exchange.prompt()}</div>
    </div>
    <div class="exchange-response">
      <div class="response-title-row">
        <p class="message-role">Response</p>
        <details class="response-edit">
          <summary>Edit</summary>
          <form method="post" action="/story/messages/${exchange.messageIndex() + 1}/edit">
            <label for="response-edit-${exchange.messageIndex()}">Response text</label>
            <textarea id="response-edit-${exchange.messageIndex()}" name="content" rows="8"
                      maxlength="100000" required>${exchange.response()}</textarea>
            <button type="submit" class="compact-button">Save response</button>
          </form>
        </details>
      </div>
      <div class="message-content">${exchange.response()}</div>
    </div>
  </article>
@endfor
<script>
  document.querySelectorAll(".response-edit").forEach(details => {
    const summary = details.querySelector("summary");
    details.addEventListener("toggle", () => {
      summary.textContent = details.open ? "Cancel" : "Edit";
    });
  });
</script>
