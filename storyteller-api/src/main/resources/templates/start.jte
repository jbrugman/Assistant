<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Storyteller</title>
    <link rel="stylesheet" href="/storyteller.css">
  </head>
  <body>
    <main class="start-page">
      <section class="panel start-panel">
        <p class="eyebrow">Local AI storytelling</p>
        <h1>Storyteller</h1>
        <p class="intro">Create a session and start writing with the model configured on this server.</p>
        <form method="post" action="/web/sessions" class="stack">
          <label for="title">Story title <span>(optional)</span></label>
          <input id="title" name="title" maxlength="255" autocomplete="off" autofocus>
          <button type="submit">Start a story</button>
        </form>
        <div class="start-divider"><span>or</span></div>
        <form method="post" action="/import" enctype="multipart/form-data" class="stack import-form">
          <label for="bundle">Import CLI session <span>(ZIP)</span></label>
          <input id="bundle" name="bundle" type="file" accept=".zip,application/zip" required>
          <button type="submit" class="secondary-action">Import session</button>
        </form>
      </section>
    </main>
  </body>
</html>
