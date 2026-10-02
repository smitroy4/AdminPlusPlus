package com.smit.taskportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly typed view of the {@code app.*} block in {@code application.yml}. */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Seed seed = new Seed();

    public Seed getSeed() {
        return seed;
    }

    /** Demo accounts created on first start so the portal is usable immediately. */
    public static class Seed {

        private boolean enabled = true;
        private boolean sampleTasks = true;

        private String adminUsername = "admin";
        private String adminPassword = "Admin@12345";
        private String adminEmail = "admin@taskportal.local";

        private String managerUsername = "manager";
        private String managerPassword = "Manager@12345";
        private String managerEmail = "manager@taskportal.local";

        private String coordinatorUsername = "coordinator";
        private String coordinatorPassword = "Coordinator@12345";
        private String coordinatorEmail = "coordinator@taskportal.local";

        private String employeeUsername = "employee";
        private String employeePassword = "Employee@12345";
        private String employeeEmail = "employee@taskportal.local";

        private String clientUsername = "client";
        private String clientPassword = "Client@12345";
        private String clientEmail = "client@acme.example";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSampleTasks() {
            return sampleTasks;
        }

        public void setSampleTasks(boolean sampleTasks) {
            this.sampleTasks = sampleTasks;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }

        public String getAdminEmail() {
            return adminEmail;
        }

        public void setAdminEmail(String adminEmail) {
            this.adminEmail = adminEmail;
        }

        public String getManagerUsername() {
            return managerUsername;
        }

        public void setManagerUsername(String managerUsername) {
            this.managerUsername = managerUsername;
        }

        public String getManagerPassword() {
            return managerPassword;
        }

        public void setManagerPassword(String managerPassword) {
            this.managerPassword = managerPassword;
        }

        public String getManagerEmail() {
            return managerEmail;
        }

        public void setManagerEmail(String managerEmail) {
            this.managerEmail = managerEmail;
        }

        public String getCoordinatorUsername() {
            return coordinatorUsername;
        }

        public void setCoordinatorUsername(String coordinatorUsername) {
            this.coordinatorUsername = coordinatorUsername;
        }

        public String getCoordinatorPassword() {
            return coordinatorPassword;
        }

        public void setCoordinatorPassword(String coordinatorPassword) {
            this.coordinatorPassword = coordinatorPassword;
        }

        public String getCoordinatorEmail() {
            return coordinatorEmail;
        }

        public void setCoordinatorEmail(String coordinatorEmail) {
            this.coordinatorEmail = coordinatorEmail;
        }

        public String getClientUsername() {
            return clientUsername;
        }

        public void setClientUsername(String clientUsername) {
            this.clientUsername = clientUsername;
        }

        public String getClientPassword() {
            return clientPassword;
        }

        public void setClientPassword(String clientPassword) {
            this.clientPassword = clientPassword;
        }

        public String getClientEmail() {
            return clientEmail;
        }

        public void setClientEmail(String clientEmail) {
            this.clientEmail = clientEmail;
        }

        public String getEmployeeUsername() {
            return employeeUsername;
        }

        public void setEmployeeUsername(String employeeUsername) {
            this.employeeUsername = employeeUsername;
        }

        public String getEmployeePassword() {
            return employeePassword;
        }

        public void setEmployeePassword(String employeePassword) {
            this.employeePassword = employeePassword;
        }

        public String getEmployeeEmail() {
            return employeeEmail;
        }

        public void setEmployeeEmail(String employeeEmail) {
            this.employeeEmail = employeeEmail;
        }
    }
}
