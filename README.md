# Mini---Project-Vehicle-Service-Tracker-
# Vehicle Service Tracker

A desktop application for managing vehicle maintenance records and service history.

## Features
- User authentication and account management
- Register and track multiple vehicles
- Log service entries with date, type, cost, and distance
- View service timeline with filtering
- Get maintenance recommendations based on distance and service history
- Multi-user support with isolated data per account

## Requirements
- Java 11 or higher
- SQLite JDBC driver (included in `lib/`)

## Build & Run
```bash
javac -cp ".:lib/sqlite-jdbc-3.49.1.0.jar" VehicleServiceTrackerApp.java
java -cp ".:lib/sqlite-jdbc-3.49.1.0.jar" VehicleServiceTrackerApp
