import { useState, useEffect } from "react";

/**
 * Detects whether at least one gamepad is currently connected.
 * Uses the browser Gamepad API events and polls initial state on mount.
 */
export function useGamepadConnected(): boolean {
  const [connected, setConnected] = useState(() => {
    const gamepads = navigator.getGamepads?.();
    return gamepads
      ? Array.from(gamepads).some((gp) => gp !== null)
      : false;
  });

  useEffect(() => {
    function onConnect() {
      setConnected(true);
    }

    function onDisconnect() {
      const gamepads = navigator.getGamepads?.();
      setConnected(
        gamepads
          ? Array.from(gamepads).some((gp) => gp !== null)
          : false,
      );
    }

    window.addEventListener("gamepadconnected", onConnect);
    window.addEventListener("gamepaddisconnected", onDisconnect);
    return () => {
      window.removeEventListener("gamepadconnected", onConnect);
      window.removeEventListener("gamepaddisconnected", onDisconnect);
    };
  }, []);

  return connected;
}
