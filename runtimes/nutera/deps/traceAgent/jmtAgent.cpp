
#include <iostream>
#include <jvmti.h>
#include <cstring>
#include <vector>
#include <memory>
#include <fstream>
#include <algorithm>
#include <iomanip>

#include "jvmti_wrapper/jvmti_wrapper.h"
#include "tracing.h"

constexpr jint max_stack_frames = 1000;

constexpr auto runner_main = "Lrunner/Main;";
constexpr auto size_exception = "exceptions/TraceSizeLimitReached";


struct trace_wrapper{
    std::vector<trace> global_trace;

    //bool tracing = false;

    std::string class_name;
    std::string method_name;
    std::string output_file;
    bool trace_bp = false;
    std::vector<int> break_points;
    int trace_limit = 0;

    json get_json_representation() {
        json j;
        j["class"] = class_name;
        j["method"] = method_name;
        j["trace_limit"] = trace_limit;

        std::vector<json> vec;
        for (trace& x : global_trace) {
            vec.push_back(x.get_json_representation());
        }
        j["traces"] = vec;

        return j;
    }
};

static trace_wrapper trace_wrp;

std::vector<std::string> tokenize(const std::string &str, std::string&& delim)
{
    std::vector<std::string> out;
    size_t start;
    size_t end = 0;

    while ((start = str.find_first_not_of(delim, end)) != std::string::npos)
    {
        end = str.find(delim, start);
        out.push_back(str.substr(start, end - start));
    }

    return out;
}

std::pair<std::string,std::string> split_on_first_occurrence(const std::string &str, std::string&& delim)
{

    return std::pair<std::string,std::string>(str.substr(0, str.find(delim)), str.substr(str.find(delim)+1, str.size()));
}


static void
version_check(jint cver, jint rver)
{
    jint cmajor, cminor, cmicro;
    jint rmajor, rminor, rmicro;

    cmajor = (cver & JVMTI_VERSION_MASK_MAJOR) >> JVMTI_VERSION_SHIFT_MAJOR;
    cminor = (cver & JVMTI_VERSION_MASK_MINOR) >> JVMTI_VERSION_SHIFT_MINOR;
    cmicro = (cver & JVMTI_VERSION_MASK_MICRO) >> JVMTI_VERSION_SHIFT_MICRO;
    rmajor = (rver & JVMTI_VERSION_MASK_MAJOR) >> JVMTI_VERSION_SHIFT_MAJOR;
    rminor = (rver & JVMTI_VERSION_MASK_MINOR) >> JVMTI_VERSION_SHIFT_MINOR;
    rmicro = (rver & JVMTI_VERSION_MASK_MICRO) >> JVMTI_VERSION_SHIFT_MICRO;
    fprintf(stdout, "Compile Time JVMTI Version: %d.%d.%d (0x%08x)\n",
            cmajor, cminor, cmicro, cver);
    fprintf(stdout, "Run Time JVMTI Version: %d.%d.%d (0x%08x)\n",
            rmajor, rminor, rmicro, rver);
    if ( cmajor != rmajor || cminor != rminor ) {
        fprintf(stderr,
                "ERROR: Compile Time JVMTI and Run Time JVMTI are incompatible\n");
        exit(1);
    }
}

/* Callback for JVMTI_EVENT_VM_INIT */
static void JNICALL
vm_init(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    jvmtiError err;
    jint       runtime_version;

    /* The exact JVMTI version doesn't have to match, however this
     *  code demonstrates how you can check that the JVMTI version seen
     *  in the jvmti.h include file matches that being supplied at runtime
     *  by the VM.
     */
    err = jvmti->GetVersionNumber(&runtime_version);
    if (err != JVMTI_ERROR_NONE) {
        fprintf(stderr, "ERROR: GetVersionNumber failed, err=%d\n", err);
        exit(1);
    } else {
        version_check(JVMTI_VERSION, runtime_version);
    }
}

void JNICALL
vm_death(jvmtiEnv *jvmti, JNIEnv *env)
{
    std::cout << "Virtual Machine Death" << std::endl;
}

