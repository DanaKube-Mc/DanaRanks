package app.danakube.danaranks.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.kyori.adventure.text.Component;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class MessageManagerTest {

    @Test
    public void testMessageLoading(@TempDir Path tempDir) throws Exception {
        Logger logger = Logger.getLogger("TestLogger");

        MessageManager manager = new MessageManager(
                tempDir.toFile(),
                logger,
                (path, replace) -> { throw new IllegalArgumentException("Resource not found in jar"); }
        );

        java.io.File expectedFile = new java.io.File(tempDir.toFile(), "lang/fr.yml");
        assertTrue(expectedFile.exists());

        assertEquals("§c[DanaRanks] Impossible de charger vos données de rang. Veuillez vous reconnecter.",
                manager.getMessage("kick-database-error"));
        assertEquals("§cVous n'avez pas la permission d'exécuter cette commande.",
                manager.getMessage("no-permission"));
        assertEquals("§aVotre profil de rang a été correctement chargé !",
                manager.getMessage("profile-loaded"));

        Component kickComponent = manager.getMessageComponent("kick-database-error");
        assertNotNull(kickComponent);

        assertEquals("§cFallback", manager.getMessage("non-existent-key", "<red>Fallback"));
    }
}
