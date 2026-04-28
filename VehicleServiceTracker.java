import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

// Vehicle Class
class Vehicle {
    String vehicleNumber;
    String model;
    String manufacturer;
    double currentDistanceKm;

    Vehicle(String vehicleNumber, String model, String manufacturer, double currentDistanceKm) {
        this.vehicleNumber = vehicleNumber;
        this.model = model;
        this.manufacturer = manufacturer;
        this.currentDistanceKm = currentDistanceKm;
    }
}

// Service Record Class
class ServiceRecord {
    String vehicleNumber;
    LocalDate serviceDate;
    String serviceType;
    double cost;
    double distanceTravelledKm;

    ServiceRecord(String vehicleNumber, LocalDate serviceDate, String serviceType, double cost, double distanceTravelledKm) {
        this.vehicleNumber = vehicleNumber;
        this.serviceDate = serviceDate;
        this.serviceType = serviceType;
        this.cost = cost;
        this.distanceTravelledKm = distanceTravelledKm;
    }

    long daysSinceService() {
        return ChronoUnit.DAYS.between(serviceDate, LocalDate.now());
    }
}

// Main Class
public class VehicleServiceTracker {
    static ArrayList<Vehicle> vehicles = new ArrayList<>();
    static ArrayList<ServiceRecord> records = new ArrayList<>();

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int choice;

        do {
            System.out.println("\n--- Vehicle Service Tracker ---");
            System.out.println("1. Add Vehicle");
            System.out.println("2. Add Service Record");
            System.out.println("3. View Service Records");
            System.out.println("4. View Vehicle Summary (History + Distance)");
            System.out.println("5. Update Vehicle Distance");
            System.out.println("6. View Service Summary & Due Vehicles");
            System.out.println("7. Exit");
            System.out.print("Enter choice: ");
            choice = sc.nextInt();
            sc.nextLine(); // consume newline

            switch (choice) {
                case 1:
                    addVehicle(sc);
                    break;

                case 2:
                    addServiceRecord(sc);
                    break;

                case 3:
                    viewRecords();
                    break;

                case 4:
                    viewVehicleSummary(sc);
                    break;

                case 5:
                    updateVehicleDistance(sc);
                    break;

                case 6:
                    viewServiceSummary(sc);
                    break;

                case 7:
                    System.out.println("Exiting...");
                    break;

                default:
                    System.out.println("Invalid choice!");
            }

        } while (choice != 7);

