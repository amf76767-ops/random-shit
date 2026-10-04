package dev.dihclient.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.dihclient.DIHClient;
import dev.dihclient.util.Notifications;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;

/** The saved logins and the ways to add one: Microsoft (code in the browser), cracked name, session token, refresh token. */
public final class AccountManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final List<Account> accounts = new ArrayList<>();
    private volatile String status = "";
    private volatile boolean busy;
    private volatile AuthApi.DeviceCode pendingCode;
    private Thread deviceThread;
    private static AccountManager instance;

    public AccountManager() {
        this.load();
    }

    public static synchronized AccountManager get() {
        if (instance == null) {
            instance = new AccountManager();
        }
        return instance;
    }

    public List<Account> accounts() {
        return this.accounts;
    }

    public String status() {
        return this.status;
    }

    public boolean busy() {
        return this.busy;
    }

    public AuthApi.DeviceCode pendingCode() {
        return this.pendingCode;
    }

    private static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve("accounts.json");
    }

    public void remove(Account account) {
        this.accounts.remove(account);
        this.save();
    }

    public void add(Account account) {
        for (int i = 0; i < this.accounts.size(); i++) {
            Account old = this.accounts.get(i);
            if (old.uuid.equals(account.uuid) && old.kind == account.kind) {
                this.accounts.set(i, account);
                this.save();
                return;
            }
        }
        this.accounts.add(account);
        this.save();
    }

    /** Logs in a saved account again (renews its token). */
    public void login(Account account) {
        if (this.busy) {
            return;
        }
        this.busy = true;
        this.status = "Logging in as " + account.name + " …";
        run("DIH-Account-Login", () -> {
            try {
                AuthApi.Result result = account.resolve();
                account.apply(result);
                this.save();
                this.applyOnClient(result, account);
            } catch (AuthApi.AuthException e) {
                this.fail(e.getMessage());
            }
        });
    }

    /** Cracked login: only a name, nothing to check online. */
    public void addOffline(String name) {
        if (this.busy) {
            return;
        }
        Account account = Account.offline(name);
        this.add(account);
        this.busy = true;
        this.status = "Logging in as " + name + " …";
        run("DIH-Account-Offline", () -> {
            try {
                this.applyOnClient(account.resolve(), account);
            } catch (AuthApi.AuthException e) {
                this.fail(e.getMessage());
            }
        });
    }

    public void addFromToken(Account.Kind kind, String token) {
        if (this.busy || token == null || token.isBlank()) {
            return;
        }
        this.busy = true;
        this.status = "Checking token …";
        run("DIH-Account-Token", () -> {
            try {
                AuthApi.Result result = kind == Account.Kind.SESSION ? AuthApi.loginWithSessionToken(token) : AuthApi.loginWithRefreshToken(token);
                Account account = Account.fromResult(kind, result);
                this.add(account);
                this.applyOnClient(result, account);
            } catch (AuthApi.AuthException e) {
                this.fail(e.getMessage());
            }
        });
    }

    /** Microsoft device-code login: the player enters a code on microsoft.com/link, no password goes through the game. */
    public void startMicrosoft() {
        if (this.busy) {
            return;
        }
        this.busy = true;
        this.pendingCode = null;
        this.status = "Contacting Microsoft …";
        this.deviceThread = new Thread(() -> {
            try {
                AuthApi.DeviceCode code = AuthApi.startDeviceCode();
                this.pendingCode = code;
                this.status = "Enter code " + code.userCode() + " at " + code.url();
                long until = System.currentTimeMillis() + 900_000L;
                while (System.currentTimeMillis() < until && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(Math.max(1, code.interval()) * 1000L);
                    try {
                        AuthApi.Result result = AuthApi.pollDeviceCode(code.deviceCode());
                        Account account = Account.fromResult(Account.Kind.MICROSOFT, result);
                        this.add(account);
                        this.applyOnClient(result, account);
                        return;
                    } catch (AuthApi.AuthException e) {
                        if (!e.pending) {
                            this.fail(e.getMessage());
                            return;
                        }
                    }
                }
                this.status = "Login timed out";
            } catch (InterruptedException e) {
                // cancelled by the player, cancel() set the status
            } catch (Exception e) {
                this.status = "Failed: " + e.getMessage();
            } finally {
                this.busy = false;
                this.pendingCode = null;
            }
        }, "DIH-Account-MS");
        this.deviceThread.setDaemon(true);
        this.deviceThread.start();
    }

    public void cancel() {
        if (this.deviceThread != null) {
            this.deviceThread.interrupt();
        }
        this.busy = false;
        this.pendingCode = null;
        this.status = "Cancelled";
    }

    /** Back to the account the game was started with. */
    public void restoreOriginal() {
        String problem = SessionApplier.restore();
        this.status = problem == null ? "Back on the original account" : problem;
    }

    private void run(String name, Runnable body) {
        Thread thread = new Thread(() -> {
            try {
                body.run();
            } finally {
                this.busy = false;
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
    }

    private void fail(String message) {
        this.status = "Failed: " + message;
        Notifications.error("Account", message);
    }

    private void applyOnClient(AuthApi.Result result, Account account) {
        class_310.method_1551().execute(() -> {
            String problem = SessionApplier.apply(result.name(), result.uuid(), result.mcToken(), result.xuid(), account.kind == Account.Kind.OFFLINE);
            if (problem == null) {
                this.status = "Logged in as " + result.name() + " (" + account.label() + ")";
                Notifications.info("Account", "Now playing as " + result.name());
            } else {
                this.status = problem;
                Notifications.warn("Account", problem);
            }
        });
    }

    public void load() {
        this.accounts.clear();
        try {
            Path file = file();
            if (Files.exists(file)) {
                JsonElement root = JsonParser.parseString(Files.readString(file));
                JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("accounts");
                if (array != null) {
                    for (JsonElement entry : array) {
                        try {
                            this.accounts.add(Account.fromJson(entry.getAsJsonObject()));
                        } catch (Exception ignored) {
                            // skips an entry it cannot read
                        }
                    }
                }
            }
        } catch (Exception e) {
            DIHClient.LOG.warn("[DIHClient] could not read accounts", e);
        }
    }

    public void save() {
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            JsonArray array = new JsonArray();
            for (Account account : this.accounts) {
                array.add(account.toJson());
            }
            Files.writeString(file, GSON.toJson(array));
        } catch (Exception e) {
            DIHClient.LOG.warn("[DIHClient] could not save accounts", e);
        }
    }
}
