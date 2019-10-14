/*
 * Copyright (C) 2019 AMIS research group, Faculty of Mathematics and Physics, Charles University in Prague, Czech Republic
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mycompany.mavenproject1;

import com.mycompany.mavenproject1.statemachine.*;

import cz.cuni.amis.introspection.java.JProp;
import cz.cuni.amis.pogamut.base.agent.navigation.IPathExecutorState;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.TabooSet;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.IUT2004Navigation;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.NavigationState;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004PathAutoFixer;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004PathExecutorStuckState;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UT2004ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Rotate;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.SendMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.StopShooting;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotDamaged;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPoint;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Player;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerDamaged;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerKilled;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.collections.MyCollections;
import cz.cuni.amis.utils.exception.PogamutException;
import cz.cuni.amis.utils.flag.FlagListener;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 *
 * @author yoann
 */
public class Bot extends UT2004BotModuleController<UT2004Bot> {
    private BotState state = new IdleState();
    
    private static int instanceCount = 0;
    
    /**
     * Used internally to maintain the information about the bot we're currently
     * hunting, i.e., should be firing at.
     */
    public Player enemy = null;
    /**
     * Item we're running for. 
     */
    public Item item = null;
    /**
     * Taboo list of items that are forbidden for some time.
     */
    public TabooSet<Item> tabooItems = null;
    
    private UT2004PathAutoFixer autoFixer;
    
    public IUT2004Navigation navigationToUse;
    
    /**
     * Taboo set is working as "black-list", that is you might add some
     * NavPoints to it for a certain time, marking them as "unavailable".
     */
    public TabooSet<NavPoint> tabooNavPoints;
    
    /**
     * Current navigation point we're navigating to.
     */
    public NavPoint targetNavPoint;
    
    /**
     * how low the health level should be to start collecting health items
     */
    @JProp
    public int healthLevel = 75;
    /**
     * how many bot the hunter killed other bots (i.e., bot has fragged them /
     * got point for killing somebody)
     */
    @JProp
    public int frags = 0;
    /**
     * how many times the hunter died
     */
    @JProp
    public int deaths = 0;

    public boolean runningToPlayer = false;
    
    public int pursueCount = 0;
    
    public List<Item> itemsToRunAround = null;
    
    public BotState getState() {
        return state;
    }

    public void setState(BotState state) {
        this.state = state;
    }
    
    /**
     * Bot's preparation - called before the bot is connected to GB2004 and
     * launched into UT2004.
     */
    @Override
    public void prepareBot(UT2004Bot bot) {
        tabooItems = new TabooSet<Item>(bot);

        autoFixer = new UT2004PathAutoFixer(bot, navigation.getPathExecutor(), fwMap, aStar, navBuilder); // auto-removes wrong navigation links between navpoints

        // listeners        
        navigation.getState().addListener(new FlagListener<NavigationState>() {

            @Override
            public void flagChanged(NavigationState changedValue) {
                switch (changedValue) {
                    case PATH_COMPUTATION_FAILED:
                    case STUCK:
                        if (item != null) {
                            tabooItems.add(item, 10);
                        }
                        reset();
                        break;

                    case TARGET_REACHED:
                        reset();
                        break;
                }
            }
        });

        // FIRST we DEFINE GENERAL WEAPON PREFERENCES
        weaponPrefs.addGeneralPref(UT2004ItemType.LIGHTNING_GUN, true);                
        weaponPrefs.addGeneralPref(UT2004ItemType.SHOCK_RIFLE, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.MINIGUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.FLAK_CANNON, true);        
        weaponPrefs.addGeneralPref(UT2004ItemType.ROCKET_LAUNCHER, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.LINK_GUN, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.ASSAULT_RIFLE, true);        
        weaponPrefs.addGeneralPref(UT2004ItemType.BIO_RIFLE, true);

		
        // AND THEN RANGED
        weaponPrefs.newPrefsRange(80)
                .add(UT2004ItemType.SHIELD_GUN, true);

        weaponPrefs.newPrefsRange(1000)
                .add(UT2004ItemType.FLAK_CANNON, true)
                .add(UT2004ItemType.MINIGUN, true)
                .add(UT2004ItemType.LINK_GUN, false)
                .add(UT2004ItemType.ASSAULT_RIFLE, true);        

        weaponPrefs.newPrefsRange(4000)
                .add(UT2004ItemType.SHOCK_RIFLE, true)
                .add(UT2004ItemType.MINIGUN, false);

        weaponPrefs.newPrefsRange(100000)
                .add(UT2004ItemType.LIGHTNING_GUN, true)
                .add(UT2004ItemType.SHOCK_RIFLE, true);
    }
    
