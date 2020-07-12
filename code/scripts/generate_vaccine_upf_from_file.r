
#
# Unrolled parameter file generator for APK runs defined int a simulation plan.
#
# Eric Tatara
#

library(data.table)

simulation_plan_file <- "simulation_plan_2020-04.csv"

df <- fread(simulation_plan_file)

x <- ""   # The complete output string
i <- 0    # The run number

# Iterate over each experiment.  Normally we vectorize this for performance, but
#  we need to count the global run number.
for (r in 1:nrow(df)){
  
  row <- df[r,]
  
  replicates <- row$runs
  vs <- row$vaccine_schedule
  vaccine_study_arm_n <- row$vaccine_study_arm_n
  vaccine_followup1_periods <- row$vaccine_followup1_periods
  
  seed <- 0   # Reset the seed counter so that each replicate uses the same set of seeds
  for (rep in 1:replicates){
    i <- i + 1
    seed <- seed + 1
  
    # This first entry is the run number, separated with a tab
    x <- paste0(x,i,"\t")
    
    # Additional entries are comma-delimitted, tab-separated pairs
    x <- paste0(x,"randomSeed\t",seed,",")
    
    x <- paste0(x,"vaccine_schedule\t",vs,",")
    
    x <- paste0(x,"vaccine_study_arm_n\t",vaccine_study_arm_n,",")
    
    x <- paste0(x,"vaccine_followup1_periods\t",vaccine_followup1_periods)
    
    x <- paste0(x,"\n")
  }
  
}

write(x, file="upf_vaccine_sweep_5.txt")
