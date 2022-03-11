package com.tchristofferson.configupdater;

import com.google.common.base.Preconditions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConfigUpdater {

    //Used for separating keys in the keyBuilder inside parseComments method
    private static final char SEPARATOR = '.';

    public static void update(Plugin plugin, String resourceName, File toUpdate, String... ignoredSections) throws IOException {
        update(plugin, resourceName, toUpdate, Arrays.asList(ignoredSections));
    }

    public static void update(Plugin plugin, String resourceName, File toUpdate, List<String> ignoredSections) throws IOException {
        Preconditions.checkArgument(toUpdate.exists(), "The toUpdate file doesn't exist!");

        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource(resourceName), StandardCharsets.UTF_8));
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(toUpdate);
        Map<String, String> comments = parseComments(plugin, resourceName, defaultConfig);
        Map<String, String> ignoredSectionsValues = parseIgnoredSections(toUpdate, currentConfig, comments, ignoredSections == null ? Collections.emptyList() : ignoredSections);

        // will write updated config file "contents" to a string
        StringWriter writer = new StringWriter();
        write(defaultConfig, currentConfig, new BufferedWriter(writer), comments, ignoredSectionsValues);
        String value = writer.toString(); // config contents

        Path toUpdatePath = toUpdate.toPath();
        if (!value.equals(new String(Files.readAllBytes(toUpdatePath), StandardCharsets.UTF_8))) { // if updated contents are not the same as current file contents, update
            Files.write(toUpdatePath, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void write(FileConfiguration defaultConfig, FileConfiguration currentConfig, BufferedWriter writer, Map<String, String> comments, Map<String, String> ignoredSectionsValues) throws IOException {
        //Used for converting objects to yaml, then cleared
        FileConfiguration parserConfig = new YamlConfiguration();

        keyLoop: for (String fullKey : defaultConfig.getKeys(true)) {
            String indents = KeyBuilder.getIndents(fullKey, SEPARATOR);

            if (ignoredSectionsValues.isEmpty()) {
                writeCommentIfExists(comments, writer, fullKey, indents);
            } else {
                for (Map.Entry<String, String> entry : ignoredSectionsValues.entrySet()) {
                    if (entry.getKey().equals(fullKey)) {
                        writer.write(ignoredSectionsValues.get(fullKey) + "\n");
                        continue keyLoop;
                    } else if (KeyBuilder.isSubKeyOf(entry.getKey(), fullKey, SEPARATOR)) {
                        continue keyLoop;
                    }
                }

                writeCommentIfExists(comments, writer, fullKey, indents);
            }

            Object currentValue = currentConfig.get(fullKey);

            if (currentValue == null)
                currentValue = defaultConfig.get(fullKey);

            String[] splitFullKey = fullKey.split("[" + SEPARATOR + "]");
            String trailingKey = splitFullKey[splitFullKey.length - 1];

            if (currentValue instanceof ConfigurationSection) {
                writer.write(indents + trailingKey + ":");

                if (!((ConfigurationSection) currentValue).getKeys(false).isEmpty())
                    writer.write("\n");
                else
                    writer.write(" {}\n");

                continue;
            }

            parserConfig.set(trailingKey, currentValue);
            String yaml = parserConfig.saveToString();
            yaml = yaml.substring(0, yaml.length() - 1).replace("\n", "\n" + indents);
            String toWrite = indents + yaml + "\n";
            parserConfig.set(trailingKey, null);
            writer.write(toWrite);
        }

        String danglingComments = comments.get(null);

        if (danglingComments != null)
            writer.write(danglingComments);

        writer.close();
    }

    //Returns a map of key comment pairs. If a key doesn't have any comments it won't be included in the map.
    private static Map<String, String> parseComments(Plugin plugin, String resourceName, FileConfiguration defaultConfig) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(plugin.getResource(resourceName)));
        Map<String, String> comments = new LinkedHashMap<>();
        StringBuilder commentBuilder = new StringBuilder();
        KeyBuilder keyBuilder = new KeyBuilder(defaultConfig, SEPARATOR);

        String line;
        while ((line = reader.readLine()) != null) {
            String trimmedLine = line.trim();

            //Only getting comments for keys. A list/array element comment(s) not supported
            if (trimmedLine.startsWith("-")) {
                continue;
            }

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

    private static Map<String, String> parseIgnoredSections(File toUpdate, FileConfiguration currentConfig, Map<String, String> comments, List<String> ignoredSections) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(toUpdate));
        Map<String, String> ignoredSectionsValues = new LinkedHashMap<>(ignoredSections.size());
        KeyBuilder keyBuilder = new KeyBuilder(currentConfig, SEPARATOR);
        StringBuilder valueBuilder = new StringBuilder();

        String currentIgnoredSection = null;
        String line;
        lineLoop : while ((line = reader.readLine()) != null) {
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#"))
                continue;

            if (trimmedLine.startsWith("-")) {
                for (String ignoredSection : ignoredSections) {
                    boolean isIgnoredParent = ignoredSection.equals(keyBuilder.toString());

                    if (isIgnoredParent || keyBuilder.isSubKeyOf(ignoredSection)) {
                        valueBuilder.append("\n").append(line);
                        continue lineLoop;
                    }
                }
            }
            
            keyBuilder.parseLine(trimmedLine);
            String fullKey = keyBuilder.toString();

            //If building the value for an ignored section and this line is no longer a part of the ignored section,
            //  write the valueBuilder, reset it, and set the current ignored section to null
            if (currentIgnoredSection != null && !KeyBuilder.isSubKeyOf(currentIgnoredSection, fullKey, SEPARATOR)) {
                ignoredSectionsValues.put(currentIgnoredSection, valueBuilder.toString());
                valueBuilder.setLength(0);
                currentIgnoredSection = null;
            }

            for (String ignoredSection : ignoredSections) {
                boolean isIgnoredParent = ignoredSection.equals(fullKey);

                if (isIgnoredParent || keyBuilder.isSubKeyOf(ignoredSection)) {
                    if (valueBuilder.length() > 0)
                        valueBuilder.append("\n");

                    String comment = comments.get(fullKey);

                    if (comment != null) {
                        String indents = KeyBuilder.getIndents(fullKey, SEPARATOR);
                        valueBuilder.append(indents).append(comment.replace("\n", "\n" + indents));//Should end with new line (\n)
                        valueBuilder.setLength(valueBuilder.length() - indents.length());//Get rid of trailing \n and spaces
                    }

                    valueBuilder.append(line);

                    //Set the current ignored section for future iterations of while loop
                    //Don't set currentIgnoredSection to any ignoredSection sub-keys
                    if (isIgnoredParent)
                        currentIgnoredSection = fullKey;

                    break;
                }
            }
        }

        reader.close();

        if (valueBuilder.length() > 0)
            ignoredSectionsValues.put(currentIgnoredSection, valueBuilder.toString());

        return ignoredSectionsValues;
    }

    private static void writeCommentIfExists(Map<String, String> comments, BufferedWriter writer, String fullKey, String indents) throws IOException {
        String comment = comments.get(fullKey);

        //Comments always end with new line (\n)
        if (comment != null)
            //Replaces all '\n' with '\n' + indents except for the last one
            writer.write(indents + comment.substring(0, comment.length() - 1).replace("\n", "\n" + indents) + "\n");
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

    private static void appendNewLine(StringBuilder builder) {
        if (builder.length() > 0)
            builder.append("\n");
    }

}
