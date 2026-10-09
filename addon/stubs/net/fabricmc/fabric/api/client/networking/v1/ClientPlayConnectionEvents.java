package net.fabricmc.fabric.api.client.networking.v1;

import net.fabricmc.fabric.api.event.Event;

public final class ClientPlayConnectionEvents {
    public static final Event<Disconnect> DISCONNECT = null;

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(net.minecraft.class_634 handler, net.minecraft.class_310 client);
    }
}
