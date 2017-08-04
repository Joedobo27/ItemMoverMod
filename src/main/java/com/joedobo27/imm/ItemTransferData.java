package com.joedobo27.imm;


import com.wurmonline.server.FailedException;
import com.wurmonline.server.Items;
import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.NoSuchTemplateException;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.IntStream;

class ItemTransferData {
    /**
     * Creature.getWurmId() derived value, the player mover.
     */
    private final long performerWurmId;
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
    private final int takeTemplateId;
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
                     int totalTime, int takeTemplateId) {
        this.performerWurmId = performerWurmId;
        this.timeStamp = timeNow;
        this.items = items;
        this.lastWholeUnitTime = 0;
        this.unitMoveTimeInterval = (short)unitMoveTimeInterval;
        this.totalTime = totalTime;
        this.takeTemplateId = takeTemplateId;
        transferDataHashMap.put(performerWurmId, this);
    }

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

    Item combineItems(Item targetItems) {
        Item toReturn = null;
        if (this.takeTemplateId == ItemList.bulkItem)
            toReturn = moveFromBulk(targetItems);
        else
            toReturn = moveFromPile(targetItems);
        return toReturn;
    }

    private Item moveFromBulk(Item targetBulk) {
        if (this.items == null || this.items.size() == 0)
            return null;
        Integer[] integerKeys = this.items.keySet().toArray(new Integer[this.items.size()]);
        if (integerKeys.length == 0 || integerKeys == null)
            return null;
        Item itemBulk = this.items.get(integerKeys[0])[0];
        int containerSpace;
        if (targetBulk.isCrate()){
            containerSpace = targetBulk.getRemainingCrateSpace();
        } else {
            containerSpace = targetBulk.getFreeVolume() / itemBulk.getRealTemplate().getVolume();
        }
        Item itemReturn;
        try{
            itemReturn = ItemFactory.createItem(itemBulk.getRealTemplateId(), itemBulk.getQualityLevel(), itemBulk.getMaterial(),
                itemBulk.getRarity(), null);
        } catch (NoSuchTemplateException | FailedException e) {
            ItemMoverMod.logger.warning(e.getMessage());
            return null;
        }
        int moveCount = Math.min(containerSpace, itemBulk.getBulkNums());
        if (moveCount == 0)
            return null;
        itemReturn.setWeight(itemReturn.getWeightGrams() * moveCount, false);
        itemBulk.setWeight(itemBulk.getWeightGrams() - (itemBulk.getRealTemplate().getVolume() * moveCount), false);
        if (itemBulk.getWeightGrams() <= 0) {
            Items.destroyItem(itemBulk.getWurmId());
            this.items.remove(integerKeys[0]);
            targetBulk.updateIfGroundItem();
        }
        if (targetBulk.isCrate() && moveCount == targetBulk.getRemainingCrateSpace()){
            this.items.remove(integerKeys[0]);
        }

        return itemReturn;
    }

    private Item moveFromPile(Item targetBulk) {
        if (this.items == null || this.items.size() == 0)
            return null;
        Integer[] integerKeys = this.items.keySet().toArray(new Integer[this.items.size()]);
        if (integerKeys.length == 0 || integerKeys == null)
            return null;
        Item toReturn = this.items.get(integerKeys[0])[0];
        int containerSpace;
        if (targetBulk.isCrate()){
            containerSpace = targetBulk.getRemainingCrateSpace();
        } else {
            containerSpace = targetBulk.getFreeVolume() / toReturn.getTemplate().getVolume();
        }
        if (this.items.get(integerKeys[0]).length == 1 && integerKeys.length == 1) {

            return toReturn;
        }
        int moveCount = Math.min(ItemMoverMod.getItemsPerTimeUnit(), this.items.get(integerKeys[0]).length);
        moveCount = Math.min(moveCount, containerSpace);
        if (moveCount > 1) {
            IntStream.range(1, moveCount)
                    .forEach(value -> toReturn.setWeight(toReturn.getWeightGrams() + items.get(integerKeys[0])[value].getWeightGrams(), false));
            IntStream.range(1, moveCount)
                    .forEach(value -> Items.destroyItem(items.get(integerKeys[0])[value].getWurmId()));
            if (this.items.get(integerKeys[0]).length - moveCount <= 0){
                transferDataHashMap.get(this.performerWurmId).items.remove(integerKeys[0]);
                return toReturn;
            }
            Item[] items2 = new Item[this.items.get(integerKeys[0]).length - moveCount];
            System.arraycopy(this.items.get(integerKeys[0]), moveCount, items2, 0, this.items.get(integerKeys[0]).length - moveCount);
            transferDataHashMap.get(this.performerWurmId).items.put(integerKeys[0], items2);
        }
        if (moveCount <= 1) {
            transferDataHashMap.get(this.performerWurmId).items.remove(integerKeys[0]);
        }
        targetBulk.updateModelNameOnGroundItem();
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

    public HashMap<Integer, Item[]> getItems() {
        return items;
    }
}
