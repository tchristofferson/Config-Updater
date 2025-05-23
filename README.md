# Config-Updater
Used to update files for Bukkit/Spigot API

#### Example Usage
onEnable method:
```
saveDefaultConfig();
//The config needs to exist before using the updater
File configFile = new File(getDataFolder(), "config.yml");

try {
  ConfigUpdater.update(plugin, "config.yml", configFile, Arrays.asList(...));
} catch (IOException e) {
  e.printStackTrace();
}

reloadConfig();
```
### Maven
```
<repository>
  <name>Central Portal Snapshots</name>
  <id>central-portal-snapshots</id>
  <url>https://central.sonatype.com/repository/maven-snapshots/</url>
  <releases>
    <enabled>false</enabled>
  </releases>
  <snapshots>
    <enabled>true</enabled>
  </snapshots>
</repository>
```
```
<dependency>
  <groupId>com.tchristofferson</groupId>
  <artifactId>ConfigUpdater</artifactId>
  <version>2.2-SNAPSHOT</version>
</dependency>
```
