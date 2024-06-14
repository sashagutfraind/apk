*** compare distributions of eVE ***

use VEsims500_VE60_May22, clear

summ pfe, d
sktest pfe
swilk pfe

use "modified biweekly\VEsims500_VE60_mod_Aug29.dta", clear

summ pfe, d
sktest pfe
swilk pfe

use VEsims500_VE80_Jun22, clear

summ pfe, d
sktest pfe
swilk pfe
