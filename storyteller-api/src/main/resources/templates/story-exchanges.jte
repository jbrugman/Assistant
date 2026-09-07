@import java.util.List
@import nl.llm.storyteller.api.web.StoryExchange
@param List<StoryExchange> exchanges

@for(StoryExchange exchange : exchanges)
  <article class="exchange" data-message-index="${exchange.messageIndex()}">
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
