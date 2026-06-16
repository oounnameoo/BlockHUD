package com.example.blockhud;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlockHudPlugin extends JavaPlugin implements Listener {

    /** How far the player can target a block. */
    private static final double RAY_TRACE_DISTANCE = 5.0;

    /** Per-player HUD state. */
    private static final class HudState {
        final BossBar bar;
        Block targetBlock = null;

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
                1f,
                BossBar.Color.WHITE,
                BossBar.Overlay.PROGRESS
        );
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
                        player.hideBossBar(state.bar);
                        continue;
                    }

                    state.targetBlock = block;
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
