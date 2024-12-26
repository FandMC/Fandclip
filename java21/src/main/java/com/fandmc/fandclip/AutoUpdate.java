package com.fandmc.fandclip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.fandmc.fandclip.Fandclip.LOGGER;

public class AutoUpdate {
    public static String autoUpdateCorePath;
    public static String autoUpdateDir = "auto_update";
    public static boolean useAutoUpdateJar = false;
    
    public static void init() {
        File workingDirFile = new File(autoUpdateDir);

        if (!workingDirFile.isDirectory() || !workingDirFile.exists()) {
            return;
        }

        File corePathFile = new File(autoUpdateDir + "/core.path");
        if (!corePathFile.isFile() || !corePathFile.exists()) {
            return;
        }

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(corePathFile))) {
            String firstLine = bufferedReader.readLine();
            if (firstLine == null) {
                return;
            }

            autoUpdateCorePath = firstLine;
            File jarFile = new File(autoUpdateCorePath);
            if (!jarFile.isFile() || !jarFile.exists()) {
                LOGGER.warn("The specified server core: {} does not exist. Using the original jar!", autoUpdateCorePath);
                return;
            }

            useAutoUpdateJar = true;

            if (!detectionFandclipVersion(autoUpdateCorePath)) {
                LOGGER.warn("Fandclip version detection in server core: {} failed. Using the original jar!", autoUpdateCorePath);
                useAutoUpdateJar = false;
                return;
            }

            LOGGER.info("Using server core: {} provide by Fandclip-Auto-Update", autoUpdateCorePath);
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage(), e);
        }
    }

    private static boolean detectionFandclipVersion(String jarPath) {
        byte[] localBytes;

        try (InputStream localStream = AutoUpdate.class.getResourceAsStream("/META-INF/fandclip-version")) {
            if (localStream != null) {
                localBytes = localStream.readAllBytes();
            } else {
                return false;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (InputStream externalStream = getResourceAsStream(jarPath, "/META-INF/fandclip-version")) {
            if (externalStream != null) {
                return Arrays.equals(localBytes, externalStream.readAllBytes());
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static InputStream getResourceAsStream(String jarPath, String name) {
        if (!useAutoUpdateJar) {
            return AutoUpdate.class.getResourceAsStream(name);
        }

        name = name.replaceFirst("/", "");
        InputStream result = null;

        try {
            ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(jarPath));
            ZipEntry zipEntry;

            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.getName().equals(name)) {
                    result = new ByteArrayInputStream(zipInputStream.readAllBytes());
                    break;
                }
                zipInputStream.closeEntry();
            }
            zipInputStream.close();

            if (result == null) {
                throw new IOException(name + " not found in our jar or in the " + jarPath);
            }
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage(), e);
        }
        return result;
    }
}
