package com.knapp.weapons;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KnappWeapons - put any item in the /kweapons menu, give it a custom right-click ability,
 * tune the cooldown, and rename it with color codes.
 *
 * Note: this plugin never sends action bar messages (no text above the hotbar).
 */
public final class KnappWeapons extends JavaPlugin implements Listener, CommandExecutor {

    // ------------------------------------------------------------------ tweakables

    /** Multiplies all ability damage. 1.0 = default. */
    private static final double DAMAGE_MULTIPLIER = 1.0;
    /** Longest cooldown you can set in the menu (300 seconds). */
    private static final int MAX_COOLDOWN_TICKS = 20 * 300;

    // ------------------------------------------------------------------ menu layout

    // main menu (27 slots)
    private static final int ITEM_SLOT = 13;
    private static final int MAIN_ABILITY_BTN = 11;
    private static final int MAIN_RENAME_BTN = 15;
    private static final int MAIN_LORE_BTN = 22;

    /** Default message sent in chat when an ability is used while on cooldown. %time% = time left. */
    private static final String DEFAULT_CD_MESSAGE = "&cAbility on cooldown! &7(&e%time%&7)";

    // lore menu (54 slots)
    private static final int[] LORE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int LORE_PREVIEW = 4;
    private static final int LORE_BACK = 45;
    private static final int LORE_ADD = 49;
    private static final int LORE_CLEAR = 51;
    private static final int LORE_HELP = 53;

    // ability menu (54 slots)
    private static final int[] ICON_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
    private static final int AB_PREVIEW = 4;
    private static final int CD_MINUS_5 = 37;
    private static final int CD_MINUS_1 = 38;
    private static final int CD_SHOW = 40;
    private static final int CD_PLUS_1 = 42;
    private static final int CD_PLUS_5 = 43;
    private static final int AB_BACK = 45;
    private static final int AB_REMOVE = 49;
    private static final int AB_CD_MSG = 31;

    // rename menu (27 slots)
    private static final int REN_PREVIEW = 4;
    private static final int REN_TYPE = 11;
    private static final int REN_HELP = 13;
    private static final int REN_RESET = 15;
    private static final int REN_BACK = 22;

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    // ------------------------------------------------------------------ abilities

    enum Ability {
        INSTANT_TRANSMISSION("Instant Transmission", Material.ENDER_PEARL, 40,
                "Teleport 8 blocks ahead of you", "and gain a short speed boost."),
        WITHER_IMPACT("Wither Impact", Material.WITHER_SKELETON_SKULL, 100,
                "Teleport 10 blocks ahead and", "explode, hurting nearby enemies.", "Grants a short absorption shield."),
        LEAP("Leap", Material.SLIME_BALL, 60,
                "Leap into the air. On landing,", "damage and slow nearby enemies."),
        GIANTS_SLAM("Giant's Slam", Material.ANVIL, 200,
                "Slam the ground 5 blocks ahead,", "damaging and launching enemies."),
        ICE_BOLT("Ice Bolt", Material.BLUE_ICE, 30,
                "Shoot a bolt of ice that damages", "and freezes the first enemy it hits."),
        WITHER_CLOAK("Wither Cloak", Material.NETHER_STAR, 200,
                "Become immune to damage for 5s", "while a cloak of souls hurts", "nearby enemies."),
        INSTANT_HEAL("Instant Heal", Material.GOLDEN_APPLE, 100,
                "Heal yourself and nearby players."),
        SOUL_ARC("Soul Arc", Material.SOUL_LANTERN, 40,
                "Fire a piercing beam of souls that", "hits every enemy in a line."),
        FIRE_VEIL("Fire Veil", Material.BLAZE_POWDER, 160,
                "Surround yourself with a ring of", "fire that burns enemies for 5s."),
        LIGHTNING("Thunder Strike", Material.LIGHTNING_ROD, 80,
                "Call down lightning where you", "look, shocking nearby enemies."),
        GRAPPLE("Grapple", Material.LEAD, 40,
                "Pull yourself toward the block", "you are looking at.");

        final String display;
        final Material icon;
        final int defaultTicks;
        final String[] description;

        Ability(String display, Material icon, int defaultTicks, String... description) {
            this.display = display;
            this.icon = icon;
            this.defaultTicks = defaultTicks;
            this.description = description;
        }

        static Ability byId(String id) {
            if (id == null) return null;
            try {
                return valueOf(id);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ state

    enum Menu {MAIN, ABILITY, RENAME, LORE}

    /** What the player is currently typing in chat. */
    enum Input {RENAME, LORE_ADD, LORE_EDIT, COOLDOWN_MSG}

    static final class MenuHolder implements InventoryHolder {
        final Menu type;
        Inventory inventory;

        MenuHolder(Menu type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static final class Session {
        ItemStack item;
        boolean transition;
        volatile Input awaiting;
        int editIndex;
        BukkitTask timeout;
    }

    private NamespacedKey abilityKey;
    private NamespacedKey cooldownKey;
    private NamespacedKey uidKey;
    private NamespacedKey loreKey;
    private NamespacedKey cdMsgKey;
    private final Map<UUID, Long> lastCdMessage = new HashMap<>();

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> cloakUntil = new HashMap<>();
    private final Map<UUID, Long> noFall = new HashMap<>();

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        abilityKey = new NamespacedKey(this, "ability");
        cooldownKey = new NamespacedKey(this, "cooldown_ticks");
        uidKey = new NamespacedKey(this, "item_uid");
        loreKey = new NamespacedKey(this, "ability_lore_lines");
        cdMsgKey = new NamespacedKey(this, "cooldown_message");

        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("kweapons");
        if (cmd != null) cmd.setExecutor(this);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sessions.containsKey(p.getUniqueId())) {
                finish(p);
                p.closeInventory();
            }
        }
        sessions.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!p.hasPermission("knappweapons.admin")) {
            p.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }
        if (sessions.containsKey(p.getUniqueId())) {
            finish(p);
            p.closeInventory();
        }
        Session s = new Session();
        sessions.put(p.getUniqueId(), s);
        openMain(p, s);
        return true;
    }

