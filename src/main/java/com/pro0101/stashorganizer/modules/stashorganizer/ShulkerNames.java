package com.pro0101.stashorganizer.modules.stashorganizer;

import java.text.Normalizer;

/**
 * Name matching for the shulker box protection.
 * <p>
 * Fancy Unicode fonts (mathematical letters, fullwidth text, combining marks, zero-width
 * characters, formatting codes, Cyrillic/Greek lookalikes) must not let a "kit" bypass the name
 * filter. NFKC folds the cosmetic variants, then only letters and digits are compared. Common
 * leetspeak spellings ("k1t", "t00ls") are folded as well.
 * <p>
 * Pure string logic: no world, no item, no session, so it can be tested without the game.
 */
public final class ShulkerNames {
    private ShulkerNames() {
    }

    /** True when a normalized name contains the normalized pattern, leetspeak included. */
    public static boolean matches(String name, String pattern) {
        String needle = normalize(pattern);
        if (needle.isEmpty()) return false;
        if (name.contains(needle)) return true;

        return leet(name, 'i').contains(needle) || leet(name, 'l').contains(needle);
    }

    /** Folds a display name down to plain lowercase letters and digits. */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) return "";

        // NFKC folds the cosmetic variants (fullwidth, mathematical letters), then NFD splits the
        // remaining precomposed accents ("ḱ" -> "k" + mark) so only the base letter is kept.
        String normalized = Normalizer.normalize(
            Normalizer.normalize(text, Normalizer.Form.NFKC), Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder(normalized.length());

        for (int i = 0; i < normalized.length(); ) {
            int codePoint = normalized.codePointAt(i);
            i += Character.charCount(codePoint);

            if (codePoint == '§') {
                // Skip the formatting code that follows a section sign.
                if (i < normalized.length()) i += Character.charCount(normalized.codePointAt(i));
                continue;
            }

            int type = Character.getType(codePoint);
            if (type == Character.FORMAT || type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK) {
                continue;
            }

            int lower = Character.toLowerCase(codePoint);
            int latin = latinLookalike(lower);
            if (Character.isLetterOrDigit(latin)) out.appendCodePoint(latin);
        }

        return out.toString();
    }

    /** Also accepts common leetspeak spellings such as "k1t" or "t00ls". */
    private static String leet(String name, char one) {
        StringBuilder out = new StringBuilder(name.length());

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            out.append(switch (c) {
                case '0' -> 'o';
                case '1' -> one;
                case '3' -> 'e';
                case '4' -> 'a';
                case '5' -> 's';
                case '7' -> 't';
                default -> c;
            });
        }

        return out.toString();
    }

    /** Common Cyrillic/Greek characters that render like Latin letters. */
    private static int latinLookalike(int codePoint) {
        return switch (codePoint) {
            // Small caps and superscript letters used by "fancy font" generators.
            case 'ᴀ', 'ᵃ' -> 'a';
            case 'ʙ', 'ᵇ' -> 'b';
            case 'ᴄ', 'ᶜ' -> 'c';
            case 'ᴅ', 'ᵈ' -> 'd';
            case 'ᴇ', 'ᵉ' -> 'e';
            case 'ꜰ', 'ᶠ' -> 'f';
            case 'ɢ', 'ᵍ' -> 'g';
            case 'ʜ', 'ʰ' -> 'h';
            case 'ɪ', 'ⁱ' -> 'i';
            case 'ᴊ', 'ʲ' -> 'j';
            case 'ᴋ', 'ᵏ' -> 'k';
            case 'ʟ', 'ˡ' -> 'l';
            case 'ᴍ', 'ᵐ' -> 'm';
            case 'ɴ', 'ⁿ' -> 'n';
            case 'ᴏ', 'ᵒ' -> 'o';
            case 'ᴘ', 'ᵖ' -> 'p';
            case 'ǫ' -> 'q';
            case 'ʀ', 'ʳ' -> 'r';
            case 'ꜱ', 'ˢ' -> 's';
            case 'ᴛ', 'ᵗ' -> 't';
            case 'ᴜ', 'ᵘ' -> 'u';
            case 'ᴠ', 'ᵛ' -> 'v';
            case 'ᴡ', 'ʷ' -> 'w';
            case 'ʏ', 'ʸ' -> 'y';
            case 'ᴢ', 'ᶻ' -> 'z';
            // Cyrillic/Greek letters that look like Latin ones.
            case 'а', 'α' -> 'a';
            case 'в' -> 'b';
            case 'с', 'ϲ' -> 'c';
            case 'ԁ', 'đ' -> 'd';
            case 'е', 'ε' -> 'e';
            case 'ƒ' -> 'f';
            case 'һ', 'ħ' -> 'h';
            case 'ı', 'ɩ' -> 'i';
            case 'ȷ' -> 'j';
            case 'і', 'ι' -> 'i';
            case 'ј' -> 'j';
            case 'к', 'κ' -> 'k';
            case 'ӏ', 'ł' -> 'l';
            case 'м' -> 'm';
            case 'н' -> 'h';
            case 'ø' -> 'o';
            case 'о', 'ο' -> 'o';
            case 'р', 'ρ' -> 'p';
            case 'ѕ' -> 's';
            case 'т', 'τ' -> 't';
            case 'υ' -> 'u';
            case 'ѵ' -> 'v';
            case 'х', 'χ' -> 'x';
            case 'у' -> 'y';
            default -> codePoint;
        };
    }
}
