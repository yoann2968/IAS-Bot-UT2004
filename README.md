# Richer Tom et Spriet Yoann
# IAS-Bot-UT2004 sur machine personnel

Exigences minimales
-----------------------
La mise en place des exigences minimales à été faites en utilisant le design pattern State Machine.
Afin de mettre en place le design pattern, nous avons repris l'exemple Hunter-Bot de pogamut.

Nous avons créé un package afin de gérer tous les états liés au bot. On retrouve dans le package l’ensemble des classes permettant de définir les états du bot.
-	**BotState** correspondant à l’interface de nos états (tous les états implémentent BotState)

-	**IdleState** correspondant à l’état de base du bot :
le bot commence la partie dans l’état Idle et se déplace sur l’ensemble de la map de manière aléatoire. Si il rencontre un ennemi il passera en état Attack grâce à la méthode next(). Pour revenir à l’état Idle, le bot doit tuer son ennemi, mourir ou perdre son ennemi de vue et ne pas le retrouver. 

-	**AttackState** correspondant à l’état d’attaque :
comme vue précédemment le bot entre dans l’état Attack lorsqu’il voit un ennemi. Dans cet état il va attaquer son ennemi avec la meilleure arme qu’il possède. Il reste statique au moment du combat mais va suivre son ennemi si celui-ci se déplace. Si le bot subit trop de dégât il passe dans l’état Hurt et s’il perd de vue l’ennemi il passe en état Search.

-	**HurtState** correspondant à l’état du bot lorsqu’il va se soigner :
dans cet état le bot court vers la vie la plus proche. S’il n’a plus d’objet pour se soigner et qu’il voit un ennemi, il retourne en état Attack, sinon s’il ne voit pas d’ennemie il passe en état Search.

-	**SearchState** correspond à l’état du bot lorsqu’il cherche son ennemi :
le bot dans cet état va à la dernière position connue de l’ennemi afin de le chercher. Il passe un temps prédéterminé dans cet état de manière à revenir à l’état Idle s’il ne retrouve pas d’ennemi.

L’ensemble des états sont liée à la classe Bot qui implémente notre bot. On initialise l'état du bot dans la méthode prepareBot :
```java
@Override
public void prepareBot(UT2004Bot bot) {
  state =  new IdleState();
  ...
}
```
La méthode logic() permettant de décider des actions du bot a ensuite été modifier :
``` java
@Override
public void logic() {  
  state.printStatus(this);
  state.logic(this);
  state.next(this);   
}
```
