package com.VLZO.mapguard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MapGuardCommand implements CommandExecutor, TabCompleter {

    private final MapGuard plugin;
    private final Map<UUID, Long> backupAllCooldowns = new ConcurrentHashMap<>();
    private static final long BACKUP_ALL_COOLDOWN_MS = 60_000L;
    private static final String PREFIX = "§2[MG] ";

    public MapGuardCommand(MapGuard plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender sender, String msg) {
        String colored = ChatColor.translateAlternateColorCodes('&', msg);
        sender.sendMessage(PREFIX + colored);
    }

    private void sendNoPrefix(CommandSender sender, String msg) {
        String colored = ChatColor.translateAlternateColorCodes('&', msg);
        sender.sendMessage(colored);
    }

    private boolean hasPerm(CommandSender sender, String perm) {
        return (sender instanceof ConsoleCommandSender) || sender.isOp() || sender.hasPermission(perm);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                if (!hasPerm(sender, "mapguard.reload")) {
                    send(sender, "&cYou do not have permission to reload MapGuard.");
                    return true;
                }
                plugin.reloadPluginConfig();
                send(sender, "&aConfiguration reloaded successfully.");
                return true;

            case "backup":
                return handleBackup(sender, args);

            case "save":
                return handleSave(sender, args);

            case "preload":
                return handlePreload(sender, args);

            default:
                send(sender, "&cUnknown subcommand. Use &e/mapguard &cfor help.");
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sendNoPrefix(sender, "&8&m----------------------------------------");
        sendNoPrefix(sender, "&6&lMapGuard &7&ov1.0 &fby &bVLZO");
        sendNoPrefix(sender, "&eCommands:");
        sendNoPrefix(sender, " &a/mapguard reload &7- Reload plugin configuration");
        sendNoPrefix(sender, " &a/mapguard backup all &7- Backup all normal worlds and slime worlds (60s cooldown per player)");
        sendNoPrefix(sender, " &a/mapguard backup world <name> &7- Backup a specific normal world");
        sendNoPrefix(sender, " &a/mapguard backup slime <name> &7- Backup a specific slime world");
        sendNoPrefix(sender, " &a/mapguard save all &7- Save all normal worlds and slime worlds");
        sendNoPrefix(sender, " &a/mapguard save world <name> &7- Save a specific normal world");
        sendNoPrefix(sender, " &a/mapguard save slime <name> &7- Save a specific slime world");
        sendNoPrefix(sender, " &a/mapguard preload <slimeName> &7- Load slime world and teleport (player only)");
        sendNoPrefix(sender, "&8&m----------------------------------------");
    }

    private boolean handleBackup(final CommandSender sender, String[] args) {
        if (!hasPerm(sender, "mapguard.backup")) {
            send(sender, "&cYou do not have permission to run backups.");
            return true;
        }

        // /mapguard backup all
        if (args.length == 2 && "all".equalsIgnoreCase(args[1])) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                UUID id = p.getUniqueId();
                Long last = backupAllCooldowns.get(id);
                long now = System.currentTimeMillis();
                if (last != null && now - last < BACKUP_ALL_COOLDOWN_MS) {
                    long remain = (BACKUP_ALL_COOLDOWN_MS - (now - last) + 999) / 1000;
                    send(sender, "&cPlease wait &e" + remain + "s &cbefore running another full backup.");
                    return true;
                }
                backupAllCooldowns.put(id, now);
            }
            send(sender, "&eQueueing full backup for all normal worlds and slime worlds...");
            new BukkitRunnable() {
                @Override public void run() {
                    // createBackup handles both normal worlds and slime files
                    plugin.createBackup();
                }
            }.runTaskAsynchronously(plugin);
            send(sender, "&aBackup task queued. Check console/logs for completion.");
            return true;
        }

        // /mapguard backup (no args) -> queue full backup (console/ops)
        if (args.length == 1) {
            send(sender, "&eQueueing full backup for all normal worlds and slime worlds...");
            new BukkitRunnable() {
                @Override public void run() { plugin.createBackup(); }
            }.runTaskAsynchronously(plugin);
            send(sender, "&aBackup task queued.");
            return true;
        }

        // /mapguard backup world <name> or /mapguard backup slime <name>
        if (args.length >= 3) {
            String type = args[1].toLowerCase();
            final String name = args[2];

            if ("world".equals(type)) {
                final World w = Bukkit.getWorld(name);
                if (w == null) {
                    send(sender, "&cWorld not found: &e" + name);
                    return true;
                }
                send(sender, "&eQueueing backup for world: &6" + name);
                new BukkitRunnable() {
                    @Override public void run() { plugin.createBackupForWorld(w); }
                }.runTaskAsynchronously(plugin);
                send(sender, "&aWorld backup queued: &6" + name);
                return true;
            }

            if ("slime".equals(type)) {
                send(sender, "&eQueueing backup for slime world: &6" + name);
                new BukkitRunnable() {
                    @Override public void run() { plugin.createBackupForSlime(name); }
                }.runTaskAsynchronously(plugin);
                send(sender, "&aSlime backup queued: &6" + name);
                return true;
            }
        }

        send(sender, "&cUsage: /mapguard backup [all|world|slime] <name?>");
        return true;
    }

    private boolean handleSave(CommandSender sender, String[] args) {
        if (!hasPerm(sender, "mapguard.save")) {
            send(sender, "&cYou do not have permission to save worlds.");
            return true;
        }

        // /mapguard save all -> save normal worlds + slime worlds
        if (args.length == 2 && "all".equalsIgnoreCase(args[1])) {
            send(sender, "&eSaving all normal worlds...");
            // save normal worlds on main thread
            new BukkitRunnable() {
                @Override public void run() { plugin.saveAllNormalWorlds(); }
            }.runTask(plugin);
            send(sender, "&aAll normal worlds saved. Proceeding to save slime worlds...");

            List<String> slimeNames = plugin.getSlimeWorldNames();
            if (slimeNames.isEmpty()) {
                send(sender, "&eNo slime worlds found to save.");
            } else {
                for (String sname : slimeNames) {
                    boolean ok = plugin.saveWorldForSlime(sname);
                    if (ok) send(sender, "&aSaved slime world: &6" + sname);
                    else send(sender, "&cFailed to save slime world: &6" + sname);
                }
            }
            send(sender, "&aSave-all operation completed.");
            return true;
        }

        // /mapguard save world <name> or /mapguard save slime <name>
        if (args.length >= 3) {
            String type = args[1].toLowerCase();
            String name = args[2];

            if ("world".equals(type)) {
                World w = Bukkit.getWorld(name);
                if (w == null) {
                    send(sender, "&cWorld not found: &e" + name);
                    return true;
                }
                plugin.saveWorld(w);
                send(sender, "&aWorld saved: &6" + name);
                return true;
            }

            if ("slime".equals(type)) {
                boolean ok = plugin.saveWorldForSlime(name);
                if (ok) send(sender, "&aSlime world saved: &6" + name);
                else send(sender, "&cFailed to save slime world: &6" + name);
                return true;
            }
        }

        send(sender, "&cUsage: /mapguard save [all|world|slime] <name?>");
        return true;
    }

    private boolean handlePreload(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            send(sender, "&cOnly players can use this command.");
            return true;
        }
        if (!hasPerm(sender, "mapguard.preload")) {
            send(sender, "&cYou do not have permission to preload slime worlds.");
            return true;
        }
        if (args.length < 2) {
            send(sender, "&cUsage: /mapguard preload <slimeName>");
            return true;
        }
        plugin.preloadSlimeWorldAndTeleport((Player) sender, args[1]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("reload", "backup", "save", "preload"));
        }

        if (args.length == 2) {
            String first = args[0].toLowerCase();
            if ("backup".equals(first) || "save".equals(first)) {
                return filter(args[1], Arrays.asList("all", "world", "slime"));
            }
            if ("preload".equals(first)) {
                return filter(args[1], plugin.getSlimeWorldNames());
            }
        }

        if (args.length == 3) {
            String first = args[0].toLowerCase();
            String second = args[1].toLowerCase();
            if (("backup".equals(first) || "save".equals(first)) && "world".equals(second)) {
                List<String> worlds = new ArrayList<>();
                for (World w : Bukkit.getWorlds()) worlds.add(w.getName());
                return filter(args[2], worlds);
            }
            if (("backup".equals(first) || "save".equals(first)) && "slime".equals(second)) {
                return filter(args[2], plugin.getSlimeWorldNames());
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(String input, List<String> options) {
        List<String> result = new ArrayList<>();
        String lower = (input == null) ? "" : input.toLowerCase();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) result.add(opt);
        }
        return result;
    }
}