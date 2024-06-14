/*** VACCINE TRIAL SIMULATION ***/

/*** import & reformat summary data from model runs ***/

tempname trial 
foreach x of numlist 1/500 {
	import delimited "vaccine_experiment_49_Aug_29_2022_results\summarytable_`x'.csv", clear 
	keep cnep_data_source cnep_plus_file nep_data
	drop in 1/2
	rename cnep_data_source vname
	rename cnep_plus_file study
	rename nep_data placebo
	destring study, replace i("%")
	destring placebo, replace i("%")
	forval i = 1/10 {
	   local label`i'=vname[`i']
	}
	drop vname
	xpose, clear varname
	rename _varname arm
	forval i = 1/10 {
		label var v`i' "`label`i''"
	}
	gen trial = `x'
	order trial arm
	if trial ==1 {
	    save trials_summary.dta, replace
	}
	else if trial > 1 {
		save "`trial'", replace
		use trials_summary.dta, clear
	    append using "`trial'"
		save trials_summary.dta, replace
	}
}
rename v1 recruited
rename v2 received_all_doses
rename v3 infected_before_final
rename v4 dropout_rate
rename v5 still_in_followup
rename v6 chronic
rename v7 not_chronic
rename v8 attack_rate
rename v9 VE
rename v10 Fishers_pval
encode arm, gen(study_arm)

/** data has 2 records for each trial (1 study / 1 placebo) **/

/*** reshape to wide format ***/
reshape wide arm recruited received_all_doses infected_before_final dropout_rate still_in_followup chronic not_chronic attack_rate VE Fishers_pval, i(trial) j(study_arm)
**** drop duplicate VE & pval variables ****
drop VE1 Fishers_pval1
rename VE2 VE 
rename Fishers_pval2 Fishers_pval
lab var VE "VE computed in sim"
lab var Fishers_pval "Fishers pvalue computed in sim"

hist VE
hist Fishers_pval
gen fail = Fishers_pval>.05 
tab fail

drop arm1 arm2

save trials_VE60_Aug29_summary_wide.dta, replace
