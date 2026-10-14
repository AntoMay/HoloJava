package me.holojava.fullgame;

import me.holojava.fullgame.ContentCatalog.CollabDef;
import me.holojava.fullgame.ContentCatalog.CharacterDef;
import me.holojava.fullgame.ContentCatalog.WeaponDef;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Parses the wiki catalog data from CSV files and generates
 * runtime definitions for the full game.
 * 
 * @see ContentCatalog#CollabDef
 */
public class WikiCatalogData {

    /**
     * Build collab definitions from the local collabs.csv catalog.
     * Each line in the CSV represents one collab definition.
     * Super collabs (type "super_collab") have an item as weapon_b
     * and are handled separately from ordinary collabs.
     *
     * @param catalogReader source of collabs CSV data (first line is header)
     * @return array of CollabDef objects, one per CSV row
     * @throws IOException if the CSV cannot be parsed
     */
    public static CollabDef[] buildCollabDefs(InputStream catalogReader) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(catalogReader));
        String line;
        java.util.ArrayList<CollabDef> list = new java.util.ArrayList<>();

        // Skip header line
        if ((line = reader.readLine()) == null) {
            return new CollabDef[0];
        }

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            // Parse CSV fields respecting quoted values
            String[] fields = parseCsvLine(line);
            if (fields.length < 5) {
                continue; // malformed line, skip
            }

            String id = trim(fields[0]);
            String name = trim(fields[1]);
            String weaponA = trim(fields[2]);
            String weaponB = trim(fields[3]);
            String type = trim(fields[4]);
            String source = fields.length > 5 ? trim(fields[5]) : "";
            String coverage = fields.length > 6 ? trim(fields[6]) : "";

            // Validate basic requirements
            if (id.isEmpty() || name.isEmpty()) {
                continue; // skip invalid entries
            }

            // Validate ID format
            if (!id.matches("[a-zA-Z0-9_\\-]+")) {
                continue; // reject IDs with invalid characters
            }

            CollabDef def = new CollabDef();
            def.id = id;
            def.name = name;
            def.weaponA = weaponA;
            def.weaponB = weaponB;
            def.type = type;
            def.source = source;


            list.add(def);
        }

        CollabDef[] result = new CollabDef[list.size()];
        list.toArray(result);
        return result;
    }

    /** Parse a CSV line respecting double-quoted fields. */
    private static String[] parseCsvLine(String line) {
        java.util.ArrayList<String> fields = new java.util.ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    static String trim(String s) {
        if (s == null) return "";
        return s.trim();
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
        if (def == null || def.id.isEmpty()) {
            return false;
        }

        // Check that the primary weapon reference (weaponA) can be resolved
        if (def.weaponA != null && !def.weaponA.isEmpty()) {
            if (catalog.weapon(def.weaponA) == null) {
                // Weapon not found in catalog; the collab may reference
                // a weapon that hasn't been loaded yet, so we defer fully.
                // But reject clearly invalid IDs.
                if (!def.weaponA.matches("[a-zA-Z0-9_\\-]+")) {
                    return false;
                }
            }
        }

        // Check weaponB:
        // - For ordinary collabs ("collab" type), weaponB should be a valid weapon ID
        // - For super collabs ("super_collab" type), weapon_b is an item (e.g., Hope Soda, Sake)
        //   and is handled by the super collab system, not the ordinary collab system.
        if (def.weaponB != null && !def.weaponB.isEmpty()) {
            if ("super_collab".equals(def.type)) {
                // Super collab: weapon_b is an item prerequisite, not a weapon.
                // These are handled separately; just validate the ID format.
                if (!def.weaponB.matches("[a-zA-Z0-9_\\-]+")) {
                    return false;
                }
            } else {
                // Ordinary collab: weaponB should reference an existing weapon
                if (catalog.weapon(def.weaponB) == null) {
                    // Deferred lookup; validate format at minimum
                    if (!def.weaponB.matches("[a-zA-Z0-9_\\-]+")) {
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