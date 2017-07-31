package com.joedobo27.imm;


import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.Properties;
import java.util.Random;
import java.util.logging.Logger;


public class ItemMoverMod implements WurmServerMod, PreInitable, ServerStartedListener, Configurable{

    static final Random r = new Random();
    static int itemsPerTimeUnit;
    static int unitMoveTimeInterval;
    static final Logger logger = Logger.getLogger(ItemMoverMod.class.getName());

    @Override
    public void configure(Properties properties) {
        itemsPerTimeUnit = Integer.parseInt(properties.getProperty("itemsPerTimeUnit", Integer.toString(itemsPerTimeUnit)));
        unitMoveTimeInterval = Integer.parseInt(properties.getProperty("unitMoveTimeInterval", Integer.toString(unitMoveTimeInterval)));
    }

    @Override
    public void preInit() {
        ModActions.init();
    }

    @Override
    public void onServerStarted() {
        ModActions.registerAction(new MarkTakeAction());
        ModActions.registerAction(new DropAction());
    }

}
