package dev.fakedonut;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1263;
import net.minecraft.class_1277;
import net.minecraft.class_1657;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1707;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_3222;
import net.minecraft.class_3917;
import net.minecraft.class_747;

public final class Menus {
    private static final int PAGE = 45;

    private Menus() {
    }

    static void open(class_3222 player, String title, java.util.function.BiFunction<Integer, class_1661, class_1703> make) {
        player.method_17355(new class_747((syncId, inv, p) -> make.apply(syncId, inv), Items2.text(title)));
    }

    public static void openAh(class_3222 player, int page, int sort) {
        List<Auction.Listing> all = Auction.sorted(sort);
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        int p = Math.max(0, Math.min(page, pages - 1));
        open(player, "Auction (Page " + (p + 1) + ")", (id, inv) -> new AhMenu(id, inv, player, p, sort));
    }

    static final class AhMenu extends class_1707 {
        private final class_1277 view;
        private final class_3222 viewer;
        private final int page;
        private final int sort;
        private final List<Auction.Listing> shown = new ArrayList<>();

        AhMenu(int syncId, class_1661 inv, class_3222 viewer, int page, int sort) {
            this(syncId, inv, new class_1277(54), viewer, page, sort);
        }

        private AhMenu(int syncId, class_1661 inv, class_1277 view, class_3222 viewer, int page, int sort) {
            super(class_3917.field_17327, syncId, inv, view, 6);
            this.view = view;
            this.viewer = viewer;
            this.page = page;
            this.sort = sort;
            this.fill();
        }

        private void fill() {
            List<Auction.Listing> all = Auction.sorted(this.sort);
            int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
            this.shown.clear();
            for (int i = 0; i < 54; i++) {
                this.view.method_5447(i, class_1799.field_8037);
            }
            for (int i = 0; i < PAGE; i++) {
                int idx = this.page * PAGE + i;
                if (idx < all.size()) {
                    Auction.Listing l = all.get(idx);
                    this.shown.add(l);
                    this.view.method_5447(i, Items2.withLore(Auction.stackOf(l), List.of("", "Price: " + Economy.money(l.price),
                            "Seller: " + l.seller, "Expires in: " + expiry(l.expires), "", "Click to buy")));
                } else {
                    this.shown.add(null);
                }
            }
            for (int i = 45; i < 54; i++) {
                this.view.method_5447(i, Items2.named(class_2246.field_10077.method_8389(), " "));
            }
            this.view.method_5447(45, Items2.named(class_1802.field_8107, "Previous Page"));
            this.view.method_5447(48, Items2.named(class_1802.field_8251, "Sort: " + SORTS[this.sort], "Click to change"));
            this.view.method_5447(49, Items2.named(class_1802.field_8695, "Page " + (this.page + 1) + "/" + pages, "Balance: " + Economy.money(Economy.balance(this.viewer.method_5667()))));
            this.view.method_5447(50, Items2.named(class_1802.field_8557, "Refresh"));
            this.view.method_5447(53, Items2.named(class_1802.field_8107, "Next Page"));
        }

        static final String[] SORTS = {"Recently Listed", "Lowest Price", "Highest Price", "Ending Soon"};

        @Override
        public void method_7593(int slot, int button, class_1713 type, class_1657 player) {
            if (slot >= 0 && slot < 54) {
                this.click(slot);
                this.method_34252();
                return;
            }
            if (type == class_1713.field_7794 || type == class_1713.field_7793 || type == class_1713.field_7789 || type == class_1713.field_7796) {
                this.method_34252();
                return;
            }
            super.method_7593(slot, button, type, player);
        }

        private void click(int slot) {
            if (slot < PAGE) {
                Auction.Listing l = this.shown.get(slot);
                if (l != null) {
                    openConfirm(this.viewer, l.id, this.page, this.sort);
                }
                return;
            }
            switch (slot) {
                case 45 -> openAh(this.viewer, this.page - 1, this.sort);
                case 53 -> openAh(this.viewer, this.page + 1, this.sort);
                case 48 -> openAh(this.viewer, 0, (this.sort + 1) % 4);
                case 50 -> openAh(this.viewer, this.page, this.sort);
                default -> { }
            }
        }

        @Override
        public class_1799 method_7601(class_1657 player, int slot) {
            return class_1799.field_8037;
        }

        @Override
        public boolean method_7613(class_1799 stack, class_1735 slot) {
            return slot.field_7871 != this.view && super.method_7613(stack, slot);
        }
    }