    /**
     * Here we can modify initializing command for our bot.
     *
     * @return
     */
    @Override
    public Initialize getInitializeCommand() {
        // just set the name of the bot and his skill level, 1 is the lowest, 7 is the highest
    	// skill level affects how well will the bot aim
        return new Initialize().setName("Terminator-" + (++instanceCount)).setDesiredSkill(1);
    }
    
    
    /**
     * Main method that controls the bot - makes decisions what to do next. It
     * is called iteratively by Pogamut engine every time a synchronous batch
     * from the environment is received. This is usually 4 times per second - it
     * is affected by visionTime variable, that can be adjusted in GameBots ini
     * file in UT2004/System folder.
     *
     */
    @Override
    public void logic() {  
        state.printStatus(this);
        state.logic(this);
        state.next(this);
        
//        // 1) do you see enemy? 	-> go to ATTACK (start shooting / hunt the enemy)
//        if ((getState().getClass() == IdleState.class || getState().getClass() == SearchState.class) && players.canSeeEnemies() && weaponry.hasLoadedWeapon()) {
//            setState(new AttackState());
//            navigation.stopNavigation();
//            return;
//        }

//        // 2) are you shooting? 	-> stop shooting, you've lost your target
//        if (info.isShooting() || info.isSecondaryShooting()) {
//            getAct().act(new StopShooting());
//        }

//        // 4) have you got enemy to pursue? -> go to the last position of enemy
//        if (enemy != null && getState().getClass() == SearchState.class && weaponry.hasLoadedWeapon()) {  // !enemy.isVisible() because of 2)
//            setState(new SearchState());
//            return;
//        }
        
    }

     /**
     * Resets the state of the Hunter.
     */
    public void reset() {
    	item = null;
        enemy = null;
        navigation.stopNavigation();
        itemsToRunAround = null;
        getAct().act(new StopShooting());
        setState(new IdleState());
    }
    
    @EventListener(eventClass=PlayerDamaged.class)
    public void playerDamaged(PlayerDamaged event) {
    	log.log(Level.INFO, "I have just hurt other bot for: {0}[{1}]", new Object[]{event.getDamageType(), event.getDamage()});
    }
    
    @EventListener(eventClass=BotDamaged.class)
    public void botDamaged(BotDamaged event) {
    	log.log(Level.INFO, "I have just been hurt by other bot for: {0}[{1}]", new Object[]{event.getDamageType(), event.getDamage()});
    }
    
    /**
     * {@link PlayerKilled} listener that provides "frag" counting + is switches
     * the state of the hunter.
     *
     * @param event
     */
    @EventListener(eventClass = PlayerKilled.class)
    public void playerKilled(PlayerKilled event) {
        if (event.getKiller().equals(info.getId())) {
            ++frags;
            log.log(Level.INFO, "nombre de kill : {0}", frags);
            act.act(new SendMessage().setGlobal(true).setText("nombre de kill : " + frags));
            reset();
        }
        if (enemy == null) {
            return;
        }
        if (enemy.getId().equals(event.getId())) {
            enemy = null;
        }
    }
    
    ////////////////
    // BOT KILLED //
    ////////////////
    @Override
    public void botKilled(BotKilled event) {
        deaths++;
    	reset();
    }
    
    ///////////////////////////////////
    public static void main(String args[]) throws PogamutException {
        // starts 3 Hunters at once
        // note that this is the most easy way to get a bunch of (the same) bots running at the same time        
    	new UT2004BotRunner(Bot.class, "Terminator").setMain(true).setLogLevel(Level.INFO).startAgents(2);
    }
}
