package fr.emse.ismin.reservation.models;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Builds the keys used to compare names and emails without considering case.
 * <p>
 * The database cannot do it itself because SQLite only folds the case of ASCII
 * letters: "Bâtiment A" and "BÂTIMENT A" must still be seen as the same name.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * Returns the comparison key of a text: Unicode-normalized (so that an accent
     * typed as one or two characters gives the same key) and lower-cased.
     *
     * @param text the text to normalize, may be {@code null}
     * @return the comparison key, or {@code null} if the text is {@code null}
     */
    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
}
