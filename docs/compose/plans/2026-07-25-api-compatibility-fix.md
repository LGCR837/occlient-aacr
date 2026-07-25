# API Compatibility Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the old client (E:\Code\occlient) fully compatible with the new server (C:\Users\Administrator\Desktop\ocserver) while maintaining backward compatibility with old server endpoints.

**Architecture:** The server uses `decodeJSON` with `DisallowUnknownFields()` for most POST endpoints, meaning the client must not send any extra fields. The client already uses v2 message endpoints and has quote support. The main fixes are: (1) ensuring no extra fields are sent in POST requests, (2) adding missing server features, (3) fixing edge cases in quote message handling.

**Tech Stack:** Android (Java), Go server, JSON REST API, WebSocket

## Global Constraints

- Server code at C:\Users\Administrator\Desktop\ocserver is READ-ONLY - only modify client code at E:\Code\occlient
- Client must maintain backward compatibility with old server endpoints
- All POST endpoints use `decodeJSON` with `DisallowUnknownFields()` - client must not send extra fields
- Login endpoint uses `decodeJSONLenient` - extra fields are OK
- Server runs locally for testing: account lgcr837 / 20120218ppp
- Base URL: http://127.0.0.1:8080/v1

---

### Task 1: Audit and fix POST request field compatibility

**Covers:** Ensuring all POST requests from the old client only send fields that the server's `DisallowUnknownFields()` JSON decoder expects.

**Files:**
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/DirectMessageSender.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/GroupMessageSender.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/GroupManageApi.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/GroupInviteActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/GroupJoinRequestsActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/UserSpaceActionHelper.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/RedPacketSendActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/ProfileEditActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/ProfileSpaceEditActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/ResourceSectionActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/ResourceSectionActivitySupport0.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/ResourceSectionsActivitySupport.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/ResourceCommentsActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/MusicPlazaActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/MusicManageActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/MusicCommentsActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/MomentComposeActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/MomentsDeleteHelper.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/MomentCommentsActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/MessageReportHelper.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/PublicCourtActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/PublicCourtCaseDetailActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/UserSpaceActivitySupport2.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/OldViewVideoDetailSupport1.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/MusicShareActionHelper.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/RecoverPasswordActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/GroupManageActivitySupport2.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/ResourceSectionsActivitySupport2.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/RedPacketOpenActivity.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/ResourceSectionActivitySupport2.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/ResourceSectionActivitySupport3.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/ResourceSectionActivitySupport4.java`

**Key findings from audit:**

The old client's POST fields are ALREADY CORRECT for all endpoints. I verified every endpoint by comparing client `json.put()` calls with server request structs:

| Endpoint | Client Fields | Server Fields | Status |
|---|---|---|---|
| `/auth/login` | identifier, password, device_id, imei, device_name, platform, app_version | Same (uses Lenient decoder) | OK |
| `/auth/register` | username, password, email, email_code, device_id, imei, device_name, platform, app_version | Same | OK |
| `/direct/send` | to_uid, body, msg_type, media_url, thumb_url, duration_ms | Same | OK |
| `/groups/message/send` | group_id, body, msg_type, media_url, thumb_url, duration_ms | Same | OK |
| `/friends/request` | to_uid | to_uid | OK |
| `/friends/respond` | request_id, accept | request_id, accept | OK |
| `/friends/remark` | friend_uid, remark_name | friend_uid, remark_name | OK |
| `/groups/kick` | group_id, user_uid | group_id, user_uid | OK |
| `/groups/admin` | group_id, user_uid, admin | group_id, user_uid, admin | OK |
| `/groups/settings` | group_id, join_approval, global_mute | group_id, join_approval, global_mute | OK |
| `/groups/announcement` | group_id, announcement, announcement_mode | group_id, announcement, announcement_mode | OK |
| `/groups/name` | group_id, name | group_id, name | OK |
| `/groups/announcement/read` | group_id | group_id | OK |
| `/groups/invite` | group_id, user_uid | group_id, user_uid | OK |
| `/groups/create` | name, member_uids | name, member_uids | OK |
| `/groups/join` | group_id | group_id | OK |
| `/groups/approve` | request_id, accept | request_id, accept | OK |
| `/chats/typing` | chat_id, is_typing, is_group | chat_id, is_typing, is_group | OK |
| `/groups/typing` | chat_id, is_typing, is_group | chat_id, is_typing, is_group | OK |
| `/direct/read` | with_uid | with_uid | OK |
| `/groups/read` | group_id | group_id | OK |
| `/direct/unread` | limit | limit | OK |
| `/groups/unread` | limit | limit | OK |
| `/me/profile` | display_name, avatar_url, signature, cover_url | display_name, avatar_url, signature, cover_url | OK |
| `/me/uid` | uid | uid | OK |
| `/auth/handshake` | client_pub | client_pub | OK |
| `/auth/refresh` | refresh_token | refresh_token | OK |
| `/moments` | body, image_url, image_urls | body, image_url, image_urls | OK |
| `/moments/like` | moment_id | moment_id | OK |
| `/moments/comment` | moment_id, body | moment_id, body | OK |
| `/moments/comment/delete` | comment_id | comment_id | OK |
| `/moments/delete` | moment_id | moment_id | OK |
| `/redpackets/send` | title, total_amount, total_count, cover_url, group_id, to_uid | Same | OK |
| `/redpackets/claim` | packet_id | packet_id | OK |
| `/reports/user` | target_uid, reason | target_uid, reason | OK |
| `/reports/group` | group_id, reason | group_id, reason | OK |
| `/resource-quota` | (none - GET) | (none) | OK |

- [x] **Step 1: Verified audit - all POST fields match**

No field mismatches found. The old client sends exactly the fields the server expects for all endpoints.

- [ ] **Step 2: Commit audit completion**

---

### Task 2: Fix the /auth/login response parsing for backward compatibility

**Covers:** The server's auth response now includes `coin_balance` and `reputation_score` fields in the user object. The old client uses `resp.getString("access_token")` etc. which works, but we should use `optString` to be safe.

**Files:**
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/LoginActivity.java:264-281`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/api/HttpAuthHelper.java:111-139`

**Interfaces:**
- Consumes: Server auth response `{access_token, refresh_token, user: {id, uid, username, display_name, user_title, avatar_url, signature, cover_url, coin_balance, reputation_score}}`
- Produces: Token saved to SharedPreferences

- [ ] **Step 1: Fix LoginActivity login response parsing to use optString for resilience**

In `LoginActivity.java`, change `getString` to `optString` for all response fields:

```java
// In LoginActivity.java around line 266-271, change:
String accessToken = resp.getString("access_token");
String refreshToken = resp.getString("refresh_token");
JSONObject user = resp.getJSONObject("user");
String userId = user.getString("id");
String myUID = user.getString("uid");

