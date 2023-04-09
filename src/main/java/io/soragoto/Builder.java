package io.soragoto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;

public class Builder {
    private static final String ONE_CHAR_PATTERN = "^[^\\x00-\\xff]\\t+([a-z]|/){4}(\\t+[0-9]+)?$";
    private static final String PATTERN = "^.+\\t+([a-z]|/){1,4}(\\t+[0-9]+)?$";
    private static final String PATH = "./";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final List<String> ONE_CHAR_DICT = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws ExecutionException, InterruptedException {

        List<Future<?>> futures = new ArrayList<>();
        futures.add(EXECUTOR.submit(() -> {
            try (var s = Files.list(Path.of(PATH))) {
                var fSet = s.filter(p -> !Files.isDirectory(p)).map(p -> p.getFileName().toString()).collect(Collectors.toSet());
                writeByResource(fSet, "default.custom.yaml");
                writeByResource(fSet, "openfly.dict.yaml");
                writeByResource(fSet, "openfly.schema.yaml");
                writeByResource(fSet, "openfly-reverse.schema.yaml");
                writeByResource(fSet, "weasel.custom.yaml");
                writeByResource(fSet, DictTemplate.SYMBOLS.getFileName());
            } catch (IOException e) {
                log(Level.SEVERE, e);
            }
        }));

        var read1 = EXECUTOR.submit(() -> readAndWriteUserDict(DictTemplate.USER_TOP));
        var read2 = EXECUTOR.submit(() -> readAndWriteUserDict(DictTemplate.USER));


        try (var s = Files.list(Path.of(PATH))) {

            Map<DictTemplate, List<String>> dictMap = new HashMap<>();

            var dictPath = s.filter(p -> !Files.isDirectory(p) && p.getFileName().toString().endsWith(".txt")).findFirst().orElseThrow(() -> new NoSuchFileException("end with .txt"));
            var table = Files.readAllLines(dictPath);

            var current = "";
            for (String line : table) {
                if (line.startsWith("#")) {
                    current = line;
                } else {
                    if (current.contains("首选")) {
                        dictMap.computeIfAbsent(DictTemplate.PRIMARY, d -> new ArrayList<>()).add(line);
                        if (Pattern.matches(ONE_CHAR_PATTERN, line)) {
                            ONE_CHAR_DICT.add(line);
                        }
                    } else if (current.contains("二重")) {
                        dictMap.computeIfAbsent(DictTemplate.SECONDARY_SHORT_CODE, d -> new ArrayList<>()).add(line);
                        if (Pattern.matches(ONE_CHAR_PATTERN, line)) {
                            ONE_CHAR_DICT.add(line);
                        }
                    } else if (current.contains("次选")) {
                        dictMap.computeIfAbsent(DictTemplate.SECONDARY, d -> new ArrayList<>()).add(line);

                        if (Pattern.matches(ONE_CHAR_PATTERN, line)) {
                            ONE_CHAR_DICT.add(line);
                        }

                    } else if (current.contains("填空")) {
                        dictMap.computeIfAbsent(DictTemplate.VOID, d -> new ArrayList<>()).add(line);
                        if (Pattern.matches(ONE_CHAR_PATTERN, line)) {
                            ONE_CHAR_DICT.add(line);
                        }

                    } else if (current.contains("表外")) {
                        dictMap.computeIfAbsent(DictTemplate.OFF_TABLE, d -> new ArrayList<>()).add(line);
                        if (Pattern.matches(ONE_CHAR_PATTERN, line)) {
                            ONE_CHAR_DICT.add(line);
                        }

                    } else if (current.contains("随心")) {
                        dictMap.computeIfAbsent(DictTemplate.WHIMSICALITY, d -> new ArrayList<>()).add(line);
                        if (Pattern.matches(ONE_CHAR_PATTERN, line)) {
                            ONE_CHAR_DICT.add(line);
                        }
                    }
                }
            }

            dictMap.forEach((t, d) -> futures.add(EXECUTOR.submit(() -> write(t, d))));
        } catch (IOException e) {
            log(Level.WARNING, e);
        }


        List<String> uncommon = new ArrayList<>();
        var chars = ONE_CHAR_DICT.stream().map(l -> l.split("\\t+")[0]).collect(Collectors.toSet());


        try {
            var uncommonFile = Files.readAllLines(Path.of(PATH, DictTemplate.UNCOMMON.getFileName()));
            for (String line : uncommonFile) {
                var c = line.split("\\t+")[0];
                if (chars.contains(c)) {
                    Logger.getGlobal().warning(c + " is already in dict");
                }
                if (Pattern.matches(PATTERN, line)) {
                    uncommon.add(line);
                    if (Pattern.matches(ONE_CHAR_PATTERN, line)) {
                        ONE_CHAR_DICT.add(line);
                    }
                }
            }
        } catch (IOException e) {
            log(Level.WARNING, e);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Objects.requireNonNull(Builder.class.getResourceAsStream("/recipe/" + DictTemplate.UNCOMMON.getFileName())), StandardCharsets.UTF_8
            ))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {

                    if (Pattern.matches(PATTERN, line)) {
                        uncommon.add(line);
                        if (Pattern.matches(ONE_CHAR_PATTERN, line)) {
                            ONE_CHAR_DICT.add(line);
                        }
                    }
                }
            } catch (IOException ex) {
                log(Level.WARNING, ex);
            }
        }

        futures.add(EXECUTOR.submit(() -> write(DictTemplate.UNCOMMON, uncommon)));

        read1.get();
        read2.get();

        var reverse = buildReverse();
        write(DictTemplate.REVERSE, reverse);


        for (Future<?> future : futures) {
            future.get();
        }

        EXECUTOR.shutdown();
        Logger.getGlobal().info("done");
    }


    public static List<String> buildReverse() {
        List<String> reverseList = new ArrayList<>();

        if (!ONE_CHAR_DICT.isEmpty()) {
            for (String line : ONE_CHAR_DICT) {
                var p = line.split("\\t+");
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
        return reverseList;
    }

    public static void readAndWriteUserDict(DictTemplate template) {
        Logger.getGlobal().info("reading " + template.getFileName());
        List<String> result = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(Path.of(PATH, template.getFileName()))) {
                if (Pattern.matches(PATTERN, line)) {
                    result.add(line);
                    if (Pattern.matches(ONE_CHAR_PATTERN, line)) {
                        ONE_CHAR_DICT.add(line);
                    }
                }
            }
        } catch (IOException e) {
            log(Level.WARNING, e);
        }

        Logger.getGlobal().info("read " + template.getFileName() + "success");
        write(template, result);
    }


    public static void writeByResource(Set<String> fSet, String name) throws IOException {
        if (!fSet.contains(name)) {
            var filePath = Paths.get(PATH, name);
            Files.createFile(filePath);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Objects.requireNonNull(Builder.class.getResourceAsStream("/recipe/" + name)), StandardCharsets.UTF_8
            ))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    Files.writeString(filePath, line, APPEND);
                    Files.writeString(filePath, "\n", APPEND);
                }
            }
        }
    }

    public static void write(DictTemplate template, List<String> dict) {
        Logger.getGlobal().info("writing into " + template.getFileName());
        var fileName = Paths.get(PATH, template.getFileName());
        try {
            Files.deleteIfExists(fileName);
            Files.createFile(fileName);
            Files.writeString(fileName, template.getTemplate(), APPEND);
            for (String l : dict) {
                Files.writeString(fileName, l, APPEND);
                Files.writeString(fileName, "\n", APPEND);
            }
        } catch (IOException e) {
            log(Level.WARNING, e);
        }

        Logger.getGlobal().info("writing " + template.getFileName() + " success");
    }

    private static void log(Level level, Throwable t) {
        Logger.getGlobal().log(level, t.getClass().getName(), t);
    }

}
