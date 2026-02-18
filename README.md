# jlogfmt

**jlogfmt** is a lightweight CLI tool for highlighting and filtering log files, especially those produced by Spring Boot, but adaptable to any log format via custom regular expressions. It reads from stdin or a file, filters by log level, includes/excludes lines with regex, and colorizes log levels for better readability.

Built with [Micronaut](https://micronaut.io/) and [Picocli](https://picocli.info/), it can be used as a standalone JAR or compiled into a native executable for fast startup.

## Features

- Read from `stdin` (pipe-friendly) or from a file (`-f, --file`).
- Filter by log levels (`-l, --level`) – `INFO`, `WARN`, `ERROR`, `DEBUG`, `TRACE` (comma-separated).
- Include lines matching a regex (`-i, --include`).
- Exclude lines matching a regex (`-e, --exclude`).
- Highlight log levels using ANSI colors (configurable).
- Customizable log parsing pattern (`-p, --pattern`) with a named capturing group `level`.
- Configuration via YAML or environment variables (thanks to Micronaut).

## Installation

### Prerequisites

- Java 25 or later (if using the JAR version)
- Gradle 8.x (or use the included Gradle wrapper)
- [GraalVM](https://www.graalvm.org/) (optional, for native image)

### Download the JAR

Build it yourself:

```bash
git clone https://github.com/owpk/jlogfmt.git
cd jlogfmt
./gradlew shadowJar
```

The JAR will be located at `target/jlogfmt-1.0.0.jar`.

### Native executable (optional)

To create a native binary using GraalVM:

```bash
./gradlew nativeCompile
```

The executable `jlogfmt` will be created in the `target/` directory. You can then move it to a directory in your `PATH`.

## Usage

```
jlogfmt [-f FILE] [-l LEVELS] [-i INCLUDE] [-e EXCLUDE] [-p PATTERN] [-hV]
```

### Options

| Option | Description |
|--------|-------------|
| `-f, --file FILE` | Read from a file instead of stdin. |
| `-l, --level LEVELS` | Comma-separated list of log levels to show (e.g., `INFO,WARN`). If not specified, all levels are shown. |
| `-i, --include REGEX` | Only show lines matching the regular expression. |
| `-e, --exclude REGEX` | Hide lines matching the regular expression. |
| `-p, --pattern REGEX` | Regular expression to parse each log line. Must contain a named group `level`. Default: `^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+(?<level>[A-Z]+)\s+` (works for default Spring Boot logs). |
| `-h, --help` | Show help message and exit. |
| `-V, --version` | Print version information and exit. |

## Examples

**Basic usage – read from file and highlight levels:**

```bash
java -jar jlogfmt-1.0.0.jar -f application.log
```

**Pipe from another command, filter by ERROR level:**

```bash
tail -f app.log | java -jar jlogfmt-1.0.0.jar -l ERROR
```

**Include lines containing "Exception" and exclude "DEBUG":**

```bash
cat log.txt | java -jar jlogfmt-1.0.0.jar -i "Exception" -e "DEBUG"
```

**Use a custom pattern for logs in a different format (e.g., `[INFO]`):**

```bash
java -jar jlogfmt-1.0.0.jar -f custom.log -p "\[(?<level>[A-Z]+)\]"
```

**Native binary usage (after building):**

```bash
./jlogfmt -f app.log --level WARN,ERROR
```

## Configuration

Colors for log levels can be customized in `src/main/resources/application.yml` or overridden via environment variables (Micronaut configuration). Defaults:

```yaml
colors:
  info: 32   # green
  warn: 33   # yellow
  error: 31  # red
  debug: 36  # cyan
  trace: 35  # magenta
  reset: 0   # reset
```

To change a color, set the corresponding environment variable, e.g.:

```bash
export COLORS_INFO=92  # bright green
```

## Building from Source

```bash
git clone https://github.com/yourusername/jlogfmt.git
cd jlogfmt
mvn clean package
```

To build a native image with GraalVM (requires `native-image` tool installed):

```bash
mvn package -Dpackaging=native-image
```

## Requirements

- Java 17+ (for running the JAR)
- Maven 3.6+ (for building)
- GraalVM 22+ (optional, for native image)

## How It Works

1. **Log parsing** – Each line is matched against the provided pattern (or default). The pattern must define a named capturing group `level` (e.g., `(?<level>[A-Z]+)`). This group’s value is used for level filtering and highlighting.
2. **Filtering** – Lines are filtered by:
   - Level set (if provided) – only those with a level in the set pass.
   - Include regex (if any) – line must contain a match.
   - Exclude regex (if any) – line must not contain a match.
3. **Highlighting** – If a level is found and its color is defined, the first occurrence of that level in the line is wrapped with ANSI color codes.
4. **Output** – Colored lines are printed to stdout. If output is redirected to a file or another program, ANSI codes remain (unless you add a `--no-color` flag – feel free to contribute!).

## Limitations / Future Improvements

- Currently only one occurrence of the level per line is highlighted.
- No automatic log format detection.
- `--no-color` flag to disable ANSI codes (useful for piping into files).
- Support for multiple files / glob patterns.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss what you would like to change.

---

Enjoy colorful logs! 🎨
