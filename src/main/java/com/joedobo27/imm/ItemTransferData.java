package com.joedobo27.imm;

import com.wurmonline.server.Items;
import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.items.Item;

import java.util.HashMap;
import java.util.stream.IntStream;

class ItemTransferData {
    private long performerWurmId;
    private Item[] items;
    private long timeStamp;
    private final static long SECONDS_30 = WurmCalendar.SECOND * 30;
    static private HashMap<Long, ItemTransferData> transferDataHashMap = new HashMap<>();

    ItemTransferData(long performerWurmId, long timeNow, Item[] items) {
        this.performerWurmId = performerWurmId;
        this.timeStamp = timeNow;
        this.items = items;
        transferDataHashMap.put(performerWurmId, this);
        if (ItemMoverMod.r.nextInt(99) < 10)
            verifyAndClean();
    }

    static int getDropItemTime(long performerWurmId) {
        if (performerWurmId == -10L)
            return 0;
        double cycles = transferDataHashMap.get(performerWurmId).items.length / ItemMoverMod.itemsPerSecond;
        cycles ++;
        cycles *= 10;
        cycles += 5;
        return (int)cycles;
    }

    static Item combineItems(long performerWurmId) {
        Item[] items = transferDataHashMap.get(performerWurmId).items;
        if (items == null || items.length == 0)
            return null;
        Item toReturn = items[0];
        if (items.length == 1)
            return toReturn;
        int moveCount = Math.min(ItemMoverMod.itemsPerSecond, items.length-1);
        IntStream.range(1, moveCount)
                .forEach(value -> toReturn.setWeight(toReturn.getWeightGrams() + items[value].getWeightGrams(), false));
        IntStream.range(1, moveCount)
                .forEach(value -> Items.destroyItem(items[value].getWurmId()));
        Item[] items2 = new Item[items.length - moveCount];
        System.arraycopy(items, moveCount, items2,0,items.length - moveCount);
        transferDataHashMap.get(performerWurmId).items = items2;
        return toReturn;
    }

    static boolean transferIsInProcess(long performerWurmId) {
        ItemTransferData itemTransferData = transferDataHashMap.getOrDefault(performerWurmId, null);
        return itemTransferData != null && itemTransferData.timeStamp + SECONDS_30 >= WurmCalendar.getCurrentTime();
    }

    private static void verifyAndClean(){
        HashMap<Long, ItemTransferData> map = new HashMap<>();
        transferDataHashMap.entrySet()
                .stream()
                .filter(longItemDataTransferEntry -> longItemDataTransferEntry.getValue().timeStamp + SECONDS_30 >=
                        WurmCalendar.getCurrentTime())
                .forEach(entrySet -> map.put(entrySet.getKey(), entrySet.getValue()));
        transferDataHashMap = map;
    }

    static void removeItemDataTransfer(long performerWurmId) {
        ItemTransferData itemTransferData = transferDataHashMap.getOrDefault(performerWurmId, null);
        if (itemTransferData == null)
            return;
        transferDataHashMap.remove(performerWurmId);
    }
}
