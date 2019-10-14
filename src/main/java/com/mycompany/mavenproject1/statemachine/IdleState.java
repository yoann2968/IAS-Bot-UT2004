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

package com.mycompany.mavenproject1.statemachine;

import com.mycompany.mavenproject1.Bot;

import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UT2004ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Rotate;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.SendMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
import cz.cuni.amis.utils.collections.MyCollections;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;


/**
 *
 * @author yoann
 */
public class IdleState implements BotState {
    
     @Override
    public void next(Bot bot) {
         // 1) do you see enemy? 	-> go to ATTACK (start shooting / hunt the enemy)
        if (bot.getPlayers().canSeeEnemies() /*&& bot.getWeaponry().hasLoadedWeapon()*/) {
            bot.setState(new AttackState());
            bot.getNavigation().stopNavigation();
        }
    }
 
    @Override
    public void printStatus(Bot bot) {
        bot.getLog().info("Je suis en état idle.");
        bot.getAct().act(new SendMessage().setGlobal(true).setText("Je suis en état idle."));
    }
    
    ////////////////////////////
    // STATE RUN AROUND ITEMS //
    ////////////////////////////
    public void logic(Bot bot) {
        //log.info("Decision is: ITEMS");
        //config.setName("Hunter [ITEMS]");
        
        if(bot.getSenses().isBeingDamaged()){
            if (bot.getNavigation().isNavigating()) {
        	bot.getNavigation().stopNavigation();
        	bot.item = null;
            }
            bot.getAct().act(new Rotate().setAmount(32000));
        }
        
        if(bot.getPlayers().canSeeEnemies()){
//            bot.setState(new AttackState());
//            bot.getNavigation().stopNavigation();
            return;
        }
        
        if (bot.getNavigation().isNavigatingToItem()) return;
        
        List<Item> interesting = new ArrayList<Item>();
        
        // ADD WEAPONS
        for (ItemType itemType : ItemType.Category.WEAPON.getTypes()) {
        	if (!bot.getWeaponry().hasLoadedWeapon(itemType)) interesting.addAll(bot.getItems().getSpawnedItems(itemType).values());
        }
        // ADD ARMORS
        for (ItemType itemType : ItemType.Category.ARMOR.getTypes()) {
        	interesting.addAll(bot.getItems().getSpawnedItems(itemType).values());
        }
        // ADD QUADS
        interesting.addAll(bot.getItems().getSpawnedItems(UT2004ItemType.U_DAMAGE_PACK).values());
        // ADD HEALTHS
        if (bot.getInfo().getHealth() < 100) {
        	interesting.addAll(bot.getItems().getSpawnedItems(UT2004ItemType.HEALTH_PACK).values());
        }
        
        Item itemActuel = MyCollections.getRandom(bot.tabooItems.filter(interesting));
        if (itemActuel == null) {
        	bot.getLog().warning("NO ITEM TO RUN FOR!");
        	if (bot.getNavigation().isNavigating()) return;
        	bot.getNavigation().navigate(bot.getNavPoints().getRandomNavPoint());
        } else {
        	bot.item = itemActuel;
        	bot.getLog().log(Level.INFO, "RUNNING FOR: {0}", itemActuel.getType().getName());
        	bot.getNavigation().navigate(itemActuel);      
        }        
    }
}
