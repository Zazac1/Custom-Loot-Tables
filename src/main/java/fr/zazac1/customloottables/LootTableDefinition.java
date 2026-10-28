package fr.zazac1.customloottables;

import java.util.ArrayList;
import java.util.List;

public final class LootTableDefinition {
    public String id = "minecraft:chests/simple_dungeon";
    public String mode = "append";
    public String type = "chest";
    public boolean enabled = true;
    public int rolls = 1;
    public List<LootEntryDefinition> entries = new ArrayList<>();

    public LootTableDefinition copy() {
        LootTableDefinition copy = new LootTableDefinition();
        copy.id = id;
        copy.mode = mode;
        copy.type = type;
        copy.enabled = enabled;
        copy.rolls = rolls;
        for (LootEntryDefinition entry : entries) copy.entries.add(entry.copy());
        return copy;
    }
}
