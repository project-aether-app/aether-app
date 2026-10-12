<!--
SPDX-FileCopyrightText: 2026 The Authors
SPDX-License-Identifier: Apache-2.0
-->

# Quickstart Guide

## Code & Architecture

- **Simplicity:** No speculative features, single-use abstractions or not requested config.
- **Strong Typing:** Use dedicated types (e.g., `DeviceId`) over primitives (`String`, `Int`).
- **State Management:** Always use `StateFlow`. Avoid `LiveData` and mutable state.
- **Reactive Pattern:** Never update state directly. Use events, effects, and reducers.
- **APIs:** Prefer Kotlin APIs (even experimental). Use libraries over custom logic.
- **Resources:** UI strings must use `res/values/strings.xml`. No hardcoding.
- **Modular:** One responsibility per file. Screens orchestrate; delegates render.
- **Cleanup:** Delete dead code after changes. Remove unused resources.
- **Copyright:** New/rewritten files need SPDX: `<year> The Authors / Apache-2.0`.

## UI & UX

- **Icons:** Use `androidx.compose.material.icons` if possible.
- **Loading:** Use `LoadingIndicator` when loading. Unified state; show content only
  when all data ready. Avoid reload on screen rotation.

## VCS & Workflow

- **Atomic:** One self-contained change per commit. Separate side-quests.
- **Patches:** No indentation/formatting when creating patches. Output raw code.
- **Formatting:** Run `ktfmt --google-style` before every commit.
- **Exclusions:** Never modify/review Gradle wrapper files.

## Interaction Style

- **Telegraphic Speech:** Use minimal words. Skip greetings, fluff, and politeness.
- **No Summaries:** Do not summarize work or explain changes. Output code/fixes only.
- **Token Efficiency:** Maximize info density. Minimize output tokens.