trace_frame generate_frame(jvmtiEnv *jvmti_env,
                    JNIEnv* jni_env,
                    jthread thread,
                    jlocation location,
                    jw::method mthd)
{

    jw::stack_trace stack_t(jvmti_env, thread, max_stack_frames);
    trace_frame frame(location, mthd);

    std::vector<float> frame_rep;

    //for (auto i = 0; i < stack_t.frames.size(); ++i) {
    for (auto i = 0; i < 1; ++i) {

        // Only possible if -g is used to compile
        try {
            jw::local_variable_table local_var_table(jvmti_env, stack_t.frames[i].method);

            std::stable_sort(local_var_table.local_variables.begin(), local_var_table.local_variables.end(),
                             [](const jw::local_variable_entry& a, const jw::local_variable_entry& b) {
                                 return a.start_location < b.start_location;}
            );

            //std::cout << local_var_table.local_variables[0].name << std::endl;

            for (auto &l_var : local_var_table.local_variables) {
                std::shared_ptr<representable> rep;

                if (! std::any_of(trace_wrp.break_points.begin(),trace_wrp.break_points.end(),
                                [&](int x){return l_var.start_location <= x && x < l_var.start_location + l_var.length;})) {
                    continue;
                }

                if ((l_var.start_location > location) || (location >= l_var.start_location + l_var.length)) { // Check if slot is valid
                    rep = get_rep_of_invalid_variable(jni_env, jvmti_env, thread, i, l_var.slot,
                                                      l_var.signature);
                } else {
                    rep = get_rep_of_variable(jni_env, jvmti_env, thread, i, l_var.slot,
                                              l_var.signature);
                }
                // s = s +" | " + l_var.name + " " + std::to_string((rep->get_float_representation()[0]));
                rep->set_identifier(identifier(mthd, l_var.name));
                frame.set_representation(rep);
            }
        } catch (jw::exception& e) {
            std::cout << e.what() << std::endl;
        }
        // std::cout << s << std::endl;
    }

    return frame;
}


void JNICALL
on_single_step(jvmtiEnv *jvmti_env,
               JNIEnv* jni_env,
               jthread thread,
               jmethodID method,
               jlocation location)
{



    //Check if we are in the initialization or already in the main function.
    //if(!stack_t.contains_main(jvmti_env)) {
    //    return;
    //}

    jw::method mthd(jvmti_env, method);
    //std::cout << mthd.name.name << std::endl;
    if (mthd.name.name != trace_wrp.method_name) {
        return;
    }
    trace_frame frame = generate_frame(jvmti_env, jni_env, thread, location, mthd);

    trace_wrp.global_trace.back().push_back_frame(frame);

}

void JNICALL
on_frame_pop(jvmtiEnv *jvmti_env,
                 JNIEnv* jni_env,
                 jthread thread,
                 jmethodID method,
                 jboolean was_popped_by_exception)
{
    jw::method m{jvmti_env, method};
    jvmtiError err;

    if (! trace_wrp.trace_bp) {
        // disable single step events
        err = jvmti_env->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, nullptr);
        jw::check_jvmti_errors(jvmti_env, err, "Could not disable Single Step event.");

    } else {
        //remove breakpoints
        for(int offset : trace_wrp.break_points) {
            err = jvmti_env->ClearBreakpoint(m.id, m.get_start_location(jvmti_env)+offset);
            jw::check_jvmti_errors(jvmti_env, err, "Could not set breakpoint at loop heads.");
        }
    }
}

void JNICALL
on_class_prepare(jvmtiEnv *jvmti_env,
                 JNIEnv* jni_env,
                 jthread thread,
                 jclass klass)
{
    jw::j_class k{klass};

    if (k.get_signature(jvmti_env) == (runner_main)) {
        auto x = k.get_direct_declared_methods(jvmti_env);
        for (jw::method& a : x) {
            if(a.name.name == "invoke") {
                try {
                    jvmtiError err = jvmti_env->SetBreakpoint(a.id, a.get_start_location(jvmti_env));
                    jw::check_jvmti_errors(jvmti_env, err, "Could not set breakpoint.");
                } catch (jw::exception& e) {
                    std::cout << e.what() << std::endl;
                }
            }
        }
    }
}