        sc.close();
    }

    // Add Vehicle
    static void addVehicle(Scanner sc) {
        System.out.print("Enter Vehicle Number: ");
        String number = sc.nextLine();

        if (vehicleExists(number)) {
            System.out.println("Vehicle already exists.");
            return;
        }

        System.out.print("Enter Model: ");
        String model = sc.nextLine();

        System.out.print("Enter Manufacturer: ");
        String manufacturer = sc.nextLine();

        System.out.print("Enter Current Distance Travelled (km): ");
        double currentDistanceKm = sc.nextDouble();
        sc.nextLine();

        if (currentDistanceKm < 0) {
            System.out.println("Distance cannot be negative.");
            return;
        }

        vehicles.add(new Vehicle(number, model, manufacturer, currentDistanceKm));
        System.out.println("Vehicle added successfully!");
    }

    // Add Service Record
    static void addServiceRecord(Scanner sc) {
        System.out.print("Enter Vehicle Number: ");
        String number = sc.nextLine();

        if (!vehicleExists(number)) {
            System.out.println("Vehicle not found. Add the vehicle first.");
            return;
        }

        LocalDate date;
        while (true) {
            System.out.print("Enter Service Date (YYYY-MM-DD): ");
            String dateText = sc.nextLine();
            try {
                date = LocalDate.parse(dateText);
                break;
            } catch (DateTimeParseException ex) {
                System.out.println("Invalid date format. Please use YYYY-MM-DD.");
            }
        }

        System.out.print("Enter Service Type: ");
        String type = sc.nextLine();

        System.out.print("Enter Cost: ");
        double cost = sc.nextDouble();

        System.out.print("Enter Distance Travelled Since Last Service (km): ");
        double distanceTravelledKm = sc.nextDouble();
        sc.nextLine();

        if (cost < 0 || distanceTravelledKm < 0) {
            System.out.println("Cost and distance cannot be negative.");
            return;
        }

        records.add(new ServiceRecord(number, date, type, cost, distanceTravelledKm));

        Vehicle vehicle = findVehicle(number);
        if (vehicle != null) {
            vehicle.currentDistanceKm += distanceTravelledKm;
        }

        System.out.println("Service record added!");
    }

    static boolean vehicleExists(String number) {
        return findVehicle(number) != null;
    }

    static Vehicle findVehicle(String number) {
        for (Vehicle v : vehicles) {
            if (v.vehicleNumber.equalsIgnoreCase(number)) {
                return v;
            }
        }
        return null;
    }

    // View Records
    static void viewRecords() {
        if (records.isEmpty()) {
            System.out.println("No service records found.");
            return;
        }

        System.out.println("\n--- Service Records (Column View) ---");
        System.out.printf("%-14s %-12s %-22s %-12s %-10s %-16s%n",
                "Vehicle", "Date", "Service Type", "Cost", "Days", "Interval KM");
        System.out.println("--------------------------------------------------------------------------------------");

        for (ServiceRecord r : records) {
            System.out.printf("%-14s %-12s %-22s %-12.2f %-10d %-16.2f%n",
                    r.vehicleNumber,
                    r.serviceDate,
                    r.serviceType,
                    r.cost,
                    r.daysSinceService(),
                    r.distanceTravelledKm);
        }
    }

    static void viewServiceSummary(Scanner sc) {
        if (vehicles.isEmpty()) {
            System.out.println("No vehicles found. Add vehicles first.");
            return;
        }

        System.out.print("Enter due threshold in days (e.g., 180): ");
        int dueDays = sc.nextInt();

        System.out.print("Enter due threshold in km (e.g., 5000): ");
        double dueKm = sc.nextDouble();
        sc.nextLine();

        if (dueDays < 0 || dueKm < 0) {
            System.out.println("Thresholds cannot be negative.");
            return;
        }

        System.out.println("\n--- Service Summary ---");
        for (Vehicle vehicle : vehicles) {
            ServiceRecord latest = getLatestServiceRecord(vehicle.vehicleNumber);
            double totalDistance = getTotalDistance(vehicle.vehicleNumber);

            System.out.println("\nVehicle Number: " + vehicle.vehicleNumber);
            System.out.println("Model: " + vehicle.model);
            System.out.println("Manufacturer: " + vehicle.manufacturer);
            System.out.println("Current Total Distance: " + vehicle.currentDistanceKm + " km");
            System.out.println("Total Distance Travelled (recorded): " + totalDistance + " km");

            if (latest == null) {
                System.out.println("Last Service Date: No service history");
                System.out.println("Service Due: NO DATA");
                continue;
            }

            long daysSinceLastService = latest.daysSinceService();
            boolean isDueByDays = daysSinceLastService >= dueDays;
            boolean isDueByKm = latest.distanceTravelledKm >= dueKm;
            boolean isDue = isDueByDays || isDueByKm;

            System.out.println("Last Service Date: " + latest.serviceDate);
            System.out.println("Days Since Last Service: " + daysSinceLastService);
            System.out.println("Distance at Last Service Interval: " + latest.distanceTravelledKm + " km");
            System.out.println("Service Due: " + (isDue ? "YES" : "NO"));
            if (isDue) {
                if (isDueByDays) {
                    System.out.println("Due Reason: Days threshold exceeded");
                }
                if (isDueByKm) {
                    System.out.println("Due Reason: Distance threshold exceeded");
                }
            }
        }
    }

    static void viewVehicleSummary(Scanner sc) {
        if (vehicles.isEmpty()) {
            System.out.println("No vehicles found. Add vehicles first.");
            return;
        }

        System.out.print("Enter Vehicle Number: ");
        String number = sc.nextLine();
        Vehicle vehicle = findVehicle(number);

        if (vehicle == null) {
            System.out.println("Vehicle not found.");
            return;
        }

        System.out.println("\n--- Vehicle Summary ---");
        System.out.printf("%-14s %-14s %-16s %-18s %-20s%n",
            "Vehicle", "Model", "Manufacturer", "Current KM", "History Total KM");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("%-14s %-14s %-16s %-18.2f %-20.2f%n",
            vehicle.vehicleNumber,
            vehicle.model,
            vehicle.manufacturer,
            vehicle.currentDistanceKm,
            getTotalDistance(vehicle.vehicleNumber));

        List<ServiceRecord> history = new ArrayList<>();
        for (ServiceRecord record : records) {
            if (record.vehicleNumber.equalsIgnoreCase(vehicle.vehicleNumber)) {
                history.add(record);
            }
        }

        history.sort(Comparator.comparing(r -> r.serviceDate));

        if (history.isEmpty()) {
            System.out.println("Service History: No service records yet.");
            return;
        }

        System.out.println("\nService History:");
        System.out.printf("%-12s %-22s %-12s %-16s %-10s%n",
            "Date", "Service Type", "Cost", "Interval KM", "Days Ago");
        System.out.println("--------------------------------------------------------------------------");
        for (ServiceRecord record : history) {
            System.out.printf("%-12s %-22s %-12.2f %-16.2f %-10d%n",
                record.serviceDate,
                record.serviceType,
                record.cost,
                record.distanceTravelledKm,
                record.daysSinceService());
        }
    }

    static void updateVehicleDistance(Scanner sc) {
        if (vehicles.isEmpty()) {
            System.out.println("No vehicles found. Add vehicles first.");
            return;
        }

        System.out.print("Enter Vehicle Number: ");
        String number = sc.nextLine();
        Vehicle vehicle = findVehicle(number);

        if (vehicle == null) {
            System.out.println("Vehicle not found.");
            return;
        }

        System.out.println("Current Distance: " + vehicle.currentDistanceKm + " km");
        System.out.print("Enter Updated Distance (km): ");
        double updatedDistance = sc.nextDouble();
        sc.nextLine();

        if (updatedDistance < 0) {
            System.out.println("Distance cannot be negative.");
            return;
        }

        if (updatedDistance < vehicle.currentDistanceKm) {
            System.out.println("Updated distance cannot be less than current distance.");
            return;
        }

        vehicle.currentDistanceKm = updatedDistance;
        System.out.println("Vehicle distance updated successfully.");
    }

    static ServiceRecord getLatestServiceRecord(String vehicleNumber) {
        ServiceRecord latest = null;
        for (ServiceRecord record : records) {
            if (!record.vehicleNumber.equalsIgnoreCase(vehicleNumber)) {
                continue;
            }
            if (latest == null || record.serviceDate.isAfter(latest.serviceDate)) {
                latest = record;
            }
        }
        return latest;
    }

    static double getTotalDistance(String vehicleNumber) {
        double totalDistance = 0.0;
        for (ServiceRecord record : records) {
            if (record.vehicleNumber.equalsIgnoreCase(vehicleNumber)) {
                totalDistance += record.distanceTravelledKm;
            }
        }
        return totalDistance;
    }
}