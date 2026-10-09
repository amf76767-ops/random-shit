package dev.dihclient.port.discordbot;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.util.Notifications;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_634;
import net.minecraft.class_642;

final class BotCommands {
    static final String HELP = String.join("\n",
            "**Commands** (as slash command, or as text in a DM / `/tell`):",
            "`status` what the game is doing right now",
            "`modules` active modules",
            "`log` the last notifications",
            "`stop` turns off every active module except visuals and lets go of all keys",
            "`toggle <module>` turns a module on or off",
            "`say <text>` sends a chat message or a /command in game",
            "`disconnect` leaves the server",
            "`help` this list");

    private static final long GAME_STARTED = ProcessHandle.current().info().startInstant().orElseGet(Instant::now).getEpochSecond();
    private static final class_310 mc = class_310.method_1551();

    private final DiscordBot bot;

    BotCommands(DiscordBot bot) {
        this.bot = bot;
    }

    String tell(String text) {
        String t = text.trim();
        if (t.startsWith("/")) {
            t = t.substring(1);
        }
        int space = t.indexOf(' ');
        String cmd = (space < 0 ? t : t.substring(0, space)).toLowerCase(Locale.ROOT);
        String rest = space < 0 ? "" : t.substring(space + 1).trim();
        return this.run(cmd, rest);
    }

    String run(String cmd, String arg) {
        return switch (cmd) {
            case "status", "s" -> this.status();
            case "modules", "m" -> this.modules();
            case "log", "l" -> this.log();
            case "stop" -> this.stop();
            case "toggle", "t" -> this.toggle(arg);
            case "say" -> this.say(arg);
            case "disconnect", "dc" -> this.disconnect();
            case "help", "h", "" -> HELP;
            default -> "Unknown command `" + cmd + "`.\n" + HELP;
        };
    }

    List<String> moduleNames(String prefix) {
        String p = norm(prefix);
        List<String> out = new ArrayList<>();
        for (Module m : visible()) {
            if (m != this.bot && norm(m.name()).contains(p) && (m.isToggleable() || m.isActionModule())) {
                out.add(m.name());
                if (out.size() >= 25) {
                    break;
                }
            }
        }
        return out;
    }