    // ------------------------------------------------------------------ small helpers

    private static Component t(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        if (lore != null && !lore.isEmpty()) meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(" "));
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack normalize(ItemStack item) {
        return (item == null || item.getType().isAir()) ? null : item.clone();
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack left : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private static String fmt(int ticks) {
        if (ticks % 20 == 0) return (ticks / 20) + "s";
        return String.format(Locale.US, "%.1fs", ticks / 20.0);
    }

    private static void play(Location loc, Sound sound, float volume, float pitch) {
        if (loc.getWorld() != null) loc.getWorld().playSound(loc, sound, volume, pitch);
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
    }

    // ------------------------------------------------------------------ item data

    private Ability abilityOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return Ability.byId(item.getItemMeta().getPersistentDataContainer().get(abilityKey, PersistentDataType.STRING));
    }

    private int cooldownOf(ItemStack item, Ability ab) {
        if (item == null || ab == null || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(cooldownKey, PersistentDataType.INTEGER, ab.defaultTicks);
    }

    private void setAbility(ItemStack item, Ability ab) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String current = pdc.get(abilityKey, PersistentDataType.STRING);
        int cd = ab.name().equals(current)
                ? pdc.getOrDefault(cooldownKey, PersistentDataType.INTEGER, ab.defaultTicks)
                : ab.defaultTicks;
        pdc.set(abilityKey, PersistentDataType.STRING, ab.name());
        pdc.set(cooldownKey, PersistentDataType.INTEGER, cd);
        if (!pdc.has(uidKey, PersistentDataType.STRING)) {
            pdc.set(uidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        }
        rebuildLore(meta, ab, cd);
        item.setItemMeta(meta);
    }

    private void setCooldown(ItemStack item, int ticks) {
        Ability ab = abilityOf(item);
        if (ab == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(cooldownKey, PersistentDataType.INTEGER, ticks);
        rebuildLore(meta, ab, ticks);
        item.setItemMeta(meta);
    }

    private void removeAbility(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        rebuildLore(meta, null, 0);
        pdc.remove(abilityKey);
        pdc.remove(cooldownKey);
        item.setItemMeta(meta);
    }

    /** Replaces the lore lines this plugin added earlier (always at the end) with fresh ones. */
    private void rebuildLore(ItemMeta meta, Ability ab, int cdTicks) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        int previous = pdc.getOrDefault(loreKey, PersistentDataType.INTEGER, 0);
        for (int i = 0; i < previous && !lore.isEmpty(); i++) lore.remove(lore.size() - 1);

        if (ab != null) {
            int before = lore.size();
            lore.add(Component.empty());
            lore.add(t("Ability: " + ab.display, NamedTextColor.GOLD)
                    .append(t("  RIGHT CLICK", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)));
            for (String line : ab.description) lore.add(t(line, NamedTextColor.GRAY));
            lore.add(t("Cooldown: ", NamedTextColor.DARK_GRAY).append(t(fmt(cdTicks), NamedTextColor.GREEN)));
            pdc.set(loreKey, PersistentDataType.INTEGER, lore.size() - before);
        } else {
            pdc.remove(loreKey);
        }
        meta.lore(lore.isEmpty() ? null : lore);
    }

    private void renameItem(ItemStack item, String input) {
        ItemMeta meta = item.getItemMeta();
        Component name = LEGACY.deserialize(input)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        meta.displayName(name);
        item.setItemMeta(meta);
    }

    private void resetName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(null);
        item.setItemMeta(meta);
    }

    // ---- editable lore (the ability lines this plugin adds always stay at the end)

    private List<Component> userLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        int tail = meta.getPersistentDataContainer().getOrDefault(loreKey, PersistentDataType.INTEGER, 0);
        int n = Math.max(0, lore.size() - tail);
        return new ArrayList<>(lore.subList(0, n));
    }

