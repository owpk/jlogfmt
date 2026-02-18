package owpk;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.TextTable;
import picocli.CommandLine.IHelpSectionRenderer;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.UsageMessageSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "jlogfmt", mixinStandardHelpOptions = true, version = "0.1", description = "Highlight and filter logs using custom color:regex patterns.", header = "JLogFmt", footer = "Color codes: 30-37 (standard), 90-97 (bright). See ANSI codes for more.")
public class JlogfmtCommand implements Runnable {

    private static void customizeHelp(CommandSpec spec) {
        var COLORS_HEADING = "colorsHeading";
        var COLORS_SECTION = "colorsSection";
        var MACROS_HEADING = "macrosHeading";
        var MACROS_SECTION = "macrosSection";

        Map<String, IHelpSectionRenderer> renderers = spec.usageMessage().sectionMap();
        renderers.put(COLORS_HEADING, help -> help.createHeading("\nColor codes (foreground only):\n"));
        renderers.put(COLORS_SECTION, help -> {
            var table = TextTable.forColumnWidths(help.ansi(), 20, 20, 20, 20);
            table.addRowValues("30 Black", "31 Red", "32 Green", "33 Yellow");
            table.addRowValues("34 Blue", "35 Magenta", "36 Cyan", "37 White");
            table.addRowValues("90 Bright Black", "91 Bright Red", "92 Bright Green", "93 Bright Yellow");
            table.addRowValues("94 Bright Blue", "95 Bright Magenta", "96 Bright Cyan", "97 Bright White");
            return table.toString();
        });

        renderers.put(MACROS_HEADING, help -> help.createHeading("\nBuilt-in macros (use like 31:{NAME}):\n"));
        renderers.put(MACROS_SECTION, help -> {
            var table = TextTable.forColumnWidths(help.ansi(), 25, 80);
            for (MacroInfo info : MACROS_.values())
                table.addRowValues("{" + info.name + "}", info.description);
            return table.toString();
        });

        List<String> keys = new ArrayList<>(spec.usageMessage().sectionKeys());
        int index = keys.indexOf(UsageMessageSpec.SECTION_KEY_FOOTER_HEADING);
        if (index < 0)
            index = keys.size(); // если нет, добавим в конец
        keys.add(index, COLORS_HEADING);
        keys.add(index + 1, COLORS_SECTION);
        keys.add(index + 2, MACROS_HEADING);
        keys.add(index + 3, MACROS_SECTION);
        spec.usageMessage().sectionKeys(keys);
    }

    public static void main(String[] args) {
        try {
            var command = new JlogfmtCommand();
            var cmd = new CommandLine(command);

            customizeHelp(cmd.getCommandSpec());

            cmd.execute(args);
        } catch (Exception e) {
            err.log(e.getLocalizedMessage());
        }
    }

    private static record MacroInfo(
            String name,
            String pattern,
            String description) {
    }

    @Option(names = { "-p",
            "--pattern" }, description = "Pattern in format 'color:regex'. Can be repeated. Example: 31:{TIMESTAMP}")
    private List<String> userPatterns;

    @Option(names = { "--filter" }, description = "Only print lines that match at least one pattern")
    private boolean filterOnly;

    @Parameters(description = "Files to process (if none, read from stdin)")
    private List<File> files;

    // Default patterns for Spring Boot logs using macros for readability
    private static final List<String> DEFAULT_PATTERNS = Arrays.asList(
            "32:{TS_ISO}", // timestamp in green
            "33:{LOGLEVEL}", // level in yellow
            "31:(ERROR)" // ERROR in red (overrides yellow if both match)
    );

    private static final Map<String, MacroInfo> MACROS_ = new HashMap<>();
    static {
        MACROS_.put("TS_ISO", new MacroInfo(
                "TS_ISO",
                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(?:Z|[+-]\\d{2}:\\d{2})",
                "ISO 8601 timestamp with optional Z or offset (e.g., 2026-02-19T04:14:23.848Z or +08:00)"));

        MACROS_.put("TS_SIMPLE", new MacroInfo(
                "TS_SIMPLE",
                "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}",
                "Simple date and time: yyyy-MM-dd HH:mm:ss"));

        MACROS_.put("TS_UNIX", new MacroInfo(
                "TS_UNIX",
                "\\d+",
                "Unix timestamp (seconds since epoch)"));

        MACROS_.put("TS_RFC1123", new MacroInfo(
                "TS_RFC1123",
                "[A-Z][a-z]{2}, \\d{2} [A-Z][a-z]{2} \\d{4} \\d{2}:\\d{2}:\\d{2} [A-Z]{3}",
                "RFC 1123 date format (e.g., Tue, 19 Feb 2026 04:14:23 GMT)"));

        MACROS_.put("TS_DATE", new MacroInfo(
                "TS_DATE",
                "\\d{4}-\\d{2}-\\d{2}",
                "Date only: yyyy-MM-dd"));

        MACROS_.put("TS_TIME", new MacroInfo(
                "TS_TIME",
                "\\d{2}:\\d{2}:\\d{2}",
                "Time only: HH:mm:ss"));

        MACROS_.put("LOGLEVEL", new MacroInfo(
                "LOGLEVEL",
                "INFO|WARN|ERROR|DEBUG|TRACE",
                "Standard log levels"));
    }