    static String expiry(long at) {
        long minutes = Math.max(0, (at - System.currentTimeMillis()) / 60_000L);
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    static void openConfirm(class_3222 player, int listingId, int page, int sort) {
        Auction.Listing l = Auction.byId(listingId);
        if (l == null) {
            player.method_64398(Items2.text("That listing is gone."));
            openAh(player, page, sort);
            return;
        }
        open(player, "Confirm Purchase", (id, inv) -> new ConfirmMenu(id, inv, player, l, page, sort));
    }

    static final class ConfirmMenu extends class_1707 {
        private final class_3222 viewer;
        private final Auction.Listing listing;
        private final int page;
        private final int sort;

        ConfirmMenu(int syncId, class_1661 inv, class_3222 viewer, Auction.Listing l, int page, int sort) {
            this(syncId, inv, new class_1277(27), viewer, l, page, sort);
        }

        private ConfirmMenu(int syncId, class_1661 inv, class_1277 view, class_3222 viewer, Auction.Listing l, int page, int sort) {
            super(class_3917.field_17326, syncId, inv, view, 3);
            this.viewer = viewer;
            this.listing = l;
            this.page = page;
            this.sort = sort;
            for (int i = 0; i < 27; i++) {
                view.method_5447(i, Items2.named(class_2246.field_10077.method_8389(), " "));
            }
            view.method_5447(13, Items2.withLore(Auction.stackOf(l), List.of("", "Price: " + Economy.money(l.price), "Seller: " + l.seller)));
            view.method_5447(11, Items2.named(class_2246.field_10305.method_8389(), "Confirm", "Buy for " + Economy.money(l.price)));
            view.method_5447(15, Items2.named(class_2246.field_10118.method_8389(), "Cancel"));
        }

        @Override
        public void method_7593(int slot, int button, class_1713 type, class_1657 player) {
            if (slot == 11) {
                Auction.buy(this.viewer, this.listing.id);
                openAh(this.viewer, this.page, this.sort);
            } else if (slot == 15) {
                openAh(this.viewer, this.page, this.sort);
            }
            if (slot < 27 || type == class_1713.field_7794 || type == class_1713.field_7793 || type == class_1713.field_7789) {
                this.method_34252();
                return;
            }
            super.method_7593(slot, button, type, player);
        }

        @Override
        public class_1799 method_7601(class_1657 player, int slot) {
            return class_1799.field_8037;
        }
    }

    public static void openSell(class_3222 player) {
        open(player, "Sell", (id, inv) -> new SellMenu(id, inv, player));
    }

    static final class SellMenu extends class_1707 {
        private final class_3222 viewer;
        private final class_1277 box;

        SellMenu(int syncId, class_1661 inv, class_3222 viewer) {
            this(syncId, inv, new class_1277(27), viewer);
        }

        private SellMenu(int syncId, class_1661 inv, class_1277 box, class_3222 viewer) {
            super(class_3917.field_17326, syncId, inv, box, 3);
            this.viewer = viewer;
            this.box = box;
            for (int i = 18; i < 27; i++) {
                box.method_5447(i, Items2.named(class_2246.field_10077.method_8389(), " "));
            }
            box.method_5447(22, Items2.named(class_2246.field_10305.method_8389(), "Sell", "Sells everything above"));
        }

        @Override
        public void method_7593(int slot, int button, class_1713 type, class_1657 player) {
            if (slot >= 18 && slot < 27) {
                if (slot == 22) {
                    this.sellAll();
                }
                this.method_34252();
                return;
            }
            if (type == class_1713.field_7794 || type == class_1713.field_7793 || type == class_1713.field_7789) {
                if (type == class_1713.field_7794 && slot >= 27) {
                    class_1799 stack = this.field_7761.get(slot).method_7677();
                    if (!stack.method_7960() && this.method_7616(stack, 0, 18, false)) {
                        this.field_7761.get(slot).method_7668();
                    }
                }
                this.method_34252();
                return;
            }
            super.method_7593(slot, button, type, player);
        }

        private void sellAll() {
            long total = 0;
            for (int i = 0; i < 18; i++) {
                class_1799 s = this.box.method_5438(i);
                if (!s.method_7960()) {
                    total += Prices.unit(s.method_7909()) * s.method_7947();
                    this.box.method_5447(i, class_1799.field_8037);
                }
            }
            if (total > 0) {
                Economy.add(this.viewer.method_5667(), total);
                Economy.save();
                this.viewer.method_64398(Items2.text("You sold items for " + Economy.money(total) + ". Balance: " + Economy.money(Economy.balance(this.viewer.method_5667()))));
            } else {
                this.viewer.method_64398(Items2.text("Put items into the top rows first."));
            }
        }

        @Override
        public void method_7595(class_1657 player) {
            for (int i = 0; i < 18; i++) {
                class_1799 s = this.box.method_5438(i);
                if (!s.method_7960()) {
                    player.method_31548().method_7398(s);
                    this.box.method_5447(i, class_1799.field_8037);
                }
            }
            super.method_7595(player);
        }

        @Override
        public class_1799 method_7601(class_1657 player, int slot) {
            return class_1799.field_8037;
        }
    }
}
