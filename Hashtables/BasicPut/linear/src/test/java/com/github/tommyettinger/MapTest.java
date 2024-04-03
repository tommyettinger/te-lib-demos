package com.github.tommyettinger;

import com.badlogic.gdx.utils.ObjectIntMap;
import org.junit.Test;

public class MapTest {

    /**
     * ObjectIntMap
     * Test: Insert 1M unique elements
     * 	 258445000
     * Test: Lookup 1M unique elements
     * 	 104969300
     * Test: Retrieve 1M unique elements
     * 	 95980800
     */
    @Test
    public void mapSpeedTest(){
        ObjectIntMap<String> map = new ObjectIntMap<>();

        long time = System.nanoTime();
        insertUniqueElements(map, 1_000_000);
        final long uniqueInsertTime = System.nanoTime() - time;

        System.out.println("Test: Insert 1M unique elements");
        System.out.println("\t " + uniqueInsertTime);

        String[] keys = generateKeys(1_000_000);
        time = System.nanoTime();
        lookUpElements(map, keys);
        long lookUpTime = System.nanoTime() - time;

        System.out.println("Test: Lookup 1M unique elements");
        System.out.println("\t " + lookUpTime);

        time = System.nanoTime();
        retrieveElements(map, keys);
        long retrieveTime = System.nanoTime() - time;

        System.out.println("Test: Retrieve 1M unique elements");
        System.out.println("\t " + retrieveTime);
    }

    private void insertUniqueElements(ObjectIntMap<String> map, int count){
        for(int i = 0; i < count; i++){
            map.put(String.valueOf(i), i);
        }
    }
    private String[] generateKeys(int count){
        String[] strings = new String[count];
        for(int i = 0; i < count; i++) {
            // stupid magic; makes about half of all keys negative, to test looking up absent keys.
            strings[i] = (String.valueOf(i ^ (Double.doubleToRawLongBits(Math.random() - 0.5) >> 63)));
        }
        return strings;
    }

    private void lookUpElements(ObjectIntMap<String> map, String[] keys){
        int contained = 0;
        for(String s : keys){
            if(map.containsKey(s)) contained++;
        }
    }

    private void retrieveElements(ObjectIntMap<String> map, String[] keys){
        int sum = 0;
        for(String s : keys){
            sum += map.get(s, 0);
        }
    }

}