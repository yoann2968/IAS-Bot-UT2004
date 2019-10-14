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
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.SendMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.StopShooting;

/**
 *
 * @author yoann
 */
public class AttackState implements BotState {
     @Override
    public void next(Bot bot) {
        if (bot.getInfo().getHealth() < bot.healthLevel) {
            bot.setState(new HurtState());
            bot.getAct().act(new StopShooting());
        }
        else if (!bot.enemy.isVisible()){
            bot.setState(new SearchState());
            bot.getAct().act(new StopShooting());
        }   
    }
 
    @Override
    public void printStatus(Bot bot) {
        bot.getLog().info("Je suis en état attack.");
        bot.getAct().act(new SendMessage().setGlobal(true).setText("Je suis en état attack."));
    }
    
    @Override
    public void logic(Bot bot){
           double distance = Double.MAX_VALUE;
           bot.pursueCount = 0;

           // 1) pick new enemy if the old one has been lost
           if (bot.enemy == null || !bot.enemy.isVisible()) {
               // pick new enemy
               if(bot.getPlayers().getNearestVisiblePlayer(bot.getPlayers().getVisibleEnemies().values()) !=null){
                    bot.enemy = bot.getPlayers().getNearestVisiblePlayer(bot.getPlayers().getVisibleEnemies().values());
               }else{
                   return;
               }
           }

           // 2) stop shooting if enemy is not visible
           if (!bot.enemy.isVisible()) {
               bot.getAct().act(new SendMessage().setGlobal(true).setText("Je te voit pas"));
               
                   if (bot.getInfo().isShooting() || bot.getInfo().isSecondaryShooting()) {
                   // stop shooting
                   bot.getAct().act(new StopShooting());
               }
               bot.runningToPlayer = false;   
           } else {
                   // 2) or shoot on enemy if it is visible
                   distance = bot.getInfo().getLocation().getDistance(bot.enemy.getLocation());
                   if (bot.getShoot().shoot(bot.getWeaponPrefs(), bot.enemy) != null) {
                       bot.getLog().info("Shooting at enemy!!!");
                   }
           }

           // 3) if enemy is far or not visible - run to him
           int decentDistance = Math.round(bot.getRandom().nextFloat() * 800) + 200;
           if (!bot.enemy.isVisible() || decentDistance < distance) {
               if (!bot.runningToPlayer) {
                   bot.getNavigation().navigate(bot.enemy);
                   bot.runningToPlayer = true;
//                   bot.setState(new SearchState());
               }
           } else {
               bot.runningToPlayer = false;
               bot.getNavigation().stopNavigation();
           }

           bot.item = null;
    }
}