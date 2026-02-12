/*
 * Stub implementation of the rcheevos rc_client API.
 * These provide empty function bodies so the library links successfully.
 * They will be replaced with the real rcheevos source files once vendored.
 */

#include "rc_client.h"
#include <stdlib.h>

/* Opaque client struct -- just enough to allocate/free */
struct rc_client_t {
    rc_client_read_memory_t read_memory;
    rc_client_server_call_t server_call;
    rc_client_event_handler_t event_handler;
    int hardcore_enabled;
};

rc_client_t* rc_client_create(rc_client_read_memory_t read_memory, rc_client_server_call_t server_call) {
    rc_client_t* client = (rc_client_t*)calloc(1, sizeof(rc_client_t));
    if (client) {
        client->read_memory = read_memory;
        client->server_call = server_call;
    }
    return client;
}

void rc_client_destroy(rc_client_t* client) {
    free(client);
}

void rc_client_set_event_handler(rc_client_t* client, rc_client_event_handler_t handler) {
    if (client) {
        client->event_handler = handler;
    }
}

void rc_client_set_hardcore_enabled(rc_client_t* client, int enabled) {
    if (client) {
        client->hardcore_enabled = enabled;
    }
}

int rc_client_get_hardcore_enabled(const rc_client_t* client) {
    return client ? client->hardcore_enabled : 0;
}

void rc_client_begin_login_with_token(rc_client_t* client, const char* username, const char* token,
                                       rc_client_callback_t callback, void* userdata) {
    /* Stub: immediately report success */
    if (callback) {
        callback(RC_OK, NULL, client, userdata);
    }
}

void rc_client_begin_load_game(rc_client_t* client, const char* hash,
                                rc_client_load_game_callback_t callback, void* userdata) {
    /* Stub: immediately report success */
    if (callback) {
        callback(RC_OK, NULL, client, userdata);
    }
}

void rc_client_unload_game(rc_client_t* client) {
    (void)client;
}

void rc_client_do_frame(rc_client_t* client) {
    (void)client;
}

const rc_client_game_t* rc_client_get_game_info(const rc_client_t* client) {
    (void)client;
    return NULL;
}
