package com.github.FortyTwoFortyTwo.Shared.memories;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.PlayerText;
import com.github.FortyTwoFortyTwo.Shared.ToolInputException;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.Serializable;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** A copy of an item, e.g. a player's sword, rather than tracking the item itself */
final class ItemMemory implements MemoryType {

    static final ItemMemory INSTANCE = new ItemMemory();

    private ItemMemory() {
    }

    /** A copy of the item, along with a summary to describe it by without deserializing it */
    static Consumer<ConfigurationSection> of(ItemStack item) {
        if (item == null || item.isEmpty())
            throw new ToolInputException("No item given");

        return section -> {
            section.set("item", Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            section.set("material", item.getType().getKey().toString());
            section.set("amount", item.getAmount());

            Map<String, Integer> enchantments = new LinkedHashMap<>();
            item.getEnchantments().forEach((enchantment, level) -> enchantments.put(enchantment.getKey().toString(), level));
            if (!enchantments.isEmpty())
                section.createSection("enchantments", enchantments);

            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasCustomName())
                section.set("custom-name", PlainTextComponentSerializer.plainText().serialize(meta.customName()));
        };
    }

    @Override
    public String getType() {
        return "item";
    }

    @Override
    public Map<String, Object> getInputSchema(MinecraftTool tool) {
        return tool.nestedObjectSchema(
                "A copy of an item as a template, from exactly one of player or item.",
                Map.of(),
                Map.of(
                        "player", tool.stringSchema("Online player whose main hand item to copy"),
                        "item", tool.stringSchema("/give syntax, e.g. diamond_sword[enchantments={sharpness:5}]")));
    }

    @Override
    public Consumer<ConfigurationSection> fromInput(JsonObject input) {
        boolean fromPlayer = input.has("player");
        if (fromPlayer == input.has("item"))
            throw new ToolInputException("Give exactly one of 'player' or 'item'");

        if (!fromPlayer) {
            try {
                return of(Bukkit.getItemFactory().createItemStack(input.get("item").getAsString()));
            } catch (IllegalArgumentException e) {
                throw new ToolInputException("Invalid item: " + e.getMessage());
            }
        }

        Player player = Bukkit.getPlayerExact(input.get("player").getAsString());
        if (player == null)
            throw new ToolInputException("Player not found or offline");

        return of(player.getInventory().getItemInMainHand());
    }

    @Override
    public void describe(ConfigurationSection section, LinkedHashMap<String, Serializable> memory) {
        memory.put("material", section.getString("material"));
        memory.put("amount", section.getInt("amount"));

        ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
        if (enchantments != null) {
            LinkedHashMap<String, Serializable> levels = new LinkedHashMap<>();
            for (String key : enchantments.getKeys(false))
                levels.put(key, enchantments.getInt(key));

            memory.put("enchantments", levels);
        }

        // Players can name items in an anvil, so it could carry instructions
        if (section.contains("custom-name"))
            memory.put("customName", PlayerText.of(section.getString("custom-name")));
    }
}
