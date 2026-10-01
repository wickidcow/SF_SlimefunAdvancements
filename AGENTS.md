# Slimefun Legacy addon maintenance

Support Minecraft/Paper 1.21.11+ with Java21 bytecode. Preserve existing item, advancement and criterion IDs, player UUID filenames, known counter/completion/reward behavior and JSON/YAML file conventions.

An unresolved advancement or criterion must not be erased simply because its definition is temporarily absent. Keep opaque persisted fields and explicit null values while updating only currently managed progress. Never rebuild existing progress from current definition defaults alone.

Keep checked IOException behavior and existing backup/atomic-replacement boundaries. A failed parse must not mix partial primary and backup state. Do not confuse generated fixtures with captured historical worlds or pure JSON tests with actual server behavior.

Use scoped branches and exact-source tests. Preserve concurrent work, document remaining limitations, keep test libraries out of plugin JARs, and do not bump or publish until coordinated release validation.
