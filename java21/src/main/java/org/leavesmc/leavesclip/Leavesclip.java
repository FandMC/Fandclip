package org.leavesmc.leavesclip;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import launchwrapper.ITweaker;
import launchwrapper.LaunchClassLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;

public final class Leavesclip {
    private static final Logger logger = LoggerFactory.getLogger("Leavesclip");
    private static final String DEFAULT_TWEAK = "org.spongepowered.asm.launch.MixinTweaker";
    public static LaunchClassLoader classLoader;
    public static Map<String, Object> blackboard = new HashMap<>();

    public static void main(final String[] args) {
        new Leavesclip(args);
    }

    private Leavesclip(final String[] args) {
        if (Path.of("").toAbsolutePath().toString().contains("!")) {
            logger.error("Leavesclip may not run in a directory containing '!'. Please rename the affected folder.");
            System.exit(1);
        }

        if (!Boolean.getBoolean("leavesclip.disable.auto-update")) {
            AutoUpdate.init();
        }

        final URL[] classpathUrls = setupClasspath();

        final ClassLoader parentClassLoader = Leavesclip.class.getClassLoader().getParent();
        classLoader = new LaunchClassLoader(classpathUrls, parentClassLoader);

        final OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();

        final OptionSpec<String> tweakClassOption = parser
                .accepts("tweakClass", "Tweak class(es) to load")
                .withRequiredArg()
                .defaultsTo(DEFAULT_TWEAK);
        final OptionSpec<String> nonOption = parser.nonOptions();

        final OptionSet options = parser.parse(args);
        final List<String> tweakClassNames = new ArrayList<>(options.valuesOf(tweakClassOption));

        final List<String> argumentList = new ArrayList<>();
        // This list of names will be interacted with through tweakers. They can append to this list
        // any 'discovered' tweakers from their preferred mod loading mechanism
        // By making this object discoverable and accessible it's possible to perform
        // things like cascading of tweakers
        blackboard.put("TweakClasses", tweakClassNames);

        // This argument list will be constructed from all tweakers. It is visible here so
        // all tweakers can figure out if a particular argument is present, and add it if not
        blackboard.put("ArgumentList", argumentList);

        // This is to prevent duplicates - in case a tweaker decides to add itself or something
        final Set<String> visitedTweakerNames = new HashSet<>();
        // The 'definitive' list of tweakers
        final List<ITweaker> allTweakers = new ArrayList<>();
        try {
            final List<ITweaker> pendingTweakers = new ArrayList<>(tweakClassNames.size() + 1);
            // The list of tweak instances - may be useful for interoperability
            blackboard.put("Tweaks", pendingTweakers);
            // The primary tweaker (the first one specified on the command line) will actually
            // be responsible for providing the 'main' name and generally gets called first
            ITweaker primaryTweaker = null;
            // This loop will terminate, unless there is some sort of pathological tweaker
            // that reinserts itself with a new identity every pass
            // It is here to allow tweakers to "push" new tweak classes onto the 'stack' of
            // tweakers to evaluate allowing for cascaded discovery and injection of tweakers
            while (!tweakClassNames.isEmpty()) {
                for (final Iterator<String> it = tweakClassNames.iterator(); it.hasNext(); ) {
                    final String tweakName = it.next();
                    // Safety check - don't reprocess something we've already visited
                    if (visitedTweakerNames.contains(tweakName)) {
                        logger.warn("Tweak class name {} has already been visited -- skipping", tweakName);
                        // remove the tweaker from the stack otherwise it will create an infinite loop
                        it.remove();
                        continue;
                    } else {
                        visitedTweakerNames.add(tweakName);
                    }
                    logger.info("Loading tweak class name {}", tweakName);

                    // Ensure we allow the tweak class to load with the parent classloader
                    classLoader.getClassLoaderExclusions().add(tweakName.substring(0, tweakName.lastIndexOf('.')));
                    final ITweaker tweaker = (ITweaker) Class.forName(tweakName, true, classLoader)
                            .getConstructor().newInstance();
                    pendingTweakers.add(tweaker);

                    // Remove the tweaker from the list of tweaker names we've processed this pass
                    it.remove();
                    // If we haven't visited a tweaker yet, the first will become the 'primary' tweaker
                    if (primaryTweaker == null) {
                        logger.info("Using primary tweak class name {}", tweakName);
                        primaryTweaker = tweaker;
                    }
                }

                // Configure environment to avoid warn
                configureMixin();

                // Now, iterate all the tweakers we just instantiated
                while (!pendingTweakers.isEmpty()) {
                    final ITweaker tweaker = pendingTweakers.removeFirst();
                    logger.info("Calling tweak class {}", tweaker.getClass().getName());
                    tweaker.acceptOptions(options.valuesOf(nonOption));
                    tweaker.injectIntoClassLoader(classLoader);
                    allTweakers.add(tweaker);
                }
                // continue around the loop until there's no tweak classes
            }

            // Once we're done, we then ask all the tweakers for their arguments and add them all to the
            // master argument list
            for (final ITweaker tweaker : allTweakers) {
                argumentList.addAll(Arrays.asList(tweaker.getLaunchArguments()));
            }

            final String mainClassName = findMainClass();
            logger.info("Starting {}", mainClassName);

            final Thread runThread = getServerMainThread(args, argumentList, mainClassName);
            runThread.start();
        } catch (Exception e) {
            logger.error("Unable to launch", e);
            System.exit(1);
        }
    }

