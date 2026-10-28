package fr.zazac1.customloottables;

public final class LootEntryDefinition {
    public String item_id = "minecraft:diamond";
    public int weight = 1;
    public int min_count = 1;
    public int max_count = 1;

    public LootEntryDefinition copy() {
        LootEntryDefinition copy = new LootEntryDefinition();
        copy.item_id = item_id;
        copy.weight = weight;
        copy.min_count = min_count;
        copy.max_count = max_count;
        return copy;
    }
}
