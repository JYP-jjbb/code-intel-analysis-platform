//
// Created by julian on 20.07.20.
//

#include "jvmti_wrapper.h"


void jw::check_jvmti_errors(jvmtiEnv *jvmti, jvmtiError errnum, const std::string& message) {
    if (errnum != JVMTI_ERROR_NONE) {
        char *err_str;
        jvmti->GetErrorName(errnum, &err_str);
        std::string msg = "JVMTI ERROR ["
                          + std::to_string(static_cast<int>(errnum))
                          + "]: "
                          + (err_str == nullptr ? "Unknown" : err_str)
                          + " " + message;

        switch (errnum) {
            case JVMTI_ERROR_INVALID_SLOT:
                throw jw::invalid_slot_exception(msg);
            default:
                throw jw::exception(msg);
        }
    }
}

jobject jw::get_local_object(jvmtiEnv *jvmti, jthread thread, jint depth, jint slot)
{
    jobject obj;
    jvmtiError err;
    err = jvmti->GetLocalObject(thread, depth, slot, &obj);
    jw::check_jvmti_errors(jvmti, err, "Cannot get Local Object.");
    return obj;
}


int jw::get_local_int(jvmtiEnv *jvmti, jthread thread, jint depth, jint slot)
{
    jint value_ptr;
    jvmtiError err;
    err = jvmti->GetLocalInt(thread, depth, slot, &value_ptr);
    jw::check_jvmti_errors(jvmti, err, "Cannot get Local Int.");
    return value_ptr;
}

float jw::get_local_float(jvmtiEnv *jvmti, jthread thread, jint depth, jint slot)
{
    jfloat value_ptr;
    jvmtiError err;
    err = jvmti->GetLocalFloat(thread, depth, slot, &value_ptr);
    jw::check_jvmti_errors(jvmti, err, "Cannot Get Local Variable Float");
    return value_ptr;
}

double jw::get_local_double(jvmtiEnv *jvmti, jthread thread, jint depth, jint slot)
{
    jdouble value_ptr;
    jvmtiError err;
    err = jvmti->GetLocalDouble(thread, depth, slot, &value_ptr);
    jw::check_jvmti_errors(jvmti, err, "Cannot Get Local Variable Double");
    return value_ptr;
}

long jw::get_local_long(jvmtiEnv *jvmti, jthread thread, jint depth, jint slot)
{
    jlong value_ptr;
    jvmtiError err;
    err = jvmti->GetLocalLong(thread, depth, slot, &value_ptr);
    jw::check_jvmti_errors(jvmti, err, "Cannot Get Local Variable Long");
    return value_ptr;
}


void jw::deallocate(jvmtiEnv *env, void *ptr)
{
    jvmtiError err = env->Deallocate(static_cast<unsigned char *>(ptr));
    check_jvmti_errors(env, err, "Could not deallocate memory.");
}

jw::method_name::method_name(jvmtiEnv *jvmti_env, const jmethodID &method) {

    jvmtiError err;
    char *name_;
    char *signature_;
    char *generic_signature_;
    err = jvmti_env->GetMethodName(method, &name_, &signature_, &generic_signature_);
    check_jvmti_errors(jvmti_env, err, "Could not get method name.");

    this->name = std::string(name_);
    deallocate(jvmti_env, name_);

    this->signature = std::string(signature_);
    deallocate(jvmti_env, signature_);

    if (generic_signature_) {
        this->generic_signature = std::string(generic_signature_);
        deallocate(jvmti_env, generic_signature_);
    }
}


jw::stack_trace::stack_trace(jvmtiEnv *jvmti_env, const jthread &thread, int max_stack_frames) {

    jvmtiFrameInfo stack_info[max_stack_frames];
    jint count;
    jvmtiError err;

    err = jvmti_env->GetStackTrace(thread, 0, max_stack_frames, stack_info, &count);
    check_jvmti_errors(jvmti_env, err, "Could not get stack trace.");

    frames = std::vector<jvmtiFrameInfo>(stack_info, stack_info + count);
}

bool jw::stack_trace::contains_main(jvmtiEnv *jvmti_env) {
    for (auto &frame_info : frames) {
        method_name m_name(jvmti_env, frame_info.method);
        if (m_name.name == "main") {
            return true;
        }
    }
    return false;
}


