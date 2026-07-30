# Cowriter Story Mode — Setup Guide

This example configuration turns the storyteller into a compact continuity-focused writing assistant for an existing fictional world.

It is meant for stories where the user remains the authorial source of truth, while the model helps execute scenes, preserve continuity, and keep character behavior aligned with established intent.

## Overview

This example shows how to configure the storyteller for a structured fiction workflow built around:

- a narrative execution prompt in `systemprompt.md`
- fixed baseline character and world data in `fixed_protagonists.yml`
- model and runtime defaults in `application.config`

The goal is not to let the model freely invent an entire story on its own.
The goal is to keep it useful, compact, and consistent while still producing readable scene prose.

## What This Mode Is For

This mode is a good fit when you want the model to:

- continue scenes inside an already established story world
- preserve continuity across longer sessions
- respect hard character constraints
- keep relationships stable unless the story explicitly changes them
- avoid introducing casual plot distortions
- stay compact and information-dense

This mode is especially useful for:

- romance
- drama
- mystery
- relationship-focused fiction
- science fiction with stable internal rules

## Included Files

### `systemprompt.md`

This prompt defines the model as a narrative execution engine.

It emphasizes:

- continuity over improvisation
- explicit state change over inference
- stable character knowledge boundaries
- stable relationship dynamics
- physical-state continuity
- scene writing instead of explanatory meta text

The prompt is intentionally restrictive.
It is designed to reduce drift and stop the model from “repairing” or normalizing unusual situations on its own.

### `fixed_protagonists.yml`

This file contains the baseline story world.

It defines:

- fixed protagonists
- supporting characters
- hard constraints
- psychological traits
- relationship values
- living environment
- world view
- world physics

In this example, the baseline cast is:

- `Valerie`: a careful physicist with emotional restraint and strong internal tension
- `Mark`: a restless technical builder with momentum and protectiveness
- `Gerard`: a controlled and watchful supporting figure who stabilizes the household

This file should contain long-lived truths.
It is best used for things that should not randomly drift between sessions.

### `application.config`

This file contains example runtime defaults for the mode.

It currently includes:

- the default chat model
- the default validator model
- recent history and recent-summary sizing
- generation settings such as temperature, top-k, top-p, and repeat penalty

These values are only a starting point.
You should tune them to the model and hardware you actually use.

## How To Use It

1. Copy the files from `systemprompts.example/cowriter_story/` into your local `systemprompts/` folder.
2. Adjust `application.config` for your available backend and model.
3. Rewrite the characters, relationships, and world assumptions in `fixed_protagonists.yml` to match your own story.
4. Refine `systemprompt.md` if your tone or narrative discipline should be stricter or looser.

Recommended workflow:

- define the stable world first
- define the main protagonists second
- define hard constraints third
- only then tune the prompt wording

## Writing Style Expectations

This setup assumes the model should:

- produce compact prose
- preserve established facts
- avoid introducing new major developments casually
- avoid changing clothes, injuries, relationships, or secrets unless the text explicitly causes that change
- avoid using explanation where scene writing is more appropriate

It is intentionally closer to a continuity-preserving co-writer than to a freeform chatbot.

## Hard Constraints

The `hard_constraints` fields inside `fixed_protagonists.yml` are intended as binding guardrails.

Use them for things such as:

- a character must not die
- a character must not leave the story
- a character must not reveal a secret without user instruction
- a character must not resolve a core conflict without explicit permission

These constraints work best when they are:

- concrete
- narrow
- testable

Avoid vague constraints that read more like tone notes than enforceable rules.

## World Design Advice

Keep these categories separate where possible:

- character identity
- relationships
- living environment
- worldview
- physics or canon rules

That separation makes later maintenance easier and gives the canonical state updater cleaner source material.

As a rule of thumb:

- `fixed_protagonists.yml` stores baseline truths
- canonical state stores current evolving truths
- history stores the raw sequence of turns

## Model Advice

This mode benefits from models that are reasonably strong at:

- instruction following
- continuity
- relationship nuance
- scene writing without overexplaining

Smaller models can still work, but may need:

- lower temperature
- stricter prompt wording
- tighter rule and character definitions
- stronger validator support

## Good First Customizations

If you want to adapt this example quickly, start with:

- rename the three characters
- replace occupations and drives
- rewrite the relationship values
- rewrite the living environment
- rewrite the worldview and world physics
- then test a few short scenes before expanding the lore

## Summary

This example is a practical baseline for a continuity-first story mode.

It is best for users who want:

- a stable story world
- strong character continuity
- compact prose
- limited drift
- less improvisational chaos from the model

If your goal is freeform roleplay or open-world improvisation, this mode is probably too strict.
If your goal is controlled scene continuation inside an existing story, it is a strong starting point.
