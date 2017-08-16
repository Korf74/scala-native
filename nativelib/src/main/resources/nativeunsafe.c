#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

typedef uintptr_t word_t;
typedef word_t* Field_t;

typedef struct {
    struct {
        int32_t id;
        word_t *name;
        int8_t kind;
    } rt;
    int64_t size;
    struct {
        int32_t from;
        int32_t to;
    } range;
    struct {
        int32_t dyn_method_count;
        word_t *dyn_method_salt;
        word_t *dyn_method_keys;
        word_t *dyn_methods;
    } dynDispatchTable;
    int64_t *refMapStruct;
} Rtti;

typedef struct {
    Rtti *rtti;
    Field_t fields[0];
} Object;

void update_object(Object* obj, Object* upd) {
    *obj = *upd;
}