# TLS bounded packet channel — 0.11.33-dev

## Scope

- Read and write existing SNX1 frames over a caller-supplied, already-authenticated TLS socket managed by TlsSocketLifecycleManager.
- Read the fixed 32-byte header first. Validate the declared payload length against the existing 64 KiB protocol maximum before allocating the frame buffer.
- Read exactly the declared payload length and pass the complete frame to NexusTransportCodec for marker, version, type, request ID, truncation and trailing-data validation.
- Serialize every read/write through the lifecycle manager.
- Configure a positive SO_TIMEOUT before each blocking read and restore the previous timeout afterward.
- A timeout before any byte of a new header is received leaves the channel reusable. A partial-header timeout, payload timeout, truncated frame, malformed header or I/O failure closes the manager because the stream cannot safely be resynchronized.
- Clean EOF between frames returns null and closes the manager.
- Writes are bounded to the existing maximum packet size and flushed under the I/O lock.

## Timeout boundary

Java Socket SO_TIMEOUT applies to blocking reads, not writes. It must be configured before entering the blocking read; timeout raises SocketTimeoutException without automatically closing the socket. Closing a socket causes threads blocked in socket I/O to fail. The implementation therefore preserves an idle channel only when a read times out before consuming any frame bytes. A timeout after partial frame consumption is terminal.

There is no portable write-timeout API in java.net.Socket. This milestone does not claim bounded write duration. A future transport owner must close the lifecycle manager to cancel a blocked write and must not expose raw streams outside the manager.

## Verification boundary

The channel does not create/connect sockets, perform TLS handshakes, exchange pairing tokens, authenticate capability requests, transfer arbitrary files, capture screens or execute remote actions. It only carries already-encoded SNX1 packets on an already-authorized TLS channel. Six mutual-TLS loopback tests are authored for fragmented reads, idle timeout, partial-header timeout, payload timeout, oversized lengths and writes. They are not considered passed until CI reports success. No production LAN transport is enabled.
