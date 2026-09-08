@import nl.llm.storyteller.api.web.StorySettingsPage
@param StorySettingsPage page

<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Story settings</title>
    <link rel="stylesheet" href="/storyteller.css">
  </head>
  <body>
    <main class="settings-page">
      <header class="story-header">
        <div>
          <p class="eyebrow">Storyteller</p>
          <h1>Story settings</h1>
        </div>
        <a class="header-action" href="/story">Back to story</a>
      </header>

      <form method="post" action="/story/settings" class="panel settings-form">
        <p class="intro">
          These prompts belong to this session and are used for subsequent story turns and validation.
        </p>

        <label for="systemPrompt">System prompt</label>
        <textarea id="systemPrompt" name="systemPrompt" rows="12" maxlength="1000000"
                  required>${page.prompts().systemPrompt()}</textarea>

        <label for="fixedProtagonists">Fixed protagonists (YAML)</label>
        @if(page.hasYamlError())
          <p id="fixed-protagonists-error" class="settings-error" role="alert"
             data-line="${page.yamlErrorLine()}" data-column="${page.yamlErrorColumn()}">
            Invalid YAML at line ${page.yamlErrorLine()}, column ${page.yamlErrorColumn()}: ${page.yamlError()}
          </p>
        @endif
        <textarea id="fixedProtagonists" name="fixedProtagonists" rows="12" maxlength="1000000"
                  aria-invalid="${page.hasYamlError()}"
                  aria-describedby="${page.hasYamlError() ? "fixed-protagonists-error" : null}"
                  required>${page.prompts().fixedProtagonists()}</textarea>

        <label for="rules">Rules</label>
        <textarea id="rules" name="rules" rows="12" maxlength="1000000"
                  required>${page.prompts().rules()}</textarea>

        <div class="settings-actions">
          <a class="header-action" href="/story">Cancel</a>
          <button type="submit">Save settings</button>
        </div>
      </form>
    </main>
    @if(page.hasYamlError())
      <script>
        const yamlEditor = document.getElementById("fixedProtagonists");
        const yamlError = document.getElementById("fixed-protagonists-error");
        const targetLine = Number(yamlError.dataset.line);
        const targetColumn = Number(yamlError.dataset.column);
        const lines = yamlEditor.value.split("\n");
        let offset = 0;
        for (let line = 1; line < targetLine && line <= lines.length; line += 1) {
          offset += lines[line - 1].length + 1;
        }
        const position = Math.min(offset + Math.max(0, targetColumn - 1), yamlEditor.value.length);
        yamlEditor.focus();
        yamlEditor.setSelectionRange(position, Math.min(position + 1, yamlEditor.value.length));
      </script>
    @endif
  </body>
</html>
