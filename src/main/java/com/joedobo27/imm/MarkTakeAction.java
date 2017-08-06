package com.joedobo27.imm;

import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.players.Player;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MarkTakeAction implements ModAction, ActionPerformer, BehaviourProvider {

    private final ActionEntry actionEntry;
    private final short actionId;

    MarkTakeAction(short actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    @Override
    public short getActionId() {
        return this.actionId;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, Item target) {
        if (!(performer instanceof Player)|| source.getTemplateId() != ItemList.bodyHand || !isValidTakeTarget(target.getTemplateId()))
            return BehaviourProvider.super.getBehavioursFor(performer, source, target);
        return Collections.singletonList(this.actionEntry);
    }

    @Override
    public boolean action(Action action, Creature performer, Item[] targets, short aActionId, float counter) {
        if (aActionId != this.actionId)
            return ActionPerformer.super.action(action, performer, targets, aActionId, counter);
        HashMap<Integer, Item[]> integerHashMap = ItemTransferData.groupItems(targets);
        int totalTime = ItemTransferData.getTotalCycles(integerHashMap) * ItemMoverMod.getUnitMoveTimeInterval();
        new ItemTransferData(performer.getWurmId(), WurmCalendar.getCurrentTime(), integerHashMap,
                ItemMoverMod.getUnitMoveTimeInterval(), totalTime, targets[0], null);
        performer.getCommunicator().sendNormalServerMessage("You mark items for transfer.");
        //TODO this isn't working at all. Verify if it is even reachable.
        return true;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short aActionId, float counter) {
        if (aActionId != this.actionId || source.getTemplateId() != ItemList.bodyHand || !isValidTakeTarget(target.getTemplateId()))
            return ActionPerformer.super.action(action, performer, source, target, aActionId, counter);
        if (target.getTemplateId() == ItemList.itemPile) {
            if (pileFailureConditions(target, performer))
                return true;
            HashMap<Integer, Item[]> ItemGroupsHashMap = ItemTransferData.groupItems(target.getItemsAsArray());
            int totalTime = ItemTransferData.getTotalCycles(ItemGroupsHashMap) * ItemMoverMod.getUnitMoveTimeInterval();
            new ItemTransferData(performer.getWurmId(), WurmCalendar.getCurrentTime(), ItemGroupsHashMap,
                    ItemMoverMod.getUnitMoveTimeInterval(), totalTime, target, null);
        }
        else if(target.getTemplateId() == ItemList.bulkItem){
            if(bulkFailureConditions(target, performer))
                return true;
            HashMap<Integer, Item[]> integerHashMap = ItemTransferData.groupItems(new Item[]{target});
            int totalTime = ItemTransferData.getTotalCycles(integerHashMap) * ItemMoverMod.getUnitMoveTimeInterval();
            Item bulkContainerParent;
            try {
                bulkContainerParent = target.getParent();
            }catch (NoSuchItemException e){
                ItemMoverMod.logger.warning(e.getMessage());
                return true;
            }
            new ItemTransferData(performer.getWurmId(), WurmCalendar.getCurrentTime(), integerHashMap,
                    ItemMoverMod.getUnitMoveTimeInterval(), totalTime, target, bulkContainerParent);
            if (ItemMoverMod.r.nextInt(99) < 10)
                ItemTransferData.verifyAndClean();
        }
        performer.getCommunicator().sendNormalServerMessage("You mark a bulk item for transfer.");
        return true;
    }

    private boolean pileFailureConditions(Item pile, Creature performer) {
        boolean containsOneItemType = Arrays.stream(pile.getItemsAsArray())
                .mapToInt(Item::getTemplateId)
                .distinct()
                .count() == 1;
        if (!containsOneItemType) {
            performer.getCommunicator().sendNormalServerMessage("Only item piles containing one type of item can be moved in this way.");
            return true;
        }
        Item[] items = pile.getItemsAsArray();
        if(items[0].isFish()) {
            performer.getCommunicator().sendNormalServerMessage("Fish can't be moved in this way.");
            return true;
        }
        return false;
    }

    private boolean bulkFailureConditions(Item bulkItem, Creature performer) {
        if(bulkItem.getRealTemplate().isFish()) {
            performer.getCommunicator().sendNormalServerMessage("Fish can't be moved in this way.");
            return true;
        }
        return false;
    }

    static private boolean isValidTakeTarget(int itemTemplate) {
        switch (itemTemplate){
            case ItemList.bulkItem:
            case ItemList.itemPile:
                return true;
            default:
                return false;
        }
    }
}
