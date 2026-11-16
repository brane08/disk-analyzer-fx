package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OsParseTest {

    @Test
    void parsesKnownOsNames() {
        assertEquals(Os.MAC, Os.parse("Mac OS X"));
        assertEquals(Os.MAC, Os.parse("Darwin"));
        assertEquals(Os.WINDOWS, Os.parse("Windows 11"));
        assertEquals(Os.LINUX, Os.parse("Linux"));
        assertEquals(Os.LINUX, Os.parse(""));
    }
}
