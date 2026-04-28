import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

public class VehicleServiceTrackerApp extends JFrame {
    private final List<Account> accounts = new ArrayList<>();
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final List<ServiceEntry> entries = new ArrayList<>();
    private final List<ServiceEntry> visibleEntries = new ArrayList<>();
    private static final String DB_URL = "jdbc:sqlite:vehicle_service_tracker.db";
    private int selectedAccountId = -1;
    private boolean suppressAccountSelectionEvent = false;
    private Account authenticatedAccount;

    private final DefaultTableModel tableModel;
    private final JTable serviceTable;

    private final JTextField vehicleNoField;
    private final JTextField modelField;
    private final JTextField makerField;
    private final JSpinner vehicleCurrentKmSpinner;

    private final JComboBox<Account> accountChooser;
    private final JTextField accountNameField;
    private final JTextField accountEmailField;
    private final JTextField accountPhoneField;
    private final JPasswordField accountPasswordField;

    private final JComboBox<String> vehicleChooser;
    private final JTextField serviceDateField;
    private final JComboBox<String> serviceTypeChooser;
    private final JSpinner costSpinner;
    private final JSpinner distanceKmSpinner;

    private final JComboBox<String> summaryVehicleChooser;
    private final JSpinner summaryDistanceSpinner;
    private final DefaultTableModel summaryTableModel;
    private final JTable summaryTable;
    private final JTextArea summaryRecommendationArea;

    private final JTextField filterField;

    private final JLabel totalVehiclesLabel;
    private final JLabel totalJobsLabel;
    private final JLabel totalRevenueLabel;

    private final DecimalFormat moneyFmt = new DecimalFormat("#,##0.00");

    public VehicleServiceTrackerApp() {
        setTitle("Vehicle Service Tracker");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(980, 640));
        setLocationRelativeTo(null);

        installGlobalUiDefaults();

        NebulaPanel root = new NebulaPanel();
        root.setLayout(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        setContentPane(root);

        JPanel top = buildTopBanner();
        root.add(top, BorderLayout.NORTH);

        JPanel left = buildControlDeck();

        tableModel = new DefaultTableModel(new Object[] { "Vehicle", "Date", "Days Since", "Service", "Cost (INR)", "Distance (km)", "Notify" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 6;
            }
        };

        summaryTableModel = new DefaultTableModel(new Object[] { "Date", "Days Since", "Service", "Cost (INR)", "Distance (km)" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        summaryTable = new JTable(summaryTableModel);

        serviceTable = new JTable(tableModel);
        serviceTable.setRowHeight(28);
        serviceTable.setShowVerticalLines(false);
        serviceTable.setGridColor(new Color(228, 228, 228));
        serviceTable.getTableHeader().setFont(new Font("Trebuchet MS", Font.BOLD, 14));
        serviceTable.getTableHeader().setBackground(new Color(18, 40, 58));
        serviceTable.getTableHeader().setForeground(Color.WHITE);
        serviceTable.setSelectionBackground(new Color(209, 238, 255));
        
        // Add button renderer and editor for notification column
        serviceTable.getColumn("Notify").setCellRenderer(new ButtonRenderer());
        serviceTable.getColumn("Notify").setCellEditor(new ButtonEditor(new JCheckBox(), this));

        summaryTable.setRowHeight(26);
        summaryTable.setShowVerticalLines(false);
        summaryTable.setGridColor(new Color(228, 228, 228));
        summaryTable.getTableHeader().setFont(new Font("Trebuchet MS", Font.BOLD, 13));
        summaryTable.getTableHeader().setBackground(new Color(18, 40, 58));
        summaryTable.getTableHeader().setForeground(Color.WHITE);
        summaryTable.setSelectionBackground(new Color(209, 238, 255));

        JPanel right = new FrostPanel(22, new Color(255, 255, 255, 235));
        right.setLayout(new BorderLayout(12, 12));
        right.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel rightTop = new JPanel(new BorderLayout(8, 8));
        rightTop.setOpaque(false);

        JLabel recordsHeading = new JLabel("Service Timeline");
        recordsHeading.setFont(new Font("Trebuchet MS", Font.BOLD, 24));
        recordsHeading.setForeground(new Color(20, 20, 20));

        filterField = new JTextField();
        filterField.setToolTipText("Filter by vehicle/date/service");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshTable();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshTable();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshTable();
            }
        });

        JPanel filterWrap = new JPanel(new BorderLayout(8, 8));
        filterWrap.setOpaque(false);
        JLabel filterLabel = new JLabel("Quick Filter");
        filterLabel.setFont(new Font("Trebuchet MS", Font.PLAIN, 13));
        filterWrap.add(filterLabel, BorderLayout.WEST);
        filterWrap.add(filterField, BorderLayout.CENTER);

        rightTop.add(recordsHeading, BorderLayout.WEST);
        rightTop.add(filterWrap, BorderLayout.CENTER);

