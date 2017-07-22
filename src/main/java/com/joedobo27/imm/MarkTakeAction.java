package com.joedobo27.imm;

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

import static com.joedobo27.imm.Wrap.Actions.*;

public class MarkTakeAction implements ModAction, ActionPerformer, BehaviourProvider {

    static ActionEntry actionEntry;
    static private short actionId;

    MarkTakeAction() {
        //actionId = Actions.TAKE;
        //actionEntry = Actions.actionEntrys[Actions.TAKE];
        actionId = (short) ModActions.getNextActionId();
        actionEntry = ActionEntry.createEntry(actionId, "Mark take", "marking-take", new int[]{ACTION_ENEMY_ALWAYS.getId()});
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
        new ItemTransferData(performer.getWurmId(), targets);
        performer.getCommunicator().sendNormalServerMessage("You mark items for transfer.");
        return true;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short aActionId, float counter) {
        if (aActionId != actionId || source.getTemplateId() != ItemList.bodyHand || target.getTemplateId() != ItemList.bulkItem)
            return ActionPerformer.super.action(action, performer, source, target, aActionId, counter);
        new ItemTransferData(performer.getWurmId(), new Item[]{target});
        performer.getCommunicator().sendNormalServerMessage("You mark a bulk item for transfer.");
        return true;
    }
}
