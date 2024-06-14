/*** Program to import & analyze vaccine trial simulation data ****/
/*** Compute outcomes and post results ****/

log using trials_VE50_May22.txt, text replace

/*** Vaccine simulation trials: VE 50 ***/
/*** Monthly RNA testing: 22 months + 9 for chronicity ***/
/*** Total trial length 33 months ***/
/*** Observing infections for 22 months (20 months following last dose) ***/
/*** Diagnosing chronic infection 6 months after conversion ***/

log off

**** set up temporary file to post output ***
tempname sims
tempfile VEsims
postfile `sims' str3 trial n_arm1 n_arm2 n_fem1 n_fem2 npp_arm1 npp_arm2 convert_male1 convert_male2 convert_female1 convert_female2 rr_convert lb_convert ub_convert rr_convert_pval ir_acute chronic_arm1 chronic_arm2 chronic_male1 chronic_male2 chronic_female1 chronic_female2 ir_arm1 ir_arm2 cases_arm1 cases_arm2 time_arm1 time_arm2 irr_chronic lb_irr ub_irr irr_pval pfe lb_pfe ub_pfe pfp mhrr_chi2 mhrr_pval irr_chronic_pp lb_irr_pp ub_irr_pp irr_pval_pp pfe_pp lb_pfe_pp ub_pfe_pp pfp_pp mhrr_pval_pp irr_chronic_exp lb_irr_exp ub_irr_exp irr_pval_exp pfe_exp lb_pfe_exp ub_pfe_exp pfp_exp mhrr_pval_exp cox_hr0 hr0_ll hr0_ul phtest pval_sexx hr_female hr_male cox_hr0_exp hr0_ll_exp hr0_ul_exp cox_hr1 hr1_ll hr1_ul cox_hr2 hr2_ll hr2_ul hr0_clear hr0_clear_ll hr0_clear_ul phtest_clear n_convert1 n_convert2 irr_acute irr_acute_pval resolved_arm1 resolved_arm2 notresolved_arm1 notresolved_arm2 resolved_male1 resolved_male2 notresolved_male1 notresolved_male2 resolved_female1 resolved_female2 notresolved_female1 notresolved_female2 restime_arm1 restime_arm2 irr_chronic_pval VE_itt VE_pp VE_exp using `VEsims'

**** read in data *****

/*** for each simulated trial ***/
foreach x of numlist 1/500 {

/* import data from event spreadsheet */
import delimited "vaccine_experiment_46_May_13_2022_results\events_history_`x'.csv", clear
drop v1
renvars, lower
rename v# t#, renumber
gen id = 1000 + _n
order id agent

/* encode RNA results to numeric variables */
label def hcvrna 0 "RNA_negative" 1 "RNA_positive" 8 "lost_to_followup" 9 "padding"
local i = 0
foreach v of varlist t1-t33 {
	local i = `i'+1
	encode `v' , gen(hcvrna`i') label(hcvrna)
	}
drop t1-t33
egen hcv_count_all = anycount(hcvrna*), v(1)
lab var hcv_count_all "# periods observed HCV positive (ALL periods)"


/* hcv period while RNA positive */
foreach i of numlist 1/33 {
	gen hcv_period`i'=`i' if hcvrna`i'==1
	gen lost_period`i'=`i' if hcvrna`i'==8
	gen ended_period`i'=`i' if hcvrna`i'==9
	}
egen period_lost = rowfirst(lost_period*)
lab var period_lost "Period lost to followup"
egen period_ended = rowfirst(ended_period*)
**# fix end_period when LTFU
replace period_ended = (period_lost-1) if period_lost < period_ended
replace period_ended = (period_ended-1) if period_lost == .
lab var period_ended "Period of last observation"

egen first_converted = rowfirst(hcv_period*)
lab var first_converted "Period first infected"

gen infected_before_final = first_converted <=2
lab var infected_before_final "HCV conversion </= 8 weeks after start"
gen per_protocol = doses==2 & infected_before_final==0

