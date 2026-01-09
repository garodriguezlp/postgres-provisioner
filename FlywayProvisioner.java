///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS info.picocli:picocli:4.7.5
//DEPS org.flywaydb:flyway-core:10.4.1
//DEPS org.flywaydb:flyway-database-postgresql:10.4.1
//DEPS org.postgresql:postgresql:42.7.1
//DEPS com.squareup.okhttp3:okhttp:4.12.0
//DEPS org.tinylog:tinylog-api:2.6.2
//DEPS org.tinylog:tinylog-impl:2.6.2
//DEPS org.tinylog:slf4j-tinylog:2.6.2

//JAVA 17+

//FILES application.properties=application.properties

import com.squareup.okhttp3.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import picocli.CommandLine;
import picocli.CommandLine.*;
import org.tinylog.Logger;
import org.tinylog.configuration.Configuration;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// @todo: test this, but in order to decuple from the PostgresSshConfigurator, then comment out the posgtress config that messes up and prevents external client connection. because I want to try out this provisioning assuming db is ok. so I want to bring up the testing infra and everything to be in place. 

/**
 * Flyway Migration Orchestrator - Automates database schema provisioning by downloading
 * migration artifacts and executing Flyway migrations with baseline initialization.
 */
@Command(
    name = "flyway-provisioner",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Automates PostgreSQL schema provisioning via Flyway"
)
public class FlywayProvisioner implements Callable<Integer> {

    @Option(
        names = {"--artifactory-base-url"},
        required = true,
        description = "Base URL for migration artifacts"
    )
    private String artifactoryBaseUrl;

    // @todo: there is no auth needed, so remove all auth options
    @Option(
        names = {"--artifactory-user"},
        description = "HTTP basic auth username (optional)"
    )
    private String artifactoryUser;

    @Option(
        names = {"--artifactory-password"},
        description = "HTTP basic auth password (optional)"
    )
    private String artifactoryPassword;

    @Option(
        names = {"--schemas"},
        required = true,
        description = "Comma-separated schema:version pairs (e.g., customer:1.5.0,orders:2.3.1)"
    )
    private String schemas;

    @Option(
        names = {"--db-url"},
        required = true,
        description = "JDBC connection URL for PostgreSQL database"
    )
    private String dbUrl;

    @Option(
        names = {"--db-user"},
        required = true,
        description = "Database username with schema creation privileges"
    )
    private String dbUser;

    @Option(
        names = {"--db-password"},
        required = true,
        description = "Database password"
    )
    private String dbPassword;

    @Option(
        names = {"--baseline-location"},
        defaultValue = "./baseline-scripts",
        description = "Path to baseline SQL scripts (default: ./baseline-scripts)"
    )
    private String baselineLocation;

    // @todo: I'd like this to be a new dir each time, but a humanfriendly timestamp and create the dir in the same java "home" where the java us running
    @Option(
        names = {"--work-dir"},
        description = "Temporary directory for downloads and extraction (default: system temp)"
    )
    private String workDir;

    @Option(
        names = {"--cleanup"},
        defaultValue = "true",
        description = "Remove temporary files after execution (default: true)" // @todo: default this to false
    )
    private boolean cleanup;

