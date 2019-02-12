package com.thedasmc.configupdater;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A class to update/add new sections/keys to your config
 * while keeping your current values
 */
public class ConfigUpdater {

    /**
     * Update a yml file from another yml file
     * @param toUpdate The yml file to update
     * @param updateFrom The yml file to update from
     */
    public static void update(File toUpdate, File updateFrom) {

        try {

            BufferedReader reader = getBufferedReader(updateFrom);
            update(toUpdate, reader);

        } catch (IOException e) {

            e.printStackTrace();

        }

    }

    /**
     * Update a yml file from an InputStream
     * This is useful for default configs (config saved in jar) using {@link org.bukkit.plugin.java.JavaPlugin#getResource(String)}
     * @param toUpdate The yml file to update
     * @param updateFrom The InputStream to update from
     */
    public static void update(File toUpdate, InputStream updateFrom) {

        BufferedReader reader = getBufferedReader(updateFrom);

        try {

            update(toUpdate, reader);

        } catch (IOException e) {

            e.printStackTrace();

        }

    }

    private static void update(File toUpdate, BufferedReader reader) throws IOException {

        FileConfiguration config = YamlConfiguration.loadConfiguration(toUpdate);

        if (!toUpdate.exists()) {

            config.save(toUpdate);
            return;

        }

        BufferedWriter writer = new BufferedWriter(new FileWriter(toUpdate));

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

                        write(writer, line);
                        continue outer;

                    }

                    String[] array = line.split(": ");

                    if (array.length > 1) {

                        if (array[1].startsWith("\"") || array[1].startsWith("'")) {

                            char c = array[1].charAt(0);
                            line = array[0] + ": " + c + config.get(key) + c;

                        } else {

                            line = array[0] + ": " + config.get(key);

                        }

                    }

                    write(writer, line);
                    continue outer;

                }

            }

            write(writer, line);

        }

    }

    private static BufferedReader getBufferedReader(InputStream inputStream) {

        return new BufferedReader(new InputStreamReader(inputStream));

    }

    private static BufferedReader getBufferedReader(File file) throws FileNotFoundException {

        return new BufferedReader(new FileReader(file));

    }

    private static void write(BufferedWriter writer, String line) throws IOException {

        writer.write(line);
        writer.newLine();

    }

}
