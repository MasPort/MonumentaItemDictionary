package dev.eliux.monumentaitemdictionary.gui;

import com.google.gson.*;
import dev.eliux.monumentaitemdictionary.gui.charm.CharmDictionaryGui;
import dev.eliux.monumentaitemdictionary.gui.charm.CharmFilterGui;
import dev.eliux.monumentaitemdictionary.gui.charm.DictionaryCharm;
import dev.eliux.monumentaitemdictionary.gui.item.DictionaryItem;
import dev.eliux.monumentaitemdictionary.gui.item.ItemDictionaryGui;
import dev.eliux.monumentaitemdictionary.gui.item.ItemFilterGui;
import dev.eliux.monumentaitemdictionary.util.CharmStat;
import dev.eliux.monumentaitemdictionary.util.Filter;
import dev.eliux.monumentaitemdictionary.util.ItemFormatter;
import dev.eliux.monumentaitemdictionary.util.ItemStat;
import dev.eliux.monumentaitemdictionary.web.ItemApiResponse;
import dev.eliux.monumentaitemdictionary.web.WebManager;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class DictionaryController {
    private String itemNameFilter;
    private boolean hasItemNameFilter = false;
    private String charmNameFilter;
    private boolean hasCharmNameFilter = false;
    private ArrayList<Filter> itemFilters = new ArrayList<>();
    private ArrayList<Filter> charmFilters = new ArrayList<>();

    private ArrayList<String> allItemTypes;
    private ArrayList<String> allItemRegions;
    private ArrayList<String> allItemTiers;
    private ArrayList<String> allItemLocations;
    private ArrayList<String> allItemStats;
    private ArrayList<String> allItemBaseItems;

    private ArrayList<String> allCharmRegions;
    private ArrayList<String> allCharmTiers;
    private ArrayList<String> allCharmLocations;
    private ArrayList<String> allCharmSkillMods;
    private ArrayList<String> allCharmClasses;
    private ArrayList<String> allCharmStats;
    private ArrayList<String> allCharmBaseItems;

    private final ArrayList<DictionaryItem> items;
    private ArrayList<DictionaryItem> validItems;
    private final ArrayList<DictionaryCharm> charms;
    private ArrayList<DictionaryCharm> validCharms;

    private @Nullable CompletableFuture<ItemApiResponse> itemResponseFuture = null;

    public boolean itemLoadFailed = false;
    public boolean charmLoadFailed = false;

    public ItemDictionaryGui itemGui;
    public boolean itemGuiPreviouslyOpened = false;
    public ItemFilterGui itemFilterGui;
    public boolean itemFilterGuiPreviouslyOpened = false;
    public CharmDictionaryGui charmGui;
    public boolean charmGuiPreviouslyOpened = false;
    public CharmFilterGui charmFilterGui;
    public boolean charmFilterGuiPreviouslyOpened = false;

    public DictionaryController() {
        items = new ArrayList<>();
        validItems = new ArrayList<>();
        charms = new ArrayList<>();
        validCharms = new ArrayList<>();

        loadItems();
        loadCharms();

        itemGui = new ItemDictionaryGui(Text.literal("Monumenta Item Dictionary"), this);
        itemFilterGui = new ItemFilterGui(Text.literal("Item Filter Menu"), this);
        charmGui = new CharmDictionaryGui(Text.literal("Monumenta Charm Dictionary"), this);
        charmFilterGui = new CharmFilterGui(Text.literal("Charm Filter Menu"), this);
    }

    public void tick() {
        if (itemResponseFuture != null) {
            try {
                // Process remaining item API response code on main thread
                ItemApiResponse response = itemResponseFuture.join();
                itemResponseFuture = null;

                loadItems();
                itemGui.buildItemList();
                loadCharms();
                charmGui.buildCharmList();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void open() {
        setItemDictionaryScreen();
    }

    public void setItemDictionaryScreen() {
        MinecraftClient.getInstance().setScreen(itemGui);
        if (!itemGuiPreviouslyOpened) {
            itemGui.postInit();
            itemGuiPreviouslyOpened = true;
        } else {
            itemGui.updateGuiPositions();
        }
    }

    public void setItemFilterScreen() {
        MinecraftClient.getInstance().setScreen(itemFilterGui);
        if (!itemFilterGuiPreviouslyOpened) {
            itemFilterGui.postInit();
            itemFilterGuiPreviouslyOpened = true;
        } else {
            //filterGui.updateGuiPositions();
        }
    }

    public void setCharmDictionaryScreen() {
        MinecraftClient.getInstance().setScreen(charmGui);
        if (!charmGuiPreviouslyOpened) {
            charmGui.postInit();
            charmGuiPreviouslyOpened = true;
        } else {
            charmGui.updateGuiPositions();
        }
    }

    public void setCharmFilterScreen() {
        MinecraftClient.getInstance().setScreen(charmFilterGui);
        if (!charmFilterGuiPreviouslyOpened) {
            charmFilterGui.postInit();
            charmFilterGuiPreviouslyOpened = true;
        } else {
            //charmFilterGui.updateGuiPositions();
        }
    }

    private String readItemData() {
        try {
            return Files.readString(Path.of("config/mid/items.json"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "{}";
    }

    private void writeItemData(String writeData) {
        try {
            File targetFile = new File("config/mid/items.json");

            targetFile.getParentFile().mkdirs();
            targetFile.createNewFile();

            FileUtils.writeStringToFile(targetFile, writeData, Charset.defaultCharset());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void requestAndUpdate() {
        if (itemResponseFuture == null) {
            itemResponseFuture = new CompletableFuture<>();
            // Process remaining item API response code on new thread to prevent lag spike
            new Thread(() -> {
                try {
                    ItemApiResponse response = new ItemApiResponse();
                    response.itemsData = WebManager.getRequest("https://api.playmonumenta.com/items");
                    writeItemData(response.itemsData);
                    itemResponseFuture.complete(response);
                } catch (IOException e) {
                    itemResponseFuture.completeExceptionally(e);
                }
            }).start();
        }
    }

    public void loadItems() {
        allItemTypes = new ArrayList<>();
        allItemRegions = new ArrayList<>();
        allItemTiers = new ArrayList<>();
        allItemLocations = new ArrayList<>();
        allItemStats = new ArrayList<>();
        allItemBaseItems = new ArrayList<>();

        try {
            String rawData = readItemData();

            items.clear();
            JsonObject data = new Gson().fromJson(rawData, JsonObject.class);
            for (JsonElement itemElement : data.asMap().values()) {
                JsonObject itemData = (JsonObject) itemElement;

                // Construct item information
                String itemType = itemData.get("type").getAsString();
                if (itemType.equals("Charm"))
                    continue;
                if (!allItemTypes.contains(itemType))
                    allItemTypes.add(itemType);

                String itemName = itemData.get("name").getAsString();

                String itemRegion = "";
                boolean hasRegion = false;
                JsonPrimitive regionPrimitive = itemData.getAsJsonPrimitive("region");
                if (regionPrimitive != null) {
                    itemRegion = regionPrimitive.getAsString();
                    hasRegion = true;

                    if (!allItemRegions.contains(itemRegion))
                        allItemRegions.add(itemRegion);
                }

                String itemTier = "";
                boolean hasTier = false;
                JsonPrimitive tierPrimitive = itemData.getAsJsonPrimitive("tier");
                if (tierPrimitive != null) {
                    List<String> plainSplit = Arrays.asList(tierPrimitive.getAsString().replace("_", " ").split(" ")); // janky code to patch Event Currency appearing as Event_currency and other future similar events
                    StringBuilder formattedSplit = new StringBuilder();
                    for (String s : plainSplit) {
                        if (s.length() > 0)
                            formattedSplit.append(s.substring(0, 1).toUpperCase()).append(s.substring(1).toLowerCase());
                        if (plainSplit.indexOf(s) != plainSplit.size() - 1)
                            formattedSplit.append(" ");
                    }
                    itemTier = formattedSplit.toString();
                    hasTier = true;

                    if (!allItemTiers.contains(itemTier))
                        allItemTiers.add(itemTier);
                }

                String itemLocation = "";
                boolean hasLocation = false;
                JsonPrimitive locationPrimitive = itemData.getAsJsonPrimitive("location");
                if (locationPrimitive != null) {
                    itemLocation = locationPrimitive.getAsString();
                    hasLocation = true;

                    if (!allItemLocations.contains(itemLocation))
                        allItemLocations.add(itemLocation);
                }

                int fishTier = -1;
                boolean isFish = false;
                JsonPrimitive fishQualityPrimitive = itemData.getAsJsonPrimitive("fish_quality");
                if (fishQualityPrimitive != null) {
                    fishTier = fishQualityPrimitive.getAsInt();
                    isFish = true;


                }

                String itemBaseItem = itemData.get("base_item").getAsString();
                if (!allItemBaseItems.contains(itemBaseItem))
                    allItemBaseItems.add(itemBaseItem);

                String itemLore = "";
                JsonPrimitive lorePrimitive = itemData.getAsJsonPrimitive("lore");
                if (lorePrimitive != null) {
                    itemLore = lorePrimitive.getAsString();
                }

                ArrayList<ItemStat> itemStats = new ArrayList<>();
                JsonObject statObject = itemData.get("stats").getAsJsonObject();
                for (Map.Entry<String, JsonElement> statEntry : statObject.entrySet()) {
                    String statKey = statEntry.getKey();
                    if (ItemFormatter.isHiddenStat(statKey)) continue;

                    itemStats.add(new ItemStat(statKey, statEntry.getValue().getAsDouble()));

                    if (!allItemStats.contains(statKey))
                        allItemStats.add(statKey);
                }

                // Build the item
                JsonPrimitive masterworkPrimitive = itemData.getAsJsonPrimitive("masterwork");
                if (masterworkPrimitive != null) {
                    boolean hasItem = false;
                    for (DictionaryItem dictionaryItem : items) {
                        if (dictionaryItem.name.equals(itemName)) {
                            hasItem = true;
                            dictionaryItem.addMasterworkTier(itemStats, masterworkPrimitive.getAsInt());
                        }
                    }
                    if (!hasItem) {
                        ArrayList<ArrayList<ItemStat>> totalList = new ArrayList<>();
                        for (int i = 0; i < ItemFormatter.getMasterworkForRarity(itemTier) + 1; i ++)
                            totalList.add(null);
                        totalList.set(masterworkPrimitive.getAsInt(), itemStats);
                        items.add(new DictionaryItem(itemName, itemType, itemRegion, itemTier, itemLocation, fishTier, isFish, itemBaseItem, itemLore, totalList, true));
                    }
                } else {
                    ArrayList<ArrayList<ItemStat>> totalList = new ArrayList<>();
                    totalList.add(itemStats);
                    items.add(new DictionaryItem(itemName, itemType, itemRegion, itemTier, itemLocation, fishTier, isFish, itemBaseItem, itemLore, totalList, false));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            itemLoadFailed = true;
        }

        items.sort(DictionaryItem::compareTo);
    }

    public void loadCharms() {
        allCharmRegions = new ArrayList<>();
        allCharmTiers = new ArrayList<>();
        allCharmLocations = new ArrayList<>();
        allCharmSkillMods = new ArrayList<>();
        allCharmClasses = new ArrayList<>();
        allCharmStats = new ArrayList<>();
        allCharmBaseItems = new ArrayList<>();

        try {
            String rawData = readItemData();

            charms.clear();
            JsonObject data = new Gson().fromJson(rawData, JsonObject.class);
            for (JsonElement charmElement : data.asMap().values()) {
                JsonObject charmData = (JsonObject) charmElement;

                // Construct charm information
                if (!charmData.get("type").getAsString().equals("Charm"))
                    continue;

                String charmName = charmData.get("name").getAsString();

                String charmRegion = "Architect's Ring";
                if (!allCharmRegions.contains(charmRegion))
                    allCharmRegions.add(charmRegion);

                String charmLocation = charmData.get("location").getAsString();
                if (!allCharmLocations.contains(charmLocation))
                    allCharmLocations.add(charmLocation);

                String charmTier = charmData.get("tier").getAsString().replace("_", " ");
                if (!allCharmTiers.contains(charmTier))
                    allCharmTiers.add(charmTier);

                int charmPower = charmData.get("power").getAsInt();

                String charmClass = charmData.get("class_name").getAsString();
                if (!allCharmClasses.contains(charmClass))
                    allCharmClasses.add(charmClass);

                String charmBaseItem = charmData.get("base_item").getAsString();
                if (!allCharmBaseItems.contains(charmBaseItem))
                    allCharmBaseItems.add(charmBaseItem);

                ArrayList<CharmStat> charmStats = new ArrayList<>();
                JsonObject statObject = charmData.get("stats").getAsJsonObject();
                for (Map.Entry<String, JsonElement> statEntry : statObject.entrySet()) {
                    String statKey = statEntry.getKey();
                    String skillMod = ItemFormatter.getSkillFromCharmStat(statKey);
                    if (!allCharmSkillMods.contains(skillMod))
                        allCharmSkillMods.add(skillMod);

                    charmStats.add(new CharmStat(statKey, skillMod, statEntry.getValue().getAsDouble()));

                    if (!allCharmStats.contains(statKey))
                        allCharmStats.add(statKey);
                }

                charms.add(new DictionaryCharm(charmName, charmRegion, charmLocation, charmTier, charmPower, charmClass, charmBaseItem, charmStats));
            }
        } catch (Exception e) {
            e.printStackTrace();
            charmLoadFailed = true;
        }

        charms.sort((o1, o2) -> {
            if (!o1.tier.equals(o2.tier)) {
                return -(ItemFormatter.getNumberForTier(o1.tier) - ItemFormatter.getNumberForTier(o2.tier));
            }
            return 0;
        });
    }

    public ArrayList<String> getAllItemTypes() {
        return allItemTypes;
    }

    public ArrayList<String> getAllItemRegions() {
        return allItemRegions;
    }

    public ArrayList<String> getAllItemTiers() {
        return allItemTiers;
    }

    public ArrayList<String> getAllItemLocations() {
        return allItemLocations;
    }

    public ArrayList<String> getAllItemStats() {
        return allItemStats;
    }

    public ArrayList<String> getAllItemBaseItems() {
        return allItemBaseItems;
    }

    public void setItemNameFilter(String nameFilter) {
        this.itemNameFilter = nameFilter;
        hasItemNameFilter = true;
    }

    public void clearItemNameFilter() {
        hasItemNameFilter = false;
    }

    public void updateItemFilters(ArrayList<Filter> filters) {
        itemFilters = new ArrayList<>(filters);
    }

    public void resetItemFilters() {
        itemFilters = new ArrayList<>();
    }

    public ArrayList<String> getAllCharmRegions() {
        return allCharmRegions;
    }

    public ArrayList<String> getAllCharmTiers() {
        return allCharmTiers;
    }

    public ArrayList<String> getAllCharmLocations() {
        return allCharmLocations;
    }

    public ArrayList<String> getAllCharmSkillMods() {
        return allCharmSkillMods;
    }

    public ArrayList<String> getAllCharmClasses() {
        return allCharmClasses;
    }

    public ArrayList<String> getAllCharmStats() {
        return allCharmStats;
    }

    public ArrayList<String> getAllCharmBaseItems() {
        return allCharmBaseItems;
    }

    public void setCharmNameFilter(String nameFilter) {
        this.charmNameFilter = nameFilter;
        hasCharmNameFilter = true;
    }

    public void clearCharmNameFilter() {
        hasCharmNameFilter = false;
    }

    public void updateCharmFilters(ArrayList<Filter> filters) {
        charmFilters = new ArrayList<>(filters);
    }

    public void resetCharmFilters() {
        charmFilters = new ArrayList<>();
    }

    public void refreshItems() {
        ArrayList<DictionaryItem> filteredItems = new ArrayList<>(items);

        for (Filter filter : itemFilters) {
            if (filter != null) {
                if (filter.getOption().equals("Stat")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredItems.removeIf(i -> !i.hasStat(filter.value));
                            case 1 -> filteredItems.removeIf(i -> i.hasStat(filter.value));
                            case 2 -> filteredItems.removeIf(i -> !i.hasStat(filter.value) || !(i.getStat(filter.value) >= filter.constant));
                            case 3 -> filteredItems.removeIf(i -> !i.hasStat(filter.value) || !(i.getStat(filter.value) > filter.constant));
                            case 4 -> filteredItems.removeIf(i -> !i.hasStat(filter.value) || !(i.getStat(filter.value) == filter.constant));
                            case 5 -> filteredItems.removeIf(i -> !i.hasStat(filter.value) || !(i.getStat(filter.value) <= filter.constant));
                            case 6 -> filteredItems.removeIf(i -> !i.hasStat(filter.value) || !(i.getStat(filter.value) < filter.constant));
                        }
                    }
                } else if (filter.getOption().equals("Tier")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredItems.removeIf(i -> !i.hasTier() || !i.tier.equals(filter.value));
                            case 1 -> filteredItems.removeIf(i -> i.hasTier() && i.tier.equals(filter.value));
                        }
                    }
                } else if (filter.getOption().equals("Region")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredItems.removeIf(i -> !i.hasRegion() || !i.region.equals(filter.value));
                            case 1 -> filteredItems.removeIf(i -> i.hasRegion() && i.region.equals(filter.value));
                        }
                    }
                } else if (filter.getOption().equals("Type")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredItems.removeIf(i -> !i.type.equals(filter.value));
                            case 1 -> filteredItems.removeIf(i -> i.type.equals(filter.value));
                        }
                    }
                } else if (filter.getOption().equals("Location")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredItems.removeIf(i -> !i.hasLocation() || !i.location.equals(filter.value));
                            case 1 -> filteredItems.removeIf(i -> i.hasLocation() && i.location.equals(filter.value));
                        }
                    }
                } else if (filter.getOption().equals("Base Item")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredItems.removeIf(i -> !i.baseItem.equals(filter.value));
                            case 1 -> filteredItems.removeIf(i -> i.baseItem.equals(filter.value));
                        }
                    }
                }
            }
        }

        if (hasItemNameFilter)
            filteredItems.removeIf(i -> !i.name.toLowerCase().contains(itemNameFilter.toLowerCase()));

        filteredItems.sort((o1, o2) -> {
            for (Filter f : itemFilters) {
                if (f.getOption().equals("Stat")) {
                    double val = o2.getStat(f.value) - o1.getStat(f.value);

                    if (val == 0)
                        continue;

                    if (val > 0) return 1;
                    if (val < 0) return -1;
                }
            }
            return 0;
        });

        validItems = filteredItems;
    }

    public void refreshCharms() {
        ArrayList<DictionaryCharm> filteredCharms = new ArrayList<>(charms);

        for (Filter filter : charmFilters) {
            if (filter != null) {
                if (filter.getOption().equals("Stat")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredCharms.removeIf(i -> !i.hasStat(filter.value));
                            case 1 -> filteredCharms.removeIf(i -> i.hasStat(filter.value));
                            case 2 -> filteredCharms.removeIf(i -> !i.hasStat(filter.value) || !(i.getStat(filter.value) >= filter.constant));
                            case 3 -> filteredCharms.removeIf(i -> !i.hasStat(filter.value) || !(i.getStat(filter.value) > filter.constant));
                            case 4 -> filteredCharms.removeIf(i -> !i.hasStat(filter.value) || !(i.getStat(filter.value) == filter.constant));
                            case 5 -> filteredCharms.removeIf(i -> !i.hasStat(filter.value) || !(i.getStat(filter.value) <= filter.constant));
                            case 6 -> filteredCharms.removeIf(i -> !i.hasStat(filter.value) || !(i.getStat(filter.value) < filter.constant));
                        }
                    }
                } else if (filter.getOption().equals("Tier")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredCharms.removeIf(i -> !i.tier.equals(filter.value));
                            case 1 -> filteredCharms.removeIf(i -> i.tier.equals(filter.value));
                        }
                    }
                } else if (filter.getOption().equals("Class")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredCharms.removeIf(i -> !i.className.equals(filter.value));
                            case 1 -> filteredCharms.removeIf(i -> i.className.equals(filter.value));
                        }
                    }
                } else if (filter.getOption().equals("Skill Modifier")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredCharms.removeIf(i -> !i.hasStatModifier(filter.value));
                            case 1 -> filteredCharms.removeIf(i -> i.hasStatModifier(filter.value));
                        }
                    }
                } else if (filter.getOption().equals("Charm Power")) {
                    switch (filter.comparator) {
                        case 2 -> filteredCharms.removeIf(i -> !(i.power >= filter.constant));
                        case 3 -> filteredCharms.removeIf(i -> !(i.power > filter.constant));
                        case 4 -> filteredCharms.removeIf(i -> !(i.power == filter.constant));
                        case 5 -> filteredCharms.removeIf(i -> !(i.power < filter.constant));
                        case 6 -> filteredCharms.removeIf(i -> !(i.power <= filter.constant));
                    }
                } else if (filter.getOption().equals("Location")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredCharms.removeIf(i -> !i.location.equals(filter.value));
                            case 1 -> filteredCharms.removeIf(i -> i.location.equals(filter.value));
                        }
                    }
                } else if (filter.getOption().equals("Base Item")) {
                    if (!filter.value.equals("")) {
                        switch (filter.comparator) {
                            case 0 -> filteredCharms.removeIf(i -> !i.baseItem.equals(filter.value));
                            case 1 -> filteredCharms.removeIf(i -> i.baseItem.equals(filter.value));
                        }
                    }
                }
            }
        }

        if (hasCharmNameFilter)
            filteredCharms.removeIf(i -> !i.name.toLowerCase().contains(charmNameFilter.toLowerCase()));

        filteredCharms.sort((o1, o2) -> {
            for (Filter f : charmFilters) {
                if (f.getOption().equals("Stat")) {
                    double val = o2.getStat(f.value) - o1.getStat(f.value);

                    if (val == 0)
                        continue;

                    if (val < 0) return -1;
                    if (val > 0) return 1;
                }
            }
            return 0;
        });

        validCharms = filteredCharms;
    }

    public ArrayList<DictionaryItem> getItems() {
        return validItems;
    }

    public ArrayList<DictionaryCharm> getCharms() {
        return validCharms;
    }

    public boolean anyItems() {
        return items.size() == 0;
    }

    public boolean anyCharms() {
        return charms.size() == 0;
    }
}
