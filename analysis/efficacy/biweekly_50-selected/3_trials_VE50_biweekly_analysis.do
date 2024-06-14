/**** VACCINE TRIAL SIMULATION ****/
/**** ANALYSIS OF 500 TRIALS ****/

/*** use computed summary statistics for 500 trials ***/
use VEsims500_VE60_mod_Aug29, clear

lab var VE_pp "eVE per protocol"
lab var pfe "Estimated Prevented Fraction (eVE)"
gen aVE = .5
gen eVE = pfe
lab var aVE "Assumed vaccine efficacy (aVE)"
lab var eVE "Estimated vaccine efficacy (eVE)"

hist pfe, title("Distribution of eVE: model aVE 50%†") w(.05) xlab(0(.1).8) norm lc(gray) fc(gray*.5) lw(vthin) graphregion(c(white)) ysc(titlegap(2)) xsc(titlegap(2)) note("†with biweekly testing", si(medium)) caption("(c)", si(large)) percent
graph export VEplot_Fig3c.jpg, replace

summ pfe
ci means pfe, level(99)

gen acute_arm1 = convert_male1 + convert_female1
gen acute_arm2 = convert_male2 + convert_female2

gen pct_chronic1 = (chronic_arm1 / n_arm1)*100
gen pct_chronic2 = (chronic_arm2 / n_arm2)*100

gen phtest_fail = phtest<.05

gen fail_pval_midp = irr_chronic_pval > .05
gen fail_pval_exact = irr_pval > .05
lab var fail_pval_midp "p-value for IRR  > .05"
lab var fail_pval_exact "exact p-value for IRR > .05"

gen rr_convert_gt1 = rr_convert > 1
gen pct_convert1 = n_convert1/n_arm1
gen pct_convert2 = n_convert2/n_arm2

summ *

gen total_N = n_arm1 + n_arm2
foreach v of varlist rr* ir* hr* lb* ub* cox* {
	gen log_`v' = ln(`v')
}

gen exposed_arm1 = round(pct_convert1 * n_arm1,1)
gen exposed_arm2 = round(pct_convert2 * n_arm2,1)
gen exposed_total = exposed_arm1 + exposed_arm2

gen pct_chronic_exposed1 = chronic_arm1 / exposed_arm1
gen pct_chronic_exposed2 = chronic_arm2 / exposed_arm2
gen pct_resolved_exposed1 = (resolved_arm1 / exposed_arm1)
gen pct_resolved_exposed2 = (resolved_arm2 / exposed_arm2)
gen pct_notresolved_exposed1 = (notresolved_arm1 / exposed_arm1)
gen pct_notresolved_exposed2 = (notresolved_arm2 / exposed_arm2)


gen pct_fem1 = n_fem1/n_arm1
gen pct_fem2 = n_fem2/n_arm2

summ n_arm1 n_convert1 chronic_arm1 pct_chronic_exposed1 n_arm2 n_convert2 chronic_arm2 pct_chronic_exposed2, sep(4)

gen pct_chron_exp_male1 = chronic_male1/convert_male1
gen pct_chron_exp_female1 = chronic_female1/convert_female1
gen pct_chron_exp_male2 = chronic_male2/convert_male2
gen pct_chron_exp_female2 = chronic_female2/convert_female2

gen pct_resolv_exp_male1 = resolved_male1/convert_male1
gen pct_resolv_exp_female1 = resolved_female1/convert_female1
gen pct_resolv_exp_male2 = resolved_male2/convert_male2
gen pct_resolv_exp_female2 = resolved_female2/convert_female2

gen pct_nresolv_exp_male1 = notresolved_male1/convert_male1
gen pct_nresolv_exp_female1 = notresolved_female1/convert_female1
gen pct_nresolv_exp_male2 = notresolved_male2/convert_male2
gen pct_nresolv_exp_female2 = notresolved_female2/convert_female2

