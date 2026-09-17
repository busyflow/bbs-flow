package mchorse.bbs_mod.film;

import mchorse.bbs_mod.actions.types.crowd.CrowdExportPreload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for video export and crowd preload readiness stability.
 */
public class ExportWarmupStabilityTest
{
    @Test
    public void testFilmExportStateTracking()
    {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        assertFalse(FilmExportState.isExporting(player1));
        assertFalse(FilmExportState.isAnyExporting());

        FilmExportState.set(player1, true);
        assertTrue(FilmExportState.isExporting(player1));
        assertFalse(FilmExportState.isExporting(player2));
        assertTrue(FilmExportState.isAnyExporting());

        FilmExportState.set(player1, false);
        assertFalse(FilmExportState.isExporting(player1));
        assertFalse(FilmExportState.isAnyExporting());
    }

    @Test
    public void testCrowdExportPreloadLifecycle()
    {
        String filmId = "test_film_" + System.currentTimeMillis();

        /* If waiting is false (not on server), isReady returns true immediately */
        CrowdExportPreload.begin(filmId);
        assertTrue(CrowdExportPreload.isReady(filmId));
        CrowdExportPreload.finish(filmId);

        /* Mismatched film ID is considered ready */
        assertTrue(CrowdExportPreload.isReady("other_film"));
    }

    @Test
    public void testActionPlayerResetsCrowdExportReady() throws IOException
    {
        Path actionPlayerSource = Path.of("src/main/java/mchorse/bbs_mod/actions/ActionPlayer.java");
        String content = Files.readString(actionPlayerSource, StandardCharsets.UTF_8);

        /* Verify that preloadCrowdsForExport clears crowdExportReadySent */
        assertTrue(content.contains("this.crowdExportReadySent = false;"),
            "ActionPlayer must reset crowdExportReadySent to false");
        assertTrue(content.contains("public void resetCrowdExportReady()"),
            "ActionPlayer must expose resetCrowdExportReady()");
    }

    @Test
    public void testGameRendererMixinDoesNotGateOnRenderBeforeScreenByCurrent() throws IOException
    {
        Path mixinSource = Path.of("src/client/java/mchorse/bbs_mod/mixin/client/GameRendererMixin.java");
        String content = Files.readString(mixinSource, StandardCharsets.UTF_8);

        /* Verify that onBeforeHudRendering unconditionally executes onRenderBeforeScreen */
        assertFalse(content.contains("current == null"),
            "GameRendererMixin must not gate onRenderBeforeScreen on current == null");
    }
}
