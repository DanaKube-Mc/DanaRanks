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
        // Arrange
        Logger logger = Logger.getLogger("TestLogger");

        // Act
        MessageManager manager = new MessageManager(
                tempDir.toFile(),
                logger,
                (path, replace) -> { throw new IllegalArgumentException("Resource not found in jar"); }
        );

        // Assert
        java.io.File expectedFile = new java.io.File(tempDir.toFile(), "lang/fr.yml");
        assertTrue(expectedFile.exists());

        String expectedPrefix = "§6§lD§6§la§6§ln§6§la§6§lK§6§lu§6§lb§e§le§r§l §7§l|§r ";
        assertEquals(expectedPrefix + "§cImpossible de charger vos données de rang. Veuillez vous reconnecter.",
                manager.getMessage("kick-database-error"));
        assertEquals(expectedPrefix + "§cVous n'avez pas la permission d'exécuter cette commande.",
                manager.getMessage("no-permission"));
        assertEquals(expectedPrefix + "§aVotre profil de rang a été correctement chargé !",
                manager.getMessage("profile-loaded"));

        Component kickComponent = manager.getMessageComponent("kick-database-error");
        assertNotNull(kickComponent);

        assertEquals("§cFallback", manager.getMessage("non-existent-key", "<red>Fallback"));
    }
}
