package app.danakube.danaranks.tracker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TrackerRegistryTest {

    @Test
    public void testTrackerResourceNames() {
        BlockBreakTracker blockBreak = new BlockBreakTracker(null);
        assertEquals("blocks_broken", blockBreak.getResourceName());

        BlockPlaceTracker blockPlace = new BlockPlaceTracker(null);
        assertEquals("blocks_placed", blockPlace.getResourceName());

        MobsKilledTracker mobsKilled = new MobsKilledTracker(null);
        assertEquals("mobs_killed", mobsKilled.getResourceName());

        FishCaughtTracker fishCaught = new FishCaughtTracker(null);
        assertEquals("fish_caught", fishCaught.getResourceName());

        ItemsCraftedTracker itemsCrafted = new ItemsCraftedTracker(null);
        assertEquals("items_crafted", itemsCrafted.getResourceName());

        VillagerTradesTracker villagerTrades = new VillagerTradesTracker(null);
        assertEquals("villager_trades", villagerTrades.getResourceName());

        DistanceTraveledTracker distanceTraveled = new DistanceTraveledTracker(null);
        assertEquals("distance_traveled", distanceTraveled.getResourceName());

        CropsHarvestedTracker cropsHarvested = new CropsHarvestedTracker(null);
        assertEquals("crops_harvested", cropsHarvested.getResourceName());

        ItemsSmeltedTracker itemsSmelted = new ItemsSmeltedTracker(null);
        assertEquals("items_smelted", itemsSmelted.getResourceName());
    }
}