    private static @NotNull Thread getServerMainThread(String[] args, List<String> argumentList, String mainClassName) {
        final Thread runThread = new Thread(() -> {
            try {
                argumentList.addAll(Arrays.asList(args));
                final Class<?> mainClass = Class.forName(mainClassName, true, classLoader);
                final MethodHandle mainHandle = MethodHandles.lookup()
                        .findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class))
                        .asFixedArity();
                mainHandle.invoke((Object) argumentList.toArray(new String[0]));
            } catch (final Throwable t) {
                throw Util.sneakyThrow(t);
            }
        }, "ServerMain");
        runThread.setContextClassLoader(classLoader);
        return runThread;
    }

    private void configureMixin() {
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("mixins.akarin.core.json");
    }

    private static URL @NotNull [] setupClasspath() {
        final var repoDir = Path.of(System.getProperty("bundlerRepoDir", ""));

        final PatchEntry[] patches = findPatches();
        final DownloadContext downloadContext = findDownloadContext();
        if (patches.length > 0 && downloadContext == null) {
            throw new IllegalArgumentException("patches.list file found without a corresponding original-url file");
        }

        final Path baseFile;
        if (downloadContext != null) {
            try {
                downloadContext.download(repoDir);
            } catch (final IOException e) {
                throw Util.fail("Failed to download original jar", e);
            }
            baseFile = downloadContext.getOutputFile(repoDir);
        } else {
            baseFile = null;
        }

        final Map<String, Map<String, URL>> classpathUrls = extractAndApplyPatches(baseFile, patches, repoDir);

        // Exit if user has set `paperclip.patchonly` or `leavesclip.patchonly` system property to `true`
        if (Boolean.getBoolean("paperclip.patchonly")
                || Boolean.getBoolean("leavesclip.patchonly")) {
            System.exit(0);
        }

        // Keep versions and libraries separate as the versions must come first
        // This is due to change we make to some library classes inside the versions jar
        final Collection<URL> versionUrls = classpathUrls.get("versions").values();
        final Collection<URL> libraryUrls = classpathUrls.get("libraries").values();

        final URL[] emptyArray = new URL[0];
        final URL[] urls = new URL[versionUrls.size() + libraryUrls.size()];
        System.arraycopy(versionUrls.toArray(emptyArray), 0, urls, 0, versionUrls.size());
        System.arraycopy(libraryUrls.toArray(emptyArray), 0, urls, versionUrls.size(), libraryUrls.size());
        return urls;
    }

    private static PatchEntry[] findPatches() {
        final InputStream patchListStream = AutoUpdate.getResourceAsStream(AutoUpdate.autoUpdateCorePath, "/META-INF/patches.list");
        if (patchListStream == null) {
            return new PatchEntry[0];
        }

        try (patchListStream) {
            return PatchEntry.parse(new BufferedReader(new InputStreamReader(patchListStream)));
        } catch (final IOException e) {
            throw Util.fail("Failed to read patches.list file", e);
        }
    }

    private static DownloadContext findDownloadContext() {
        final String line;
        try {
            line = Util.readResourceText("/META-INF/download-context");
        } catch (final IOException e) {
            throw Util.fail("Failed to read download-context file", e);
        }

        return DownloadContext.parseLine(line);
    }

    private static FileEntry[] findVersionEntries() {
        return findFileEntries("versions.list");
    }

    private static FileEntry[] findLibraryEntries() {
        return findFileEntries("libraries.list");
    }

    private static FileEntry[] findFileEntries(final String fileName) {
        final InputStream libListStream = AutoUpdate.getResourceAsStream(AutoUpdate.autoUpdateCorePath, "/META-INF/" + fileName);
        if (libListStream == null) {
            return null;
        }

        try (libListStream) {
            return FileEntry.parse(new BufferedReader(new InputStreamReader(libListStream)));
        } catch (final IOException e) {
            throw Util.fail("Failed to read " + fileName + " file", e);
        }
    }

    private static String findMainClass() {
        final String mainClassName = System.getProperty("bundlerMainClass");
        if (mainClassName != null) {
            return mainClassName;
        }

        try {
            return Util.readResourceText("/META-INF/main-class");
        } catch (final IOException e) {
            throw Util.fail("Failed to read main-class file", e);
        }
    }

    private static Map<String, Map<String, URL>> extractAndApplyPatches(final Path originalJar, final PatchEntry[] patches, final Path repoDir) {
        if (originalJar == null && patches.length > 0) {
            throw new IllegalArgumentException("Patch data found without patch target");
        }

        // First extract any non-patch files
        final Map<String, Map<String, URL>> urls = extractFiles(patches, originalJar, repoDir);

        // Next apply any patches that we have
        applyPatches(urls, patches, originalJar, repoDir);

        return urls;
    }

    private static Map<String, Map<String, URL>> extractFiles(final PatchEntry[] patches, final Path originalJar, final Path repoDir) {
        final var urls = new HashMap<String, Map<String, URL>>();

        try {
            final FileSystem originalJarFs;
            if (originalJar == null) {
                originalJarFs = null;
            } else {
                originalJarFs = FileSystems.newFileSystem(originalJar);
            }

            try {
                final Path originalRootDir;
                if (originalJarFs == null) {
                    originalRootDir = null;
                } else {
                    originalRootDir = originalJarFs.getPath("/");
                }

                final var versionsMap = new HashMap<String, URL>();
                urls.putIfAbsent("versions", versionsMap);
                final FileEntry[] versionEntries = findVersionEntries();
                extractEntries(versionsMap, patches, originalRootDir, repoDir, versionEntries, "versions");

                final FileEntry[] libraryEntries = findLibraryEntries();
                final var librariesMap = new HashMap<String, URL>();
                urls.putIfAbsent("libraries", librariesMap);
                extractEntries(librariesMap, patches, originalRootDir, repoDir, libraryEntries, "libraries");
            } finally {
                if (originalJarFs != null) {
                    originalJarFs.close();
                }
            }
        } catch (final IOException e) {
            throw Util.fail("Failed to extract jar files", e);
        }

        return urls;
    }

    private static void extractEntries(
            final Map<String, URL> urls,
            final PatchEntry[] patches,
            final Path originalRootDir,
            final Path repoDir,
            final FileEntry[] entries,
            final String targetName
    ) throws IOException {
        if (entries == null) {
            return;
        }

        final String targetPath = "/META-INF/" + targetName;
        final Path targetDir = repoDir.resolve(targetName);

        for (final FileEntry entry : entries) {
            entry.extractFile(urls, patches, targetName, originalRootDir, targetPath, targetDir);
        }
    }

    private static void applyPatches(
            final Map<String, Map<String, URL>> urls,
            final PatchEntry[] patches,
            final Path originalJar,
            final Path repoDir
    ) {
        if (patches.length == 0) {
            return;
        }
        if (originalJar == null) {
            throw new IllegalStateException("Patches provided without patch target");
        }

        try (final FileSystem originalFs = FileSystems.newFileSystem(originalJar)) {
            final Path originalRootDir = originalFs.getPath("/");

            for (final PatchEntry patch : patches) {
                patch.applyPatch(urls, originalRootDir, repoDir);
            }
        } catch (final IOException e) {
            throw Util.fail("Failed to apply patches", e);
        }
    }
}
