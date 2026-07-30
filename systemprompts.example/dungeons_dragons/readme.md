# Dungeons & Dragons Mode — Setup Guide

This example configuration turns the storyteller into a structured Dungeon Master for a tactical party-based fantasy adventure.

It is meant for play sessions where the model should manage scene progression, risks, inventory pressure, and character state without drifting into vague freeform roleplay.

## Overview

This example shows how to configure the storyteller for a rules-aware tabletop-inspired mode built around:

- a Dungeon Master style prompt in `systemprompt.md`
- explicit gameplay rules in `rules.md`
- fixed party and world data in `fixed_protagonists.yml`
- canonical state support in `canonicalstatesystemprompt.md`
- model and runtime defaults in `application.config`

The goal is not just to generate fantasy prose.
The goal is to create a playable DM-style loop with continuity, consequences, and bounded party state.
This example also demonstrates the optional engine-level turn-based rule tracking built into the app itself.

## What This Mode Is For

This mode is a good fit when you want the model to:

- run a tactical fantasy adventure
- track party status over multiple turns
- preserve explicit health and inventory state
- apply consequences to poor decisions
- keep scenarios coherent
- maintain clear action-and-outcome structure

This mode is especially useful for:

- solo D&D-style sessions
- party-based dungeon exploration
- puzzle and hazard driven adventures
- tactical roleplay with explicit consequences

## Included Files

### `systemprompt.md`

This prompt frames the model as a Dungeon Master rather than as a general storyteller.

It should encourage:

- scenario control
- consequence-driven narration
- bounded improvisation
- clear action resolution
- party-oriented storytelling

### `rules.md`

This file defines the hard guardrails for the mode.

It is where you should describe things such as:

- health limits
- inventory restrictions
- item depletion
- unconscious or dead states
- scenario or world restrictions

This file is especially important when the validator is enabled, because it helps reject invalid or continuity-breaking outcomes.

### `fixed_protagonists.yml`

This file stores the fixed party baseline.

It should define things such as:

- party members
- roles or classes
- health values
- inventories
- hard constraints
- durable character traits

Important detail:

- the top-level YAML key in this example is `fixed_protagonist`
- the same file also contains the `scenarios` list and game-mode world assumptions

This is the right place for the starting party, active scenario pool, and stable game rules, not for temporary combat outcomes.

### `canonicalstatesystemprompt.md`

This file supports canonical state updates.

It helps the app maintain a condensed world and party state over longer sessions.
That matters more in game-like modes, because tactical scenes create lots of evolving details that need to stay consistent.

### `application.config`

This file contains runtime override defaults for the mode.

It can define things such as:

- the chat model
- the validator model
- recent history sizing
- sampling settings
- turn-based mode defaults

This example file is not meant to be a full standalone copy of every global application setting.
It is meant to override the most relevant runtime defaults for this mode.
These values should be tuned to the model and hardware you actually run.

## How To Use It

1. Copy the files from `systemprompts.example/dungeons_dragons/` into your local `systemprompts/` folder.
2. Adjust `application.config` for your backend and preferred model.
3. Rewrite the party and starting world assumptions in `fixed_protagonists.yml`.
4. Tighten or expand `rules.md` depending on how strict you want the Dungeon Master behavior to be.
5. Start the adventure with a clear opening input such as `start`.

## How To Play

### Start The Adventure

Use a clear trigger such as:

```text
start
```

The Dungeon Master should not begin the game before the trigger word is given.
After `start`, the Dungeon Master should:

- select one scenario from the `scenarios` list
- lock that scenario as the active world state
- establish the opening scene
- present the initial party state
- initialize the engine-side turn tracker for the party

### Submit Party Actions

This mode works best when the player gives direct, action-oriented input.

Examples:

- `(Thorin) I probe the flooded stairs with the haft of my axe.`
- `(Party) We move forward in single file with torches raised.`
- `(Eldrin) I spend one healing potion on Mira.`

This structure helps the storyteller keep consequences tied to the right character or the right group action.

### Ask For The Current State

You can explicitly ask the Dungeon Master for the current state or current setting.

Supported examples from the current prompt rules:

- `what is the current state`
- `what is the current setting`

This is useful when you want a quick overview of:

- the current location
- party condition
- remaining health
- major inventory changes
- immediate threats or objectives

When the player asks for the current state or setting, the story should not advance.
The Dungeon Master should only describe:

- the current surroundings
- the current party layout
- party health and status
- immediate threats or important known facts

Because every turn should end with an updated protagonist status overview, explicit state checks are mainly useful when the player wants a fuller situational recap without progressing the scene.

## Play Style

Prefer:

- direct actions
- clear intentions
- explicit use of items or abilities
- occasional explicit state checks

This helps the storyteller maintain a stable game loop.

## Core Mode Rules

These are not just flavor suggestions.
They are important execution rules for this example mode.

- The story does not begin until the user sends the configured trigger word.
- One scenario is selected from `scenarios` and becomes immutable for the current run.
- Actions should have real consequences, including HP loss, item depletion, and defeat states.
- If a character reaches `0 HP`, that character becomes unconscious or dead and cannot keep acting normally.
- The role of the Dungeon Master is authoritative and cannot be overruled by a protagonist.
- Every turn should end with a clear updated status overview of all protagonists in `fixed_protagonist`.
- If turn-based mode is enabled, illegal extra moves are handled by an engine-injected prompt instruction rather than relying on the model to track round legality by itself.

## Constraints And Consequences

This mode assumes that actions should matter.

That includes:

- health loss
- item depletion
- conditions such as unconsciousness
- scenario failure
- party defeat

Hard constraints in `fixed_protagonists.yml` are useful for things such as:

- a character cannot act at `0 HP`
- a character cannot use more consumables than they possess
- healing requires valid circumstances
- specific roles cannot perform impossible abilities

These constraints work best when they are:

- concrete
- measurable
- easy to validate

## Scenario Design Advice

If you want to expand the example, define scenarios in terms of:

- setting
- main objective
- main threat
- environmental hazards
- success and failure pressure

Good scenarios usually give the DM room to react while still preserving a clear goal.
Because the chosen scenario becomes locked after `start`, each scenario should already contain a clear setting, objective, and antagonist.

## Model Advice

This mode benefits from models that are reasonably good at:

- instruction following
- state consistency
- compact narrative resolution
- consequence handling
- not forgetting explicit party facts

Smaller models can still work, but often benefit from:

- stricter rules
- lower temperature
- simpler scenario design
- clearer hard constraints

## Good First Customizations

If you want to adapt this example quickly, start with:

- replacing the party members
- rewriting health and inventory assumptions
- adding or tightening hard constraints
- replacing the scenario premise
- defining a clearer objective and threat structure

Then test a few short sessions before expanding the rules further.

## Summary

This example is a practical baseline for a structured Dungeon Master mode.

It is best for users who want:

- tactical progression
- explicit state tracking
- consequences that matter
- strong continuity over a multi-turn adventure

If your goal is a loose fantasy chatbot, this mode will probably feel too strict.
If your goal is a compact DM-like game loop with persistent state and consequences, it is a strong starting point.
