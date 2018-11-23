package edu.uic.apk;

import java.util.HashMap;

import edu.uic.apkSynth.DrugUser;

/**
 * Interface for implementations of classes that generate person data.
 * 
 * @author Eric Tatara
 *
 */
public interface IPersonGenerator {

  public DrugUser generate(HashMap<String, Object> params) throws Exception;
  
  public int catalogueSize(); //the size of the catalog (e.g. ~5000 for CNEP, ~12000 for CNEP+ normally)
}
