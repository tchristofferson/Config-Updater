package com.tchristofferson.configupdater;

import org.bukkit.configuration.file.FileConfiguration;

public class KeyBuilder implements Cloneable {

    private final FileConfiguration config;
    private final char separator;
    private final StringBuilder builder;

    /**
     * Constructs a new KeyBuilder instance.
     *
     * @param config the FileConfiguration to work with.
     * @param separator the character used as the separator between key parts. The default separator is a dot ('.').
     */
    public KeyBuilder(FileConfiguration config, char separator) {
        this.config = config;
        this.separator = separator;
        this.builder = new StringBuilder();
    }

    /**
     * Constructs a new KeyBuilder instance as a clone of another KeyBuilder.
     *
     * @param keyBuilder the KeyBuilder to clone.
     */
    private KeyBuilder(KeyBuilder keyBuilder) {
        this.config = keyBuilder.config;
        this.separator = keyBuilder.separator;
        this.builder = new StringBuilder(keyBuilder.toString());
    }

    /**
     * Parses the line to check if it represents a valid YAML path. If the
     * config does not contain the path, it removes the last part of the line
     * until it finds a valid path or becomes empty.
     *
     * @param line the line to check if it belongs to the current path set in the {@link #builder}.
     * @param checkIfExists set to true to check if the path is valid in the config.
     */
    public void parseLine(String line, boolean checkIfExists) {
        line = line.trim();

        String[] currentSplitLine = line.split(":");

        if (currentSplitLine.length > 2)
            currentSplitLine = line.split(": ");

        String key = currentSplitLine[0].replace("'", "").replace("\"", "");

        if (checkIfExists) {
            //Checks keyBuilder path against config to see if the path is valid.
            //If the path doesn't exist in the config it keeps removing last key in keyBuilder.
            while (builder.length() > 0 && !config.contains(builder.toString() + separator + key)) {
                removeLastKey();
            }
        }

        //Add the separator if there is already a key inside keyBuilder
        //If currentSplitLine[0] is 'key2' and keyBuilder contains 'key1' the result will be 'key1.' if '.' is the separator
        if (builder.length() > 0)
            builder.append(separator);

        //Appends the current key to keyBuilder
        //If keyBuilder is 'key1.' and currentSplitLine[0] is 'key2' the resulting keyBuilder will be 'key1.key2' if separator is '.'
        builder.append(key);
    }

    /**
     * Gets the last key in the builder.
     *
     * @return the last key, or an empty string if the builder is empty.
     */
    public String getLastKey() {
        if (builder.length() == 0)
            return "";

        return builder.toString().split("[" + separator + "]")[0];
    }

    /**
     * Checks if the builder is empty.
     *
     * @return true if the builder is empty; otherwise, false.
     */
    public boolean isEmpty() {
        return builder.length() == 0;
    }

    /**
     * Clears the contents of the builder.
     */
    public void clear() {
        builder.setLength(0);
    }
    /**
     * Checks if the full key path represented by this instance is a sub-key of the specified parent key.
     *
     * @param parentKey the parent key to check against.
     * @return true if the full key path is a sub-key of the parentKey; otherwise, false.
     */
    public boolean isSubKeyOf(String parentKey) {
        return KeyUtils.isSubKeyOf(parentKey, builder.toString(), separator);
    }

    /**
     * Checks if the specified subKey is a sub-key of the key path represented by this instance.
     *
     * @param subKey the sub-key to check.
     * @return true if the subKey is a sub-key of the key path; otherwise, false.
     */
    public boolean isSubKey(String subKey) {
        return KeyUtils.isSubKeyOf(builder.toString(), subKey, separator);
    }

    /**
     * Checks if the key path represented by this instance is a configuration section in the FileConfiguration.
     *
     * @return true if the key path is a configuration section; otherwise, false.
     */
    public boolean isConfigSection() {
        String key = builder.toString();
        return config.isConfigurationSection(key);
    }

    /**
     * Checks if the key path represented by this instance is a non-empty configuration section in the FileConfiguration.
     *
     * @return true if the key path is a non-empty configuration section; otherwise, false.
     */
    public boolean isConfigSectionWithKeys() {
        String key = builder.toString();
        return config.isConfigurationSection(key) && !config.getConfigurationSection(key).getKeys(false).isEmpty();
    }

    /**
     * Removes the last key from the builder.
     *
     * For example, if the input is 'key1.key2', the result will be 'key1'.
     */
    public void removeLastKey() {
        if (builder.length() == 0)
            return;

        String keyString = builder.toString();
        //Must be enclosed in brackets in case a regex special character is the separator
        String[] split = keyString.split("[" + separator + "]");
        //Makes sure begin index isn't < 0 (error). Occurs when there is only one key in the path
        int minIndex = Math.max(0, builder.length() - split[split.length - 1].length() - 1);
        builder.replace(minIndex, builder.length(), "");
    }

    /**
     * Returns the current key path as a string.
     *
     * @return the current key path.
     */
    @Override
    public String toString() {
        return builder.toString();
    }

    /**
     * Creates a clone of this KeyBuilder instance.
     *
     * @return a new KeyBuilder instance with the same contents as this instance.
     */
    @Override
    protected KeyBuilder clone() {
        return new KeyBuilder(this);
    }
}
