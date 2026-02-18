package owpk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.annotation.Value;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "jlogfmt",
        description = "Подсветка и фильтрация логов (аналог jlogfmt для цветного вывода)",
        mixinStandardHelpOptions = true,
        version = "1.0.0")
public class JlogfmtCommand implements Runnable {

    public static void main(String[] args) throws Exception {
        PicocliRunner.run(JlogfmtCommand.class, args);
    }

    @Option(names = {"-f", "--file"}, description = "Файл с логами (если не указан, читает из stdin)")
    private Path file;

    @Option(names = {"-l", "--level"}, description = "Уровни логирования для показа (INFO,WARN,ERROR,DEBUG,TRACE)")
    private Set<String> levels = new HashSet<>();

    @Option(names = {"-i", "--include"}, description = "Регулярное выражение: строки должны совпадать")
    private Pattern includePattern;

    @Option(names = {"-e", "--exclude"}, description = "Регулярное выражение: строки не должны совпадать")
    private Pattern excludePattern;

    @Option(names = {"-p", "--pattern"}, description = "Регулярное выражение для разбора строки лога. Должно содержать именованную группу 'level'. По умолчанию: ^\\\\d{4}-\\\\d{2}-\\\\d{2} \\\\d{2}:\\\\d{2}:\\\\d{2}\\.\\\\d{3}\\\\s+(?<level>[A-Z]+)\\s+")
    private String logPattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+(?<level>[A-Z]+)\\s+";

    @Value("${colors.info:32}")  // зелёный
    private String colorInfo;

    @Value("${colors.warn:33}")  // жёлтый
    private String colorWarn;

    @Value("${colors.error:31}") // красный
    private String colorError;

    @Value("${colors.debug:36}") // циан
    private String colorDebug;

    @Value("${colors.trace:35}") // пурпурный
    private String colorTrace;

    @Value("${colors.reset:0}")
    private String colorReset;

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_PREFIX = "\u001B[";

    private final Map<String, String> levelColors = new HashMap<>();

    @Override
    public void run() {
        // Инициализация цветов
        levelColors.put("INFO", ANSI_PREFIX + colorInfo + "m");
        levelColors.put("WARN", ANSI_PREFIX + colorWarn + "m");
        levelColors.put("ERROR", ANSI_PREFIX + colorError + "m");
        levelColors.put("DEBUG", ANSI_PREFIX + colorDebug + "m");
        levelColors.put("TRACE", ANSI_PREFIX + colorTrace + "m");

        try (BufferedReader reader = createReader()) {
            Pattern parser = Pattern.compile(logPattern);
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line, parser);
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения: " + e.getMessage());
        }
    }

    private BufferedReader createReader() throws IOException {
        if (file != null) {
            return Files.newBufferedReader(file);
        } else {
            return new BufferedReader(new InputStreamReader(System.in));
        }
    }

    private void processLine(String line, Pattern parser) {
        // Фильтрация по include/exclude
        if (includePattern != null && !includePattern.matcher(line).find()) {
            return;
        }
        if (excludePattern != null && excludePattern.matcher(line).find()) {
            return;
        }

        // Пытаемся извлечь уровень
        String level = extractLevel(line, parser);
        if (level != null && !levels.isEmpty() && !levels.contains(level)) {
            return; // фильтр по уровню не пропускает
        }

        // Вывод с подсветкой уровня (если найден)
        if (level != null && levelColors.containsKey(level)) {
            String color = levelColors.get(level);
            // Подсвечиваем только уровень (можно и всю строку, но так нагляднее)
            String highlighted = line.replaceFirst(
                    Pattern.quote(level),
                    color + level + ANSI_RESET
            );
            System.out.println(highlighted);
        } else {
            System.out.println(line);
        }
    }

    private String extractLevel(String line, Pattern parser) {
        Matcher matcher = parser.matcher(line);
        if (matcher.find()) {
            try {
                return matcher.group("level");
            } catch (IllegalArgumentException e) {
                // нет группы level
                return null;
            }
        }
        return null;
    }
}