void JNICALL
on_breakpoint(jvmtiEnv *jvmti_env,
              JNIEnv* jni_env,
              jthread thread,
              jmethodID method,
              jlocation location)
{
    jw::method mthd{jvmti_env, method};
    jvmtiError err;

    if (mthd.name.name == "invoke") {
        try {
            err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr);
            jw::check_jvmti_errors(jvmti_env, err, "Could not enable Method Entry event.");
        } catch (jw::exception &e) {
            std::cout << e.what() << std::endl;
        }
    } else {
        if (mthd.name.name == trace_wrp.method_name && trace_wrp.trace_bp) {
            trace_frame frame = generate_frame(jvmti_env, jni_env, thread, location, mthd);
            trace_wrp.global_trace.back().push_back_frame(frame);
            if (trace_wrp.trace_limit > 0) {
                // Check if trace limit exists
                if (trace_wrp.global_trace.back().get_stack_images().size() >= trace_wrp.trace_limit ) {
                    jclass c = jni_env->FindClass(size_exception);
                    jni_env->ThrowNew(c, "");
                }
            }

        }
    }
}

void JNICALL on_method_entry(jvmtiEnv *jvmti_env,
                             JNIEnv* jni_env,
                             jthread thread,
                             jmethodID method)
{

    jw::method mthd{jvmti_env, method};

    // Check if we enter the method that we want to trace
    if (mthd.name.name == trace_wrp.method_name) { //TODO also check if it's the right class not only method
        jvmtiError err;

        if (! trace_wrp.trace_bp) {
            // create single step events in this method
            err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, nullptr);
            jw::check_jvmti_errors(jvmti_env, err, "Could not enable Step Event event.");
        } else {
            // create breakpoints //TODO this is not, yet
            for(int offset : trace_wrp.break_points) {
                err = jvmti_env->SetBreakpoint(mthd.id, mthd.get_start_location(jvmti_env)+offset);
                jw::check_jvmti_errors(jvmti_env, err, "Could not set breakpoint at loop heads.");
            }
        }

        // Create new trace
        trace_wrp.global_trace.emplace_back(trace_wrp.class_name, trace_wrp.method_name, trace_wrp.output_file);

        // disable method entry event
        err = jvmti_env->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr);
        jw::check_jvmti_errors(jvmti_env, err, "Could not disable JVMTI_EVENT_METHOD_ENTRY event.");

        //Notify when we leave method again
        err = jvmti_env->NotifyFramePop(thread, 0);
        jw::check_jvmti_errors(jvmti_env, err, "Could not enable FRAME POP event.");

    }
}


JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{

    std::vector<std::string> args = tokenize(std::string(options), ",");
    std::map<std::string, std::string> opts;
    for (auto& arg : args) {
        if (arg.find('=') != std::string::npos) {
            auto pair = split_on_first_occurrence(arg, "=");
            opts[pair.first] = pair.second;
        }
    }


    if (args.size() < 3) {
        throw missing_information_exception("Not enough arguments were provided. "
                                            "Class, Method, output file are required.");
    }
    //std::string clazz = args[0];
    //std::string method = args[1];
    //std::string file = args[2];
    std::string clazz = opts["class"];
    std::string method = opts["method"];
    std::string file = opts["output"];



    trace_wrp.output_file = file;
    trace_wrp.class_name = clazz;
    trace_wrp.method_name = method;

    if (opts.count("loopheads") >= 1 ) {
        trace_wrp.trace_bp = true;
        std::vector<std::string> vals = tokenize(opts["loopheads"], ":");
        std::vector<int> bp;
        try {
            std::transform(vals.begin(), vals.end(), std::back_inserter(bp),
                           [](std::string &s) -> int { return std::stoi(s); });
        } catch (std::invalid_argument& e) {
            std::cout << "Could not convert loophead list to integers" << std::endl;
            std::cout << e.what() << std::endl;
        }
        trace_wrp.break_points = bp;
    }

    if (opts.count("tracelimit") >= 1 ) {
        int trace_limit = std::stoi(opts["tracelimit"]);
        trace_wrp.trace_limit = trace_limit;
    }


    std::cout << "Writing to " << trace_wrp.output_file << std::endl;

    std::cout << "Tracing: " << trace_wrp.method_name << " in " << trace_wrp.class_name << std::endl;

    jvmtiEnv *jvmti;
    jint rc = vm->GetEnv((void **) &jvmti, JVMTI_VERSION);
    if (rc != JNI_OK) {
        fprintf(stderr, "ERROR: GetEnv failed: %d\n\t JVMTI_VERSION:%d", rc, JVMTI_VERSION);
        return JNI_ERR;
    }

    jvmtiError err;

    // Ensuring that JLocationFormat is JVMBCI
    jvmtiJlocationFormat format;
    std::memset(&format, 0, sizeof(jvmtiJlocationFormat));
    err = jvmti->GetJLocationFormat(&format);
    jw::check_jvmti_errors(jvmti, err);
    if (format != JVMTI_JLOCATION_JVMBCI) {
        throw jw::unexpected_location_format("Location format is not JVMBCI");
    }

    //Adding capabilities
    jvmtiCapabilities capabilities = {0};
    capabilities.can_generate_single_step_events = 1;
    capabilities.can_get_bytecodes = 1;
    capabilities.can_access_local_variables = 1;
    capabilities.can_get_source_file_name = 1;
    capabilities.can_generate_frame_pop_events = 1;
    capabilities.can_generate_breakpoint_events = 1;
    capabilities.can_generate_method_entry_events = 1;
    capabilities.can_signal_thread = 1;
    capabilities.can_generate_exception_events = 1;

    try {
        err = jvmti->AddCapabilities(&capabilities);
        jw::check_jvmti_errors(jvmti, err);
    } catch (jw::exception& e) {
        std::cout << e.what() << std::endl;
        return JNI_ABORT;
    }

    //Setting Event Notification
    try {
       // err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, nullptr);
       //jw::check_jvmti_errors(jvmti, err, "Could not enable Single Step event.");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullptr);
        jw::check_jvmti_errors(jvmti, err, "Could not enable VM INIT event.");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
        jw::check_jvmti_errors(jvmti, err, "Could not enable VM DEATH event.");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, nullptr);
        jw::check_jvmti_errors(jvmti, err, "Could not enable CLASS PREPARE event.");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullptr);
        jw::check_jvmti_errors(jvmti, err, "Could not enable BREAKPOINT event.");
        err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr);
        jw::check_jvmti_errors(jvmti, err, "Could not enable METHOD Entry event.");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FRAME_POP, nullptr);
        jw::check_jvmti_errors(jvmti, err, "Could not enable JVMTI_EVENT_FRAME_POP event.");

    } catch (jw::exception& e) {
        std::cout << e.what() << std::endl;
        return JNI_ABORT;
    }

    //Setting Event Callbacks
    jvmtiEventCallbacks callbacks;
    std::memset(&callbacks, 0, sizeof(callbacks));

    callbacks.VMInit = &vm_init;  // VM_INIT
    callbacks.VMDeath = &vm_death;  // JVMTI_EVENT_VM_DEATH
    callbacks.SingleStep = &on_single_step;  // JVMTI_EVENT_SINGLE_STEP
    callbacks.FramePop = &on_frame_pop;  //
    callbacks.ClassPrepare = &on_class_prepare;  //
    callbacks.Breakpoint = &on_breakpoint;  //
    callbacks.MethodEntry = &on_method_entry;

    try {
        err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
        jw::check_jvmti_errors(jvmti, err, "Could not register event callbacks.");
    } catch (jw::exception& e) {
        std::cout << e.what() << std::endl;
        return JNI_ABORT;
    }


    return JNI_OK;
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm)
{
    std::cout << "Writing to " << trace_wrp.output_file << std::endl;

    json j = trace_wrp.get_json_representation();
    std::ofstream file(trace_wrp.output_file);
    file << std::setw(4) << j << std::endl;


}