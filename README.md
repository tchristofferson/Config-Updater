# Config-Updater
Used to update files for Bukkit/Spigot API

#### Example Usage
onEnable method:
```
saveDefaultConfig();
//The config needs to exist before using the updater
File configFile = new File(getDataFolder(), "config.yml");

try {
  ConfigUpdater.update(this, "config.yml", configFile);
} catch (IOException e) {
  e.printStackTrace();
}

reloadConfig();
```
