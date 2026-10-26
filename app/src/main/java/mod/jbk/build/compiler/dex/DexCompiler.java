package mod.jbk.build.compiler.dex;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;

import a.a.a.ProjectBuilder;
import mod.hey.studios.project.ProjectSettings;
import mod.jbk.util.LogUtil;
import sketchware.plus.utility.FileUtil;

public class DexCompiler {
    private static final String TAG = "DexCompiler";

    public static void compileDexFiles(ProjectBuilder builder) throws CompilationFailedException {
        int minApiLevel;

        try {
            minApiLevel = Integer.parseInt(builder.settings.getValue(
                    ProjectSettings.SETTING_MINIMUM_SDK_VERSION, "21"));
        } catch (NumberFormatException e) {
            throw new CompilationFailedException("Invalid minSdkVersion specified in Project Settings" + e.getMessage());
        }

        Collection<Path> programFiles = new LinkedList<>();
        File proguardClassesFile = new File(builder.yq.proguardClassesPath);

        if (builder.proguard.isShrinkingEnabled()) {
            if (!proguardClassesFile.exists() || proguardClassesFile.length() == 0) {
                throw new CompilationFailedException("ProGuard shrinking is enabled, but ProGuard output file ("
                        + proguardClassesFile.getAbsolutePath() + ") is missing or empty ("
                        + (proguardClassesFile.exists() ? proguardClassesFile.length() : 0) + " bytes)");
            }
            programFiles.add(proguardClassesFile.toPath());
        } else {
            for (File file : FileUtil.listFilesRecursively(new File(builder.yq.compiledClassesPath), ".class")) {
                programFiles.add(file.toPath());
            }
        }

        LogUtil.d(TAG, "D8 compilation info: shrinkingEnabled=" + builder.proguard.isShrinkingEnabled()
                + ", r8Enabled=" + builder.proguard.isR8Enabled()
                + ", programFilesCount=" + programFiles.size()
                + ", proguardClassesPath=" + proguardClassesFile.getAbsolutePath()
                + " (exists=" + proguardClassesFile.exists() + ", size=" + (proguardClassesFile.exists() ? proguardClassesFile.length() : 0) + " bytes)");

        if (programFiles.isEmpty()) {
            throw new CompilationFailedException("No program files for D8");
        }

        Collection<Path> libraryFiles = new LinkedList<>();
        Path compiledClassesPath = Paths.get(builder.yq.compiledClassesPath).toAbsolutePath();
        for (String jarPath : builder.getClasspath().split(":")) {
            if (!jarPath.trim().isEmpty()) {
                Path path = Paths.get(jarPath).toAbsolutePath();
                if (!path.equals(compiledClassesPath) && !jarPath.equals(builder.yq.compiledClassesPath)) {
                    libraryFiles.add(path);
                }
            }
        }

        D8.run(D8Command.builder()
                .setMode(CompilationMode.RELEASE)
                .setIntermediate(false)
                .setMinApiLevel(minApiLevel)
                .addLibraryFiles(libraryFiles)
                .setOutput(new File(builder.yq.binDirectoryPath, "dex").toPath(), OutputMode.DexIndexed)
                .addProgramFiles(programFiles)
                .build());
    }
}
