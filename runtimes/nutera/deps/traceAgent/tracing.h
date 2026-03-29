//
// Created by julian on 21.07.20.
//

#ifndef JAVAMEMTRACEAGENT_TRACING_H
#define JAVAMEMTRACEAGENT_TRACING_H

#include <jni.h>
#include "jvmti_wrapper/jvmti_wrapper.h"
#include <map>
#include <list>
#include <nlohmann/json.hpp>


using json = nlohmann::json;


class unsupported_type_exception : std::exception {
private:
    std::string message;
public:
    explicit unsupported_type_exception(std::string &message) : message{message} {}
    explicit unsupported_type_exception(std::string &&message) : message{message} {}

    [[nodiscard]] const char *what() const noexcept override {
        return message.c_str();
    }
};

class missing_information_exception : std::exception {
private:
    std::string message;
public:
    explicit missing_information_exception(std::string &message) : message{message} {}
    explicit missing_information_exception(std::string &&message) : message{message} {}

    [[nodiscard]] const char *what() const noexcept override {
        return message.c_str();
    }
};

// Todo make more efficient and place in right place, maybe find a cannonical way of doing it
std::string intercallate_string_list(std::list<std::string> lst, const std::string& s);

struct identifier {

    jw::method method;
    std::string id_name;


    //explicit identifier(jw::method& method): method(method) {}
    identifier()= default;

    identifier(jw::method& method, std::string& id_name): method(method), id_name(id_name) {}
    identifier(identifier& id) = default;

    bool operator==(identifier& id) const {
        return !((!(this->method == id.method)) || (this->id_name != id.id_name));
    }

};


/* *
 *
 * IMPORTANT: get_float_representation get_position_name should return vectors of the same length.
 * Not entirely sure yet how I should enfroce this all the time ->
 * I was thinking about simply working with a list of pairs. However, I discarded this idea.
 *
 */
struct representable {
public:

    [[nodiscard]] virtual identifier get_identifier() = 0;
    virtual void set_identifier(const identifier& id) = 0;
    virtual void add_name(const std::string& name) = 0;
    [[nodiscard]] virtual int get_size() const = 0;
    virtual void set_size(int size) = 0;
    [[nodiscard]] virtual std::vector<float> get_float_representation() = 0;
    [[nodiscard]] virtual std::vector<std::string> get_position_name() = 0;

};



class primitive_rep : public representable {

    std::list<std::string> id_path;

    identifier id;
    int size = 1;
    std::vector<float> rep;

public:

    primitive_rep(identifier& id, std::vector<float>& rep):
        id(id),  rep(rep)
    {

    }

    explicit primitive_rep(std::vector<float>& rep): rep(rep)
    { }

    explicit primitive_rep(std::vector<float>&& rep): rep(rep)
    { }

    void set_identifier(const identifier& iden) override {
        this->id = iden;
    }

    void add_name(const std::string& name) override {
        id_path.push_front(name);
    }

    [[nodiscard]] identifier get_identifier() override
    {
        return this->id;
    }

    [[nodiscard]] int get_size() const override
    {
        return this->size;
    }

    void set_size(int size_) override
    {
    }

    [[nodiscard]] std::vector<float> get_float_representation() override
    {
        return this->rep;
    }


    std::vector<std::string> get_position_name() override
    {
        if (!id_path.empty()) {
            return std::vector<std::string>{intercallate_string_list(id_path, ".") + "." + this->id.id_name};
        } else {
            return std::vector<std::string>{this->id.id_name};
        }
    }

};

class list_rep : public representable {

    std::list<std::string> id_path;

    identifier id;
    int size = 1;
    std::vector<float> rep;

public:

    list_rep(identifier& id, std::vector<float>& rep):
            id(id),  rep(rep)
    {

    }

    explicit list_rep(std::vector<float>& rep): rep(rep)
    { }

    explicit list_rep(std::vector<float>&& rep): rep(rep)
    { }

    void set_identifier(const identifier& iden) override {
        this->id = iden;
    }

    void add_name(const std::string& name) override {
        id_path.push_front(name);
    }

