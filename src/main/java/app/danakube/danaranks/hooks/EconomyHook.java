package app.danakube.danaranks.hooks;

import java.util.UUID;

public interface EconomyHook {
    boolean has(UUID uuid, double amount);
    boolean withdraw(UUID uuid, double amount);
    boolean deposit(UUID uuid, double amount);
}
