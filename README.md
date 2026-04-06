# LevelSystemAPI

> A powerful and modular Level & XP API for Minecraft (Spigot/Paper).  
> Plug & Play – zero boilerplate for other plugin developers.

---

## Quick Start (für Plugin-Entwickler)

```java
// Minimal – fertig
LevelAPI api = LevelAPI.get();
api.addXP(player, 25);

// Mit Source-Tracking
api.addXP(player, 100, XPSource.MOB_KILL);

// Skill XP
Skill mining = api.getSkill("mining");
api.addXP(player, mining, 20, XPSource.ORE_MINE);

// Player-Daten lesen
LevelPlayer lp = api.getPlayer(player);
lp.getLevel();          // globales Level
lp.getXP();             // globale XP
lp.getLevel(mining);    // Skill-Level
```

---

## Events

```java
@EventHandler
public void onLevelUp(LevelUpEvent event) {
    Player p = event.getPlayer();
    int newLevel = event.getNewLevel();

    if (event.isSkillLevelUp()) {
        Skill skill = event.getSkill(); // nicht-null bei Skill-Level-Up
    }
}

@EventHandler
public void onXPGain(XPGainEvent event) {
    // XP-Booster
    event.setAmount(event.getAmount() * 2);

    // Blocken
    if (event.getSource() == XPSource.BLOCK_BREAK) {
        event.setCancelled(true);
    }
}
```

---

## Rewards registrieren

```java
LevelAPI api = LevelAPI.get();

// Für jeden Level-Up
api.registerReward(event -> {
    if (event.getNewLevel() % 10 == 0) {
        event.getPlayer().sendMessage("§6Milestone! Level " + event.getNewLevel());
    }
});

// Für einen bestimmten Level
api.registerReward(50, event -> {
    giveItem(event.getPlayer(), new ItemStack(Material.DIAMOND, 5));
});
```

---

## Level-Formel anpassen

In `config.yml`:
```yaml
level-formula: "100 * level^2"
```

Oder programmatisch:
```java
api.setLevelFormula(level -> 100L * level * level);
api.setLevelFormula(LevelFormula.CLASSIC);   // Minecraft-Stil
api.setLevelFormula(LevelFormula.LINEAR);    // 100 × level
```

---

## Skill-System

```java
// Eigenen Skill registrieren
Skill woodcutting = new Skill("woodcutting", "Woodcutting", LevelFormula.MODERATE, 50);
api.registerSkill(woodcutting);

// XP hinzufügen
api.addXP(player, woodcutting, 30, XPSource.BLOCK_BREAK);

// Status abfragen
lp.getLevel(woodcutting);
lp.getXP(woodcutting);
```

---

## PlaceholderAPI

| Placeholder | Beschreibung |
|---|---|
| `%levelsystem_level%` | Globales Level |
| `%levelsystem_xp%` | Aktuelle XP |
| `%levelsystem_xp_required%` | XP für nächstes Level |
| `%levelsystem_xp_to_next%` | Fehlende XP |
| `%levelsystem_progress%` | Fortschritt in % |
| `%levelsystem_multiplier%` | Aktiver Multiplikator |
| `%levelsystem_skill_mining_level%` | Skill-Level |
| `%levelsystem_skill_mining_xp%` | Skill-XP |

---

## config.yml Übersicht

```yaml
max-level: 100
level-formula: "100 * level^2"

xp:
  kill-mob: 10
  break-block: 2
  mine-ore: 8
  fish-catch: 15

multipliers:
  default: 1.0
  vip: 2.0        # Permission: levelsystem.xp.multiplier.vip
  mvp: 3.0

storage:
  type: sqlite    # sqlite | mysql | mariadb
  redis:
    enabled: false

skills:
  enabled: true
  list: [ mining, combat, fishing, farming, crafting ]

anti-exploit:
  enabled: true
  xp-cooldown-ms: 500
  max-xp-per-minute: 500
```

---

## Commands

| Command | Beschreibung |
|---|---|
| `/level info [player]` | Level-Status anzeigen |
| `/level set <player> <level>` | Level setzen (Admin) |
| `/level reset <player>` | Level zurücksetzen |
| `/xp add <player> <amount> [source]` | XP hinzufügen |
| `/xp remove <player> <amount>` | XP entfernen |
| `/xp set <player> <amount>` | XP setzen |
| `/skill info <skill> [player]` | Skill-Status |
| `/skill set <skill> <player> <level>` | Skill-Level setzen |

---

## Storage

| Backend | Config | Beschreibung |
|---|---|---|
| SQLite | `type: sqlite` | Default, kein Setup |
| MySQL | `type: mysql` | Produktiv, multi-server |
| MariaDB | `type: mariadb` | Wie MySQL |
| Redis | `redis.enabled: true` | Cache + Cross-Server Sync |

---

## Architektur

```
LevelPlugin (onEnable)
 ├── LevelConfig         → config.yml parsen
 ├── StorageProvider     → SQLite / MySQL / Redis
 ├── UIManager           → Actionbar, Title, Sound, BossBar
 ├── AntiExploit         → Cooldowns, Caps, Pattern Detection
 ├── LevelAPI            → zentrale Logik, Player-Cache
 │    ├── LevelPlayer    → gecachter Spielerstatus
 │    ├── LevelFormula   → konfigurierbare Level-Kurve
 │    ├── Skill          → Multi-Skill-System
 │    └── XPSource       → Analytics + Pipeline
 ├── Events              → LevelUpEvent, XPGainEvent
 ├── Hooks               → Vault, PlaceholderAPI
 └── Commands            → /level, /xp, /skill
```

---

## Build

```bash
mvn clean package
```

Output: `target/LevelSystemAPI-1.0.0.jar`

---

## Dependencies (shaded)

- HikariCP 5.1.0
- Jedis 5.1.0
- SQLite JDBC 3.45.1.0
- MySQL Connector 8.3.0

Vault + PlaceholderAPI sind `provided` (soft-depend).
