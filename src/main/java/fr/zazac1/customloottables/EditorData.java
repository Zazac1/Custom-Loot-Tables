package fr.zazac1.customloottables;

import java.util.List;

public record EditorData(ModConfig config, List<String> loadedTableIds,
                         String activeWorldId, String activeWorldName) {}
