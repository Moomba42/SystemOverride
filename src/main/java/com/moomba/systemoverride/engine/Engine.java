package com.moomba.systemoverride.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Engine implements Runnable{

    final double TARGET_UPS = 30.0;//UPDATES PER SECOND
    final double TIME_BETWEEN_UPDATES = 1000000000 / TARGET_UPS;

    final int MAX_UPDATES_BEFORE_RENDER = 5;

    final double TARGET_FPS = 60;//FRAMES PER SECOND
    final double TARGET_TIME_BETWEEN_RENDERS = 1000000000 / TARGET_FPS;



    private boolean running;
    private boolean paused;
    private int fps = 60;

    private List<EntitySystem> systems;
    private List<Entity> entities;
    private Map<Family, List<Entity>> familyMap;

    private List<EntityListener> entityListeners;

    private Renderer renderer;
    private AssetLoader assetLoader;

    public Engine(){
        systems = new ArrayList<EntitySystem>();
        entities = new ArrayList<Entity>();
        familyMap = new HashMap<Family, List<Entity>>();

        entityListeners = new ArrayList<EntityListener>();
    }

    public void init(){
        renderer = new Renderer();
        assetLoader = new AssetLoader();

        systems.forEach(system -> system.initialize());
    }

    public void start(){
        new Thread(this).start();
    }

    public void run() {
        running = true;
        paused = false;

        double lastUpdateTime = System.nanoTime();
        double lastRenderTime;

        //Simple way of finding FPS.
        int lastSecondTime = (int)(lastUpdateTime / 1000000000);

        //Keep track of number of frames rendered each second.
        int frameCount = 0;

        while (running) {
            double now = System.nanoTime();
            int updateCount = 0;

            if (!paused) {
                //UPDATE//
                while (now - lastUpdateTime > TIME_BETWEEN_UPDATES && updateCount < MAX_UPDATES_BEFORE_RENDER) {
                    update();
                    lastUpdateTime += TIME_BETWEEN_UPDATES;
                    updateCount++;
                }

                //If for some reason an update takes forever, we don't want to do an insane number of catchups.
                if (now - lastUpdateTime > TIME_BETWEEN_UPDATES) {
                    lastUpdateTime = now - TIME_BETWEEN_UPDATES;
                }

                //RENDER//
                //float interpolation = Math.min(1.0f, (float)((now - lastUpdateTime) / TIME_BETWEEN_UPDATES));
                render();
                lastRenderTime = now;
                frameCount++;

                //UPDATE FPS COUNTER//
                int thisSecond = (int)(lastUpdateTime / 1000000000);
                if (thisSecond > lastSecondTime) {
                    fps = frameCount;
                    frameCount = 0;
                    lastSecondTime = thisSecond;
                }

                //WAIT//
                //Yield until its time to render. This prevents the cpu from overloading.
                while (now - lastRenderTime < TARGET_TIME_BETWEEN_RENDERS && now - lastUpdateTime < TIME_BETWEEN_UPDATES) {
                    Thread.yield();

                    //On some systems this can cause pretty bad stuttering but helps reducing the cpu usage.
                    try {
                        Thread.sleep(1);
                    } catch (Exception e) {}

                    now = System.nanoTime();
                }
            }
        }
    }

    private void update() {
        System.out.println("Update");
    }

    private void render() {
        System.out.println("Render");
    }

    public int getFPS(){
        return fps;
    }

    public void addSystem(EntitySystem system){
        if(systems.contains(system)) return;

        //add the system
        systems.add(system);

        //if this system's family is unique, group all entities that qualify for this family
        if(!familyMap.containsKey(system.getFamily()))
            groupEntitiesForFamily(system.getFamily());
    }

    public void removeSystem(EntitySystem system){
        if(!systems.contains(system)) return;

        //remove the system
        systems.remove(system);
    }

    private void groupEntitiesForFamily(Family family) {
        List<Entity> groupedEntities = new ArrayList<>();
        entities.forEach(entity -> {if(family.qualifies(entity)) groupedEntities.add(entity);});
        familyMap.put(family, groupedEntities);
    }

    public void addEntity(final Entity entity){
        //if the entity is already in the system
        if(entities.contains(entity)) return;

        //add the entity to the overall list
        entities.add(entity);

        //add the entity to families that the entity qualifies for
        familyMap.forEach((family, list)->{
            if(family.qualifies(entity))
                list.add(entity);
        });

        //notify the listeners
        entityListeners.forEach(l -> l.entityAdded(entity));
    }

    public List<Entity> getEntitiesFor(Family family){
        return familyMap.get(family);
    }

    public void removeEntity(Entity entity){
        if(!entities.contains(entity)) return;

        entities.remove(entity);
        entityListeners.forEach(l -> l.entityRemoved(entity));
    }


}