        right.add(rightTop, BorderLayout.NORTH);
        right.add(new JScrollPane(serviceTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.37);
        split.setBorder(null);
        split.setDividerSize(8);
        split.setOpaque(false);

        root.add(split, BorderLayout.CENTER);

        totalVehiclesLabel = new JLabel("0");
        totalJobsLabel = new JLabel("0");
        totalRevenueLabel = new JLabel("INR 0.00");
        bindStatCards((JPanel) top);

        accountChooser = new JComboBox<>();
        accountNameField = new JTextField();
        accountEmailField = new JTextField();
        accountPhoneField = new JTextField();
        accountPasswordField = new JPasswordField();

        vehicleNoField = new JTextField();
        modelField = new JTextField();
        makerField = new JTextField();
        vehicleCurrentKmSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 2000000.0, 10.0));

        vehicleChooser = new JComboBox<>();
        serviceDateField = new JTextField(LocalDate.now().toString());
        serviceTypeChooser = new JComboBox<>(new String[] {
                "Periodic Service", "Engine Work", "Brake Work", "Electrical", "Tyre & Alignment", "Body Work", "Other"
        });
        costSpinner = new JSpinner(new SpinnerNumberModel(1200.0, 0.0, 500000.0, 100.0));
        distanceKmSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 200000.0, 10.0));

        summaryVehicleChooser = new JComboBox<>();
        summaryDistanceSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 2000000.0, 10.0));
        summaryRecommendationArea = new JTextArea(5, 20);
        summaryRecommendationArea.setEditable(false);
        summaryRecommendationArea.setLineWrap(true);
        summaryRecommendationArea.setWrapStyleWord(true);
        summaryRecommendationArea.setFont(new Font("Trebuchet MS", Font.PLAIN, 13));
        summaryRecommendationArea.setText("Select a vehicle to view maintenance recommendations.");

        installDeckForms(left);
        initializeDatabase();

        Account loginAccount = promptForLoginOrRegistration();
        if (loginAccount == null) {
            dispose();
            System.exit(0);
            return;
        }

        authenticateIntoSession(loginAccount);
        refreshTable();
        refreshStats();
    }

    private Connection openConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("SQLite JDBC driver not found on classpath.", ex);
        }

        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    private void initializeDatabase() {
        String createAccounts = "CREATE TABLE IF NOT EXISTS accounts ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "name TEXT NOT NULL,"
            + "email TEXT NOT NULL UNIQUE,"
            + "phone TEXT,"
            + "password TEXT NOT NULL DEFAULT ''"
            + ")";

        String createVehicles = "CREATE TABLE IF NOT EXISTS vehicles ("
                + "vehicle_number TEXT PRIMARY KEY,"
                + "model TEXT NOT NULL,"
            + "manufacturer TEXT NOT NULL,"
            + "account_id INTEGER NOT NULL DEFAULT 1,"
            + "current_km REAL NOT NULL DEFAULT 0"
                + ")";

        String createEntries = "CREATE TABLE IF NOT EXISTS service_entries ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "vehicle_number TEXT NOT NULL,"
                + "service_date TEXT NOT NULL,"
                + "service_type TEXT NOT NULL,"
                + "cost REAL NOT NULL,"
                + "distance_km REAL NOT NULL DEFAULT 0,"
                + "account_id INTEGER NOT NULL DEFAULT 1,"
                + "notification_sent INTEGER NOT NULL DEFAULT 0,"
                + "FOREIGN KEY(vehicle_number) REFERENCES vehicles(vehicle_number)"
                + ")";

        try (Connection conn = openConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createAccounts);
            stmt.execute(createVehicles);
            stmt.execute(createEntries);

            if (!columnExists(conn, "vehicles", "account_id")) {
                stmt.execute("ALTER TABLE vehicles ADD COLUMN account_id INTEGER NOT NULL DEFAULT 1");
            }

            if (!columnExists(conn, "service_entries", "account_id")) {
                stmt.execute("ALTER TABLE service_entries ADD COLUMN account_id INTEGER NOT NULL DEFAULT 1");
            }

            if (!columnExists(conn, "vehicles", "current_km")) {
                stmt.execute("ALTER TABLE vehicles ADD COLUMN current_km REAL NOT NULL DEFAULT 0");
            }

            if (!columnExists(conn, "service_entries", "distance_km")) {
                stmt.execute("ALTER TABLE service_entries ADD COLUMN distance_km REAL NOT NULL DEFAULT 0");
            }

            if (!columnExists(conn, "accounts", "password")) {
                stmt.execute("ALTER TABLE accounts ADD COLUMN password TEXT NOT NULL DEFAULT ''");
            }

            if (!columnExists(conn, "service_entries", "notification_sent")) {
                stmt.execute("ALTER TABLE service_entries ADD COLUMN notification_sent INTEGER NOT NULL DEFAULT 0");
            }

            stmt.executeUpdate("UPDATE accounts SET password = 'changeme' WHERE password IS NULL OR TRIM(password) = ''");

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS c FROM accounts")) {
                if (rs.next() && rs.getInt("c") == 0) {
                    stmt.execute("INSERT INTO accounts(name, email, phone, password) VALUES ('Default User', 'default@tracker.local', '', 'changeme')");
                }
            }
        } catch (SQLException ex) {
            showDatabaseError("Could not initialize local database.", ex);
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String pragma = "PRAGMA table_info(" + tableName + ")";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(pragma)) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void authenticateIntoSession(Account account) {
        authenticatedAccount = account;
        setTitle("Vehicle Service Tracker - " + account.name);

        accounts.clear();
        accounts.add(account);

        suppressAccountSelectionEvent = true;
        accountChooser.removeAllItems();
        accountChooser.addItem(account);
        accountChooser.setSelectedIndex(0);
        suppressAccountSelectionEvent = false;
        accountChooser.setEnabled(false);

        selectedAccountId = account.id;
        accountNameField.setText(account.name);
        accountEmailField.setText(account.email);
        accountPhoneField.setText(account.phone == null ? "" : account.phone);
        accountPasswordField.setText("");

        loadAccountData(account.id);
    }

    private void loadAccountsFromDatabase() {
        accounts.clear();
        suppressAccountSelectionEvent = true;
        accountChooser.removeAllItems();

        String sql = "SELECT id, name, email, phone FROM accounts ORDER BY id";
        try (Connection conn = openConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Account account = new Account(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("phone"));
                accounts.add(account);
                accountChooser.addItem(account);
            }
        } catch (SQLException ex) {
            showDatabaseError("Could not load accounts.", ex);
        } finally {
            suppressAccountSelectionEvent = false;
        }
    }

    private Account getAccountById(int accountId) {
        String sql = "SELECT id, name, email, phone FROM accounts WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new GarageAccount(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("phone"));
                }
            }
        } catch (SQLException ex) {
            showDatabaseError("Could not fetch account details.", ex);
        }
        return null;
    }

    private Account authenticateAccount(String email, String password) {
        String sql = "SELECT id, name, email, phone FROM accounts WHERE LOWER(email) = LOWER(?) AND password = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new GarageAccount(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("phone"));
                }
            }
        } catch (SQLException ex) {
            showDatabaseError("Could not authenticate account.", ex);
        }
        return null;
    }

    private Account promptForLoginOrRegistration() {
        while (true) {
            String[] choices = { "Log In", "Create Account", "Exit" };
            int decision = JOptionPane.showOptionDialog(
                    this,
                    "Please log in to continue, or create a new account.",
                    "Account Access",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    choices,
                    choices[0]);

            if (decision == 0) {
                JTextField emailField = new JTextField();
                JPasswordField passwordField = new JPasswordField();
                JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
                panel.add(new JLabel("Email"));
                panel.add(emailField);
                panel.add(new JLabel("Password"));
                panel.add(passwordField);

                int ok = JOptionPane.showConfirmDialog(this, panel, "Log In", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (ok != JOptionPane.OK_OPTION) {
                    continue;
                }

                String email = emailField.getText().trim();
                String password = new String(passwordField.getPassword());

                if (email.isEmpty() || password.isEmpty()) {
                    showError("Email and password are required.");
                    continue;
                }

                Account authenticated = authenticateAccount(email, password);
                if (authenticated == null) {
                    showError("Invalid email or password.");
                    continue;
                }
                return authenticated;
            }

            if (decision == 1) {
                JTextField nameField = new JTextField();
                JTextField emailField = new JTextField();
                JTextField phoneField = new JTextField();
                JPasswordField passwordField = new JPasswordField();
                JPasswordField confirmPasswordField = new JPasswordField();

                JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
                panel.add(new JLabel("Name"));
                panel.add(nameField);
                panel.add(new JLabel("Email"));
                panel.add(emailField);
                panel.add(new JLabel("Phone"));
                panel.add(phoneField);
                panel.add(new JLabel("Password"));
                panel.add(passwordField);
                panel.add(new JLabel("Confirm Password"));
                panel.add(confirmPasswordField);

                int ok = JOptionPane.showConfirmDialog(this, panel, "Create Account", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (ok != JOptionPane.OK_OPTION) {
                    continue;
                }

                String name = nameField.getText().trim();
                String email = emailField.getText().trim();
                String phone = phoneField.getText().trim();
                String password = new String(passwordField.getPassword());
                String confirm = new String(confirmPasswordField.getPassword());

                if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    showError("Name, email, and password are required.");
                    continue;
                }

                if (password.length() < 4) {
                    showError("Password must be at least 4 characters.");
                    continue;
                }

                if (!password.equals(confirm)) {
                    showError("Password and confirmation do not match.");
                    continue;
                }

                int createdId = createAccountInDatabase(name, email, phone, password);
                if (createdId <= 0) {
                    continue;
                }

                Account created = getAccountById(createdId);
                if (created == null) {
                    showError("Account created, but login failed. Please log in manually.");
                    continue;
                }

                showInfo("Account created and logged in.");
                return created;
            }

            return null;
        }
    }

    private void onAccountChanged() {
        if (suppressAccountSelectionEvent) {
            return;
        }

        GarageAccount selected = (GarageAccount) accountChooser.getSelectedItem();
        if (selected == null) {
            selectedAccountId = -1;
            vehicleChooser.removeAllItems();
            vehicles.clear();
            entries.clear();
            refreshTable();
            refreshStats();
            return;
        }

        selectedAccountId = selected.id;
        accountNameField.setText(selected.name);
        accountEmailField.setText(selected.email);
        accountPhoneField.setText(selected.phone == null ? "" : selected.phone);
        loadAccountData(selected.id);
        refreshTable();
        refreshStats();
    }

    private void loadAccountData(int accountId) {
        vehicles.clear();
        entries.clear();
        vehicleChooser.removeAllItems();

        String loadVehicles = "SELECT vehicle_number, model, manufacturer, account_id, current_km FROM vehicles WHERE account_id = ? ORDER BY vehicle_number";
        String loadEntries = "SELECT vehicle_number, service_date, service_type, cost, distance_km, account_id, notification_sent FROM service_entries WHERE account_id = ? ORDER BY id";

        try (Connection conn = openConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(loadVehicles)) {
                ps.setInt(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        GarageVehicle vehicle = new GarageVehicle(
                                rs.getString("vehicle_number"),
                                rs.getString("model"),
                                rs.getString("manufacturer"),
                            rs.getInt("account_id"),
                            rs.getDouble("current_km"));
                        vehicles.add(vehicle);
                        vehicleChooser.addItem(vehicle.vehicleNumber);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(loadEntries)) {
                ps.setInt(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new GarageServiceEntry(
                                rs.getString("vehicle_number"),
                                LocalDate.parse(rs.getString("service_date")),
                                rs.getString("service_type"),
                                rs.getDouble("cost"),
                                rs.getDouble("distance_km"),
                                rs.getInt("account_id"),
                                rs.getInt("notification_sent") == 1));
                    }
                }
            }
        } catch (SQLException | DateTimeParseException ex) {
            showDatabaseError("Could not load saved data.", ex);
        }

        refreshVehicleSummaryPanel();
    }

    private int createAccountInDatabase(String name, String email, String phone, String password) {
        String sql = "INSERT INTO accounts (name, email, phone, password) VALUES (?, ?, ?, ?)";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, password);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException ex) {
            showDatabaseError("Could not create account.", ex);
        }
        return -1;
    }

    private boolean updateAccountInDatabase(int accountId, String name, String email, String phone, String password) {
        boolean hasNewPassword = password != null && !password.isBlank();
        String sql = hasNewPassword
                ? "UPDATE accounts SET name = ?, email = ?, phone = ?, password = ? WHERE id = ?"
                : "UPDATE accounts SET name = ?, email = ?, phone = ? WHERE id = ?";

        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            if (hasNewPassword) {
                ps.setString(4, password);
                ps.setInt(5, accountId);
            } else {
                ps.setInt(4, accountId);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            showDatabaseError("Could not update account.", ex);
            return false;
        }
    }

    private void selectAccountById(int accountId) {
        suppressAccountSelectionEvent = true;
        for (int i = 0; i < accountChooser.getItemCount(); i++) {
            GarageAccount account = accountChooser.getItemAt(i);
            if (account.id == accountId) {
                accountChooser.setSelectedIndex(i);
                break;
            }
        }
        suppressAccountSelectionEvent = false;
        onAccountChanged();
    }

    private boolean saveVehicleToDatabase(GarageVehicle vehicle) {
        String sql = "INSERT INTO vehicles (vehicle_number, model, manufacturer, account_id, current_km) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vehicle.vehicleNumber);
            ps.setString(2, vehicle.model);
            ps.setString(3, vehicle.manufacturer);
            ps.setInt(4, vehicle.accountId);
            ps.setDouble(5, vehicle.currentKm);
            ps.executeUpdate();
            return true;
        } catch (SQLException ex) {
            showDatabaseError("Could not save vehicle.", ex);
            return false;
        }
    }

    private boolean saveServiceEntryToDatabase(GarageServiceEntry entry) {
        String sql = "INSERT INTO service_entries (vehicle_number, service_date, service_type, cost, distance_km, account_id, notification_sent) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.vehicleNumber);
            ps.setString(2, entry.serviceDate.toString());
            ps.setString(3, entry.serviceType);
            ps.setDouble(4, entry.cost);
            ps.setDouble(5, entry.distanceKm);
            ps.setInt(6, entry.accountId);
            ps.setInt(7, entry.notificationSent ? 1 : 0);
            ps.executeUpdate();
            return true;
        } catch (SQLException ex) {
            showDatabaseError("Could not save service entry.", ex);
            return false;
        }
    }

    private boolean updateVehicleDistanceInDatabase(String vehicleNumber, double currentKm) {
        String sql = "UPDATE vehicles SET current_km = ? WHERE vehicle_number = ? AND account_id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, currentKm);
            ps.setString(2, vehicleNumber);
            ps.setInt(3, selectedAccountId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            showDatabaseError("Could not update vehicle distance.", ex);
            return false;
        }
    }

    private boolean markNotificationAsSent(GarageServiceEntry entry) {
        if (entry == null) {
            return false;
        }
        
        String sql = "UPDATE service_entries SET notification_sent = 1 WHERE vehicle_number = ? AND service_date = ? AND account_id = ?";
        
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.vehicleNumber);
            ps.setString(2, entry.serviceDate.toString());
            ps.setInt(3, entry.accountId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                entry.notificationSent = true;
                return true;
            }
        } catch (SQLException ex) {
            showDatabaseError("Could not update notification status.", ex);
        }
        return false;
    }
    
    protected void sendNotificationForRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= visibleEntries.size()) {
            return;
        }
        
        GarageServiceEntry entry = visibleEntries.get(rowIndex);
        
        if (entry.notificationSent) {
            showInfo("Notification already sent for this service entry.");
            return;
        }
        
        if (markNotificationAsSent(entry)) {
            // Simulate sending notification (in real app, send email/SMS here)
            String message = "Notification sent for " + entry.vehicleNumber + "\n"
                    + "Service: " + entry.serviceType + "\n"
                    + "Date: " + entry.serviceDate + "\n"
                    + "Cost: INR " + moneyFmt.format(entry.cost);
            showInfo(message);
            refreshTable();
        }
    }

    private void refreshVehicleSummaryPanel() {
        summaryVehicleChooser.removeAllItems();
        for (GarageVehicle vehicle : vehicles) {
            summaryVehicleChooser.addItem(vehicle.vehicleNumber);
        }
        refreshSummaryTable();
    }

    private void refreshSummaryTable() {
        summaryTableModel.setRowCount(0);
        String selectedVehicleNo = (String) summaryVehicleChooser.getSelectedItem();
        if (selectedVehicleNo == null || selectedVehicleNo.isBlank()) {
            summaryDistanceSpinner.setValue(0.0);
            summaryRecommendationArea.setText("Select a vehicle to view maintenance recommendations.");
            return;
        }

        GarageVehicle vehicle = findVehicle(selectedVehicleNo);
        if (vehicle != null) {
            summaryDistanceSpinner.setValue(vehicle.currentKm);
            summaryRecommendationArea.setText(buildDistanceRecommendation(vehicle));
        }

        for (GarageServiceEntry entry : entries) {
            if (!entry.vehicleNumber.equalsIgnoreCase(selectedVehicleNo)) {
                continue;
            }

            long daysSince = ChronoUnit.DAYS.between(entry.serviceDate, LocalDate.now());
            summaryTableModel.addRow(new Object[] {
                    entry.serviceDate,
                    daysSince,
                    entry.serviceType,
                    moneyFmt.format(entry.cost),
                    moneyFmt.format(entry.distanceKm)
            });
        }
    }

    private String buildDistanceRecommendation(GarageVehicle vehicle) {
        double km = vehicle.currentKm;
        GarageServiceEntry latest = null;
        for (GarageServiceEntry entry : entries) {
            if (!entry.vehicleNumber.equalsIgnoreCase(vehicle.vehicleNumber)) {
                continue;
            }
            if (latest == null || entry.serviceDate.isAfter(latest.serviceDate)) {
                latest = entry;
            }
        }

        StringBuilder recommendation = new StringBuilder();
        recommendation.append("Vehicle ").append(vehicle.vehicleNumber).append(" | Current distance: ")
                .append(moneyFmt.format(km)).append(" km.\n");

        if (km < 5000) {
            recommendation.append("Recommendation: Routine checks only. Monitor tire pressure and fluid levels monthly.\n");
        } else if (km < 10000) {
            recommendation.append("Recommendation: Schedule a basic service. Prioritize engine oil and oil filter replacement.\n");
        } else if (km < 20000) {
            recommendation.append("Recommendation: Full periodic service advised. Inspect brakes, wheel alignment, and battery health.\n");
        } else if (km < 40000) {
            recommendation.append("Recommendation: Major inspection due. Check spark plugs, coolant system, and suspension components.\n");
        } else {
            recommendation.append("Recommendation: Comprehensive maintenance needed soon. Plan transmission, timing system, and full diagnostic checks.\n");
        }

        if (latest == null) {
            recommendation.append("No service history found for this vehicle. Add an initial service entry for better recommendations.");
            return recommendation.toString();
        }

        long daysSince = ChronoUnit.DAYS.between(latest.serviceDate, LocalDate.now());
        recommendation.append("Last service: ").append(latest.serviceDate)
                .append(" (").append(daysSince).append(" days ago), ")
                .append("distance since last service: ")
                .append(moneyFmt.format(latest.distanceKm)).append(" km.\n");

        if (daysSince > 180) {
            recommendation.append("Time-based alert: More than 180 days since last service. Book a service visit.");
        } else if (latest.distanceKm > 5000) {
            recommendation.append("Distance-based alert: Over 5000 km since last service. A periodic service is recommended now.");
        } else {
            recommendation.append("Status: Service cycle looks healthy. Continue regular checks and update distance after trips.");
        }

        return recommendation.toString();
    }

    private void showDatabaseError(String msg, Exception ex) {
        JOptionPane.showMessageDialog(
                this,
            msg + "\n"
                + ex.getMessage() + "\n"
                + "Run with: java -cp \".;lib/sqlite-jdbc-3.49.1.0.jar\" GarageServiceStudio\n"
                + "Or use VS Code Run and Debug profile: Launch GarageServiceStudio.",
                "Database Error",
                JOptionPane.ERROR_MESSAGE);
    }

    private void installGlobalUiDefaults() {
        Font base = new Font("Trebuchet MS", Font.PLAIN, 14);
        UIManager.put("Label.font", base);
        UIManager.put("TextField.font", base);
        UIManager.put("Button.font", base);
        UIManager.put("ComboBox.font", base);
        UIManager.put("Table.font", base);
        UIManager.put("TabbedPane.font", new Font("Trebuchet MS", Font.BOLD, 13));
    }

    private JPanel buildTopBanner() {
        JPanel top = new FrostPanel(26, new Color(14, 36, 50, 224));
        top.setLayout(new BorderLayout(12, 12));
        top.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel titleWrap = new JPanel(new GridLayout(2, 1));
        titleWrap.setOpaque(false);

        JLabel title = new JLabel("VEHICLE SERVICE TRACKER");
        title.setForeground(new Color(235, 251, 255));
        title.setFont(new Font("Impact", Font.PLAIN, 34));

        JLabel sub = new JLabel("An expressive command center for vehicle intake, service logging, and revenue monitoring");
        sub.setForeground(new Color(183, 224, 236));
        sub.setFont(new Font("Trebuchet MS", Font.PLAIN, 14));

        titleWrap.add(title);
        titleWrap.add(sub);

        JPanel stats = new JPanel(new GridLayout(1, 3, 10, 0));
        stats.setOpaque(false);
        stats.setName("statsArea");

        top.add(titleWrap, BorderLayout.WEST);
        top.add(stats, BorderLayout.EAST);

        return top;
    }

    private JPanel buildControlDeck() {
        JPanel left = new FrostPanel(22, new Color(255, 255, 255, 238));
        left.setLayout(new BorderLayout(8, 8));
        left.setBorder(new EmptyBorder(14, 14, 14, 14));
        return left;
    }

    private void installDeckForms(JPanel left) {
        JLabel heading = new JLabel("Operations Deck");
        heading.setFont(new Font("Trebuchet MS", Font.BOLD, 23));
        left.add(heading, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();

        JPanel accountPanel = new JPanel(new GridBagLayout());
        accountPanel.setOpaque(false);
        accountPanel.setBorder(new EmptyBorder(10, 6, 10, 6));

        addLabeledField(accountPanel, "Select Account", accountChooser, 0);
        addLabeledField(accountPanel, "Name", accountNameField, 1);
        addLabeledField(accountPanel, "Email", accountEmailField, 2);
        addLabeledField(accountPanel, "Phone", accountPhoneField, 3);
        addLabeledField(accountPanel, "Password (leave blank to keep current)", accountPasswordField, 4);

        JPanel accountButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        accountButtons.setOpaque(false);
        JButton createAccountBtn = createActionButton("Create Account", new Color(18, 84, 132));
        createAccountBtn.addActionListener(this::createAccount);
        JButton updateAccountBtn = createActionButton("Update Account", new Color(84, 65, 170));
        updateAccountBtn.addActionListener(this::updateAccount);
        accountButtons.add(createAccountBtn);
        accountButtons.add(updateAccountBtn);
        addLabeledField(accountPanel, "", accountButtons, 5);

        accountChooser.addActionListener(e -> onAccountChanged());

        JPanel intake = new JPanel(new GridBagLayout());
        intake.setOpaque(false);
        intake.setBorder(new EmptyBorder(10, 6, 10, 6));

        addLabeledField(intake, "Vehicle Number", vehicleNoField, 0);
        addLabeledField(intake, "Vehicle Model", modelField, 1);
        addLabeledField(intake, "Manufacturer", makerField, 2);
        addLabeledField(intake, "Current Distance (km)", vehicleCurrentKmSpinner, 3);

        JButton addVehicleBtn = createActionButton("Register Vehicle", new Color(0, 127, 95));
        addVehicleBtn.addActionListener(this::addVehicle);
        addButtonRow(intake, addVehicleBtn, 4);

        JPanel service = new JPanel(new GridBagLayout());
        service.setOpaque(false);
        service.setBorder(new EmptyBorder(10, 6, 10, 6));

        addLabeledField(service, "Vehicle", vehicleChooser, 0);
        addLabeledField(service, "Service Date (YYYY-MM-DD)", serviceDateField, 1);
        addLabeledField(service, "Service Type", serviceTypeChooser, 2);
        addLabeledField(service, "Cost", costSpinner, 3);
        addLabeledField(service, "Distance Since Last Service (km)", distanceKmSpinner, 4);

        JButton addServiceBtn = createActionButton("Log Service", new Color(196, 104, 5));
        addServiceBtn.addActionListener(this::addServiceEntry);
        addButtonRow(service, addServiceBtn, 5);

        JPanel summary = new JPanel(new BorderLayout(8, 8));
        summary.setOpaque(false);
        summary.setBorder(new EmptyBorder(10, 6, 10, 6));

        JPanel summaryTop = new JPanel(new GridBagLayout());
        summaryTop.setOpaque(false);
        addLabeledField(summaryTop, "Vehicle", summaryVehicleChooser, 0);
        addLabeledField(summaryTop, "Current Distance (km)", summaryDistanceSpinner, 1);
        JButton updateDistanceBtn = createActionButton("Update Distance", new Color(18, 84, 132));
        updateDistanceBtn.addActionListener(this::updateVehicleDistanceFromSummary);
        addButtonRow(summaryTop, updateDistanceBtn, 2);

        summaryVehicleChooser.addActionListener(e -> refreshSummaryTable());

        JPanel recommendationPanel = new JPanel(new BorderLayout(6, 6));
        recommendationPanel.setOpaque(false);
        JLabel recommendationLabel = new JLabel("Maintenance Recommendations");
        recommendationLabel.setFont(new Font("Trebuchet MS", Font.BOLD, 13));
        recommendationPanel.add(recommendationLabel, BorderLayout.NORTH);
        recommendationPanel.add(new JScrollPane(summaryRecommendationArea), BorderLayout.CENTER);

        summary.add(summaryTop, BorderLayout.NORTH);
        summary.add(new JScrollPane(summaryTable), BorderLayout.CENTER);
        summary.add(recommendationPanel, BorderLayout.SOUTH);

        tabs.addTab("Account", accountPanel);
        tabs.addTab("Vehicle Intake", intake);
        tabs.addTab("Service Entry", service);
        tabs.addTab("Summary", summary);

        left.add(tabs, BorderLayout.CENTER);
    }

    private void createAccount(ActionEvent e) {
        String name = accountNameField.getText().trim();
        String email = accountEmailField.getText().trim();
        String phone = accountPhoneField.getText().trim();
        String password = new String(accountPasswordField.getPassword());

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Account name, email, and password are required.");
            return;
        }

        if (password.length() < 4) {
            showError("Password must be at least 4 characters.");
            return;
        }

        int newId = createAccountInDatabase(name, email, phone, password);
        if (newId <= 0) {
            return;
        }

        loadAccountsFromDatabase();
        selectAccountById(newId);
        accountPasswordField.setText("");
        showInfo("Account created. You can now add vehicles for this account.");
    }

    private void updateAccount(ActionEvent e) {
        GarageAccount selected = (GarageAccount) accountChooser.getSelectedItem();
        if (selected == null) {
            showError("Select an account first.");
            return;
        }

        String name = accountNameField.getText().trim();
        String email = accountEmailField.getText().trim();
        String phone = accountPhoneField.getText().trim();
        String password = new String(accountPasswordField.getPassword());

        if (name.isEmpty() || email.isEmpty()) {
            showError("Account name and email are required.");
            return;
        }

        if (!password.isBlank() && password.length() < 4) {
            showError("Password must be at least 4 characters.");
            return;
        }

        if (!updateAccountInDatabase(selected.id, name, email, phone, password)) {
            return;
        }

        loadAccountsFromDatabase();
        selectAccountById(selected.id);
        accountPasswordField.setText("");

        if (authenticatedAccount != null && authenticatedAccount.id == selected.id) {
            authenticatedAccount = getAccountById(selected.id);
            if (authenticatedAccount != null) {
                setTitle("Vehicle Service Tracker - " + authenticatedAccount.name);
            }
        }

        showInfo("Account information updated.");
    }

    private void bindStatCards(JPanel top) {
        JPanel stats = null;
        for (Component c : top.getComponents()) {
            if (c instanceof JPanel && "statsArea".equals(c.getName())) {
                stats = (JPanel) c;
                break;
            }
        }

        if (stats == null) {
            return;
        }

        stats.add(createStatCard("Vehicles", totalVehiclesLabel));
        stats.add(createStatCard("Jobs", totalJobsLabel));
        stats.add(createStatCard("Revenue", totalRevenueLabel));
    }

    private JPanel createStatCard(String title, JLabel value) {
        JPanel card = new FrostPanel(16, new Color(255, 255, 255, 45));
        card.setLayout(new GridLayout(2, 1));
        card.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel t = new JLabel(title);
        t.setForeground(new Color(187, 224, 236));
        t.setFont(new Font("Trebuchet MS", Font.PLAIN, 12));

        value.setForeground(Color.WHITE);
        value.setFont(new Font("Trebuchet MS", Font.BOLD, 18));

        card.add(t);
        card.add(value);

        return card;
    }

    private void addLabeledField(JPanel panel, String label, JComponent field, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = y * 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(8, 2, 2, 2);
        panel.add(new JLabel(label), gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = y * 2 + 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 2, 4, 2);
        panel.add(field, gbc);
    }

    private void addButtonRow(JPanel panel, JButton button, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = y * 2 + 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(14, 2, 2, 2);
        panel.add(button, gbc);
    }

    private JButton createActionButton(String text, Color bg) {
        JButton button = new JButton(text);
        button.setBackground(bg);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(9, 16, 9, 16));
        return button;
    }

    private void addVehicle(ActionEvent e) {
        if (selectedAccountId <= 0) {
            showError("Create or select an account first.");
            return;
        }

        String number = vehicleNoField.getText().trim();
        String model = modelField.getText().trim();
        String maker = makerField.getText().trim();
        double currentKm = (double) vehicleCurrentKmSpinner.getValue();

        if (number.isEmpty() || model.isEmpty() || maker.isEmpty()) {
            showError("Please fill all vehicle fields.");
            return;
        }

        if (currentKm < 0) {
            showError("Distance cannot be negative.");
            return;
        }

        if (findVehicle(number) != null) {
            showError("Vehicle already registered.");
            return;
        }

        Vehicle vehicle = new Vehicle(number, model, maker, selectedAccountId, currentKm);
        if (!saveVehicleToDatabase(vehicle)) {
            return;
        }

        vehicles.add(vehicle);
        vehicleChooser.addItem(number);

        vehicleNoField.setText("");
        modelField.setText("");
        makerField.setText("");
        vehicleCurrentKmSpinner.setValue(0.0);

        refreshVehicleSummaryPanel();
        refreshStats();
        showInfo("Vehicle registered successfully.");
    }

    private void addServiceEntry(ActionEvent e) {
        if (selectedAccountId <= 0) {
            showError("Create or select an account first.");
            return;
        }

        if (vehicleChooser.getItemCount() == 0) {
            showError("No vehicles available. Register one first.");
            return;
        }

        String vehicleNo = (String) vehicleChooser.getSelectedItem();
        String dateText = serviceDateField.getText().trim();
        String serviceType = (String) serviceTypeChooser.getSelectedItem();
        double cost = (double) costSpinner.getValue();
        double distanceKm = (double) distanceKmSpinner.getValue();

        if (vehicleNo == null || vehicleNo.isBlank() || dateText.isEmpty() || serviceType == null || serviceType.isBlank()) {
            showError("Please complete all service details.");
            return;
        }

        LocalDate serviceDate;
        try {
            serviceDate = LocalDate.parse(dateText);
        } catch (DateTimeParseException ex) {
            showError("Invalid date format. Use YYYY-MM-DD.");
            return;
        }

        if (cost < 0) {
            showError("Cost cannot be negative.");
            return;
        }

        if (distanceKm < 0) {
            showError("Distance cannot be negative.");
            return;
        }

        ServiceEntry entry = new ServiceEntry(vehicleNo, serviceDate, serviceType, cost, distanceKm, selectedAccountId, false);
        if (!saveServiceEntryToDatabase(entry)) {
            return;
        }

        entries.add(entry);

        GarageVehicle vehicle = findVehicle(vehicleNo);
        if (vehicle != null) {
            vehicle.currentKm += distanceKm;
            if (!updateVehicleDistanceInDatabase(vehicle.vehicleNumber, vehicle.currentKm)) {
                return;
            }
        }

        serviceDateField.setText(LocalDate.now().toString());
        costSpinner.setValue(1200.0);
        distanceKmSpinner.setValue(0.0);

        refreshTable();
        refreshVehicleSummaryPanel();
        refreshStats();
        showInfo("Service entry saved.");
    }

    private void updateVehicleDistanceFromSummary(ActionEvent e) {
        String vehicleNo = (String) summaryVehicleChooser.getSelectedItem();
        if (vehicleNo == null || vehicleNo.isBlank()) {
            showError("Select a vehicle first.");
            return;
        }

        double newDistance = (double) summaryDistanceSpinner.getValue();
        if (newDistance < 0) {
            showError("Distance cannot be negative.");
            return;
        }

        GarageVehicle vehicle = findVehicle(vehicleNo);
        if (vehicle == null) {
            showError("Vehicle not found.");
            return;
        }

        if (newDistance < vehicle.currentKm) {
            showError("Updated distance cannot be less than existing distance.");
            return;
        }

        if (!updateVehicleDistanceInDatabase(vehicleNo, newDistance)) {
            return;
        }

        vehicle.currentKm = newDistance;
        refreshVehicleSummaryPanel();
        showInfo("Vehicle distance updated.");
    }

    private GarageVehicle findVehicle(String vehicleNo) {
        for (GarageVehicle vehicle : vehicles) {
            if (vehicle.accountId == selectedAccountId && vehicle.vehicleNumber.equalsIgnoreCase(vehicleNo)) {
                return vehicle;
            }
        }
        return null;
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        visibleEntries.clear();
        String query = filterField.getText().trim().toLowerCase();

        for (GarageServiceEntry entry : entries) {
            if (!query.isEmpty()) {
                boolean matches = entry.vehicleNumber.toLowerCase().contains(query)
                        || entry.serviceType.toLowerCase().contains(query)
                        || entry.serviceDate.toString().contains(query);
                if (!matches) {
                    continue;
                }
            }

            visibleEntries.add(entry);
            tableModel.addRow(new Object[] {
                    entry.vehicleNumber,
                    entry.serviceDate,
                    ChronoUnit.DAYS.between(entry.serviceDate, LocalDate.now()),
                    entry.serviceType,
                    moneyFmt.format(entry.cost),
                    moneyFmt.format(entry.distanceKm),
                    entry.notificationSent ? "Notified" : "Send Notification"
            });
        }
    }

    private void refreshStats() {
        totalVehiclesLabel.setText(String.valueOf(vehicles.size()));
        totalJobsLabel.setText(String.valueOf(entries.size()));

        double total = 0;
        for (GarageServiceEntry entry : entries) {
            total += entry.cost;
        }
        totalRevenueLabel.setText("INR " + moneyFmt.format(total));
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Input Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showInfo(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VehicleServiceTrackerApp app = new VehicleServiceTrackerApp();
            app.setVisible(true);
        });
    }

    private static class Vehicle {
        String vehicleNumber;
        String model;
        String manufacturer;
        int accountId;
        double currentKm;

        Vehicle(String vehicleNumber, String model, String manufacturer, int accountId, double currentKm) {
            this.vehicleNumber = vehicleNumber;
            this.model = model;
            this.manufacturer = manufacturer;
            this.accountId = accountId;
            this.currentKm = currentKm;
        }
    }

    private static class Account {
        int id;
        String name;
        String email;
        String phone;

        Account(int id, String name, String email, String phone) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.phone = phone;
        }

        @Override
        public String toString() {
            return name + " | " + email;
        }
    }

    private static class ServiceEntry {
        String vehicleNumber;
        LocalDate serviceDate;
        String serviceType;
        double cost;
        double distanceKm;
        int accountId;
        boolean notificationSent;

        ServiceEntry(String vehicleNumber, LocalDate serviceDate, String serviceType, double cost, double distanceKm, int accountId, boolean notificationSent) {
            this.vehicleNumber = vehicleNumber;
            this.serviceDate = serviceDate;
            this.serviceType = serviceType;
            this.cost = cost;
            this.distanceKm = distanceKm;
            this.accountId = accountId;
            this.notificationSent = notificationSent;
        }
    }

    private static class FrostPanel extends JPanel {
        private final int radius;
        private final Color panelColor;

        FrostPanel(int radius, Color panelColor) {
            this.radius = radius;
            this.panelColor = panelColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(panelColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class ButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
            setFocusPainted(false);
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                                 boolean hasFocus, int row, int column) {
            setText(value == null ? "Send" : value.toString());
            setBackground(new Color(25, 103, 151));
            setForeground(Color.WHITE);
            setBorder(new EmptyBorder(4, 8, 4, 8));
            setFont(new Font("Trebuchet MS", Font.PLAIN, 12));
            return this;
        }
    }

    private static class ButtonEditor extends javax.swing.DefaultCellEditor {
        private final JButton button;
        private final GarageServiceStudio parent;
        private int selectedRow;
        
        public ButtonEditor(JCheckBox checkBox, GarageServiceStudio parent) {
            super(checkBox);
            this.parent = parent;
            button = new JButton();
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.setBackground(new Color(25, 103, 151));
            button.setForeground(Color.WHITE);
            button.setBorder(new EmptyBorder(4, 8, 4, 8));
            button.setFont(new Font("Trebuchet MS", Font.PLAIN, 12));
            button.addActionListener(e -> fireEditingStopped());
        }

        @Override
        public java.awt.Component getTableCellEditorComponent(JTable table, Object value,
                                                              boolean isSelected, int row, int column) {
            selectedRow = row;
            button.setText(value == null ? "Send" : value.toString());
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            parent.sendNotificationForRow(selectedRow);
            return "Send Notification";
        }
    }

    private static class NebulaPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            GradientPaint bg = new GradientPaint(
                    0,
                    0,
                    new Color(227, 242, 253),
                    getWidth(),
                    getHeight(),
                    new Color(196, 224, 248));
            g2.setPaint(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(255, 255, 255, 120));
            g2.fillOval(40, 50, 260, 180);
            g2.fillOval(getWidth() - 320, 80, 260, 200);

            g2.setColor(new Color(18, 85, 132, 32));
            g2.fillOval(120, getHeight() - 210, 340, 170);
            g2.fillOval(getWidth() - 420, getHeight() - 240, 360, 200);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
