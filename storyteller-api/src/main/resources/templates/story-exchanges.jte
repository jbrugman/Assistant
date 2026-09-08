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
      <p class="message-role">Response</p>
      <div class="message-content">${exchange.response()}</div>
    </div>
  </article>
@endfor