    // Map color codes to ANSI escape sequences
    private static final Map<Integer, String> ANSI_COLORS = new HashMap<>();
    static {
        ANSI_COLORS.put(30, "\u001B[30m"); // black
        ANSI_COLORS.put(31, "\u001B[31m"); // red
        ANSI_COLORS.put(32, "\u001B[32m"); // green
        ANSI_COLORS.put(33, "\u001B[33m"); // yellow
        ANSI_COLORS.put(34, "\u001B[34m"); // blue
        ANSI_COLORS.put(35, "\u001B[35m"); // magenta
        ANSI_COLORS.put(36, "\u001B[36m"); // cyan
        ANSI_COLORS.put(37, "\u001B[37m"); // white
        ANSI_COLORS.put(90, "\u001B[90m"); // bright black
        ANSI_COLORS.put(91, "\u001B[91m"); // bright red
        ANSI_COLORS.put(92, "\u001B[92m"); // bright green
        ANSI_COLORS.put(93, "\u001B[93m"); // bright yellow
        ANSI_COLORS.put(94, "\u001B[94m"); // bright blue
        ANSI_COLORS.put(95, "\u001B[95m"); // bright magenta
        ANSI_COLORS.put(96, "\u001B[96m"); // bright cyan
        ANSI_COLORS.put(97, "\u001B[97m"); // bright white
    }
    private static final String RESET = "\u001B[0m";

    private void printColorTable() {
        int[] codes = { 30, 31, 32, 33, 34, 35, 36, 37, 90, 91, 92, 93, 94, 95, 96, 97 };
        String[] names = {
                "Black", "Red", "Green", "Yellow",
                "Blue", "Magenta", "Cyan", "White",
                "Bright Black", "Bright Red", "Bright Green", "Bright Yellow",
                "Bright Blue", "Bright Magenta", "Bright Cyan", "Bright White"
        };
        for (int i = 0; i < codes.length; i++) {
            info.log(new LoggerProps("  %-3d %-13s",
                    false, false,
                    List.of(codes[i], names[i])));
            if ((i + 1) % 4 == 0)
                info.log();
        }
        if (codes.length % 4 != 0)
            info.log();
    }

    private void printMacroTable() {
        for (var macInfo : MACROS_.values()) {
            info.log(new LoggerProps("  {%s} – %s%n",
                    false, false,
                    List.of(macInfo.name, macInfo.description)));
        }
    }

    @Override
    public void run() {
        // Use default patterns if none provided
        List<String> patternsToUse = (userPatterns == null || userPatterns.isEmpty())
                ? DEFAULT_PATTERNS
                : userPatterns;

        // Parse patterns into list of PatternWithColor
        List<PatternWithColor> parsedPatterns = parsePatterns(patternsToUse);
        if (parsedPatterns.isEmpty()) {
            err.log("No valid patterns provided.");
            return;
        }

        // Build combined regex with named groups
        var combinedRegex = buildCombinedRegex(parsedPatterns);
        var combinedPattern = Pattern.compile(combinedRegex);
        Map<String, Integer> groupToColor = buildGroupToColorMap(parsedPatterns);

        // Process input
        if (files == null || files.isEmpty()) {
            processStream(new BufferedReader(new InputStreamReader(System.in)), combinedPattern, groupToColor);
        } else {
            for (File file : files) {
                try (var reader = new BufferedReader(new FileReader(file))) {
                    processStream(reader, combinedPattern, groupToColor);
                } catch (Exception e) {
                    err.log("Error reading file " + file + ": " + e.getMessage());
                }
            }
        }
    }

    private List<PatternWithColor> parsePatterns(List<String> patterns) {
        List<PatternWithColor> result = new ArrayList<>();
        var patternParser = Pattern.compile("^(\\d+):(.*)$");
        for (var p : patterns) {
            var m = patternParser.matcher(p.trim());
            if (m.matches()) {
                int color = Integer.parseInt(m.group(1));
                var rawRegex = m.group(2);
                // Expand macros inside the regex part
                var expandedRegex = expandMacros(rawRegex);
                if (ANSI_COLORS.containsKey(color)) {
                    result.add(new PatternWithColor(color, expandedRegex));
                } else {
                    err.log("Warning: Unsupported color code " + color + " in pattern: " + p + ". Ignored.");
                }
            } else {
                err.log("Warning: Invalid pattern format: " + p + ". Expected 'color:regex'. Ignored.");
            }
        }
        return result;
    }

