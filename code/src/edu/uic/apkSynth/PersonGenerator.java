package edu.uic.apkSynth;

import java.util.HashMap;

/**
 * Interface for implementations of classes that generate person data.
 * 
 * @author Eric Tatara
 *
 */
public interface PersonGenerator {

  public DrugUser generate(HashMap<String, Object> params) throws Exception;
  
  public int catalogueSize();
}
