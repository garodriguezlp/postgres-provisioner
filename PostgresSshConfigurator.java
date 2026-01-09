///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS info.picocli:picocli:4.7.5
//DEPS org.tinylog:tinylog-api:2.6.2
//DEPS org.tinylog:tinylog-impl:2.6.2
//DEPS com.jcraft:jsch:0.1.55

//JAVA 17+

//FILES application.properties=application.properties

import com.jcraft.jsch.*;
import picocli.CommandLine;
import picocli.CommandLine.*;
import org.tinylog.Logger;
import org.tinylog.configuration.Configuration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * PostgreSQL SSH Configurator - Connects to a remote PostgreSQL server via SSH
 * to configure it for remote connections and restart the service.
 */
@Command(
    name = "postgres-ssh-configurator",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Configures PostgreSQL for remote access via SSH"
)
public class PostgresSshConfigurator implements Callable<Integer> {

    @Option(
        names = {"--host"},
        required = true,
        description = "Target server hostname or IP address"
    )
    private String host;

    @Option(
        names = {"--port"},
        defaultValue = "22",
        description = "SSH port (default: 22)"
    )
    private int port;

    @Option(
        names = {"--user"},
        required = true,
        description = "SSH username"
    )
    private String user;

    @Option(
        names = {"--password"},
        required = true,
        description = "SSH password"
    )
    private String password;

    @Option(
        names = {"--pg-config-path"},
        description = "PostgreSQL configuration directory path"
    )
    private String pgConfigPath;

