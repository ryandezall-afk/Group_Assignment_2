import problemdomain.Hatchback;
import problemdomain.Hybrid;
import problemdomain.SUV;
import problemdomain.Sedan;
import problemdomain.Truck;
import problemdomain.Vehicle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class VehicleFileStore {

    private VehicleFileStore() {
        // Utility class - no instances.
    }

    /**
     * 1) Parse vehicles.txt into a single List<Vehicle> using CarID first digit rules.
     */
    public static List<Vehicle> loadVehicles(Path filePath) throws IOException {
        List<Vehicle> vehicles = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;

            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                try {
                    vehicles.add(parseVehicleLine(line));
                } catch (Exception e) {
                    throw new IOException("Failed to parse line " + lineNo + ": " + line, e);
                }
            }
        }

        return vehicles;
    }

    /**
     * 2) Handle per-type field differences when creating the correct subclass.
     */
    private static Vehicle parseVehicleLine(String line) {
        // Keep empty tokens if any (defensive)
        String[] p = line.split(";", -1);

        if (p.length < 10) {
            throw new IllegalArgumentException("Expected at least 10 fields, got " + p.length);
        }

        long carID = Long.parseLong(p[0].trim());
        String vehicleType = p[1].trim();
        String subType = p[2].trim();
        int speed = Integer.parseInt(p[3].trim());
        double fuel = Double.parseDouble(p[4].trim());
        int seats = Integer.parseInt(p[5].trim());
        int year = Integer.parseInt(p[6].trim());
        String drivetrain = p[7].trim();
        int price = Integer.parseInt(p[8].trim());
        int quantity = Integer.parseInt(p[9].trim());

        String carIdStr = String.valueOf(carID);
        if (carIdStr.isEmpty()) {
            throw new IllegalArgumentException("Invalid CarID");
        }

        char firstDigit = carIdStr.charAt(0);

        switch (firstDigit) {
            case '1': // Sedan -> + trunkSize
                requireFields(p, 11);
                return new Sedan(
                        carID, vehicleType, subType, speed, fuel, seats, year, drivetrain, price, quantity,
                        p[10].trim()
                );

            case '2': // Hatchback -> + hatchType
                requireFields(p, 11);
                return new Hatchback(
                        carID, vehicleType, subType, speed, fuel, seats, year, drivetrain, price, quantity,
                        p[10].trim()
                );

            case '3': // SUV -> base fields only
                return new SUV(
                        carID, vehicleType, subType, speed, fuel, seats, year, drivetrain, price, quantity
                );

            case '4': // Hybrid
            case '5': // Hybrid -> + powerTrain + electricRange
                requireFields(p, 12);
                return new Hybrid(
                        carID, vehicleType, subType, speed, fuel, seats, year, drivetrain, price, quantity,
                        p[10].trim(),
                        Integer.parseInt(p[11].trim())
                );

            case '6': // Pickup Truck -> + cargoBed + cargoCapacity
                requireFields(p, 12);
                return new Truck(
                        carID, vehicleType, subType, speed, fuel, seats, year, drivetrain, price, quantity,
                        p[10].trim(),
                        p[11].trim()
                );

            default:
                throw new IllegalArgumentException("Unknown CarID first digit: " + firstDigit);
        }
    }

    /**
     * 3) Persist vehicles back to vehicles.txt in original format.
     */
    public static void saveVehicles(Path filePath, List<Vehicle> vehicles) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            for (int i = 0; i < vehicles.size(); i++) {
                writer.write(toOriginalFormatLine(vehicles.get(i)));
                if (i < vehicles.size() - 1) {
                    writer.newLine();
                }
            }
        }
    }

    private static String toOriginalFormatLine(Vehicle v) {
        String base = v.getCarID() + ";" +
                v.getVehicleType() + ";" +
                v.getSubType() + ";" +
                v.getSpeed() + ";" +
                v.getFuel() + ";" +
                v.getSeats() + ";" +
                v.getYear() + ";" +
                v.getDrivetrain() + ";" +
                v.getPrice() + ";" +
                v.getQuantity();

        // Subtype-specific raw fields (must preserve original data format)
        if (v instanceof Sedan) {
            return base + ";" + readPrivateField(v, "trunkSize");
        }
        if (v instanceof Hatchback) {
            return base + ";" + readPrivateField(v, "hatchType");
        }
        if (v instanceof Hybrid) {
            return base + ";" + readPrivateField(v, "powerTrain") + ";" + readPrivateField(v, "electricRange");
        }
        if (v instanceof Truck) {
            return base + ";" + readPrivateField(v, "cargoBed") + ";" + readPrivateField(v, "cargoCapacity");
        }

        // SUV and any other base-only types
        return base;
    }

    private static void requireFields(String[] p, int minFields) {
        if (p.length < minFields) {
            throw new IllegalArgumentException("Expected at least " + minFields + " fields, got " + p.length);
        }
    }

    /**
     * Reflection is used only because existing subtype classes keep raw file fields private
     * and do not expose getters for the original code values (L/S/M, PHEV, SB/EB/DB, etc.).
     * This avoids changing your existing uploaded classes.
     */
    private static Object readPrivateField(Object target, String fieldName) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot read field '" + fieldName + "' from " + target.getClass().getSimpleName(), e
            );
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}