    private void setUserLore(ItemStack item, List<Component> user) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        int tail = meta.getPersistentDataContainer().getOrDefault(loreKey, PersistentDataType.INTEGER, 0);
        int n = Math.max(0, lore.size() - tail);
        List<Component> out = new ArrayList<>(user);
        out.addAll(lore.subList(n, lore.size()));
        meta.lore(out.isEmpty() ? null : out);
        item.setItemMeta(meta);
    }

    private Component parseLine(String input) {
        if (input.trim().equalsIgnoreCase("[blank]")) return Component.empty();
        return LEGACY.deserialize(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    // ---- cooldown message (stored on the item; empty string = disabled)

    private String cooldownMessageOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return DEFAULT_CD_MESSAGE;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(cdMsgKey, PersistentDataType.STRING, DEFAULT_CD_MESSAGE);
    }

    private void setCooldownMessage(ItemStack item, String message) {
        ItemMeta meta = item.getItemMeta();
        if (message == null) meta.getPersistentDataContainer().remove(cdMsgKey);
        else meta.getPersistentDataContainer().set(cdMsgKey, PersistentDataType.STRING, message);
        item.setItemMeta(meta);
    }

    // ------------------------------------------------------------------ sessions

    /** Ends a session and gives the item back to the player. */
    private void finish(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        if (s.timeout != null) s.timeout.cancel();
        s.awaiting = null;

        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof MenuHolder h && h.type == Menu.MAIN) {
            s.item = normalize(top.getItem(ITEM_SLOT));
            top.setItem(ITEM_SLOT, null);
        }
        if (s.item != null) {
            giveOrDrop(p, s.item);
            s.item = null;
        }
    }

    private void open(Player p, Session s, Inventory inv) {
        s.transition = true;
        try {
            p.openInventory(inv);
        } finally {
            s.transition = false;
        }
    }

    // ------------------------------------------------------------------ menus

    private void openMain(Player p, Session s) {
        MenuHolder holder = new MenuHolder(Menu.MAIN);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("KnappWeapons"));
        holder.inventory = inv;
        for (int i = 0; i < 27; i++) inv.setItem(i, filler());
        inv.setItem(ITEM_SLOT, s.item == null ? null : s.item.clone());
        renderMainButtons(inv);
        open(p, s, inv);
    }

    private void renderMainButtons(Inventory inv) {
        boolean has = normalize(inv.getItem(ITEM_SLOT)) != null;
        if (has) {
            inv.setItem(MAIN_ABILITY_BTN, icon(Material.NETHER_STAR, t("Ability & Cooldown", NamedTextColor.GOLD),
                    List.of(t("Click to choose an ability", NamedTextColor.GRAY),
                            t("and set its cooldown.", NamedTextColor.GRAY))));
            inv.setItem(MAIN_RENAME_BTN, icon(Material.NAME_TAG, t("Rename Item", NamedTextColor.AQUA),
                    List.of(t("Click to rename with colors,", NamedTextColor.GRAY),
                            t("bold, italic and more.", NamedTextColor.GRAY))));
            inv.setItem(MAIN_LORE_BTN, icon(Material.WRITABLE_BOOK, t("Edit Lore", NamedTextColor.GREEN),
                    List.of(t("Add, edit and remove the lore", NamedTextColor.GRAY),
                            t("lines shown under the item name.", NamedTextColor.GRAY))));
        } else {
            List<Component> hint = List.of(t("Place an item in the center slot first.", NamedTextColor.GRAY));
            inv.setItem(MAIN_ABILITY_BTN, icon(Material.BARRIER, t("Ability & Cooldown", NamedTextColor.DARK_GRAY), hint));
            inv.setItem(MAIN_RENAME_BTN, icon(Material.BARRIER, t("Rename Item", NamedTextColor.DARK_GRAY), hint));
            inv.setItem(MAIN_LORE_BTN, icon(Material.BARRIER, t("Edit Lore", NamedTextColor.DARK_GRAY), hint));
        }
    }

    private void scheduleMainRefresh(Player p, Inventory inv) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (p.getOpenInventory().getTopInventory() == inv) renderMainButtons(inv);
        });
    }

    private void openAbility(Player p, Session s) {
        MenuHolder holder = new MenuHolder(Menu.ABILITY);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Weapon Ability"));
        holder.inventory = inv;
        for (int i = 0; i < 54; i++) inv.setItem(i, filler());
        renderAbility(inv, s);
        open(p, s, inv);
    }

    private void renderAbility(Inventory inv, Session s) {
        inv.setItem(AB_PREVIEW, s.item.clone());
        Ability current = abilityOf(s.item);

        Ability[] all = Ability.values();
        for (int i = 0; i < all.length && i < ICON_SLOTS.length; i++) {
            inv.setItem(ICON_SLOTS[i], abilityIcon(all[i], all[i] == current));
        }

        int cd = cooldownOf(s.item, current);
        if (current != null) {
            inv.setItem(CD_SHOW, icon(Material.CLOCK, t("Cooldown: " + fmt(cd), NamedTextColor.GREEN),
                    List.of(t("Default: " + fmt(current.defaultTicks), NamedTextColor.GRAY))));
        } else {
            inv.setItem(CD_SHOW, icon(Material.CLOCK, t("Cooldown", NamedTextColor.DARK_GRAY),
                    List.of(t("Select an ability first.", NamedTextColor.GRAY))));
        }
        inv.setItem(CD_MINUS_5, cdButton(Material.RED_CONCRETE, "-5s", "5s", "0.5s"));
        inv.setItem(CD_MINUS_1, cdButton(Material.RED_CONCRETE, "-1s", "1s", "0.1s"));
        inv.setItem(CD_PLUS_1, cdButton(Material.LIME_CONCRETE, "+1s", "1s", "0.1s"));
        inv.setItem(CD_PLUS_5, cdButton(Material.LIME_CONCRETE, "+5s", "5s", "0.5s"));

        inv.setItem(AB_BACK, icon(Material.ARROW, t("Back", NamedTextColor.YELLOW), null));
        inv.setItem(AB_REMOVE, icon(Material.BARRIER, t("Remove Ability", NamedTextColor.RED),
                List.of(t("Removes the ability from this item.", NamedTextColor.GRAY))));

        String msg = cooldownMessageOf(s.item);
        List<Component> msgLore = new ArrayList<>();
        msgLore.add(t("Sent in chat when the ability", NamedTextColor.GRAY));
        msgLore.add(t("is used while on cooldown.", NamedTextColor.GRAY));
        msgLore.add(Component.empty());
        msgLore.add(t("Current:", NamedTextColor.DARK_GRAY));
        msgLore.add(msg.isEmpty() ? t("(disabled)", NamedTextColor.RED)
                : LEGACY.deserialize(msg.replace("%time%", "3.2s"))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        msgLore.add(Component.empty());
        msgLore.add(t("Left-click: change message", NamedTextColor.YELLOW));
        msgLore.add(t("Right-click: reset to default", NamedTextColor.YELLOW));
        msgLore.add(t("Use %time% for the time left.", NamedTextColor.DARK_GRAY));
        msgLore.add(t("Type 'off' in chat to disable it.", NamedTextColor.DARK_GRAY));
        inv.setItem(AB_CD_MSG, icon(Material.OAK_SIGN, t("Cooldown Message", NamedTextColor.AQUA), msgLore));
    }

    private ItemStack cdButton(Material mat, String name, String left, String right) {
        NamedTextColor color = mat == Material.RED_CONCRETE ? NamedTextColor.RED : NamedTextColor.GREEN;
        return icon(mat, t(name, color), List.of(
                t("Left-click: " + left, NamedTextColor.GRAY),
                t("Right-click: " + right, NamedTextColor.GRAY)));
    }

    private ItemStack abilityIcon(Ability a, boolean selected) {
        List<Component> lore = new ArrayList<>();
        for (String line : a.description) lore.add(t(line, NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(t("Default cooldown: " + fmt(a.defaultTicks), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(selected ? t("SELECTED", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                : t("Click to apply", NamedTextColor.YELLOW));
        ItemStack it = icon(a.icon, t(a.display, selected ? NamedTextColor.GREEN : NamedTextColor.GOLD), lore);
        if (selected) {
            ItemMeta meta = it.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void openRename(Player p, Session s) {
        MenuHolder holder = new MenuHolder(Menu.RENAME);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Rename Item"));
        holder.inventory = inv;
        for (int i = 0; i < 27; i++) inv.setItem(i, filler());
        renderRename(inv, s);
        open(p, s, inv);
    }

    private void renderRename(Inventory inv, Session s) {
        inv.setItem(REN_PREVIEW, s.item.clone());
        inv.setItem(REN_TYPE, icon(Material.NAME_TAG, t("Type a New Name", NamedTextColor.AQUA),
                List.of(t("Click, then type the name in chat.", NamedTextColor.GRAY),
                        t("Color codes are supported!", NamedTextColor.GRAY))));
        inv.setItem(REN_HELP, helpBook());
        inv.setItem(REN_RESET, icon(Material.REDSTONE, t("Reset Name", NamedTextColor.RED),
                List.of(t("Back to the default item name.", NamedTextColor.GRAY))));
        inv.setItem(REN_BACK, icon(Material.ARROW, t("Back", NamedTextColor.YELLOW), null));
    }

    private void openLore(Player p, Session s) {
        MenuHolder holder = new MenuHolder(Menu.LORE);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Edit Lore"));
        holder.inventory = inv;
        for (int i = 0; i < 54; i++) inv.setItem(i, filler());
        renderLore(inv, s);
        open(p, s, inv);
    }

    private void renderLore(Inventory inv, Session s) {
        inv.setItem(LORE_PREVIEW, s.item.clone());
        for (int slot : LORE_SLOTS) inv.setItem(slot, filler());

        List<Component> lines = userLore(s.item);
        for (int i = 0; i < lines.size() && i < LORE_SLOTS.length; i++) {
            Component line = lines.get(i);
            boolean blank = PlainTextComponentSerializer.plainText().serialize(line).isBlank();
            inv.setItem(LORE_SLOTS[i], icon(Material.PAPER,
                    blank ? t("(blank line)", NamedTextColor.DARK_GRAY) : line,
                    List.of(t("Line " + (i + 1), NamedTextColor.DARK_GRAY),
                            Component.empty(),
                            t("Left-click: edit this line", NamedTextColor.YELLOW),
                            t("Right-click: delete this line", NamedTextColor.RED))));
        }

        boolean full = lines.size() >= LORE_SLOTS.length;
        inv.setItem(LORE_ADD, full
                ? icon(Material.BARRIER, t("Lore is full", NamedTextColor.RED), null)
                : icon(Material.LIME_DYE, t("Add a Line", NamedTextColor.GREEN),
                List.of(t("Click, then type the new line in chat.", NamedTextColor.GRAY),
                        t("Type [blank] for an empty line.", NamedTextColor.GRAY))));
        inv.setItem(LORE_CLEAR, icon(Material.REDSTONE, t("Clear All Lore", NamedTextColor.RED),
                List.of(t("Removes your lore lines.", NamedTextColor.GRAY),
                        t("(The ability text is kept.)", NamedTextColor.DARK_GRAY))));
        inv.setItem(LORE_HELP, helpBook());
        inv.setItem(LORE_BACK, icon(Material.ARROW, t("Back", NamedTextColor.YELLOW), null));
    }

    private ItemStack helpBook() {
        NamedTextColor[] colors = {
                NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_AQUA,
                NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE, NamedTextColor.GOLD, NamedTextColor.GRAY,
                NamedTextColor.DARK_GRAY, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
                NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.WHITE};
        String codes = "0123456789abcdef";

        Component colorLine = Component.empty().decoration(TextDecoration.ITALIC, false);
        for (int i = 0; i < colors.length; i++) {
            colorLine = colorLine.append(Component.text("&" + codes.charAt(i) + " ", colors[i]));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(t("Colors:", NamedTextColor.GRAY));
        lore.add(colorLine);
        lore.add(Component.empty());
        lore.add(t("&l  Bold", NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
        lore.add(t("&o  Italic", NamedTextColor.WHITE).decorate(TextDecoration.ITALIC));
        lore.add(t("&n  Underline", NamedTextColor.WHITE).decorate(TextDecoration.UNDERLINED));
        lore.add(t("&m  Strikethrough", NamedTextColor.WHITE).decorate(TextDecoration.STRIKETHROUGH));
        lore.add(t("&k  Obfuscated", NamedTextColor.WHITE));
        lore.add(t("&r  Reset", NamedTextColor.WHITE));
        lore.add(Component.empty());
        lore.add(t("Hex colors: &#RRGGBB", NamedTextColor.GRAY));
        lore.add(t("Example: &6&lFire &c&oBlade", NamedTextColor.GRAY));
        return icon(Material.BOOK, t("Color Codes", NamedTextColor.YELLOW), lore);
    }

    // ------------------------------------------------------------------ menu events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder h)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            e.setCancelled(true);
            return;
        }

        switch (h.type) {
            case MAIN -> clickMain(e, p, s, top);
            case ABILITY -> {
                e.setCancelled(true);
                clickAbility(e, p, s, top);
            }
            case RENAME -> {
                e.setCancelled(true);
                clickRename(e, p, s, top);
            }
            case LORE -> {
                e.setCancelled(true);
                clickLore(e, p, s, top);
            }
        }
    }

    private void clickLore(InventoryClickEvent e, Player p, Session s, Inventory top) {
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) return;

        if (raw == LORE_BACK) {
            click(p);
            openMain(p, s);
            return;
        }
        if (raw == LORE_ADD) {
            if (userLore(s.item).size() >= LORE_SLOTS.length) return;
            click(p);
            startInput(p, s, Input.LORE_ADD, -1);
            return;
        }
        if (raw == LORE_CLEAR) {
            setUserLore(s.item, new ArrayList<>());
            click(p);
            renderLore(top, s);
            return;
        }
        for (int i = 0; i < LORE_SLOTS.length; i++) {
            if (raw != LORE_SLOTS[i]) continue;
            List<Component> lines = userLore(s.item);
            if (i >= lines.size()) return;
            if (e.isRightClick()) {
                lines.remove(i);
                setUserLore(s.item, lines);
                click(p);
                renderLore(top, s);
            } else {
                click(p);
                startInput(p, s, Input.LORE_EDIT, i);
            }
            return;
        }
    }

    private void clickMain(InventoryClickEvent e, Player p, Session s, Inventory top) {
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }
        int raw = e.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        if (inTop && raw != ITEM_SLOT) {
            e.setCancelled(true);
            ItemStack current = normalize(top.getItem(ITEM_SLOT));
            if (current == null) return;
            if (raw == MAIN_ABILITY_BTN) {
                click(p);
                s.item = current;
                openAbility(p, s);
            } else if (raw == MAIN_RENAME_BTN) {
                click(p);
                s.item = current;
                openRename(p, s);
            } else if (raw == MAIN_LORE_BTN) {
                click(p);
                s.item = current;
                openLore(p, s);
            }
            return;
        }
        // item slot or the player's own inventory: allowed, then refresh the buttons
        scheduleMainRefresh(p, top);
    }

    private void clickAbility(InventoryClickEvent e, Player p, Session s, Inventory top) {
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) return;

        if (raw == AB_BACK) {
            click(p);
            openMain(p, s);
            return;
        }
        if (raw == AB_REMOVE) {
            if (abilityOf(s.item) != null) {
                removeAbility(s.item);
                click(p);
                renderAbility(top, s);
            }
            return;
        }
        if (raw == AB_CD_MSG) {
            click(p);
            if (e.isRightClick()) {
                setCooldownMessage(s.item, null); // back to default
                renderAbility(top, s);
            } else {
                startInput(p, s, Input.COOLDOWN_MSG, -1);
            }
            return;
        }

        Ability[] all = Ability.values();
        for (int i = 0; i < all.length && i < ICON_SLOTS.length; i++) {
            if (raw == ICON_SLOTS[i]) {
                setAbility(s.item, all[i]);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
                renderAbility(top, s);
                return;
            }
        }

        int delta = 0;
        boolean right = e.isRightClick();
        if (raw == CD_MINUS_5) delta = right ? -10 : -100;
        else if (raw == CD_MINUS_1) delta = right ? -2 : -20;
        else if (raw == CD_PLUS_1) delta = right ? 2 : 20;
        else if (raw == CD_PLUS_5) delta = right ? 10 : 100;
        if (delta == 0) return;

        Ability current = abilityOf(s.item);
        if (current == null) return;
        int next = Math.max(0, Math.min(MAX_COOLDOWN_TICKS, cooldownOf(s.item, current) + delta));
        setCooldown(s.item, next);
        click(p);
        renderAbility(top, s);
    }

    private void clickRename(InventoryClickEvent e, Player p, Session s, Inventory top) {
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) return;

        if (raw == REN_BACK) {
            click(p);
            openMain(p, s);
        } else if (raw == REN_RESET) {
            resetName(s.item);
            click(p);
            renderRename(top, s);
        } else if (raw == REN_TYPE) {
            click(p);
            startInput(p, s, Input.RENAME, -1);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder h)) return;
        for (int slot : e.getRawSlots()) {
            if (slot < top.getSize() && !(h.type == Menu.MAIN && slot == ITEM_SLOT)) {
                e.setCancelled(true);
                return;
            }
        }
        if (h.type == Menu.MAIN && e.getWhoClicked() instanceof Player p) scheduleMainRefresh(p, top);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder h)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        if (h.type == Menu.MAIN) s.item = normalize(e.getInventory().getItem(ITEM_SLOT));
        if (s.transition || s.awaiting != null) return;
        finish(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        finish(p);
        String prefix = p.getUniqueId() + ":";
        cooldowns.keySet().removeIf(k -> k.startsWith(prefix));
        cloakUntil.remove(p.getUniqueId());
        noFall.remove(p.getUniqueId());
        lastCdMessage.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ chat input (rename / lore / cooldown message)

    private void startInput(Player p, Session s, Input input, int index) {
        s.awaiting = input;
        s.editIndex = index;
        if (s.timeout != null) s.timeout.cancel();
        s.timeout = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (s.awaiting != null && sessions.get(p.getUniqueId()) == s) {
                finish(p);
                if (p.isOnline()) {
                    p.sendMessage(Component.text("Timed out. Your item was returned.", NamedTextColor.RED));
                }
            }
        }, 20L * 60);

        Bukkit.getScheduler().runTask(this, () -> p.closeInventory());

        String prompt = switch (input) {
            case RENAME -> "Type the new item name in chat";
            case LORE_ADD -> "Type the new lore line in chat";
            case LORE_EDIT -> "Type the new text for lore line " + (index + 1) + " in chat";
            case COOLDOWN_MSG -> "Type the new cooldown message in chat";
        };
        p.sendMessage(Component.text(prompt + ", or type ", NamedTextColor.YELLOW)
                .append(Component.text("cancel", NamedTextColor.RED))
                .append(Component.text(".", NamedTextColor.YELLOW)));
        p.sendMessage(Component.text("Colors: &0-&f   Bold: &l   Italic: &o   Underline: &n   Strike: &m   "
                + "Magic: &k   Reset: &r   Hex: &#RRGGBB", NamedTextColor.GRAY));
        if (input == Input.COOLDOWN_MSG) {
            p.sendMessage(Component.text("Use %time% for the time left. Type 'off' to disable the message, "
                    + "or 'default' to reset it.", NamedTextColor.GRAY));
        } else if (input != Input.RENAME) {
            p.sendMessage(Component.text("Type [blank] for an empty line.", NamedTextColor.GRAY));
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.awaiting == null) return;
        e.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(this, () -> handleInput(p, message));
    }

    private void handleInput(Player p, String message) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.awaiting == null || s.item == null) return;
        Input input = s.awaiting;
        s.awaiting = null;
        if (s.timeout != null) s.timeout.cancel();

        boolean cancel = message.isBlank() || message.trim().equalsIgnoreCase("cancel");

        switch (input) {
            case RENAME -> {
                if (!cancel) renameItem(s.item, message);
                openRename(p, s);
            }
            case LORE_ADD -> {
                if (!cancel) {
                    List<Component> lines = userLore(s.item);
                    if (lines.size() < LORE_SLOTS.length) {
                        lines.add(parseLine(message));
                        setUserLore(s.item, lines);
                    }
                }
                openLore(p, s);
            }
            case LORE_EDIT -> {
                if (!cancel) {
                    List<Component> lines = userLore(s.item);
                    if (s.editIndex >= 0 && s.editIndex < lines.size()) {
                        lines.set(s.editIndex, parseLine(message));
                        setUserLore(s.item, lines);
                    }
                }
                openLore(p, s);
            }
            case COOLDOWN_MSG -> {
                if (!cancel) {
                    String m = message.trim();
                    if (m.equalsIgnoreCase("off")) setCooldownMessage(s.item, "");
                    else if (m.equalsIgnoreCase("default")) setCooldownMessage(s.item, null);
                    else setCooldownMessage(s.item, message);
                }
                openAbility(p, s);
            }
        }
    }

    // ------------------------------------------------------------------ using abilities

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Ability ab = Ability.byId(pdc.get(abilityKey, PersistentDataType.STRING));
        if (ab == null) return;

        Player p = e.getPlayer();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && !p.isSneaking()
                && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) {
            return; // let people open chests, doors, etc.
        }
        if (!p.hasPermission("knappweapons.use")) return;

        e.setCancelled(true);

        int cdTicks = pdc.getOrDefault(cooldownKey, PersistentDataType.INTEGER, ab.defaultTicks);
        String key = p.getUniqueId() + ":" + pdc.getOrDefault(uidKey, PersistentDataType.STRING, "none");
        long now = System.currentTimeMillis();
        Long readyAt = cooldowns.get(key);
        if (readyAt != null && now < readyAt) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.3f, 1.4f);
            sendCooldownMessage(p, pdc.getOrDefault(cdMsgKey, PersistentDataType.STRING, DEFAULT_CD_MESSAGE),
                    readyAt - now, now);
            return;
        }

        if (cast(p, ab)) {
            p.swingMainHand();
            if (cdTicks > 0) cooldowns.put(key, now + cdTicks * 50L);
        }
    }

    /** Chat message shown when an ability is used on cooldown (at most once per second per player). */
    private void sendCooldownMessage(Player p, String template, long remainingMs, long now) {
        if (template.isEmpty()) return; // disabled for this item
        Long last = lastCdMessage.get(p.getUniqueId());
        if (last != null && now - last < 1000L) return;
        lastCdMessage.put(p.getUniqueId(), now);

        String time = String.format(Locale.US, "%.1fs", Math.max(0, remainingMs) / 1000.0);
        p.sendMessage(LEGACY.deserialize(template.replace("%time%", time)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        Long cloak = cloakUntil.get(id);
        if (cloak != null) {
            if (cloak > now) {
                e.setCancelled(true);
                return;
            }
            cloakUntil.remove(id);
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Long until = noFall.get(id);
            if (until != null) {
                if (until > now) e.setCancelled(true);
                else noFall.remove(id);
            }
        }
    }

    private boolean cast(Player p, Ability a) {
        return switch (a) {
            case INSTANT_TRANSMISSION -> instantTransmission(p);
            case WITHER_IMPACT -> witherImpact(p);
            case LEAP -> leap(p);
            case GIANTS_SLAM -> giantsSlam(p);
            case ICE_BOLT -> iceBolt(p);
            case WITHER_CLOAK -> witherCloak(p);
            case INSTANT_HEAL -> instantHeal(p);
            case SOUL_ARC -> soulArc(p);
            case FIRE_VEIL -> fireVeil(p);
            case LIGHTNING -> lightning(p);
            case GRAPPLE -> grapple(p);
        };
    }

    // ------------------------------------------------------------------ ability helpers

    private List<LivingEntity> enemiesNear(Player caster, Location center, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return list;
        for (Entity en : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(en instanceof LivingEntity le)) continue;
            if (le.equals(caster) || le instanceof ArmorStand) continue;
            if (le instanceof Player other) {
                if (!world.getPVP() || other.getGameMode() == GameMode.SPECTATOR) continue;
            }
            if (le.getBoundingBox().getCenter().distanceSquared(center.toVector()) > radius * radius) continue;
            list.add(le);
        }
        return list;
    }

    private void hurt(Player caster, LivingEntity target, double damage) {
        target.damage(damage * DAMAGE_MULTIPLIER, caster);
    }

    private void groundParticles(Location c, int count, double spread) {
        Material m = c.clone().subtract(0, 0.5, 0).getBlock().getType();
        if (m.isAir() || !m.isBlock()) m = Material.DIRT;
        BlockData data = m.createBlockData();
        c.getWorld().spawnParticle(Particle.BLOCK, c, count, spread, 0.1, spread, 0.1, data);
    }

    private boolean isFree(Location l) {
        Block feet = l.getBlock();
        Block head = l.clone().add(0, 1.8, 0).getBlock();
        return feet.isPassable() && head.isPassable() && !feet.isLiquid();
    }

    private boolean teleportForward(Player p, double maxDistance) {
        Location start = p.getLocation();
        Vector dir = p.getEyeLocation().getDirection();
        if (p.isOnGround() && dir.getY() < 0) dir.setY(0);
        if (dir.lengthSquared() < 1e-4) return false;
        dir.normalize();

        Location best = null;
        for (double d = 0.5; d <= maxDistance + 1e-6; d += 0.5) {
            Location t = start.clone().add(dir.clone().multiply(d));
            if (!isFree(t)) break;
            best = t;
        }
        if (best == null) return false;

        World world = start.getWorld();
        world.spawnParticle(Particle.PORTAL, start.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.2);
        best.setYaw(start.getYaw());
        best.setPitch(start.getPitch());
        p.teleport(best);
        world.spawnParticle(Particle.PORTAL, best.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.2);
        play(best, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        return true;
    }

    // ------------------------------------------------------------------ the abilities

    private boolean instantTransmission(Player p) {
        if (!teleportForward(p, 8)) return false;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
        return true;
    }

    private boolean witherImpact(Player p) {
        if (!teleportForward(p, 10)) return false;
        Location l = p.getLocation();
        l.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, l.clone().add(0, 1, 0), 1);
        play(l, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        for (LivingEntity le : enemiesNear(p, l.clone().add(0, 1, 0), 6)) hurt(p, le, 12);
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
        return true;
    }

    private boolean leap(Player p) {
        Vector dir = p.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() > 1e-4) dir.normalize().multiply(1.1);
        else dir.zero();
        dir.setY(0.9);
        p.setVelocity(dir);

        UUID id = p.getUniqueId();
        noFall.put(id, System.currentTimeMillis() + 10_000L);
        play(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 2, 0.2, 0.05, 0.2, 0.01);
                if ((ticks > 4 && p.isOnGround()) || ticks > 100) {
                    landingSlam(p);
                    noFall.put(id, System.currentTimeMillis() + 1000L);
                    cancel();
                }
            }
        }.runTaskTimer(this, 2L, 1L);
        return true;
    }

    private void landingSlam(Player p) {
        Location l = p.getLocation();
        groundParticles(l, 40, 1.5);
        l.getWorld().spawnParticle(Particle.EXPLOSION, l, 1);
        play(l, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
        for (LivingEntity le : enemiesNear(p, l, 4)) {
            hurt(p, le, 8);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 6));
            le.setFreezeTicks(140);
        }
    }

    private boolean giantsSlam(Player p) {
        Location base = p.getLocation();
        World world = base.getWorld();
        Vector dir = base.getDirection();
        dir.setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        dir.normalize();

        Location center = base.clone().add(dir.multiply(5));
        RayTraceResult ground = world.rayTraceBlocks(center.clone().add(0, 3, 0), new Vector(0, -1, 0), 8,
                FluidCollisionMode.NEVER, true);
        if (ground != null && ground.getHitPosition() != null) {
            center = ground.getHitPosition().toLocation(world);
        }
        final Location impact = center;

        play(impact, Sound.BLOCK_ANVIL_LAND, 1f, 0.6f);
        play(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION, impact.clone().add(0, 0.5, 0), 1);

        for (LivingEntity le : enemiesNear(p, impact.clone().add(0, 1, 0), 6)) {
            hurt(p, le, 14);
            le.setVelocity(le.getVelocity().add(new Vector(0, 0.7, 0)));
        }

        new BukkitRunnable() {
            int r = 1;

            @Override
            public void run() {
                if (r > 6) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24;
                    Location ring = impact.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r);
                    groundParticles(ring, 2, 0.1);
                }
                r++;
            }
        }.runTaskTimer(this, 0L, 1L);
        return true;
    }

    private boolean iceBolt(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = eye.getWorld();
        play(eye, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f);

        for (double d = 1; d <= 25; d += 0.5) {
            Location pt = eye.clone().add(dir.clone().multiply(d));
            if (!pt.getBlock().isPassable()) {
                world.spawnParticle(Particle.SNOWFLAKE, pt, 15, 0.2, 0.2, 0.2, 0.05);
                play(pt, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.4f);
                break;
            }
            world.spawnParticle(Particle.SNOWFLAKE, pt, 3, 0.05, 0.05, 0.05, 0.01);
            world.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0);

            List<LivingEntity> hit = enemiesNear(p, pt, 1.0);
            if (!hit.isEmpty()) {
                for (LivingEntity le : hit) {
                    hurt(p, le, 10);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
                    le.setFreezeTicks(200);
                }
                world.spawnParticle(Particle.SNOWFLAKE, pt, 30, 0.4, 0.4, 0.4, 0.1);
                play(pt, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.0f);
                break;
            }
        }
        return true;
    }

    private boolean witherCloak(Player p) {
        UUID id = p.getUniqueId();
        cloakUntil.put(id, System.currentTimeMillis() + 5000L);
        play(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.6f);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= 50) {
                    cloakUntil.remove(id);
                    if (p.isOnline()) play(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 0.6f);
                    cancel();
                    return;
                }
                double base = t * 0.35;
                for (int k = 0; k < 3; k++) {
                    double a = base + k * (Math.PI * 2 / 3);
                    Location l = p.getLocation().add(Math.cos(a) * 1.4, 1.0 + Math.sin(t * 0.2 + k) * 0.4,
                            Math.sin(a) * 1.4);
                    p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, l, 2, 0.05, 0.05, 0.05, 0.01);
                    p.getWorld().spawnParticle(Particle.SMOKE, l, 1, 0.05, 0.05, 0.05, 0.0);
                }
                if (t % 5 == 0) {
                    for (LivingEntity le : enemiesNear(p, p.getLocation().add(0, 1, 0), 3.5)) hurt(p, le, 4);
                }
                t++;
            }
        }.runTaskTimer(this, 0L, 2L);
        return true;
    }

    private void heal(Player target, double amount) {
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20.0 : max.getValue();
        target.setHealth(Math.min(cap, target.getHealth() + amount));
        target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 1.8, 0), 5, 0.4, 0.3, 0.4, 0);
    }

    private boolean instantHeal(Player p) {
        heal(p, 8);
        for (Player other : p.getWorld().getNearbyPlayers(p.getLocation(), 8)) {
            if (!other.equals(p) && other.getGameMode() != GameMode.SPECTATOR) heal(other, 6);
        }
        play(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        return true;
    }

    private boolean soulArc(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vector(1, 0, 0);
        right.normalize();

        World world = eye.getWorld();
        Set<UUID> hit = new HashSet<>();
        play(eye, Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f, 1.4f);

        for (double d = 1; d <= 14; d += 0.5) {
            Location pt = eye.clone().add(dir.clone().multiply(d)).add(0, -0.3, 0);
            if (!pt.getBlock().isPassable()) break;
            Location wave = pt.clone().add(right.clone().multiply(Math.sin(d * 1.2) * 0.5));
            world.spawnParticle(Particle.SOUL, wave, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, wave, 1, 0.05, 0.05, 0.05, 0);
            for (LivingEntity le : enemiesNear(p, pt, 1.2)) {
                if (hit.add(le.getUniqueId())) hurt(p, le, 9);
            }
        }
        return true;
    }

    private boolean fireVeil(Player p) {
        play(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= 50) {
                    cancel();
                    return;
                }
                Location c = p.getLocation();
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24 + t * 0.2;
                    Location l = c.clone().add(Math.cos(a) * 4, 0.15, Math.sin(a) * 4);
                    c.getWorld().spawnParticle(Particle.FLAME, l, 1, 0.05, 0.05, 0.05, 0.0);
                }
                if (t % 5 == 0) {
                    for (LivingEntity le : enemiesNear(p, c.clone().add(0, 1, 0), 4.5)) {
                        hurt(p, le, 3);
                        le.setFireTicks(60);
                    }
                }
                t++;
            }
        }.runTaskTimer(this, 0L, 2L);
        return true;
    }

    private boolean lightning(Player p) {
        Location eye = p.getEyeLocation();
        World world = eye.getWorld();
        Vector dir = eye.getDirection().normalize();

        RayTraceResult hit = world.rayTrace(eye, dir, 30, FluidCollisionMode.NEVER, true, 0.5,
                en -> en instanceof LivingEntity && !en.equals(p));
        Location target;
        if (hit != null && hit.getHitPosition() != null) {
            target = hit.getHitPosition().toLocation(world);
        } else {
            target = eye.clone().add(dir.clone().multiply(30));
            RayTraceResult down = world.rayTraceBlocks(target, new Vector(0, -1, 0), 40, FluidCollisionMode.NEVER, true);
            if (down != null && down.getHitPosition() != null) target = down.getHitPosition().toLocation(world);
        }

        world.strikeLightningEffect(target);
        for (LivingEntity le : enemiesNear(p, target.clone().add(0, 1, 0), 4)) {
            hurt(p, le, 12);
            le.setFireTicks(40);
        }
        return true;
    }

    private boolean grapple(Player p) {
        Location eye = p.getEyeLocation();
        World world = eye.getWorld();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult hit = world.rayTraceBlocks(eye, dir, 40, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitPosition() == null) return false;

        Vector target = hit.getHitPosition();
        Vector from = p.getLocation().toVector().add(new Vector(0, 1, 0));
        Vector pull = target.clone().subtract(from);
        double dist = pull.length();
        if (dist < 2) return false;

        pull.normalize().multiply(Math.min(2.2, 0.6 + dist * 0.07));
        pull.setY(pull.getY() + 0.25);
        p.setVelocity(pull);
        noFall.put(p.getUniqueId(), System.currentTimeMillis() + 5000L);

        Vector step = target.clone().subtract(from).normalize().multiply(0.8);
        Location pt = eye.clone();
        for (double d = 0; d < dist; d += 0.8) {
            world.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0);
            pt.add(step);
        }
        play(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1f);
        return true;
    }
}
