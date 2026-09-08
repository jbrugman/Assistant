@import nl.llm.storyteller.api.web.StoryMemoryPage

@param StoryMemoryPage page

<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Story history</title>
    <link rel="stylesheet" href="/storyteller.css">
  </head>
  <body>
    <main class="settings-page">
      <header class="story-header">
        <div>
          <p class="eyebrow">Storyteller</p>
          <h1>Story history</h1>
        </div>
        <a class="header-action" href="/story">Back to story</a>
      </header>

      <section class="panel settings-form">
        <p class="intro">
          These read-only summaries are generated from older story turns and supplied to subsequent model requests.
        </p>

        <label for="recentSummary">Mid-term history · processed messages: ${page.memory().recentSummaryCursor()}</label>
        <textarea id="recentSummary" rows="12" readonly
                  placeholder="No mid-term history has been generated yet.">${page.memory().recentSummary()}</textarea>

        <label for="summary">Long-term history · processed messages: ${page.memory().summaryCursor()}</label>
        <textarea id="summary" rows="16" readonly
                  placeholder="No long-term history has been generated yet.">${page.memory().summary()}</textarea>

        <label for="canonicalState">Canonical state · processed messages: ${page.memory().canonicalStateCursor()}</label>
        <textarea id="canonicalState" rows="16" readonly
                  placeholder="No canonical state has been generated yet.">${page.memory().canonicalState()}</textarea>
      </section>
    </main>
  </body>
</html>
