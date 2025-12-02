package tech.flowerbirds;

import com.google.javascript.jscomp.*;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Closure Compiler 批量编译 JS 并覆盖源文件
 */
public class JsCompiler {

    /**
     * 代码行数统计结果
     */
    private static class LineCountResult {
        long beforeCount;      // 编译前的行数
        long afterCount;       // 编译后的行数
        boolean success;       // 编译是否成功
        
        LineCountResult(long beforeCount, long afterCount, boolean success) {
            this.beforeCount = beforeCount;
            this.afterCount = afterCount;
            this.success = success;
        }
    }

    // 配置项（可根据需求调整）
    private static String TARGET_DIR = "src/main/resources"; // 要扫描的JS根目录
    private static String FILE_LIST = ""; // 文件列表路径（可选）
    private static String ROOT_DIR = ""; // 根目录路径（可选，配合FILE_LIST使用）
    private static final CompilationLevel COMPILE_LEVEL = CompilationLevel.SIMPLE_OPTIMIZATIONS; // 优化级别
    private static final LanguageMode INPUT_LANG = LanguageMode.ECMASCRIPT_2020; // 输入JS版本
    private static final LanguageMode OUTPUT_LANG = LanguageMode.ECMASCRIPT5; // 输出兼容版本
    private static final String IGNORE_DIR = "src/main/js/test"; // 排除的目录（可选）
    private static String[] KEYWORDS = {}; // 关键字过滤（可选），空数组表示不过滤
    private static long FILE_SIZE_THRESHOLD = 0; // 文件大小阈值（KB），0表示不过滤
    private static String CLOC_MODE = ""; // 代码行数统计模式：空字符串(不统计)、"after"(压缩后)、"before"(压缩前)、"all"(前后都统计)

