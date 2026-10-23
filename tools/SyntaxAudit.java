import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** JDK 17 自带语法解析器：只检查语法，不能替代 Maven 编译及数据库测试。 */
public class SyntaxAudit {
    public static void main(String[] args) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK compiler not found");
        List<java.io.File> files = new ArrayList<>();
        for (String root : args) try (var stream = Files.walk(Path.of(root))) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(p -> files.add(p.toFile()));
        }
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(collector, null, null)) {
            Iterable<? extends JavaFileObject> units=manager.getJavaFileObjectsFromFiles(files);
            JavacTask task=(JavacTask)compiler.getTask(null,manager,collector,
                List.of("-proc:none","--release","17"),null,units);
            task.parse();
        }
        long errors=collector.getDiagnostics().stream().filter(d -> d.getKind()==Diagnostic.Kind.ERROR).count();
        collector.getDiagnostics().forEach(System.err::println);
        System.out.println("Parsed Java sources: "+files.size()+"; syntax errors: "+errors);
        if (errors>0)System.exit(1);
    }
}
