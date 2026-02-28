# Cobblemon Bingo (Fabric 1.21.1)

A fully server-side Cobblemon Bingo system built for Fabric 1.21.1.

Designed for multiplayer Cobblemon servers, this mod provides per-player
randomized bingo boards, persistent progress tracking, and configurable
win conditions --- all without requiring client-side installation.

------------------------------------------------------------------------

## ✨ Features

-   🎲 Per-player randomized bingo boards
-   💾 Persistent progress using `SavedData`
-   🧠 Challenge types:
    -   `catch`
    -   `collect`
    -   `enterarea`
    -   `custom`
-   🏆 Win conditions:
    -   Horizontal
    -   Vertical
    -   Diagonal
-   ⚙️ Weighted `onCompletion` command execution (runs as server)
-   🔄 Configurable reset behavior:
    -   Global reset on win
    -   One-time reward per player until manual reset
-   🛡️ Reward-claim tracking per player per game
-   📦 Fully server-side (no client mod required)

------------------------------------------------------------------------

## 🧩 Challenge Types

### Catch

Track Pokémon captures by: - Specific Pokémon - Pokémon type - Quantity
required

### Collect

Track items in player inventory by: - Item ID - Quantity required

### Enter Area

Track player entering specific coordinates.

### Custom

Manually incremented via commands or integrated with other
systems/events.

------------------------------------------------------------------------

## 🛠 Commands

### General

    /bingo open
    /bingo reload
    /bingo enable <game>
    /bingo disable <game>

### Progress Control

    /bingo addprogress <player> <challengeId> <amount>
    /bingo incrementprogress <player> <challengeId>

### Reset Controls

    /bingo resetbingo <game>
    /bingo resetbingoall
    /bingo resetchallenge <game> <challengeId>
    /bingo resetchallengeall

------------------------------------------------------------------------

## ⚙️ Configuration

Each bingo game is defined in JSON.

Example structure:

``` json
{
  "name": "Cobblemon Bingo",
  "isRandomized": true,
  "isActive": true,
  "doesResetOnCompletion": true,
  "completion": ["horizontal", "vertical", "diagonal"],
  "completionMessage": "&a%player% completed &e%game%&a!",
  "disableOnCompletion": false,
  "onCompletion": [
    { "command": "say %player% won Bingo!", "weight": 1 }
  ],
  "challenges": []
}
```

------------------------------------------------------------------------

## 🔁 Reset Behavior

`doesResetOnCompletion` determines how wins behave:

-   `true` → Resets the board for all players after a win
-   `false` → Players can only claim rewards once until `/resetbingo`

------------------------------------------------------------------------

## 🚀 Performance Notes

Optimized to: - Avoid unnecessary inventory scanning - Only process
relevant challenge types - Persist data efficiently with `SavedData`

------------------------------------------------------------------------

## 📌 Requirements

-   Minecraft 1.21.1
-   Fabric Loader
-   Cobblemon 1.7.3

------------------------------------------------------------------------

## 🧠 Design Goals

-   Fully server-side compatibility
-   Minimal performance overhead
-   Highly configurable
-   Easy integration with custom server events

------------------------------------------------------------------------

Generated on 2026-02-28
