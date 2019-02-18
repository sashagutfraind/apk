import io;
import sys;
import files;

string emews_root = getenv("EMEWS_PROJECT_ROOT");
string turbine_output = getenv("TURBINE_OUTPUT");

app (file out, file err) run_model (file shfile, string param_line, string instance)
{
    "bash" shfile param_line emews_root instance @stdout=out @stderr=err;
}

// call this to create any required directories
app (void o) make_dir(string dirname) {
  "mkdir" "-p" dirname;
}

app (void o) cp_message_center() {
  "cp" (emews_root+"/complete_model/MessageCenter.log4j.properties") turbine_output;
}

cp_message_center() => {
  file model_sh = input(emews_root+"/scripts/APK.sh");
  file upf = input(argv("f"));
  string upf_lines[] = file_lines(upf);
  foreach s,i in upf_lines {
    string instance = "%s/instance_%i/" % (turbine_output, i+1);
    make_dir(instance) => {
      file out <instance+"out.txt">;
      file err <instance+"err.txt">;
      (out,err) = run_model(model_sh, s, instance);
    }
  }
}
