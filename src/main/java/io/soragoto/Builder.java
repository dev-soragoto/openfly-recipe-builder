package io.soragoto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;

public class Builder {

    private static final String PATH = Optional.ofNullable(System.getenv("RECIPE_PATH")).orElse("./");
    private static final String OUT_PATH = Optional.ofNullable(System.getenv("RECIPE_OUT_PATH")).orElse("./");

    private static final Logger LOGGER = Logger.getLogger(Builder.class.getName());

    public static void main(String[] args) throws IOException {

        var path = args.length > 0 ? args[0] : PATH;
        var outPath = args.length > 1 ? args[1] : OUT_PATH;

        var dict = read(path);

        for (Map.Entry<DictTemplate, List<String>> e : dict.entrySet()) {
            write(e.getKey(), e.getValue(), outPath);
        }

        try (var s = Files.list(Path.of(outPath))) {
            var fSet = s.filter(p -> !Files.isDirectory(p)).map(p -> p.getFileName().toString()).collect(Collectors.toSet());
            write(fSet, "default.custom.yaml", outPath);
            write(fSet, "openfly.dict.yaml", outPath);
            write(fSet, "openfly.schema.yaml", outPath);
            write(fSet, "openfly-reverse.schema.yaml", outPath);
            write(fSet, "weasel.custom.yaml", outPath);
            write(fSet, "openfly.symbols.dict.yaml", outPath);
        }
    }


