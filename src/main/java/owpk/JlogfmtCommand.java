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

import io.micronaut.configuration.picocli.PicocliRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "jlogfmt",
    mixinStandardHelpOptions = true,
    version = "1.0",
    description = "Highlight and filter logs using custom color:regex patterns.",
    header = "Log Highlighter",
    footer = "Color codes: 30-37 (standard), 90-97 (bright). See ANSI codes for more."
)
public class JlogfmtCommand implements Runnable {

    public static void main(String[] args) {
        try {
            PicocliRunner.run(JlogfmtCommand.class, args);
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    @Option(
        names = {"-p", "--pattern"},
        description = "Pattern in format 'color:regex'. Can be repeated. Example: 31:(\\d{4}-\\d{2}-\\d{2})"
    )
    private List<String> userPatterns;

    @Option(
        names = {"--filter"},
        description = "Only print lines that match at least one pattern"
    )
    private boolean filterOnly;

    @Parameters(description = "Files to process (if none, read from stdin)")
    private List<File> files;

    // Default patterns for Spring Boot logs (ISO timestamp and log level)
    private static final List<String> DEFAULT_PATTERNS = Arrays.asList(
        "32:(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+\\d{2}:\\d{2})", // timestamp in green
        "33:(INFO|WARN|ERROR|DEBUG|TRACE)",                                           // level in yellow
        "31:(ERROR)"                                                                  // ERROR in red (overrides yellow if both match)
    );

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

    @Override
    public void run() {
        // Use default patterns if none provided
        List<String> patternsToUse = (userPatterns == null || userPatterns.isEmpty())
                ? DEFAULT_PATTERNS
                : userPatterns;

        // Parse patterns into list of PatternWithColor
        List<PatternWithColor> parsedPatterns = parsePatterns(patternsToUse);
        if (parsedPatterns.isEmpty()) {
            System.err.println("No valid patterns provided.");
            return;
        }

        // Build combined regex with named groups
        String combinedRegex = buildCombinedRegex(parsedPatterns);
        Pattern combinedPattern = Pattern.compile(combinedRegex);
        Map<String, Integer> groupToColor = buildGroupToColorMap(parsedPatterns);

        // Process input
        if (files == null || files.isEmpty()) {
            processStream(new BufferedReader(new InputStreamReader(System.in)), combinedPattern, groupToColor);
        } else {
            for (File file : files) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    processStream(reader, combinedPattern, groupToColor);
                } catch (Exception e) {
                    System.err.println("Error reading file " + file + ": " + e.getMessage());
                }
            }
        }
    }

    private List<PatternWithColor> parsePatterns(List<String> patterns) {
        List<PatternWithColor> result = new ArrayList<>();
        Pattern patternParser = Pattern.compile("^(\\d+):(.*)$");
        for (String p : patterns) {
            Matcher m = patternParser.matcher(p.trim());
            if (m.matches()) {
                int color = Integer.parseInt(m.group(1));
                String regex = m.group(2);
                if (ANSI_COLORS.containsKey(color)) {
                    result.add(new PatternWithColor(color, regex));
                } else {
                    System.err.println("Warning: Unsupported color code " + color + " in pattern: " + p + ". Ignored.");
                }
            } else {
                System.err.println("Warning: Invalid pattern format: " + p + ". Expected 'color:regex'. Ignored.");
            }
        }
        return result;
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
            System.err.println("Error reading input: " + e.getMessage());
        }
    }

    private void processLine(String line, Pattern combinedPattern, Map<String, Integer> groupToColor) {
        Matcher matcher = combinedPattern.matcher(line);
        List<Match> matches = new ArrayList<>();

        // Find all matches and record their positions and colors
        while (matcher.find()) {
            for (Map.Entry<String, Integer> entry : groupToColor.entrySet()) {
                String groupName = entry.getKey();
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
            System.out.println(line);
            return;
        }

        // Sort matches by start position (they are naturally in order but ensure)
        matches.sort(Comparator.comparingInt(m -> m.start));

        // Build highlighted line with ANSI codes
        StringBuilder highlighted = new StringBuilder();
        int lastIdx = 0;
        for (Match m : matches) {
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

        System.out.println(highlighted);
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
}