jw::local_variable_entry::local_variable_entry(jlocation start_location, jint length, std::string &&name,
                                               std::string &&signature, std::string &generic_signature, jint slot) :
        start_location(start_location),
        length(length),
        name(name),
        signature(signature),
        generic_signature(generic_signature),
        slot(slot) {}

jw::local_variable_table::local_variable_table(jvmtiEnv *env, const jmethodID &method) {
    jint count;
    jvmtiLocalVariableEntry *table_ptr;
    jvmtiError err = env->GetLocalVariableTable(method, &count, &table_ptr);
    check_jvmti_errors(env, err, "Could not get local variable table.");

    for (auto i = 0; i < count; ++i) {
        std::string gen_sig = table_ptr[i].generic_signature ? std::string(table_ptr[i].generic_signature) : "";
        local_variables.emplace_back(
                table_ptr[i].start_location,
                table_ptr[i].length,
                std::string(table_ptr[i].name),
                std::string(table_ptr[i].signature),
                gen_sig,
                table_ptr[i].slot);

        deallocate(env, table_ptr[i].name);
        deallocate(env, table_ptr[i].signature);
        deallocate(env, table_ptr[i].generic_signature);
    }

    deallocate(env, table_ptr);
}

jw::j_class::j_class(jvmtiEnv *env, const jmethodID &method) {
    jvmtiError err;

    err = env->GetMethodDeclaringClass(method, &klass);
    check_jvmti_errors(env, err, "Cannot get method declaring class.");
}

std::string jw::j_class::get_signature(jvmtiEnv *env) const {
    char *sig;
    char *generic_ptr;
    jvmtiError err = env->GetClassSignature(klass, &sig, &generic_ptr);
    check_jvmti_errors(env, err, "klass is not a class object or the class has been unloaded.");

    std::string res(sig);

    deallocate(env, sig);
    deallocate(env, generic_ptr);

    return res;
}

std::string jw::j_class::get_generic_signature(jvmtiEnv *env) const {
    char *sig;
    char *generic_ptr;
    jvmtiError err = env->GetClassSignature(klass, &sig, &generic_ptr);
    check_jvmti_errors(env, err, "klass is not a class object or the class has been unloaded.");

    if (!generic_ptr) {
        throw jw::exception("Generic pointer is nullptr.");
    }

    std::string res(generic_ptr);

    deallocate(env, sig);
    deallocate(env, generic_ptr);

    return res;
}

std::string jw::j_class::get_source_file_name(jvmtiEnv *env) const {
    char *name;
    jvmtiError err = env->GetSourceFileName(klass, &name);
    check_jvmti_errors(env, err, "Could not get class source file.");
    std::string res(name);
    deallocate(env, name);
    return res;
}

std::vector<jfieldID> jw::j_class::get_class_fields(jvmtiEnv *env) const {
    jint cnt;
    jfieldID* fields;
    jvmtiError err = env->GetClassFields(this->klass, &cnt, &fields);
    check_jvmti_errors(env, err);
    std::vector<jfieldID> res(fields, fields+cnt);
    deallocate(env, fields);
    return res;
}

jfieldID jw::j_class::get_field_handle_by_name(JNIEnv *env, const std::string& name, const std::string& signature) const {
    jfieldID fidInt = env->GetFieldID(this->klass, name.c_str(), signature.c_str());
    return fidInt;
}

std::string jw::j_class::get_field_name(jvmtiEnv *env, jfieldID id) const {
    char *name, *sig, *gen_sig;
    jvmtiError err = env->GetFieldName(this->klass, id, &name, &sig, &gen_sig);
    check_jvmti_errors(env, err);
    std::string s(name);

    deallocate(env, name);
    deallocate(env, sig);
    deallocate(env, gen_sig);
    return s;
}

std::string jw::j_class::get_field_signature(jvmtiEnv *env, jfieldID id) const {
    char *name, *sig, *gen_sig;
    jvmtiError err = env->GetFieldName(this->klass, id, &name, &sig, &gen_sig);
    check_jvmti_errors(env, err);
    std::string s(sig);

    deallocate(env, name);
    deallocate(env, sig);
    deallocate(env, gen_sig);
    return s;
}

