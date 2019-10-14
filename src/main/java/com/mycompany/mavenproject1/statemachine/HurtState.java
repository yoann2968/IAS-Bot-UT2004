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
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.SendMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;

/**
 *
 * @author yoann
 */
public class HurtState implements BotState {
     @Override
    public void next(Bot bot) {
        Item itemActuel = bot.getItems().getPathNearestSpawnedItem(ItemType.Category.HEALTH);
        if(itemActuel == null){
            if (bot.getPlayers().canSeeEnemies()){
                bot.setState(new AttackState());
            }else{
                bot.setState(new SearchState());
            }
        }
    }
 
    @Override
    public void printStatus(Bot bot) {
        bot.getLog().info("Je suis en état blessé.");
        bot.getAct().act(new SendMessage().setGlobal(true).setText("Je suis en état blessé."));
    }
    
    @Override
    public void logic(Bot bot){
        Item itemActuel = bot.getItems().getPathNearestSpawnedItem(ItemType.Category.HEALTH);
        if (itemActuel == null) {
            bot.getLog().warning("NO HEALTH ITEM TO RUN TO => ITEMS");
        } else {
            bot.getNavigation().navigate(itemActuel);
            bot.item = itemActuel;
        }
    }
}