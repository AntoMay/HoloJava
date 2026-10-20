package me.holojava.fullgame;

import me.holojava.fullgame.ContentCatalog.CollabDef;
import me.holojava.fullgame.ContentCatalog.CharacterDef;
import me.holojava.fullgame.ContentCatalog.WeaponDef;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Vector;

/**
 * Parses the wiki catalog data from CSV files and generates
 * runtime definitions for the full game.
 *
 * <p>Everything here is written against the CLDC 1.1 API surface, because this
 * class runs from a static initializer: a missing class shows up as a
 * NoClassDefFoundError there, which the IOException the caller catches cannot
 * turn into the fallback catalog. That means no ArrayList, no StringBuilder,
 * no BufferedReader and no String.isEmpty()/String.matches(), none of which
 * exist on a CLDC handset — the desktop JDK has all of them, so a violation
 * compiles cleanly and only fails on the phone.
 *
 * @see ContentCatalog#CollabDef
 */
public class WikiCatalogData {

    /**
     * Build collab definitions from the local collabs catalog.
     * Each line in the CSV represents one collab definition.
     * Super collabs (type "super_collab") have an item as weapon_b
     * and are handled separately from ordinary collabs.
     *
     * @param catalogReader source of collab CSV data (first line is header)
     * @return array of CollabDef objects, one per CSV row
     * @throws IOException if the CSV cannot be read
     */
    public static CollabDef[] buildCollabDefs(InputStream catalogReader) throws IOException {
        Reader reader = new InputStreamReader(catalogReader);
        String line;
        Vector list = new Vector();

        // Skip header line
        if (readLine(reader) == null) {
            return new CollabDef[0];
        }

        while ((line = readLine(reader)) != null) {
            if (trim(line).length() == 0) {
                continue;
            }
            // Parse CSV fields respecting quoted values
            String[] fields = parseCsvLine(line);
            if (fields.length < 5) {
                continue; // malformed line, skip
            }

            String id = trim(fields[0]);
            String name = trim(fields[1]);
            // data/wiki/collabs.csv stores display names (verified against the
            // name column of data/wiki/weapons.csv, e.g. "Bounce Ball"), while
            // the runtime catalog keys weapons by id (e.g. "bounce_ball").
            // Normalize here so fillOffer matching and weaponLevelById work.
            // The CSV itself is unchanged; unresolvable names stay as-is and
            // are skipped by the offer gate instead of crashing.
            String weaponA = toWeaponId(trim(fields[2]));
            String weaponB = toWeaponId(trim(fields[3]));
            String type = trim(fields[4]);
            String source = fields.length > 5 ? trim(fields[5]) : "";
            String coverage = fields.length > 6 ? trim(fields[6]) : "";

            // Validate basic requirements
            if (id.length() == 0 || name.length() == 0) {
                continue; // skip invalid entries
            }

            // Validate ID format
            if (!isValidId(id)) {
                continue; // reject IDs with invalid characters
            }

            CollabDef def = new CollabDef();
            def.id = id;
            def.name = name;
            def.weaponA = weaponA;
            def.weaponB = weaponB;
            def.type = type;
            def.source = source;


            list.addElement(def);
        }

        CollabDef[] result = new CollabDef[list.size()];
        list.copyInto(result);
        return result;
    }

    /**
     * Reads one line, dropping the terminator. Handles both LF and CRLF so the
     * catalog parses the same whichever line endings the file carries.
     *
     * <p>CLDC has no BufferedReader.readLine, so the line is assembled here from
     * single characters.
     *
     * @return the next line without its terminator, or null at end of stream
     */
    private static String readLine(Reader reader) throws IOException {
        StringBuffer line = new StringBuffer();
        int c = reader.read();
        if (c < 0) {
            return null;
        }
        while (c >= 0 && c != '\n') {
            if (c != '\r') {
                line.append((char) c);
            }
            c = reader.read();
        }
        return line.toString();
    }

