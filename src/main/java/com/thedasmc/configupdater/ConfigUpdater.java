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

            //Clear file
            new FileWriter(toUpdate, false).close();

            String line;
            while ((line = reader.readLine()) != null) {

                if (line.startsWith("#")) {

                    writer.write(line);
                    writer.newLine();
                    continue;

                }

                for (String key : config.getKeys(true)) {

                    if (line.startsWith(key + ":")) {

                        String[] array = line.split(": ");

                        if (array.length == 2) {

                            line = array[0] + ": " + config.getString(key);

                        }

                        break;

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
