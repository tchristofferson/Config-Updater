package com.tchristofferson.configupdater;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigUpdaterTest {

    private static final String FILE_NAME = "config.yml";
    private static final List<String> ignoredSections = Collections.singletonList("key6-ignored");
    private static Plugin plugin;

    @BeforeClass
    public static void beforeClass() {
        plugin = mock(Plugin.class);
        when(plugin.getResource(anyString())).then((Answer<InputStream>) invocationOnMock -> ConfigUpdaterTest.class.getClassLoader().getResourceAsStream(FILE_NAME));
    }

    @AfterClass
    public static void afterClass() {
        new File(FILE_NAME).delete();
    }

    @Test
    public void testUpdateMethodToCheckIfFilesAreSameAfter() throws URISyntaxException, IOException {
        File toUpdate = new File(FILE_NAME);

        /* Save the default config */
        URL preUpdateUrl = getClass().getClassLoader().getResource(FILE_NAME);
        Path path = Paths.get(preUpdateUrl.toURI());
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(Files.newBufferedReader(path));
        configuration.save(toUpdate);

        //config.yml uses \r\n for new lines whereas after update uses \n
        String preUpdateContent = new String(Files.readAllBytes(path)).replace("\r\n", "\n");
        ConfigUpdater.update(plugin, FILE_NAME, toUpdate, ignoredSections);
        String postUpdateContent = new String(Files.readAllBytes(toUpdate.toPath())).trim();

        assertEquals(preUpdateContent, postUpdateContent);
    }
}