    public static void main(String[] args) {
        // 检查是否需要显示帮助信息
        for (String arg : args) {
            if (arg.equals("-help") || arg.equals("-h") || arg.equals("--help")) {
                printHelp();
                return;
            }
        }
        
        try {
            // 解析命令行参数
            parseArguments(args);
            
            // 根据参数选择文件处理方式
            List<File> jsFiles;
            if (!FILE_LIST.isEmpty()) {
                // 从文件列表读取JS文件
                jsFiles = readJsFilesFromFileList(FILE_LIST, ROOT_DIR);
            } else {
                // 扫描目录下所有JS文件
                jsFiles = scanJsFiles(TARGET_DIR);
            }
            
            if (jsFiles.isEmpty()) {
                System.out.println("⚠️ 未找到需要编译的JS文件");
                return;
            }
            System.out.println("找到 " + jsFiles.size() + " 个JS文件待处理");

            // 2. 逐个编译并覆盖源文件
            int successCount = 0;
            int failCount = 0;
            long totalBeforeLineCount = 0;  // 压缩前总行数
            long totalAfterLineCount = 0;   // 压缩后总行数
            
            for (File srcFile : jsFiles) {
                LineCountResult result = compileAndOverwrite(srcFile);
                if (result != null) {
                    // 统计成功/失败的编译
                    if (result.success) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                    
                    // 根据CLOC_MODE输出统计信息（不管编译是否成功）
                    if (!CLOC_MODE.isEmpty()) {
                        if (CLOC_MODE.equals("before")) {
                            System.out.println("  📊 压缩前代码行数: " + result.beforeCount);
                            totalBeforeLineCount += result.beforeCount;
                        } else if (CLOC_MODE.equals("after")) {
                            System.out.println("  📊 代码行数: " + result.afterCount);
                            totalAfterLineCount += result.afterCount;
                        } else if (CLOC_MODE.equals("all")) {
                            System.out.println("  📊 压缩前: " + result.beforeCount + " 行，压缩后: " + result.afterCount + " 行");
                            totalBeforeLineCount += result.beforeCount;
                            totalAfterLineCount += result.afterCount;
                        }
                    }
                } else {
                    failCount++;
                }
            }

            // 3. 输出统计结果
            System.out.println("\n✅ 处理完成：成功 " + successCount + " 个，失败 " + failCount + " 个");
            
            // 如果启用了CLOC模式，输出总代码行数统计
            if (!CLOC_MODE.isEmpty() && successCount > 0) {
                if (CLOC_MODE.equals("before")) {
                    System.out.println("📈 压缩前总代码行数: " + totalBeforeLineCount + " 行");
                } else if (CLOC_MODE.equals("after")) {
                    System.out.println("📈 压缩后总代码行数: " + totalAfterLineCount + " 行");
                } else if (CLOC_MODE.equals("all")) {
                    System.out.println("📈 压缩前总代码行数: " + totalBeforeLineCount + " 行");
                    System.out.println("📈 压缩后总代码行数: " + totalAfterLineCount + " 行");
                    long reduction = totalBeforeLineCount - totalAfterLineCount;
                    double ratio = totalBeforeLineCount > 0 ? (reduction * 100.0 / totalBeforeLineCount) : 0;
                    System.out.println("📈 压缩率: " + reduction + " 行 (" + String.format("%.2f%%", ratio) + ")");
                }
            }

        } catch (Exception e) {
            System.err.println("❌ 整体执行失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 打印帮助信息
     */
    private static void printHelp() {
        System.out.println("JsCompiler - Closure Compiler 批量编译 JS 并覆盖源文件");
        System.out.println();
        System.out.println("参数说明:");
        System.out.println("  -help, -h, --help      显示帮助信息");
        System.out.println("  -dir=目录路径          设置要扫描的JS根目录，默认: src/main/resources");
        System.out.println("  -file=文件列表路径     从文本文件读取JS文件列表进行处理");
        System.out.println("  -root=根目录路径       配合-file使用，指定文件列表中的相对路径的根目录");
        System.out.println("  -keywords=关键字1,关键字2,关键字3  设置路径关键字过滤，默认: 无");
        System.out.println("  -size=文件大小阈值     设置文件大小阈值(KB)，超过该大小的文件才会被处理，默认: 0 (无限制)");
        System.out.println("  -cloc[=mode]           启用代码行数统计，mode可以是:");
        System.out.println("                         - after (压缩后) [默认]");
        System.out.println("                         - before (压缩前)");
        System.out.println("                         - all (压缩前后都统计)");
        System.out.println("                         不指定mode时默认为 after");
        System.out.println();
        System.out.println("注意: -dir 和 -file 参数不能同时使用");
        System.out.println();
        System.out.println("使用示例:");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources");
        System.out.println("  java -jar JsCompiler.jar -file=filelist.txt -root=/project/root");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources -keywords=echarts,chart");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources -size=100");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources -keywords=echarts -size=50");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources -cloc");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources -cloc=after");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources -cloc=before");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources -cloc=all");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources -keywords=echarts -cloc=all");
        System.out.println("  java -jar JsCompiler.jar -help");
    }

    /**
     * 解析命令行参数
     * 参数格式:
     * -dir=目录路径  // 设置要扫描的JS根目录
     * -keywords=关键字1,关键字2,关键字3  // 设置路径关键字过滤
     * -size=文件大小阈值  // 设置文件大小阈值(KB)，超过该大小的文件才会被处理
     * -file=文件列表路径  // 从文本文件读取JS文件列表进行处理
     * -root=根目录路径  // 配合-file使用，指定文件列表中的相对路径的根目录
     * -cloc  // 启用代码行数统计模式
     */
    private static void parseArguments(String[] args) {
        boolean hasDir = false;
        boolean hasFile = false;
        
        for (String arg : args) {
            if (arg.startsWith("-dir=")) {
                hasDir = true;
                TARGET_DIR = arg.substring(5); // 提取目录路径
            } else if (arg.startsWith("-keywords=")) {
                String keywordsStr = arg.substring(10); // 提取关键字字符串
                if (!keywordsStr.isEmpty()) {
                    KEYWORDS = keywordsStr.split(","); // 按逗号分割关键字
                }
            } else if (arg.startsWith("-size=")) {
                try {
                    FILE_SIZE_THRESHOLD = Long.parseLong(arg.substring(6)); // 提取文件大小阈值
                } catch (NumberFormatException e) {
                    System.err.println("⚠️ 文件大小阈值格式错误，使用默认值0");
                    FILE_SIZE_THRESHOLD = 0;
                }
            } else if (arg.startsWith("-file=")) {
                hasFile = true;
                FILE_LIST = arg.substring(6); // 提取文件列表路径
            } else if (arg.startsWith("-root=")) {
                ROOT_DIR = arg.substring(6); // 提取根目录路径
            } else if (arg.startsWith("-cloc")) {
                // 处理 -cloc 参数，支持 -cloc、-cloc=after、-cloc=before、-cloc=all
                if (arg.equals("-cloc")) {
                    CLOC_MODE = "after"; // 默认为压缩后统计
                } else if (arg.startsWith("-cloc=")) {
                    String mode = arg.substring(6); // 提取模式值
                    if (mode.equals("after") || mode.equals("before") || mode.equals("all")) {
                        CLOC_MODE = mode;
                    } else {
                        System.err.println("⚠️ 无效的 -cloc 模式: " + mode + "，使用默认值 after");
                        CLOC_MODE = "after";
                    }
                }
            }
        }
        if (hasDir && hasFile) {
            System.err.println("❌ 错误：-dir 和 -file 参数不能同时使用");
            printHelp();
            System.exit(1);
        }
        
        // 输出配置信息
        if (!FILE_LIST.isEmpty()) {
            System.out.println("文件列表: " + FILE_LIST);
            if (!ROOT_DIR.isEmpty()) {
                System.out.println("根目录: " + ROOT_DIR);
            } else {
                System.out.println("根目录: 未指定（使用相对路径）");
            }
        } else {
            System.out.println("扫描目录: " + TARGET_DIR);
        }
        if (KEYWORDS.length > 0) {
            System.out.println("关键字过滤: " + String.join(", ", KEYWORDS));
        } else {
            System.out.println("关键字过滤: 无");
        }
        if (FILE_SIZE_THRESHOLD > 0) {
            System.out.println("文件大小阈值: " + FILE_SIZE_THRESHOLD + " KB");
        } else {
            System.out.println("文件大小阈值: 无");
        }
        if (!CLOC_MODE.isEmpty()) {
            System.out.println("代码行数统计: " + CLOC_MODE + "模式");
        }
    }

    /**
     * 扫描指定目录下所有JS文件（递归），排除指定目录，并根据关键字和文件大小过滤
     */
    private static List<File> scanJsFiles(String rootDir) throws IOException {
        List<File> jsFiles = new ArrayList<>();
        Path rootPath = Paths.get(rootDir);

        // 递归遍历目录
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // 过滤：仅处理.js文件，排除指定目录
                String filePath = file.toString();
                if (filePath.endsWith(".js") && !filePath.contains(IGNORE_DIR)) {
                    File jsFile = file.toFile();
                    // 如果没有设置关键字或文件路径包含关键字，且文件大小超过阈值，则添加到处理列表
                    if ((KEYWORDS.length == 0 || containsKeywordInPath(filePath)) && 
                        (FILE_SIZE_THRESHOLD == 0 || jsFile.length() > FILE_SIZE_THRESHOLD * 1024)) {
                        jsFiles.add(jsFile);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                System.err.println("⚠️ 访问文件失败：" + file + "，原因：" + exc.getMessage());
                return FileVisitResult.CONTINUE; // 跳过错误文件，继续处理
            }
        });
        return jsFiles;
    }

    /**
     * 检查文件路径是否包含指定关键字
     */
    private static boolean containsKeywordInPath(String filePath) {
        for (String keyword : KEYWORDS) {
            if (filePath.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 编译单个JS文件到临时文件，验证后覆盖源文件
     * @return 返回包含编译前后行数的结果，不管编译是否成功都返回行数统计（编译失败时 afterCount 为 0）
     */
    private static LineCountResult compileAndOverwrite(File srcFile) {
        System.out.println("正在处理：" + srcFile);
        
        long beforeLineCount = 0;
        long afterLineCount = 0;
        boolean compilationSuccess = false;
        
        try {
            // 如果需要统计压缩前的行数，先进行统计
            if (!CLOC_MODE.isEmpty() && (CLOC_MODE.equals("before") || CLOC_MODE.equals("all"))) {
                beforeLineCount = countLinesOfCode(srcFile);
            }
            
            // 步骤1：创建临时文件（避免读写冲突）
            File tempFile = File.createTempFile(
                    "js_compile_" + UUID.randomUUID().toString().substring(0, 8),
                    ".tmp.js"
            );
            tempFile.deleteOnExit(); // JVM退出时自动删除临时文件

            // 2. 读取源文件为SourceFile（新版核心API）
            SourceFile inputFile = SourceFile.fromFile(srcFile.getAbsolutePath());

            // 3. 配置编译选项
            CompilerOptions options = new CompilerOptions();
            COMPILE_LEVEL.setOptionsForCompilationLevel(options);
            options.setLanguageIn(INPUT_LANG);
            options.setLanguageOut(OUTPUT_LANG);

            // 步骤4：执行编译（输出到临时文件）
            // 4. 执行编译
            SourceFile extern = SourceFile.fromCode("externs.js", "");
            Compiler compiler = new Compiler(System.out);
            Result result = compiler.compile(
                    extern,
                    inputFile,
                    options
            );

            // 5. 校验编译结果
            if (!result.success) {
                System.err.println("❌ 编译失败：" + srcFile.getPath());
                compiler.getErrors().forEach(err -> System.err.println("   → " + err));
            } else {
                // 6. 将编译结果写入临时文件
                try (Writer writer = new FileWriter(tempFile)) {
                    writer.write(compiler.toSource());
                    writer.flush();
                }

                // 7. 验证临时文件非空（避免空文件覆盖源文件）
                if (tempFile.length() == 0) {
                    System.err.println("❌ 编译结果为空：" + srcFile.getPath());
                } else {
                    // 8. 覆盖源文件（先删原文件，再移动临时文件）
                    if (!srcFile.delete()) {
                        System.err.println("❌ 源文件被占用，无法删除：" + srcFile.getPath());
                    } else if (!tempFile.renameTo(srcFile)) {
                        System.err.println("❌ 临时文件移动失败：" + srcFile.getPath());
                    } else {
                        compilationSuccess = true;
                        System.out.println("✅ 成功覆盖：" + srcFile.getPath());
                    }
                }
            }
            
            // 9. 无论编译是否成功，如果需要统计压缩后的行数，都进行统计
            // 编译成功时统计压缩后文件的行数，失败时统计原文件的行数
            if (!CLOC_MODE.isEmpty() && (CLOC_MODE.equals("after") || CLOC_MODE.equals("all"))) {
                if (compilationSuccess) {
                    // 编译成功，统计压缩后文件的行数
                    afterLineCount = countLinesOfCode(srcFile);
                } else {
                    // 编译失败，统计原文件的行数
                    afterLineCount = countLinesOfCode(srcFile);
                }
            }
            
            // 返回行数统计结果（不管编译是否成功）
            return new LineCountResult(beforeLineCount, afterLineCount, compilationSuccess);

        } catch (Exception e) {
            System.err.println("❌ 处理文件失败：" + srcFile.getPath() + "，原因：" + e.getMessage());
            e.printStackTrace();
            
            // 即使发生异常，如果需要统计行数，也尽量进行统计
            try {
                if (!CLOC_MODE.isEmpty() && (CLOC_MODE.equals("before") || CLOC_MODE.equals("all"))) {
                    if (beforeLineCount == 0) {
                        beforeLineCount = countLinesOfCode(srcFile);
                    }
                }
                if (!CLOC_MODE.isEmpty() && (CLOC_MODE.equals("after") || CLOC_MODE.equals("all"))) {
                    afterLineCount = countLinesOfCode(srcFile);
                }
                return new LineCountResult(beforeLineCount, afterLineCount, false);
            } catch (Exception ex) {
                System.err.println("⚠️ 统计行数时出错：" + ex.getMessage());
                return new LineCountResult(beforeLineCount, afterLineCount, false);
            }
        }
    }

    /**
     * 从文件列表读取JS文件
     */
    private static List<File> readJsFilesFromFileList(String fileListPath, String rootDir) throws IOException {
        List<File> jsFiles = new ArrayList<>();
        Path listPath = Paths.get(fileListPath);
        Path rootPath = rootDir.isEmpty() ? listPath.getParent() : Paths.get(rootDir);
        
        System.out.println("正在从文件列表读取JS文件: " + fileListPath);
        System.out.println("根目录: " + rootPath.toString());
        
        // 读取文件列表
        List<String> filePaths = Files.readAllLines(listPath);
        for (String filePath : filePaths) {
            // 跳过空行和注释行
            if (filePath.trim().isEmpty() || filePath.trim().startsWith("#")) {
                continue;
            }
            String relativeFilePath = filePath.trim().replaceFirst("^/", "").replaceFirst("^\\\\", "");
            // 构造完整路径
            Path fullPath = rootPath.resolve(relativeFilePath.trim());
            File jsFile = fullPath.toFile();
            
            // 检查文件是否存在且是JS文件
            if (jsFile.exists() && jsFile.isFile() && jsFile.getName().endsWith(".js")) {
                // 应用关键字和文件大小过滤条件
                if ((KEYWORDS.length == 0 || containsKeywordInPath(filePath)) && 
                    (FILE_SIZE_THRESHOLD == 0 || jsFile.length() > FILE_SIZE_THRESHOLD * 1024)) {
                    jsFiles.add(jsFile);
                }
            } else {
                System.err.println("⚠️ 文件不存在或不是JS文件: " + fullPath);
            }
        }
        return jsFiles;
    }

    /**
     * 统计文件的代码行数（排除空行和注释）
     */
    private static long countLinesOfCode(File file) throws IOException {
        long codeLines = 0;
        boolean inMultiLineComment = false;
        
        List<String> lines = Files.readAllLines(file.toPath());
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // 跳过空行
            if (trimmedLine.isEmpty()) {
                continue;
            }
            
            // 处理多行注释 /* ... */
            if (inMultiLineComment) {
                if (trimmedLine.contains("*/")) {
                    inMultiLineComment = false;
                }
                continue;
            }
            
            if (trimmedLine.contains("/*")) {
                inMultiLineComment = true;
                if (trimmedLine.contains("*/")) {
                    inMultiLineComment = false;
                }
                continue;
            }
            
            // 跳过单行注释 //
            if (trimmedLine.startsWith("//")) {
                continue;
            }
            
            // 计数代码行
            codeLines++;
        }
        
        return codeLines;
    }
}