egen hcvlast = rowlast(hcv_period*)
**# dx on first +6
gen dx_period = first_converted + 6
lab var dx_period "period to diagnose chronic status"

**# count infections in first 22 months 
egen hcv_convert = anymatch(hcvrna1-hcvrna22), v(1)
egen periods_observed = anycount(hcvrna1-hcvrna33), v(0 1)
egen dropout = anymatch(hcvrna*), v(8)
lab var dropout "Lost to followup"
lab var periods_observed "Number of periods observed"

gen late_end = hcv_convert==0 & periods_observed>24
gen early_end = dropout==0 & hcv_convert==0 & periods_observed<22

drop hcv_period* lost_period* ended_period*



/* reshape to long format to compute time of conversion */
reshape long hcvrna, i(id) j(period)
drop if hcvrna==8 | hcvrna==9
xtset id period

/* mark period converted and resolved */
gen conversion = 1 if hcvrna==1 & (l.hcvrna==0 | period==1)
gen resolved = 1 if hcvrna==0 & l.hcvrna==1
recode conversion resolved (.=0)

gen resolv = period if hcvrna==0 & l.hcvrna==1
bys id: egen first_resolved = min(resolv)
bys id: egen last_resolved = max(resolv)
drop resolv

bys id: egen hcv_converted = max(conversion)
bys id: egen hcv_resolved = max(resolved)
bys id: egen hcv_count = total(conversion)
lab var hcv_count "number of periods infected during observation (months 1-24)"
bys id: egen hcv_clear_count = total(resolved)
lab var hcv_clear_count "number of times cleared infection during trial (months 1-24)"

**# count as chronic if resolved late
gen hcv_chronic = hcv_converted==1 & (first_resolved>dx_period) & (hcvlast>= dx_period)  
lab var hcv_chronic "Chronic HCV Case"


/** back to wide format **/
reshape wide hcvrna conversion resolved, i(id) j(period)

tostring zip , replace
gen chicago = substr(zip,1,3)=="606"
encode trial_arm, g(arm) 
encode gender, gen(sex)
encode race, g(race_ethn)
encode syringe_source, g(source)
encode chicago_community_name, g(community)
drop gender race syringe_source chicago_community_name

gen age30 = age>=30
lab var age30 "age 30 or older"
recode sex (1=1) (2=0), g(female)
lab var female "Sex Female"
qui: tab race_ethn, gen(race)
recode arm (2=1) (1=0), g(study_arm)

gen exposed = first_converted<.
gen ltfu_not_resolved = hcv_converted==1 & hcv_chronic==0 & hcv_resolved==0


/***** analyze trial data ***********/

log on

disp ""
disp "========================================================================="
disp "                           TRIAL" "`x'"                                "
disp "========================================================================="

tab trial_arm, matcell(armobs)
global n_arm1 = armobs[1,1]
global n_arm2 = armobs[2,1]

tab trial_arm female, row matcell(nfem)
global n_fem1 = nfem[1,2]
global n_fem2 = nfem[2,2]

tab trial_arm doses, row
tab trial_arm rna_at_start_of_trial, row
tab trial per_protocol, row matcell(ppobs)
global npp_arm1 = ppobs[1,2]
global npp_arm2 = ppobs[2,2]

disp ""
disp "****** compare incident infection rates by study arm ******"
tab trial_arm hcv_converted, row matcell(converted)
global n_convert1 = converted[1,2]
global n_convert2 = converted[2,2]
disp "Males"
tab trial_arm hcv_converted if female==0, row matcell(convert_male)
global convert_male1 = convert_male[1,2]
global convert_male2 = convert_male[2,2]
disp "Females"
tab trial_arm hcv_converted if female==1, row matcell(convert_female)
global convert_female1 = convert_female[1,2]
global convert_female2 = convert_female[2,2]

