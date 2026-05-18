package org.example;

import org.example.entity.Bitcask;

import java.util.Map;

public class Main {

    public static void main(String[] args) {

        try (
                Bitcask db =
                        new Bitcask("data")
        ) {

            // =========================
            // PUT TEST
            // =========================

            db.put("station_1", "25C");
            db.put("station_2", "31C");
            db.put("station_3", "18C");

            // overwrite existing key
            db.put("station_1", "27C");

            System.out.println(
                    "Data inserted successfully.\n"
            );

            // =========================
            // GET TEST
            // =========================

            String value =
                    db.get("station_1");

            System.out.println(
                    "station_1 -> "
                            + value
            );

            // =========================
            // GET NON-EXISTING KEY
            // =========================

            String missing =
                    db.get("unknown");

            System.out.println(
                    "unknown -> "
                            + missing
            );

            // =========================
            // VIEW ALL TEST
            // =========================

            System.out.println(
                    "\nAll latest records:\n"
            );

            Map<String, String> all =
                    db.getAll();

            for (Map.Entry<String, String> entry
                    : all.entrySet()) {

                System.out.println(
                        entry.getKey()
                                + " -> "
                                + entry.getValue()
                );
            }

            // =========================
            // RESTART RECOVERY TEST
            // =========================

            System.out.println(
                    "\nNow stop the program and run again."
            );

            System.out.println(
                    "If values still exist,"
            );

            System.out.println(
                    "then recovery works correctly."
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}