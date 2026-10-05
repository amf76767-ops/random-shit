package dev.fakedonut;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.Random;
import net.minecraft.class_1799;
import net.minecraft.class_2168;
import net.minecraft.class_2170;
import net.minecraft.class_2818;
import net.minecraft.class_3222;

public final class Commands {
    private Commands() {
    }

    private static void say(class_2168 src, String s) {
        src.method_9226(() -> Items2.text(s), false);
    }

    public static void register(CommandDispatcher<class_2168> d) {
        d.register(class_2170.method_9247("ah")
            .executes(ctx -> {
                class_3222 p = ctx.getSource().method_44023();
                if (p != null) {
                    Menus.openAh(p, 0, 0);
                }
                return 1;
            })
            .then(class_2170.method_9247("sell")
                .then(class_2170.method_9244("price", LongArgumentType.longArg(1, 1_000_000_000_000L))
                    .executes(ctx -> {
                        class_3222 p = ctx.getSource().method_44023();
                        if (p == null) {
                            return 0;
                        }
                        class_1799 hand = p.method_6047();
                        if (hand.method_7960()) {
                            say(ctx.getSource(), "Hold an item in your hand.");
                            return 0;
                        }
                        long price = LongArgumentType.getLong(ctx, "price");
                        Auction.sell(p, hand.method_7972(), price);
                        p.method_6122(net.minecraft.class_1268.field_5808, class_1799.field_8037);
                        say(ctx.getSource(), "Listed for " + Economy.money(price) + " (" + Config.get().listingHours + "h).");
                        return 1;
                    }))));
        d.register(class_2170.method_9247("sell").executes(ctx -> {
            class_3222 p = ctx.getSource().method_44023();
            if (p != null) {
                Menus.openSell(p);
            }
            return 1;
        }));
        d.register(class_2170.method_9247("bal").executes(ctx -> {
            class_3222 p = ctx.getSource().method_44023();
            if (p != null) {
                say(ctx.getSource(), "Balance: " + Economy.money(Economy.balance(p.method_5667())));
            }
            return 1;
        }));
        d.register(class_2170.method_9247("fakedonut")
            .then(class_2170.method_9247("status").executes(ctx -> {
                Config c = Config.get();
                say(ctx.getSource(), "Bases: " + Bases.all().size() + ", anti-xray: " + c.antiXray + ", hidden below Y " + c.hideBelowY + ", reveal radius " + c.revealChunks + " chunks, chance " + c.baseChance);
                return 1;
            }))
            .then(class_2170.method_9247("list").executes(ctx -> {
                List<Bases.Base> all = Bases.all();
                int n = 0;
                for (Bases.Base b : all) {
                    if (n++ >= 20) {
                        say(ctx.getSource(), "... " + (all.size() - 20) + " more");
                        break;
                    }
                    say(ctx.getSource(), b.owner + ": " + b.x + " " + b.y + " " + b.z + "  kelp " + b.kelpX + " " + b.kelpTop + " " + b.kelpZ + (b.spawner ? "  spawner" : ""));
                }
                if (all.isEmpty()) {
                    say(ctx.getSource(), "No bases yet. Explore oceans or use /fakedonut base.");
                }
                return 1;
            }))
            .then(class_2170.method_9247("base").executes(ctx -> {
                class_3222 p = ctx.getSource().method_44023();
                if (p == null) {
                    return 0;
                }
                class_2818 chunk = p.method_51469().method_8500(p.method_24515());
                boolean ok = BaseGen.tryChunk(p.method_51469(), chunk, new Random(), true);
                say(ctx.getSource(), ok ? "Kelp planted and base built under it. See /fakedonut list." : "No suitable ocean (6+ deep, sand/gravel floor) in this chunk, or a base already exists.");
                return ok ? 1 : 0;
            }))
            .then(class_2170.method_9247("antixray")
                .then(class_2170.method_9244("on", StringArgumentType.word()).executes(ctx -> {
                    boolean on = !StringArgumentType.getString(ctx, "on").equalsIgnoreCase("off");
                    Config.get().antiXray = on;
                    say(ctx.getSource(), "Anti-xray " + (on ? "on" : "off") + " (applies to chunks sent from now on).");
                    return 1;
                }))));
    }
}