    [[nodiscard]] identifier get_identifier() override
    {
        return this->id;
    }

    [[nodiscard]] int get_size() const override
    {
        return this->size;
    }

    void set_size(int size_) override
    {
    }

    [[nodiscard]] std::vector<float> get_float_representation() override
    {
        return this->rep;
    }


    std::vector<std::string> get_position_name() override
    {
        if (!id_path.empty()) {
            return std::vector<std::string>{intercallate_string_list(id_path, ".") + "." + this->id.id_name + ".size()"};
        } else {
            return std::vector<std::string>{id.id_name + "." + "size"};
        }
    }

};



class iterator_rep : public representable {

    std::list<std::string> id_path;

    identifier id;
    int size = 1;
    std::vector<float> rep;

public:

    iterator_rep(identifier& id, std::vector<float>& rep):
            id(id),  rep(rep)
    {

    }

    explicit iterator_rep(std::vector<float>& rep): rep(rep)
    { }

    explicit iterator_rep(std::vector<float>&& rep): rep(rep)
    { }

    void set_identifier(const identifier& iden) override {
        this->id = iden;
    }

    void add_name(const std::string& name) override {
        id_path.push_front(name);
    }

    [[nodiscard]] identifier get_identifier() override
    {
        return this->id;
    }

    [[nodiscard]] int get_size() const override
    {
        return this->size;
    }

    void set_size(int size_) override
    {
    }

    [[nodiscard]] std::vector<float> get_float_representation() override
    {
        return this->rep;
    }

    std::vector<std::string> get_position_name() override
    {
        if (!id_path.empty()) {
            return std::vector<std::string>{intercallate_string_list(id_path, ".") + "." + this->id.id_name + ".cursor"};
        } else {
            return std::vector<std::string>{id.id_name + "." + "cursor"};
        }
    }
};


/* *
 * Maybe this needs to be changed down the line, as it currently does not allow for proper ids for object array
 * components. i.e. pair.first.value cannot be attributed properly
 * */
class java_array_rep : public representable {

    std::list<std::string> id_path;

    identifier id;
    int size;
    std::vector<float> rep;

public:


    java_array_rep(identifier& id, int size, std::vector<float>& rep):
    id(id), size(size), rep(rep)
    {

    }

    java_array_rep(int size, std::vector<float>& rep):  size(size), rep(rep) {

    }

    java_array_rep(int size, std::vector<float>&& rep):  size(size), rep(rep) {

    }

    void add_name(const std::string& name) override {
        id_path.push_front(name);
    }

    [[nodiscard]] identifier get_identifier() override
    {
        return this->id;
    }

    void set_identifier(const identifier& i) override {
        this->id = i;
    }

    [[nodiscard]] int get_size() const override
    {
        if (size == 0) {
            return 1;
        }
        return this->size;
    }

    void set_size(int size_) override
    {
        //this->size = size_;
    }

    [[nodiscard]] std::vector<float> get_float_representation() override
    {
        if (size == 0) {
            return std::vector<float>{0};
        }
        return this->rep;
    }

    std::vector<std::string> get_position_name() override
    {
        std::vector<std::string> vec;
        vec.push_back(id.id_name + "." + "length");

        if (!id_path.empty()) {
            return std::vector<std::string>{intercallate_string_list(id_path, ".") + "." + this->id.id_name + ".length"};
        } else {
            return std::vector<std::string>{id.id_name + "." + "length"};
        }

    }

};


class trace_frame {
    long location; // offset
    jw::method method;
    //std::vector<float> float_rep;
    //std::vector<identifier> identifiers;

    std::vector<std::shared_ptr<representable>> representations;


public:

    trace_frame(long location, const jw::method& md): location(location), method(md){}
    trace_frame(const trace_frame& t_frame) = default;

    [[nodiscard]] long get_location() const;
    void set_location(long location);

    [[nodiscard]] const jw::method &get_method() const;
    void set_method(const jw::method &method);

    [[nodiscard]] std::vector<float> get_float_rep() const;

