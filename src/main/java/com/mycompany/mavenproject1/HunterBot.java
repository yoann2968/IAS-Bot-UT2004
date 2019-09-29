package com.mycompany.mavenproject1;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import cz.cuni.amis.introspection.java.JProp;
import cz.cuni.amis.pogamut.base.agent.navigation.IPathExecutorState;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.Pogamut;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.NavPoints;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.TabooSet;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.IUT2004Navigation;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.NavigationState;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004Navigation;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004PathAutoFixer;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004PathExecutorStuckState;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.stuckdetector.UT2004DistanceStuckDetector;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.stuckdetector.UT2004PositionStuckDetector;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.stuckdetector.UT2004TimeStuckDetector;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UT2004ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Move;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Rotate;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.SendMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Stop;
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

/**
 * Example of Simple Pogamut bot, that randomly walks around the map searching
 * for preys shooting at everything that is in its way.
 *
 * @author Rudolf Kadlec aka ik
 * @author Jimmy
 */
@AgentScoped
public class HunterBot extends UT2004BotModuleController<UT2004Bot> {

    /**
     * boolean switch if the current state is idle
     */
    @JProp
    public boolean idleState = true;
    /**
     * boolean switch if the current state is attack
     */
    @JProp
    public boolean attackState = false;
    /**
     * boolean switch if the current state is search
     */
    @JProp
    public boolean searchState = false;
    /**
     * boolean switch if the current state is hurt
     */
    @JProp
    public boolean hurtState = false;
    
    protected IUT2004Navigation navigationToUse;
    
    /**
     * Taboo set is working as "black-list", that is you might add some
     * NavPoints to it for a certain time, marking them as "unavailable".
     */
    protected TabooSet<NavPoint> tabooNavPoints;
    
    /**
     * Current navigation point we're navigating to.
     */
    protected NavPoint targetNavPoint;
    
    /**
     * boolean switch to activate engage behavior
     */
    @JProp
    public boolean shouldEngage = true;
    /**
     * boolean switch to activate pursue behavior
     */
    @JProp
    public boolean shouldPursue = true;
    /**
     * boolean switch to activate rearm behavior
     */
    @JProp
    public boolean shouldRearm = true;
    /**
     * boolean switch to activate collect health behavior
     */
    @JProp
    public boolean shouldCollectHealth = true;
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
            attackState=false;
            idleState=true;
        }
        if (enemy == null) {
            return;
        }
        if (enemy.getId().equals(event.getId())) {
            enemy = null;
        }
    }
    /**
     * Used internally to maintain the information about the bot we're currently
     * hunting, i.e., should be firing at.
     */
    protected Player enemy = null;
    /**
     * Item we're running for. 
     */
    protected Item item = null;
    /**
     * Taboo list of items that are forbidden for some time.
     */
    protected TabooSet<Item> tabooItems = null;
    
    private UT2004PathAutoFixer autoFixer;
    
	private static int instanceCount = 0;

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
         // initialize taboo set where we store temporarily unavailable navpoints
        tabooNavPoints = new TabooSet<NavPoint>(bot);

        // auto-removes wrong navigation links between navpoints
        autoFixer = new UT2004PathAutoFixer(bot, navigation.getPathExecutor(), fwMap, aStar, navBuilder);

        // IMPORTANT
        // adds a listener to the path executor for its state changes, it will allow you to 
        // react on stuff like "PATH TARGET REACHED" or "BOT STUCK"
        navigation.getPathExecutor().getState().addStrongListener(new FlagListener<IPathExecutorState>() {

            @Override
            public void flagChanged(IPathExecutorState changedValue) {
                pathExecutorStateChange(changedValue);
            }
        });
        
        nmNav.getPathExecutor().getState().addStrongListener(new FlagListener<IPathExecutorState>() {

            @Override
            public void flagChanged(IPathExecutorState changedValue) {
                pathExecutorStateChange(changedValue);
            }
        });
        
