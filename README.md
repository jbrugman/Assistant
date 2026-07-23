# Assistant

Kleine lokale CLI-assistant in Java die praat met LM Studio via het OpenAI-achtige endpoint op `http://localhost:1234`.

## Vereisten

- Java 17+
- Maven
- LM Studio draaiend met een geladen chat/instruct-model

## Lokaal draaien

### Aanbevolen

Maak eerst een runnable jar en start daarna direct met `java -jar`:

```bash
cd /Users/jbrugman/Assistant
mvn -q package
java -jar target/assistant-1.0.0-SNAPSHOT.jar
```

Dit geeft meestal het beste interactieve terminalgedrag.

## Native build (GraalVM)

Deze app is ook voorbereid voor een native build met GraalVM.

### Vereisten

- GraalVM met `native-image`
- Maven

Controleer eerst of `native-image` beschikbaar is:

```bash
native-image --version
```

### Native binary bouwen

```bash
cd /Users/jbrugman/Assistant
mvn -Pnative -DskipTests package
```

De binary komt daarna normaal terecht op:

```text
target/assistant
```

### Native binary draaien

Start hem vanuit de projectmap, zodat `assistant.properties`, `systemprompt.md`, `rules.md`, `summary.md` en `history.json` op de verwachte plek staan:

```bash
cd /Users/jbrugman/Assistant
./target/assistant
```

Als er naast de native executable een `application.config` staat, dan wordt die automatisch ingelezen als override op `assistant.properties`.

### Let op

De app leest zijn configuratie en promptbestanden standaard relatief vanuit de huidige werkdirectory.  
Dus ook als je een native binary hebt, moet je hem voor nu nog vanuit de projectroot starten, of die bestanden naast de juiste werkmap beschikbaar hebben.

### Snel via Maven

```bash
cd /Users/jbrugman/Assistant
mvn exec:java
```

### Alternatief tijdens ontwikkeling

Als je zonder jar direct vanaf classes wilt starten:

```bash
cd /Users/jbrugman/Assistant
mvn -q compile dependency:copy-dependencies
java -cp "target/classes:target/dependency/*" nl.jbrugman.assistant.AssistantApp
```

## Pijltjestoetsen in de terminal

Als je de app start via `mvn exec:java`, kunnen pijltjestoetsen soms rare tekens tonen zoals:

```text
^[[D
^[[C
```

Dat komt door de manier waarop de Maven exec-plugin stdin/terminal-interactie doorgeeft.  
Gebruik in dat geval liever de aanbevolen startmethode hierboven met directe `java`-startup.

## Bestanden

- `systemprompt.md`: system prompt
- `rules.md`: guardrails/regels voor de validator-check
- `summarysystemprompt.md`: instructies voor het bijwerken van de summary
- `canonicalstatesystemprompt.md`: instructies voor het bijwerken van de canonieke story-state
- `assistant.properties`: configuratie voor modellen, paden, timeouts en modelopties
- `summary.md`: samenvatting van oudere context
- `canonical-state.yaml`: actuele canonieke verhaaltoestand voor story-mode
- `history.json`: volledige chatgeschiedenis

De app stuurt niet de hele history naar het model, maar alleen de meest recente complete turns, plus de summary en in `story` mode ook de canonical state.

## Configuratie

De meeste hard-coded waarden zijn verplaatst naar `assistant.properties`, waaronder:

- LM Studio endpoint
- chat- en validator-model
- paden naar `systemprompt.md`, `rules.md`, `summary.md` en `history.json`
- paden naar `summarysystemprompt.md`, `canonicalstatesystemprompt.md` en `canonical-state.yaml`
- timeouts
- modelopties zoals `temperature`, `topP` en `repeatPenalty`

Zo kun je gedrag aanpassen zonder Java-code te wijzigen.

## Story-mode geheugen

In `story` appmode zijn er twee achtergrondprocessen voor langetermijncontext:

- `canonical-state.yaml` wordt onafhankelijk bijgewerkt op basis van oudere turns en is bedoeld voor actuele, bevestigde canonieke feiten.
- `summary.md` blijft een compactere samenvatting voor bredere context en blijvende achtergrond.

Standaard draait de canonical state 2x zo vaak als de summary via:

- `summary.batchMessages=10`
- `canonicalState.batchMessages=5`
