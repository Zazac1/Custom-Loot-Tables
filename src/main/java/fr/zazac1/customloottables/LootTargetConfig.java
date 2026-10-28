package fr.zazac1.customloottables;

import java.util.ArrayList;
import java.util.List;

/** One isolated global or per-world loot-table profile. */
public final class LootTargetConfig {
    public List<LootTableDefinition> loot_tables = new ArrayList<>();

    /** True once the first-day editor hint has been shown for this world. */
    public boolean shown_editor_tip = false;

    /** Creation fingerprint so a deleted and recreated save can show the hint again. */
    public String editor_tip_world_instance = "";
}
