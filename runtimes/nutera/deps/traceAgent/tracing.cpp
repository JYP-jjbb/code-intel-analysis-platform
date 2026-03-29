//
// Created by julian on 21.07.20.
//

#include "tracing.h"

std::string intercallate_string_list(std::list<std::string> lst, const std::string &s) {
    if (lst.empty()) {
        return "";
    }
    std::string res = lst.front();
    for (auto it = (++lst.begin()); it != lst.end(); ++it) {
        res += s + *it;
    }

    return res;
}

trace::trace(std::string &class_name, std::string &method_name, std::string &output_file) :
        class_name{class_name},
        method_name{method_name},
        output_file{output_file} {}

trace::trace(std::string &&class_name, std::string &&method_name, std::string &&output_file) :
        class_name{class_name},
        method_name{method_name},
        output_file{output_file} {}


[[nodiscard]] const std::string &trace::get_class_name() const {
    return class_name;
}

[[nodiscard]] const std::string &trace::get_method_name() const {
    return method_name;
}

[[nodiscard]] const std::vector<trace_frame> &trace::get_stack_images() const {
    return stack_images;
}

void trace::push_back_frame(trace_frame &frame) {
    this->stack_images.push_back(frame);
}

const std::string &trace::get_output_file() const {
    return output_file;
}

long trace_frame::get_location() const {
    return location;
}

void trace_frame::set_location(long loc) {
    trace_frame::location = loc;
}

const jw::method &trace_frame::get_method() const {
    return method;
}

void trace_frame::set_method(const jw::method &m) {
    trace_frame::method = m;
}

std::vector<float> trace_frame::get_float_rep() const {

    std::vector<float> res;

    for (auto &rep : this->representations) {
        auto repres = rep->get_float_representation();
        res.insert(res.end(), repres.begin(), repres.end());
    }

    return res;
}

std::vector<std::string> trace_frame::get_pos_names() const {

    std::vector<std::string> res;

    for (auto &rep : this->representations) {
        auto repres = rep->get_position_name();
        res.insert(res.end(), repres.begin(), repres.end());
    }

    return res;
}

json trace_frame::get_json_representation() const {

    auto names = this->get_pos_names();
    auto reps = this->get_float_rep();

    if (names.size() != reps.size()) {
        throw missing_information_exception("Identifier vector and representation vector are not the same length.");
    }

    json j;
    j["location"] = location;
    j["rep"] = reps;
    j["names"] = names;

    return j;
}


std::shared_ptr<representable>
get_rep_of_invalid_variable(JNIEnv *jniEnv, jvmtiEnv *jvmti_env, jthread thread, jint depth, jint slot,
                            const std::string& signature) {
    return std::shared_ptr<representable>(new primitive_rep({0}));

}

//std::vector<float> get_rep_of_field(JNIEnv* jniEnv, jvmtiEnv* jvmti_env, jobject obj, jfieldID field) {}