std::string jw::j_class::get_field_generic_signature(jvmtiEnv *env, jfieldID id) const {
    char *name, *sig, *gen_sig;
    jvmtiError err = env->GetFieldName(this->klass, id, &name, &sig, &gen_sig);
    check_jvmti_errors(env, err);
    std::string s(gen_sig);

    deallocate(env, name);
    deallocate(env, sig);
    deallocate(env, gen_sig);
    return s;
}

jfieldID jw::j_class::get_field_handle_by_name(jvmtiEnv *env, const std::string& name) const {
    std::vector<jfieldID> fields = this->get_class_fields(env);
    for (auto& field : fields) {
        if (this->get_field_name(env, field) == name) {
            return field;
        }
    }
    throw field_not_found_exception("Could not find filed " + name + " in class " + this->get_signature(env));
}

jw::j_class::j_class(JNIEnv *env, const jobject& object): klass{env->GetObjectClass(object)} {
}

jw::j_class::j_class(JNIEnv *env, const std::string& name): klass{get_class_by_name(env, name)} {

}

jw::j_class::j_class(jclass cls): klass{cls} {

}

std::vector<jw::method> jw::j_class::get_direct_declared_methods(jvmtiEnv *env) const {
    std::vector<method> vec;

    jvmtiError err;
    jint cnt;
    jmethodID* methods;

    err = env->GetClassMethods(this->klass, &cnt, &methods);
    jw::check_jvmti_errors(env, err, "Could not get declared methods.");

    for(auto i = 0; i < cnt; ++i) {
        vec.emplace_back(env,methods[i]);
    }

    deallocate(env, methods);

    return vec;
}

jw::method jw::j_class::get_method_by_name_sig(jvmtiEnv* jvmti_env, JNIEnv *jni_env, const std::string& name, const std::string& sig) const {
    jmethodID mthd = jni_env->GetMethodID(this->klass,name.c_str(),sig.c_str());
    if (!mthd) {
        throw jw::exception("Could not get method" + name + ", sig:" + sig);
    }

    return jw::method{jvmti_env, mthd};
}

jw::bytecodes::bytecodes(jvmtiEnv *env, const jmethodID &method) {

    jvmtiError err;
    jint bytecode_count; // length
    unsigned char *bytecodes_ptr;

    err = env->GetBytecodes(method, &bytecode_count, &bytecodes_ptr);
    jw::check_jvmti_errors(env, err, "Could not get Bytecodes.");

    b_codes = std::vector<unsigned char>(bytecodes_ptr, bytecodes_ptr + bytecode_count);

    deallocate(env, bytecodes_ptr);
}

jw::method::method(jvmtiEnv *jvmti_env, jmethodID const &method): id{method}, name(jvmti_env, method) {

}

jlocation jw::method::get_start_location(jvmtiEnv *env) const {
    jvmtiError err;
    jlocation start_location_ptr;
    jlocation end_location_ptr;

    err = env->GetMethodLocation(this->id, &start_location_ptr, &end_location_ptr);
    jw::check_jvmti_errors(env, err, "Could not get start location.");

    return start_location_ptr;
}

jlocation jw::method::get_end_location(jvmtiEnv *env) const {
    jvmtiError err;
    jlocation start_location_ptr;
    jlocation end_location_ptr;

    err = env->GetMethodLocation(this->id, &start_location_ptr, &end_location_ptr);
    jw::check_jvmti_errors(env, err, "Could not get end location.");

    return end_location_ptr;
}

jclass jw::get_class_by_name(JNIEnv* env, const std::string& name)
{
    return env->FindClass(name.c_str());
}

std::vector<jw::j_class> jw::get_loaded_classes(jvmtiEnv* env)
{
    jvmtiError err;
    jint cnt;
    jclass* classes;
    err = env->GetLoadedClasses(&cnt, &classes);
    jw::check_jvmti_errors(env, err, "Could not get Loaded Classes");

    std::vector<jw::j_class> vec;
    vec.reserve(cnt);
    for(auto i = 0; i < cnt; ++i) {
         vec.emplace_back(classes[i]);
    }

    deallocate(env, classes);

    return vec;
}