//        navigationAStar = new UT2004Navigation(bot, navigation.getPathExecutor(), aStar, navigation.getBackToNavGraph(), navigation.getRunStraight());          
//        navigationAStar.getLog().setLevel(navigationLogLevel);
//        
//        navigation.getLog().setLevel(navigationLogLevel);
//        
//        nmNav.setLogLevel(navigationLogLevel);
        // just set the name of the bot and his skill level, 1 is the lowest, 7 is the highest
    	// skill level affects how well will the bot aim
        return new Initialize().setName("Hunter-" + (++instanceCount)).setDesiredSkill(5);
    }
    
    /**
     * Path executor has changed its state 
     *
     * @param event
     */
    protected void pathExecutorStateChange(IPathExecutorState event) {
        switch (event.getState()) {
            case PATH_COMPUTATION_FAILED:
                // if path computation fails to whatever reason, just try another navpoint
                // taboo bad navpoint for 3 minutes
                tabooNavPoints.add(targetNavPoint, 180);
                break;

            case TARGET_REACHED:
                // taboo reached navpoint for 3 minutes
                tabooNavPoints.add(targetNavPoint, 180);
                break;

            case STUCK:
            	UT2004PathExecutorStuckState stuck = (UT2004PathExecutorStuckState)event;
//            	if (stuck.isGlobalTimeout()) {
//            		say("UT2004PathExecutor GLOBAL TIMEOUT!");
//            	} else {
//            		say(stuck.getStuckDetector() + " reported STUCK!");
//            	}
//            	if (stuck.getLink() == null) {
//            		say("STUCK LINK is NOT AVAILABLE!");
//            	} else {
//            		say("Bot has stuck while running from " + stuck.getLink().getFromNavPoint().getId() + " -> " + stuck.getLink().getToNavPoint().getId());
//            	}
            	
                // the bot has stuck! ... target nav point is unavailable currently
                tabooNavPoints.add(targetNavPoint, 60);
                break;

            case STOPPED:
                // path execution has stopped
                targetNavPoint = null;
                break;
        }
    }

    /**
     * Resets the state of the Hunter.
     */
    protected void reset() {
    	item = null;
        enemy = null;
        navigation.stopNavigation();
        itemsToRunAround = null;
        deaths++;
        act.act(new SendMessage().setGlobal(true).setText("Je suis mort " + deaths + " fois"));
        idleState = true;
        attackState=false;
        searchState=false;
        hurtState=false;
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
     * Randomly picks some navigation point to head to.
     *
     * @return randomly choosed navpoint
     */
    protected NavPoint getRandomNavPoint() {
        body.getCommunication().sendGlobalTextMessage("Picking new target navpoint.");

        // choose one feasible navpoint (== not belonging to tabooNavPoints) randomly
        NavPoint chosen = MyCollections.getRandomFiltered(getWorldView().getAll(NavPoint.class).values(), tabooNavPoints);

        if (chosen != null) {
            return chosen;
        }

        log.warning("All navpoints are tabooized at this moment, choosing navpoint randomly!");

        // ok, all navpoints have been visited probably, try to pick one at random
        return MyCollections.getRandom(getWorldView().getAll(NavPoint.class).values());
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
        navigationToUse = navigation;
        // Parcourt l'ensemble des états pour lancer l'action correspondant à l'état du bot
        if (idleState){
            config.setName("idleBot");
            pacing();
        }
        else if (attackState){
            config.setName("attackBot");
            stateEngage();
        }
        else if (searchState){
            log.info("SEARCH STATE");
        }
        else if (hurtState){
            
        }
        
        
        
//        // 1) do you see enemy? 	-> go to PURSUE (start shooting / hunt the enemy)
//        if (shouldEngage && players.canSeeEnemies() && weaponry.hasLoadedWeapon()) {
//            stateEngage();
//            return;
//        }
//
//        // 2) are you shooting? 	-> stop shooting, you've lost your target
//        if (info.isShooting() || info.isSecondaryShooting()) {
//            getAct().act(new StopShooting());
//        }
//
//        // 3) are you being shot? 	-> go to HIT (turn around - try to find your enemy)
//        if (senses.isBeingDamaged()) {
//            this.stateHit();
//            return;
//        }
//
//        // 4) have you got enemy to pursue? -> go to the last position of enemy
//        if (enemy != null && shouldPursue && weaponry.hasLoadedWeapon()) {  // !enemy.isVisible() because of 2)
//            this.statePursue();
//            return;
//        }
//
//        // 5) are you hurt?			-> get yourself some medKit
//        if (shouldCollectHealth && info.getHealth() < healthLevel) {
//            this.stateMedKit();
//            return;
//        }
//
//        // 6) if nothing ... run around items
//        stateRunAroundItems();
    }

    //////////////////
    // STATE ENGAGE //
    //////////////////
    protected boolean runningToPlayer = false;

    /**
     * Fired when bot see any enemy. <ol> <li> if enemy that was attacked last
     * time is not visible than choose new enemy <li> if enemy is reachable and the bot is far - run to him
     * <li> otherwise - stand still (kind a silly, right? :-)
     * </ol>
     */
    protected void stateEngage() {
        //log.info("Decision is: ENGAGE");
        //config.setName("Hunter [ENGAGE]");

        boolean shooting = false;
        double distance = Double.MAX_VALUE;
        pursueCount = 0;

        // 1) pick new enemy if the old one has been lost
        if (enemy == null || !enemy.isVisible()) {
            // pick new enemy
            enemy = players.getNearestVisiblePlayer(players.getVisibleEnemies().values());
            if (enemy == null) {
                log.info("Can't see any enemies... ???");
                return;
            }
        }

        // 2) stop shooting if enemy is not visible
        if (!enemy.isVisible()) {
	        if (info.isShooting() || info.isSecondaryShooting()) {
                // stop shooting
                getAct().act(new StopShooting());
            }
            runningToPlayer = false;
            attackState=false;
            searchState=true;
        } else {
        	// 2) or shoot on enemy if it is visible
	        distance = info.getLocation().getDistance(enemy.getLocation());
	        if (shoot.shoot(weaponPrefs, enemy) != null) {
	            log.info("Shooting at enemy!!!");
	            shooting = true;
	        }
        }

        // 3) if enemy is far or not visible - run to him
        int decentDistance = Math.round(random.nextFloat() * 800) + 200;
        if (!enemy.isVisible() || !shooting || decentDistance < distance) {
            if (!runningToPlayer) {
                navigation.navigate(enemy);
                runningToPlayer = true;
            }
        } else {
            runningToPlayer = false;
            navigation.stopNavigation();
        }
        
        item = null;
    }

    ///////////////
    // STATE HIT //
    ///////////////
    protected void stateHit() {
        //log.info("Decision is: HIT");
        bot.getBotName().setInfo("HIT");
        if (navigation.isNavigating()) {
        	navigation.stopNavigation();
        	item = null;
        }
        getAct().act(new Rotate().setAmount(32000));
    }

    //////////////////
    // STATE PURSUE //
    //////////////////
    /**
     * State pursue is for pursuing enemy who was for example lost behind a
     * corner. How it works?: <ol> <li> initialize properties <li> obtain path
     * to the enemy <li> follow the path - if it reaches the end - set lastEnemy
     * to null - bot would have seen him before or lost him once for all </ol>
     */
    protected void statePursue() {
        //log.info("Decision is: PURSUE");
        ++pursueCount;
        if (pursueCount > 30) {
            reset();
        }
        if (enemy != null) {
        	bot.getBotName().setInfo("PURSUE");
        	navigation.navigate(enemy);
        	item = null;
        } else {
        	reset();
        }
    }
    protected int pursueCount = 0;

    //////////////////
    // STATE MEDKIT //
    //////////////////
    protected void stateMedKit() {
        //log.info("Decision is: MEDKIT");
        Item itemActuel = items.getPathNearestSpawnedItem(ItemType.Category.HEALTH);
        if (itemActuel == null) {
        	log.warning("NO HEALTH ITEM TO RUN TO => ITEMS");
        	stateRunAroundItems();
        } else {
        	bot.getBotName().setInfo("MEDKIT");
        	navigation.navigate(itemActuel);
        	this.item = itemActuel;
        }
    }

    ////////////////////////////
    // STATE RUN AROUND ITEMS //
    ////////////////////////////
    protected List<Item> itemsToRunAround = null;

    protected void stateRunAroundItems() {
        //log.info("Decision is: ITEMS");
        //config.setName("Hunter [ITEMS]");
        if (navigation.isNavigatingToItem()) return;
        
        List<Item> interesting = new ArrayList<Item>();
        
        // ADD WEAPONS
        for (ItemType itemType : ItemType.Category.WEAPON.getTypes()) {
        	if (!weaponry.hasLoadedWeapon(itemType)) interesting.addAll(items.getSpawnedItems(itemType).values());
        }
        // ADD ARMORS
        for (ItemType itemType : ItemType.Category.ARMOR.getTypes()) {
        	interesting.addAll(items.getSpawnedItems(itemType).values());
        }
        // ADD QUADS
        interesting.addAll(items.getSpawnedItems(UT2004ItemType.U_DAMAGE_PACK).values());
        // ADD HEALTHS
        if (info.getHealth() < 100) {
        	interesting.addAll(items.getSpawnedItems(UT2004ItemType.HEALTH_PACK).values());
        }
        
        Item itemActuel = MyCollections.getRandom(tabooItems.filter(interesting));
        if (itemActuel == null) {
        	log.warning("NO ITEM TO RUN FOR!");
        	if (navigation.isNavigating()) return;
        	bot.getBotName().setInfo("RANDOM NAV");
        	navigation.navigate(navPoints.getRandomNavPoint());
        } else {
        	this.item = itemActuel;
        	log.log(Level.INFO, "RUNNING FOR: {0}", itemActuel.getType().getName());
        	bot.getBotName().setInfo("ITEM: " + itemActuel.getType().getName() + "");
        	navigation.navigate(itemActuel);        	
        }        
    }
    
    ////////////////
    // BOT PACING //
    ////////////////
    private void pacing(){
        if (navigationToUse.isNavigatingToNavPoint()) {
            // IS TARGET CLOSE & NEXT TARGET NOT SPECIFIED?
            while (navigationToUse.getContinueTo() == null && navigationToUse.getRemainingDistance() < 400) {
                // YES, THERE IS NO "next-target" SET AND WE'RE ABOUT TO REACH OUR TARGET!
            	NavPoint nextNavPoint = getRandomNavPoint();
                body.getCommunication().sendGlobalTextMessage("EXTENDING THE PATH: " + NavPoints.describe(nextNavPoint));
                navigationToUse.setContinueTo(nextNavPoint);
                // note that it is WHILE because navigation may immediately eat up "next target" and next target may be actually still too close!
            }

            // WE'RE NAVIGATING TO SOME NAVPOINT
//            logNavigation();
            if(players.canSeeEnemies()){
                idleState=false;
                attackState=true;
            }
            return;
        }
        
        // NAVIGATION HAS STOPPED ... 
        // => we need to choose another navpoint to navigate to
        // => possibly follow some players ...

        targetNavPoint = getRandomNavPoint();
        if (targetNavPoint == null) {
            log.severe("COULD NOT CHOOSE ANY NAVIGATION POINT TO RUN TO!!!");
            if (world.getAll(NavPoint.class).isEmpty()) {
                log.severe("world.getAll(NavPoint.class).size() == 0, there are no navigation ponits to choose from! Is exporting of nav points enabled in GameBots2004.ini inside UT2004?");
            }
            config.setName("NavigationBot [CRASHED]");
            return;
        }

//        talking = 0;
//
//        say("CHOOSING FIRST NAVPOINT TO RUN TO: " + NavPoints.describe(targetNavPoint));
        navigationToUse.navigate(targetNavPoint);
//        logNavigation();
    }

    ////////////////
    // BOT KILLED //
    ////////////////
    @Override
    public void botKilled(BotKilled event) {
    	reset();
    }

    ///////////////////////////////////
    public static void main(String args[]) throws PogamutException {
        // starts 3 Hunters at once
        // note that this is the most easy way to get a bunch of (the same) bots running at the same time        
    	new UT2004BotRunner(HunterBot.class, "Hunter").setMain(true).setLogLevel(Level.INFO).startAgents(2);
    }
}
