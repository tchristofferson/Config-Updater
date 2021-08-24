package com.tchristofferson.configupdater;

import org.bukkit.configuration.file.FileConfiguration;

public class KeyBuilder {

    private final FileConfiguration config;
    private final char separator;
    private final StringBuilder builder = new StringBuilder();

    public KeyBuilder(FileConfiguration config, char separator) {
        this.config = config;
        this.separator = separator;
    }

    public void parseLine(String line) {
        line = line.trim();
        String[] currentSplitLine = line.split(":");

        //Checks keyBuilder path against defaultConfig to see if the path is valid.
        //If the path doesn't exist in the default config it keeps removing last key in keyBuilder.
        while (builder.length() > 0 && !config.contains(builder.toString() + separator + currentSplitLine[0])) {
            removeLastKey();
        }

        //Add the separator if there is already a key inside keyBuilder
        //If currentSplitLine[0] is 'key2' and keyBuilder contains 'key1' the result will be 'key1.' if '.' is the separator
        if (builder.length() > 0)
            builder.append(separator);

        //Appends the current key to keyBuilder
        //If keyBuilder is 'key1.' and currentSplitLine[0] is 'key2' the resulting keyBuilder will be 'key1.key2' if separator is '.'
        builder.append(currentSplitLine[0]);
    }

    public String getLastKey() {
        if (builder.length() == 0)
            return "";

        return builder.toString().split("[" + separator + "]")[0];
    }

    public boolean isEmpty() {
        return builder.length() == 0;
    }

    public boolean isSubKey(String key) {
        return isSubKey(builder.toString(), key, separator);
    }

    public static boolean isSubKey(String parentKey, String subKey, char separator) {
        if (parentKey.isEmpty())
            return false;

        return parentKey.startsWith(subKey)
                && subKey.substring(parentKey.length()).startsWith(String.valueOf(separator));
    }

    public boolean isConfigSection() {
        String key = builder.toString();
        return config.isConfigurationSection(key);
    }

    public boolean isConfigSectionWithKeys() {
        String key = builder.toString();
        return config.isConfigurationSection(key) && !config.getConfigurationSection(key).getKeys(false).isEmpty();
    }

    //Input: 'key1.key2' Result: 'key1'
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

    @Override
    public String toString() {
        return builder.toString();
    }

}
