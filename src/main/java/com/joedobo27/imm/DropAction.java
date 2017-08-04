package com.joedobo27.imm;

import com.wurmonline.server.*;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.players.Player;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

    // DROP ON TILE
    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, int tileX, int tileY, boolean onSurface, int encodedTile) {
        return getBehavioursFor(performer, null, tileX, tileY, onSurface, encodedTile);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, int tileX, int tileY, boolean onSurface, int encodedTile){
        if (!(performer instanceof Player) || !isValidDropTarget(tileX, tileY, encodedTile) || !ItemTransferData.transferIsInProcess(performer.getWurmId()))
                return null;
        return Collections.singletonList(actionEntry);
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
    public boolean action(Action action, Creature performer, Item source, Item target, short aActionId, float counter) {
        // ACTION, SHOULD IT BE DONE
        if (aActionId != this.actionId || target == null || (!target.isBulkContainer() && !target.isCrate()))
            return ActionPerformer.super.action(action, performer, source, target, aActionId, counter);

        String youMessage;
        String broadcastMessage;
        ItemTransferData itemTransferData = ItemTransferData.getItemTransferData(performer.getWurmId());
        final float ACTION_START_TIME = 1.0f;
        final float TIME_TO_COUNTER_DIVISOR = 10.0f;
        //  ACTION SET UP
        if(counter == ACTION_START_TIME && hasAFailureCondition(itemTransferData, target))
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
        if (hasAFailureCondition(itemTransferData, target))
            return true;
        Item combinedItem = itemTransferData.combineItems(target);
        if (combinedItem == null)
            return false;
        if (combinedItem.getTemplateId() != ItemList.bulkItem) {
            try {
                combinedItem.moveToItem(performer, target.getWurmId(), true);
            } catch (Exception ignored) {}
            return false;
        }
        Item item;
        try {
            item = ItemFactory.createItem(combinedItem.getRealTemplateId(), combinedItem.getQualityLevel(), combinedItem.getMaterial(),
                    combinedItem.getRarity(), null);
        }catch (NoSuchTemplateException | FailedException e){
            performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
            return true;
        }
        int combinedCount = Integer.parseInt(combinedItem.getDescription().replaceAll("x",""));
        item.setWeight(combinedCount * item.getTemplate().getWeightGrams(), false);
        if (target.getTemplateId() == ItemList.bulkContainer || target.getTemplateId() == ItemList.hopper)
            item.AddBulkItem(performer, target);
        if (target.isCrate())
            item.AddBulkItemToCrate(performer, target);
        Items.destroyItem(combinedItem.getWurmId());
        return false;
    }

    private boolean hasAFailureCondition(ItemTransferData itemTransferData, Item target) {
        return false;
    }

    private boolean isValidDropTarget(int tileX, int tileY, int encodedTile) {
        return true;
    }
}
