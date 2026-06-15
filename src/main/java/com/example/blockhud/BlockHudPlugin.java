package com.example.blockhud;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class BlockHudPlugin extends JavaPlugin implements Listener {

    /** How far the player can target a block. */
    private static final double RAY_TRACE_DISTANCE = 5.0;

    /** Per-player HUD state. */
    private static final class HudState {
        final BossBar bar;
        Block targetBlock = null;
        long targetSince = 0;
        float progress = 0f;

        HudState(BossBar bar) {
            this.bar = bar;
        }
    }

    private final Map<UUID, HudState> playerStates = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        startUpdateTask();
        getLogger().info("BlockHud enabled.");
    }

    @Override
    public void onDisable() {
        for (Map.Entry<UUID, HudState> entry : playerStates.entrySet()) {
            Player player = getServer().getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue().bar);
            }
        }
        playerStates.clear();
        getLogger().info("BlockHud disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        BossBar bar = BossBar.bossBar(
                Component.empty(),
                0f,
                BossBar.Color.WHITE,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bar);
        playerStates.put(player.getUniqueId(), new HudState(bar));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        HudState state = playerStates.remove(player.getUniqueId());
        if (state != null) {
            player.hideBossBar(state.bar);
        }
    }

    /** Advance digging progress when the player swings at the target block. */
    @EventHandler
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        HudState state = playerStates.get(player.getUniqueId());
        if (state == null || state.targetBlock == null) {
            return;
        }

        Block block = state.targetBlock;
        float damagePerSwing = calculateDamagePerSwing(player, block);
        state.progress = Math.min(1f, state.progress + damagePerSwing);
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : getServer().getOnlinePlayers()) {
                    HudState state = playerStates.get(player.getUniqueId());
                    if (state == null) {
                        continue;
                    }

                    RayTraceResult result = player.rayTraceBlocks(RAY_TRACE_DISTANCE, FluidCollisionMode.NEVER);
                    Block block = result != null ? result.getHitBlock() : null;

                    if (block == null || block.getType().isAir()) {
                        state.targetBlock = null;
                        state.progress = 0f;
                        player.hideBossBar(state.bar);
                        continue;
                    }

                    // Reset progress when target changes
                    if (!block.equals(state.targetBlock)) {
                        state.targetBlock = block;
                        state.progress = 0f;
                    }

                    // Slowly decay progress when not actively mining
                    if (state.progress > 0f && state.progress < 1f) {
                        state.progress = Math.max(0f, state.progress - 0.015f);
                    }

                    updateBossBar(player, state, block);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void updateBossBar(Player player, HudState state, Block block) {
        Material type = block.getType();
        String toolName = getRequiredTool(type);
        Component toolComponent = formatTool(toolName);

        Component title = Component.text()
                .append(Component.text("▣ ", NamedTextColor.GOLD))
                .append(Component.translatable(type.translationKey(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(toolComponent)
                .build();

        state.bar.name(title);
        state.bar.progress(state.progress);
        state.bar.color(barColor(state.progress));
        player.showBossBar(state.bar);
    }

    private Component formatTool(String toolName) {
        NamedTextColor color = switch (toolName.toLowerCase()) {
            case "pickaxe" -> NamedTextColor.AQUA;
            case "axe" -> NamedTextColor.GOLD;
            case "shovel" -> NamedTextColor.YELLOW;
            case "hoe" -> NamedTextColor.GREEN;
            case "shears" -> NamedTextColor.LIGHT_PURPLE;
            case "sword" -> NamedTextColor.RED;
            default -> NamedTextColor.GRAY;
        };
        return Component.text("Tool: " + toolName, color);
    }

    private BossBar.Color barColor(float progress) {
        if (progress < 0.33f) return BossBar.Color.WHITE;
        if (progress < 0.66f) return BossBar.Color.YELLOW;
        if (progress < 1f) return BossBar.Color.GREEN;
        return BossBar.Color.RED;
    }

    /**
     * Calculates how much digging progress one arm swing contributes.
     * Returns 0 if the block is unbreakable or the player is using the wrong tool.
     */
    private float calculateDamagePerSwing(Player player, Block block) {
        Material type = block.getType();
        float hardness = type.getHardness();
        if (hardness < 0) {
            return 0f; // unbreakable
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        float speed = getToolSpeed(tool, type);

        if (speed <= 1f && requiresCorrectTool(type)) {
            // Wrong tool for a block that needs a specific tool
            return 0f;
        }

        // Haste / Mining Fatigue
        if (player.hasPotionEffect(PotionEffectType.HASTE)) {
            int level = player.getPotionEffect(PotionEffectType.HASTE).getAmplifier() + 1;
            speed *= (1f + 0.2f * level * level);
        }
        if (player.hasPotionEffect(PotionEffectType.MINING_FATIGUE)) {
            int level = player.getPotionEffect(PotionEffectType.MINING_FATIGUE).getAmplifier() + 1;
            speed *= (float) Math.pow(0.3, level);
        }

        // In-air and not-on-ground penalties
        if (player.isInWater() && !hasAquaAffinity(player)) {
            speed *= 0.2f;
        }
        if (!player.isOnGround()) {
            speed *= 0.2f;
        }

        float damage = speed / hardness;
        if (!canHarvest(tool, type)) {
            damage /= 5f; // penalty for not being able to harvest drops
        } else {
            damage /= 1.5f;
        }

        // Clamp to a reasonable per-swing increment
        return Math.max(0f, damage / 20f);
    }

    private float getToolSpeed(ItemStack tool, Material blockType) {
        Material toolType = tool.getType();
        return switch (toolType) {
            case WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL, WOODEN_HOE -> 2f;
            case STONE_PICKAXE, STONE_AXE, STONE_SHOVEL, STONE_HOE -> 4f;
            case IRON_PICKAXE, IRON_AXE, IRON_SHOVEL, IRON_HOE -> 6f;
            case DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SHOVEL, DIAMOND_HOE -> 8f;
            case NETHERITE_PICKAXE, NETHERITE_AXE, NETHERITE_SHOVEL, NETHERITE_HOE -> 9f;
            case GOLDEN_PICKAXE, GOLDEN_AXE, GOLDEN_SHOVEL, GOLDEN_HOE -> 12f;
            case SHEARS -> {
                if (Tag.WOOL.isTagged(blockType) || Tag.LEAVES.isTagged(blockType)) {
                    yield 5f;
                }
                yield 1f;
            }
            default -> {
                if (toolType.name().endsWith("_SWORD")) {
                    if (blockType == Material.COBWEB) {
                        yield 15f;
                    }
                    yield 1.5f;
                }
                yield 1f;
            }
        };
    }

    private boolean requiresCorrectTool(Material type) {
        return Tag.MINEABLE_PICKAXE.isTagged(type)
                || Tag.MINEABLE_AXE.isTagged(type)
                || Tag.MINEABLE_SHOVEL.isTagged(type)
                || Tag.MINEABLE_HOE.isTagged(type);
    }

    private boolean canHarvest(ItemStack tool, Material blockType) {
        // Simplified: assume correct tool type can harvest
        String required = getRequiredTool(blockType);
        return switch (required) {
            case "Pickaxe" -> isToolType(tool, "PICKAXE");
            case "Axe" -> isToolType(tool, "AXE");
            case "Shovel" -> isToolType(tool, "SHOVEL");
            case "Hoe" -> isToolType(tool, "HOE");
            case "Shears" -> tool.getType() == Material.SHEARS;
            case "Sword" -> tool.getType().name().endsWith("_SWORD");
            default -> true; // hand-minable
        };
    }

    private boolean isToolType(ItemStack tool, String suffix) {
        String name = tool.getType().name();
        return name.endsWith("_" + suffix) && !name.contains("LEGACY");
    }

    private boolean hasAquaAffinity(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return helmet != null && helmet.containsEnchantment(org.bukkit.enchantments.Enchantment.AQUA_AFFINITY);
    }

    private String getRequiredTool(Material type) {
        if (Tag.MINEABLE_PICKAXE.isTagged(type)) return "Pickaxe";
        if (Tag.MINEABLE_AXE.isTagged(type)) return "Axe";
        if (Tag.MINEABLE_SHOVEL.isTagged(type)) return "Shovel";
        if (Tag.MINEABLE_HOE.isTagged(type)) return "Hoe";
        if (Tag.WOOL.isTagged(type) || Tag.LEAVES.isTagged(type)
                || type == Material.COBWEB || type == Material.SHORT_GRASS
                || type == Material.TALL_GRASS || type == Material.FERN
                || type == Material.LARGE_FERN || type == Material.VINE) {
            return "Shears";
        }
        if (type == Material.COBWEB) return "Sword";
        return "Hand";
    }
}
