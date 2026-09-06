package gg.jte.generated.ondemand.templates;
@SuppressWarnings("unchecked")
public final class JtestartGenerated {
	public static final String JTE_NAME = "templates/start.jte";
	public static final int[] JTE_LINE_INFO = {23,23,23,23,23,23,23,23,23,23,23,23};
	public static void render(gg.jte.html.HtmlTemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor) {
		jteOutput.writeContent("<!doctype html>\n<html lang=\"en\">\n  <head>\n    <meta charset=\"utf-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n    <title>Storyteller</title>\n    <link rel=\"stylesheet\" href=\"/storyteller.css\">\n  </head>\n  <body>\n    <main class=\"start-page\">\n      <section class=\"panel start-panel\">\n        <p class=\"eyebrow\">Local AI storytelling</p>\n        <h1>Storyteller</h1>\n        <p class=\"intro\">Create a session and start writing with the model configured on this server.</p>\n        <form method=\"post\" action=\"/web/sessions\" class=\"stack\">\n          <label for=\"title\">Story title <span>(optional)</span></label>\n          <input id=\"title\" name=\"title\" maxlength=\"255\" autocomplete=\"off\" autofocus>\n          <button type=\"submit\">Start a story</button>\n        </form>\n      </section>\n    </main>\n  </body>\n</html>\n");
	}
	public static void renderMap(gg.jte.html.HtmlTemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		render(jteOutput, jteHtmlInterceptor);
	}
}
