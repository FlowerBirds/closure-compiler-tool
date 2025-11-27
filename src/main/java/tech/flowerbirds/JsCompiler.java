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
            for (File srcFile : jsFiles) {
                if (compileAndOverwrite(srcFile)) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            // 3. 输出统计结果
            System.out.println("\n✅ 处理完成：成功 " + successCount + " 个，失败 " + failCount + " 个");

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
        System.out.println("  -help, -h, --help    显示帮助信息");
        System.out.println("  -dir=目录路径        设置要扫描的JS根目录，默认: src/main/resources");
        System.out.println("  -file=文件列表路径   从文本文件读取JS文件列表进行处理");
        System.out.println("  -root=根目录路径     配合-file使用，指定文件列表中的相对路径的根目录");
        System.out.println("  -keywords=关键字1,关键字2,关键字3  设置路径关键字过滤，默认: 无");
        System.out.println("  -size=文件大小阈值   设置文件大小阈值(KB)，超过该大小的文件才会被处理，默认: 0 ( 无限制)");
        System.out.println();
        System.out.println("注意: -dir 和 -file 参数不能同时使用");
        System.out.println();
        System.out.println("使用示例:");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources");
        System.out.println("  java -jar JsCompiler.jar -file=filelist.txt -root=/project/root");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources -keywords=echarts,chart");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources -size=100");
        System.out.println("  java -jar JsCompiler.jar -dir=src/main/resources -keywords=echarts -size=50");
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
            }
        }
        
        // 检查-dir和-file参数是否同时使用
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
     */
    private static boolean compileAndOverwrite(File srcFile) {
        System.out.println("正在处理：" + srcFile);
        // 步骤1：创建临时文件（避免读写冲突）
        File tempFile = null;
        try {
            // 创建唯一临时文件（放在系统临时目录）
            tempFile = File.createTempFile(
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
                return false;
            }

            // 6. 将编译结果写入临时文件
            try (Writer writer = new FileWriter(tempFile)) {
                writer.write(compiler.toSource());
                writer.flush();
            }

            // 7. 验证临时文件非空（避免空文件覆盖源文件）
            if (tempFile.length() == 0) {
                System.err.println("❌ 编译结果为空：" + srcFile.getPath());
                return false;
            }

            // 8. 覆盖源文件（先删原文件，再移动临时文件）
            if (!srcFile.delete()) {
                System.err.println("❌ 源文件被占用，无法删除：" + srcFile.getPath());
                return false;
            }
            if (!tempFile.renameTo(srcFile)) {
                System.err.println("❌ 临时文件移动失败：" + srcFile.getPath());
                return false;
            }

            System.out.println("✅ 成功覆盖：" + srcFile.getPath());
            return true;

        } catch (Exception e) {
            System.err.println("❌ 处理文件失败：" + srcFile.getPath() + "，原因：" + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // 清理临时文件（若未被覆盖）
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
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
            
            // 构造完整路径
            Path fullPath = rootPath.resolve(filePath.trim());
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
}