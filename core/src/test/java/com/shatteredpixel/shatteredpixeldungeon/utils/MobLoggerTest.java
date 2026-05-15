package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MobLoggerTest {

    private static final File LOG_FILE = new File("mob_behavior.log");

    private PrintStream originalOut;
    private ByteArrayOutputStream capturedOut;

    @Before
    public void setUp() {
        Actor.clear();
        deleteLogFile();
        originalOut = System.out;
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
        deleteLogFile();
        Actor.clear();
    }

    @Test
    public void logWritesExpectedFormatToStdoutAndFile() throws Exception {
        TestMob mob = new TestMob("test rat");
        int mobId = mob.id();

        MobLogger.log(mob, "SPAWN", "Spawned at pos: 42");

        String stdoutLine = capturedOut.toString(StandardCharsets.UTF_8.name()).trim();
        List<String> fileLines = Files.readAllLines(LOG_FILE.toPath(), StandardCharsets.UTF_8);

        assertEquals(1, fileLines.size());
        assertEquals(stdoutLine, fileLines.get(0));
        assertTrue(stdoutLine.matches(
                "\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}] " +
                        "\\[TICK:\\d+\\.\\d{2}] \\[test rat:" + mobId + "] " +
                        "\\[SPAWN] Spawned at pos: 42"));
    }

    @Test
    public void logAppendsEntriesInsteadOfOverwriting() throws Exception {
        TestMob mob = new TestMob("test crab");

        MobLogger.log(mob, "ALERT", "Mob became alerted.");
        MobLogger.log(mob, "TARGET_ASSIGN", "New target: hero");

        List<String> fileLines = Files.readAllLines(LOG_FILE.toPath(), StandardCharsets.UTF_8);

        assertEquals(2, fileLines.size());
        assertTrue(fileLines.get(0).contains("[ALERT] Mob became alerted."));
        assertTrue(fileLines.get(1).contains("[TARGET_ASSIGN] New target: hero"));
    }

    @Test
    public void logIncludesDistinctIdsForMobsWithSameName() throws Exception {
        TestMob first = new TestMob("duplicate");
        TestMob second = new TestMob("duplicate");
        int firstId = first.id();
        int secondId = second.id();

        MobLogger.log(first, "SPAWN", "first");
        MobLogger.log(second, "SPAWN", "second");

        List<String> fileLines = Files.readAllLines(LOG_FILE.toPath(), StandardCharsets.UTF_8);

        assertEquals(2, fileLines.size());
        assertTrue(fileLines.get(0).contains("[duplicate:" + firstId + "]"));
        assertTrue(fileLines.get(1).contains("[duplicate:" + secondId + "]"));
        assertFalse(firstId == secondId);
    }

    @Test
    public void logSwallowsInvalidMobInput() {
        MobLogger.log(null, "SPAWN", "invalid");

        assertFalse(LOG_FILE.exists());
        assertEquals("", capturedOut.toString());
    }

    private static void deleteLogFile() {
        if (LOG_FILE.exists()) {
            LOG_FILE.delete();
        }
    }

    private static class TestMob extends Mob {

        private final String name;

        private TestMob(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
