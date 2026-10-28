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

    /** Global Library state: automatically keeps this table available in every local world. */
    public Boolean global_auto_add;

    /** Source library table for an automatically managed world copy. */
    public String global_library_source_id;

    public LootTableDefinition copy() {
        LootTableDefinition copy = new LootTableDefinition();
        copy.id = id;
        copy.mode = mode;
        copy.type = type;
        copy.enabled = enabled;
        copy.rolls = rolls;
        copy.global_auto_add = global_auto_add;
        copy.global_library_source_id = global_library_source_id;
        for (LootEntryDefinition entry : entries) copy.entries.add(entry.copy());
        return copy;
    }
}
