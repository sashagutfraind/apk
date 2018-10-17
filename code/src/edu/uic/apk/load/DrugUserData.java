package edu.uic.apk.load;

import edu.uic.apkSynth.Gender;
import edu.uic.apkSynth.HCV_state;
import edu.uic.apkSynth.HarmReduction;
import edu.uic.apkSynth.Race;

/**
 * Stores basic info on drug user instances loaded from a CNEP+ file.
 * 
 * @author Eric Tatara
 *
 */
public class DrugUserData {

  public double age;
  public double ageStarted;
  public Gender gender;
  public Race race;
  public HarmReduction syringeSource;
  public String zipCode;
  public HCV_state hcvState = HCV_state.unknown;
  public int drug_inDegree;
  public int drug_outDegree;
  public double injectionIntensity;
  public double fractionReceptSharing;
  public boolean early_career;
  public String dbLabel;

}