    @Option(
        names = {"--pg-version"},
        defaultValue = "15",
        description = "PostgreSQL major version (default: 15)"
    )
    private String pgVersion;

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable debug logging"
    )
    private boolean verbose;

    private SshExecutor sshExecutor;

    static {
        configureLogging();
    }

    private static void configureLogging() {
        // Check for verbose flag in args
        String level = "INFO";
        for (String arg : System.getProperty("sun.java.command", "").split(" ")) {
            if (arg.equals("--verbose") || arg.equals("-v")) {
                level = "DEBUG";
                break;
            }
        }

        Map<String, String> config = new HashMap<>();
        config.put("writer", "console");
        config.put("writer.format", "{date: yyyy-MM-dd HH:mm:ss} {level|min-size=5} [{thread}] {class-name} - {message}");
        config.put("writer.level", level);
        Configuration.replace(config);
    }

    public static void main(String[] args) {
        // Load properties file from classpath
        var propertiesUrl = PostgresSshConfigurator.class.getClassLoader()
            .getResource("application.properties");
        
        var cmd = new CommandLine(new PostgresSshConfigurator());
        if (propertiesUrl != null) {
            cmd.setDefaultValueProvider(new PropertiesDefaultProvider(new File(propertiesUrl.getFile())));
        }
        
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        Logger.info("=".repeat(60));
        Logger.info("PostgreSQL SSH Configurator");
        Logger.info("=".repeat(60));
        Logger.info("Target: {}@{}:{}", user, host, port);
        Logger.info("PostgreSQL Version: {}", pgVersion);
        
        try {
            // Initialize SSH executor
            sshExecutor = new SshExecutor(host, port, user, password);
            
            // Connect to SSH
            Logger.info("→ Connecting to SSH...");
            sshExecutor.connect();
            Logger.info("✓ SSH connection established");
            
            // Locate config files
            Logger.info("→ Locating PostgreSQL configuration files...");
            String configDir = locateConfigDirectory();
            Logger.info("✓ Found configuration directory: {}", configDir);
            
            // Backup configs
            Logger.info("→ Creating backup of configuration files...");
            backupConfigs(configDir);
            Logger.info("✓ Backup completed");
            
            // Modify configs
            Logger.info("→ Modifying PostgreSQL configuration...");
            modifyPostgresqlConf(configDir);
            modifyPgHbaConf(configDir);
            Logger.info("✓ Configuration modified");
            
            // Restart PostgreSQL
            Logger.info("→ Restarting PostgreSQL service...");
            restartPostgreSQL();
            Logger.info("✓ PostgreSQL restarted successfully");
            
            // Verify status
            Logger.info("→ Verifying PostgreSQL status...");
            verifyPostgreSQLStatus();
            Logger.info("✓ PostgreSQL is running and accepting connections");
            
            Logger.info("=".repeat(60));
            Logger.info("✓ Configuration completed successfully!");
            Logger.info("=".repeat(60));
            
            return 0;
            
        } catch (Exception e) {
            Logger.error(e, "✗ Configuration failed: {}", e.getMessage());
            return 1;
        } finally {
            if (sshExecutor != null) {
                sshExecutor.disconnect();
            }
        }
    }

    private String locateConfigDirectory() throws Exception {
        if (pgConfigPath != null && !pgConfigPath.isEmpty()) {
            // Normalize path to Unix format (remove Windows-style prefixes)
            String normalizedPath = pgConfigPath;
            
            // Remove Windows drive letters (C:, D:, etc.)
            normalizedPath = normalizedPath.replaceAll("^[A-Za-z]:", "");
            
            // Convert backslashes to forward slashes
            normalizedPath = normalizedPath.replace("\\", "/");
            
            // Remove Git Bash prefix if present (/c/ becomes /, /d/ becomes /, etc.)
            normalizedPath = normalizedPath.replaceAll("^/[a-z]/", "/");
            
            Logger.debug("Using provided config path: {}", normalizedPath);
            return normalizedPath;
        }
        
        // Try common locations
        String[] possiblePaths = {
            "/etc/postgresql/" + pgVersion + "/main",
            "/var/lib/pgsql/" + pgVersion + "/data",
            "/usr/local/pgsql/data",
            "/var/lib/postgresql/" + pgVersion + "/main",
            "/var/lib/postgresql/data"
        };
        
        for (String path : possiblePaths) {
            Logger.debug("Checking path: {}", path);
            String result = sshExecutor.executeCommand("sudo test -f " + path + "/postgresql.conf && echo 'found' || echo 'not found'");
            if (result.trim().equals("found")) {
                Logger.debug("Found config at: {}", path);
                return path;
            }
        }
        
        throw new RuntimeException("Could not locate PostgreSQL configuration directory. Please specify --pg-config-path");
    }

    private void backupConfigs(String configDir) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        String postgresqlConf = configDir + "/postgresql.conf";
        String pgHbaConf = configDir + "/pg_hba.conf";
        
        String postgresqlBackup = postgresqlConf + "." + timestamp + ".backup";
        String pgHbaBackup = pgHbaConf + "." + timestamp + ".backup";
        
        Logger.debug("Backing up {} to {}", postgresqlConf, postgresqlBackup);
        sshExecutor.executeCommand("sudo cp " + postgresqlConf + " " + postgresqlBackup);
        
        Logger.debug("Backing up {} to {}", pgHbaConf, pgHbaBackup);
        sshExecutor.executeCommand("sudo cp " + pgHbaConf + " " + pgHbaBackup);
    }

    private void modifyPostgresqlConf(String configDir) throws Exception {
        String confFile = configDir + "/postgresql.conf";
        
        Logger.debug("Modifying listen_addresses in {}", confFile);
        
        // Set listen_addresses = '*'
        String sedCmd = "sudo sed -i \"s/^#*listen_addresses.*=.*/listen_addresses = '*'/\" " + confFile;
        sshExecutor.executeCommand(sedCmd);
        
        // Ensure it's uncommented and set
        String ensureCmd = "sudo grep -q \"^listen_addresses = '\\*'\" " + confFile + " || " +
                          "echo \"listen_addresses = '*'\" | sudo tee -a " + confFile;
        sshExecutor.executeCommand(ensureCmd);
        
        // Set shared_buffers = 256MB
        Logger.debug("Modifying shared_buffers in {}", confFile);
        sedCmd = "sudo sed -i \"s/^#*shared_buffers.*=.*/shared_buffers = 256MB/\" " + confFile;
        sshExecutor.executeCommand(sedCmd);
        
        ensureCmd = "sudo grep -q \"^shared_buffers = 256MB\" " + confFile + " || " +
                   "echo \"shared_buffers = 256MB\" | sudo tee -a " + confFile;
        sshExecutor.executeCommand(ensureCmd);
        
        // Verify changes
        Logger.debug("Verifying postgresql.conf changes...");
        String listenAddr = sshExecutor.executeCommand("sudo grep \"^listen_addresses\" " + confFile);
        String sharedBuf = sshExecutor.executeCommand("sudo grep \"^shared_buffers\" " + confFile);
        
        Logger.debug("listen_addresses: {}", listenAddr.trim());
        Logger.debug("shared_buffers: {}", sharedBuf.trim());
    }

    private void modifyPgHbaConf(String configDir) throws Exception {
        String confFile = configDir + "/pg_hba.conf";
        
        Logger.debug("Modifying {}", confFile);
        
        // Add entry for remote connections if not exists
        String entry = "host    all             all             0.0.0.0/0               md5";
        String checkCmd = "sudo grep -q \"^host.*all.*all.*0.0.0.0/0\" " + confFile + " && echo 'exists' || echo 'not exists'";
        String result = sshExecutor.executeCommand(checkCmd);
        
        if (result.trim().equals("not exists")) {
            Logger.debug("Adding remote access entry to pg_hba.conf");
            sshExecutor.executeCommand("echo \"" + entry + "\" | sudo tee -a " + confFile);
        } else {
            Logger.debug("Remote access entry already exists in pg_hba.conf");
        }
        
        // Verify changes
        Logger.debug("Verifying pg_hba.conf changes...");
        String hbaContents = sshExecutor.executeCommand("sudo grep \"^host.*all.*all.*0.0.0.0/0\" " + confFile);
        Logger.debug("pg_hba.conf entry: {}", hbaContents.trim());
    }

    private void restartPostgreSQL() throws Exception {
        // Try different restart methods
        
        // Method 1: Try systemctl first
        try {
            Logger.debug("Attempting to restart PostgreSQL using systemctl...");
            sshExecutor.executeCommand("sudo systemctl restart postgresql");
            Thread.sleep(2000); // Wait for service to restart
            return;
        } catch (Exception e) {
            Logger.debug("systemctl failed: {}", e.getMessage());
        }
        
        // Method 2: Try pg_ctl with full path
        try {
            Logger.debug("Attempting to restart PostgreSQL using pg_ctl...");
            sshExecutor.executeCommand("sudo -u postgres /usr/lib/postgresql/" + pgVersion + "/bin/pg_ctl restart -D /var/lib/postgresql/data");
            Thread.sleep(2000);
            return;
        } catch (Exception e) {
            Logger.debug("pg_ctl with version path failed: {}", e.getMessage());
        }
        
        // Method 3: Send SIGHUP to postgres process (reload config without full restart)
        try {
            Logger.debug("Attempting to reload PostgreSQL configuration using SIGHUP...");
            String pgPidCmd = "sudo cat /var/lib/postgresql/data/postmaster.pid | head -1";
            String pid = sshExecutor.executeCommand(pgPidCmd).trim();
            Logger.debug("PostgreSQL PID: {}", pid);
            sshExecutor.executeCommand("sudo kill -HUP " + pid);
            Thread.sleep(1000);
            Logger.info("PostgreSQL configuration reloaded (SIGHUP)");
            return;
        } catch (Exception e) {
            Logger.debug("SIGHUP reload failed: {}", e.getMessage());
        }
        
        throw new RuntimeException("Failed to restart/reload PostgreSQL service. Please restart manually.");
    }

    private void verifyPostgreSQLStatus() throws Exception {
        // Reconnect if session was closed during restart
        if (sshExecutor.session == null || !sshExecutor.session.isConnected()) {
            Logger.debug("SSH session disconnected, reconnecting...");
            sshExecutor.connect();
        }
        
        // Check if PostgreSQL is running
        try {
            Logger.debug("Checking PostgreSQL status...");
            String status = sshExecutor.executeCommand("sudo -u postgres pg_isready");
            Logger.debug("pg_isready output: {}", status.trim());
            
            if (!status.contains("accepting connections")) {
                throw new RuntimeException("PostgreSQL is not accepting connections");
            }
        } catch (Exception e) {
            Logger.warn("pg_isready check failed, trying alternative method...");
            // Alternative: check if process is running
            String psCheck = sshExecutor.executeCommand("ps aux | grep postgres | grep -v grep | head -1");
            if (psCheck.trim().isEmpty()) {
                throw new RuntimeException("PostgreSQL process not running");
            }
            Logger.debug("PostgreSQL process found: {}", psCheck.trim());
        }
    }

    /**
     * SSH command executor using JSch
     */
    static class SshExecutor {
        private final String host;
        private final int port;
        private final String user;
        private final String password;
        Session session;
        private int commandTimeout = 30000; // 30 seconds

        public SshExecutor(String host, int port, String user, String password) {
            this.host = host;
            this.port = port;
            this.user = user;
            this.password = password;
        }

        public void connect() throws JSchException {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, port);
            session.setPassword(password);
            
            // Disable strict host key checking for simplicity
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            
            Logger.debug("Connecting to {}@{}:{}", user, host, port);
            session.connect(30000); // 30 second timeout
            Logger.debug("SSH session established");
        }

        public String executeCommand(String command) throws Exception {
            if (session == null || !session.isConnected()) {
                throw new IllegalStateException("SSH session not connected");
            }

            Logger.debug("Executing command: {}", command);
            
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
            
            channel.setOutputStream(outputStream);
            channel.setErrStream(errorStream);
            
            channel.connect(commandTimeout);
            
            // Wait for command to complete with timeout
            long startTime = System.currentTimeMillis();
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() - startTime > commandTimeout) {
                    Logger.debug("Command timed out after {} ms", commandTimeout);
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Command execution interrupted", e);
                }
            }
            
            int exitStatus = channel.getExitStatus();
            String output = outputStream.toString();
            String error = errorStream.toString();
            
            channel.disconnect();
            
            Logger.debug("Command exit status: {}", exitStatus);
            if (!output.isEmpty()) {
                Logger.debug("Command output: {}", output.trim());
            }
            if (!error.isEmpty()) {
                Logger.debug("Command error: {}", error.trim());
            }
            
            // Only fail if exit status is explicitly non-zero
            // Exit status of -1 might indicate channel closed before completion (for long-running commands)
            if (exitStatus > 0 && !error.isEmpty()) {
                throw new RuntimeException("Command failed with exit code " + exitStatus + ": " + error);
            }
            
            return output;
        }

        public void disconnect() {
            if (session != null && session.isConnected()) {
                Logger.debug("Disconnecting SSH session");
                session.disconnect();
            }
        }
    }
}
