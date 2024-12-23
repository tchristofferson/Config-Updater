package com.tchristofferson.configupdater;

import com.google.common.base.Preconditions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.YamlConstructor;
import org.bukkit.configuration.file.YamlRepresenter;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConfigUpdater {

    //Used for separating keys in the keyBuilder inside parseComments method
    private static final char SEPARATOR = '.';

	/**
	 * Update the YAML file inside the plugin folder, only if it does not match the file from the JAR.
	 *
	 * @param plugin          the main class instance where you extend JavaPlugin.
	 * @param resourceName    the path to your original YAML file inside the JAR.
	 * @param toUpdate        the file you want to update.
	 * @param ignoredSections the array of ignored section values, where each element represents the full path or the first path of the ignored section
	 *                        and the value is the YAML content to keep unchanged.
	 * @throws IOException if an I/O error occurs when writing to BufferedWriter or if the file does not exist,
	 *                     is a directory rather than a regular file, or for some other reason cannot be opened for reading.
	 */
    public static void update(Plugin plugin, String resourceName, File toUpdate, String... ignoredSections) throws IOException {
        update(plugin, resourceName, toUpdate, Arrays.asList(ignoredSections));
    }

	/**
	 * Update the YAML file inside the plugin folder, only if it does not match the file from the JAR.
	 *
	 * @param plugin the main class instance where you extend JavaPlugin.
	 * @param resourceName the path to your original YAML file inside the JAR.
	 * @param toUpdate the file you want to update.
	 * @param ignoredSections the list of ignored section values, where each element represents the full path or the first path of
	 *                           the ignored section and the value is the YAML content to keep unchanged.
	 * @throws IOException if an I/O error occurs when writing to BufferedWriter or if the file does not exist,
	 *                     is a directory rather than a regular file, or for some other reason cannot be opened for reading.
	 */
    public static void update(Plugin plugin, String resourceName, File toUpdate, List<String> ignoredSections) throws IOException {
        Preconditions.checkArgument(toUpdate.exists(), "The toUpdate file doesn't exist!");

        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource(resourceName), StandardCharsets.UTF_8));
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(Files.newBufferedReader(toUpdate.toPath(), StandardCharsets.UTF_8));
        Map<String, String> comments = parseComments(plugin, resourceName, defaultConfig);
        Map<String, String> ignoredSectionsValues = parseIgnoredSections(toUpdate, comments, ignoredSections == null ? Collections.emptyList() : ignoredSections);
        // will write updated config file "contents" to a string
        StringWriter writer = new StringWriter();
        write(defaultConfig, currentConfig, new BufferedWriter(writer), comments, ignoredSectionsValues);
        String value = writer.toString(); // config contents

        Path toUpdatePath = toUpdate.toPath();
        if (!value.equals(new String(Files.readAllBytes(toUpdatePath), StandardCharsets.UTF_8))) { // if updated contents are not the same as current file contents, update
            Files.write(toUpdatePath, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Writes the updated configuration data to the specified BufferedWriter, including comments and ignored sections.
     *
     * @param defaultConfig the configuration from the JAR file, representing the default values.
     * @param currentConfig the configuration from the file inside the plugin folder, containing the current values.
     * @param writer the BufferedWriter instance used to write the updated data.
     * @param comments the map of comments to write, where the key represents the full path to where the comments will be added.
     * @param ignoredSectionsValues the map of ignored section values, where the key is the full path of the ignored section and the value is the YAML content to keep unchanged.
     * @throws IOException if an I/O error occurs while writing the data to the BufferedWriter.
     */
    private static void write(FileConfiguration defaultConfig, FileConfiguration currentConfig, BufferedWriter writer, Map<String, String> comments, Map<String, String> ignoredSectionsValues) throws IOException {
        //Used for converting objects to yaml, then cleared
        FileConfiguration parserConfig = new YamlConfiguration();

       for (String fullKey : defaultConfig.getKeys(true)) {
            String indents = KeyUtils.getIndents(fullKey, SEPARATOR);


           if (!ignoredSectionsValues.isEmpty()) {
               if (writeIgnoredSectionValueIfExists(ignoredSectionsValues, writer, fullKey))
                   continue;
           }
           writeCommentIfExists(comments, writer, fullKey, indents);
           Object currentValue = currentConfig.get(fullKey);

           if (currentValue == null)
               currentValue = defaultConfig.get(fullKey);

           String[] splitFullKey = fullKey.split("[" + SEPARATOR + "]");
           String trailingKey = splitFullKey[splitFullKey.length - 1];

           if (currentValue instanceof ConfigurationSection) {
               writeConfigurationSection(writer, indents, trailingKey, (ConfigurationSection) currentValue);
               continue;
           }
           writeYamlValue(parserConfig, writer, indents, trailingKey, currentValue);
       }

        String danglingComments = comments.get(null);

        if (danglingComments != null)
            writer.write(danglingComments);

        writer.close();
    }

    /**
     * Parses comments from the YAML resource file inside the JAR and returns a map of key-comment pairs.
     *
     * @param plugin        the main class instance where you extend JavaPlugin.
     * @param resourceName  the path to your original YAML file inside the JAR.
     * @param defaultConfig the FileConfiguration representing the YAML file inside the JAR.
     * @return a map containing key-comment pairs. If a key doesn't have any comments, it won't be included in the map.
     * @throws IOException if an I/O error occurs while writing the comments.
     */
    private static Map<String, String> parseComments(Plugin plugin, String resourceName, FileConfiguration defaultConfig) throws IOException {
        //keys are in order
        List<String> keys = new ArrayList<>(defaultConfig.getKeys(true));
        BufferedReader reader = new BufferedReader(new InputStreamReader(plugin.getResource(resourceName), StandardCharsets.UTF_8));
        Map<String, String> comments = new LinkedHashMap<>();
        StringBuilder commentBuilder = new StringBuilder();
        KeyBuilder keyBuilder = new KeyBuilder(defaultConfig, SEPARATOR);
        String currentValidKey = null;

        String line;
        while ((line = reader.readLine()) != null) {
            String trimmedLine = line.trim();
            //Only getting comments for keys. A list/array element comment(s) not supported
            if (trimmedLine.startsWith("-")) continue;

            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {//Is blank line or is comment
                commentBuilder.append(trimmedLine).append("\n");
            } else {//is a valid yaml key
                //This part verifies if it is the first non-nested key in the YAML file and then stores the result as the next non-nested value.
                if (!line.startsWith(" ")) {
                    keyBuilder.clear();//add clear method instead of create new instance.
                    currentValidKey = trimmedLine;
                }

                keyBuilder.parseLine(trimmedLine, true);
                String key = keyBuilder.toString();

                //If there is a comment associated with the key it is added to comments map and the commentBuilder is reset
                if (commentBuilder.length() > 0) {
                    comments.put(key, commentBuilder.toString());
                    commentBuilder.setLength(0);
                }

                int nextKeyIndex = keys.indexOf(keyBuilder.toString()) + 1;
                if (nextKeyIndex < keys.size()) {

                    String nextKey = keys.get(nextKeyIndex);
                    while (!keyBuilder.isEmpty() && !nextKey.startsWith(keyBuilder.toString())) {
                        keyBuilder.removeLastKey();
                    }
                    //If all keys are cleared in a loop, then the first key from the nested keys in the YAML file is assigned to this keyBuilder instance.
                    //If the file contains multiple non-nested keys, the next first non-nested key will be used.
                    if (keyBuilder.isEmpty()) {
                        keyBuilder.parseLine(currentValidKey, false);
                    }
                }
            }
        }
        reader.close();

        if (commentBuilder.length() > 0)
            comments.put(null, commentBuilder.toString());

        return comments;
    }

    /**
     * Parses through the ignored sections of the YAML file and returns a map containing the sections,
     * along with their values, comments, and path names.
     *
     * @param toUpdate the file you want to update with the ignored sections.
     * @param comments the map of comments you want to add to the YAML file. The key of each entry in the map is
     *                 the full path to the section where you want to add the comment, and the value is the comment itself.
     * @param ignoredSections the list of sections that will not be changed during the update. Where the elements are the full
     *                        path or the first section that will be ignored.
     * @return a map containing the YAML sections to be written to the file, along with their values, comments, and path names.
     * @throws IOException if the file does not exist, is a directory rather than a regular file, or for some other reason cannot be opened for reading.
     */
    private static Map<String, String> parseIgnoredSections(File toUpdate, Map<String, String> comments, List<String> ignoredSections) throws IOException {
        Map<String, String> ignoredSectionValues = new LinkedHashMap<>(ignoredSections.size());

        DumperOptions options = new DumperOptions();
        options.setLineBreak(DumperOptions.LineBreak.UNIX);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(new YamlConstructor(), new YamlRepresenter(), options);

        Map<Object, Object> root = (Map<Object, Object>) yaml.load(new FileReader(toUpdate));
        ignoredSections.forEach(section -> {
            String[] split = section.split("[" + SEPARATOR + "]");
            String key = split[split.length - 1];
            Map<Object, Object> map = getSection(section, root);

            StringBuilder keyBuilder = new StringBuilder();
            for (int i = 0; i < split.length; i++) {
                if (i != split.length - 1) {
                    if (keyBuilder.length() > 0)
                        keyBuilder.append(SEPARATOR);

                    keyBuilder.append(split[i]);
                }
            }

            ignoredSectionValues.put(section, buildIgnored(key, map, comments, keyBuilder, new StringBuilder(), yaml));
        });
        return ignoredSectionValues;
    }

    /**
     * Recursively retrieves a specific section from the YAML file based on the provided full path.
     *
     * @param fullKey the full path to the desired section in the YAML file.
     * @param root the root section of the YAML file.
     * @return the map representing the desired section from the YAML file.
     * @throws IllegalArgumentException if the specified section is not a ConfigurationSection or is invalid.
     */
    private static Map<Object, Object> getSection(String fullKey, Map<Object, Object> root) {
        String[] keys = fullKey.split("[" + SEPARATOR + "]", 2);
        String key = keys[0];
        Object value = root.get(getKeyAsObject(key, root));

        if (keys.length == 1) {
            if (value instanceof Map)
                return root;
	   /*     if (value == null) {
                Map<Object, Object>  map= new HashMap<>();
                map.put(key,"{}");
                System.out.println("key " + key);
                return  map;
            }*/
            throw new IllegalArgumentException("Ignored sections must be a ConfigurationSection not a value!");
        }

        if (!(value instanceof Map))
            throw new IllegalArgumentException("Invalid ignored ConfigurationSection specified!");

        return getSection(keys[1], (Map<Object, Object>) value);
    }

    /**
     * Recursively builds the ignored path and values back to the file.
     *
     * @param fullKey the full path to the current section in the YAML file.
     * @param ymlMap the map of sections to write.
     * @param comments the commits to add back to the file.
     * @param keyBuilder the StringBuilder containing the current path being read from the file.
     * @param ignoredBuilder the StringBuilder instance to write the data to.
     * @param yaml the Yaml instance used to serialize the Java object into a YAML String.
     * @return the built ignored path and values as a String.
     * @throws IllegalArgumentException if an invalid ignored section is encountered during the process.
     */
    private static String buildIgnored(String fullKey, Map<Object, Object> ymlMap, Map<String, String> comments, StringBuilder keyBuilder, StringBuilder ignoredBuilder, Yaml yaml) {
        //0 will be the next key, 1 will be the remaining keys
        String[] keys = fullKey.split("[" + SEPARATOR + "]", 2);
        String key = keys[0];
        Object originalKey = getKeyAsObject(key, ymlMap);

        if (keyBuilder.length() > 0)
            keyBuilder.append(".");

        keyBuilder.append(key);

        if (!ymlMap.containsKey(originalKey)) {
            if (keys.length == 1)
                throw new IllegalArgumentException("Invalid ignored section: " + keyBuilder);

            throw new IllegalArgumentException("Invalid ignored section: " + keyBuilder + "." + keys[1]);
        }

        String comment = comments.get(keyBuilder.toString());
        String indents = KeyUtils.getIndents(keyBuilder.toString(), SEPARATOR);

        if (comment != null)
            ignoredBuilder.append(addIndentation(comment, indents)).append("\n");

        ignoredBuilder.append(addIndentation(key, indents)).append(":");
        Object obj = ymlMap.get(originalKey);

        if (obj instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) obj;

            if (map.isEmpty()) {
                ignoredBuilder.append(" {}\n");
            } else {
                ignoredBuilder.append("\n");
            }

            StringBuilder preLoopKey = new StringBuilder(keyBuilder);

            for (Object o : map.keySet()) {
                buildIgnored(o.toString(), map, comments, keyBuilder, ignoredBuilder, yaml);
                keyBuilder = new StringBuilder(preLoopKey);
            }
        } else {
            writeIgnoredValue(yaml, obj, ignoredBuilder, indents);
        }

        return ignoredBuilder.toString();
    }

    /**
     * Writes the ignored section to the file without making any changes.
     *
     * @param yaml the Yaml instance used to serialize the Java object into a YAML String.
     * @param toWrite the object you want to write to the file as an ignored section.
     * @param ignoredBuilder the StringBuilder instance to write the data to.
     * @param indents the number of spaces used for indentation in the YAML representation.
     */
    private static void writeIgnoredValue(Yaml yaml, Object toWrite, StringBuilder ignoredBuilder, String indents) {
        String yml = yaml.dump(toWrite);
        if (toWrite instanceof Collection) {
            ignoredBuilder.append("\n").append(addIndentation(yml, indents)).append("\n");
        } else {
            ignoredBuilder.append(" ").append(yml);
        }
    }

    /**
     * Adds the specified number of indents to each line of the given string.
     *
     * @param s the string to which indents are added.
     * @param indents the indents to add to each line.
     * @return the provided string with the correct number of indentations applied.
     */
    private static String addIndentation(String s, String indents) {
        StringBuilder builder = new StringBuilder();
        String[] split = s.split("\n");

        for (String value : split) {
            if (builder.length() > 0)
                builder.append("\n");

            builder.append(indents).append(value);
        }

        return builder.toString();
    }

    /**
     * Writes the specified commit to the provided buffer writer. If the commit exist for this path.
     *
     * @param comments the map containing key-value pairs of commits where the key represents the path from the YAML file.
     * @param writer the BufferedWriter instance used to write the commit value.
     * @param fullKey the key representing the path from the YAML file.
     * @param indents the number of spaces used for indentation.
     * @throws IOException If an I/O error occurs while writing the YAML commits.
     */
    private static void writeCommentIfExists(Map<String, String> comments, BufferedWriter writer, String fullKey, String indents) throws IOException {
        String comment = comments.get(fullKey);

        //Comments always end with new line (\n)
        if (comment != null)
            //Replaces all '\n' with '\n' + indents except for the last one
            writer.write(indents + comment.substring(0, comment.length() - 1).replace("\n", "\n" + indents) + "\n");
    }

    /**
     * Attempts to find the correct key in the sectionContext using the provided key and section context.
     *
     * @param key the YAML key to be searched for in the section.
     * @param sectionContext the configuration section (Map) from the YAML file.
     * @return the value associated with the correct key in the configuration section, or null if not found.
     */
    private static Object getKeyAsObject(String key, Map<Object, Object> sectionContext) {
        if (sectionContext.containsKey(key))
            return key;

        try {
            Float keyFloat = Float.parseFloat(key);

            if (sectionContext.containsKey(keyFloat))
                return keyFloat;
        } catch (NumberFormatException ignored) {}

        try {
            Double keyDouble = Double.parseDouble(key);

            if (sectionContext.containsKey(keyDouble))
                return keyDouble;
        } catch (NumberFormatException ignored) {}

        try {
            Integer keyInteger = Integer.parseInt(key);

            if (sectionContext.containsKey(keyInteger))
                return keyInteger;
        } catch (NumberFormatException ignored) {}

        try {
            Long longKey = Long.parseLong(key);

            if (sectionContext.containsKey(longKey))
                return longKey;
        } catch (NumberFormatException ignored) {}

        return null;
    }

	/**
	 * Writes the current value with the provided trailing key to the provided writer.
	 *
	 * @param parserConfig   The parser configuration to use for writing the YAML value.
	 * @param bufferedWriter The writer to write the value to.
	 * @param indents        The string representation of the indentation.
	 * @param trailingKey    The trailing key for the YAML value.
	 * @param currentValue   The current value to write as YAML.
	 * @throws IOException If an I/O error occurs while writing the YAML value.
	 */
	private static void writeYamlValue(final FileConfiguration parserConfig, final BufferedWriter bufferedWriter, final String indents, final String trailingKey, final Object currentValue) throws IOException {
		parserConfig.set(trailingKey, currentValue);
		String yaml = parserConfig.saveToString();
		yaml = yaml.substring(0, yaml.length() - 1).replace("\n", "\n" + indents);
		final String toWrite = indents + yaml + "\n";
		parserConfig.set(trailingKey, null);
		bufferedWriter.write(toWrite);
	}

    /**
     * Writes the value associated with the ignored section to the provided writer,
     * if it exists in the ignoredSectionsValues map.
     *
     * @param ignoredSectionsValues The map containing the ignored section-value mappings.
     * @param bufferedWriter        The writer to write the value to.
     * @param fullKey               The full key to search for in the ignoredSectionsValues map.
     * @throws IOException If an I/O error occurs while writing the value.
     */
    private static boolean writeIgnoredSectionValueIfExists(final Map<String, String> ignoredSectionsValues, final BufferedWriter bufferedWriter, final String fullKey) throws IOException {
        String ignored = ignoredSectionsValues.get(fullKey);
        if (ignored != null) {
            bufferedWriter.write(ignored);
            return true;
        }
        for (final Map.Entry<String, String> entry : ignoredSectionsValues.entrySet()) {
            if (KeyUtils.isSubKeyOf(entry.getKey(), fullKey, SEPARATOR)) {
                return true;
            }
        }
        return false;
    }

	/**
	 * Writes a configuration section with the provided trailing key and the current value to the provided writer.
	 *
	 * @param bufferedWriter The writer to write the configuration section to.
	 * @param indents        The string representation of the indentation level.
	 * @param trailingKey    The trailing key for the configuration section.
	 * @param configurationSection   The current value of the configuration section.
	 * @throws IOException If an I/O error occurs while writing the configuration section.
	 */
	private static void writeConfigurationSection(final BufferedWriter bufferedWriter, final String indents, final String trailingKey, final ConfigurationSection configurationSection) throws IOException {
		bufferedWriter.write(indents + trailingKey + ":");
		if (!(configurationSection).getKeys(false).isEmpty()) {
			bufferedWriter.write("\n");
		} else {
			bufferedWriter.write(" {}\n");
		}
	}
}
