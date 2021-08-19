package com.tchristofferson.configupdater;

import com.google.common.base.Preconditions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigUpdater {

    //Used for separating keys in the keyBuilder inside parseComments method
    private static final char SEPARATOR = '.';

    public static void update(Plugin plugin, String resourceName, File toUpdate, List<String> ignoredSections) throws IOException {
        Preconditions.checkArgument(toUpdate.exists(), "The toUpdate file doesn't exist!");

        if (ignoredSections == null)
            ignoredSections = Collections.emptyList();

        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource(resourceName), StandardCharsets.UTF_8));
        Map<String, Object> currentValues = YamlConfiguration.loadConfiguration(toUpdate).getValues(true);
        Map<String, String> comments = parseComments(plugin, resourceName, defaultConfig);
    }

    private static void write(FileConfiguration defaultConfig, FileConfiguration currentConfig, File toUpdate, Map<String, String> comments, List<String> ignoredSections) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(toUpdate));
        //Used for converting objects to yaml, then cleared
        FileConfiguration parserConfig = new YamlConfiguration();

        for (String fullKey : defaultConfig.getKeys(true)) {
            String comment = comments.get(fullKey);

            //Comments always end with \n
            if (comment != null)
                writer.write(comment);

            for (String ignoredSectionKey : ignoredSections) {
                if (!fullKey.startsWith(ignoredSectionKey))
                    continue;

                String subString = fullKey.substring(ignoredSectionKey.length());
                if (subString.isEmpty() || subString.startsWith(String.valueOf(SEPARATOR))) {
                    //Is ignored section or within ignored section
                    ConfigurationSection ignoredSection = currentConfig.getConfigurationSection(fullKey);

                    if (ignoredSection == null)
                        break;

                    //TODO: read lines of currentConfig, paste using writer.
                    //Possibly parse ignored sections before hand similar to comments
                    for (String ignoredKey : ignoredSection.getKeys(true)) {

                    }
                }
            }
        }



        writer.close();
    }

    //Returns a map of key comment pairs. If a key doesn't have any comments it won't be included in the map.
    private static Map<String, String> parseComments(Plugin plugin, String resourceName, FileConfiguration defaultConfig) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(plugin.getResource(resourceName)));
        Map<String, String> comments = new LinkedHashMap<>();
        StringBuilder commentBuilder = new StringBuilder();
        StringBuilder keyBuilder = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            String trimmedLine = line.trim();

            //Only getting comments for keys. A list/array element comment(s) not supported
            if (trimmedLine.startsWith("-"))
                continue;

            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {//Is blank line or is comment
                commentBuilder.append(trimmedLine).append("\n");
            } else {//is a valid yaml key
                String[] currentSplitLine = trimmedLine.split(":");

                //Checks keyBuilder path against defaultConfig to see if the path is valid.
                //If the path doesn't exist in the default config it keeps removing last key in keyBuilder.
                while (keyBuilder.length() > 0 && !defaultConfig.contains(keyBuilder.toString() + SEPARATOR + currentSplitLine[0])) {
                    removeLastKey(keyBuilder);
                }

                //Add the separator if there is already a key inside keyBuilder
                //If currentSplitLine[0] is 'key2' and keyBuilder contains 'key1' the result will be 'key1.' if '.' is the separator
                if (keyBuilder.length() > 0)
                    keyBuilder.append(SEPARATOR);

                //Appends the current key to keyBuilder
                //If keyBuilder is 'key1.' and currentSplitLine[0] is 'key2' the resulting keyBuilder will be 'key1.key2' if separator is '.'
                keyBuilder.append(currentSplitLine[0]);
                String key = keyBuilder.toString();

                //If there is a comment associated with the key it is added to comments map and the commentBuilder is reset
                if (commentBuilder.length() > 0) {
                    comments.put(key, commentBuilder.toString());
                    commentBuilder.setLength(0);
                }

                //Remove the last key from keyBuilder if current path isn't a config section or if it is empty to prepare for the next key
                if (!defaultConfig.isConfigurationSection(key) || defaultConfig.getConfigurationSection(key).getKeys(false).isEmpty()) {
                    removeLastKey(keyBuilder);
                }
            }
        }

        return comments;
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
