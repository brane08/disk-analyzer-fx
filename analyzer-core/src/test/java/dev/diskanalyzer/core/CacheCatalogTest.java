package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheCatalogTest {

    @Test
    void returnsOnlyExistingLocations(@TempDir Path home) throws IOException {
        Files.createDirectories(home.resolve(".npm"));
        Files.createDirectories(home.resolve(".m2/repository"));

        List<CacheLocation> found = new CacheCatalog(home, Os.MAC).existing();

        assertEquals(List.of("npm", "Maven"), found.stream().map(CacheLocation::label).toList());
        assertEquals(home.resolve(".npm"), found.get(0).path());
    }

    @Test
    void linuxAndWindowsUseTheirOwnLocations(@TempDir Path home) throws IOException {
        Files.createDirectories(home.resolve(".cache/pip"));
        Files.createDirectories(home.resolve("AppData/Local/npm-cache"));

        assertEquals(List.of("pip"), new CacheCatalog(home, Os.LINUX).existing().stream().map(CacheLocation::label).toList());
        assertEquals(List.of("npm"), new CacheCatalog(home, Os.WINDOWS).existing().stream().map(CacheLocation::label).toList());
        assertTrue(new CacheCatalog(home, Os.MAC).existing().isEmpty());
    }

    @Test
    void emptyHomeHasNoCaches(@TempDir Path home) {
        assertTrue(new CacheCatalog(home).existing().isEmpty());
    }
}