    /** Parse a CSV line respecting double-quoted fields. */
    private static String[] parseCsvLine(String line) {
        Vector fields = new Vector();
        StringBuffer current = new StringBuffer();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.addElement(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.addElement(current.toString());
        String[] result = new String[fields.size()];
        fields.copyInto(result);
        return result;
    }

    static String trim(String s) {
        if (s == null) return "";
        return s.trim();
    }

    /**
     * True when every character is one the id grammar allows: ASCII letters,
     * digits, underscore or hyphen. The empty string is rejected.
     *
     * <p>This is the hand-rolled stand-in for {@code String.matches("[a-zA-Z0-9_-]+")},
     * which needs {@code java.util.regex} — absent from CLDC.
     */
    static boolean isValidId(String s) {
        if (s == null || s.length() == 0) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /**
     * Maps a wiki display name to a runtime weapon id.
     * Verified mapping rule against data/wiki/weapons.csv: lowercase, drop
     * apostrophes/quotes/dots, collapse spaces and hyphens to underscores
     * ("Bounce Ball" -&gt; "bounce_ball", "EN's Curse" -&gt; "ens_curse",
     * "X-Potato" -&gt; "x_potato"). Values already in id form pass through.
     */
    static String toWeaponId(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase();
        StringBuffer out = new StringBuffer();
        boolean lastUnderscore = true;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_') {
                out.append(c);
                lastUnderscore = false;
            } else if (c == ' ' || c == '-') {
                if (!lastUnderscore) {
                    out.append('_');
                    lastUnderscore = true;
                }
            }
            // any other character (apostrophe, quote, dot) is dropped
        }
        String id = out.toString();
        // StringBuffer has no substring(0, n) guard against a trailing separator,
        // so trim the trailing underscores here.
        int n = id.length();
        while (n > 0 && id.charAt(n - 1) == '_') n--;
        return id.substring(0, n);
    }

    /**
     * Validation rules for collab definitions.
     * Super collabs have extra item prerequisites (weapon_b is an item, not a weapon)
     * and are validated differently from ordinary collabs.
     *
     * @param def the collab definition to validate
     * @param catalog the content catalog for weapon/item lookups
     * @return true if the collab definition is valid, false otherwise
     */
    public static boolean isValidCollab(CollabDef def, ContentCatalog catalog) {
        if (def == null || def.id == null || def.id.length() == 0) {
            return false;
        }

        // Check that the primary weapon reference (weaponA) can be resolved
        if (def.weaponA != null && def.weaponA.length() > 0) {
            if (catalog.weapon(def.weaponA) == null) {
                // Weapon not found in catalog; the collab may reference
                // a weapon that hasn't been loaded yet, so we defer fully.
                // But reject clearly invalid IDs.
                if (!isValidId(def.weaponA)) {
                    return false;
                }
            }
        }

        // Check weaponB:
        // - For ordinary collabs ("collab" type), weaponB should be a valid weapon ID
        // - For super collabs ("super_collab" type), weapon_b is an item (e.g., Hope Soda, Sake)
        //   and is handled by the super collab system, not the ordinary collab system.
        if (def.weaponB != null && def.weaponB.length() > 0) {
            if ("super_collab".equals(def.type)) {
                // Super collab: weapon_b is an item prerequisite, not a weapon.
                // These are handled separately; just validate the ID format.
                if (!isValidId(def.weaponB)) {
                    return false;
                }
            } else {
                // Ordinary collab: weaponB should reference an existing weapon
                if (catalog.weapon(def.weaponB) == null) {
                    // Deferred lookup; validate format at minimum
                    if (!isValidId(def.weaponB)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }



    /**
     * Returns the playable character roster defined in {@code ContentCatalog.BASE_CHARACTERS}.
     * Entries are limited to characters with verified local animation assets and a main weapon
     * that resolves against {@code ContentCatalog.BASE_WEAPONS}; see {@code ContentCatalog.validate()}.
     *
     * <p>Does not read {@code /data/wiki/characters.csv}. That resource is not packaged into the
     * build output, and its rows use display-name weapons that would fail validation.
     */
    static final CharacterDef[] buildCharacters(CharacterDef[] baseCharacters) {
        return baseCharacters;
    }

    /**
     * Stub method: returns weapons built from the base list. Full implementation would
     * parse weapons.csv and apply character-specific mappings.
     */
    static final WeaponDef[] buildWeapons(WeaponDef[] baseWeapons) {
        return baseWeapons;
    }

}
