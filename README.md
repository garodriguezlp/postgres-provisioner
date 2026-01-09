# PostgreSQL Provisioner

A suite of Java 17 JBang-powered command-line tools for PostgreSQL database provisioning and configuration. Automate
database setup, remote access configuration, and schema migrations with ease.

## 🚀 Features

### PostgresSshConfigurator

- Configure remote PostgreSQL servers via SSH
- Automatically locate and update PostgreSQL configuration files
- Enable remote connections with one command
- Backup existing configurations before making changes
- Restart PostgreSQL service and verify status

### FlywayProvisioner

- Download migration artifacts from remote repositories
- Execute versioned database migrations using Flyway
- Support multiple schemas with independent versioning
- Baseline initialization with custom SQL scripts
- Comprehensive progress tracking and error reporting

## 📋 Requirements

- Java 17+
- [JBang](https://www.jbang.dev/) installed
- PostgreSQL server (local or remote)
- SSH access (for PostgresSshConfigurator)

## 🔧 Installation

Install JBang:

```bash
# macOS/Linux
curl -Ls https://sh.jbang.dev | bash -s - app setup

# Windows (with Chocolatey)
choco install jbang
```

Clone this repository:

```bash
git clone https://github.com/yourusername/postgres-provisioner.git
cd postgres-provisioner
```

## 📖 Usage

### Configure Remote PostgreSQL Server

```bash
jbang PostgresSshConfigurator.java \
  --host myserver.com \
  --user admin \
  --password ${SSH_PASSWORD} \
  --verbose
```

This will:

- Connect to the server via SSH
- Locate PostgreSQL configuration files
- Update `listen_addresses` to allow remote connections
- Optimize `shared_buffers` setting
- Restart PostgreSQL service

### Run Database Migrations

```bash
jbang FlywayProvisioner.java \
  --artifactory-base-url http://localhost:8080 \
  --schemas customer:1.5.0,orders:2.3.1,inventory:1.0.0 \
  --db-url jdbc:postgresql://localhost:5432/mydb \
  --db-user postgres \
  --db-password ${DB_PASSWORD} \
  --verbose
```

This will:

- Download migration artifacts for each schema
- Execute baseline SQL scripts
- Run Flyway migrations for each schema
- Generate a detailed execution report

## 🧪 Testing

A complete Docker-based test environment is included:

```bash
cd testing
./scripts/up.sh                           # Start test environment
./scripts/test-ssh-configurator.sh        # Test SSH configurator
./scripts/test-flyway-provisioner.sh      # Test Flyway provisioner
./scripts/down.sh                          # Stop environment
```

## ⚙️ Configuration

Both tools support configuration via:

1. Command-line arguments (highest priority)
2. Environment variables (via `${VAR_NAME}`)
3. `application.properties` file
4. Default values

Example `application.properties`:

```properties
db.url=jdbc:postgresql://localhost:5432/mydb
db.user=postgres
ssh.port=22
artifactory.base.url=http://localhost:8080
```

## 📝 License

MIT License - feel free to use this in your projects!

## 🤝 Contributing

Contributions welcome! Please feel free to submit a Pull Request.
