package gg.jte.generated.ondemand.templates;
import nl.llm.storyteller.api.web.StoryPage;
import nl.llm.storyteller.api.web.StoryExchange;
@SuppressWarnings("unchecked")
public final class JtestoryGenerated {
	public static final String JTE_NAME = "templates/story.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,2,2,2,9,9,9,9,17,17,17,27,27,32,32,33,33,37,37,37,41,41,41,44,44,55,55,55,2,2,2,2};
	public static void render(gg.jte.html.HtmlTemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, StoryPage page) {
		jteOutput.writeContent("\n<!doctype html>\n<html lang=\"en\">\n  <head>\n    <meta charset=\"utf-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n    <title>");
		jteOutput.setContext("title", null);
		jteOutput.writeUserContent(page.session().title() == null ? "Storyteller" : page.session().title());
		jteOutput.writeContent("</title>\n    <link rel=\"stylesheet\" href=\"/storyteller.css\">\n  </head>\n  <body>\n    <main class=\"story-page\">\n      <header class=\"story-header\">\n        <div>\n          <p class=\"eyebrow\">Storyteller</p>\n          <h1>");
		jteOutput.setContext("h1", null);
		jteOutput.writeUserContent(page.session().title() == null ? "Untitled story" : page.session().title());
		jteOutput.writeContent("</h1>\n        </div>\n        <span class=\"session-status\">Session active</span>\n      </header>\n\n      <section class=\"conversation\" aria-live=\"polite\">\n        <div class=\"conversation-heading\" aria-hidden=\"true\">\n          <span>Prompt</span>\n          <span>Response</span>\n        </div>\n        ");
		if (page.exchanges().isEmpty()) {
			jteOutput.writeContent("\n          <div class=\"empty-state\">\n            <h2>Begin the story</h2>\n            <p>Describe the opening scene or tell the storyteller what should happen next.</p>\n          </div>\n        ");
		}
		jteOutput.writeContent("\n        ");
		for (StoryExchange exchange : page.exchanges()) {
			jteOutput.writeContent("\n          <article class=\"exchange\">\n            <div class=\"exchange-prompt\">\n              <p class=\"message-role\">Prompt</p>\n              <div class=\"message-content\">");
			jteOutput.setContext("div", null);
			jteOutput.writeUserContent(exchange.prompt());
			jteOutput.writeContent("</div>\n            </div>\n            <div class=\"exchange-response\">\n              <p class=\"message-role\">Response</p>\n              <div class=\"message-content\">");
			jteOutput.setContext("div", null);
			jteOutput.writeUserContent(exchange.response());
			jteOutput.writeContent("</div>\n            </div>\n          </article>\n        ");
		}
		jteOutput.writeContent("\n      </section>\n\n      <form method=\"post\" action=\"/story/turns\" class=\"composer\">\n        <label for=\"prompt\">What happens next?</label>\n        <textarea id=\"prompt\" name=\"prompt\" rows=\"4\" maxlength=\"100000\" required autofocus></textarea>\n        <button type=\"submit\">Continue story</button>\n      </form>\n    </main>\n  </body>\n</html>\n");
	}
	public static void renderMap(gg.jte.html.HtmlTemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		StoryPage page = (StoryPage)params.get("page");
		render(jteOutput, jteHtmlInterceptor, page);
	}
}
