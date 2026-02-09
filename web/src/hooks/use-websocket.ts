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
    } catch {
      // ignore malformed messages
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

export function useWebSocketEvent(type: string, callback: (payload: never) => void) {
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  useEffect(() => {
    const listener: Listener = (payload) => callbackRef.current(payload as never);
    subscribe(type, listener);
    return () => unsubscribe(type, listener);
  }, [type]);
}
