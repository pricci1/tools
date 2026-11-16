import com.sun.source.util.JavacTask;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/** Syntax parsing only. Does not claim Android type-checking or SDK compatibility. */
class ParseJava {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A JDK, not just a JRE, is required");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            var units = files.getJavaFileObjectsFromPaths(Arrays.stream(args).map(Path::of).toList());
            var task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "--release", "17"), null, units);
            task.parse();
            boolean failed = false;
            for (var d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) { System.err.println(d); failed = true; }
            }
            if (failed) System.exit(1);
        }
        System.out.println("PASS: Java syntax parsed for " + args.length + " files (not SDK compilation)");
    }
}
