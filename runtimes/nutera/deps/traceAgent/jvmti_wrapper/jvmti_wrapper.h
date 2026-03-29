//
// Created by julian on 20.07.20.
//

#ifndef JAVAMEMTRACEAGENT_JVMTI_WRAPPER_H
#define JAVAMEMTRACEAGENT_JVMTI_WRAPPER_H

#include <iostream>
#include <jvmti.h>
#include <cstring>
#include <vector>
#include <exception>
#include <memory>

#include <jvmti.h>


namespace jw {

    struct j_class;

    void deallocate(jvmtiEnv* env, void* ptr);
    void check_jvmti_errors(jvmtiEnv *jvmti, jvmtiError errnum, const std::string& message="");
    int get_local_int(jvmtiEnv *jvmti, jthread thread, jint depth, jint slot);
    float get_local_float(jvmtiEnv *jvmti, jthread thread, jint depth, jint slot);
    double get_local_double(jvmtiEnv *jvmti, jthread thread, jint depth, jint slot);
    long get_local_long(jvmtiEnv *jvmti, jthread thread, jint depth, jint slot);
    jobject get_local_object(jvmtiEnv *jvmti, jthread thread, jint depth, jint slot);

    jclass get_class_by_name(JNIEnv* env, const std::string& name);
    std::vector<j_class> get_loaded_classes(jvmtiEnv* env);


    class exception : std::exception {
    private:
        std::string message;
    public:
        explicit exception(std::string &message) : message{message} {}
        explicit exception(std::string &&message) : message{message} {}

        [[nodiscard]] const char *what() const noexcept override {
            return message.c_str();
        }
    };

    class invalid_slot_exception : public exception {
    public:
        explicit invalid_slot_exception(std::string& message) : exception(message){}
        explicit invalid_slot_exception(std::string&& message) : exception(message) {}


    };

    class unexpected_location_format : exception {
    public:
        explicit unexpected_location_format(std::string& message) : exception(message){}
        explicit unexpected_location_format(std::string&& message) : exception(message) {}
    };

    class field_not_found_exception : exception {
    public:
        explicit field_not_found_exception(std::string& message) : exception(message){}
        explicit field_not_found_exception(std::string&& message) : exception(message) {}
    };

    struct method_name {
        std::string name;
        std::string signature;
        std::string generic_signature;

        method_name() = default;

        method_name(jvmtiEnv *jvmti_env, const jmethodID& method);
        method_name(const method_name& mname) {
            this->name = mname.name;
            this->signature = mname.signature;
            this->generic_signature = mname.generic_signature;
        }

        bool operator==(const method_name& id) const {
            return !((this->name != id.name) || (this->signature != id.signature) ||
                     (this->generic_signature != id.generic_signature));
        }

        bool operator!=(const method_name& id) const {
            return  !(*this == id);
        }
        };

    struct method {
        jmethodID id{};

        method_name name;

        method() = default;

    public:
        method(jvmtiEnv *jvmti_env, const jmethodID& method);
        method(const method& mname) = default;

        bool operator==(const method& mthd) const {
            return !((this->id != mthd.id) || (name == mthd.name));
        }

        bool operator!=(const method& mthd) const {
            return !(*this == mthd);
        }

        jlocation get_start_location(jvmtiEnv* env) const;
        jlocation get_end_location(jvmtiEnv *env) const;
    };

    struct stack_trace {

        std::vector<jvmtiFrameInfo> frames;

        stack_trace(jvmtiEnv* jvmti_env, const jthread& thread, int max_stack_frames);
        bool contains_main(jvmtiEnv* jvmti_env);

    };

    struct local_variable_entry {

        jlocation start_location;
        jint length;
        std::string name;
        std::string signature;
        std::string generic_signature;
        jint slot;

        local_variable_entry(jlocation start_location, jint length, std::string&& name,
                             std::string&& signature, std::string& generic_signature,jint slot);
    };

    struct local_variable_table {
        std::vector<local_variable_entry> local_variables;

        local_variable_table(jvmtiEnv* env, const jmethodID& method);
    };

    struct j_class {
        jclass klass;

        j_class() = delete;
        explicit j_class(jclass cls);
        j_class(jvmtiEnv* env, const jmethodID& method);
        j_class(JNIEnv* env, const jobject& object);
        j_class(JNIEnv* env, const std::string& name);

        std::string get_signature(jvmtiEnv* env) const ;
        std::string get_generic_signature(jvmtiEnv* env) const;
        std::string get_source_file_name(jvmtiEnv* env) const;
        std::vector<jfieldID> get_class_fields(jvmtiEnv* env) const;
        std::string get_field_name(jvmtiEnv* env, jfieldID id) const;
        std::string get_field_signature(jvmtiEnv* env, jfieldID id) const;
        std::string get_field_generic_signature(jvmtiEnv* env, jfieldID id) const;
        jfieldID get_field_handle_by_name(jvmtiEnv* env, const std::string& name) const;
        std::vector<method> get_direct_declared_methods(jvmtiEnv *env) const;
        method get_method_by_name_sig(jvmtiEnv* jvmti_env, JNIEnv *jni_env, const std::string& name, const std::string& sig) const;

        jfieldID get_field_handle_by_name(JNIEnv *env, const std::string& name, const std::string& signature) const ;

    };

    struct bytecodes {
        std::vector<unsigned char> b_codes;

        bytecodes(jvmtiEnv* env, const jmethodID& method);
    };

}


#endif //JAVAMEMTRACEAGENT_JVMTI_WRAPPER_H