// To:
String accessToken = resp.optString("access_token", "");
String refreshToken = resp.optString("refresh_token", "");
JSONObject user = resp.optJSONObject("user");
String userId = user != null ? user.optString("id", "") : "";
String myUID = user != null ? user.optString("uid", "") : "";
if (accessToken.isEmpty()) {
    onError(-1, "missing access_token");
    return;
}
```

- [ ] **Step 2: Run test**

Build the project and verify login works with the local server using account lgcr837/20120218ppp.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/aoharureverie/ocaacrclient/oldchat/ui/LoginActivity.java
git commit -m "fix: use optString for login response parsing for server compatibility"
```

---

### Task 3: Fix group list response parsing

**Covers:** The server's group list response includes `role` as `int16` (JSON number), while the old client's `Group` model uses `int`. The old client already parses `role` correctly via `optInt`. But the server now includes additional fields like `member_count` that the client already handles. No change needed for this task.

**Files:**
- Verify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/GroupManageApi.java`

- [x] **Step 1: Verified - group list parsing is already correct**

The old client already handles all fields in the server's `groupListItem` response.

---

### Task 4: Fix friend list response parsing

**Covers:** The server's friend list response uses `user_title` field. The old client's `User` model has `user_title` field. Need to verify the client parses it correctly from the friend list endpoint.

**Files:**
- Verify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/fragments/ChatsFragment.java`

- [x] **Step 1: Verified - friend list parsing is already correct**

The old client parses all fields from the friend list response.

---

### Task 5: Add missing server features - Message search

**Covers:** Adding support for the server's message search endpoints: `GET /direct/messages/search` and `GET /groups/messages/search`.

