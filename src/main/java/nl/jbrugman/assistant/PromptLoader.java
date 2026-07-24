package nl.jbrugman.assistant;

final class PromptLoader {
    private static final String DEFAULT_SUMMARY_SYSTEM_PROMPT = """
        Je onderhoudt een duurzame geheugen-samenvatting voor een assistent.
        De appmode is `{{APP_MODE}}`.

        Als de appmode `default` is:
        - Werk de bestaande samenvatting bij met alleen de context uit het oudere gesprek die later nog nodig kan zijn.
        - Neem alleen op: blijvende voorkeuren, belangrijke besluiten, openstaande issues en relevante technische context.
        - Laat weg: begroetingen, voorbeelden, tijdelijke details, kleine herhaling en overige ruis.
        - Geef een compacte markdown-samenvatting terug van maximaal 15 bullets.

        Als de appmode `story` is:
        - Werk de bestaande samenvatting bij met alleen de verhaalcontext uit het oudere gesprek die later nog nodig is.
        - Gebruik altijd precies deze markdown-secties: `## Huidige situatie`, `## Blijvende achtergrond`, `## Open verhaallijnen`, `## Vervaagde details`.
        - In `Huidige situatie` bewaar je alleen de actuele toestand: huidige locatie, huidige situatie, aanwezige personages, direct doel, toon en relevante recente ontwikkeling.
        - In `Blijvende achtergrond` bewaar je alleen zaken die lang moeten blijven bestaan, zoals trauma, geschiedenis, karaktereigenschappen, relaties, wereldregels, motieven en andere diepe oorzaken of blijvende gevolgen.
        - In `Open verhaallijnen` bewaar je alleen lopende conflicten, mysteries, beloften, risico's en nog niet afgeronde ontwikkelingen.
        - In `Vervaagde details` noteer je hooguit enkele korte punten over oudere details die nog misschien nuttig zijn, maar minder belangrijk zijn geworden.
        - Als personages ergens verblijven, reizen, op vakantie zijn of zich naar een nieuwe plek verplaatsen, moet dat in `Huidige situatie` blijven staan totdat het verhaal dat echt verandert.
        - Details mogen in de loop der tijd compacter en abstracter worden, maar blijvende achtergrond mag niet verdwijnen alleen omdat die ouder is.

        Voor alle modi:
        - De summary is belangrijker dan een grote context, dus bewaar liever de juiste kern dan veel losse tekst.
        - Schrijf compact in markdown en houd elke sectie zo kort mogelijk zonder belangrijke continuiteit te verliezen.
        - Geef alleen de nieuwe volledige summary terug in markdown.
        """;
    private static final String DEFAULT_RECENT_SUMMARY_SYSTEM_PROMPT = """
        Je onderhoudt een compacte samenvatting van recente, maar niet allerlaatste gesprekscontext voor een assistent.
        De appmode is `{{APP_MODE}}`.

        Als de appmode `default` is:
        - Werk de bestaande recente samenvatting bij met alleen de recente context die nog waarschijnlijk direct relevant is.
        - Bewaar recente beslissingen, expliciete instructies, open TODO's, aannames, beperkingen en relevante technische details.
        - Laat weg: begroetingen, herhaling, uitgewerkte voorbeelden, irrelevante bijzinnen en details die alleen stijl of ruis waren.
        - Geef een compacte markdown-samenvatting terug van maximaal 10 bullets.

        Als de appmode `story` is:
        - Werk de bestaande recente samenvatting bij met alleen de recente verhaalcontext die nog nodig is net vóór de allerlaatste turns.
        - Gebruik altijd precies deze markdown-secties: `## Recente situatie`, `## Lopende instructies`, `## Details in reserve`.
        - In `Recente situatie` bewaar je de recente voortgang, locatie, betrokken personages, actuele spanning en directe aanleiding.
        - In `Lopende instructies` bewaar je recente expliciete wensen over vertelstijl, focus, perspectief, tempo en wat wel of niet moet gebeuren.
        - In `Details in reserve` bewaar je alleen enkele recente details die misschien nog nuttig zijn, maar niet belangrijk genoeg zijn voor de canonieke toestand.

        Voor alle modi:
        - Deze recente samenvatting is kortetermijncontext: compacter dan ruwe turns, maar concreter en actueler dan de gewone summary.
        - Herschrijf de volledige recente samenvatting steeds opnieuw op basis van de huidige recente vensterberichten; laat details weg die niet meer in dit venster thuishoren.
        - Behoud recente, expliciete beperkingen en afspraken zo letterlijk mogelijk in betekenis.
        - Schrijf compact in markdown en geef alleen de nieuwe volledige recente samenvatting terug.
        """;
    private static final String DEFAULT_CANONICAL_STATE_SYSTEM_PROMPT = """
        Je onderhoudt een canonieke verhaaltoestand voor een verhalenverteller.
        De appmode is `{{APP_MODE}}`.

        Als de appmode niet `story` is:
        - Geef een lege string terug.

        Als de appmode `story` is:
        - Werk de bestaande canonieke toestand bij met alleen de actuele, bevestigde verhaalfeiten uit het oudere gesprek.
        - Geef altijd uitsluitend compacte YAML terug, zonder markdown, zonder code fences en zonder uitleg.
        - Gebruik altijd de top-level sleutels `world`, `characters`, `relationships`, `active_threads` en `story_mode`.
        - Onder `world` noteer je alleen actuele, bevestigde wereldstatus zoals datum, weer, plek, tijd of andere direct relevante situatie.
        - Onder `characters` houd je per relevant personage de actuele status bij, zoals `alive`, `injured`, `unconscious`, `location` en eventueel `inventory`, maar alleen als die informatie bevestigd en momenteel relevant is.
        - Onder `relationships` noteer je alleen stabiele of momenteel bepalende relaties tussen relevante personages.
        - Onder `active_threads` noteer je een korte lijst met actuele doelen, mysteries, dreigingen of open conflicten.
        - Zet in `story_mode` altijd `reality`, tenzij het verhaal expliciet iets anders als canon heeft vastgesteld.
        - Als een detail onzeker, tegenstrijdig of niet bevestigd is, zet het niet als feit in de YAML.
        - Laat irrelevante, verouderde of onbevestigde details weg in plaats van te gokken.
        - Geef alleen de nieuwe volledige canonieke toestand terug als YAML.
        """;
    private final AppConfig config;

    PromptLoader(AppConfig config) {
        this.config = config;
    }

    String loadSystemPrompt() {
        return FileSupport.readTextFile(config.systemPromptFile(), "You are a helpful assistant.");
    }

    String loadRulesPrompt() {
        return FileSupport.readTextFile(config.rulesFile(), "You are a helpful assistant.");
    }

    String loadSummarySystemPrompt() {
        return withAppMode(
            FileSupport.readTextFile(config.summarySystemPromptFile(), DEFAULT_SUMMARY_SYSTEM_PROMPT)
        );
    }

    String loadRecentSummarySystemPrompt() {
        return withAppMode(
            FileSupport.readTextFile(config.recentSummarySystemPromptFile(), DEFAULT_RECENT_SUMMARY_SYSTEM_PROMPT)
        );
    }

    String loadCanonicalStateSystemPrompt() {
        return withAppMode(
            FileSupport.readTextFile(config.canonicalStateSystemPromptFile(), DEFAULT_CANONICAL_STATE_SYSTEM_PROMPT)
        );
    }

    private String withAppMode(String prompt) {
        return prompt.replace("{{APP_MODE}}", config.appMode());
    }
}
