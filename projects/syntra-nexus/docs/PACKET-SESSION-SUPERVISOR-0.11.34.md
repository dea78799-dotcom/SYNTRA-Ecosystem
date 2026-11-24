# Packet Session Supervisor — 0.11.34-dev

## Scope
- Add a deterministic session policy layer for a future transport loop.
- States: NEW, ACTIVE, DISCONNECTED and CLOSED.
- Bound the outbound queue (default 32, hard maximum 256); enqueue never waits for capacity.
- Reserve PING/PONG for the supervisor so application code cannot forge control frames.
- Generate a correlated PING after the heartbeat interval and require the matching PONG before the response deadline.
- Respond to a peer PING with a PONG carrying the same request ID.
- Treat unsolicited/mismatched PONG, missing PONG, backwards monotonic time and control-queue overflow as fail-closed disconnects.
- Clear queued packets and pending heartbeat state on disconnect/close.

## Clock and threading contract
- The owner must call start/tick/pollOutbound/receive using one monotonic clock, normally System.nanoTime().
- No wall-clock timestamps are used for deadlines.
- The class is synchronized and deterministic; it creates no executor, scheduler, worker thread or socket.
- The transport owner must call tick periodically and serialize actual channel reads/writes through the existing TLS lifecycle manager.
- This layer does not implement retransmission, reconnect, packet persistence or application command execution.

## Security boundary
- No socket creation, LAN listener, service discovery, QR token exchange, file transfer, screen capture or remote action is enabled.
- A PONG proves only correlation with the current heartbeat; it does not replace mutual TLS, transcript binding, capability authorization or user consent.
- Tests are authored but are not verified until CI reports a successful run.
