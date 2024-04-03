package gbw.melange;

import com.badlogic.gdx.utils.ObjectIntMap;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SpeediestMapTest {

    @Test
    public void speediestMapTest(){
        Map<String, Integer> hashMap = new HashMap<>();
        ObjectIntMap<String> libgdxMap = new ObjectIntMap<>();

        double hashMapTimeA = System.nanoTime();
        insertUniqueElements(hashMap, 1_000_000);
        final double hashMapUniqueInsertTime = System.nanoTime() - hashMapTimeA;

        double libgdxMapTimeA = System.nanoTime();
        insertUniqueElements(libgdxMap, 1_000_000);
        final double libgdxUniqueInsertTime = System.nanoTime() - libgdxMapTimeA;

        System.out.println("Test: Insert 1m unique elements");
        System.out.println("\t Java HashMap: " + hashMapUniqueInsertTime);
        System.out.println("\t Libgdx Map: " + libgdxUniqueInsertTime);
        System.out.println("\t %-diff: " + (hashMapUniqueInsertTime / libgdxUniqueInsertTime));

        Set<String> hashMapKeyset = hashMap.keySet();
        hashMapTimeA = System.nanoTime();
        lookUpElements(hashMap, hashMapKeyset);
        final double hashMapLookUpTime = System.nanoTime() - hashMapTimeA;

        ObjectIntMap.Keys<String> libgdxKeyset = libgdxMap.keys();
        libgdxMapTimeA = System.nanoTime();
        lookUpElements(libgdxMap, libgdxKeyset);
        final double libgdxLookUpTime = System.nanoTime() - libgdxMapTimeA;

        System.out.println("Test: Lookup 1m unique elements");
        System.out.println("\t Java HashMap: " + hashMapLookUpTime);
        System.out.println("\t Libgdx Map: " + libgdxLookUpTime);
        System.out.println("\t %-diff: " + (hashMapLookUpTime / libgdxLookUpTime));

        hashMapTimeA = System.nanoTime();
        retrieveElements(hashMap, hashMapKeyset);
        final double hashMapRetrieveTime = System.nanoTime() - hashMapTimeA;

        libgdxKeyset = libgdxMap.keys();
        libgdxMapTimeA = System.nanoTime();
        retrieveElements(libgdxMap, libgdxKeyset);
        final double libgdxRetrieveTime = System.nanoTime() - libgdxMapTimeA;

        System.out.println("Test: Retrieve 1m unique elements");
        System.out.println("\t Java HashMap: " + hashMapRetrieveTime);
        System.out.println("\t Libgdx Map: " + libgdxRetrieveTime);
        System.out.println("\t %-diff: " + (hashMapRetrieveTime / libgdxRetrieveTime));
    }

    private void insertUniqueElements(Map<String, Integer> map, int count){
        for(int i = 0; i < count; i++){
            map.put("" + i, i);
        }
    }
    private void insertUniqueElements(ObjectIntMap<String> map, int count){
        for(int i = 0; i < count; i++){
            map.put("" + i, i);
        }
    }

    private void lookUpElements(Map<String, Integer> map, Set<String> keys){
        for(String s : keys){
            map.containsKey(s);
        }
    }
    private void lookUpElements(ObjectIntMap<String> map, ObjectIntMap.Keys<String> keys){
        for(String s : keys){
            map.containsKey(s);
        }
    }

    private void retrieveElements(Map<String, Integer> map, Set<String> keys){
        for(String s : keys){
            map.get(s);
        }
    }
    private void retrieveElements(ObjectIntMap<String> map, ObjectIntMap.Keys<String> keys){
        for(String s : keys){
            map.get(s,0);
        }
    }

}