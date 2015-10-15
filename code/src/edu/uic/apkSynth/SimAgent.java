package edu.uic.apkSynth;

import java.util.HashMap;

import org.joda.time.LocalDate;

public interface SimAgent {
	void setAgeStarted(double aget);
	double getAgeStarted();

	LocalDate getBirthDate();
	void setBirthDate(LocalDate b_day);

	HashMap<String,Object> getCharacteristics();	
	Object getCharacteristic(String c_name);
	void setCharacteristics(HashMap <String,Object> chrs);	

	void setDatabaseLabel(String label);
	String getDatabaseLabel();

	void   setDrugReceptDegree(double v);
	double getDrugReceptDegree();

	void   setDrugGivingDegree(double v);
	double getDrugGivingDegree();

	LocalDate getEntryDate();
	void setEntryDate(LocalDate b_day);

	void setFractionReceptSharing(double f);
	double getFractionReceptSharing();

	void setGender(Gender g);
	Gender getGender();
	
	void setHcvState(HCV_state stt);
	HCV_state getHcvState();

	void setInjectionIntensity(double v);
	double getInjectionIntensity();

	boolean isHcvABpos();  //needed for the display
	boolean isHcvRNA(); //needed for the display
	
	void setPreliminaryZip(String s);
	String getPreliminaryZip();
	
	void setRace(Race r);
	Race getRace();

	void receive_equipment_or_drugs();
	
	HarmReduction getSyringe_source();
	void setSyringe_source(HarmReduction s);
}
