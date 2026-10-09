package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;

public final class ClientLifecycleEvents {
    public static final Event<ClientStopping> CLIENT_STOPPING = null;

    @FunctionalInterface
    public interface ClientStopping {
        void onClientStopping(net.minecraft.class_310 client);
    }
}
