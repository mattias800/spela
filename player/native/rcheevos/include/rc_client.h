#ifndef RC_CLIENT_H
#define RC_CLIENT_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Forward declarations */
typedef struct rc_client_t rc_client_t;
typedef struct rc_client_event_t rc_client_event_t;
typedef struct rc_client_game_t rc_client_game_t;
typedef struct rc_client_achievement_t rc_client_achievement_t;

/* Event types */
#define RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED   1
#define RC_CLIENT_EVENT_GAME_COMPLETED          2
#define RC_CLIENT_EVENT_CHALLENGE_INDICATOR_SHOW 3
#define RC_CLIENT_EVENT_CHALLENGE_INDICATOR_HIDE 4
#define RC_CLIENT_EVENT_LEADERBOARD_STARTED      5
#define RC_CLIENT_EVENT_LEADERBOARD_FAILED       6
#define RC_CLIENT_EVENT_LEADERBOARD_SUBMITTED    7
#define RC_CLIENT_EVENT_SERVER_ERROR             8

/* Achievement categories */
#define RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE      1
#define RC_CLIENT_ACHIEVEMENT_CATEGORY_UNOFFICIAL 2

/* Achievement states */
#define RC_CLIENT_ACHIEVEMENT_STATE_ACTIVE    0
#define RC_CLIENT_ACHIEVEMENT_STATE_INACTIVE  1

/* Return codes */
#define RC_OK 0
#define RC_INVALID_STATE -1
#define RC_API_FAILURE -2

/* Achievement info */
struct rc_client_achievement_t {
    uint32_t id;
    const char* title;
    const char* description;
    uint32_t points;
    const char* badge_name;
    uint8_t category;
    uint8_t state;
    uint8_t unlocked;
};

/* Event structure */
struct rc_client_event_t {
    uint32_t type;
    rc_client_achievement_t* achievement;
};

/* Game info */
struct rc_client_game_t {
    uint32_t id;
    const char* title;
    const char* badge_name;
};

/* HTTP request/response for server communication */
typedef struct rc_api_request_t {
    const char* url;
    const char* post_data;
    const char* content_type;
} rc_api_request_t;

typedef struct rc_api_server_response_t {
    const char* body;
    size_t body_length;
    int http_status_code;
} rc_api_server_response_t;

/* Callback types */
typedef void (*rc_client_event_handler_t)(const rc_client_event_t* event, rc_client_t* client);
typedef uint32_t (*rc_client_read_memory_t)(uint32_t address, uint8_t* buffer, uint32_t num_bytes, rc_client_t* client);
typedef void (*rc_client_server_callback_t)(const rc_api_server_response_t* response, void* callback_data);
typedef void (*rc_client_server_call_t)(const rc_api_request_t* request, rc_client_server_callback_t callback, void* callback_data, rc_client_t* client);
typedef void (*rc_client_message_callback_t)(const char* message, const rc_client_t* client);

/* Client lifecycle */
rc_client_t* rc_client_create(rc_client_read_memory_t read_memory, rc_client_server_call_t server_call);
void rc_client_destroy(rc_client_t* client);

/* Configuration */
void rc_client_set_event_handler(rc_client_t* client, rc_client_event_handler_t handler);
void rc_client_set_hardcore_enabled(rc_client_t* client, int enabled);
int rc_client_get_hardcore_enabled(const rc_client_t* client);

/* Authentication */
typedef void (*rc_client_callback_t)(int result, const char* error_message, rc_client_t* client, void* userdata);
void rc_client_begin_login_with_token(rc_client_t* client, const char* username, const char* token, rc_client_callback_t callback, void* userdata);

/* Game loading */
typedef void (*rc_client_load_game_callback_t)(int result, const char* error_message, rc_client_t* client, void* userdata);
void rc_client_begin_load_game(rc_client_t* client, const char* hash, rc_client_load_game_callback_t callback, void* userdata);
void rc_client_unload_game(rc_client_t* client);

/* Per-frame processing */
void rc_client_do_frame(rc_client_t* client);

/* Game info */
const rc_client_game_t* rc_client_get_game_info(const rc_client_t* client);

#ifdef __cplusplus
}
#endif

#endif /* RC_CLIENT_H */