disp "***** incident infection prevalence risk difference by study arm *****"
cs hcv_converted study_arm
global rr_convert = r(rr)
global lb_convert = r(lb_rr)
global ub_convert = r(ub_rr)
global rr_convert_pval = r(p)

disp ""
disp "****** survival analysis of incident infection ******"
gen time = periods_observed
stset time , f(hcv_converted==1)  id(id)
/* scale to year */
streset, scale(12)
stptime
global ir_acute = r(rate)
stir arm
global irr_acute = r(irr)
global irr_acute_pval = r(p_twosided_midp)



disp ""
disp "****** chronic case count ******"
tab trial_arm hcv_chronic, row matcell(chronic)
global chronic_arm1 = chronic[1,2]
global chronic_arm2 = chronic[2,2]
disp " *** male ***"
tab trial_arm hcv_chronic if female==0, row matcell(chronic_male)
global chronic_male1 = chronic_male[1,2]
global chronic_male2 = chronic_male[2,2]
disp " *** female ***"
tab trial_arm hcv_chronic if female==1, row matcell(chronic_female)
global chronic_female1 = chronic_female[1,2]
global chronic_female2 = chronic_female[2,2]

disp ""
disp "****** non-chronic case count ******"
tab trial_arm hcv_resolved, row matcell(resolved)
global resolved_arm1 = resolved[1,2]
global resolved_arm2 = resolved[2,2]
tab trial_arm ltfu_not_resolved, row matcell(notresolved)
global notresolved_arm1 = notresolved[1,2]
global notresolved_arm2 = notresolved[2,2]

disp " *** male ***"
tab trial_arm hcv_resolved if female==0, row matcell(resolved_male)
global resolved_male1 = resolved_male[1,2]
global resolved_male2 = resolved_male[2,2]
tab trial_arm ltfu_not_resolved if female==0, row matcell(notresolved_male)
global notresolved_male1 = notresolved_male[1,2]
global notresolved_male2 = notresolved_male[2,2]

disp " *** female ***"
tab trial_arm hcv_resolved if female==1, row matcell(resolved_female)
global resolved_female1 = resolved_female[1,2]
global resolved_female2 = resolved_female[2,2]
tab trial_arm ltfu_not_resolved if female==1, row matcell(notresolved_female)
global notresolved_female1 = notresolved_female[1,2]
global notresolved_female2 = notresolved_female[2,2]



disp ""
disp "****** ITT ******"
tab trial_arm hcv_chronic , row
cs hcv_chronic study_arm 
global VE_itt = r(afe)

disp ""
disp "****** per protocol ******"
tab trial_arm hcv_chronic if per_protocol==1 , row
cs hcv_chronic study_arm if per_protocol==1
global VE_pp = r(afe)

disp ""
disp "****** among exposed ******"
tab trial_arm hcv_chronic if exposed==1 , row
cs hcv_chronic study_arm if exposed==1
global VE_exp = r(afe)

disp ""
disp "****** survival analysis of chronic infection ******"
**** end time for chronic cases at time of conversion
replace time = first_converted if hcv_chronic==1
stset time , f(hcv_chronic==1)  id(id)
/* scale to year */
streset, scale(12)
stptime if arm==1
global ir_arm1 = r(rate)
global time_arm1 = r(ptime)
global cases_arm1 = r(failures)
stptime if arm==2
global ir_arm2 = r(rate)
global time_arm2 = r(ptime)
global cases_arm2 = r(failures)


disp ""
disp "****** incidence risk ratio ******"
stir arm
global irr_chronic = r(irr)
global irr_chronic_pval = r(p_twosided_midp)
global lb_irr = r(lb_irr)
global ub_irr = r(ub_irr)
global irr_pval = r(p_twosided_exact)
global pfe = r(afe)
global lb_pfe = r(lb_afe)
global ub_pfe = r(ub_afe)
global pfp = r(afp)

