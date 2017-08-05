package com.joedobo27.imm;

import com.wurmonline.server.*;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.players.Player;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public class DropAction implements ModAction, ActionPerformer, BehaviourProvider {

    private final ActionEntry actionEntry;
    private final short actionId;

    DropAction(short actionId, ActionEntry actionEntry){
        //actionId = Actions.DROP;
        //actionEntry = Actions.actionEntrys[Actions.DROP];
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    @Override
    public short getActionId() {
        return this.actionId;
    }

    // DROP ON BULK
    @Override
    public List<ActionEntry> getBehavioursFor(final Creature performer, final Item target) {
        return getBehavioursFor(performer, null, target);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(final Creature performer, final Item source, final Item target) {
        if (!(performer instanceof Player) || !(target.isBulkContainer()) || !ItemTransferData.transferIsInProcess(performer.getWurmId()))
            return null;
        return Collections.singletonList(this.actionEntry);
    }

    @Override
    public boolean action(final Action act, final Creature performer, final Item target, final short action, final float counter) {
        return action(act, performer, null, target, action, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item bulkContainer, short aActionId, float counter) {
        // ACTION, SHOULD IT BE DONE
        if (aActionId != this.actionId || bulkContainer == null || (!bulkContainer.isBulkContainer() && !bulkContainer.isCrate()))
            return ActionPerformer.super.action(action, performer, source, bulkContainer, aActionId, counter);

        String youMessage;
        String broadcastMessage;
        ItemTransferData itemTransferData;
        final float ACTION_START_TIME = 1.0f;
        final float TIME_TO_COUNTER_DIVISOR = 10.0f;
        //  ACTION SET UP
        if(counter == ACTION_START_TIME && hasAFailureCondition())
            return true;
        if (counter == ACTION_START_TIME) {
            youMessage = String.format("You start %s.", action.getActionString());
            performer.getCommunicator().sendNormalServerMessage(youMessage);
            broadcastMessage = String.format("%s starts to %s.", performer.getName(), action.getActionString());
            Server.getInstance().broadCastAction(broadcastMessage, performer, 5);
            itemTransferData = ItemTransferData.getItemTransferData(performer.getWurmId());
            if (itemTransferData == null) {
                performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong");
                ItemMoverMod.logger.warning("ItemTransferData instance wasn't found.");
                return true;
            }
            action.setTimeLeft(itemTransferData.getTotalTime());
            performer.sendActionControl(action.getActionEntry().getVerbString(), true, itemTransferData.getTotalTime());
            performer.getStatus().modifyStamina(-1000.0f);
            return false;
        }
        boolean isTimedOut = counter - 1 > action.getTimeLeft() / TIME_TO_COUNTER_DIVISOR;
        // ACTION HAS FINISHED
        if (isTimedOut) {
            youMessage = String.format("You finish %s.", action.getActionString());
            performer.getCommunicator().sendNormalServerMessage(youMessage);
            broadcastMessage = String.format("%s finishes %s.", performer.getName(), action.getActionString());
            Server.getInstance().broadCastAction(broadcastMessage, performer, 5);
            ItemTransferData.removeItemDataTransfer(performer.getWurmId());
            return true;
        }
        itemTransferData = ItemTransferData.getItemTransferData(performer.getWurmId());
        if (itemTransferData == null) {
            performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong");
            ItemMoverMod.logger.warning("ItemTransferData instance wasn't found.");
            return true;
        }
        // ACTION IN PROCESS
        if (!itemTransferData.unitTimeJustTicked(counter))
            return false;
        if (hasAFailureCondition())
            return true;
        if (itemTransferData.isMoveFromBulk()) {
            moveFromBulk(bulkContainer, itemTransferData, performer);
            // Bulk moves consists of a single move action.
            youMessage = String.format("You finish %s.", action.getActionString());
            performer.getCommunicator().sendNormalServerMessage(youMessage);
            broadcastMessage = String.format("%s finishes %s.", performer.getName(), action.getActionString());
            Server.getInstance().broadCastAction(broadcastMessage, performer, 5);
            ItemTransferData.removeItemDataTransfer(performer.getWurmId());
            return true;
        }
        if (itemTransferData.isMoveFromPile()) {
            moveFromPile(bulkContainer, itemTransferData, performer);
            // Moves from piles consists of multiple move actions.
            return false;
        }
        //noinspection ConstantConditions
        return itemTransferData.isMoveFromBulk() || isTimedOut;
    }

    private void moveFromPile(Item bulkContainer, ItemTransferData itemTransferData, Creature performer) {
        // Verify item-HashMap existence.
        if (itemTransferData.getItems() == null || itemTransferData.getItems().size() == 0)
            return;
        Integer[] integerKeys = itemTransferData.getItems().keySet().toArray(new Integer[itemTransferData.getItems().size()]);
        Item[] itemsCurrentGroup = itemTransferData.getItems().get(integerKeys[0]);
        if (itemsCurrentGroup == null || itemsCurrentGroup.length == 0)
            return;
        Item firstItem = itemsCurrentGroup[0];
        if (firstItem == null)
            return;

        // Tally move count and verify available bulk container available space.
        int containerSpace;
        if (bulkContainer.isCrate()){
            containerSpace = bulkContainer.getRemainingCrateSpace();
        } else {
            containerSpace = bulkContainer.getFreeVolume() / firstItem.getTemplate().getVolume();
        }
        int moveCount = Math.min(containerSpace, itemsCurrentGroup.length);
        moveCount = Math.min(moveCount, ItemMoverMod.getItemsPerTimeUnit());
        if (moveCount == 0)
            return;

        //  Scale grams of insert item and try to insert it.
        int originalGrams = firstItem.getWeightGrams();
        int summedGrams = firstItem.getWeightGrams();
        if (moveCount > 1) {
            summedGrams = IntStream.range(0, moveCount)
                    .map(value -> itemsCurrentGroup[value].getWeightGrams())
                    .sum();
        }
        firstItem.setWeight(summedGrams, false);
        if (!bulkContainer.testInsertItem(firstItem)) {
            ItemMoverMod.logger.warning("problem inserting");
            performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong");
            firstItem.setWeight(originalGrams, false);
            return;
        }
        try {
            firstItem.moveToItem(performer, bulkContainer.getWurmId(), true);
        } catch (NoSuchItemException |  NoSuchPlayerException | NoSuchCreatureException e) {
            ItemMoverMod.logger.warning(e.getMessage());
            performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong");
            firstItem.setWeight(originalGrams, false);
            return;
        }

        // Delete combined items and update item-HashMap
        if (moveCount > 1) {
            IntStream.range(1, moveCount)
                    .forEach(value -> Items.destroyItem(itemsCurrentGroup[value].getWurmId()));
            if (itemsCurrentGroup.length - moveCount <= 0){
                itemTransferData.getItems().remove(integerKeys[0]);
                return ;
            }
            Item[] items2 = new Item[itemsCurrentGroup.length - moveCount];
            System.arraycopy(itemTransferData.getItems().get(integerKeys[0]), moveCount, items2, 0,
                    itemTransferData.getItems().get(integerKeys[0]).length - moveCount);
            itemTransferData.getItems().put(integerKeys[0], items2);
        }
        if (moveCount <= 1) {
            itemTransferData.getItems().remove(integerKeys[0]);
        }
    }

    private void moveFromBulk(Item bulkContainer, ItemTransferData itemTransferData, Creature performer) {
        // Verify item-HashMap existence.
        if (itemTransferData.getItems() == null || itemTransferData.getItems().size() == 0)
            return;
        Integer[] integerKeys = itemTransferData.getItems().keySet().toArray(new Integer[itemTransferData.getItems().size()]);
        Item[] items = itemTransferData.getItems().get(integerKeys[0]);
        if (items == null || items.length == 0)
            return;
        Item itemBulk = items[0];
        if (itemBulk == null)
            return;

        // Tally move count and verify available bulk container available space.
        int containerSpace;
        if (bulkContainer.isCrate()){
            containerSpace = bulkContainer.getRemainingCrateSpace();
        } else {
            containerSpace = bulkContainer.getFreeVolume() / itemBulk.getRealTemplate().getVolume();
        }
        int moveCount = Math.min(containerSpace, itemBulk.getBulkNums());
        if (moveCount == 0)
            return;

        // create insert item and try to insert it.
        Item itemInsert;
        try{
            itemInsert = ItemFactory.createItem(itemBulk.getRealTemplateId(), itemBulk.getQualityLevel(), itemBulk.getMaterial(),
                    itemBulk.getRarity(), null);
        } catch (NoSuchTemplateException | FailedException e) {
            ItemMoverMod.logger.warning(e.getMessage());
            return;
        }
        itemInsert.setWeight(itemInsert.getWeightGrams() * moveCount, false);
        if (!bulkContainer.testInsertItem(itemInsert)) {
            ItemMoverMod.logger.warning("problem inserting");
            performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong");
            Items.destroyItem(itemInsert.getWurmId());
            return;
        }
        try {
            itemInsert.moveToItem(performer, bulkContainer.getWurmId(), true);
        } catch (NoSuchItemException |  NoSuchPlayerException | NoSuchCreatureException e) {
            ItemMoverMod.logger.warning(e.getMessage());
            performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong");
            Items.destroyItem(itemInsert.getWurmId());
            return;
        }

        // update quantities take/removed from sources.
        itemBulk.setWeight(itemBulk.getWeightGrams() - (itemBulk.getRealTemplate().getVolume() * moveCount), false);
        if (itemBulk.getWeightGrams() <= 0) {
            Items.destroyItem(itemBulk.getWurmId());
            itemTransferData.getItems().remove(integerKeys[0]);
            bulkContainer.updateIfGroundItem();
        }
        if (bulkContainer.isCrate() && moveCount == bulkContainer.getRemainingCrateSpace()){
            itemTransferData.getItems().remove(integerKeys[0]);
        }
    }

    private boolean hasAFailureCondition() {
        return false;
    }
}
