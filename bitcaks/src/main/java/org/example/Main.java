package org.example;

import org.example.entity.RecordOffset;
import org.example.entity.Segment;

import java.util.List;

public class Main {

    public static void main(String[] args) {

        try (
                Segment segment =
                        new Segment(
                                "segment.data",
                                1
                        )
        ) {

            // =========================
            // WRITE TEST
            // =========================

            long offset1 =
                    segment.write(
                            "name",
                            "pedri",
                            System.currentTimeMillis()
                    );

            long offset2 =
                    segment.write(
                            "database",
                            "bitcask",
                            System.currentTimeMillis()
                    );

            System.out.println(
                    "First record offset: "
                            + offset1
            );

            System.out.println(
                    "Second record offset: "
                            + offset2
            );

            // =========================
            // DIRECT READ TEST
            // =========================

            String value =
                    segment.read(
                            offset1,
                            "pedri"
                                    .getBytes()
                                    .length
                    );

            System.out.println(
                    "\nRead value from offset:"
            );

            System.out.println(value);

            // =========================
            // ITERATION TEST
            // =========================

            System.out.println(
                    "\nIterating records:\n"
            );

            List<RecordOffset> records =
                    segment.iterate();

            for (RecordOffset ro : records) {

                System.out.println(
                        "Offset: "
                                + ro.offset
                );

                System.out.println(
                        "Key: "
                                + ro.record.key
                );

                System.out.println(
                        "Value: "
                                + ro.record.value
                );

                System.out.println(
                        "Timestamp: "
                                + ro.record.timestamp
                );

                System.out.println(
                        "-------------------"
                );
            }

            // =========================
            // FILE SIZE TEST
            // =========================

            System.out.println(
                    "\nSegment size: "
                            + segment.size()
                            + " bytes"
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}