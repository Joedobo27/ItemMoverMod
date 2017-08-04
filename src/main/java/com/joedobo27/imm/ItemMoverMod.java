package com.joedobo27.imm;


import com.joedobo27.libs.ActionUtilities;
import com.wurmonline.server.behaviours.ActionEntry;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;


import java.util.Properties;
import java.util.Random;
import java.util.logging.Logger;

import static com.joedobo27.libs.ActionTypes.ACTION_ENEMY_ALWAYS;


public class ItemMoverMod implements WurmServerMod, PreInitable, ServerStartedListener, Configurable{

    private int itemsPerTimeUnit;
    private int unitMoveTimeInterval;
    private int qualityRange;

    private static ItemMoverMod instance = null;
    static final Logger logger = Logger.getLogger(ItemMoverMod.class.getName());
    static final Random r = new Random();

    @Override
    public void configure(Properties properties) {
        instance = this;
        this.itemsPerTimeUnit = Integer.parseInt(properties.getProperty("itemsPerTimeUnit", Integer.toString(itemsPerTimeUnit)));
        this.unitMoveTimeInterval = Integer.parseInt(properties.getProperty("unitMoveTimeInterval", Integer.toString(unitMoveTimeInterval)));
        this.qualityRange = Integer.parseInt(properties.getProperty("qualityRange", Integer.toString(qualityRange)));
    }

    @Override
    public void preInit() {
        ModActions.init();
    }

    @Override
    public void onServerStarted() {
        short actionIdTake = (short) ModActions.getNextActionId();
        ActionEntry actionEntryTake = ActionEntry.createEntry(actionIdTake, "Mark-take", "marking-take", new int[]{});
        ModActions.registerAction(actionEntryTake);
        MarkTakeAction markTakeAction = new MarkTakeAction(actionIdTake, actionEntryTake);
        ModActions.registerAction(markTakeAction);
        ActionUtilities.maxRangeReflect(actionEntryTake, 8, logger);

        short actionIdDrop = (short) ModActions.getNextActionId();
        ActionEntry actionEntryDrop = ActionEntry.createEntry(actionIdDrop, "Move items", "moving items",
                new int[] {ACTION_ENEMY_ALWAYS.getId()});
        ModActions.registerAction(actionEntryDrop);
        DropAction dropAction = new DropAction(actionIdDrop, actionEntryDrop);
        ModActions.registerAction(dropAction);
        ActionUtilities.maxRangeReflect(actionEntryDrop, 8 , logger);
    }

    static int getItemsPerTimeUnit() {
        return instance.itemsPerTimeUnit;
    }

    static int getUnitMoveTimeInterval() {
        return instance.unitMoveTimeInterval;
    }

    static int getQualityRange() {
        return instance.qualityRange;
    }
}