lab var pct_chronic1 "Prevalence of chronic HCV in placebo group"
lab var pct_chronic2 "Prevalence of chronic HCV in treatment group"
lab var rr_convert "Acute HCV Risk Ratio"


log using "VE60_Aug29_stats", replace

tabstat rr_convert pct_chronic1 ir_arm1 irr_chronic pfe fail* cox_hr0, stat(n mean sd min max p5 p95) col(stat) varwidth(25)
ci mean rr_convert pct_chronic1 ir_arm1 irr_chronic pfe fail* cox_hr0, sep(0)
tabstat pct*1, stat(n mean sd min max p5 p95) col(stat) varwidth(25)
tabstat pct*2, stat(n mean sd min max p5 p95) col(stat) varwidth(25)

log close


des pfe pfe_exp VE*
summ pfe pfe_exp VE*

*** check ci symmetry
gen dlb = log_irr_chronic - log_lb_irr
gen dub = log_ub_irr - log_irr_chronic
gen diff = dlb - dub

/**** summarize stats with meta-analysis ****/
/**** analyze outcomes with meta-regression ****/

log using "VE60_Aug29_meta-summary", replace

** check ci symmetry 
summ diff, d

** common effect model
meta set log_irr_chronic log_lb_irr log_ub_irr, studylabel(trial) studysize(total_N) common civartolerance(.05)
meta sum, common eform nostudies 
meta sum, common transform(efficacy) nostudies 

** exposed
meta set log_irr_chronic_exp log_lb_irr_exp log_ub_irr_exp, studylabel(trial) studysize(total_N) common civartolerance(.05)
meta sum, common eform nostudies 
meta sum, common transform(efficacy) nostudies 

** random effects model
meta set log_irr_chronic log_lb_irr log_ub_irr, studylabel(trial) studysize(total_N) civartolerance(.05)
meta regress rr_convert
meta regress ir_acute
meta regress pct_chronic1
meta regress ir_acute rr_convert
* predicted log IRR 
margins, at(rr_convert=(0.6 0.8 1 1.2 1.4 1.6))
margins, dydx(rr_convert)
predict log_pred_irr_meta
gen pred_irr_meta=exp(log_pred_irr_meta)
gen pred_VE = 1-pred_irr_meta
lab var pred_VE "Predicted vaccine efficacy"

** among acute infected
meta set log_irr_chronic_exp log_lb_irr_exp log_ub_irr_exp, studylabel(trial) studysize(total_N) common civartolerance(.05)
meta sum, common eform nostudies 
meta sum, common transform(efficacy) nostudies 

** Cox model
meta set log_cox_hr0 log_hr0_ll log_hr0_ul, studylabel(trial) studysize(total_N) common 
meta sum, common eform nostudies
meta sum, common transform(efficacy) nostudies 

** time to clearance
meta set log_hr0_clear log_hr0_clear_ll log_hr0_clear_ul, studylabel(trial) studysize(exposed_total) common 
meta sum, common eform nostudies

log close




/*** merge in summary data generated in simulation ****/
merge 1:1 trial using trials_VE60_Aug29_summary_wide, nogen

log using "vactrials_VE60_biweekly_Aug29", replace

summ recruited? n_arm? pct_convert? pct_chronic?, sep(0)
summ ir_arm* rr_convert pct_chronic1 irr_chronic pfe fail_pval* cox_hr0 , sep(0)
summ irr_chronic_exp cox_hr0_exp

ci means rr_convert pct_chronic1 irr_chronic pfe cox_hr0 
ci proportion fail_pval*

pwcorr irr_chronic rr_convert
reg irr_chronic rr_convert 
linktest
reg irr_chronic pct_chronic1
linktest

scatter irr_chronic pct_chronic1


reg log_irr_chronic rr_convert pct_chronic1
linktest
margins, at(rr_convert=(1) pct_chronic1=(2 3 4 5 6))
margins, at(rr_convert=(1.0 1.1 1.2 1.3))
predict pred_irr
predict resid, r

log close