    public static void write(Set<String> fSet, String name, String outPath) throws IOException {
        if (!fSet.contains(name)) {
            var filePath = Paths.get(outPath, name);
            Files.createFile(filePath);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(Builder.class.getResourceAsStream("/recipe/" + name))))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    Files.writeString(filePath, line, APPEND);
                    Files.writeString(filePath, "\n", APPEND);
                }
            }
        }
    }

    public static void write(DictTemplate template, List<String> dict, String path) throws IOException {
        var filePath = Paths.get(path, template.getFileName());
        Files.deleteIfExists(filePath);
        Files.createFile(filePath);
        Files.writeString(filePath, template.getTemplate(), APPEND);
        for (String l : dict) {
            Files.writeString(filePath, l, APPEND);
            Files.writeString(filePath, "\n", APPEND);
        }
    }


    public static Map<DictTemplate, List<String>> read(final String path) {

        Map<DictTemplate, List<String>> dictMap = Arrays.stream(DictTemplate.values()).collect(Collectors.toMap(Function.identity(), d -> new ArrayList<>()));

        final var oneCharPattern = "^[^\\x00-\\xff](\\t)+([a-z]|/){4}(\\t+[0-9]*)*+$";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(Builder.class.getResourceAsStream("/recipe/openfly.symbols.dict.yaml"))))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                dictMap.computeIfAbsent(DictTemplate.SYMBOLS, d -> new ArrayList<>()).add(line);
            }
        } catch (IOException e) {
            LOGGER.warning(e.getClass().getName() + "\t" + e.getMessage());
        }

        try {
            var userTop = Files.readAllLines(Path.of(path, DictTemplate.USER_TOP.getFileName()));
            for (String line : userTop) {
                if (Pattern.matches(oneCharPattern, line)) {
                    dictMap.computeIfAbsent(DictTemplate.REVERSE, d -> new ArrayList<>()).add(line);
                }
                dictMap.computeIfAbsent(DictTemplate.USER_TOP, d -> new ArrayList<>()).add(line);
            }
        } catch (IOException e) {
            LOGGER.warning(e.getClass().getName() + "\t" + e.getMessage());
        }

        try (var s = Files.list(Path.of(path))) {
            var dictPath = s.filter(p -> !Files.isDirectory(p) && p.getFileName().toString().endsWith(".txt")).findFirst().orElseThrow(() -> new NoSuchFileException("end with .txt"));

            var table = Files.readAllLines(dictPath);

            var current = "";
            for (String line : table) {
                if (line.startsWith("#")) {
                    current = line;
                } else {
                    if (current.contains("首选")) {
                        dictMap.computeIfAbsent(DictTemplate.PRIMARY, d -> new ArrayList<>()).add(line);
                        if (Pattern.matches(oneCharPattern, line)) {
                            dictMap.computeIfAbsent(DictTemplate.REVERSE, d -> new ArrayList<>()).add(line);
                        }
                    } else if (current.contains("二重")) {
                        dictMap.computeIfAbsent(DictTemplate.SECONDARY_SHORT_CODE, d -> new ArrayList<>()).add(line);
                        if (Pattern.matches(oneCharPattern, line)) {
                            dictMap.computeIfAbsent(DictTemplate.REVERSE, d -> new ArrayList<>()).add(line);
                        }
                    } else if (current.contains("次选")) {
                        dictMap.computeIfAbsent(DictTemplate.SECONDARY, d -> new ArrayList<>()).add(line);
                        if (Pattern.matches(oneCharPattern, line)) {
                            dictMap.computeIfAbsent(DictTemplate.REVERSE, d -> new ArrayList<>()).add(line);
                        }
                    } else if (current.contains("填空")) {
                        dictMap.computeIfAbsent(DictTemplate.VOID, d -> new ArrayList<>()).add(line);
                        if (Pattern.matches(oneCharPattern, line)) {
                            dictMap.computeIfAbsent(DictTemplate.REVERSE, d -> new ArrayList<>()).add(line);
                        }
                    } else if (current.contains("表外")) {
                        dictMap.computeIfAbsent(DictTemplate.OFF_TABLE, d -> new ArrayList<>()).add(line);
                        if (Pattern.matches(oneCharPattern, line)) {
                            dictMap.computeIfAbsent(DictTemplate.REVERSE, d -> new ArrayList<>()).add(line);
                        }
                    } else if (current.contains("随心")) {
                        dictMap.computeIfAbsent(DictTemplate.WHIMSICALITY, d -> new ArrayList<>()).add(line);
                        if (Pattern.matches(oneCharPattern, line)) {
                            dictMap.computeIfAbsent(DictTemplate.REVERSE, d -> new ArrayList<>()).add(line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warning(e.getClass().getName() + "\t" + e.getMessage());
        }

        try {
            var user = Files.readAllLines(Path.of(path, DictTemplate.USER.getFileName()));
            for (String line : user) {
                if (Pattern.matches(oneCharPattern, line)) {
                    dictMap.computeIfAbsent(DictTemplate.REVERSE, d -> new ArrayList<>()).add(line);
                }
                dictMap.computeIfAbsent(DictTemplate.USER, d -> new ArrayList<>()).add(line);
            }
        } catch (IOException e) {
            LOGGER.warning(e.getClass().getName() + "\t" + e.getMessage());
        }

        try {
            var uncommon = Files.readAllLines(Path.of(path, DictTemplate.UNCOMMON.getFileName()));
            for (String line : uncommon) {
                if (Pattern.matches(oneCharPattern, line)) {
                    dictMap.computeIfAbsent(DictTemplate.REVERSE, d -> new ArrayList<>()).add(line);
                }
                dictMap.computeIfAbsent(DictTemplate.UNCOMMON, d -> new ArrayList<>()).add(line);
            }
        } catch (IOException e) {
            LOGGER.warning(e.getClass().getName() + "\t" + e.getMessage());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(Builder.class.getResourceAsStream("/recipe/openfly.uncommon.dict.yaml"))))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (Pattern.matches(oneCharPattern, line)) {
                        dictMap.computeIfAbsent(DictTemplate.REVERSE, d -> new ArrayList<>()).add(line);
                    }
                    dictMap.computeIfAbsent(DictTemplate.UNCOMMON, d -> new ArrayList<>()).add(line);
                }
            } catch (IOException ex) {
                LOGGER.warning(ex.getClass().getName() + "\t" + ex.getMessage());
            }
        }

        var reverseTable = dictMap.get(DictTemplate.REVERSE);

        List<String> reverseList = new ArrayList<>();

        if (reverseTable != null) {
            for (String line : reverseTable) {
                var p = line.split("\t+");
                var code = p[1].toCharArray();

                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{'`', code[1], code[2], code[3]})));
                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{code[0], '`', code[2], code[3]})));
                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{code[0], code[1], '`', code[3]})));
                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{code[0], code[1], code[2], '`'})));

                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{'`', '`', code[2], code[3]})));
                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{'`', code[1], '`', code[3]})));
                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{'`', code[1], code[2], '`'})));
                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{code[0], '`', '`', code[3]})));
                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{code[0], '`', code[2], '`'})));
                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{code[0], code[1], '`', '`'})));

                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{'`', '`', '`', code[3]})));
                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{'`', '`', code[2], '`'})));
                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{'`', code[1], '`', '`'})));
                reverseList.add(String.format("%s\t%s", p[0], new String(new char[]{code[0], '`', '`', '`'})));
            }
        }

        dictMap.put(DictTemplate.REVERSE, reverseList);

        return dictMap;
    }

}