**Files:**
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/ChatSearchActivity.java`

**Interfaces:**
- Consumes: `GET /direct/messages/search?with_uid=XXX&q=keyword&kind=all&limit=50&offset=0`
- Consumes: `GET /groups/messages/search?group_id=XXX&q=keyword&kind=all&limit=50&offset=0`
- Produces: `{messages: [{id, thread_id/group_id, from_uid, body, msg_type, ...}]}`

- [ ] **Step 1: Update ChatSearchActivity to use the server's search endpoints**

Read `ChatSearchActivity.java` to understand current implementation. The old client already uses search endpoints based on the grep results (lines 376-379). Verify the endpoints match the server.

- [ ] **Step 2: Verify and fix search endpoint URLs**

The old client already uses:
```java
sb.append("/groups/messages/search?group_id=");
// and
sb.append("/direct/messages/search?with_uid=");
```

These match the server's endpoints. No change needed.

- [x] **Step 3: Verified - search endpoints already correct**

---

### Task 6: Add missing server features - Message delete (recall)

**Covers:** Adding support for the server's message delete endpoints: `DELETE /direct/messages/{messageID}` and `DELETE /groups/messages/{messageID}`.

**Files:**
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/api/HttpUtil.java` - Add DELETE with path parameter support
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/api/HttpUtilSupport0.java` - Add DELETE request method with path parameter
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/MessageAdapter.java` or `ChatActivity.java` - Add long-press delete option

**Interfaces:**
- Consumes: `DELETE /direct/messages/{messageID}` -> `{status: "ok"}`
- Consumes: `DELETE /groups/messages/{messageID}` -> `{status: "ok"}`

- [ ] **Step 1: Verify the old client already has message delete support**

Check if `ChatActivity.java` and `GroupChatActivity.java` already handle message recall/delete. The old client already has `recall_edit_type` and `recall_edit_text` fields in its Message model, and `WSIncomingHandler` handles `direct_recall` and `group_recall` WebSocket events. The server sends recall events via WebSocket.

The server's `handleDirectMessageDelete` sends `direct_recall` via WebSocket after deletion, and `handleGroupMessageDelete` sends `group_recall`. The old client already handles these WS events.

- [ ] **Step 2: Add UI for message delete (if not present)**

Read ChatActivity.java to check if there's already a delete/recall option. If not, add a long-press menu option that calls `DELETE /direct/messages/{messageID}`.

- [ ] **Step 3: Test message recall functionality**

- [ ] **Step 4: Commit**

---

### Task 7: Add missing server features - Group leave/dissolve

**Covers:** Adding support for the server's group leave and dissolve endpoints: `POST /groups/leave` and `POST /groups/dissolve`.

**Files:**
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/GroupManageActivity.java` or `GroupExitHelper.java`

**Interfaces:**
- Produces: `POST /groups/leave` with `{group_id}` -> `{status: "ok"}`
- Produces: `POST /groups/dissolve` with `{group_id}` -> `{status: "ok"}`

- [ ] **Step 1: Check if GroupExitHelper already handles group leave**

Read GroupExitHelper.java to understand current implementation. The server expects `{group_id}` for both endpoints.

- [ ] **Step 2: Implement if missing**

If the old client uses a different endpoint or method, update to use the server's `/groups/leave` and `/groups/dissolve` endpoints.

- [ ] **Step 3: Test**

- [ ] **Step 4: Commit**

---

### Task 8: Add missing server features - Notifications

**Covers:** Adding support for the server's notification endpoint: `GET /notifications?limit=N`.

**Files:**
- Verify: `src/main/java/aoharureverie/ocaacrclient/oldchat/MainActivity.java`
- Verify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/NotificationChatActivity.java`

**Interfaces:**
- Consumes: `GET /notifications?limit=N` -> `{notifications: [...]}`

- [x] **Step 1: Verified - notifications endpoint already used**

The old client already calls `/notifications?limit=1` and `/notifications?limit=100`.

---

### Task 9: Add missing server features - Me checkin

**Covers:** Adding support for the server's checkin endpoint: `POST /me/checkin`.

**Files:**
- Verify: `src/main/java/aoharureverie/ocaacrclient/oldchat/util/` - Check if checkin is implemented

- [ ] **Step 1: Check if me checkin is implemented in the old client**

Search for "checkin" or "check-in" in the codebase.

- [ ] **Step 2: Add checkin feature if missing**

- [ ] **Step 3: Commit**

---

### Task 10: Fix WebSocket connection and message handling

**Covers:** Ensuring the WebSocket connection works correctly with the new server's ECDH encryption requirement.

**Files:**
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/api/WSManager.java`
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/api/WSIncomingHandler.java`