std::shared_ptr<representable>
get_rep_of_variable(JNIEnv *jniEnv, jvmtiEnv *jvmti_env, jthread thread, jint depth, jint slot, std::string signature) {

    switch (signature[0]) {
        case 'B': {
            return std::shared_ptr<representable>(
                    new primitive_rep(get_float_rep_of_byte(jvmti_env, thread, depth, slot)));
            break;
        }
        case 'C': {
            return std::shared_ptr<representable>(
                    new primitive_rep(get_float_rep_of_char(jvmti_env, thread, depth, slot)));
            break;
        }
        case 'D': {
            return std::shared_ptr<representable>(
                    new primitive_rep(get_float_rep_of_double(jvmti_env, thread, depth, slot)));
            break;
        }
        case 'F': {
            return std::shared_ptr<representable>(
                    new primitive_rep(get_float_rep_of_float(jvmti_env, thread, depth, slot)));
            break;
        }
        case 'I': {
            return std::shared_ptr<representable>(
                    new primitive_rep(get_float_rep_of_int(jvmti_env, thread, depth, slot)));
            break;
        }
        case 'J': {
            return std::shared_ptr<representable>(
                    new primitive_rep(get_float_rep_of_long(jvmti_env, thread, depth, slot)));
            break;
        }
        case 'S': {
            return std::shared_ptr<representable>(
                    new primitive_rep(get_float_rep_of_short(jvmti_env, thread, depth, slot)));
            break;
        }
        case 'Z': {
            return std::shared_ptr<representable>(
                    new primitive_rep(get_float_rep_of_boolean(jvmti_env, thread, depth, slot)));
            break;
        }
        case 'L': {
            jobject obj = jw::get_local_object(jvmti_env, thread, depth, slot);
            return get_float_rep_of_object(jvmti_env, jniEnv, obj);
            break;
        }

        case '[': {
            jobject obj = jw::get_local_object(jvmti_env, thread, depth, slot);
            return get_float_rep_of_array(jniEnv, jvmti_env, obj, signature.substr(1));
            break;
        }
        default:
            throw unsupported_type_exception("Type " + signature + " not supported.");
    }
    throw unsupported_type_exception("Type " + signature + " not supported.");
}

std::shared_ptr<representable> get_float_rep_of_object_array(JNIEnv *jniEnv, jvmtiEnv *jvmti_env, jobjectArray arr) {
    jsize size = jniEnv->GetArrayLength(arr);
    return std::shared_ptr<representable>(new java_array_rep(1, std::vector<float>{static_cast<float>(size)}));

    /* *
     *
    if (size == 0) {
        std::vector<float> rep({0});
        return std::shared_ptr<representable>(new java_array_rep(1, rep));
    }

    std::vector<float> rep;
    for (auto i = 0; i < size; ++i) {
        jobject obj = jniEnv->GetObjectArrayElement(arr, i);
        auto obj_rep = get_float_rep_of_object(jniEnv, obj);
        std::vector<float> r = obj_rep->get_float_representation();
        rep.insert(rep.end(), r.begin(), r.end());
    }

    return std::shared_ptr<representable>(new java_array_rep(rep.size(), rep));

    * */
}


