
#
# Unrolled parameter file generator
#
# Eric Tatara
#

#vaccine_schedule <- c('D1', 'D2a', 'D2b', 'D3a', 'D3b')
#vaccine_study_arm_n <- c(797, 1599, 797, 797, 797)

#vaccine_schedule <- c('D1', 'D2b', 'D3b', 'D1', 'D2b', 'D1', 'D2b', 'D3b', 'D1', 'D2b', 'D3b')
#vaccine_study_arm_n <- c(797, 797, 797, 797, 797, 1594, 1594, 1594, 797, 797, 797)
#vaccine_followup1_periods <- c(18, 18, 18, 24, 22, 18, 18, 18, 30, 30, 30)

vaccine_schedule <- c('D2a', 'D1', 'D2b', 'D3b')
vaccine_study_arm_n <- c(797, 1594, 1594, 1594)
vaccine_followup1_periods <- c(22, 24, 22, 18)

replicates <- 500          # Number of replicates per rate param value 

x <- ""
i <- 0
arm <- 1

for (vs in vaccine_schedule){
  seed <- 0   # Reset the seed counter so that each replicate uses the same set of seeds
  for (rep in 1:replicates){
    i <- i + 1
    seed <- seed + 1
  
    # This first entry is the run number, separated with a tab
    x <- paste0(x,i,"\t")
    
    # Additional entries are comma-delimitted, tab-separated pairs
    x <- paste0(x,"randomSeed\t",seed,",")
  
    x <- paste0(x,"vaccine_schedule\t",vs,",")
  
    x <- paste0(x,"vaccine_study_arm_n\t",vaccine_study_arm_n[arm],",")
    
    x <- paste0(x,"vaccine_followup1_periods\t",vaccine_followup1_periods[arm])
    
    x <- paste0(x,"\n")
  
  }
  arm <- arm + 1
}

write(x, file="upf_vaccine_sweep_3.txt")