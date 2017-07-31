package com.joedobo27.imm;

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
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.Collections;
import java.util.List;

public class MarkTakeAction implements ModAction, ActionPerformer, BehaviourProvider {

    static ActionEntry actionEntry;
    static private short actionId;

    MarkTakeAction() {
        //actionId = Actions.TAKE;
        //actionEntry = Actions.actionEntrys[Actions.TAKE];
        actionId = (short) ModActions.getNextActionId();
        actionEntry = ActionEntry.createEntry(actionId, "Mark-take", "marking-take", new int[]{});
        ModActions.registerAction(actionEntry);
    }

    @Override
    public short getActionId() {
        return actionId;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(final Creature performer, final Item target) {
        return BehaviourProvider.super.getBehavioursFor(performer, target);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(final Creature performer, final Item source, final Item target) {
        if (!(performer instanceof Player) || source.getTemplateId() != ItemList.bodyHand)
            return BehaviourProvider.super.getBehavioursFor(performer, source, target);
        if (ItemTransferData.transferIsInProcess(performer.getWurmId()) &&
                target != null && (target.isBulkContainer() || target.isCrate())){
            return Collections.singletonList(DropAction.actionEntry);
        }
        return Collections.singletonList(actionEntry);
    }


    @Override
    public boolean action(Action action, Creature performer, Item target, short aActionId, float counter) {
        return ActionPerformer.super.action(action, performer, target, aActionId, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item[] targets, short aActionId, float counter) {
        if (aActionId != actionId)
            return ActionPerformer.super.action(action, performer, targets, aActionId, counter);
        new ItemTransferData(performer.getWurmId(), WurmCalendar.getCurrentTime(), targets);
        performer.getCommunicator().sendNormalServerMessage("You mark items for transfer.");
        //TODO this isn't working at all. Verify if it is even reachable.
        return true;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short aActionId, float counter) {
        if (aActionId != actionId || source.getTemplateId() != ItemList.bodyHand || !isValidTakeTarget(target.getTemplateId()))
            return ActionPerformer.super.action(action, performer, source, target, aActionId, counter);
        new ItemTransferData(performer.getWurmId(), WurmCalendar.getCurrentTime(), new Item[]{target});
        performer.getCommunicator().sendNormalServerMessage("You mark a bulk item for transfer.");
        //TODO add in logic to make use of a pile-of-items, templateId == 177 (pile).
        return true;
    }

    private boolean isValidTakeTarget(int itemTemplate) {
        switch (itemTemplate){
            case ItemList.bulkItem:
            case ItemList.itemPile:
                return true;
            default:
                return false;
        }
    }
}
