# Fix WebSocket Connection Error

The application is experiencing a `websocket error` during the Socket.IO connection. This is likely due to the connection attempt starting with the `websocket` transport directly, which can fail due to network restrictions, proxy issues, or server configuration.

## Proposed Changes

### [Component Name] Socket Networking

#### [MODIFY] [GameSocketClient.java](file:///C:/Users/sddrk/Documents/Projects/Territory Wars/android/app/src/main/java/glab/guesscard/socket/GameSocketClient.java)
- Change the transport order to `polling` then `websocket`. This allows the connection to establish quickly via HTTP and then upgrade to WebSocket, which is the recommended approach for Socket.IO.
- Add `query` parameters to the connection options using the existing `buildQuery()` method, ensuring compatibility with servers that might not support the `auth` field (Socket.IO 2.x and below).
- Improve error logging to capture the root cause of connection failures.

## Verification Plan

### Manual Verification
- Run the app and check the Logcat for the `GameSocketClient` tag.
- Verify that "connected" is logged and the `connect_error` is no longer appearing.
- Test real-time features like joining a room or chat to ensure the connection is functional.