std::shared_ptr<representable>
get_float_rep_of_primitive_array(JNIEnv *jniEnv, jvmtiEnv *jvmti_env, jobject obj, const std::string &signature) {

    std::vector<float> rep;
    switch (signature[0]) {
        case 'B': {
            auto arr = reinterpret_cast<jbyteArray>(obj);
            jsize size = jniEnv->GetArrayLength(reinterpret_cast<jarray>(arr));
            // jbyte* data = jniEnv->GetByteArrayElements(arr, nullptr);
            rep.push_back(static_cast<float>(size));
            /* *
            for(auto i = 0; i < size; ++i) {
                rep.push_back(static_cast<float>(data[i]));
            }
            * */
            break;
        }
        case 'C': {
            auto arr = reinterpret_cast<jcharArray>(obj);
            jsize size = jniEnv->GetArrayLength(reinterpret_cast<jarray>(arr));
            //jchar* data = jniEnv->GetCharArrayElements(arr, nullptr);
            /*
             * for(auto i = 0; i < size; ++i) {
                rep.push_back(static_cast<float>(data[i]));
            }
             */
            rep.push_back(static_cast<float>(size));

            break;
        }
        case 'D': {
            auto arr = reinterpret_cast<jdoubleArray>(obj);
            jsize size = jniEnv->GetArrayLength(reinterpret_cast<jarray>(arr));
            // jdouble * data = jniEnv->GetDoubleArrayElements(arr, nullptr);
            /* for(auto i = 0; i < size; ++i) {
                rep.push_back(static_cast<float>(data[i]));
            } */
            rep.push_back(static_cast<float>(size));

            break;
        }
        case 'F': {
            auto arr = reinterpret_cast<jfloatArray>(obj);
            jsize size = jniEnv->GetArrayLength(reinterpret_cast<jarray>(arr));
            // jfloat * data = jniEnv->GetFloatArrayElements(arr, nullptr);
            /*for(auto i = 0; i < size; ++i) {
                rep.push_back(data[i]);
            }*/
            rep.push_back(static_cast<float>(size));

            break;
        }
        case 'I': {
            auto arr = reinterpret_cast<jintArray>(obj);
            jsize size = jniEnv->GetArrayLength(reinterpret_cast<jarray>(arr));
            // jint * data = jniEnv->GetIntArrayElements(arr, nullptr);
            /* for(auto i = 0; i < size; ++i) {
                rep.push_back(static_cast<float>(data[i]));
            }*/
            rep.push_back(static_cast<float>(size));
            break;
        }
        case 'J': {
            auto arr = reinterpret_cast<jlongArray>(obj);
            jsize size = jniEnv->GetArrayLength(reinterpret_cast<jarray>(arr));
            // jlong* data = jniEnv->GetLongArrayElements(arr, nullptr);
            /*for(auto i = 0; i < size; ++i) {
                rep.push_back(static_cast<float>(data[i]));
            }*/
            rep.push_back(static_cast<float>(size));
            break;
        }
        case 'S': {
            auto arr = reinterpret_cast<jshortArray>(obj);
            jsize size = jniEnv->GetArrayLength(reinterpret_cast<jarray>(arr));
            // jshort * data = jniEnv->GetShortArrayElements(arr, nullptr);
            /* for(auto i = 0; i < size; ++i) {
                 rep.push_back(static_cast<float>(data[i]));
             }*/
            rep.push_back(static_cast<float>(size));
            break;
        }
        case 'Z': {
            auto arr = reinterpret_cast<jbooleanArray>(obj);
            jsize size = jniEnv->GetArrayLength(reinterpret_cast<jarray>(arr));
            // jboolean * data = jniEnv->GetBooleanArrayElements(arr, nullptr);
            /*for(auto i = 0; i < size; ++i) {
                rep.push_back(static_cast<float>(data[i]));
            }*/
            rep.push_back(static_cast<float>(size));
            break;
        }
        default: {
            throw jw::exception("Given Element is not a primitive array type");
        }
    }

    return std::shared_ptr<representable>(new java_array_rep(rep.size(), rep));
}

std::shared_ptr<representable>
get_float_rep_of_array(JNIEnv *jniEnv, jvmtiEnv *jvmti_env, jobject obj, const std::string &signature) {
    if (signature[0] == 'L') {
        //jobjectArray arr = static_cast<jobjectArray>(jw::get_local_object(jvmti_env, thread, depth, slot));
        // std::cout << get_float_rep_of_object_array(jniEnv, jvmti_env, static_cast<jobjectArray>(obj)) << std::endl;
        return get_float_rep_of_object_array(jniEnv, jvmti_env, static_cast<jobjectArray>(obj));
    } else {
        return get_float_rep_of_primitive_array(jniEnv, jvmti_env, obj, signature);
    }
}

std::vector<float> get_float_rep_of_integer(JNIEnv *jniEnv, jobject obj) {
    jw::j_class klass(jniEnv, obj);
    jfieldID id = klass.get_field_handle_by_name(jniEnv, "value", "I");
    jint iVal = jniEnv->GetIntField(obj, id);
    return std::vector<float>({static_cast<float>(iVal)});
}


std::vector<float> get_float_rep_of_util_list(JNIEnv *jniEnv, jobject obj) {
    std::vector<float> res;

    return res;
}

