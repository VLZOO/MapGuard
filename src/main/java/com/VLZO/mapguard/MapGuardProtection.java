package com.VLZO.mapguard;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import java.util.EnumSet;
import java.util.Set;

public class MapGuardProtection implements Listener {

    private final MapGuard plugin;

    private final Set<Material> physicsProtectedBlocks = EnumSet.of(Material.SAND, Material.GRAVEL);

    private volatile boolean protectDecay;
    private volatile boolean protectFarmland;
    private volatile boolean protectPhysics;
    private volatile boolean debugMode;

    public MapGuardProtection(MapGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        try {
            protectDecay = plugin.getConfig().getBoolean("protect-decay", true);
            protectFarmland = plugin.getConfig().getBoolean("protect-farmland", true);
            protectPhysics = plugin.getConfig().getBoolean("protect-physics", true);
            debugMode = plugin.getConfig().getBoolean("debug-mode", false);
        } catch (Throwable t) {
            // if config access fails for any reason, fall back to safe defaults
            protectDecay = true;
            protectFarmland = true;
            protectPhysics = true;
            debugMode = false;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (!protectDecay) return;
        event.setCancelled(true);
        logDebug("LeavesDecay cancelled at " + formatBlock(event.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (!protectFarmland) return;
        Block b = event.getBlock();
        Material type = b.getType();
        if (isFarmlandLike(type)) {
            event.setCancelled(true);
            logDebug("BlockFade cancelled for " + type + " at " + formatBlock(b));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (!protectPhysics) return;
        Material type = event.getBlock().getType();
        if (physicsProtectedBlocks.contains(type)) {
            event.setCancelled(true);
            logDebug("BlockPhysics cancelled for " + type + " at " + formatBlock(event.getBlock()));
        }
    }

    private boolean isFarmlandLike(Material m) {
        if (m == null) return false;
        String name = m.name();
        return name.equalsIgnoreCase("FARMLAND") || name.equalsIgnoreCase("SOIL");
    }

    private void logDebug(String message) {
        if (debugMode) plugin.getLogger().info("[MapGuard DEBUG] " + message);
    }

    private String formatBlock(Block block) {
        if (block == null || block.getWorld() == null) return "unknown";
        return "world=" + block.getWorld().getName() +
                ", x=" + block.getX() +
                ", y=" + block.getY() +
                ", z=" + block.getZ();
    }
}