    @Option(
        names = {"--fail-fast"},
        defaultValue = "false",
        description = "Stop execution on first failure (default: false)"
    )
    private boolean failFast;

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable debug logging"
    )
    private boolean verbose;

    private Path workDirectory;
    private final List<MigrationResult> results = new ArrayList<>();

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
        var propertiesUrl = FlywayProvisioner.class.getClassLoader()
            .getResource("application.properties");
        
        var cmd = new CommandLine(new FlywayProvisioner());
        if (propertiesUrl != null) {
            cmd.setDefaultValueProvider(new PropertiesDefaultProvider(new File(propertiesUrl.getFile())));
        }
        
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    // @todo: call method is huge, remember that I love small methods, where things are a single level of abstraction, refactor this
    public Integer call() throws Exception {
        Instant startTime = Instant.now();
        
        Logger.info("=".repeat(60));
        Logger.info("Flyway Migration Orchestrator");
        Logger.info("=".repeat(60));
        
        try {
            // Parse schema mappings
            Logger.info("→ Parsing schema configurations...");
            List<SchemaMapping> schemaMappings = parseSchemas(schemas);
            Logger.info("✓ Found {} schema(s) to process", schemaMappings.size());
            
            // Initialize work directory
            Logger.info("→ Initializing work directory...");
            initializeWorkDirectory();
            Logger.info("✓ Work directory: {}", workDirectory);
            
            // Validate baseline location
            Logger.info("→ Validating baseline scripts...");
            validateBaselineLocation();
            Logger.info("✓ Baseline location validated: {}", baselineLocation);
            
            // Download and extract artifacts
            Logger.info("→ Downloading migration artifacts...");
            ArtifactDownloader downloader = new ArtifactDownloader(
                artifactoryBaseUrl, 
                artifactoryUser, 
                artifactoryPassword,
                workDirectory
            );
            
            for (int i = 0; i < schemaMappings.size(); i++) {
                SchemaMapping mapping = schemaMappings.get(i);
                Logger.info("  [{}/{}] Downloading schema '{}' v{}...", 
                    i + 1, schemaMappings.size(), mapping.schemaName, mapping.version);
                
                try {
                    Path artifactPath = downloader.download(mapping);
                    Logger.info("  ✓ Downloaded {} ({} MB)", 
                        artifactPath.getFileName(), 
                        String.format("%.2f", Files.size(artifactPath) / 1024.0 / 1024.0));
                    
                    Logger.debug("  Extracting artifact...");
                    Path extractedPath = downloader.extract(artifactPath, mapping);
                    Logger.info("  ✓ Extracted to {}", extractedPath);
                    
                    mapping.migrationsPath = extractedPath;
                    
                } catch (Exception e) {
                    Logger.error("  ✗ Failed to download/extract schema '{}': {}", 
                        mapping.schemaName, e.getMessage());
                    
                    if (failFast) {
                        throw e;
                    }
                    
                    results.add(new MigrationResult(mapping, false, 0, 0, e.getMessage()));
                    Logger.warn("  Skipping schema '{}', continuing with remaining schemas", mapping.schemaName);
                }
            }
            
            // Execute migrations
            Logger.info("→ Executing database migrations...");
            DatabaseManager dbManager = new DatabaseManager(dbUrl, dbUser, dbPassword, baselineLocation);
            
            for (int i = 0; i < schemaMappings.size(); i++) {
                SchemaMapping mapping = schemaMappings.get(i);
                
                // Skip if download/extraction failed
                if (mapping.migrationsPath == null) {
                    continue;
                }
                
                Logger.info("  [{}/{}] Processing schema '{}'...", 
                    i + 1, schemaMappings.size(), mapping.schemaName);
                
                try {
                    Instant schemaStart = Instant.now();
                    
                    // @todo: this is not what I want, I made a mistake with the original spec. The baseline are a set of scripts that are run once at the very begining of the process, not per schema. refactor to adhere to that
                    // @todo: make sure that in our sample baseline, we simply create all the schemas, regarless of the schema being processed, and users to connect to those schemas, where search path is set to the schema being processed. I mean this is like one script works for all schemas thing
                    // Execute baseline
                    Logger.info("    Executing baseline scripts...");
                    dbManager.executeBaseline(mapping.schemaName);
                    Logger.info("    ✓ Baseline completed");
                    
                    // Run Flyway migrations
                    Logger.info("    Running Flyway migrations...");
                    int migrationsApplied = dbManager.migrate(mapping);
                    
                    long durationSeconds = Duration.between(schemaStart, Instant.now()).getSeconds();
                    Logger.info("    ✓ Applied {} migration(s) in {}s", migrationsApplied, durationSeconds);
                    
                    results.add(new MigrationResult(
                        mapping, 
                        true, 
                        migrationsApplied, 
                        durationSeconds, 
                        null
                    ));
                    
                } catch (Exception e) {
                    Logger.error("    ✗ Migration failed for schema '{}': {}", 
                        mapping.schemaName, e.getMessage());
                    Logger.debug(e, "Migration error details");
                    
                    if (failFast) {
                        throw e;
                    }
                    
                    results.add(new MigrationResult(mapping, false, 0, 0, e.getMessage()));
                    Logger.warn("    Skipping schema '{}', continuing with remaining schemas", mapping.schemaName);
                }
            }
            
            // Generate summary report
            long totalDurationSeconds = Duration.between(startTime, Instant.now()).getSeconds();
            generateSummaryReport(totalDurationSeconds);
            
            // Cleanup if requested
            if (cleanup) {
                Logger.info("→ Cleaning up temporary files...");
                cleanupWorkDirectory();
                Logger.info("✓ Cleanup completed");
            }
            
            // Determine exit code based on results
            long failedCount = results.stream().filter(r -> !r.success).count();
            if (failedCount > 0) {
                Logger.warn("=".repeat(60));
                Logger.warn("✗ Completed with {} failure(s)", failedCount);
                Logger.warn("=".repeat(60));
                return 1;
            }
            
            Logger.info("=".repeat(60));
            Logger.info("✓ All migrations completed successfully!");
            Logger.info("=".repeat(60));
            return 0;
            
        } catch (Exception e) {
            Logger.error(e, "✗ Provisioning failed: {}", e.getMessage());
            return 1;
        }
    }

    private List<SchemaMapping> parseSchemas(String schemasStr) {
        List<SchemaMapping> mappings = new ArrayList<>();
        
        for (String pair : schemasStr.split(",")) {
            String[] parts = pair.trim().split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid schema:version format: " + pair);
            }
            
            String schemaName = parts[0].trim();
            String version = parts[1].trim();
            
            Logger.debug("Parsed schema mapping: {} -> {}", schemaName, version);
            mappings.add(new SchemaMapping(schemaName, version));
        }
        
        return mappings;
    }

    private void initializeWorkDirectory() throws IOException {
        if (workDir != null && !workDir.isEmpty()) {
            workDirectory = Paths.get(workDir);
        } else {
            workDirectory = Files.createTempDirectory("flyway-work-");
        }
        
        if (!Files.exists(workDirectory)) {
            Files.createDirectories(workDirectory);
        }
        
        Logger.debug("Work directory initialized: {}", workDirectory);
    }

    private void validateBaselineLocation() {
        Path baselinePath = Paths.get(baselineLocation);
        if (!Files.exists(baselinePath) || !Files.isDirectory(baselinePath)) {
            throw new IllegalArgumentException("Baseline location does not exist or is not a directory: " + baselineLocation);
        }
        
        Logger.debug("Baseline location validated: {}", baselinePath.toAbsolutePath());
    }

    private void cleanupWorkDirectory() {
        if (workDirectory != null && Files.exists(workDirectory)) {
            try {
                deleteDirectory(workDirectory);
                Logger.debug("Deleted work directory: {}", workDirectory);
            } catch (IOException e) {
                Logger.warn("Failed to cleanup work directory: {}", e.getMessage());
            }
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        Logger.debug("Failed to delete: {}", path);
                    }
                });
        }
    }

    private void generateSummaryReport(long totalDurationSeconds) {
        long successCount = results.stream().filter(r -> r.success).count();
        long failedCount = results.stream().filter(r -> !r.success).count();
        int totalMigrations = results.stream().mapToInt(r -> r.migrationsApplied).sum();
        
        Logger.info("");
        Logger.info("=".repeat(60));
        Logger.info("  Flyway Migration Execution Summary");
        Logger.info("=".repeat(60));
        Logger.info("Total Schemas: {}", results.size());
        Logger.info("Successful: {}", successCount);
        Logger.info("Failed: {}", failedCount);
        Logger.info("Total Migrations Applied: {}", totalMigrations);
        Logger.info("Total Execution Time: {}s", totalDurationSeconds);
        Logger.info("");
        Logger.info("Details:");
        
        for (MigrationResult result : results) {
            if (result.success) {
                Logger.info("  ✓ {} (v{}) - {}s - {} migration(s) applied",
                    result.mapping.schemaName,
                    result.mapping.version,
                    result.durationSeconds,
                    result.migrationsApplied);
                
                if (verbose && result.mapping.appliedMigrations != null) {
                    for (String migration : result.mapping.appliedMigrations) {
                        Logger.info("      - {}", migration);
                    }
                }
            } else {
                Logger.info("  ✗ {} (v{}) - FAILED - {}",
                    result.mapping.schemaName,
                    result.mapping.version,
                    result.errorMessage);
            }
        }
        
        Logger.info("=".repeat(60));
    }

    /**
     * Schema mapping containing name, version, and migration path
     */
    static class SchemaMapping {
        final String schemaName;
        final String version;
        Path migrationsPath;
        List<String> appliedMigrations;

        SchemaMapping(String schemaName, String version) {
            this.schemaName = schemaName;
            this.version = version;
        }
    }

    /**
     * Migration result for reporting
     */
    static class MigrationResult {
        final SchemaMapping mapping;
        final boolean success;
        final int migrationsApplied;
        final long durationSeconds;
        final String errorMessage;

        MigrationResult(SchemaMapping mapping, boolean success, int migrationsApplied, 
                       long durationSeconds, String errorMessage) {
            this.mapping = mapping;
            this.success = success;
            this.migrationsApplied = migrationsApplied;
            this.durationSeconds = durationSeconds;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Handles downloading and extracting migration artifacts
     */
    static class ArtifactDownloader {
        private final String baseUrl;
        private final String username;
        private final String password;
        private final Path workDirectory;
        private final OkHttpClient httpClient;

        ArtifactDownloader(String baseUrl, String username, String password, Path workDirectory) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.username = username;
            this.password = password;
            this.workDirectory = workDirectory;
            
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS);
            
            // Add basic auth if credentials provided
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                builder.authenticator((route, response) -> {
                    String credential = Credentials.basic(username, password);
                    return response.request().newBuilder()
                        .header("Authorization", credential)
                        .build();
                });
            }
            
            this.httpClient = builder.build();
        }

        Path download(SchemaMapping mapping) throws IOException {
            // Construct URL: {base-url}/{schema-name}/{schema-name}-{version}.zip
            String artifactName = mapping.schemaName + "-" + mapping.version + ".zip";
            String url = baseUrl + "/" + mapping.schemaName + "/" + artifactName;
            
            Logger.debug("Constructed download URL: {}", url);
            
            Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                Logger.debug("Response: {} {}", response.code(), response.message());
                
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code() + ": " + response.message() + " - URL: " + url);
                }
                
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty response body from: " + url);
                }
                
                Path downloadPath = workDirectory.resolve(artifactName);
                
                try (InputStream in = body.byteStream();
                     FileOutputStream out = new FileOutputStream(downloadPath.toFile())) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                
                Logger.debug("Downloaded to: {}", downloadPath);
                return downloadPath;
            }
        }

        Path extract(Path zipFile, SchemaMapping mapping) throws IOException {
            Path extractDir = workDirectory.resolve(mapping.schemaName);
            
            if (!Files.exists(extractDir)) {
                Files.createDirectories(extractDir);
            }
            
            Logger.debug("Extracting ZIP to: {}", extractDir);
            
            int filesExtracted = 0;
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = extractDir.resolve(entry.getName());
                    
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        
                        try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = zis.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }
                        filesExtracted++;
                    }
                    
                    zis.closeEntry();
                }
            }
            
            Logger.debug("Extracted {} file(s)", filesExtracted);
            return extractDir;
        }
    }

    /**
     * Manages database operations including baseline and migrations
     */
    static class DatabaseManager {
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final String baselineLocation;

        DatabaseManager(String jdbcUrl, String username, String password, String baselineLocation) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.baselineLocation = baselineLocation;
        }

        void executeBaseline(String schemaName) throws SQLException, IOException {
            Path baselinePath = Paths.get(baselineLocation);
            
            // Execute baseline scripts in order
            List<Path> scripts = new ArrayList<>();
            try (var stream = Files.list(baselinePath)) {
                stream.filter(p -> p.toString().endsWith(".sql"))
                      .sorted()
                      .forEach(scripts::add);
            }
            
            if (scripts.isEmpty()) {
                Logger.warn("No baseline scripts found in: {}", baselineLocation);
                return;
            }
            
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                for (Path script : scripts) {
                    Logger.debug("Executing baseline script: {}", script.getFileName());
                    
                    String sql = Files.readString(script);
                    
                    // Replace placeholders
                    sql = sql.replace("${schema_name}", schemaName);
                    sql = sql.replace("${database_name}", getDatabaseName(jdbcUrl));
                    
                    // Execute SQL statements
                    executeSqlScript(conn, sql, script.getFileName().toString());
                }
            }
        }

        int migrate(SchemaMapping mapping) throws Exception {
            Logger.debug("Configuring Flyway for schema: {}", mapping.schemaName);
            
            // Verify migrations directory exists
            if (!Files.exists(mapping.migrationsPath) || !Files.isDirectory(mapping.migrationsPath)) {
                throw new IllegalStateException("Migrations path does not exist: " + mapping.migrationsPath);
            }
            
            // Configure Flyway
            Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("filesystem:" + mapping.migrationsPath.toAbsolutePath())
                .schemas(mapping.schemaName)
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateMigrationNaming(true)
                .outOfOrder(false)
                .load();
            
            Logger.debug("Flyway configured with location: filesystem:{}", mapping.migrationsPath.toAbsolutePath());
            
            // Execute migrations
            MigrateResult result = flyway.migrate();
            
            // Track applied migrations for verbose reporting
            if (result.migrationsExecuted > 0) {
                mapping.appliedMigrations = new ArrayList<>();
                for (MigrationInfo info : flyway.info().applied()) {
                    if (info.getVersion() != null) {
                        mapping.appliedMigrations.add(info.getScript());
                        Logger.debug("Applied migration: {}", info.getScript());
                    }
                }
            }
            
            return result.migrationsExecuted;
        }

        private void executeSqlScript(Connection conn, String sql, String scriptName) throws SQLException {
            // Split by semicolon and execute each statement
            String[] statements = sql.split(";");
            
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                
                Logger.debug("Executing SQL: {}", trimmed.substring(0, Math.min(60, trimmed.length())) + "...");
                
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(trimmed);
                }
            }
        }

        private String getDatabaseName(String jdbcUrl) {
            // Extract database name from JDBC URL
            // Format: jdbc:postgresql://host:port/database
            int lastSlash = jdbcUrl.lastIndexOf('/');
            if (lastSlash != -1 && lastSlash < jdbcUrl.length() - 1) {
                String dbPart = jdbcUrl.substring(lastSlash + 1);
                // Remove query parameters if present
                int queryStart = dbPart.indexOf('?');
                if (queryStart != -1) {
                    return dbPart.substring(0, queryStart);
                }
                return dbPart;
            }
            return "postgres"; // fallback
        }
    }
}
