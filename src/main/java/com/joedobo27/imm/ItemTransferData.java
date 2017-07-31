package com.joedobo27.imm;


import com.wurmonline.server.Items;
import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.items.Item;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.stream.IntStream;

class ItemTransferData {
    /**
     * Creature.getWurmId() derived value, the player mover.
     */
    private long performerWurmId;
    /**
     * A list of items to move.
     */
    private Item[] items;
    /**
     * value Scale: {@link WurmCalendar} time, When was the target marked for movement?
     */
    private long timeStamp;
    /**
     * value scale: tens of a second, incrementation trigger interval.
     */
    private short unitMoveTimeInterval;
    /**
     * value scale: tens of a second, total time for the item move action.
     */
    private int totalTime;
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

    ItemTransferData(long performerWurmId, long timeNow, Item[] items, int unitMoveTimeInterval) {
        this.performerWurmId = performerWurmId;
        this.timeStamp = timeNow;
        this.items = items;
        this.lastWholeUnitTime = 0;
        this.unitMoveTimeInterval = (short)unitMoveTimeInterval;
        transferDataHashMap.put(performerWurmId, this);
        if (ItemMoverMod.r.nextInt(99) < 10)
            verifyAndClean();
    }

    /**
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

    Item combineItems() {
        if (this.items == null || this.items.length == 0)
            return null;
        Item toReturn = this.items[0];
        if (this.items.length == 1)
            return toReturn;
        int moveCount = Math.min(ItemMoverMod.itemsPerTimeUnit, this.items.length-1);
        IntStream.range(1, moveCount)
                .forEach(value -> toReturn.setWeight(toReturn.getWeightGrams() + items[value].getWeightGrams(), false));
        IntStream.range(1, moveCount)
                .forEach(value -> Items.destroyItem(items[value].getWurmId()));
        Item[] items2 = new Item[this.items.length - moveCount];
        System.arraycopy(this.items, moveCount, items2,0,this.items.length - moveCount);
        transferDataHashMap.get(this.performerWurmId).items = items2;
        return toReturn;
    }

    static boolean transferIsInProcess(long performerWurmId) {
        ItemTransferData itemTransferData = transferDataHashMap.getOrDefault(performerWurmId, null);
        return itemTransferData != null && itemTransferData.timeStamp + SECONDS_30 >= WurmCalendar.getCurrentTime();
    }

    /**
     * Field {@link ItemTransferData#transferDataHashMap} needs to be checked for timed-out or just invalid entries. Those
     * entries are found and removed with this method.
     */
    private static void verifyAndClean(){
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

    void setTotalTime() {
        if (this == null)
            return;
        int cycles = (int)Math.ceil(this.items.length / ItemMoverMod.itemsPerTimeUnit);
        cycles = Math.max(cycles, 1);
        cycles ++;
        this.totalTime = (cycles * this.unitMoveTimeInterval);
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
}
