package com.joedobo27.imm;

import com.wurmonline.server.*;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.NoSuchTemplateException;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import static com.joedobo27.libs.ActionTypes.*;

public class DropAction implements ModAction, ActionPerformer{

    static ActionEntry actionEntry;
    private static short actionId;
    private final static float ACTION_START_TIME = 1.0f;
    private final static float TIME_TO_COUNTER_DIVISOR = 10.0f;

    DropAction(){
        //actionId = Actions.DROP;
        //actionEntry = Actions.actionEntrys[Actions.DROP];

        actionId = (short) ModActions.getNextActionId();
        actionEntry = ActionEntry.createEntry(actionId, "Empty", "emptying", new int[] {ACTION_ENEMY_ALWAYS.getId()});
        ModActions.registerAction(actionEntry);
    }

    @Override
    public short getActionId() {
        return actionId;
    }

    @Override
    public boolean action(Action action, Creature performer, Item barrel, Item bulkContainer, short aActionId, float counter) {
        // ACTION, SHOULD IT BE DONE
        if (aActionId != actionId || bulkContainer == null || (!bulkContainer.isBulkContainer() && !bulkContainer.isCrate()))
            return ActionPerformer.super.action(action, performer, barrel, bulkContainer, aActionId, counter);

        String youMessage;
        String broadcastMessage;
        //  ACTION SET UP
        if(counter == ACTION_START_TIME && hasAFailureCondition())
            return true;
        if (counter == ACTION_START_TIME) {
            youMessage = String.format("You start %s.", action.getActionString());
            performer.getCommunicator().sendNormalServerMessage(youMessage);
            broadcastMessage = String.format("%s starts to %s.", performer.getName(), action.getActionString());
            Server.getInstance().broadCastAction(broadcastMessage, performer, 5);
            int time = ItemTransferData.getDropItemTime(performer.getWurmId());
            action.setTimeLeft(time);
            performer.sendActionControl(action.getActionEntry().getVerbString(), true, time);
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
        // ACTION IN PROCESS
        if (!action.justTickedSecond())
            return false;
        if (hasAFailureCondition())
            return true;
        Item combinedItem = ItemTransferData.combineItems(performer.getWurmId());
        if (combinedItem == null)
            return false;
        if (combinedItem.getTemplateId() != ItemList.bulkItem) {
            try {
                combinedItem.moveToItem(performer, bulkContainer.getWurmId(), true);
            } catch (Exception ignored) {}
            return false;
        }
        Item item = null;
        try {
            item = ItemFactory.createItem(combinedItem.getRealTemplateId(), combinedItem.getQualityLevel(), combinedItem.getMaterial(),
                    combinedItem.getRarity(), null);
        }catch (NoSuchTemplateException | FailedException e){
            performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
            return true;
        }
        int combinedCount = Integer.parseInt(combinedItem.getDescription().replaceAll("x",""));
        item.setWeight(combinedCount * item.getTemplate().getWeightGrams(), false);
        try {
            item.moveToItem(performer, bulkContainer.getWurmId(), true);
        } catch (NoSuchItemException | NoSuchPlayerException | NoSuchCreatureException e) {
            Items.destroyItem(item.getWurmId());
            performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
            return true;
        }
        Items.destroyItem(combinedItem.getWurmId());
        return false;
    }

    private boolean hasAFailureCondition() {
        return false;
    }
}