    [[nodiscard]] std::vector<std::string> get_pos_names() const;

    void set_representation(const std::shared_ptr<representable>& rep) {
        this->representations.push_back(rep);
    }

    [[nodiscard]] json get_json_representation() const;

};

class trace {
    std::string class_name;
    std::string method_name;
    std::string output_file;
    std::vector<trace_frame> stack_images;

public:

    trace(std::string& class_name, std::string& method_name, std::string& output_file);
    trace(std::string&& class_name, std::string&& method_name, std::string&& output_file);

    [[nodiscard]] const std::string &get_class_name() const;
    [[nodiscard]] const std::string &get_method_name() const;
    [[nodiscard]] const std::vector<trace_frame> &get_stack_images() const;
    [[nodiscard]] const std::string &get_output_file() const;

    void push_back_frame(trace_frame& frame);

    json get_json_representation() {
        json j;
        j["class"] = class_name;
        j["method"] = method_name;

        std::vector<json> vec;
        for (auto& x : stack_images) {
            vec.push_back(x.get_json_representation());
        }
        j["frames"] = vec;

        return j;
    }

    [[nodiscard]] std::vector<std::vector<float>> get_matrix_format() const {
        std::vector<std::vector<float>> mat;

        for(const trace_frame &frame : this->get_stack_images()) {
            mat.push_back(frame.get_float_rep());
        }


        //unsigned long longest = 0;
        //for (auto& x: mat) {
        //    longest = std::max(x.size(), longest);
        //}

        //for(std::vector<float>& frame : mat) {
        //    while(frame.size() < longest){
        //        frame.push_back(0);
        //    }
        //}

        return mat;
    }

    [[nodiscard]] std::vector<std::vector<std::string>> get_id_names_list() const {
        std::vector<std::vector<std::string>> lists;

        for(const trace_frame &frame : this->get_stack_images()) {
            lists.push_back(frame.get_pos_names());
        }
        return lists;
    }

    [[nodiscard]] std::vector<std::string> get_location_list() const {
        std::vector<std::string> res;

        for(const trace_frame &frame : this->get_stack_images()) {
            std::string s(std::to_string(frame.get_location()));
            res.push_back(s + "@" + frame.get_method().name.name);
        }

        return res;
    }

};

std::shared_ptr<representable> get_rep_of_invalid_variable(JNIEnv* jni_env, jvmtiEnv* jvmti_env, jthread thread, jint depth, jint slot, const std::string& signature);

std::shared_ptr<representable> get_rep_of_variable(JNIEnv* jni_env, jvmtiEnv* jvmti_env, jthread thread, jint depth, jint slot, std::string signature);

std::shared_ptr<representable> get_float_rep_of_object(jvmtiEnv* jvmti_env, JNIEnv* jniEnv, jobject obj);

std::shared_ptr<representable> get_float_rep_of_array(JNIEnv* jniEnv, jvmtiEnv* jvmti_env, jobject obj, const std::string& signature);

// Primitive Types
std::vector<float> get_float_rep_of_byte(jvmtiEnv* jvmti_env, jthread thread, jint depth, jint slot);

std::vector<float> get_float_rep_of_int(jvmtiEnv* jvmti_env, jthread thread, jint depth, jint slot);

std::vector<float> get_float_rep_of_short(jvmtiEnv* jvmti_env, jthread thread, jint depth, jint slot);

std::vector<float> get_float_rep_of_boolean(jvmtiEnv* jvmti_env, jthread thread, jint depth, jint slot);

std::vector<float> get_float_rep_of_char(jvmtiEnv* jvmti_env, jthread thread, jint depth, jint slot);

std::vector<float> get_float_rep_of_float(jvmtiEnv* jvmti_env, jthread thread, jint depth, jint slot);

std::vector<float> get_float_rep_of_double(jvmtiEnv* jvmti_env, jthread thread, jint depth, jint slot);

std::vector<float> get_float_rep_of_long(jvmtiEnv* jvmti_env, jthread thread, jint depth, jint slot);


#endif //JAVAMEMTRACEAGENT_TRACING_H
