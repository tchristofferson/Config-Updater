package com.tchristofferson.configupdater;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A class to update/add new sections/keys to your config
 * while keeping your current values and keeping your comments
 */
public class ConfigUpdater {

    /**
     * Update a yaml file from a resource inside your plugin jar
     * @param plugin You plugin
     * @param resourceName The yaml file name to update from, typically config.yml
     * @param toUpdate The yaml file to update
     * @throws IOException If an IOException occurs
     */
    public static void update(Plugin plugin, String resourceName, File toUpdate) throws IOException {
        BufferedReader newReader = new BufferedReader(new InputStreamReader(plugin.getResource(resourceName)));
        List<String> newLines = newReader.lines().collect(Collectors.toList());
        newReader.close();

        FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(toUpdate);
        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource(resourceName)));
        BufferedWriter writer = new BufferedWriter(new FileWriter(toUpdate));

        Map<String, String> comments = parseComments(newLines);
        write(newConfig, oldConfig, comments, writer);
    }

    //                    Key, CommentAboveKey
    //Also parses a blank line
    private static Map<String, String> parseComments(List<String> lines) {
        Map<String, String> comments = new HashMap<>();
        StringBuilder builder = new StringBuilder();
        StringBuilder keyBuilder = new StringBuilder();
        int lastLineIndentCount = 0;

        for (String line : lines) {
            if (line != null && line.trim().startsWith("-"))
                continue;

            if (line == null || line.trim().equals("") || line.trim().startsWith("#")) {
                builder.append(line).append("\n");
            } else {
                int currentIndents = countIndents(line);
                String key = line.trim().split(":")[0];

                if (keyBuilder.length() == 0) {
                    keyBuilder.append(key);
                } else if (currentIndents == lastLineIndentCount) {
                    //Replace the last part of the key with current key
                    removeLastKey(keyBuilder);

                    if (keyBuilder.length() > 0) {
                        keyBuilder.append(".");
                    }

                    keyBuilder.append(key);
                } else if (currentIndents > lastLineIndentCount) {
                    //Append current key to the keyBuilder
                    keyBuilder.append(".").append(key);
                } else {
                    int difference = lastLineIndentCount - currentIndents;

                    for (int i = 0; i < difference + 1; i++) {
                        removeLastKey(keyBuilder);
                    }

                    if (keyBuilder.length() > 0) {
                        keyBuilder.append(".");
                    }

                    keyBuilder.append(key);
                }

                if (keyBuilder.length() > 0) {
                    comments.put(keyBuilder.toString(), builder.toString());
                    builder.setLength(0);
                }

                lastLineIndentCount = currentIndents;
            }
        }

        return comments;
    }

    //Write method doing the work.
    //It checks if key has a comment associated with it and writes comment then the key and value
    private static void write(FileConfiguration newConfig, FileConfiguration oldConfig, Map<String, String> comments, BufferedWriter writer) throws IOException {
        Yaml yaml = new Yaml();
        for (String key : newConfig.getKeys(true)) {
            String[] keys = key.split("\\.");
            String actualKey = keys[keys.length - 1];
            String comment = comments.remove(key);

            StringBuilder builder = new StringBuilder();
            int indents = keys.length - 1;

            for (int i = 0; i < indents; i++) {
                builder.append("  ");
            }

            String prefixSpaces = builder.toString();

            if (comment != null) {
                writer.write(comment);//No \n character necessary, new line is automatically at end of comment
            }

            Object newObj = newConfig.get(key);
            Object oldObj = oldConfig.get(key);

            if (newObj instanceof ConfigurationSection && oldObj instanceof ConfigurationSection) {
                //write the old section
                writeSection(writer, actualKey, prefixSpaces, (ConfigurationSection) oldObj);
            } else if (newObj instanceof ConfigurationSection) {
                //write the new section, old value is no more
                writeSection(writer, actualKey, prefixSpaces, (ConfigurationSection) newObj);
            } else if (oldObj != null) {
                //write the old object
                write(oldObj, actualKey, prefixSpaces, yaml, writer);
            } else {
                //write new object
                write(newObj, actualKey, prefixSpaces, yaml, writer);
            }
        }

        writer.close();
    }

    //Writes a configuration section
    private static void writeSection(BufferedWriter writer, String actualKey, String prefixSpaces, ConfigurationSection section) throws IOException {
        if (section.getKeys(false).isEmpty()) {
            writer.write(prefixSpaces + actualKey + ": {}");
        } else {
            writer.write(prefixSpaces + actualKey + ":");
        }

        writer.write("\n");
    }

    //Doesn't work with configuration sections, must be an actual object
    //Auto checks if it is serializable and writes to file
    private static void write(Object obj, String actualKey, String prefixSpaces, Yaml yaml, BufferedWriter writer) throws IOException {
        if (obj instanceof ConfigurationSerializable) {
            writer.write(prefixSpaces + actualKey + ": " + yaml.dump(((ConfigurationSerializable) obj).serialize()));
        } else if (obj instanceof String || obj instanceof Character) {
            if (obj instanceof String) {
                String s = (String) obj;
                obj = s.replace("\n", "\\n").replace("\t", "\\t");
            }
            writer.write(prefixSpaces + actualKey + ": '" + obj + "'\n");
        } else if (obj instanceof List) {
            writeList((List) obj, actualKey, prefixSpaces, yaml, writer);
        } else {
            writer.write(prefixSpaces + actualKey + ": " + yaml.dump(obj));
        }
    }

    //Writes a list of any object
    private static void writeList(List list, String actualKey, String prefixSpaces, Yaml yaml, BufferedWriter writer) throws IOException {
        writer.write(prefixSpaces + actualKey + ":\n");

        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);

            if (o instanceof String || o instanceof Character) {
                writer.write(prefixSpaces + "- '" + o + "'");
            } else if (o instanceof List) {
                writer.write(prefixSpaces + "- " + yaml.dump(o));
            } else {
                writer.write(prefixSpaces + "- " + o);
            }

            if (i != list.size()) {
                writer.write("\n");
            }
        }
    }

    //Counts spaces in front of key and divides by 2 since 1 indent = 2 spaces
    private static int countIndents(String s) {
        int spaces = 0;

        for (char c : s.toCharArray()) {
            if (c == ' ') {
                spaces += 1;
            } else {
                break;
            }
        }

        return spaces / 2;
    }

    //Ex. keyBuilder = key1.key2.key3 --> key1.key2
    private static void removeLastKey(StringBuilder keyBuilder) {
        String temp = keyBuilder.toString();
        String[] keys = temp.split("\\.");

        if (keys.length == 1) {
            keyBuilder.setLength(0);
            return;
        }

        temp = temp.substring(0, temp.length() - keys[keys.length - 1].length() - 1);
        keyBuilder.setLength(temp.length());
    }
}
