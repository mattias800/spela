import { useState } from "react";
import { useWebSocketEvent } from "@/hooks/use-websocket";

interface GroupingProgressEvent {
  message: string;
  current: number;
  total: number;
}

/**
 * Listens for grouping_progress and grouping_complete WebSocket events.
 * Returns the current grouping status for display on the Library Scan page.
 */
export function useGroupingProgress() {
  const [active, setActive] = useState(false);
  const [message, setMessage] = useState("");
  const [current, setCurrent] = useState(0);
  const [total, setTotal] = useState(0);

  useWebSocketEvent("grouping_progress", (payload: GroupingProgressEvent) => {
    setActive(true);
    setMessage(payload.message || "Grouping variants...");
    setCurrent(payload.current || 0);
    setTotal(payload.total || 0);
  });

  useWebSocketEvent("grouping_complete", () => {
    setActive(false);
    setMessage("");
    setCurrent(0);
    setTotal(0);
  });

  return { active, message, current, total };
}
