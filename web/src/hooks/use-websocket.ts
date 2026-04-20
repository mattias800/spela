import { useEffect, useRef } from "react";
import { api } from "@/lib/api-client";

interface WsEvent {
  type: string;
  payload: unknown;
}

type Listener = (payload: unknown) => void;

let socket: WebSocket | null = null;
let listeners: Map<string, Set<Listener>> = new Map();
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

function connect() {
  const token = api.getAccessToken();
  if (!token) return;

  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  const url = `${proto}//${window.location.host}/api/ws?token=${encodeURIComponent(token)}`;

  socket = new WebSocket(url);

  socket.onmessage = (ev) => {
    try {
      const event: WsEvent = JSON.parse(ev.data);
      const set = listeners.get(event.type);
      if (set) {
        set.forEach((fn) => fn(event.payload));
      }
    } catch (err) {
      if (import.meta.env.DEV) {
        console.warn("[WebSocket] Failed to parse message:", ev.data, err);
      }
    }
  };

  socket.onclose = () => {
    socket = null;
    if (listeners.size > 0) {
      reconnectTimer = setTimeout(connect, 3000);
    }
  };

  socket.onerror = () => {
    socket?.close();
  };
}

function subscribe(type: string, listener: Listener) {
  let set = listeners.get(type);
  if (!set) {
    set = new Set();
    listeners.set(type, set);
  }
  set.add(listener);

  // Connect if not already connected
  if (!socket || socket.readyState === WebSocket.CLOSED) {
    connect();
  }
}

function unsubscribe(type: string, listener: Listener) {
  const set = listeners.get(type);
  if (set) {
    set.delete(listener);
    if (set.size === 0) {
      listeners.delete(type);
    }
  }

  // Disconnect if no listeners remain
  if (listeners.size === 0) {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    socket?.close();
    socket = null;
  }
}

/**
 * Subscribe to a WebSocket event by name. The `callback` parameter type
 * defines the caller's contract with the server for this event's payload —
 * the runtime data comes through as `unknown` and is passed to the callback
 * without validation. If the server ever changes the shape, the first
 * property access inside the callback will blow up at runtime.
 *
 * Swapping to per-event runtime validation (e.g. zod schemas) would
 * eliminate the trust here; until that lands, callers should keep payload
 * property access defensive.
 */
export function useWebSocketEvent<P>(
  type: string,
  callback: (payload: P) => void,
) {
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  useEffect(() => {
    const listener: Listener = (payload) => {
      // Trust-the-server narrow at the WebSocket boundary; see the JSDoc
      // above. Contained to this single line so the rest of the hook graph
      // stays `as`-free.
      callbackRef.current(payload as P);
    };
    subscribe(type, listener);
    return () => unsubscribe(type, listener);
  }, [type]);
}