    /**
     * Replace macro placeholders {NAME} with their predefined regex patterns.
     * Unknown macros are left unchanged (with a warning).
     */
    private String expandMacros(String input) {
        var macroPattern = Pattern.compile("\\{([A-Z_]+)\\}");
        var m = macroPattern.matcher(input);
        var sb = new StringBuffer();
        while (m.find()) {
            var macroName = m.group(1);
            var replacement = MACROS_.get(macroName).pattern();
            if (replacement == null) {
                err.log("Warning: Unknown macro '" + macroName + "' in pattern. Leaving unchanged.");
                m.appendReplacement(sb, Matcher.quoteReplacement("{" + macroName + "}"));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String buildCombinedRegex(List<PatternWithColor> patterns) {
        List<String> namedGroups = new ArrayList<>();
        for (int i = 0; i < patterns.size(); i++) {
            namedGroups.add("(?<g" + i + ">" + patterns.get(i).regex + ")");
        }
        return String.join("|", namedGroups);
    }

    private Map<String, Integer> buildGroupToColorMap(List<PatternWithColor> patterns) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < patterns.size(); i++) {
            map.put("g" + i, patterns.get(i).color);
        }
        return map;
    }

    private void processStream(BufferedReader reader, Pattern combinedPattern, Map<String, Integer> groupToColor) {
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                processLine(line, combinedPattern, groupToColor);
            }
        } catch (Exception e) {
            err.log("Error reading input: " + e.getMessage());
        }
    }

    private void processLine(String line, Pattern combinedPattern, Map<String, Integer> groupToColor) {
        var matcher = combinedPattern.matcher(line);
        List<Match> matches = new ArrayList<>();

        // Find all matches and record their positions and colors
        while (matcher.find()) {
            for (Map.Entry<String, Integer> entry : groupToColor.entrySet()) {
                var groupName = entry.getKey();
                int color = entry.getValue();
                try {
                    int start = matcher.start(groupName);
                    if (start != -1) {
                        int end = matcher.end(groupName);
                        matches.add(new Match(start, end, color));
                    }
                } catch (IllegalArgumentException e) {
                    // group not present in this match
                }
            }
        }

        // If filter mode and no matches, skip line
        if (filterOnly && matches.isEmpty()) {
            return;
        }

        // If no matches, print line as is (unless filter mode prevented it)
        if (matches.isEmpty()) {
            info.log(line);
            return;
        }

        // Sort matches by start position (they are naturally in order but ensure)
        matches.sort(Comparator.comparingInt(m -> m.start));

        // Build highlighted line with ANSI codes
        var highlighted = new StringBuilder();
        int lastIdx = 0;
        for (var m : matches) {
            // Append text before match
            if (m.start > lastIdx) {
                highlighted.append(line, lastIdx, m.start);
            }
            // Append matched part with color
            highlighted.append(ANSI_COLORS.get(m.color));
            highlighted.append(line, m.start, m.end);
            highlighted.append(RESET);
            lastIdx = m.end;
        }
        // Append remaining text after last match
        if (lastIdx < line.length()) {
            highlighted.append(line.substring(lastIdx));
        }

        info.log(highlighted.toString());
    }

    // Helper classes
    private static class PatternWithColor {
        int color;
        String regex;

        PatternWithColor(int color, String regex) {
            this.color = color;
            this.regex = regex;
        }
    }

    private static class Match {
        int start, end, color;

        Match(int start, int end, int color) {
            this.start = start;
            this.end = end;
            this.color = color;
        }
    }

    private record LoggerProps(
            String pattern,
            boolean isErr,
            boolean carriegeReturn,
            List args) {

        LoggerProps(String pattern, List args) {
            this(pattern, false, true, args);
        }
    }

    private static Logger info = (props) -> System.out.printf(definePattern(props), props.args);
    private static Logger err = (props) -> System.err.printf(definePattern(props), props.args);

    private static String definePattern(LoggerProps props) {
        return props.pattern + (props.carriegeReturn ? "%n" : "");
    }

    private interface Logger {
        void log(LoggerProps props);

        default void log(String object) {
            this.log(new LoggerProps("%s", List.of(object)));
        }

        default void log() {
            this.log(new LoggerProps("", true, false, List.of()));
        }
    }

}