package com.tchristofferson.configupdater;

import com.google.common.base.Preconditions;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConfigUpdater {

    //Used for separating keys in the keyBuilder inside parseComments method
    private static final char SEPARATOR = '.';

    public static void update(Plugin plugin, String resourceName, File toUpdate, List<String> ignoredSections) throws IOException {
        Preconditions.checkArgument(toUpdate.exists(), "The toUpdate file doesn't exist!");

        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource(resourceName), StandardCharsets.UTF_8));
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(toUpdate);
        Map<String, String> comments = parseComments(plugin, resourceName, defaultConfig);
        Map<String, String> ignoredSectionsValues = parseIgnoredSections(toUpdate, currentConfig, ignoredSections == null ? Collections.emptyList() : ignoredSections);
    }

    //TODO: Test write method
    private static void write(FileConfiguration defaultConfig, FileConfiguration currentConfig, File toUpdate, Map<String, String> comments, Map<String, String> ignoredSectionsValues) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(toUpdate));
        //Used for converting objects to yaml, then cleared
        FileConfiguration parserConfig = new YamlConfiguration();

        keyLoop: for (String fullKey : defaultConfig.getKeys(true)) {
            String comment = comments.get(fullKey);

            //Comments always end with \n
            if (comment != null)
                writer.write(comment);

            Iterator<Map.Entry<String, String>> iterator = ignoredSectionsValues.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> entry = iterator.next();

                if (entry.getKey().equals(fullKey)) {
                    writer.write(entry.getValue());
                    iterator.remove();
                    continue keyLoop;
                } else if (KeyBuilder.isSubKey(entry.getKey(), fullKey, SEPARATOR)) {
                    continue keyLoop;
                }
            }

            //TODO: Write indents, last index of split full key, then currentConfig value
        }

        writer.close();
    }

    //Returns a map of key comment pairs. If a key doesn't have any comments it won't be included in the map.
    private static Map<String, String> parseComments(Plugin plugin, String resourceName, FileConfiguration defaultConfig) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(plugin.getResource(resourceName)));
        Map<String, String> comments = new LinkedHashMap<>();
        StringBuilder commentBuilder = new StringBuilder();
        KeyBuilder keyBuilder = new KeyBuilder(defaultConfig, SEPARATOR);

        String line;
        lineLoop: while ((line = reader.readLine()) != null) {
            String trimmedLine = line.trim();

            //Only getting comments for keys. A list/array element comment(s) not supported
            if (trimmedLine.startsWith("-"))
                continue;

            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {//Is blank line or is comment
                commentBuilder.append(trimmedLine).append("\n");
            } else {//is a valid yaml key
                keyBuilder.parseLine(trimmedLine);
                String key = keyBuilder.toString();

                //If there is a comment associated with the key it is added to comments map and the commentBuilder is reset
                if (commentBuilder.length() > 0) {
                    comments.put(key, commentBuilder.toString());
                    commentBuilder.setLength(0);
                }

                //Remove the last key from keyBuilder if current path isn't a config section or if it is empty to prepare for the next key
                if (!keyBuilder.isConfigSectionWithKeys()) {
                    keyBuilder.removeLastKey();
                }
            }
        }

        reader.close();

        if (commentBuilder.length() > 0)
            comments.put(null, commentBuilder.toString());

        return comments;
    }

    private static Map<String, String> parseIgnoredSections(File toUpdate, FileConfiguration currentConfig, List<String> ignoredSections) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(toUpdate));
        Map<String, String> ignoredSectionsValues = new LinkedHashMap<>(ignoredSections.size());
        KeyBuilder keyBuilder = new KeyBuilder(currentConfig, SEPARATOR);

        String line;
        while ((line = reader.readLine()) != null) {
            //Will parse if it is an ignored section
            parseIgnoredSection(ignoredSections, reader, ignoredSectionsValues, keyBuilder, line);
        }

        reader.close();
        return ignoredSectionsValues;
    }

    private static void parseIgnoredSection(List<String> ignoredSections, BufferedReader reader, Map<String, String> ignoredSectionsValues, KeyBuilder keyBuilder, String line) throws IOException {
        String trimmedLine = line.trim();

        //Ignore blank lines, comments, and array/list elements
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("-"))
            return;

        keyBuilder.parseLine(trimmedLine);
        String key = keyBuilder.toString();

        for (String ignoredSection : ignoredSections) {
            if (key.equals(ignoredSection)) {
                ignoredSectionsValues.put(key, buildIgnoredSectionValue(reader, keyBuilder, ignoredSection));
                //Needs to use recursion because buildIgnoredSectionValue method reads until the key isn't a sub-key of the ignored section, which needs to be parsed as well
                parseIgnoredSection(ignoredSections, reader, ignoredSectionsValues, keyBuilder, trimmedLine);
                break;
            }
        }
    }

    private static String buildIgnoredSectionValue(BufferedReader reader, KeyBuilder keyBuilder, String ignoredSection) throws IOException {
        StringBuilder valueBuilder = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("-"))
                continue;

            keyBuilder.parseLine(line);

            if (!keyBuilder.isSubKey(ignoredSection))
                break;

            if (valueBuilder.length() > 0)
                valueBuilder.append("\n");

            valueBuilder.append(line);
        }

        return valueBuilder.toString();
    }

    //Input: 'key1.key2' Result: 'key1'
    private static void removeLastKey(StringBuilder keyBuilder) {
        if (keyBuilder.length() == 0)
            return;

        String keyString = keyBuilder.toString();
        //Must be enclosed in brackets in case a regex special character is the separator
        String[] split = keyString.split("[" + SEPARATOR + "]");
        //Makes sure begin index isn't < 0 (error). Occurs when there is only one key in the path
        int minIndex = Math.max(0, keyBuilder.length() - split[split.length - 1].length() - 1);
        keyBuilder.replace(minIndex, keyBuilder.length(), "");
    }

}
