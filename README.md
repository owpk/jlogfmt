# JlogFmt – CLI Tool for Coloring and Filtering Logs

A lightweight command-line utility built with **Micronaut** and **Picocli** that highlights parts of log lines based on user‑defined regular expression patterns and ANSI color codes.  
Reads from standard input or files, applies the patterns, and prints the lines with matching substrings wrapped in color escape sequences.  
Optionally, it can filter out lines that do not contain any match.

## Features

- Custom **`color:regex`** pattern language – you decide which parts of a log line get which color.
- Combine multiple patterns; the tool builds a single combined regex with named groups for efficient matching.
- Built‑in default patterns for typical Spring Boot logs (timestamp, log level, ERROR highlighting).
- Filter mode (`--filter`) to show only lines that contain at least one match.
- Reads from `stdin` or from a list of files.
- Simple and fast – each line is processed by a single regex matcher.

## Requirements

- Java 25 or later
- (Optional) Gradle to build from source
- (Optional) GraalVM JDK for native image build

## Installation

### Download a pre‑built JAR

You can download the latest `log-highlighter-<version>-all.jar` from the [Releases](../../releases) page.

### Build

#### Option 1. Plain JAR from source

```bash
git clone https://github.com/owpk/jlogfmt.git
cd jlogfmt
./gradlew shadowJar
```

The fat JAR will be created in `build/libs/jlogfmt-0.1-all.jar`.

#### Option 2. (Recommended) Native image build

##### Prerequisites

Install GraalVM (25+ recommended) and set JAVA_HOME accordingly.
Make sure the native-image utility is available (gu install native-image if needed).

(Optional) Have Gradle installed, or use the Gradle wrapper (./gradlew).

Then run:

```bash
./gradlew nativeCompile
```

The executable will be created at build/native/nativeCompile/jlogfmt. You can now run it directly:

```bash
./build/native/nativeCompile/jlogfmt -p "31:ERROR" < app.log
```

## Usage

```bash
java -jar log-highlighter-0.1-all.jar [OPTIONS] [FILE...]
# or with native image 
jlogfmt [OPTIONS] [FILE...]
```

If no files are given, the program reads from standard input.

### Options

| Option | Description |
| ------ | ----------- |
| `-p, --pattern <color:regex>` | Pattern in the format `color:regex`. Can be repeated. |
| `--filter` | Print only lines that contain at least one match. |
| `-h, --help` | Show help message and exit. |
| `-V, --version` | Print version information and exit. |

### Pattern format

Each pattern must follow the syntax:

```txt
<color-code>:<regular-expression>
```

- **`<color-code>`** – an integer representing an ANSI foreground color.  
  Supported codes:  
  `30` black, `31` red, `32` green, `33` yellow, `34` blue, `35` magenta, `36` cyan, `37` white  
  `90` bright black, `91` bright red, `92` bright green, `93` bright yellow, `94` bright blue, `95` bright magenta, `96` bright cyan, `97` bright white  
  (Other codes can be added by modifying the source.)

- **`<regular-expression>`** – any valid Java regular expression.  
  **Important:** Because the pattern is given on the command line, you may need to escape backslashes. For example, in Bash use `\\d` for a digit.

The program combines all provided patterns into a single regular expression using alternation and assigns a unique named group to each pattern. When a line matches, every capturing group that participated is highlighted with its associated color.

### Default patterns (Spring Boot)

If no `-p` option is given, the tool uses these built‑in patterns:

- `32:(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}\+\d{2}:\d{2})` – timestamp in green  
- `33:(INFO|WARN|ERROR|DEBUG|TRACE)` – log level in yellow  
- `31:(ERROR)` – the word `ERROR` in red (overrides yellow on overlapping matches)

## Examples

### 1. Basic usage with default patterns

```bash
jlogfmt < application.log
```

### 2. Custom pattern: highlight class names in cyan

```bash
jlogfmt -p "36:([a-zA-Z0-9.]+(?=\\s+:))" < app.log
```

### 3. Multiple patterns: timestamp (green), level (yellow), class (cyan), ERROR (red)

```bash
jlogfmt \
  -p "32:(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+\\d{2}:\\d{2})" \
  -p "33:(INFO|WARN|ERROR|DEBUG|TRACE)" \
  -p "31:(ERROR)" \
  -p "36:([a-zA-Z0-9.]+(?=\\s+:))" \
  < springboot.log
```

### 4. Filter mode: only show lines containing a class name

```bash
jlogfmt --filter -p "36:([a-zA-Z0-9.]+)" < app.log
```

### 5. Process multiple files

```bash
jlogfmt log1.log log2.log log3.log
```

### 6. Pipe from another command

```bash
tail -f app.log | jlogfmt -p "31:(ERROR)"
```

## How It Works

1. **Pattern parsing** – each `color:regex` argument is split; the color code is mapped to an ANSI escape sequence; the regex is stored.
2. **Combined regex construction** – the tool creates a single regex of the form  
   `(?<g0>regex0)|(?<g1>regex1)|...`  
   Each pattern gets a unique named group (`g0`, `g1`, …).
3. **Matching and highlighting** – for every input line, the combined regex is applied. When a match is found, the program iterates over the named groups that participated, collects their start/end positions and associated colors, then inserts the ANSI codes around the matched substrings.
4. **Filtering** – if `--filter` is used, lines without any match are skipped.

Overlapping matches are handled by the regex engine: it finds the next match starting after the end of the previous one. If two patterns could match at exactly the same position, the one that appears first in the alternation (i.e., the one provided earlier on the command line) takes precedence.

## Color Reference

| Code | Color        | Code | Bright Color   |
|------|--------------|------|----------------|
| 30   | Black        | 90   | Bright Black   |
| 31   | Red          | 91   | Bright Red     |
| 32   | Green        | 92   | Bright Green   |
| 33   | Yellow       | 93   | Bright Yellow  |
| 34   | Blue         | 94   | Bright Blue    |
| 35   | Magenta      | 95   | Bright Magenta |
| 36   | Cyan         | 96   | Bright Cyan    |
| 37   | White        | 97   | Bright White   |

## Limitations

- Only foreground colors are supported (no background, bold, underline, etc.).
- The tool does **not** interpret log structure; it simply applies regex patterns to plain text.
- If patterns overlap, only the leftmost/longest match is highlighted according to the regex engine's rules.

## License

MIT License – feel free to use, modify, and distribute.

## Contributing

Issues and pull requests are welcome! Please ensure any new functionality is covered by tests and follows the existing pattern format.