disp ""
disp "MH adjusted for sex"
stmh arm sex
global mhrr_sex = r(rratio)
global mhrr_chi2 = r(chi2)
global mhrr_pval = r(p)

disp ""
disp "per protocol"
stir arm if per_protocol==1
global irr_chronic_pp = r(irr)
global lb_irr_pp = r(lb_irr)
global ub_irr_pp = r(ub_irr)
global irr_pval_pp = r(p_twosided_exact)
global pfe_pp = r(afe)
global lb_pfe_pp = r(lb_afe)
global ub_pfe_pp = r(ub_afe)
global pfp_pp = r(afp)

stmh arm sex if per_protocol==1
global mhrr_sex_pp = r(rratio)
global mhrr_chi2_pp = r(chi2)
global mhrr_pval_pp = r(p)

disp ""
disp "among exposed"
stir arm if exposed==1
global irr_chronic_exp = r(irr)
global lb_irr_exp = r(lb_irr)
global ub_irr_exp = r(ub_irr)
global irr_pval_exp = r(p_twosided_exact)
global pfe_exp = r(afe)
global lb_pfe_exp = r(lb_afe)
global ub_pfe_exp = r(ub_afe)
global pfp_exp = r(afp)

stmh arm sex if exposed==1
global mhrr_sex_exp = r(rratio)
global mhrr_chi2_exp = r(chi2)
global mhrr_pval_exp = r(p)



disp ""
disp "****** hazard ratio ******"
streset, scale(1)
stcox i.arm , nolog
global hr0 = r(table)[1,2]
global hr0_ll = r(table)[5,2]
global hr0_ul = r(table)[6,2]
disp ""
disp "****** test proportional hazards ******"
estat phtest
global phtest = r(p)

disp ""
disp "****** test sex difference ******"
stcox i.arm##i.sex , nolog
global pval_sexx = r(table)[4,8]
global hr_female = r(table)[1,2]
lincom 2.arm + 2.arm#2.sex, eform
global hr_male = r(estimate)

disp ""
disp "****** among exposed ******"
stcox i.arm if exposed==1, nolog 
global hr0_exp = r(table)[1,2]
global hr0_ll_exp = r(table)[5,2]
global hr0_ul_exp = r(table)[6,2]


disp ""
disp "****** adjusted hazard ratio ******"
stcox i.study_arm i.sex i.race_ethn age drug_out_degree hcv_friend_preval ///
	daily_injection_intensity mean_age_total_network fraction_recept_sharing, ///
	vce(robust) nolog
global hr1 = r(table)[1,2]
global hr1_ll = r(table)[5,2]
global hr1_ul = r(table)[6,2]

disp ""
disp "****** adjusted hazard ratio stratified by sex ******"
stcox i.study_arm i.race_ethn age drug_out_degree hcv_friend_preval ///
	daily_injection_intensity mean_age_total_network fraction_recept_sharing, ///
	vce(robust) strata(sex) nolog
global hr2 = r(table)[1,2]
global hr2_ll = r(table)[5,2]
global hr2_ul = r(table)[6,2]



disp ""
disp "***** time to clearance ******"
gen restime = first_resolved
replace restime = periods_observed if first_resolved==.
stset restime , f(hcv_resolved==1) origin(first_converted)
stdes
tab arm _st
tab hcv_resolved arm, matcell(resolved)
global zero_resolved = resolved[2,1]==0

mean restime if hcv_resolved==1, over(arm)
global restime_arm1 = r(table)[1,1]
global restime_arm2 = r(table)[1,2]


/* scale to year */
streset, scale(12)
stsum
strate arm

streset, scale(1)
if $zero_resolved ~= 1 {
	stcox i.arm if hcv_resolved==1
	global hr0_clear = r(table)[1,2]
	global hr0_clear_ll = r(table)[5,2]
	global hr0_clear_ul = r(table)[6,2]
	disp ""
	disp "****** test proportional hazards ******"
	estat phtest
	global phtest_clear = r(p)
}
else {
	display "Zero resolved in placebo group"
}






