# 🛡️ MapGuard — Your World's Last Line of Defense

**MapGuard** is not just another backup plugin. It's your server's shield against chaos. Whether it's a crash, a grief attack, or a misclick from an admin — MapGuard makes sure your worlds stay safe, recoverable, and protected.

> **Compatibility note:** MapGuard fully supports Slime-World-Manager (SWM).  
> Official SWM repository: https://github.com/cijaaimee/Slime-World-Manager

---

## 🚀 What MapGuard Does Best

- Protects normal Bukkit/Spigot worlds and SlimeWorldManager `.slime` files.  
- Minimizes data loss from sudden shutdowns and crashes with frequent, safe saves.  
- Provides fast, reliable ZIP backups that can be created without killing TPS.  
- Prevents abuse with permission checks and per-player cooldowns for heavy operations.  
- Gives clean, colorized admin feedback so you always know the server's state.

---

## ✨ Core Features

### Crash Recovery
- Crash detection and admin alerts when the server restarts without a clean shutdown.  
- Periodic auto-save of all worlds to reduce lost progress.  
- Thread-safe saving: normal worlds saved compatibly on the main thread; heavier tasks handled safely.

### Reliable Backups
- ZIP backups for normal worlds and `.slime` files.  
- Asynchronous backup execution to avoid TPS impact.  
- Built-in cooldown (default 60s) for `/mapguard backup all` to prevent spam and abuse.

### SlimeWorldManager Support
- Full support for `.slime` files and SWM workflows.  
- `/mapguard preload <slimeName>` loads a slime world and (optionally) teleports the player to spawn.  
- Auto-detects slime folder if `slimeworld-path` is not configured.  
- **Works with the Slime-World-Manager project:** MapGuard integrates with SWM (see https://github.com/cijaaimee/Slime-World-Manager) so admins can backup, save, preload, and manage slime worlds seamlessly alongside normal worlds.

### Permission-First Design
- Granular permissions for all sensitive actions; help and tab-completion are filtered by permissions.  
- `mapguard.admin` convenience bundle for full access while keeping fine-grained controls available.

### Admin Experience & Crash Detection
- `/mapguard reload` applies config changes without restarting the server.  
- **Crash Detection Workflow:** on startup MapGuard checks for an unclean shutdown flag or missing clean-stop marker. If a crash is detected it:
  - Immediately logs a clear, timestamped alert to console and server log.  
  - Sends an in-game admin notification (permission-checked) listing which worlds were last saved and the recommended recovery actions.  
  - Optionally triggers an automatic backup of current world files to a timestamped ZIP to preserve post-crash state (configurable).  
  - Records a recovery entry in MapGuard logs so admins can review the crash timeline and any automatic actions taken.  
- **Quick Recovery Actions:** dedicated commands and suggested steps appear in the alert so admins can restore from the latest safe backup or run targeted saves/backups without hunting through logs.

---

## 🔧 Commands

```bash
/mapguard reload                # Reload config
/mapguard save all              # Save all worlds
/mapguard save world <name>     # Save one normal world
/mapguard save slime <name>     # Save one slime world
/mapguard backup all            # Backup everything (60s cooldown per player)
/mapguard backup world <name>   # Backup one normal world
/mapguard backup slime <name>   # Backup one slime world
```

---

## 🔐 Permissions

| Permission         | What It Does                                | Default |
|--------------------|----------------------------------------------|---------|
| `mapguard.use`     | See `/mapguard` help and use allowed subcommands | OP      |
| `mapguard.reload`  | Reload plugin config                         | OP      |
| `mapguard.save`    | Save worlds                                  | OP      |
| `mapguard.backup`  | Run backups                                  | OP      |
| `mapguard.admin`   | Full access (convenience bundle)             | OP      |

Use LuckPerms or your preferred manager to assign these to groups or users.

---

## ⚙️ Installation

1. Place `MapGuard.jar` into your server's `/plugins` folder.  
2. Restart the server.  
3. (Optional) Edit `config.yml` to set `slimeworld-path` or other preferences. If omitted, MapGuard auto-detects `slime_worlds`.  
4. Grant permissions via your permission plugin.

---

## 📑 Example `config.yml`

```yaml
# Plugin maintained by VLZO: https://github.com/VLZOO

# ------------------------
# Save & Backup Timing
# ------------------------

# Enable or disable automatic world saving
enable-auto-save: true

# Show a save message in the in-game chat when auto-save runs
show-save-message: true

# The message text shown in chat when a save occurs
save-message-text: "§2[MG] §fWorld saved successfully!"

# ------------------------
# Normal World Settings
# ------------------------

normal-worlds:
  # Time in minutes between each automatic world save
  save-interval-minutes: 5

  # Time in minutes between each automatic backup
  # ⚠ Warning: Frequent backups (especially under 15 minutes) can negatively affect server TPS,
  # particularly during zip operations or when handling large worlds.
  # It is strongly recommended to keep this value at 15 minutes or higher to maintain stable performance.
  backup-interval-minutes: 15

  # Maximum number of backups to keep per world
  max-backups: 5

  # List of world names to exclude from backup (but still save them)
  backup-disabled-worlds:
    - lobby_disabled
    - world_disabled

# ------------------------
# SlimeWorldManager Settings
# ------------------------

slime-worlds:
  # Enable backup of SlimeWorldManager .slime files
  enable-slimeworld-backup: true

  # Time in minutes between each automatic backup
  # ⚠ Warning: Frequent backups (especially under 15 minutes) can negatively affect server TPS,
  # particularly during zip operations or when handling large worlds.
  # It is strongly recommended to keep this value at 15 minutes or higher to maintain stable performance.
  backup-interval-minutes: 30

  # Maximum number of slime backups to keep per world
  max-backups: 3

  # List of slime world names to exclude from backup
  backup-disabled-worlds:
    - lobby_disabled
    - world_disabled

# Allow manual backup of specific worlds via command
allow-manual-world-backup: true

# ------------------------
# Crash Recovery
# ------------------------

# Enable crash detection and automatic restoration of the latest backup
enable-crash-detection: true

# Enable or disable automatic backup feature
enable-auto-backup: true

# ------------------------
# Block Protection
# ------------------------

# Prevent leaves from decaying naturally
protect-decay: true

# Prevent farmland from drying out
protect-farmland: true

# Prevent sand and gravel from falling due to physics
protect-physics: true

# ------------------------
# Debugging
# ------------------------

# Enable debug logs for protection events
debug-mode: false
```

---

## 💡 Best Practices — Keep your map safe during attacks

- Restrict save/backup permissions to trusted admin roles only.  
- Keep an off-site copy of important backups (remote storage or sync).  
- Pair MapGuard with anti-grief (CoreProtect), anti-cheat, and network-level protections for layered defense.  
- Monitor logs and enable webhook/Discord notifications for backup and crash events.

---

## 🚀 Quick Examples

Save everything:  
`/mapguard save all`

Backup everything:  
`/mapguard backup all`

Preload a slime world:  
`/mapguard preload arena_world`

---

## 🧾 Support & Contributions

Issues, feature requests, and pull requests are welcome. Open an issue with reproduction steps and logs for faster help.

---

Made by **VLZO**💖 — built to keep your maps safe when everything else fails.
