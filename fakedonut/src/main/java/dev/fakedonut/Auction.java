package dev.fakedonut;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_3222;
import net.minecraft.class_6903;
import net.minecraft.server.MinecraftServer;

public final class Auction {
    public static final class Listing {
        public int id;
        public String seller;
        public String sellerId;
        public JsonElement stack;
        public long price;
        public long expires;
        public transient class_1799 cache;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Listing> LISTINGS = new ArrayList<>();
    private static final String[] NPC = {"Notch_AH", "ShopKeeper", "xX_Flipper_Xx", "DonutBaron", "SpawnerSally", "KelpFarmer", "DeepslateDan", "Trader_Joe"};
    private static MinecraftServer server;
    private static int nextId = 1;
    private static long lastRestock;

    private Auction() {
    }

    public static synchronized void start(MinecraftServer s) {
        server = s;
        load();
        restock();
    }

    public static synchronized void stop() {
        save();
        server = null;
    }

    private static Path file() {
        return Config.dir().resolve("auction.json");
    }

    private static class_6903<JsonElement> ops() {
        return class_6903.method_46632(JsonOps.INSTANCE, server.method_30611());
    }

    private static synchronized void load() {
        LISTINGS.clear();
        try {
            if (Files.isRegularFile(file())) {
                List<Listing> read = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), new TypeToken<List<Listing>>() { }.getType());
                if (read != null) {
                    LISTINGS.addAll(read);
                }
            }
        } catch (IOException | RuntimeException e) {
            FakeDonut.LOG.warn("auction.json could not be read", e);
        }
        for (Listing l : LISTINGS) {
            nextId = Math.max(nextId, l.id + 1);
        }
    }

    public static synchronized void save() {
        try {
            Files.writeString(file(), GSON.toJson(LISTINGS), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FakeDonut.LOG.warn("auction.json could not be written", e);
        }
    }

    public static synchronized class_1799 stackOf(Listing l) {
        if (l.cache == null) {
            Optional<class_1799> parsed = class_1799.field_24671.parse(ops(), l.stack).result();
            l.cache = parsed.orElse(class_1799.field_8037);
        }
        return l.cache.method_7972();
    }

    private static synchronized Listing make(String seller, UUID sellerId, class_1799 stack, long price, long hours) {
        Listing l = new Listing();
        l.id = nextId++;
        l.seller = seller;
        l.sellerId = sellerId == null ? null : sellerId.toString();
        l.stack = class_1799.field_24671.encodeStart(ops(), stack).result().orElseThrow();
        l.price = price;
        l.expires = System.currentTimeMillis() + hours * 3_600_000L;
        l.cache = stack.method_7972();
        LISTINGS.add(l);
        return l;
    }

    public static synchronized Listing sell(class_3222 player, class_1799 stack, long price) {
        Listing l = make(player.method_5477().getString(), player.method_5667(), stack, price, Config.get().listingHours);
        save();
        return l;
    }

    public static synchronized void expire() {
        long now = System.currentTimeMillis();
        for (java.util.Iterator<Listing> it = LISTINGS.iterator(); it.hasNext();) {
            Listing l = it.next();
            if (l.expires < now) {
                if (l.sellerId != null && server != null) {
                    class_3222 p = server.method_3760().method_14602(UUID.fromString(l.sellerId));
                    class_1799 back = stackOf(l);
                    if (p != null) {
                        p.method_31548().method_7398(back);
                        p.method_64398(Items2.text("Your listing expired: " + back.method_7947() + "x " + back.method_7964().getString()));
                    }
                }
                it.remove();
            }
        }
    }

    public static synchronized List<Listing> sorted(int mode) {
        List<Listing> out = new ArrayList<>(LISTINGS);
        switch (mode) {
            case 1 -> out.sort(Comparator.comparingLong((Listing l) -> l.price));
            case 2 -> out.sort(Comparator.comparingLong((Listing l) -> l.price).reversed());
            case 3 -> out.sort(Comparator.comparingLong((Listing l) -> l.expires));
            default -> out.sort(Comparator.comparingLong((Listing l) -> l.expires).reversed());
        }
        return out;
    }

    public static synchronized Listing byId(int id) {
        for (Listing l : LISTINGS) {
            if (l.id == id) {
                return l;
            }
        }
        return null;
    }

    public static synchronized boolean buy(class_3222 buyer, int id) {
        Listing l = byId(id);
        if (l == null) {
            buyer.method_64398(Items2.text("That listing is gone."));
            return false;
        }
        if (l.sellerId != null && l.sellerId.equals(buyer.method_5667().toString())) {
            buyer.method_64398(Items2.text("You cannot buy your own listing."));
            return false;
        }
        if (!Economy.take(buyer.method_5667(), l.price)) {
            buyer.method_64398(Items2.text("You need " + Economy.money(l.price) + " (you have " + Economy.money(Economy.balance(buyer.method_5667())) + ")."));
            return false;
        }
        LISTINGS.remove(l);
        class_1799 got = stackOf(l);
        buyer.method_31548().method_7398(got);
        if (l.sellerId != null) {
            UUID seller = UUID.fromString(l.sellerId);
            Economy.add(seller, l.price);
            class_3222 online = server.method_3760().method_14602(seller);
            if (online != null) {
                online.method_64398(Items2.text(buyer.method_5477().getString() + " bought your " + got.method_7964().getString() + " for " + Economy.money(l.price)));
            }
        }
        buyer.method_64398(Items2.text("Bought " + got.method_7947() + "x " + got.method_7964().getString() + " for " + Economy.money(l.price)));
        save();
        Economy.save();
        return true;
    }

    public static synchronized void restock() {
        if (server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRestock < 60_000L && !LISTINGS.isEmpty()) {
            return;
        }
        lastRestock = now;
        expire();
        Random rnd = new Random(now / 60_000L);
        while (LISTINGS.size() < Config.get().minListings) {
            Object[] pick = SEED_ITEMS[rnd.nextInt(SEED_ITEMS.length)];
            class_1792 item = (class_1792) pick[0];
            int base = (Integer) pick[1];
            int maxCount = (Integer) pick[2];
            int count = 1 + rnd.nextInt(Math.max(1, maxCount));
            double factor = Math.exp(rnd.nextGaussian() * 0.45);
            long price = Math.max(1, Math.round(base * count * factor));
            make(NPC[rnd.nextInt(NPC.length)], null, new class_1799(item, count), price, 1 + rnd.nextInt(Config.get().listingHours));
        }
        save();
    }

    private static final Object[][] SEED_ITEMS = {
        {Ids.DIAMOND, 400, 16}, {Ids.NETHERITE_INGOT, 4500, 4}, {Ids.NETHERITE_SCRAP, 1200, 8}, {Ids.ELYTRA, 30000, 1},
        {Ids.TOTEM_OF_UNDYING, 2500, 3}, {Ids.ENCHANTED_GOLDEN_APPLE, 9000, 2}, {Ids.GOLDEN_APPLE, 300, 16},
        {Ids.ENDER_PEARL, 150, 16}, {Ids.DIAMOND_PICKAXE, 1800, 1}, {Ids.NETHERITE_SWORD, 12000, 1}, {Ids.NETHERITE_PICKAXE, 15000, 1},
        {Ids.EMERALD, 90, 32}, {Ids.IRON_INGOT, 20, 64}, {Ids.GOLD_INGOT, 35, 64}, {Ids.OBSIDIAN.method_8389(), 40, 64},
        {Ids.TNT.method_8389(), 60, 64}, {Ids.EXPERIENCE_BOTTLE, 45, 64}, {Ids.ARROW, 4, 64}, {Ids.COOKED_BEEF, 6, 64},
        {Ids.NETHERITE_HELMET, 14000, 1}, {Ids.NETHERITE_CHESTPLATE, 20000, 1}, {Ids.END_CRYSTAL, 650, 16}, {Ids.TRIDENT, 8000, 1},
        {Ids.MACE, 40000, 1}, {Ids.SPAWNER.method_8389(), 250000, 1}, {Ids.AMETHYST_SHARD, 20, 64}, {Ids.WIND_CHARGE, 25, 64}
    };
}