std::shared_ptr<representable> get_float_rep_of_object(jvmtiEnv *jvmti, JNIEnv *jniEnv, jobject obj) {

    std::shared_ptr<representable> res;

    if (jniEnv->IsInstanceOf(obj, jw::get_class_by_name(jniEnv, "java/lang/String"))) {
        jsize size = jniEnv->GetStringUTFLength((jstring) obj);
        res = std::make_shared<primitive_rep>(std::vector<float>({static_cast<float>(size)}));
    } else if (jniEnv->IsInstanceOf(obj, jw::get_class_by_name(jniEnv, "java/lang/Integer"))) {
        res = std::make_shared<primitive_rep>(get_float_rep_of_integer(jniEnv, obj));
    } else if (jniEnv->IsInstanceOf(obj, jw::get_class_by_name(jniEnv, "java/util/LinkedList"))) {
        jw::j_class klass{jniEnv, obj};
        // jw::method x = klass.get_method_by_name_sig(jvmti, jniEnv, "size", "()I");
        // jint i = jniEnv->CallIntMethod(obj, x.id);
        // res = std::make_shared<list_rep>(std::vector<float>({static_cast<float>(i)}));
        jfieldID x = klass.get_field_handle_by_name(jniEnv, "size", "I");
        jint v = jniEnv->GetIntField(obj, x);
        res = std::make_shared<list_rep>(std::vector<float>({static_cast<float>(v)}));
    } else if (jniEnv->IsInstanceOf(obj, jw::get_class_by_name(jniEnv, "java/util/LinkedList$ListItr"))) {
        jw::j_class klass{jniEnv, obj};
        jfieldID x = klass.get_field_handle_by_name(jniEnv, "nextIndex", "I");
        jint v = jniEnv->GetIntField(obj, x);
        res = std::make_shared<iterator_rep>(std::vector<float>({static_cast<float>(v)}));
    } else { // TODO add here
        //jniEnv->IsInstanceOf(obj, jw::get_class_by_name(jniEnv, "java/util/ArrayList"));
        res = std::make_shared<primitive_rep>(std::vector<float>({0}));
        //throw unsupported_type_exception("Type " + signature + " not supported.");
    }

    return res;
}


std::vector<float> get_float_rep_of_byte(jvmtiEnv *jvmti_env, jthread thread, jint depth, jint slot) {
    return std::vector<float>{static_cast<float>(jw::get_local_int(jvmti_env, thread, depth, slot))};
}

std::vector<float> get_float_rep_of_int(jvmtiEnv *jvmti_env, jthread thread, jint depth, jint slot) {
    return std::vector<float>{static_cast<float>(jw::get_local_int(jvmti_env, thread, depth, slot))};
}

std::vector<float> get_float_rep_of_short(jvmtiEnv *jvmti_env, jthread thread, jint depth, jint slot) {
    return std::vector<float>{static_cast<float>(jw::get_local_int(jvmti_env, thread, depth, slot))};
}

std::vector<float> get_float_rep_of_boolean(jvmtiEnv *jvmti_env, jthread thread, jint depth, jint slot) {
    return std::vector<float>{static_cast<float>(jw::get_local_int(jvmti_env, thread, depth, slot))};
}

std::vector<float> get_float_rep_of_char(jvmtiEnv *jvmti_env, jthread thread, jint depth, jint slot) {
    return std::vector<float>{static_cast<float>(jw::get_local_int(jvmti_env, thread, depth, slot))};
}

std::vector<float> get_float_rep_of_float(jvmtiEnv *jvmti_env, jthread thread, jint depth, jint slot) {
    return std::vector<float>{(jw::get_local_float(jvmti_env, thread, depth, slot))};
}

std::vector<float> get_float_rep_of_double(jvmtiEnv *jvmti_env, jthread thread, jint depth, jint slot) {
    return std::vector<float>{static_cast<float>(jw::get_local_double(jvmti_env, thread, depth, slot))};
}

std::vector<float> get_float_rep_of_long(jvmtiEnv *jvmti_env, jthread thread, jint depth, jint slot) {
    return std::vector<float>{static_cast<float>(jw::get_local_long(jvmti_env, thread, depth, slot))};
}