    private static <T> T onGame(Supplier<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                f.complete(task.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("The game did not answer in time (loading or frozen?)");
        }
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "").replace("_", "");
    }

    private static List<Module> visible() {
        List<Module> out = new ArrayList<>();
        if (DIHClient.modules() == null) {
            return out;
        }
        for (Module m : DIHClient.modules().all()) {
            if (!m.isHidden()) {
                out.add(m);
            }
        }
        return out;
    }

    private static boolean visual(Module m) {
        return m.category() == Category.RENDER || m.category() == Category.CLIENT || m.category() == Category.DEBUG;
    }

    private static String label(Module m) {
        String info = null;
        try {
            info = m.getInfo();
        } catch (Throwable ignored) {

        }
        return info == null || info.isBlank() ? m.name() : m.name() + " [" + info + "]";
    }

    private static String where() {
        if (mc.field_1687 == null || mc.field_1724 == null) {
            return "In the main menu";
        }
        class_642 server = mc.method_1558();
        String place = server != null ? server.field_3761 : mc.method_1542() ? "Singleplayer" : "a server";
        return "On " + place + " · " + mc.field_1687.method_27983().method_29177().method_12832();
    }

    private static String duration(long seconds) {
        long h = seconds / 3600;
        long m = seconds % 3600 / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m " + seconds % 60 + "s";
    }

    String status() {
        return onGame(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("**DIHClient** · ").append(mc.method_1548().method_1676()).append('\n');
            sb.append(where()).append('\n');
            if (mc.field_1724 != null) {
                sb.append(String.format(Locale.ROOT, "Position %d %d %d · Health %.0f/%.0f · Food %d%n",
                        (int) Math.floor(mc.field_1724.method_23317()), (int) Math.floor(mc.field_1724.method_23318()), (int) Math.floor(mc.field_1724.method_23321()),
                        mc.field_1724.method_6032(), mc.field_1724.method_6063(), mc.field_1724.method_7344().method_7586()));
            }
            List<String> active = new ArrayList<>();
            int visuals = 0;
            for (Module m : visible()) {
                if (m.isEnabled() && m.isToggleable()) {
                    if (visual(m)) {
                        visuals++;
                    } else {
                        active.add(label(m));
                    }
                }
            }
            sb.append("Active: ").append(active.isEmpty() ? "nothing" : String.join(", ", active));
            if (visuals > 0) {
                sb.append(" (+").append(visuals).append(" visual/client)");
            }
            sb.append('\n');
            List<String> log = Notifications.recent();
            if (!log.isEmpty()) {
                sb.append("Last notification: ").append(log.get(log.size() - 1)).append('\n');
            }
            sb.append("Game running ").append(duration(Instant.now().getEpochSecond() - GAME_STARTED)).append(" · ").append(mc.method_47599()).append(" FPS");
            return sb.toString();
        });
    }

    String modules() {
        return onGame(() -> {
            List<String> active = new ArrayList<>();
            List<String> visuals = new ArrayList<>();
            for (Module m : visible()) {
                if (m.isEnabled() && m.isToggleable()) {
                    (visual(m) ? visuals : active).add(label(m));
                }
            }
            return "**Active (" + active.size() + "):** " + (active.isEmpty() ? "nothing" : String.join(", ", active))
                    + "\n**Visual / client (" + visuals.size() + "):** " + (visuals.isEmpty() ? "nothing" : String.join(", ", visuals));
        });
    }

    String log() {
        List<String> log = Notifications.recent();
        if (log.isEmpty()) {
            return "No notifications yet.";
        }
        List<String> last = log.subList(Math.max(0, log.size() - 12), log.size());
        return "**Last notifications:**\n" + String.join("\n", last);
    }

    String stop() {
        if (!this.bot.allowControl()) {
            return "Control is turned off in the Discord Bot settings.";
        }
        return onGame(() -> {
            List<String> off = new ArrayList<>();
            for (Module m : visible()) {
                if (m != this.bot && m.isEnabled() && m.isToggleable() && !visual(m)) {
                    m.setEnabled(false);
                    off.add(m.name());
                }
            }
            mc.field_1690.field_1894.method_23481(false);
            mc.field_1690.field_1881.method_23481(false);
            mc.field_1690.field_1913.method_23481(false);
            mc.field_1690.field_1849.method_23481(false);
            mc.field_1690.field_1903.method_23481(false);
            mc.field_1690.field_1832.method_23481(false);
            mc.field_1690.field_1867.method_23481(false);
            mc.field_1690.field_1886.method_23481(false);
            mc.field_1690.field_1904.method_23481(false);
            Notifications.warn("Discord Bot", "Stopped from Discord");
            return off.isEmpty() ? "Nothing was running. Keys released." : "Stopped: " + String.join(", ", off) + ". Keys released.";
        });
    }

    String toggle(String name) {
        if (!this.bot.allowControl()) {
            return "Control is turned off in the Discord Bot settings.";
        }
        if (name.isBlank()) {
            return "Which module? Example: `toggle AutoBuild`";
        }
        return onGame(() -> {
            String n = norm(name);
            Module found = null;
            for (Module m : visible()) {
                if (norm(m.name()).equals(n)) {
                    found = m;
                    break;
                }
            }
            if (found == null) {
                for (Module m : visible()) {
                    if (norm(m.name()).contains(n)) {
                        if (found != null) {
                            return "More than one module matches `" + name + "`, write more of the name.";
                        }
                        found = m;
                    }
                }
            }
            if (found == null) {
                return "No module called `" + name + "`.";
            }
            if (found == this.bot) {
                return "The bot can not turn itself off from Discord.";
            }
            if (found.isToggleable()) {
                found.setEnabled(!found.isEnabled());
                return found.name() + " is now " + (found.isEnabled() ? "on" : "off") + ".";
            }
            if (found.isActionModule()) {
                if (mc.field_1724 == null) {
                    return found.name() + " needs a world.";
                }
                found.onAction();
                return "Ran " + found.name() + ".";
            }
            return found.name() + " can not be toggled.";
        });
    }

    String say(String text) {
        if (!this.bot.allowSay()) {
            return "Say is turned off in the Discord Bot settings.";
        }
        if (text.isBlank()) {
            return "What should I say? Example: `say hi`";
        }
        String clean = text.replace('\n', ' ').trim();
        if (clean.length() > 256) {
            return "Too long (Minecraft allows 256 characters).";
        }
        return onGame(() -> {
            class_634 net = mc.method_1562();
            if (net == null || mc.field_1724 == null) {
                return "Not on a server.";
            }
            if (clean.startsWith("/")) {
                net.method_45730(clean.substring(1));
                return "Ran `" + clean + "`.";
            }
            net.method_45729(clean);
            return "Sent: " + clean;
        });
    }

    String disconnect() {
        if (!this.bot.allowControl()) {
            return "Control is turned off in the Discord Bot settings.";
        }
        return onGame(() -> {
            class_634 net = mc.method_1562();
            if (net == null) {
                return "Not on a server.";
            }
            String where = where();
            net.method_48296().method_10747(class_2561.method_43470("Disconnected from Discord"));
            return "Left. (" + where + ")";
        });
    }
}
