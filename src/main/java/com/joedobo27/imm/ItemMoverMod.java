package com.joedobo27.imm;


import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.Properties;
import java.util.Random;


public class ItemMoverMod implements WurmServerMod, PreInitable, ServerStartedListener, Configurable{

    static final Random r = new Random();
    static int itemsPerSecond;

    @Override
    public void configure(Properties properties) {
        itemsPerSecond = Integer.parseInt(properties.getProperty("itemsPerSecond", Integer.toString(itemsPerSecond)));
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
