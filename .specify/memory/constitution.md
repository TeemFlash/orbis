<!-- Sync impact: scaffold → 1.0.0. Initial principles from user scope; no template changes or deferred placeholders. -->
# Semey Map Constitution

## Core Principles

### I. Scope follows the assignment
Implement the requested lab inside Semey Map. Additional services, BLE protocols, history storage and account flows require an explicit requirement.

### II. Preserve existing application behavior
Keep map/globe, location, authentication, chat and photos usable. New functionality belongs to the existing app module unless the user chooses otherwise.

### III. Lifecycle owns hardware subscriptions
Collect hardware data only while the tracker is resumed. Release listeners on pause and destruction; repeated navigation must not duplicate subscriptions.

### IV. Honest hardware state and minimal permissions
Missing, failed and not-yet-sampled sensors must be distinguishable from a genuine zero. Request only permissions required by the implemented hardware feature.

### V. Evidence before completion
Unit and emulator tests prove software behavior. Claims about physical sensor behavior require a real-device check. Record limitations explicitly.

## Constraints
Existing Java screens may coexist with Kotlin/Compose. Keep English UI and the established light blue/mint visual style. Sensor data stays local and in memory.

## Workflow
Specification → technical plan → executable tasks → implementation only when requested. Review the preservation and lifecycle gates before delivery.

## Governance
User instructions override this constitution. Amend principles with reasons and a semantic version increment; check each feature plan against them. No application implementation is authorized by a planning-only request.

**Version**: 1.0.0 | **Ratified**: 2026-11-10 | **Last Amended**: 2026-11-10
