package com.joedobo27.imm;


import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.IntStream;

class ItemTransferData {
    /**
     * A list of items to move.
     */
    private HashMap<Integer, Item[]> items;
    /**
     * value Scale: {@link WurmCalendar} time, When was the target marked for movement?
     */
    private final long timeStamp;
    /**
     * value scale: tens of a second, incrementation trigger interval.
     */
    private final short unitMoveTimeInterval;
    /**
     * value scale: tens of a second, total time for the item move action.
     */
    private final int totalTime;
    /**
     * the templateId for the item marked as take. Different take targets need different handling.
     */
    private final Item takeItem;
    /**
     * The item container which held the take item.
     */
    private final @Nullable Item takeItemParent;
    /**
     * value scale: whole ints, time justTicked comparator.
     */
    private int lastWholeUnitTime;
    /**
     * Player instance object indexing tool, aka temporary database.
     */
    static private HashMap<Long, ItemTransferData> transferDataHashMap = new HashMap<>();
    /**
     * value Scale: {@link WurmCalendar} time, standard time interval to test if too much time as passed.
     */
    private final static long SECONDS_30 = WurmCalendar.SECOND * 30;

    ItemTransferData(long performerWurmId, long timeNow, HashMap<Integer, Item[]> items, int unitMoveTimeInterval,
                     int totalTime, Item takeItem, @Nullable Item takeItemParent) {
        this.timeStamp = timeNow;
        this.items = items;
        this.lastWholeUnitTime = 0;
        this.unitMoveTimeInterval = (short)unitMoveTimeInterval;
        this.totalTime = totalTime;
        this.takeItem = takeItem;
        this.takeItemParent = takeItemParent;
        transferDataHashMap.put(performerWurmId, this);
    }

    /**
     * There are mods that divided bulk items into quality groupings (often 10). Since this combines withing this mod
     * there is this process to make sure things get deposited in groupings. Without mods the default WU will just average
     * them when deposited.
     *
     * @param items list of item to seperate.
     * @return Items seperated by the quality range value
     */
    static HashMap<Integer, Item[]> groupItems(final Item[] items) {
        HashMap<Integer, Item[]> toReturn = new HashMap<>();
        IntStream.range(0,(100 / ItemMoverMod.getQualityRange()) + 1)
                .parallel()
                .forEach(value -> {
                    Item[] i = Arrays.stream(items)
                            .parallel()
                            .filter(item -> (int)(item.getQualityLevel() / ItemMoverMod.getQualityRange()) == value)
                            .toArray(Item[]::new);
                    if (i.length > 0)
                        toReturn.put(value, i);
                });
        return toReturn;
    }

    /**
     * A measurement tool to test if a time interval steps. WU had something like that measurements in 1 second steps.
     *
     * @param counter Value from WU action() "counter" arg.
     * @return Has counter advanced to the next interval?
     */
    boolean unitTimeJustTicked(float counter){
        int unitTime = (int)(Math.floor((counter * 100) / (this.unitMoveTimeInterval * 10)));
        if (unitTime != this.lastWholeUnitTime){
            this.lastWholeUnitTime = unitTime;
            return true;
        }
        return false;
    }

    /**
     * @return did the take target ordinate from a bulk item.
     */
    boolean isMoveFromBulk() {
        return this.takeItem.getTemplateId() == ItemList.bulkItem;
    }

    /**
     * @return did the take target orginate from a item pile.
     */
    boolean isMoveFromPile() {
        return this.takeItem.getTemplateId() == ItemList.itemPile;
    }

    /**
     * @param performerWurmId player wurmId is the key to hashmap of ItemTransferData instances.
     * @return did the take command occur less then 30 second ago?
     */
    static boolean transferIsInProcess(long performerWurmId) {
        ItemTransferData itemTransferData = transferDataHashMap.getOrDefault(performerWurmId, null);
        return itemTransferData != null && itemTransferData.timeStamp + SECONDS_30 >= WurmCalendar.getCurrentTime();
    }

    /**
     * Field {@link ItemTransferData#transferDataHashMap} needs to be checked for timed-out or just invalid entries. Those
     * entries are found and removed with this method.
     */
    static void verifyAndClean(){
        HashMap<Long, ItemTransferData> map = new HashMap<>();
        transferDataHashMap.entrySet()
                .stream()
                .filter(longItemDataTransferEntry -> longItemDataTransferEntry.getValue().timeStamp + SECONDS_30 >=
                        WurmCalendar.getCurrentTime())
                .forEach(entrySet -> map.put(entrySet.getKey(), entrySet.getValue()));
        transferDataHashMap = map;
    }

    /**
     * Remove, and as its only object reference also delete, an instance from {@link ItemTransferData#transferDataHashMap}.
     *
     * @param performerWurmId Creature.getWurmId() derived value, remove this entry from {@link ItemTransferData#transferDataHashMap}
     */
    static void removeItemDataTransfer(long performerWurmId) {
        ItemTransferData itemTransferData = transferDataHashMap.getOrDefault(performerWurmId, null);
        if (itemTransferData == null)
            return;
        transferDataHashMap.remove(performerWurmId);
    }

    /**
     * @param items a quality divistion sorted list of items.
     * @return total number of cycles needed to move all the items.
     */
    static int getTotalCycles(HashMap<Integer, Item[]> items) {
        int cycles = items.entrySet()
                .stream()
                .mapToInt(value -> 1 + (value.getValue().length / ItemMoverMod.getItemsPerTimeUnit()))
                .sum();
        cycles = Math.max(cycles, 1);
        cycles ++;
        return cycles;
    }

    int getTotalTime() {
        return this.totalTime;
    }

    /**
     * Get an instance in {@link ItemTransferData#transferDataHashMap}
     *
     * @param performerWurmId Creature.getWurmId() derived value, get entry in {@link ItemTransferData#transferDataHashMap}
     * @return An instance of ItemTransferData.
     */
    static @Nullable ItemTransferData getItemTransferData(long performerWurmId) {
        return transferDataHashMap.getOrDefault(performerWurmId, null);
    }

    @Nullable
    Item getTakeItemParent() {
        return this.takeItemParent;
    }

    HashMap<Integer, Item[]> getItems() {
        return items;
    }
}
