package com.shatteredpixel.shatteredpixeldungeon.actors.mobs;

import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
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
import static org.junit.Assert.assertTrue;

public class MobStateLoggingTest {

    private static final File LOG_FILE = new File("mob_behavior.log");

    private PrintStream originalOut;

    @Before
    public void setUp() {
        Actor.clear();
        deleteLogFile();
        originalOut = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream()));
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
        deleteLogFile();
        Actor.clear();
    }

    @Test
    public void updateStateLogsStateTransitionWhenStateChanges() throws Exception {
        TestMob mob = new TestMob("state mob");

        mob.transitionToWandering();

        List<String> fileLines = Files.readAllLines(LOG_FILE.toPath(), StandardCharsets.UTF_8);

        assertEquals(1, fileLines.size());
        assertTrue(fileLines.get(0).contains("[STATE_TRANSITION] Sleeping -> Wandering"));
    }

    @Test
    public void updateStateDoesNotLogDuplicateTransitionForSameState() throws Exception {
        TestMob mob = new TestMob("state mob");

        mob.transitionToWandering();
        mob.transitionToWandering();

        List<String> fileLines = Files.readAllLines(LOG_FILE.toPath(), StandardCharsets.UTF_8);

        assertEquals(1, fileLines.size());
    }

    @Test
    public void onAddLogsSpawnOnlyOnce() throws Exception {
        TestMob mob = new TestMob("spawn mob");
        mob.pos = 12;

        mob.addToLevel();
        mob.addToLevel();

        List<String> fileLines = Files.readAllLines(LOG_FILE.toPath(), StandardCharsets.UTF_8);

        assertEquals(1, fileLines.size());
        assertTrue(fileLines.get(0).contains("[SPAWN] Spawned at pos: 12"));
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
            HP = HT = 10;
        }

        @Override
        public String name() {
            return name;
        }

        private void transitionToWandering() {
            updateState(WANDERING);
        }

        private void addToLevel() {
            onAdd();
        }
    }
}
