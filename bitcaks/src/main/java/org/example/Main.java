package org.example;

import org.example.entity.Bitcask;

import java.io.File;
import java.util.Map;

public class Main {

    public static void main(String[] args) {

        try {

            // =========================
            // CLEAN OLD TEST DATA
            // =========================

            File dir = new File("data");

            if (dir.exists()) {

                File[] files = dir.listFiles();

                if (files != null) {

                    for (File file : files) {

                        file.delete();
                    }
                }
            }

            // =========================
            // FIRST RUN
            // =========================

            System.out.println(
                    "========== FIRST RUN ==========\n"
            );

            Bitcask db =
                    new Bitcask("data");

            // create many writes
            // to test:
            // - hint files
            // - recovery
            // - overwrites

            for (int i = 0; i < 20; i++) {

                db.put(
                        "stationafsadfsdfsdafsfdsafsdfsdfasdfsdfcsdfvdvdfb_" + i,
                        "temasdfdsafsafsdafsdafsdfdafsdfdsafvfdvbgfsdbfgasdfgvdsap_" + i
                );
            }

            // overwrite test
            db.put("station_5", "UPDATED");

            System.out.println(
                    "station_5 => "
                            + db.get("station_5")
            );

            System.out.println(
                    "\nAll values:\n"
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

            db.close();

            // =========================
            // SECOND RUN
            // =========================

            System.out.println(
                    "\n========== RECOVERY RUN ==========\n"
            );

            Bitcask recovered =
                    new Bitcask("data");

            System.out.println(
                    "Recovered station_5 => "
                            + recovered.get("station_5")
            );

            System.out.println(
                    "\nRecovered values:\n"
            );

            Map<String, String> recoveredAll =
                    recovered.getAll();

            for (Map.Entry<String, String> entry
                    : recoveredAll.entrySet()) {

                System.out.println(
                        entry.getKey()
                                + " -> "
                                + entry.getValue()
                );
            }

            recovered.close();

            // =========================
            // SHOW GENERATED FILES
            // =========================

            System.out.println(
                    "\n========== GENERATED FILES ==========\n"
            );

            File[] generated =
                    dir.listFiles();

            if (generated != null) {

                for (File file : generated) {

                    System.out.println(
                            file.getName()
                                    + " | "
                                    + file.length()
                                    + " bytes"
                    );
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}