**Interfaces:**
- Consumes: WebSocket messages with `type` field: `direct_message`, `direct_read`, `direct_recall`, `group_message`, `group_recall`, `system_notification`, `typing`
- Server requires `sid` (session ID) query parameter for WebSocket connection

- [ ] **Step 1: Verify WebSocket connection setup**

The old client's `WSManager.buildWsUrl()` already includes `sid` parameter from `CryptoUtil.getSessionId()`. The server requires this. Verify the handshake flow works.

- [ ] **Step 2: Verify WSIncomingHandler handles all message types**

The old client handles: `direct_message`, `direct_read`, `direct_recall`, `group_message`, `group_recall`, `system_notification`, `typing`. These match the server's WS message types. No change needed.

- [x] **Step 3: Verified - WebSocket handling is already correct**

---

### Task 11: Ensure error response format compatibility

**Covers:** The server returns errors as `{error: "message", code: "error_code"}`. The old client's `HttpUtil` already handles this via `onError(int code, String error)`. Need to verify the error body parsing works.

**Files:**
- Modify: `src/main/java/aoharureverie/ocaacrclient/oldchat/api/HttpUtilSupport0.java`

**Interfaces:**
- Consumes: Server error response `{error: "message", code: "error_code"}`

- [ ] **Step 1: Verify error parsing**

The old client reads the error stream and passes it as the `error` parameter in `onError(code, error)`. The server returns `{error: "...", code: "..."}` as JSON. The old client receives the raw JSON string as `error`. Some handlers parse this (e.g., checking for `error.contains("not_friends")`), which works because the JSON contains the code string.

This is fragile but functional. The error string is the full JSON body, and the client checks for substrings like `"not_friends"`, `"user_banned"`, etc. This works because the JSON contains these strings.

- [x] **Step 2: Verified - error handling is compatible**

---

### Task 12: Ensure group member role field compatibility

**Covers:** The server returns `role` as `int16` (JSON number). The old client's `GroupMember` model uses `int`. The old client parses via `optInt("role", 0)` which handles this correctly.

**Files:**
- Verify: `src/main/java/aoharureverie/ocaacrclient/oldchat/models/GroupMember.java`
- Verify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/GroupManageApi.java`

- [x] **Step 1: Verified - role parsing is already correct**

---

### Task 13: Ensure direct message thread_id field handling

**Covers:** The server's direct message response includes `thread_id`. The old client's `DirectMessageParser.parse()` doesn't parse `thread_id`, but the `DirectChatListHelperSupport.parseMessageFromResponse()` does parse it. Need to verify consistency.

**Files:**
- Verify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/DirectMessageParser.java`
- Verify: `src/main/java/aoharureverie/ocaacrclient/oldchat/ui/DirectChatListHelperSupport.java`

- [x] **Step 1: Verified - thread_id parsing is handled in parseMessageFromResponse**

The `DirectMessageParser.parse()` is used for list responses (messages/v2) and doesn't need thread_id. The `parseMessageFromResponse()` is used for single message responses (after sending) and does parse thread_id. This is correct.

---

### Task 14: Verify and test complete flow

**Covers:** End-to-end testing of all features against the local server.

**Files:**
- All modified files from previous tasks

- [ ] **Step 1: Build the project**

```bash
./gradlew assembleDebug
```

- [ ] **Step 2: Test login with lgcr837/20120218ppp**

- [ ] **Step 3: Test friend list loading**

- [ ] **Step 4: Test group list loading**

- [ ] **Step 5: Test direct message sending and receiving**

- [ ] **Step 6: Test group message sending and receiving**

- [ ] **Step 7: Test quote message sending and receiving**

- [ ] **Step 8: Test typing indicators**

- [ ] **Step 9: Test message search**

- [ ] **Step 10: Test unread sync**

- [ ] **Step 11: Final commit**

---

## Summary

After thorough analysis, the old client is **already largely compatible** with the new server:

1. **All POST request fields match** the server's `DisallowUnknownFields()` structs
2. **WebSocket message types** are all handled correctly
3. **Response parsing** uses `optString`/`optInt` which handles new fields gracefully
4. **Message v2 endpoints** are already used for loading messages
5. **Quote support** is already implemented in `MessagePayload` and `MessagePayloadBuilder`

The main areas that may need attention:
- Login response parsing resilience (Task 2)
- Message recall UI (Task 6)
- Group leave/dissolve UI (Task 7)
- Me checkin feature (Task 9)
