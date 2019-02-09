package com.thedasmc.configupdater;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;

public class ConfigUpdater {

    public static void update(File toUpdate, File updateFrom) {

        FileConfiguration config = YamlConfiguration.loadConfiguration(toUpdate);

        if (!toUpdate.exists()) {

            YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(updateFrom);
            try {
                yamlConfiguration.save(toUpdate);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;

        }

        try (BufferedReader reader = new BufferedReader(new FileReader(updateFrom));
             BufferedWriter writer = new BufferedWriter(new FileWriter(toUpdate))) {

            String line;
            outer: while ((line = reader.readLine()) != null) {

                if (line.startsWith("#")) {

                    writer.write(line);
                    writer.newLine();
                    continue;

                }

                for (String key : config.getKeys(true)) {

                    String[] keyArray = key.split("\\.");
                    String keyString = keyArray[keyArray.length - 1];

                    if (line.trim().startsWith(keyString + ":")) {

                        if (config.isConfigurationSection(key)) {

                            writer.write(line);
                            writer.newLine();
                            continue outer;

                        }

                        String[] array = line.split(": ");

                        if (array.length == 2) {

                            if (array[1].startsWith("\"") || array[1].startsWith("'")) {

                                char c = array[1].charAt(0);
                                line = array[0] + ": " + c + config.get(key) + c;

                            } else {

                                line = array[0] + ": " + config.get(key);

                            }

                        }

                        writer.write(line);
                        writer.newLine();
                        continue outer;

                    }

                }

                writer.write(line);
                writer.newLine();

            }

        } catch (IOException e) {

            e.printStackTrace();

        }

    }

}
