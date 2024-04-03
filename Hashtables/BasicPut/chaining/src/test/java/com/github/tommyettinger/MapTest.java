package com.github.tommyettinger;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class MapTest {

    /**
     * Java 17
     * <br>
     * ObjectIntMap
     * Test: Insert 1M unique elements
     * 	 258445000
     * Test: Lookup 1M unique elements
     * 	 104969300
     * Test: Retrieve 1M unique elements
     * 	 95980800
     * <br>
     * HashMap
     * Test: Insert 1M unique elements
     * 	 171395900
     * Test: Lookup 1M unique elements
     * 	 71046800
     * Test: Retrieve 1M unique elements
     * 	 49839000
     * <br>
     * FlipMap
     * Test: Insert 1M unique elements
     * 	 147980200
     * Test: Lookup 1M unique elements
     * 	 50283700
     * Test: Retrieve 1M unique elements
     * 	 36840300
     * <br>
     * Java 21
     * <br>
     * Test: Insert 1M unique elements
     * 	 256933700
     * Test: Lookup 1M unique elements
     * 	 104400900
     * Test: Retrieve 1M unique elements
     * 	 104456600
     * <br>
     * HashMap
     * <br>
     * Test: Insert 1M unique elements
     * 	 139600300
     * Test: Lookup 1M unique elements
     * 	 47773700
     * Test: Retrieve 1M unique elements
     * 	 46442000
     * <br>
     * FlipMap
     * <br>
     * Test: Insert 1M unique elements
     * 	 131024100
     * Test: Lookup 1M unique elements
     * 	 41994100
     * Test: Retrieve 1M unique elements
     * 	 36732500
     */
    @Test
    public void mapSpeedTest(){
        Map<String, Integer> map = new HashMap<>();

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

    private void insertUniqueElements(Map<String, Integer> map, int count){
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

    private void lookUpElements(Map<String, Integer> map, String[] keys){
        int contained = 0;
        for(String s : keys){
            if(map.containsKey(s)) contained++;
        }
    }

    private void retrieveElements(Map<String, Integer> map, String[] keys){
        int sum = 0;
        for(String s : keys){
            sum += map.getOrDefault(s, 0);
        }
    }

}