log off

**** post outcomes ****

post `sims' ("`x'") ($n_arm1) ($n_arm2) ($n_fem1) ($n_fem2) ($npp_arm1) ($npp_arm2) ($convert_male1) ($convert_male2) ($convert_female1) ($convert_female2) ($rr_convert) ($lb_convert) ($ub_convert) ($rr_convert_pval) ($ir_acute) ($chronic_arm1) ($chronic_arm2) ($chronic_male1) ($chronic_male2) ($chronic_female1) ($chronic_female2) ($ir_arm1) ($ir_arm2) ($cases_arm1) ($cases_arm2) ($time_arm1) ($time_arm2) ($irr_chronic) ($lb_irr) ($ub_irr) ($irr_pval) ($pfe) ($lb_pfe) ($ub_pfe) ($pfp) ($mhrr_chi2) ($mhrr_pval) ($irr_chronic_pp) ($lb_irr_pp) ($ub_irr_pp) ($irr_pval_pp) ($pfe_pp) ($lb_pfe_pp) ($ub_pfe_pp) ($pfp_pp) ($mhrr_pval_pp) ($irr_chronic_exp) ($lb_irr_exp) ($ub_irr_exp) ($irr_pval_exp) ($pfe_exp) ($lb_pfe_exp) ($ub_pfe_exp) ($pfp_exp) ($mhrr_pval_exp) ($hr0) ($hr0_ll) ($hr0_ul) ($phtest) ($pval_sexx) ($hr_female) ($hr_male) ($hr0_exp) ($hr0_ll_exp) ($hr0_ul_exp) ($hr1) ($hr1_ll) ($hr1_ul) ($hr2) ($hr2_ll) ($hr2_ul) ($hr0_clear) ($hr0_clear_ll) ($hr0_clear_ul) ($phtest_clear) ($n_convert1) ($n_convert2) ($irr_acute) ($irr_acute_pval) ($resolved_arm1) ($resolved_arm2) ($notresolved_arm1) ($notresolved_arm2) ($resolved_male1) ($resolved_male2) ($notresolved_male1) ($notresolved_male2) ($resolved_female1) ($resolved_female2) ($notresolved_female1) ($notresolved_female2) ($restime_arm1) ($restime_arm2) ($irr_chronic_pval) ($VE_itt) ($VE_pp) ($VE_exp) 
}

log close


postclose `sims'
use `VEsims', clear
destring trial, replace
lab var ir_acute "Incidence rate of HCV (acute) infection"
lab var irr_acute "Incidence rate ratio of HCV (acute) infection: study arm vs. placebo"
lab var irr_chronic "Incidence rate ratio of chronic HCV: study arm vs. placebo"
lab var irr_chronic_exp "IRR for chronic HCV among incident cases: study arm vs. placebo"
lab var rr_convert "Risk ratio of acute HCV infection: study arm vs. placebo"
lab var rr_convert_pval "P-value for acute HCV risk ratio"
lab var VE_pp "eVE per protocol from risk ratio"
lab var VE_itt "eVE (ITT) from risk ratio"
lab var VE_exp "eVE among exposed from risk ratio"
lab var pfe "eVE from inicidence rate ratio"
lab var pfe_pp "eVE per protocol from incidence rate ratio"
lab var pfe_exp "eVE among exposed from incidence rate ratio"
lab var cox_hr0 "Hazard ratio for chronic infection: study arm vs. placebo"
lab var cox_hr0_exp "Hazard ratio for chronic infection among incident cases: study arm vs. placebo"
lab var cox_hr1 "Adjusted HR for chronic infection: study arm vs. placebo"
lab var cox_hr2 "Sex-stratified adjusted HR for chronic infection: study arm vs. placebo"
lab var restime_arm1 "Duration of infection: placebo"
lab var restime_arm2 "Duration of infection: vaccine"

save VEsims500_VE50_May22.dta, replace





