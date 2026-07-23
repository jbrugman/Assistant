package nl.jbrugman.assistant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

final class SummaryManager {
    private final HistoryStore historyStore;
    private final LMStudioClient client;
    private final AppConfig config;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object lock = new Object();

    SummaryManager(HistoryStore historyStore, LMStudioClient client, AppConfig config) {
        this.historyStore = historyStore;
        this.client = client;
        this.config = config;
        this.executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
    }

    String loadSummary() {
        return FileSupport.readTextFile(config.summaryFile(), "");
    }

    void startUpdateSummaryIfNeeded() {
        synchronized (lock) {
            if (running.get()) {
                return;
            }

            SummaryJob job = prepareJob();
            if (job == null) {
                return;
            }

            running.set(true);
            executor.submit(() -> runSummaryJob(job));
        }
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private SummaryJob prepareJob() {
        HistoryState state = historyStore.load();
        List<Message> recent = historyStore.recentMessages(config.maxRecentTurns());
        int cutoffIndex = state.messages().size() - recent.size();
        int cursor = Math.max(0, Math.min(state.summaryCursor(), state.messages().size()));

        if (cutoffIndex <= cursor) {
            return null;
        }

        List<Message> pendingMessages = new ArrayList<>(state.messages().subList(cursor, cutoffIndex));
        if (pendingMessages.size() < config.summaryBatchMessages()) {
            return null;
        }

        return new SummaryJob(cursor, cutoffIndex, loadSummary(), pendingMessages);
    }

    private void runSummaryJob(SummaryJob job) {
        try {
            List<Message> summaryPrompt = buildSummaryMessages(job.existingSummary(), job.pendingMessages());
            String newSummary = client.chat(
                summaryPrompt,
                config.summaryOptions(),
                config.summaryRequestTimeoutSeconds()
            );

            synchronized (lock) {
                HistoryState currentState = historyStore.load();
                int currentCursor = Math.max(0, Math.min(currentState.summaryCursor(), currentState.messages().size()));
                if (currentCursor != job.cursor()) {
                    return;
                }

                FileSupport.writeTextFile(config.summaryFile(), newSummary);
                historyStore.markSummarized(job.cutoffIndex());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException ex) {
            ignoreSummaryFailure();
        } finally {
            running.set(false);
        }
    }

    private void ignoreSummaryFailure() {
        // Summary refresh is best-effort and must never interrupt the main chat flow.
    }

    private List<Message> buildSummaryMessages(String existingSummary, List<Message> pendingMessages) {
        StringBuilder formattedHistory = new StringBuilder();
        for (Message message : pendingMessages) {
            if (!formattedHistory.isEmpty()) {
                formattedHistory.append('\n');
            }
            formattedHistory.append(message.role().toUpperCase()).append(": ").append(message.content());
        }

        String currentSummary = (existingSummary == null || existingSummary.isBlank())
            ? "Nog geen samenvatting."
            : existingSummary;

        String summaryInstruction = switch (config.appMode()) {
            case "story" -> storySummaryInstruction();
            case "default" -> defaultSummaryInstruction();
            default -> throw new IllegalStateException("Onbekende appmode: " + config.appMode());
        };

        return List.of(
            new Message(
                "system",
                summaryInstruction
            ),
            new Message(
                "user",
                "Bestaande summary:\n" + currentSummary + "\n\n"
                    + "Nieuwe oudere berichten om te verwerken:\n" + formattedHistory
            )
        );
    }

    private String defaultSummaryInstruction() {
        return "Je onderhoudt een duurzame geheugen-samenvatting voor een assistent. "
            + "Werk de bestaande samenvatting bij met alleen de context uit het oudere gesprek die later nog nodig kan zijn. "
            + "Neem alleen op: blijvende voorkeuren, belangrijke besluiten, openstaande issues en relevante technische context. "
            + "Laat weg: begroetingen, voorbeelden, tijdelijke details, kleine herhaling en overige ruis. "
            + "De summary is belangrijker dan een grote context, dus bewaar liever de juiste kern dan veel losse tekst. "
            + "Geef een compacte markdown-samenvatting terug van maximaal 15 bullets. "
            + "Geef alleen de nieuwe volledige summary terug in markdown.";
    }

    private String storySummaryInstruction() {
        return "Je onderhoudt een duurzame geheugen-samenvatting voor een verhalenverteller. "
            + "Werk de bestaande samenvatting bij met alleen de verhaalcontext uit het oudere gesprek die later nog nodig is. "
            + "De summary is belangrijker dan een grote context, dus leg de verhaaltoestand betrouwbaar vast ook als dat compacter moet dan de ruwe history. "
            + "Gebruik altijd precies deze markdown-secties: `## Huidige situatie`, `## Blijvende achtergrond`, `## Open verhaallijnen`, `## Canonical State`, `## Vervaagde details`. "
            + "In `Huidige situatie` bewaar je alleen de actuele toestand: huidige locatie, huidige situatie, aanwezige personages, direct doel, toon en relevante recente ontwikkeling. "
            + "In `Blijvende achtergrond` bewaar je alleen zaken die lang moeten blijven bestaan, zoals trauma, geschiedenis, karaktereigenschappen, relaties, wereldregels, motieven en andere diepe oorzaken of blijvende gevolgen. "
            + "In `Open verhaallijnen` bewaar je alleen lopende conflicten, mysteries, beloften, risico's en nog niet afgeronde ontwikkelingen. "
            + "In `Canonical State` geef je altijd een compacte YAML-state terug van de actuele canon. "
            + "Gebruik daar altijd de top-level sleutels `world`, `characters`, `relationships`, `active_threads` en `story_mode`. "
            + "Onder `characters` houd je per relevant personage de actuele status bij, zoals `alive`, `injured`, `unconscious`, `location` en eventueel `inventory`, maar alleen als die informatie echt canoniek of momenteel relevant is. "
            + "Onder `relationships` noteer je alleen stabiele of momenteel bepalende relaties tussen relevante personages. "
            + "Onder `active_threads` noteer je een korte lijst met actuele doelen, mysteries, dreigingen of open conflicten. "
            + "Zet in `story_mode` altijd `reality`, tenzij het verhaal expliciet iets anders heeft vastgesteld. "
            + "Als een detail onzeker, tegenstrijdig of niet bevestigd is, zet het niet als feit in de YAML maar laat het terugkomen in `Open verhaallijnen` of `Vervaagde details`. "
            + "In `Vervaagde details` noteer je hooguit enkele korte punten over oudere details die nog misschien nuttig zijn, maar minder belangrijk zijn geworden. "
            + "Als personages ergens verblijven, reizen, op vakantie zijn of zich naar een nieuwe plek verplaatsen, moet dat in `Huidige situatie` blijven staan totdat het verhaal dat echt verandert. "
            + "Details mogen in de loop der tijd compacter en abstracter worden, maar blijvende achtergrond mag niet verdwijnen alleen omdat die ouder is. "
            + "Laat weg: begroetingen, losse zijpaden, tijdelijke formuleringen, dubbele informatie en overige ruis. "
            + "Schrijf compact in markdown en houd elke sectie zo kort mogelijk zonder belangrijke continuiteit te verliezen. "
            + "Geef alleen de nieuwe volledige summary terug in markdown.";
    }

    private record SummaryJob(int cursor, int cutoffIndex, String existingSummary, List<Message> pendingMessages) {}

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "summary-worker");
            thread.setDaemon(true);
            return thread;
        }
    }
}
