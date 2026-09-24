package fr.emse.ismin.reservation.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests of {@link TextNormalizer}.
 */
class TextNormalizerTest {

    @Test
    void testNormalizeAccentedUpperCaseName() {
        // GIVEN the same name written in lower and upper case with accents
        String lowerCase = "Bâtiment Élysée";
        String upperCase = "BÂTIMENT ÉLYSÉE";

        // WHEN normalizing both
        // THEN they give the same key
        assertEquals(TextNormalizer.normalize(lowerCase), TextNormalizer.normalize(upperCase));
    }

    @Test
    void testNormalizeAccentTypedAsTwoCharacters() {
        // GIVEN "é" typed as one character and as "e" followed by a combining accent
        String composed = "Salle été";
        String decomposed = "Salle été";

        // WHEN normalizing both
        // THEN they give the same key
        assertEquals(TextNormalizer.normalize(composed), TextNormalizer.normalize(decomposed));
    }

    @Test
    void testNormalizeNullText() {
        // GIVEN no text
        // WHEN normalizing it
        // THEN the key is null
        assertNull(TextNormalizer.normalize(null));
    }
}
