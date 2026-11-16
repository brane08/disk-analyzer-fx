package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiScaleTest {

    @Test
    void explicitGlassPropertyWins() {
        assertEquals(Optional.empty(), UiScale.override("2", "1.5", "1.25"));
    }

    @Test
    void systemPropertyBeatsEnvironment() {
        assertEquals(Optional.of("1.5"), UiScale.override(null, "1.5", "2"));
    }

    @Test
    void environmentUsedWhenNoSystemProperty() {
        assertEquals(Optional.of("150%"), UiScale.override("", " ", " 150% "));
    }

    @Test
    void nothingSetLeavesDetectionAlone() {
        assertEquals(Optional.empty(), UiScale.override(null, null, null));
    }

    @Test
    void junkIsIgnored() {
        assertEquals(Optional.empty(), UiScale.override(null, "abc", null));
        assertEquals(Optional.empty(), UiScale.override(null, "0x10", null));
        assertEquals(Optional.empty(), UiScale.override(null, "1.5; rm", null));
    }

    @Test
    void javafxUnparseableOrZeroValuesAreRejected() {
        assertEquals(Optional.empty(), UiScale.override(null, "150.5%", null));
        assertEquals(Optional.empty(), UiScale.override(null, "0", null));
        assertEquals(Optional.empty(), UiScale.override(null, "0%", null));
        assertEquals(Optional.empty(), UiScale.override(null, "0.00", null));
    }

    @Test
    void invalidPropertyFallsThroughToEnvironment() {
        assertEquals(Optional.of("2"), UiScale.override(null, "abc", "2"));
    }
}
