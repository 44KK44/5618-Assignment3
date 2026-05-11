/*
 * Shattered Pixel Dungeon
 *
 * Save integrity validation utility added for software quality improvement.
 */

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.watabou.utils.FileUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Provides checksum-based integrity validation for save files.
 *
 * This class uses LibGDX local FileHandle storage so that it resolves save paths
 * in the same storage system used by the game.
 */
public class SaveIntegrityValidator {

    private static final String CHECKSUM_SUFFIX = ".sha256";

    private SaveIntegrityValidator() {
        // Utility class; do not instantiate.
    }

    /**
     * Generates and writes a SHA-256 checksum file for the given save file.
     *
     * Example:
     * save file:     game1/game.dat
     * checksum file: game1/game.dat.sha256
     *
     * @param saveFilePath project/local save file path
     * @throws IOException if the save file cannot be read or checksum cannot be written
     */
    public static void writeChecksum(String saveFilePath) throws IOException {
        System.out.println("[SAVE-INTEGRITY] writeChecksum called with path: " + saveFilePath);

        FileHandle saveFile = FileUtils.getFileHandle(saveFilePath);

        if (saveFile == null || !saveFile.exists() || saveFile.isDirectory() || saveFile.length() == 0) {
            throw new IOException("Cannot generate checksum because save file does not exist or is empty: " + saveFilePath);
        }

        String checksum = calculateSHA256(saveFile);

        FileHandle checksumFile = FileUtils.getFileHandle(saveFilePath + CHECKSUM_SUFFIX);
        checksumFile.writeString(checksum + System.lineSeparator(), false);

        System.out.println("[SAVE-INTEGRITY] Checksum written: " + checksumFile.path());
    }

    /**
     * Verifies whether the save file matches its checksum file.
     *
     * If the checksum file does not exist, this method treats the save as a
     * legacy save and allows loading to continue. This avoids breaking old saves
     * that were created before the checksum feature existed.
     *
     * @param saveFilePath project/local save file path
     * @throws IOException if the checksum exists but does not match, or if file reading fails
     */
    public static void verifyChecksum(String saveFilePath) throws IOException {
        System.out.println("[SAVE-INTEGRITY] verifyChecksum called with path: " + saveFilePath);

        FileHandle saveFile = FileUtils.getFileHandle(saveFilePath);

        if (!saveFile.exists()) {
            throw new IOException("Cannot verify checksum because save file does not exist: " + saveFilePath);
        }

        FileHandle checksumFile = FileUtils.getFileHandle(saveFilePath + CHECKSUM_SUFFIX);

        // Legacy compatibility: allow loading if no checksum file exists yet.
        if (!checksumFile.exists()) {
            System.out.println("[SAVE-INTEGRITY] No checksum file found for legacy save: " + saveFilePath);
            return;
        }

        System.out.println("[SAVE-INTEGRITY] Checksum file found for: " + saveFilePath);

        String expected = checksumFile.readString().trim();
        String actual = calculateSHA256(saveFile);

        if (!expected.equalsIgnoreCase(actual)) {
            String message = "Save integrity validation failed for " + saveFilePath
                    + ". Expected checksum: " + expected
                    + ", actual checksum: " + actual;

            System.out.println("[SAVE-INTEGRITY] " + message);

            throw new IOException(message);
        }

        System.out.println("[SAVE-INTEGRITY] Checksum verified: " + saveFilePath);
    }

    /**
     * Returns true if the save file has a checksum file.
     * This is mainly useful for testing or debugging.
     */
    public static boolean hasChecksum(String saveFilePath) {
        return Gdx.files.local(saveFilePath + CHECKSUM_SUFFIX).exists();
    }

    private static String calculateSHA256(FileHandle file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[8192];

            try (InputStream rawInput = file.read();
                 BufferedInputStream input = new BufferedInputStream(rawInput)) {

                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            return toHex(digest.digest());

        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm is not available.", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }

        return result.toString();
    }
}